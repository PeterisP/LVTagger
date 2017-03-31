#!/bin/bash

cd $(dirname $0)
time java -Xmx12G -cp target/tagger-1.0.3-jar-with-dependencies.jar lv.lumii.morphotagger.MorphoCRF -train -dev $*
