/*******************************************************************************
 * Copyright 2013,2014 Institute of Mathematics and Computer Science, University of Latvia
 * Author: Ginta Garkāje, Pēteris Paikens
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

package lv.lumii.expressions;

import lv.semti.morphology.analyzer.Word;
import lv.semti.morphology.analyzer.Wordform;
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