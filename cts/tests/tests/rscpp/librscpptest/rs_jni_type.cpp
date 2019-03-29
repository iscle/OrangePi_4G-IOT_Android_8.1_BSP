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

using namespace android::RSC;

static bool testTypeBuilderHelper(const sp<RS> &mRS, sp<const Element> e) {
    const int min = 1;
    const int max = 8;

    Type::Builder b(mRS, e);
    bool result = true;
    for (int mips = 0; mips <= 1; mips ++) {
        bool useMips = (mips == 1);
        for (int faces = 0; faces <= 1; faces++) {
            bool useFaces = (faces == 1);

            b.setMipmaps(useMips);
            b.setFaces(useFaces);
            for (int x = min; x < max; x ++) {
                for (int y = min; y < max; y ++) {
                    b.setX(x);
                    b.setY(y);
                    result &= (b.create() != nullptr);
                }
            }
        }
    }
    return result;
}


extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSTypeTest_testCreate(JNIEnv * env,
                                                                                   jclass obj,
                                                                                   jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, nullptr);
    sp<RS> mRS = new RS();
    mRS->init(path);
    env->ReleaseStringUTFChars(pathObj, path);

    bool passed = true;

    passed &= testTypeBuilderHelper(mRS, Element::A_8(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::RGB_565(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::RGB_888(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::RGBA_8888(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::F32(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::F32_2(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::F32_3(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::F32_4(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::BOOLEAN(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::F64(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::I8(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::I16(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::I32(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::I64(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::U8(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::U8_4(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::U16(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::U32(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::U64(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::MATRIX_2X2(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::MATRIX_3X3(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::MATRIX_4X4(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::ALLOCATION(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::SAMPLER(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::SCRIPT(mRS));
    passed &= testTypeBuilderHelper(mRS, Element::TYPE(mRS));

    return passed;
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSTypeTest_testGetCount(JNIEnv * env,
                                                                                     jclass obj,
                                                                                     jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, nullptr);
    sp<RS> mRS = new RS();
    mRS->init(path);
    env->ReleaseStringUTFChars(pathObj, path);

    bool passed = true;

    Type::Builder b(mRS, Element::F32(mRS));
    for (int faces = 0; faces <= 1; faces++) {
        bool useFaces = (faces == 1);
        uint32_t faceMultiplier = useFaces ? 6 : 1;
        for (int x = 1; x < 8; x ++) {
            for (int y = 1; y < 8; y ++) {
                b.setFaces(useFaces);
                b.setX(x);
                b.setY(y);
                sp<const Type> t = b.create();
                passed &= (t->getCount() == x * y * faceMultiplier);
            }
        }
    }

    // Test mipmaps
    b.setFaces(false);
    b.setMipmaps(true);
    b.setX(8);
    b.setY(1);
    sp<const Type> t = b.create();

    size_t expectedCount = 8 + 4 + 2 + 1;
    passed &= (t->getCount() == expectedCount);

    b.setX(8);
    b.setY(8);
    t = b.create();
    expectedCount = 8*8 + 4*4 + 2*2 + 1;
    passed &= (t->getCount() == expectedCount);

    b.setX(8);
    b.setY(4);
    t = b.create();
    expectedCount = 8*4 + 4*2 + 2*1 + 1;
    passed &= (t->getCount() == expectedCount);

    b.setX(4);
    b.setY(8);
    t = b.create();
    passed &= (t->getCount() == expectedCount);

    b.setX(7);
    b.setY(1);
    t = b.create();
    expectedCount = 7 + 3 + 1;
    passed &= (t->getCount() == expectedCount);

    b.setX(7);
    b.setY(3);
    t = b.create();
    expectedCount = 7*3 + 3*1 + 1;
    passed &= (t->getCount() == expectedCount);
    return passed;
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSTypeTest_testGet(JNIEnv * env,
                                                                                jclass obj,
                                                                                jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, nullptr);
    sp<RS> mRS = new RS();
    mRS->init(path);
    env->ReleaseStringUTFChars(pathObj, path);

    bool passed = true;

    sp<const Type> t = Type::create(mRS, Element::F32(mRS), 3, 4, 0);
    passed &= (t->getElement() == Element::F32(mRS));
    passed &= (t->getX() == 3);
    passed &= (t->getY() == 4);
    passed &= (t->getZ() == 0);

    Type::Builder b(mRS, Element::F32(mRS));
    b.setX(4);
    b.setY(4);
    b.setFaces(true);
    passed &= (b.create()->hasFaces());
    b.setFaces(false);
    passed &= !(b.create()->hasFaces());
    b.setMipmaps(true);
    passed &= (b.create()->hasMipmaps());
    b.setMipmaps(false);
    passed &= !(b.create()->hasMipmaps());

    return passed;
}



