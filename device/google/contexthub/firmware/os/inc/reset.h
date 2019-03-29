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

#ifndef RESET_H_
#define RESET_H_

#ifdef __cplusplus
extern "C" {
#endif

#define RESET_HARDWARE              0x00000001UL
#define RESET_SOFTWARE              0x00000002UL
#define RESET_POWER_ON              0x00000004UL
#define RESET_BROWN_OUT             0x00000008UL
#define RESET_POWER_MANAGEMENT      0x00000010UL
#define RESET_WINDOW_WATCHDOG       0x00000020UL
#define RESET_INDEPENDENT_WATCHDOG  0x00000040UL

#ifdef __cplusplus
}
#endif

#endif
