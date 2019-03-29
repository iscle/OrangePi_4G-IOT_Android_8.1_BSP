# Copyright (C) 2014 The Android Open Source Project
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

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_JAVA_RESOURCE_DIRS := res

LOCAL_MODULE_TAGS := optional

# tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

# Must match the package name in CtsTestCaseList.mk
LOCAL_MODULE := CtsSecurityHostTestCases

LOCAL_MODULE_CLASS := JAVA_LIBRARIES

LOCAL_JAVA_LIBRARIES := cts-tradefed tradefed compatibility-host-util

LOCAL_CTS_TEST_PACKAGE := android.host.security

selinux_plat_seapp_contexts := $(call intermediates-dir-for,ETC,plat_seapp_contexts)/plat_seapp_contexts

selinux_plat_seapp_neverallows := $(call intermediates-dir-for,ETC,plat_seapp_neverallows)/plat_seapp_neverallows

selinux_plat_file_contexts := $(call intermediates-dir-for,ETC,plat_file_contexts)/plat_file_contexts

selinux_plat_property_contexts := $(call intermediates-dir-for,ETC,plat_property_contexts)/plat_property_contexts

selinux_plat_service_contexts := $(call intermediates-dir-for,ETC,plat_service_contexts)/plat_service_contexts

LOCAL_JAVA_RESOURCE_FILES := \
    $(HOST_OUT_EXECUTABLES)/checkseapp \
    $(HOST_OUT_EXECUTABLES)/checkfc \
    $(selinux_plat_seapp_contexts) \
    $(selinux_plat_seapp_neverallows) \
    $(selinux_plat_file_contexts) \
    $(selinux_plat_property_contexts) \
    $(selinux_plat_service_contexts)

selinux_general_policy := $(call intermediates-dir-for,ETC,general_sepolicy.conf)/general_sepolicy.conf

selinux_neverallow_gen := cts/tools/selinux/SELinuxNeverallowTestGen.py

selinux_neverallow_gen_data := cts/tools/selinux/SELinuxNeverallowTestFrame.py

LOCAL_ADDITIONAL_DEPENDENCIES := $(COMPATIBILITY_TESTCASES_OUT_cts)/sepolicy-analyze

LOCAL_GENERATED_SOURCES := $(call local-generated-sources-dir)/android/cts/security/SELinuxNeverallowRulesTest.java

$(LOCAL_GENERATED_SOURCES) : PRIVATE_SELINUX_GENERAL_POLICY := $(selinux_general_policy)
$(LOCAL_GENERATED_SOURCES) : $(selinux_neverallow_gen) $(selinux_general_policy) $(selinux_neverallow_gen_data)
	mkdir -p $(dir $@)
	$< $(PRIVATE_SELINUX_GENERAL_POLICY) $@

include $(BUILD_CTS_HOST_JAVA_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))
