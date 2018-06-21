/*******************************************************************************
 * Copyright 2012,2013,2014 Institute of Mathematics and Computer Science, University of Latvia
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
import static org.junit.Assert.*;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.List;

import lv.semti.morphology.analyzer.Word;
import lv.semti.morphology.analyzer.Wordform;
import lv.semti.morphology.attributes.AttributeNames;

import org.junit.BeforeClass;
import org.junit.Test;

import edu.stanford.nlp.ie.ner.CMMClassifier;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LVMorphologyAnalysis;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.sequences.LVMorphologyReaderAndWriter;
import org.w3c.dom.Attr;

import javax.management.Attribute;


public class TaggerTest {

	private static CMMClassifier<CoreLabel> cmm;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		cmm = CMMClassifier.getClassifier("models/lv-morpho-model.ser.gz");
	}
	
	private static List<CoreLabel> tag (String sentence) {
		return cmm.classify( LVMorphologyReaderAndWriter.analyzeSentence(sentence.trim() ));
	}
		
	private void assertPOS(List<CoreLabel> sentence, int word, String pos) {
		assertValue(sentence, word, AttributeNames.i_PartOfSpeech, pos);
	}
		
	private void assertValue(List<CoreLabel> sentence, int word, String key, String value) {	
		String token = sentence.get(word).getString(TextAnnotation.class);
		assertFalse(token.contains("<s>"));
		Word analysis = sentence.get(word).get(LVMorphologyAnalysis.class);
		Wordform maxwf = analysis.getMatchingWordform(sentence.get(word).getString(AnswerAnnotation.class), true);
		assertEquals(value, maxwf.getValue(key));
	}

    private void assertTag(List<CoreLabel> sentence, int word, String tag) {
        String token = sentence.get(word).getString(TextAnnotation.class);
        assertFalse(token.contains("<s>"));
        Word analysis = sentence.get(word).get(LVMorphologyAnalysis.class);
        Wordform maxwf = analysis.getMatchingWordform(sentence.get(word).getString(AnswerAnnotation.class), true);
        assertEquals(tag, maxwf.getTag());
    }

	private void assertLemma(List<CoreLabel> sentence, int word, String lemma) {
		String token = sentence.get(word).getString(TextAnnotation.class);
		assertFalse(token.contains("<s>"));
		Word analysis = sentence.get(word).get(LVMorphologyAnalysis.class);
		Wordform maxwf = analysis.getMatchingWordform(sentence.get(word).getString(AnswerAnnotation.class), true);
		assertEquals(lemma, maxwf.getValue(AttributeNames.i_Lemma));
	}

	private void describe(List<CoreLabel> sentence, int word) {
        String token = sentence.get(word).getString(TextAnnotation.class);
        assertFalse(token.contains("<s>"));
        Word analysis = sentence.get(word).get(LVMorphologyAnalysis.class);
        Wordform maxwf = analysis.getMatchingWordform(sentence.get(word).getString(AnswerAnnotation.class), true);
        PrintWriter izeja;
        try {
            izeja = new PrintWriter(new OutputStreamWriter(System.out, "UTF-8"));
            maxwf.describe(izeja);
            izeja.flush();
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }


	@Test
	public void sanity() {
		List<CoreLabel> sentence = tag("cirvis");
		assertPOS(sentence, 1, AttributeNames.v_Noun);
	}
	
	@Test
	public void roka() {
		List<CoreLabel> sentence = tag("Es roku roku.");
		assertPOS(sentence, 2, AttributeNames.v_Verb);
		assertPOS(sentence, 3, AttributeNames.v_Noun);
	}
	
	@Test
	public void gunta19dec_1() {
		// Guntas sūdzības pa skype 2012.12.19 - uz 2013.02.15 strādāja
		List<CoreLabel> word = tag("kontrolētājs");
		assertPOS(word, 1, AttributeNames.v_Noun);
		assertValue(word, 1, AttributeNames.i_Declension, "1");
		
		word = tag("Čeinijs");
		assertPOS(word, 1, AttributeNames.v_Noun);
		assertValue(word, 1, AttributeNames.i_Declension, "1");
		
		word = tag("GPS");
		assertPOS(word, 1, AttributeNames.v_Abbreviation);

		word = tag("Čārlzs");
		assertPOS(word, 1, AttributeNames.v_Noun);
		assertValue(word, 1, AttributeNames.i_Declension, "1");
	}

	@Test
	public void gunta19dec_2() {
		// Guntas sūdzības pa skype 2012.12.19 - uz 2013.02.15 nestrādāja
		List<CoreLabel> word;
		word = tag("padzīs");
		assertPOS(word, 1, AttributeNames.v_Verb);

		word = tag("marokāņu");
		assertPOS(word, 1, AttributeNames.v_Noun);
		assertValue(word, 1, AttributeNames.i_Declension, "2");

		word = tag("Makartnijas");
		assertPOS(word, 1, AttributeNames.v_Noun);
		assertValue(word, 1, AttributeNames.i_Declension, "4");
			
		word = tag("Bārbijas");
		assertPOS(word, 1, AttributeNames.v_Noun);
		assertValue(word, 1, AttributeNames.i_Declension, "4");

		word = tag("Ziemeļlatvijas");
		assertPOS(word, 1, AttributeNames.v_Noun);
		assertValue(word, 1, AttributeNames.i_Declension, "4");
		
		word = tag("nodotu");
		assertPOS(word, 1, AttributeNames.v_Verb);
		assertValue(word, 1, AttributeNames.i_Konjugaacija, AttributeNames.v_Nekaartns);

        word = tag("ambiciozs vīrietis");
        assertPOS(word, 1, AttributeNames.v_Adjective);
    }
	
	@Test
	public void gunta19dec_3() {
		// Guntas sūdzības pa skype 2012.12.19 - retās deklinācijas
		List<CoreLabel> word = tag("ragus");
		assertPOS(word, 1, AttributeNames.v_Noun);
		assertValue(word, 1, AttributeNames.i_Declension, "1");
			
		word = tag("dermatovenerologi");
		assertPOS(word, 1, AttributeNames.v_Noun);
		assertValue(word, 1, AttributeNames.i_Declension, "1");

	}
	
	@Test
	public void lemmas() {
		List<CoreLabel> word = tag("neizpaušana");
		assertLemma(word, 1, "neizpaušana");  // bija gļuks ar 'neizpausšana'
	}
	
	@Test
	public void noliegumi() {
		List<CoreLabel> sentence = tag("Es šodien neiešu nepastaigāties un nedomāt.");
		assertLemma(sentence, 3, "neiet");
		assertLemma(sentence, 4, "nepastaigāties");
		assertLemma(sentence, 6, "nedomāt");
		assertValue(sentence, 3, AttributeNames.i_Noliegums, AttributeNames.v_Yes);
		assertValue(sentence, 4, AttributeNames.i_Noliegums, AttributeNames.v_Yes);
		assertValue(sentence, 6, AttributeNames.i_Noliegums, AttributeNames.v_Yes);
	}

//	Mistiskā kārtā visādi vārdi tagojas kā īpašvārdi
	@Test
    public void īpašvārdi_2017mar() {
	    String vide = "Tūrisma attīstības integrēšana vietējā un nacionālā stratēģiskās plānošanas darbā, ietekmes uz vidi izvērtēšana";
        List<CoreLabel> tagged = tag(vide);
        describe(tagged, 13);
        assertLemma(tagged, 13, "vide");
        assertValue(tagged, 13, AttributeNames.i_NounType, null /*AttributeNames.v_CommonNoun*/);
    }

    @Test
    public void issue10() {
        List<CoreLabel> word = tag("trešdaļa");
        assertValue(word, 1, AttributeNames.i_PartOfSpeech, AttributeNames.v_Noun);

        word = tag("trīs");
        assertValue(word, 1, AttributeNames.i_PartOfSpeech, AttributeNames.v_Numeral);

        word = tag("3");
        assertValue(word, 1, AttributeNames.i_PartOfSpeech, AttributeNames.v_Residual);
        assertValue(word, 1, AttributeNames.i_ResidualType, AttributeNames.v_Number);
    }

    @Test
    public void tagset_update_2017okt() {
        List<CoreLabel> word = tag("braukdams");
        assertTag(word, 1, "vmnppmsn0000n");

        word = tag("saasinātāks");
        assertTag(word, 1, "vmnpdmsnpsncn");

        word = tag("nenieka");
        assertTag(word, 1, "r0q");

        word = tag("svarīgi");
        assertTag(word, 1, "rpm");
    }


	@Test
	public void nebūt() {
        List<CoreLabel> sentence = tag("Man nav alus.");
        assertLemma(sentence, 2, "nebūt");
    }

//    Verbu tipu problēmas
    @Test
    public void verbtypes() {
        List<CoreLabel> sentence = tag("Darbs tiek darīts.");
        assertValue(sentence, 2, AttributeNames.i_VerbType, AttributeNames.v_TiktTapt);

        sentence = tag("Darbs ir izdarīts.");
        assertValue(sentence, 2, AttributeNames.i_VerbType, AttributeNames.v_Buut);
    }

    @Test
    public void laura_20180614() {
        List<CoreLabel> sentence = tag("Lange (Lange) un Regini (Regini) norādījuši, ka elastību lielā mērā var saistīt ar darba tirgus regulējošo aspektu samazināšanu, kā arī valsts vai pārvaldības mazāku „iejaukšanos” noteiktu sistēmu noteikšanā.");
        assertTag(sentence, 12, "cs");
        assertTag(sentence, 26, "q");
        assertTag(sentence, 10, "vmnpdmpnasnpn");
//        Word analysis = sentence.get(16).get(LVMorphologyAnalysis.class);
//        analysis.describe(System.out);
//        Wordform maxwf = analysis.getMatchingWordform(sentence.get(16).getString(AnswerAnnotation.class), true);
//        maxwf.describe(System.out);
        assertValue(sentence, 16, AttributeNames.i_VerbType, AttributeNames.v_Modaals);
    }

    @Test
    public void laura_20180619() {
        List<CoreLabel> sentence = tag("Kas ir ergonoms risinājums?");
        String token = sentence.get(3).getString(TextAnnotation.class);
        assertFalse(token.contains("<s>"));
        Word analysis = sentence.get(3).get(LVMorphologyAnalysis.class);
//        analysis.describe(System.out);
        boolean found = false;
        for (Wordform wf : analysis.wordforms) {
            if (wf.getTag().equalsIgnoreCase("ncmsn1")) found = true;
        }
        assertTrue(found);
    }

    @Test
    public void mistika() {
	    // atšķīrās rezultāti, laižot it kā to pašu versiju caur morphotagger.sh un webservices
        List<CoreLabel> sentence = tag("Vārds Ford ir slikts.");
        assertTag(sentence, 2, "xf");

        sentence = tag("Vārds fuck ir slikts.");
        assertTag(sentence, 2, "xf");

        sentence = tag("Vārds DJ ir slikts.");
        assertTag(sentence, 2, "y");

        sentence = tag("Vārds Fords ir slikts.");
        assertTag(sentence, 2, "ncmsn1");
    }
}
