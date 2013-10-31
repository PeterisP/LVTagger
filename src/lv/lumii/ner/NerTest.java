package lv.lumii.ner;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.Properties;

import lv.semti.morphology.analyzer.Splitting;
import lv.semti.morphology.analyzer.Word;
import lv.semti.morphology.analyzer.Wordform;
import lv.semti.morphology.attributes.AttributeNames;

import org.junit.BeforeClass;
import org.junit.Test;

import edu.stanford.nlp.ie.ner.CMMClassifier;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.FullTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.GoldAnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LVMorphologyAnalysis;
import edu.stanford.nlp.ling.CoreAnnotations.MorphologyFeatureStringAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagGoldAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.sequences.LVMorphologyReaderAndWriter;



public class NerTest {
	private static final String nerPropertiesFile = "lv-ner-tagger.prop";
	private static NerPipe ner;
	private static final String morphoClassifierModel = "../LVTagger/models/lv-morpho-model.ser.gz";
	private static transient CMMClassifier<CoreLabel> morphoClassifier = null;	
	
	@BeforeClass
    public static void oneTimeSetUp() throws IOException, ClassCastException, ClassNotFoundException {
		Properties props = new Properties();
		props.load(new FileInputStream(nerPropertiesFile));
		ner = new NerPipe(props);
		morphoClassifier = CMMClassifier.getClassifier(new File(morphoClassifierModel));
    }

	@Test
	public void sample() throws Exception {
		List<CoreLabel> doc = ner.classify("test/ner/sample_ner.conll");
		print(doc);
		testAll(doc);
	}
	
	@Test
	public void VentspilsDomesPriekšēdētājs() {
		String s = "Venstpils domes priekšēdētājs Lembergs";
		List<CoreLabel> doc = classifyString(s);
		print(doc);
		test(doc, 3, "person");
		test(doc, 2, "profession");
	}
	
	@Test
	public void datums() {
		String s = "2013. gada 13. maijs";
		List<CoreLabel> doc = classifyString(s);
		print(doc);
		test(doc, 0, "time");
		test(doc, 1, "time");
		test(doc, 2, "time");
		test(doc, 3, "time");
	}
	
	@Test
	public void profesija() {
		String s = "Vadītāja palīgs";
		List<CoreLabel> doc = classifyString(s);
		print(doc);
		test(doc, 0, "profession");
		test(doc, 1, "profession");
	}
	
	
	@Test
	public void siaCirvis() {
		String s = "SIA \"Cirvis\"";
		List<CoreLabel> doc = classifyString(s);
		print(doc);
		test(doc, 2, "organization");
	}
	@Test
	public void siaCirvis2() {
		String s = "\"Cirvis\"";
		List<CoreLabel> doc = classifyString(s);
		print(doc);
		test(doc, 1, "organization");
	}
	
	
	
	public static void print(List<CoreLabel> doc) {
		System.err.println("\n--- TEST ---");
		for (CoreLabel w : doc) {
			System.err.println(String.format("%s\t%s\t%s\t%s", w.word(), w.lemma(), w.ner(), w.getString(NamedEntityTagGoldAnnotation.class)));
		};
	}
	
	public static void testAll(List<CoreLabel> doc) {
		for (CoreLabel l : doc) {
			String gold = l.get(NamedEntityTagGoldAnnotation.class);
			String predicted = l.get(NamedEntityTagAnnotation.class);
	        if (predicted == null) {
	        	predicted = l.get(AnswerAnnotation.class);
	        }
	        //System.err.println(l.word() + "\t" + gold + "\t" +  predicted);
			assertEquals("Not equal " + l.word() + " [gold="+gold+ ", answer=" + predicted + "] ", gold, predicted);
		}
	}
	
	public static List<CoreLabel> classifyString(String text) {
		List<CoreLabel> doc = morphoClassify(text);
		ner.classify(doc);
		ListIterator<CoreLabel> it = doc.listIterator();
		//remove sentence seperators
		while(it.hasNext()) {
			CoreLabel w = it.next();
			if (w.ner() == null) w.setNER(w.get(AnswerAnnotation.class));
			if (w.word().contains("<s>")) it.remove();
		}
		return doc;
	}
	
	public static void test(List<CoreLabel> doc, int index, String category) {
		CoreLabel w = doc.get(index);
		assertEquals(String.format("Not equal %s + [test=%s answer=%s]", w.word(), category, w.ner()), category, w.ner());
		//System.err.println(w.word() + "\t" + category + "\t" + w.ner());
	}
	
	public static List<CoreLabel> morphoClassify(String text) {
		List<CoreLabel> doc = new LinkedList<CoreLabel>();
		LinkedList<LinkedList<Word>> sentences = Splitting.tokenizeSentences(LVMorphologyReaderAndWriter.getAnalyzer(), text);
		for (LinkedList<Word> sentence : sentences) {
			List<CoreLabel> sent = LVMorphologyReaderAndWriter.analyzeSentence2(sentence);
			sent = morphoClassifier.classify(sent);			
			int counter = 1;
			for (CoreLabel word : sent) {
				String token = word.getString(TextAnnotation.class);
				if (token.contains("<s>")) continue;
				token = token.replace(' ', '_');
				Word analysis = word.get(LVMorphologyAnalysis.class);
				Wordform mainwf = analysis.getMatchingWordform(word.getString(AnswerAnnotation.class), false); 
				if (mainwf != null) {
					String lemma = mainwf.getValue(AttributeNames.i_Lemma);
					lemma = lemma.replace(' ', '_');
					String fullTag = mainwf.getTag();					
					String pos = fullTag.length() > 0 ? fullTag.substring(0,1) : "_";					
					StringBuilder morphoString = new StringBuilder();
					for (Entry<String, String> entry : mainwf.entrySet()) { // visi attributevalue paariishi
						morphoString.append(entry.getKey().replace(' ', '_'));
						morphoString.append('=');
						morphoString.append(entry.getValue().replace(' ', '_'));
						morphoString.append('|');
					}
					morphoString.deleteCharAt(morphoString.length()-1); // noņemam peedeejo | separatoru, kas ir lieks

					CoreLabel w = new CoreLabel();
					w.setIndex(counter++);
					w.setWord(token);
					w.setLemma(lemma);
					w.set(FullTagAnnotation.class, fullTag);
					w.setTag(pos);
					w.set(MorphologyFeatureStringAnnotation.class, morphoString.toString());
					doc.add(w);
				}
			}
			CoreLabel w = new CoreLabel();
			w.setWord("<s>"); // seperator
			w.setIndex(-1);
			w.setLemma("_");
			w.set(FullTagAnnotation.class, "_");
			w.setTag("_");
			w.set(MorphologyFeatureStringAnnotation.class, "_");
			doc.add(w);
		}
		return doc;
	}
	
	
}
