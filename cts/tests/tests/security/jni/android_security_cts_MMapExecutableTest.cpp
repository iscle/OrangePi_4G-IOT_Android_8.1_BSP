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

#define LOG_TAG "MMapExecutableTest"

#include <android/log.h>
#include <cutils/log.h>
#include <fcntl.h>
#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <nativehelper/ScopedUtfChars.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

const size_t kOffset = 4096;

// Verify that we can mmap a region of a file with a non-zero offset executable
static jboolean mmap_executable(JNIEnv *env, jobject, jstring jfilename) {
    if (jfilename == NULL) {
        jniThrowNullPointerException(env, NULL);
        return false;
    }

    ScopedUtfChars filename(env, jfilename);
    int fd = open(filename.c_str(), O_RDONLY);
    if (fd == -1) {
        ALOGE("open %s: %s", filename.c_str(), strerror(errno));
        return false;
    }

    struct stat stat_buf;
    if (fstat(fd, &stat_buf) == -1) {
        ALOGE("fstat %s: %s", filename.c_str(), strerror(errno));
        return false;
    }

    if (stat_buf.st_size < kOffset) {
        ALOGE("file %s is too small", filename.c_str());
        return false;
    }

    void * mem =
            mmap(NULL, stat_buf.st_size - kOffset,
                 PROT_EXEC | PROT_READ, MAP_PRIVATE, fd, kOffset);
    if (mem == MAP_FAILED) {
        ALOGE("mmap %s: %s", filename.c_str(), strerror(errno));
        return false;
    }

    if (munmap(mem, stat_buf.st_size - kOffset) == -1) {
        ALOGE("munmap %s: %s", filename.c_str(), strerror(errno));
        return false;
    }

    return true;
}

static JNINativeMethod gMethods[] = {
    { (char*)"mmapExecutable",
      (char*)"(Ljava/lang/String;)Z", (void *)mmap_executable }
};

int register_android_security_cts_MMapExecutableTest(JNIEnv* env) {
    jclass clazz = env->FindClass("android/security/cts/MMapExecutableTest");
    return env->RegisterNatives(
            clazz, gMethods, sizeof(gMethods) / sizeof(JNINativeMethod));
}
