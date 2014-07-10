package lv.lumii.expressions;

import static org.junit.Assert.*;

import java.io.PrintWriter;

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
	public void test5() throws Exception
	{
		Expression e = new Expression("Latvija tēla veidošanas institūtā", null, false);
		assertEquals("Latvija tēla veidošanas institūts", e.normalize());
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
				new Expression("Hansa Kristiana Andersena", "hum", false).inflect(AttributeNames.v_Nominative, true));
		assertEquals("Hansa Kristiana Andersena", 
				new Expression("Hanss Kristians Andersens", "hum", true).inflect(AttributeNames.v_Genitive, true));
		
		assertEquals("Valda Dombrovska", 
				new Expression("Valdis Dombrovskis", "hum", true).inflect(AttributeNames.v_Genitive));
		assertEquals("V. Dombrovska", 
				new Expression("V. Dombrovskis", "hum", true).inflect(AttributeNames.v_Genitive));
		
		assertEquals("Bērziņš", 
				new Expression("Bērziņš", "hum", false).normalize());
		assertEquals("Bērziņam", 
				new Expression("Bērziņš", "hum", false).inflect(AttributeNames.v_Dative));
		
		assertEquals("Žverelo", 
				new Expression("Žverelo", "hum", false).inflect(AttributeNames.v_Nominative, true));
		
		Expression savickis = new Expression("Savickis", "hum", true);
		assertEquals("Savickim", savickis.inflect(AttributeNames.v_Dative));
		
		assertEquals("Žubikai Ilzei", 
				new Expression("Žubika Ilze", "person", true).inflect(AttributeNames.v_Dative));
		
		assertEquals("Znarokam Oļegam", 
				new Expression("Znaroks Oļegs", "person", true).inflect(AttributeNames.v_Dative, true));
		
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
		assertEquals("Vētram Andrejam", veetra.inflect(AttributeNames.v_Dative, true));			
		
		assertEquals("Valdavam Vigo", 
				new Expression("Valdavs Vigo", "person", true).inflect(AttributeNames.v_Dative));
		
		assertEquals("Turlajam Dainim", 
				new Expression("Turlais Dainis", "person", true).inflect(AttributeNames.v_Dative, true));
		
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
	public void nemācētie() throws Exception
	{
		assertEquals("Latvijas Lielo pilsētu asociācijā", 
				new Expression("Latvijas Lielo pilsētu asociācija", "org", true).inflect(AttributeNames.v_Locative));
		
		assertEquals("veiksmīga atgriešanās", 
				new Expression("veiksmīgu atgriešanos", null, false).normalize());
		
		assertEquals("akcijas kopējajā vērtībā", //TODO - te varbūt labāk bez noteiktās formas, bet citur gan ir svarīgi (augstākā izglītība vs augsta izglītība) 
				new Expression("akcijas kopējā vērtība", "sum", true).inflect(AttributeNames.v_Locative));
		
		assertEquals("ANO", 
				new Expression("ANO", null, true).normalize());
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
				new Expression("Zane Graudiņa-Arāja", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Žanetai Jaunzemei-Grendei", 
				new Expression("Žaneta Jaunzeme-Grende", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Zundai Kalniņai-Lukaševicai", 
				new Expression("Zunda Kalniņa-Lukaševica", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Solam Normanam Bukingoltam", 
				new Expression("Sols Normans Bukingolts", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Signei Reinholdei-Āboliņai", 
				new Expression("Signe Reinholde-Āboliņa", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Silvai Jeromanovai-Maurai", 
				new Expression("Silva Jeromanova-Maura", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Oļģertam Persijam Dzenītim", 
				new Expression("Oļģerts Persijs Dzenītis", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Mārtiņam Gunāram Bauzem-Krastiņam", 
				new Expression("Mārtiņš Gunārs Bauze-Krastiņš", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Mārtiņam Burkem-Burkevicam", 
				new Expression("Mārtiņš Burke-Burkevics", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Mārtiņam Ādamam Kalniņam", 
				new Expression("Mārtiņš Ādams Kalniņš", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Mārim Rudaus-Rudovskim", 
				new Expression("Māris Rudaus-Rudovskis", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Sanitai Pavļutai-Deslandes", 
				new Expression("Sanita Pavļuta-Deslandes", "person", true).inflect(AttributeNames.v_Dative, true));
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
				new Expression("Dace Markus", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Maijai Kubli", // ?
				new Expression("Maija Kubli", "person", true).inflect(AttributeNames.v_Dative, true));		
		assertEquals("Inesei Bērs", // ?
				new Expression("Inese Bērs", "person", true).inflect(AttributeNames.v_Dative, true));		
	}
	
	@Test
	public void LETA_personas_20140414() throws Exception {
		assertEquals("Kasparam Siļķem", 
				new Expression("Kaspars Siļķe", "person", true).inflect(AttributeNames.v_Dative, true));		
		assertEquals("Ingmāram Līdakam", 
				new Expression("Ingmārs Līdaka", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Pēterim Līdakam", 
				new Expression("Pēteris Līdaka", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Valdim Līdakam", 
				new Expression("Valdis Līdaka", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Kasparam Gobam", 
				new Expression("Kaspars Goba", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Kasparam Gravam", 
				new Expression("Kaspars Grava", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Kasparam Kambalam", 
				new Expression("Kaspars Kambala", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Kārlim Smilgam", 
				new Expression("Kārlis Smilga", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Kārlim Miķelim Čakstem", 
				new Expression("Kārlis Miķelis Čakste", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Jēkaba Blaua", 
				new Expression("Jēkabs Blaus", "person", true).inflect(AttributeNames.v_Genitive, true));
		assertEquals("Jēkabam Blauam", 
				new Expression("Jēkabs Blaus", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Jēkabu Blauu", 
				new Expression("Jēkabs Blaus", "person", true).inflect(AttributeNames.v_Accusative, true));
		assertEquals("Maijai Ozolai", 
				new Expression("Maija Ozola", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Sanitai Liepiņai", 
				new Expression("Sanita Liepiņa", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Anitai Kalniņai", 
				new Expression("Anita Kalniņa", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Anitai Riekstiņai", 
				new Expression("Anita Riekstiņa", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Anitai Vītolai", 
				new Expression("Anita Vītola", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Kristīnei Zariņai", 
				new Expression("Kristīne Zariņa", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Gunitai Liepiņai", 
				new Expression("Gunita Liepiņa", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Gunitai Bērziņai", 
				new Expression("Gunita Bērziņa", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Gunitai Ozoliņai", 
				new Expression("Gunita Ozoliņa", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Laurai Liepiņai", 
				new Expression("Laura Liepiņa", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Laurai Slaviņai", 
				new Expression("Laura Slaviņa", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Dacei Ozolai", 
				new Expression("Dace Ozola", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Dacei Zariņai", 
				new Expression("Dace Zariņa", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Jānim Bārdam", 
				new Expression("Jānis Bārda", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Jānim Dirbam", 
				new Expression("Jānis Dirba", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Jānim Drullem", 
				new Expression("Jānis Drulle", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Jānim Mottem", 
				new Expression("Jānis Motte", "person", true).inflect(AttributeNames.v_Dative, true));
		assertEquals("Jānim Ogam", 
				new Expression("Jānis Oga", "person", true).inflect(AttributeNames.v_Dative, true));

	}

	@Test
	public void x20140703() throws Exception {
		assertEquals("Andris Bērziņš", 
				new Expression("Andra Bērziņa", "person", false).normalize());
	}
}
