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

#include "ScriptC_foreach.h"
#include "ScriptC_fe_all.h"
#include "ScriptC_noroot.h"

using namespace android::RSC;

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSForEachTest_testForEach(JNIEnv * env,
                                                                                       jclass obj,
                                                                                       jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, NULL);
    sp<RS> mRS = new RS();
    mRS->init(path);
    env->ReleaseStringUTFChars(pathObj, path);

    bool passed = true;
    int x = 7;

    sp<ScriptC_fe_all> fe_all = new ScriptC_fe_all(mRS);
    sp<const Type> t = Type::create(mRS, Element::I8(mRS), x, 0, 0);

    // I8
    sp<Allocation> in = Allocation::createTyped(mRS, t);
    t = Type::create(mRS, Element::U8(mRS), x, 0, 0);
    sp<Allocation> out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_i8(in, out);
    mRS->finish();

    // I8_2
    t = Type::create(mRS, Element::I8_2(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    t = Type::create(mRS, Element::U8_2(mRS), x, 0, 0);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_i8_2(in, out);
    mRS->finish();

    // I8_3
    t = Type::create(mRS, Element::I8_3(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    t = Type::create(mRS, Element::U8_3(mRS), x, 0, 0);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_i8_3(in, out);
    mRS->finish();

    // I8_4
    t = Type::create(mRS, Element::I8_4(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    t = Type::create(mRS, Element::U8_4(mRS), x, 0, 0);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_i8_4(in, out);
    mRS->finish();

    // I16
    t = Type::create(mRS, Element::I16(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    t = Type::create(mRS, Element::U16(mRS), x, 0, 0);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_i16(in, out);
    mRS->finish();

    // I16_2
    t = Type::create(mRS, Element::I16_2(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    t = Type::create(mRS, Element::U16_2(mRS), x, 0, 0);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_i16_2(in, out);
    mRS->finish();

    // I16_3
    t = Type::create(mRS, Element::I16_3(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    t = Type::create(mRS, Element::U16_3(mRS), x, 0, 0);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_i16_3(in, out);
    mRS->finish();

    // I16_4
    t = Type::create(mRS, Element::I16_4(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    t = Type::create(mRS, Element::U16_4(mRS), x, 0, 0);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_i16_4(in, out);
    mRS->finish();

    // I32
    t = Type::create(mRS, Element::I32(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    t = Type::create(mRS, Element::U32(mRS), x, 0, 0);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_i32(in, out);
    mRS->finish();

    // I32_2
    t = Type::create(mRS, Element::I32_2(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    t = Type::create(mRS, Element::U32_2(mRS), x, 0, 0);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_i32_2(in, out);
    mRS->finish();

    // I32_3
    t = Type::create(mRS, Element::I32_3(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    t = Type::create(mRS, Element::U32_3(mRS), x, 0, 0);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_i32_3(in, out);
    mRS->finish();

    // I32_4
    t = Type::create(mRS, Element::I32_4(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    t = Type::create(mRS, Element::U32_4(mRS), x, 0, 0);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_i32_4(in, out);
    mRS->finish();

    // I64
    t = Type::create(mRS, Element::I64(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    t = Type::create(mRS, Element::U64(mRS), x, 0, 0);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_i64(in, out);
    mRS->finish();

    // I64_2
    t = Type::create(mRS, Element::I64_2(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    t = Type::create(mRS, Element::U64_2(mRS), x, 0, 0);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_i64_2(in, out);
    mRS->finish();

    // I64_3
    t = Type::create(mRS, Element::I64_3(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    t = Type::create(mRS, Element::U64_3(mRS), x, 0, 0);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_i64_3(in, out);
    mRS->finish();

    // I64_4
    t = Type::create(mRS, Element::I64_4(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    t = Type::create(mRS, Element::U64_4(mRS), x, 0, 0);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_i64_4(in, out);
    mRS->finish();

    // F32
    t = Type::create(mRS, Element::F32(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_f32(in, out);
    mRS->finish();

    // F32_2
    t = Type::create(mRS, Element::F32_2(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    t = Type::create(mRS, Element::F32_2(mRS), x, 0, 0);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_f32_2(in, out);
    mRS->finish();

    // F32_3
    t = Type::create(mRS, Element::F32_3(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_f32_3(in, out);
    mRS->finish();

    // F32_4
    t = Type::create(mRS, Element::F32_4(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_f32_4(in, out);
    mRS->finish();

    // F64
    t = Type::create(mRS, Element::F64(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_f64(in, out);
    mRS->finish();

    // F64_2
    t = Type::create(mRS, Element::F64_2(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_f64_2(in, out);
    mRS->finish();

    // F64_3
    t = Type::create(mRS, Element::F64_3(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_f64_3(in, out);
    mRS->finish();

    // F64_4
    t = Type::create(mRS, Element::F64_4(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_f64_4(in, out);
    mRS->finish();

    // BOOLEAN
    t = Type::create(mRS, Element::BOOLEAN(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_bool(in, out);
    mRS->finish();

    // A_8
    t = Type::create(mRS, Element::I8(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    t = Type::create(mRS, Element::A_8(mRS), x, 0, 0);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_i8(in, out);
    mRS->finish();

    // RGBA_8888
    t = Type::create(mRS, Element::I8_4(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    t = Type::create(mRS, Element::RGBA_8888(mRS), x, 0, 0);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_i8_4(in, out);
    mRS->finish();

    // RGB_888
    t = Type::create(mRS, Element::I8_3(mRS), x, 0, 0);
    in = Allocation::createTyped(mRS, t);
    t = Type::create(mRS, Element::RGB_888(mRS), x, 0, 0);
    out = Allocation::createTyped(mRS, t);
    fe_all->forEach_test_i8_3(in, out);
    mRS->finish();

    return passed;
}

#define RS_MSG_TEST_PASSED 100
#define RS_MSG_TEST_FAILED 101

static int result = 0;
static void rsMsgHandler(uint32_t msgNum, const void *msgData, size_t msgLen) {
    if (result == 0) {
        result = msgNum;
    }
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSForEachTest_testMultipleForEach(JNIEnv * env,
                                                                                               jclass obj,
                                                                                               jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, NULL);
    sp<RS> mRS = new RS();
    mRS->init(path);
    env->ReleaseStringUTFChars(pathObj, path);
    MessageHandlerFunc_t mHandler = rsMsgHandler;
    mRS->setMessageHandler(mHandler);

    bool passed = true;
    sp<ScriptC_foreach> s = new ScriptC_foreach(mRS);

    int X = 5;
    int Y = 7;
    s->set_dimX(X);
    s->set_dimY(Y);
    sp<const Type> t = Type::create(mRS, Element::I32(mRS), X, Y, 0);
    sp<Allocation> A = Allocation::createTyped(mRS, t);
    s->set_aRaw(A);
    s->forEach_root(A);
    s->invoke_verify_root();
    s->forEach_foo(A, A);
    s->invoke_verify_foo();
    s->invoke_foreach_test();
    mRS->finish();
    if (result == RS_MSG_TEST_FAILED) {
        passed = false;
    }
    result = 0;
    return passed;
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSForEachTest_testNoRoot(JNIEnv * env,
                                                                                      jclass obj,
                                                                                      jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, NULL);
    sp<RS> mRS = new RS();
    mRS->init(path);
    env->ReleaseStringUTFChars(pathObj, path);
    MessageHandlerFunc_t mHandler = rsMsgHandler;
    mRS->setMessageHandler(mHandler);

    bool passed = true;
    sp<ScriptC_noroot> s = new ScriptC_noroot(mRS);

    int X = 5;
    int Y = 7;
    s->set_dimX(X);
    s->set_dimY(Y);
    sp<const Type> t = Type::create(mRS, Element::I32(mRS), X, Y, 0);
    sp<Allocation> A = Allocation::createTyped(mRS, t);
    s->set_aRaw(A);
    s->forEach_foo(A, A);
    s->invoke_verify_foo();
    s->invoke_noroot_test();
    mRS->finish();
    if (result == RS_MSG_TEST_FAILED) {
        passed = false;
    }
    result = 0;
    return passed;
}

