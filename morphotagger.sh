#!/bin/bash

cd $(dirname $0)
java -mx1200m -jar target/tagger-1.0.2-SNAPSHOT-jar-with-dependencies.jar $*
# /Library/Java/JavaVirtualMachines/jdk1.8.0_25.jdk/Contents/Home/bin/java -Xmx2G -cp dist/CRF.jar:dist/morphology.jar:dist/transliterator.jar:lib/json-simple-1.1.1.jar lv.lumii.morphotagger.MorphoPipe $*
