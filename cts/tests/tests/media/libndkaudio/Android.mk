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
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := libndkaudioLib

LOCAL_MODULE_TAGS := optional

LOCAL_C_INCLUDES := \
  frameworks/wilhelm/include \
  frameworks/wilhelm/src/android \
  $(call include-path-for, wilhelm)

LOCAL_SRC_FILES := \
  OpenSLESUtils.cpp \
  AudioPlayer.cpp \
  AudioSource.cpp \
  PeriodicAudioSource.cpp \
  SystemParams.cpp \
  WaveTableGenerator.cpp \
  WaveTableOscillator.cpp \
  com_android_ndkaudio_AudioPlayer.cpp \
  AudioRecorder.cpp \
  com_android_ndkaudio_AudioRecorder.cpp

LOCAL_CXX_STL := libc++_static

LOCAL_SHARED_LIBRARIES := liblog libOpenSLES

LOCAL_CFLAGS := -Werror -Wall

include $(BUILD_SHARED_LIBRARY)

#
# ndkaudio - java
#
include $(CLEAR_VARS)

LOCAL_MODULE  := ndkaudio

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_CERTIFICATE := platform

include $(BUILD_STATIC_JAVA_LIBRARY)
