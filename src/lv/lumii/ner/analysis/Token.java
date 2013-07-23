package lv.lumii.ner.analysis;


public class Token {
	String word;
	String guess;
	String gold;
	
	Document doc;

	int sent_id;
    int id;
    
    boolean sent_start;
    
	Token(Document _doc, int _id, String _word, String _guess, String _gold) {
		doc = _doc;
		id = _id;
		word = _word;
		guess = _guess;
		gold = _gold;
	}
	
	
    public void setSentStart() {
    	sent_start = true;
    }
    
    public int getId() {
    	return id;
    }
   
    public int getSentId() {
    	return sent_id;
    }
    
    public boolean isSentStart() {
    	if (sent_start) return true;
    	return false;
    }
    
    public boolean isSentEnd() {
    	if (id == doc.getSize()-1 || doc.getToken(id+1).sent_id != sent_id) return true;
    	return false;
    }
}
