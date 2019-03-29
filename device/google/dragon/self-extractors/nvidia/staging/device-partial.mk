# Copyright 2015 The Android Open Source Project
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

# Nvidia blob(s) necessary for Dragon hardware
PRODUCT_COPY_FILES := \
    vendor/nvidia/dragon/proprietary/acr_ucode.bin:root/vendor/firmware/nouveau/acr_ucode.bin:nvidia \
    vendor/nvidia/dragon/proprietary/fecs.bin:root/vendor/firmware/nouveau/fecs.bin:nvidia \
    vendor/nvidia/dragon/proprietary/fecs_sig.bin:root/vendor/firmware/nouveau/fecs_sig.bin:nvidia \
    vendor/nvidia/dragon/proprietary/gpmu_ucode_desc.bin:root/vendor/firmware/nouveau/gpmu_ucode_desc.bin:nvidia \
    vendor/nvidia/dragon/proprietary/gpmu_ucode_image.bin:root/vendor/firmware/nouveau/gpmu_ucode_image.bin:nvidia \
    vendor/nvidia/dragon/proprietary/nv12b_bundle:root/vendor/firmware/nouveau/nv12b_bundle:nvidia \
    vendor/nvidia/dragon/proprietary/nv12b_fuc409c:root/vendor/firmware/nouveau/nv12b_fuc409c:nvidia \
    vendor/nvidia/dragon/proprietary/nv12b_fuc409d:root/vendor/firmware/nouveau/nv12b_fuc409d:nvidia \
    vendor/nvidia/dragon/proprietary/nv12b_fuc41ac:root/vendor/firmware/nouveau/nv12b_fuc41ac:nvidia \
    vendor/nvidia/dragon/proprietary/nv12b_fuc41ad:root/vendor/firmware/nouveau/nv12b_fuc41ad:nvidia \
    vendor/nvidia/dragon/proprietary/nv12b_method:root/vendor/firmware/nouveau/nv12b_method:nvidia \
    vendor/nvidia/dragon/proprietary/nv12b_sw_ctx:root/vendor/firmware/nouveau/nv12b_sw_ctx:nvidia \
    vendor/nvidia/dragon/proprietary/nv12b_sw_nonctx:root/vendor/firmware/nouveau/nv12b_sw_nonctx:nvidia \
    vendor/nvidia/dragon/proprietary/pmu_bl.bin:root/vendor/firmware/nouveau/pmu_bl.bin:nvidia \
    vendor/nvidia/dragon/proprietary/pmu_sig.bin:root/vendor/firmware/nouveau/pmu_sig.bin:nvidia \
    vendor/nvidia/dragon/proprietary/bpmp.bin:root/vendor/firmware/nvidia/tegra210/bpmp.bin:nvidia \
    vendor/nvidia/dragon/proprietary/nvdec_bl_prod.bin:root/vendor/firmware/nvidia/tegra210/nvdec_bl_prod.bin:nvidia \
    vendor/nvidia/dragon/proprietary/nvdec_ns.bin:root/vendor/firmware/nvidia/tegra210/nvdec_ns.bin:nvidia \
    vendor/nvidia/dragon/proprietary/nvdec_prod.bin:root/vendor/firmware/nvidia/tegra210/nvdec_prod.bin:nvidia \
    vendor/nvidia/dragon/proprietary/xusb.bin:root/vendor/firmware/nvidia/tegra210/xusb.bin:nvidia \

