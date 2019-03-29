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

#ifndef __HOSTINTF_PRIV_H
#define __HOSTINTF_PRIV_H

#include <i2c.h>
#include <stdint.h>
#include <stddef.h>


/**
 * hostIntf communication abstraction layer
 */

typedef void (*HostIntfCommCallbackF)(size_t bytesTransferred, int err);
struct HostIntfComm {
    int (*request)(void);

    int (*rxPacket)(void *rxBuf, size_t rxSize, HostIntfCommCallbackF callback);
    int (*txPacket)(const void *txBuf, size_t txSize,
            HostIntfCommCallbackF callback);

    int (*release)(void);
};

/**
 * Returns a HostIntfOps backed by I2C
 */
const struct HostIntfComm *hostIntfI2cInit(uint32_t busId);

/**
 * Returns a HostIntfOps backed by SPI
 */
const struct HostIntfComm *hostIntfSpiInit(uint8_t busId);


/**
 * Platform-internal hostIntf API
 */

/**
 * Returns the platform's communication implementation.  The platform should
 * delegate this to hostIntfI2cInit() or hostIntfSpiInit() as appropriate.
 */
const struct HostIntfComm *platHostIntfInit();

/**
 * Returns the platform's hardware type (16-bit, host byte order)
 */
uint16_t platHwType(void);

/**
 * Returns the platform's hardware version (16-bit, host byte order)
 */
uint16_t platHwVer(void);

/**
 * Returns the platform's bootloader version (16-bit, host byte order)
 */
uint16_t platBlVer(void);

#endif /* __HOSTINTF_PRIV_H */
