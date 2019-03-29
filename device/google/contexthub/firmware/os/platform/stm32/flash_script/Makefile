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

GCC = $(CROSS_COMPILE)gcc
OBJCOPY = $(CROSS_COMPILE)objcopy

FLAGS += -mthumb -mcpu=cortex-m4 -march=armv7e-m -mno-thumb-interwork -fno-unwind-tables -fstack-reuse=all -Os -Wall -Werror -nostartfiles


tool.bin: tool.elf Makefile
	$(OBJCOPY) -j .data -j .text -I elf32-littlearm -O binary tool.elf tool.bin

tool.elf: tool.c Makefile
	$(GCC) -o tool.elf $(FLAGS) tool.c

clean:
	rm -rf tool.o tool.bin

