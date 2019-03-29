/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef _PRINTF_H_
#define _PRINTF_H_

#ifdef __cplusplus
extern "C" {
#endif

#include <stdbool.h>
#include <stdint.h>
#include <stdarg.h>

#define PRINTF_FLAG_CHRE            0x00000001
#define PRINTF_FLAG_SHORT_DOUBLE    0x00000002

typedef bool (*printf_write_c)(void* userData, char c);		//callback can return false anytime to abort printing immediately

uint32_t cvprintf(printf_write_c writeF, uint32_t flags, void* writeD, const char* fmtStr, va_list vl);

#ifdef __cplusplus
}
#endif

#endif
