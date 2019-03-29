#
# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# only locally defined variables can be used at this time

my_variant := linux

AUX_OS_$(my_variant)             := nanohub
AUX_ARCH_$(my_variant)           := native
AUX_SUBARCH_$(my_variant)        := native
AUX_CPU_$(my_variant)            := x86

# variant supports building OS bootloader, main OS image and application as targets
# target should one of the following:
#   "" (empty) -- applies to all (OS, BL, APP)
#  _BL -- applies to OS bootloader target build only
#  _OS -- applies to OS image target build only
# _APP -- applies to application target build only
#
# the following variables may be defined in variant script for any target:
# NANO_VARIANT<target>_CFLAGS_<variant>
# NANO_VARIANT<target>_C_INCLUDES_<variant>
# NANO_VARIANT<target>_STATIC_LIBRARIRES_<variant>
# NANO_VARIANT<target>_WHOLE_STATIC_LIBRARIRES_<variant>
#
# the following may be defined for _OS and _BL only, to control
# what additional source files need to be included in the build;
# the file paths in this list are relative to the target (_OS or _BL) LOCAL_PATH;
# NANO_VARIANT<target>_SRC_FILES_<variant>

# 100K heap
NANO_VARIANT_CFLAGS_$(my_variant)  := -DHEAP_SIZE=102400

NANO_VARIANT_C_INCLUDES_$(my_variant) := device/google/contexthub/firmware/variant/linux/inc

NANO_VARIANT_NO_BOOTLOADER_$(my_variant) := true
