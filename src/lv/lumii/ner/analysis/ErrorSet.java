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
