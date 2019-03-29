/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <android/log.h>

#include "com_android_ndkaudio_AudioRecorder.h"

#include "AudioRecorder.h"

using namespace ndkaudio;

static const char* TAG = "_com_android_ndkaudio_AudioRecorder_";

static int numChannels = 2;

static AudioRecorder* nativeRecorder;

static SLresult lastSLResult = 0;
extern "C" {

JNIEXPORT void JNICALL Java_com_android_ndkaudio_AudioRecorder_Create(JNIEnv*, jobject) {
  __android_log_print(ANDROID_LOG_INFO, TAG, "AudioRecorder_Create() ...");
  if (nativeRecorder == 0) {
      nativeRecorder = new AudioRecorder();
  }
  nativeRecorder->Open(numChannels, 0);
}

JNIEXPORT void JNICALL Java_com_android_ndkaudio_AudioRecorder_Destroy(JNIEnv*, jobject) {
  __android_log_print(ANDROID_LOG_INFO, TAG, "AudioRecorder_Destroy() ...");
  nativeRecorder->Close();
  delete nativeRecorder;
  nativeRecorder = 0;
}

JNIEXPORT void JNICALL Java_com_android_ndkaudio_AudioRecorder_RealizeRecorder(JNIEnv*, jobject) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "AudioRecorder_RealizePlayer() ...");
    nativeRecorder->RealizeRecorder();
  }

JNIEXPORT void JNICALL Java_com_android_ndkaudio_AudioRecorder_RealizeRoutingProxy(JNIEnv*, jobject) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "RealizeRoutingProxy ...");
    nativeRecorder->RealizeRoutingProxy();
  }

JNIEXPORT void JNICALL Java_com_android_ndkaudio_AudioRecorder_Start(JNIEnv *, jobject) {
  __android_log_print(ANDROID_LOG_INFO, TAG, "AudioRecorder_Start() ...");
  nativeRecorder->Start();
}

JNIEXPORT void JNICALL Java_com_android_ndkaudio_AudioRecorder_Stop(JNIEnv *, jobject) {
  __android_log_print(ANDROID_LOG_INFO, TAG, "AudioRecorder_Stop() ...");
  nativeRecorder->Stop();
}

JNIEXPORT jobject JNICALL Java_com_android_ndkaudio_AudioRecorder_GetRoutingInterface(JNIEnv*, jobject) {
  __android_log_print(ANDROID_LOG_INFO, TAG, "AudioPlayer_GetRoutingObj() ...");

  SLAndroidConfigurationItf configItf = nativeRecorder->getConfigItf();
  jobject routingObj = 0;
  lastSLResult = (*configItf)->AcquireJavaProxy(configItf, SL_ANDROID_JAVA_PROXY_ROUTING, &routingObj);
  __android_log_print(ANDROID_LOG_INFO, TAG, "  routingObj:%p", routingObj);
  return routingObj;
}

JNIEXPORT void JNICALL Java_com_android_ndkaudio_AudioRecorder_ReleaseRoutingInterface(JNIEnv*, jobject, jobject /*proxyObj*/) {
  __android_log_print(ANDROID_LOG_INFO, TAG, "AudioPlayer_ReleaseRoutingInterface() ...");

  SLAndroidConfigurationItf configItf = nativeRecorder->getConfigItf();
  lastSLResult = (*configItf)->ReleaseJavaProxy(configItf, SL_ANDROID_JAVA_PROXY_ROUTING/*, proxyObj*/);
}

JNIEXPORT jint JNICALL Java_com_android_ndkaudio_AudioRecorder_GetNumBufferSamples(JNIEnv*, jobject) {
    return nativeRecorder->GetNumBufferSamples();
}

JNIEXPORT void JNICALL Java_com_android_ndkaudio_AudioRecorder_GetBufferData(JNIEnv* jEnv, jobject, jfloatArray j_data) {
    float* dataBuffer = nativeRecorder->GetRecordBuffer();
    if (dataBuffer != 0) {
        jEnv->SetFloatArrayRegion(j_data, 0, nativeRecorder->GetNumBufferSamples(), dataBuffer);
    }
}

JNIEXPORT jlong JNICALL Java_com_android_ndkaudio_AudioRecorder_GetLastSLResult(JNIEnv*, jobject) {
    return lastSLResult;
}

JNIEXPORT void JNICALL Java_com_android_ndkaudio_AudioRecorder_ClearLastSLResult(JNIEnv*, jobject) {
    lastSLResult = 0;
}

} // extern "C"
