/*******************************************************************************
 * Copyright 2012 Institute of Mathematics and Computer Science, University of Latvia
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
package lv.lnb.ner;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;

public class NECounterSingleDoc {
	private HashMap<String,NE> entities = new HashMap<String, NE>();
	private HashMap<NE,Integer> count = new HashMap<NE, Integer>();
	private String docID;
	
	void add(String document, String name, String normalform, String tag) {
		NE entity = entities.get(tag+"|"+normalform);
		if (entity==null) {
			entity = new NE(name, normalform, tag);
			entities.put(tag+"|"+normalform, entity);
		}
		entity.add(name, null);
					
		Integer prevcount = count.get(entity);
		if (prevcount == null) prevcount = 0;
		count.put(entity,prevcount+1);		
	}
	
	void show() {
		List<NE> sorted = new LinkedList<NE>( new TreeSet<NE>(count.keySet())); 
		for (NE entity : sorted) {
			//System.out.printf("%s\t%s\t%d\n%s",entity.tag, entity.normalform, count.get(entity), entity.alternatives());
			System.out.printf("%s\t%s\t%d\n%s",entity.tag, entity.normalform, count.get(entity), "");
		}		
	}
	
	void db_insert(Writer writer) throws IOException {
		for (Entry<NE,Integer> entry : count.entrySet()) {
			//String entryItem = String.format("insert into NELinks (name, normalform, category, docID, frequency) values (\"%s\", \"%s\", %d, \"%s\", %d);\n", entry.getKey().getName().replace("\\", "\\\\").replace("\"", "\\\""), entry.getKey().normalform.replace("\\", "\\\\").replace("\"", "\\\""), entry.getKey().category(), docID, entry.getValue());
			String entryItem = String.format("%s\t%s\t%d\t%s\t%d\n", entry.getKey().getName(), entry.getKey().normalform, entry.getKey().category(), docID, entry.getValue());
			writer.write( entryItem );		
		}		
	}
	
	void filter(int limit) {
		HashMap<String,NE> newentities = new HashMap<String,NE>();
		for (Map.Entry<String,NE> entry : entities.entrySet()) {
			NE entity = entry.getValue();
			int num = count.get(entity);
			if (num<limit) {
				count.remove(entity);
			} else newentities.put(entry.getKey(), entry.getValue());				
		}
		entities = newentities;
	}
	
	public NECounterSingleDoc(String doc) {
		docID = doc;
	}
}
