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

#scatter flag
SCATTER=`echo $platform | tr '[A-Z' '[a-z]'`
SCATTER=`echo $platform | tr '[a-z' '[A-Z]'`
echo $SCATTER
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

echo "################  PAC IMG FILES AND MODEM FILES BEGIN  ####################"
RF_LTG_LWG_BIN_DEST_DIR=${PARENT_DIR}/vendor/mediatek/proprietary/modem
RF_LTG_LWG_BIN_OUT_DIR=${PARENT_DIR}/out/target/product/$base_project

cp -aR ${RF_LTG_LWG_BIN_OUT_DIR}/obj/CGEN/*APDB* ${RF_LTG_LWG_BIN_DEST_DIR}
cp -aR ${RF_LTG_LWG_BIN_DEST_DIR} ${RF_LTG_LWG_BIN_OUT_DIR}
#cd ${RF_LTG_LWG_BIN_OUT_DIR}

# define pac file name
#test1=_bd6753_65_b_m0_
#test2=_"$base_project"_

#echo ${carg1/_bd6753_65_b_m0_/_}
#echo ${carg1/$test1/_}
#echo ${carg1/$test2/_}
PacFileName=${carg1/_"$base_project"_/_}
echo "$PacFileName"

if [ -d ${PARENT_DIR}/$PacFileName ] 
then
rm -rf ${PARENT_DIR}/$PacFileName/*

#mkdir $1
mkdir ${PARENT_DIR}/$PacFileName/images
mkdir ${PARENT_DIR}/$PacFileName/modem
images_dir=${PARENT_DIR}/$PacFileName/images
modem_dir=${PARENT_DIR}/$PacFileName/modem
else
mkdir ${PARENT_DIR}/$PacFileName
mkdir ${PARENT_DIR}/$PacFileName/images
mkdir ${PARENT_DIR}/$PacFileName/modem
images_dir=${PARENT_DIR}/$PacFileName/images
modem_dir=${PARENT_DIR}/$PacFileName/modem
fi


#cp modem releated files
cp -aR ${RF_LTG_LWG_BIN_OUT_DIR}/modem/* ${PARENT_DIR}/$PacFileName/modem
rm -rf ${PARENT_DIR}/$PacFileName/modem/*.mk
#cp images releated files
cp -v ${RF_LTG_LWG_BIN_OUT_DIR}/preloader_"$base_project".bin $images_dir/preloader_"$base_project".bin
cp -v ${RF_LTG_LWG_BIN_OUT_DIR}/lk.img $images_dir/lk.img
cp -v ${RF_LTG_LWG_BIN_OUT_DIR}/boot.img $images_dir/boot.img
cp -v ${RF_LTG_LWG_BIN_OUT_DIR}/recovery.img $images_dir/recovery.img
cp -v ${RF_LTG_LWG_BIN_OUT_DIR}/logo.bin $images_dir/logo.bin
cp -v ${RF_LTG_LWG_BIN_OUT_DIR}/secro.img $images_dir/secro.img
cp -v ${RF_LTG_LWG_BIN_OUT_DIR}/tee.img $images_dir/tee.img
cp -v ${RF_LTG_LWG_BIN_OUT_DIR}/odmdtbo.img $images_dir/odmdtbo.img
cp -v ${RF_LTG_LWG_BIN_OUT_DIR}/vendor.img $images_dir/vendor.img
cp -v ${RF_LTG_LWG_BIN_OUT_DIR}/system.img $images_dir/system.img
cp -v ${RF_LTG_LWG_BIN_OUT_DIR}/cache.img $images_dir/cache.img
cp -v ${RF_LTG_LWG_BIN_OUT_DIR}/userdata.img $images_dir/userdata.img
cp -v ${RF_LTG_LWG_BIN_OUT_DIR}/"$SCATTER"_Android_scatter.txt $images_dir/"$SCATTER"_Android_scatter.txt

#entry root dir
cd ../..
if [ -d "out/target/product/$base_project/obj/PACKAGING/target_files_intermediates/otapackage" ] 
then
rm -rf out/target/product/$base_project/obj/PACKAGING/target_files_intermediates/otapackage
fi
if [ -f "out/target/product/$base_project/obj/PACKAGING/target_files_intermediates/otapackage.zip" ]; then 
mv out/target/product/$base_project/obj/PACKAGING/target_files_intermediates/otapackage.zip $PacFileName/"$PacFileName"_$2_$3_$(date +%Y%m%d%H%M%S)_otapackage.zip
fi

tar zcvf "$PacFileName"_$2_$3_$(date +%Y%m%d%H%M%S).tar.gz $PacFileName
echo "################  PAC IMG FILES AND MODEM FILES END  ####################"





