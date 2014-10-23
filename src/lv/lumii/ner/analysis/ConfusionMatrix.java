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
import edu.stanford.nlp.util.StringUtils;

public class ConfusionMatrix {
	
	  private Collection<String> labels;
	  
	  private Map<String,Integer> labelMap = new HashMap<String,Integer>();
	  
	  private int[][] confusionMatrix;
	  
	  private String defaultLabel = "O";
	  
	  // correctLabel => classifiedLabel => MarkablePair(correct, classified)
	  private Map<String, Map<String, List<Pair<Markable, Markable>>>> errors = new HashMap<String, Map<String, List<Pair<Markable, Markable>>>>();
	  
	  public ConfusionMatrix(Collection<String> labels, String defaultLabel) {
	    this.labels = labels;
	    confusionMatrix = new int[labels.size() + 1][labels.size() + 1];
	    this.defaultLabel = defaultLabel;
	    for (String label : labels) {
	      labelMap.put(label, labelMap.size());
	    }
	    labelMap.put(defaultLabel, labelMap.size());
	  }
	  
	  public int[][] getConfusionMatrix() {
	    return confusionMatrix;
	  }
	  
	  public Collection<String> getLabels() {
	    return labels;
	  }
	  
	  public double getAccuracy(String label) {
	    int labelId = labelMap.get(label);
	    int labelTotal = 0;
	    int correct = 0;
	    for (int i = 0; i < labels.size(); i++) {
	      labelTotal += confusionMatrix[labelId][i];
	      if (i == labelId) {
	        correct = confusionMatrix[labelId][i];
	      }
	    }
	    return 100.0 * correct / labelTotal;
	  }
	  
	  public int getCorrect(String label) {
	    int labelId = labelMap.get(label);
	    return confusionMatrix[labelId][labelId];
	  }
	  
	  public double getTotal(String label) {
	    int labelId = labelMap.get(label);
	    int labelTotal = 0;
	    for (int i = 0; i < labels.size(); i++) {
	      labelTotal += confusionMatrix[labelId][i];
	    }
	    return labelTotal;
	  }
	  
	  public void addInstance(String correctLabel, String guessLabel) {
	    incrementCount(correctLabel, guessLabel);
	  }
	  
	  public int getCount(String correctLabel, String classifiedLabel) {
	    if (labels.contains(correctLabel) && (labels.contains(classifiedLabel) == false)
	        && (defaultLabel.equals(classifiedLabel) == false)) {
	      throw new IllegalArgumentException("Label not found " + correctLabel + ' ' + classifiedLabel);
	    }
	    int correctId = labelMap.get(correctLabel);
	    int classifiedId = labelMap.get(classifiedLabel);
	    return confusionMatrix[correctId][classifiedId];
	  }
	  
	  public void putCount(String correctLabel, String classifiedLabel, int count) {
	    if (labels.contains(correctLabel) && (labels.contains(classifiedLabel) == false)
	        && (defaultLabel.equals(classifiedLabel) == false)) {
	      throw new IllegalArgumentException("Label not found");
	    }
	    int correctId = labelMap.get(correctLabel);
	    int classifiedId = labelMap.get(classifiedLabel);
	    confusionMatrix[correctId][classifiedId] = count;
	  }
	  
	  public void incrementCount(String correctLabel, String classifiedLabel, int count) {
	    putCount(correctLabel, classifiedLabel, count + getCount(correctLabel, classifiedLabel));
	  }
	  
	  public void incrementCount(String correctLabel, String classifiedLabel) {
	    incrementCount(correctLabel, classifiedLabel, 1);
	  }
	  
	  public ConfusionMatrix merge(ConfusionMatrix b) {
	    if (labels.size() != b.getLabels().size()) {
	      throw new IllegalArgumentException("The Labels do not Match");
	    }
	    
	    for (String correctLabel : this.labels) {
	      for (String classifiedLabel : this.labels) {
	        incrementCount(correctLabel, classifiedLabel, b.getCount(correctLabel, classifiedLabel));
	      }
	    }
	    return this;
	  }

	  
	  public String summarize() {
	    StringBuilder returnString = new StringBuilder();
	    returnString.append("Confusion Matrix\n");
	    returnString.append("----------------").append('\n');
	    
	    for (String correctLabel : this.labels) {
	      returnString.append(StringUtils.padLeft((correctLabel.length() > 3)?correctLabel.substring(0,4):correctLabel, 5))
	          .append(' ');
	    }
	    
	    returnString.append("<--Classified as").append('\n');
	    
	    for (String correctLabel : this.labels) {
	      int labelTotal = 0;
	      for (String classifiedLabel : this.labels) {
	        returnString.append(
	          StringUtils.padLeft(Integer.toString(getCount(correctLabel, classifiedLabel)), 5)).append(' ');
	        labelTotal += getCount(correctLabel, classifiedLabel);
	      }
	      returnString.append(" |  ").append(String.valueOf(labelTotal)).append('\t').append(correctLabel).append('\n');
	    }
	    returnString.append('\n');
	    return returnString.toString();
	  }
	  
	  static String getSmallLabel(int i) {
	    int val = i;
	    StringBuilder returnString = new StringBuilder();
	    do {
	      int n = val % 26;
	      int c = 'a';
	      returnString.insert(0, (char) (c + n));
	      val /= 26;
	    } while (val > 0);
	    return returnString.toString();
	  }
	  
	  
	  void addError(String classifiedLabel, String correctLabel, Markable guess, Markable gold) {
		if (!errors.keySet().contains(correctLabel)) errors.put(correctLabel, new HashMap<String, List<Pair<Markable,Markable>>>());
		Map<String, List<Pair<Markable, Markable>>> q = errors.get(correctLabel);
		if (!q.keySet().contains(classifiedLabel)) q.put(classifiedLabel, new ArrayList<Pair<Markable,Markable>>());
		List<Pair<Markable, Markable>> errs = q.get(classifiedLabel);
		errs.add(new Pair<Markable,Markable>(gold, guess));
	}
	  

	String errors() {
		StringBuilder s = new StringBuilder();
		s.append("Detalized errors (gold - guess)\n-----------------\n");
		for (String correctLabel : errors.keySet()) {
			s.append('\n');
			for (String classifiedLabel : errors.get(correctLabel).keySet()) {
				s.append("==== ").append(correctLabel).append(" - ").append(classifiedLabel).append(" ====\n");
				for (Pair<Markable,Markable> p : errors.get(correctLabel).get(classifiedLabel)) {
					s.append('\t').append(StringUtils.pad(p.first.full(),30)).append("\t").append(p.second.full()).append('\n');
				}
			}
		}
		s.append('\n');
		return s.toString();
	}
}
