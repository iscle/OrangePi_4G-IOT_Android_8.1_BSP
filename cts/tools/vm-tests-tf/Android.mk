# Copyright (C) 2011 The Android Open Source Project
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

# test dex library
# ============================================================
include $(CLEAR_VARS)

# custom variables used to generate test description. do not touch!
LOCAL_SRC_FILES := $(call all-java-files-under, src/dot)

LOCAL_MODULE := cts-tf-dalvik-lib
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE_TAGS := optional
LOCAL_JAVA_LIBRARIES := junit
-include cts/error_prone_rules_tests.mk
include $(BUILD_JAVA_LIBRARY)

cts-tf-dalvik-lib.jack := $(full_classes_jack)

# buildutil java library
# ============================================================
include $(CLEAR_VARS)

# custom variables used by cts/tools/utils/CollectAllTests.java to generate test description. do not touch!
LOCAL_TEST_TYPE := vmHostTest
LOCAL_JAR_PATH := android.core.vm-tests-tf.jar

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_MODULE := cts-tf-dalvik-buildutil
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE_TAGS := optional

LOCAL_JAVA_LIBRARIES := dx dasm cfassembler junit-host jsr305lib

LOCAL_CLASSPATH := $(HOST_JDK_TOOLS_JAR)

include $(BUILD_HOST_JAVA_LIBRARY)

# Buid android.core.vm-tests-tf.jar
# ============================================================
#
include $(CLEAR_VARS)

include $(BUILD_SYSTEM)/configure_local_jack.mk

LOCAL_MODULE := vm-tests-tf
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE_SUFFIX := $(COMMON_JAVA_PACKAGE_SUFFIX)
LOCAL_IS_HOST_MODULE := true
LOCAL_BUILT_MODULE_STEM := javalib.jar
intermediates := $(call local-intermediates-dir)
# Install the module as $(intermediates)/android.core.vm-tests-tf.jar.
LOCAL_INSTALLED_MODULE_STEM := android.core.vm-tests-tf.jar
LOCAL_MODULE_PATH := $(intermediates)

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

include $(BUILD_SYSTEM)/base_rules.mk

vmteststf_dep_jars := \
    $(HOST_JDK_TOOLS_JAR) \
    $(addprefix $(HOST_OUT_JAVA_LIBRARIES)/, cts-tf-dalvik-buildutil.jar dasm.jar dx.jar cfassembler.jar junit-host.jar)

$(LOCAL_BUILT_MODULE): PRIVATE_JACK_EXTRA_ARGS := $(LOCAL_JACK_EXTRA_ARGS)

ifdef LOCAL_JACK_ENABLED
    vmteststf_dep_jars += $(cts-tf-dalvik-lib.jack)
endif

$(LOCAL_BUILT_MODULE): PRIVATE_SRC_FOLDER := $(LOCAL_PATH)/src
$(LOCAL_BUILT_MODULE): PRIVATE_INTERMEDIATES_CLASSES := $(call intermediates-dir-for,JAVA_LIBRARIES,cts-tf-dalvik-buildutil,HOST)/classes
$(LOCAL_BUILT_MODULE): PRIVATE_INTERMEDIATES := $(intermediates)/tests
$(LOCAL_BUILT_MODULE): PRIVATE_INTERMEDIATES_DEXCORE_JAR := $(intermediates)/tests/dot/junit/dexcore.jar
$(LOCAL_BUILT_MODULE): PRIVATE_INTERMEDIATES_MAIN_FILES := $(intermediates)/main_files
$(LOCAL_BUILT_MODULE): PRIVATE_INTERMEDIATES_HOSTJUNIT_FILES := $(intermediates)/hostjunit_files
$(LOCAL_BUILT_MODULE): PRIVATE_CLASS_PATH := $(call normalize-path-list, $(vmteststf_dep_jars))
$(LOCAL_BUILT_MODULE): PRIVATE_JACK_VERSION := $(LOCAL_JACK_VERSION)
ifndef LOCAL_JACK_ENABLED
$(LOCAL_BUILT_MODULE) : $(vmteststf_dep_jars) $(HOST_OUT_JAVA_LIBRARIES)/tradefed.jar
	$(hide) rm -rf $(dir $@) && mkdir -p $(dir $@)
	$(hide) mkdir -p $(PRIVATE_INTERMEDIATES_HOSTJUNIT_FILES)/dot/junit $(dir $(PRIVATE_INTERMEDIATES_DEXCORE_JAR))
	# generated and compile the host side junit tests
	@echo "Write generated Main_*.java files to $(PRIVATE_INTERMEDIATES_MAIN_FILES)"
	$(hide) $(JAVA) \
	    $(if $(EXPERIMENTAL_USE_OPENJDK9),--add-exports jdk.jartool/sun.tools.jar=ALL-UNNAMED) \
	    -cp $(PRIVATE_CLASS_PATH) util.build.BuildDalvikSuite $(PRIVATE_SRC_FOLDER) $(PRIVATE_INTERMEDIATES) \
		$(HOST_OUT_JAVA_LIBRARIES)/cts-tf-dalvik-buildutil.jar:$(HOST_OUT_JAVA_LIBRARIES)/tradefed.jar \
		$(PRIVATE_INTERMEDIATES_MAIN_FILES) $(PRIVATE_INTERMEDIATES_CLASSES) $(PRIVATE_INTERMEDIATES_HOSTJUNIT_FILES) $$RUN_VM_TESTS_RTO
	@echo "Generate $(PRIVATE_INTERMEDIATES_DEXCORE_JAR)"
	$(hide) $(JAR) -cf $(PRIVATE_INTERMEDIATES_DEXCORE_JAR).jar \
		$(addprefix -C $(PRIVATE_INTERMEDIATES_CLASSES) , dot/junit/DxUtil.class dot/junit/DxAbstractMain.class)
	$(hide) $(DX) -JXms16M -JXmx768M --dex --output=$(PRIVATE_INTERMEDIATES_DEXCORE_JAR) \
		$(if $(NO_OPTIMIZE_DX), --no-optimize) $(PRIVATE_INTERMEDIATES_DEXCORE_JAR).jar && rm -f $(PRIVATE_INTERMEDIATES_DEXCORE_JAR).jar
	$(hide) cd $(PRIVATE_INTERMEDIATES_HOSTJUNIT_FILES)/classes && zip -q -r ../../$(notdir $@).jar .
	$(hide) cd $(dir $@) && zip -q -r $(notdir $@) tests
else # LOCAL_JACK_ENABLED
oj_jack := $(call intermediates-dir-for,JAVA_LIBRARIES,core-oj,,COMMON)/classes.jack
libart_jack := $(call intermediates-dir-for,JAVA_LIBRARIES,core-libart,,COMMON)/classes.jack
$(LOCAL_BUILT_MODULE): PRIVATE_DALVIK_SUITE_CLASSPATH := $(oj_jack):$(libart_jack):$(cts-tf-dalvik-lib.jack):$(HOST_OUT_JAVA_LIBRARIES)/tradefed.jar
$(LOCAL_BUILT_MODULE) : $(vmteststf_dep_jars) $(JACK) $(oj_jack) $(libart_jack) $(HOST_OUT_JAVA_LIBRARIES)/tradefed.jar | setup-jack-server
	$(hide) rm -rf $(dir $@) && mkdir -p $(dir $@)
	$(hide) mkdir -p $(PRIVATE_INTERMEDIATES_HOSTJUNIT_FILES)/dot/junit $(dir $(PRIVATE_INTERMEDIATES_DEXCORE_JAR))
	# generated and compile the host side junit tests
	@echo "Write generated Main_*.java files to $(PRIVATE_INTERMEDIATES_MAIN_FILES)"
	$(hide) JACK_VERSION=$(PRIVATE_JACK_VERSION) $(JAVA) -cp $(PRIVATE_CLASS_PATH) util.build.JackBuildDalvikSuite $(JACK) $(PRIVATE_SRC_FOLDER) $(PRIVATE_INTERMEDIATES) \
		$(PRIVATE_DALVIK_SUITE_CLASSPATH) \
		$(PRIVATE_INTERMEDIATES_MAIN_FILES) $(PRIVATE_INTERMEDIATES_CLASSES) $(PRIVATE_INTERMEDIATES_HOSTJUNIT_FILES) $$RUN_VM_TESTS_RTO
	@echo "Generate $(PRIVATE_INTERMEDIATES_DEXCORE_JAR)"
	$(hide) $(JAR) -cf $(PRIVATE_INTERMEDIATES_DEXCORE_JAR)-class.jar \
		$(addprefix -C $(PRIVATE_INTERMEDIATES_CLASSES) , dot/junit/DxUtil.class dot/junit/DxAbstractMain.class)
	$(hide) $(call call-jack) --import $(PRIVATE_INTERMEDIATES_DEXCORE_JAR)-class.jar --output-jack $(PRIVATE_INTERMEDIATES_DEXCORE_JAR).jack
	$(hide) mkdir -p $(PRIVATE_INTERMEDIATES_DEXCORE_JAR).tmp
	$(hide) $(call call-jack,$(PRIVATE_JACK_EXTRA_ARGS)) --output-dex $(PRIVATE_INTERMEDIATES_DEXCORE_JAR).tmp \
		$(if $(NO_OPTIMIZE_DX), -D jack.dex.optimize "false") --import $(PRIVATE_INTERMEDIATES_DEXCORE_JAR).jack && rm -f $(PRIVATE_INTERMEDIATES_DEXCORE_JAR).jack
	$(hide) cd $(PRIVATE_INTERMEDIATES_DEXCORE_JAR).tmp && zip -q -r $(abspath $(PRIVATE_INTERMEDIATES_DEXCORE_JAR)) .
	$(hide) cd $(PRIVATE_INTERMEDIATES_HOSTJUNIT_FILES)/classes && zip -q -r ../../$(notdir $@) .
	$(hide) cd $(dir $@) && zip -q -r $(notdir $@) tests
oj_jack :=
libart_jack :=
endif # LOCAL_JACK_ENABLED

# Clean up temp vars
intermediates :=
vmteststf_dep_jars :=

include $(call all-makefiles-under,$(LOCAL_PATH))
