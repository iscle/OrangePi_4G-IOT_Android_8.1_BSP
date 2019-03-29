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

#ifndef _VARIANT_LNX_H_
#define _VARIANT_LNX_H_

#ifdef __cplusplus
extern "C" {
#endif

#define PLATFORM_HW_TYPE  0x8086

#define PLAT_HAS_NO_U_TYPES_H

//we have no LSE in linux
#define HAVE_LSE    false

//i2c bus for comms (dummy)
#define PLATFORM_HOST_INTF_I2C_BUS  12345




#ifdef __cplusplus
}
#endif

#endif

