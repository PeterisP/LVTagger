#!/bin/sh
scriptdir=`pwd`
jar="/Users/pet/Documents/java/PaikensNER/stanford-ner.jar"

echo "Training data..."

java -mx4g -Dfile.encoding=utf-8 -cp "$jar" edu.stanford.nlp.ie.crf.CRFClassifier -prop lv-PP.prop

echo "Converting devtest data..."

java -mx1g -Dfile.encoding=utf-8 -cp "$jar" edu.stanford.nlp.ie.crf.CRFClassifier -prop lv-sample-test.prop > "$datadir/morphocrf.tagged.txt"

echo "Morpho CRF one"