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

#ifndef _STM32F4xx_EEDATA_H_
#define _STM32F4xx_EEDATA_H_

#include <eeData.h>
#include <seos.h>

struct Stm32f4xxEedataHdr {
    uint32_t info;
};

struct Stm32f4xxEedataEncrKey {
    struct Stm32f4xxEedataHdr hdr;
    struct SeosEedataEncrKeyData data;
} __attribute__((packed));


#define PREPOPULATED_ENCR_KEY(name, keyid, ...) \
    const struct Stm32f4xxEedataEncrKey __attribute__ ((section (".eedata"))) __EE__ ## name = { { EE_DATA_NAME_ENCR_KEY + sizeof(struct SeosEedataEncrKeyData) * (EE_DATA_NAME_MAX + 1)}, {keyid, {__VA_ARGS__}}}



#endif
