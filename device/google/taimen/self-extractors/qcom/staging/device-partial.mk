# Copyright 2017 The Android Open Source Project
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

#  blob(s) necessary for Taimen hardware
PRODUCT_COPY_FILES := \
    vendor/qcom/taimen/proprietary/netutils-wrapper-1.0:system/bin/netutils-wrapper-1.0:qcom \
    vendor/qcom/taimen/proprietary/ATT_profiles.xml:system/etc/cne/Nexus/ATT/ATT_profiles.xml:qcom \
    vendor/qcom/taimen/proprietary/ROW_profiles.xml:system/etc/cne/Nexus/ROW/ROW_profiles.xml:qcom \
    vendor/qcom/taimen/proprietary/VZW_profiles.xml:system/etc/cne/Nexus/VZW/VZW_profiles.xml:qcom \
    vendor/qcom/taimen/proprietary/dnd.descriptor:system/etc/firmware/dnd.descriptor:qcom \
    vendor/qcom/taimen/proprietary/dnd.sound_model:system/etc/firmware/dnd.sound_model:qcom \
    vendor/qcom/taimen/proprietary/music_detector.descriptor:system/etc/firmware/music_detector.descriptor:qcom \
    vendor/qcom/taimen/proprietary/music_detector.sound_model:system/etc/firmware/music_detector.sound_model:qcom \
    vendor/qcom/taimen/proprietary/com.android.ims.rcsmanager.xml:system/etc/permissions/com.android.ims.rcsmanager.xml:qcom \
    vendor/qcom/taimen/proprietary/cneapiclient.jar:system/framework/cneapiclient.jar:qcom \
    vendor/qcom/taimen/proprietary/com.android.ims.rcsmanager.jar:system/framework/com.android.ims.rcsmanager.jar:qcom \
    vendor/qcom/taimen/proprietary/com.quicinc.cne.api-V1.0-java.jar:system/framework/com.quicinc.cne.api-V1.0-java.jar:qcom \
    vendor/qcom/taimen/proprietary/com.quicinc.cne.jar:system/framework/com.quicinc.cne.jar:qcom \
    vendor/qcom/taimen/proprietary/embmslibrary.jar:system/framework/embmslibrary.jar:qcom \
    vendor/qcom/taimen/proprietary/rcsimssettings.jar:system/framework/rcsimssettings.jar:qcom \
    vendor/qcom/taimen/proprietary/vendor.qti.qcril.am-V1.0-java.jar:system/framework/vendor.qti.qcril.am-V1.0-java.jar:qcom \
    vendor/qcom/taimen/proprietary/lib64/com.qualcomm.qti.imsrtpservice@1.0.so:system/lib64/com.qualcomm.qti.imsrtpservice@1.0.so:qcom \
    vendor/qcom/taimen/proprietary/lib64/libaptX_encoder.so:system/lib64/libaptX_encoder.so:qcom \
    vendor/qcom/taimen/proprietary/lib64/libaptXHD_encoder.so:system/lib64/libaptXHD_encoder.so:qcom \
    vendor/qcom/taimen/proprietary/lib64/libdiag_system.so:system/lib64/libdiag_system.so:qcom \
    vendor/qcom/taimen/proprietary/lib64/libimscamera_jni.so:system/lib64/libimscamera_jni.so:qcom \
    vendor/qcom/taimen/proprietary/lib64/libimsmedia_jni.so:system/lib64/libimsmedia_jni.so:qcom \
    vendor/qcom/taimen/proprietary/lib64/lib-imsvideocodec.so:system/lib64/lib-imsvideocodec.so:qcom \
    vendor/qcom/taimen/proprietary/lib64/lib-imsvtextutils.so:system/lib64/lib-imsvtextutils.so:qcom \
    vendor/qcom/taimen/proprietary/lib64/lib-imsvt.so:system/lib64/lib-imsvt.so:qcom \
    vendor/qcom/taimen/proprietary/lib64/lib-imsvtutils.so:system/lib64/lib-imsvtutils.so:qcom \
    vendor/qcom/taimen/proprietary/lib64/libiperf.so:system/lib64/libiperf.so:qcom \
    vendor/qcom/taimen/proprietary/lib64/librcc.so:system/lib64/librcc.so:qcom \
    vendor/qcom/taimen/proprietary/com.qualcomm.qti.imsrtpservice@1.0.so:system/lib/com.qualcomm.qti.imsrtpservice@1.0.so:qcom \
    vendor/qcom/taimen/proprietary/libdiag_system.so:system/lib/libdiag_system.so:qcom \
    vendor/qcom/taimen/proprietary/libimscamera_jni.so:system/lib/libimscamera_jni.so:qcom \
    vendor/qcom/taimen/proprietary/libimsmedia_jni.so:system/lib/libimsmedia_jni.so:qcom \
    vendor/qcom/taimen/proprietary/lib-imsvideocodec.so:system/lib/lib-imsvideocodec.so:qcom \
    vendor/qcom/taimen/proprietary/lib-imsvtextutils.so:system/lib/lib-imsvtextutils.so:qcom \
    vendor/qcom/taimen/proprietary/lib-imsvt.so:system/lib/lib-imsvt.so:qcom \
    vendor/qcom/taimen/proprietary/lib-imsvtutils.so:system/lib/lib-imsvtutils.so:qcom \
    vendor/qcom/taimen/proprietary/libiperf.so:system/lib/libiperf.so:qcom \
    vendor/qcom/taimen/proprietary/librcc.so:system/lib/librcc.so:qcom \
    vendor/qcom/taimen/proprietary/iperf3:system/xbin/iperf3:qcom \
    vendor/qcom/taimen/proprietary/sanitizer-status:system/xbin/sanitizer-status:qcom \
