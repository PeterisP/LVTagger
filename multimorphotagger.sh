#!/bin/bash

cd $(dirname $0)
java -mx1200m -cp "target/*" lv.lumii.morphotagger.MultithreadingMorphoPipe $*
# /Library/Java/JavaVirtualMachines/jdk1.8.0_25.jdk/Contents/Home/bin/java -Xmx2G -cp dist/CRF.jar:dist/morphology.jar:dist/transliterator.jar:lib/json-simple-1.1.1.jar lv.lumii.morphotagger.MorphoPipe $*
