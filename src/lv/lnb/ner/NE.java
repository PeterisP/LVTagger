package lv.lnb.ner;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


public class NE implements Comparable<NE> {
	String normalform;
	String tag;
	HashMap<String,Integer> name_count = new HashMap<String, Integer>();
	HashMap<String,Integer> doc_count = new HashMap<String, Integer>();
	
	NE (String _name, String _normalform, String _tag) {
		normalform = _normalform;
		tag = _tag;
	}

	@Override
	public int compareTo(NE that) {
		int result = tag.compareTo(that.tag);
		//if (result == 0) result = name.compareTo(that.name);
		if (result == 0) result = normalform.compareTo(that.normalform);
		return result;
	}
	
	void add(String name, String doc) {
		Integer prevcount = name_count.get(name);
		if (prevcount == null) prevcount = 0;
		name_count.put(name,prevcount+1);
		
		if (doc != null) {
			prevcount = doc_count.get(doc);
			if (prevcount == null) prevcount = 0;
			doc_count.put(doc,prevcount+1);
		}
	}
	
	int category() {
		int result = 23; //other
		if (tag.equalsIgnoreCase("PERSONA")) result = 1; //pers.hum
		if (tag.equalsIgnoreCase("LOKACIJA")) result = 4; //loc.geo
		if (tag.equalsIgnoreCase("ORGANIZACIJA")) result = 13; //org.other
				
		return result;
	}
	
	String alternatives() {
		String result = "";
		if (name_count.size()>1) {
			Map<String,Integer> sorted = sortByValueDesc(name_count);
			for (Entry<String, Integer> entry : sorted.entrySet()) {
				result += String.format("\t%s\t%s\n", entry.getKey(), entry.getValue());
			}
		}

		if (doc_count.size()>1) {
			Map<String,Integer> sorted = sortByValueDesc(doc_count);
			for (Entry<String, Integer> entry : sorted.entrySet()) {
				result += String.format("\t%s\t%s\n", entry.getKey(), entry.getValue());
			}
		}
		
		return result;
	}
	
	String getName() {
		if (tag.equalsIgnoreCase("PERSONA")) return capitalizeString(normalform);
		
		Map.Entry <String,Integer> popular = maxEntry(name_count);		
		return popular.getKey();		
	}

	
	private static <K, V extends Comparable<? super V>> Map<K, V> sortByValueDesc( Map<K, V> map )
	{
	    List<Map.Entry<K, V>> list =
	        new LinkedList<Map.Entry<K, V>>( map.entrySet() );
	    Collections.sort( list, new Comparator<Map.Entry<K, V>>()
	    {
	        public int compare( Map.Entry<K, V> o1, Map.Entry<K, V> o2 )
	        {
	            return -(o1.getValue()).compareTo( o2.getValue() );
	        }
	    } );
	
	    Map<K, V> result = new LinkedHashMap<K, V>();
	    for (Map.Entry<K, V> entry : list)
	    {
	        result.put( entry.getKey(), entry.getValue() );
	    }
	    return result;
	}
	
	private static <K, V extends Comparable<? super V>> Map.Entry<K, V> maxEntry( Map<K, V> map )
	{
	    LinkedList<Map.Entry<K, V>> list =
	        new LinkedList<Map.Entry<K, V>>( map.entrySet() );
	    Collections.sort( list, new Comparator<Map.Entry<K, V>>()
	    {
	        public int compare( Map.Entry<K, V> o1, Map.Entry<K, V> o2 )
	        {
	            return -(o1.getValue()).compareTo( o2.getValue() );
	        }
	    } );
	    return list.getFirst();
	}
	
	
	
	public static String capitalizeString(String string) {
		  char[] chars = string.toLowerCase().toCharArray();
		  boolean found = false;
		  for (int i = 0; i < chars.length; i++) {
		    if (!found && Character.isLetter(chars[i])) {
		      chars[i] = Character.toUpperCase(chars[i]);
		      found = true;
		    } else if (Character.isWhitespace(chars[i]) || chars[i]=='.' || chars[i]=='\'') { // You can add other chars here
		      found = false;
		    }
		  }
		  return String.valueOf(chars);
		}
}
