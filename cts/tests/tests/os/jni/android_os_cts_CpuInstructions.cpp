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

#if defined(__arm__) || defined(__aarch64__)
#include <setjmp.h>
#include <signal.h>
#include <string.h>

static sigjmp_buf jmpenv;

static void sigill_handler(int signum __attribute__((unused)))
{
    siglongjmp(jmpenv, 1);
}

static int do_sigsetjmp()
{
    return sigsetjmp(jmpenv, 1);
}

static jboolean test_instruction(void (*func)())
{
    struct sigaction sigill_act;
    struct sigaction oldact;
    int err;
    jboolean ret = true;

    memset(&sigill_act, 0, sizeof(sigill_act));
    sigill_act.sa_handler = sigill_handler;

    err = sigaction(SIGILL, &sigill_act, &oldact);
    if (err) {
        ret = false;
        goto err_sigaction;
    }

    if (do_sigsetjmp()) {
        ret = false;
        goto err_segill;
    }

    func();

err_segill:
    sigaction(SIGILL, &oldact, NULL);
err_sigaction:
    return ret;
}
#endif

#ifdef __aarch64__
static void cntvct()
{
    asm volatile ( "mrs x0, cntvct_el0" : : : "x0" );
}

jboolean android_os_cts_CpuInstructions_canReadCntvct(JNIEnv *, jobject)
{
    return test_instruction(cntvct);
}
#else
jboolean android_os_cts_CpuInstructions_canReadCntvct(JNIEnv *, jobject)
{
    return false;
}
#endif

#ifdef __arm__
static void swp()
{
    uint32_t dummy = 0;
    uint32_t *ptr = &dummy;
    asm volatile ( "swp r0, r0, [%0]" : "+r"(ptr) : : "r0" );
}

static void setend()
{
    asm volatile (
        "setend be" "\n"
        "setend le" "\n"
    );
}

static void cp15_dsb()
{
    asm volatile ( "mcr p15, 0, %0, c7, c10, 4" : : "r"(0) );
}

jboolean android_os_cts_CpuInstructions_hasSwp(JNIEnv *, jobject)
{
    return test_instruction(swp);
}

jboolean android_os_cts_CpuInstructions_hasSetend(JNIEnv *, jobject)
{
    return test_instruction(setend);
}

jboolean android_os_cts_CpuInstructions_hasCp15Barriers(JNIEnv *, jobject)
{
    return test_instruction(cp15_dsb);
}
#else
jboolean android_os_cts_CpuInstructions_hasSwp(JNIEnv *, jobject)
{
    return false;
}

jboolean android_os_cts_CpuInstructions_hasSetend(JNIEnv *, jobject)
{
    return false;
}

jboolean android_os_cts_CpuInstructions_hasCp15Barriers(JNIEnv *, jobject)
{
    return false;
}
#endif

static JNINativeMethod gMethods[] = {
    { "canReadCntvct", "()Z", (void *)android_os_cts_CpuInstructions_canReadCntvct },
    { "hasSwp", "()Z", (void *)android_os_cts_CpuInstructions_hasSwp },
    { "hasSetend", "()Z", (void *)android_os_cts_CpuInstructions_hasSetend },
    { "hasCp15Barriers", "()Z",
            (void *)android_os_cts_CpuInstructions_hasCp15Barriers },
};

int register_android_os_cts_CpuInstructions(JNIEnv *env)
{
    jclass clazz = env->FindClass("android/os/cts/CpuInstructions");

    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
