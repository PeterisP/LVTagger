package lv.lumii.ner.analysis;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Document {
	List<Token> document;
	
	Document() {
		document = new ArrayList<Token>();
	}
	
	public int getSize() {
		return document.size();
	}
	
	public Token getToken(int index) {
		try {
			return document.get(index);
		} catch(IndexOutOfBoundsException e) {
			return null;
		}
	}
	
    public void readDocument(String fileName) throws NumberFormatException, IOException {
    	BufferedReader conll_in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName), "UTF8"));
        String s;    
        int sent_id = 0;
        boolean sent_start = true;
        int token_id = -1;
        
        while ((s = conll_in.readLine()) != null)  {
            if (s.trim().length() > 0) {
                String[] fields = s.split("\t");
                
                String word = fields[0];
                String guess = fields[2];
                String gold = fields[1];
                
                token_id++;
                Token token = new Token(this, token_id, word, guess, gold);                
                if (sent_start) {
                	token.setSentStart();
                	sent_start = false;
                }
                token.sent_id = sent_id;
                document.add(token);
            } else {
            	sent_id++;
            	sent_start = true;
            }
            
        }
        conll_in.close();
	}
    
    public void addToken(Token t) {
    	assert(t.id == getSize());
    	document.add(t);
    }
    
    public int getGoldMarkableStart(int pos) {
    	Token t = getToken(pos);
    	int i = pos;
    	if (t.gold.equals("O") || t.isSentStart()) return pos;
    	while (--i >= 0 && getToken(i).gold.equals(t.gold) && !getToken(i).isSentEnd()) {
    	}
    	return i+1;
    }
    public int getGoldMarkableEnd(int pos) {
    	Token t = getToken(pos);
    	int i = pos;
    	if (t.gold.equals("O") || t.isSentEnd()) return pos;
    	while (++i < getSize() && getToken(i).gold.equals(t.gold) && !getToken(i).isSentStart()) {
    	}
    	return i-1;
    }
    public int getGuessMarkableStart(int pos) {
    	Token t = getToken(pos);
    	int i = pos;
    	if (t.guess.equals("O") || t.isSentStart()) return pos;
    	while (--i >= 0 && getToken(i).guess.equals(t.guess) && !getToken(i).isSentEnd()) {
    	}
    	return i+1;
    }
    public int getGuessMarkableEnd(int pos) {
    	Token t = getToken(pos);
    	int i = pos;
    	if (t.guess.equals("O") || t.isSentEnd()) return pos;
    	while (++i < getSize() && getToken(i).guess.equals(t.guess) && !getToken(i).isSentStart()) {
    	}
    	return i-1;
    }
    
    Markable makeGoldMarkable(int from, int to) {
    	String line = "";
    	String cat = "";
       	String full_line = "";
    	for(int i = from; i <= to; i++) {
    		Token t = getToken(i);
    		if (t == null) continue;
    		String ccat = t.gold;
    		if (ccat.length() > 4) ccat = ccat.substring(0,4);
    		line += t.word + " ";
    		full_line += t.word + "\\" + ccat + " ";
    		if (cat.length()>0 && !cat.equals(t.gold)) cat = "MISC";
    		else cat = t.gold;
    	}
    	Markable res = new Markable(line, cat, from);
    	res.full_m = full_line;
    	return res;
    }
    Markable makeGuessMarkable(int from, int to) {
       	String line = "";
       	String full_line = "";
    	String cat = "";
    	for(int i = from; i <= to; i++) {
    		Token t = getToken(i);
    		if (t == null) continue;
    		String ccat = t.guess;
    		if (ccat.length() > 4) ccat = ccat.substring(0,4);
    		line += t.word + " ";
    		full_line += t.word + "\\" + ccat + " ";
    		if (cat.length()>0 && !cat.equals(t.guess)) cat = "MISC";
    		else cat = t.guess;
    	}
    	Markable res = new Markable(line, cat, from);
    	res.full_m = full_line;
    	return res;
    }
}
