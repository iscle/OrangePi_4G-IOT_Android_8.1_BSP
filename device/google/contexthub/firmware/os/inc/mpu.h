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

#ifndef _MPU_H_
#define _MPU_H_

#ifdef __cplusplus
extern "C" {
#endif


/*
 * MPU is very specific to each platform, so here really we just
 * leave it to the platform code to do the right thing for all the
 * requisite areas and such. Thhe simplest valid MPU implementation
 * will do nothing. Clearly we should strive to do more than that
 */

void mpuStart(void);
void mpuAllowRamExecution(bool allowSvcExecute);         /* for Supervisor only, if possible */
void mpuAllowRomWrite(bool allowSvcWrite);     /* for Supervisor only, if possible */
void mpuShow(void);


#ifdef __cplusplus
}
#endif

#endif

