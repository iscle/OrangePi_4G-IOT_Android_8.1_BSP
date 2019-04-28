#!/bin/bash

export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export ANDROID_JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export JRE_HOME=/usr/lib/jvm/java-8-openjdk-amd64/jre
export CLASSPATH=.:$JAVA_HOME/lib/dt.jar:$JAVA_HOME/lib/tools.jar:$JAVA_HOME/lib:$JRE_HOME/lib:$CLASSPATH
export PATH=$JAVA_HOME/bin:$JRE_HOME/bin:$PATH

java -version

echo "gcc version before setting is:"
gcc --version
echo "setting gcc version:"
sudo rm /usr/bin/gcc -f
sudo ln -s /usr/bin/gcc-4.6 /usr/bin/gcc
echo "gcc version after setting is:"
gcc --version

echo "g++ version before setting is:"
g++ --version
echo "setting g++ version:"
sudo rm /usr/bin/g++ -f
sudo ln -s /usr/bin/g++-4.6 /usr/bin/g++
echo "g++ version after setting is:"
g++ --version

