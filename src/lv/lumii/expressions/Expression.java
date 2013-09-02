
package lv.lumii.expressions;
import lv.semti.morphology.analyzer.*;
import lv.semti.morphology.attributes.AttributeNames;
import lv.semti.morphology.attributes.AttributeValues;

import java.io.File;
import java.io.IOException;
import java.util.*;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.ner.CMMClassifier;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ConllSyntaxAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LVMorphologyAnalysis;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.sequences.LVMorphologyReaderAndWriter;

/**
 * @author Ginta
 * 
 */
public class Expression
{
	public LinkedList <ExpressionWord> expWords;
	Category cat;
	private static transient Analyzer analyzer = null;
	private static transient CMMClassifier<CoreLabel> morphoClassifier = null;
	private static transient Analyzer locītājs = null;
	
	private static void initClassifier(String model) throws Exception {
		morphoClassifier = CMMClassifier.getClassifier(new File(model));		
		locītājs = LVMorphologyReaderAndWriter.getAnalyzer(); // Assumption - that the morphology model actually loads the LVMorphologyReaderAndWriter data, so it should be filled.
	}
	
	private static void initClassifier() throws Exception {
		initClassifier("models/lv-morpho-model.ser.gz");
	}
	
	public Expression(String phrase) throws Exception
	{
		this(phrase,true);
	}
	
	public Expression(String phrase, boolean useTagger) throws Exception
	{
		if (morphoClassifier == null) initClassifier(); 
		if(useTagger)
		{
			loadUsingTagger(phrase);
		}
		else
		{
			loadUsingBestWordform(phrase);
		}
	}
	
	public void loadUsingBestWordform(String phrase) throws Exception
	{
		LinkedList <Word> words = Splitting.tokenize(locītājs, phrase);
		expWords=new LinkedList<ExpressionWord>();
		for (Word w : words)
		{
			ExpressionWord tmp = new ExpressionWord(w);
			expWords.add(tmp);
		}
	}
	
	public void loadUsingTagger(String phrase) 
	{
		expWords=new LinkedList<ExpressionWord>();
		
		//FIXME šo vajadzētu ielādēt globāli, nevis uz katru objektu		
		List<CoreLabel> sentence = LVMorphologyReaderAndWriter.analyzeSentence(phrase);
		sentence = morphoClassifier.classify(sentence);
		
		String token;
		Word analysis;
		Wordform maxwf;
		for(CoreLabel label : sentence)
		{
			token = label.getString(TextAnnotation.class);
			
			if(token.equals("<s>")) //FIXME kāpēc tiek pievienoti <s>? Varbūt ir kāds labāks veids kā to apiet
			{
				continue;
			}
			
		  analysis = label.get(LVMorphologyAnalysis.class);
		  /*
		  System.out.print(token);
		  System.out.print(" ");
		  System.out.println(analysis);
		  */
		  maxwf = analysis.getMatchingWordform(label.getString(AnswerAnnotation.class), false);
		  
		  ExpressionWord tmp = new ExpressionWord(analysis, maxwf);
		  expWords.add(tmp);
		}
		
	}
	
	
	public void addPattern(String c) //Method adds isStatic attribute to the Expression word, which indicates, whether to inflect the Word
	{
		boolean staticWhile=false;
		cat=get(c);
		
		switch(cat)
		{
			case org :
			{
				for (ExpressionWord w : expWords)
				{
					if(w.word.isRecognized()==false || w.bestWordform.lexeme==null) //FIXME nav īstā vieta, kur pārbaudīt, vai lexeme is null
					{
						w.isStatic=true;
						continue;
					}
					
					switch(w.bestWordform.getValue("Vārdšķira"))
					{
						case "Lietvārds":
						{
							if(staticWhile || expWords.lastIndexOf(w)!=expWords.size()-1) 
							{
								w.isStatic=true;
								break;
							}
							w.isStatic=false;
							break;
						}
						case "Īpašības vārds":
						{
							if(staticWhile) 
							{
								w.isStatic=true;
								break;
							}
							w.isStatic=false;
							break;
						}
						case "Pieturzīme":
						{
							w.isStatic=true;
							staticWhile=!staticWhile;
							break;
						}
					}
				}
				break;
			}
			
			case hum :
			{
				break;
			}
			
			default :
			{
				break;
			}
		}
	}
	
	public String normalize() throws Exception
	{
		return inflect("Nominatīvs",null);
	}
	
	public String inflect(String inflect, String cat) throws Exception
	{
		addPattern(cat);
		
		String inflectedPhrase="";
		
		AttributeValues filtrs;
		HashMap<String,String> attribute_map;
		Wordform forma, inflected_form;
		ArrayList<Wordform> inflWordforms;
		for(ExpressionWord w : expWords)
		{
			if(w.isStatic==false)
			{
				forma=w.bestWordform;
								
				filtrs = new AttributeValues(forma);
				filtrs.addAttribute(AttributeNames.i_Case,inflect); 
				filtrs.removeAttribute("Galotnes nr");
				filtrs.removeAttribute("Vārds");
				//FIXME varbūt vajag izmest vēl kādu īpašību

				/*
				inflectedPhrase+=locītājs.generateInflections(forma.getValue("Pamatforma"),false,filtrs).toString()+' ';
				*/
				inflWordforms=locītājs.generateInflections(forma.lexeme); //FIXME lexem is private
				for(Wordform wf : inflWordforms)
				{
					//System.out.println(wf.getToken());
					if(wf.isMatchingWeak(filtrs))
					{
						inflectedPhrase+=wf.getToken()+' ';
					}
				}
				
			}
			else
			{
				inflectedPhrase+=w.word.getToken();
				if(w.word.getToken()!="\"")
				{
					inflectedPhrase+=' ';
				}
			}
		}
		
		return inflectedPhrase.trim();
	}
	
	public static Category get(String s)
	{
		if (s==null)
		{
			return Category.org;
		}
		switch(s)
		{
		case "org":
			return Category.org;
		default:
				return null;
		}
	}


}
	


