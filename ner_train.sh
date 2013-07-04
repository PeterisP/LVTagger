#!/bin/sh

java -mx2g -Dfile.encoding=utf-8 -cp "dist/CRF.jar" edu.stanford.nlp.ie.crf.CRFClassifier -prop lv-ner-train.prop

java -mx1g -Dfile.encoding=utf-8 -cp "dist/CRF.jar" edu.stanford.nlp.ie.crf.CRFClassifier -prop lv-ner-tag.prop > "ner_train_comparison.txt"