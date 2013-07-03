#!/bin/sh

java -mx2g -Dfile.encoding=utf-8 -cp "dist/CRF.jar" edu.stanford.nlp.ie.crf.CRFClassifier -prop lv-ner-train.prop