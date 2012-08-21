package edu.stanford.nlp.sequences;

import java.io.PrintWriter;
import java.io.Reader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreAnnotations.LVMorphologyAnalysis;
import edu.stanford.nlp.ling.CoreAnnotations.LVMorphologyAnalysisBest;
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

  private String[] map; // = null;
  private IteratorFromReaderFactory<List<CoreLabel>> factory;


  public void init(SeqClassifierFlags flags) {
    this.map = StringUtils.mapStringToArray(flags.map);
	try {
		Analyzer analyzer = new Analyzer("dist/Lexicon.xml");
		Statistics statistics = new Statistics("dist/Statistics.xml");    
		factory = DelimitRegExIterator.getFactory("\n(?:\\s*\n)+", new LVColumnDocParser(analyzer, statistics));
	} catch (Exception e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
  }


  public void init(String map) {
    this.map = StringUtils.mapStringToArray(map);
	try {
		Analyzer analyzer = new Analyzer("dist/Lexicon.xml");
		Statistics statistics = new Statistics("dist/Statistics.xml");    
		factory = DelimitRegExIterator.getFactory("\n(?:\\s*\n)+", new LVColumnDocParser(analyzer, statistics));
	} catch (Exception e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
  }

  public Iterator<List<CoreLabel>> getIterator(Reader r) {
    return factory.getIterator(r);
  }

  private int num; // = 0;


  private class LVColumnDocParser implements Serializable, Function<String,List<CoreLabel>> {

    private static final long serialVersionUID = -6266332614596132572L;
    private final Pattern whitePattern = Pattern.compile("\\t+");
    private Analyzer analyzer;
    private Statistics statistics;

    int lineCount = 0;
    
    LVColumnDocParser(Analyzer _analyzer, Statistics _statistics) {
    	analyzer = _analyzer;
    	statistics = _statistics;
    }

    public List<CoreLabel> apply(String doc) {
      if (num > 0 && num % 1000 == 0) { System.err.print("["+num+"]"); }
      num++;

      List<CoreLabel> words = new ArrayList<CoreLabel>();

      String[] lines = doc.split("\n");

      for (String line : lines) {
        ++lineCount;
        if (line.trim().length() == 0) continue;
        //if (line.contains("<s>") || line.contains("</s>"))
        String[] info = whitePattern.split(line);
        // todo: We could speed things up here by having one time only having converted map into an array of CoreLabel keys (Class<? extends CoreAnnotation<?>>) and then instantiating them. Need new constructor.
        CoreLabel wi;
        try {
          wi = new CoreLabel(map, info);
        } catch (RuntimeException e) {
          System.err.println("Error on line " + lineCount + ": " + line);
          throw e;
        }
        
        String token = wi.word();
        String answer = wi.get(AnswerAnnotation.class);
        
        answer = answer.substring(0,1);
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
        
        //System.out.println(wi.word());
        words.add(wi);        
      }
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

}
