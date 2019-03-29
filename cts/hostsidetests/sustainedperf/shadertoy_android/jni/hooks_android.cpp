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

#include <jni.h>

extern void Init(int width, int height);
extern void DrawFrame();
extern void Cleanup();

extern "C" {
    JNIEXPORT void JNICALL Java_com_android_gputest_GLtestLib_init(JNIEnv * env, jobject obj,  jint width, jint height);
    JNIEXPORT void JNICALL Java_com_android_gputest_GLtestLib_step(JNIEnv * env, jobject obj);
};

JNIEXPORT void JNICALL Java_com_android_gputest_GLtestLib_init(__attribute__((unused)) JNIEnv * env,__attribute__((unused)) jobject obj,  jint width, jint height)
{
    Init(width, height);
}

JNIEXPORT void JNICALL Java_com_android_gputest_GLtestLib_step(__attribute__((unused)) JNIEnv * env,__attribute__((unused)) jobject obj)
{
    DrawFrame();
}
