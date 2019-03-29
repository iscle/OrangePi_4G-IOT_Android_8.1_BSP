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
 *
 */
#include <jni.h>
#include <inttypes.h>
#include <setjmp.h>
#include <signal.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

//mask the top 8 bits
#define TAG_MASK ((0xFFULL) << 56)

#define PATTERN 0x600DC0DE

static sigjmp_buf jmpenv;

static void sigsegv_handler(int signum) {
    siglongjmp(jmpenv, 1);
}

jboolean android_os_cts_TaggedPointer_hasTaggedPointer(JNIEnv* env, jobject thiz)
{
    uint32_t data;
    uint32_t *tagged;
    uintptr_t tmp;
    int err;
    jboolean ret = true;
    struct sigaction sigsegv_act;
    struct sigaction oldact;

    tmp = TAG_MASK | (uintptr_t)(&data);
    tagged = (uint32_t *)tmp;
    data = PATTERN;

    memset(&sigsegv_act, 0, sizeof(sigsegv_act));
    sigsegv_act.sa_handler = sigsegv_handler;

    err = sigaction(SIGSEGV, &sigsegv_act, &oldact);
    if (err) {
        ret = false;
        goto err_sigaction;
    }

    if (sigsetjmp(jmpenv, 1)) {
        ret = false;
        goto err_segfault;
    }

    if (*tagged != PATTERN) {
        ret = false;
    }

err_segfault:
    sigaction(SIGSEGV, &oldact, NULL);
err_sigaction:
    return ret;
}

static JNINativeMethod gMethods[] = {
    {  "hasTaggedPointer", "()Z",
            (void *) android_os_cts_TaggedPointer_hasTaggedPointer },
};

int register_android_os_cts_TaggedPointer(JNIEnv* env)
{
    jclass clazz = env->FindClass("android/os/cts/TaggedPointer");

    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
