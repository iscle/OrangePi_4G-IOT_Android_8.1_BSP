/*
 * Copyright (C) 2014 The Android Open Source Project
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
#include <algorithm>
#include <math.h>
#include <string>

#include <RenderScript.h>

#define LOG_TAG "rscpptest"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace android::RSC;

/* This class helps return multiple values to Java.  To use:
 * - define a class in Java,
 * - have the jni method return a jobject,
 * - create an instance of this class,
 * - use Set* to fill the fields,
 * - return getObject() on exit of the JNI method.
 */
class JavaStruct {
private:
    JNIEnv* mEnv;
    jclass mClass;
    jobject mObject;

    /* Returns the id of the named field.  Type is one of "F" (float) or "I" (int).
     * If there's an error, logs a message and returns 0.
     */
    jfieldID GetFieldId(const char* name, const char* type);

public:
    // Creates an instance of the named Java class.
    JavaStruct(JNIEnv* env, const std::string& name);
    // Sets the field of the instance.
    void SetFloat(const char* name, float value);
    void SetInt(const char* name, int value);
    // Returns the instance.
    jobject getObject() { return mObject; }
};

JavaStruct::JavaStruct(JNIEnv* env, const std::string& name) : mEnv(env), mClass(0), mObject(0) {
    /* This creates an instance of the specified static inner class of CoreMathVerifier.
     * To convert this to return a non-static inner class instead, pass
     * "(Landroid/renderscript/cts/CoreMathVerifier;)V" instead of "()V" go getMethodID
     * and pass the parent class as a third argument to NewObject.
     */
    std::string fullName = "android/renderscript/cts/CoreMathVerifier$" + name;
    mClass = env->FindClass(fullName.c_str());
    if (!mClass) {
        LOGE("Can't find the Java class %s", name.c_str());
        return;
    }
    jmethodID constructor = env->GetMethodID(mClass, "<init>", "()V");
    if (!constructor) {
        LOGE("Can't find the constructor of %s", name.c_str());
        return;
    }
    mObject = env->NewObject(mClass, constructor);
    if (!mObject) {
        LOGE("Can't construct a %s", name.c_str());
    }
}

void JavaStruct::SetInt(const char* name, int value) {
    jfieldID fieldId = GetFieldId(name, "I");
    if (fieldId) {
        mEnv->SetIntField(mObject, fieldId, value);
    }
}

void JavaStruct::SetFloat(const char* name, float value) {
    jfieldID fieldId = GetFieldId(name, "F");
    if (fieldId) {
        mEnv->SetFloatField(mObject, fieldId, value);
    }
}

jfieldID JavaStruct::GetFieldId(const char* name, const char* type) {
    if (!mClass) {
        return 0;  // We already have logged the error in the constructor.
    }
    jfieldID fieldId = mEnv->GetFieldID(mClass, name, type);
    if (!fieldId) {
        LOGE("Can't find the field %s", name);
        return 0;
    }
    return fieldId;
}

/* We provide access to many primitive math functions because:
 * - not all functions are available in Java, notably gamma and erf,
 * - Java lacks float version of these functions, so we can compare implementations with
 *   similar constraints, and
 * - handling unsigned integers, especially longs, is painful and error prone in Java.
 */

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_acos(JNIEnv*, jclass, jfloat x) {
    return acosf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_acosh(JNIEnv*, jclass, jfloat x) {
    return acoshf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_asin(JNIEnv*, jclass, jfloat x) {
    return asinf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_asinh(JNIEnv*, jclass, jfloat x) {
    return asinhf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_atan(JNIEnv*, jclass, jfloat x) {
    return atanf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_atan2(JNIEnv*, jclass, jfloat x, jfloat y) {
    return atan2f(x, y);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_atanh(JNIEnv*, jclass, jfloat x) {
    return atanhf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_cbrt(JNIEnv*, jclass, jfloat x) {
    return cbrtf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_ceil(JNIEnv*, jclass, jfloat x) {
    return ceilf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_cos(JNIEnv*, jclass, jfloat x) {
    return cosf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_cosh(JNIEnv*, jclass, jfloat x) {
    return coshf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_erf(JNIEnv*, jclass, jfloat x) {
    return erff(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_erfc(JNIEnv*, jclass, jfloat x) {
    return erfcf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_exp(JNIEnv*, jclass, jfloat x) {
    return expf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_exp10(JNIEnv*, jclass, jfloat x) {
    return powf(10.0f, x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_exp2(JNIEnv*, jclass, jfloat x) {
    return powf(2.0f, x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_expm1(JNIEnv*, jclass, jfloat x) {
    return expm1f(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_floor(JNIEnv*, jclass, jfloat x) {
    return floorf(x);
}

extern "C" JNIEXPORT jobject JNICALL
Java_android_renderscript_cts_CoreMathVerifier_frexp(JNIEnv* env, jclass, jfloat x) {
    JavaStruct result(env, "FrexpResult");
    int exp = 0;
    result.SetFloat("significand", frexpf(x, &exp));
    result.SetInt("exponent", exp);
    return result.getObject();
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_hypot(JNIEnv*, jclass, jfloat x, jfloat y) {
    return hypotf(x, y);
}

extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_ilogb(JNIEnv*, jclass, jfloat x) {
    return ilogbf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_ldexp(JNIEnv*, jclass, jfloat x, jint exp) {
    return ldexpf(x, exp);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_lgamma(JNIEnv*, jclass, jfloat x) {
    return lgammaf(x);
}

extern "C" JNIEXPORT jobject JNICALL
Java_android_renderscript_cts_CoreMathVerifier_lgamma2(JNIEnv* env, jclass, jfloat x) {
    JavaStruct result(env, "LgammaResult");
    int sign = 0;
    result.SetFloat("lgamma", lgammaf_r(x, &sign));
    result.SetInt("gammaSign", sign);
    return result.getObject();
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_log(JNIEnv*, jclass, jfloat x) {
    return logf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_logb(JNIEnv*, jclass, jfloat x) {
    return logbf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_log10(JNIEnv*, jclass, jfloat x) {
    return log10f(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_log1p(JNIEnv*, jclass, jfloat x) {
    return log1pf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_log2(JNIEnv*, jclass, jfloat x) {
    return log2f(x);
}

extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_maxI8(JNIEnv*, jclass, jbyte x, jbyte y) {
    return std::max(x, y);
}

extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_maxU8(JNIEnv*, jclass, jbyte x, jbyte y) {
    return std::max((uint8_t)x, (uint8_t)y);
}

extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_maxI16(JNIEnv*, jclass, jshort x, jshort y) {
    return std::max(x, y);
}

extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_maxU16(JNIEnv*, jclass, jshort x, jshort y) {
    return std::max((uint16_t)x, (uint16_t)y);
}

extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_maxI32(JNIEnv*, jclass, jint x, jint y) {
    return std::max(x, y);
}

extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_maxU32(JNIEnv*, jclass, jint x, jint y) {
    return std::max((uint32_t)x, (uint32_t)y);
}

extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_maxI64(JNIEnv*, jclass, jlong x, jlong y) {
    return std::max(x, y);
}

extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_maxU64(JNIEnv*, jclass, jlong x, jlong y) {
    return std::max((uint64_t)x, (uint64_t)y);
}

extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_minI8(JNIEnv*, jclass, jbyte x, jbyte y) {
    return std::min(x, y);
}

extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_minU8(JNIEnv*, jclass, jbyte x, jbyte y) {
    return std::min((uint8_t)x, (uint8_t)y);
}

extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_minI16(JNIEnv*, jclass, jshort x, jshort y) {
    return std::min(x, y);
}

extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_minU16(JNIEnv*, jclass, jshort x, jshort y) {
    return std::min((uint16_t)x, (uint16_t)y);
}

extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_minI32(JNIEnv*, jclass, jint x, jint y) {
    return std::min(x, y);
}

extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_minU32(JNIEnv*, jclass, jint x, jint y) {
    return std::min((uint32_t)x, (uint32_t)y);
}

extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_minI64(JNIEnv*, jclass, jlong x, jlong y) {
    return std::min(x, y);
}

extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_minU64(JNIEnv*, jclass, jlong x, jlong y) {
    return std::min((uint64_t)x, (uint64_t)y);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_pow(JNIEnv*, jclass, jfloat x, jfloat y) {
    return powf(x, y);
}

extern "C" JNIEXPORT jobject JNICALL
Java_android_renderscript_cts_CoreMathVerifier_remquo(JNIEnv* env, jclass, jfloat numerator,
                                                      jfloat denominator) {
    JavaStruct result(env, "RemquoResult");
    int quotient = 0;
    result.SetFloat("remainder", remquof(numerator, denominator, &quotient));
    result.SetInt("quotient", quotient);
    return result.getObject();
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_rint(JNIEnv*, jclass, jfloat x) {
    return rintf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_round(JNIEnv*, jclass, jfloat x) {
    return roundf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_sin(JNIEnv*, jclass, jfloat x) {
    return sinf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_sinh(JNIEnv*, jclass, jfloat x) {
    return sinhf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_sqrt(JNIEnv*, jclass, jfloat x) {
    return sqrtf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_tan(JNIEnv*, jclass, jfloat x) {
    return tanf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_tanh(JNIEnv*, jclass, jfloat x) {
    return tanhf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_tgamma(JNIEnv*, jclass, jfloat x) {
    return tgammaf(x);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_trunc(JNIEnv*, jclass, jfloat x) {
    return truncf(x);
}

extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertCharToChar(JNIEnv*, jclass, jbyte x) {
    return (jbyte)(int8_t)(int8_t)x;
}
extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertCharToUchar(JNIEnv*, jclass, jbyte x) {
    return (jbyte)(uint8_t)(int8_t)x;
}
extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertCharToShort(JNIEnv*, jclass, jbyte x) {
    return (jshort)(int16_t)(int8_t)x;
}
extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertCharToUshort(JNIEnv*, jclass, jbyte x) {
    return (jshort)(uint16_t)(int8_t)x;
}
extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertCharToInt(JNIEnv*, jclass, jbyte x) {
    return (jint)(int32_t)(int8_t)x;
}
extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertCharToUint(JNIEnv*, jclass, jbyte x) {
    return (jint)(uint32_t)(int8_t)x;
}
extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertCharToLong(JNIEnv*, jclass, jbyte x) {
    return (jlong)(int64_t)(int8_t)x;
}
extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertCharToUlong(JNIEnv*, jclass, jbyte x) {
    return (jlong)(uint64_t)(int8_t)x;
}
extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertCharToFloat(JNIEnv*, jclass, jbyte x) {
    return (jfloat)(float)(int8_t)x;
}
extern "C" JNIEXPORT jdouble JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertCharToDouble(JNIEnv*, jclass, jbyte x) {
    return (jdouble)(double)(int8_t)x;
}

extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUcharToChar(JNIEnv*, jclass, jbyte x) {
    return (jbyte)(int8_t)(uint8_t)x;
}
extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUcharToUchar(JNIEnv*, jclass, jbyte x) {
    return (jbyte)(uint8_t)(uint8_t)x;
}
extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUcharToShort(JNIEnv*, jclass, jbyte x) {
    return (jshort)(int16_t)(uint8_t)x;
}
extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUcharToUshort(JNIEnv*, jclass, jbyte x) {
    return (jshort)(uint16_t)(uint8_t)x;
}
extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUcharToInt(JNIEnv*, jclass, jbyte x) {
    return (jint)(int32_t)(uint8_t)x;
}
extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUcharToUint(JNIEnv*, jclass, jbyte x) {
    return (jint)(uint32_t)(uint8_t)x;
}
extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUcharToLong(JNIEnv*, jclass, jbyte x) {
    return (jlong)(int64_t)(uint8_t)x;
}
extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUcharToUlong(JNIEnv*, jclass, jbyte x) {
    return (jlong)(uint64_t)(uint8_t)x;
}
extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUcharToFloat(JNIEnv*, jclass, jbyte x) {
    return (jfloat)(float)(uint8_t)x;
}
extern "C" JNIEXPORT jdouble JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUcharToDouble(JNIEnv*, jclass, jbyte x) {
    return (jdouble)(double)(uint8_t)x;
}

extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertShortToChar(JNIEnv*, jclass, jshort x) {
    return (jbyte)(int8_t)(int16_t)x;
}
extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertShortToUchar(JNIEnv*, jclass, jshort x) {
    return (jbyte)(uint8_t)(int16_t)x;
}
extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertShortToShort(JNIEnv*, jclass, jshort x) {
    return (jshort)(int16_t)(int16_t)x;
}
extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertShortToUshort(JNIEnv*, jclass, jshort x) {
    return (jshort)(uint16_t)(int16_t)x;
}
extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertShortToInt(JNIEnv*, jclass, jshort x) {
    return (jint)(int32_t)(int16_t)x;
}
extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertShortToUint(JNIEnv*, jclass, jshort x) {
    return (jint)(uint32_t)(int16_t)x;
}
extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertShortToLong(JNIEnv*, jclass, jshort x) {
    return (jlong)(int64_t)(int16_t)x;
}
extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertShortToUlong(JNIEnv*, jclass, jshort x) {
    return (jlong)(uint64_t)(int16_t)x;
}
extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertShortToFloat(JNIEnv*, jclass, jshort x) {
    return (jfloat)(float)(int16_t)x;
}
extern "C" JNIEXPORT jdouble JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertShortToDouble(JNIEnv*, jclass, jshort x) {
    return (jdouble)(double)(int16_t)x;
}

extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUshortToChar(JNIEnv*, jclass, jshort x) {
    return (jbyte)(int8_t)(uint16_t)x;
}
extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUshortToUchar(JNIEnv*, jclass, jshort x) {
    return (jbyte)(uint8_t)(uint16_t)x;
}
extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUshortToShort(JNIEnv*, jclass, jshort x) {
    return (jshort)(int16_t)(uint16_t)x;
}
extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUshortToUshort(JNIEnv*, jclass, jshort x) {
    return (jshort)(uint16_t)(uint16_t)x;
}
extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUshortToInt(JNIEnv*, jclass, jshort x) {
    return (jint)(int32_t)(uint16_t)x;
}
extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUshortToUint(JNIEnv*, jclass, jshort x) {
    return (jint)(uint32_t)(uint16_t)x;
}
extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUshortToLong(JNIEnv*, jclass, jshort x) {
    return (jlong)(int64_t)(uint16_t)x;
}
extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUshortToUlong(JNIEnv*, jclass, jshort x) {
    return (jlong)(uint64_t)(uint16_t)x;
}
extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUshortToFloat(JNIEnv*, jclass, jshort x) {
    return (jfloat)(float)(uint16_t)x;
}
extern "C" JNIEXPORT jdouble JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUshortToDouble(JNIEnv*, jclass, jshort x) {
    return (jdouble)(double)(uint16_t)x;
}

extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertIntToChar(JNIEnv*, jclass, jint x) {
    return (jbyte)(int8_t)(int32_t)x;
}
extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertIntToUchar(JNIEnv*, jclass, jint x) {
    return (jbyte)(uint8_t)(int32_t)x;
}
extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertIntToShort(JNIEnv*, jclass, jint x) {
    return (jshort)(int16_t)(int32_t)x;
}
extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertIntToUshort(JNIEnv*, jclass, jint x) {
    return (jshort)(uint16_t)(int32_t)x;
}
extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertIntToInt(JNIEnv*, jclass, jint x) {
    return (jint)(int32_t)(int32_t)x;
}
extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertIntToUint(JNIEnv*, jclass, jint x) {
    return (jint)(uint32_t)(int32_t)x;
}
extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertIntToLong(JNIEnv*, jclass, jint x) {
    return (jlong)(int64_t)(int32_t)x;
}
extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertIntToUlong(JNIEnv*, jclass, jint x) {
    return (jlong)(uint64_t)(int32_t)x;
}
extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertIntToFloat(JNIEnv*, jclass, jint x) {
    return (jfloat)(float)(int32_t)x;
}
extern "C" JNIEXPORT jdouble JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertIntToDouble(JNIEnv*, jclass, jint x) {
    return (jdouble)(double)(int32_t)x;
}

extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUintToChar(JNIEnv*, jclass, jint x) {
    return (jbyte)(int8_t)(uint32_t)x;
}
extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUintToUchar(JNIEnv*, jclass, jint x) {
    return (jbyte)(uint8_t)(uint32_t)x;
}
extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUintToShort(JNIEnv*, jclass, jint x) {
    return (jshort)(int16_t)(uint32_t)x;
}
extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUintToUshort(JNIEnv*, jclass, jint x) {
    return (jshort)(uint16_t)(uint32_t)x;
}
extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUintToInt(JNIEnv*, jclass, jint x) {
    return (jint)(int32_t)(uint32_t)x;
}
extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUintToUint(JNIEnv*, jclass, jint x) {
    return (jint)(uint32_t)(uint32_t)x;
}
extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUintToLong(JNIEnv*, jclass, jint x) {
    return (jlong)(int64_t)(uint32_t)x;
}
extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUintToUlong(JNIEnv*, jclass, jint x) {
    return (jlong)(uint64_t)(uint32_t)x;
}
extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUintToFloat(JNIEnv*, jclass, jint x) {
    return (jfloat)(float)(uint32_t)x;
}
extern "C" JNIEXPORT jdouble JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUintToDouble(JNIEnv*, jclass, jint x) {
    return (jdouble)(double)(uint32_t)x;
}

extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertLongToChar(JNIEnv*, jclass, jlong x) {
    return (jbyte)(int8_t)(int64_t)x;
}
extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertLongToUchar(JNIEnv*, jclass, jlong x) {
    return (jbyte)(uint8_t)(int64_t)x;
}
extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertLongToShort(JNIEnv*, jclass, jlong x) {
    return (jshort)(int16_t)(int64_t)x;
}
extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertLongToUshort(JNIEnv*, jclass, jlong x) {
    return (jshort)(uint16_t)(int64_t)x;
}
extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertLongToInt(JNIEnv*, jclass, jlong x) {
    return (jint)(int32_t)(int64_t)x;
}
extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertLongToUint(JNIEnv*, jclass, jlong x) {
    return (jint)(uint32_t)(int64_t)x;
}
extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertLongToLong(JNIEnv*, jclass, jlong x) {
    return (jlong)(int64_t)(int64_t)x;
}
extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertLongToUlong(JNIEnv*, jclass, jlong x) {
    return (jlong)(uint64_t)(int64_t)x;
}
extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertLongToFloat(JNIEnv*, jclass, jlong x) {
    return (jfloat)(float)(int64_t)x;
}
extern "C" JNIEXPORT jdouble JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertLongToDouble(JNIEnv*, jclass, jlong x) {
    return (jdouble)(double)(int64_t)x;
}

extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUlongToChar(JNIEnv*, jclass, jlong x) {
    return (jbyte)(int8_t)(uint64_t)x;
}
extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUlongToUchar(JNIEnv*, jclass, jlong x) {
    return (jbyte)(uint8_t)(uint64_t)x;
}
extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUlongToShort(JNIEnv*, jclass, jlong x) {
    return (jshort)(int16_t)(uint64_t)x;
}
extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUlongToUshort(JNIEnv*, jclass, jlong x) {
    return (jshort)(uint16_t)(uint64_t)x;
}
extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUlongToInt(JNIEnv*, jclass, jlong x) {
    return (jint)(int32_t)(uint64_t)x;
}
extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUlongToUint(JNIEnv*, jclass, jlong x) {
    return (jint)(uint32_t)(uint64_t)x;
}
extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUlongToLong(JNIEnv*, jclass, jlong x) {
    return (jlong)(int64_t)(uint64_t)x;
}
extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUlongToUlong(JNIEnv*, jclass, jlong x) {
    return (jlong)(uint64_t)(uint64_t)x;
}
extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUlongToFloat(JNIEnv*, jclass, jlong x) {
    return (jfloat)(float)(uint64_t)x;
}
extern "C" JNIEXPORT jdouble JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertUlongToDouble(JNIEnv*, jclass, jlong x) {
    return (jdouble)(double)(uint64_t)x;
}

extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertFloatToChar(JNIEnv*, jclass, jfloat x) {
    return (jbyte)(int8_t)(float)x;
}
extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertFloatToUchar(JNIEnv*, jclass, jfloat x) {
    return (jbyte)(uint8_t)(float)x;
}
extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertFloatToShort(JNIEnv*, jclass, jfloat x) {
    return (jshort)(int16_t)(float)x;
}
extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertFloatToUshort(JNIEnv*, jclass, jfloat x) {
    return (jshort)(uint16_t)(float)x;
}
extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertFloatToInt(JNIEnv*, jclass, jfloat x) {
    return (jint)(int32_t)(float)x;
}
extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertFloatToUint(JNIEnv*, jclass, jfloat x) {
    return (jint)(uint32_t)(float)x;
}
extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertFloatToLong(JNIEnv*, jclass, jfloat x) {
    return (jlong)(int64_t)(float)x;
}
extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertFloatToUlong(JNIEnv*, jclass, jfloat x) {
    return (jlong)(uint64_t)(float)x;
}
extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertFloatToFloat(JNIEnv*, jclass, jfloat x) {
    return (jfloat)(float)(float)x;
}
extern "C" JNIEXPORT jdouble JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertFloatToDouble(JNIEnv*, jclass, jfloat x) {
    return (jdouble)(double)(float)x;
}

extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertDoubleToChar(JNIEnv*, jclass, jdouble x) {
    return (jbyte)(int8_t)(double)x;
}
extern "C" JNIEXPORT jbyte JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertDoubleToUchar(JNIEnv*, jclass, jdouble x) {
    return (jbyte)(uint8_t)(double)x;
}
extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertDoubleToShort(JNIEnv*, jclass, jdouble x) {
    return (jshort)(int16_t)(double)x;
}
extern "C" JNIEXPORT jshort JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertDoubleToUshort(JNIEnv*, jclass, jdouble x) {
    return (jshort)(uint16_t)(double)x;
}
extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertDoubleToInt(JNIEnv*, jclass, jdouble x) {
    return (jint)(int32_t)(double)x;
}
extern "C" JNIEXPORT jint JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertDoubleToUint(JNIEnv*, jclass, jdouble x) {
    return (jint)(uint32_t)(double)x;
}
extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertDoubleToLong(JNIEnv*, jclass, jdouble x) {
    return (jlong)(int64_t)(double)x;
}
extern "C" JNIEXPORT jlong JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertDoubleToUlong(JNIEnv*, jclass, jdouble x) {
    return (jlong)(uint64_t)(double)x;
}
extern "C" JNIEXPORT jfloat JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertDoubleToFloat(JNIEnv*, jclass, jdouble x) {
    return (jfloat)(float)(double)x;
}
extern "C" JNIEXPORT jdouble JNICALL
Java_android_renderscript_cts_CoreMathVerifier_convertDoubleToDouble(JNIEnv*, jclass, jdouble x) {
    return (jdouble)(double)(double)x;
}
