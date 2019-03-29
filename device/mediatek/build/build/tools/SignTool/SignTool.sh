#!/bin/sh
# Copyright Statement:
#
# This software/firmware and related documentation ("MediaTek Software") are
# protected under relevant copyright laws. The information contained herein
# is confidential and proprietary to MediaTek Inc. and/or its licensors.
# Without the prior written permission of MediaTek inc. and/or its licensors,
# any reproduction, modification, use or disclosure of MediaTek Software,
# and information contained herein, in whole or in part, shall be strictly prohibited.
#
# MediaTek Inc. (C) 2010. All rights reserved.
#
# BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
# THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
# RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
# AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
# NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
# SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
# SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
# THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
# THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
# CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
# SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
# STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
# CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
# AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
# OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
# MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
#
# The following software/firmware and/or related documentation ("MediaTek Software")
# have been modified by MediaTek Inc. All revisions are subject to any receiver's
# applicable license agreements with MediaTek Inc.


##############################################################
# Program:
#	SIGN TOOL
#

CUR_DIR=`pwd`

if [ "$3" = "" ]; then
    echo "Usage: Sign Image .."
    echo "       ./SignTool [KEY] [CONFIG] [INPUT_IMAGE] [OUTPUT_IMAGE]"
    echo ""
    echo "Example:"
    echo "       ./SignTool.sh IMG_KEY.ini IMG_CFG.ini u-boot.bin signed-u-boot.bin"    
    echo ""
    exit 1;
fi
echo [Dependency] $0
cd device/mediatek/build/build/tools/SignTool
make
cd $CUR_DIR

##############################################################
# Setup tool and nand page size
SignTool=${0%/*}/SignTool
Simg2imgTool=device/mediatek/build/build/tools/SignTool/simg2img
Yaffs2Covert=device/mediatek/build/build/tools/SignTool/yimg2img
if [ "$5" = "2K" ] ; then
    NAND_PAGE_SIZE=2048
elif [ "$5" = "4K" ] ; then
    NAND_PAGE_SIZE=4096
elif [ "$5" = "1K" ] ; then
    NAND_PAGE_SIZE=1024
else
    echo "[SignTool] NAND page size $5 not supported, set to default 2048!!"
    NAND_PAGE_SIZE=2048
fi

##############################################################
# Check if it is a sparse image or yaffs2 image
Sparse_Str=`hexdump -n 4 $3 | head -n 1 | awk '{print $2,$3}'`
Yaffs_Str=`hexdump $3 | head -n 1 | awk '{print $2,$3,$4,$5,$6,$7,$8,$9}'`
if [ "$Sparse_Str" = "ff3a ed26" ] ; then
    echo [Dependency] ${Simg2imgTool}
    ./${Simg2imgTool} $3 UN_SPARSE_TEMP_IMG
    ./${SignTool} $1 $2 UN_SPARSE_TEMP_IMG SIGNATURE HEADER
    if [ $? -eq 0 ] ; then
        echo "SIGN PASS !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!(Sparse S1 IMG)"
        cp -f HEADER EXT_HDR_SPARSE
        cat SIGNATURE >> EXT_HDR_SPARSE
    else
        echo "SIGN FAIL !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!(Sparse S1 IMG)"
        exit 1;
    fi

    rm -f SIGNATURE
    rm -f HEADER

    ./${SignTool} $1 $2 $3 SIGNATURE HEADER EXT_HDR_SPARSE
    if [ $? -eq 0 ] ; then
        echo "SIGN PASS !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!(Sparse S2 IMG)"
        cp -f HEADER $4
        cat $3 >> $4
        cat SIGNATURE >> $4
        rm -f SIGNATURE
        rm -f HEADER
    else
        echo "SIGN FAIL !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!(Sparse S2 IMG)"
        exit 1;
    fi

    rm -rf UN_SPARSE_TEMP_IMG
    rm -rf EXT_HDR_SPARSE

elif [ "$Yaffs_Str" = "0003 0000 0001 0000 ffff 0000 0000 0000" ] ; then
    echo [Dependency] ${Yaffs2Covert}
    ./${Yaffs2Covert} -c $NAND_PAGE_SIZE $3 UN_YAFFS_TEMP_IMG
    echo "./${Yaffs2Covert} -c $NAND_PAGE_SIZE $3 UN_YAFFS_TEMP_IMG"
    ./${SignTool} $1 $2 UN_YAFFS_TEMP_IMG SIGNATURE HEADER
    if [ $? -eq 0 ] ; then
        echo "SIGN PASS !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!(Yaffs S1 IMG)"
        cp -f HEADER EXT_HDR_SPARSE
        cat SIGNATURE >> EXT_HDR_SPARSE
    else
        echo "SIGN FAIL !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!(Yaffs S1 IMG)"
        exit 1;
    fi

    rm -f SIGNATURE
    rm -f HEADER

    ./${SignTool} $1 $2 $3 SIGNATURE HEADER EXT_HDR_SPARSE
    if [ $? -eq 0 ] ; then
        echo "SIGN PASS !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!(Yaffs S2 IMG)"
        cp -f HEADER $4
        cat $3 >> $4
        cat SIGNATURE >> $4
        rm -f SIGNATURE
        rm -f HEADER
    else
        echo "SIGN FAIL !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!(Yaffs S2 IMG)"
        exit 1;
    fi

    rm -rf UN_YAFFS_TEMP_IMG
    rm -rf EXT_HDR_SPARSE
    
else

    ./${SignTool} $1 $2 $3 SIGNATURE HEADER
    if [ $? -eq 0 ] ; then
        echo "SIGN PASS !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!(Normal IMG)"
        cp -f HEADER $4
        cat $3 >> $4
        cat SIGNATURE >> $4
    else
        echo "SIGN FAIL !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!(Normal IMG)"
        exit 1;
    fi

    rm -f SIGNATURE
    rm -f HEADER
fi


##############################################################
# Check if need to generate fastboot signature
if [ "$6" = "FB_SIG" ] ; then
    ./${SignTool} $1 $2 $4 $3 FB_SIG
    if [ $? -eq 0 ] ; then
        echo "SIGN PASS !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!(FB SIG)"
    else
        echo "SIGN FAIL !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!(FB SIG)"
        exit 1;
    fi
    cp -f FB_SIG $4.csd
    cat $4 >> $4.csd
    rm -f FB_SIG
    rm -f $4.sig
    rm -f $4
    mv -f $4.csd $4
fi
