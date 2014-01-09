package lv.lumii.expressions;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Map;

import org.json.simple.JSONObject;

/**
 * Izlokam tab-delimited failu ar semantikas DB entītijām, un noformējam rezultātu kā DB update steitmentus 
 * @author pet
 *
 */
public class EntityInflection {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		String input_filename = "entities_locīšanai.txt"; // Ieejā jābūt 1 ieraksts uz rindiņu, ar tab-delimited kolonnām id-name-category 
		String output_filename = "entities_izlocītas.txt";
		BufferedReader ieeja = new BufferedReader(new InputStreamReader(new FileInputStream(input_filename), "UTF-8"));
		PrintWriter izeja = new PrintWriter(new OutputStreamWriter(new FileOutputStream(output_filename), "UTF-8"));
		
		String entity_row;
		int count = 0;
		while ((entity_row = ieeja.readLine()) != null) {
			count++;
			if (count % 1000 == 0) System.out.print('.');
			if (count % 10000 == 0) System.out.println();
			String[] fields = entity_row.split("\t");
			if (fields.length < 3) {
				//throw new Exception(String.format("Rindā '%s' nav 3 tab-delimited lauki, kā būtu jābūt",entity_row));
				System.err.println(String.format("Rindā '%s' nav 3 tab-delimited lauki, kā būtu jābūt",entity_row));
				continue;
			}
			String entity_id = fields[0];
			String entity_name = fields[1];
			if (entity_name.trim().isEmpty()) continue; // TODO - šādi ir jāmet ārā no DB...
			String category = null;
			if (fields.length > 2)
				category = fields[2];
			
			JSONObject oInflections = new JSONObject();
        	Expression e = new Expression(entity_name, category, true); // Pieņemam ka te jau visi ir normalizēti
        	Map<String,String> inflections= e.getInflections();
        	for (String i_case : inflections.keySet()) {
        		oInflections.put(i_case, inflections.get(i_case).replaceAll("'", "''"));
        	}
        	            
			izeja.printf("update entities set nameinflections='%s' where entityid = %s;\n",oInflections.toJSONString(), entity_id);
		}
		
		ieeja.close();
		izeja.flush();
		izeja.close();
		System.out.println("Done!");
	}

}
