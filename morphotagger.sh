#!/bin/bash

cd $(dirname $0)
/Library/Java/JavaVirtualMachines/jdk1.8.0_25.jdk/Contents/Home/bin/java -Xmx2G -cp dist/CRF.jar:dist/morphology.jar:dist/transliterator.jar:lib/json-simple-1.1.1.jar lv.lumii.morphotagger.MorphoPipe $*
