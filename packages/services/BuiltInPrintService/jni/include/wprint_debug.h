/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
 * Copyright (C) 2013 Hewlett-Packard Development Company, L.P.
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
#ifndef __WPRINT_DEBUG_H__
#define __WPRINT_DEBUG_H__

#include <stdio.h>
#include <stdarg.h>
#include <android/log.h>

#define LEVEL_DEBUG     3
#define LEVEL_INFO      4
#define LEVEL_ERROR     6

/*
 * Set LOG_LEVEL to the minimum level of logging required
 */
#ifndef LOG_LEVEL
#define LOG_LEVEL       LEVEL_ERROR
#endif // LOG_LEVEL

#if LOG_LEVEL > LEVEL_DEBUG
#define LOGD(...)
#else
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#endif

#if LOG_LEVEL > LEVEL_INFO
#define LOGI(...)
#else
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#endif

#if LOG_LEVEL > LEVEL_ERROR
#define LOGE(...)
#else
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#endif

#endif // __WPRINT_DEBUG_H__