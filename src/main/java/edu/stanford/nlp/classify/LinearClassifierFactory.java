// Stanford Classifier - a multiclass maxent classifier
// LinearClassifierFactory
// Copyright (c) 2003-2007 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    Support/Questions: java-nlp-user@lists.stanford.edu
//    Licensing: java-nlp-support@lists.stanford.edu
//    http://www-nlp.stanford.edu/software/classifier.shtml

package edu.stanford.nlp.classify;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.*;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.MultiClassAccuracyStats;
import edu.stanford.nlp.stats.Scorer;
import edu.stanford.nlp.util.ArrayUtils;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.Triple;

/**
 * Builds various types of linear classifiers, with functionality for
 * setting objective function, optimization method, and other parameters.
 * Classifiers can be defined with passed constructor arguments or using setter methods.
 * Defaults to Quasi-newton optimization of a <code>LogConditionalObjectiveFunction</code>
 * (Merges old classes: CGLinearClassifierFactory, QNLinearClassifierFactory, and MaxEntClassifierFactory).
 *
 * @author Jenny Finkel
 * @author Chris Cox (merged factories, 8/11/04)
 * @author Dan Klein (CGLinearClassifierFactory, MaxEntClassifierFactory)
 * @author Galen Andrew (tuneSigma), Marie-Catherine de Marneffe (CV in tuneSigma)
 * @author Sarah Spikes (Templatization, though I don't know what to do with the Minimizer)
 * @author Ramesh Nallapati (nmramesh@cs.stanford.edu) {@link #trainSemiSupGE} methods
 */

public class LinearClassifierFactory<L, F> extends AbstractLinearClassifierFactory<L, F> {

  private static final long serialVersionUID = 7893768984379107397L;
  private double TOL;
  //public double sigma;
  private int mem = 15;
  private boolean verbose = false;
  //private int prior;
  //private double epsilon = 0.0;
  private LogPrior logPrior;
  private Minimizer<DiffFunction> minimizer;
  //private boolean useSum = false;
  private boolean tuneSigmaHeldOut = false;
  private boolean tuneSigmaCV = false;
  //private boolean resetWeight = true;
  private int folds;
  private double min = 0.1;
  private double max = 10.0;
  private boolean retrainFromScratchAfterSigmaTuning = false;


  /**
   * Adapt classifier (adjust the mean of Gaussian prior)
   * under construction -pichuan
   * @param origWeights the original weights trained from the training data
   * @param adaptDataset the Dataset used to adapt the trained weights
   * @return adapted weights
   */
  public double[][] adaptWeights(double[][] origWeights, GeneralDataset<L, F> adaptDataset) {
    System.err.println("adaptWeights in LinearClassifierFactory. increase weight dim only");
    double[][] newWeights = new double[adaptDataset.featureIndex.size()][adaptDataset.labelIndex.size()];

    System.arraycopy(origWeights,0,newWeights,0,origWeights.length);

    AdaptedGaussianPriorObjectiveFunction<L, F> objective = new AdaptedGaussianPriorObjectiveFunction<L, F>(adaptDataset, logPrior,newWeights);

    double[] initial = objective.initial();

    double[] weights = minimizer.minimize(objective, TOL, initial);
    return objective.to2D(weights);

    //Question: maybe the adaptWeights can be done just in LinearClassifier ?? (pichuan)
  }

  @Override
  public double[][] trainWeights(GeneralDataset<L, F> dataset) {
    return trainWeights(dataset, null);
  }

  public double[][] trainWeights(GeneralDataset<L, F> dataset, double[] initial) {
    return trainWeights(dataset, initial, false);
  }

  public double[][] trainWeights(GeneralDataset<L, F> dataset, double[] initial, boolean bypassTuneSigma) {
    if(dataset instanceof RVFDataset)
      ((RVFDataset<L,F>)dataset).ensureRealValues();
    double[] interimWeights = null;
    if(! bypassTuneSigma) {
      if (tuneSigmaHeldOut) {
        interimWeights = heldOutSetSigma(dataset); // the optimum interim weights from held-out training data have already been found.
      } else if (tuneSigmaCV) {
        crossValidateSetSigma(dataset,folds); // TODO: assign optimum interim weights as part of this process.
      }
    }
    LogConditionalObjectiveFunction<L, F> objective = new LogConditionalObjectiveFunction<L, F>(dataset, logPrior);
    if(initial == null && interimWeights != null && ! retrainFromScratchAfterSigmaTuning) {
      //System.err.println("## taking advantage of interim weights as starting point.");
      initial = interimWeights;
    }
    if (initial == null) {
      initial = objective.initial();
    }

    double[] weights = minimizer.minimize(objective, TOL, initial);
    return objective.to2D(weights);
  }

  /**
   * IMPORTANT: dataset and biasedDataset must have same featureIndex, labelIndex
   */
  public Classifier<L, F> trainClassifierSemiSup(GeneralDataset<L, F> data, GeneralDataset<L, F> biasedData, double[][] confusionMatrix, double[] initial) {
    double[][] weights =  trainWeightsSemiSup(data, biasedData, confusionMatrix, initial);
    LinearClassifier<L, F> classifier = new LinearClassifier<L, F>(weights, data.featureIndex(), data.labelIndex());
    return classifier;
  }

  public double[][] trainWeightsSemiSup(GeneralDataset<L, F> data, GeneralDataset<L, F> biasedData, double[][] confusionMatrix, double[] initial) {
    LogConditionalObjectiveFunction<L, F> objective = new LogConditionalObjectiveFunction<L, F>(data, new LogPrior(LogPrior.LogPriorType.NULL));
    BiasedLogConditionalObjectiveFunction biasedObjective = new BiasedLogConditionalObjectiveFunction(biasedData, confusionMatrix, new LogPrior(LogPrior.LogPriorType.NULL));
    SemiSupervisedLogConditionalObjectiveFunction semiSupObjective = new SemiSupervisedLogConditionalObjectiveFunction(objective, biasedObjective, logPrior);
    if (initial == null) {
      initial = objective.initial();
    }
    double[] weights = minimizer.minimize(semiSupObjective, TOL, initial);
    return objective.to2D(weights);
  }

  /**
   * Trains the linear classifier using Generalized Expectation criteria as described in
   * <tt>Generalized Expectation Criteria for Semi Supervised Learning of Conditional Random Fields</tt>, Mann and McCallum, ACL 2008.
   * The original algorithm is proposed for CRFs but has been adopted to LinearClassifier (which is a simpler special case of a CRF).
   * IMPORTANT: the labeled features that are passed as an argument are assumed to be binary valued, although
   * other features are allowed to be real valued.
   */
  public LinearClassifier<L,F> trainSemiSupGE(GeneralDataset<L, F> labeledDataset, List<? extends Datum<L, F>> unlabeledDataList, List<F> GEFeatures, double convexComboCoeff) {
    LogConditionalObjectiveFunction<L, F> objective = new LogConditionalObjectiveFunction<L, F>(labeledDataset, new LogPrior(LogPrior.LogPriorType.NULL));
    GeneralizedExpectationObjectiveFunction<L,F> geObjective = new GeneralizedExpectationObjectiveFunction<L,F>(labeledDataset, unlabeledDataList, GEFeatures);
    SemiSupervisedLogConditionalObjectiveFunction semiSupObjective = new SemiSupervisedLogConditionalObjectiveFunction(objective, geObjective, null,convexComboCoeff);
    double[] initial = objective.initial();
    double[] weights = minimizer.minimize(semiSupObjective, TOL, initial);
    return new LinearClassifier<L, F>(objective.to2D(weights), labeledDataset.featureIndex(), labeledDataset.labelIndex());
  }


  /**
   * Trains the linear classifier using Generalized Expectation criteria as described in
   * <tt>Generalized Expectation Criteria for Semi Supervised Learning of Conditional Random Fields</tt>, Mann and McCallum, ACL 2008.
   * The original algorithm is proposed for CRFs but has been adopted to LinearClassifier (which is a simpler, special case of a CRF).
   * Automatically discovers high precision, high frequency labeled features to be used as GE constraints.
   * IMPORTANT: the current feature selector assumes the features are binary. The GE constraints assume the constraining features are binary anyway, although
   * it doesn't make such assumptions about other features.
   */
  public LinearClassifier<L,F> trainSemiSupGE(GeneralDataset<L, F> labeledDataset, List<? extends Datum<L, F>> unlabeledDataList) {
    List<F> GEFeatures = getHighPrecisionFeatures(labeledDataset,0.9,10);
    return trainSemiSupGE(labeledDataset, unlabeledDataList, GEFeatures,0.5);
  }

  public LinearClassifier<L,F> trainSemiSupGE(GeneralDataset<L, F> labeledDataset, List<? extends Datum<L, F>> unlabeledDataList, double convexComboCoeff) {
    List<F> GEFeatures = getHighPrecisionFeatures(labeledDataset,0.9,10);
    return trainSemiSupGE(labeledDataset, unlabeledDataList, GEFeatures,convexComboCoeff);
  }


  /**
   * Returns a list of featured thresholded by minPrecision and sorted by their frequency of occurrence.
   * precision in this case, is defined as the frequency of majority label over total frequency for that feature.
   * @return list of high precision features.
   */
  private List<F> getHighPrecisionFeatures(GeneralDataset<L,F> dataset, double minPrecision, int maxNumFeatures){
    int[][] feature2label = new int[dataset.numFeatures()][dataset.numClasses()];
    for(int f = 0; f < dataset.numFeatures(); f++)
      Arrays.fill(feature2label[f],0);

    int[][] data = dataset.data;
    int[] labels = dataset.labels;
    for(int d = 0; d < data.length; d++){
      int label = labels[d];
      //System.out.println("datum id:"+d+" label id: "+label);
      if(data[d] != null){
        //System.out.println(" number of features:"+data[d].length);
        for(int n = 0; n < data[d].length; n++){
          feature2label[data[d][n]][label]++;
        }
      }
    }
    Counter<F> feature2freq = new ClassicCounter<F>();
    for(int f = 0; f < dataset.numFeatures(); f++){
     int maxF = ArrayMath.max(feature2label[f]);
     int total = ArrayMath.sum(feature2label[f]);
     double precision = ((double)maxF)/total;
     F feature = dataset.featureIndex.get(f);
     if(precision >= minPrecision){
       feature2freq.incrementCount(feature, total);
     }
    }
    if(feature2freq.size() > maxNumFeatures){
      Counters.retainTop(feature2freq, maxNumFeatures);
    }
    //for(F feature : feature2freq.keySet())
      //System.out.println(feature+" "+feature2freq.getCount(feature));
    //System.exit(0);
    return Counters.toSortedList(feature2freq);
  }

  /**
   * Train a classifier with a sigma tuned on a validation set.
   *
   * @return The constructed classifier
   */
  public LinearClassifier<L, F> trainClassifierV(GeneralDataset<L, F> train, GeneralDataset<L, F> validation, double min, double max, boolean accuracy) {
    labelIndex = train.labelIndex();
    featureIndex = train.featureIndex();
    this.min = min;
    this.max = max;
    heldOutSetSigma(train, validation);
    double[][] weights = trainWeights(train);
    return new LinearClassifier<L, F>(weights, train.featureIndex(), train.labelIndex());
  }

  /**
   * Train a classifier with a sigma tuned on a validation set.
   * In this case we are fitting on the last 30% of the training data.
   *
   * @param train The data to train (and validate) on.
   * @return The constructed classifier
   */
  public LinearClassifier<L, F> trainClassifierV(GeneralDataset<L, F> train, double min, double max, boolean accuracy) {
    labelIndex = train.labelIndex();
    featureIndex = train.featureIndex();
    tuneSigmaHeldOut = true;
    this.min = min;
    this.max = max;
    heldOutSetSigma(train);
    double[][] weights = trainWeights(train);
    return new LinearClassifier<L, F>(weights, train.featureIndex(), train.labelIndex());
  }


  public LinearClassifierFactory() {
    this(new QNMinimizer(15));
  }

  public LinearClassifierFactory(Minimizer<DiffFunction> min) {
    this(min, false);
  }

  public LinearClassifierFactory(boolean useSum) {
    this(new QNMinimizer(15), useSum);
  }

  public LinearClassifierFactory(double tol) {
    this(new QNMinimizer(15), tol, false);
  }
  public LinearClassifierFactory(Minimizer<DiffFunction> min, boolean useSum) {
    this(min, 1e-4, useSum);
  }
  public LinearClassifierFactory(Minimizer<DiffFunction> min, double tol, boolean useSum) {
    this(min, tol, useSum, 1.0);
  }
  public LinearClassifierFactory(double tol, boolean useSum, double sigma) {
    this(new QNMinimizer(15), tol, useSum, sigma);
  }
  public LinearClassifierFactory(Minimizer<DiffFunction> min, double tol, boolean useSum, double sigma) {
    this(min, tol, useSum, LogPrior.LogPriorType.QUADRATIC.ordinal(), sigma);
  }
  public LinearClassifierFactory(Minimizer<DiffFunction> min, double tol, boolean useSum, int prior, double sigma) {
    this(min, tol, useSum, prior, sigma, 0.0);
  }
  public LinearClassifierFactory(double tol, boolean useSum, int prior, double sigma, double epsilon) {
    this(new QNMinimizer(15), tol, useSum, new LogPrior(prior, sigma, epsilon));
  }

  public LinearClassifierFactory(double tol, boolean useSum, int prior, double sigma, double epsilon, int mem) {
    this(new QNMinimizer(mem), tol, useSum, new LogPrior(prior, sigma, epsilon));
  }

  /**
   * Create a factory that builds linear classifiers from training data.
   *
   * @param min     The method to be used for optimization (minimization) (default: {@link QNMinimizer})
   * @param tol     The convergence threshold for the minimization (default: 1e-4)
   * @param useSum  Asks to the optimizer to minimize the sum of the
   *                likelihoods of individual data items rather than their product (default: false)
   *                NOTE: this is currently ignored!!!
   * @param prior   What kind of prior to use, as an enum constant from class
   *                LogPrior
   * @param sigma   The strength of the prior (smaller is stronger for most
   *                standard priors) (default: 1.0)
   * @param epsilon A second parameter to the prior (currently only used
   *                by the Huber prior)
   */
  public LinearClassifierFactory(Minimizer<DiffFunction> min, double tol, boolean useSum, int prior, double sigma, double epsilon) {
    this(min, tol, useSum, new LogPrior(prior, sigma, epsilon));
  }

  public LinearClassifierFactory(Minimizer<DiffFunction> min, double tol, boolean useSum, LogPrior logPrior) {
    this.minimizer = min;
    this.TOL = tol;
    //this.useSum = useSum;
    this.logPrior = logPrior;
  }

  /**
   * Set the tolerance.  1e-4 is the default.
   */
  public void setTol(double tol) {
    this.TOL = tol;
  }

  /**
   * Set the prior.
   *
   * @param logPrior One of the priors defined in
   *              <code>LogConditionalObjectiveFunction</code>.
   *              <code>LogPrior.QUADRATIC</code> is the default.
   */
  public void setPrior(LogPrior logPrior) {
    this.logPrior = logPrior;
  }

  /**
   * Set the verbose flag for {@link CGMinimizer}.
   * Only used with conjugate-gradient minimization.
   * <code>false</code> is the default.
   */

  public void setVerbose(boolean verbose) {
    this.verbose = verbose;
  }

  /**
   * Sets the minimizer.  {@link QNMinimizer} is the default.
   */
  public void setMinimizer(Minimizer<DiffFunction> min) {
    this.minimizer = min;
  }

  /**
   * Sets the epsilon value for {@link LogConditionalObjectiveFunction}.
   */
  public void setEpsilon(double eps) {
    logPrior.setEpsilon(eps);
  }

  public void setSigma(double sigma) {
    logPrior.setSigma(sigma);
  }

  public double getSigma() {
    return logPrior.getSigma();
  }

  /**
   * Sets the minimizer to QuasiNewton. {@link QNMinimizer} is the default.
   */
  public void useQuasiNewton() {
    this.minimizer = new QNMinimizer(mem);
  }

  public void useQuasiNewton(boolean useRobust) {
    this.minimizer = new QNMinimizer(mem,useRobust);
  }

  public void useStochasticQN(double initialSMDGain, int stochasticBatchSize){
    this.minimizer = new SQNMinimizer<DiffFunction>(mem,initialSMDGain,stochasticBatchSize,false);
  }

  public void useStochasticMetaDescent(){
    useStochasticMetaDescent(0.1,15,StochasticCalculateMethods.ExternalFiniteDifference,20);
  }

  public void useStochasticMetaDescent(double initialSMDGain, int stochasticBatchSize,StochasticCalculateMethods stochasticMethod,int passes) {
    this.minimizer = new SMDMinimizer<DiffFunction>(initialSMDGain, stochasticBatchSize,stochasticMethod,passes);
  }

  public void useStochasticGradientDescent(){
    useStochasticGradientDescent(0.1,15);
  }

  public void useStochasticGradientDescent(double gainSGD, int stochasticBatchSize){
    this.minimizer = new SGDMinimizer<DiffFunction>(gainSGD,stochasticBatchSize);
  }

  public void useInPlaceStochasticGradientDescent(){
    useInPlaceStochasticGradientDescent(-1, -1, 1.0);
  }

  public void useInPlaceStochasticGradientDescent(int SGDPasses, int tuneSampleSize, double sigma) {
    this.minimizer = new StochasticInPlaceMinimizer(sigma, SGDPasses, tuneSampleSize);
  }

  public void useHybridMinimizerWithInPlaceSGD(int SGDPasses, int tuneSampleSize, double sigma) {
    Minimizer<DiffFunction> firstMinimizer = new StochasticInPlaceMinimizer(sigma, SGDPasses, tuneSampleSize);
    Minimizer<DiffFunction> secondMinimizer = new QNMinimizer(mem);
    this.minimizer = new HybridMinimizer(firstMinimizer, secondMinimizer, SGDPasses);
  }

  public void useStochasticGradientDescentToQuasiNewton(double SGDGain, int batchSize, int sgdPasses,
                                                        int qnPasses, int hessSamples, int QNMem,
                                                        boolean outputToFile) {
    this.minimizer = new SGDToQNMinimizer(SGDGain, batchSize, sgdPasses,
                                          qnPasses, hessSamples, QNMem, outputToFile);
  }

  public void useHybridMinimizer() {
    useHybridMinimizer(0.1,15,StochasticCalculateMethods.ExternalFiniteDifference , 0);
  }

  public void useHybridMinimizer(double initialSMDGain, int stochasticBatchSize,StochasticCalculateMethods stochasticMethod,int cutoffIteration){
    Minimizer<DiffFunction> firstMinimizer = new SMDMinimizer<DiffFunction>(initialSMDGain, stochasticBatchSize,stochasticMethod,cutoffIteration);
    Minimizer<DiffFunction> secondMinimizer = new QNMinimizer(mem);
    this.minimizer = new HybridMinimizer(firstMinimizer,secondMinimizer,cutoffIteration);
  }

  /**
   * Set the mem value for {@link QNMinimizer}.
   * Only used with quasi-newton minimization.  15 is the default.
   *
   * @param mem Number of previous function/derivative evaluations to store
   *            to estimate second derivative.  Storing more previous evaluations
   *            improves training convergence speed.  This number can be very
   *            small, if memory conservation is the priority.  For large
   *            optimization systems (of 100,000-1,000,000 dimensions), setting this
   *            to 15 produces quite good results, but setting it to 50 can
   *            decrease the iteration count by about 20% over a value of 15.
   */
  public void setMem(int mem) {
    this.mem = mem;
  }

  /**
   * Sets the minimizer to {@link CGMinimizer}, with the passed <code>verbose</code> flag.
   */
  public void useConjugateGradientAscent(boolean verbose) {
    this.verbose = verbose;
    useConjugateGradientAscent();
  }

  /**
   * Sets the minimizer to {@link CGMinimizer}.
   */
  public void useConjugateGradientAscent() {
    this.minimizer = new CGMinimizer(!this.verbose);
  }

  /**
   * NOTE: nothing is actually done with this value!
   *
   * SetUseSum sets the <code>useSum</code> flag: when turned on,
   * the Summed Conditional Objective Function is used.  Otherwise, the
   * LogConditionalObjectiveFunction is used.  The default is false.
   */
  public void setUseSum(boolean useSum) {
    //this.useSum = useSum;
  }

  /**
   * setTuneSigmaHeldOut sets the <code>tuneSigmaHeldOut</code> flag: when turned on,
   * the sigma is tuned by means of held-out (70%-30%). Otherwise no tuning on sigma is done.
   * The default is false.
   */
  public void setTuneSigmaHeldOut() {
    tuneSigmaHeldOut = true;
    tuneSigmaCV = false;
  }

  /**
   * setTuneSigmaCV sets the <code>tuneSigmaCV</code> flag: when turned on,
   * the sigma is tuned by cross-validation. The number of folds is the parameter.
   * If there is less data than the number of folds, leave-one-out is used.
   * The default is false.
   */
  public void setTuneSigmaCV(int folds) {
    tuneSigmaCV = true;
    tuneSigmaHeldOut = false;
    this.folds = folds;
  }

  /**
   * NOTE: Nothing is actually done with this value.
   *
   * resetWeight sets the <code>restWeight</code> flag. This flag makes sense only if sigma is tuned:
   * when turned on, the weights outputed by the tuneSigma method will be reset to zero when training the
   * classifier.
   * The default is false.
   */
  public void resetWeight() {
    //resetWeight = true;
  }

  static protected double[] sigmasToTry = {0.5,1.0,2.0,4.0,10.0, 20.0, 100.0};

  /**
   * Calls the method {@link #crossValidateSetSigma(GeneralDataset, int)} with 5-fold cross-validation.
   * @param dataset the data set to optimize sigma on.
   */
  public void crossValidateSetSigma(GeneralDataset<L, F> dataset) {
    crossValidateSetSigma(dataset, 5);
  }

  /**
   * callls the method {@link #crossValidateSetSigma(GeneralDataset, int, Scorer, LineSearcher)} with
   * multi-class log-likelihood scoring (see {@link MultiClassAccuracyStats}) and golden-section line search
   * (see {@link GoldenSectionLineSearch}).
   * @param dataset the data set to optimize sigma on.
   */
  public void crossValidateSetSigma(GeneralDataset<L, F> dataset,int kfold) {
    System.err.println("##you are here.");
    crossValidateSetSigma(dataset, kfold, new MultiClassAccuracyStats<L>(MultiClassAccuracyStats.USE_LOGLIKELIHOOD), new GoldenSectionLineSearch(true, 1e-2, min, max));
  }

  public void crossValidateSetSigma(GeneralDataset<L, F> dataset,int kfold, final Scorer<L> scorer) {
    crossValidateSetSigma(dataset, kfold, scorer, new GoldenSectionLineSearch(true, 1e-2, min, max));
  }
  public void crossValidateSetSigma(GeneralDataset<L, F> dataset,int kfold, LineSearcher minimizer) {
    crossValidateSetSigma(dataset, kfold, new MultiClassAccuracyStats<L>(MultiClassAccuracyStats.USE_LOGLIKELIHOOD), minimizer);
  }
  /**
   * Sets the sigma parameter to a value that optimizes the cross-validation score given by <code>scorer</code>.  Search for an optimal value
   * is carried out by <code>minimizer</code>
   * @param dataset the data set to optimize sigma on.
   */
  public void crossValidateSetSigma(GeneralDataset<L, F> dataset,int kfold, final Scorer<L> scorer, LineSearcher minimizer) {
    System.err.println("##in Cross Validate, folds = " + kfold);
    System.err.println("##Scorer is " + scorer);

    featureIndex = dataset.featureIndex;
    labelIndex = dataset.labelIndex;

    final CrossValidator<L, F> crossValidator = new CrossValidator<L, F>(dataset,kfold);
    final Function<Triple<GeneralDataset<L, F>,GeneralDataset<L, F>,CrossValidator.SavedState>,Double> score =
      new Function<Triple<GeneralDataset<L, F>,GeneralDataset<L, F>,CrossValidator.SavedState>,Double> ()
      {
        public Double apply (Triple<GeneralDataset<L, F>,GeneralDataset<L, F>,CrossValidator.SavedState> fold) {
          GeneralDataset<L, F> trainSet = fold.first();
          GeneralDataset<L, F> devSet   = fold.second();

          double[] weights = (double[])fold.third().state;
          double[][] weights2D;

          weights2D = trainWeights(trainSet, weights,true); // must of course bypass sigma tuning here.

          fold.third().state = ArrayUtils.flatten(weights2D);

          LinearClassifier<L, F> classifier = new LinearClassifier<L, F>(weights2D, trainSet.featureIndex, trainSet.labelIndex);

          double score = scorer.score(classifier, devSet);
          //System.out.println("score: "+score);
          System.out.print(".");
          return score;
        }
      };

    Function<Double,Double> negativeScorer =
      new Function<Double,Double> ()
      {
        public Double apply(Double sigmaToTry) {
          //sigma = sigmaToTry;
          setSigma(sigmaToTry);
          Double averageScore = crossValidator.computeAverage(score);
          System.err.print("##sigma = "+getSigma()+" ");
          System.err.println("-> average Score: "+averageScore);
          return -averageScore;
        }
      };

    double bestSigma = minimizer.minimize(negativeScorer);
    System.err.println("##best sigma: " + bestSigma);
    setSigma(bestSigma);
  }

  /**
   * Set the {@link LineSearcher} to be used in {@link #heldOutSetSigma(GeneralDataset, GeneralDataset)}.
   */
  public void setHeldOutSearcher(LineSearcher heldOutSearcher) {
    this.heldOutSearcher = heldOutSearcher;
  }

  private LineSearcher heldOutSearcher = null;
  public double[] heldOutSetSigma(GeneralDataset<L, F> train) {
    Pair<GeneralDataset<L, F>, GeneralDataset<L, F>> data = train.split(0.3);
    return heldOutSetSigma(data.first(), data.second());
  }

  public double[] heldOutSetSigma(GeneralDataset<L, F> train, Scorer<L> scorer) {
    Pair<GeneralDataset<L, F>, GeneralDataset<L, F>> data = train.split(0.3);
    return heldOutSetSigma(data.first(), data.second(), scorer);
  }

  public double[] heldOutSetSigma(GeneralDataset<L, F> train, GeneralDataset<L, F> dev) {
    return heldOutSetSigma(train, dev, new MultiClassAccuracyStats<L>(MultiClassAccuracyStats.USE_LOGLIKELIHOOD), heldOutSearcher == null ? new GoldenSectionLineSearch(true, 1e-2, min, max) : heldOutSearcher);
  }

  public double[] heldOutSetSigma(GeneralDataset<L, F> train, GeneralDataset<L, F> dev, final Scorer<L> scorer) {
    return heldOutSetSigma(train, dev, scorer, new GoldenSectionLineSearch(true, 1e-2, min, max));
  }
  public double[]  heldOutSetSigma(GeneralDataset<L, F> train, GeneralDataset<L, F> dev, LineSearcher minimizer) {
    return heldOutSetSigma(train, dev, new MultiClassAccuracyStats<L>(MultiClassAccuracyStats.USE_LOGLIKELIHOOD), minimizer);
  }

  /**
   * Sets the sigma parameter to a value that optimizes the held-out score given by <code>scorer</code>.  Search for an optimal value
   * is carried out by <code>minimizer</code>
   * dataset the data set to optimize sigma on.
   * kfold
   * @return an interim set of optimal weights: the weights
   */
  public double[] heldOutSetSigma(final GeneralDataset<L, F> trainSet, final GeneralDataset<L, F> devSet, final Scorer<L> scorer, LineSearcher minimizer) {

    featureIndex = trainSet.featureIndex;
    labelIndex = trainSet.labelIndex;
    //double[] resultWeights = null;
    Timing timer = new Timing();

    NegativeScorer negativeScorer = new NegativeScorer(trainSet,devSet,scorer,timer);

    timer.start();
    double bestSigma = minimizer.minimize(negativeScorer);
    System.err.println("##best sigma: " + bestSigma);
    setSigma(bestSigma);

    return ArrayUtils.flatten(trainWeights(trainSet,negativeScorer.weights,true)); // make sure it's actually the interim weights from best sigma
  }

  class NegativeScorer implements Function<Double, Double> {
    public double[] weights = null;
    GeneralDataset<L, F> trainSet;
    GeneralDataset<L, F> devSet;
    Scorer<L> scorer;
    Timing timer;

    public NegativeScorer(GeneralDataset<L, F> trainSet, GeneralDataset<L, F> devSet, Scorer<L> scorer,Timing timer) {
      super();
      this.trainSet = trainSet;
      this.devSet = devSet;
      this.scorer = scorer;
      this.timer = timer;
    }

    public Double apply(Double sigmaToTry) {
      double[][] weights2D;
      setSigma(sigmaToTry);

      weights2D = trainWeights(trainSet, weights,true); //bypass.

      weights = ArrayUtils.flatten(weights2D);

      LinearClassifier<L, F> classifier = new LinearClassifier<L, F>(weights2D, trainSet.featureIndex, trainSet.labelIndex);

      double score = scorer.score(classifier, devSet);
      //System.out.println("score: "+score);
      //System.out.print(".");
      System.err.print("##sigma = "+getSigma()+" ");
      System.err.println("-> average Score: "+ score);
      System.err.println("##time elapsed: " + timer.stop() + " milliseconds.");
      timer.restart();
      return -score;
    }
  }

  /** If set to true, then when training a classifier, after an optimal sigma is chosen a model is relearned from
   * scratch. If set to false (the default), then the model is updated from wherever it wound up in the sigma-tuning process.
   * The latter is likely to be faster, but it's not clear which model will wind up better.  */
  public void setRetrainFromScratchAfterSigmaTuning( boolean retrainFromScratchAfterSigmaTuning) {
    this.retrainFromScratchAfterSigmaTuning = retrainFromScratchAfterSigmaTuning;
  }


  public Classifier<L, F> trainClassifier(Iterable<Datum<L, F>> dataIterable) {
    Index<F> featureIndex = Generics.newIndex();
    Index<L> labelIndex = Generics.newIndex();
    for(Datum<L, F> d : dataIterable) {
      labelIndex.add(d.label());
      featureIndex.addAll(d.asFeatures());//If there are duplicates, it doesn't add them again.
    }
    System.err.println(String.format("Training linear classifier with %d features and %d labels", featureIndex.size(), labelIndex.size()));

    LogConditionalObjectiveFunction<L, F> objective = new LogConditionalObjectiveFunction<L, F>(dataIterable, logPrior, featureIndex, labelIndex);
    objective.setPrior(new LogPrior(LogPrior.LogPriorType.QUADRATIC));

    double[] initial = objective.initial();
    double[] weights = minimizer.minimize(objective, TOL, initial);

    LinearClassifier<L, F> classifier = new LinearClassifier<L, F>(objective.to2D(weights), featureIndex, labelIndex);
    return
      classifier;

  }

  public Classifier<L, F> trainClassifier(GeneralDataset<L, F> dataset, float[] dataWeights, LogPrior prior) {
    if(dataset instanceof RVFDataset)
      ((RVFDataset<L,F>)dataset).ensureRealValues();
    LogConditionalObjectiveFunction<L, F> objective = new LogConditionalObjectiveFunction<L, F>(dataset, dataWeights, logPrior);

    double[] initial = objective.initial();
    double[] weights = minimizer.minimize(objective, TOL, initial);

    LinearClassifier<L, F> classifier = new LinearClassifier<L, F>(objective.to2D(weights), dataset.featureIndex(), dataset.labelIndex());
    return classifier;
  }


  @Override
  public LinearClassifier<L, F> trainClassifier(GeneralDataset<L, F> dataset) {
    return trainClassifier(dataset, null);
  }
  public LinearClassifier<L, F> trainClassifier(GeneralDataset<L, F> dataset, double[] initial) {
    if(dataset instanceof RVFDataset)
      ((RVFDataset<L,F>)dataset).ensureRealValues();
    double[][] weights =  trainWeights(dataset, initial, false);
    LinearClassifier<L, F> classifier = new LinearClassifier<L, F>(weights, dataset.featureIndex(), dataset.labelIndex());
    return classifier;
  }

  /**
   * Given the path to a file representing the text based serialization of a
   * Linear Classifier, reconstitutes and returns that LinearClassifier.
   *
   * TODO: Leverage Index
   */
  public Classifier<String, String> loadFromFilename(String file) {
    try {
      File tgtFile = new File(file);
      BufferedReader in = new BufferedReader(new FileReader(tgtFile));

      // Format: read indicies first, weights, then thresholds
      Index<String> labelIndex = HashIndex.loadFromReader(in);
      Index<String> featureIndex = HashIndex.loadFromReader(in);
      double[][] weights = new double[featureIndex.size()][labelIndex.size()];
      String line = in.readLine();
      int currLine = 1;
      while (line != null && line.length()>0) {
        String[] tuples = line.split(LinearClassifier.TEXT_SERIALIZATION_DELIMITER);
        if (tuples.length != 3) {
            throw new Exception("Error: incorrect number of tokens in weight specifier, line="
            +currLine+" in file "+tgtFile.getAbsolutePath());
        }
        currLine++;
        int feature = Integer.valueOf(tuples[0]);
        int label = Integer.valueOf(tuples[1]);
        double value = Double.valueOf(tuples[2]);
        weights[feature][label] = value;
        line = in.readLine();
      }

      // First line in thresholds is the number of thresholds
      int numThresholds = Integer.valueOf(in.readLine());
      double[] thresholds = new double[numThresholds];
      int curr = 0;
      while ((line = in.readLine()) != null) {
        double tval = Double.valueOf(line.trim());
        thresholds[curr++] = tval;
      }
      in.close();
      LinearClassifier<String, String> classifier = new LinearClassifier<String, String>(weights, featureIndex, labelIndex);
      return classifier;
    } catch (Exception e) {
      System.err.println("Error in LinearClassifierFactory, loading from file="+file);
      e.printStackTrace();
      return null;
    }
  }

  @Deprecated
  @Override
  public LinearClassifier<L, F> trainClassifier(List<RVFDatum<L, F>> examples) {
    // TODO Auto-generated method stub
    return null;
  }

  public boolean setEvaluators(int iters, Evaluator[] evaluators)
  {
    if (minimizer instanceof HasEvaluators) {
      ((HasEvaluators) minimizer).setEvaluators(iters, evaluators);
      return true;
    } else {
      return false;
    }
  }

  public LinearClassifierCreator<L,F> getClassifierCreator(GeneralDataset<L, F> dataset) {
//    LogConditionalObjectiveFunction<L, F> objective = new LogConditionalObjectiveFunction<L, F>(dataset, logPrior);
    return new LinearClassifierCreator<L,F>(dataset.featureIndex, dataset.labelIndex);
  }

  public static class LinearClassifierCreator<L,F> implements ClassifierCreator, ProbabilisticClassifierCreator
  {
    LogConditionalObjectiveFunction objective;
    Index<F> featureIndex;
    Index<L> labelIndex;

    public LinearClassifierCreator(LogConditionalObjectiveFunction objective, Index<F> featureIndex, Index<L> labelIndex)
    {
      this.objective = objective;
      this.featureIndex = featureIndex;
      this.labelIndex = labelIndex;
    }

    public LinearClassifierCreator(Index<F> featureIndex, Index<L> labelIndex)
    {
      this.featureIndex = featureIndex;
      this.labelIndex = labelIndex;
    }

    public LinearClassifier createLinearClassifier(double[] weights) {
      double[][] weights2D;
      if (objective != null) {
        weights2D = objective.to2D(weights);
      } else {
        weights2D = ArrayUtils.to2D(weights, featureIndex.size(), labelIndex.size());
      }
      return new LinearClassifier<L, F>(weights2D, featureIndex, labelIndex);
    }

    public Classifier createClassifier(double[] weights) {
      return createLinearClassifier(weights);
    }

    public ProbabilisticClassifier createProbabilisticClassifier(double[] weights) {
      return createLinearClassifier(weights);
    }
  }

}
