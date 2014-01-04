package lv.lumii.morphotagger;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.json.simple.JSONValue;

import lv.semti.morphology.analyzer.Splitting;
import lv.semti.morphology.analyzer.Word;
import lv.semti.morphology.analyzer.Wordform;
import lv.semti.morphology.attributes.AttributeNames;
import lv.semti.morphology.attributes.AttributeValues;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.ner.CMMClassifier;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ConllSyntaxAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LVMorphologyAnalysis;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.sequences.LVMorphologyReaderAndWriter;

// Copied/pasted/mangled from transliteration webservices java project

public class MorphoPipeLETAArticle {
	private enum inputTypes {SENTENCE, PARAGRAPH, VERT, CONLL};
	private enum outputTypes {JSON, TAB, VERT, MOSES, CONLL_X, XML, VISL_CG};

	private static String eol = System.getProperty("line.separator");
	private static String field_separator = "\t";
	private static String token_separator = eol;
	
	private static boolean mini_tag = false;		
	private static boolean features = false;		
	private static inputTypes inputType = inputTypes.SENTENCE;
	
	private static String morphoClassifierLocation = "models/lv-morpho-model.ser.gz"; //FIXME - make it configurable
	

	private static void splitInSentences(HashSet<String> sentencesSet, String text) {
		
		if (inputType == inputTypes.PARAGRAPH) { // split in multiple sentences
			LinkedList<LinkedList<Word>> sentences = Splitting.tokenizeSentences(LVMorphologyReaderAndWriter.getAnalyzer(), text);
			for (LinkedList<Word> sentence : sentences) 
			{
				if(sentence != null)
				{
					String madeSentence = "";
					for(int w = 0; w < sentence.size(); w++)
					{
						madeSentence = madeSentence + " " + sentence.get(w);
					}
					madeSentence = madeSentence.trim();
					//pārbaudam, vai teikuma atšķiribas nav punkts teikuma beigas
					if(madeSentence.length() > 0 && madeSentence.lastIndexOf(".") >= 0) madeSentence = madeSentence.substring(0, madeSentence.length() - 1);
					sentencesSet.add(madeSentence.trim());
				}
			}
		} 
	}
	
	public static void getParagrapgInSentences(String inputTypePar, String outputTypePar, String otherOptionPar, 
			String fileNameOut, String fileNameIn) throws ClassCastException, ClassNotFoundException, IOException
	{
		if (inputTypePar.equalsIgnoreCase("-paragraphs")) inputType = inputTypes.PARAGRAPH;
		CMMClassifier<CoreLabel> morphoClassifier = CMMClassifier.getClassifier(morphoClassifierLocation);


		//PrintStream out = new PrintStream(System.out, true, "UTF8");
		PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(fileNameOut, true)), true, "UTF8");
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileNameIn), "UTF8"));//System.in

		String sentence = "";
		HashSet<String> sentencesSet = new HashSet<>();
		while ((sentence = in.readLine()) != null)
		{
			if(sentence.length() != 0)
			{
				splitInSentences(sentencesSet, sentence.trim());
				sentence = "";
				
			}
		}
		
		if(sentencesSet.size() > 0)
		{		
			Iterator<String> iterator = sentencesSet.iterator();
			while(iterator.hasNext())
			{
				out.println(iterator.next());
			}
		}		
		in.close();
		out.close();
	}
	
	
	public static void main(String[] args) throws Exception 
	{
		//
		
		
	}

	
}	
