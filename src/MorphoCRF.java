import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreAnnotations.GoldAnswerAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
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
		//props.setProperty("usePrevSequences", "true");
		//props.setProperty("useClassFeature", "true");
		//props.setProperty("useTypeSeqs2", "true");
		//props.setProperty("useSequences", "true");
		//props.setProperty("wordShape", "dan2useLC");
		//props.setProperty("saveFeatureIndexToDisk", "true");
		//props.setProperty("useTypeySequences", "true");
		//props.setProperty("useDisjunctive", "true");		
		//props.setProperty("noMidNGrams", "true");
		//props.setProperty("maxNGramLeng", "6");
		//props.setProperty("useNGrams", "true");
		//props.setProperty("usePrev", "true");
		//props.setProperty("useNext", "true");
		//props.setProperty("maxLeft", "1");
		//props.setProperty("useTypeSeqs", "true");
		
		props.setProperty("readerAndWriter", "edu.stanford.nlp.sequences.LVMorphologyReaderAndWriter");
		
	    CRFClassifier<CoreLabel> crf = new CRFClassifier<CoreLabel>(props);
	    LVMorphologyReaderAndWriter reader = new LVMorphologyReaderAndWriter();
	    reader.init("word=0,answer=1,lemma=2");   //TODO - aizvākt uz normālo parsi
	    ObjectBank<List<CoreLabel>> documents = crf.makeObjectBankFromFile("MorfoCRF/train.txt", reader);
	    
	    crf.train(documents, reader); //atbilstoši props datiem
	    
	    //crf.loadClassifierNoExceptions(crf.flags.loadClassifier, props);
	    
	    //crf.flags.map = "word=0,tag=1,lemma=2,answer=3";
				 
		testData(crf, "MorfoCRF/dev.txt", reader);
	}

	private static void testData(CRFClassifier crf, String filename, DocumentReaderAndWriter<CoreLabel> reader) {			
		try {
			PrintWriter izeja = new PrintWriter(new OutputStreamWriter(System.out, "UTF-8"));
		    
			ObjectBank<List<CoreLabel>> documents = crf.makeObjectBankFromFile(filename, reader);
	
			int correct = 0;
			int total = 0;
			
			for (List<CoreLabel> document : documents) {
			  List<CoreLabel> out = crf.classify(document);
			  
			  System.out.println("-----");
		      for (CoreLabel word : out) {
				  String token = word.word();
				  if (token.contains("<s>")) continue;

				  String answer = word.get(AnswerAnnotation.class);
				  String lemma = word.get(LemmaAnnotation.class);
				  String gold_tag = word.get(GoldAnswerAnnotation.class);
				  
				  AttributeValues pareizie = MarkupConverter.fromKamolsMarkup(gold_tag);
				  //String output = crf.classifyToString(sentences)
				
				  total++;
				
				  if (gold_tag != null && answer.equalsIgnoreCase(gold_tag.substring(0, 1))) {
					  correct++;
				  } else {
					  System.out.println("vārds: " + token+ ", pareizais: " + gold_tag + ", automātiskais: " + answer);		    		    			    		
				  }
		      }	    
			}
		    
		    izeja.printf("\nAnalīzes rezultāti:\n");
			izeja.printf("\tPareizi:\t%4.1f%%\t%d\n", correct*100.0/total, correct);
		    izeja.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
