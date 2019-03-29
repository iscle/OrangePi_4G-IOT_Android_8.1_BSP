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
#

# If you don't need to do a full clean build but would like to touch
# a file or delete some intermediate files, add a clean step to the end
# of the list.  These steps will only be run once, if they haven't been
# run before.
#
# E.g.:
#     $(call add-clean-step, touch -c external/sqlite/sqlite3.h)
#     $(call add-clean-step, rm -rf $(PRODUCT_OUT)/obj/STATIC_LIBRARIES/libz_intermediates)
#
# Always use "touch -c" and "rm -f" or "rm -rf" to gracefully deal with
# files that are missing or have been moved.
#
# Use $(PRODUCT_OUT) to get to the "out/target/product/blah/" directory.
# Use $(OUT_DIR) to refer to the "out" directory.
#
# If you need to re-do something that's already mentioned, just copy
# the command and add it to the bottom of the list.  E.g., if a change
# that you made last week required touching a file and a change you
# made today requires touching the same file, just copy the old
# touch step and add it to the end of the list.
#
# ************************************************
# NEWER CLEAN STEPS MUST BE AT THE END OF THE LIST
# ************************************************

# For example:
#$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/APPS/AndroidTests_intermediates)
#$(call add-clean-step, rm -rf $(OUT_DIR)/target/common/obj/JAVA_LIBRARIES/core_intermediates)
#$(call add-clean-step, find $(OUT_DIR) -type f -name "IGTalkSession*" -print0 | xargs -0 rm -f)
#$(call add-clean-step, rm -rf $(PRODUCT_OUT)/data/*)

$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/bin/init.power.sh)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/bin/init.radio.sh)

$(call add-clean-step, rm -rf $(PRODUCT_OUT)/root/init.$(PRODUCT_HARDWARE).rc)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/root/init.wahoo.usb.rc)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/root/init.$(PRODUCT_HARDWARE).diag.rc)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/root/ueventd.$(PRODUCT_HARDWARE).rc)

$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.camera.flash-autofocus.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.camera.front.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.camera.full.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.camera.raw.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.bluetooth.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.bluetooth_le.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.sensor.accelerometer.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.sensor.compass.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.sensor.gyroscope.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.sensor.light.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.sensor.proximity.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.sensor.barometer.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.sensor.stepcounter.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.sensor.stepdetector.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.sensor.hifi_sensors.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.location.gps.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.telephony.gsm.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.telephony.cdma.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.touchscreen.multitouch.jazzhand.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.wifi.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.wifi.direct.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.wifi.passpoint.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.software.sip.voip.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.usb.accessory.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.usb.host.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.opengles.aep.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.nfc.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.nfc.hce.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.nfc.hcef.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.vr.headtracking.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.vr.high_performance.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.vulkan.level.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/permissions/android.hardware.vulkan.version.xml)

$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/wifi/wpa_supplicant_overlay.conf)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/wifi/p2p_supplicant_overlay.conf)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/wifi/wifi_concurrency_cfg.txt)

$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/lib/hw/lights.$(PRODUCT_HARDWARE).so)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/lib64/hw/lights.$(PRODUCT_HARDWARE).so)

$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/sec_config)

$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/audio_policy_configuration.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/a2dp_audio_policy_configuration.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/usb_audio_policy_configuration.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/r_submix_audio_policy_configuration.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/audio_policy_volumes.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/default_volume_tables.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/mixer_paths.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/mixer_paths_tasha.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/mixer_paths_tavil.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/audio_platform_info.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/audio_platform_info_tavil.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/sound_trigger_platform_info.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/sound_trigger_mixer_paths_wcd9340.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/graphite_ipc_platform_info.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/media_codecs.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/media_codecs_performance.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/media_codecs_google_audio.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/media_codecs_google_telephony.xml)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/media_codecs_google_video.xml)

# Move /system/lib/vndk-sp to /system/lib/vndk-sp-26.1.0
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/lib/vndk-sp)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/lib64/vndk-sp)

# Revert: Move /system/lib/vndk-sp to /system/lib/vndk-sp-26.1.0
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/lib/vndk-sp-*)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/lib64/vndk-sp-*)

$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/etc/lowi.conf)

# Vibrator HAL 1.0
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/vendor/etc/init/android.hardware.vibrator@1.0-service.wahoo.rc)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/vendor/bin/hw/android.hardware.vibrator@1.0-service.wahoo)

# Broadcast Radio HAL impl 1.0
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/vendor/lib/hw/android.hardware.broadcastradio@1.0-impl.so)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/vendor/lib64/hw/android.hardware.broadcastradio@1.0-impl.so)

# Revert /system/lib[64]/vndk-sp/libz.so
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/obj/SHARED_LIBRARIES/libz.vndk-sp_*)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/symbols/system/lib/vndk-sp/libz.so)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/symbols/system/lib64/vndk-sp/libz.so)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/lib/vndk-sp/libz.so)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/lib64/vndk-sp/libz.so)

# Remove eSIM OTA image
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/priv-app/EuiccGoogle/esim.img)
$(call add-clean-step, rm -rf $(PRODUCT_OUT)/system/priv-app/EuiccGoogle/esim2.img)
