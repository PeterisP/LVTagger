#!/bin/sh

cd $(dirname $0)
java -Xmx2g -Dfile.encoding=utf-8 -cp "dist/CRF.jar" lv.lumii.ner.NerPipe -prop lv-ner-tagger.prop $*
exit $?

