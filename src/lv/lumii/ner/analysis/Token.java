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
