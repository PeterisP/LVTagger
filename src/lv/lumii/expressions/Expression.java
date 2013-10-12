
package lv.lumii.expressions;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import lv.semti.morphology.analyzer.Analyzer;
import lv.semti.morphology.analyzer.Splitting;
import lv.semti.morphology.analyzer.Word;
import lv.semti.morphology.analyzer.Wordform;
import lv.semti.morphology.attributes.AttributeNames;
import lv.semti.morphology.attributes.AttributeValues;
import edu.stanford.nlp.ie.ner.CMMClassifier;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LVMorphologyAnalysis;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
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
		initClassifier("../LVTagger/models/lv-morpho-model.ser.gz"); // FIXME - nepamatoti paļaujamies ka tur tāds modelis būs
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
	
	/** 
	 * Izveido frāzi no jau notagotiem tokeniem - jābūt uzsetotai 'correct wordform' katrā objektā
	 * @param tokens - saraksts ar vārdiem
	 */
	public Expression(List<Word> tokens) {
		expWords=new LinkedList<ExpressionWord>();
		for (Word w: tokens) {
			expWords.add(new ExpressionWord(w, w.getCorrectWordform()));
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
			case hum : break; // Cilvēku vārdiem lokam visus tokenus
			case org : 
			case other: // Nesaprastas kategorijas lokam kā organizācijas
			{
				for (ExpressionWord w : expWords)
				{
					if(w.word.isRecognized()==false || w.correctWordform.lexeme==null) //FIXME nav īstā vieta, kur pārbaudīt, vai lexeme is null
					{
						w.isStatic=true;
						continue;
					}
					
					switch(w.correctWordform.getValue(AttributeNames.i_PartOfSpeech)) {
						case AttributeNames.v_Noun: {
							if(staticWhile || expWords.lastIndexOf(w)!=expWords.size()-1) {
								w.isStatic=true;
								break;
							}
							w.isStatic=false;
							break;
						}
						case AttributeNames.v_Adjective: {
							if(staticWhile) {
								w.isStatic=true;
								break;
							}
							w.isStatic=false;
							break;
						}
						case AttributeNames.v_Punctuation: 
						case AttributeNames.v_Numeral:
						case AttributeNames.v_Pronoun: { // TODO - vietniekvārdus teorētiski var locīt, taču netriviāli jo tie ir hardcoded 
							w.isStatic=true;
							//staticWhile=!staticWhile;  // PP: nesaprotu kāpēc Ginta to ielika; testiem nevajag un salauž 'Triju zvaigžņu ordenis'
							break;
						}
					}
				}
				break;
			}

			default : break;
		}
	}
	
	public String normalize() throws Exception
	{
		return inflect("Nominatīvs",null);
	}
	
	public Map<String,String> getInflections(String cat) {
		Map <String,String> result = new HashMap<String, String>();
		String inflection;
		String[] cases = {"Nominatīvs", "Ģenitīvs", "Datīvs", "Akuzatīvs", "Lokatīvs"};
		
		for (String i_case : cases) {
			try {
				inflection = inflect(i_case, cat);
				if (inflection != null) result.put(i_case, inflection);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return result;
	}
	
	public String inflect(String inflect, String cat) throws Exception
	{
		addPattern(cat);
		
		String inflectedPhrase="";
		
		AttributeValues filtrs;
		HashMap<String,String> attribute_map;
		Wordform forma, inflected_form;
		ArrayList<Wordform> inflWordforms;
		boolean matching = true;
		for(ExpressionWord w : expWords)
		{			
			if(w.isStatic==false)
			{
				forma=w.correctWordform; 
								
				filtrs = new AttributeValues(forma);
				filtrs.addAttribute(AttributeNames.i_Case,inflect);
				filtrs.removeAttribute(AttributeNames.i_EndingID);
				filtrs.removeAttribute(AttributeNames.i_LexemeID);
				filtrs.removeAttribute(AttributeNames.i_Guess);
				filtrs.removeAttribute(AttributeNames.i_Mija);
				filtrs.removeAttribute(AttributeNames.i_CapitalLetters);
				filtrs.removeAttribute(AttributeNames.i_Source);
				filtrs.removeAttribute(AttributeNames.i_SourceLemma);
				filtrs.removeAttribute(AttributeNames.i_Word);

				/*
				inflectedPhrase+=locītājs.generateInflections(forma.getValue("Pamatforma"),false,filtrs).toString()+' ';
				*/
				if (forma.lexeme == null || forma.getValue(AttributeNames.i_Guess) != null) // Deminutīviem kā Bērziņš cita lemma
					inflWordforms=locītājs.generateInflections(forma.getValue(AttributeNames.i_Lemma),false,filtrs);
				else 
					inflWordforms=locītājs.generateInflections(forma.lexeme);
				
				matching = false;
				for(Wordform wf : inflWordforms) {					
					if (wf.isMatchingWeak(filtrs)) {
						String token = wf.getToken();
						if (forma.isMatchingStrong(AttributeNames.i_CapitalLetters, AttributeNames.v_FirstUpper))
							token = token.substring(0, 1).toUpperCase() + token.substring(1);
						if (forma.isMatchingStrong(AttributeNames.i_CapitalLetters, AttributeNames.v_AllUpper))
							token = token.toUpperCase();

						inflectedPhrase += token+' ';						
						matching = true;
						break; //TODO - teorētiski  
					}
				}
				if (!matching) {
					//FIXME ko likt, ja nav ģenerēti locījumi lokāmajam vārdam (vv no locītāja, neatpazīti svešvārdi)
					System.err.printf("Expression nemācēja izlocīt vārdu %s uz %s\n",forma.getToken(),cat);
					inflectedPhrase += forma.getToken() + ' ';
//					System.err.println("Filtrs:");
//					filtrs.describe(new PrintWriter(System.err));
//					System.err.println("Vārds:");
//					forma.describe(new PrintWriter(System.err));
//					System.err.println("Varianti:");
//					for (Wordform wf : inflWordforms)
//						wf.describe(new PrintWriter(System.err));
					break;
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
		if (inflectedPhrase.trim().isEmpty()) {
			System.err.print("Expression sanāca tukšs rezultāts no frāzes [");
		    for (ExpressionWord w : expWords)
		    	System.err.print(w.word.getToken()+" ");
		    System.err.println("]");
		}
		return inflectedPhrase.trim();
	}
	
	public static Category get(String s)
	{
		if (s==null)
		{
			return Category.other;
		}
		switch(s)
		{
		case "org":
			return Category.org;
		case "hum":
			return Category.hum;
		default:
				return Category.other; //FIXME - nav labi šitā, tad jau var vispār stringus neparsēt bet prasīt ieejā enum
		}
	}


}
	


