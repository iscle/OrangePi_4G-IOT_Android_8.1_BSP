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
#include <errno.h>
#include <jni.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <cutils/log.h>
#include <inttypes.h>

static jboolean isAddressExecutable(uintptr_t address) {
    char line[1024];
    jboolean retval = false;
    FILE *fp = fopen("/proc/self/maps", "re");
    if (fp == NULL) {
        ALOGE("Unable to open /proc/self/maps: %s", strerror(errno));
        return false;
    }
    while(fgets(line, sizeof(line), fp) != NULL) {
        uintptr_t start;
        uintptr_t end;
        char permissions[10];
        int scan = sscanf(line, "%" SCNxPTR "-%" SCNxPTR " %9s ", &start, &end, permissions);
        if ((scan == 3) && (start <= address) && (address < end)) {
            retval = (permissions[2] == 'x');
            break;
        }
    }
    fclose(fp);
    return retval;
}

static jboolean android_os_cts_NoExecutePermissionTest_isMyCodeExecutable(JNIEnv*, jobject)
{
    return isAddressExecutable((uintptr_t) __builtin_return_address(0));
}

static jboolean android_os_cts_NoExecutePermissionTest_isStackExecutable(JNIEnv*, jobject)
{
    unsigned int foo;
    return isAddressExecutable((uintptr_t) &foo);
}


static jboolean android_os_cts_NoExecutePermissionTest_isHeapExecutable(JNIEnv*, jobject)
{
    unsigned int* foo = (unsigned int *) malloc(sizeof(unsigned int));
    if (foo == NULL) {
        ALOGE("Unable to allocate memory");
        return false;
    }
    jboolean result = isAddressExecutable((uintptr_t) foo);
    free(foo);
    return result;
}

static JNINativeMethod gMethods[] = {
    {  "isMyCodeExecutable", "()Z",
            (void *) android_os_cts_NoExecutePermissionTest_isMyCodeExecutable  },
    {  "isStackExecutable", "()Z",
            (void *) android_os_cts_NoExecutePermissionTest_isStackExecutable  },
    {  "isHeapExecutable", "()Z",
            (void *) android_os_cts_NoExecutePermissionTest_isHeapExecutable  }
};

int register_android_os_cts_NoExecutePermissionTest(JNIEnv* env)
{
    jclass clazz = env->FindClass("android/os/cts/NoExecutePermissionTest");

    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
