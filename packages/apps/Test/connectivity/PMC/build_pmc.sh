#! /bin/bash
#This script does a clean build of PMC and install it to your device through adb
#Requires envsetup.sh in the branch

#function to change jdk version
function setup_jdk() {
  # Remove the current JDK from PATH
  if [ -n "$JAVA_HOME" ] ; then
    PATH=${PATH/$JAVA_HOME\/bin:/}
  fi
  export JAVA_HOME=$1
  export PATH=$JAVA_HOME/bin:$PATH
}

#Color code for echo
r='\e[0;31m'
brn='\e[0;33m'
y='\e[1;33m'
g='\e[0;32m'
cy='\e[0;36m'
lb='\e[1;34m'
p='\e[0;35m'
lg='\e[0;37m'
NC='\e[0m' # No Color

echo -e "Welcome to ${r}U${brn}N${y}I${g}C${lb}O${cy}R${p}N${NC} build system for ${g}PMC${NC}"

IFS='_' read -a array <<< "$TARGET_PRODUCT"
export TP=${array[0]}
if [[ ${#array[@]} -eq 2 ]]; then
  export TP=${array[1]}
fi

APP_NAME=PMC
APP_PACKAGE_NAME=com.android.pmc

BRANCH_ROOT=$PWD/../../../../..
PMC_PROJ=$BRANCH_ROOT/vendor/google_testing/comms/Tools/PMC
SHARED_LIB_JAR_ROOT=$BRANCH_ROOT/out/target/common/obj/JAVA_LIBRARIES
APP_JAR_ROOT=$BRANCH_ROOT/out/target/common/obj/APPS
APK_ROOT=$BRANCH_ROOT/out/target/product/$TP/system/priv-app/PMC

function pmc_build {

echo -e "${y}Removing intermeidates of the app${NC}"
rm -r $APP_JAR_ROOT/"$APP_NAME"_intermediates
#Remove the apk file
rm $APK_ROOT/"$APP_NAME".apk

#Build all the dependency libs
. $BRANCH_ROOT/build/envsetup.sh

exec () {
  ${@:1:($#-1)}
  if [ $? -ne 0 ]; then
    echo -e "${r}Encountered error when ${@:$#}${NC}"
    echo -e "${lg}UNICORN ${r}DIED${NC}!"
    exit 1
  fi
}

echo -e "${lb}+++++++ Building $APP_NAME.apk +++++++${NC}"
cd $PMC_PROJ
exec mm -B "building $APP_NAME.apk"
echo

}

function pmc_flash {

echo -e "${y}Switching to root${NC}"
adb root
adb wait-for-device remount

echo -e "${y}Uninstalling old apk from device${NC}"
adb uninstall $APP_PACKAGE_NAME
adb shell rm -r /system/priv-app/$APP_NAME.apk

echo -e "${lb}Installing apk to device${NC}"
cd $APK_ROOT
#exec adb install $APP_NAME.apk "installing apk to device"
#exec adb push $APP_NAME.apk /system/priv-app "installing apk to previliged dir"
exec adb install -r $APP_NAME.apk "installing apk to previliged dir"

}

DO_BUILD=1
DO_FLASH=1

if [ $# -ne 0 ] ; then
  DO_BUILD=0
  DO_FLASH=0
  while getopts "bf" ARG
  do
    case $ARG in
      b) DO_BUILD=1 && echo "Build it we will.";;
      f) DO_FLASH=1 && echo "Flash it we must.";;
      ?) echo "Invalid Argument ${ARG}" && exit 1;;
    esac
  done
fi

if [ ${DO_BUILD} -eq "1" ] ; then
  pmc_build
fi

if [ ${DO_FLASH} -eq "1" ] ; then
  pmc_flash
fi

echo "All clear!"
echo -e " ${r}U${brn}N${y}I${g}C${cy}O${lb}R${p}N ${r}P${brn}O${y}W${g}E${cy}R${lb}!${p}!${NC}"

