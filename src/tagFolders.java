import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.*;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;

import java.util.List;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

public class tagFolders {
    public static void main(String[] args) throws IOException {

      String serializedClassifier = "/Users/pet/Dropbox/NER/stanford-ner-2012-04-07/lv-ner-model.ser.gz";

      if (args.length > 0) {
        serializedClassifier = args[0];
      }

      //String startFolder = "/Users/pet/Documents/LNB_converted_done/01-01-01/LNB-Grammata 5/g_001_0302067766";
      String startFolder = "/Users/pet/Documents/LNB_converted";
      AbstractSequenceClassifier<CoreLabel> classifier = CRFClassifier.getClassifierNoExceptions(serializedClassifier);
//      Writer straume = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(startFolder + "/entities.sql"), "UTF-8"));
	  //NECounterSingleDoc counter = new NECounterFull();      
      try {
    	  walk(startFolder, classifier, null);
      } finally { // in case of OOM while analyzing, still dump the accumulated results
	      //counter.filter(2);
	      //counter.show();
	      //counter.db_insert();
    	  //straume.close();
      }
    }

	private static void processFile(
			AbstractSequenceClassifier<CoreLabel> classifier,
			//NECounterFull counter,
			String filename,
			String doc_id,
			Writer writer) {

		NECounterSingleDoc counter = new NECounterSingleDoc(doc_id);	
		DocumentReaderAndWriter<CoreLabel> readerAndWriter = classifier.makeReaderAndWriter();
		ObjectBank<List<CoreLabel>> documents = classifier.makeObjectBankFromFile(filename, readerAndWriter);
		  
		for (List<CoreLabel> document : documents) {
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
		}
		try {
			counter.db_insert(writer);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
    
    private static void walk( String path,
    		AbstractSequenceClassifier<CoreLabel> classifier,
			//NECounterSingleDoc counter
    		Writer writer
			) throws IOException {

        File root = new File( path );
        File[] list = root.listFiles();

        for ( File f : list ) {
            if ( f.isDirectory() ) {
            	Writer straume;
            	if (writer == null)  {
	                straume = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f.getAbsolutePath() + "/entities.sql"), "UTF-8"));
	                straume.write("-- " + f.getAbsolutePath() + "\n");
            	} else straume = writer;
	
                walk( f.getAbsolutePath(), classifier, straume );
                
                if (writer == null) {
                	System.out.println( "Finished:" + f.getAbsoluteFile() );
                	straume.close();
                }
            }
            else {        
                processFile(classifier, f.getAbsoluteFile().toString(), f.getAbsoluteFile().getParentFile().getName(), writer);
            }
        }
    }
 
}
