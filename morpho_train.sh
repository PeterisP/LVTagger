#!/bin/bash

cd $(dirname $0)
time java -Xms16G -Xmx24G -cp "target/*" lv.lumii.morphotagger.MorphoCRF -train -dev $*
