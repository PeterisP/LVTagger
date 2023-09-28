#!/bin/bash

cd $(dirname $0)
time java -Xms12G -Xmx20G -cp "target/*" lv.lumii.morphotagger.MorphoCRF -train -dev $*
