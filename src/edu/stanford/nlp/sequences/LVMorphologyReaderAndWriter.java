package edu.stanford.nlp.sequences;

import java.io.PrintWriter;
import java.io.Reader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
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
import lv.semti.morphology.attributes.AttributeNames;
import lv.semti.morphology.attributes.AttributeValues;
import lv.semti.morphology.corpus.Statistics;
/**
 * DocumentReader for column format, running LV morphology afterwards
 *
 * @author Pēteris Paikens
 */
public class LVMorphologyReaderAndWriter implements DocumentReaderAndWriter<CoreLabel> {

  private static final long serialVersionUID = 4858022869289996959L;
  private static transient Analyzer analyzer = null;
  private static transient Statistics statistics = null;
  private Collection<String> answerAttributes;

  private String[] map; // = null;
  private IteratorFromReaderFactory<List<CoreLabel>> factory;


  private static void initAnalyzer(){
	  try {
		  analyzer = new Analyzer("dist/Lexicon.xml");
		  statistics = new Statistics("dist/Statistics.xml");
		  analyzer.enableVocative = true;
		  analyzer.enableGuessing = true; 
		  //analyzer.enableAllGuesses = true;
	  } catch (Exception e) {
		  // TODO Auto-generated catch block
		  e.printStackTrace();
	  }
  }
  
  public void init(SeqClassifierFlags flags) {
    this.map = StringUtils.mapStringToArray(flags.map);
    if (analyzer == null || statistics == null) initAnalyzer();
    //answerAttributes = Arrays.asList(AttributeNames.i_PartOfSpeech, AttributeNames.i_Gender, AttributeNames.i_Number, AttributeNames.i_Case, AttributeNames.i_Izteiksme);
    //answerAttributes = Arrays.asList(flags.lvMorphoAnalyzerTag);
    factory = DelimitRegExIterator.getFactory("\n(?:\\s*\n)+", new LVColumnDocParser(answerAttributes));
  }


  public void init(String map) {
    this.map = StringUtils.mapStringToArray(map);
    if (analyzer == null || statistics == null) initAnalyzer();
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
  
	private static void applyLVmorphoanalysis(CoreLabel wi, Collection<String> answerAttributes) {
		String token = wi.word();
	    if (!token.contains("<s>")) {
	        String answer = wi.get(AnswerAnnotation.class);
	        if (answerAttributes == null) {
		        AttributeValues answerAV = MarkupConverter.fromKamolsMarkup(answer);
		        answerAV.removeNonlexicalAttributes();
				answer = MarkupConverter.toKamolsMarkup(answerAV);	        	
	        } else if (answerAttributes.size() == 1) {
		        AttributeValues answerAV = MarkupConverter.fromKamolsMarkup(answer);
		        answerAV.filterAttributes(answerAttributes);
		        if (answerAV.size()>0)
		        	answer = answerAV.get(0).getValue();
		        else answer = "O";
	        } else {
		        AttributeValues answerAV = MarkupConverter.fromKamolsMarkup(answer);
		        answerAV.filterAttributes(answerAttributes);
				answer = MarkupConverter.toKamolsMarkup(answerAV);	        	
	        }
	        wi.set(AnswerAnnotation.class, answer);
	        
	        Word analysis = analyzer.analyze(token);
	        Wordform mainwf = null;
			double maxticamība = -1;
			for (Wordform wf : analysis.wordforms) {  // Paskatamies visus atrastos variantus un ņemam statistiski ticamāko
				if (statistics.getEstimate(wf) > maxticamība) {
					maxticamība = statistics.getEstimate(wf);
					mainwf = wf;
				}
			}
	        wi.set(LVMorphologyAnalysis.class, analysis);
	        wi.set(LVMorphologyAnalysisBest.class, mainwf);
	    }
	}

	public static List<CoreLabel> analyzeSentence(String sentence) {
	    if (analyzer == null || statistics == null) initAnalyzer();

	    List<CoreLabel> result = new ArrayList<CoreLabel>();
		CoreLabel s = new CoreLabel();
		s.set(TextAnnotation.class, "<s>");
		result.add(s);
		
		List<Word> words = Splitting.tokenize(analyzer, sentence);
		for (Word w : words) {
			CoreLabel word = new CoreLabel();
			word.set(TextAnnotation.class, w.getToken());
			applyLVmorphoanalysis(word, null); //answerAttributes varbūt jāpatjūnē
			result.add(word);
		}
		
		result.add(s);
		return result;
	}

}
