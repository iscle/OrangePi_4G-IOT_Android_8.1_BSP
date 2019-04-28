#!/bin/bash

#$1 project_info  [eg:IoT_k37mv1_bsp_512_ry_smt_hd720_pcb_v2]
#$2 version_info  [eg:v00 v01 ...]
#$3 compile_mode  [eng:user userdebug eng]

carg1=$1

if [ -z "$2" ]; then 
	echo "2nd param [version number]: is empty, please input VERNO [v00 v01 ...]"
	exit
fi
if [ -z "$3" ]; then 
	echo "3rd param [version flag]: is empty, please input build flag [user userdebug eng]"
	exit
fi

echo "################  RUNYEE PROJECT INFO BEGIN  ####################"
orangepi_project=${carg1%%_*}
echo $orangepi_project
echo "################  RUNYEE PROJECT INFO END  ####################"

echo "########################### CHANGE JDK/GCC VERSION BEGIN #############################"
source ./anr_O.sh
echo "########################### CHANGE JDK/GCC VERSION END #############################"

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

#./init_project.sh $1
#./modem.sh $1

echo "################  COMPILE PROJECT BEGIN  ####################"
cd ${PARENT_DIR}

#make clean
source build/envsetup.sh
lunch full_$base_project-$3
#make -j8 2>&1 | tee build.log
make -j8 otapackage

cd orangepi/scripts
echo "################  COMPILE PROJECT END  ####################"

./tar_img.sh $1 $2 $3
