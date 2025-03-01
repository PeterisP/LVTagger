package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.ling.CoreAnnotations.CopyAnnotation;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.trees.international.pennchinese.ChineseEnglishWordMap;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.XMLUtils;

import java.io.*;
import java.util.*;


/**
 * A class for customizing the print method(s) for a
 * <code>edu.stanford.nlp.trees.Tree</code> as the output of the
 * parser.  This class supports printing in multiple ways and altering
 * behavior via properties specified at construction.
 *
 * @author Roger Levy
 * @author Christopher Manning
 * @author Galen Andrew
 */
public class TreePrint {

  // TODO: Add support for makeCopulaHead as an outputFormatOption here.

  public static final String rootLabelOnlyFormat = "rootSymbolOnly";
  public static final String headMark = "=H";

  /** The legal output tree formats. */
  public static final String[] outputTreeFormats = {
    "penn",
    "oneline",
    rootLabelOnlyFormat,
    "words",
    "wordsAndTags",
    "dependencies",
    "typedDependencies",
    "typedDependenciesCollapsed",
    "latexTree",
    "collocations",
    "semanticGraph",
    "conllStyleDependencies",
    "conll2007"
  };

  private final Properties formats;
  private final Properties options;

  private final boolean markHeadNodes; // = false;
  private final boolean lexicalize; // = false;
  private final boolean removeEmpty;
  private final boolean ptb2text;
  private final boolean transChinese; // = false;
  private final boolean basicDependencies;
  private final boolean collapsedDependencies;
  private final boolean nonCollapsedDependencies;
  private final boolean nonCollapsedDependenciesSeparated;
  private final boolean CCPropagatedDependencies;
  private final boolean treeDependencies;

  private final HeadFinder hf;
  private final TreebankLanguagePack tlp;
  private final WordStemmer stemmer;
  private final Filter<Dependency<Label, Label, Object>> dependencyFilter;
  private final Filter<Dependency<Label, Label, Object>> dependencyWordFilter;
  private final GrammaticalStructureFactory gsf;

  /** Pool use of one WordNetConnection.  I don't really know if
   *  Dan Bikel's WordNet code is thread safe, but it definitely doesn't
   *  close its files, and too much of our code makes TreePrint objects and
   *  then drops them on the floor, and so we run out of file handles.
   *  That is, if this variable isn't static, code crashes.
   *  Maybe we should change this code to use jwnl(x)?
   *  CDM July 2006.
   */
  private static WordNetConnection wnc;


  /** This PrintWriter is used iff the user doesn't pass one in to a
   *  call to printTree().  It prints to System.out.
   */
  private final PrintWriter pw = new PrintWriter(System.out, true);


  /** The anglocentric constructor. Should work for English only.
   *  @param formats The formats to print the tree in.
   */
  public TreePrint(String formats) {
    this(formats, "", new PennTreebankLanguagePack());
  }

  /** Make a TreePrint instance with no options specified. */
  public TreePrint(String formats, TreebankLanguagePack tlp) {
    this(formats, "", tlp);
  }

  /** Make a TreePrint instance. This one uses the default tlp headFinder. */
  public TreePrint(String formats, String options, TreebankLanguagePack tlp) {
    this(formats, options, tlp, tlp.headFinder(), tlp.typedDependencyHeadFinder());
  }

  /**
   * Make a TreePrint instance.
   *
   * @param formatString A comma separated list of ways to print each Tree.
   *                For instance, "penn" or "words,typedDependencies".
   *                Known formats are: oneline, penn, latexTree, words,
   *                wordsAndTags, rootSymbolOnly, dependencies,
   *                typedDependencies, typedDependenciesCollapsed,
   *                collocations, semanticGraph, conllStyleDependencies,
   *                conll2007.  The last two are both tab-separated values
   *                formats.  The latter has a lot more columns filled with
   *                underscores. All of them print a blank line after
   *                the output except for oneline.  oneline is also not
   *                meaningful in XML output (it is ignored: use penn instead).
   *                (Use of typedDependenciesCollapsed is deprecated.  It
   *                works but we recommend instead selecting a type of
   *                dependencies using the optionsString argument.  Note in
   *                particular that typedDependenciesCollapsed does not do
   *                CC propagation, which we generally recommend.)
   * @param optionsString Options that additionally specify how trees are to
   *                be printed (for instance, whether stemming should be done).
   *                Known options are: <code>stem, lexicalize, markHeadNodes,
   *                xml, removeTopBracket, transChinese,
   *                includePunctuationDependencies, basicDependencies, treeDependencies,
   *                CCPropagatedDependencies, collapsedDependencies, nonCollapsedDependencies,
   *                nonCollapsedDependenciesSeparated
   *                </code>.
   * @param tlp     The TreebankLanguagePack used to do things like delete
   *                or ignore punctuation in output
   * @param hf      The HeadFinder used in printing output
   */
  public TreePrint(String formatString, String optionsString, TreebankLanguagePack tlp, HeadFinder hf, HeadFinder typedDependencyHF) {
    formats = StringUtils.stringToProperties(formatString);
    options = StringUtils.stringToProperties(optionsString);
    List<String> okOutputs = Arrays.asList(outputTreeFormats);
    for (Object formObj : formats.keySet()) {
      String format = (String) formObj;
      if ( ! okOutputs.contains(format)) {
        throw new RuntimeException("Error: output tree format " + format + " not supported. Known formats are: " + okOutputs);
      }
    }

    this.hf = hf;
    this.tlp = tlp;
    boolean includePunctuationDependencies;
    includePunctuationDependencies = propertyToBoolean(this.options,
                                                       "includePunctuationDependencies");
    Filter<String> puncWordFilter;
    if (includePunctuationDependencies) {
      dependencyFilter = Filters.acceptFilter();
      dependencyWordFilter = Filters.acceptFilter();
      puncWordFilter = Filters.acceptFilter();
    } else {
      dependencyFilter = new Dependencies.DependentPuncTagRejectFilter<Label, Label, Object>(tlp.punctuationTagRejectFilter());
      dependencyWordFilter = new Dependencies.DependentPuncWordRejectFilter<Label, Label, Object>(tlp.punctuationWordRejectFilter());
      puncWordFilter = tlp.punctuationWordRejectFilter();
    }
    if (propertyToBoolean(this.options, "stem")) {
      stemmer = new WordStemmer();
    } else {
      stemmer = null;
    }
    if (formats.containsKey("typedDependenciesCollapsed") ||
        formats.containsKey("typedDependencies")) {
      gsf = tlp.grammaticalStructureFactory(puncWordFilter, typedDependencyHF);
    } else {
      gsf = null;
    }

    lexicalize = propertyToBoolean(this.options, "lexicalize");
    markHeadNodes = propertyToBoolean(this.options, "markHeadNodes");
    transChinese = propertyToBoolean(this.options, "transChinese");
    ptb2text = propertyToBoolean(this.options, "ptb2text");
    removeEmpty = propertyToBoolean(this.options, "noempty") || ptb2text;

    basicDependencies =  propertyToBoolean(this.options, "basicDependencies");
    collapsedDependencies = propertyToBoolean(this.options, "collapsedDependencies");
    nonCollapsedDependencies = propertyToBoolean(this.options, "nonCollapsedDependencies");
    nonCollapsedDependenciesSeparated = propertyToBoolean(this.options, "nonCollapsedDependenciesSeparated");
    treeDependencies = propertyToBoolean(this.options, "treeDependencies");

    // if no option format for the dependencies is specified, CCPropagated is the default
    if ( ! basicDependencies && ! collapsedDependencies && ! nonCollapsedDependencies && ! nonCollapsedDependenciesSeparated && ! treeDependencies) {
      CCPropagatedDependencies = true;
    } else {
      CCPropagatedDependencies = propertyToBoolean(this.options, "CCPropagatedDependencies");
    }
  }


  private static boolean propertyToBoolean(Properties prop, String key) {
    return Boolean.parseBoolean(prop.getProperty(key));
  }

  /**
   * Prints the tree to the default PrintWriter.
   * @param t The tree to display
   */
  public void printTree(Tree t) {
    printTree(t, pw);
  }

  /**
   * Prints the tree, with an empty ID.
   * @param t The tree to display
   * @param pw The PrintWriter to print it to
   */
  public void printTree(final Tree t, PrintWriter pw) {
    printTree(t, "", pw);
  }


  /**
   * Prints the tree according to the options specified for this instance.
   * If the tree <code>t</code> is <code>null</code>, then the code prints
   * a line indicating a skipped tree.  Under the XML option this is
   * an <code>s</code> element with the <code>skipped</code> attribute having
   * value <code>true</code>, and, otherwise, it is the token
   * <code>SENTENCE_SKIPPED_OR_UNPARSABLE</code>.
   *
   * @param t The tree to display
   * @param id A name for this sentence
   * @param pw Where to display the tree
   */
  public void printTree(final Tree t, final String id, final PrintWriter pw) {
    final boolean inXml = propertyToBoolean(options, "xml");
    if (t == null) {
      // Parsing didn't succeed.
      if (inXml) {
        pw.print("<s");
        if (id != null && ! "".equals(id)) {
          pw.print(" id=\"" + XMLUtils.escapeXML(id) + '\"');
        }
        pw.println(" skipped=\"true\"/>");
        pw.println();
      } else {
        pw.println("SENTENCE_SKIPPED_OR_UNPARSABLE");
      }
    } else {
      if (inXml) {
        pw.print("<s");
        if (id != null && ! "".equals(id)) {
          pw.print(" id=\"" + XMLUtils.escapeXML(id) + '\"');
        }
        pw.println(">");
      }
      printTreeInternal(t, pw, inXml);
      if (inXml) {
        pw.println("</s>");
        pw.println();
      }
    }
  }


  /**
   * Prints the trees according to the options specified for this instance.
   * If the tree <code>t</code> is <code>null</code>, then the code prints
   * a line indicating a skipped tree.  Under the XML option this is
   * an <code>s</code> element with the <code>skipped</code> attribute having
   * value <code>true</code>, and, otherwise, it is the token
   * <code>SENTENCE_SKIPPED_OR_UNPARSABLE</code>.
   *
   * @param trees The list of trees to display
   * @param id A name for this sentence
   * @param pw Where to dislay the tree
   */
  public void printTrees(final List<ScoredObject<Tree>> trees, final String id, final PrintWriter pw) {
    final boolean inXml = propertyToBoolean(options, "xml");
    int ii = 0;  // incremented before used, so first tree is numbered 1
    for (ScoredObject<Tree> tp : trees) {
      ii++;
      Tree t = tp.object();
      double score = tp.score();

      if (t == null) {
        // Parsing didn't succeed.
        if (inXml) {
          pw.print("<s");
          if (id != null && ! "".equals(id)) {
            pw.print(" id=\"" + XMLUtils.escapeXML(id) + '\"');
          }
          pw.print(" n=\"");
          pw.print(ii);
          pw.print('\"');
          pw.print(" score=\"" + score + '\"');
          pw.println(" skipped=\"true\"/>");
          pw.println();
        } else {
          pw.println("SENTENCE_SKIPPED_OR_UNPARSABLE Parse #" + ii + " with score " + score);
        }
      } else {
        if (inXml) {
          pw.print("<s");
          if (id != null && ! "".equals(id)) {
            pw.print(" id=\"");
            pw.print(XMLUtils.escapeXML(id));
            pw.print('\"');
          }
          pw.print(" n=\"");
          pw.print(ii);
          pw.print('\"');
          pw.print(" score=\"");
          pw.print(score);
          pw.print('\"');
          pw.println(">");
        } else {
          pw.print("# Parse ");
          pw.print(ii);
          pw.print(" with score ");
          pw.println(score);
        }
        printTreeInternal(t, pw, inXml);
        if (inXml) {
          pw.println("</s>");
          pw.println();
        }
      }
    }
  }


  /** Print the internal part of a tree having already identified it.
   *  The ID and outer XML element is printed wrapping this method, but none
   *  of the internal content.
   *
   * @param t The tree to print. Now known to be non-null
   * @param pw Where to print it to
   * @param inXml Whether to use XML style printing
   */
  private void printTreeInternal(final Tree t, final PrintWriter pw, final boolean inXml) {
    Tree outputTree = t;

    if (formats.containsKey("conll2007") || removeEmpty) {
      outputTree = outputTree.prune(new BobChrisTreeNormalizer.EmptyFilter());
    }

    if (formats.containsKey("words")) {
      if (inXml) {
        ArrayList<Label> sentUnstemmed = outputTree.yield();
        pw.println("  <words>");
        int i = 1;
        for (Label w : sentUnstemmed) {
          pw.println("    <word ind=\"" + i + "\">" + XMLUtils.escapeXML(w.value()) + "</word>");
          i++;
        }
        pw.println("  </words>");
      } else {
        String sent = Sentence.listToString(outputTree.yield(), false);
        if(ptb2text) {
          pw.println(PTBTokenizer.ptb2Text(sent));
        } else {
          pw.println(sent);
          pw.println();
        }
      }
    }

    if (propertyToBoolean(options, "removeTopBracket")) {
      String s = outputTree.label().value();
      if (tlp.isStartSymbol(s)) {
        if (outputTree.isUnaryRewrite()) {
          outputTree = outputTree.firstChild();
        } else {
          // It's not quite clear what to do if the tree isn't unary at the top
          // but we then don't strip the ROOT symbol, since that seems closer
          // than losing part of the tree altogether....
          System.err.println("TreePrint: can't remove top bracket: not unary");
        }
      }
      // Note that TreePrint is also called on dependency trees that have
      // a word as the root node, and so we don't error if there isn't
      // the root symbol at the top; rather we silently assume that this
      // is a dependency tree!!
    }
    if (stemmer != null) {
      stemmer.visitTree(outputTree);
    }
    if (lexicalize) {
      outputTree = Trees.lexicalize(outputTree, hf);
      Function<Tree, Tree> a =
        TreeFunctions.getLabeledToDescriptiveCoreLabelTreeFunction();
      outputTree = a.apply(outputTree);
    }

    if (formats.containsKey("collocations")) {
      outputTree = getCollocationProcessedTree(outputTree, hf);
    }

    if (!lexicalize) { // delexicalize the output tree
      Function<Tree, Tree> a =
        TreeFunctions.getLabeledTreeToStringLabeledTreeFunction();
      outputTree = a.apply(outputTree);
    }

    Tree outputPSTree = outputTree;  // variant with head-marking, translations

    if (markHeadNodes) {
      outputPSTree = markHeadNodes(outputPSTree);
    }

    if (transChinese) {
      TreeTransformer tt = new TreeTransformer() {
        public Tree transformTree(Tree t) {
          t = t.treeSkeletonCopy();
          for (Tree subtree : t) {
            if (subtree.isLeaf()) {
              Label oldLabel = subtree.label();
              String translation = ChineseEnglishWordMap.getInstance().getFirstTranslation(oldLabel.value());
              if (translation == null) translation = "[UNK]";
              Label newLabel = new StringLabel(oldLabel.value() + ':' + translation);
              subtree.setLabel(newLabel);
            }
          }
          return t;
        }
      };
      outputPSTree = tt.transformTree(outputPSTree);
    }

    if (propertyToBoolean(options, "xml")) {
      if (formats.containsKey("wordsAndTags")) {
        ArrayList<TaggedWord> sent = outputTree.taggedYield();
        pw.println("  <words pos=\"true\">");
        int i = 1;
        for (TaggedWord tw : sent) {
          pw.println("    <word ind=\"" + i + "\" pos=\"" + XMLUtils.escapeXML(tw.tag()) + "\">" + XMLUtils.escapeXML(tw.word()) + "</word>");
          i++;
        }
        pw.println("  </words>");
      }
      if (formats.containsKey("penn")) {
        pw.println("  <tree style=\"penn\">");
        StringWriter sw = new StringWriter();
        PrintWriter psw = new PrintWriter(sw);
        outputPSTree.pennPrint(psw);
        pw.print(XMLUtils.escapeXML(sw.toString()));
        pw.println("  </tree>");
      }
      if (formats.containsKey("latexTree")) {
        pw.println("    <tree style=\"latexTrees\">");
        pw.println(".[");
        StringWriter sw = new StringWriter();
        PrintWriter psw = new PrintWriter(sw);
        outputTree.indentedListPrint(psw,false);
        pw.print(XMLUtils.escapeXML(sw.toString()));
        pw.println(".]");
        pw.println("  </tree>");
      }
      if (formats.containsKey("dependencies")) {
        Tree indexedTree = outputTree.deepCopy(outputTree.treeFactory(),
                                                 CoreLabel.factory());
        indexedTree.indexLeaves();
        Set<Dependency<Label, Label, Object>> depsSet = indexedTree.mapDependencies(dependencyWordFilter, hf);
        List<Dependency<Label, Label, Object>> sortedDeps = new ArrayList<Dependency<Label, Label, Object>>(depsSet);
        Collections.sort(sortedDeps, Dependencies.dependencyIndexComparator());
        pw.println("<dependencies style=\"untyped\">");
        for (Dependency<Label, Label, Object> d : sortedDeps) {
          pw.println(d.toString("xml"));
        }
        pw.println("</dependencies>");
      }
      if (formats.containsKey("conll2007") || formats.containsKey("conllStyleDependencies")) {
        System.err.println("The \"conll2007\" and \"conllStyleDependencies\" formats are ignored in xml.");
      }
      if (formats.containsKey("typedDependencies")) {
        GrammaticalStructure gs = gsf.newGrammaticalStructure(outputTree);
        if (basicDependencies) {
          print(gs.typedDependencies(), "xml", pw);
        }
        if (nonCollapsedDependencies || nonCollapsedDependenciesSeparated) {
          print(gs.allTypedDependencies(), "xml", pw);
        }
        if (collapsedDependencies) {
          print(gs.typedDependenciesCollapsed(true), "xml", pw);
        }
        if (CCPropagatedDependencies) {
          print(gs.typedDependenciesCCprocessed(true), "xml", pw);
        }
        if(treeDependencies) {
          print(gs.typedDependenciesCollapsedTree(), "xml", pw);
        }
      }
      if (formats.containsKey("typedDependenciesCollapsed")) {
        GrammaticalStructure gs = gsf.newGrammaticalStructure(outputTree);
        print(gs.typedDependenciesCCprocessed(true), "xml", pw);
      }

      // This makes parser require jgrapht.  Bad.
      // if (formats.containsKey("semanticGraph")) {
      //  SemanticGraph sg = SemanticGraph.makeFromTree(outputTree, true, false, false, null);
      //  pw.println(sg.toFormattedString());
      // }
    } else {
      // non-XML printing
      if (formats.containsKey("wordsAndTags")) {
        pw.println(Sentence.listToString(outputTree.taggedYield(), false));
        pw.println();
      }
      if (formats.containsKey("oneline")) {
        pw.println(outputPSTree.toString());
      }
      if (formats.containsKey("penn")) {
        outputPSTree.pennPrint(pw);
        pw.println();
      }
      if (formats.containsKey(rootLabelOnlyFormat)) {
        pw.println(outputTree.label());
      }
      if (formats.containsKey("latexTree")) {
        pw.println(".[");
        outputTree.indentedListPrint(pw,false);
        pw.println(".]");
      }
      if (formats.containsKey("dependencies")) {
        // NB: We need a CyclicCoreLabel here as we use	its "value-index" printing method
        Tree indexedTree = outputTree.deepCopy(outputTree.treeFactory(),
                                                 CyclicCoreLabel.factory());
        indexedTree.indexLeaves();
        Set<Dependency<Label, Label, Object>> depsSet = indexedTree.mapDependencies(dependencyWordFilter, hf);
        List<Dependency<Label, Label, Object>> sortedDeps = new ArrayList<Dependency<Label, Label, Object>>(depsSet);
        Collections.sort(sortedDeps, Dependencies.dependencyIndexComparator());
        for (Dependency<Label, Label, Object> d : sortedDeps) {
          pw.println(d.toString("predicate"));
        }
        pw.println();
      }
      if (formats.containsKey("conll2007")) {
        // CoNLL-X 2007 format: http://ilk.uvt.nl/conll/#dataformat
        // wsg: This code should be retained (and not subsumed into EnglishGrammaticalStructure) so
        //      that dependencies for other languages can be printed.
        // wsg2011: This code currently ignores the dependency label since the present implementation
        //          of mapDependencies() returns UnnamedDependency objects.

        Tree it = outputTree.deepCopy(outputTree.treeFactory(), CoreLabel.factory());
        it.indexLeaves();

        List<CoreLabel> tagged = it.taggedLabeledYield();
        Set<Dependency<Label, Label, Object>> depsSet = it.mapDependencies(dependencyFilter, hf, "root");
        List<Dependency<Label, Label, Object>> sortedDeps = new ArrayList<Dependency<Label, Label, Object>>(depsSet);
        Collections.sort(sortedDeps, Dependencies.dependencyIndexComparator());

        for (int i = 0; i < tagged.size(); i++) {
          CoreLabel w = tagged.get(i);
          Dependency<Label, Label, Object> d = sortedDeps.get(i);
          CoreMap dep = (CoreMap) d.dependent();
          CoreMap gov = (CoreMap) d.governor();

          Integer depi = dep.get(CoreAnnotations.IndexAnnotation.class);
          Integer govi = gov.get(CoreAnnotations.IndexAnnotation.class);

          // Used for both course and fine POS tag fields
          String tag = PTBTokenizer.ptbToken2Text(w.tag());

          String word = PTBTokenizer.ptbToken2Text(w.word());
          String lemma = "_";
          String feats = "_";
          String pHead = "_";
          String pDepRel = "_";
          String depRel = (govi == 0) ? "ROOT" : "NULL";

          // The 2007 format has 10 fields
          pw.printf("%d\t%s\t%s\t%s\t%s\t%s\t%d\t%s\t%s\t%s%n", depi,word,lemma,tag,tag,feats,govi,depRel,pHead,pDepRel);
        }
        pw.println();
      }
      if (formats.containsKey("conllStyleDependencies")) {
        // TODO: Rewrite this to output StanfordDependencies using EnglishGrammaticalStructure code
        BobChrisTreeNormalizer tn = new BobChrisTreeNormalizer();
        Tree indexedTree = outputTree.deepCopy(outputTree.treeFactory(),
                                                 CoreLabel.factory());
        // TODO: Can the below for-loop be deleted now?  (Now that the HeadFinder knows about NML.)
        for (Tree node : indexedTree) {
          if (node.label().value().startsWith("NML")) {
            node.label().setValue("NP");
          }
        }

        indexedTree = tn.normalizeWholeTree(indexedTree, outputTree.treeFactory());
        indexedTree.indexLeaves();
        Set<Dependency<Label, Label, Object>> depsSet = null;
        boolean failed = false;
        try {
          depsSet = indexedTree.mapDependencies(dependencyFilter, hf);
        } catch (Exception e) {
          failed = true;
        }
        if (failed) {
          System.err.println("failed: ");
          System.err.println(t);
          System.err.println();
        } else {
          Map<Integer,Integer> deps = new HashMap<Integer, Integer>();
          for (Dependency<Label, Label, Object> dep : depsSet) {
            CoreLabel child = (CoreLabel)dep.dependent();
            CoreLabel parent = (CoreLabel)dep.governor();
            Integer childIndex =
              child.get(CoreAnnotations.IndexAnnotation.class);
            Integer parentIndex =
              parent.get(CoreAnnotations.IndexAnnotation.class);
//            System.err.println(childIndex+"\t"+parentIndex);
            deps.put(childIndex, parentIndex);
          }
          boolean foundRoot = false;
          int index = 1;
          for (Tree node : indexedTree.getLeaves()) {
            String word = node.label().value();
            String tag = node.parent(indexedTree).label().value();
            int parent = 0;
            if (deps.containsKey(index)) {
              parent = deps.get(index);
            } else {
              if (foundRoot) { throw new RuntimeException(); }
              foundRoot = true;
            }
            pw.println(index+"\t"+word+"\t"+tag+"\t"+parent);
            index++;
          }
          pw.println();
        }
      }
      if (formats.containsKey("typedDependencies")) {
        GrammaticalStructure gs = gsf.newGrammaticalStructure(outputTree);
        if (basicDependencies) {
          print(gs.typedDependencies(), pw);
        }
        if (nonCollapsedDependencies) {
          print(gs.allTypedDependencies(), pw);
        }
        if (nonCollapsedDependenciesSeparated) {
          print(gs.allTypedDependencies(), "separator", pw);
        }
        if (collapsedDependencies) {
          print(gs.typedDependenciesCollapsed(true), pw);
        }
        if (CCPropagatedDependencies) {
          print(gs.typedDependenciesCCprocessed(true), pw);
        }
        if (treeDependencies) {
          print(gs.typedDependenciesCollapsedTree(), pw);
        }
      }
      if (formats.containsKey("typedDependenciesCollapsed")) {
        GrammaticalStructure gs = gsf.newGrammaticalStructure(outputTree);
        print(gs.typedDependenciesCCprocessed(true), pw);
      }
      // This makes parser require jgrapht.  Bad
      // if (formats.containsKey("semanticGraph")) {
      //  SemanticGraph sg = SemanticGraph.makeFromTree(outputTree, true, false, false, null);
      //  pw.println(sg.toFormattedString());
      // }
    }

    // flush to make sure we see all output
    pw.flush();
  }


  /** For the input tree, collapse any collocations in it that exist in
   *  WordNet and are contiguous in the tree into a single node.
   *  A single static Wordnet connection is used by all instances of this
   *  class.  Reflection to check that a Wordnet connection exists.  Otherwise
   *  we print an error and do nothing.
   *
   *  @param tree The input tree.  NOTE: This tree is mangled by this method
   *  @param hf The head finder to use
   *  @return The collocation collapsed tree
   */
  private static synchronized Tree getCollocationProcessedTree(Tree tree,
                                                               HeadFinder hf) {
    if (wnc == null) {
      try {
        Class<?> cl = Class.forName("edu.stanford.nlp.trees.WordNetInstance");
        wnc = (WordNetConnection) cl.newInstance();
      } catch (Exception e) {
        System.err.println("Couldn't open WordNet Connection.  Aborting collocation detection.");
        e.printStackTrace();
        wnc = null;
      }
    }
    if (wnc != null) {
      CollocationFinder cf = new CollocationFinder(tree, wnc, hf);
      tree = cf.getMangledTree();
    } else {
      System.err.println("ERROR: WordNetConnection unavailable for collocations.");
    }
    return tree;
  }


  public void printHeader(PrintWriter pw, String charset) {
    if (propertyToBoolean(options, "xml")) {
      pw.println("<?xml version=\"1.0\" encoding=\"" + charset + "\"?>");
      pw.println("<corpus>");
    }
  }


  public void printFooter(PrintWriter pw) {
    if (propertyToBoolean(options, "xml")) {
      pw.println("</corpus>");
    }
  }


  public Tree markHeadNodes(Tree t) {
    return markHeadNodes(t, null);
  }

  private Tree markHeadNodes(Tree t, Tree head) {
    if (t.isLeaf()) {
      return t; // don't worry about head-marking leaves
    }
    Label newLabel;
    if (t == head) {
      newLabel = headMark(t.label());
    } else {
      newLabel = t.label();
    }
    Tree newHead = hf.determineHead(t);
    return t.treeFactory().newTreeNode(newLabel, Arrays.asList(headMarkChildren(t, newHead)));
  }

  private static Label headMark(Label l) {
    Label l1 = l.labelFactory().newLabel(l);
    l1.setValue(l1.value() + headMark);
    return l1;
  }

  private Tree[] headMarkChildren(Tree t, Tree head) {
    Tree[] kids = t.children();
    Tree[] newKids = new Tree[kids.length];
    for (int i = 0, n = kids.length; i < n; i++) {
      newKids[i] = markHeadNodes(kids[i], head);
    }
    return newKids;
  }

  /** This provides a simple main method for calling TreePrint.
   *  Flags supported are:
   *  <ol>
   *  <li> -format format (like -outputFormat of parser, default "penn")
   *  <li> -options options (like -outputFormatOptions of parser, default "")
   *  <li> -tLP class (the TreebankLanguagePack, default "edu.stanford.nlp.tree.PennTreebankLanguagePack")
   *  <li> -hf class (the HeadFinder, default, the one in the class specified by -tLP)
   *  <li> -useTLPTreeReader (use the treeReaderFactory() inside
   *       the -tLP class; otherwise a PennTreeReader with no normalization is used)
   *  </ol>
   *  The single argument should be a file containing Trees in the format that is either
   *  Penn Treebank s-expressions or as specified by -useTLPTreeReader and the -tLP class,
   *  or if there is no such argument, trees are read from stdin and the program runs as a
   *  filter.
   *
   *  @param args Command line arguments, as above.
   */
  public static void main(String[] args) {
    String format = "penn";
    String options = "";
    String tlpName = "edu.stanford.nlp.trees.PennTreebankLanguagePack";
    String hfName = null;
    Map<String,Integer> flagMap = new HashMap<String,Integer>();
    flagMap.put("-format", 1);
    flagMap.put("-options", 1);
    flagMap.put("-tLP", 1);
    flagMap.put("-hf", 1);
    Map<String,String[]> argsMap = StringUtils.argsToMap(args,flagMap);
    args = argsMap.get(null);
    if(argsMap.keySet().contains("-format")) {
      format = argsMap.get("-format")[0];
    }
    if(argsMap.keySet().contains("-options")) {
      options = argsMap.get("-options")[0];
    }
    if (argsMap.keySet().contains("-tLP")) {
      tlpName = argsMap.get("-tLP")[0];
    }
    if (argsMap.keySet().contains("-hf")) {
      hfName = argsMap.get("-hf")[0];
    }
    TreebankLanguagePack tlp;
    try {
      tlp = (TreebankLanguagePack) Class.forName(tlpName).newInstance();
    } catch (Exception e) {
      e.printStackTrace();
      return;
    }
    HeadFinder hf;
    if (hfName != null) {
      try {
        hf = (HeadFinder) Class.forName(hfName).newInstance();
      } catch (Exception e) {
        e.printStackTrace();
        return;
      }
    } else {
      hf = tlp.headFinder();
    }
    TreePrint print = new TreePrint(format, options, tlp, (hf == null) ? tlp.headFinder(): hf, tlp.typedDependencyHeadFinder());
    Iterator<Tree> i; // initialized below
    if (args.length > 0) {
      Treebank trees; // initialized below
      TreeReaderFactory trf;
      if (argsMap.keySet().contains("-useTLPTreeReader")) {
        trf = tlp.treeReaderFactory();
      } else {
        trf = new TreeReaderFactory() {
          public TreeReader newTreeReader(Reader in) {
            return new PennTreeReader(in, new LabeledScoredTreeFactory(new StringLabelFactory()), new TreeNormalizer());
          }
        };
      }
      trees = new DiskTreebank(trf);
      trees.loadPath(args[0]);
      i = trees.iterator();
    } else {
      i = tlp.treeTokenizerFactory().getTokenizer(new BufferedReader(new InputStreamReader(System.in)));
    }
    while(i.hasNext()) {
      print.printTree(i.next());
    }
  }

  /**
   * NO OUTSIDE USE
   * Returns a String representation of the result of this set of
   * typed dependencies in a user-specified format.
   * Currently, three formats are supported:
   * <dl>
   * <dt>"plain"</dt>
   * <dd>(Default.)  Formats the dependencies as logical relations,
   * as exemplified by the following:
   * <pre>
   *  nsubj(died-1, Sam-0)
   *  tmod(died-1, today-2)
   *  </pre>
   * </dd>
   * <dt>"readable"</dt>
   * <dd>Formats the dependencies as a table with columns
   * <code>dependent</code>, <code>relation</code>, and
   * <code>governor</code>, as exemplified by the following:
   * <pre>
   *  Sam-0               nsubj               died-1
   *  today-2             tmod                died-1
   *  </pre>
   * </dd>
   * <dt>"xml"</dt>
   * <dd>Formats the dependencies as XML, as exemplified by the following:
   * <pre>
   *  &lt;dependencies&gt;
   *    &lt;dep type="nsubj"&gt;
   *      &lt;governor idx="1"&gt;died&lt;/governor&gt;
   *      &lt;dependent idx="0"&gt;Sam&lt;/dependent&gt;
   *    &lt;/dep&gt;
   *    &lt;dep type="tmod"&gt;
   *      &lt;governor idx="1"&gt;died&lt;/governor&gt;
   *      &lt;dependent idx="2"&gt;today&lt;/dependent&gt;
   *    &lt;/dep&gt;
   *  &lt;/dependencies&gt;
   *  </pre>
   * </dd>
   * </dl>
   *
   * @param dependencies The TypedDependencies to print
   * @param format a <code>String</code> specifying the desired format
   * @return a <code>String</code> representation of the typed
   *         dependencies in this <code>GrammaticalStructure</code>
   */
  private static String toString(Collection<TypedDependency> dependencies, String format) {
    if (format != null && format.equals("xml")) {
      return toXMLString(dependencies);
    } else if (format != null && format.equals("readable")) {
      return toReadableString(dependencies);
    } else if (format != null && format.equals("separator")) {
        return toString(dependencies, true);
    } else {
      return toString(dependencies, false);
    }
  }

  /**
   * NO OUTSIDE USE
   * Returns a String representation of this set of typed dependencies
   * as exemplified by the following:
   * <pre>
   *  tmod(died-6, today-9)
   *  nsubj(died-6, Sam-3)
   *  </pre>
   *
   * @param dependencies The TypedDependencies to print
   * @param extraSep boolean indicating whether the extra dependencies have to be printed separately, after the basic ones
   * @return a <code>String</code> representation of this set of
   *         typed dependencies
   */
  private static String toString(Collection<TypedDependency> dependencies, boolean extraSep) {
    StringBuilder buf = new StringBuilder();
    if (extraSep) {
      List<TypedDependency> extraDeps =  new ArrayList<TypedDependency>();
      for (TypedDependency td : dependencies) {
        if (td.extra()) {
          extraDeps.add(td);
        }
        else {
          buf.append(td.toString()).append('\n');
        }
      }
      // now we print the separator for extra dependencies, and print these if there are some
      if (!extraDeps.isEmpty()) {
        buf.append("======\n");
        for (TypedDependency td : extraDeps) {
          buf.append(td.toString()).append('\n');
        }
      }
    } else {
      for (TypedDependency td : dependencies) {
        buf.append(td.toString()).append('\n');
      }
    }
    return buf.toString();
  }

  // NO OUTSIDE USE
  private static String toReadableString(Collection<TypedDependency> dependencies) {
    StringBuilder buf = new StringBuilder();
    buf.append(String.format("%-20s%-20s%-20s%n", "dep", "reln", "gov"));
    buf.append(String.format("%-20s%-20s%-20s%n", "---", "----", "---"));
    for (TypedDependency td : dependencies) {
      buf.append(String.format("%-20s%-20s%-20s%n", td.dep(), td.reln(), td.gov()));
    }
    return buf.toString();
  }

  // NO OUTSIDE USE
  private static String toXMLString(Collection<TypedDependency> dependencies) {
    StringBuilder buf = new StringBuilder("<dependencies style=\"typed\">\n");
    for (TypedDependency td : dependencies) {
      String reln = td.reln().toString();
      String gov = td.gov().value();
      int govIdx = td.gov().index();
      String dep = td.dep().value();
      int depIdx = td.dep().index();
      boolean extra = td.extra();
      // add an attribute if the node is a copy
      // (this happens in collapsing when different prepositions are conjuncts)
      String govCopy = "";
      Integer copyGov = td.gov().label.get(CopyAnnotation.class);
      if (copyGov != null) {
        govCopy = " copy=\"" + copyGov + "\"";
      }
      String depCopy = "";
      Integer copyDep = td.dep().label.get(CopyAnnotation.class);
      if (copyDep != null) {
        depCopy = " copy=\"" + copyDep + "\"";
      }
      // add an attribute if the typed dependency is an extra relation (do not preserve the tree structure)
      String extraAttr = "";
      if (extra) {
        extraAttr = " extra=\"yes\"";
      }
      buf.append("  <dep type=\"").append(XMLUtils.escapeXML(reln)).append('\"').append(extraAttr).append(">\n");
      buf.append("    <governor idx=\"").append(govIdx).append('\"').append(govCopy).append('>').append(XMLUtils.escapeXML(gov)).append("</governor>\n");
      buf.append("    <dependent idx=\"").append(depIdx).append('\"').append(depCopy).append('>').append(XMLUtils.escapeXML(dep)).append("</dependent>\n");
      buf.append("  </dep>\n");
    }
    buf.append("</dependencies>");
    return buf.toString();
  }

  /**
   * USED BY TREEPRINT AND WSD.SUPWSD.PREPROCESS
   * Prints this set of typed dependencies to the specified
   * <code>PrintWriter</code>.
   * @param dependencies The collection of TypedDependency to print
   * @param pw Where to print them
   */
  public static void print(Collection<TypedDependency> dependencies, PrintWriter pw) {
    pw.println(toString(dependencies, false));
  }

  /**
   * USED BY TREEPRINT
   * Prints this set of typed dependencies to the specified
   * <code>PrintWriter</code> in the specified format.
   * @param dependencies The collection of TypedDependency to print
   * @param format "xml" or "readable" or other
   * @param pw Where to print them
   */
  public static void print(Collection<TypedDependency> dependencies, String format, PrintWriter pw) {
    pw.println(toString(dependencies, format));
  }

}
