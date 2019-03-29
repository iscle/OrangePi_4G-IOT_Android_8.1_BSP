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

#include "com_android_ndkaudio_AudioPlayer.h"

#include "AudioPlayer.h"
#include "WaveTableGenerator.h"
#include "WaveTableOscillator.h"
#include "SystemParams.h"

static const char* TAG = "_com_android_ndkaudio_AudioPlayer_";

using namespace ndkaudio;

static int numChannels = 2;
static int waveTableSize = 0;
static float * waveTable = 0;

static WaveTableOscillator* waveTableSource;
static AudioPlayer* nativePlayer;

static SLresult lastSLResult = 0;

extern "C" {

JNIEXPORT void JNICALL Java_com_android_ndkaudio_AudioPlayer_Create(JNIEnv*, jobject) {
  __android_log_print(ANDROID_LOG_INFO, TAG, "AudioPlayer_Create() ...");

  if (nativePlayer == 0) {
      waveTableSize = SystemParams::getNumBufferFrames();
      waveTable = WaveTableGenerator::genSinWave(waveTableSize, 1.0f);
      waveTableSource = new WaveTableOscillator(numChannels, waveTable, waveTableSize);

      nativePlayer = new AudioPlayer();
      nativePlayer->Open(numChannels, waveTableSource);
  }
}

JNIEXPORT void JNICALL Java_com_android_ndkaudio_AudioPlayer_Destroy(JNIEnv*, jobject) {
  __android_log_print(ANDROID_LOG_INFO, TAG, "AudioPlayer_Destroy() ...");
  nativePlayer->Close();
}

JNIEXPORT void JNICALL Java_com_android_ndkaudio_AudioPlayer_RealizePlayer(JNIEnv*, jobject) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "AudioPlayer_RealizePlayer() ...");
    nativePlayer->RealizePlayer();
  }

JNIEXPORT void JNICALL Java_com_android_ndkaudio_AudioPlayer_RealizeRoutingProxy(JNIEnv*, jobject) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "AudioPlayer_RealizeRoutingProxy() ...");
    nativePlayer->RealizeRoutingProxy();
  }

JNIEXPORT void JNICALL Java_com_android_ndkaudio_AudioPlayer_Start(JNIEnv*, jobject) {
  __android_log_print(ANDROID_LOG_INFO, TAG, "AudioPlayer_Start() ...");
  nativePlayer->Start();
}

JNIEXPORT void JNICALL Java_com_android_ndkaudio_AudioPlayer_Stop(JNIEnv*, jobject) {
  __android_log_print(ANDROID_LOG_INFO, TAG, "AudioPlayer_Stop() ...");
  nativePlayer->Stop();
}

JNIEXPORT jobject JNICALL Java_com_android_ndkaudio_AudioPlayer_GetRoutingInterface(JNIEnv*, jobject) {
  __android_log_print(ANDROID_LOG_INFO, TAG, "AudioPlayer_GetRoutingInterface() ...");

  SLAndroidConfigurationItf configItf = nativePlayer->getConfigItf();
  __android_log_print(ANDROID_LOG_INFO, TAG, "  configItf:%p", configItf);
  jobject routingObj = 0;
  lastSLResult = (*configItf)->AcquireJavaProxy(configItf, SL_ANDROID_JAVA_PROXY_ROUTING, &routingObj);
  __android_log_print(ANDROID_LOG_INFO, TAG, "  routingObj:%p", routingObj);
  return routingObj;
}

JNIEXPORT void JNICALL Java_com_android_ndkaudio_AudioPlayer_ReleaseRoutingInterface(JNIEnv*, jobject, jobject /*proxyObj*/) {
  __android_log_print(ANDROID_LOG_INFO, TAG, "AudioPlayer_ReleaseRoutingInterface() ...");

  SLAndroidConfigurationItf configItf = nativePlayer->getConfigItf();
  lastSLResult = (*configItf)->ReleaseJavaProxy(configItf, SL_ANDROID_JAVA_PROXY_ROUTING/*, proxyObj*/);
}

JNIEXPORT jlong JNICALL Java_com_android_ndkaudio_AudioPlayer_GetLastSLResult(JNIEnv*, jobject) {
    return lastSLResult;
}

JNIEXPORT void JNICALL Java_com_android_ndkaudio_AudioPlayer_ClearLastSLResult(JNIEnv*, jobject) {
    lastSLResult = 0;
}

} // extern "C"
