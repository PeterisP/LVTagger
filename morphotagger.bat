@echo off
cd /d %~dp0
java -Xmx1G -Dfile.encoding=UTF8 -cp "target\*" lv.lumii.morphotagger.MorphoPipe %*