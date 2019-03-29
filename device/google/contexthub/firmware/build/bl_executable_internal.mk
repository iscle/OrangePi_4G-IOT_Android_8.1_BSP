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

ifeq ($(strip $(NANO_VARIANT_NO_BOOTLOADER_$(AUX_OS_VARIANT))),)

LOCAL_WHOLE_STATIC_LIBRARIES += libnanohub_bl_$(AUX_CPU)
LOCAL_WHOLE_STATIC_LIBRARIES += libnanohub_bl_$(AUX_ARCH)
LOCAL_WHOLE_STATIC_LIBRARIES += $(NANO_VARIANT_BLCFG_STATIC_LIBRARIES_$(AUX_OS_VARIANT))

LOCAL_SRC_FILES += $(NANO_VARIANT_BLCFG_SRC_FILES_$(AUX_OS_VARIANT))

include $(BUILD_NANOHUB_EXECUTABLE)

endif
