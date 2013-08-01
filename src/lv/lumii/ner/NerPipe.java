package lv.lumii.ner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ConllSyntaxAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.FullTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.MorphologyFeatureStringAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ShapeAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.process.WordShapeClassifier;
import edu.stanford.nlp.sequences.LVCoNLLDocumentReaderAndWriter;
import edu.stanford.nlp.sequences.LVCoNLLDocumentReaderAndWriter;
import edu.stanford.nlp.sequences.ObjectBankWrapper;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;

/**
 * @author Artūrs
 *
 */
public class NerPipe {
	
	
	private enum inputTypes {SENTENCE, CONLL};
	private enum outputTypes {CONLL_X};

	private static String eol = System.getProperty("line.separator");
	private static String field_separator = "\t";
	private static String token_separator = eol;
		
//	private static boolean features = false;		
	private static inputTypes inputType = inputTypes.SENTENCE;
	private static outputTypes outputType = outputTypes.CONLL_X;
	
	private static String loadClassifier = "lv-ner-model.ser.gz";
	
	
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
			System.out.println("\nOther options:");
			System.out.flush();
			System.exit(0);
		}
		if (props.getProperty("conll-in") != null) inputType = inputTypes.CONLL;
		if (props.getProperty("conll-x") != null) outputType = outputTypes.CONLL_X;
		if (props.getProperty("loadClassifier") != null) loadClassifier = props.getProperty("loadClassifier");

		edu.stanford.nlp.util.PropertiesUtils.printProperties("LV Named Entity Recogniser", props, System.err);	
		
		CRFClassifier<CoreLabel> nerClassifier = CRFClassifier.getClassifier(loadClassifier, props);
		
		PrintStream out = new PrintStream(System.out, true, "UTF-8");
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
		
		
		LVCoNLLDocumentReaderAndWriter conllReader = new LVCoNLLDocumentReaderAndWriter();
		conllReader.init(nerClassifier.flags);
		
		switch(inputType) {
		default:
//			nerClassifier.classifyAndWriteAnswers("tmp.in", conllReader);
//			ObjectBank<List<CoreLabel>> b = nerClassifier.makeObjectBankFromFile("tmp.in", conllReader);
//			nerClassifier.printProbsDocuments(b);
//			try {
//				nerClassifier.printLabelInformation("tmp.in", conllReader);
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
			while(nerClassifier.classifySentenceStdin(conllReader)) {
//				List<List<CoreLabel>> sentences = readCONLL(in, nerClassifier);
//				if (sentences == null) break;
//				for (List<CoreLabel> sentence : sentences) {
//			    	outputSentence(nerClassifier, out, sentence);
//				}
			}
		}
		in.close();
		out.close();
	}
	
	private static void outputSentence(CRFClassifier<CoreLabel> crf,
			PrintStream out, List<CoreLabel> sentence) {
		sentence = crf.classify(sentence);
		switch (outputType) {
		default: 
			out.println( output_CONLL(sentence) );    
		}
		out.flush();
	}	
	
	private static String output_CONLL(List<CoreLabel> tokens){
		StringBuilder s = new StringBuilder();
		int counter = 1;
		for (CoreLabel word : tokens) {
			String token = word.getString(TextAnnotation.class);
			if (token.contains("<s>")) continue;
			token = token.replace(' ', '_');
			
			s.append(Integer.toString(counter));
			s.append('\t');
			s.append(token);
			s.append('\t');
			s.append(word.getString(LemmaAnnotation.class));
			s.append('\t');
			s.append(word.tag());
			s.append('\t');
			s.append(word.getString(FullTagAnnotation.class));
			s.append('\t');
			s.append(word.getString(MorphologyFeatureStringAnnotation.class));
			s.append('\t');
			String syntax = word.getString(ConllSyntaxAnnotation.class);
			if (syntax != null) {
				s.append(syntax);
			}
			else s.append("_\t_\t_\t_\t");
			s.append(word.getString(AnswerAnnotation.class));
			s.append('\t');
			s.append(eol);
			counter++;
		}
		
		return s.toString();
	}
	
	public static List<List<CoreLabel>> readCONLL(BufferedReader in, CRFClassifier<CoreLabel> nerClassifier) throws IOException {
		String s;
	    List<CoreLabel> sentence = new LinkedList<CoreLabel>();
	    List<List<CoreLabel>> result = new LinkedList<List<CoreLabel>>();
		
		boolean empty = true; // no words in the input
	    
	    while ((s = in.readLine()) != null) {
	    	if (s.trim().length() > 0) {
	    		String[] fields = s.split("\t");
	    		String token = fields[1];
	    		String lemma = fields[2];
	    		String tag = fields[3];
	    		String fullTag = fields[4];
	    		String morphoFeatures = fields[5];
	    		
	    		//if (token.contains("<s>")) continue;

	    		CoreLabel word = new CoreLabel(); 
	    		if (!token.equalsIgnoreCase("_")) token = token.replace('_', ' ');
	    		
				word.set(TextAnnotation.class, token);
				word.setLemma(lemma);
				word.setTag(fullTag.substring(0,1));
				word.set(FullTagAnnotation.class, fullTag);
				word.set(MorphologyFeatureStringAnnotation.class, morphoFeatures);
				
				word.set(ShapeAnnotation.class, WordShapeClassifier.wordShape(token, nerClassifier.flags.wordShape)); //nepieliek zināmos LC vārdus
	
	    		if (fields.length > 8) {
	    			String syntax = fields[6] + "\t" + fields[7] + "\t" + fields[8] + "\t" + fields[9];
	    			word.set(ConllSyntaxAnnotation.class, syntax);
	    		}	    		
	    		sentence.add(word);
	    		empty = false;
	    	} else {
	    		result.add(sentence); 		
	    		sentence = new LinkedList<CoreLabel>();
	    		break; // stop reading sentence at first blank line
	    	}
	    }
	    if (sentence.size() > 0) {
	    	result.add(sentence);
	    }
	    if (empty) return null;
		return result;
	}

}
