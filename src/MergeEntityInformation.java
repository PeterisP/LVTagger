import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import lv.semti.morphology.analyzer.Analyzer;
import lv.semti.morphology.analyzer.Splitting;
import lv.semti.morphology.analyzer.Word;
import lv.semti.morphology.analyzer.Wordform;
import lv.semti.morphology.attributes.AttributeNames;
import lv.semti.morphology.corpus.Statistics;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.IntCounter;

public class MergeEntityInformation {
	
	static int filter_floor = 10; // vismaz cik pieminējumiem jābūt, lai iekļautu entīti sarakstā
	
	static Counter<String> counter = new IntCounter<String>();
	static Counter<String> counterbydoc = new IntCounter<String>();
	static HashMap<String, Counter<String>> popular_forms = new HashMap<String, Counter<String>>();
	static HashMap<String, String> best_forms = new HashMap<String, String>();
	static HashMap<String, String> blacklist = new HashMap<String, String>();
	static HashMap<String, String> doc_ids = new HashMap<String, String>();
	static HashMap<String, String> name_ids = new HashMap<String, String>();
	
    public static void main(String[] args) throws Exception {
    	String all_entity_file = "/Users/pet/Documents/LNB_converted/all_entities.txt";
    	String doc_id_file = "/Users/pet/Dropbox/LNB/data/data_LNB_documents.txt";
    	String name_id_file = "/Users/pet/Dropbox/LNB/data/data_sep_LNB_names2.txt";
    	
    	String entity_doc_file = "/Users/pet/Documents/LNB_converted/entities_documents.sql";
    	String new_name_file = "/Users/pet/Documents/LNB_converted/entities.sql";
    	
    	String src = "PP120924";
    	    	
		String line;
		BufferedReader liste = new BufferedReader(	new InputStreamReader(new FileInputStream("NERdicts/Forenames.txt"), "UTF-8"));
		while ((line = liste.readLine()) != null) blacklist.put(line, line);					
		liste.close();
		liste = new BufferedReader(	new InputStreamReader(new FileInputStream("NERdicts/blacklist.txt"), "UTF-8"));
		while ((line = liste.readLine()) != null) blacklist.put(line, line);					
		liste.close();

		liste = new BufferedReader(	new InputStreamReader(new FileInputStream(doc_id_file), "UTF-8"));
		while ((line = liste.readLine()) != null) {
			if (line.startsWith("id")) continue;
			String[] info = line.split("\t");
			if (info.length<2) {
				System.err.printf("Can't split doc id fields: %s", line);
			} else {
				String id = info[0];
				String foldername = info[1];
				doc_ids.put(foldername, id);
			}
		}
		liste.close();	
		
		liste = new BufferedReader(	new InputStreamReader(new FileInputStream(name_id_file), "UTF-8"));
		while ((line = liste.readLine()) != null) {
			if (line.startsWith("ID")) continue;
			String[] info = line.split("\t");
			if (info.length<2) {
				System.err.printf("Can't split doc id fields: %s", line);
			} else {
				String id = info[0];
				String name = info[1].replace("\"","");
				name_ids.put(name, id);
			}
		}
		liste.close();	
		
		BufferedReader ieeja = new BufferedReader(	new InputStreamReader(new FileInputStream(all_entity_file), "UTF-8"));
		int i=0;
		
		while ((line = ieeja.readLine()) != null) {
			if (line.startsWith("--") ) continue;			
			//if (i>=100000) break;
			i++;
			
			String[] info = line.split("\t");
			if (info.length<5) {
				System.err.printf("%d @ %s", i, line);
			} else {
				String word = info[0];
				String normalform = info[1];
				String category = info[2];
				String doc = info[3];
				int mention_count = Integer.parseInt(info[4]);
				
				add_word(word.trim(), normalform.trim(), category, doc, mention_count);
				//System.out.printf("%s:%d\n", word, mention_count);
			}
		}
		ieeja.close();
		
		System.out.printf("Apskatījām %d ierakstus, %d dažādi.\n", i, counter.size());
		
		Counters.retainAbove(counter, filter_floor);
		System.out.printf("\t%d no tiem vismaz %d reizes.\n\n", counter.size(), filter_floor);
		
		Analyzer analyzer = new Analyzer("dist/Lexicon.xml");
		
		for (Entry<String, Double> entry: counter.entrySet()) {
			String key = entry.getKey();
			Counter<String> forms = popular_forms.get(key);
			String best_form = Counters.argmax(forms);
			String[] info = key.split(Pattern.quote("|"));
			String category = info[0];
			
			best_form = normalizeForm(best_form, category, analyzer);			
			best_forms.put(key, best_form);
			//System.out.printf("%s sastopams %d reizes.\n", best_form, entry.getValue().intValue());
		}
		
		BufferedWriter izeja = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(entity_doc_file), "UTF-8"));
		BufferedWriter jaunie_vārdi = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new_name_file), "UTF-8"));
		for (Entry<String, Double> entry: counterbydoc.entrySet()) {
			String[] info = entry.getKey().split(Pattern.quote("|"));
			if (info.length<3) {
				System.err.printf("%d @ %s", i, line);
			} else {
				String normalform = info[1];
				String category = info[0];
				String doc = doc_ids.get(info[2]);
				if (doc == null) System.err.println("bad docid: " + info[2]);
				
				String key = category+"|"+normalform;
				String best_form = best_forms.get(key);								
				if (best_form != null) {
					String name_id = name_ids.get(best_form);
					if (name_id == null) {
						String insert = String.format("insert into entity (categoryid, definition, infoSource) values (%s, \"%s\", \"%s\");\n", category, best_form, src);
						insert += String.format("insert into name (name, infoSource) values (\"%s\", \"%s\");\n", best_form, src);
						insert += String.format("insert into entityName(nameID, entityID, infoSource) select LAST_INSERT_ID() as nameID, ID as entityID, \"%s\" from entity where definition=\"%s\" and infoSource=\"%s\";\n",src,best_form,src);
						
						jaunie_vārdi.append(String.format("%s\n", insert));
						name_id = "1234567890";
						name_ids.put(best_form, name_id);
					}
					izeja.append(String.format("insert into nameDocument (nameID, documentID, occurrences, infoSource) values (%s, %s, %d, \"%s\");\n", name_id, doc, entry.getValue().intValue(), src));
					//izeja.append(String.format("%s\t%s\t%s\t%s\t%d\t%s\n", best_form, normalform, category, doc, entry.getValue().intValue(),name_id));
				}
			}
		}
		jaunie_vārdi.flush();
		jaunie_vārdi.close();
		izeja.flush();
		izeja.close();
    }

	private static void add_word(String word, String normalform,
			String category, String doc, int mention_count) {
		if (word.trim().length()<3) return;
		if (word.contains(",")) {
			String[] wordparts = word.split(",");
			String[] formparts = normalform.split(",");
			if (wordparts.length != formparts.length) {
				System.err.println(String.format("Nesakrīt komatu skaits '%s' un '%s'.", word, normalform));
				return;
			}
			for (int i=0; i<wordparts.length; i++) {
				add_word(wordparts[i].trim(), formparts[i].trim(), category, doc, mention_count);
			}
			return;
		} 
		if (word.contains(" .")) {
			String[] wordparts = word.split(Pattern.quote(" ."));
			String[] formparts = normalform.split(Pattern.quote(" ."));
			if (wordparts.length != formparts.length) {
				System.err.println(String.format("Nesakrīt punktu skaits '%s' un '%s'.", word, normalform));
				return;
			}
			for (int i=0; i<wordparts.length; i++) {
				add_word(wordparts[i].trim(), formparts[i].trim(), category, doc, mention_count);
			}
			return;
		} 
		
    	word = word.replace("CX", "CK");
    	word = word.replace(" eela", " iela");
    	word = word.replace("Kr .", "Kr.");
    	word = word.replace("Riga", "Rīga");
    	word = word.replace("Kreemija", "Krievija");
    	word = word.replace("Kreewija", "Krievija");
    	word = word.replace("Wahzija", "Vācija");
    	word = word.replace("Cehoslovakija", "Čehoslovakija");    	
    	word = word.replaceAll("[aA]ugstākās [pP]adomes$", "Augstākā Padome");
    	word = word.replaceAll("[fF]ederatīvajā [rR]epublikā$", "Federatīvā Republika");
    	word = word.replaceAll("[dD]emokrātiskajā [rR]epublikā$", "Demokrātiskā Republika");
    	word = word.replaceAll("[kK]omunistiskās [pP]artijas$", "Komunistiskā Partija");
    	word = word.replace(" lela", " iela");
    	word = word.replace(" lels", " iela");
    	word = word.replace("dzīv. ", "");
    	if (word.equalsIgnoreCase("Hitlera")) word = "Hitlers";
    	if (word.equalsIgnoreCase("Raiņa")) word = "Rainis";
    	if (word.equalsIgnoreCase("Vītola")) word = "Vītols";
    	if (word.equalsIgnoreCase("Mocarta")) word = "Mocarts";
    	if (word.equalsIgnoreCase("Čaikovska")) word = "Čaikovskis";
    	if (word.equalsIgnoreCase("Daugavpilī")) word = "Daugavpils";
    	if (word.equalsIgnoreCase("Liepājas")) word = "Liepāja";
    	if (word.equalsIgnoreCase("Liepājā")) word = "Liepāja";
    	if (word.equalsIgnoreCase("Cēsu")) word = "Cēsis";
    	if (word.equalsIgnoreCase("Blaumaņa")) word = "Blaumanis";
    	if (word.equalsIgnoreCase("Lietavas")) word = "Lietava";
    	if (word.equalsIgnoreCase("Ķīns")) word = "Ķīna";
    	if (word.equalsIgnoreCase("Strencis")) word = "Strenči";
    	if (word.equalsIgnoreCase("Rigā")) word = "Rīga";
    	if (word.equalsIgnoreCase("Kubs")) word = "Kuba";
    	if (word.equalsIgnoreCase("Rigā")) word = "Rīga";
    	if (word.equalsIgnoreCase("Kijevs")) word = "Kijeva";
    	if (word.equalsIgnoreCase("Latmija")) word = "Latvija";
    	if (word.equalsIgnoreCase("Tērbatā")) word = "Tērbata";
    	if (word.equalsIgnoreCase("Sabilē")) word = "Sabile";
    	if (word.equalsIgnoreCase("Melluzis")) word = "Melluži";
    	if (word.equalsIgnoreCase("Polijas") || word.equalsIgnoreCase("Polijā")) word = "Polija";
    	//hack nesaprastiem locījumiem
		if (word.endsWith("ijas")) word = word.substring(0, word.length()-1);
		if (word.endsWith("ijā")) word = word.substring(0, word.length()-1) + "a";

    	normalform = normalform.replace("CX", "CK");
    	normalform = normalform.replace("|", "");
    	normalform = normalform.replace(" eela", " iela");
    	normalform = normalform.replace("Kr .", "Kr.");
    	normalform = normalform.replace("riga", "rīga");
    	normalform = normalform.replace("kreewija", "krievija");
    	normalform = normalform.replace("kreemija", "krievija");
    	normalform = normalform.replace("wahzija", "vācija");
    	normalform = normalform.replace(" lels", " iela");
    	normalform = normalform.replace("dzīv. ", "");
    	if (normalform.equalsIgnoreCase("Hitlera")) normalform = "hitlers";
    	if (normalform.equalsIgnoreCase("Raiņa")) normalform = "rainis";
    	if (normalform.equalsIgnoreCase("Vītola")) normalform = "vītols";
    	if (normalform.equalsIgnoreCase("Mocarta")) normalform = "mocarts";
    	if (normalform.equalsIgnoreCase("Čaikovska")) normalform = "čaikovskis";
    	if (normalform.equalsIgnoreCase("Daugavpilī")) normalform = "daugavpils";
    	if (normalform.equalsIgnoreCase("Liepājas")) normalform = "liepāja";
    	if (normalform.equalsIgnoreCase("Liepājā")) normalform = "liepāja";
    	if (normalform.equalsIgnoreCase("blaumaņa")) normalform = "blaumanis";
    	if (normalform.equalsIgnoreCase("ķīns")) normalform = "ķīna";
    	if (normalform.equalsIgnoreCase("Polijas") || normalform.equalsIgnoreCase("Polijs")) normalform = "polija";
		if (normalform.endsWith("ijas")) normalform = normalform.substring(0, normalform.length()-1);
		if (normalform.endsWith("ijā")) normalform = normalform.substring(0, normalform.length()-1) + "a";

		if (blacklist.containsKey(word)) return;
		if (blacklist.containsKey(normalform)) return;
		if (normalform.contains(" un ") && !normalform.contains("opera")) return;
		if (normalform.length() > 50) return;  
    	
		String key = category+"|"+normalform;				
		counter.incrementCount(key, mention_count);
		
		Counter<String> forms = popular_forms.get(key);
		if (forms == null) {
			forms = new IntCounter<String>();
			popular_forms.put(key, forms);
		}
		forms.incrementCount(word, mention_count);
		
		counterbydoc.incrementCount(key+"|"+doc, mention_count);
	}

    static String normalizeForm(String form, String category, Analyzer analyzer) {
    	String result = form;
    	
    	List<Word> words = Splitting.tokenize(analyzer, result);
    	Word lastword = words.get(words.size()-1);
    	Wordform bestform = lastword.getBestWordform(); 
    	if (bestform != null && (bestform.isMatchingStrong(AttributeNames.i_Case, AttributeNames.v_Genitive) || bestform.isMatchingStrong(AttributeNames.i_Case, AttributeNames.v_Locative))) {
    		//System.out.printf("'%s' -> '%s'\t%s\n", lastword.getToken(), bestform.getValue(AttributeNames.i_Lemma), result);
    		boolean propername = Character.isUpperCase(lastword.getToken().charAt(0));
    		String replacement = bestform.getValue(AttributeNames.i_Lemma);
    		if (propername) replacement = NE.capitalizeString(replacement);
    		result = result.replace(lastword.getToken(), replacement);
    	}
    	
    	if (result.endsWith(" kaps")) result = result.replace(" kaps", " kapi");
    	
    	if (!result.equalsIgnoreCase(form)) {
    		//System.out.printf("'%s' -> '%s'\t%s\n", form, result, category);
    	}
    	
    	//if (!result.contains(" ") && !category.equalsIgnoreCase("4"))
    	if (result.contains("\\") || result.contains("\""))	
    		//System.out.printf("'%s'\t%s\n", result, category);
    		System.err.printf("%s\n", result, category);
    	return result;
    }
}
