#! /bin/bash

TOOLCHAIN_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export MD32_NEWLIB_HOME=$TOOLCHAIN_ROOT/ToolChain/md32/md32-elf
export MD32_NEWLIB_OPT="-lc -lsoftfloat_gnu -lgloss -lm"
export MS15E30_GNU_LIB=$TOOLCHAIN_ROOT/Md32/nml/s15r30_md32_v3.0/lib/softfloat/lib/Release
export PATH=$TOOLCHAIN_ROOT/ToolChain/install_md32/bin:$TOOLCHAIN_ROOT/ToolChain/md32/bin:$PATH
