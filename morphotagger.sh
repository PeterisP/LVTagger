#!/bin/bash

cd $(dirname $0)
java -Xmx2G -cp dist/CRF.jar:dist/morphology.jar:dist/transliterator.jar:lib/json_simple-1.1.jar WordPipe $*