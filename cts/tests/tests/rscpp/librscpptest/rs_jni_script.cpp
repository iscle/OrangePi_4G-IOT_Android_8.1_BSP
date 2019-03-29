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

#include <ScriptC_primitives.h>
#include <ScriptC_instance.h>
#include <ScriptC_vector.h>

using namespace android::RSC;

#define RS_MSG_TEST_PASSED 100
#define RS_MSG_TEST_FAILED 101

static int result = 0;
static void rsMsgHandler(uint32_t msgNum, const void *msgData, size_t msgLen) {
    if (result == 0) {
        result = msgNum;
    }
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSScriptTest_testSet(JNIEnv * env,
                                                                                  jclass obj,
                                                                                  jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, nullptr);
    sp<RS> mRS = new RS();
    mRS->init(path);
    env->ReleaseStringUTFChars(pathObj, path);
    MessageHandlerFunc_t mHandler = rsMsgHandler;
    mRS->setMessageHandler(mHandler);

    bool passed = true;

    sp<const Type> t = Type::create(mRS, Element::I32(mRS), 8, 0, 0);
    sp<Allocation> alloc = Allocation::createTyped(mRS, t);

    sp<ScriptC_primitives> script = new ScriptC_primitives(mRS);
    script->set_floatTest(2.99f);  // floatTest
    script->set_doubleTest(3.05);  // doubleTest
    script->set_charTest(-16);  // charTest
    script->set_shortTest(-32);  // shortTest
    script->set_intTest(-64);  // intTest
    script->set_longTest(17179869185l);  // longTest
    script->set_longlongTest(68719476735L); //longlongTest
    script->set_ulongTest(4611686018427387903L);  // boolTest
    script->set_uint64_tTest(117179869185l); //uint64_tTest
    script->set_allocationTest(alloc);  // allocationTest
    
    script->invoke_test_primitive_types();
    mRS->finish();
    if (result == RS_MSG_TEST_FAILED) {
        passed = false;
    }

    return passed;
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSScriptTest_testInstance(JNIEnv * env,
                                                                                       jclass obj,
                                                                                       jstring pathObj)
{
    /**
     * Test script instancing.
     */
    const char * path = env->GetStringUTFChars(pathObj, nullptr);
    sp<RS> mRS = new RS();
    mRS->init(path);
    env->ReleaseStringUTFChars(pathObj, path);
    MessageHandlerFunc_t mHandler = rsMsgHandler;
    mRS->setMessageHandler(mHandler);

    bool passed = true;

    sp<const Type> t = Type::create(mRS, Element::I32(mRS), 1, 0, 0);
    sp<Allocation> ai1 = Allocation::createTyped(mRS, t);
    sp<Allocation> ai2 = Allocation::createTyped(mRS, t);
    sp<ScriptC_instance> instance_1 = new ScriptC_instance(mRS);
    sp<ScriptC_instance> instance_2 = new ScriptC_instance(mRS);

    instance_1->set_i(1);
    instance_2->set_i(2);
    instance_1->set_ai(ai1);
    instance_2->set_ai(ai2);

    // We now check to ensure that the global is not being shared across
    // our separate script instances. Our invoke here merely sets the
    // instanced allocation with the instanced global variable's value.
    // If globals are being shared (i.e. not instancing scripts), then
    // both instanced allocations will have the same resulting value
    // (depending on the order in which the invokes complete).
    instance_1->invoke_instance_test();
    instance_2->invoke_instance_test();

    int i1[1];
    int i2[1];

    ai1->copy1DTo(i1);
    ai2->copy1DTo(i2);

    // 3-step check ensures that a fortunate race condition wouldn't let us
    // pass accidentally.
    passed &= (2 == i2[0]);
    passed &= (1 == i1[0]);
    passed &= (2 == i2[0]);
    mRS->finish();
    if (result == RS_MSG_TEST_FAILED) {
        passed = false;
    }

    return passed;
}

// Define some reasonable types for use with the vector invoke testing.
typedef unsigned char uchar;
typedef unsigned short ushort;
typedef unsigned int uint;
typedef unsigned long ulong;

#define TEST_VECTOR_INVOKE(L, U) \
L temp_##L = 0; \
vector->invoke_vector_test_##L(temp_##L); \
U##2 temp_##L##2; \
vector->invoke_vector_test_##L##2(temp_##L##2); \
U##3 temp_##L##3; \
vector->invoke_vector_test_##L##3(temp_##L##3); \
U##4 temp_##L##4; \
vector->invoke_vector_test_##L##4(temp_##L##4);


/*
 * Test that vector invoke C++ reflection is working/present.
 */
extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSScriptTest_testVector(JNIEnv * env,
                                                                                     jclass obj,
                                                                                     jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, nullptr);
    sp<RS> mRS = new RS();
    mRS->init(path);
    env->ReleaseStringUTFChars(pathObj, path);
    MessageHandlerFunc_t mHandler = rsMsgHandler;
    mRS->setMessageHandler(mHandler);

    bool passed = true;
    sp<ScriptC_vector> vector = new ScriptC_vector(mRS);

    TEST_VECTOR_INVOKE(float, Float)
    TEST_VECTOR_INVOKE(double, Double)
    TEST_VECTOR_INVOKE(char, Byte)
    TEST_VECTOR_INVOKE(uchar, UByte)
    TEST_VECTOR_INVOKE(short, Short)
    TEST_VECTOR_INVOKE(ushort, UShort)
    TEST_VECTOR_INVOKE(int, Int)
    TEST_VECTOR_INVOKE(uint, UInt)
    TEST_VECTOR_INVOKE(long, Long)
    TEST_VECTOR_INVOKE(ulong, ULong)

    return passed;
}
