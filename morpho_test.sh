#!/bin/bash

cd $(dirname $0)
java -Xmx4G -cp target/tagger-1.0.2-jar-with-dependencies.jar lv.lumii.morphotagger.MorphoCRF $*
