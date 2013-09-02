package lv.lumii.expressions;

import static org.junit.Assert.*;

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
	
//Ģeogrāfisko vietu nosaukumu testi
	
//Apbalvojumu nosaukumu testi
}
