#!/bin/bash

cd $(dirname $0)
time java -Xms10G -Xmx14G -cp "target/*" lv.lumii.morphotagger.MorphoCRF -train -dev $*
