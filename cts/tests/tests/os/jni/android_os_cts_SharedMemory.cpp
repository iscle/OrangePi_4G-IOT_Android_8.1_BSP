/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include <android/sharedmem_jni.h>

#include <sys/mman.h>
#include <unistd.h>

jboolean nWriteByte(JNIEnv* env, jobject, jobject jSharedMemory, jint index, jbyte value) {
    int fd = ASharedMemory_dupFromJava(env, jSharedMemory);
    if (fd == -1) return false;
    void* addr = mmap(nullptr, 1, PROT_READ | PROT_WRITE, MAP_SHARED, fd, index);
    if (addr == nullptr) {
        close(fd);
        return false;
    }
    reinterpret_cast<int8_t*>(addr)[0] = value;
    munmap(addr, 1);
    close(fd);
    return true;
}

static JNINativeMethod gMethods[] = {
    {  "nWriteByte", "(Landroid/os/SharedMemory;IB)Z", (void *) nWriteByte },
};

int register_android_os_cts_SharedMemoryTest(JNIEnv* env)
{
    jclass clazz = env->FindClass("android/os/cts/SharedMemoryTest");

    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
