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
 *
 */

#include "jni.h"

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <linux/filter.h>
#include <linux/seccomp.h>

#include <sys/prctl.h>
#include <sys/types.h>
#include <sys/utsname.h>
#include <sys/wait.h>

jint android_os_cts_OSFeatures_prctlCapBsetRead(JNIEnv* env, jobject thiz, jint i)
{
    return prctl(PR_CAPBSET_READ, i, 0, 0, 0);
}

#define DENY BPF_STMT(BPF_RET+BPF_K, SECCOMP_RET_KILL)

static void test_seccomp() {
    if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) < 0) {
        _exit(0);
    }

    struct sock_filter filter[] = { DENY };
    struct sock_fprog prog;
    memset(&prog, 0, sizeof(prog));
    prog.len = sizeof(filter) / sizeof(filter[0]);
    prog.filter = filter;

    if (prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &prog) < 0) {
        _exit(0);
    }

    while(1) {
        _exit(0);  // should crash with SIGSYS
    }
}

jboolean android_os_cts_OSFeatures_hasSeccompSupport(JNIEnv* env, jobject)
{
    pid_t pid = fork();
    if (pid == -1) {
        jclass cls = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(cls, "fork failed");
        return false;
    }
    if (pid == 0) {
        // child
        test_seccomp();
        _exit(0);
    }

    int status;
    TEMP_FAILURE_RETRY(waitpid(pid, &status, 0));
    return WIFSIGNALED(status) && (WTERMSIG(status) == SIGSYS);
}

jboolean android_os_cts_OSFeatures_needsSeccompSupport(JNIEnv*, jobject)
{
#if !defined(ARCH_SUPPORTS_SECCOMP)
    // Seccomp support is only available for ARM, x86, x86_64.
    // This define is controlled by the Android.mk.
    return false;
#endif
    return true;
}

static JNINativeMethod gMethods[] = {
    {  "prctlCapBsetRead", "(I)I",
            (void *) android_os_cts_OSFeatures_prctlCapBsetRead },
    {  "hasSeccompSupport", "()Z",
            (void *) android_os_cts_OSFeatures_hasSeccompSupport  },
    {  "needsSeccompSupport", "()Z",
            (void *) android_os_cts_OSFeatures_needsSeccompSupport  }
};

int register_android_os_cts_OSFeatures(JNIEnv* env)
{
    jclass clazz = env->FindClass("android/os/cts/OSFeatures");

    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
