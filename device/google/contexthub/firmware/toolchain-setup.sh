#!/bin/bash

[[ $_ != $0 ]] && echo "configuring external toolchain for nanohub" || (echo "Script has to be sourced" && exit 1)

if [ ! -e $HOME/toolchains/gcc-arm-none-eabi-5_3-2016q1/bin ] ; then
    echo Toolchain is not found, downloading
    (mkdir -p $HOME/toolchains && cd $HOME/toolchains &&
     wget https://launchpad.net/gcc-arm-embedded/5.0/5-2016-q1-update/+download/gcc-arm-none-eabi-5_3-2016q1-20160330-linux.tar.bz2 &&
     tar xjf gcc-arm-none-eabi-5_3-2016q1-20160330-linux.tar.bz2 &&
     echo Toolchain download done
    ) || (echo Toolchain download failed)
fi

export ARM_NONE_GCC_PATH=$HOME/toolchains/gcc-arm-none-eabi-5_3-2016q1
export CROSS_COMPILE=$ARM_NONE_GCC_PATH/bin/arm-none-eabi-
export NANOHUB_TOOLCHAIN=$CROSS_COMPILE
