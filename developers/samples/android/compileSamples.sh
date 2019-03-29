#!/bin/bash

# Abort on error
set -e

if [ -z $1 ]; then
    echo "Error: Please specify output directory";
    exit 1
else
    echo "Output dir: ~/samples-out/${1}";
fi

export GRADLE_OPTS="-Xmx4096m -XX:MaxPermSize=512m -XX:-UseGCOverheadLimit -XX:+HeapDumpOnOutOfMemoryError"
export JAVA_OPTS="-Xmx4096m -XX:MaxPermSize=512m -XX:-UseGCOverheadLimit -XX:+HeapDumpOnOutOfMemoryError"
export _JAVA_OPTIONS="-Xmx4096m -XX:MaxPermSize=512m -XX:-UseGCOverheadLimit -XX:+HeapDumpOnOutOfMemoryError"

parallel --joblog emit.log --max-procs 8 --retries 5 -a projects.txt ./emitSample.sh

rsync -avzrt --delete ../../build/out/gradle/ ../../build/prebuilts/gradle
rm -rf ~/samples-out/$1 || true
mkdir -p ~/samples-out/$1
mv ../../build/out/browseable/*.zip ~/samples-out/$1
rsync -avzrt --delete ../../build/out/browseable/ ../../../development/samples/browseable
