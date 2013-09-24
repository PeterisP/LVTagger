package lv.lumii.morphotagger;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/* 
 * Failsistēmā balstīti saraksti ar vārdiem; read-only; ielasa kā singletonus - pēc pirmā pieprasījuma
 */
public class Dictionary {
	private static HashMap<String,Set<String>> dictionaries = new HashMap<String,Set<String>>();
	
	public static Set<String> dict(String filename) {
		Set<String> result = dictionaries.get(filename);
		if (result == null) {
			result = new HashSet<String>();
			try {
				BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream("LETAdicts/"+filename+".txt"), "UTF8"));
				
				String line;
				while ((line = in.readLine()) != null) {
			        result.add(line);
			    }
				in.close();
				dictionaries.put(filename, result);
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}
		}
		return result;
	}
}
