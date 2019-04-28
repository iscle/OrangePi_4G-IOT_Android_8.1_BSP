/*
 * Copyright 2015 Google Inc.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Google designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Google in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

#include "jni.h"
#include <nativehelper/JNIHelp.h>
#include "nativehelper/jni_macros.h"

#include <stdlib.h>
#include <math.h>

JNIEXPORT jdouble JNICALL
Math_cos(JNIEnv *env, jclass unused, jdouble d) {
    return cos(d);
}

JNIEXPORT jdouble JNICALL
Math_sin(JNIEnv *env, jclass unused, jdouble d) {
    return sin(d);
}

JNIEXPORT jdouble JNICALL
Math_tan(JNIEnv *env, jclass unused, jdouble d) {
    return tan(d);
}

JNIEXPORT jdouble JNICALL
Math_asin(JNIEnv *env, jclass unused, jdouble d) {
    return asin(d);
}

JNIEXPORT jdouble JNICALL
Math_acos(JNIEnv *env, jclass unused, jdouble d) {
    return acos(d);
}

JNIEXPORT jdouble JNICALL
Math_atan(JNIEnv *env, jclass unused, jdouble d) {
    return atan(d);
}

JNIEXPORT jdouble JNICALL
Math_exp(JNIEnv *env, jclass unused, jdouble d) {
    return exp(d);
}

JNIEXPORT jdouble JNICALL
Math_log(JNIEnv *env, jclass unused, jdouble d) {
    return log(d);
}

JNIEXPORT jdouble JNICALL
Math_log10(JNIEnv *env, jclass unused, jdouble d) {
    return log10(d);
}

JNIEXPORT jdouble JNICALL
Math_sqrt(JNIEnv *env, jclass unused, jdouble d) {
    return sqrt(d);
}

JNIEXPORT jdouble JNICALL
Math_cbrt(JNIEnv *env, jclass unused, jdouble d) {
    return cbrt(d);
}

JNIEXPORT jdouble JNICALL
Math_atan2(JNIEnv *env, jclass unused, jdouble d1, jdouble d2) {
    return atan2(d1, d2);
}

JNIEXPORT jdouble JNICALL
Math_pow(JNIEnv *env, jclass unused, jdouble d1, jdouble d2) {
    return pow(d1, d2);
}

JNIEXPORT jdouble JNICALL
Math_IEEEremainder(JNIEnv *env, jclass unused,
                                  jdouble dividend,
                                  jdouble divisor) {
    return remainder(dividend, divisor);
}

JNIEXPORT jdouble JNICALL
Math_cosh(JNIEnv *env, jclass unused, jdouble d) {
    return cosh(d);
}

JNIEXPORT jdouble JNICALL
Math_sinh(JNIEnv *env, jclass unused, jdouble d) {
    return sinh(d);
}

JNIEXPORT jdouble JNICALL
Math_tanh(JNIEnv *env, jclass unused, jdouble d) {
    return tanh(d);
}

JNIEXPORT jdouble JNICALL
Math_hypot(JNIEnv *env, jclass unused, jdouble x, jdouble y) {
    return hypot(x, y);
}

JNIEXPORT jdouble JNICALL
Math_log1p(JNIEnv *env, jclass unused, jdouble d) {
    return log1p(d);
}

JNIEXPORT jdouble JNICALL
Math_expm1(JNIEnv *env, jclass unused, jdouble d) {
    return expm1(d);
}

JNIEXPORT jdouble JNICALL
Math_floor(JNIEnv *env, jclass unused, jdouble d) {
    return floor(d);
}

JNIEXPORT jdouble JNICALL
Math_ceil(JNIEnv *env, jclass unused, jdouble d) {
    return ceil(d);
}

JNIEXPORT jdouble JNICALL
Math_rint(JNIEnv *env, jclass unused, jdouble d) {
    return rint(d);
}

static JNINativeMethod gMethods[] = {
  FAST_NATIVE_METHOD(Math, IEEEremainder, "(DD)D"),
  FAST_NATIVE_METHOD(Math, acos, "(D)D"),
  FAST_NATIVE_METHOD(Math, asin, "(D)D"),
  FAST_NATIVE_METHOD(Math, atan, "(D)D"),
  FAST_NATIVE_METHOD(Math, atan2, "(DD)D"),
  FAST_NATIVE_METHOD(Math, cbrt, "(D)D"),
  FAST_NATIVE_METHOD(Math, cos, "(D)D"),
  FAST_NATIVE_METHOD(Math, ceil, "(D)D"),
  FAST_NATIVE_METHOD(Math, cosh, "(D)D"),
  FAST_NATIVE_METHOD(Math, exp, "(D)D"),
  FAST_NATIVE_METHOD(Math, expm1, "(D)D"),
  FAST_NATIVE_METHOD(Math, floor, "(D)D"),
  FAST_NATIVE_METHOD(Math, hypot, "(DD)D"),
  FAST_NATIVE_METHOD(Math, log, "(D)D"),
  FAST_NATIVE_METHOD(Math, log10, "(D)D"),
  FAST_NATIVE_METHOD(Math, log1p, "(D)D"),
  FAST_NATIVE_METHOD(Math, pow, "(DD)D"),
  FAST_NATIVE_METHOD(Math, rint, "(D)D"),
  FAST_NATIVE_METHOD(Math, sin, "(D)D"),
  FAST_NATIVE_METHOD(Math, sinh, "(D)D"),
  FAST_NATIVE_METHOD(Math, sqrt, "(D)D"),
  FAST_NATIVE_METHOD(Math, tan, "(D)D"),
  FAST_NATIVE_METHOD(Math, tanh, "(D)D"),
};

void register_java_lang_Math(JNIEnv* env) {
  jniRegisterNativeMethods(env, "java/lang/Math", gMethods, NELEM(gMethods));
}
