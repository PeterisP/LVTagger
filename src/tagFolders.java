import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.*;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TagLabelAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;

import java.util.ArrayList;
import java.util.List;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

public class tagFolders {
	static String serializedClassifier = "/Users/pet/Dropbox/NER/stanford-ner-2012-04-07/lv-ner-model.ser.gz";
	
    public static void main(String[] args) throws IOException {

      if (args.length > 0) {
        serializedClassifier = args[0];
      }

      //String startFolder = "/Users/pet/Documents/LNB_converted/01-03-01";
      String startFolder = "/Users/pet/Documents/LNB_converted";
      
      int total = walk(startFolder, null, null);
      System.out.print(total);
    }

	private static int processFile(
			AbstractSequenceClassifier<CoreLabel> classifier,
			String filename,
			String doc_id,
			Writer writer) {

		NECounterSingleDoc counter = new NECounterSingleDoc(doc_id);	
		
		List<CoreLabel> document = new ArrayList<CoreLabel>(); 
		int i = 0;
		try {
			BufferedReader ieeja = new BufferedReader(	new InputStreamReader(new FileInputStream(filename), "UTF-8"));
			String line;
			while ((line = ieeja.readLine()) != null) {
				i++;
				if (line.contains("<doc") || line.contains("</doc>") || line.contains("<page") || line.contains("</page>") ||line.contains("<p>")) continue;
				
				String[] info = line.split("\t");
				CoreLabel word = new CoreLabel();

				if (line.contains("</p>")) {
					word.set(TextAnnotation.class, "<p/>");
					word.set(LemmaAnnotation.class, "<p/>");
					word.set(PartOfSpeechAnnotation.class, "-");										
					continue;
				} else if (line.contains("<g />")) {
					word.set(TextAnnotation.class, "<g/>");
					word.set(LemmaAnnotation.class, "<g/>");
					word.set(PartOfSpeechAnnotation.class, "-");										
					continue;
				} else if (info.length<3) {
					System.err.printf("%d @ %s:%s", i, filename, line);
				} else {
					word.set(TextAnnotation.class, info[0]);
					word.set(LemmaAnnotation.class, info[1]);
					word.set(PartOfSpeechAnnotation.class, info[2].substring(0, 1));					
				}
				document.add(word);
			}
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
	  List<CoreLabel> out = classifier.classify(document);
	  String prevtag = "";
	  String name_part = "";
	  String lemma_part = "";
      for (CoreLabel word : out) {
    	  String tag = word.get(AnswerAnnotation.class);
    	  if (tag.length()<2) tag = "";
    	  if (!tag.equalsIgnoreCase(prevtag)) {
    		  if (!prevtag.equalsIgnoreCase("")) counter.add(doc_id, name_part, lemma_part, prevtag);
    		  if (!tag.equalsIgnoreCase("")) {
    			  name_part = word.word();
    			  lemma_part = word.get(LemmaAnnotation.class);
    		  }
    	  } else if (!tag.equalsIgnoreCase("")) {
    		  name_part = name_part + " "  + word.word();
    		  lemma_part = lemma_part + " "  + word.get(LemmaAnnotation.class);
    	  }
    		  
    	  prevtag = tag;
      }	    
		try {
			counter.db_insert(writer);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return i;
	}
    
    private static int walk( String path,
    		AbstractSequenceClassifier<CoreLabel> parentclassifier,
    		Writer writer
			) throws IOException {

        File root = new File( path );
        File[] list = root.listFiles();

		int i = 0;

        AbstractSequenceClassifier<CoreLabel> classifier = parentclassifier;
        if (classifier == null && !path.endsWith("converted")) classifier = CRFClassifier.getClassifierNoExceptions(serializedClassifier);
        
        for ( File f : list ) {
            if (f.getName().startsWith(".")) continue;
        	if ( f.isDirectory() ) {
            	Writer straume;
            	if (writer == null)  {
	                straume = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f.getAbsolutePath() + "/entities.sql"), "UTF-8"));
	                straume.write("-- " + f.getAbsolutePath() + "\n");
            	} else straume = writer;
            	long sākums = System.currentTimeMillis();
	
                int words = walk( f.getAbsolutePath(), classifier, straume );
                i += words;
                
                if (writer == null) {
                	long beigas = System.currentTimeMillis();
                	System.out.printf( "Finished %s : %,d k-words, %,d wps\n", f.getAbsoluteFile(), i/1000, words*1000/(beigas-sākums));
                	straume.close();
                }
            }
            else {        
                i += processFile(classifier, f.getAbsoluteFile().toString(), f.getAbsoluteFile().getParentFile().getName(), writer);
            }
        }
        
        return i;
    }
 
}
