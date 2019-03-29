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

using namespace android::RSC;

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSElementTest_testCreatePixel(JNIEnv * env,
                                                                                           jclass obj,
                                                                                           jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, NULL);
    sp<RS> mRS = new RS();
    mRS->init(path);
    env->ReleaseStringUTFChars(pathObj, path);

    bool passed = true;
    passed &= (Element::createPixel(mRS,
                                    RS_TYPE_UNSIGNED_8,
                                    RS_KIND_PIXEL_A) != nullptr);
    passed &= (Element::createPixel(mRS,
                                    RS_TYPE_UNSIGNED_5_6_5,
                                    RS_KIND_PIXEL_RGB) != nullptr);
    passed &= (Element::createPixel(mRS,
                                    RS_TYPE_UNSIGNED_8,
                                    RS_KIND_PIXEL_RGB) != nullptr);
    passed &= (Element::createPixel(mRS,
                                    RS_TYPE_UNSIGNED_5_5_5_1,
                                    RS_KIND_PIXEL_RGBA) != nullptr);
    passed &= (Element::createPixel(mRS,
                                    RS_TYPE_UNSIGNED_4_4_4_4,
                                    RS_KIND_PIXEL_RGBA) != nullptr);
    passed &= (Element::createPixel(mRS,
                                    RS_TYPE_UNSIGNED_8,
                                    RS_KIND_PIXEL_RGBA) != nullptr);

    return passed;
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSElementTest_testCreateVector(JNIEnv * env,
                                                                                            jclass obj,
                                                                                            jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, NULL);
    sp<RS> mRS = new RS();
    mRS->init(path);
    env->ReleaseStringUTFChars(pathObj, path);

    bool passed = true;
    for (int len = 2; len <= 4; len ++) {
        passed &= (Element::createVector(mRS, RS_TYPE_FLOAT_32, len) != nullptr);
        passed &= (Element::createVector(mRS, RS_TYPE_FLOAT_64, len) != nullptr);
        passed &= (Element::createVector(mRS, RS_TYPE_SIGNED_8, len) != nullptr);
        passed &= (Element::createVector(mRS, RS_TYPE_SIGNED_16, len) != nullptr);
        passed &= (Element::createVector(mRS, RS_TYPE_SIGNED_32, len) != nullptr);
        passed &= (Element::createVector(mRS, RS_TYPE_SIGNED_64, len) != nullptr);
        passed &= (Element::createVector(mRS, RS_TYPE_UNSIGNED_8, len) != nullptr);
        passed &= (Element::createVector(mRS, RS_TYPE_UNSIGNED_16, len) != nullptr);
        passed &= (Element::createVector(mRS, RS_TYPE_UNSIGNED_32, len) != nullptr);
        passed &= (Element::createVector(mRS, RS_TYPE_UNSIGNED_64, len) != nullptr);
    }

    return passed;
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSElementTest_testPrebuiltElements(JNIEnv * env,
                                                                                                jclass obj,
                                                                                                jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, NULL);
    sp<RS> mRS = new RS();
    mRS->init(path);
    env->ReleaseStringUTFChars(pathObj, path);

    bool passed = true;
    passed &= (Element::A_8(mRS) != nullptr);
    passed &= (Element::RGBA_4444(mRS) != nullptr);
    passed &= (Element::RGBA_5551(mRS) != nullptr);
    passed &= (Element::RGB_565(mRS) != nullptr);
    passed &= (Element::RGB_888(mRS) != nullptr);
    passed &= (Element::RGBA_8888(mRS) != nullptr);
    passed &= (Element::F32(mRS) != nullptr);
    passed &= (Element::F32_2(mRS) != nullptr);
    passed &= (Element::F32_3(mRS) != nullptr);
    passed &= (Element::F32_4(mRS) != nullptr);
    passed &= (Element::F64(mRS) != nullptr);
    passed &= (Element::F64_2(mRS) != nullptr);
    passed &= (Element::F64_3(mRS) != nullptr);
    passed &= (Element::F64_4(mRS) != nullptr);
    passed &= (Element::I8(mRS) != nullptr);
    passed &= (Element::I8_2(mRS) != nullptr);
    passed &= (Element::I8_3(mRS) != nullptr);
    passed &= (Element::I8_4(mRS) != nullptr);
    passed &= (Element::I16(mRS) != nullptr);
    passed &= (Element::I16_2(mRS) != nullptr);
    passed &= (Element::I16_3(mRS) != nullptr);
    passed &= (Element::I16_4(mRS) != nullptr);
    passed &= (Element::I32(mRS) != nullptr);
    passed &= (Element::I32_2(mRS) != nullptr);
    passed &= (Element::I32_3(mRS) != nullptr);
    passed &= (Element::I32_4(mRS) != nullptr);
    passed &= (Element::I64(mRS) != nullptr);
    passed &= (Element::I64_2(mRS) != nullptr);
    passed &= (Element::I64_3(mRS) != nullptr);
    passed &= (Element::I64_4(mRS) != nullptr);
    passed &= (Element::U8(mRS) != nullptr);
    passed &= (Element::U8_2(mRS) != nullptr);
    passed &= (Element::U8_3(mRS) != nullptr);
    passed &= (Element::U8_4(mRS) != nullptr);
    passed &= (Element::U16(mRS) != nullptr);
    passed &= (Element::U16_2(mRS) != nullptr);
    passed &= (Element::U16_3(mRS) != nullptr);
    passed &= (Element::U16_4(mRS) != nullptr);
    passed &= (Element::U32(mRS) != nullptr);
    passed &= (Element::U32_2(mRS) != nullptr);
    passed &= (Element::U32_3(mRS) != nullptr);
    passed &= (Element::U32_4(mRS) != nullptr);
    passed &= (Element::U64(mRS) != nullptr);
    passed &= (Element::U64_2(mRS) != nullptr);
    passed &= (Element::U64_3(mRS) != nullptr);
    passed &= (Element::U64_4(mRS) != nullptr);
    passed &= (Element::MATRIX_2X2(mRS) != nullptr);
    passed &= (Element::MATRIX_3X3(mRS) != nullptr);
    passed &= (Element::MATRIX_4X4(mRS) != nullptr);
    passed &= (Element::ALLOCATION(mRS) != nullptr);
    passed &= (Element::SAMPLER(mRS) != nullptr);
    passed &= (Element::SCRIPT(mRS) != nullptr);
    passed &= (Element::TYPE(mRS) != nullptr);
    passed &= (Element::BOOLEAN(mRS) != nullptr);
    passed &= (Element::ELEMENT(mRS) != nullptr);

    return passed;
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSElementTest_testIsCompatible(JNIEnv * env,
                                                                                            jclass obj,
                                                                                            jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, NULL);
    sp<RS> mRS = new RS();
    mRS->init(path);
    env->ReleaseStringUTFChars(pathObj, path);

    bool passed = true;
    sp<const Element> ALLOCATION = Element::ALLOCATION(mRS);
    // A_8 is in U8
    sp<const Element> BOOLEAN = Element::BOOLEAN(mRS);
    sp<const Element> ELEMENT = Element::ELEMENT(mRS);
    sp<const Element> F32 = Element::F32(mRS);
    sp<const Element> F32_2 = Element::F32_2(mRS);
    sp<const Element> F32_3 = Element::F32_3(mRS);
    sp<const Element> F32_4 = Element::F32_4(mRS);
    sp<const Element> F64 = Element::F64(mRS);
    sp<const Element> I16 = Element::I16(mRS);
    sp<const Element> I32 = Element::I32(mRS);
    sp<const Element> I64 = Element::I64(mRS);
    sp<const Element> I8 = Element::I8(mRS);
    // MATRIX4X4 is in MATRIX_4X4
    sp<const Element> MATRIX_2X2 = Element::MATRIX_2X2(mRS);
    sp<const Element> MATRIX_3X3 = Element::MATRIX_3X3(mRS);
    sp<const Element> MATRIX_4X4 = Element::MATRIX_4X4(mRS);

    sp<const Element> RGBA_4444 = Element::RGBA_4444(mRS);
    sp<const Element> RGBA_5551 = Element::RGBA_5551(mRS);
    // RGBA_8888 is in U8_4
    sp<const Element> RGB_565 = Element::RGB_565(mRS);
    // RGB_888 is in U8_3
    sp<const Element> SAMPLER = Element::SAMPLER(mRS);
    sp<const Element> SCRIPT = Element::SCRIPT(mRS);
    sp<const Element> TYPE = Element::TYPE(mRS);
    sp<const Element> U16 = Element::U16(mRS);
    sp<const Element> U32 = Element::U32(mRS);
    sp<const Element> U64 = Element::U64(mRS);
    sp<const Element> U8 = Element::A_8(mRS);
    sp<const Element> U8_3 = Element::RGB_888(mRS);
    sp<const Element> U8_4 = Element::U8_4(mRS);

    int numTypes = 27;
    sp<const Element> ElementArrs[] = { ALLOCATION, BOOLEAN, ELEMENT, F32, F32_2,
                                      F32_3, F32_4, F64, I16, I32, I64, I8,
                                      MATRIX_2X2, MATRIX_3X3, MATRIX_4X4, RGBA_4444,
                                      RGBA_5551, RGB_565, SAMPLER, SCRIPT, TYPE,
                                      U16, U32, U64, U8, U8_3, U8_4 };

    for (int i = 0; i < numTypes; i++) {
        for (int j = 0; j < numTypes; j++) {
            if (i == j) {
                // Elements within a group are compatible
                passed &= (ElementArrs[i]->isCompatible(ElementArrs[j]));
            } else {
                // Elements from different groups are incompatible
                passed &= !(ElementArrs[i]->isCompatible(ElementArrs[j]));
            }
        }
    }
    return passed;
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSElementTest_testElementBuilder(JNIEnv * env,
                                                                                              jclass obj,
                                                                                              jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, NULL);
    sp<RS> mRS = new RS();
    mRS->init(path);
    env->ReleaseStringUTFChars(pathObj, path);

    bool passed = true;
    for (int arraySize = 1; arraySize <= 3; arraySize++) {
        // Now test array size
        Element::Builder *eb = new Element::Builder(mRS);
        eb->add(Element::A_8(mRS), "A_8", arraySize);
        eb->add(Element::RGBA_4444(mRS), "RGBA_4444", arraySize);
        eb->add(Element::RGBA_5551(mRS), "RGBA_5551", arraySize);
        eb->add(Element::RGB_565(mRS), "RGB_565", arraySize);
        eb->add(Element::RGB_888(mRS), "RGB_888", arraySize);
        eb->add(Element::RGBA_8888(mRS), "RGBA_8888", arraySize);
        eb->add(Element::F32(mRS), "F32", arraySize);
        eb->add(Element::F32_2(mRS), "F32_2", arraySize);
        eb->add(Element::F32_3(mRS), "F32_3", arraySize);
        eb->add(Element::F32_4(mRS), "F32_4", arraySize);
        eb->add(Element::F64(mRS), "F64", arraySize);
        eb->add(Element::F64_2(mRS), "F64_2", arraySize);
        eb->add(Element::F64_3(mRS), "F64_3", arraySize);
        eb->add(Element::F64_4(mRS), "F64_4", arraySize);
        eb->add(Element::I8(mRS), "I8", arraySize);
        eb->add(Element::I8_2(mRS), "I8_2", arraySize);
        eb->add(Element::I8_3(mRS), "I8_3", arraySize);
        eb->add(Element::I8_4(mRS), "I8_4", arraySize);
        eb->add(Element::I16(mRS), "I16", arraySize);
        eb->add(Element::I16_2(mRS), "I16_2", arraySize);
        eb->add(Element::I16_3(mRS), "I16_3", arraySize);
        eb->add(Element::I16_4(mRS), "I16_4", arraySize);
        eb->add(Element::I32(mRS), "I32", arraySize);
        eb->add(Element::I32_2(mRS), "I32_2", arraySize);
        eb->add(Element::I32_3(mRS), "I32_3", arraySize);
        eb->add(Element::I32_4(mRS), "I32_4", arraySize);
        eb->add(Element::I64(mRS), "I64", arraySize);
        eb->add(Element::I64_2(mRS), "I64_2", arraySize);
        eb->add(Element::I64_3(mRS), "I64_3", arraySize);
        eb->add(Element::I64_4(mRS), "I64_4", arraySize);
        eb->add(Element::U8(mRS), "U8", arraySize);
        eb->add(Element::U8_2(mRS), "U8_2", arraySize);
        eb->add(Element::U8_3(mRS), "U8_3", arraySize);
        eb->add(Element::U8_4(mRS), "U8_4", arraySize);
        eb->add(Element::U16(mRS), "U16", arraySize);
        eb->add(Element::U16_2(mRS), "U16_2", arraySize);
        eb->add(Element::U16_3(mRS), "U16_3", arraySize);
        eb->add(Element::U16_4(mRS), "U16_4", arraySize);
        eb->add(Element::U32(mRS), "U32", arraySize);
        eb->add(Element::U32_2(mRS), "U32_2", arraySize);
        eb->add(Element::U32_3(mRS), "U32_3", arraySize);
        eb->add(Element::U32_4(mRS), "U32_4", arraySize);
        eb->add(Element::U64(mRS), "U64", arraySize);
        eb->add(Element::U64_2(mRS), "U64_2", arraySize);
        eb->add(Element::U64_3(mRS), "U64_3", arraySize);
        eb->add(Element::U64_4(mRS), "U64_4", arraySize);
        eb->add(Element::MATRIX_2X2(mRS), "MATRIX_2X2", arraySize);
        eb->add(Element::MATRIX_3X3(mRS), "MATRIX_3X3", arraySize);
        eb->add(Element::MATRIX_4X4(mRS), "MATRIX_4X4", arraySize);
        eb->add(Element::ALLOCATION(mRS), "ALLOCATION", arraySize);
        eb->add(Element::SAMPLER(mRS), "SAMPLER", arraySize);
        eb->add(Element::SCRIPT(mRS), "SCRIPT", arraySize);
        eb->add(Element::TYPE(mRS), "TYPE", arraySize);
        eb->add(Element::BOOLEAN(mRS), "BOOLEAN", arraySize);
        eb->add(Element::ELEMENT(mRS), "ELEMENT", arraySize);
        passed &= (eb->create() != nullptr);
    }
    return passed;
}

