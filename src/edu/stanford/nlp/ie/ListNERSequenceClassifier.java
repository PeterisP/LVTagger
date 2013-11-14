package edu.stanford.nlp.ie;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Properties;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.HashIndex;

public class ListNERSequenceClassifier extends AbstractSequenceClassifier<CoreLabel> {
  private Map<String, Entry> entries;

  /**
   * If true, it overwrites NE labels generated through this regex NER
   * This is necessary because sometimes the RegexNERSequenceClassifier is run successively over the same text (e.g., to overwrite some older annotations)
   */
  private boolean overwriteMyLabels;

  private Set<String> myLabels;

  private boolean ignoreCase;

//  public ListNERSequenceClassifier(String mapping, boolean ignoreCase, boolean overwriteMyLabels) {
//    this(mapping, ignoreCase, overwriteMyLabels);
//  }

  /**
   * Make a new instance of this classifier. The ignoreCase option allows case-insensitive
   * regular expression matching, provided with the idea that the provided file might just
   * be a manual list of the possible entities for each type.
   * @param mapping
   * @param ignoreCase
   */
  public ListNERSequenceClassifier(String mapping, boolean ignoreCase, boolean overwriteMyLabels) {
    super(new Properties());
    entries = new HashMap<>();
    this.ignoreCase = ignoreCase;
    this.overwriteMyLabels = overwriteMyLabels;
    String[] mappings = mapping.split(",");
    myLabels = new HashSet<String>();
    for (String map : mappings) {
    	readEntries(map, ignoreCase);
    }
    System.err.println("Lists are initialized...");
    this.classIndex = new HashIndex<String>(myLabels);
    //System.err.println("RegexNERSequenceClassifier using labels: " + myLabels);
  }

  public static String token(CoreLabel w) {
	String lemma = w.lemma();
	String token = w.word();
	Character cLemma = lemma.charAt(0);
	Character cToken = token.charAt(0);
	if (Character.isUpperCase(cToken) != Character.isUpperCase(cLemma)) {
		lemma = token.substring(0,1) + lemma.substring(1);
	}
	return lemma;
	  
  }

  private static class Entry {
	  	public String word;
	    public Map<String, Entry> words;
	    public String type; // the associated type
	    public Set<String> overWritableTypes;
	    public double priority;

	    public Entry() {
	    	words = new HashMap<>();
	    }
	    public Entry(String type, Set<String> overWritableTypes, double priority) {
	      this.type = type.intern();
	      this.overWritableTypes = overWritableTypes;
	      this.priority = priority;
	      this.words = new HashMap<>();
	    }
	  }

  // TODO: make this a property?
  // ms: but really this should be rewritten from scratch
  //     we should have a language to specify regexes over *tokens*, where each token could be a regular Java regex (over words, POSs, etc.)
  public static final String DEFAULT_VALID_POS = "^(NN|JJ)";
  private boolean containsValidPos(List<CoreLabel> tokens, int start, int end) {
	Pattern validPosPattern = Pattern.compile(DEFAULT_VALID_POS);
    if (validPosPattern == null) {
      return true;
    }
    // System.err.println("CHECKING " + start + " " + end);
    for(int i = start; i < end; i ++){
      // System.err.println("TAG = " + tokens.get(i).tag());
      if (tokens.get(i).tag() == null) {
        throw new IllegalArgumentException("The regex ner was asked to check for valid tags on an untagged sequence.  Either tag the sequence, perhaps with the pos annotator, or create the regex ner with an empty pos tag, perhaps with the flag regexner.validpospattern=");
      }
      Matcher m = validPosPattern.matcher(tokens.get(i).tag());
      if(m.find()) return true;
    }
    return false;
  }

	private static class MatchedEntry implements Comparable<MatchedEntry>{
		public Double priority;
		public String type;
		public Set<String> overwritableTypes;
		public List<CoreLabel> tokens;
		
		public MatchedEntry(List<CoreLabel> tokens, String type, Set<String> overWritableTypes, Double priority) {
			this.tokens = tokens;
			this.type = type;
			this.overwritableTypes = overWritableTypes;
			this.priority = priority;
		 }
		@Override
		public int compareTo(MatchedEntry other) {
			if (this.priority > other.priority)return -1;
			if (this.priority < other.priority) return 1;
			return  this.tokens.size()- other.tokens.size();
		}		  
	}
	
	@Override
	public List<CoreLabel> classify(List<CoreLabel> document) {
		List<MatchedEntry> matchedEntries = new ArrayList<>();
		int maxWord = 0; // max right word reached (in matched entry) from before or current position i
		for (int i = 0; i < document.size(); i++) {
			//if ((i)%100==0) System.err.println(i+ " words tagged");
			Map<String, Entry> entryMap = entries;
			List<CoreLabel> tokens = new ArrayList<>();
			int j = i;
			CoreLabel wj = document.get(j);
			while (entryMap != null && entryMap.containsKey(token(wj))) {
				tokens.add(wj);
				Entry e = entryMap.get(token(wj));
				if (e.type != null && e.type.length() > 0) {
					//System.err.println("FOUND " + e.type);
					matchedEntries.add(new MatchedEntry(tokens, e.type, e.overWritableTypes, e.priority));
					tokens = new ArrayList<>(tokens);
				}
				entryMap = e.words;
				if (j+1 >= document.size()) break;
				wj = document.get(++j);
			}
			
			maxWord = Math.max(maxWord, j);
			if (i == maxWord && matchedEntries.size() > 0) {
				Collections.sort(matchedEntries);
				for (MatchedEntry me : matchedEntries) {
					for (CoreLabel c : me.tokens) {
						c.set(AnswerAnnotation.class, me.type);
						//System.err.println(c.word() + "\t" + me.type);
					}
				}
				matchedEntries.clear();
			}
		}
		return document;
	}

  public void train(Collection<List<CoreLabel>> docs,
                    DocumentReaderAndWriter<CoreLabel> readerAndWriter) {}

  public void printProbsDocument(List<CoreLabel> document) {}

  public void serializeClassifier(String serializePath) {}

  public void loadClassifier(ObjectInputStream in, Properties props)
          throws IOException, ClassCastException, ClassNotFoundException {}

  /**
   *  Creates a combined list of Entries using the provided mapping file, and sorts them by
   *  first by priority, then the number of tokens in the regex.
   *
   *  @param mapping The path to a file of mappings
   *  @return a sorted list of Entries
   */
  private Map<String, Entry> readEntries(String mapping, boolean ignoreCase) {
    Map<String, Entry> entries = new HashMap<>();

    try {
      // ms, 2010-10-05: try to load the file from the CLASSPATH first
      InputStream is = getClass().getClassLoader().getResourceAsStream(mapping);
      // if not found in the CLASSPATH, load from the file system
      if (is == null) is = new FileInputStream(mapping);
      BufferedReader rd = new BufferedReader(new InputStreamReader(is));

      int lineCount = 0;
      for (String line; (line = rd.readLine()) != null; ) {
        lineCount ++;
        String[] split = line.split("\t");
        if (split.length < 2 || split.length > 4)
          throw new RuntimeException("Provided mapping file is in wrong format");

        String[] words = split[1].trim().split("\\s+");
        String type = split[0].trim();
        Set<String> overwritableTypes = new HashSet<String>();
        overwritableTypes.add(flags.backgroundSymbol);
        overwritableTypes.add(null);
        double priority = 0;
        List<String> tokens = new ArrayList<String>();

        try {
          if (split.length >= 3)
            overwritableTypes.addAll(Arrays.asList(split[2].trim().split(",")));
          if (split.length == 4)
            priority = Double.parseDouble(split[3].trim());

          for (String str : words) {
            tokens.add(str);
          }
        } catch(NumberFormatException e) {
          System.err.println("ERROR: Invalid line " + lineCount + " in regexner file " + mapping + ": \"" + line + "\"!");
          throw e;
        }
        addEntry(words, type, priority, overwritableTypes);
      }
      rd.close();
      is.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return entries;
  }
  
  
  private void addEntry(String[] words, String type, Double priority, Set<String> overwritableTypes) {
	  Map<String, Entry> entryMap = entries;
	  Entry currentEntry = new Entry();
      for(String word : words) {
      	currentEntry = new Entry();
      	if (entryMap.containsKey(word)) {
      		currentEntry = entryMap.get(word);
      		entryMap = currentEntry.words;
      	} else {
      		entryMap.put(word, currentEntry);
      		entryMap = currentEntry.words;
      	}
      }
      currentEntry.overWritableTypes = overwritableTypes;
      currentEntry.priority = priority;
      currentEntry.type = type;
      
      myLabels.add(type);
  }

  @Override
  public List<CoreLabel> classifyWithGlobalInformation(List<CoreLabel> tokenSeq, final CoreMap doc, final CoreMap sent) {
    return classify(tokenSeq);
  }
}
