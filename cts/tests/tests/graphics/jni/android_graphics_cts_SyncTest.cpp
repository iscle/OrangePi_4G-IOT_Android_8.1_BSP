/*
 * Copyright 2017 The Android Open Source Project
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

#define LOG_TAG "SyncTest"

#include <poll.h>
#include <unistd.h>

#include <array>
#include <memory>

#include <jni.h>

#include <android/sync.h>

namespace {

enum {
    STATUS_ERROR = -1,
    STATUS_UNSIGNALED = 0,
    STATUS_SIGNALED = 1,
};

jboolean syncPoll(JNIEnv* env, jclass /*clazz*/, jintArray fds_array, jintArray status_array) {
    jsize n = env->GetArrayLength(fds_array);
    if (env->GetArrayLength(status_array) != n)
        return JNI_FALSE;
    std::unique_ptr<pollfd[]> pollfds = std::make_unique<pollfd[]>(n);

    jint* fds = static_cast<jint*>(env->GetPrimitiveArrayCritical(fds_array, nullptr));
    for (jsize i = 0; i < n; i++) {
        pollfds[i].fd = fds[i];
        pollfds[i].events = POLLIN;
    }
    env->ReleasePrimitiveArrayCritical(fds_array, fds, 0);

    int ret;
    do {
        ret = poll(pollfds.get(), n, -1 /* infinite timeout */);
    } while (ret == -1 && errno == EINTR);
    if (ret == -1)
        return JNI_FALSE;

    jint* status = static_cast<jint*>(env->GetPrimitiveArrayCritical(status_array, nullptr));
    for (jsize i = 0; i < n; i++) {
        if (pollfds[i].fd < 0)
            continue;
        if ((pollfds[i].revents & (POLLERR | POLLNVAL)) != 0)
            status[i] = STATUS_ERROR;
        else if ((pollfds[i].revents & POLLIN) != 0)
            status[i] = STATUS_SIGNALED;
        else
            status[i] = STATUS_UNSIGNALED;
    }
    env->ReleasePrimitiveArrayCritical(status_array, status, 0);

    return JNI_TRUE;
}

jint syncMerge(JNIEnv* env, jclass /*clazz*/, jstring nameStr, jint fd1, jint fd2) {
    const char* name = env->GetStringUTFChars(nameStr, nullptr);
    int32_t result_fd = sync_merge(name, fd1, fd2);
    env->ReleaseStringUTFChars(nameStr, name);
    return result_fd;
}

jobject syncFileInfo(JNIEnv* /*env*/, jclass /*clazz*/, jint fd) {
    auto info = sync_file_info(fd);
    if (!info) return nullptr;
    // TODO: convert to SyncFileInfo
    sync_file_info_free(info);
    return nullptr;
}

void syncClose(int fd) {
    close(fd);
}

const std::array<JNINativeMethod, 4> JNI_METHODS = {{
    { "nSyncPoll", "([I[I)Z", (void*)syncPoll },
    { "nSyncMerge", "(Ljava/lang/String;II)I", (void*)syncMerge },
    { "nSyncFileInfo", "(I)Landroid/graphics/cts/SyncTest/SyncFileInfo;", (void*)syncFileInfo },
    { "nSyncClose", "(I)V", (void*)syncClose },
}};

}

int register_android_graphics_cts_SyncTest(JNIEnv* env) {
    jclass clazz = env->FindClass("android/graphics/cts/SyncTest");
    return env->RegisterNatives(clazz, JNI_METHODS.data(), JNI_METHODS.size());
}
