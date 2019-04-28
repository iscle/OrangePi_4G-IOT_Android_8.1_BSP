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
 */

#include "minijail.h"
#include <unistd.h>
#include <sys/types.h>
#include <signal.h>

#include <libminijail.h>
#include <scoped_minijail.h>
#include <android/log.h>

#ifndef LOG_TAG
#define LOG_TAG "minijail"
#endif

#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR  , LOG_TAG, __VA_ARGS__)


/*
 * Class:     com_android_tv_tuner_exoplayer_ffmpeg_FfmpegDecoderService
 * Method:    nativeSetupMinijail
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_com_android_tv_tuner_exoplayer_ffmpeg_FfmpegDecoderService_nativeSetupMinijail
(JNIEnv *, jobject, jint policyFd) {
    ScopedMinijail jail{minijail_new()};
    if (!jail) {
        ALOGE("Failed to create minijail");
    }

    minijail_no_new_privs(jail.get());
    minijail_log_seccomp_filter_failures(jail.get());
    minijail_use_seccomp_filter(jail.get());
    minijail_set_seccomp_filter_tsync(jail.get());
    // Transfer ownership of |policy_fd|.
    minijail_parse_seccomp_filters_from_fd(jail.get(), policyFd);
    minijail_enter(jail.get());
    close(policyFd);
}

/*
 * Class:     com_android_tv_tuner_exoplayer_ffmpeg_FfmpegDecoderService
 * Method:    nativeTestMinijail
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_com_android_tv_tuner_exoplayer_ffmpeg_FfmpegDecoderService_nativeTestMinijail
(JNIEnv *, jobject) {
    kill(getpid(), SIGUSR1);
}