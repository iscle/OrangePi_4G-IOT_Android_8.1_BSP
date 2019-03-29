/*
 * Copyright (C) 2013 The Android Open Source Project
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

#include "ScriptC_setelementat.h"

using namespace android::RSC;

static void createTypedHelper (const sp<RS> &rs, sp<const Element> e) {
    Type::Builder typeBuilder(rs, e);
    for (int mips = 0; mips <= 1; mips ++) {
        bool useMips = (mips == 1);

        for (int faces = 0; faces <= 1; faces++) {
            bool useFaces = (faces == 1);

            for (uint32_t x = 1; x < 8; x ++) {
                for (uint32_t y = 1; y < 8; y ++) {
                    typeBuilder.setMipmaps(useMips);
                    typeBuilder.setFaces(useFaces);
                    typeBuilder.setX(x);
                    typeBuilder.setY(y);
                    Allocation::createTyped(rs, typeBuilder.create());
                }
            }
        }
    }

}

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSAllocationTest_typedTest(JNIEnv * env,
                                                                                        jclass obj,
                                                                                        jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, NULL);
    sp<RS> rs = new RS();
    rs->init(path);
    env->ReleaseStringUTFChars(pathObj, path);

    createTypedHelper(rs, Element::A_8(rs));
    createTypedHelper(rs, Element::RGBA_4444(rs));
    createTypedHelper(rs, Element::RGBA_5551(rs));
    createTypedHelper(rs, Element::RGB_565(rs));
    createTypedHelper(rs, Element::RGB_888(rs));
    createTypedHelper(rs, Element::RGBA_8888(rs));
    createTypedHelper(rs, Element::F32(rs));
    createTypedHelper(rs, Element::F32_2(rs));
    createTypedHelper(rs, Element::F32_3(rs));
    createTypedHelper(rs, Element::F32_4(rs));
    createTypedHelper(rs, Element::F64(rs));
    createTypedHelper(rs, Element::F64_2(rs));
    createTypedHelper(rs, Element::F64_3(rs));
    createTypedHelper(rs, Element::F64_4(rs));
    createTypedHelper(rs, Element::I8(rs));
    createTypedHelper(rs, Element::I8_2(rs));
    createTypedHelper(rs, Element::I8_3(rs));
    createTypedHelper(rs, Element::I8_4(rs));
    createTypedHelper(rs, Element::I16(rs));
    createTypedHelper(rs, Element::I16_2(rs));
    createTypedHelper(rs, Element::I16_3(rs));
    createTypedHelper(rs, Element::I16_4(rs));
    createTypedHelper(rs, Element::I32(rs));
    createTypedHelper(rs, Element::I32_2(rs));
    createTypedHelper(rs, Element::I32_3(rs));
    createTypedHelper(rs, Element::I32_4(rs));
    createTypedHelper(rs, Element::I64(rs));
    createTypedHelper(rs, Element::I64_2(rs));
    createTypedHelper(rs, Element::I64_3(rs));
    createTypedHelper(rs, Element::I64_4(rs));
    createTypedHelper(rs, Element::U8(rs));
    createTypedHelper(rs, Element::U8_2(rs));
    createTypedHelper(rs, Element::U8_3(rs));
    createTypedHelper(rs, Element::U8_4(rs));
    createTypedHelper(rs, Element::U16(rs));
    createTypedHelper(rs, Element::U16_2(rs));
    createTypedHelper(rs, Element::U16_3(rs));
    createTypedHelper(rs, Element::U16_4(rs));
    createTypedHelper(rs, Element::U32(rs));
    createTypedHelper(rs, Element::U32_2(rs));
    createTypedHelper(rs, Element::U32_3(rs));
    createTypedHelper(rs, Element::U32_4(rs));
    createTypedHelper(rs, Element::U64(rs));
    createTypedHelper(rs, Element::U64_2(rs));
    createTypedHelper(rs, Element::U64_3(rs));
    createTypedHelper(rs, Element::U64_4(rs));
    createTypedHelper(rs, Element::MATRIX_2X2(rs));
    createTypedHelper(rs, Element::MATRIX_3X3(rs));
    createTypedHelper(rs, Element::MATRIX_4X4(rs));
    createTypedHelper(rs, Element::SAMPLER(rs));
    createTypedHelper(rs, Element::SCRIPT(rs));
    createTypedHelper(rs, Element::TYPE(rs));
    createTypedHelper(rs, Element::BOOLEAN(rs));
    createTypedHelper(rs, Element::ELEMENT(rs));
    createTypedHelper(rs, Element::ALLOCATION(rs));

    rs->finish();
    return true;
}

static sp<const Element> makeElement(const sp<RS> &rs, RsDataType dt, int vecSize) {
    if (vecSize > 1) {
        return Element::createVector(rs, dt, vecSize);
    } else {
        return Element::createUser(rs, dt);
    }
}

/**
 * Test copyTo and copyFrom for all or part of a 1D Allocation.
 *
 * @param rs RS Context.
 * @param cellCount Total number of elements in this Allocation.
 * @param offset Offset of this Allocation for copy.
 * @param count Number of elements need to copy.
 * @param copyRange Copy the entire allocation or part of it (using different API).
 * @param dt DataType intended to test.
 * @param autoPadding Enable autoPadding or not. 
*/
template <class T>
static bool helperCopy1D(const sp<RS> &rs, int cellCount, int offset, int count, bool copyRange,
                         RsDataType dt, bool autoPadding = false) {
    bool passed = true;
    int arrLen = cellCount;
    int copyCount = count;
    int iOffset = offset;
    sp<Allocation> alloc = nullptr;

    if (autoPadding) {
        arrLen = cellCount * 3;
        copyCount = count * 3;
        iOffset = offset * 3;
        alloc = Allocation::createSized(rs, makeElement(rs, dt, 3), cellCount);
        alloc->setAutoPadding(autoPadding);
    } else {
        alloc = Allocation::createSized(rs, makeElement(rs, dt, 1), cellCount);
    }

    T* src = new T[arrLen];
    T* dst = new T[arrLen];

    for (int i = 0; i < copyCount; i++) {
        src[i] = (T)rand();
        dst[iOffset + i] = (T)(-1);
    }

    if (!copyRange) {
        alloc->copy1DFrom(src);
    } else {
        alloc->copy1DRangeFrom(offset, count, src);
    }
    alloc->copy1DTo(dst);

    for (int i = 0; i < copyCount; i++) {
        if (dst[iOffset + i] != src[i]) {
            passed = false;
            break;
        }
    }

    delete[] src;
    delete[] dst;
    return passed;
}

//Corresponding 1D allocation to allocation copy.
static bool helperFloatAllocationCopy1D(const sp<RS> &rs, int cellCount, int offset, int count) {

    bool passed = true;
    sp<Allocation> srcA = Allocation::createSized(rs, Element::F32(rs), cellCount);
    sp<Allocation> dstA = Allocation::createSized(rs, Element::F32(rs), cellCount);

    float *src, *dst;
    src = new float[cellCount];
    dst = new float[cellCount];
    for (int i = 0; i < cellCount; i++) {
        src[i] = (float)rand();
        dst[i] = -1.0f;
    }

    // First populate the source allocation
    srcA->copy1DFrom(src);
    // Now test allocation to allocation copy
    dstA->copy1DRangeFrom(offset, count, srcA, offset);
    dstA->copy1DTo(dst);

    for (int i = 0; i < count; i++) {
        if (dst[offset + i] != src[offset + i]) {
            passed = false;
            break;
        }
    }

    delete[] src;
    delete[] dst;
    return passed;
}

/**
 * Test copyTo and copyFrom for all or part of a 2D Allocation.
 *
 * @param rs RS Context.
 * @param xElems Number of elements in X dimension in this Allocation.
 * @param yElems Number of elements in Y dimension in this Allocation.
 * @param xOffset Offset in X dimension of this Allocation for copy.
 * @param yOffset Offset in Y dimension of this Allocation for copy.
 * @param xCount Number of elements in X dimension need to copy.
 * @param yCount Number of elements in Y dimension need to copy.
 * @param dt DataType intended to test.
 * @param autoPadding Enable autoPadding or not. 
*/
template <class T>
static bool helperCopy2D(const sp<RS> &rs, int xElems, int yElems,
                         int xOffset, int yOffset, int xCount, int yCount,
                         RsDataType dt, bool autoPadding = false) {
    bool passed = true;
    int arrLen = xElems * yElems;
    int copyCount = xCount * yCount;
    sp<Allocation> alloc = nullptr;

    if (autoPadding) {
        arrLen = arrLen * 3;
        copyCount = copyCount * 3;
        alloc = Allocation::createSized2D(rs, makeElement(rs, dt, 3), xElems, yElems);
        alloc->setAutoPadding(autoPadding);
    } else {
        alloc = Allocation::createSized2D(rs, makeElement(rs, dt, 1), xElems, yElems);
    }

    T* src = new T[arrLen];
    T* dst = new T[arrLen];

    for (int i = 0; i < copyCount; i++) {
        src[i] = (T)rand();
        dst[i] = (T)(-1);
    }

    alloc->copy2DRangeFrom(xOffset, yOffset, xCount, yCount, src);
    alloc->copy2DRangeTo(xOffset, yOffset, xCount, yCount, dst);

    for (int i = 0; i < copyCount; i++) {
        if (dst[i] != src[i]) {
            passed = false;
            break;
        }
    }

    delete[] src;
    delete[] dst;
    return passed;
}

//Corresponding 2D allocation to allocation copy.
static bool helperFloatAllocationCopy2D(const sp<RS> &rs, int xElems, int yElems,
                                        int xOffset, int yOffset, int xCount, int yCount) {

    bool passed = true;
    sp<Allocation> srcA = Allocation::createSized2D(rs, Element::F32(rs), xElems, yElems);
    sp<Allocation> dstA = Allocation::createSized2D(rs, Element::F32(rs), xElems, yElems);

    float *src, *dst;
    src = new float[xElems * yElems];
    dst = new float[xElems * yElems];
    for (int i = 0; i < xCount * yCount; i++) {
        src[i] = (float)rand();
        dst[i] = -1.0f;
    }

    // First populate the source allocation
    srcA->copy2DRangeFrom(xOffset, yOffset, xCount, yCount, src);
    // Now test allocation to allocation copy
    dstA->copy2DRangeFrom(xOffset, yOffset, xCount, yCount, srcA, xOffset, yOffset);
    dstA->copy2DRangeTo(xOffset, yOffset, xCount, yCount, dst);

    for (int i = 0; i < xCount * yCount; i++) {
        if (dst[i] != src[i]) {
            passed = false;
            break;
        }
    }

    delete[] src;
    delete[] dst;
    return passed;
}

/**
 * Test copyTo and copyFrom for all or part of a 2D Allocation.
 *
 * @param rs RS Context.
 * @param xElems Number of elements in X dimension in this Allocation.
 * @param yElems Number of elements in Y dimension in this Allocation.
 * @param zElems Number of elements in Z dimension in this Allocation.
 * @param xOffset Offset in X dimension of this Allocation for copy.
 * @param yOffset Offset in Y dimension of this Allocation for copy.
 * @param zOffset Offset in Z dimension of this Allocation for copy.
 * @param xCount Number of elements in X dimension need to copy.
 * @param yCount Number of elements in Y dimension need to copy.
 * @param zCount Number of elements in Z dimension need to copy.
 * @param dt DataType intended to test.
 * @param autoPadding Enable autoPadding or not. 
*/
template <class T>
static bool helperCopy3D(const sp<RS> &rs, int xElems, int yElems, int zElems,
                         int xOffset, int yOffset, int zOffset,
                         int xCount, int yCount, int zCount,
                         RsDataType dt, bool autoPadding = false) {
    bool passed = true;
    int arrLen = xElems * yElems * zElems;
    int copyCount = xCount * yCount * zCount;
    sp<Allocation> alloc = nullptr;

    if (autoPadding) {
        arrLen = arrLen * 3;
        copyCount = copyCount * 3;

        Type::Builder typeBuilder(rs, makeElement(rs, dt, 3));
        typeBuilder.setX(xElems);
        typeBuilder.setY(yElems);
        typeBuilder.setZ(zElems);

        alloc = Allocation::createTyped(rs, typeBuilder.create());
        alloc->setAutoPadding(autoPadding);
    } else {
        Type::Builder typeBuilder(rs, makeElement(rs, dt, 1));
        typeBuilder.setX(xElems);
        typeBuilder.setY(yElems);
        typeBuilder.setZ(zElems);

        alloc = Allocation::createTyped(rs, typeBuilder.create());
    }

    T* src = new T[arrLen];
    T* dst = new T[arrLen];

    for (int i = 0; i < copyCount; i++) {
        src[i] = (T)rand();
        dst[i] = (T)(-1);
    }

    alloc->copy3DRangeFrom(xOffset, yOffset, zOffset, xCount, yCount, zCount, src);
    alloc->copy3DRangeTo(xOffset, yOffset, zOffset, xCount, yCount, zCount, dst);

    for (int i = 0; i < copyCount; i++) {
        if (dst[i] != src[i]) {
            passed = false;
            break;
        }
    }

    delete[] src;
    delete[] dst;
    return passed;
}

//Corresponding 3D allocation to allocation copy.
static bool helperFloatAllocationCopy3D(const sp<RS> &rs, int xElems, int yElems, int zElems,
                                        int xOffset, int yOffset, int zOffset,
                                        int xCount, int yCount, int zCount) {

    bool passed = true;
    Type::Builder typeBuilder(rs, Element::F32(rs));

    typeBuilder.setX(xElems);
    typeBuilder.setY(yElems);
    typeBuilder.setZ(zElems);

    sp<Allocation> srcA = Allocation::createTyped(rs, typeBuilder.create());
    sp<Allocation> dstA = Allocation::createTyped(rs, typeBuilder.create());

    float *src, *dst;
    src = new float[xElems * yElems * zElems];
    dst = new float[xElems * yElems * zElems];
    for (int i = 0; i < xCount * yCount * zCount; i++) {
        src[i] = (float)rand();
        dst[i] = -1.0f;
    }

    // First populate the source allocation
    srcA->copy3DRangeFrom(xOffset, yOffset, zOffset, xCount, yCount, zCount, src);
    // Now test allocation to allocation copy
    dstA->copy3DRangeFrom(xOffset, yOffset, zOffset, xCount, yCount, zCount,
                          srcA, xOffset, yOffset, zOffset);
    dstA->copy3DRangeTo(xOffset, yOffset, zOffset, xCount, yCount, zCount, dst);

    for (int i = 0; i < xCount * yCount * zCount; i++) {
        if (dst[i] != src[i]) {
            passed = false;
            break;
        }
    }

    delete[] src;
    delete[] dst;
    return passed;
}

static int elemsToTest = 20;

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSAllocationTest_test1DCopy(JNIEnv * env,
                                                                                         jclass obj,
                                                                                         jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, NULL);
    sp<RS> rs = new RS();
    rs->init(path);
    env->ReleaseStringUTFChars(pathObj, path);
    bool passed = true;

    for (int s = 8; s <= elemsToTest; s += 2) {
        passed &= helperCopy1D<float>(rs, s, 0, s, false, RS_TYPE_FLOAT_32);
        passed &= helperCopy1D<char>(rs, s, 0, s, false, RS_TYPE_SIGNED_8);
        passed &= helperCopy1D<short>(rs, s, 0, s, false, RS_TYPE_SIGNED_16);
        passed &= helperCopy1D<int>(rs, s, 0, s, false, RS_TYPE_SIGNED_32);
        passed &= helperCopy1D<double>(rs, s, 0, s, false, RS_TYPE_FLOAT_64);

        // now test copy range
        for (int off = 0; off < s; off ++) {
            for (int count = 1; count <= s - off; count ++) {
                passed &= helperCopy1D<float>(rs, s, off, count, true, RS_TYPE_FLOAT_32);
                passed &= helperCopy1D<char>(rs, s, off, count, true, RS_TYPE_SIGNED_8);
                passed &= helperCopy1D<short>(rs, s, off, count, true, RS_TYPE_SIGNED_16);
                passed &= helperCopy1D<int>(rs, s, off, count, true, RS_TYPE_SIGNED_32);
                passed &= helperCopy1D<double>(rs, s, off, count, true, RS_TYPE_FLOAT_64);
            }
        }

        for (int off = 0; off < s; off ++) {
            for (int count = 1; count <= s - off; count ++) {
                passed &= helperFloatAllocationCopy1D(rs, s, off, count);
            }
        }
    }
    return passed;
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSAllocationTest_test2DCopy(JNIEnv * env,
                                                                                         jclass obj,
                                                                                         jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, NULL);
    sp<RS> rs = new RS();
    rs->init(path);
    env->ReleaseStringUTFChars(pathObj, path);
    bool passed = true;

    for (int s = 8; s <= elemsToTest; s += 2) {
        // now test copy range
        for (int off = 0; off < s; off ++) {
            for (int count = 1; count <= s - off; count ++) {
                passed &= helperCopy2D<float>(rs, s, s, off, off, count, count, RS_TYPE_FLOAT_32);
                passed &= helperCopy2D<char>(rs, s, s, off, off, count, count, RS_TYPE_SIGNED_8);
                passed &= helperCopy2D<short>(rs, s, s, off, off, count, count, RS_TYPE_SIGNED_16);
                passed &= helperCopy2D<int>(rs, s, s, off, off, count, count, RS_TYPE_SIGNED_32);
                passed &= helperCopy2D<double>(rs, s, s, off, off, count, count, RS_TYPE_FLOAT_64);
            }
        }

        for (int off = 0; off < s; off ++) {
            for (int count = 1; count <= s - off; count ++) {
                passed &= helperFloatAllocationCopy2D(rs, s, s, off, off, count, count);
            }
        }
    }
    return passed;
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSAllocationTest_test3DCopy(JNIEnv * env,
                                                                                         jclass obj,
                                                                                         jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, NULL);
    sp<RS> rs = new RS();
    rs->init(path);
    env->ReleaseStringUTFChars(pathObj, path);
    bool passed = true;

    for (int s = 8; s <= elemsToTest; s += 2) {
        // now test copy range
        for (int off = 0; off < s; off ++) {
            for (int count = 1; count <= s - off; count ++) {
                passed &= helperCopy3D<float>(rs, s, s, s, off, off, off, count, count, count, RS_TYPE_FLOAT_32);
                passed &= helperCopy3D<char>(rs, s, s, s, off, off, off, count, count, count, RS_TYPE_SIGNED_8);
                passed &= helperCopy3D<short>(rs, s, s, s, off, off, off, count, count, count, RS_TYPE_SIGNED_16);
                passed &= helperCopy3D<int>(rs, s, s, s, off, off, off, count, count, count, RS_TYPE_SIGNED_32);
                passed &= helperCopy3D<double>(rs, s, s, s, off, off, off, count, count, count, RS_TYPE_FLOAT_64);
            }
        }

        for (int off = 0; off < s; off ++) {
            for (int count = 1; count <= s - off; count ++) {
                passed &= helperFloatAllocationCopy3D(rs, s, s, s, off, off, off, count, count, count);
            }
        }
    }
    return passed;
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSAllocationTest_test1DCopyPadded(JNIEnv * env,
                                                                                               jclass obj,
                                                                                               jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, NULL);
    sp<RS> rs = new RS();
    rs->init(path);
    env->ReleaseStringUTFChars(pathObj, path);
    bool passed = true;

    for (int s = 8; s <= elemsToTest; s += 2) {
        passed &= helperCopy1D<float>(rs, s, 0, s, false, RS_TYPE_FLOAT_32, true);
        passed &= helperCopy1D<char>(rs, s, 0, s, false, RS_TYPE_SIGNED_8, true);
        passed &= helperCopy1D<short>(rs, s, 0, s, false, RS_TYPE_SIGNED_16, true);
        passed &= helperCopy1D<int>(rs, s, 0, s, false, RS_TYPE_SIGNED_32, true);
        passed &= helperCopy1D<double>(rs, s, 0, s, false, RS_TYPE_FLOAT_64, true);

        // now test copy range
        for (int off = 0; off < s; off ++) {
            for (int count = 1; count <= s - off; count ++) {
                passed &= helperCopy1D<float>(rs, s, off, count, true, RS_TYPE_FLOAT_32, true);
                passed &= helperCopy1D<char>(rs, s, off, count, true, RS_TYPE_SIGNED_8, true);
                passed &= helperCopy1D<short>(rs, s, off, count, true, RS_TYPE_SIGNED_16, true);
                passed &= helperCopy1D<int>(rs, s, off, count, true, RS_TYPE_SIGNED_32, true);
                passed &= helperCopy1D<double>(rs, s, off, count, true, RS_TYPE_FLOAT_64, true);
            }
        }
    }
    return passed;
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSAllocationTest_test2DCopyPadded(JNIEnv * env,
                                                                                               jclass obj,
                                                                                               jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, NULL);
    sp<RS> rs = new RS();
    rs->init(path);
    env->ReleaseStringUTFChars(pathObj, path);
    bool passed = true;

    for (int s = 8; s <= elemsToTest; s += 2) {
        // now test copy range
        for (int off = 0; off < s; off ++) {
            for (int count = 1; count <= s - off; count ++) {
                passed &= helperCopy2D<float>(rs, s, s, off, off, count, count, RS_TYPE_FLOAT_32, true);
                passed &= helperCopy2D<char>(rs, s, s, off, off, count, count, RS_TYPE_SIGNED_8, true);
                passed &= helperCopy2D<short>(rs, s, s, off, off, count, count, RS_TYPE_SIGNED_16, true);
                passed &= helperCopy2D<int>(rs, s, s, off, off, count, count, RS_TYPE_SIGNED_32, true);
                passed &= helperCopy2D<double>(rs, s, s, off, off, count, count, RS_TYPE_FLOAT_64, true);
            }
        }
    }
    return passed;
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSAllocationTest_test3DCopyPadded(JNIEnv * env,
                                                                                               jclass obj,
                                                                                               jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, NULL);
    sp<RS> rs = new RS();
    rs->init(path);
    env->ReleaseStringUTFChars(pathObj, path);
    bool passed = true;

    for (int s = 8; s <= elemsToTest; s += 2) {
        // now test copy range
        for (int off = 0; off < s; off ++) {
            for (int count = 1; count <= s - off; count ++) {
                passed &= helperCopy3D<float>(rs, s, s, s, off, off, off, count, count, count,
                                              RS_TYPE_FLOAT_32, true);
                passed &= helperCopy3D<char>(rs, s, s, s, off, off, off, count, count, count,
                                             RS_TYPE_SIGNED_8, true);
                passed &= helperCopy3D<short>(rs, s, s, s, off, off, off, count, count, count,
                                              RS_TYPE_SIGNED_16, true);
                passed &= helperCopy3D<int>(rs, s, s, s, off, off, off, count, count, count,
                                            RS_TYPE_SIGNED_32, true);
                passed &= helperCopy3D<double>(rs, s, s, s, off, off, off, count, count, count,
                                               RS_TYPE_FLOAT_64, true);
            }
        }
    }
    return passed;
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_rscpp_RSAllocationTest_testSetElementAt(JNIEnv * env,
                                                                                               jclass obj,
                                                                                               jstring pathObj)
{
    const char * path = env->GetStringUTFChars(pathObj, NULL);
    sp<RS> rs = new RS();
    rs->init(path);
    env->ReleaseStringUTFChars(pathObj, path);

    bool passed = true;

    Type::Builder b(rs, Element::I32(rs));
    b.setX(48);
    sp<Allocation> largeArray = Allocation::createTyped(rs, b.create());
    b.setX(1);
    sp<Allocation> singleElement = Allocation::createTyped(rs, b.create());

    sp<ScriptC_setelementat> script = new ScriptC_setelementat(rs);

    script->set_memset_toValue(1);
    script->forEach_memset(singleElement);

    script->set_dimX(48);
    script->set_array(largeArray);

    script->forEach_setLargeArray(singleElement);

    int result = 0;

    script->set_compare_value(10);
    script->forEach_compare(largeArray);
    script->forEach_getCompareResult(singleElement);
    singleElement->copy1DTo(&result);
    if (result != 2) {
        passed = false;
    }

    return passed;
}
