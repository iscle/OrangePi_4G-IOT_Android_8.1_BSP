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

#include <plat/gpio.h>
#include <plat/usart.h>
#include <plat/pwr.h>
#include <usart.h>
#include <gpio.h>

struct StmUsart {
  volatile uint16_t SR;
  uint8_t unused0[2];
  volatile uint16_t DR;
  uint8_t unused1[2];
  volatile uint16_t BRR;
  uint8_t unused2[2];
  volatile uint16_t CR1;
  uint8_t unused3[2];
  volatile uint16_t CR2;
  uint8_t unused4[2];
  volatile uint16_t CR3;
  uint8_t unused5[2];
  volatile uint16_t GTPR;
  uint8_t unused6[2];
};

static const uint32_t mUsartPorts[] = {
    USART1_BASE,
    USART2_BASE,
    USART3_BASE,
    UART4_BASE,
    UART5_BASE,
    USART6_BASE,
};

static const uint32_t mUsartPeriphs[] = {
    PERIPH_APB2_USART1,
    PERIPH_APB1_USART2,
    PERIPH_APB1_USART3,
    PERIPH_APB1_UART4,
    PERIPH_APB1_UART5,
    PERIPH_APB2_USART6,
};

static uint8_t mUsartBusses[] = {
    PERIPH_BUS_APB2,
    PERIPH_BUS_APB1,
    PERIPH_BUS_APB1,
    PERIPH_BUS_APB1,
    PERIPH_BUS_APB1,
    PERIPH_BUS_APB2,
};

static bool mUsartHasFlowControl[] = {
    true,
    true,
    true,
    false,
    false,
    true,
};

static enum StmGpioAltFunc mUsartAlt[] = {
    GPIO_AF_USART1,
    GPIO_AF_USART2,
    GPIO_AF00,
    GPIO_AF00,
    GPIO_AF00,
    GPIO_AF_USART6,
};

void usartOpen(struct usart* __restrict usart, UsartPort port,
                uint32_t txGpioNum, uint32_t rxGpioNum,
                uint32_t baud, UsartDataBitsCfg data_bits,
                UsatStopBitsCfg stop_bits, UsartParityCfg parity,
                UsartFlowControlCfg flow_control)
{
    static const uint16_t stopBitsVals[] = {0x1000, 0x0000, 0x3000, 0x2000}; // indexed by UsatStopBitsCfg
    static const uint16_t wordLengthVals[] = {0x0000, 0x1000}; // indexed by UsartDataBitsCfg
    static const uint16_t parityVals[] = {0x0000, 0x0400, 0x0600}; // indexed by UsartParityCfg
    static const uint16_t flowCtrlVals[] = {0x0000, 0x0100, 0x0200, 0x0300}; // indexed by UsartFlowControlCfg
    struct StmUsart *block = (struct StmUsart*)mUsartPorts[usart->unit = --port];
    uint32_t baseClk, div, intPart, fraPart;

    /* configure tx/rx gpios */

    usart->rx = gpioRequest(rxGpioNum); /* rx */
    gpioConfigAlt(usart->rx, GPIO_SPEED_LOW, GPIO_PULL_UP, GPIO_OUT_PUSH_PULL, mUsartAlt[port]);
    usart->tx = gpioRequest(txGpioNum); /* tx */
    gpioConfigAlt(usart->tx, GPIO_SPEED_LOW, GPIO_PULL_UP, GPIO_OUT_PUSH_PULL, mUsartAlt[port]);

    /* enable clock */
    pwrUnitClock(mUsartBusses[port], mUsartPeriphs[port], true);

    /* sanity checks */
    if (!mUsartHasFlowControl[port])
        flow_control = USART_FLOW_CONTROL_NONE;

    /* basic config as required + oversample by 8, tx+rx on */
    block->CR2 = (block->CR2 &~ 0x3000) | stopBitsVals[stop_bits];
    block->CR1 = (block->CR1 &~ 0x1600) | wordLengthVals[data_bits] | parityVals[parity] | 0x800C;
    block->CR3 = (block->CR3 &~ 0x0300) | flowCtrlVals[flow_control];

    /* clocking calc */
    baseClk = pwrGetBusSpeed(mUsartBusses[port]);
    div = (baseClk * 25) / (baud * 2);
    intPart = div / 100;
    fraPart = div % 100;

    /* clocking munging */
    intPart = intPart << 4;
    fraPart = ((fraPart * 8 + 50) / 100) & 7;
    block->BRR = intPart | fraPart;

    /* enable */
    block->CR1 |= 0x2000;
}

void usartClose(const struct usart* __restrict usart)
{
    struct StmUsart *block = (struct StmUsart*)mUsartPorts[usart->unit];

    /* Disable USART */
    block->CR1 &=~ 0x2000;

    /* Disable USART clock */
    pwrUnitClock(mUsartBusses[usart->unit], mUsartPeriphs[usart->unit], false);

    /* Release gpios */
    gpioRelease(usart->rx);
    gpioRelease(usart->tx);
}

/*
 * don't use this immediately after usart initialization
 * the test is valid only after the first char has been sent out
 */
void usartFlush(const struct usart* __restrict usart)
{
    struct StmUsart *block = (struct StmUsart*)mUsartPorts[usart->unit];

    while ((block->SR & 0x00c0) != 0x00c0);
}

void usartPutchar(const struct usart* __restrict usart, char c)
{
    struct StmUsart *block = (struct StmUsart*)mUsartPorts[usart->unit];

    /* wait for ready */
    while (!(block->SR & 0x0080));

    /* send */
    block->DR = (uint8_t)c;
}
