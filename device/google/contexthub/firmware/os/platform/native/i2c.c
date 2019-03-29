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

#include <errno.h>
#include <stdint.h>
#include <gpio.h>
#include <i2c.h>
#include <seos.h>
#include <util.h>
#include <atomicBitset.h>
#include <atomic.h>




int i2cMasterRequest(I2cBus busId, I2cSpeed speed)
{
    return -EINVAL;
}

int i2cMasterRelease(I2cBus busId)
{
    return -EINVAL;
}

int i2cMasterTxRx(I2cBus busId, I2cAddr addr,
        const void *txBuf, size_t txSize, void *rxBuf, size_t rxSize,
        I2cCallbackF callback, void *cookie)
{
    return -EINVAL;
}

int i2cSlaveRequest(I2cBus busId, I2cAddr addr)
{
    return -EINVAL;
}

int i2cSlaveRelease(I2cBus busId)
{
    return -EINVAL;
}

void i2cSlaveEnableRx(I2cBus busId, void *rxBuf, size_t rxSize,
        I2cCallbackF callback, void *cookie)
{
    //
}

int i2cSlaveTxPreamble(I2cBus busId, uint8_t byte, I2cCallbackF callback, void *cookie)
{
    return -EBUSY;
}

int i2cSlaveTxPacket(I2cBus busId, const void *txBuf, size_t txSize, I2cCallbackF callback, void *cookie)
{
    return -EBUSY;
}
