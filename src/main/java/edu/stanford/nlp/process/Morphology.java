package edu.stanford.nlp.process;


import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.logging.Logger;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.ling.WordLemmaTag;
import edu.stanford.nlp.ling.WordTag;
import edu.stanford.nlp.util.Function;


/**
 * Morphology computes the base form of English words, by removing just
 * inflections (not derivational morphology).  That is, it only does noun
 * plurals, pronoun case, and verb endings, and not things like comparative adjectives
 * or derived nominals.  It is based on a finite-state
 * transducer implemented by John Carroll et al., written in flex and publicly
 * available.
 * See: http://www.informatics.susx.ac.uk/research/nlp/carroll/morph.html .
 * There are several ways of invoking Morphology. One is by calling the static
 * methods
 * WordTag stemStatic(String word, String tag) or
 * WordTag stemStatic(WordTag wordTag).
 * If we have created a Morphology object already we can use the methods
 * WordTag stem(String word, string tag) or WordTag stem(WordTag wordTag).
 * <br>
 * Another way of using Morphology is to run it on an input file by running
 * <code>java Morphology filename</code>.  In this case, POS tags MUST be
 * separated from words by an underscore ("_").
 * <br>
 * Note that a single instance of Morphology is not thread-safe, as
 * the underlying lexer object is not built to be re-entrant.  One thing that
 * you can do to get around this is build a new Morphology object for
 * each set of calls to the Morphology.  For example, the
 * MorphaAnnotator builds a Morphology for each document it annotates.
 * The other approach is to use the synchronized methods in this class.
 * <br>
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 * @author Christopher Manning
 */
public class Morphology implements Function {

  private static final Logger LOGGER = Logger.getLogger(Morphology.class.getName());

  private static final boolean DEBUG = false;
  private static Morpha staticLexer;

  private final Morpha lexer;

  public Morphology() {
    lexer = new Morpha(System.in);
  }

  /**
   * Process morphologically words from a Reader.
   *
   * @param in The Reader to read from
   */
  public Morphology(Reader in) {
    lexer = new Morpha(in);
  }


  public Morphology(Reader in, int flags) {
    lexer = new Morpha(in);
    lexer.setOptions(flags);
  }


  public Word next() throws IOException {
    String nx = lexer.next();
    if (nx == null) {
      return null;
    } else {
      return new Word(nx);
    }
  }

  static boolean isProper(String posTag) {
    return posTag.equals("NNP") || posTag.equals("NNPS") || posTag.equals("NP");
  }

  public Word stem(Word w) {
    return new Word(stem(w.value()));
  }

  public String stem(String word) {
    try {
      lexer.yyreset(new StringReader(word));
      lexer.yybegin(Morpha.any);
      String wordRes = lexer.next();
      return wordRes;
    } catch (IOException e) {
      LOGGER.warning("Morphology.stem() had error on word " + word);
      return word;
    }
  }


  public String lemma(String word, String tag) {
    return lemmatize(word, tag, lexer, lexer.option(1));
  }

  public String lemma(String word, String tag, boolean lowercase) {
    return lemmatize(word, tag, lexer, lowercase);
  }


  /**
   * Adds the LemmaAnnotation to the given CoreLabel.
   */
  public void stem(CoreLabel label) {
    stem(label, LemmaAnnotation.class);
  }

  /**
   * Adds annotation <code>ann</code> to the given CoreLabel.
   * Assumes that it has a TextAnnotation and PartOfSpeechAnnotation.
   */
  public void stem(CoreLabel label,
                   Class<? extends CoreAnnotation<String>> ann) {
    String lemma = lemmatize(label.word(), label.tag(), lexer, lexer.option(1));
    label.set(ann, lemma);
  }

  /** Lemmatize the word, being sensitive to the tag, using the
   *  passed in lexer.
   *
   *  @param lowercase If this is true, words other than proper nouns will
   *      be changed to all lowercase.
   */
  private static String lemmatize(String word, String tag, Morpha lexer, boolean lowercase) {
    boolean wordHasForbiddenChar = word.indexOf('_') >= 0 ||word.indexOf(' ') >= 0;
    String quotedWord = word;
    if (wordHasForbiddenChar) {
      // choose something unlikely. Classical Vedic!
      quotedWord = quotedWord.replaceAll("_", "\u1CF0");
      quotedWord = quotedWord.replaceAll(" ", "\u1CF1");
    }
    String wordtag = quotedWord + '_' + tag;
    if (DEBUG) System.err.println("Trying to normalize |" + wordtag + "|");
    try {
      lexer.setOption(1, lowercase);
      lexer.yyreset(new StringReader(wordtag));
      lexer.yybegin(Morpha.scan);
      String wordRes = lexer.next();
      lexer.next(); // go past tag
      if (wordHasForbiddenChar) {
        if (DEBUG) System.err.println("Restoring forbidden chars");
        wordRes = wordRes.replaceAll("\u1CF0", "_");
        wordRes = wordRes.replaceAll("\u1CF1", " ");
      }
      return wordRes;
    } catch (IOException e) {
      LOGGER.warning("Morphology.stem() had error on word " + word + "/" + tag);
      return word;
    }
  }

  private static synchronized void initStaticLexer() {
    if (staticLexer == null) {
      staticLexer = new Morpha(System.in);
    }
  }

  /** Return a new WordTag which has the lemma as the value of word().
   *  The default is to lowercase non-proper-nouns, unless options have
   *  been set.
   */
  public static WordTag stemStatic(String word, String tag) {
    initStaticLexer();
    return new WordTag(lemmatize(word, tag, staticLexer, staticLexer.option(1)), tag);
  }


  public static String lemmaStatic(String word, String tag,
                                   boolean lowercase) {
    initStaticLexer();
    return lemmatize(word, tag, staticLexer, lowercase);
  }



  public static synchronized WordTag stemStaticSynchronized(String word,
                                                            String tag) {
    return stemStatic(word, tag);
  }


  public static synchronized String lemmaStaticSynchronized(String word,
                                                            String tag,
                                                            boolean lowercase) {
    return lemmaStatic(word, tag, lowercase);
  }

  /** Return a new WordTag which has the lemma as the value of word().
   *  The default is to lowercase non-proper-nouns, unless options have
   *  been set.
   */
  public static WordTag stemStatic(WordTag wT) {
    return stemStatic(wT.word(), wT.tag());
  }


  public Object apply(Object in) {
    if (in instanceof WordTag) {
      WordTag wt = (WordTag) in;
      String tag = wt.tag();
      return new WordTag(lemmatize(wt.word(), tag, lexer, lexer.option(1)), tag);
    }
    if (in instanceof Word) {
      return stem((Word) in);
    }
    return in;
  }

  /**
   * Lemmatize returning a <code>WordLemmaTag </code>.
   */
  public WordLemmaTag lemmatize(WordTag wT) {
    String tag = wT.tag();
    String word = wT.word();
    String lemma = lemma(word, tag);
    return new WordLemmaTag(word, lemma, tag);
  }

  public static WordLemmaTag lemmatizeStatic(WordTag wT) {
    String tag = wT.tag();
    String word = wT.word();
    String lemma = stemStatic(wT).word();
    return new WordLemmaTag(word, lemma, tag);
  }


  /** Run the morphological analyzer.  Options are:
   *  <ul>
   *  <li>-rebuildVerbTable verbTableFile Convert a verb table from a text file
   *  (e.g., /u/nlp/data/morph/verbstem.list) to Java code contained in Morpha.flex .
   *  <li>-stem args ...  Stem each of the following arguments, which should either be
   *  in the form of just word or word_tag.
   *  <li> args ...  Each argument is a file and the contents of it are stemmed as
   *  space-separated tokens.    <i>Note:</i> If the tokens are tagged
   *  words, they must be in the format of whitespace separated word_tag pairs.
   * </ul>
   */
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.println("java Morphology [-rebuildVerbTable file|-stem word+|file+]");
    } else if (args.length == 2 && args[0].equals("-rebuildVerbTable")) {
      String verbs = IOUtils.slurpFile(args[1]);
      String[] words = verbs.split("\\s+");
      System.out.print(" private static final String[] verbStems = { ");
      for (int i = 0; i < words.length; i++) {
        System.out.print("\"" + words[i] + "\"");
        if (i != words.length - 1) {
          System.out.print(", ");
          if (i % 5 == 0) {
            System.out.println();
            System.out.print("    ");
          }
        }
      }
      System.out.println(" };");
    } else if (args[0].equals("-stem")) {
      for (int i = 1; i < args.length; i++) {
        System.out.println(args[i] + " --> " + stemStatic(WordTag.valueOf(args[i], "_")));
      }
    } else {
      int flags = 0;
      for (String arg :  args) {
        if (arg.charAt(0) == '-') {
          try {
            flags = Integer.parseInt(arg.substring(1));
          } catch (NumberFormatException nfe) {
            System.err.println("Couldn't handle flag: " + arg + "\n");
            // ignore flag
          }
        } else {
          Morphology morph = new Morphology(new FileReader(arg), flags);
          for (Word next; (next = morph.next()) != null; ) {
            System.out.print(next);
          }
        }
      }
    }
  }

}
