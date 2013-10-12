package lv.lumii.expressions;

import static org.junit.Assert.*;
import lv.semti.morphology.attributes.AttributeNames;

import org.junit.Test;

public class AnalyzerTest
{
//Organizāciju nosaukumu testi

	@Test
	public void test1() throws Exception
	{
		Expression e = new Expression("Latvijas vēstures un filoloģijas fakultāte");
		//System.out.println(e.inflect("Ģenitīvs", "org"));
		assertEquals("Latvijas vēstures un filoloģijas fakultātes", e.inflect("Ģenitīvs", "org"));
	}
	
	@Test
	public void test2() throws Exception
	{
		Expression e = new Expression("Latvijas vēstures un filoloģijas fakultāti");
		//System.out.println(e.normalize());
		assertEquals("Latvijas vēstures un filoloģijas fakultāte", e.normalize()); // e.normalize() == e.inflect("Nominatīvs", "org") == e.inflect("Nominatīvs", null)
	}
	
	@Test
	public void test3() throws Exception
	{
		Expression e = new Expression("Latvijas rakstnieku savienība");
		//System.out.println(e.inflect("Lokatīvs", "org"));
		assertEquals("Latvijas rakstnieku savienībā", e.inflect("Lokatīvs", "org"));
	}
	
	@Test
	public void test4() throws Exception
	{
		Expression e = new Expression("Hārvardas universitāte");
		assertEquals("Hārvardas universitāti", e.inflect("Akuzatīvs", "org"));
	}
	
	@Test
	public void test5() throws Exception
	{
		Expression e = new Expression("Latvija tēla veidošanas institūtā");
		assertEquals("Latvija tēla veidošanas institūts", e.normalize());
	}
	
	@Test
	public void test6() throws Exception
	{
		Expression e = new Expression("izdevniecība \"Liesma\"");
		System.out.println(e.inflect("Ģenitīvs", "org"));
		//assertEquals("izdevniecības \"Liesma\"", e.inflect("Ģenitīvs", "org"));
	}
	
	@Test
	public void test7() throws Exception
	{
		Expression e = new Expression("Jūrmalas 1. vidusskola");
		//System.out.println(e.inflect("Akuzatīvs", "org"));
		assertEquals("Jūrmalas 1. vidusskolu", e.inflect("Akuzatīvs", "org"));
	}
	
//Cilvēku vārdu un uzvārdu testi
	@Test
	public void cilvēki() throws Exception
	{
		assertEquals("Hanss Kristians Andersens", 
				new Expression("Hansa Kristiana Andersena").inflect(AttributeNames.v_Nominative, "hum"));
		assertEquals("Hansa Kristiana Andersena", 
				new Expression("Hanss Kristians Andersens").inflect(AttributeNames.v_Genitive, "hum"));
		
		assertEquals("Valda Dombrovska", 
				new Expression("Valdis Dombrovskis").inflect(AttributeNames.v_Genitive, "hum"));
		assertEquals("V. Dombrovska", 
				new Expression("V. Dombrovskis").inflect(AttributeNames.v_Genitive, "hum"));
		
		assertEquals("Bērziņš", 
				new Expression("Bērziņš").normalize());
		assertEquals("Bērziņam", 
				new Expression("Bērziņš").inflect(AttributeNames.v_Dative, "hum"));
	}
	
	@Test
	public void organizācijas() throws Exception
	{
		assertEquals("Latvijas Kultūras fonds", 
				new Expression("Latvijas Kultūras fondā").normalize());
		assertEquals("Latvijas Kultūras fondā", 
				new Expression("Latvijas Kultūras fonds").inflect(AttributeNames.v_Locative, "org"));
	}
	
	@Test
	public void awards() throws Exception
	{
		assertEquals("Triju zvaigžņu ordenis", 
				new Expression("Triju zvaigžņu ordeni").normalize());
		assertEquals("Triju zvaigžņu ordeni", 
				new Expression("Triju zvaigžņu ordenis").inflect(AttributeNames.v_Accusative, "award"));
	}
	
//Ģeogrāfisko vietu nosaukumu testi
	
//Apbalvojumu nosaukumu testi
}
