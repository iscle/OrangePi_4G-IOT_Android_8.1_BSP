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

#include <bl.h>
#include <hostIntf.h>
#include <hostIntf_priv.h>

#include <variant/variant.h>

#include <plat/cmsis.h>
#include <plat/spi.h>
#include <plat/exti.h>
#include <plat/syscfg.h>

static struct Gpio *mShWakeupGpio;
static struct ChainedIsr mShWakeupIsr;

static bool platWakeupIsr(struct ChainedIsr *isr)
{
    if (!extiIsPendingGpio(mShWakeupGpio))
        return false;

    extiClearPendingGpio(mShWakeupGpio);

    hostIntfRxPacket(!gpioGet(mShWakeupGpio));

    return true;
}

const struct HostIntfComm *platHostIntfInit()
{
    uint32_t priorityGroup = NVIC_GetPriorityGrouping();
    uint32_t priority, preemptPriority, subPriority;
    enum IRQn rx, tx;
    const struct HostIntfComm *ret = NULL;

#if defined(PLATFORM_HOST_INTF_I2C_BUS)
    ret = hostIntfI2cInit(PLATFORM_HOST_INTF_I2C_BUS);
#elif defined(PLATFORM_HOST_INTF_SPI_BUS)
    ret = hostIntfSpiInit(PLATFORM_HOST_INTF_SPI_BUS);

    /* get the rx and tx irq numbers used by the host spi bus */
    ret->request();
    rx = spiRxIrq(PLATFORM_HOST_INTF_SPI_BUS);
    tx = spiTxIrq(PLATFORM_HOST_INTF_SPI_BUS);
    ret->release();

    /* make the tx and rx dma isrs execute before other isrs when multiple
       interrupts are pending (if avaliable subpriority bits).
       We do not change the preempt priority */
    priority = NVIC_GetPriority(rx);
    NVIC_DecodePriority (priority, priorityGroup, &preemptPriority, &subPriority);
    if (&subPriority > 0)
        subPriority --;
    NVIC_SetPriority(rx, NVIC_EncodePriority(priorityGroup, preemptPriority, subPriority));

    priority = NVIC_GetPriority(tx);
    NVIC_DecodePriority (priority, priorityGroup, &preemptPriority, &subPriority);
    if (&subPriority > 0)
        subPriority --;
    NVIC_SetPriority(tx, NVIC_EncodePriority(priorityGroup, preemptPriority, subPriority));

    mShWakeupGpio = gpioRequest(SH_INT_WAKEUP);
    gpioConfigInput(mShWakeupGpio, GPIO_SPEED_LOW, GPIO_PULL_NONE);
    syscfgSetExtiPort(mShWakeupGpio);
    extiEnableIntGpio(mShWakeupGpio, EXTI_TRIGGER_BOTH);
    mShWakeupIsr.func = platWakeupIsr;
    extiChainIsr(SH_EXTI_WAKEUP_IRQ, &mShWakeupIsr);
#else
#error "No host interface bus specified"
#endif
    return ret;
}

uint16_t platHwType(void)
{
    return PLATFORM_HW_TYPE;
}

uint16_t platHwVer(void)
{
    return PLATFORM_HW_VER;
}

uint16_t platBlVer()
{
    return BL.blGetVersion();
}
