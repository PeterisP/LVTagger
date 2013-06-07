package lv.lnb.ner;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class NECounterFull {
	private HashMap<String,NE> entities = new HashMap<String, NE>();
	private HashMap<NE,Integer> count = new HashMap<NE, Integer>();
	private HashMap<String, HashMap<NE,Integer>> documents = new HashMap<String, HashMap<NE,Integer>>();
	
	void add(String document, String name, String normalform, String tag) {
		NE entity = entities.get(tag+"|"+normalform);
		if (entity==null) {
			entity = new NE(name, normalform, tag);
			entities.put(tag+"|"+normalform, entity);
		}
		entity.add(name, document);
				
		HashMap<NE,Integer> doc = documents.get(document);
		if (doc == null) {
			doc = new HashMap<NE,Integer>();
			documents.put(document,doc);
		}
		
		Integer prevcount = count.get(entity);
		if (prevcount == null) prevcount = 0;
		count.put(entity,prevcount+1);
		
		prevcount = doc.get(entity);
		if (prevcount == null) prevcount = 0;
		doc.put(entity,prevcount+1);
	}
	
	void show() {
		List<NE> sorted = new LinkedList<NE>( new TreeSet<NE>(count.keySet())); 
		for (NE entity : sorted) {
			//System.out.printf("%s\t%s\t%d\n%s",entity.tag, entity.normalform, count.get(entity), entity.alternatives());
			System.out.printf("%s\t%s\t%d\n%s",entity.tag, entity.normalform, count.get(entity), "");
		}		
	}
	
	void db_insert() {
		String infoSource = "PP imports 27jun";
		for (NE entity : count.keySet()) {
			System.out.printf("insert into entity (categoryid, definition, infoSource) values (%d, \"%s\", \"%s\");\n", entity.category(), entity.getName(), infoSource);
			System.out.printf("insert into name (name, infoSource) values (\"%s\", \"%s\");\n", entity.getName(), infoSource);
			System.out.printf("insert into entityName(nameID, entityID, infoSource) select LAST_INSERT_ID() as nameID, ID as entityID, infoSource from entity where infoSource=\"%s\" and definition=\"%s\";\n", entity.getName(), infoSource);
		}
		
		for (Map.Entry<String, HashMap<NE,Integer>> doc : documents.entrySet()) {
			String url = "http://www.periodika.lv/periodika2-viewer/view/index-dev.html#panel:pp|issue:/" + doc.getKey();
			System.out.printf("insert into document (reference, infoSource) values (\"%s\",\"PP imports\");\n",url);
			for (Map.Entry<NE, Integer> entry : doc.getValue().entrySet()) {
				System.out.printf("insert into nameDocument (nameID, documentID, occurrences, infoSource) select min(name.ID) as nameID, min(document.ID) as documentID, %d as occurrences, \"PP imports\" from name, document where name = \"%s\" and reference = \"%s\";\n", 
						entry.getValue(), entry.getKey().getName(), url);
			}
		}
	}
	
	void filter(int limit) {
		HashMap<String,NE> newentities = new HashMap<String,NE>();
		for (Map.Entry<String,NE> entry : entities.entrySet()) {
			NE entity = entry.getValue();
			int num = count.get(entity);
			if (num<limit) {
				count.remove(entity);
				for (String document : entity.doc_count.keySet()) {
					HashMap<NE,Integer> doc = documents.get(document);
					if (doc != null) doc.remove(entity);
				}
			} else newentities.put(entry.getKey(), entry.getValue());				
		}
		entities = newentities;
	}
}
