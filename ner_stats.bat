@echo off

java -mx2g -Dfile.encoding=utf-8 -cp "dist/CRF.jar" lv.lumii.ner.analysis.ErrorAnalysis -nerFile ner_train_comparison.txt %*
