package lv.lumii.ner.analysis;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import edu.stanford.nlp.util.Pair;


public class ErrorAnalysis {
	public static void main(String[] args) throws Exception {
		String nerFile = "";
	
		for (int i = 0; i < args.length; i++) {			
			if (args[i].equalsIgnoreCase("-nerFile")) nerFile = args[i+1];
			
			if (args[i].equalsIgnoreCase("-h") || args[i].equalsIgnoreCase("--help") || args[i].equalsIgnoreCase("-?")) {
				System.out.println("--- Error analysis ---");
				System.out.println("\n\t-nerFile : NER comparison file");
				System.out.flush();
				System.exit(0);
			}
		}
		
		java.util.Date date = new java.util.Date();
		SimpleDateFormat timeFormat = new SimpleDateFormat("MM/dd/yyyy h:mm:ss a");
		System.out.println(timeFormat.format(date));
		System.out.println("--- Error analysis ---");
		
		Document d = new Document();
		d.readDocument(nerFile);
		
		Tally tp = new Tally();
		Tally fp = new Tally();
		Tally fn = new Tally();
		countResults(d, tp, fp, fn);
		printResults(tp, fp, fn);
		printErrors(tp, fp, fn);
		
    }

	public static boolean countResults(Document doc, 
			Tally entityTP,
			Tally entityFP,
			Tally entityFN) {
	int index = 0;
	int goldIndex = 0, guessIndex = 0;
	String lastGold = "O", lastGuess = "O";
	
	// As we go through the document, there are two events we might be
	// interested in.  One is when a gold entity ends, and the other
	// is when a guessed entity ends.  If the gold and guessed
	// entities end at the same time, started at the same time, and
	// match entity type, we have a true positive.  Otherwise we
	// either have a false positive or a false negative.
	for (Token line : doc.document) {
	  String gold = line.gold;
	  String guess = line.guess;
	
	  if (gold == null || guess == null)
	    return false;
	
	  if (!lastGold.equals(gold) && !lastGold.equals("O")) {
	    if (lastGuess.equals(lastGold) && !lastGuess.equals(guess) &&
	        goldIndex == guessIndex) {
	      entityTP.incrementCount(lastGold, 1.0);
	    } else {
	      entityFN.incrementCount(lastGold, 1.0);
	            
	      int start = Math.min(doc.getGoldMarkableStart(index-1), doc.getGuessMarkableStart(index-1));
	      int end = Math.max(doc.getGoldMarkableEnd(index-1), doc.getGuessMarkableEnd(index-1));
	      if (!lastGuess.equals("O")) entityFN.addError(lastGold, doc.makeGuessMarkable(start, end), doc.makeGoldMarkable(start, end), false);
		  else entityFN.addError(lastGold, doc.makeGuessMarkable(start, end), doc.makeGoldMarkable(start, end), false);
	    }
	  }
	
	  if (!lastGuess.equals(guess) && !lastGuess.equals("O")) {
	    if (lastGuess.equals(lastGold) && !lastGuess.equals(guess) &&
	        goldIndex == guessIndex && !lastGold.equals(gold)) {
	      // correct guesses already tallied
	      // only need to tally false positives
	    } else {
	      entityFP.incrementCount(lastGuess, 1.0);
	      int start = Math.min(doc.getGoldMarkableStart(index-1), doc.getGuessMarkableStart(index-1));
	      int end = Math.max(doc.getGoldMarkableEnd(index-1), doc.getGuessMarkableEnd(index-1));
	      if (!lastGold.equals("O")) entityFP.addError(lastGuess, doc.makeGuessMarkable(start, end), doc.makeGoldMarkable(start, end), true);
		  else entityFP.addError(lastGuess, doc.makeGuessMarkable(start, end), doc.makeGoldMarkable(start, end), true);
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
	if (!lastGold.equals("O")) {
	  if (lastGold.equals(lastGuess) && goldIndex == guessIndex) {
	    entityTP.incrementCount(lastGold, 1.0);
	  } else {
	    entityFN.incrementCount(lastGold, 1.0);
	    int start = Math.min(doc.getGoldMarkableStart(index-1), doc.getGuessMarkableStart(index-1));
	      int end = Math.max(doc.getGoldMarkableEnd(index), doc.getGuessMarkableEnd(index-1));
	    if (!lastGuess.equals("O")) entityFN.addError(lastGold, doc.makeGuessMarkable(start, end), doc.makeGoldMarkable(start, end), false);
		else entityFN.addError(lastGold, doc.makeGuessMarkable(start, end), doc.makeGoldMarkable(start, end), false);
	  }
	}
	if (!lastGuess.equals("O")) {
	  if (lastGold.equals(lastGuess) && goldIndex == guessIndex) {
	    // correct guesses already tallied
	  } else {
	    entityFP.incrementCount(lastGuess, 1.0);
	    int start = Math.min(doc.getGoldMarkableStart(index-1), doc.getGuessMarkableStart(index-1));
	    int end = Math.max(doc.getGoldMarkableEnd(index-1), doc.getGuessMarkableEnd(index-1));
	    entityFP.addError(lastGuess, doc.makeGuessMarkable(start, end), doc.makeGoldMarkable(start, end), true);
//	    if (!lastGold.equals("O")) entityFP.addError(lastGuess, doc.makeGuessMarkable(guessIndex, index), doc.makeGoldMarkable(goldIndex, index), true);
//	    else entityFP.addError(lastGuess, doc.makeGuessMarkable(guessIndex, index), doc.makeGoldMarkable(guessIndex, index), true);
	  }
	}
	return true;
	}

	  public static void printResults(Tally entityTP,Tally entityFP, Tally entityFN) {
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
	      System.err.println("         Entity\tP\tR\tF1\tTP\tFP\tFN");
	      printedHeader = true;
	    }
	    System.err.format("%15s\t%.4f\t%.4f\t%.4f\t%.0f\t%.0f\t%.0f\n",
	                      entity, precision, recall, f1,
	                      tp, fp, fn);
	    return printedHeader;
	  }

	  public static void printErrors(Tally entityTP, Tally entityFP, Tally entityFN) {
		  
		  double tp = entityTP.totalCount();
		  
			System.err.println("\n===============\nFalse positives\n===============\n");
			System.err.format("%20s\t%s\n","GUESS", "GOLD");
			for (String cat : entityFP.errByCategory.keySet()) {
				System.err.format("\n===== %s %.4f (%.0f/%.0f)================ \n", cat, entityFP.getImpact(cat), entityFP.getCount(cat),entityFP.totalCount());
				for (Pair<Markable, Markable> p : entityFP.errByCategory.get(cat)) {
					System.err.format("%20s\t%s\n",p.first.full(), p.second.full());
				}
			}
			
			Collections.sort(entityFP.allErrors);
			for (String s : entityFP.allErrors) {
				System.err.println(s);
			}
			
			System.err.println("\n===============\nFalse negatives\n===============\n");
			System.err.format("%20s\t%s\n","GOLD", "GUESS");
			for (String cat : entityFN.errByCategory.keySet()) {
				System.err.format("\n===== %s %.4f (%.0f/%.0f)================ \n", cat, entityFN.getImpact(cat), entityFN.getCount(cat), entityFN.totalCount());
				for (Pair<Markable, Markable> p : entityFN.errByCategory.get(cat)) {
					System.err.format("%20s\t%s\n",p.second.full(), p.first.full());
				}
			}
			
			Collections.sort(entityFN.allErrors);
			for (String s : entityFN.allErrors) {
				System.err.println(s);
			}
	  }
	  
	  
}
