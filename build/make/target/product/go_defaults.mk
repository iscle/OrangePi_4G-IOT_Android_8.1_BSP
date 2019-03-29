#
# Copyright (C) 2017 The Android Open Source Project
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

# Inherit common Android Go defaults.
$(call inherit-product, build/target/product/go_defaults_common.mk)

# Specifier 2.0 heap growth instead of the 1.0 default from
# ro.config.low_ram=true. This reduces GC frequency at the cost of some RAM
# usage. The RAM usage is proportional to the cumulative dalvik heap size.
PRODUCT_PROPERTY_OVERRIDES += \
     dalvik.vm.foreground-heap-growth-multiplier=2.0
