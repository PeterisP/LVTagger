/*******************************************************************************
 * Copyright 2013,2014 Institute of Mathematics and Computer Science, University of Latvia
 * Author: Artūrs Znotiņš
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
package lv.lumii.ner.analysis;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.ListNERSequenceClassifier;
import edu.stanford.nlp.ie.NERClassifierCombiner;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ie.regexp.RegexNERSequenceClassifier;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerObjectAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.AnswersAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagGoldAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.sequences.LVCoNLLDocumentReaderAndWriter;
import edu.stanford.nlp.sequences.LVCoNLLDocumentReaderAndWriter.outputTypes;
import edu.stanford.nlp.util.StringUtils;

public class ClassifierComparator {
	
	public class NamedEntities {
		public class NamedEntity {
			String text;
			String key;
			String goldCat;
			String guessCat;
			int count = 0;
			int tp = 0;
			int fn = 0;
			int fp = 0;
			NamedEntity(String text) {
				this.text = text;
			}
			void incCount() {
				count++;
			}
			void incTp() {
				tp++;
			}
			void incFn() {
				fn++;
			}
			void incFp() {
				fp++;
			}
		}
		Map<String,NamedEntity> entities = new HashMap<>();
		
		void addEntity(String key, String text) {
			String[] textParts = text.split("\\|");
			NamedEntity en = new NamedEntity(textParts[0]);
			en.goldCat = textParts[1];
			en.guessCat = textParts[2];
			entities.put(key, en);
		}
		
		void incCount(String key, String text) {
			if (!entities.containsKey(key)) addEntity(key, text);
			NamedEntity ne = entities.get(key);
			ne.key = key;
			ne.incCount();
			entities.put(key, ne);
		}
		
		void incTp(String key, String text) {
			if (!entities.containsKey(key)) addEntity(key, text);
			NamedEntity ne = entities.get(key);
			ne.key = key;
			ne.incTp();
			ne.incCount();
			entities.put(key, ne);
		}
		
		void incFn(String key, String text) {
			if (!entities.containsKey(key)) addEntity(key, text);
			NamedEntity ne = entities.get(key);
			ne.key = key;
			ne.incFn();
			ne.incCount();
			entities.put(key, ne);
		}
		
		void incFp(String key, String text) {
			if (!entities.containsKey(key)) addEntity(key, text);
			NamedEntity ne = entities.get(key);
			ne.key = key;
			ne.incFp();
			entities.put(key, ne);
		}
		
		void preview() {
			System.out.printf("ENTITY\tGOLD\tGUESS\tCOUNT\tTP\tFP\tFN\n");
			for (String key : entities.keySet()) {
				NamedEntity ne = entities.get(key);
				System.out.printf("%s\t%s\t%s\t%d\t%d\t%d\t%d\n", ne.text, ne.goldCat, ne.guessCat, ne.count, ne.tp, ne.fp, ne.fn);
			}
		}
	}	
	
	Properties props;
	List<NERClassifierCombiner> classifiers = new ArrayList<>();
	List<CoreLabel> document;

	ClassifierComparator(Properties props) {
		this.props = props;
	}
	
	void addClassifier(NERClassifierCombiner c) {
		classifiers.add(c);
	}
	
	void addClassifier(List<AbstractSequenceClassifier<CoreLabel>> classifiers) throws FileNotFoundException {
		NERClassifierCombiner ncc = new NERClassifierCombiner(classifiers);
		addClassifier(ncc);
	}
	
	void addClassifier(AbstractSequenceClassifier<CoreLabel> c) throws FileNotFoundException {
		List<AbstractSequenceClassifier<CoreLabel>> classifiers = new ArrayList<>();
		classifiers.add(c);
		addClassifier(classifiers);
	}
	
	void classify(List<CoreLabel> document) {
		this.document = document;

		// initialize AnswersAnnotation
		for (CoreLabel c : document) {
			c.set(AnswersAnnotation.class, new ArrayList<String>());
		}
		
		for (NERClassifierCombiner ncc : classifiers) {
			ncc.classify(document);			
			for (CoreLabel c : document) {
				List<String> a = c.get(AnswersAnnotation.class);
				a.add(c.get(AnswerAnnotation.class));
				c.set(AnswerAnnotation.class, null);
				c.set(AnswersAnnotation.class, a);
			}
		}
	}
	
	/**
	 * Provides all agregated stats about GOLD and GUESS entities (FP, TP, FP) and their categories
	 * @return
	 */
	public NamedEntities entity_stats() {
		NamedEntities res = new NamedEntities();
		CoreLabel c;
		for (int classifierId = 0; classifierId < classifiers.size(); classifierId++) {
			System.err.printf("-- Classifier-%d\n", classifierId);
			
			String lastGold = "O", lastGuess = "O";
			int goldIndex = 0, guessIndex = 0;
			String goldString = "", guessString = "";
			String goldLemmaString = "", guessLemmaString = "";
			for (int i = 0; i < document.size(); i++) {
				c = document.get(i);
				String gold = c.getString(NamedEntityTagGoldAnnotation.class);
				String guess = c.get(AnswersAnnotation.class).get(classifierId);
				
				// continue current gold string
				if (lastGold.equals(gold) && !lastGold.equals("O")) {
					goldString += " " + c.word();
					goldLemmaString += " " + c.lemma();
				}
				
				// continue current guess string
				if (lastGuess.equals(guess) && !lastGuess.equals("O")) {
					guessString += " " + c.word();
					guessLemmaString += " " + c.lemma();
				}
				
				if (!lastGold.equals(gold) && !lastGold.equals("O")) {
					String t = "|" + lastGold + "|" + lastGuess;
				    if (lastGuess.equals(lastGold) && !lastGuess.equals(guess) &&
				    		goldIndex == guessIndex) {
				    	// TP 0/0 1/1 0/0
				    	//System.err.printf("TP %s\n", goldString);
				    	res.incTp(goldLemmaString + t, goldString + t);
				    } else {
				    	// FN 0/0 1/0 1/0 0/0; 0/0 1/1 1/0 0/0
						//System.err.printf("FN %s\n", goldString);
						res.incFn(goldLemmaString + t, goldString + t);
				    }
				}
				
				if (!lastGuess.equals(guess) && !lastGuess.equals("O")) {
					if (lastGuess.equals(lastGold) && !lastGuess.equals(guess) &&
							goldIndex == guessIndex && !lastGold.equals(gold)) {
						// already TP
					} else {
						// FP 
						String t = "|" + lastGold + "|" + lastGuess;
						res.incFp(guessLemmaString + t, guessString + t);
						System.err.printf("FP %s\n", guessString);
					}
				}
				
				if (!lastGold.equals(gold)) {
					lastGold = gold;
					goldIndex = i;
					goldString = c.word();
					goldLemmaString = c.lemma();
				}

				if (!lastGuess.equals(guess)) {
					lastGuess = guess;
					guessIndex = i;
					guessString = c.word();
					guessLemmaString = c.lemma();
				}
			}
				
			if (!lastGold.equals("O")) {
				String t = "|" + lastGold + "|" + lastGuess;
				if (lastGold.equals(lastGuess) && goldIndex == guessIndex) {
					// System.err.printf("TP %s\n", goldString);
					res.incTp(goldLemmaString + t, goldString + t);
				} else {
					// System.err.printf("FN %s\n", goldString);
					res.incFn(goldLemmaString + t, goldString + t);
				}
			}
			
			if (!lastGuess.equals("O")) {
				if (lastGold.equals(lastGuess) && goldIndex == guessIndex) {
					// OK
				} else {
					String t = "|" + lastGold + "|" + lastGuess;
					res.incFn(guessLemmaString + t, guessString + t);
				}
			}
		}
		return res;
	}
	
	/**
	 * Compare two classifier output
	 * @param doc1
	 * @param doc2
	 * @return
	 */
	public boolean compare(List<CoreLabel> doc1, List<CoreLabel> doc2) {
		int i1 = 0, i2 = 0; //
		String last1 = "O", last2 = "O";

		boolean cm_set;
	
		for (int i = 0; i < doc1.size(); i++) {
			CoreLabel c1 = doc1.get(i);
			CoreLabel c2 = doc2.get(i);
			
			String a1 = c1.getString(AnswerAnnotation.class);
			String a2 = c2.getString(AnswerAnnotation.class);
			
			if (a1 == null || a2 == null) return false;
		}
		// TODO
			
		return true;
	}
	
	public static String inputFile;
	
	public static void main(String[] args) throws ClassCastException, ClassNotFoundException, IOException {
		Properties props = new Properties();
		props = StringUtils.argsToProperties(args);
		inputFile = props.getProperty("inputFile", null);
		String propString = ""
				+ "-loadClassifier lv-ner-model.ser.gz"
				//+ " -whiteList Gazetteer/DB_persons.txt,Gazetteer/DB_locations.txt,Gazetteer/DB_professions.txt,Gazetteer/Laura_partijas_lem.txt,Gazetteer/AZ_valsts_parvaldes_struktura_lem.txt,D:/LUMII/ner/improve_9.1.2014/leta-2014-jan/export/stats/DB_organizations2.txt"
				//+ "-loadClassifier D:/LUMII/workspace/LVTagger/ner_models/26-11-2013_011138_cross/lv-ner-model-1.ser.gz;D:/LUMII/workspace/LVTagger/ner_models/26-11-2013_011138_cross/lv-ner-model-2.ser.gz;D:/LUMII/workspace/LVTagger/ner_models/26-11-2013_011138_cross/lv-ner-model-3.ser.gz"
				//";D:/LUMII/workspace/LVTagger/ner_models/26-11-2013_011138_cross/lv-ner-model-4.ser.gz;D:/LUMII/workspace/LVTagger/ner_models/26-11-2013_011138_cross/lv-ner-model-5.ser.gz"
				+ "";
		//props = StringUtils.argsToProperties(propString.split(" "));
		System.err.println(props);
		ClassifierComparator cc = new ClassifierComparator(props);
		
		for (Integer i = 0; i < 10; i++) {
			if (props.containsKey("classifier" + i.toString())) {
				System.err.println("classifier" + i.toString());
				String[] parts = props.getProperty("classifier" + i.toString()).split("\\s*\\|\\s*");				
				List<AbstractSequenceClassifier<CoreLabel>> nccl = new ArrayList<>();				
				for (String part : parts) {
					System.err.println("\t"+ part);
					String[] items = part.split("\\s*=\\s*");
					System.err.println("\t"+ part);
					if (items[0].equalsIgnoreCase("whiteList")) {
						System.err.println("\twhiteList" + items[1]);
						nccl.add(new ListNERSequenceClassifier(items[1], true, true));
					}
					if (items[0].equalsIgnoreCase("loadClassifier")) {
						System.err.println("\tloadClassifier" + items[1]);
						nccl.add(CRFClassifier.getClassifier(items[1], props));
					}
					if (items[0].equalsIgnoreCase("regexList")) {
						System.err.println("\tregexListt" + items[1]);
						nccl.add(new RegexNERSequenceClassifier(items[1], true, true));
					}
				}
				cc.addClassifier(nccl);
			}
		}
		
		if (props.containsKey("whiteList")) {
			String whiteListString = props.getProperty("whiteList");
			String whiteLists[] = whiteListString.split(";");
			for (String whiteList : whiteLists) {
				whiteList = whiteList.trim();
				cc.addClassifier(new ListNERSequenceClassifier(whiteList, true, true));
			}
		}
		
		if (props.containsKey("loadClassifier")) {
			String loadClassifierString = props.getProperty("loadClassifier");
			String loadClassifiers[] = loadClassifierString.split(";");
			for (String loadClassifier : loadClassifiers) {
				loadClassifier = loadClassifier.trim();
				cc.addClassifier(CRFClassifier.getClassifier(loadClassifier, props));				
			}
		}
		
		if (props.containsKey("regexList")) {
			String regexListString = props.getProperty("regexList");
			String regexLists[] = regexListString.split(";");
			for (String regexList : regexLists) {
				regexList = regexList.trim();
				cc.addClassifier(new RegexNERSequenceClassifier(regexList, true, true));
			}
		}
		
		LVCoNLLDocumentReaderAndWriter reader = new LVCoNLLDocumentReaderAndWriter();
		//List<CoreLabel> doc = reader.readCONLL("D:/LUMII/ner/improve_9.1.2014/leta-2014-jan/export/FB1A480C-5109-4D34-AFDF-FD4B9CC6E790.conll");
		//inputFile = "D:/LUMII/ner/improve_9.1.2014/leta-2014-jan/export/leta-2014-jan-test.tab";
		
		List<CoreLabel> doc = reader.readCONLL(inputFile);
		
		cc.classify(doc);
		
		reader.outputType = LVCoNLLDocumentReaderAndWriter.outputTypes.COMPARE;
		//reader.printAnswers(cc.document, new PrintWriter(System.out));
		
		cc.entity_stats().preview();
	}

}
