#!/bin/bash

cd $(dirname $0)
time java -Xmx12G -cp "target/*" lv.lumii.morphotagger.MorphoCRF -train -dev $*
