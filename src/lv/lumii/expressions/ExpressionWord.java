
package lv.lumii.expressions;

import lv.semti.morphology.analyzer.Word;
import lv.semti.morphology.analyzer.Wordform;

/**
 * @author Ginta
 *
 */
public class ExpressionWord
{
	public boolean isStatic;
	public Word word;
	public Wordform correctWordform;
	
	ExpressionWord(Word w)
	{
		this(w,w.getBestWordform());
	}
	
	ExpressionWord(Word w, Wordform correctWF)
	{
		this.word=w;
		this.correctWordform=correctWF;
		isStatic=false;
	}
	
}