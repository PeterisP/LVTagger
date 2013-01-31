import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.json.simple.JSONValue;

import lv.semti.morphology.analyzer.Splitting;
import lv.semti.morphology.analyzer.Word;
import lv.semti.morphology.analyzer.Wordform;
import lv.semti.morphology.attributes.AttributeNames;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.ner.CMMClassifier;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LVMorphologyAnalysis;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.sequences.LVMorphologyReaderAndWriter;

// Copied/pasted/mangled from transliteration webservices java project

public class WordPipe {
	private enum inputTypes {SENTENCE, VERT, CONLL};
	private enum outputTypes {JSON, TAB, VERT, MOSES, CONLL_X};
	
	public static void main(String[] args) throws Exception {
		String field_separator = "\t";
		String token_separator = "\n";
		
		boolean mini_tag = false;		
		inputTypes inputType = inputTypes.SENTENCE;
		outputTypes outputType = outputTypes.JSON;
		
		for (int i=0; i<args.length; i++) {
			if (args[i].equalsIgnoreCase("-tab")) {  // one response line per each query line, tab-separated
				outputType = outputTypes.TAB;
				token_separator = "\t";
			}
			if (args[i].equalsIgnoreCase("-vert")) { // one response line per token, tab-separated
				outputType = outputTypes.VERT;
			}
			if (args[i].equalsIgnoreCase("-moses")) { // one response line per token, pipe-separated
				field_separator = "|";
				token_separator = " ";
				outputType = outputTypes.MOSES;
			}
			if (args[i].equalsIgnoreCase("-stripped")) mini_tag = true; //remove nonlexical attributes
			if (args[i].equalsIgnoreCase("-vertinput")) inputType = inputTypes.VERT; //vertical input format as requested by Milos Jakubicek 2012.11.01
			if (args[i].equalsIgnoreCase("-conll-in")) inputType = inputTypes.CONLL; 
			if (args[i].equalsIgnoreCase("-conll-x")) outputType = outputTypes.CONLL_X;
			
			if (args[i].equalsIgnoreCase("-h") || args[i].equalsIgnoreCase("--help") || args[i].equalsIgnoreCase("-?")) {
				System.out.println("LV morphological tagger");
				System.out.println("\nInput formats");
				System.out.println("\tDefault : plain text UTF-8, one sentence per line.");
				System.out.println("\t-vertinput : one line per token, sentences separated by <s></s>. Any XML-style tags are echoed as-is. \n\t\tNB! sentences are retokenized, the number of tokens may be different.");
				System.out.println("\t-conll-in : CONLL shared task data format - one line per token, with tab-delimited columns, sentences separated by blank lines.");
				System.out.println("\nOutput formats");
				System.out.println("\tDefault : JSON. Each sentence is returned as a list of dicts, each dict contains elements 'Word', 'Tag' and 'Lemma'.");
				System.out.println("\t-tab : one response line for each query line; tab-separated lists of word, tag and lemma.");
				System.out.println("\t-vert : one response line for each token; tab-separated lists of word, tag and lemma.");
				System.out.println("\t-moses : one response line for each token; pipe-separated lists of word, tag and lemma.");
				System.out.println("\t-conll-x : CONLL-X shared task data format - one line per token, with tab-delimited columns, sentences separated by blank lines.");
				System.out.println("\nOther options:");
				System.out.println("\t-stripped : lexical/nonessential parts of the tag are replaced with '-' to reduce sparsity.");
				System.out.flush();
				System.exit(0);
			}
		}
				
		String serializedClassifier = "MorphoCRF/lv-morpho-model.ser.gz"; //FIXME - make it configurable
		AbstractSequenceClassifier<CoreLabel> cmm = CMMClassifier.getClassifier(serializedClassifier);
			
		PrintStream out = new PrintStream(System.out, true, "UTF8");
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in, "UTF8"));
		
		List<List<CoreLabel>> sentences;
		
		switch(inputType) {
		case CONLL:
			sentences = readCONLL(in);
			break;
		default:
		    String s;
		    String sentence = "";
		    sentences = new LinkedList<List<CoreLabel>>();
		    while ((s = in.readLine()) != null && s.length() != 0) {
		    	boolean finished = true; // is sentence finished and ready to analyze
		    	if (inputType != inputTypes.VERT) sentence = s;
		    	else {
		    		if (s.startsWith("<") && s.length()>1) out.println(s);
		    		else sentence = sentence + " " + s;
		    		finished = s.startsWith("</s>");
		    	}	    	
		    	if (finished) {
			    	sentences.add( LVMorphologyReaderAndWriter.analyzeSentence(sentence.trim()) );
			    	sentence = "";
		    	}
		    }
	    	if (inputType != inputTypes.VERT && sentence.length()>0) { //FIXME, not DRY
		    	sentences.add( LVMorphologyReaderAndWriter.analyzeSentence(sentence.trim()) );
	    	}	    			
		}
		
		for (List<CoreLabel> sentence : sentences) {
	    	cmm.classify(sentence); // runs the actual morphotagging system
	    	switch (outputType) {
	    	case JSON:
	    		out.println( output_JSON(sentence, mini_tag));
	    		break;
	    	case CONLL_X:
	    		out.println( output_CONLL(sentence, mini_tag));
	    		break;
	    	default: 
	    		out.println( output_separated(sentence, field_separator, token_separator, mini_tag));	    
	    	}
	    	out.flush();
		}
	}	
	
	private static String output_JSON(List<CoreLabel> tokens, boolean mini_tag) {		
		LinkedList<String> tokenJSON = new LinkedList<String>();
		
		for (CoreLabel word : tokens) {
			String token = word.getString(TextAnnotation.class);
			if (token.contains("<s>")) continue;
			Word analysis = word.get(LVMorphologyAnalysis.class);
			Wordform maxwf = analysis.getMatchingWordform(word.getString(AnswerAnnotation.class), false);
			if (mini_tag) maxwf.removeNonlexicalAttributes();
			if (maxwf != null)
				tokenJSON.add(String.format("{\"Word\":\"%s\",\"Tag\":\"%s\",\"Lemma\":\"%s\"}", JSONValue.escape(token), JSONValue.escape(maxwf.getTag()), JSONValue.escape(maxwf.getValue(AttributeNames.i_Lemma))));
			else 
				tokenJSON.add(String.format("{\"Word\":\"%s\",\"Tag\":\"-\",\"Lemma\":\"%s\"}", JSONValue.escape(token), JSONValue.escape(token)));			
		}
		
		String s = formatJSON(tokenJSON).toString();
		tokens = null;
		tokenJSON = null;
		
		return s;
	}

	private static String output_CONLL(List<CoreLabel> tokens, boolean mini_tag){
		StringBuilder s = new StringBuilder();

		int counter = 1;
		for (CoreLabel word : tokens) {
			String token = word.getString(TextAnnotation.class);
			if (token.contains("<s>")) continue;
			s.append(Integer.toString(counter));
			s.append('\t');
			s.append(token);
			s.append('\t');
			Word analysis = word.get(LVMorphologyAnalysis.class);
			Wordform mainwf = analysis.getMatchingWordform(word.getString(AnswerAnnotation.class), false); 
			if (mainwf != null) {
				if (mini_tag) mainwf.removeNonlexicalAttributes();
				s.append(mainwf.getValue(AttributeNames.i_Lemma));
				s.append('\t');
				s.append(mainwf.getTag().substring(0,1));
				s.append('\t');
				s.append(mainwf.getTag());
				s.append('\t');
				for (Entry<String, String> entry : mainwf.entrySet()) { // visi attributevalue paariishi
					 s.append(entry.getKey());
					 s.append('=');
					 s.append(entry.getValue());
					 s.append('|');
				}
				s.deleteCharAt(s.length()-1); // noņemam peedeejo | separatoru, kas ir lieks
				s.append('\t');
				
			} else {
				s.append(token); 
				s.append("\t_\t_\t_\t");
			}
			s.append("_\t_\t_\t_\n");
		}
		s.append('\n');
		
		return s.toString();
	}
	
	
	private static String output_separated(List<CoreLabel> tokens, String field_separator, String token_separator, boolean mini_tag){
		StringBuilder s = new StringBuilder();
		
		for (CoreLabel word : tokens) {
			String token = word.getString(TextAnnotation.class);
			if (token.contains("<s>")) continue;
			if (s.length()>0) s.append(token_separator);
			s.append(token);
			s.append(field_separator);
			Word analysis = word.get(LVMorphologyAnalysis.class);
			Wordform mainwf = analysis.getMatchingWordform(word.getString(AnswerAnnotation.class), false); 
			if (mainwf != null) {
				if (mini_tag) mainwf.removeNonlexicalAttributes();
				s.append(mainwf.getTag());
				s.append(field_separator);
				s.append(mainwf.getValue(AttributeNames.i_Lemma));
			} else s.append(field_separator); 
			/*
			mainwf = word.get(LVMorphologyAnalysisBest.class);
			if (mainwf != null) {
				s.append("Single-token suggestion:\t");
				s.append(mainwf.getTag());
				s.append("\t");
				s.append(mainwf.getValue(AttributeNames.i_Lemma));
				s.append("\t");
			}
			s.append("\n");
			if (all_options)
					s.append(word.toTabSep(statistics, probabilities));
			else s.append(word.toTabSepsingle(statistics)); */
		}
		
		tokens = null;
		return s.toString();
	}
	
	private static StringBuilder formatJSON(Collection<String> tags) {
		Iterator<String> i = tags.iterator();
		StringBuilder out = new StringBuilder("[");
		while (i.hasNext()) {
			out.append(i.next());
			if (i.hasNext()) out.append(", ");
		}
		out.append("]");
		return out;
	}
	
	
	public static List<List<CoreLabel>> readCONLL(BufferedReader in) throws IOException {
		String s;
	    List<String> sentence = new LinkedList<String>();
	    List<List<CoreLabel>> result = new LinkedList<List<CoreLabel>>();
	    while ((s = in.readLine()) != null) {
	    	if (s.trim().length() > 0) {
	    		String[] fields = s.split("\t");
	    		String token = fields[1];
	    		sentence.add(token);
	    	} else {
	    		result.add(LVMorphologyReaderAndWriter.analyzeSentence(sentence));
	    		sentence.clear();
	    	}
	    }
	    if (sentence.size() > 0) result.add(LVMorphologyReaderAndWriter.analyzeSentence(sentence));
	    		
		return result;
	}
}	