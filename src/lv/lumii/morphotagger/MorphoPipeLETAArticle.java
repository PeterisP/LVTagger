/*******************************************************************************
 * Copyright 2013,2014 Institute of Mathematics and Computer Science, University of Latvia
 * Author: Pēteris Paikens
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package lv.lumii.morphotagger;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import lv.semti.morphology.analyzer.Splitting;
import lv.semti.morphology.analyzer.Word;
import edu.stanford.nlp.ie.ner.CMMClassifier;
import edu.stanford.nlp.ling.CoreLabel;
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
