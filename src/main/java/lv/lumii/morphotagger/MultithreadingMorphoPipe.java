/*******************************************************************************
 * Copyright 2012,2013,2014 Institute of Mathematics and Computer Science, University of Latvia
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
package lv.lumii.morphotagger;

import edu.stanford.nlp.ie.ner.CMMClassifier;
import edu.stanford.nlp.ling.CoreAnnotations.*;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.sequences.LVMorphologyReaderAndWriter;
import lv.semti.morphology.analyzer.Splitting;
import lv.semti.morphology.analyzer.Word;
import lv.semti.morphology.analyzer.Wordform;
import lv.semti.morphology.attributes.AttributeNames;
import lv.semti.morphology.attributes.AttributeValues;
import org.json.simple.JSONValue;

import java.io.*;
import java.lang.reflect.Array;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.PriorityBlockingQueue;

public class MultithreadingMorphoPipe {

    private static String morphoClassifierLocation = "models/lv-morpho-model.ser.gz"; //FIXME - make it configurable

    public static void main(String[] args) throws Exception {
        ProcessingParams params = new ProcessingParams(args);

        // TODO: Ja šeit ir faili, kurus spējam apstrādāt paralēli tad to darām, ja nē tad pielietojam veco procesu.
        if (params.inputType.equals(ProcessingParams.inputTypes.SENTENCE) || params.inputType.equals(ProcessingParams.inputTypes.PARAGRAPH)
                || params.inputType.equals(ProcessingParams.inputTypes.VERT)) {
            params.cmm = CMMClassifier.getClassifier(morphoClassifierLocation);

            PrintStream out = new PrintStream(System.out, true, "UTF8");
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, "UTF8"));

            Reader reader = new Reader(in);
            Writer writer = new Writer(out);


//            int processors = Runtime.getRuntime().availableProcessors();
//            processors = 4;

            ArrayList<Thread> threads = new ArrayList<>();
            // TODO: Te izmainīts processors -> params.processors, ja tas ir slikti noņem
            for(int i=0; i < params.processors; i++) {
                Thread t;
                // TODO: Te sadalās dažādos Thread tipos, varbūt drīzāk sadalīšanu pa input tipiem veikt iekš thread klases.
                if (params.inputType.equals(ProcessingParams.inputTypes.VERT)) {
                    t = new VERTWorkerThread(reader, writer, params);
                } else {
                    t = new PlainTextWorkerThread(reader, writer, params);
                }

                t.start();
                threads.add(t);
            }

            for(Thread t : threads)
                t.join();

            in.close();
            out.close();
        }
        else {
            MorphoPipe morphoPipe = new MorphoPipe();
            morphoPipe.mainPipe(params);
        }
    }
}


class ProcessingParams {
    // Šeit modificējam input Types sarakstu
    public enum inputTypes {SENTENCE, PARAGRAPH, VERT, CONLL, JSON};
    public enum outputTypes {JSON, TAB, VERT, MOSES, CONLL_X, XML, VISL_CG, lemmatizedText, lowercasedText, analyzerOptions};

    public String eol = System.getProperty("line.separator");
    public String field_separator = "\t";
    public String token_separator = eol;

    public boolean mini_tag = false;
    public boolean features = false;
    public boolean LETAfeatures = false;
    public inputTypes inputType = inputTypes.PARAGRAPH;
    public outputTypes outputType = outputTypes.CONLL_X;
    public int sentencelengthcap = 250;
    public boolean saveColumns = false;
    public boolean keepTags = false;
    public boolean saveCase = false; // for lemmatized text output format
    public boolean outputSeparators = false; // <s> for sentences, <p> for paragraphs
    public boolean whitespaceMarker = false;
    public boolean stopOnEmpty = true; // quit on empty line
    public CMMClassifier<CoreLabel> cmm;

    // Nezinu vai šis ir pareizi, bet nu redzēs
    public int processors = Runtime.getRuntime().availableProcessors();

    public ProcessingParams(String[] args) {
        for (int i=0; i<args.length; i++) {
            if (args[i].equalsIgnoreCase("-tab")) {  // one response line per each query line, tab-separated
                this.outputType = ProcessingParams.outputTypes.TAB;
                this.token_separator = "\t";
            }
            else if (args[i].equalsIgnoreCase("-vert")) { // one response line per token, tab-separated
                this.outputType = ProcessingParams.outputTypes.VERT;
            }
            else if (args[i].equalsIgnoreCase("-moses")) { // one response line per token, pipe-separated
                this.field_separator = "|";
                this.token_separator = " ";
                this.outputType = ProcessingParams.outputTypes.MOSES;
            }
            else if (args[i].equalsIgnoreCase("-stripped")) this.mini_tag = true; //remove nonlexical attributes
            else if (args[i].equalsIgnoreCase("-features")) this.features = true; //output training features
            else if (args[i].equalsIgnoreCase("-leta")) this.LETAfeatures = true; //output specific features for LETA semantic frame analysis

                // TODO: Šeit sākās input tipu modifikācijas

            else if (args[i].equalsIgnoreCase("-vertinput")) {
                this.inputType = ProcessingParams.inputTypes.VERT; //vertical input format as requested by Milos Jakubicek 2012.11.01
            }
            else if (args[i].equalsIgnoreCase("-paragraphs")) {
                this.inputType = ProcessingParams.inputTypes.PARAGRAPH;
                if (i+1 < args.length && !args[i+1].startsWith("-")) {
                    try {
                        this.sentencelengthcap = Integer.parseInt(args[i+1]);
                        System.err.printf("Sentence length capped to %d\n", this.sentencelengthcap);
                        i++;
                    } catch (Exception e) {
                        System.err.printf("Error when parsing command line param '%s %s'\n",args[i], args[i+1]);
                        System.err.println(e.getMessage());
                    }
                }
            }
            else if (args[i].equalsIgnoreCase("-conll-in")) this.inputType = ProcessingParams.inputTypes.CONLL;
            else if (args[i].equalsIgnoreCase("-json-in")) this.inputType = ProcessingParams.inputTypes.JSON;
//            else if (args[i].equalsIgnoreCase("-paragraphs")) {
//                params.inputType = ProcessingParams.inputTypes.PARAGRAPH;
//                if (i+1 < args.length && !args[i+1].startsWith("-")) {
//                    try {
//                        params.sentencelengthcap = Integer.parseInt(args[i+1]);
//                        System.err.printf("Sentence length capped to %d\n", params.sentencelengthcap);
//                        i++;
//                    } catch (Exception e) {
//                        System.err.printf("Error when parsing command line param '%s %s'\n",args[i], args[i+1]);
//                        System.err.println(e.getMessage());
//                    }
//                }
//            }
            else if (args[i].equalsIgnoreCase("-conll-x")) this.outputType = ProcessingParams.outputTypes.CONLL_X;
            else if (args[i].equalsIgnoreCase("-xml")) this.outputType = ProcessingParams.outputTypes.XML;
            else if (args[i].equalsIgnoreCase("-visl-cg")) this.outputType = ProcessingParams.outputTypes.VISL_CG;
            else if (args[i].equalsIgnoreCase("-lemmatized-text")) this.outputType = ProcessingParams.outputTypes.lemmatizedText;
            else if (args[i].equalsIgnoreCase("-lowercased-text")) this.outputType = ProcessingParams.outputTypes.lowercasedText;
            else if (args[i].equalsIgnoreCase("-analyzer")) {
                this.outputType = ProcessingParams.outputTypes.analyzerOptions;
                this.token_separator = "\t";
            }
            else if (args[i].equalsIgnoreCase("-saveColumns")) this.saveColumns = true; //save extra columns from conll input
            else if (args[i].equalsIgnoreCase("-unix-line-endings")) this.eol="\n";
            else if (args[i].equalsIgnoreCase("-keep-tags")) this.keepTags = true;
            else if (args[i].equalsIgnoreCase("-output-separators")) this.outputSeparators = true;
            else if (args[i].equalsIgnoreCase("-whitespace-marker")) this.whitespaceMarker = true;
            else if (args[i].equalsIgnoreCase("-allow-empty-lines")) this.stopOnEmpty = false;

            else if (args[i].equalsIgnoreCase("-h") || args[i].equalsIgnoreCase("--help") || args[i].equalsIgnoreCase("-?")) {
                System.out.println("LV morphological tagger");
                System.out.println("\nInput formats");
                System.out.println("\tDefault : platext UTF-8, one sentence per line, terminated by a blank line.");
                System.out.println("\t-paragraphs [lengthcap]: plain text UTF-8, each line will be split in sentences. In output, paragraph borders are noted by an extra blank line. If lengthcap parameter is provided, then sentence length will be limited to that, instead of the default of " + this.sentencelengthcap);
                System.out.println("\t-vertinput : one line per token, sentences separated by <s></s>. Any XML-style tags are echoed as-is. \n\t\tNB! sentences are retokenized, the number of tokens may be different.");
                System.out.println("\t-conll-in : CONLL shared task data format - one line per token, with tab-delimited columns, sentences separated by blank lines.");
                System.out.println("\t-json-in : one line per sentence, each line contains a single json array of strings-tokens.");
                System.out.println("\nOutput formats");
                System.out.println("\tDefault : JSON. Each sentence is returned as a list of dicts, each dict contains elements 'Word', 'Tag' and 'Lemma'.");
                System.out.println("\t-tab : one response line for each query line; tab-separated lists of word, tag and lemma.");
                System.out.println("\t-vert : one response line for each token; tab-separated lists of word, tag and lemma.");
                System.out.println("\t-moses : one response line for each token; pipe-separated lists of word, tag and lemma.");
                System.out.println("\t-conll-x : CONLL-X shared task data format - one line per token, with tab-delimited columns, sentences separated by blank lines.");
                System.out.println("\t-xml : one xml word per line");
                System.out.println("\t-visl-cg : output format for VISL constraint grammar tool");
                System.out.println("\t-lemmatized-text : output lowercase lemmatized text, each sentence in new row, tokens seperated by single space");
                System.out.println("\t-lowercased-text : output lowercased text, each sentence in new row, tokens seperated by single space");
                System.out.println("\t-analyzer : one response line for each token; word followed by a tab-separated list of undisambiguated morphological tag options");
                System.out.println("\nOther options:");
                System.out.println("\t-stripped : lexical/nonessential parts of the tag are replaced with '-' to reduce sparsity.");
                System.out.println("\t-features : in conll output, include the features that were used for training/tagging.");
                System.out.println("\t-leta : in conll output, include extra features used for semantic frame analysis.");
                System.out.println("\t-saveColumns : save extra columns from conll or vert input.");
                System.out.println("\t-unix-line-endings : use \\n line endings for output even on windows systems");
                System.out.println("\t-keep-tags : preserve lines that start with '<' to enable xml-style metadata");
                System.out.println("\t-output-separators : put <s></s> sentence markup and <p></p> paragraph markup");
                System.out.println("\t-whitespace-marker : put <g /> tags where the tokens did not have whitespace between them");
                System.out.println("\t-allow-empty-lines : do not quit on blank lines input (as per default)");
                System.out.flush();
                System.exit(0);
            }
            // Questionable processor parameter passing implementation:
            else if (args[i].split("=")[0].equalsIgnoreCase("-processors")) {
                try{
                    this.processors = Integer.parseInt(args[i].split("=")[1]);
                }
                catch (Exception e) {
                    System.err.printf("Please provide processor count in the form: -processors=4");
                }
            }
            // End of questionable code
            else {
                System.err.println("Unrecognized parameter: " + args[i]);
            }
        }

        System.err.printf("Input type : %s\nOutput type : %s\n", this.inputType.toString(), this.outputType.toString());
        if (inputType == inputTypes.VERT && keepTags) System.err.println("WARNING - keepTags and VERT input may interact badly");
    }
}

class Line implements Comparable<Line> {
    public final long number;
    public String line;
    public Line(long number, String line) {
        this.number = number;
        this.line = line;
    }

    public int compareTo(Line o) {
        return Long.compare(number, o.number);
    }
}


class WorkerThread extends Thread {
    final Reader reader;
    final Writer writer;
    final ProcessingParams params;

    public WorkerThread(Reader reader, Writer writer, ProcessingParams params) {
        this.reader = reader;
        this.writer = writer;
        this.params = params;
    }


    /**
     * Outputs the tagged sentence according to the outputType set in this class
     * @param sentence - actual tokens to be output
     */
    public StringBuilder outputSentence(List<CoreLabel> sentence) {
        StringBuilder output = new StringBuilder();
        if (params.outputSeparators)
            output.append("<s>\n");

        if (params.outputType != ProcessingParams.outputTypes.lowercasedText && params.outputType != ProcessingParams.outputTypes.analyzerOptions) { //FIXME - a separate flag would be better
            sentence = params.cmm.classify(sentence); // runs the actual morphotagging system
        }

        switch (params.outputType) {
            case JSON:
                output.append( output_JSON(sentence));
                output.append('\n');
                break;
            case CONLL_X:
                output.append( output_CONLL(sentence));
                output.append('\n');
                break;
            case XML:
                try {
                    output.append(output_XML(sentence));
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                break;
            case VISL_CG:
                output.append( output_VISL(sentence));
                output.append('\n');
                break;
            case lemmatizedText:
                output.append( output_lemmatized(sentence));
                output.append('\n');
                break;
            case lowercasedText:
                output.append( output_lowercased(sentence));
                output.append('\n');
                break;
            case analyzerOptions:
                output.append( output_analyzer(sentence));
                output.append('\n');
                break;
            default:
                output.append( output_separated(sentence));
                output.append('\n');
        }
        if (params.outputSeparators)
        {
            output.append("</s>\n");
        }
        return output;
    }

    private String output_JSON(List<CoreLabel> tokens) {
        LinkedList<String> tokenJSON = new LinkedList<String>();

        for (CoreLabel word : tokens) {
            String token = word.getString(TextAnnotation.class);
            if (token.contains("<s>")) continue;
            Word analysis = word.get(LVMorphologyAnalysis.class);
            Wordform maxwf = analysis.getMatchingWordform(word.getString(AnswerAnnotation.class), false);
            if (params.mini_tag) maxwf.removeNonlexicalAttributes();
            if (maxwf != null)
                tokenJSON.add(String.format("{\"Word\":\"%s\",\"Tag\":\"%s\",\"Lemma\":\"%s\"}", JSONValue.escape(token), JSONValue.escape(maxwf.getTag()), JSONValue.escape(maxwf.getValue(AttributeNames.i_Lemma))));
            else
                tokenJSON.add(String.format("{\"Word\":\"%s\",\"Tag\":\"-\",\"Lemma\":\"%s\"}", JSONValue.escape(token), JSONValue.escape(token)));
        }

        String s = formatJSON(tokenJSON).toString();

        return s;
    }

    private String output_XML(List<CoreLabel> tokens) throws IOException {
        StringWriter stringWriter = new StringWriter();
        PrintWriter w = new PrintWriter(stringWriter);
        for (CoreLabel word : tokens) {
            String token = word.getString(TextAnnotation.class);
            if (token.contains("<s>")) continue;
            Word analysis = word.get(LVMorphologyAnalysis.class);
            Wordform maxwf = analysis.getMatchingWordform(word.getString(AnswerAnnotation.class), false);
            if (params.mini_tag) maxwf.removeNonlexicalAttributes();
            maxwf.addAttribute("Tag", maxwf.getTag());
            maxwf.toXML(w);
//			if (maxwf != null)
//				tokenJSON.add(String.format("{\"Word\":\"%s\",\"Tag\":\"%s\",\"Lemma\":\"%s\"}", JSONValue.escape(token), JSONValue.escape(maxwf.getTag()), JSONValue.escape(maxwf.getValue(AttributeNames.i_Lemma))));
//			else
//				tokenJSON.add(String.format("{\"Word\":\"%s\",\"Tag\":\"-\",\"Lemma\":\"%s\"}", JSONValue.escape(token), JSONValue.escape(token)));
        }
        return stringWriter.toString();
    }

    private String output_CONLL(List<CoreLabel> tokens){
        CMMClassifier<CoreLabel> cmm = params.cmm;
        StringBuilder s = new StringBuilder();

        int counter = 1;
        for (CoreLabel word : tokens) {
            String token = word.getString(TextAnnotation.class);
            if (token.contains("<s>")) continue;
            token = token.replace(' ', '_');

            s.append(Integer.toString(counter));
            s.append('\t');
            s.append(token);
            s.append('\t');
            Word analysis = word.get(LVMorphologyAnalysis.class);
            Wordform mainwf = analysis.getMatchingWordform(word.getString(AnswerAnnotation.class), false);
            if (mainwf != null) {
                String lemma = mainwf.getValue(AttributeNames.i_Lemma);
                lemma = lemma.replace(' ', '_');
                String answer = word.getString(AnswerAnnotation.class);
                if (answer.trim().isEmpty()) answer = "_"; // no empty tag
                s.append(lemma);
                s.append('\t');
                s.append(answer);
                s.append('\t');
                s.append(mainwf.getTag());
                s.append('\t');

                // Feature atribūtu filtri
                if (params.mini_tag) mainwf.removeNonlexicalAttributes();
                if (params.LETAfeatures) {
                    addLETAfeatures(mainwf);
                    // mainwf.removeAttribute(AttributeNames.i_SourceLemma); FIXME - atvasinātiem vārdiem šis var būt svarīgs, atpriedekļotas lemmas..
                    mainwf.removeTechnicalAttributes();
                }

                s.append(mainwf.pipeDelimitedEntries()); // Pievienojam vārda fīčas

                if (params.features) { // visas fīčas, ko lietoja trenējot
                    Datum<String, String> d = cmm.makeDatum(tokens, counter, cmm.featureFactory);
                    for (String feature : d.asFeatures()) {
                        s.append(feature.substring(0, feature.length()-2).replace(' ', '_')); // noņeam trailing |C kas tām fīčām tur ir
                        s.append('|');
                    }
                }
                s.deleteCharAt(s.length()-1); // noņemam peedeejo | separatoru, kas ir lieks
                s.append('\t');

            } else {
                s.append(token);
                s.append("\t_\t_\t_\t");
            }
            if (params.saveColumns) {
                s.append(word.getString(ExtraColumnAnnotation.class));
            } else {
                String syntax = word.getString(ParentAnnotation.class);
                if (syntax != null) {
                    s.append(syntax);
                }
                else s.append("_\t_\t_\t_");
            }
            s.append(params.eol);
            counter++;
        }

        return s.toString();
    }

    private String output_lemmatized(List<CoreLabel> tokens){
        StringBuilder s = new StringBuilder();

        for (CoreLabel word : tokens) {
            String token = word.getString(TextAnnotation.class);
            if (token.contains("<s>")) continue;
            token = token.replace(' ', '_');

            Word analysis = word.get(LVMorphologyAnalysis.class);
            Wordform mainwf = analysis.getMatchingWordform(word.getString(AnswerAnnotation.class), false);
            if (mainwf != null && !token.isEmpty()) {
                String lemma = mainwf.getValue(AttributeNames.i_Lemma);
                if (params.saveCase && Character.isUpperCase(token.charAt(0))) lemma = lemma.substring(0,1).toUpperCase() + lemma.substring(1);
                if (!params.saveCase) lemma=lemma.toLowerCase();
                lemma = lemma.replace(' ', '_');
                s.append(lemma);
                s.append(' ');

            } else {
                System.err.println("Empty lemma");
            }
        }
        return s.toString().trim();
    }

    private String output_lowercased(List<CoreLabel> tokens){
        StringBuilder s = new StringBuilder();

        for (CoreLabel word : tokens) {
            String token = word.getString(TextAnnotation.class);
            if (token.contains("<s>")) continue;
            token = token.replace(' ', '_').toLowerCase();
            s.append(token);
            s.append(' ');
        }
        return s.toString().trim();
    }

    private String output_analyzer(List<CoreLabel> tokens){
        StringBuilder s = new StringBuilder();

        for (CoreLabel word : tokens) {
            String token = word.getString(TextAnnotation.class);
            if (token.contains("<s>")) continue;
            token = token.replace(' ', '_');
            s.append(token);
            Word analysis = word.get(LVMorphologyAnalysis.class);
            for (Wordform wf : analysis.wordforms) {
                s.append(params.token_separator);
                s.append(wf.getTag());
            }
        }
        return s.toString().trim();
    }

    private void addLETAfeatures(Wordform wf) {
        String lemma = wf.getValue(AttributeNames.i_Lemma);

        if (wf.isMatchingStrong(AttributeNames.i_PartOfSpeech, AttributeNames.i_Number)) {
            String numbercode = lemma.replaceAll("\\d", "0"); // uzskatam ka nav atšķirības starp skaitļiem ja ciparu skaits vienāds
            wf.addAttribute("LETA_lemma", numbercode);
        } else if (wf.isMatchingStrong(AttributeNames.i_CapitalLetters, AttributeNames.v_FirstUpper) && Dictionary.dict("surnames").contains(lemma))
            wf.addAttribute("LETA_lemma", "_surname_");
        else if (Dictionary.dict("vocations").contains(lemma))
            wf.addAttribute("LETA_lemma", "_vocation_");
        else if (Dictionary.dict("relations").contains(lemma))
            wf.addAttribute("LETA_lemma", "_relationship_");
        else if (Dictionary.dict("partijas").contains(lemma))
            wf.addAttribute("LETA_lemma", "_party_"); // TODO - nočekot kā visā procesā sanāk ar case-sensitivity, te tas ir svarīgi
        else if (Dictionary.dict("months").contains(lemma)) // TODO - te būtu jāčeko, lai personvārdi Marts un Jūlijs te neapēdas, ja ir ar lielo burtu ne teikuma sākumā
            wf.addAttribute("LETA_lemma", "_month_");
        else if (Dictionary.dict("common_lemmas").contains(lemma))
            wf.addAttribute("LETA_lemma", lemma);
        else wf.addAttribute("LETA_lemma", "_rare_");
    }

    // VISL CG format, as described in http://beta.visl.sdu.dk/cg3/chunked/streamformats.html#stream-vislcg
    private String output_VISL(List<CoreLabel> tokens) {
        StringBuilder s = new StringBuilder();

        for (CoreLabel word : tokens) {
            String token = word.getString(TextAnnotation.class);
            if (token.contains("<s>")) continue;

            token.replaceAll("\"", "\\\""); // VISL (seems to) require to escape quotes in their format. Possibly other escaping needs, not sure from their docs.

            s.append(String.format("\"<%s>\"\n", token)); // <"They"> from the example

            Word analysis = word.get(LVMorphologyAnalysis.class);
            Wordform maxwf = analysis.getMatchingWordform(word.getString(AnswerAnnotation.class), false);
            for (Wordform wf : analysis.wordforms) { // output the "cohort" in VISL-CG terms
                String lemma = wf.getValue(AttributeNames.i_Lemma);
                //Ad-hoc ... removing 'bookkeeping' attributes that seem useless for CG
                wf.removeTechnicalAttributes();
                wf.removeAttribute(AttributeNames.i_Lemma);
                wf.removeAttribute(AttributeNames.i_SourceLemma);

                lemma.replaceAll("\"", "\\\"");
                s.append(String.format("\t\"%s\" ", lemma)); // <"They"> from the example
                s.append(wf.getTag());
                s.append(" ");
                AttributeValues minimum = new AttributeValues(wf);
                minimum.removeNonlexicalAttributes();
                s.append(minimum.getTag());
                s.append(" ");
                for (Entry<String, String> entry : wf.entrySet()) { // visi attributevalue paariishi
                    String key = entry.getKey();
                    String value = entry.getValue();
                    // For attributes with distinctive value names (like parts of speech) skip the attribute name for readability in CG
                    if ((!key.equalsIgnoreCase(AttributeNames.i_PartOfSpeech) &&
                            !key.equalsIgnoreCase(AttributeNames.i_Case) &&
                            !key.equalsIgnoreCase(AttributeNames.i_Number) &&
                            !key.equalsIgnoreCase(AttributeNames.i_Gender) &&
                            !key.equalsIgnoreCase(AttributeNames.i_NounType) &&
                            !key.equalsIgnoreCase(AttributeNames.i_Izteiksme) &&
                            !key.equalsIgnoreCase(AttributeNames.i_VerbType) &&
                            !key.equalsIgnoreCase(AttributeNames.i_Laiks) &&
                            !key.equalsIgnoreCase(AttributeNames.i_Transitivity) &&
                            !key.equalsIgnoreCase(AttributeNames.i_Declension) &&
                            !key.equalsIgnoreCase(AttributeNames.i_Definiteness) &&
                            !key.equalsIgnoreCase(AttributeNames.i_Lokaamiiba) &&
                            !key.equalsIgnoreCase(AttributeNames.i_AdjectiveType) &&
                            !key.equalsIgnoreCase(AttributeNames.i_SaikljaTips) &&
                            !key.equalsIgnoreCase(AttributeNames.i_Uzbuuve) &&
                            !key.equalsIgnoreCase(AttributeNames.i_PieturziimesTips) &&
                            !key.equalsIgnoreCase(AttributeNames.i_Voice) &&
                            !key.equalsIgnoreCase(AttributeNames.i_VvTips)
                    ) || (value.equalsIgnoreCase(AttributeNames.v_NA) &&
                            !key.equalsIgnoreCase(AttributeNames.i_Anafora) &&
                            !key.equalsIgnoreCase(AttributeNames.i_Laiks)
                    )) {
                        s.append(key.replace(' ', '_'));
                        s.append('=');
                    }
                    s.append(value.replace(' ', '_'));
                    s.append(' ');
                }
                s.append(params.eol);
            }
        }

        s.append("\"<<s>>\"");

        return s.toString();
    }

    private String output_separated(List<CoreLabel> tokens){
        StringBuilder s = new StringBuilder();

        for (CoreLabel word : tokens) {
            String token = word.getString(TextAnnotation.class);
            if (token.contains("<s>") && !(params.inputType == ProcessingParams.inputTypes.VERT)) continue;
            if (token.startsWith("<") && !token.startsWith("<\t") && token.endsWith(">")) {
                if (s.length() != 0) s.append(params.token_separator);
                s.append(token);
                continue;
            }
            Word analysis = word.get(LVMorphologyAnalysis.class);
            Wordform mainwf = analysis.getMatchingWordform(word.getString(AnswerAnnotation.class), false);

            if (s.length()>0) s.append(params.token_separator);
            if (params.whitespaceMarker && mainwf.isMatchingStrong(AttributeNames.i_WhitespaceBefore, "")) {
                if (!mainwf.isMatchingStrong(AttributeNames.i_Offset, "0")) {
                    s.append("<g />");
                    s.append(params.token_separator);
                }
            } else if (mainwf.isMatchingStrong(AttributeNames.i_WhitespaceBefore, null)) {
                if (params.whitespaceMarker && analysis.hasAttribute(AttributeNames.i_WhitespaceBefore, "")) {
                    if (!mainwf.isMatchingStrong(AttributeNames.i_Offset, "0")) {
                        s.append("<g />");
                        s.append(params.token_separator);
                    }
                }
            }

            if (params.outputType == ProcessingParams.outputTypes.MOSES) token = token.replace(' ', '_');
            s.append(token);
            s.append(params.field_separator);

            if (mainwf != null) {
                if (params.mini_tag) mainwf.removeNonlexicalAttributes();
                s.append(mainwf.getTag());
                s.append(params.field_separator);
                String lemma = mainwf.getValue(AttributeNames.i_Lemma);
                if (params.outputType == ProcessingParams.outputTypes.MOSES) lemma = lemma.replace(' ', '_');
                s.append(lemma);

                if (params.outputType == ProcessingParams.outputTypes.VERT && params.saveColumns) {
                    String extraColumns = word.getString(ExtraColumnAnnotation.class);
                    if (extraColumns != "") {
                        s.append(params.field_separator);
                        s.append(extraColumns);
                    };
                }
            } else s.append(params.field_separator);
        }

        return s.toString();
    }

    private StringBuilder formatJSON(Collection<String> tags) {
        Iterator<String> i = tags.iterator();
        StringBuilder out = new StringBuilder("[");
        while (i.hasNext()) {
            out.append(i.next());
            if (i.hasNext()) out.append(", ");
        }
        out.append("]");
        return out;
    }
}

class PlainTextWorkerThread extends WorkerThread {

    public PlainTextWorkerThread(Reader reader, Writer writer, ProcessingParams params) {
        super(reader, writer, params);
    }

    public void run() {

        Line line;
        String s;
        while (true) {
            try {
                if ((line = reader.read()) == null) break;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            s = line.line;
            if (s.startsWith("<") && params.keepTags) {
                if (params.outputType != ProcessingParams.outputTypes.lemmatizedText && params.outputType != ProcessingParams.outputTypes.lowercasedText)
                    writer.write(line);
                else {
                    line.line = null;
                    writer.write(line);
                }
                continue;
            }

            if (s.length() == 0) {
                line.line = null;
                writer.write(line);
                continue;
            }

            line.line = processSentences(s.trim()).toString();
            writer.write(line);
        }
    }


    /**
     * Splits the text in sentences if needed, and forwards to outputSentance
     * @param text - actual tokens to be output
     */
    public StringBuilder processSentences(String text) {
        StringBuilder result = new StringBuilder();

        if (params.inputType == ProcessingParams.inputTypes.PARAGRAPH) { // split in multiple sentences
            if (params.outputSeparators) result.append("<p>\n");

            LinkedList<LinkedList<Word>> sentences = Splitting.tokenizeSentences(LVMorphologyReaderAndWriter.getAnalyzer(), text, params.sentencelengthcap);
            for (LinkedList<Word> sentence : sentences) {
                result.append(outputSentence(LVMorphologyReaderAndWriter.analyzeSentence2(sentence)));
            }

            if (params.outputSeparators) result.append("</p>");

        } else {
            result.append(outputSentence(LVMorphologyReaderAndWriter.analyzeSentence(text)));
        }

        return result;

    }
}


class VERTWorkerThread extends WorkerThread {
    // FIXME: Es ja godīgi pats nesaprotu līdz galam, ko daru.
    public VERTWorkerThread(Reader reader, Writer writer, ProcessingParams params) {
        super(reader, writer, params);
    }

    public void run() {
        try {
            processVERT();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void processVERT() throws IOException {
        Line line;
        String text;
        List<CoreLabel> sentence = new LinkedList<CoreLabel>();
        StringBuilder result = new StringBuilder();

        while (true) {
            try {
                if ((line = reader.readVERT()) == null) break;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            text = line.line;
            for (String s : text.split("\n")) {
                if (s.startsWith("</s>")) {
                    CoreLabel stop = new CoreLabel();
                    stop.set(TextAnnotation.class, "</s>");
                    sentence.add(stop);
                    result.append(outputSentence(LVMorphologyReaderAndWriter.analyzeLabels(sentence)));
                    line.line = result.toString().trim();
                    writer.write(line);

                    sentence = new LinkedList<CoreLabel>();
                    result = new StringBuilder();
                }
                else if (s.startsWith("<") && !s.startsWith("<\t") && s.length() > 1) {
                    CoreLabel tag = new CoreLabel();
                    tag.set(TextAnnotation.class, s.trim());
                    sentence.add(tag);
                } else {
                    String[] fields = s.split("\t", 4);
                    String token = fields[0];
                    String extraColumns = "";
                    if (fields.length == 4 && params.saveColumns) extraColumns = fields[3];

                    CoreLabel word = new CoreLabel();
                    word.set(TextAnnotation.class, token);
                    word.set(ExtraColumnAnnotation.class, extraColumns);
                    sentence.add(word);
                }
            }
            if (!sentence.isEmpty()) {
                result.append(outputSentence(LVMorphologyReaderAndWriter.analyzeLabels(sentence)));
                line.line = result.toString().trim();
                writer.write(line);
            }
        }
    }
}


class Reader {
    BufferedReader in;
    long line = 0;

    public Reader(BufferedReader in) {
        this.in = in;
    }

    public synchronized Line read() throws IOException {
        String s = in.readLine();
        if(s == null)
            return null;
        else
            return new Line(line++, s);
    }

    public synchronized Line readVERT() throws IOException {
        String s;
        String sentence = "";
        while (true) {
            s = in.readLine();
            if (s == null) {
                if (!sentence.isEmpty()) return new Line(line++, sentence);
                else return null;
            }
            else {
                sentence += s+"\n";
                if (s.startsWith("</s>")) {
                    return new Line(line++, sentence);
                }
            }
        }
    }
}


class Writer {
    long head = 0;
    PriorityBlockingQueue<Line> que;
    PrintStream out;

    public Writer(PrintStream out) {
        que = new PriorityBlockingQueue<>();
        this.out = out;
    }

    public synchronized void write(Line line) {
        if(line.number == head) {
            out.print(line.line);
            out.print('\n');
            head++;

            while (que.size() > 0 && que.peek().number == head) {
                Line next_line = que.peek();
                if(next_line.line != null) {
                    out.print(next_line.line);
                    out.print('\n');
                }
                que.poll();
                head++;
            }
        }
        else
            que.add(line);
    }
}