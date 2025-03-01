package edu.stanford.nlp.optimization;

import edu.stanford.nlp.classify.LogPrior;
import edu.stanford.nlp.util.Timing;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Random;

/**
 * In place Stochastic Gradient Descent Minimizer.
 * <ul>
 * <li> Follows weight decay and tuning of learning parameter of crfsgd of
 *   Leon Bottou: http://leon.bottou.org/projects/sgd
 * <li> Only support L2 regularization (QUADRATIC)
 * <li> Requires objective function to be an AbstractStochasticCachingDiffUpdateFunction.
 *   NOTE: unlike other minimizers, regularization is done in the minimizer, not the objective function
 * 
 * @author Angel Chang
 */
public class StochasticInPlaceMinimizer<T extends Function> implements Minimizer<T>, HasEvaluators {
  protected double xscale,xnorm;
  protected double[] x;
  protected int t0;  // Initial stochastic iteration count
  protected final double sigma;
  protected double lambda;
  protected boolean quiet = false;
  protected int numPasses = 50; //-1;
  protected int bSize = 1;  // NOTE: If bSize does not divide evenly into total number of samples,
                            // some samples may get accounted for twice in one pass
  protected int tuningSamples = 1000;

  protected Random gen = new Random(1);
  protected long maxTime = Long.MAX_VALUE;

  private int evaluateIters = 0;    // Evaluate every x iterations (0 = no evaluation)
  private Evaluator[] evaluators;  // separate set of evaluators to check how optimization is going


  public StochasticInPlaceMinimizer(double sigma, int numPasses)
  {
    this(sigma, numPasses, -1);
  }

  public StochasticInPlaceMinimizer(double sigma, int numPasses, int tuningSamples)
  {
    this.sigma = sigma;
    if (numPasses >= 0) {
      this.numPasses = numPasses;
    } else {
      sayln("  StochasticInPlaceMinimizer: numPasses=" + numPasses + ", defaulting to " + this.numPasses);
    }
    if (tuningSamples > 0) {
      this.tuningSamples = tuningSamples;
    } else {
      sayln("  StochasticInPlaceMinimizer: tuneSampleSize=" + tuningSamples + ", defaulting to " + this.tuningSamples);
    }
  }

  public StochasticInPlaceMinimizer(LogPrior prior, int numPasses, int batchSize, int tuningSamples)
  {
    if (LogPrior.LogPriorType.QUADRATIC == prior.getType()) {
      sigma = prior.getSigma();
    } else {
      throw new RuntimeException("Unsupported prior type " + prior.getType());
    }
    if (numPasses >= 0) {
      this.numPasses = numPasses;
    } else {
      sayln("  StochasticInPlaceMinimizer: numPasses=" + numPasses + ", defaulting to " + this.numPasses);
    }
    this.bSize = batchSize;
    if (tuningSamples > 0) {
      this.tuningSamples = tuningSamples;
    } else {
      sayln("  StochasticInPlaceMinimizer: tuneSampleSize=" + tuningSamples + ", defaulting to " + this.tuningSamples);
    }
  }

  public void shutUp() {
    this.quiet = true;
  }

  private static NumberFormat nf = new DecimalFormat("0.000E0");

  protected String getName() {
    return "SGD_InPlace_b" + bSize + "_lambda" + nf.format(lambda);
  }

  public void setEvaluators(int iters, Evaluator[] evaluators)
  {
    this.evaluateIters = iters;
    this.evaluators = evaluators;
  }

  public static class InvalidElementException extends Throwable {
    /**
     *
     */
    private static final long serialVersionUID = 1647150702529757545L;

    public InvalidElementException(String s) {
      super(s);
    }
  }

  private static void ensureFinite(double[] vect, String name) throws InvalidElementException {
    for(int i=0;i<vect.length;i++){
      if(Double.isNaN(vect[i])){
        throw new InvalidElementException("NAN found in " + name + " element " + i);
      }else if(Double.isInfinite(vect[i])){
        throw new InvalidElementException("Infinity found in " + name + " element " + i);
      }
    }
  }

  //This can be filled if an extending class needs to initialize things.
  protected void init(AbstractStochasticCachingDiffUpdateFunction func){
  }

  public double getObjective(AbstractStochasticCachingDiffUpdateFunction function, double[] w, double wscale, int[] sample)
  {
    double wnorm = getNorm(w) * wscale*wscale;
    double obj = function.valueAt(w,wscale,sample);
    // Calculate objective with L2 regularization
    return obj + 0.5*sample.length*lambda*wnorm;
  }

  public double tryEta(AbstractStochasticCachingDiffUpdateFunction function, double[] initial, int[] sample, double eta)
  {
    int numBatches =  sample.length / bSize;
    double[] w = new double[initial.length];
    double wscale = 1;
    double obj = 0;
    System.arraycopy(initial, 0, w, 0, w.length);
    int[] sampleBatch = new int[bSize];
    int sampleIndex = 0;
    for (int batch = 0; batch < numBatches; batch++) {
      for (int i = 0; i < bSize; i++) {
        sampleBatch[i] = sample[(sampleIndex + i) % sample.length];
      }
      sampleIndex += bSize;
      double gain = eta/wscale;
      function.calculateStochasticUpdate(w, wscale, sampleBatch, gain);
      wscale *= (1 - eta * lambda*bSize);
    }
    obj = getObjective(function, w, wscale, sample);
    return obj;
  }

  /**
   * Finds a good learning rate to start with.
   * eta = 1/(lambda*(t0+t)) - we find good t0
   * @param function
   * @param initial
   * @param sampleSize
   * @param seta
   */
  public double tune(AbstractStochasticCachingDiffUpdateFunction function, double[] initial, int sampleSize, double seta)
  {
    Timing timer = new Timing();
    int[] sample = function.getSample(sampleSize);
    double sobj = getObjective(function, initial, 1, sample);
    double besteta = 1;
    double bestobj = sobj;
    double eta = seta;
    int totest = 10;
    double factor = 2;
    boolean phase2 = false;
    while (totest > 0 || !phase2)
    {
      double obj = tryEta(function, initial, sample, eta);
      boolean okay = (obj < sobj);
      sayln("  Trying eta=" + eta + "  obj=" + obj + ((okay)? "(possible)":"(too large)"));
      if (okay)
      {
        totest -= 1;
        if (obj < bestobj) {
          bestobj = obj;
          besteta = eta;
        }
      }
      if (! phase2)
      {
        if (okay) {
          eta = eta * factor;
        } else {
          phase2 = true;
          eta = seta;
        }
      }
      if (phase2) {
        eta = eta / factor;
      }
    }
    // take it on the safe side (implicit regularization)
    besteta /= factor;
    // determine t
    t0 = (int) (1 / (besteta * lambda));
    sayln("  Taking eta=" + besteta + " t0=" + t0);
    sayln("  Tuning completed in: " + Timing.toSecondsString(timer.report()) + " s");
    return besteta;
  }

  // really this is the square of the L2 norm....
  private static double getNorm(double[] w)
  {
    double norm = 0;
    for (int i = 0; i < w.length; i++) {
      norm += w[i]*w[i];
    }
    return norm;
  }

  private void rescale()
  {
    if (xscale == 1) return;
    for (int i = 0; i < x.length; i++) {
      x[i] *= xscale;
    }
    xscale = 1;
  }

  private void doEvaluation(double[] x) {
    // Evaluate solution
    if (evaluators == null) return;
    for (Evaluator eval:evaluators) {
      sayln("  Evaluating: " + eval.toString());
      eval.evaluate(x);
    }
  }

  public double[] minimize(Function function, double functionTolerance, double[] initial) {
    return minimize(function, functionTolerance, initial, -1);
  }

  public double[] minimize(Function f, double functionTolerance, double[] initial, int maxIterations) {
    if (!(f instanceof AbstractStochasticCachingDiffUpdateFunction)) {
      throw new UnsupportedOperationException();
    }
    AbstractStochasticCachingDiffUpdateFunction function = (AbstractStochasticCachingDiffUpdateFunction) f;
    int totalSamples = function.dataDimension();
    int tuneSampleSize = Math.min(totalSamples, tuningSamples);
    if (tuneSampleSize < tuningSamples) {
      System.err.println("WARNING: Total number of samples=" + totalSamples +
              " is smaller than requested tuning sample size=" + tuningSamples + "!!!");
    }
    lambda = 1.0/(sigma*totalSamples);
    sayln("Using sigma=" + sigma + " lambda=" + lambda + " tuning sample size " + tuneSampleSize);
    tune(function, initial, tuneSampleSize, 0.1);

    x = new double[initial.length];
    System.arraycopy(initial, 0, x, 0, x.length);
    xscale = 1;
    xnorm = getNorm(x);
    int numBatches =  totalSamples/ bSize;

    init(function);

    boolean have_max = (maxIterations > 0 || numPasses > 0);

    if (!have_max){
      throw new UnsupportedOperationException("No maximum number of iterations has been specified.");
    } else{
      maxIterations = Math.max(maxIterations, numPasses)*numBatches;
    }

    sayln("       Batchsize of: " + bSize);
    sayln("       Data dimension of: " + totalSamples );
    sayln("       Batches per pass through data:  " + numBatches );
    sayln("       Number of passes is = " + numPasses);
    sayln("       Max iterations is = " + maxIterations);

    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    //            Loop
    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    boolean doEval = false;

    Timing total = new Timing();
    Timing current = new Timing();
    total.start();
    current.start();
    int t=t0;
    int iters = 0;
    for (int pass = 0; pass < numPasses; pass++)  {
      doEval = (pass > 0 && evaluateIters > 0 && pass % evaluateIters == 0);
      if (doEval) {
        rescale();
        doEvaluation(x);
      }

      double totalValue = 0;
      double lastValue = 0;
      say("Iter: " + iters + " pass " + pass + " batch 1 ... ");
      for (int batch = 0; batch < numBatches; batch++) {
        iters++;

        //Get the next X
        double eta = 1/(lambda*t);
        double gain = eta/xscale;
        lastValue = function.calculateStochasticUpdate(x, xscale, bSize, gain);
        totalValue += lastValue;
        // weight decay (for L2 regularization)
        xscale *= (1 - eta * lambda*bSize);
        t+=bSize;
      }
      if (xscale < 1e-6) {
        rescale();
      }
      try{
        ensureFinite(x,"x");
      }catch(InvalidElementException e){
        System.err.println(e.toString());
        for(int i=0;i<x.length;i++){ x[i]=Double.NaN; }
        break;
      }
      xnorm = getNorm(x)*xscale*xscale;
      // Calculate loss based on L2 regularization
      double loss = totalValue + 0.5 * xnorm * lambda * totalSamples;
      say(String.valueOf(numBatches));
      say("[" + ( total.report() )/1000.0 + " s " );
      say("{" + (current.restart()/1000.0) + " s}] ");
      sayln(" "+lastValue + " " + totalValue + " " + loss);

      if (iters >= maxIterations) {
        sayln("Stochastic Optimization complete.  Stopped after max iterations");
        break;
      }

      if (total.report() >= maxTime){
        sayln("Stochastic Optimization complete.  Stopped after max time");
        break;
      }

    }
    rescale();

    if (evaluateIters > 0) {
      // do final evaluation
      doEvaluation(x);
    }

    sayln("Completed in: " + Timing.toSecondsString(total.report()) + " s");

    return x;
  }

  protected void sayln(String s) {
    if (!quiet) {
      System.err.println(s);
    }
  }

  protected void say(String s) {
    if (!quiet) {
      System.err.print(s);
    }
  }

}