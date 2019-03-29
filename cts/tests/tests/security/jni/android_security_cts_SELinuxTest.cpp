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
#include <selinux/selinux.h>
#include <JNIHelp.h>
#include <ScopedLocalRef.h>
#include <ScopedUtfChars.h>

#include <memory>

struct SecurityContext_Delete {
    void operator()(security_context_t p) const {
        freecon(p);
    }
};
typedef std::unique_ptr<char[], SecurityContext_Delete> Unique_SecurityContext;

/*
 * Function: getFileContext
 * Purpose: retrieves the context associated with the given path in the file system
 * Parameters:
 *        path: given path in the file system
 * Returns:
 *        string representing the security context string of the file object
 *        the string may be NULL if an error occured
 * Exceptions: NullPointerException if the path object is null
 */
static jstring getFileContext(JNIEnv *env, jobject, jstring pathStr) {
    ScopedUtfChars path(env, pathStr);
    if (path.c_str() == NULL) {
        return NULL;
    }

    security_context_t tmp = NULL;
    int ret = getfilecon(path.c_str(), &tmp);
    Unique_SecurityContext context(tmp);

    ScopedLocalRef<jstring> securityString(env, NULL);
    if (ret != -1) {
        securityString.reset(env->NewStringUTF(context.get()));
    }

    return securityString.release();
}

static JNINativeMethod gMethods[] = {
    { "getFileContext", "(Ljava/lang/String;)Ljava/lang/String;",
            (void*) getFileContext },
};

int register_android_security_cts_SELinuxTest(JNIEnv* env)
{
    jclass clazz = env->FindClass("android/security/cts/SELinuxTest");

    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
