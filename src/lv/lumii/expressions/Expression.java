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
import edu.stanford.nlp.ie.AbstractSequenceClassifier;
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
	private static transient AbstractSequenceClassifier<CoreLabel> morphoClassifier = null;
	protected static transient Analyzer analyzer = null;
	public Category category = Category.other; 
	
	private static void initClassifier(String model) throws Exception {
		morphoClassifier = CMMClassifier.getClassifier(new File(model));		
		analyzer = LVMorphologyReaderAndWriter.getAnalyzer(); // Assumption - that the morphology model actually loads the LVMorphologyReaderAndWriter data, so it should be filled.
	}
	
	public static void setClassifier(AbstractSequenceClassifier<CoreLabel> preloadedClassifier) {
		morphoClassifier = preloadedClassifier;
		analyzer = LVMorphologyReaderAndWriter.getAnalyzer(); // Assumption - that the morphology model actually loads the LVMorphologyReaderAndWriter data, so it should be filled.
	}
	
	private static void initClassifier() throws Exception {
		initClassifier("../LVTagger/models/lv-morpho-model.ser.gz"); // FIXME - nepamatoti paļaujamies ka tur tāds modelis būs
	}
	
	/*
	 * Izveido frāzi no teksta, pirms tam to notagojot ar morfotageri
	 * @param phrase - pati frāze
	 * @param category - frāzes kategorija - person org loc
	 * @param knownLemma - ja true, tad frāze ir 'pamatformā'; ja false - tad kautkādā formā kam jāmeklē pamatforma
	 */
	public Expression(String phrase, String phraseCategory, boolean knownLemma) 
	{
		if (morphoClassifier == null)
			try {
				initClassifier();
			} catch (Exception e) {
				e.printStackTrace();
				throw new Error("Expression inflection: morphoClassifier not supplied and unable to load from default values");
			} 
		setCategory(phraseCategory);

		loadUsingTagger(phrase, knownLemma);
		//loadUsingBestWordform(); alternatīva ja nav tageris
		setPattern(); // Daļa, kas izdomā, kurus vārdus locīt līdzi galvenajam un kurus atstāt
	}
		
	/** 
	 * Izveido frāzi no jau notagotiem tokeniem - jābūt uzsetotai 'correct wordform' katrā objektā
	 * @param tokens - saraksts ar vārdiem
	 */
	public Expression(List<Word> tokens, String phraseCategory) {
		setCategory(phraseCategory);
		expWords=new LinkedList<ExpressionWord>();
		for (Word w: tokens) {
			expWords.add(new ExpressionWord(w, w.getCorrectWordform()));
		}
		setPattern();
	}
	
	public void loadUsingBestWordform(String phrase)
	{
		LinkedList <Word> words = Splitting.tokenize(analyzer, phrase);
		expWords=new LinkedList<ExpressionWord>();
		for (Word w : words)
		{
			ExpressionWord tmp = new ExpressionWord(w);
			expWords.add(tmp);
		}
	}
	
	public void loadUsingTagger(String phrase, boolean knownLemma) 
	{
		expWords=new LinkedList<ExpressionWord>();
		
	    List<Word> words = Splitting.tokenize(analyzer, phrase);
	    for (Word word : words) { // filtrējam variantus, ņemot vērā to ko zinam par frāzi un kategoriju	    	
	    	if (knownLemma && category == Category.hum) { // personvārdiem metam ārā nenominatīvus, ja ir kāds variants ar nominatīvu (piemēram 'Dombrovska' kā siev. nominatīvs vai vīr. ģenitīvs)
	    		LinkedList<Wordform> izmetamie = new LinkedList<Wordform>();
	    		for (Wordform wf : word.wordforms) {
	    			if (!wf.isMatchingStrong(AttributeNames.i_Case, AttributeNames.v_Nominative) // Nominatīvs vai nelokāms
	    					&& !wf.isMatchingStrong(AttributeNames.i_Declension, AttributeNames.v_NA)) {
	    				izmetamie.add(wf);
	    			}	    				
	    		}
	    		if (izmetamie.size() < word.wordforms.size()) // ja ir kaut viens derīgs 
	    			word.wordforms.removeAll(izmetamie);
	    	}
	    }
	    
		List<CoreLabel> sentence = LVMorphologyReaderAndWriter.analyzeSentence2(words);
		sentence = morphoClassifier.classify(sentence);
		
		String token;
		Word analysis;
		Wordform maxwf;
		for(CoreLabel label : sentence)
		{
			token = label.getString(TextAnnotation.class);
			
			if(token.equals("<s>")) //kāpēc tiek pievienoti <s>?  PP - tageris skatās uz vārda apkaimi; teikuma sākuma/beigu vārdi ir īpaši, to signalizē pieliekot sākumā/beigās <s>
			{
				continue;
			}
			
		  analysis = label.get(LVMorphologyAnalysis.class);
		  
		  maxwf = analysis.getMatchingWordform(label.getString(AnswerAnnotation.class), false);
		  if (maxwf.isMatchingStrong(AttributeNames.i_PartOfSpeech, AttributeNames.v_Verb)) {
			  // Mēs varam pieņemt ka entītijas ir 'nounphrase' un ja beigās ir verbs (nevis divdabis) tad tas ir tagera gļuks (piemērs 'DPS saraksta')
			  for (Wordform wf : analysis.wordforms) {
				  if (wf.isMatchingStrong(AttributeNames.i_PartOfSpeech, AttributeNames.v_Noun))
					  maxwf = wf; // TODO - varbūt var mazliet gudrāk, ja ir vairāki tad ņemt ticamāko
			  }
		  }
		  
		  ExpressionWord tmp = new ExpressionWord(analysis, maxwf);
		  expWords.add(tmp);
		}
		
	}
	
	
	public void setPattern() //Method adds isStatic attribute to the Expression word, which indicates, whether to inflect the Word
	{
		if (expWords.size() == 0) return;
		boolean staticWhile=false;
		
		switch(category)
		{
			case hum : // Cilvēku vārdiem lokam visus tokenus ko var, izņemot gadījumos kad klāt ir arī amats (valdes priekšsēdētājs Ivars Zariņš)
				for (ExpressionWord w : expWords) {
					w.isStatic = false;
					if (w.word.isRecognized()==false) {
						w.isStatic=true;
						continue;
					}
					switch(w.correctWordform.getValue(AttributeNames.i_PartOfSpeech)) {
						case AttributeNames.v_Verb:
						case AttributeNames.v_Punctuation: 
						case AttributeNames.v_Numeral:
						case AttributeNames.v_Abbreviation:
						case AttributeNames.v_Preposition:
						case AttributeNames.v_Pronoun: { // TODO - vietniekvārdus teorētiski var locīt, taču netriviāli jo tie ir hardcoded 
							w.isStatic=true;
							break;
						}
						case AttributeNames.v_Noun:
						case AttributeNames.v_Adjective: {
							int wordPos = expWords.lastIndexOf(w);
							if (expWords.size()-wordPos > 2) { // ja ir 3+ vārdus no beigām...
								if (!w.correctWordform.isMatchingStrong(AttributeNames.i_NounType, AttributeNames.v_ProperNoun) && // .. īpašvārdus locīsim 
									w.correctWordform.isMatchingWeak(AttributeNames.i_Guess, AttributeNames.v_NoGuess) && 
									!w.correctWordform.isMatchingStrong(AttributeNames.i_Source, "generateInflections")) // neuzminētos (kas laikam arī ir īpašvārdi) locīsim
									w.isStatic = true; // pārējos gan ne
							}
							break;
						}
					}
				}
			break;
			
			
			case org : 
			case loc : // daudzvārdu lokācijas ('Ludzas pilsēta') lokās praktiski identiski
			case other: // Nesaprastas kategorijas lokam kā organizācijas
			{
				List<ExpressionWord> phraseWords;
				if (expWords.getLast().correctWordform.getToken().equalsIgnoreCase("\"")) {
					// piemēram 'sabiedrība "trīs ali" ' - to kas pēdiņās, to nelokam bet 'galva' ir pirms pēdiņām
					boolean otrapēdiņa = false;
					phraseWords = new LinkedList<ExpressionWord>();
					for (int j = expWords.size()-2; j>=0; j--) {
						if (!otrapēdiņa) 
							expWords.get(j).isStatic = true;
						else
							phraseWords.add(0, expWords.get(j)); // insertojam sākumā
						if (expWords.get(j).correctWordform.getToken().equalsIgnoreCase("\""))
							otrapēdiņa = true;
					}
				} else phraseWords = (List<ExpressionWord>) expWords.clone();
				
				boolean esampēdiņās = false; // Arī ja pa vidu ir pēdiņas, tad to kas pa vidu to nelokam
				for (ExpressionWord w : phraseWords)
				{
					if (w.word.isRecognized()==false || esampēdiņās) {
						w.isStatic=true;
						continue;
					}
					
					if (w.correctWordform.getToken().equalsIgnoreCase("\""))
						esampēdiņās = true;							
					
					switch (w.correctWordform.getValue(AttributeNames.i_PartOfSpeech)) {
						case AttributeNames.v_Noun: {
							if (phraseWords.lastIndexOf(w)!=phraseWords.size()-1) {
								w.isStatic=true;
								break;
							}
							w.isStatic=false;
							break;
						}
						case AttributeNames.v_Adjective: {
							int wordPos = phraseWords.lastIndexOf(w);
							// ja īpašības vārds saskaņojas ar "galveno" vārdu, tad loka līdzi,ja ne, tad ir statisks.
							if (wordPos == phraseWords.size()-2) {
								w.isStatic = false; // ja viņš ir priekšpēdējais, tad, pieņemot ka pēdējais ir lietvārds, lokam 
								break;
							}
							if (wordPos == phraseWords.size()-3) {
								w.isStatic = !phraseWords.get(phraseWords.size()-2).correctWordform.isMatchingStrong(AttributeNames.i_PartOfSpeech, AttributeNames.v_Adjective);
								// ja nu ir vēl viens cits īpašības vārds pa vidu (vidējā speciālā izglītība) tad arī der, savādāk gan ne
								break;
							}
							// nu un ja kautkas cits, tad laikam ir statisks
							w.isStatic = true; 
							break;
						}
						case AttributeNames.v_Verb:
						case AttributeNames.v_Punctuation: 
						case AttributeNames.v_Numeral:
						case AttributeNames.v_Abbreviation:
						case AttributeNames.v_Preposition:
						case AttributeNames.v_Pronoun: { // TODO - vietniekvārdus teorētiski var locīt, taču netriviāli jo tie ir hardcoded 
							w.isStatic=true;
							break;
						}
					}
				}
				break;
			}

			default : break;
		}
	}
	
	public String normalize() 
	{
		return inflect("Nominatīvs");
	}
	
	private void setCategory(String s)
	{
		if (s==null) {
			category = Category.other;
		} else switch(s) {
		case "org": case "organization":
			category = Category.org; break;
		case "hum": case "person":
			category = Category.hum; break;
		case "loc": case "location":
			category = Category.loc; break;
		default:
			category = Category.other; //FIXME - nav labi šitā, tad jau var vispār stringus neparsēt bet prasīt ieejā enum
		}
	}
	
	public Map<String,String> getInflections() {
		Map <String,String> result = new HashMap<String, String>();
		String inflection;
		String[] cases = {"Nominatīvs", "Ģenitīvs", "Datīvs", "Akuzatīvs", "Lokatīvs"};
		
		for (String i_case : cases) {
			try {
				inflection = inflect(i_case);
				if (inflection != null) result.put(i_case, inflection);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return result;
	}
	
	public String inflect(String inflectCase) 
	{
//		for (ExpressionWord w : expWords)
//			System.err.printf("Vārds '%s' būs statisks - '%b'\n", w.word.getToken(), w.isStatic);
		
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
				filtrs.addAttribute(AttributeNames.i_Case,inflectCase);
				filtrs.removeAttribute(AttributeNames.i_EndingID);
				filtrs.removeAttribute(AttributeNames.i_LexemeID);
				filtrs.removeAttribute(AttributeNames.i_Guess);
				filtrs.removeAttribute(AttributeNames.i_Mija);
				filtrs.removeAttribute(AttributeNames.i_CapitalLetters);
				filtrs.removeAttribute(AttributeNames.i_Source);
				filtrs.removeAttribute(AttributeNames.i_SourceLemma);
				filtrs.removeAttribute(AttributeNames.i_Word);
				if (!forma.isMatchingStrong(AttributeNames.i_NumberSpecial, AttributeNames.v_PlurareTantum) 
						&& category == Category.org ) {
					// ja nav daudzskaitlinieks, tad ņemsim ka vienskaitļa formu
					filtrs.addAttribute(AttributeNames.i_Number, AttributeNames.v_Singular);
				}
				//filtrs.removeAttribute(AttributeNames.i_Definiteness);
				if (forma.isMatchingStrong(AttributeNames.i_Definiteness, AttributeNames.v_Indefinite))
					filtrs.removeAttribute(AttributeNames.i_PartOfSpeech); // OOV gadījumā nevar nošķirt lietvārdus no nenoteiktajiem īpašības vārdiem
				if (forma.getToken().endsWith("ā") && forma.isMatchingStrong(AttributeNames.i_Definiteness, AttributeNames.v_Definite))
					filtrs.removeAttribute(AttributeNames.i_PartOfSpeech); // OOV gadījumā minētājs piedāvās lietvārdu
				if (forma.getToken().endsWith("us") && forma.isMatchingStrong(AttributeNames.i_Declension, "6")) {
					//FIXME - tāds nedrīkstētu būt, tas jālabo analizatorā/minētājā/tagerī.. piemērs - 'Zlatkus Ēriks' iedod kā 6. dekl
					filtrs.removeAttribute(AttributeNames.i_Declension);
					filtrs.removeAttribute(AttributeNames.i_Gender);
				}
				if (forma.getToken().endsWith("a") && forma.isMatchingStrong(AttributeNames.i_Gender, AttributeNames.v_Masculine)) {
					//FIXME - Andrejs Vētra -> tur jāmāk korekti izlocīt, pagaidām sieviešu dzimtes sugasvārds nomāc visu statistiku
					filtrs.removeAttribute(AttributeNames.i_Declension);
					filtrs.removeAttribute(AttributeNames.i_Gender);
				}
				if (forma.getToken().endsWith("ais") && forma.isMatchingStrong(AttributeNames.i_Declension, "2")) { 
					// FIXME - vispār tas rāda par to ka minētājam būtu agresīvi jāņem vērā 'atļautie' burti pirms galotnes; 2.deklinācijā -ais nemēdz būt..
					filtrs.removeAttribute(AttributeNames.i_PartOfSpeech); // OOV gadījumā (Turlais) tageris reizem iedod lietvārdu
					filtrs.removeAttribute(AttributeNames.i_Declension);
					filtrs.removeAttribute(AttributeNames.i_Lemma); // jo lemma ir turls nevis turlais
				}
				//FIXME - vai visiem vai pēdējam? Un profesija te domāta, nevis jebkas, varbūt tas ir īpaši jāatlasa
				if (category==Category.other || w.word.getToken().startsWith("apvienīb")) {
					filtrs.addAttribute(AttributeNames.i_Number, AttributeNames.v_Singular);
				}
				if (w.word.getToken().startsWith("vēlēšan") // izņēmums lai nemēģina vienskaitļa variantus par vēlēšanu likt
					|| w.word.getToken().equalsIgnoreCase("Salas") || w.word.getToken().equalsIgnoreCase("Salās")) { 
					filtrs.addAttribute(AttributeNames.i_Number, AttributeNames.v_Plural);
				}
				if (forma.getToken().endsWith("o") && forma.isMatchingStrong(AttributeNames.i_Gender, AttributeNames.v_Masculine)) {
					//FIXME - Tageris frāzē "vidējo speciālo izglītību" pirmo vārdu notago kā vīriešu dzimti..
					if (expWords.getLast().correctWordform.getValue(AttributeNames.i_Lemma).equalsIgnoreCase("izglītība"))
						filtrs.addAttribute(AttributeNames.i_Gender, AttributeNames.v_Feminine);
				}
					
				/*
				inflectedPhrase+=locītājs.generateInflections(forma.getValue("Pamatforma"),false,filtrs).toString()+' ';
				*/
				if (forma.lexeme == null || !forma.isMatchingWeak(AttributeNames.i_Guess, AttributeNames.v_NoGuess)) // Deminutīviem kā Bērziņš cita lemma
					inflWordforms=analyzer.generateInflections(forma.getValue(AttributeNames.i_Lemma),false,filtrs);
				else 
					inflWordforms=analyzer.generateInflections(forma.lexeme, w.word.getToken());
				
				filtrs.removeAttribute(AttributeNames.i_Lemma); // jo reizēm (dzimtes utml) te būscita lemma nekā notagotajā; piemēram vidēja/vidējs
				
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
				
				if (!matching && forma.getToken().endsWith("o") && forma.isMatchingStrong(AttributeNames.i_Definiteness, AttributeNames.v_Definite)) {
					// Reizēm nelokāmos personvārdus ar -o ('Žverelo') tageris izdomā nosaukt par noteiktajiem īpašības vārdiem, kas arī ir variants..
					inflectedPhrase += forma.getToken() +' ';						
					matching = true;
				}
				
				if (!matching && forma.getToken().endsWith("i") && forma.isMatchingStrong(AttributeNames.i_PartOfSpeech, AttributeNames.v_Noun)) {
					// Reizēm nelokāmos personvārdus ar -i ('Tennila Veli Pekka') tageris izdomā nosaukt par apstākļa vārdiem
					inflectedPhrase += forma.getToken() +' ';						
					matching = true;
				}
				
				if (!matching && inflWordforms.size() == 0) {
					// 'Zalcmanis Raivo' tā bija - FIXME, kautkādiem variantiem būtu jābūt :-/
					inflectedPhrase += forma.getToken() +' ';						
					matching = true;
				}
				
				if (!matching && forma.isMatchingStrong(AttributeNames.i_Declension, "5")) {
					// "Jānis Uve" piemērs - šajā minēšanā tageris iedod vīriešu dzimti bet locītājs sieviešu
					// FIXME - jālabo, bet ne tagad
					inflectedPhrase += forma.getToken() +' ';						
					matching = true;
				}
				
				if (!matching && forma.isMatchingStrong(AttributeNames.i_Declension, "6")) {
					// "Zaļaiskalns Sandra" piemērs - šajā minēšanā tageris iedod sieviešu dzimti bet locītājs vīriešu
					// FIXME - jālabo, bet ne tagad
					inflectedPhrase += forma.getToken() +' ';						
					matching = true;
				}
				
				if (!matching && forma.isMatchingStrong(AttributeNames.i_CapitalLetters, AttributeNames.v_AllUpper)) {
					// "RPVIA" piemērs - minētājs saka ka varētu būt arī lietvārds bet tas īsti neder
					// FIXME - jālabo, bet ne tagad
					inflectedPhrase += forma.getToken() +' ';						
					matching = true;
				}
				
				if (!matching) {									
					//FIXME ko likt, ja nav ģenerēti locījumi lokāmajam vārdam (vv no locītāja, neatpazīti svešvārdi)
					String frāze = "";
					for (ExpressionWord w2 : expWords)
						frāze += w2.correctWordform.getToken() + " ";
					// TODO - interesanto frāžu logging - System.err.printf("Expression nemācēja izlocīt vārdu %s uz %s frāzē '%s'\n",forma.getToken(), inflect, frāze.trim());
					inflectedPhrase += forma.getToken() + ' ';
//					System.err.println("Filtrs:");
//					filtrs.describe(new PrintWriter(System.err));
//					System.err.println("Vārds:");
//					forma.describe(new PrintWriter(System.err));
//					System.err.println("Varianti:");
//					for (Wordform wf : inflWordforms) {
//						wf.describe(new PrintWriter(System.err));
//						System.err.println();
//					}
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
		// TODO - interesanto frāžu logging
//		if (inflectedPhrase.trim().isEmpty()) {
//			System.err.print("Expression sanāca tukšs rezultāts no frāzes [");
//		    for (ExpressionWord w : expWords)
//		    	System.err.print(w.word.getToken()+" ");
//		    System.err.println("]");
//		}
		if (inflectedPhrase.endsWith(" .")) inflectedPhrase = inflectedPhrase.substring(0, inflectedPhrase.length()-2) + ".";
		return inflectedPhrase.trim();
	}

	public String getWordPartOfSpeech(String string) {
		Word vārds = analyzer.analyze(string); //FIXME - jāskatās, kas te bija Guntai un varbūt vajag AnalyzeLemma saukt
		if (vārds.getBestWordform() == null) return AttributeNames.v_Noun;
		return vārds.getBestWordform().getValue(AttributeNames.i_PartOfSpeech);
	}

	public void describe(PrintWriter pipe) {
		//pipe.format("Word\tchosen tag\n");
		for (ExpressionWord w : expWords) {
			pipe.format("%s\t%s\n", w.word.getToken(), w.correctWordform.getTag());
		}
		pipe.flush();
	}


}
	


