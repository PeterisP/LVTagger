// NERGUI -- a GUI for a probabilistic (CRF) sequence model for NER.
// Copyright (c) 2002-2008 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    Support/Questions: java-nlp-user@lists.stanford.edu
//    Licensing: java-nlp-support@lists.stanford.edu
//    http://nlp.stanford.edu/downloads/crf-classifier.shtml

package edu.stanford.nlp.ie.crf;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.util.StringUtils;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A GUI for Named Entity sequence classifiers.
 *  This version only supports the CRF.
 *
 *  @author Jenny Finkel
 *  @author Christopher Manning
 */
public class NERGUI {

  private AbstractSequenceClassifier classifier;

  private JFrame frame;
  private JEditorPane editorPane;
  private JToolBar tagPanel;
  private static int HEIGHT = 600;
  private static int WIDTH = 650;
  private Map<String, Color> tagToColorMap;
  private JFileChooser fileChooser = new JFileChooser();
  private MutableAttributeSet defaultAttrSet = new SimpleAttributeSet();
  private ActionListener actor = new ActionPerformer();
  private File loadedFile;
  private String taggedContents = null;
  private String htmlContents = null;

  private JMenuItem saveUntagged = null;
  private JMenuItem saveTaggedAs = null;

  private JButton extractButton = null;
  private JMenuItem extract = null;

  private void createAndShowGUI() {
    //Make sure we have nice window decorations.
    JFrame.setDefaultLookAndFeelDecorated(true);

    //Create and set up the window.
    frame = new JFrame("Stanford Named Entity Recognizer");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.getContentPane().setLayout(new BorderLayout());
    frame.getContentPane().setPreferredSize(new Dimension(WIDTH, HEIGHT));

    frame.setJMenuBar(addMenuBar());

    buildTagPanel();
    buildContentPanel();
    buildExtractButton();
    extractButton.setEnabled(false);
    extract.setEnabled(false);

    //Display the window.
    frame.pack();
    frame.setVisible(true);
  }

  private JMenuBar addMenuBar() {
    JMenuBar menubar = new JMenuBar();

    JMenu fileMenu = new JMenu("File");
    menubar.add(fileMenu);

    JMenu editMenu = new JMenu("Edit");
    menubar.add(editMenu);

    JMenu classifierMenu = new JMenu("Classifier");
    menubar.add(classifierMenu);

    /**
     * FILE MENU
     */

    JMenuItem openFile = new JMenuItem("Open File");
    openFile.setMnemonic('O');
    openFile.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, java.awt.Event.CTRL_MASK));
    openFile.addActionListener(actor);
    fileMenu.add(openFile);

    JMenuItem loadURL = new JMenuItem("Load URL");
    loadURL.setMnemonic('L');
    loadURL.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_U, java.awt.Event.CTRL_MASK));
    loadURL.addActionListener(actor);
    fileMenu.add(loadURL);

    fileMenu.add(new JSeparator());

    saveUntagged = new JMenuItem("Save Untagged File");
    saveUntagged.setMnemonic('S');
    saveUntagged.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.Event.CTRL_MASK));
    saveUntagged.addActionListener(actor);
    saveUntagged.setEnabled(false);
    fileMenu.add(saveUntagged);

    JMenuItem saveUntaggedAs = new JMenuItem("Save Untagged File As ...");
    saveUntaggedAs.setMnemonic('U');
    saveUntaggedAs.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_U, java.awt.Event.CTRL_MASK));
    saveUntaggedAs.addActionListener(actor);
    fileMenu.add(saveUntaggedAs);

    saveTaggedAs = new JMenuItem("Save Tagged File As ...");
    saveTaggedAs.setMnemonic('T');
    saveTaggedAs.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, java.awt.Event.CTRL_MASK));
    saveTaggedAs.addActionListener(actor);
    saveTaggedAs.setEnabled(false);
    fileMenu.add(saveTaggedAs);

    fileMenu.add(new JSeparator());

    JMenuItem exit = new JMenuItem("Exit");
    exit.setMnemonic('x');
    exit.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Q, java.awt.Event.CTRL_MASK));
    exit.addActionListener(actor);
    fileMenu.add(exit);


    /**
     * EDIT MENU
     */

    JMenuItem cut = new JMenuItem("Cut");
    cut.setMnemonic('X');
    cut.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, java.awt.Event.CTRL_MASK));
    cut.addActionListener(actor);
    editMenu.add(cut);

    JMenuItem copy = new JMenuItem("Copy");
    copy.setMnemonic('C');
    copy.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, java.awt.Event.CTRL_MASK));
    copy.addActionListener(actor);
    editMenu.add(copy);

    JMenuItem paste = new JMenuItem("Paste");
    paste.setMnemonic('V');
    paste.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, java.awt.Event.CTRL_MASK));
    paste.addActionListener(actor);
    editMenu.add(paste);

    JMenuItem clear = new JMenuItem("Clear");
    clear.setMnemonic('C');
    clear.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L, java.awt.Event.CTRL_MASK));
    clear.addActionListener(actor);
    editMenu.add(clear);


    /**
     * CLASSIFIER MENU
     */

    JMenuItem loadCRF = new JMenuItem("Load CRF From File");
    loadCRF.setMnemonic('R');
    loadCRF.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, java.awt.Event.CTRL_MASK));
    loadCRF.addActionListener(actor);
    classifierMenu.add(loadCRF);

    JMenuItem loadDefaultCRF = new JMenuItem("Load Default CRF");
    loadDefaultCRF.setMnemonic('L');
    loadDefaultCRF.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L, java.awt.Event.CTRL_MASK));
    loadDefaultCRF.addActionListener(actor);
    classifierMenu.add(loadDefaultCRF);

    extract = new JMenuItem("Run NER");
    extract.setMnemonic('N');
    extract.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, java.awt.Event.CTRL_MASK));
    extract.addActionListener(actor);
    classifierMenu.add(extract);

    return menubar;
  }


  private class InputListener implements KeyListener {
    public void keyPressed(KeyEvent e) {

    }

    public void keyReleased(KeyEvent e) {

    }

    public void keyTyped(KeyEvent e) {
      saveTaggedAs.setEnabled(false);
    }

  }


  private class ActionPerformer implements ActionListener {

    public void actionPerformed(ActionEvent e) {
      String com = e.getActionCommand();

      if (com.equals("Open File")) {
        File file = getFile(true);
        if (file != null) {
          openFile(file);
        }
      } else if (com.equals("Load URL")) {
        String url = getURL();
        if (url != null) {
          openURL(url);
        }
      } else if (com.equals("Exit")) {
        exit();
      } else if (com.equals("Clear")) {
        clearDocument();
      } else if (com.equals("Cut")) {
        cutDocument();
      } else if (com.equals("Copy")) {
        copyDocument();
      } else if (com.equals("Paste")) {
        pasteDocument();
      } else if (com.equals("Load CRF From File")) {
        File file = getFile(true);
        if (file != null) {
          loadClassifier(file);
        }
      } else if (com.equals("Load Default CRF")) {
        loadClassifier(null);
      } else if (com.equals("Run NER")) {
        extract();
      } else if (com.equals("Save Untagged File")) {
        saveUntaggedContents(loadedFile);
      } else if (com.equals("Save Untagged File As ...")) {
        saveUntaggedContents(getFile(false));
      } else if (com.equals("Save Tagged File As ...")) {
        File f = getFile(false);
        if (f != null) {
          // i.e., they didn't cancel out of the file dialog
          saveFile (f, taggedContents);
        }
      } else {
        System.err.println("Unknown Action: "+e);
      }
    }
  }

  public File getFile(boolean open) {
    File file = null;
    int returnVal;
    if (open) {
      returnVal = fileChooser.showOpenDialog(frame);
    } else {
      returnVal = fileChooser.showSaveDialog(frame);
    }
    if(returnVal == JFileChooser.APPROVE_OPTION) {
      file = fileChooser.getSelectedFile();
      if (open && !checkFile(file)) { file = null; }
    }
    return file;
  }

  public void saveUntaggedContents(File file) {
    try {
      String contents;
      if (editorPane.getContentType().equals("text/html")) {
        contents = editorPane.getText();
      } else {
        Document doc = editorPane.getDocument();
        contents = doc.getText(0, doc.getLength());
      }
      saveFile(file, contents);
      saveUntagged.setEnabled(true);
      loadedFile = file;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void saveFile(File file, String contents) {
    StringUtils.printToFile(file, contents);
  }

  public String getURL() {
    String url = JOptionPane.showInputDialog(frame, "URL: ", "Load URL", JOptionPane.QUESTION_MESSAGE);
    return url;
  }

  public boolean checkFile(File file) {
    if (file.isFile()) {
      fileChooser.setCurrentDirectory(file.getParentFile());
      return true;
    } else {
      String message = "File Not Found: "+file.getAbsolutePath();
      displayError("File Not Found Error", message);
      return false;
    }
  }

  public void displayError(String title, String message) {
    JOptionPane.showMessageDialog(frame, message, title, JOptionPane.ERROR_MESSAGE);
  }

  /** Load a classifier from a file or the default.
   *  The default is specified by passing in <code>null</code>.
   */
  public void loadClassifier(File file) {
    try {
      if (file != null) {
        classifier = CRFClassifier.getClassifier(file);
      } else {
        // default classifier in jar
        classifier = CRFClassifier.getDefaultClassifier();
      }
    } catch (Throwable e) {
      // we catch Throwable, since we'd also like to be able to get an
      // OutOfMemoryError
      String message;
      if (file != null) {
        message = "Error loading CRF: " + file.getAbsolutePath();
      } else {
        message = "Error loading default CRF";
      }
      System.err.println(message);
      String title = "CRF Load Error";
      String msg = e.toString();
      if (msg != null) {
        message += "\n" + msg;
      }
      displayError(title, message);
      return;
    }
    removeTags();
    buildTagPanel();
    // buildExtractButton();
    extractButton.setEnabled(true);
    extract.setEnabled(true);
  }

  public void openFile(File file) {
    openURL(file.toURI().toString());
    loadedFile = file;
    saveUntagged.setEnabled(true);
  }

  public void openURL(String url) {
    try {
      editorPane.setPage(url);
    } catch (Exception e) {
      System.err.println("Error loading |" + url + "|");
      e.printStackTrace();
      displayError("Error Loading URL " + url, "Message: " + e.toString());
      return;
    }
    loadedFile = null;
    String text = editorPane.getText();
    taggedContents = null;
    if (!editorPane.getContentType().equals("text/html")) {
      editorPane.setContentType("text/rtf");
      Document doc = editorPane.getDocument();
      try {
        doc.insertString(0, text, defaultAttrSet);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      editorPane.revalidate();
      editorPane.repaint();
      editorPane.setEditable(true);
      htmlContents = null;
    } else {
      editorPane.setEditable(false);
      htmlContents = editorPane.getText();
    }

    saveUntagged.setEnabled(false);
    saveTaggedAs.setEnabled(false);
  }

  private void removeTags() {
    if (editorPane.getContentType().equals("text/html")) {
      if (htmlContents != null) {
        editorPane.setText(htmlContents);
      }
      editorPane.revalidate();
      editorPane.repaint();
    } else {
      DefaultStyledDocument doc = (DefaultStyledDocument)editorPane.getDocument();
      SimpleAttributeSet attr = new SimpleAttributeSet();
      StyleConstants.setForeground(attr, Color.BLACK);
      StyleConstants.setBackground(attr, Color.WHITE);
      doc.setCharacterAttributes(0, doc.getLength(), attr, false);
    }
    saveTaggedAs.setEnabled(false);
  }


  private void extract() {
    System.err.println("content type: "+editorPane.getContentType());
    if (!editorPane.getContentType().equals("text/html")) {

      DefaultStyledDocument doc = (DefaultStyledDocument)editorPane.getDocument();
      String text = null;
      try {
        text = doc.getText(0, doc.getLength());
      } catch (Exception e) {
        e.printStackTrace();
      }
      String labeledText = classifier.classifyWithInlineXML(text);
      taggedContents = labeledText;

      Set<String> tags = classifier.labels();
      String background = classifier.backgroundSymbol();
      String tagPattern = "";
      for (String tag : tags) {
        if (background.equals(tag)) { continue; }
        if (tagPattern.length() > 0) { tagPattern += "|"; }
        tagPattern += tag;
      }

      Pattern startPattern = Pattern.compile("<("+tagPattern+")>");
      Pattern endPattern = Pattern.compile("</("+tagPattern+")>");

      String finalText = labeledText;

      Matcher m = startPattern.matcher(finalText);
      while (m.find()) {
        int start = m.start();
        finalText = m.replaceFirst("");
        m = endPattern.matcher(finalText);
        if (m.find()) {
          int end = m.start();
          String tag = m.group(1);
          finalText = m.replaceFirst("");
          AttributeSet attSet = getAttributeSet(tag);
          try {
            String entity = finalText.substring(start, end);
            doc.setCharacterAttributes(start, entity.length(), attSet, false);
          } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(-1);
          }
          System.err.println(tag+": "+ finalText.substring(start, end));
        } else {
          // print error message
        }
        m = startPattern.matcher(finalText);
      }
      editorPane.revalidate();
      editorPane.repaint();
    } else {
      String untaggedContents = editorPane.getText();
      if (untaggedContents == null) { untaggedContents = ""; }
      taggedContents = classifier.classifyWithInlineXML(untaggedContents);

      Set<String> tags = classifier.labels();
      String background = classifier.backgroundSymbol();
      String tagPattern = "";
      for (String tag : tags) {
        if (background.equals(tag)) { continue; }
        if (tagPattern.length() > 0) { tagPattern += "|"; }
        tagPattern += tag;
      }

      Pattern startPattern = Pattern.compile("<("+tagPattern+")>");
      Pattern endPattern = Pattern.compile("</("+tagPattern+")>");

      String finalText = taggedContents;

      Matcher m = startPattern.matcher(finalText);
      while (m.find()) {
        String tag = m.group(1);
        String color = colorToHTML(tagToColorMap.get(tag));
        String newTag = "<span style=\"background-color: "+color+"; color: white\">";
        finalText = m.replaceFirst(newTag);
        int start = m.start()+newTag.length();
        Matcher m1 = endPattern.matcher(finalText);
        m1.find(m.end());
        String entity = finalText.substring(start, m1.start());
        System.err.println(tag+": "+ entity);
        finalText = m1.replaceFirst("</span>");
        m = startPattern.matcher(finalText);
      }
      // System.out.println(finalText);
      editorPane.setText(finalText);
      editorPane.revalidate();
      editorPane.repaint();

      // System.err.println(finalText);
    }
    saveTaggedAs.setEnabled(true);
  }

  private AttributeSet getAttributeSet(String tag) {
    MutableAttributeSet attr = new SimpleAttributeSet();
    Color color = tagToColorMap.get(tag);
    StyleConstants.setBackground(attr, color);
    StyleConstants.setForeground(attr, Color.WHITE);
    return attr;
  }

  public void clearDocument() {
    editorPane.setContentType("text/rtf");
    Document doc = new DefaultStyledDocument();
    editorPane.setDocument(doc);
    //    defaultAttrSet = ((StyledEditorKit)editorPane.getEditorKit()).getInputAttributes();
    //    StyleConstants.setFontFamily(defaultAttrSet, "Lucinda Sans Unicode");
    System.err.println("attr: "+defaultAttrSet);

    try {
      doc.insertString(0, " ", defaultAttrSet);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }

    editorPane.setEditable(true);
    editorPane.revalidate();
    editorPane.repaint();

    saveUntagged.setEnabled(false);
    saveTaggedAs.setEnabled(false);

    taggedContents = null;
    htmlContents = null;
    loadedFile = null;

  }

  public void cutDocument() {
    editorPane.cut();
    saveTaggedAs.setEnabled(false);
  }

  public void copyDocument() {
    editorPane.copy();
  }

  public void pasteDocument() {
    editorPane.paste();
    saveTaggedAs.setEnabled(false);
  }



  public void exit() {
    // ask if they're sure?
    System.exit(-1);
  }

  private String initText = "In bringing his distinct vision to the Western genre, writer-director Jim Jarmusch has created a quasi-mystical avant-garde drama that remains a deeply spiritual viewing experience. After losing his parents and fianc\u00E9e, a Cleveland accountant named William Blake (a remarkable Johnny Depp) spends all his money and takes a train to the frontier town of Machine in order to work at a factory. Upon arriving in Machine, he is denied his expected job and finds himself a fugitive after murdering a man in self-defense. Wounded and helpless, Blake is befriended by Nobody (Gary Farmer), a wandering Native American who considers him to be a ghostly manifestation of the famous poet. Nobody aids Blake in his flight from three bumbling bounty hunters, preparing him for his final journey--a return to the world of the spirits.";
  //  private String initText = "In";

  private void buildContentPanel() {

    editorPane = new JEditorPane ();
    editorPane.setContentType("text/rtf");
    editorPane.addKeyListener(new InputListener());

    //    defaultAttrSet = ((StyledEditorKit)editorPane.getEditorKit()).getInputAttributes();
    StyleConstants.setFontFamily(defaultAttrSet, "Lucida Sans");

    Document doc = new DefaultStyledDocument();
    editorPane.setDocument(doc);
    try {
      doc.insertString(0, initText, defaultAttrSet);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }

    JScrollPane scrollPane = new JScrollPane(editorPane);
    frame.getContentPane().add(scrollPane, BorderLayout.CENTER);

    editorPane.setEditable(true);
  }


  public static String colorToHTML(Color color) {
    String r = Integer.toHexString(color.getRed());
    if (r.length() == 0) { r = "00"; }
    else if (r.length() == 1) { r = "0" + r; }
    else if (r.length() > 2) { throw new IllegalArgumentException("invalid hex color for red"+r); }

    String g = Integer.toHexString(color.getGreen());
    if (g.length() == 0) { g = "00"; }
    else if (g.length() == 1) { g = "0" + g; }
    else if (g.length() > 2) { throw new IllegalArgumentException("invalid hex color for green"+g); }

    String b = Integer.toHexString(color.getBlue());
    if (b.length() == 0) { b = "00"; }
    else if (b.length() == 1) { b = "0" + b; }
    else if (b.length() > 2) { throw new IllegalArgumentException("invalid hex color for blue"+b); }

    return "#"+r+g+b;
  }

  static class ColorIcon implements Icon {
    Color color;

    public ColorIcon(Color c) {
      color = c;
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
      g.setColor(color);
      g.fillRect(x, y, getIconWidth(), getIconHeight());
    }

    public int getIconWidth() {
      return 10;
    }

    public int getIconHeight() {
      return 10;
    }
  }

  private void buildExtractButton() {
    if (extractButton == null) {
      JPanel buttonPanel = new JPanel();
      extractButton = new JButton("Run NER");
      buttonPanel.add(extractButton);
      frame.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
      extractButton.addActionListener(actor);
    }
  }

  private void buildTagPanel() {

    if (tagPanel == null) {
      tagPanel = new JToolBar(SwingConstants.VERTICAL);
      tagPanel.setFloatable(false);
      frame.getContentPane().add(tagPanel, BorderLayout.EAST);
    } else {
      tagPanel.removeAll();
    }

    if (classifier != null) {

      makeTagMaps();

      Set<String> tags = classifier.labels();
      String backgroundSymbol = classifier.backgroundSymbol();

      for (String tag : tags) {
        if (backgroundSymbol.equals(tag)) { continue; }
        Color color = tagToColorMap.get(tag);
        JButton b = new JButton(tag, new ColorIcon(color));
        tagPanel.add(b);
      }
    }
    tagPanel.revalidate();
    tagPanel.repaint();
  }

  private void makeTagMaps() {
    Set<String> tags = classifier.labels();
    String backgroundSymbol = classifier.backgroundSymbol();
    tagToColorMap = makeTagToColorMap(tags, backgroundSymbol);
  }

  public static Map<String, Color> makeTagToColorMap(Set<String> tags, 
                                                     String backgroundSymbol) {
    int numColors = tags.size() - 1;
    Color[] colors = getNColors(numColors);
    Map<String, Color> result = new HashMap<String, Color>();

    int i = 0;
    for (String tag : tags) {
      if (backgroundSymbol.equals(tag)) { continue; }
      if (result.get(tag) != null) { continue; }
      result.put(tag, colors[i++]);
    }
    return result;
  }


  private static Color[] basicColors = new Color[]{new Color(204, 102, 0),
                                                   new Color(102, 0, 102),
                                                   new Color(204, 0, 102),
                                                   new Color(153, 0, 0),
                                                   new Color(153, 0, 204),
                                                   new Color(255, 102, 0),
                                                   new Color(255, 102, 153),
                                                   new Color(204, 152, 255),
                                                   new Color(102, 102, 255),
                                                   new Color(153, 102, 0),
                                                   new Color(51, 102, 51),
                                                   new Color(0, 102, 255)};

//   private static Color[] basicColors = new Color[]{new Color(153, 102, 153),
//                                                    new Color(102, 153, 153),
//                                                    new Color(153, 153, 102),
//                                                    new Color(102, 102, 102),
//                                                    new Color(102, 153, 102),
//                                                    new Color(153, 102, 102),
//                                                    new Color(204, 153, 51),
//                                                    new Color(204, 51, 102),
//                                                    new Color(255, 204, 0),
//                                                    new Color(153, 0, 255),
//                                                    new Color(204, 204, 204),
//                                                    new Color(0, 255, 153)};

//   private static Color[] basicColors = new Color[]{Color.BLUE,
//                                     Color.GREEN,
//                                     Color.RED,
//                                     Color.ORANGE,
//                                     Color.LIGHT_GRAY,
//                                     Color.CYAN,
//                                     Color.MAGENTA,
//                                     Color.YELLOW,
//                                     Color.RED,
//                                     Color.GRAY,
//                                     Color.PINK,
//                                     Color.DARK_GRAY};


  public static Color[] getNColors(int n) {
    Color[] colors = new Color[n];
    if (n <= basicColors.length) {
      System.arraycopy(basicColors, 0, colors, 0, n);
    } else {
      int s = 255 / (int)Math.ceil(Math.pow(n, (1.0 / 3.0)));
      int index = 0;
      OUTER: for (int i = 0; i < 256; i += s) {
        for (int j = 0; j < 256; j += s) {
          for (int k = 0; k < 256; k += s) {
            colors[index++] = new Color(i,j,k);
            if (index == n) { break OUTER; }
          }
        }
      }
    }
    return colors;
  }

  /** Run the GUI.  This program accepts no command-line arguments.
   *  Everything is entered into the GUI.
   */
  public static void main(String[] args) {
    //Schedule a job for the event-dispatching thread:
    //creating and showing this application's GUI.
    SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          NERGUI gui = new NERGUI();
          gui.createAndShowGUI();
        }
      });
  }
}
