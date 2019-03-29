#
# Copyright (C) 2016 ARM Limited. All rights reserved.
#
# Copyright (C) 2008 The Android Open Source Project
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

$(info gralloc for hikey960)
GRALLOC_FB_SWAP_RED_BLUE := 0
GRALLOC_DEPTH := GRALLOC_32_BITS

# GPU support for AFBC 1.0
MALI_GPU_SUPPORT_AFBC_BASIC=1
# GPU support for AFBC 1.1 block split
MALI_GPU_SUPPORT_AFBC_SPLITBLK=1
# GPU support for AFBC 1.1 wide block
MALI_GPU_SUPPORT_AFBC_WIDEBLK=1
# GPU support for AFBC 1.2 tiled headers
MALI_GPU_SUPPORT_AFBC_TILED_HEADERS=0
# GPU support YUV AFBC formats in wide block
MALI_GPU_USE_YUV_AFBC_WIDEBLK=0

#
# Software behaviour defines
#

# Use ION DMA heap for all allocations. Default is system heap.
GRALLOC_USE_ION_DMA_HEAP=0
# Use ION Compound heap for all allocations. Default is system heap.
GRALLOC_USE_ION_COMPOUND_PAGE_HEAP=0
# Properly initializes an empty AFBC buffer
GRALLOC_INIT_AFBC=0
# When enabled, forces display framebuffer format to BGRA_8888
GRALLOC_FB_SWAP_RED_BLUE=0
# Disables the framebuffer HAL device. When a hwc impl is available.
GRALLOC_DISABLE_FRAMEBUFFER_HAL=0
# When enabled, buffers will never be allocated with AFBC
GRALLOC_ARM_NO_EXTERNAL_AFBC=0
# Minimum buffer dimensions in pixels when buffer will use AFBC
GRALLOC_DISP_W=0
GRALLOC_DISP_H=0
# Vsync backend(not used)
GRALLOC_VSYNC_BACKEND=default
