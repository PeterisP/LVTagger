#!/bin/sh
jar="/Users/pet/Documents/java/PaikensNER/stanford-ner.jar"

echo "Converting devtest data..."
java -mx1g -Dfile.encoding=utf-8 -cp "$jar" edu.stanford.nlp.ie.crf.CRFClassifier -prop lv-sample-test.prop > "morphocrf.tagged.txt"
