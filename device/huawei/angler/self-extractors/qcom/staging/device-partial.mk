# Copyright 2014 The Android Open Source Project
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

# Qualcomm blob(s) necessary for Shamu hardware
PRODUCT_COPY_FILES := \
    vendor/qcom/angler/proprietary/diag_qshrink4_daemon:system/bin/diag_qshrink4_daemon:qcom \
    vendor/qcom/angler/proprietary/mm-audio-alsa-test:system/bin/mm-audio-alsa-test:qcom \
    vendor/qcom/angler/proprietary/PktRspTest:system/bin/PktRspTest:qcom \
    vendor/qcom/angler/proprietary/check_system_health:system/bin/qmi-framework-tests/check_system_health:qcom \
    vendor/qcom/angler/proprietary/qmi_ping_clnt_test_0000:system/bin/qmi-framework-tests/qmi_ping_clnt_test_0000:qcom \
    vendor/qcom/angler/proprietary/qmi_ping_clnt_test_0001:system/bin/qmi-framework-tests/qmi_ping_clnt_test_0001:qcom \
    vendor/qcom/angler/proprietary/qmi_ping_clnt_test_1000:system/bin/qmi-framework-tests/qmi_ping_clnt_test_1000:qcom \
    vendor/qcom/angler/proprietary/qmi_ping_clnt_test_1001:system/bin/qmi-framework-tests/qmi_ping_clnt_test_1001:qcom \
    vendor/qcom/angler/proprietary/qmi_ping_clnt_test_2000:system/bin/qmi-framework-tests/qmi_ping_clnt_test_2000:qcom \
    vendor/qcom/angler/proprietary/qmi_ping_svc:system/bin/qmi-framework-tests/qmi_ping_svc:qcom \
    vendor/qcom/angler/proprietary/qmi_ping_test:system/bin/qmi-framework-tests/qmi_ping_test:qcom \
    vendor/qcom/angler/proprietary/qmi_test_mt_client_init_instance:system/bin/qmi-framework-tests/qmi_test_mt_client_init_instance:qcom \
    vendor/qcom/angler/proprietary/qmi_test_service_clnt_test_0000:system/bin/qmi-framework-tests/qmi_test_service_clnt_test_0000:qcom \
    vendor/qcom/angler/proprietary/qmi_test_service_clnt_test_0001:system/bin/qmi-framework-tests/qmi_test_service_clnt_test_0001:qcom \
    vendor/qcom/angler/proprietary/qmi_test_service_clnt_test_1000:system/bin/qmi-framework-tests/qmi_test_service_clnt_test_1000:qcom \
    vendor/qcom/angler/proprietary/qmi_test_service_clnt_test_1001:system/bin/qmi-framework-tests/qmi_test_service_clnt_test_1001:qcom \
    vendor/qcom/angler/proprietary/qmi_test_service_clnt_test_2000:system/bin/qmi-framework-tests/qmi_test_service_clnt_test_2000:qcom \
    vendor/qcom/angler/proprietary/qmi_test_service_test:system/bin/qmi-framework-tests/qmi_test_service_test:qcom \
    vendor/qcom/angler/proprietary/qmi_simple_ril_test:system/bin/qmi_simple_ril_test:qcom \
    vendor/qcom/angler/proprietary/ssr_setup:system/bin/ssr_setup:qcom \
    vendor/qcom/angler/proprietary/subsystem_ramdump:system/bin/subsystem_ramdump:qcom \
    vendor/qcom/angler/proprietary/test_diag:system/bin/test_diag:qcom \
    vendor/qcom/angler/proprietary/cneapiclient.xml:system/etc/permissions/cneapiclient.xml:qcom \
    vendor/qcom/angler/proprietary/embms.xml:system/etc/permissions/embms.xml:qcom \
    vendor/qcom/angler/proprietary/qcrilhook.xml:system/etc/permissions/qcrilhook.xml:qcom \
    vendor/qcom/angler/proprietary/rcsservice.xml:system/etc/permissions/rcsservice.xml:qcom \
    vendor/qcom/angler/proprietary/embmslibrary.jar:system/framework/embmslibrary.jar:qcom \
    vendor/qcom/angler/proprietary/qcrilhook.jar:system/framework/qcrilhook.jar:qcom \
    vendor/qcom/angler/proprietary/lib64/libiperf.so:system/lib64/libiperf.so:qcom \
    vendor/qcom/angler/proprietary/libiperf.so:system/lib/libiperf.so:qcom \
    vendor/qcom/angler/proprietary/libmm-qcamera.so:system/lib/libmm-qcamera.so:qcom \
    vendor/qcom/angler/proprietary/iperf3:system/xbin/iperf3:qcom \
    vendor/qcom/angler/proprietary/lib64/android.hardware.biometrics.fingerprint@2.1.so:system/lib64/android.hardware.biometrics.fingerprint@2.1.so:qcom \
    vendor/qcom/angler/proprietary/lib64/android.hardware.configstore@1.0.so:system/lib64/android.hardware.configstore@1.0.so:qcom \
    vendor/qcom/angler/proprietary/lib64/android.hardware.usb@1.0.so:system/lib64/android.hardware.usb@1.0.so:qcom \
    vendor/qcom/angler/proprietary/lib64/android.hardware.wifi@1.0.so:system/lib64/android.hardware.wifi@1.0.so:qcom \
