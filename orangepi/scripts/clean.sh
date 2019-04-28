#!/bin/bash

cd ../..

echo "############################         cleanup and update starting ...             ###########################"

echo "########################### CHANGE JDK/GCC VERSION BEGIN #############################"
source orangepi/scripts/anr_O.sh
echo "########################### CHANGE JDK/GCC VERSION END #############################"

echo "#####################         clean out dir begin......     #########################"
make clean
echo "#####################         clean out dir successfully!     #########################"

echo "#####################         revert all modified files begin......     #########################"
svn revert -R ./
echo "#####################         revert all modified files successfully!     #######################"

echo "######################            remove all files added begin......      #######################"
svn st | sed -e "s/^?//" | xargs rm -rf
echo "#####################          remove all files added successfully!       #######################"

echo "#####################  svn to the latest version ......   #####################"
svn up .
echo "#####################  svn to the latest successfully!   #####################"

echo "###########################       Cleanup and update finished !!!     ###########################"

