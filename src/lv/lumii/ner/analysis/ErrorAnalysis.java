package lv.lumii.ner.analysis;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import sun.reflect.generics.tree.BottomSignature;

import edu.stanford.nlp.util.Pair;


public class ErrorAnalysis {
	
	public static ConfusionMatrix cm;
	public static boolean verbose = true;
	public static String[] categories;
	public static String[] defaultCategories = {"person", "organization", "location", "profession", "time", "product", "media", "sum", "event", "O"};
	
	public static Tally tp;
	public static Tally fp;
	public static Tally fn;
	
	public static ErrorSet borderErrors = new ErrorSet();	
	
	public static void main(String[] args) throws Exception {
		String nerFile = "";
	
		for (int i = 0; i < args.length; i++) {			
			if (args[i].equalsIgnoreCase("-nerFile")) nerFile = args[i+1];
			if (args[i].equalsIgnoreCase("-nonverbose")) verbose = false;
			if (args[i].equalsIgnoreCase("-categories")) categories = args[i+1].split(",");
			if (args[i].equalsIgnoreCase("-h") || args[i].equalsIgnoreCase("--help") || args[i].equalsIgnoreCase("-?")) {
				
				System.out.println("--- Error analysis ---");
				System.out.println("\n\t-nerFile : NER comparison file");
				System.out.println("\n\t-categories : custom categories used");
				System.out.println("\n\t-nonverbose : Print only summary");
				System.out.flush();
				System.exit(0);
			}
		}
		System.out.println("======== LV NER Error Analysis ========");
		java.util.Date date = new java.util.Date();
		SimpleDateFormat timeFormat = new SimpleDateFormat("MM/dd/yyyy h:mm:ss a");
		System.out.println(timeFormat.format(date));
		System.out.println();		
		
		Document d = new Document();
		d.readDocument(nerFile);
		
		Tally tp = new Tally();
		Tally fp = new Tally();
		Tally fn = new Tally();	
		
		
		if (categories == null || categories.length == 0) categories = defaultCategories;
				
		cm = new ConfusionMatrix(Arrays.asList(categories), "O");
		
		countResults(d, tp, fp, fn);
		
		printResults(tp, fp, fn);
		
		System.out.println(cm.summarize());
		
		System.out.println("Border Errors");
		System.out.println(borderErrors.summarize());
		
		if (verbose) {
			//printErrors(tp, fp, fn);
			System.out.println(cm.errors());
			
			System.out.println("Border Errors:");
			System.out.println(borderErrors);
		}
    }

	public static boolean countResults(Document doc, 
			Tally entityTP,
			Tally entityFP,
			Tally entityFN) {
	int index = 0;
	int goldIndex = 0, guessIndex = 0;
	String lastGold = "O", lastGuess = "O";
	boolean cm_set;
	for (Token line : doc.document) {
	  cm_set = false;
	  String gold = line.gold;
	  String guess = line.guess;
	
	  if (gold == null || guess == null)
	    return false;
	
	  if (!lastGold.equals(gold) && !lastGold.equals("O")) {
	    if (lastGuess.equals(lastGold) && !lastGuess.equals(guess) &&
	        goldIndex == guessIndex) {
	      entityTP.incrementCount(lastGold, 1.0);
	      cm.addInstance(lastGold, lastGuess);
	    } else {
	      entityFN.incrementCount(lastGold, 1.0); 
	      int start = Math.min(doc.getGoldMarkableStart(index-1), doc.getGuessMarkableStart(index-1));
	      int end = Math.max(doc.getGoldMarkableEnd(index-1), doc.getGuessMarkableEnd(index-1));
	      Markable guessMarkable = doc.makeGuessMarkable(start, end);
	      Markable goldMarkable = doc.makeGoldMarkable(start, end);
	      entityFN.addError(lastGold, guessMarkable, goldMarkable, false);
	      if (!lastGuess.equals(lastGold)) {
	    	  cm.addInstance(lastGold, lastGuess); 
	    	  cm.addError(lastGuess, lastGold, guessMarkable, goldMarkable);
	    	  cm_set = true;
	      } else {
	    	  //already same category and not "O"
	    	  borderErrors.addError(lastGold, goldMarkable, guessMarkable);
	      }
	    }
	  }
	
	  if (!lastGuess.equals(guess) && !lastGuess.equals("O")) {
	    if (lastGuess.equals(lastGold) && !lastGuess.equals(guess) &&
	        goldIndex == guessIndex && !lastGold.equals(gold)) {
	    } else {
	      entityFP.incrementCount(lastGuess, 1.0);
	      int start = Math.min(doc.getGoldMarkableStart(index-1), doc.getGuessMarkableStart(index-1));
	      int end = Math.max(doc.getGoldMarkableEnd(index-1), doc.getGuessMarkableEnd(index-1));
	      Markable guessMarkable = doc.makeGuessMarkable(start, end);
	      Markable goldMarkable = doc.makeGoldMarkable(start, end);
	      entityFP.addError(lastGuess, guessMarkable, goldMarkable, true);
	      if (!lastGuess.equals(lastGold) && !cm_set) {
	    	  cm.addInstance(lastGold, lastGuess); 
	    	  cm.addError(lastGuess, lastGold, guessMarkable, goldMarkable);
	      }
	    }
	  }
	
	  if (!lastGold.equals(gold)) {
	    lastGold = gold;
	    goldIndex = index;
	  }
	
	  if (!lastGuess.equals(guess)) {
	    lastGuess = guess;
	    guessIndex = index;
	  }
	  ++index;
	}
	
	index--;
	cm_set = false;
	if (!lastGold.equals("O")) {
	  if (lastGold.equals(lastGuess) && goldIndex == guessIndex) {
	    entityTP.incrementCount(lastGold, 1.0);
	    cm.addInstance(lastGold, lastGuess);
	  } else {
		if (!lastGuess.equals(lastGold)) cm.addInstance(lastGold, lastGuess); 
	    entityFN.incrementCount(lastGold, 1.0);
	    int start = Math.min(doc.getGoldMarkableStart(index-1), doc.getGuessMarkableStart(index-1));
	    int end = Math.max(doc.getGoldMarkableEnd(index), doc.getGuessMarkableEnd(index-1));
	    Markable guessMarkable = doc.makeGuessMarkable(start, end);
	    Markable goldMarkable = doc.makeGoldMarkable(start, end);
	    entityFN.addError(lastGold, guessMarkable, goldMarkable, false);
		if (!lastGuess.equals(lastGold)) {
		  cm.addInstance(lastGold, lastGuess); 
		  cm.addError(lastGuess, lastGold, guessMarkable, goldMarkable);
		  cm_set = true;
		} else {
	    	  borderErrors.addError(lastGold, goldMarkable, guessMarkable);
	      }
	  }
	}
	if (!lastGuess.equals("O")) {
	  if (lastGold.equals(lastGuess) && goldIndex == guessIndex) {
	    // correct guesses already tallied
	  } else {
		if (!lastGuess.equals(lastGold)) cm.addInstance(lastGold, lastGuess); 
	    entityFP.incrementCount(lastGuess, 1.0);
	    int start = Math.min(doc.getGoldMarkableStart(index-1), doc.getGuessMarkableStart(index-1));
	    int end = Math.max(doc.getGoldMarkableEnd(index-1), doc.getGuessMarkableEnd(index-1));
	    Markable guessMarkable = doc.makeGuessMarkable(start, end);
		Markable goldMarkable = doc.makeGoldMarkable(start, end);
		entityFP.addError(lastGuess, guessMarkable, goldMarkable, true);
		if (!lastGuess.equals(lastGold) && !cm_set) {
		  cm.addInstance(lastGold, lastGuess);
		  cm.addError(lastGuess, lastGold, guessMarkable, goldMarkable);
		}
	  }
	}
	return true;
	}

	  public static void printResults(Tally entityTP,Tally entityFP, Tally entityFN) {
		System.out.println("NER Evaluation\n-----------------");
	    Set<String> entities = new TreeSet<String>();
	    entities.addAll(entityTP.keySet());
	    entities.addAll(entityFP.keySet());
	    entities.addAll(entityFN.keySet());
	    boolean printedHeader = false;
	    for (String entity : entities) {
	      double tp = entityTP.getCount(entity);
	      double fp = entityFP.getCount(entity);
	      double fn = entityFN.getCount(entity);
	      printedHeader = printPRLine(entity, tp, fp, fn, printedHeader);
	    }
	    double tp = entityTP.totalCount();
	    double fp = entityFP.totalCount();
	    double fn = entityFN.totalCount();
	    printedHeader = printPRLine("Totals", tp, fp, fn, printedHeader);
	    System.out.println();
	  }

	  private static boolean printPRLine(String entity, double tp, double fp, double fn,
	                             boolean printedHeader) {
	    if (tp == 0.0 && (fp == 0.0 || fn == 0.0))
	      return printedHeader;
	    double precision = tp / (tp + fp);
	    double recall = tp / (tp + fn);
	    double f1 = ((precision == 0.0 || recall == 0.0) ?
	                 0.0 : 2.0 / (1.0 / precision + 1.0 / recall));
	    if (!printedHeader) {
	      System.out.println("         Entity\tP\tR\tF1\tTP\tFP\tFN");
	      printedHeader = true;
	    }
	    System.out.format("%15s\t%.4f\t%.4f\t%.4f\t%.0f\t%.0f\t%.0f\n",
	                      entity, precision, recall, f1,
	                      tp, fp, fn);
	    return printedHeader;
	  }

	  public static void printErrors(Tally entityTP, Tally entityFP, Tally entityFN) {

		  double tp = entityTP.totalCount();
		  
			System.out.println("\n===============\nFalse positives\n===============\n");
			System.out.format("%20s\t%s\n","GUESS", "GOLD");
			for (String cat : entityFP.errByCategory.keySet()) {
				System.out.format("\n===== %s %.4f (%.0f/%.0f)================ \n", cat, entityFP.getImpact(cat), entityFP.getCount(cat),entityFP.totalCount());
				for (Pair<Markable, Markable> p : entityFP.errByCategory.get(cat)) {
					System.out.format("%20s\t%s\n",p.first.full(), p.second.full());
				}
			}

			System.out.println("\n===============\nFalse negatives\n===============\n");
			System.out.format("%20s\t%s\n","GOLD", "GUESS");
			for (String cat : entityFN.errByCategory.keySet()) {
				System.out.format("\n===== %s %.4f (%.0f/%.0f)================ \n", cat, entityFN.getImpact(cat), entityFN.getCount(cat), entityFN.totalCount());
				for (Pair<Markable, Markable> p : entityFN.errByCategory.get(cat)) {
					System.out.format("%20s\t%s\n",p.second.full(), p.first.full());
				}
			}
	  } 
}
