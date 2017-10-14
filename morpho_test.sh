#!/bin/bash

cd $(dirname $0)
time java -Xmx4G -cp "target/*" lv.lumii.morphotagger.MorphoCRF $*
