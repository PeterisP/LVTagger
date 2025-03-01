// CMMClassifier -- a probabilistic (CMM) Named Entity Recognizer
// Copyright (c) 2002-2006 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program has been made available for research purposes only.
// Please do not further distribute it.
// Commercial development of the software is not to be undertaken without
// prior agreement from Stanford University.
// This program is not open source nor is it in the public domain.
//
// For information contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    manning@cs.stanford.edu

package edu.stanford.nlp.ie.ner;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import edu.stanford.nlp.classify.Dataset;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.classify.LogPrior;
import edu.stanford.nlp.classify.NBLinearClassifierFactory;
import edu.stanford.nlp.classify.ProbabilisticClassifier;
import edu.stanford.nlp.classify.SVMLightClassifierFactory;
import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.NERFeatureFactory;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.Document;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.WordTag;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.GazAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.IDAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PositionAnnotation;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.process.DocumentProcessor;
import edu.stanford.nlp.process.ListProcessor;
import edu.stanford.nlp.sequences.BeamBestSequenceFinder;
import edu.stanford.nlp.sequences.Clique;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.sequences.ExactBestSequenceFinder;
import edu.stanford.nlp.sequences.FeatureFactory;
import edu.stanford.nlp.sequences.PlainTextDocumentReaderAndWriter;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.sequences.SequenceModel;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.PaddedList;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;


/**
 * Does Sequence Classification using a Conditional Markov Model.
 * It could be used for other purposes, but the provided features
 * are aimed at doing Named Entity Recognition.
 * The code has functionality for different document encodings, but when
 * using the standard <code>ColumnDocumentReader</code>,
 * input files are expected to
 * be one word per line with the columns indicating things like the word,
 * POS, chunk, and class.
 * <p/>
 * <b>Typical usage</b>
 * <p>For running a trained model with a provided serialized classifier: <p>
 * <code>
 * java -server -mx1000m edu.stanford.nlp.ie.ner.CMMClassifier -loadClassifier
 * conll.ner.gz -textFile samplesentences.txt
 * </code><p>
 * When specifying all parameters in a properties file (train, test, or
 * runtime):<p>
 * <code>
 * java -mx1000m edu.stanford.nlp.ie.ner.CMMClassifier -prop propFile
 * </code><p>
 * To train and test a model from the command line:<p>
 * <code>java -mx1000m edu.stanford.nlp.ie.ner.CMMClassifier
 * -trainFile trainFile -testFile testFile -goodCoNLL &gt; output </code>
 * <p/>
 * Features are defined by a {@link FeatureFactory}; the
 * {@link FeatureFactory} which is used by default is
 * {@link NERFeatureFactory}, and you should look there for feature templates.
 * Features are specified either by a Properties file (which is the
 * recommended method) or on the command line.  The features are read into
 * a {@link SeqClassifierFlags} object, which the
 * user need not know much about, unless one wishes to add new features.
 * <p/>
 * CMMClassifier may also be used programmatically.  When creating a new instance, you
 * <i>must</i> specify a properties file.  The other way to get a CMMClassifier is to
 * deserialize one via {@link CMMClassifier#getClassifier(String)}, which returns a
 * deserialized classifier.  You may then tag sentences using either the assorted
 * <code>test</code> or <code>testSentence</code> methods.
 *
 * @author Dan Klein
 * @author Jenny Finkel
 * @author Christopher Manning
 * @author Shipra Dingare
 * @author Huy Nguyen
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) - cleanup and filling in types
 */

@SuppressWarnings("rawtypes")
public class CMMClassifier<IN extends CoreLabel> extends AbstractSequenceClassifier<IN> implements DocumentProcessor, ListProcessor<Object, WordTag> {

  private ProbabilisticClassifier<String, String> classifier;

  /** The set of empirically legal label sequences (of length (order) at most
   *  <code>flags.maxLeft</code>).  Used to filter valid class sequences if
   *  <code>useObuseObservedSequencesOnly</code> is set.
   */
  Set<List<String>> answerArrays;

  /** Default place to look in Jar file for classifier. */
  public static final String DEFAULT_CLASSIFIER = "/classifiers/ner-eng-ie.cmm-3-all2006.ser.gz";

  protected CMMClassifier() {
    super(new SeqClassifierFlags());
  }

  public CMMClassifier(Properties props) {
    super(props);
  }


  /**
   * Returns the Set of entities recognized by this Classifier.
   *
   * @return The Set of entities recognized by this Classifier.
   */
  public Set<String> getTags() {
    Set<String> tags = new HashSet<String>(classIndex.objectsList());
    tags.remove(flags.backgroundSymbol);
    return tags;
  }

  /**
   * Classify a {@link List} of {@link CoreLabel}s.
   *
   * @param document A {@link List} of {@link CoreLabel}s
   *                 to be classified.
   */
  @Override
  public List<IN> classify(List<IN> document) {
    if (flags.useSequences) {
      classifySeq(document);
    } else {
      classifyNoSeq(document);
    }
    return document;
  }

  /**
   * Classify a List of {@link CoreLabel}s without using sequence information
   * (i.e. no Viterbi algorithm, just distribution over next class).
   *
   * @param document a List of {@link CoreLabel}s to be classified
   */
  private void classifyNoSeq(List<IN> document) {
    if (flags.useReverse) {
      Collections.reverse(document);
    }

    if (flags.lowerNewgeneThreshold) {
      // Used to raise recall for task 1B
      System.err.println("Using NEWGENE threshold: " + flags.newgeneThreshold);
      for (int i = 0, docSize = document.size(); i < docSize; i++) {
        CoreLabel wordInfo = document.get(i);
        Datum<String, String> d = makeDatum(document, i, featureFactory);
        Counter<String> scores = classifier.scoresOf(d);
        //String answer = BACKGROUND;
        String answer = flags.backgroundSymbol;
        // HN: The evaluation of scoresOf seems to result in some
        // kind of side effect.  Specifically, the symptom is that
        // if scoresOf is not evaluated at every position, the
        // answers are different
        if ("NEWGENE".equals(wordInfo.get(GazAnnotation.class))) {
          for (String label : scores.keySet()) {
            if ("G".equals(label)) {
              System.err.println(wordInfo.word() + ':' + scores.getCount(label));
              if (scores.getCount(label) > flags.newgeneThreshold) {
                answer = label;
              }
            }
          }
        }
        wordInfo.set(AnswerAnnotation.class, answer);
      }
    } else {
      for (int i = 0, listSize = document.size(); i < listSize; i++) {
        String answer = classOf(document, i);
        CoreLabel wordInfo = document.get(i);
        //System.err.println("XXX answer for " +
        //        wordInfo.word() + " is " + answer);
        wordInfo.set(AnswerAnnotation.class, answer);
      }
      if (flags.justify && (classifier instanceof LinearClassifier)) {
        LinearClassifier<String, String> lc = (LinearClassifier<String, String>) classifier;
        for (int i = 0, lsize = document.size(); i < lsize; i++) {
          CoreLabel lineInfo = document.get(i);
          System.err.print("@@ Position " + i + ": ");
          System.err.println(lineInfo.word() + " chose " + lineInfo.get(AnswerAnnotation.class));
          lc.justificationOf(makeDatum(document, i, featureFactory));
        }
      }
    }
    if (flags.useReverse) {
      Collections.reverse(document);
    }
  }

  /**
   * Returns the most likely class for the word at the given position.
   */
  protected String classOf(List<IN> lineInfos, int pos) {
    Datum<String, String> d = makeDatum(lineInfos, pos, featureFactory);
    return classifier.classOf(d);
  }

  /**
   * Returns the log conditional likelihood of the given dataset.
   *
   * @return The log conditional likelihood of the given dataset.
   */
  public double loglikelihood(List<IN> lineInfos) {
    double cll = 0.0;

    for (int i = 0; i < lineInfos.size(); i++) {
      Datum<String, String> d = makeDatum(lineInfos, i, featureFactory);
      Counter<String> c = classifier.logProbabilityOf(d);

      double total = Double.NEGATIVE_INFINITY;
      for (String s : c.keySet()) {
        total = SloppyMath.logAdd(total, c.getCount(s));
      }
      cll -= c.getCount(d.label()) - total;
    }
    // quadratic prior
    // HN: TODO: add other priors

    if (classifier instanceof LinearClassifier) {
      double sigmaSq = flags.sigma * flags.sigma;
      LinearClassifier<String, String> lc = (LinearClassifier<String, String>)classifier;
      for (String feature: lc.features()) {
        for (String classLabel: classIndex) {
          double w = lc.weight(feature, classLabel);
          cll += w * w / 2.0 / sigmaSq;
        }
      }
    }
    return cll;
  }

  @Override
  public SequenceModel getSequenceModel(List<IN> document) {
    //System.err.println(flags.useReverse);

    if (flags.useReverse) {
      Collections.reverse(document);
    }

    // cdm Aug 2005: why is this next line needed?  Seems really ugly!!!  [2006: it broke things! removed]
    // document.add(0, new CoreLabel());

    SequenceModel ts = new Scorer(document,
                                  classIndex,
                                  this,
                                  (!flags.useTaggySequences ? (flags.usePrevSequences ? 1 : 0) : flags.maxLeft),
                                  (flags.useNextSequences ? 1 : 0),
                                  answerArrays);

    return ts;
  }

  /**
   * Classify a List of {@link CoreLabel}s using sequence information
   * (i.e. Viterbi or Beam Search).
   *
   * @param document A List of {@link CoreLabel}s to be classified
   */
  private void classifySeq(List<IN> document) {

    if (document.isEmpty()) {
      return;
    }

    SequenceModel ts = getSequenceModel(document);

    //    TagScorer ts = new PrevOnlyScorer(document, tagIndex, this, (!flags.useTaggySequences ? (flags.usePrevSequences ? 1 : 0) : flags.maxLeft), 0, answerArrays);

    int[] tags;
    //System.err.println("***begin test***");
    if (flags.useViterbi) {
      ExactBestSequenceFinder ti = new ExactBestSequenceFinder();
      tags = ti.bestSequence(ts);
    } else {
      BeamBestSequenceFinder ti = new BeamBestSequenceFinder(flags.beamSize, true, true);
      tags = ti.bestSequence(ts, document.size());
    }
    //System.err.println("***end test***");

    // used to improve recall in task 1b
    if (flags.lowerNewgeneThreshold) {
      System.err.println("Using NEWGENE threshold: " + flags.newgeneThreshold);

      int[] copy = new int[tags.length];
      System.arraycopy(tags, 0, copy, 0, tags.length);

      // for each sequence marked as NEWGENE in the gazette
      // tag the entire sequence as NEWGENE and sum the score
      // if the score is greater than newgeneThreshold, accept
      int ngTag = classIndex.indexOf("G");
      //int bgTag = classIndex.indexOf(BACKGROUND);
      int bgTag = classIndex.indexOf(flags.backgroundSymbol);

      for (int i = 0, dSize = document.size(); i < dSize; i++) {
        CoreLabel wordInfo =document.get(i);

        if ("NEWGENE".equals(wordInfo.get(GazAnnotation.class))) {
          int start = i;
          int j;
          for (j = i; j < document.size(); j++) {
            wordInfo = document.get(j);
            if (!"NEWGENE".equals(wordInfo.get(GazAnnotation.class))) {
              break;
            }
          }
          int end = j;
          //int end = i + 1;

          int winStart = Math.max(0, start - 4);
          int winEnd = Math.min(tags.length, end + 4);
          // clear a window around the sequences
          for (j = winStart; j < winEnd; j++) {
            copy[j] = bgTag;
          }

          // score as nongene
          double bgScore = 0.0;
          for (j = start; j < end; j++) {
            double[] scores = ts.scoresOf(copy, j);
            scores = Scorer.recenter(scores);
            bgScore += scores[bgTag];
          }

          // first pass, compute all of the scores
          ClassicCounter<Pair<Integer,Integer>> prevScores = new ClassicCounter<Pair<Integer,Integer>>();
          for (j = start; j < end; j++) {
            // clear the sequence
            for (int k = start; k < end; k++) {
              copy[k] = bgTag;
            }

            // grow the sequence from j until the end
            for (int k = j; k < end; k++) {
              copy[k] = ngTag;
              // score the sequence
              double ngScore = 0.0;
              for (int m = start; m < end; m++) {
                double[] scores = ts.scoresOf(copy, m);
                scores = Scorer.recenter(scores);
                ngScore += scores[tags[m]];
              }
              prevScores.incrementCount(new Pair<Integer,Integer>(Integer.valueOf(j), Integer.valueOf(k)), ngScore - bgScore);
            }
          }
          for (j = start; j < end; j++) {
            // grow the sequence from j until the end
            for (int k = j; k < end; k++) {
              double score = prevScores.getCount(new Pair<Integer,Integer>(Integer.valueOf(j), Integer.valueOf(k)));
              Pair<Integer, Integer> al = new Pair<Integer,Integer>(Integer.valueOf(j - 1), Integer.valueOf(k)); // adding a word to the left
              Pair<Integer, Integer> ar = new Pair<Integer,Integer>(Integer.valueOf(j), Integer.valueOf(k + 1)); // adding a word to the right
              Pair<Integer, Integer> sl = new Pair<Integer,Integer>(Integer.valueOf(j + 1), Integer.valueOf(k)); // subtracting word from left
              Pair<Integer, Integer> sr = new Pair<Integer,Integer>(Integer.valueOf(j), Integer.valueOf(k - 1)); // subtracting word from right

              // make sure the score is greater than all its neighbors (one add or subtract)
              if (score >= flags.newgeneThreshold && (!prevScores.containsKey(al) || score > prevScores.getCount(al)) && (!prevScores.containsKey(ar) || score > prevScores.getCount(ar)) && (!prevScores.containsKey(sl) || score > prevScores.getCount(sl)) && (!prevScores.containsKey(sr) || score > prevScores.getCount(sr))) {
                StringBuilder sb = new StringBuilder();
                wordInfo = document.get(j);
                String docId = wordInfo.get(IDAnnotation.class);
                String startIndex = wordInfo.get(PositionAnnotation.class);
                wordInfo = document.get(k);
                String endIndex = wordInfo.get(PositionAnnotation.class);
                for (int m = j; m <= k; m++) {
                  wordInfo = document.get(m);
                  sb.append(wordInfo.word());
                  sb.append(' ');
                }
                /*System.err.println(sb.toString()+"score:"+score+
                  " al:"+prevScores.getCount(al)+
                  " ar:"+prevScores.getCount(ar)+
                  "  sl:"+prevScores.getCount(sl)+" sr:"+ prevScores.getCount(sr));*/
                System.out.println(docId + '|' + startIndex + ' ' + endIndex + '|' + sb.toString().trim());
              }
            }
          }

          // restore the original tags
          for (j = winStart; j < winEnd; j++) {
            copy[j] = tags[j];
          }
          i = end;
        }
      }
    }

    for (int i = 0, docSize = document.size(); i < docSize; i++) {
      CoreLabel lineInfo = document.get(i);
      String answer = classIndex.get(tags[i]);
      lineInfo.set(AnswerAnnotation.class, answer);
    }

    if (flags.justify && classifier instanceof LinearClassifier) {
      LinearClassifier<String, String> lc = (LinearClassifier<String, String>) classifier;
      if (flags.dump) {
        lc.dump();
      }
      for (int i = 0, docSize = document.size(); i < docSize; i++) {
        CoreLabel lineInfo = document.get(i);
        System.err.print("@@ Position is: " + i + ": ");
        System.err.println(lineInfo.word() + ' ' + lineInfo.get(AnswerAnnotation.class));
        lc.justificationOf(makeDatum(document, i, featureFactory));
      }
    }

//    document.remove(0);

    if (flags.useReverse) {
      Collections.reverse(document);

    }
  } // end testSeq


  /**
   * @param filename adaptation file
   * @param trainDataset original dataset (used in training)
   */
  public void adapt(String filename, Dataset<String, String> trainDataset,
                    DocumentReaderAndWriter<IN> readerWriter) {
    flags.ocrTrain = false;  // ?? Do we need this? (Pi-Chuan Sat Nov  5 15:42:49 2005)
    ObjectBank<List<IN>> docs =
      makeObjectBankFromFile(filename, readerWriter);
    adapt(docs, trainDataset);
  }

  /**
   * @param featureLabels adaptation docs
   * @param trainDataset original dataset (used in training)
   */
  public void adapt(ObjectBank<List<IN>> featureLabels, Dataset<String, String> trainDataset) {
    Dataset<String, String> adapt = getDataset(featureLabels, trainDataset);
    adapt(adapt);
  }

  /**
   * @param featureLabels retrain docs
   * @param featureIndex featureIndex of original dataset (used in training)
   * @param labelIndex labelIndex of original dataset (used in training)
   */
  public void retrain(ObjectBank<List<IN>> featureLabels, Index<String> featureIndex, Index<String> labelIndex) {
    int fs = featureIndex.size(); // old dim
    int ls = labelIndex.size();   // old dim

    Dataset<String, String> adapt = getDataset(featureLabels, featureIndex, labelIndex);

    int prior = LogPrior.LogPriorType.QUADRATIC.ordinal();
    LinearClassifier<String, String> lc = (LinearClassifier<String, String>) classifier;
    LinearClassifierFactory<String, String> lcf = new LinearClassifierFactory<String, String>(flags.tolerance, flags.useSum, prior, flags.sigma, flags.epsilon, flags.QNsize);

    double[][] weights = lc.weights();  // old dim
    Index<String> newF = adapt.featureIndex;
    Index<String> newL = adapt.labelIndex;
    int newFS = newF.size();
    int newLS = newL.size();
    double[] x = new double[newFS*newLS]; // new dim
    //System.err.println("old  ["+fs+"]"+"["+ls+"]");
    //System.err.println("new  ["+newFS+"]"+"["+newLS+"]");
    //System.err.println("new  ["+newFS*newLS+"]");
    for (int i = 0; i < fs; i++) {
      for (int j = 0; j < ls; j++) {
        String f = featureIndex.get(i);
        String l = labelIndex.get(j);
        int newi = newF.indexOf(f)*newLS+newL.indexOf(l);
        x[newi] = weights[i][j];
        //if (newi == 144745*2) {
        //System.err.println("What??"+i+"\t"+j);
        //}
      }
    }
    //System.err.println("x[144745*2]"+x[144745*2]);
    weights = lcf.trainWeights(adapt, x);
    //System.err.println("x[144745*2]"+x[144745*2]);
    //System.err.println("weights[144745]"+"[0]="+weights[144745][0]);

    lc.setWeights(weights);
    /*
    int delme = 0;
    if (true) {
      for (double[] dd : weights) {
        delme++;
        for (double d : dd) {
        }
      }
    }
    System.err.println(weights[delme-1][0]);
    System.err.println("size of weights: "+delme);
    */
  }


  public void retrain(ObjectBank<List<IN>> doc) {
    if (classifier == null) {
      System.err.println("Cannot retrain before you train!");
      System.exit(-1);
    }
    Index<String> findex = ((LinearClassifier<String, String>)classifier).featureIndex();
    Index<String> lindex = ((LinearClassifier<String, String>)classifier).labelIndex();
    System.err.println("Starting retrain:\t# of original features"+findex.size()+", # of original labels"+lindex.size());
    retrain(doc, findex, lindex);
  }


  @Override
  public void train(Collection<List<IN>> wordInfos,
                    DocumentReaderAndWriter<IN> readerAndWriter) {
    Dataset<String, String> train = getDataset(wordInfos);
    //train.summaryStatistics();
    //train.printSVMLightFormat();
    // wordInfos = null;  // cdm: I think this does no good as ptr exists in caller (could empty the list or better refactor so conversion done earlier?)
    train(train);

    for (int i = 0; i < flags.numTimesPruneFeatures; i++) {

      Index<String> featuresAboveThreshhold = getFeaturesAboveThreshhold(train, flags.featureDiffThresh);
      System.err.println("Removing features with weight below " + flags.featureDiffThresh + " and retraining...");
      train = getDataset(train, featuresAboveThreshhold);

      int tmp = flags.QNsize;
      flags.QNsize = flags.QNsize2;
      train(train);
      flags.QNsize = tmp;
    }

    if (flags.doAdaptation && flags.adaptFile != null) {
      adapt(flags.adaptFile,train,readerAndWriter);
    }

    System.err.print("Built this classifier: ");
    if (classifier instanceof LinearClassifier) {
      String classString = ((LinearClassifier<String, String>)classifier).toString(flags.printClassifier, flags.printClassifierParam);
      System.err.println(classString);
    } else {
      String classString = classifier.toString();
      System.err.println(classString);
    }
  }

  public Index<String> getFeaturesAboveThreshhold(Dataset<String, String> dataset, double thresh) {
    if (!(classifier instanceof LinearClassifier)) {
      throw new RuntimeException("Attempting to remove features based on weight from a non-linear classifier");
    }
    Index<String> featureIndex = dataset.featureIndex;
    Index<String> labelIndex = dataset.labelIndex;

    Index<String> features = new HashIndex<String>();
    Iterator<String> featureIt = featureIndex.iterator();
    LinearClassifier<String, String> lc = (LinearClassifier<String, String>)classifier;
    LOOP:
    while (featureIt.hasNext()) {
      String f = featureIt.next();
      Iterator<String> labelIt = labelIndex.iterator();
      double smallest = Double.POSITIVE_INFINITY;
      double biggest = Double.NEGATIVE_INFINITY;
      while (labelIt.hasNext()) {
        String l = labelIt.next();
        double weight = lc.weight(f, l);
        if (weight < smallest) {
          smallest = weight;
        }
        if (weight > biggest) {
          biggest = weight;
        }
        if (biggest - smallest > thresh) {
          features.add(f);
          continue LOOP;
        }
      }
    }
    return features;
  }

  /**
   * Build a Dataset from some data. Used for training a classifier.
   *
   * @param data This variable is a list of lists of CoreLabel.  That is,
   *             it is a collection of documents, each of which is represented
   *             as a sequence of CoreLabel objects.
   * @return The Dataset which is an efficient encoding of the information
   *         in a List of Datums
   */
  public Dataset<String, String> getDataset(Collection<List<IN>> data) {
    return getDataset(data, null, null);
  }

  /**
   * Build a Dataset from some data. Used for training a classifier.
   *
   * By passing in extra featureIndex and classIndex, you can get a Dataset based on featureIndex and
   * classIndex
   *
   * @param data This variable is a list of lists of CoreLabel.  That is,
   *             it is a collection of documents, each of which is represented
   *             as a sequence of CoreLabel objects.
   * @param classIndex if you want to get a Dataset based on featureIndex and
   *                    classIndex in an existing origDataset
   * @return The Dataset which is an efficient encoding of the information
   *         in a List of Datums
   */
  public Dataset<String, String> getDataset(Collection<List<IN>> data, Index<String> featureIndex, Index<String> classIndex) {
    makeAnswerArraysAndTagIndex(data);

    int size = 0;
    for (List<IN> doc : data) {
      size += doc.size();
    }

    System.err.println("Making Dataset...");
    Dataset<String, String> train;
    if (featureIndex != null && classIndex != null) {
      System.err.println("Using feature/class Index from existing Dataset...");
      System.err.println("(This is used when getting Dataset from adaptation set. We want to make the index consistent.)"); //pichuan
      train = new Dataset<String, String>(size, featureIndex, classIndex);
    } else {
      train = new Dataset<String, String>(size);
    }

    for (List<IN> doc : data) {
      if (flags.useReverse) {
        Collections.reverse(doc);
      }

      for (int i = 0, dsize = doc.size(); i < dsize; i++) {
        Datum<String, String> d = makeDatum(doc, i, featureFactory);

        //CoreLabel fl = doc.get(i);

        train.add(d);
      }

      if (flags.useReverse) {
        Collections.reverse(doc);
      }
    }

    System.err.println("done.");
    // reset printing before test data
    // what is this???? -JRF
//     if (featureFactory instanceof FeatureFactory) {
//       ((FeatureFactory) featureFactory).resetPrintFeatures();
//     }

    if (flags.featThreshFile != null) {
      System.err.println("applying thresholds...");
      List<Pair<Pattern, Integer>> thresh = getThresholds(flags.featThreshFile);
      train.applyFeatureCountThreshold(thresh);
    } else if (flags.featureThreshold > 1) {
      System.err.println("Removing Features with counts < " + flags.featureThreshold);
      train.applyFeatureCountThreshold(flags.featureThreshold);
    }
    train.summaryStatistics();
    return train;
  }

  public Dataset<String, String> getBiasedDataset(ObjectBank<List<IN>> data, Index<String> featureIndex, Index<String> classIndex) {
    makeAnswerArraysAndTagIndex(data);

    Index<String> origFeatIndex = new HashIndex<String>(featureIndex.objectsList()); // mg2009: TODO: check

    int size = 0;
    for (List<IN> doc : data) {
      size += doc.size();
    }

    System.err.println("Making Dataset...");
    Dataset<String, String> train = new Dataset<String, String>(size, featureIndex, classIndex);

    for (List<IN> doc : data) {
      if (flags.useReverse) {
        Collections.reverse(doc);
      }

      for (int i = 0, dsize = doc.size(); i < dsize; i++) {
        Datum<String, String> d = makeDatum(doc, i, featureFactory);
        Collection<String> newFeats = new ArrayList<String>();
        for (String f : d.asFeatures()) {
          if ( ! origFeatIndex.contains(f)) {
            newFeats.add(f);
          }
        }
//        System.err.println(d.label()+"\t"+d.asFeatures()+"\n\t"+newFeats);
//        d = new BasicDatum(newFeats, d.label());
        train.add(d);
      }

      if (flags.useReverse) {
        Collections.reverse(doc);
      }
    }

    System.err.println("done.");
    // reset printing before test data
    // what is this???? -JRF
//     if (featureFactory instanceof FeatureFactory) {
//       ((FeatureFactory) featureFactory).resetPrintFeatures();
//     }

    if (flags.featThreshFile != null) {
      System.err.println("applying thresholds...");
      List<Pair<Pattern, Integer>> thresh = getThresholds(flags.featThreshFile);
      train.applyFeatureCountThreshold(thresh);
    } else if (flags.featureThreshold > 1) {
      System.err.println("Removing Features with counts < " + flags.featureThreshold);
      train.applyFeatureCountThreshold(flags.featureThreshold);
    }
    train.summaryStatistics();
    return train;
  }




  /**
   * Build a Dataset from some data. Used for training a classifier.
   *
   * By passing in an extra origDataset, you can get a Dataset based on featureIndex and
   * classIndex in an existing origDataset.
   *
   * @param data This variable is a list of lists of CoreLabel.  That is,
   *             it is a collection of documents, each of which is represented
   *             as a sequence of CoreLabel objects.
   * @param origDataset if you want to get a Dataset based on featureIndex and
   *                    classIndex in an existing origDataset
   * @return The Dataset which is an efficient encoding of the information
   *         in a List of Datums
   */
  public Dataset<String, String> getDataset(ObjectBank<List<IN>> data, Dataset<String, String> origDataset) {
    if(origDataset == null) {
      return getDataset(data);
    }
    return getDataset(data, origDataset.featureIndex, origDataset.labelIndex);
  }


  /**
   * Build a Dataset from some data.
   *
   * @param oldData      This {@link Dataset} represents data for which we which to
   *                     some features, specifically those features not in the {@link edu.stanford.nlp.util.Index}
   *                     goodFeatures.
   * @param goodFeatures An {@link edu.stanford.nlp.util.Index} of features we wish to retain.
   * @return A new {@link Dataset} wheres each datapoint contains only features
   *         which were in goodFeatures.
   */

  public Dataset<String, String> getDataset(Dataset<String, String> oldData, Index<String> goodFeatures) {
    //public Dataset getDataset(List data, Collection goodFeatures) {
    //makeAnswerArraysAndTagIndex(data);

    int[][] oldDataArray = oldData.getDataArray();
    int[] oldLabelArray = oldData.getLabelsArray();
    Index<String> oldFeatureIndex = oldData.featureIndex;

    int[] oldToNewFeatureMap = new int[oldFeatureIndex.size()];

    int[][] newDataArray = new int[oldDataArray.length][];

    System.err.print("Building reduced dataset...");

    int size = oldFeatureIndex.size();
    int max = 0;
    for (int i = 0; i < size; i++) {
      oldToNewFeatureMap[i] = goodFeatures.indexOf(oldFeatureIndex.get(i));
      if (oldToNewFeatureMap[i] > max) {
        max = oldToNewFeatureMap[i];
      }
    }

    for (int i = 0; i < oldDataArray.length; i++) {
      int[] data = oldDataArray[i];
      size = 0;
      for (int j = 0; j < data.length; j++) {
        if (oldToNewFeatureMap[data[j]] > 0) {
          size++;
        }
      }
      int[] newData = new int[size];
      int index = 0;
      for (int j = 0; j < data.length; j++) {
        int f = oldToNewFeatureMap[data[j]];
        if (f > 0) {
          newData[index++] = f;
        }
      }
      newDataArray[i] = newData;
    }

    Dataset<String, String> train = new Dataset<String, String>(oldData.labelIndex, oldLabelArray, goodFeatures, newDataArray, newDataArray.length);

    System.err.println("done.");
    if (flags.featThreshFile != null) {
      System.err.println("applying thresholds...");
      List<Pair<Pattern,Integer>> thresh = getThresholds(flags.featThreshFile);
      train.applyFeatureCountThreshold(thresh);
    } else if (flags.featureThreshold > 1) {
      System.err.println("Removing Features with counts < " + flags.featureThreshold);
      train.applyFeatureCountThreshold(flags.featureThreshold);
    }
    train.summaryStatistics();
    return train;
  }

  private void adapt(Dataset<String, String> adapt) {
    if (flags.classifierType.equalsIgnoreCase("SVM")) {
      throw new UnsupportedOperationException();
    }
    adaptMaxEnt(adapt);
  }

  private void adaptMaxEnt(Dataset<String, String> adapt) {
    if (classifier instanceof LinearClassifier) {
      // So far the adaptation is only done on Gaussian Prior. Haven't checked how it'll work on other kinds of priors. -pichuan
      int prior = LogPrior.LogPriorType.QUADRATIC.ordinal();
      if (flags.useHuber) {
        throw new UnsupportedOperationException();
      } else if (flags.useQuartic) {
        throw new UnsupportedOperationException();
      }

      LinearClassifierFactory<String, String> lcf = new LinearClassifierFactory<String, String>(flags.tolerance, flags.useSum, prior, flags.adaptSigma, flags.epsilon, flags.QNsize);
      ((LinearClassifier<String, String>)classifier).adaptWeights(adapt,lcf);
    } else {
      throw new UnsupportedOperationException();
    }
  }

  private void train(Dataset<String, String> train) {
    if (flags.classifierType.equalsIgnoreCase("SVM")) {
      trainSVM(train);
    } else {
      trainMaxEnt(train);
    }
  }

  private void trainSVM(Dataset<String, String> train) {
    SVMLightClassifierFactory<String, String> fact = new SVMLightClassifierFactory<String, String>();
    classifier = fact.trainClassifier(train);

  }

  private void trainMaxEnt(Dataset<String, String> train) {
    int prior = LogPrior.LogPriorType.QUADRATIC.ordinal();
    if (flags.useHuber) {
      prior = LogPrior.LogPriorType.HUBER.ordinal();
    } else if (flags.useQuartic) {
      prior = LogPrior.LogPriorType.QUARTIC.ordinal();
    }

    LinearClassifier<String, String> lc;
    if (flags.useNB) {
      lc = new NBLinearClassifierFactory<String, String>(flags.sigma).trainClassifier(train);
    } else {
      LinearClassifierFactory<String, String> lcf = new LinearClassifierFactory<String, String>(flags.tolerance, flags.useSum, prior, flags.sigma, flags.epsilon, flags.QNsize);
      if (flags.useQN) {
        lcf.useQuasiNewton(flags.useRobustQN);
      } else if(flags.useStochasticQN) {
        lcf.useStochasticQN(flags.initialGain,flags.stochasticBatchSize);
      } else if(flags.useSMD) {
        lcf.useStochasticMetaDescent(flags.initialGain, flags.stochasticBatchSize,flags.stochasticMethod,flags.SGDPasses);
      } else if(flags.useSGD) {
        lcf.useStochasticGradientDescent(flags.gainSGD,flags.stochasticBatchSize);
      } else if(flags.useSGDtoQN) {
        lcf.useStochasticGradientDescentToQuasiNewton(flags.initialGain, flags.stochasticBatchSize,
                                       flags.SGDPasses, flags.QNPasses, flags.SGD2QNhessSamples,
                                       flags.QNsize, flags.outputIterationsToFile);
      } else if(flags.useHybrid) {
        lcf.useHybridMinimizer(flags.initialGain, flags.stochasticBatchSize ,flags.stochasticMethod ,flags.hybridCutoffIteration );
      } else {
        lcf.useConjugateGradientAscent();
      }
      lc = lcf.trainClassifier(train);
    }
    this.classifier = lc;
  }

  private void trainSemiSup(Dataset<String, String> data, Dataset<String, String> biasedData, double[][] confusionMatrix) {
    int prior = LogPrior.LogPriorType.QUADRATIC.ordinal();
    if (flags.useHuber) {
      prior = LogPrior.LogPriorType.HUBER.ordinal();
    } else if (flags.useQuartic) {
      prior = LogPrior.LogPriorType.QUARTIC.ordinal();
    }

    LinearClassifierFactory<String, String> lcf;
    lcf = new LinearClassifierFactory<String, String>(flags.tolerance, flags.useSum, prior, flags.sigma, flags.epsilon, flags.QNsize);
    if (flags.useQN) {
      lcf.useQuasiNewton();
    } else{
      lcf.useConjugateGradientAscent();
    }

    this.classifier = (LinearClassifier<String, String>) lcf.trainClassifierSemiSup(data, biasedData, confusionMatrix, null);
  }


//   public void crossValidateTrainAndTest() throws Exception {
//     crossValidateTrainAndTest(flags.trainFile);
//   }

//   public void crossValidateTrainAndTest(String filename) throws Exception {
//     // wordshapes

//     for (int fold = flags.startFold; fold <= flags.endFold; fold++) {
//       System.err.println("fold " + fold + " of " + flags.endFold);
//       // train

//       List = makeObjectBank(filename);
//       List folds = split(data, flags.numFolds);
//       data = null;

//       List train = new ArrayList();

//       for (int i = 0; i < flags.numFolds; i++) {
//         List docs = (List) folds.get(i);
//         if (i != fold) {
//           train.addAll(docs);
//         }
//       }
//       folds = null;
//       train(train);
//       train = null;

//       List test = new ArrayList();
//       data = makeObjectBank(filename);
//       folds = split(data, flags.numFolds);
//       data = null;

//       for (int i = 0; i < flags.numFolds; i++) {
//         List docs = (List) folds.get(i);
//         if (i == fold) {
//           test.addAll(docs);
//         }
//       }
//       folds = null;
//       // test
//       test(test);
//       writeAnswers(test);
//     }
//   }

//   /**
//    * Splits the given train corpus into a train and a test corpus based on the fold number.
//    * 1 / numFolds documents are held out for test, with the offset determined by the fold number.
//    *
//    * @param data     The original data
//    * @param numFolds The number of folds to split the data into
//    * @return A list of folds giving the new training set
//    */
//   private List split(List data, int numFolds) {
//     List folds = new ArrayList();
//     int foldSize = data.size() / numFolds;
//     int r = data.size() - (numFolds * foldSize);

//     int index = 0;
//     for (int i = 0; i < numFolds; i++) {
//       List fold = new ArrayList();
//       int end = (i < r ? foldSize + 1 : foldSize);
//       for (int j = 0; j < end; j++) {
//         fold.add(data.get(index++));
//       }
//       folds.add(fold);
//     }

//     return folds;
//   }

  @Override
  public void serializeClassifier(String serializePath) {

    System.err.print("Serializing classifier to " + serializePath + "...");

    try {
      ObjectOutputStream oos = IOUtils.writeStreamFromString(serializePath);

      oos.writeObject(classifier);
      oos.writeObject(flags);
      oos.writeObject(featureFactory);
      oos.writeObject(classIndex);
      oos.writeObject(answerArrays);
      //oos.writeObject(WordShapeClassifier.getKnownLowerCaseWords());

      oos.writeObject(knownLCWords);

      oos.close();
      System.err.println("Done.");

    } catch (Exception e) {
      System.err.println("Error serializing to " + serializePath);
      e.printStackTrace();
      // dont actually exit in case they're testing too
      //System.exit(1);
    }
  }


  /**
   * Used to load the default supplied classifier.  **THIS FUNCTION
   * WILL ONLY WORK IF RUN INSIDE A JAR FILE**
   */
  public void loadDefaultClassifier() {
    loadJarClassifier(DEFAULT_CLASSIFIER, null);
  }

  /**
   * Used to obtain the default classifier which is
   * stored inside a jar file.  <i>THIS FUNCTION
   * WILL ONLY WORK IF RUN INSIDE A JAR FILE.</i>
   *
   * @return A Default CMMClassifier from a jar file
   */
  public static CMMClassifier getDefaultClassifier() {

    CMMClassifier cmm = new CMMClassifier();
    cmm.loadDefaultClassifier();
    return cmm;

  }

  /** Load a classifier from the given Stream.
   *  <i>Implementation note: </i> This method <i>does not</i> close the
   *  Stream that it reads from.
   *
   *  @param ois The ObjectInputStream to load the serialized classifier from
   *
   *  @throws IOException If there are problems accessing the input stream
   *  @throws ClassCastException If there are problems interpreting the serialized data
   *  @throws ClassNotFoundException If there are problems interpreting the serialized data

   *  */
  @SuppressWarnings("unchecked")
  @Override
  public void loadClassifier(ObjectInputStream ois, Properties props) throws ClassCastException, IOException, ClassNotFoundException {
    classifier = (LinearClassifier<String, String>) ois.readObject();
    flags = (SeqClassifierFlags) ois.readObject();
    featureFactory = (FeatureFactory) ois.readObject();

    if (props != null) {
      flags.setProperties(props);
    }
    reinit();

    classIndex = (Index<String>) ois.readObject();
    answerArrays = (Set<List<String>>) ois.readObject();

    knownLCWords = (Set<String>) ois.readObject();
  }


  public static CMMClassifier getClassifierNoExceptions(File file) {
    CMMClassifier cmm = new CMMClassifier();
    cmm.loadClassifierNoExceptions(file);
    return cmm;

  }

  public static CMMClassifier getClassifier(File file) throws IOException, ClassCastException, ClassNotFoundException {

    CMMClassifier cmm = new CMMClassifier();
    cmm.loadClassifier(file);
    return cmm;
  }

  public static CMMClassifier getClassifierNoExceptions(String loadPath) {
    CMMClassifier cmm = new CMMClassifier();
    cmm.loadClassifierNoExceptions(loadPath);
    return cmm;

  }

  public static CMMClassifier getClassifier(String loadPath) throws IOException, ClassCastException, ClassNotFoundException {

    CMMClassifier cmm = new CMMClassifier();
    cmm.loadClassifier(loadPath);
    return cmm;
  }

  public static CMMClassifier getClassifierNoExceptions(InputStream in) {
    CMMClassifier cmm = new CMMClassifier();
    cmm.loadClassifierNoExceptions(new BufferedInputStream(in), null);
    return cmm;
  }

  public static CMMClassifier getClassifier(InputStream in) throws IOException, ClassCastException, ClassNotFoundException {
    CMMClassifier cmm = new CMMClassifier();
    cmm.loadClassifier(new BufferedInputStream(in));
    return cmm;
  }

  /** This routine builds the <code>answerArrays</code> which give the
   *  empirically legal label sequences (of length (order) at most
   *  <code>flags.maxLeft</code>) and the <code>classIndex</code>,
   *  which indexes known answer classes.
   *
   * @param docs The training data: A List of List of CoreLabel
   */
  private void makeAnswerArraysAndTagIndex(Collection<List<IN>> docs) {
    if (answerArrays == null) {
      answerArrays = new HashSet<List<String>>();
    }
    if (classIndex == null) {
      classIndex = new HashIndex<String>();
    }

    for (List<IN> doc : docs) {
      if (flags.useReverse) {
        Collections.reverse(doc);
      }

      int leng = doc.size();
      for (int start = 0; start < leng; start++) {
        for (int diff = 1; diff <= flags.maxLeft && start + diff <= leng; diff++) {
          String[] seq = new String[diff];
          for (int i = start; i < start + diff; i++) {
            seq[i - start] = doc.get(i).get(AnswerAnnotation.class);
          }
          answerArrays.add(Arrays.asList(seq));
        }
      }
      for (int i = 0; i < leng; i++) {
        CoreLabel wordInfo = doc.get(i);
        classIndex.add(wordInfo.get(AnswerAnnotation.class));
      }

      if (flags.useReverse) {
        Collections.reverse(doc);
      }
    }
  }

  /** Make an individual Datum out of the data list info, focused at position
   *  loc.
   *  @param info A List of WordInfo objects
   *  @param loc  The position in the info list to focus feature creation on
   *  @param featureFactory The factory that constructs features out of the item
   *  @return A Datum (BasicDatum) representing this data instance
   */
  public <T extends CoreLabel> Datum<String, String> makeDatum(List<IN> info, int loc, FeatureFactory featureFactory) {
    PaddedList<IN> pInfo = new PaddedList<IN>(info, pad);

    Collection<String> features = new ArrayList<String>();
    List<Clique> cliques = featureFactory.getCliques();
    for (Clique c : cliques) {
      Collection<String> feats = featureFactory.getCliqueFeatures(pInfo, loc, c);
      feats = addOtherClasses(feats, pInfo, loc, c);
      features.addAll(feats);
    }

    printFeatures(pInfo.get(loc), features);
    CoreLabel c = info.get(loc);
    return new BasicDatum<String, String>(features, c.get(AnswerAnnotation.class));
  }


  /** This adds to the feature name the name of classes that are other than
   *  the current class that are involved in the clique.  In the CMM, these
   *  other classes become part of the conditioning feature, and only the
   *  class of the current position is being predicted.
   *
   *  @return A collection of features with extra class information put
   *          into the feature name.
   */
  private static Collection<String> addOtherClasses(Collection<String> feats, List<? extends CoreLabel> info,
                                     int loc, Clique c) {
    String addend = null;
    String pAnswer = info.get(loc - 1).get(AnswerAnnotation.class);
    String p2Answer = info.get(loc - 2).get(AnswerAnnotation.class);
    String p3Answer = info.get(loc - 3).get(AnswerAnnotation.class);
    String p4Answer = info.get(loc - 4).get(AnswerAnnotation.class);
    String p5Answer = info.get(loc - 5).get(AnswerAnnotation.class);
    String nAnswer = info.get(loc + 1).get(AnswerAnnotation.class);
    // cdm 2009: Is this really right? Do we not need to differentiate names that would collide???
    if (c == FeatureFactory.cliqueCpC) {
      addend = '|' + pAnswer;
    } else if (c == FeatureFactory.cliqueCp2C) {
      addend = '|' + p2Answer;
    } else if (c == FeatureFactory.cliqueCp3C) {
      addend = '|' + p3Answer;
    } else if (c == FeatureFactory.cliqueCp4C) {
      addend = '|' + p4Answer;
    } else if (c == FeatureFactory.cliqueCp5C) {
      addend = '|' + p5Answer;
    } else if (c == FeatureFactory.cliqueCpCp2C) {
      addend = '|' + pAnswer + '-' + p2Answer;
    } else if (c == FeatureFactory.cliqueCpCp2Cp3C) {
      addend = '|' + pAnswer + '-' + p2Answer + '-' + p3Answer;
    } else if (c == FeatureFactory.cliqueCpCp2Cp3Cp4C) {
      addend = '|' + pAnswer + '-' + p2Answer + '-' + p3Answer + '-' + p4Answer;
    } else if (c == FeatureFactory.cliqueCpCp2Cp3Cp4Cp5C) {
      addend = '|' + pAnswer + '-' + p2Answer + '-' + p3Answer + '-' + p4Answer + '-' + p5Answer;
    } else if (c == FeatureFactory.cliqueCnC) {
      addend = '|' + nAnswer;
    } else if (c == FeatureFactory.cliqueCpCnC) {
      addend = '|' + pAnswer + '-' + nAnswer;
    }
    if (addend == null) {
      return feats;
    }
    Collection<String> newFeats = new HashSet<String>();
    for (String feat : feats) {
      String newFeat = feat + addend;
      newFeats.add(newFeat);
    }
    return newFeats;
  }


  private static List<Pair<Pattern, Integer>> getThresholds(String filename) {
    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      List<Pair<Pattern, Integer>> thresholds = new ArrayList<Pair<Pattern, Integer>>();
      String line;
      while ((line = in.readLine()) != null) {
        int i = line.lastIndexOf(' ');
        Pattern p = Pattern.compile(line.substring(0, i));
        //System.err.println(":"+line.substring(0,i)+":");
        Integer t = Integer.valueOf(line.substring(i + 1));
        Pair<Pattern, Integer> pair = new Pair<Pattern, Integer>(p, t);
        thresholds.add(pair);
      }
      in.close();
      return thresholds;
    } catch (Exception e) {
      throw new RuntimeException("Error reading threshold file", e);
    }
  }

  public void trainSemiSup() {
    DocumentReaderAndWriter<IN> readerAndWriter = makeReaderAndWriter();

    String filename = flags.trainFile;
    String biasedFilename = flags.biasedTrainFile;

    ObjectBank<List<IN>> data =
      makeObjectBankFromFile(filename, readerAndWriter);
    ObjectBank<List<IN>> biasedData =
      makeObjectBankFromFile(biasedFilename, readerAndWriter);

    Index<String> featureIndex = new HashIndex<String>();
    Index<String> classIndex = new HashIndex<String>();

    Dataset<String, String> dataset = getDataset(data, featureIndex, classIndex);
    Dataset<String, String> biasedDataset = getBiasedDataset(biasedData, featureIndex, classIndex);

    double[][] confusionMatrix = new double[classIndex.size()][classIndex.size()];

    for (int i = 0; i < confusionMatrix.length; i++) {
      Arrays.fill(confusionMatrix[i], 0.0);
      confusionMatrix[i][i] = 1.0;
    }

    String cm = flags.confusionMatrix;
    String[] bits = cm.split(":");
    for (String bit : bits) {
      String[] bits1 = bit.split("\\|");
      int i1 = classIndex.indexOf(bits1[0]);
      int i2 = classIndex.indexOf(bits1[1]);
      double d = Double.parseDouble(bits1[2]);
      confusionMatrix[i2][i1] = d;
    }

    for (int i = 0; i < confusionMatrix.length; i++) {
      ArrayMath.normalize(confusionMatrix[i]);
    }

    for (int i = 0; i < confusionMatrix.length; i++) {
      for (int j = 0; j < i; j++) {
        double d = confusionMatrix[i][j];
        confusionMatrix[i][j] = confusionMatrix[j][i];
        confusionMatrix[j][i] = d;
      }
    }

    for (int i = 0; i < confusionMatrix.length; i++) {
      for (int j = 0; j < confusionMatrix.length; j++) {
        System.err.println("P("+classIndex.get(j)+ '|' +classIndex.get(i)+") = "+confusionMatrix[j][i]);
      }
    }

    trainSemiSup(dataset, biasedDataset, confusionMatrix);
  }

  static class Scorer implements SequenceModel {
    private CMMClassifier classifier = null;

    private int[] tagArray = null;
    private int[] backgroundTags = null;
    private Index<String> tagIndex = null;
    private List<? extends CoreLabel> lineInfos = null;
    private int pre = 0;
    private int post = 0;
    private Set<List<String>> legalTags = null;

    private static final boolean VERBOSE = false;

    void buildTagArray() {
      int sz = tagIndex.size();
      tagArray = new int[sz];
      for (int i = 0; i < sz; i++) {
        tagArray[i] = i;
      }
    }

    public int length() {
      return lineInfos.size() - pre - post;
    }

    public int leftWindow() {
      return pre;
    }

    public int rightWindow() {
      return post;
    }

    public int[] getPossibleValues(int position) {
      //             if (position == 0 || position == lineInfos.size() - 1) {
      //                 int[] a = new int[1];
      //                 a[0] = tagIndex.indexOf(BACKGROUND);
      //                 return a;
      //             }
      if (tagArray == null) {
        buildTagArray();
      }
      if (position < pre) {
        return backgroundTags;
      }
      return tagArray;
    }

    public double scoreOf(int[] sequence) {
      throw new UnsupportedOperationException();
    }

    private double[] scoreCache = null;
    private int[] lastWindow = null;
    //private int lastPos = -1;

    public double scoreOf(int[] tags, int pos) {
      if (false) {
        return scoresOf(tags, pos)[tags[pos]];
      }
      if (lastWindow == null) {
        lastWindow = new int[leftWindow() + rightWindow() + 1];
        Arrays.fill(lastWindow, -1);
      }
      boolean match = (pos == lastPos);
      for (int i = pos - leftWindow(); i <= pos + rightWindow(); i++) {
        if (i == pos || i < 0) {
          continue;
        }
        /*System.err.println("p:"+pos);
        System.err.println("lw:"+leftWindow());
        System.err.println("i:"+i);*/
        match &= tags[i] == lastWindow[i - pos + leftWindow()];
      }
      if (!match) {
        scoreCache = scoresOf(tags, pos);
        for (int i = pos - leftWindow(); i <= pos + rightWindow(); i++) {
          if (i < 0) {
            continue;
          }
          lastWindow[i - pos + leftWindow()] = tags[i];
        }
        lastPos = pos;
      }
      return scoreCache[tags[pos]];
    }

    private int percent = -1;
    private int num = 0;
    private long secs = System.currentTimeMillis();
    private long hit = 0;
    private long tot = 0;

    public double[] scoresOf(int[] tags, int pos) {
      if (VERBOSE) {
        int p = (100 * pos) / length();
        if (p > percent) {
          long secs2 = System.currentTimeMillis();
          System.err.println(StringUtils.padLeft(p, 3) + "%   " + ((secs2 - secs == 0) ? 0 : (num * 1000 / (secs2 - secs))) + " hits per sec, position=" + pos + ", legal=" + ((tot == 0) ? 100 : ((100 * hit) / tot)));
          // + "% [hit=" + hit + ", tot=" + tot + "]");
          percent = p;
          num = 0;
          secs = secs2;
        }
        tot++;
      }
      String[] answers = new String[1 + leftWindow() + rightWindow()];
      String[] pre = new String[leftWindow()];
      for (int i = 0; i < 1 + leftWindow() + rightWindow(); i++) {
        int absPos = pos - leftWindow() + i;
        if (absPos < 0) {
          continue;
        }
        answers[i] = tagIndex.get(tags[absPos]);
        CoreLabel li = lineInfos.get(absPos);
        li.set(AnswerAnnotation.class, answers[i]);
        if (i < leftWindow()) {
          pre[i] = answers[i];
        }
      }
      double[] scores = new double[tagIndex.size()];
      //System.out.println("Considering: "+Arrays.asList(pre));
      if (!legalTags.contains(Arrays.asList(pre)) && classifier.flags.useObservedSequencesOnly) {
        // System.out.println("Rejecting: " + Arrays.asList(pre));
        // System.out.println(legalTags);
        Arrays.fill(scores, -1000);// Double.NEGATIVE_INFINITY;
        return scores;
      }
      num++;
      hit++;
      Counter<String> c = classifier.scoresOf(lineInfos, pos);
      //System.out.println("Pos "+pos+" hist "+Arrays.asList(pre)+" result "+c);
      //System.out.println(c);
      //if (false && flags.justify) {
      //    System.out.println("Considering position " + pos + ", word is " + ((CoreLabel) lineInfos.get(pos)).word());
      //    //System.out.println("Datum is "+d.asFeatures());
      //    System.out.println("History: " + Arrays.asList(pre));
      //}
      for (String s : c.keySet()) {
        int t = tagIndex.indexOf(s);
        if (t > -1) {
          int[] tA = getPossibleValues(pos);
          for (int j = 0; j < tA.length; j++) {
            if (tA[j] == t) {
              scores[j] = c.getCount(s);
              //if (false && flags.justify) {
              //    System.out.println("Label " + s + " got score " + scores[j]);
              //}
            }
          }
        }
      }
      // normalize?
      if (classifier.normalize()) {
        ArrayMath.logNormalize(scores);
      }
      return scores;
    }

    static double[] recenter(double[] x) {
      double[] r = new double[x.length];
      // double logTotal = Double.NEGATIVE_INFINITY;
      // for (int i = 0; i < x.length; i++)
      //    logTotal = SloppyMath.logAdd(logTotal, x[i]);
      double logTotal = ArrayMath.logSum(x);
      for (int i = 0; i < x.length; i++) {
        r[i] = x[i] - logTotal;
      }
      return r;
    }

    /**
     * Build a Scorer.
     *
     * @param lineInfos  List of WordInfo data items to classify
     * @param classifier The trained Classifier
     * @param pre        Number of previous tags that condition current tag
     * @param post       Number of following tags that condition previous tag
     *                   (if pre and post are both nonzero, then you have a
     *                   dependency network tagger)
     */
    Scorer(List<? extends CoreLabel> lineInfos, Index<String> tagIndex, CMMClassifier classifier, int pre, int post, Set<List<String>> legalTags) {
      if (VERBOSE) {
        System.err.println("Built Scorer for " + lineInfos.size() + " words, clique pre=" + pre + " post=" + post);
      }
      this.pre = pre;
      this.post = post;
      this.lineInfos = lineInfos;
      this.tagIndex = tagIndex;
      this.classifier = classifier;
      this.legalTags = legalTags;
      backgroundTags = new int[]{tagIndex.indexOf(classifier.flags.backgroundSymbol)};
    }

  } // end class Scorer

  private boolean normalize() {
    return flags.normalize;
  }

  static int lastPos = -1;

  public Counter<String> scoresOf(List<IN> lineInfos, int pos) {
//     if (pos != lastPos) {
//       System.err.print(pos+".");
//       lastPos = pos;
//     }
//     System.err.print("!");
    Datum<String, String> d = makeDatum(lineInfos, pos, featureFactory);
    return classifier.logProbabilityOf(d);
  }


  /**
   * Takes a {@link List} of {@link CoreLabel}s and prints the likelihood
   * of each possible label at each point.
   * TODO: Finish or delete this method!
   *
   * @param document A {@link List} of {@link CoreLabel}s.
   */
  @Override
  public void printProbsDocument(List<IN> document) {

    //ClassicCounter<String> c = scoresOf(document, 0);

  }

  /** Command-line version of the classifier.  See the class
   *  comments for examples of use, and SeqClassifierFlags
   *  for more information on supported flags.
   */
  public static void main(String[] args) throws Exception {
    StringUtils.printErrInvocationString("CMMClassifier", args);

    Properties props = StringUtils.argsToProperties(args);
    CMMClassifier cmm = new CMMClassifier<CoreLabel>(props);
    String testFile = cmm.flags.testFile;
    String textFile = cmm.flags.textFile;
    String loadPath = cmm.flags.loadClassifier;
    String serializeTo = cmm.flags.serializeTo;

    // cmm.crossValidateTrainAndTest(trainFile);
    if (loadPath != null) {
      cmm.loadClassifierNoExceptions(loadPath, props);
    } else if (cmm.flags.loadJarClassifier != null) {
      cmm.loadJarClassifier(cmm.flags.loadJarClassifier, props);
    } else if (cmm.flags.trainFile != null) {
      if (cmm.flags.biasedTrainFile != null) {
        cmm.trainSemiSup();
      } else {
        cmm.train();
      }
    } else {
      cmm.loadDefaultClassifier();
    }

    if (serializeTo != null) {
      cmm.serializeClassifier(serializeTo);
    }

    if (testFile != null) {
      cmm.classifyAndWriteAnswers(testFile, cmm.makeReaderAndWriter());
    } else if (cmm.flags.testFiles != null) {
      cmm.classifyAndWriteAnswers(cmm.flags.baseTestDir, cmm.flags.testFiles,
                                  cmm.makeReaderAndWriter());
    }

    if (textFile != null) {
      DocumentReaderAndWriter readerAndWriter =
        new PlainTextDocumentReaderAndWriter();
      cmm.classifyAndWriteAnswers(textFile, readerAndWriter);
    }
  } // end main


  /**
   * Assigns NER labels to the words in the given Document.
   * Implements the {@link DocumentProcessor} interface.  Outputs a new document
   * with the same meta-data as the old one, but whose contents are a
   * List of {@link WordTag}s, where the tags are the NER labels assigned
   * to the word.
   */
  public Document<?, ?, WordTag> processDocument(Document in) {
    Document<?, ?, WordTag> d = in.blankDocument();
    d.addAll(process(in));
    return d;
  }

  /**
   * Assigns NER labels to the words in the given List.
   * Implements the {@link ListProcessor} interface.  Checks the input
   * for instances of {@link HasWord} and {@link HasTag}, or uses the
   * toString() method, the HasWord check fails.  Outputs a list of
   * {@link WordTag}s, where the tag is the NER label assigned to the word.
   */
  //TODO: require this list to have things that are instanceof HasWord?
  public List<WordTag> process(List list) {
    List<IN> featureLabels = new ArrayList<IN>();
    for (Object o : list) {
      CoreLabel wi = new CoreLabel();
      if (o instanceof HasWord) {
        wi.setWord(((HasWord) o).word());
        if (o instanceof HasTag) {
          wi.setTag(((HasTag) o).tag());
        }
      } else {
        wi.setWord(o.toString());
      }
      featureLabels.add((IN) wi);
    }
    List<IN> tagged = classify(featureLabels);
    List<WordTag> out = new ArrayList<WordTag>();
    for (CoreLabel wi : tagged) {
      out.add(new WordTag(wi.word(), wi.get(AnswerAnnotation.class)));
    }
    return out;
  }

  public double weight(String feature, String label) {
    return ((LinearClassifier<String, String>)classifier).weight(feature, label);
  }

  public double[][] weights() {
    return ((LinearClassifier<String, String>)classifier).weights();
  }

  @Override
  public List<IN> classifyWithGlobalInformation(List<IN> tokenSeq, final CoreMap doc, final CoreMap sent) {
    return classify(tokenSeq);
  }

} // end class CMMClassifier
