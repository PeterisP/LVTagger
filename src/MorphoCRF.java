import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ie.ner.CMMClassifier;
import edu.stanford.nlp.ling.CoreAnnotations.GoldAnswerAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LVMorphologyAnalysis;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.sequences.LVMorphologyReaderAndWriter;
import edu.stanford.nlp.util.StringUtils;

import lv.semti.morphology.analyzer.MarkupConverter;
import lv.semti.morphology.analyzer.Word;
import lv.semti.morphology.analyzer.Wordform;
import lv.semti.morphology.attributes.AttributeNames;
import lv.semti.morphology.attributes.AttributeValues;
import lv.semti.morphology.corpus.Statistics;


public class MorphoCRF {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
	    //Properties props = StringUtils.propFileToProperties("/Users/pet/Documents/java/PaikensNER/MorfoCRF/lv-PP.prop");
		Properties props = new Properties();
		
		props.setProperty("useLVMorphoAnalyzer", "true");
		props.setProperty("LVMorphoAnalyzerTag", AttributeNames.i_PartOfSpeech);
		//props.setProperty("LVMorphoAnalyzerTag", AttributeNames.i_Case);
		props.setProperty("useLVMorphoAnalyzerPOS", "true");
		props.setProperty("useLVMorphoAnalyzerTag", "true");
		props.setProperty("useLVMorphoAnalyzerPrev", "true");
		props.setProperty("useLVMorphoAnalyzerNext", "true");
		props.setProperty("useLVMorphoAnalyzerItemIDs", "true");
		
		props.setProperty("saveFeatureIndexToDisk", "true");
		props.setProperty("maxLeft", "1");

		props.setProperty("useWord", "true");
		//props.setProperty("use2W", "true");
		//props.setProperty("usePrevSequences", "true");
		//props.setProperty("useClassFeature", "true");
		//props.setProperty("useTypeSeqs2", "true");
		//props.setProperty("useSequences", "true");
		props.setProperty("wordShape", "dan2useLC");
		//props.setProperty("useTypeySequences", "true");
		//props.setProperty("useDisjunctive", "true");		
		props.setProperty("noMidNGrams", "true");
		props.setProperty("maxNGramLeng", "6");
		props.setProperty("useNGrams", "true");
		//props.setProperty("usePrev", "true");
		//props.setProperty("useNext", "true");
		//props.setProperty("useTypeSeqs", "true");
		
		props.setProperty("readerAndWriter", "edu.stanford.nlp.sequences.LVMorphologyReaderAndWriter");
		props.setProperty("map", "word=0,answer=1,lemma=2");
		
	    AbstractSequenceClassifier<CoreLabel> crf = new CMMClassifier<CoreLabel>(props);
	    DocumentReaderAndWriter reader = crf.makeReaderAndWriter();
	    
	    ObjectBank<List<CoreLabel>> documents = crf.makeObjectBankFromFile("MorphoCRF/train.txt", reader);	    
	    crf.train(documents, reader); //atbilstoši props datiem
	    
	    crf.serializeClassifier("MorphoCRF/lv-morpho-model.ser.gz");
	    
	    //crf.loadClassifierNoExceptions(crf.flags.loadClassifier, props);
				 
		testData(crf, "MorphoCRF/test.txt", reader);
	}

	private static void testData(AbstractSequenceClassifier<CoreLabel> crf, String filename, DocumentReaderAndWriter<CoreLabel> reader) {			
		try {
			PrintWriter izeja = new PrintWriter(new OutputStreamWriter(System.out, "UTF-8"));
		    
			ObjectBank<List<CoreLabel>> documents = crf.makeObjectBankFromFile(filename, reader);
	
			int correct_tag = 0;
			int correct_lemma = 0;
			int correct_all = 0;
			int total = 0;
			Collection<AttributeValues> errors = new LinkedList<AttributeValues>();
			
			for (List<CoreLabel> document : documents) {
			  List<CoreLabel> out = crf.classify(document);
			  
			  System.out.println("-----");
		      for (CoreLabel word : out) {
				  String token = word.word();
				  if (token.contains("<s>")) continue;

				  String answer = word.get(AnswerAnnotation.class);
				  Word analysis = word.get(LVMorphologyAnalysis.class);
				  Wordform maxwf = analysis.getMatchingWordform(word.getString(AnswerAnnotation.class), false); 
				  String lemma = maxwf.getValue(AttributeNames.i_Lemma); 
				  
				  String gold_tag = word.get(GoldAnswerAnnotation.class);
				  String gold_lemma = word.get(LemmaAnnotation.class); // The lemma that's written in the test data
				  
				  AttributeValues pareizie = MarkupConverter.fromKamolsMarkup(gold_tag);
				  AttributeValues atrastie = MarkupConverter.fromKamolsMarkup(answer);
				  errors.add(compareAVs(pareizie, atrastie));
				  //String output = crf.classifyToString(sentences)
				
				  total++;
				  
				  if (gold_lemma.equalsIgnoreCase(lemma)) correct_lemma++;
				
				  if (gold_tag != null && answer.equalsIgnoreCase(gold_tag)) {
					  correct_tag++;
					  if (gold_lemma.equalsIgnoreCase(lemma)) correct_all++;
				  } else {
					  //System.out.println("vārds: " + token+ ", pareizais: " + gold_tag + ", automātiskais: " + answer);
					  //compareAVs(pareizie, atrastie).describe(new PrintWriter(System.out));
				  }
		      }	    
			}			
		    
		    izeja.printf("\nEvaluation results:\n");
			izeja.printf("\tCorrect tag:\t%4.1f%%\t%d\n", correct_tag*100.0/total, correct_tag);
			izeja.printf("\tCorrect lemma:\t%4.1f%%\t%d\n", correct_lemma*100.0/total, correct_lemma);
			izeja.printf("\tCorrect all:\t%4.1f%%\t%d\n", correct_all*100.0/total, correct_all);
			summarizeErrors(errors, izeja);
		    izeja.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void summarizeErrors(Collection<AttributeValues> errors,
			PrintWriter izeja) {
		HashMap<String, HashMap<String, Integer>> counters = new HashMap<String, HashMap<String, Integer>>();
		
		for (AttributeValues wordErrors : errors) 
			for (Entry<String,String> error : wordErrors.entrySet()) {
				HashMap<String, Integer> counter = counters.get(error.getKey());
				if (counter == null) {
					counter = new HashMap<String, Integer>();
					counters.put(error.getKey(), counter);
				}
				Integer count = counter.get(error.getValue());
				if (count==null) count = 0;
				counter.put(error.getValue(), count+1);
			}
		
		for (Entry<String,HashMap<String,Integer>> counter : counters.entrySet()) {
			int total = 0;
			int ok = 0;
			String ok_entry = "";
			String other_entries = "";
			for (Entry<String, Integer> count : counter.getValue().entrySet()) {
				total += count.getValue();
				if (count.getKey().equalsIgnoreCase("OK")) {
					ok += count.getValue();
					//ok_entry = "\t"+count.getKey()+" :\t"+ count.getValue().toString()+"\n";
				} else {
					//other_entries += "\t"+count.getKey()+" :\t"+ count.getValue().toString()+"\n";
				}
			}
			if (ok != total) izeja.printf("%s : %5.2f%%\n%s%s", counter.getKey(), 100-(ok*100.0/total), ok_entry, other_entries);
		}
	}

	private static AttributeValues compareAVs(AttributeValues a, AttributeValues b) {
		AttributeValues result = new AttributeValues();
		for (Entry<String,String> attr : a.entrySet()) {
			String aVal = attr.getValue();
			String bVal = b.getValue(attr.getKey());
			if (bVal != null) {
				if (aVal.equalsIgnoreCase(bVal)) 
					result.addAttribute(attr.getKey(), "OK");
				else result.addAttribute(attr.getKey(), aVal + " -> " + bVal);
			}
		}		
		return result;
	}
	
}
