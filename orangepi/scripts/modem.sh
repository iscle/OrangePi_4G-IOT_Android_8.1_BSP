#!/bin/bash

#$1 project_info  [eg:IoT_k37mv1_bsp_512_ry_smt_hd720_pcb_v2]

carg1=$1

echo "################  RUNYEE PROJECT INFO BEGIN  ####################"
orangepi_project=${carg1%%_*}
echo $orangepi_project
echo "################  RUNYEE PROJECT INFO END  ####################"

echo "################  MTK PLATFORM AND RY_PROJ_PLAT BEGIN  ####################"
echo "$carg1" | grep -q "k37m"
if [ $? -eq 0 ]
then
	echo "platform is mt6737m ....."
	platform="mt6737m"
	ry_proj_plat=$orangepi_project"_"$platform
	echo $platform
	echo $ry_proj_plat
fi
echo "$carg1" | grep -q "k37t"
if [ $? -eq 0 ]
then
	echo "platform is mt6737t ....."
	platform="mt6737t"
	ry_proj_plat=$orangepi_project"_"$platform
	echo $platform
	echo $ry_proj_plat
fi
echo "$carg1" | grep -q "k53"
if [ $? -eq 0 ]
then
	echo "platform is mt6753 ....."
	platform="mt6753"
	ry_proj_plat=$orangepi_project"_"$platform
	echo $platform
	echo $ry_proj_plat
fi
echo "################  MTK PLATFORM AND RY_PROJ_PLAT END  ####################"

echo "################  BASE PROJECT BEGIN  ####################"
echo "$carg1" | grep -q "k37mv1_bsp_512"
if [ $? -eq 0 ]
then
	echo "base_project is k37mv1_bsp_512 ....."
	base_project="k37mv1_bsp_512"
	echo $base_project
else
echo "$carg1" | grep -q "k37mv1_bsp"
if [ $? -eq 0 ]
then
	echo "base_project is k37mv1_bsp ....."
	base_project="k37mv1_bsp"
	echo $base_project
fi	
fi

echo "$carg1" | grep -q "k37mv1_64_bsp"
if [ $? -eq 0 ]
then
	echo "base_project is k37mv1_64_bsp ....."
	base_project="k37mv1_64_bsp"
	echo $base_project
fi

echo "$carg1" | grep -q "k37tv1_bsp_512"
if [ $? -eq 0 ]
then
	echo "base_project is k37tv1_bsp_512 ....."
	base_project="k37tv1_bsp_512"
	echo $base_project
else
echo "$carg1" | grep -q "k37tv1_bsp"
if [ $? -eq 0 ]
then
	echo "base_project is k37tv1_bsp ....."
	base_project="k37tv1_bsp"
	echo $base_project
fi	
fi
echo "$carg1" | grep -q "k37tv1_bsp_gmo_1g"
if [ $? -eq 0 ]
then
	echo "base_project is k37tv1_bsp_gmo_1g ....."
	base_project="k37tv1_bsp_gmo_1g"
	echo $base_project
fi
echo "$carg1" | grep -q "k37tv1_64_bsp"
if [ $? -eq 0 ]
then
	echo "base_project is k37tv1_64_bsp ....."
	base_project="k37tv1_64_bsp"
	echo $base_project
fi

echo "$carg1" | grep -q "k53v1_bsp_gmo_1g"
if [ $? -eq 0 ]
then
	echo "base_project is k53v1_bsp_gmo_1g ....."
	base_project="k53v1_bsp_gmo_1g"
	echo $base_project
else
echo "$carg1" | grep -q "k53v1_bsp"
if [ $? -eq 0 ]
then
	echo "base_project is k53v1_bsp ....."
	base_project="k53v1_bsp"
	echo $base_project
fi	
fi
echo "$carg1" | grep -q "k53v1_64_bsp"
if [ $? -eq 0 ]
then
	echo "base_project is k53v1_64_bsp ....."
	base_project="k53v1_64_bsp"
	echo $base_project
fi
echo "################  BASE PROJECT END  ####################"

echo "################  ARM FLAG BEGIN  ####################"
echo "$carg1" | grep -q "_64_"
if [ $? -eq 0 ]
then
	echo "arm flag is arm64 ....."
	arm_flag="arm64"
	echo $arm_flag
else
	echo "arm flag is arm ....."
	arm_flag="arm"
	echo $arm_flag
fi
echo "################  ARM FLAG END  ####################"

echo "################  RF BANDS BEGIN  ####################"
echo "$carg1" | grep -q "_5m"
if [ $? -eq 0 ]
then
	echo "rf bands is 5m ....."
	bands_flag="5m"
	echo $bands_flag
fi

echo "$carg1" | grep -q "_4m"
if [ $? -eq 0 ]
then
	echo "rf bands is 4m ....."
	bands_flag="4m"
	echo $bands_flag
fi

echo "$carg1" | grep -q "_3m"
if [ $? -eq 0 ]
then
	echo "rf bands is 3m ....."
	bands_flag="3m"
	echo $bands_flag
fi
echo "################  RF BANDS END  ####################"

PARENT_DIR=../..

echo "###############################  COMPILE MODEM FILE BEGIN  ####################################"
cp -aR ${PARENT_DIR}/orangepi/projects/$orangepi_project/$ry_proj_plat/$1/rf*/* ${PARENT_DIR}/modem/ltg_lwg/mcu/

echo "########################### CHANGE JDK/GCC VERSION BEGIN #############################"
source ./anr_O.sh
echo "########################### CHANGE JDK/GCC VERSION END #############################"

cd ${PARENT_DIR}/orangepi/projects/$orangepi_project/$ry_proj_plat/$1/rf*
rf_param1=`pwd`
rf_param2=${rf_param1##*/}
echo $rf_param2
rf_param=${rf_param2#*_}
echo $rf_param
cd ../../../..
#exit

RF_LTG_LWG_COMPILE_DIR=${PARENT_DIR}/modem/ltg_lwg/mcu

cd ${RF_LTG_LWG_COMPILE_DIR}


svn up ../apps
./auto_build.sh $rf_param
echo "###############################  COMPILE BRANCHE MODEM FILE END  ####################################"



