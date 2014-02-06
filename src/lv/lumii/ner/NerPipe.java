package lv.lumii.ner;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.ListNERSequenceClassifier;
import edu.stanford.nlp.ie.NERClassifierCombiner;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ie.regexp.RegexNERSequenceClassifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.sequences.LVCoNLLDocumentReaderAndWriter;
import edu.stanford.nlp.util.StringUtils;

/**
 * @author Artūrs
 *
 */
public class NerPipe {
	private static enum inputTypes {SENTENCE, CONLL};
	private static enum outputTypes {CONLL_X, SIMPLE, INFEATURES};
	
	public Properties props;
	public NERClassifierCombiner classifier;
	public DocumentReaderAndWriter<CoreLabel> defaultReaderWriter;
					
	private static inputTypes inputType = inputTypes.SENTENCE;
	private static outputTypes outputType = outputTypes.CONLL_X;
	
	private static String defaultCrfClassifier; // = "lv-ner-model.ser.gz" removed for testing, use loadClassifier in properties file
	
	@SuppressWarnings("unchecked")
	NerPipe(Properties props) throws ClassCastException, ClassNotFoundException, IOException {
		this.props = props;
		initializeFromProperties();
		
		List<AbstractSequenceClassifier<CoreLabel>> classifiers = new ArrayList<>();
		
		if (props.containsKey("whiteList")) classifiers.add(new ListNERSequenceClassifier(props.getProperty("whiteList"), true, true));
		if (defaultCrfClassifier != null) classifiers.add(CRFClassifier.getClassifier(defaultCrfClassifier, props));
		if (props.containsKey("regexList")) classifiers.add(new RegexNERSequenceClassifier(props.getProperty("regexList"), true, true));

		classifier = new NERClassifierCombiner(classifiers);
		defaultReaderWriter = new LVCoNLLDocumentReaderAndWriter();
		defaultReaderWriter.init(classifier.flags);
	}
	
	public void setDefaultClassifier(NERClassifierCombiner classifier) {		
		this.classifier = classifier;
	}
	
	NerPipe (Properties props, AbstractSequenceClassifier<CoreLabel> classifier) throws FileNotFoundException {
		this(props, new NERClassifierCombiner(classifier));
	}
	
	NerPipe(Properties props, NERClassifierCombiner nerClassifier) {
		this.props = props;
		classifier = nerClassifier;
		defaultReaderWriter = new LVCoNLLDocumentReaderAndWriter();
	}

	void initializeFromProperties() {	
		if (props.getProperty("conll-in") != null) LVCoNLLDocumentReaderAndWriter.inputType = LVCoNLLDocumentReaderAndWriter.inputTypes.CONLL;
		if (props.getProperty("conll-x") != null) LVCoNLLDocumentReaderAndWriter.outputType = LVCoNLLDocumentReaderAndWriter.outputTypes.CONLL;
		if (props.getProperty("simple") != null) LVCoNLLDocumentReaderAndWriter.outputType = LVCoNLLDocumentReaderAndWriter.outputTypes.SIMPLE;
		if (props.getProperty("toFeatures") != null) LVCoNLLDocumentReaderAndWriter.outputType = LVCoNLLDocumentReaderAndWriter.outputTypes.INFEATURES;
		if (props.getProperty("saveExtraColumns") != null) LVCoNLLDocumentReaderAndWriter.saveExtraColumns = true;
		if (props.getProperty("loadClassifier") != null) defaultCrfClassifier = props.getProperty("loadClassifier");
		
		switch(outputType) {
		case SIMPLE:
			LVCoNLLDocumentReaderAndWriter.outputType = LVCoNLLDocumentReaderAndWriter.outputTypes.SIMPLE;
			break;
		case INFEATURES:
			LVCoNLLDocumentReaderAndWriter.outputType = LVCoNLLDocumentReaderAndWriter.outputTypes.INFEATURES;
		default:				
	}
	
	}
	
	void setReaderWriter(DocumentReaderAndWriter<CoreLabel> readerWriter) {
		this.defaultReaderWriter = readerWriter;
	}
	
	public void classifyDocumentStdin(DocumentReaderAndWriter<CoreLabel> readerWriter)
			throws IOException 
	{
		classifier.classifyDocumentStdin(readerWriter);
	}	
		
	public void classifyDocumentStdin() throws IOException {
		classifyDocumentStdin(defaultReaderWriter);
	}
	
	List<CoreLabel> classify(List<CoreLabel> document) {
		classifier.classify(document);
		return document;
	}
	
	ObjectBank<List<CoreLabel>> classify(ObjectBank<List<CoreLabel>> documents) {
		for (List<CoreLabel> doc : documents) {
			System.out.println(doc);
		    classifier.classify(doc);
		}
		return documents;
	}

	public List<CoreLabel> classify(String filename) {
		ObjectBank<List<CoreLabel>> ob = classifier.makeObjectBankFromFile(filename, defaultReaderWriter);
		List<CoreLabel> res = new ArrayList<CoreLabel>();		
		for (List<CoreLabel> doc : ob) {
		      classifier.classify(doc);
		      res.addAll(doc);
		}
		return res;
	}
	
	void writeAnswers(List<CoreLabel> doc, DocumentReaderAndWriter<CoreLabel> writer) {
		try {
			classifier.writeAnswers(doc,
			        IOUtils.encodedOutputStreamPrintWriter(System.out, "utf-8", true), writer);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	void writeAnswers(ObjectBank<List<CoreLabel>> documents, DocumentReaderAndWriter<CoreLabel> writer) {
		try {
			for(List<CoreLabel> doc : documents) {
				classifier.writeAnswers(doc,
			        IOUtils.encodedOutputStreamPrintWriter(System.out, "utf-8", true), writer);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) throws IOException, ClassCastException, ClassNotFoundException {
		Properties props = new Properties();
		props = StringUtils.argsToProperties(args);	
		if (props.containsKey("h") || props.containsKey("help") || props.containsKey("?")) {
			System.out.println("LV Named Entity Recogniser");
			System.out.println("\nInput formats");
			System.out.println("\tDefault : conll-in");
			System.out.println("\t-conll-in : CONLL shared task data format - one line per token, with tab-delimited columns, sentences separated by blank lines.");
			System.out.println("\nOutput formats");
			System.out.println("\tDefault : conll-x");
			System.out.println("\t-conll-x : CONLL-X shared task data format - one line per token, with tab-delimited columns, sentences separated by blank lines.");;
			System.out.println("\t-simple : Simple compare format used for ner analysis");
			System.out.println("\t-toFeatures : add ner key and value to morphoFeature string");
			System.out.println("\nOther options:");
			System.out.println("\t-saveExtraColumns : save extra columns after typical conll input (6 columns)");
			System.out.println("\t-whiteList : files containing white list named entities (separated by comma)");
			System.out.flush();
			System.exit(0);
		}
		NerPipe ner = new NerPipe(props);
		ner.classifyDocumentStdin();
		
		//CRFClassifier<CoreLabel> classifier = CRFClassifier.getClassifier(NerPipe.defaultCrfClassifier, props);
		///LVCoNLLDocumentReaderAndWriter lvconll = new LVCoNLLDocumentReaderAndWriter();
		//lvconll.init(classifier.flags);
//		ObjectBank<List<CoreLabel>> b = classifier.makeObjectBankFromFile("z_tomins.conll",lvconll);
//		classifier.printProbsDocuments(b);
//		classifier.printFirstOrderProbsDocuments(b);
		//try {classifier.printLabelInformation("z_tomins.conll", lvconll);} catch (Exception e) {e.printStackTrace();}
	}

	
//	private static void outputSentence(CRFClassifier<CoreLabel> crf,
//			PrintStream out, List<CoreLabel> sentence) {
//		sentence = crf.classify(sentence);
//		switch (outputType) {
//		default: 
//			out.println( output_CONLL(sentence) );    
//		}
//		out.flush();
//	}	
	
//	private static String output_CONLL(List<CoreLabel> tokens){
//		StringBuilder s = new StringBuilder();
//		int counter = 1;
//		String eol = System.getProperty("line.separator");
//		for (CoreLabel word : tokens) {
//			String token = word.getString(TextAnnotation.class);
//			if (token.contains("<s>")) continue;
//			token = token.replace(' ', '_');
//			
//			s.append(Integer.toString(counter));
//			s.append('\t');
//			s.append(token);
//			s.append('\t');
//			s.append(word.getString(LemmaAnnotation.class));
//			s.append('\t');
//			s.append(word.tag());
//			s.append('\t');
//			s.append(word.getString(FullTagAnnotation.class));
//			s.append('\t');
//			s.append(word.getString(MorphologyFeatureStringAnnotation.class));
//			s.append('\t');
//			String syntax = word.getString(ConllSyntaxAnnotation.class);
//			if (syntax != null) {
//				s.append(syntax);
//			}
//			else s.append("_\t_\t_\t_\t");
//			s.append(word.getString(AnswerAnnotation.class));
//			s.append('\t');
//			s.append(eol);
//			counter++;
//		}
//		
//		return s.toString();
//	}
	
//	public static List<List<CoreLabel>> readCONLL(BufferedReader in, CRFClassifier<CoreLabel> nerClassifier) throws IOException {
//		String s;
//	    List<CoreLabel> sentence = new LinkedList<CoreLabel>();
//	    List<List<CoreLabel>> result = new LinkedList<List<CoreLabel>>();
//		
//		boolean empty = true; // no words in the input
//	    
//	    while ((s = in.readLine()) != null) {
//	    	if (s.trim().length() > 0) {
//	    		String[] fields = s.split("\t");
//	    		String token = fields[1];
//	    		String lemma = fields[2];
//	    		String tag = fields[3];
//	    		String fullTag = fields[4];
//	    		String morphoFeatures = fields[5];
//	    		
//	    		//if (token.contains("<s>")) continue;
//
//	    		CoreLabel word = new CoreLabel(); 
//	    		if (!token.equalsIgnoreCase("_")) token = token.replace('_', ' ');
//	    		
//				word.set(TextAnnotation.class, token);
//				word.setLemma(lemma);
//				word.setTag(fullTag.substring(0,1));
//				word.set(FullTagAnnotation.class, fullTag);
//				word.set(MorphologyFeatureStringAnnotation.class, morphoFeatures);
//				
//				word.set(ShapeAnnotation.class, WordShapeClassifier.wordShape(token, nerClassifier.flags.wordShape)); //nepieliek zināmos LC vārdus
//	
//	    		if (fields.length > 8) {
//	    			String syntax = fields[6] + "\t" + fields[7] + "\t" + fields[8] + "\t" + fields[9];
//	    			word.set(ConllSyntaxAnnotation.class, syntax);
//	    		}	    		
//	    		sentence.add(word);
//	    		empty = false;
//	    	} else {
//	    		result.add(sentence); 		
//	    		sentence = new LinkedList<CoreLabel>();
//	    		break; // stop reading sentence at first blank line
//	    	}
//	    }
//	    if (sentence.size() > 0) {
//	    	result.add(sentence);
//	    }
//	    if (empty) return null;
//		return result;
//	}

}
