#!/bin/bash

cd $(dirname $0)
java -Xmx12G -Dfile.encoding=UTF8 -cp dist/CRF.jar:dist/morphology.jar:dist/transliterator.jar:lib/json-simple-1.1.1.jar lv.lumii.morphotagger.MorphoCRF -train $*
