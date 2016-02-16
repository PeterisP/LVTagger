/*******************************************************************************
 * Copyright 2013,2014 Institute of Mathematics and Computer Science, University of Latvia
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

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/* 
 * Failsistēmā balstīti saraksti ar vārdiem; read-only; ielasa kā singletonus - pēc pirmā pieprasījuma
 */
public class Dictionary {
	private static HashMap<String,Set<String>> dictionaries = new HashMap<String,Set<String>>();
	
	public static Set<String> dict(String dict_name) {
		Set<String> result = dictionaries.get(dict_name);
		if (result == null) {
			result = new HashSet<String>();
			try {
                String filename = "LETAdicts/" + dict_name + ".txt";
				InputStream stream = Dictionary.class.getClassLoader().getResourceAsStream(filename);
				if (stream == null) {
                    stream = new FileInputStream(filename);
				}
				BufferedReader in = new BufferedReader(new InputStreamReader(stream, "UTF8"));
				
				String line;
				while ((line = in.readLine()) != null) {
			        result.add(line);
			    }
				in.close();
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}
            dictionaries.put(dict_name, result);
		}
		return result;
	}
}
