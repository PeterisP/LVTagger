package edu.stanford.nlp.process;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.*;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.objectbank.TokenizerFactory;
import edu.stanford.nlp.objectbank.XMLBeginEndIterator;
import edu.stanford.nlp.util.Function;

/**
 * Produces a list of sentences from either a plain text or XML document.
 * <p>
 * Tokenization: The default tokenizer is {@link PTBTokenizer}. If null is passed to
 * <code>setTokenizerFactory</code>, then whitespace tokenization is assumed.
 * <p>
 * Adding a new document type requires two steps:
 * <ol>
 * <li> Add a new DocType.
 * <li> Create an iterator for the new DocType and modify the iterator() function to return the new iterator.
 * </ol>
 * <p>
 * NOTE: This implementation should <em>not</em> use external libraries since it is used in the parser.
 *
 * @author Spence Green
 */
public class DocumentPreprocessor implements Iterable<List<HasWord>> {

  public static enum DocType {Plain, XML}

  private Reader inputReader = null;
  private String inputPath = null;
  private DocType docType = DocType.Plain;

  //Configurable options
  private TokenizerFactory<? extends HasWord> tokenizerFactory = PTBTokenizer.factory();
  private String encoding = null;
  private String[] sentenceFinalPuncWords = {".", "?", "!"};
  private Function<List<HasWord>,List<HasWord>> escaper = null;
  private String sentenceDelimiter = null;
  /**
   * Example: if the words are already POS tagged and look like
   * foo_VB, you want to set the tagDelimiter to "_"
   */
  private String tagDelimiter = null;
  /**
   * When doing XML parsing, only accept text in between tags that
   * match this regular expression.  Defaults to everything.
   */
  private String elementDelimiter = ".*";

  //From PTB conventions
  private final String[] sentenceFinalFollowers = {")", "]", "\"", "\'", "''", "-RRB-", "-RSB-", "-RCB-"};

  /**
   * Constructs a preprocessor from an existing input stream.
   *
   * @param input An existing reader
   */
  public DocumentPreprocessor(Reader input) {
    this(input,DocType.Plain);
  }

  public DocumentPreprocessor(Reader input, DocType t) {
    if (input == null)
      throw new RuntimeException("Cannot read from null object!");

    docType = t;
    inputReader = input;
  }

  /**
   * Constructs a preprocessor from a file at a path, which can be either
   * a filesystem location or a URL.
   *
   * @param docPath
   */
  public DocumentPreprocessor(String docPath) {
    this(docPath,DocType.Plain);
  }

  public DocumentPreprocessor(String docPath, DocType t) {
    if (docPath == null)
      throw new RuntimeException("Cannot open null document path!");

    docType = t;
    inputPath = docPath;
  }

  /**
   * Set the character encoding.
   *
   * @param encoding The character encoding used by Readers
   * @throws IllegalCharsetNameException If the JVM does not support the named character set.
   */
  public void setEncoding(String encoding) throws IllegalCharsetNameException {
    if (Charset.isSupported(encoding))
      this.encoding = encoding;
  }

  /**
   * Sets the end-of-sentence delimiters.
   * <p>
   * For newline tokenization, use the argument {"\n"}.
   *
   * @param sentenceFinalPuncWords
   */
  public void setSentenceFinalPuncWords(String[] sentenceFinalPuncWords) {
    this.sentenceFinalPuncWords = sentenceFinalPuncWords;
  }

  /**
   * Sets the factory from which to produce a {@link Tokenizer}.  The default is
   * {@link PTBTokenizer}.
   * <p>
   * NOTE: If a null argument is used, then the document is assumed to be tokenized
   * and DocumentPreprocessor performs no tokenization.
   *
   */
  public void setTokenizerFactory(TokenizerFactory<? extends HasWord> newTokenizerFactory) {
    tokenizerFactory = newTokenizerFactory;
  }

  /**
   * Set an escaper.
   *
   * @param e The escaper
   */
  public void setEscaper(Function<List<HasWord>,List<HasWord>> e) { escaper = e; }

  /**
   * Make the processor assume that the document is already delimited
   * by the supplied parameter.
   *
   * @param s The sentence delimiter
   */
  public void setSentenceDelimiter(String s) { sentenceDelimiter = s; }

  /**
   * Split tags from tokens. The tag will be placed in the TagAnnotation of
   * the returned label.
   * <p>
   * Note that for strings that contain two or more instances of the tag delimiter,
   * the last instance is treated as the split point.
   * <p>
   * The tag delimiter should not contain any characters that must be escaped in a Java
   * regex.
   *
   * @param s POS tag delimiter
   */
  public void setTagDelimiter(String s) { tagDelimiter = s; }

  /**
   * Only read text from inside these XML elements if in XML mode.
   * <i>Note:</i> This class implements an approximation to XML via regex.
   *
   * Otherwise, text will read from all tokens.
   */
  public void setElementDelimiter(String s) { elementDelimiter = s; }


  /**
   * Returns sentences until the document is exhausted. Calls close() if the end of the document
   * is reached. Otherwise, the user is required to close the stream.
   */
  public Iterator<List<HasWord>> iterator() {
    try {
      if (inputReader == null)
        inputReader = getReaderFromPath(inputPath);

      //TODO: Add new document types here
      if (docType == DocType.Plain) {
        return new PlainTextIterator();
      } else if (docType == DocType.XML) {
        return new XMLIterator();
      }

    } catch (IOException e) {
      System.err.printf("%s: Could not open path %s\n", this.getClass().getName(), inputPath);
    }

    return new Iterator<List<HasWord>>() {
      public boolean hasNext() { return false; }
      public List<HasWord> next() { throw new NoSuchElementException(); }
      public void remove() {}
    };
  }


  private Reader getReaderFromPath(String path) throws IOException {
    //Check if it is a URL first, otherwise look for a file
    try {
      URL url = new URL(path);
      URLConnection connection = url.openConnection();
      return new BufferedReader(new InputStreamReader(connection.getInputStream()));

    } catch(MalformedURLException e) {
      //Do nothing: the path may be a file
    }

    File file = new File(path);
    if (file.exists()) {
      return (encoding == null) ? new FileReader(path) : new BufferedReader(new InputStreamReader(new FileInputStream(file), encoding));
    }
    throw new IOException("Unable to open " + path);
  }

  private class PlainTextIterator implements Iterator<List<HasWord>> {

    private Tokenizer<? extends HasWord> tokenizer;
    private Set<String> sentDelims;
    private Set<String> delimFollowers = new HashSet<String>(Arrays.asList(sentenceFinalFollowers));
    private Function<String, String[]> splitTag;
    private List<HasWord> nextSent = null;
    private List<HasWord> nextSentCarryover = new ArrayList<HasWord>();

    public PlainTextIterator() {
      // Establish how to find sentence boundaries
      sentDelims = new HashSet<String>();
      boolean eolIsSignificant = false;
      if (sentenceDelimiter == null) {
        if (sentenceFinalPuncWords != null) {
          sentDelims = new HashSet<String>(Arrays.asList(sentenceFinalPuncWords));
        }
      } else {
        sentDelims.add(sentenceDelimiter);
        delimFollowers = new HashSet<String>();
        eolIsSignificant = sentenceDelimiter.matches("\\s+");
        if(eolIsSignificant) // For Stanford English Tokenizer
          sentDelims.add(PTBLexer.NEWLINE_TOKEN);
      }

      // Setup the tokenizer
      if(tokenizerFactory == null) {
        tokenizer = WhitespaceTokenizer.
          newWordWhitespaceTokenizer(inputReader, eolIsSignificant);
      } else {
        if (eolIsSignificant) {
          tokenizer = tokenizerFactory.getTokenizer(inputReader, "tokenizeNLs");
        } else {
          tokenizer = tokenizerFactory.getTokenizer(inputReader);
        }
      }

      // If tokens are tagged, then we must split them
      // Note that if the token contains two or more instances of the delimiter, then the last
      // instance is regarded as the split point.
      if (tagDelimiter != null) {
        splitTag = new Function<String,String[]>() {
          private final String splitRegex = String.format("%s(?!.*%s)",tagDelimiter,tagDelimiter);
          public String[] apply(String in) {
            final String[] splits = in.trim().split(splitRegex);
            if(splits.length == 2)
              return splits;
            else {
              String[] oldStr = {in};
              return oldStr;
            }
          }
        };
      }
    }

    private void primeNext() {
      nextSent = new ArrayList<HasWord>(nextSentCarryover);
      nextSentCarryover.clear();
      boolean seenBoundary = false;

      while (tokenizer.hasNext()) {

        HasWord token = tokenizer.next();
        if (splitTag != null) {
          String[] toks = splitTag.apply(token.word());
          token.setWord(toks[0]);
          if (token instanceof Label) {
            ((Label) token).setValue(toks[0]);
          }
          if(toks.length == 2 && token instanceof HasTag) {
            //wsg2011: Some of the underlying tokenizers return old
            //JavaNLP labels.  We could convert to CoreLabel here, but
            //we choose a conservative implementation....
            ((HasTag) token).setTag(toks[1]);
          }
        }

        if (sentDelims.contains(token.word())) {
          seenBoundary = true;
        } else if (seenBoundary && !delimFollowers.contains(token.word())) {
          nextSentCarryover.add(token);
          break;
        }

        if ( ! (token.word().matches("\\s+") || 
                token.word().equals(PTBLexer.NEWLINE_TOKEN))) {
          nextSent.add(token);
        }

        // If there are no words that can follow a sentence delimiter,
        // then there are two cases.  In one case is we already have a
        // sentence, in which case there is no reason to look at the
        // next token, since that just causes buffering without any
        // chance of the current sentence being extended, since
        // delimFollowers = {}.  In the other case, we have an empty
        // sentence, which at this point means the sentence delimiter
        // was a whitespace token such as \n.  We might as well keep
        // going as if we had never seen anything.
        if (seenBoundary && delimFollowers.size() == 0) {
          if (nextSent.size() > 0) {
            break;
          } else {
            seenBoundary = false;
          }
        }
      }

      if (nextSent.size() == 0 && nextSentCarryover.size() == 0) {
        IOUtils.closeIgnoringExceptions(inputReader);
        inputReader = null;
        nextSent = null;
      } else if (escaper != null) {
        nextSent = escaper.apply(nextSent);
      }
    }

    public boolean hasNext() { 
      if (nextSent == null) {
        primeNext();
      }
      return nextSent != null; 
    }

    public List<HasWord> next() {
      if (nextSent == null) {
        primeNext();
      }
      if (nextSent == null) {
        throw new NoSuchElementException();
      }
      List<HasWord> thisIteration = nextSent;
      nextSent = null;
      return thisIteration;
    }

    public void remove() { throw new UnsupportedOperationException(); }
  }

  private class XMLIterator implements Iterator<List<HasWord>> {

    private final XMLBeginEndIterator<String> xmlItr;
    private final Reader originalDocReader;
    private PlainTextIterator plainItr; // = null;
    private List<HasWord> nextSent; // = null;

    public XMLIterator() {
      xmlItr = new XMLBeginEndIterator<String>(inputReader, elementDelimiter);
      originalDocReader = inputReader;
      primeNext();
    }

    private void primeNext() {
      // It is necessary to loop because if a document has a pattern
      // that goes: <tag></tag> the xmlItr will return an empty
      // string, which the plainItr will process to null.  If we
      // didn't loop to find the next tag, the iterator would stop.
      do {
        if (plainItr != null && plainItr.hasNext()) {
          nextSent = plainItr.next();
        } else if (xmlItr.hasNext()) {
          String block = xmlItr.next();
          inputReader = new BufferedReader(new StringReader(block));
          plainItr = new PlainTextIterator();
          if (plainItr.hasNext()) {
            nextSent = plainItr.next();
          } else {
            nextSent = null;
          }
        } else {
          IOUtils.closeIgnoringExceptions(originalDocReader);
          nextSent = null;
          break;
        }
      } while (nextSent == null);
    }

    public boolean hasNext() {
      return nextSent != null;
    }

    public List<HasWord> next() {
      if (nextSent == null) {
        throw new NoSuchElementException();
      }
      List<HasWord> thisSentence = nextSent;
      primeNext();
      return thisSentence;
    }

    public void remove() { throw new UnsupportedOperationException(); }
  }


  /**
   * This provides a simple test method for DocumentPreprocessor. <br/>
   * Usage:
   * java
   * DocumentPreprocessor filename [-xml tag] [-suppressEscaping] [-noTokenization]
   * <p>
   * A filename is required. The code doesn't run as a filter currently.
   * <p>
   * tag is the element name of the XML from which to extract text.  It can
   * be a regular expression which is called on the element with the
   * matches() method, such as 'TITLE|P'.
   *
   * @param args Command-line arguments
   */
  public static void main(String[] args) throws IOException{
    if (args.length < 1) {
      System.err.println("usage: DocumentPreprocessor filename [OPTS]");
      return;
    }

    DocumentPreprocessor docPreprocessor = new DocumentPreprocessor(args[0]);
    String encoding = "utf-8";
    boolean printSentenceLengths = false;

    for (int i = 1; i < args.length; i++) {
      if (args[i].equals("-xml")) {
        docPreprocessor = new DocumentPreprocessor(args[0], DocType.XML);
        docPreprocessor.setElementDelimiter(args[++i]);

      } else if (args[i].equals("-encoding") && i+1 < args.length) {
        i++;
        encoding = args[i];

      } else if (args[i].equals("-printSentenceLengths")) {
        printSentenceLengths = true;

      } else if (args[i].equals("-suppressEscaping")) {
        String options = "ptb3Escaping=false";
        docPreprocessor.setTokenizerFactory(PTBTokenizer.factory(new WordTokenFactory(),options));

      } else if (args[i].equals("-tokenizerOptions") && i+1 < args.length) {
        i++;
        String options = args[i];
        docPreprocessor.setTokenizerFactory(PTBTokenizer.factory(new WordTokenFactory(),options));

      } else if (args[i].equals("-noTokenization")) {
        docPreprocessor.setTokenizerFactory(null);
        docPreprocessor.setSentenceDelimiter(System.getProperty("line.separator"));

      } else if (args[i].equals("-tag")) {
        docPreprocessor.setTagDelimiter(args[++i]);

      } else {
        System.err.println("Unknown option: " + args[i]);
      }
    }

    docPreprocessor.setEncoding(encoding);

    int numSents = 0;
    PrintWriter pw = new PrintWriter(new OutputStreamWriter(System.out, encoding), true);
    for (List<HasWord> sentence : docPreprocessor) {
      numSents++;
      if (printSentenceLengths) {
        System.err.println("Length:\t" + sentence.size());
      }
      boolean printSpace = false;
      for (HasWord word : sentence) {
        if (printSpace) pw.print(" ");
        printSpace = true;
        pw.print(word.word());
      }
      pw.println();
    }
    pw.close();
    System.err.println("Read in " + numSents + " sentences.");
  }

}
