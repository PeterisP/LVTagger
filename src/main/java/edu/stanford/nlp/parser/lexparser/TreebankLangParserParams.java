package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.objectbank.TokenizerFactory;
import edu.stanford.nlp.parser.metrics.AbstractEval;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.util.Filter;
import edu.stanford.nlp.util.Index;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.List;


/**
 * Contains language-specific methods necessary to get the parser to parse
 * an arbitrary treebank.
 *
 * @author Roger Levy
 * @version 03/05/2003
 */
public interface TreebankLangParserParams extends TreebankFactory, Serializable {

  public HeadFinder headFinder();

  public HeadFinder typedDependencyHeadFinder();

  /**
   * Allows language specific processing (e.g., stemming) of head words.
   * 
   * @param headWord An {@link edu.stanford.nlp.ling.Label} that minimally implements the 
   * {@link edu.stanford.nlp.ling.HasWord} and {@link edu.stanford.nlp.ling.HasTag} interfaces.
   * @return A processed {@link edu.stanford.nlp.ling.Label}
   */
  public Label processHeadWord(Label headWord);
  
  /**
   * Convenience method for setting state parameters specific to evaluation. For example, if grammatical
   * functions are retained during training but discarded during evaluation, this method may be used
   * to make that state change.
   */
  public void setupForEval();
  
  public void setInputEncoding(String encoding);

  public void setOutputEncoding(String encoding);

  /**
   * If evalGFs = true, then the evaluation of parse trees will include evaluation on grammatical functions.
   * Otherwise, evaluation will strip the grammatical functions.
   */
  public void setEvaluateGrammaticalFunctions(boolean evalGFs);
  
  /**
   * Returns the output encoding being used.
   * @return The output encoding being used.
   */
  public String getOutputEncoding();

  /**
   * Returns the input encoding being used.
   * @return The input encoding being used.
   */
  public String getInputEncoding();


  /**
   * Returns a factory for reading in trees from the source you want.  It's
   * the responsibility of trf to deal properly with character-set encoding
   * of the input.  It also is the responsibility of trf to properly
   * normalize trees.
   *
   * @return A factory that vends an appropriate TreeReader
   */
  public TreeReaderFactory treeReaderFactory();


  /**
   * Vends a {@link Lexicon} object suitable to the particular language/treebank combination of interest.
   * @param op Options as to how the Lexicon behaves
   * @return A Lexicon, constructed based on the given option
   */
  public Lexicon lex(Options op, Index<String> wordIndex, Index<String> tagIndex);


  /**
   * The tree transformer applied to trees prior to evaluation.
   * For instance, it might delete punctuation nodes.  This method will
   * be applied both to the parse output tree and to the gold
   * tree.  The exact specification depends on "standard practice" for
   * various treebanks.
   *
   * @return A TreeTransformer that performs adjustments to trees to delete
   *     or equivalence class things not evaluated in the parser performance
   *     evaluation.
   */
  public TreeTransformer collinizer();

  
  /**
   * the tree transformer used to produce trees for evaluation.  Will
   * be applied both to the parse output tree and to the gold
   * tree. Should strip punctuation and maybe do some other
   * things. The evalb version should strip some more stuff
   * off. (finish this doc!)
   */
  public TreeTransformer collinizerEvalb();

  /**
   * returns a MemoryTreebank appropriate to the treebank source
   */
  public MemoryTreebank memoryTreebank();

  /**
   * returns a DiskTreebank appropriate to the treebank source
   */
  public DiskTreebank diskTreebank();

  /**
   * returns a MemoryTreebank appropriate to the testing treebank source
   */
  public MemoryTreebank testMemoryTreebank();
  
  /**
   * Required to extend TreebankFactory
   */
  public Treebank treebank();

  /**
   * returns a TreebankLanguagePack containing Treebank-specific (but
   * not parser-specific) info such as what is punctuation, and also
   * information about the structure of labels
   */
  public TreebankLanguagePack treebankLanguagePack();

  /**
   * returns a PrintWriter used to print output. It's the
   * responsibility of the returned PrintWriter to deal properly with
   * character encodings for the relevant treebank
   */
  public PrintWriter pw();

  /**
   * returns a PrintWriter used to print output to the OutputStream
   * o. It's the responsibility of the returned PrintWriter to deal
   * properly with character encodings for the relevant treebank
   */
  public PrintWriter pw(OutputStream o);


  /**
   * Returns the splitting strings used for selective splits.
   *
   * @return An array containing ancestor-annotated Strings: categories
   *         should be split according to these ancestor annotations.
   */
  public String[] sisterSplitters();


  /**
   * Returns a TreeTransformer appropriate to the Treebank which
   * can be used to remove functional tags (such as "-TMP") from
   * categories.
   */
  public TreeTransformer subcategoryStripper();

  /**
   * This method does language-specific tree transformations such
   * as annotating particular nodes with language-relevant features.
   * Such parameterizations should be inside the specific
   * TreebankLangParserParams class.  This method is recursively
   * applied to each node in the tree (depth first, left-to-right),
   * so you shouldn't write this method to apply recursively to tree
   * members.  This method is allowed to (and in some cases does)
   * destructively change the input tree <code>t</code>. It changes both
   * labels and the tree shape.
   *
   * @param t The input tree (with non-language specific annotation already
   *           done, so you need to strip back to basic categories)
   * @param root The root of the current tree (can be null for words)
   * @return The fully annotated tree node (with daughters still as you
   *           want them in the final result)
   */
  public Tree transformTree(Tree t, Tree root);

  /**
   * display language-specific settings
   */
  public void display();

  /**
   * Set a language-specific option according to command-line flags.
   * This routine should try to process the option starting at args[i] (which
   * might potentially be several arguments long if it takes arguments).
   * It should return the index after the last index it consumed in
   * processing.  In particular, if it cannot process the current option,
   * the return value should be i.
   *
   * @param args Array of command line arguments
   * @param i    Index in command line arguments to try to process as an option
   * @return The index of the item after arguments processed as part of this
   *         command line option.
   */
  public int setOptionFlag(String[] args, int i);


  /**
   * Return a default sentence of the language (for testing).
   * @return A default sentence of the language
   */
  public List<? extends HasWord> defaultTestSentence();

  public TokenizerFactory<Tree> treeTokenizerFactory();

  public Extractor<DependencyGrammar> dependencyGrammarExtractor(Options op, Index<String> wordIndex, Index<String> tagIndex);

  /**
   * Give the parameters for smoothing in the MLEDependencyGrammar.
   * @return an array of doubles with smooth_aT_hTWd, smooth_aTW_hTWd, smooth_stop, and interp
   */
  double[] MLEDependencyGrammarSmoothingParams();

  /**
   * Returns a language specific object for evaluating PP attachment
   *  
   * @return An object that implements {@link AbstractEval}
   */
  public AbstractEval ppAttachmentEval();

  /**
   * Returns a function which reads the given filename and turns its
   * content in a list of GrammaticalStructures.  Will throw
   * UnsupportedOperationException if the language doesn't support
   * dependencies or GrammaticalStructures.
   */
  List<GrammaticalStructure> readGrammaticalStructureFromFile(String filename);

  /**
   * Build a GrammaticalStructure from a Tree.  Throws
   * UnsupportedOperationException if the language doesn't support
   * dependencies or GrammaticalStructures.
   */
  GrammaticalStructure getGrammaticalStructure(Tree t, Filter<String> filter, 
                                               HeadFinder hf);
}
