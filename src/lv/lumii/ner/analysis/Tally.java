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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.util.Pair;

public class Tally {
	double c;
	
	Map<String, List<Pair<Markable, Markable>>> errByCategory;
	Map<String, Double> cByCategory;
	List<String> allErrors;
	
	Tally() {
		errByCategory = new HashMap<String, List<Pair<Markable,Markable>>>();
		allErrors = new ArrayList<String>();
		c = 0;
		cByCategory = new HashMap<String, Double>();
	}
	
	void incrementCount(String category, double d) {
		c += d;
		Double q = cByCategory.get(category);
		if (q == null) {
			cByCategory.put(category, d);
		} else {
			cByCategory.put(category, q + d);
		}
	}
	
	void addError(String cat, Markable guess, Markable gold) {
		//System.out.println("G:" + guess + " \t "  + gold);
		List<Pair<Markable, Markable>> q = errByCategory.get(cat);
		if (q == null) {
			q = new ArrayList<Pair<Markable, Markable>>();
			q.add(new Pair<Markable,Markable>(guess, gold));
			errByCategory.put(cat, q);
		} else {
			q.add(new Pair<Markable,Markable>(guess, gold));
		}
	}
	void addError(String cat, Markable guess, Markable gold, boolean first) {
		//System.out.println("G:" + guess + " \t "  + gold);
		List<Pair<Markable, Markable>> q = errByCategory.get(cat);
		if (q == null) {
			q = new ArrayList<Pair<Markable, Markable>>();
			q.add(new Pair<Markable,Markable>(guess, gold));
			errByCategory.put(cat, q);
		} else {
			q.add(new Pair<Markable,Markable>(guess, gold));
		}
		if (first) allErrors.add(guess.toString());
		else allErrors.add(gold.toString());
	}
	
	public Collection<? extends String> keySet() {
		return cByCategory.keySet();
	}
	public double getCount(String category) {
		Double d = cByCategory.get(category);
		if (d != null) return d;
		else return 0.0;
	}
	public double totalCount() {
		return c;
	}
	
	public double getImpact(String category) {
		Double d = cByCategory.get(category);
		if (d == null || c == 0.0) return 0.0;
		return d/totalCount();
	}
}
