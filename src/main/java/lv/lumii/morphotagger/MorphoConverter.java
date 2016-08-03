package lv.lumii.morphotagger;

import lv.semti.morphology.analyzer.Analyzer;
import lv.semti.morphology.analyzer.MarkupConverter;
import lv.semti.morphology.analyzer.Word;
import lv.semti.morphology.analyzer.Wordform;
import lv.semti.morphology.attributes.AttributeValues;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.*;

/**
 * Created by pet on 2016-05-30.
 */
public class MorphoConverter {
    private static Analyzer analyzer;

    public static void main(String[] args) throws Exception{
        // Extend train/test files with all the morphological analysis options
        analyzer = new Analyzer(false);

//        convert_tf_json("MorphoCRF/test.txt",  "/Users/pet/Documents/DNN/pp_tf/data/test.json");
        convert_tf_json("MorphoCRF/dev.txt",  "/Users/pet/Documents/DNN/pp_tf/data/dev.sent.json", true);
        convert_tf_json("MorphoCRF/train.txt", "/Users/pet/Documents/DNN/pp_tf/data/train.sent.json", true);

//        convert_pdonald("MorphoCRF/2014 gada dati/test.txt", "MorphoCRF/2014 gada dati/test2014.analyzed.txt");
//        convert_pdonald("MorphoCRF/2014 gada dati/train.txt", "MorphoCRF/2014 gada dati/train2014.analyzed.txt");
//        convert_pdonald("MorphoCRF/test.txt", "MorphoCRF/2014 gada dati/test2016.analyzed.txt");
//        convert_pdonald("MorphoCRF/dev.txt", "MorphoCRF/2014 gada dati/dev2016.analyzed.txt");
//        convert_pdonald("MorphoCRF/train.txt", "MorphoCRF/2014 gada dati/train2016.analyzed.txt");
    }

    public static void convert_tf_json(String filename_in, String filename_out, boolean sentence_split) throws IOException {
        BufferedReader in = new BufferedReader(new FileReader(filename_in));
        String input;
        JSONArray document = new JSONArray();
        JSONArray sentence = null;
        int sentences = 0;
        while ((input = in.readLine()) != null) {
            if (input.startsWith("<") || input.isEmpty()) {
                if (sentence_split) {
                    if (input.equalsIgnoreCase("<s>")) {
                        assert (sentence == null);
                        sentence = new JSONArray();
                    } else if (input.equalsIgnoreCase("</s>")) {
                        assert (sentence != null);
                        assert (!sentence.isEmpty());
                        document.add(sentence);
                        sentences++;
                        sentence = null;
                    } else
                        assert (input.isEmpty()); // Pārbaudam, vai nav kādi sveši tagi ieklīduši, par kuriem jādomā ko darīt
                }
            } else {
                String[] fields = input.split("\t");
                String wordform = fields[0];
                String gold_tag = fields[1];
                String gold_lemma = fields[2];

                JSONObject token = new JSONObject();
                token.put("wordform", wordform);
                token.put("gold_tag", gold_tag);
                token.put("gold_lemma", gold_lemma);

                AttributeValues answerAV = MarkupConverter.fromKamolsMarkup(gold_tag);
                answerAV.removeNonlexicalAttributes();
                String gold_tag_simple = MarkupConverter.toKamolsMarkup(answerAV);
                token.put("gold_tag_simple", gold_tag_simple);

                JSONArray options = new JSONArray();
                Word w = analyzer.analyze(wordform);
                for (Wordform wf : w.wordforms) {
                    wf.removeNonlexicalAttributes();
                    String tag = wf.getTag();
                    options.add(tag);
                }
                token.put("options", options);
                if (sentence_split)
                    sentence.add(token);
                else
                    document.add(token);
            }
        }
        in.close();
        try (FileWriter out = new FileWriter(filename_out)) {
            document.writeJSONString(out);
            System.out.printf("Converted %d sentences to %s", sentences, filename_out);
        }
    }


    public static void convert_pdonald(String filename_in, String filename_out) throws IOException {
        BufferedReader in = new BufferedReader(new FileReader(filename_in));
        PrintWriter out = new PrintWriter(filename_out, "UTF-8");
        String s;
        while ((s = in.readLine()) != null) {
            if (s.startsWith("<") || s.isEmpty())
                out.println(s);
            else {
                String[] fields = s.split("\t");
                String token = fields[0];
                String correct_tag = fields[1];
                StringBuilder sb = new StringBuilder();
                sb.append(token);
                sb.append("\t");
                sb.append(correct_tag);
                Word w = analyzer.analyze(token);
                for (Wordform wf : w.wordforms) {
                    String tag = wf.getTag();
//                    if (tag.equalsIgnoreCase(correct_tag))
//                        continue;
                    sb.append("\t");
                    sb.append(tag);
                }
                out.println(sb);
            }
        }
        in.close();
        out.close();
    }

}
