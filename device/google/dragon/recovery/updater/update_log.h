/*
 * Copyright (C) 2015 The Android Open Source Project
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
#ifndef _RECOVERY_UPDATE_LOG_H_
#define _RECOVERY_UPDATE_LOG_H_

#ifdef USE_LOGCAT
#include <cutils/log.h>
#else
/* when running in recovery mode, only stdout is logged properly */
#define ALOGD(format, args...) printf("D %s: " format, LOG_TAG, ## args)
#define ALOGI(format, args...) printf("I %s: " format, LOG_TAG, ## args)
#define ALOGW(format, args...) printf("W %s: " format, LOG_TAG, ## args)
#define ALOGE(format, args...) printf("E %s: " format, LOG_TAG, ## args)
#endif

#endif /* _RECOVERY_UPDATE_LOG_H_ */
