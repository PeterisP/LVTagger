import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.json.simple.JSONValue;

import lv.semti.morphology.analyzer.MarkupConverter;
import lv.semti.morphology.analyzer.Word;
import lv.semti.morphology.analyzer.Wordform;
import lv.semti.morphology.attributes.AttributeNames;
import lv.semti.morphology.attributes.AttributeValues;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.ner.CMMClassifier;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LVMorphologyAnalysis;
import edu.stanford.nlp.ling.CoreAnnotations.LVMorphologyAnalysisBest;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.sequences.LVMorphologyReaderAndWriter;

// Copied/pasted/mangled from transliteration webservices java project

public class WordPipe {
	public static void main(String[] args) throws Exception {
		boolean tab_output = false;
		for (int i=0; i<args.length; i++) {
			if (args[i].equalsIgnoreCase("-tab")) tab_output = true;
		}
				
		String serializedClassifier = "MorfoCRF/lv-morpho-model.ser.gz"; //FIXME - make it configurable
		AbstractSequenceClassifier<CoreLabel> cmm = CMMClassifier.getClassifier(serializedClassifier);
			
		PrintStream out = new PrintStream(System.out, true, "UTF8");
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in, "UTF8"));
		
	    String s;	    
	    while ((s = in.readLine()) != null && s.length() != 0) {
	    	List<CoreLabel> doc = LVMorphologyReaderAndWriter.analyzeSentence(s);
	    	cmm.classify(doc);
	    	if (!tab_output) 
	    		out.println( analyze( doc));
	    	else out.println( analyze_tab( doc));
	    	out.flush();
	    }
	}	
	
	private static String analyze(List<CoreLabel> tokens) {		
		LinkedList<String> tokenJSON = new LinkedList<String>();
		
		for (CoreLabel word : tokens) {
			String token = word.getString(TextAnnotation.class);
			if (token.contains("<s>")) continue;
			Word analysis = word.get(LVMorphologyAnalysis.class);
			Wordform maxwf = chooseWordform(analysis, word.getString(AnswerAnnotation.class)); 
			if (maxwf != null)
				tokenJSON.add(String.format("{\"Vārds\":\"%s\",\"Marķējums\":\"%s\",\"Pamatforma\":\"%s\"}", JSONValue.escape(token), JSONValue.escape(maxwf.getTag()), JSONValue.escape(maxwf.getValue(AttributeNames.i_Lemma))));
			else 
				tokenJSON.add(String.format("{\"Vārds\":\"%s\",\"Marķējums\":\"-\",\"Pamatforma\":\"%s\"}", JSONValue.escape(token), JSONValue.escape(token)));			
		}
		
		String s = formatJSON(tokenJSON).toString();
		tokens = null;
		tokenJSON = null;
		
		return s;
	}
	
	private static String analyze_tab(List<CoreLabel> tokens){
		StringBuilder s = new StringBuilder(); 
		
		for (CoreLabel word : tokens) {
			String token = word.getString(TextAnnotation.class);
			if (token.contains("<s>")) continue;
			if (s.length()>0) s.append("\t");
			s.append(token);
			s.append("\t");
			/*
			s.append(word.getString(AnswerAnnotation.class));
			s.append("\t");
			*/
			Word analysis = word.get(LVMorphologyAnalysis.class);
			Wordform mainwf = chooseWordform(analysis, word.getString(AnswerAnnotation.class)); 
			if (mainwf != null) {
				s.append(mainwf.getTag());
				s.append("\t");
				s.append(mainwf.getValue(AttributeNames.i_Lemma));
				//s.append("\t");
			} else s.append("\t"); 
			/*
			mainwf = word.get(LVMorphologyAnalysisBest.class);
			if (mainwf != null) {
				s.append("Statistics:\t");
				s.append(mainwf.getTag());
				s.append("\t");
				s.append(mainwf.getValue(AttributeNames.i_Lemma));
				s.append("\t");
			}
			s.append("\n");*/
			//if (all_options)
		//		s.append(word.toTabSep(statistics, probabilities));
			//else s.append(word.toTabSepsingle(statistics));
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
	
	
	private static Wordform chooseWordform(Word analysis, String answerTag) {
		Wordform result = null;
		AttributeValues av = MarkupConverter.fromKamolsMarkup(answerTag);
		for (Wordform wf : analysis.wordforms) {
			if (wf.isMatchingWeak(av)) {
				if (result != null) 
					System.err.printf("Multiple valid options for word %s tag %s: %s and %s\n", analysis.getToken(), answerTag, wf.getTag(), result.getTag());
				result = wf;
			}
		}
		
		if (result == null) {
			result = new Wordform(analysis.getToken());
			result.addAttributes(av);
			result.addAttribute(AttributeNames.i_Source, "CMM tagger guess");
			result.addAttribute(AttributeNames.i_Lemma, analysis.getToken());
			System.err.printf("None of analysis options valid for word %s tag %s\n", analysis.getToken(), answerTag);
		}
		
		return result;
	}
}	