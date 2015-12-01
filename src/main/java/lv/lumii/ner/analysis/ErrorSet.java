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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

public class ErrorSet {

	private Map<String,List<Pair<Markable,Markable>>> errors= new HashMap<String,List<Pair<Markable,Markable>>>();
	
	public void addError(String comment, Markable gold, Markable guess) {
		if (!errors.containsKey(comment)) errors.put(comment, new LinkedList<Pair<Markable,Markable>>());
		errors.get(comment).add(new Pair<Markable,Markable>(gold,guess));
	}
	
	
	public String toString() {
		StringBuilder s = new StringBuilder();
		for (String comment : errors.keySet()) {
			s.append("==== ").append(comment).append("=====\n");
			for (Pair<Markable,Markable> p : errors.get(comment)) {
				s.append('\t').append(StringUtils.pad(p.first.full(),30)).append("\t").append(p.second.full()).append('\n');
			}
		}
		return s.toString();
	}
	
	public String summarize() {
		StringBuilder s = new StringBuilder();
		s.append("----------------").append('\n');
		s.append("          Class\tCount\n");
		for (String comment : errors.keySet()) {
			s.append(StringUtils.pad(comment,15)).append('\t').append(String.valueOf(errors.get(comment).size())).append('\n');
		}
		return s.toString();
	}
}
