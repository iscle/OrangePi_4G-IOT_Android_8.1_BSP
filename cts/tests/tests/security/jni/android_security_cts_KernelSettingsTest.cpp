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
#include <sys/types.h>
#include <unistd.h>
#include <sys/xattr.h>
#include <errno.h>

static jboolean android_security_cts_KernelSettingsTest_supportsXattr(JNIEnv* /* env */, jobject /* thiz */)
{
    int result = getxattr("/system/bin/cat", "security.capability", NULL, 0);
    return ((result != -1) || (errno == ENODATA));
}

static JNINativeMethod gMethods[] = {
    {  "supportsXattr", "()Z",
            (void *) android_security_cts_KernelSettingsTest_supportsXattr },
};

int register_android_security_cts_KernelSettingsTest(JNIEnv* env)
{
    jclass clazz = env->FindClass("android/security/cts/KernelSettingsTest");
    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
