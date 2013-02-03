#!/bin/sh

java -mx1g -Dfile.encoding=utf-8 -cp "dist/CRF.jar" edu.stanford.nlp.ie.crf.CRFClassifier -prop lv-ner-tag.prop > "test_file.tagged.txt"