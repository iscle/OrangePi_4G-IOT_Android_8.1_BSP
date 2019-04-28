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

#ifndef CAM_COND_H
#define CAM_COND_H

#define PTHREAD_COND_INIT(cond) \
  ({                                   \
    int rc = 0;                       \
    pthread_condattr_t cond_attr;     \
    rc = pthread_condattr_init(&cond_attr);   \
    if (rc == 0) {                            \
      rc = pthread_condattr_setclock(&cond_attr, CLOCK_MONOTONIC);  \
      if (rc == 0) {                                 \
        rc = pthread_cond_init(cond, &cond_attr);  \
      } \
    } \
    rc; \
  })

#endif // CAM_COND_H
