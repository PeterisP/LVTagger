package edu.stanford.nlp.ie;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ie.ner.CMMClassifier;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.DistSimAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LVGazAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LVGazFileAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Merges the outputs of two or more AbstractSequenceClassifiers according to
 * a simple precedence scheme: any given base classifier contributes only
 * classifications of labels that do not exist in the base classifiers specified
 * before, and that do not have any token overlap with labels assigned by
 * higher priority classifiers.
 * <p>
 * This is a pure AbstractSequenceClassifier, i.e., it sets the AnswerAnnotation label.
 * If you work with NER classifiers, you should use NERClassifierCombiner. This class
 * inherits from ClassifierCombiner, and takes care that all AnswerAnnotations are also
 * copied to NERAnnotation.
 * <p>
 * You can specify up to 10 base classifiers using the -loadClassifier1 to -loadClassifier10
 * properties. We also maintain the older usage when only two base classifiers were accepted,
 * specified using -loadClassifier and -loadAuxClassifier.
 * <p>
 * ms 2009: removed all NER functionality (see NERClassifierCombiner), changed code so it accepts an arbitrary number of base classifiers, removed dead code.
 *
 * @author Chris Cox
 * @author Mihai Surdeanu
 */
public class ClassifierCombiner<IN extends CoreMap & HasWord> extends AbstractSequenceClassifier<IN> {

	private static final boolean DEBUG = false;
	private List<AbstractSequenceClassifier<IN>> baseClassifiers;

	private static final String DEFAULT_AUX_CLASSIFIER_PATH="/u/nlp/data/ner/goodClassifiers/english.muc.7class.distsim.crf.ser.gz";
	private static final String DEFAULT_CLASSIFIER_PATH="/u/nlp/data/ner/goodClassifiers/english.all.3class.distsim.crf.ser.gz";

	/**
	 * @param p Properties File that specifies <code>loadClassifier</code>
	 * and <code>loadAuxClassifier</code> properties or, alternatively, <code>loadClassifier[1-10]</code> properties.
	 * @throws FileNotFoundException If classifier files not found
	 */
	public ClassifierCombiner(Properties p) throws FileNotFoundException {
		super(p);
		String loadPath1, loadPath2;
		List<String> paths = new ArrayList<String>();

		//
		// preferred configuration: specify up to 10 base classifiers using loadClassifier1 to loadClassifier10 properties
		//
		if((loadPath1 = p.getProperty("loadClassifier1")) != null && (loadPath2 = p.getProperty("loadClassifier2")) != null) {
			paths.add(loadPath1);
			paths.add(loadPath2);
			for(int i = 3; i <= 10; i ++){
				String path;
				if ((path = p.getProperty("loadClassifier" + i)) != null) {
					paths.add(path);
				}
			}
			loadClassifiers(paths);
		}

		//
		// second accepted setup (backward compatible): two classifier given in loadClassifier and loadAuxClassifier
		//
		else if((loadPath1 = p.getProperty("loadClassifier")) != null && (loadPath2 = p.getProperty("loadAuxClassifier")) != null){
			paths.add(loadPath1);
			paths.add(loadPath2);
			loadClassifiers(paths);
		}

		//
		// fall back strategy: use the two default paths on NLP machines
		//
		else {
			paths.add(DEFAULT_CLASSIFIER_PATH);
			paths.add(DEFAULT_AUX_CLASSIFIER_PATH);
			loadClassifiers(paths);
		}
	}

	/** Loads a series of base classifiers from the paths specified.
	 *
	 * @param loadPaths Paths to the base classifiers
	 * @throws FileNotFoundException If classifier files not found
	 */
	public ClassifierCombiner(String... loadPaths) throws FileNotFoundException {
		super(new Properties());
		List<String> paths = new ArrayList<String>(Arrays.asList(loadPaths));
		loadClassifiers(paths);
	}


	/** Combines a series of base classifiers
	 *
	 * @param classifiers The base classifiers
	 */
	public ClassifierCombiner(AbstractSequenceClassifier<IN>... classifiers) {
		super(new Properties());
		baseClassifiers = new ArrayList<AbstractSequenceClassifier<IN>>(Arrays.asList(classifiers));
		flags.backgroundSymbol = baseClassifiers.get(0).flags.backgroundSymbol;
	}
	
	/** Combines a series of base classifiers
	*
	* @param classifiers The base classifiers
	*/
 public ClassifierCombiner(List<AbstractSequenceClassifier<IN>> classifiers) {
	 super(new Properties());
	 baseClassifiers = classifiers;
	 flags.backgroundSymbol = baseClassifiers.get(0).flags.backgroundSymbol;
 }


	private void loadClassifiers(List<String> paths) throws FileNotFoundException {
		baseClassifiers = new ArrayList<AbstractSequenceClassifier<IN>>();
		for(String path: paths){
			AbstractSequenceClassifier<IN> cls = loadClassifierFromPath(path);
			baseClassifiers.add(cls);
			if(DEBUG){
				System.err.printf("Successfully loaded classifier #%d from %s.\n", baseClassifiers.size(), path);
			}
		}
		flags.backgroundSymbol = baseClassifiers.get(0).flags.backgroundSymbol;
	}


	public static <INN extends CoreMap & HasWord> AbstractSequenceClassifier<INN> loadClassifierFromPath(String path)
			throws FileNotFoundException {
		//try loading as a CRFClassifier
		try {
			 return ErasureUtils.uncheckedCast(CRFClassifier.getClassifier(path));
		} catch (Exception e) {
			e.printStackTrace();
		}
		//try loading as a CMMClassifier
		try {
			return ErasureUtils.uncheckedCast(CMMClassifier.getClassifier(path));
		} catch (Exception e) {
			//fail
			//System.err.println("Couldn't load classifier from path :"+path);
			FileNotFoundException fnfe = new FileNotFoundException();
			fnfe.initCause(e);
			throw fnfe;
		}
	}

	@Override
	public Set<String> labels() {
		Set<String> labs = new HashSet<String>();
		for(AbstractSequenceClassifier<? extends CoreMap> cls: baseClassifiers)
			labs.addAll(cls.labels());
		return labs;
	}


	/**
	 * Reads the Answer annotations in the given labellings (produced by the base models)
	 *	 and combines them using a priority ordering, i.e., for a given baseDocument all
	 *	 labellings seen before in the baseDocuments list have higher priority.
	 *	 Writes the answer to AnswerAnnotation in the labeling at position 0
	 *	 (considered to be the main document).
	 *
	 *	@param baseDocuments Results of all base AbstractSequenceClassifier models
	 *	@return A List of IN with the combined annotations.	(This is an
	 *		 updating of baseDocuments.get(0), not a new List.)
	 */
	private List<IN> mergeDocuments(List<List<IN>> baseDocuments){
		// we should only get here if there is something to merge
		assert(! baseClassifiers.isEmpty() && ! baseDocuments.isEmpty());
		// all base outputs MUST have the same length (we generated them internally!)
		for(int i = 1; i < baseDocuments.size(); i ++)
			assert(baseDocuments.get(0).size() == baseDocuments.get(i).size());

		// baseLabels.get(i) points to the labels assigned by baseClassifiers.get(i)
		List<Set<String>> baseLabels = new ArrayList<Set<String>>();
		Set<String> seenLabels = new HashSet<String>();
		for (AbstractSequenceClassifier<? extends CoreMap> baseClassifier : baseClassifiers) {
			Set<String> labs = baseClassifier.labels();
			labs.removeAll(seenLabels);
			//seenLabels.addAll(labs); // AZ allow next classifiers to add same label markups
			// TODO make this configurable
			baseLabels.add(labs);
		}
		String background = baseClassifiers.get(0).flags.backgroundSymbol;

		if (DEBUG) {
			for(int i = 0; i < baseLabels.size(); i ++)
				System.err.println("mergeDocuments: Using classifier #" + i + " for " + baseLabels.get(i));
			System.err.println("mergeDocuments: Background symbol is " + background);

			System.err.println("Base model outputs:");
			for( int i = 0; i < baseDocuments.size(); i ++){
				System.err.printf("Output of model #%d:", i);
				for (IN l : baseDocuments.get(i)) {
					System.err.print(' ');
					System.err.print(l.get(AnswerAnnotation.class));
				}
				System.err.println();
			}
		}

		// incrementally merge each additional model with the main model (i.e., baseDocuments.get(0))
		// this keeps adding labels from the additional models to mainDocument
		// hence, when all is done, mainDocument contains the labels of all base models
		List<IN> mainDocument = baseDocuments.get(0);
		for (int i = 1; i < baseDocuments.size(); i ++) {
			//mergeTwoDocuments(mainDocument, baseDocuments.get(i), baseLabels.get(i), background);
			mergeTwoDocumentsByLongestSequence(mainDocument, baseDocuments.get(i), baseLabels.get(i), background);
			
			// save extra annotation seen in aux documents
			for (int id = 0; id < mainDocument.size(); id++) {
				IN m = mainDocument.get(id);
				IN n = baseDocuments.get(i).get(id);
				if (n != null && m != null) {
					if (n.get(DistSimAnnotation.class) != null) m.set(DistSimAnnotation.class, n.get(DistSimAnnotation.class));
					if (n.get(LVGazAnnotation.class) != null) m.set(LVGazAnnotation.class, n.get(LVGazAnnotation.class));
					if (n.get(LVGazFileAnnotation.class) != null) m.set(LVGazFileAnnotation.class, n.get(LVGazFileAnnotation.class));
				}
			}
		}

		if (DEBUG) {
			System.err.print("Output of combined model:");
			for (IN l: mainDocument) {
				System.err.print(' ');
				System.err.print(l.get(AnswerAnnotation.class));
			}
			System.err.println();
			System.err.println();
		}

		return mainDocument;
	}


	/** This merges in labels from the auxDocument into the mainDocument when
	 *	tokens have one of the labels in auxLabels, and the subsequence
	 *	labeled with this auxLabel does not conflict with any non-background
	 *	labelling in the mainDocument.
	 */
	static <INN extends CoreMap & HasWord> void mergeTwoDocuments(List<INN> mainDocument, List<INN> auxDocument, Set<String> auxLabels, String background) {		
		boolean insideAuxTag = false;
		boolean auxTagValid = true;
		String prevAnswer = background;
		Collection<INN> constituents = new ArrayList<INN>();

		Iterator<INN> auxIterator = auxDocument.listIterator();

		for (INN wMain : mainDocument) {
			String mainAnswer = wMain.get(AnswerAnnotation.class);
			INN wAux = auxIterator.next();
			String auxAnswer = wAux.get(AnswerAnnotation.class);
			boolean insideMainTag = !mainAnswer.equals(background);

			/* if the auxiliary classifier gave it one of the labels unique to
				 auxClassifier, we might set the mainLabel to that. */
			if (auxLabels.contains(auxAnswer) && !auxAnswer.equals(background)) { //AZ not equal background symbol (it is background symbol not strong class)
				if ( ! prevAnswer.equals(auxAnswer) && ! prevAnswer.equals(background)) {
					if (auxTagValid){
						for (INN wi : constituents) {
							wi.set(AnswerAnnotation.class, prevAnswer);
						}
					}
					constituents = new ArrayList<INN>();
				}
				insideAuxTag = true;
				if (insideMainTag) { auxTagValid = false; }
				prevAnswer = auxAnswer;
				constituents.add(wMain);
			} else {
				if (insideAuxTag) {
					if (auxTagValid){
						for (INN wi : constituents) {
							wi.set(AnswerAnnotation.class, prevAnswer);
						}
					}
					constituents = new ArrayList<INN>();
				}
				insideAuxTag=false;
				auxTagValid = true;
				prevAnswer = background;
			}
		}
		// deal with a sequence final auxLabel
		if (auxTagValid){
			for (INN wi : constituents) {
				wi.set(AnswerAnnotation.class, prevAnswer);
			}
		}
	}
	
	
	/** 
	 * AZ
	 * This merges in labels from the auxDocument into the mainDocument.
	 * Conflicts are resolved by greedy choosing the longest label sequence from left.
	 */
	static <INN extends CoreMap & HasWord> void mergeTwoDocumentsByLongestSequence(
			List<INN> mainDocument, 
			List<INN> auxDocument, 
			Set<String> auxLabels, 
			String background ) {
		
		class Sequence {
			String type;
			int start;
			int len;
			Sequence (String type, int start, int len) {
				this.type = type;
				this.start = start;
				this.len = len;
			}
			public String toString() {
				return String.format("%s:%d:%d", type, start, len);
			}
		}
		
		List<Sequence> mainSeq = new ArrayList<>();
		List<Sequence> auxSeq = new ArrayList<>();
		
		String prevAnswer = "O";
		int len = 0;
		int id = -1;
		for (INN l : mainDocument) {
			id++;
			String answer = l.get(AnswerAnnotation.class);
			if (!answer.equals(prevAnswer)) {
				if (!prevAnswer.equals(background)) {
					mainSeq.add(new Sequence(prevAnswer, id-len, len));
				}
				if (!answer.equals(background)) {
					len = 1;
				}
				prevAnswer = answer;
			} else {
				len++;
			}			
		}
		if (!prevAnswer.equals(background)) mainSeq.add(new Sequence(prevAnswer, id-len+1, len));
		
		prevAnswer = "O";
		len = 0;
		id = -1;
		for (INN l : auxDocument) {
			id++;
			String answer = l.get(AnswerAnnotation.class);
			if (!answer.equals(prevAnswer)) {
				if (!prevAnswer.equals(background)) {
					auxSeq.add(new Sequence(prevAnswer, id-len, len));
				}
				if (!answer.equals(background)) {
					len = 1;
				}
				prevAnswer = answer;
			} else {
				len++;
			}			
		}
		if (!prevAnswer.equals(background)) auxSeq.add(new Sequence(prevAnswer, id-len+1, len));
		
		List<Sequence> resSeq = new ArrayList<>();
		Iterator<Sequence> mainSeqIt = mainSeq.iterator();
		Iterator<Sequence> auxSeqIt = auxSeq.iterator();
		Sequence main = null, aux = null, s = null;
		id = -1;
		if (mainSeqIt.hasNext()) main = mainSeqIt.next();
		if (auxSeqIt.hasNext()) aux = auxSeqIt.next();
		while(main != null || aux != null) {
			while (main != null && main.start < id) {
				if (mainSeqIt.hasNext()) main = mainSeqIt.next();
				else main = null;
			}
			
			while (aux != null && aux.start < id) {
				if (auxSeqIt.hasNext()) aux = auxSeqIt.next();
				else aux = null;
			}
			
			s = main;
			if (s == null 
					|| aux != null && (aux.start + aux.len - 1 < s.start
							|| s.len < aux.len
							&& (aux.start >= s.start && aux.start < s.start + s.len || s.start >= aux.start && s.start < aux.start + aux.len))) {
				s = aux;
			}
			if (s != null) {
				resSeq.add(s);
				id = s.start + s.len;
			}
		}
		
//		for (Sequence seq: mainSeq) System.err.print(seq + " ");
//		System.err.println();
//		for (Sequence seq: auxSeq) System.err.print(seq + " ");
//		System.err.println();
//		for (Sequence seq: resSeq) System.err.print(seq + " ");
//		System.err.println();
//		for (Sequence seq: resSeq) {
//			for (int i = seq.start; i < seq.start + seq.len; i++) {
//				System.err.printf("%s[%s/%s] ", mainDocument.get(i).word(), mainDocument.get(i).get(AnswerAnnotation.class), auxDocument.get(i).get(AnswerAnnotation.class));
//			}
//		}		
		for (INN l : mainDocument) l.set(AnswerAnnotation.class, background);
		for (Sequence seq : resSeq) {
			for (int i = seq.start; i < seq.start + seq.len; i++) {
				mainDocument.get(i).set(AnswerAnnotation.class, seq.type);
			}
		}
	}

	/**
	 * Generates the AnswerAnnotation labels of the combined model for the given
	 * tokens, storing them in place in the tokens.
	 *
	 * @param tokens A List of IN
	 * @return The passed in parameters, which will have the AnswerAnnotation field added/overwritten
	 */
	@Override
	public List<IN> classify(List<IN> tokens) {
		if (baseClassifiers.isEmpty()) {
			return tokens;
		}
		List<List<IN>> baseOutputs = new ArrayList<List<IN>>();

		// the first base model works in place, modifying the original tokens
		List<IN> output = baseClassifiers.get(0).classifySentence(tokens);
		// classify(List<IN>) is supposed to work in place, so add AnswerAnnotation to tokens!
		for (int i = 0, sz = output.size(); i < sz; i++) {
			tokens.get(i).set(AnswerAnnotation.class, output.get(i).get(AnswerAnnotation.class));
		}
		baseOutputs.add(tokens);

		for (int i = 1, sz = baseClassifiers.size(); i < sz; i ++) {
			//List<CoreLabel> copy = deepCopy(tokens);
			// no need for deep copy: classifySentence creates a copy of the input anyway
			// List<CoreLabel> copy = tokens;
			output = baseClassifiers.get(i).classifySentence(tokens);
			baseOutputs.add(output);
		}
		assert(baseOutputs.size() == baseClassifiers.size());
		List<IN> finalAnswer = mergeDocuments(baseOutputs);

		return finalAnswer;
	}


	@Override
	public void train(Collection<List<IN>> docs,
										DocumentReaderAndWriter<IN> readerAndWriter) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void printProbsDocument(List<IN> document) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void serializeClassifier(String serializePath) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void loadClassifier(ObjectInputStream in, Properties props) throws IOException, ClassCastException, ClassNotFoundException {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<IN> classifyWithGlobalInformation(List<IN> tokenSeq, CoreMap doc, CoreMap sent) {
		return classify(tokenSeq);
	}

	/**
	 * Some basic testing of the ClassifierCombiner.
	 *
	 * @param args Command-line arguments as properties: -loadClassifier1 serializedFile -loadClassifier2 serializedFile
	 * @throws Exception If IO or serialization error loading classifiers
	 */
	public static void main(String[] args) throws Exception {
		Properties props = StringUtils.argsToProperties(args);
		ClassifierCombiner ec = new ClassifierCombiner(props);

		System.err.println(ec.classifyToString("Marketing : Sony Hopes to Win Much Bigger Market For Wide Range of Small-Video Products --- By Andrew B. Cohen Staff Reporter of The Wall Street Journal"));
	
//		test_mergeTwoDocumentsByLongestSequence("O O X O O", "O Y Y Y O");
//		test_mergeTwoDocumentsByLongestSequence("O Y Y Y Y", "O O X X X");
//		test_mergeTwoDocumentsByLongestSequence("X X X O Z Z Z Z Z", "O Y Y Y Y O O O O");
//		test_mergeTwoDocumentsByLongestSequence("O Y Y Y Y O O O O", "X X X O Z Z Z Z Z");
//		test_mergeTwoDocumentsByLongestSequence("O O O O O", "O X X X O");
//		test_mergeTwoDocumentsByLongestSequence("O X X O O O", "O Y Y Y Z O");
//		test_mergeTwoDocumentsByLongestSequence("O Y Y Y Z O", "O X X O O O");
//		test_mergeTwoDocumentsByLongestSequence("O Y Y Y O O", "O O X X Z O");
//		test_mergeTwoDocumentsByLongestSequence("O Y Y Y O O", "O O X X Z Z");
//		test_mergeTwoDocumentsByLongestSequence("O X Y Z W", "A B B D E");
//		test_mergeTwoDocumentsByLongestSequence("O X O O O", "O O Y Y O");
//		test_mergeTwoDocumentsByLongestSequence("O O Y Y O", "O X O O O");
//		test_mergeTwoDocumentsByLongestSequence("O X X O O", "O O Y Y Y");
//		test_mergeTwoDocumentsByLongestSequence("O O Y Y Y", "O X X O O");
	}
	
	public static void test_mergeTwoDocumentsByLongestSequence(String doc1String, String doc2String) {
		System.err.println("----MERGE 2 docs by sequence length----");
		String[] doc1Arr = doc1String.split(" "), doc2Arr = doc2String.split(" ");
		List<CoreLabel> doc1 = new ArrayList<>(), doc2 = new ArrayList<>();
		for (String s: doc1Arr) { CoreLabel l = new CoreLabel(); l.set(AnswerAnnotation.class, s); doc1.add(l); }
		for (String s: doc2Arr) { CoreLabel l = new CoreLabel(); l.set(AnswerAnnotation.class, s); doc2.add(l); }
		for (CoreLabel l : doc1) {System.err.print(l.getString(AnswerAnnotation.class));} System.err.println();
		for (CoreLabel l : doc2) {System.err.print(l.getString(AnswerAnnotation.class));} System.err.println();
		mergeTwoDocumentsByLongestSequence(doc1, doc2, null, "O");
		for (CoreLabel l : doc1) {System.err.print(l.getString(AnswerAnnotation.class));} System.err.println();
	}

}
