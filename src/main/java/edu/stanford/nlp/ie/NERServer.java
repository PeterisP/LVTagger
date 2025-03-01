package edu.stanford.nlp.ie;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Properties;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.io.EncodingPrintWriter;
import edu.stanford.nlp.util.StringUtils;


/*****************************************************************************
 * A named-entity recognizer server for Stanford's NER.
 * Runs on a socket and waits for text to annotate and returns the
 * annotated text.  (Internally, it uses the <code>classifyString()</code>
 * method on a classifier, which can be either the default CRFClassifier
 * which is serialized inside the jar file from which it is called, or another
 * classifier which is passed as an argument to the main method.
 *
 * @version $Id: NERServer.java 36863 2011-07-12 22:58:19Z horatio $
 * @author
 *      Bjorn Aldag<BR>
 *      Copyright &copy; 2000 - 2004 Cycorp, Inc.  All rights reserved.
 *      Permission granted for Stanford to distribute with their NER code
 *      by Bjorn Aldag
 * @author Christopher Manning 2006 (considerably rewritten)
 *
*****************************************************************************/

public class NERServer {

  //// Variables

  /**
   * Debugging toggle.
   */
  private boolean DEBUG = false;

  private final String charset;

  /**
   * The listener socket of this server.
   */
  private final ServerSocket listener;

  /**
   * The classifier that does the actual tagging.
   */
  private final AbstractSequenceClassifier ner;


  //// Constructors

  /**
   * Creates a new named entity recognizer server on the specified port.
   *
   * @param port the port this NERServer listens on.
   * @param asc The classifier which will do the tagging
   * @param charset The character set for encoding Strings over the socket stream, e.g., "utf-8"
   * @throws IOException If there is a problem creating a ServerSocket
   */
  public NERServer(int port, AbstractSequenceClassifier asc, String charset) throws IOException {
    ner = asc;
    listener = new ServerSocket(port);
    this.charset = charset;
  }

  //// Public Methods

  /**
   * Runs this named entity recognizer server.
   */
  @SuppressWarnings({"InfiniteLoopStatement", "ConstantConditions", "null"})
  public void run() {
    Socket client = null;
    while (true) {
      try {
        client = listener.accept();
        if (DEBUG) {
          System.err.print("Accepted request from ");
          System.err.println(client.getInetAddress().getHostName());
        }
        new Session(client);
      } catch (Exception e1) {
        System.err.println("NERServer: couldn't accept");
        e1.printStackTrace(System.err);
        try {
          client.close();
        } catch (Exception e2) {
          System.err.println("NERServer: couldn't close client");
          e2.printStackTrace(System.err);
        }
      }
    }
  }


  //// Inner Classes

  /**
   * A single user session, accepting one request, processing it, and
   * sending back the results.
   */
  private class Session extends Thread {

  //// Instance Fields

    /**
     * The socket to the client.
     */
    private final Socket client;

    /**
     * The input stream from the client.
     */
    private final BufferedReader in;

    /**
     * The output stream to the client.
     */
    private PrintWriter out;


    //// Constructors

    private Session(Socket socket) throws IOException {
      client = socket;
      in = new BufferedReader(new InputStreamReader(client.getInputStream(), charset));
      out = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), charset));
      start();
    }


    //// Public Methods

    /**
     * Runs this session by reading a string, tagging it, and writing
     * back the result.  The input should be a single line (no embedded
     * newlines), which represents a whole sentence or document.
     */
    @Override
    public void run() {
      if (DEBUG) {System.err.println("Created new session");}
      String input = null;
      try {
        // TODO: why not allow for multiple lines of input?
        input = in.readLine();
        if (DEBUG) {
          EncodingPrintWriter.err.println("Receiving: \"" + input + '\"', charset);
        }
      } catch (IOException e) {
        System.err.println("NERServer:Session: couldn't read input");
        e.printStackTrace(System.err);
      } catch (NullPointerException npe) {
        System.err.println("NERServer:Session: connection closed by peer");
        npe.printStackTrace(System.err);
      }
      try {
        if (! (input == null)) {
          String output = 
            ner.classifyToString(input, ner.flags.outputFormat, 
                                 !"slashTags".equals(ner.flags.outputFormat));
          
          if (DEBUG) {
            EncodingPrintWriter.err.println("Sending: \"" + output + '\"', charset);
          }
          out.print(output);
          out.flush();
        }
      } catch (RuntimeException e) {
        // ah well, guess they won't be hearing back from us after all
      }
      close();
    }

    /**
     * Terminates this session gracefully.
     */
    private void close() {
      try {
        in.close();
        out.close();
        client.close();
      } catch (Exception e) {
        System.err.println("NERServer:Session: can't close session");
        e.printStackTrace(System.err);
      }
    }

  } // end class Session

  /** This example sends material to the NER server one line at a time.
   *  Each line should be at least a whole sentence, or can be a whole
   *  document.
   */
  public static class NERClient {

    private NERClient() {}

    public static void communicateWithNERServer(String host, int port,
                                                String charset) 
      throws IOException
    {
      System.out.println("Input some text and press RETURN to NER tag it, " +
                         " or just RETURN to finish.");

      BufferedReader stdIn = 
        new BufferedReader(new InputStreamReader(System.in, charset));
      communicateWithNERServer(host, port, charset, stdIn, null, true);
      stdIn.close();
    }

    public static void communicateWithNERServer(String host, int port, 
                                                String charset, 
                                                BufferedReader input,
                                                BufferedWriter output,
                                                boolean closeOnBlank)
      throws IOException 
    {
      if (host == null) {
        host = "localhost";
      }

      for (String userInput; (userInput = input.readLine()) != null; ) {
        if (userInput.matches("\\n?")) {
          if (closeOnBlank) {
            break;
          } else {
            continue;
          }
        }
        try {
          // TODO: why not keep the same socket for multiple lines?
          Socket socket = new Socket(host, port);
          PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), charset), true);
          BufferedReader in = new BufferedReader(new InputStreamReader(
                  socket.getInputStream(), charset));
          // send material to NER to socket
          out.println(userInput);
          // Print the results of NER
          String result;
          while ((result = in.readLine()) != null) {
            if (output == null) {
              EncodingPrintWriter.out.println(result, charset);
            } else {
              output.write(result);
              output.newLine();
            }
          }
          in.close();
          socket.close();
        } catch (UnknownHostException e) {
          System.err.print("Cannot find host: ");
          System.err.println(host);
          return;
        } catch (IOException e) {
          System.err.print("I/O error in the connection to: ");
          System.err.println(host);
          return;
        }
      }
    }
  } // end static class NERClient


  private static final String USAGE = "Usage: NERServer [-loadClassifier file|-loadJarClassifier resource|-client] -port portNumber";

  /**
   * Starts this server on the specified port.  The classifier used can be
   * either a default one stored in the jar file from which this code is
   * invoked or you can specify it as a filename or as another classifier
   * resource name, which must correspond to the name of a resource in the
   * /classifiers/ directory of the jar file.
   * <p>
   * Usage: <code>java edu.stanford.nlp.ie.NERServer [-loadClassifier file|-loadJarClassifier resource|-client] -port portNumber</code>
   *
   * @param args Command-line arguments (described above)
   * @throws Exception If file or Java class problems with serialized classifier
   */
  @SuppressWarnings({"StringEqualsEmptyString"})
  public static void main (String[] args) throws Exception {
    Properties props = StringUtils.argsToProperties(args);
    String loadFile = props.getProperty("loadClassifier");
    String loadJarFile = props.getProperty("loadJarClassifier");
    String client = props.getProperty("client");
    String portStr = props.getProperty("port");
    props.remove("port"); // so later code doesn't complain
    if (portStr == null || portStr.equals("")) {
      System.err.println(USAGE);
      return;
    }
    String charset = "utf-8";
    String encoding = props.getProperty("encoding");
    if (encoding != null && ! "".equals(encoding)) {
      charset = encoding;
    }
    int port;
    try {
      port = Integer.parseInt(portStr);
    } catch (NumberFormatException e) {
      System.err.println("Non-numerical port");
      System.err.println(USAGE);
      return;
    }
    // default output format for if no output format is specified
    if (props.getProperty("outputFormat") == null) {
      props.setProperty("outputFormat", "slashTags");
    }

    if (client != null && ! client.equals("")) {
      // run a test client for illustration/testing
      String host = props.getProperty("host");
      NERClient.communicateWithNERServer(host, port, charset);
    } else {
      AbstractSequenceClassifier asc;
      if (loadFile != null && ! loadFile.equals("")) {
        asc = CRFClassifier.getClassifier(loadFile, props);
      } else if (loadJarFile != null && ! loadJarFile.equals("")) {
        asc = CRFClassifier.getJarClassifier(loadJarFile, props);
      } else {
        asc = CRFClassifier.getDefaultClassifier(props);
      }

      new NERServer(port, asc, charset).run();
    }
  }

}
