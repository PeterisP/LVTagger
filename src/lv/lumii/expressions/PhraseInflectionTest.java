/*******************************************************************************
 * Copyright 2013,2014 Institute of Mathematics and Computer Science, University of Latvia
 * Author: Pēteris Paikens
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

import static org.junit.Assert.*;

import java.io.PrintWriter;

import lv.lumii.expressions.Expression.Gender;
import lv.semti.morphology.analyzer.Analyzer;
import lv.semti.morphology.attributes.AttributeNames;

import org.junit.BeforeClass;
import org.junit.Test;

import edu.stanford.nlp.sequences.LVMorphologyReaderAndWriter;

public class PhraseInflectionTest
{
//Organizāciju nosaukumu testi
	@BeforeClass
	public static void setUpBeforeClass() {
		LVMorphologyReaderAndWriter.initAnalyzer();
		LVMorphologyReaderAndWriter.getAnalyzer().guessInflexibleNouns = true;
		LVMorphologyReaderAndWriter.getAnalyzer().guessVerbs = false;
		
		//TODO - šie settingi ir kopēti no webservisa, un var izskaidrot atšķirības starp uzvedību tur un testos. Tur būtu jābūt vienādi!
		//LVMorphologyReaderAndWriter.getAnalyzer().guessAdjectives = false;
		LVMorphologyReaderAndWriter.getAnalyzer().guessParticiples = false;		
	}
	
	@Test
	public void test1() throws Exception
	{
		Expression e = new Expression("Latvijas vēstures un filoloģijas fakultāte", "org", true);
		//System.out.println(e.inflect("Ģenitīvs", "org"));
		assertEquals("Latvijas vēstures un filoloģijas fakultātes", e.inflect("Ģenitīvs"));
	}
	
	@Test
	public void test2() throws Exception
	{
		Expression e = new Expression("Latvijas vēstures un filoloģijas fakultāti", null, false);
		//System.out.println(e.normalize());
		assertEquals("Latvijas vēstures un filoloģijas fakultāte", e.normalize()); // e.normalize() == e.inflect("Nominatīvs", "org") == e.inflect("Nominatīvs", null)
	}
	
	@Test
	public void test3() throws Exception
	{
		Expression e = new Expression("Latvijas rakstnieku savienība", "org", true);
		//System.out.println(e.inflect("Lokatīvs", "org"));
		assertEquals("Latvijas rakstnieku savienībā", e.inflect("Lokatīvs"));
	}
	
	@Test
	public void test4() throws Exception
	{
		Expression e = new Expression("Hārvardas universitāte", "org", true);
		assertEquals("Hārvardas universitāti", e.inflect("Akuzatīvs"));
	}
	
	@Test
	public void tagera_problēmas() throws Exception
	{		
		// Jāuzlabo tageris, dodot treniņdatos pareizu entīšu paraugus.
		
		Expression e = new Expression("Latvija tēla veidošanas institūtā", null, false, false);
		assertEquals("Latvija tēla veidošanas institūts", e.inflect("Nominatīvs", false));
		// Tageris spītīgi izdomā, ka 'institūtā' ir sieviešu dzimte (jo pārējie 3 vārdi sieviešu). 

		assertEquals("Reģionālajai investīciju bankai", 
				new Expression("Reģionālā investīciju banka", "organization", true, true).inflect(AttributeNames.v_Dative));
		// Tageris izdomā, ka "Reģionālā" ir vienskaitļa ģenitīvs un tādēļ nesaskaņojas ar "banka"
	}
	
	@Test
	public void test6() throws Exception
	{
		Expression e = new Expression("izdevniecība \"Liesma\"", "org", true);
		assertEquals("izdevniecības \" Liesma \"", e.inflect("Ģenitīvs"));
	}
	
	@Test
	public void test7() throws Exception
	{
		Expression e = new Expression("Jūrmalas 1. vidusskola", "org", true);
		//System.out.println(e.inflect("Akuzatīvs", "org"));
		assertEquals("Jūrmalas 1. vidusskolu", e.inflect("Akuzatīvs"));
	}
	
//Cilvēku vārdu un uzvārdu testi
	@Test
	public void cilveeki() throws Exception
	{		
		assertEquals("Hanss Kristians Andersens", 
				new Expression("Hansa Kristiana Andersena", "hum", false).inflect(AttributeNames.v_Nominative));
		assertEquals("Hansa Kristiana Andersena", 
				new Expression("Hanss Kristians Andersens", "hum", true).inflect(AttributeNames.v_Genitive));
		
		assertEquals("Valda Dombrovska", 
				new Expression("Valdis Dombrovskis", "hum", true).inflect(AttributeNames.v_Genitive));
		assertEquals("V. Dombrovska", 
				new Expression("V. Dombrovskis", "hum", true).inflect(AttributeNames.v_Genitive));
		
		assertEquals("Bērziņš", 
				new Expression("Bērziņš", "hum", false).normalize());
		assertEquals("Bērziņam", 
				new Expression("Bērziņš", "hum", false).inflect(AttributeNames.v_Dative));
		
		assertEquals("Žverelo", 
				new Expression("Žverelo", "hum", false).inflect(AttributeNames.v_Nominative));
		
		Expression savickis = new Expression("Savickis", "hum", true);
		assertEquals("Savickim", savickis.inflect(AttributeNames.v_Dative));
		
		assertEquals("Žubikai Ilzei", 
				new Expression("Žubika Ilze", "person", true).inflect(AttributeNames.v_Dative));
		
		assertEquals("Znarokam Oļegam", 
				new Expression("Znaroks Oļegs", "person", true).inflect(AttributeNames.v_Dative));
		
		assertEquals("Zosārs Uve", 
				new Expression("Zosārs Uve", "person", true).inflect(AttributeNames.v_Nominative));
		
		assertEquals("Linai Santoro", 
				new Expression("Lina Santoro", "person", true).inflect(AttributeNames.v_Dative));
		
		assertEquals("Zlatkum Ērikam", 
				new Expression("Zlatkus Ēriks", "person", true).inflect(AttributeNames.v_Dative));
		
		assertEquals("Zaļaiskalnam Sandrai", 
				new Expression("Zaļaiskalns Sandra", "person", true).inflect(AttributeNames.v_Dative));
		
		assertEquals("Zalcmanim Raivo", 
				new Expression("Zalcmanis Raivo", "person", true).inflect(AttributeNames.v_Dative));
		
		Expression veetra = new Expression("Vētra Andrejs", "person", true);
		assertEquals("Vētram Andrejam", veetra.inflect(AttributeNames.v_Dative));			
		
		assertEquals("Valdavam Vigo", 
				new Expression("Valdavs Vigo", "person", true).inflect(AttributeNames.v_Dative));
		
		assertEquals("Turlajam Dainim", 
				new Expression("Turlais Dainis", "person", true).inflect(AttributeNames.v_Dative));
		
		assertEquals("Rižajai Genovefai",  
				new Expression("Rižā Genovefa", "person", true).inflect(AttributeNames.v_Dative));
		
//		assertEquals("Tennilam Veli Pekkai", //TODO - ko vispār ar tādiem nevalodas vārdiem darīt ?? 
//				new Expression("Tennila Veli Pekka", "person", true).inflect(AttributeNames.v_Dative));
	}
	
	@Test
	public void organizācijas() throws Exception
	{
		assertEquals("Latvijas Kultūras fonds", 
				new Expression("Latvijas Kultūras fondā", null, false).normalize());
		assertEquals("Latvijas Kultūras fondā", 
				new Expression("Latvijas Kultūras fonds", "org", true).inflect(AttributeNames.v_Locative));
		
		assertEquals("uzņēmums \" LATSTROITRANS \"", 
				new Expression("uzņēmuma \" LATSTROITRANS \"", null, false).normalize());
		
		assertEquals("\" WEI RED \" , SIA", 
				new Expression("\"WEI RED\", SIA", null, false).normalize());
		
		assertEquals("RPVIA", 
				new Expression("RPVIA", null, false).normalize());
		
		assertEquals("IU \" Rītums \"", 
				new Expression("IU \" Rītums \"", null, false).normalize());
	}
	
	@Test
	public void orgNames2() throws Exception
	{
		assertEquals("AS Aldaris", 
				new Expression("AS Aldaris", "org", true).inflect(AttributeNames.v_Locative));
		
		assertEquals("SIA Latrostrans", 
				new Expression("SIA Latrostrans", "org", true).inflect(AttributeNames.v_Locative));
	}	
	
	@Test
	public void awards() throws Exception
	{
		assertEquals("Triju zvaigžņu ordenis", 
				new Expression("Triju zvaigžņu ordeni", null, false).normalize());
		assertEquals("Triju zvaigžņu ordeni", 
				new Expression("Triju zvaigžņu ordenis", "award", true).inflect(AttributeNames.v_Accusative));
	}
	
	@Test
	public void vietas() throws Exception
	{
		assertEquals("Ragaciemā", 
				new Expression("Ragaciems", "loc", true).inflect(AttributeNames.v_Locative));
	}
	
	@Test
	public void nemācētie_salabotie() throws Exception
	{
		assertEquals("Latvijas Lielo pilsētu asociācijā", 
				new Expression("Latvijas Lielo pilsētu asociācija", "org", true).inflect(AttributeNames.v_Locative));
		
		assertEquals("akcijas kopējajā vērtībā", //TODO - te varbūt labāk bez noteiktās formas, bet citur gan ir svarīgi (augstākā izglītība vs augsta izglītība) 
				new Expression("akcijas kopējā vērtība", "sum", true).inflect(AttributeNames.v_Locative));
		
		assertEquals("ANO", 
				new Expression("ANO", null, true).normalize());

		assertEquals("sabiedriskajām attiecībām", 
				new Expression("sabiedriskās attiecības", null, true, false).inflect(AttributeNames.v_Dative, false));
		
		assertEquals("starpkultūru sakariem", 
				new Expression("starpkultūru sakari", null, true, false).inflect(AttributeNames.v_Dative, false));
	}
	
	@Test
	public void nemācētie() throws Exception
	{		
		assertEquals("veiksmīga atgriešanās", 
				new Expression("veiksmīgu atgriešanos", null, false, false).inflect(AttributeNames.v_Nominative, false));
	}

	@Test
	public void laiki() throws Exception
	{
		assertEquals("5. marts", 
				new Expression("5. martā", "time", false).inflect(AttributeNames.v_Nominative));
		
		assertEquals("6. jūlijs", 
				new Expression("6. jūlijā", "time", false).inflect(AttributeNames.v_Nominative));
		
		assertEquals("7. augusts", 
				new Expression("7. augustā", "time", false).inflect(AttributeNames.v_Nominative));
	}

	@Test
	public void vietvārdi_īpašie() throws Exception {
		assertEquals("Pļaviņas", 
				new Expression("Pļaviņas", "loc", true).inflect(AttributeNames.v_Nominative));

		assertEquals("Saldus vidusskola", 
				new Expression("Saldus vidusskolu", "org", false).inflect(AttributeNames.v_Nominative));
	}
	
	@Test
	public void CVbugi20131104() throws Exception {
		assertEquals("deputāts", 
				new Expression("deputāti", "profession", false).inflect(AttributeNames.v_Nominative));
		
		assertEquals("Ludzas pilsēta", 
				new Expression("Ludzas pilsētas", "loc", false).inflect(AttributeNames.v_Nominative));
		
		assertEquals("DPS saraksts", 
				new Expression("DPS saraksta", "org", false).inflect(AttributeNames.v_Nominative));
	}

	@Test
	public void CVbugi20131105() throws Exception {
		assertEquals("Rīgas domes vēlēšanas", 
				new Expression("Rīgas domes vēlēšanās", null, false).inflect(AttributeNames.v_Nominative));
		
		assertEquals("augstākā izglītība", 
				new Expression("augstākā izglītība", null, true).inflect(AttributeNames.v_Nominative));
		
		assertEquals("Tautsaimnieku politiskā apvienība", 
				new Expression("Tautsaimnieku politiskās apvienības", "org", false).inflect(AttributeNames.v_Nominative));
		
		assertEquals("Tautsaimnieku politiskās apvienības", 
				new Expression("Tautsaimnieku politiskā apvienība", "org", true).inflect(AttributeNames.v_Genitive));
	}
	
	@Test 
	public void CV50() throws Exception {
		assertEquals("Deniss Gorba", 
				new Expression("Deniss Gorba", "person", true).inflect(AttributeNames.v_Nominative));
		
		assertEquals("Edgars Zalāns", 
				new Expression("Edgara Zalāna", "person", false).inflect(AttributeNames.v_Nominative));
		
		assertEquals("locekļa amats", 
				new Expression("locekļa amata", null, false).normalize());
		
		Expression sudraba = new Expression("Inguna Sudraba", "hum", true);
		//sudraba.describe(new PrintWriter(System.out));
		assertEquals("Ingunai Sudrabai", sudraba.inflect(AttributeNames.v_Dative));
	}
	
	@Test 
	public void vienskaitļi() throws Exception {
		assertEquals("valdes priekšsēdētājs", 
				new Expression("valdes priekšsēdētāju", null, false).normalize());
	}
	
	@Test 
	public void pārākā() throws Exception {
		assertEquals("Jelgavas tehniskais licejs", 
				new Expression("Jelgavas tehniskais licejs", null, true).normalize());
		
		assertEquals("augstākā izglītība", 
				new Expression("augstāko izglītību", null, false).normalize());
		
		assertEquals("vidējā speciālā izglītība", 
				new Expression("vidējo speciālo izglītību", null, false).normalize());
	}
	
	@Test
	public void personas_ar_amatiem() throws Exception {
		assertEquals("valdes priekšsēdētājs Ivars Zariņš", 
				new Expression("valdes priekšsēdētājs Ivars Zariņš", null, true).inflect(AttributeNames.v_Nominative));
	}

	@Test
	public void vietas2() throws Exception {
		assertEquals("Seišelu Salās", 
				new Expression("Seišelu Salas", "loc", true).inflect(AttributeNames.v_Locative));
		
		assertEquals("Ķemeri", 
				new Expression("Ķemeros", "loc", false).inflect(AttributeNames.v_Nominative));
		
		// jālabo:
		// Gana -> Ganai
		// Amerikas Savinotās Valstis, Apvienotie Arābu Emirāti
		// Bosnija un Hercegovina
		// Gvineja-Bisava
		// Balvi		
	}
	
	@Test
	public void double_surnames() throws Exception {
		Expression vvf = new Expression("Vaira Vīķe-Freiberga", "person", true); 
		assertEquals("Vairai Vīķei-Freibergai", vvf.inflect(AttributeNames.v_Dative));

		assertEquals("Zanei Graudiņai-Arājai", 
				new Expression("Zane Graudiņa-Arāja", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Žanetai Jaunzemei-Grendei", 
				new Expression("Žaneta Jaunzeme-Grende", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Zundai Kalniņai-Lukaševicai", 
				new Expression("Zunda Kalniņa-Lukaševica", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Solam Normanam Bukingoltam", 
				new Expression("Sols Normans Bukingolts", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Signei Reinholdei-Āboliņai", 
				new Expression("Signe Reinholde-Āboliņa", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Silvai Jeromanovai-Maurai", 
				new Expression("Silva Jeromanova-Maura", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Oļģertam Persijam Dzenītim", 
				new Expression("Oļģerts Persijs Dzenītis", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Mārtiņam Gunāram Bauzem-Krastiņam", 
				new Expression("Mārtiņš Gunārs Bauze-Krastiņš", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Mārtiņam Burkem-Burkevicam", 
				new Expression("Mārtiņš Burke-Burkevics", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Mārtiņam Ādamam Kalniņam", 
				new Expression("Mārtiņš Ādams Kalniņš", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Mārim Rudaus-Rudovskim", 
				new Expression("Māris Rudaus-Rudovskis", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Sanitai Pavļutai-Deslandes", 
				new Expression("Sanita Pavļuta-Deslandes", "person", true).inflect(AttributeNames.v_Dative));
	}
	
	@Test
	public void neizloka() throws Exception {
		// nav assert, bet jāskatās vai nemet errorpaziņojumus konsolē
		new Expression("pakalpojumi saistīti ar uzturēšanu", null, false).normalize();
		new Expression("Sabiedriskās attiecības", null, false).normalize();
		new Expression("reklāma , mārketings , telekomunikācijas", null, false).normalize();		
	}
	
	@Test
	public void OOV_uzvārdi() throws Exception {
		assertEquals("Guntim Bārzdiņam", 
				new Expression("Guntis Bārzdiņš", "person", true).inflect(AttributeNames.v_Dative));
		
		assertEquals("Ansim Dombrovskim", 
				new Expression("Ansis Dombrovskis", "person", true).inflect(AttributeNames.v_Dative));
	}
	
	@Test
	public void WTFnames() throws Exception {
		assertEquals("Dacei Markui", // ? 
				new Expression("Dace Markus", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Maijai Kubli", // ?
				new Expression("Maija Kubli", "person", true).inflect(AttributeNames.v_Dative, true));		
		assertEquals("Inesei Bērs", // ?
				new Expression("Inese Bērs", "person", true).inflect(AttributeNames.v_Dative, true));		
	}
	
	@Test
	public void LETA_personas_20140414() throws Exception {
		assertEquals("Kasparam Siļķem", 
				new Expression("Kaspars Siļķe", "person", true).inflect(AttributeNames.v_Dative));		
		assertEquals("Ingmāram Līdakam", 
				new Expression("Ingmārs Līdaka", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Pēterim Līdakam", 
				new Expression("Pēteris Līdaka", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Valdim Līdakam", 
				new Expression("Valdis Līdaka", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Kasparam Gobam", 
				new Expression("Kaspars Goba", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Kasparam Gravam", 
				new Expression("Kaspars Grava", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Kasparam Kambalam", 
				new Expression("Kaspars Kambala", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Kārlim Smilgam", 
				new Expression("Kārlis Smilga", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Kārlim Miķelim Čakstem", 
				new Expression("Kārlis Miķelis Čakste", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Jēkaba Blaua", 
				new Expression("Jēkabs Blaus", "person", true).inflect(AttributeNames.v_Genitive));
		assertEquals("Jēkabam Blauam", 
				new Expression("Jēkabs Blaus", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Jēkabu Blauu", 
				new Expression("Jēkabs Blaus", "person", true).inflect(AttributeNames.v_Accusative));
		assertEquals("Maijai Ozolai", 
				new Expression("Maija Ozola", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Sanitai Liepiņai", 
				new Expression("Sanita Liepiņa", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Anitai Kalniņai", 
				new Expression("Anita Kalniņa", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Anitai Riekstiņai", 
				new Expression("Anita Riekstiņa", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Anitai Vītolai", 
				new Expression("Anita Vītola", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Kristīnei Zariņai", 
				new Expression("Kristīne Zariņa", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Gunitai Liepiņai", 
				new Expression("Gunita Liepiņa", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Gunitai Bērziņai", 
				new Expression("Gunita Bērziņa", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Gunitai Ozoliņai", 
				new Expression("Gunita Ozoliņa", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Laurai Liepiņai", 
				new Expression("Laura Liepiņa", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Laurai Slaviņai", 
				new Expression("Laura Slaviņa", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Dacei Ozolai", 
				new Expression("Dace Ozola", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Dacei Zariņai", 
				new Expression("Dace Zariņa", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Jānim Bārdam", 
				new Expression("Jānis Bārda", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Jānim Dirbam", 
				new Expression("Jānis Dirba", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Jānim Drullem", 
				new Expression("Jānis Drulle", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Jānim Mottem", 
				new Expression("Jānis Motte", "person", true).inflect(AttributeNames.v_Dative));
		assertEquals("Jānim Ogam", 
				new Expression("Jānis Oga", "person", true).inflect(AttributeNames.v_Dative));

	}

	@Test
	public void x20140703() throws Exception {
		assertEquals("Andris Bērziņš", 
				new Expression("Andra Bērziņa", "person", false).normalize());
	}

	@Test
	public void LETA_personas_20140819() throws Exception {
		assertEquals("Kristiānai Lībanei", 
				new Expression("Kristiāna Lībane", "person", true).inflect(AttributeNames.v_Dative));
	}
	
	@Test 
	public void LETA_personas_20141023() throws Exception {
		assertEquals("Jurijam Sovam", 
				new Expression("Jurijs Sova", "person", true).inflect(AttributeNames.v_Dative));
		
		assertEquals("Gunaram Šķēlem", 
				new Expression("Gunars Šķēle", "person", true).inflect(AttributeNames.v_Dative));
		
		assertEquals("Arno Sonkam", 
				new Expression("Arno Sonks", "person", true).inflect(AttributeNames.v_Dative));
		
		assertEquals("Sarmai Vaskai", 
				new Expression("Sarma Vaska", "person", true).inflect(AttributeNames.v_Dative));
		
		assertEquals("Ojāram Bitem", 
				new Expression("Ojārs Bite", "person", true).inflect(AttributeNames.v_Dative));
		
		assertEquals("Ainai Āriņai", 
				new Expression("Aina Āriņa", "person", true).inflect(AttributeNames.v_Dative));
		
		assertEquals("Aleksandram Niedrem", 
				new Expression("Aleksandrs Niedre", "person", true).inflect(AttributeNames.v_Dative));
	}
	
	
	@Test 
	public void galvenais() throws Exception {
		assertEquals("galveno redaktoru", 
				new Expression("galvenais redaktors", "profession", false).inflect(AttributeNames.v_Accusative));
	}
	
	@Test
	public void LETA_personas_20141204() throws Exception {
		assertEquals("Regīna Laudere", 
				new Expression("Regīna Laudere", "person", false).normalize());
	}
	
	@Test
	public void dzimtes() throws Exception {
		assertEquals(Gender.feminine, 
				new Expression("Aija Krištopane", "person", true, false).gender);
		
		assertEquals(Gender.feminine, 
				new Expression("Ieva Bondare", "person", true, false).gender);

	}
	
	@Test
	public void defises() throws Exception {
		assertEquals("biedram-kandidātam", 
				new Expression("biedrs-kandidāts", "xxx", true, false).inflect(AttributeNames.v_Dative));
	}
	
	@Test
	public void lokatīvnieki() throws Exception {
		assertEquals("Latvijas Republikas vēstniecībai Dānijā", 
				new Expression("Latvijas Republikas vēstniecība Dānijā", "organization", true, false).inflect(AttributeNames.v_Dative));
		
		assertEquals("direktora vietniecei mācību darbā", 
				new Expression("direktora vietniece mācību darbā", "organization", true, false).inflect(AttributeNames.v_Dative));
		
		assertEquals("padomniekam ārlietu jautājumos", 
				new Expression("padomnieks ārlietu jautājumos", "organization", true, false).inflect(AttributeNames.v_Dative));		
	}
	
	@Test
	public void pēdiņas() throws Exception {
		assertEquals("\" Mis Foto \" titulam", 
				new Expression("\"Mis Foto\" tituls", null, true, false).inflect(AttributeNames.v_Dative));
		
		assertEquals("raidījuma \" Nedēļa \" vadītājai", 
				new Expression("raidījuma \"Nedēļa\" vadītāja", null, true, false).inflect(AttributeNames.v_Dative));
	}
	
	@Test
	public void šanās() throws Exception {
		assertEquals("šķīrušās", 
				new Expression("šķīrusies", null, true, false).inflect(AttributeNames.v_Genitive, true));
	}
	
	@Test 
	public void Ilzes_todo() throws Exception {
		assertEquals("Apvienotajam štābam", 
				new Expression("Apvienotais štābs", "organization", true, false).inflect(AttributeNames.v_Dative, false));
		
		assertEquals("Nacionālajam veselības centram", 
				new Expression("Nacionālais veselības centrs", "organization", true, false).inflect(AttributeNames.v_Dative, false));
		
		assertEquals("Pirmajam atklātajam pensiju fondam", 
				new Expression("Pirmais atklātais pensiju fonds", "organization", true, false).inflect(AttributeNames.v_Dative, false));
		
		assertEquals("Nacionālajiem bruņotajiem spēkiem", 
				new Expression("Nacionālie bruņotie spēki", "organization", true, false).inflect(AttributeNames.v_Dative, false));
		
		assertEquals("Baltijas pirmajam kanālam", 
				new Expression("Baltijas pirmais kanāls", "organization", true, false).inflect(AttributeNames.v_Dative, false));
		
		assertEquals("Rīgas Austrumu klīniskajai universitātes slimnīcai", 
				new Expression("Rīgas Austrumu klīniskā universitātes slimnīca", "organization", true, false).inflect(AttributeNames.v_Dative, false));
		
		assertEquals("Projektēšanas birojam Arhis", 
				new Expression("Projektēšanas birojs Arhis", "organization", true, false).inflect(AttributeNames.v_Dative, false));
		
		assertEquals("Valteram un Rapai", 
				new Expression("Valters un Rapa", "organization", true, false).inflect(AttributeNames.v_Dative, false));
	}
		
	@Test 
	public void LETA_personas_20150126() throws Exception {
		assertEquals("Raivim Zeltītam", 
				new Expression("Raivis Zeltīts", "person", true, false).inflect(AttributeNames.v_Dative));
		
		assertEquals("Laumai ( Dzintara Pomera sievai )", 
				new Expression("Lauma (Dzintara Pomera sieva)", "person", true, false).inflect(AttributeNames.v_Dative));
		
		assertEquals("Jaundubultiem", 
				new Expression("Jaundubulti", "location", true).inflect(AttributeNames.v_Dative));
		
		assertEquals("Amerikas Savienotajām Valstīm", 
				new Expression("Amerikas Savienotās Valstis", "location", true).inflect(AttributeNames.v_Dative));
	}
	
	@Test 
	public void atkal_ģenitīvi() throws Exception {
		assertEquals("Saldus novadā", 
				new Expression("Saldus novads", "location", true, true).inflect(AttributeNames.v_Locative));
	}	

	
	@Test 
	public void LETA_veelmes_uz_20150215() throws Exception {
		assertEquals("Olimpiskajām spēlēm", 
				new Expression("Olimpiskās spēles", "xxx", true, false).inflect(AttributeNames.v_Dative, false));
	}
}
