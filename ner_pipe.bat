@echo off

java -mx2g -Dfile.encoding=utf-8 -cp "dist/CRF.jar" lv.lumii.ner.NerPipe -prop lv-ner-tagger.prop %*
