/*
 * Copyright (C) 2015 The Android Open Source Project
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
#include <android/log.h>

#include <stdio.h>
#include <stdlib.h>
#include <math.h>

#include <RenderScript.h>

#define  LOG_TAG    "rscpptest"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#include <ScriptC_clear_object.h>

using namespace android::RSC;

#define ObjectNum 1

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSObjectTest_testClearObjectElement(JNIEnv * env,
                                                                                                 jclass obj,
                                                                                                 jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, nullptr);
    sp<RS> mRS = new RS();
    mRS->init(path);
    env->ReleaseStringUTFChars(pathObj, path);

    bool passed = true;

    sp<ScriptC_clear_object> ms_clear = new ScriptC_clear_object(mRS);

    sp<const Element> element = Element::BOOLEAN(mRS);
    sp<Allocation> mOut = Allocation::createSized(mRS, Element::I32(mRS), ObjectNum);
    ms_clear->set_element(element);
    ms_clear->forEach_clear_element(mOut);

    int tmpArray[ObjectNum];
    mOut->copy1DTo(tmpArray);

    for(int i = 0; i < ObjectNum; i++) {
        passed &= (tmpArray[i] == 1);
    }

    return passed;
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSObjectTest_testClearObjectType(JNIEnv * env,
                                                                                        jclass obj,
                                                                                        jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, nullptr);
    sp<RS> mRS = new RS();
    mRS->init(path);
    env->ReleaseStringUTFChars(pathObj, path);

    bool passed = true;

    sp<ScriptC_clear_object> ms_clear = new ScriptC_clear_object(mRS);

    sp<const Type> type= Type::create(mRS, Element::I8(mRS), 1, 0, 0);
    sp<Allocation> mOut = Allocation::createSized(mRS, Element::I32(mRS), ObjectNum);
    ms_clear->set_type(type);
    ms_clear->forEach_clear_type(mOut);

    int tmpArray[ObjectNum];
    mOut->copy1DTo(tmpArray);

    for(int i = 0; i < ObjectNum; i++) {
        passed &= (tmpArray[i] == 1);
    }

    return passed;
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSObjectTest_testClearObjectAllocation(JNIEnv * env,
                                                                                        jclass obj,
                                                                                        jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, nullptr);
    sp<RS> mRS = new RS();
    mRS->init(path);
    env->ReleaseStringUTFChars(pathObj, path);

    bool passed = true;

    sp<ScriptC_clear_object> ms_clear = new ScriptC_clear_object(mRS);

    sp<Allocation> mOut = Allocation::createSized(mRS, Element::I32(mRS), ObjectNum);
    sp<Allocation> mIn = Allocation::createSized(mRS, Element::I32(mRS), ObjectNum);
    sp<Allocation> allocation = Allocation::createTyped(mRS, mIn->getType());
    ms_clear->set_allocation(allocation);
    ms_clear->forEach_clear_allocation(mOut);

    int tmpArray[ObjectNum];
    mOut->copy1DTo(tmpArray);

    for(int i = 0; i < ObjectNum; i++) {
        passed &= (tmpArray[i] == 1);
    }

    return passed;
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSObjectTest_testClearObjectSampler(JNIEnv * env,
                                                                                        jclass obj,
                                                                                        jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, nullptr);
    sp<RS> mRS = new RS();
    mRS->init(path);
    env->ReleaseStringUTFChars(pathObj, path);

    bool passed = true;

    sp<ScriptC_clear_object> ms_clear = new ScriptC_clear_object(mRS);

    sp<Sampler> sampler = Sampler::create(mRS, RS_SAMPLER_NEAREST, RS_SAMPLER_NEAREST,
                                          RS_SAMPLER_WRAP, RS_SAMPLER_WRAP, 1.0f);
    sp<Allocation> mOut = Allocation::createSized(mRS, Element::I32(mRS), ObjectNum);
    ms_clear->set_sampler(sampler);
    ms_clear->forEach_clear_sampler(mOut);

    int tmpArray[ObjectNum];
    mOut->copy1DTo(tmpArray);

    for(int i = 0; i < ObjectNum; i++) {
        passed &= (tmpArray[i] == 1);
    }


    return passed;
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSObjectTest_testClearObjectScript(JNIEnv * env,
                                                                                        jclass obj,
                                                                                        jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, nullptr);
    sp<RS> mRS = new RS();
    mRS->init(path);
    env->ReleaseStringUTFChars(pathObj, path);

    bool passed = true;

    sp<ScriptC_clear_object> ms_clear = new ScriptC_clear_object(mRS);

    sp<Script> script = new ScriptC_clear_object(mRS);
    sp<Allocation> mOut = Allocation::createSized(mRS, Element::I32(mRS), ObjectNum);
    ms_clear->set_script(script);
    ms_clear->forEach_clear_script(mOut);

    int tmpArray[ObjectNum];
    mOut->copy1DTo(tmpArray);

    for(int i = 0; i < ObjectNum; i++) {
        passed &= (tmpArray[i] == 1);
    }

    return passed;
}
