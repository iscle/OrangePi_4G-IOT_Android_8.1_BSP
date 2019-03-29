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
 */

#ifndef CTS_MEDIA_TEST_AAUDIO_H
#define CTS_MEDIA_TEST_AAUDIO_H

#define NANOS_PER_MICROSECOND  ((int64_t)1000)
#define NANOS_PER_MILLISECOND  (NANOS_PER_MICROSECOND * 1000)
#define MICROS_PER_MILLISECOND 1000
#define MILLIS_PER_SECOND      1000
#define NANOS_PER_SECOND       (NANOS_PER_MILLISECOND * MILLIS_PER_SECOND)

#define DEFAULT_STATE_TIMEOUT  (500 * NANOS_PER_MILLISECOND)
#define DEFAULT_READ_TIMEOUT   (300 * NANOS_PER_MILLISECOND)

#endif //CTS_MEDIA_TEST_AAUDIO_H
