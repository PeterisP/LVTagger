package lv.lumii.ner.analysis;

public class Markable {
	Document doc;
	String m;
	String full_m; // with categories
	String cat;
	int index;	
	
	Markable(String _m, String _cat, int _index) {
		m = _m;
		cat =_cat;
		index = _index;
	}
	
	public String toString(){
		return m;
	}
	
	public String full() {
		return full_m;
	}
}
