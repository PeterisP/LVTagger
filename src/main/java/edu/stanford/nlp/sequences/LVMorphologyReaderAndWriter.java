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
package edu.stanford.nlp.sequences;

import java.io.PrintWriter;
import java.io.Reader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreAnnotations.LVMorphologyAnalysis;
import edu.stanford.nlp.ling.CoreAnnotations.LVMorphologyAnalysisBest;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.GoldAnswerAnnotation;
import edu.stanford.nlp.objectbank.DelimitRegExIterator;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.StringUtils;

import lv.semti.morphology.analyzer.*;
import lv.semti.morphology.attributes.AttributeValues;
import lv.semti.morphology.attributes.TagSet;
import lv.semti.morphology.corpus.Statistics;

import javax.swing.text.html.HTML;

/**
 * DocumentReader for column format, running LV morphology afterwards
 *
 * @author Pēteris Paikens
 */
public class LVMorphologyReaderAndWriter implements DocumentReaderAndWriter<CoreLabel> {

  private static final long serialVersionUID = 4858022869289996959L;
  private static transient Analyzer analyzer = null;
  private Collection<String> answerAttributes = null;

  private String[] map; // = null;
  private IteratorFromReaderFactory<List<CoreLabel>> factory;


  public static void initAnalyzer(){
	  try {
		  analyzer = new Analyzer(false);
		  setAnalyzerDefaults();
	  } catch (Exception e) {
		  // TODO Auto-generated catch block
		  e.printStackTrace();
	  }
  }

	public static void setAnalyzerDefaults() {
		analyzer.enableVocative = true;
		analyzer.enableGuessing = true;
		analyzer.enablePrefixes = true;
		analyzer.enableAllGuesses = true; //TODO - check the effect on morphotagging accuracy
	}

	/**
   * Instead of loading lexicon.xml in this class (as would happen automatically), provide a pre-loaded analyzer object to put in that singleton. 
   * @param preloaded
   */
  public static void setPreloadedAnalyzer(Analyzer preloaded){
	  analyzer = preloaded;
  }
  
  /**
   * Getter for the currently used analyzer
   * @return
   */
  public static Analyzer getAnalyzer() {
      if (analyzer == null ) initAnalyzer();
      if (!analyzer.enableGuessing)
          System.err.println("Morphologic analyzer has disabled out-of-vocabulary guessing - this will reduce tagging accuracy");
      return analyzer;
  }
  
  public void init(SeqClassifierFlags flags) {
    this.map = StringUtils.mapStringToArray(flags.map);
    if (analyzer == null ) initAnalyzer();
    //answerAttributes = Arrays.asList(AttributeNames.i_PartOfSpeech, AttributeNames.i_Gender, AttributeNames.i_Number, AttributeNames.i_Case, AttributeNames.i_Izteiksme);
    //answerAttributes = Arrays.asList(flags.lvMorphoAnalyzerTag);
    factory = DelimitRegExIterator.getFactory("\n(?:\\s*\n)+", new LVColumnDocParser(answerAttributes));
  }


  public void init(String map) {
    this.map = StringUtils.mapStringToArray(map);
    if (analyzer == null ) initAnalyzer();
	factory = DelimitRegExIterator.getFactory("\n(?:\\s*\n)+", new LVColumnDocParser(answerAttributes));
  }

  public Iterator<List<CoreLabel>> getIterator(Reader r) {
    return factory.getIterator(r);
  }

  private int num; // = 0;


  private class LVColumnDocParser implements Serializable, Function<String,List<CoreLabel>> {

    private static final long serialVersionUID = -6266312614596132573L;
    private final Pattern whitePattern = Pattern.compile("\\t+");
    private Collection<String> answerAttributes;

    int lineCount = 0;
    
    LVColumnDocParser(Collection<String> _answerAttributes) {
    	answerAttributes = _answerAttributes;
    }

    public List<CoreLabel> apply(String doc) {
      if (num > 0 && num % 1000 == 0) { System.err.print("["+num+"]"); }
      num++;

      List<CoreLabel> words = new ArrayList<CoreLabel>();

      String[] lines = doc.split("\n");

      int wordCount = 0;
      for (String line : lines) {
        ++lineCount;
        //if (lineCount > 0 && lineCount % 1000 == 0) { System.err.print("["+lineCount+"]"); }
        if (line.trim().length() == 0) continue;
        if (line.contains("<g />")) continue;
        if (!line.contains("<s>") && !line.contains("</s>")) wordCount++;
        String[] info = whitePattern.split(line);
        // todo: We could speed things up here by having one time only having converted map into an array of CoreLabel keys (Class<? extends CoreAnnotation<?>>) and then instantiating them. Need new constructor.
        CoreLabel wi;
        try {
          wi = new CoreLabel(map, info);
        } catch (RuntimeException e) {
          System.err.println("Error on line " + lineCount + ": " + line);
          throw e;
        }
                
        applyLVmorphoanalysis(wi, answerAttributes);
        
        //System.out.println(wi.word());
        words.add(wi);        
      }
      System.err.print("["+wordCount+"]");
      return words;
    }

  } // end class ColumnDocParser


  public void printAnswers(List<CoreLabel> doc, PrintWriter out) {
    for (CoreLabel wi : doc) {
      String answer = wi.get(AnswerAnnotation.class);
      String goldAnswer = wi.get(GoldAnswerAnnotation.class);
      out.println(wi.word() + "\t" + goldAnswer + "\t" + answer);
    }
    out.println();
  }
  
  /**
   * Performs LV morphology analysis of the token wi, adds the possible readins and marks the most likely one.
   * If an AnswerAnnotation exists, then it is considered to be a morphosyntactic tag, and the attributes are filtered for the training. 
   * 
   * @param wi
   * @param answerAttributes
   */
  private static void applyLVmorphoanalysis(CoreLabel wi, Collection<String> answerAttributes) {
	  Word analysis = analyzer.analyze(wi.word());
	  applyLVmorphoanalysis(wi, analysis, answerAttributes);
  }
  
	private static void applyLVmorphoanalysis(CoreLabel wi, Word analysis, Collection<String> answerAttributes) {
		String token = wi.word();
	    if (!token.contains("<s>")) {
	        String answer = wi.get(AnswerAnnotation.class);
	        if (answerAttributes == null) {
		        AttributeValues answerAV = TagSet.getTagSet().fromTag(answer);
		        answerAV.removeNonlexicalAttributes();
				answer = TagSet.getTagSet().toTag(answerAV);
	        } else if (answerAttributes.size() == 1) {
		        AttributeValues answerAV = TagSet.getTagSet().fromTag(answer);
		        answerAV.filterAttributes(answerAttributes);
		        if (answerAV.size()>0)
		        	answer = answerAV.get(0).getValue();
		        else answer = "O";
	        } else {
		        AttributeValues answerAV = TagSet.getTagSet().fromTag(answer);
		        answerAV.filterAttributes(answerAttributes);
				answer = TagSet.getTagSet().toTag(answerAV);
	        }
	        wi.set(AnswerAnnotation.class, answer);
	        	        
	        Wordform mainwf = null;
			double max_likelihood = -1;
			for (Wordform wf : analysis.wordforms) {  // Paskatamies visus atrastos variantus un ņemam statistiski ticamāko
				double estimate = Statistics.getStatistics().getEstimate(wf);
				if (estimate > max_likelihood) {
					max_likelihood = estimate;
					mainwf = wf;
				}
			}
	        wi.set(LVMorphologyAnalysis.class, analysis);
	        wi.set(LVMorphologyAnalysisBest.class, mainwf);
	    }
	}
	
	public static List<CoreLabel> analyzeSentence(List<String> sentence) {
		List<CoreLabel> result = new ArrayList<CoreLabel>();
		CoreLabel s = new CoreLabel();
		s.set(TextAnnotation.class, "<s>");
		result.add(s);
		
		for (String w : sentence) {
			CoreLabel word = new CoreLabel();
			word.set(TextAnnotation.class, w);
			applyLVmorphoanalysis(word, null); //answerAttributes varbūt jāpatjūnē
			result.add(word);
		}
		
		s = new CoreLabel();
		s.set(TextAnnotation.class, "<s>");
		result.add(s);
		return result;
	}
	
	public static List<CoreLabel> analyzeLabels(List<CoreLabel> sentence) {
		for (CoreLabel word : sentence) 
			applyLVmorphoanalysis(word, null); //answerAttributes varbūt jāpatjūnē

		return sentence;
	}

	public static List<CoreLabel> analyzeSentence(String sentence) {
	    if (analyzer == null ) initAnalyzer();
	    List<Word> words = Splitting.tokenize(analyzer, sentence); 
	    
	    return analyzeSentence2(words);
	}

	public static List<CoreLabel> analyzeSentence2(List<Word> sentence) {
	    List<CoreLabel> result = new ArrayList<CoreLabel>();
		CoreLabel s = new CoreLabel();
		s.set(TextAnnotation.class, "<s>");
		result.add(s);
		
		for (Word w : sentence) {
			CoreLabel word = new CoreLabel();
			word.set(TextAnnotation.class, w.getToken());
			applyLVmorphoanalysis(word, w, null); //answerAttributes varbūt jāpatjūnē
			result.add(word);
		}
		
		s = new CoreLabel();
		s.set(TextAnnotation.class, "<s>");
		result.add(s);
		return result;
	}	
	
}
