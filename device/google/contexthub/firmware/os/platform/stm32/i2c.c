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
#include <string.h>

#include <gpio.h>
#include <i2c.h>
#include <seos.h>
#include <util.h>
#include <gpio.h>
#include <atomicBitset.h>
#include <atomic.h>
#include <platform.h>

#include <plat/cmsis.h>
#include <plat/dma.h>
#include <plat/gpio.h>
#include <plat/i2c.h>
#include <plat/pwr.h>
#include <plat/plat.h>

#include <cpu/barrier.h>

#define I2C_VERBOSE_DEBUG       0
#define I2C_MAX_QUEUE_DEPTH     5

#if I2C_VERBOSE_DEBUG
#define i2c_log_debug(x) osLog(LOG_DEBUG, x "\n")
#else
#define i2c_log_debug(x) do {} while(0)
#endif

#define I2C_CR1_PE          (1 << 0)
#define I2C_CR1_SMBUS       (1 << 1)
#define I2C_CR1_SMBTYPE     (1 << 3)
#define I2C_CR1_ENARP       (1 << 4)
#define I2C_CR1_ENPEC       (1 << 5)
#define I2C_CR1_ENGC        (1 << 6)
#define I2C_CR1_NOSTRETCH   (1 << 7)
#define I2C_CR1_START       (1 << 8)
#define I2C_CR1_STOP        (1 << 9)
#define I2C_CR1_ACK         (1 << 10)
#define I2C_CR1_POS         (1 << 11)
#define I2C_CR1_PEC         (1 << 12)
#define I2C_CR1_ALERT       (1 << 13)
#define I2C_CR1_SWRST       (1 << 15)

#define I2C_CR2_FREQ(x)     ((x) & I2C_CR2_FREQ_MASK)
#define I2C_CR2_FREQ_MASK   0x3F
#define I2C_CR2_ITERREN     (1 << 8)
#define I2C_CR2_ITEVTEN     (1 << 9)
#define I2C_CR2_ITBUFEN     (1 << 10)
#define I2C_CR2_DMAEN       (1 << 11)
#define I2C_CR2_LAST        (1 << 12)

#define I2C_OAR1_ADD7(x)    (((x) & I2C_OAR1_ADD7_MASK) << 1)
#define I2C_OAR1_ADD7_MASK  0x7F
#define I2C_OAR1_ADD10(x)   ((x) & I2C_OAR1_ADD10_MASK)
#define I2C_OAR1_ADD10_MASK 0x3FF
#define I2C_OAR1_ADDMODE    (1 << 15)

#define I2C_SR1_SB          (1 << 0)
#define I2C_SR1_ADDR        (1 << 1)
#define I2C_SR1_BTF         (1 << 2)
#define I2C_SR1_ADD10       (1 << 3)
#define I2C_SR1_STOPF       (1 << 4)
#define I2C_SR1_RXNE        (1 << 6)
#define I2C_SR1_TXE         (1 << 7)
#define I2C_SR1_BERR        (1 << 8)
#define I2C_SR1_ARLO        (1 << 9)
#define I2C_SR1_AF          (1 << 10)
#define I2C_SR1_OVR         (1 << 11)
#define I2C_SR1_PECERR      (1 << 12)
#define I2C_SR1_TIMEOUT     (1 << 14)
#define I2C_SR1_SMBALERT    (1 << 15)

#define I2C_SR2_MSL         (1 << 0)
#define I2C_SR2_BUSY        (1 << 1)
#define I2C_SR2_TRA         (1 << 2)
#define I2C_SR2_GENCALL     (1 << 4)
#define I2C_SR2_SMBDEFAULT  (1 << 5)
#define I2C_SR2_SMBHOST     (1 << 6)
#define I2C_SR2_DUALF       (1 << 7)

#define I2C_CCR(x)          ((x) & I2C_CCR_MASK)
#define I2C_CCR_MASK        0xFFF
#define I2C_CCR_DUTY_16_9   (1 << 14)
#define I2C_CCR_FM          (1 << 15)

#define I2C_TRISE(x)        ((x) & I2C_TRISE_MASK)
#define I2C_TRISE_MASK      0x3F

struct StmI2c {
    volatile uint32_t CR1;
    volatile uint32_t CR2;
    volatile uint32_t OAR1;
    volatile uint32_t OAR2;
    volatile uint32_t DR;
    volatile uint32_t SR1;
    volatile uint32_t SR2;
    volatile uint32_t CCR;
    volatile uint32_t TRISE;
    volatile uint32_t FLTR;
};

enum StmI2cSpiMasterState
{
    STM_I2C_MASTER_IDLE,
    STM_I2C_MASTER_START,
    STM_I2C_MASTER_TX_ADDR,
    STM_I2C_MASTER_TX_DATA,
    STM_I2C_MASTER_RX_ADDR,
    STM_I2C_MASTER_RX_DATA,
};

struct I2cStmState {
    struct {
        union {
            uint8_t *buf;
            const uint8_t *cbuf;
            uint8_t byte;
        };
        size_t size;
        size_t offset;
        bool preamble;

        I2cCallbackF callback;
        void *cookie;
    } rx, tx;

    enum {
        STM_I2C_DISABLED,
        STM_I2C_SLAVE,
        STM_I2C_MASTER,
    } mode;

    enum {
        STM_I2C_SLAVE_IDLE,
        STM_I2C_SLAVE_RX_ARMED,
        STM_I2C_SLAVE_RX,
        STM_I2C_SLAVE_TX_ARMED,
        STM_I2C_SLAVE_TX,
    } slaveState;

    // StmI2cSpiMasterState
    uint8_t masterState;
    uint16_t tid;
};

struct StmI2cCfg {
    struct StmI2c *regs;

    uint32_t clock;

    IRQn_Type irqEv;
    IRQn_Type irqEr;
};

struct StmI2cDev {
    const struct StmI2cCfg *cfg;
    const struct StmI2cBoardCfg *board;
    struct I2cStmState state;

    uint32_t next;
    uint32_t last;

    struct Gpio *scl;
    struct Gpio *sda;

    uint8_t addr;
};

static const struct StmI2cCfg mStmI2cCfgs[] = {
    [0] = {
        .regs = (struct StmI2c *)I2C1_BASE,

        .clock = PERIPH_APB1_I2C1,

        .irqEv = I2C1_EV_IRQn,
        .irqEr = I2C1_ER_IRQn,
    },
    [1] = {
        .regs = (struct StmI2c *)I2C2_BASE,

        .clock = PERIPH_APB1_I2C2,

        .irqEv = I2C2_EV_IRQn,
        .irqEr = I2C2_ER_IRQn,
    },
    [2] = {
        .regs = (struct StmI2c *)I2C3_BASE,

        .clock = PERIPH_APB1_I2C3,

        .irqEv = I2C3_EV_IRQn,
        .irqEr = I2C3_ER_IRQn,
    },
};

static struct StmI2cDev mStmI2cDevs[ARRAY_SIZE(mStmI2cCfgs)];

struct StmI2cXfer
{
    uint32_t        id;
    const void     *txBuf;
    size_t          txSize;
    void           *rxBuf;
    size_t          rxSize;
    I2cCallbackF    callback;
    void           *cookie;
    uint8_t         busId; /* for us these are both fine in a uint 8 */
    uint8_t         addr;
    uint16_t        tid;
};

ATOMIC_BITSET_DECL(mXfersValid, I2C_MAX_QUEUE_DEPTH, static);
static struct StmI2cXfer mXfers[I2C_MAX_QUEUE_DEPTH] = { };

static inline struct StmI2cXfer *stmI2cGetXfer(void)
{
    int32_t idx = atomicBitsetFindClearAndSet(mXfersValid);

    if (idx < 0)
        return NULL;
    else
        return mXfers + idx;
}

static inline void stmI2cPutXfer(struct StmI2cXfer *xfer)
{
    if (xfer)
        atomicBitsetClearBit(mXfersValid, xfer - mXfers);
}

static inline void stmI2cAckEnable(struct StmI2cDev *pdev)
{
    pdev->cfg->regs->CR1 |= I2C_CR1_ACK;
}

static inline void stmI2cAckDisable(struct StmI2cDev *pdev)
{
    pdev->cfg->regs->CR1 &= ~I2C_CR1_ACK;
}

static inline void stmI2cDmaEnable(struct StmI2cDev *pdev)
{
    pdev->cfg->regs->CR2 |= I2C_CR2_DMAEN;
}

static inline void stmI2cDmaDisable(struct StmI2cDev *pdev)
{
    pdev->cfg->regs->CR2 &= ~I2C_CR2_DMAEN;
}

static inline void stmI2cStopEnable(struct StmI2cDev *pdev)
{
    struct StmI2c *regs = pdev->cfg->regs;

    while (regs->CR1 & (I2C_CR1_STOP | I2C_CR1_START))
        ;
    regs->CR1 |= I2C_CR1_STOP;
}

static inline void stmI2cStartEnable(struct StmI2cDev *pdev)
{
    struct StmI2c *regs = pdev->cfg->regs;

    while (regs->CR1 & (I2C_CR1_STOP | I2C_CR1_START))
        ;
    regs->CR1 |= I2C_CR1_START;
}

static inline void stmI2cIrqEnable(struct StmI2cDev *pdev,
        uint32_t mask)
{
    pdev->cfg->regs->CR2 |= mask;
}

static inline void stmI2cIrqDisable(struct StmI2cDev *pdev,
        uint32_t mask)
{
    pdev->cfg->regs->CR2 &= ~mask;
}

static inline void stmI2cEnable(struct StmI2cDev *pdev)
{
    pdev->cfg->regs->CR1 |= I2C_CR1_PE;
}

static inline void stmI2cDisable(struct StmI2cDev *pdev)
{
    pdev->cfg->regs->CR1 &= ~I2C_CR1_PE;
}

static inline void stmI2cSpeedSet(struct StmI2cDev *pdev,
        const uint32_t speed)
{
    struct StmI2c *regs = pdev->cfg->regs;
    int ccr, ccr_1, ccr_2;
    int apb1_clk;

    apb1_clk = pwrGetBusSpeed(PERIPH_BUS_APB1);

    regs->CR2 = (regs->CR2 & ~I2C_CR2_FREQ_MASK) |
                 I2C_CR2_FREQ(apb1_clk / 1000000);

    if (speed <= 100000) {
        ccr = apb1_clk / (speed * 2);
        if (ccr < 4)
            ccr = 4;
        regs->CCR = I2C_CCR(ccr);

        regs->TRISE = I2C_TRISE((apb1_clk / 1000000) + 1);
    } else if (speed <= 400000) {
        ccr_1 = apb1_clk / (speed * 3);
        if (ccr_1 == 0 || apb1_clk / (ccr_1 * 3) > speed)
            ccr_1 ++;
        ccr_2 = apb1_clk / (speed * 25);
        if (ccr_2 == 0 || apb1_clk / (ccr_2 * 25) > speed)
            ccr_2 ++;

        if ((apb1_clk / (ccr_1 * 3)) > (apb1_clk / (ccr_2 * 25)))
            regs->CCR = I2C_CCR_FM | I2C_CCR(ccr_1);
        else
            regs->CCR = I2C_CCR_FM | I2C_CCR_DUTY_16_9 | I2C_CCR(ccr_2);

        regs->TRISE = I2C_TRISE(((3*apb1_clk)/10000000) + 1);
    }
}

static inline void stmI2cSlaveIdle(struct StmI2cDev *pdev)
{
    struct I2cStmState *state = &pdev->state;

    state->slaveState = STM_I2C_SLAVE_RX_ARMED;
    stmI2cAckEnable(pdev);
    stmI2cIrqDisable(pdev, I2C_CR2_ITBUFEN | I2C_CR2_ITERREN);
}

static inline void stmI2cInvokeRxCallback(struct I2cStmState *state, size_t tx, size_t rx, int err)
{
    uint16_t oldTid = osSetCurrentTid(state->tid);
    state->rx.callback(state->rx.cookie, tx, rx, err);
    osSetCurrentTid(oldTid);
}

static inline void stmI2cInvokeTxCallback(struct I2cStmState *state, size_t tx, size_t rx, int err)
{
    uint16_t oldTid = osSetCurrentTid(state->tid);
    state->tx.callback(state->tx.cookie, tx, rx, err);
    osSetCurrentTid(oldTid);
}

static inline void stmI2cSlaveRxDone(struct StmI2cDev *pdev)
{
    struct I2cStmState *state = &pdev->state;
    size_t rxOffst = state->rx.offset;

    state->rx.offset = 0;
    stmI2cInvokeRxCallback(state, 0, rxOffst, 0);
}

static inline void stmI2cSlaveTxDone(struct StmI2cDev *pdev)
{
    struct I2cStmState *state = &pdev->state;
    size_t txOffst = state->tx.offset;

    stmI2cSlaveIdle(pdev);
    stmI2cInvokeTxCallback(state, txOffst, 0, 0);
}

static void stmI2cSlaveTxNextByte(struct StmI2cDev *pdev)
{
    struct I2cStmState *state = &pdev->state;
    struct StmI2c *regs = pdev->cfg->regs;

    if (state->tx.preamble) {
        regs->DR = state->tx.byte;
        state->tx.offset++;
    } else if (state->tx.offset < state->tx.size) {
        regs->DR = state->tx.cbuf[state->tx.offset];
        state->tx.offset++;
    } else {
        state->slaveState = STM_I2C_SLAVE_TX_ARMED;
        stmI2cIrqDisable(pdev, I2C_CR2_ITBUFEN);
        stmI2cInvokeTxCallback(state, state->tx.offset, 0, 0);
    }
}

static void stmI2cSlaveAddrMatched(struct StmI2cDev *pdev)
{
    struct I2cStmState *state = &pdev->state;
    struct StmI2c *regs = pdev->cfg->regs;

    i2c_log_debug("addr");

    if (state->slaveState == STM_I2C_SLAVE_RX_ARMED) {
        state->slaveState = STM_I2C_SLAVE_RX;
        stmI2cIrqEnable(pdev, I2C_CR2_ITBUFEN | I2C_CR2_ITERREN);
    } else if (state->slaveState == STM_I2C_SLAVE_TX) {
        stmI2cIrqEnable(pdev, I2C_CR2_ITBUFEN | I2C_CR2_ITERREN);
    }
    /* clear ADDR by doing a dummy reads from SR1 (already read) then SR2 */
    (void)regs->SR2;
}

static void stmI2cSlaveStopRxed(struct StmI2cDev *pdev)
{
    struct StmI2c *regs = pdev->cfg->regs;

    i2c_log_debug("stopf");

    (void)regs->SR1;
    stmI2cEnable(pdev);
    /* clear STOPF by doing a dummy read from SR1 and strobing the PE bit */

    stmI2cSlaveIdle(pdev);
    stmI2cSlaveRxDone(pdev);
}

static inline void stmI2cSlaveRxBufNotEmpty(struct StmI2cDev *pdev)
{
    struct I2cStmState *state = &pdev->state;
    struct StmI2c *regs = pdev->cfg->regs;
    uint8_t data = regs->DR;

    i2c_log_debug("rxne");

    if (state->rx.offset < state->rx.size) {
        state->rx.buf[state->rx.offset] = data;
        state->rx.offset++;
    } else {
        stmI2cAckDisable(pdev);
        /* TODO: error on overflow */
    }
}

static void stmI2cSlaveTxBufEmpty(struct StmI2cDev *pdev)
{
    struct I2cStmState *state = &pdev->state;

    i2c_log_debug("txe");

    if (state->slaveState == STM_I2C_SLAVE_RX) {
        state->slaveState = STM_I2C_SLAVE_TX_ARMED;
        stmI2cIrqDisable(pdev, I2C_CR2_ITBUFEN);
        stmI2cAckDisable(pdev);
        stmI2cSlaveRxDone(pdev);
        /* stmI2cTxNextByte() will happen when the task provides a
           TX buffer; the I2C controller will stretch the clock until then */
    } else {
        stmI2cSlaveTxNextByte(pdev);
    }
}

static void stmI2cSlaveNakRxed(struct StmI2cDev *pdev)
{
    struct I2cStmState *state = &pdev->state;
    struct StmI2c *regs = pdev->cfg->regs;

    i2c_log_debug("af");

    if (state->slaveState == STM_I2C_SLAVE_TX) {
        state->tx.offset--;
        /* NACKs seem to be preceded by a spurious TXNE, so adjust the offset to
           compensate (the corresponding byte written to DR was never actually
           transmitted) */
        stmI2cSlaveTxDone(pdev);
    }
    regs->SR1 &= ~I2C_SR1_AF;
}

static inline void stmI2cMasterTxRxDone(struct StmI2cDev *pdev, int err)
{
    struct I2cStmState *state = &pdev->state;
    size_t txOffst = state->tx.offset;
    size_t rxOffst = state->rx.offset;
    uint32_t id;
    int i;
    struct StmI2cXfer *xfer;

    if (pdev->board->sleepDev >= 0)
        platReleaseDevInSleepMode(pdev->board->sleepDev);

    state->tx.offset = 0;
    state->rx.offset = 0;
    stmI2cInvokeTxCallback(state, txOffst, rxOffst, err);

    do {
        id = atomicAdd32bits(&pdev->next, 1);
    } while (!id);

    for (i=0; i<I2C_MAX_QUEUE_DEPTH; i++) {
        xfer = &mXfers[i];

        if (xfer->busId == (pdev - mStmI2cDevs) &&
                atomicCmpXchg32bits(&xfer->id, id, 0)) {
            pdev->addr = xfer->addr;
            state->tx.cbuf = xfer->txBuf;
            state->tx.offset = 0;
            state->tx.size = xfer->txSize;
            state->tx.callback = xfer->callback;
            state->tx.cookie = xfer->cookie;
            state->rx.buf = xfer->rxBuf;
            state->rx.offset = 0;
            state->rx.size = xfer->rxSize;
            state->rx.callback = NULL;
            state->rx.cookie = NULL;
            state->tid = xfer->tid;
            atomicWriteByte(&state->masterState, STM_I2C_MASTER_START);
            if (pdev->board->sleepDev >= 0)
                platRequestDevInSleepMode(pdev->board->sleepDev, 12);
            stmI2cPutXfer(xfer);
            stmI2cStartEnable(pdev);
            return;
        }
    }

    atomicWriteByte(&state->masterState, STM_I2C_MASTER_IDLE);
}

static void stmI2cMasterDmaTxDone(void *cookie, uint16_t bytesLeft, int err)
{
    struct StmI2cDev *pdev = cookie;
    struct I2cStmState *state = &pdev->state;
    struct StmI2c *regs = pdev->cfg->regs;

    state->tx.offset = state->tx.size - bytesLeft;
    state->tx.size = 0;
    stmI2cDmaDisable(pdev);
    if (err == 0 && state->rx.size > 0) {
        atomicWriteByte(&state->masterState, STM_I2C_MASTER_START);
        stmI2cStartEnable(pdev);
    } else {
        while (!(regs->SR1 & I2C_SR1_BTF))
            ;

        stmI2cStopEnable(pdev);
        stmI2cMasterTxRxDone(pdev, err);
    }
}

static void stmI2cMasterDmaRxDone(void *cookie, uint16_t bytesLeft, int err)
{
    struct StmI2cDev *pdev = cookie;
    struct I2cStmState *state = &pdev->state;

    state->rx.offset = state->rx.size - bytesLeft;
    state->rx.size = 0;

    stmI2cDmaDisable(pdev);
    stmI2cStopEnable(pdev);
    stmI2cMasterTxRxDone(pdev, err);
}

static inline void stmI2cMasterDmaCancel(struct StmI2cDev *pdev)
{
    struct I2cStmState *state = &pdev->state;

    dmaStop(I2C_DMA_BUS, pdev->board->dmaRx.stream);
    state->rx.offset = state->rx.size - dmaBytesLeft(I2C_DMA_BUS,
            pdev->board->dmaRx.stream);
    dmaStop(I2C_DMA_BUS, pdev->board->dmaTx.stream);
    state->tx.offset = state->tx.size - dmaBytesLeft(I2C_DMA_BUS,
            pdev->board->dmaTx.stream);

    stmI2cDmaDisable(pdev);
}

static inline void stmI2cMasterStartDma(struct StmI2cDev *pdev,
        const struct StmI2cDmaCfg *dmaCfg, const void *buf,
        size_t size, DmaCallbackF callback, bool rx, bool last)
{
    struct StmI2c *regs = pdev->cfg->regs;
    struct dmaMode mode;

    memset(&mode, 0, sizeof(mode));
    mode.priority = DMA_PRIORITY_HIGH;
    mode.direction = rx ? DMA_DIRECTION_PERIPH_TO_MEM :
            DMA_DIRECTION_MEM_TO_PERIPH;
    mode.periphAddr = (uintptr_t)&regs->DR;
    mode.minc = true;
    mode.channel = dmaCfg->channel;

    dmaStart(I2C_DMA_BUS, dmaCfg->stream, buf, size, &mode, callback, pdev);
    if (last)
        stmI2cIrqEnable(pdev, I2C_CR2_LAST);
    else
        stmI2cIrqDisable(pdev, I2C_CR2_LAST);
    stmI2cDmaEnable(pdev);
}

static void stmI2cMasterSentStart(struct StmI2cDev *pdev)
{
    struct I2cStmState *state = &pdev->state;
    struct StmI2c *regs = pdev->cfg->regs;

    if (atomicReadByte(&state->masterState) == STM_I2C_MASTER_START) {
        if (state->tx.size > 0) {
            atomicWriteByte(&state->masterState, STM_I2C_MASTER_TX_ADDR);
            regs->DR = pdev->addr << 1;
        } else {
            atomicWriteByte(&state->masterState, STM_I2C_MASTER_RX_ADDR);
            stmI2cAckEnable(pdev);
            regs->DR = (pdev->addr << 1) | 0x01;
        }
    }
}

static void stmI2cMasterSentAddr(struct StmI2cDev *pdev)
{
    struct I2cStmState *state = &pdev->state;
    struct StmI2c *regs = pdev->cfg->regs;
    uint8_t masterState = atomicReadByte(&state->masterState);

    if (masterState == STM_I2C_MASTER_TX_ADDR) {
        stmI2cMasterStartDma(pdev, &pdev->board->dmaTx, state->tx.cbuf,
                state->tx.size, stmI2cMasterDmaTxDone, false, !!state->rx.size);
        regs->SR2; // Clear ADDR
        atomicWriteByte(&state->masterState, STM_I2C_MASTER_TX_DATA);
    } else if (masterState == STM_I2C_MASTER_RX_ADDR) {
        if (state->rx.size == 1) // Generate NACK here for 1 byte transfers
            stmI2cAckDisable(pdev);

        stmI2cMasterStartDma(pdev, &pdev->board->dmaRx, state->rx.buf,
                state->rx.size, stmI2cMasterDmaRxDone, true,
                state->rx.size > 1);
        regs->SR2; // Clear ADDR
        atomicWriteByte(&state->masterState, STM_I2C_MASTER_RX_DATA);
    }
}

static void stmI2cMasterNakRxed(struct StmI2cDev *pdev)
{
    struct I2cStmState *state = &pdev->state;
    struct StmI2c *regs = pdev->cfg->regs;
    uint8_t masterState = atomicReadByte(&state->masterState);
    int err = 0;

    if (masterState == STM_I2C_MASTER_TX_ADDR ||
            masterState == STM_I2C_MASTER_RX_ADDR) {
        err = -ENXIO;
    } else if (masterState == STM_I2C_MASTER_TX_DATA ||
            masterState == STM_I2C_MASTER_RX_DATA) {
        stmI2cMasterDmaCancel(pdev);
        err = -EIO;
    }

    if (err) {
        regs->SR1 &= ~I2C_SR1_AF;
        stmI2cStopEnable(pdev);
        stmI2cMasterTxRxDone(pdev, err);
    }
}

static void stmI2cMasterBusError(struct StmI2cDev *pdev)
{
    struct StmI2c *regs = pdev->cfg->regs;

    stmI2cMasterDmaCancel(pdev);
    regs->SR1 &= ~I2C_SR1_BERR;
    stmI2cMasterTxRxDone(pdev, -EIO);
}

static void stmI2cMasterArbitrationLoss(struct StmI2cDev *pdev)
{
    struct StmI2c *regs = pdev->cfg->regs;

    stmI2cMasterDmaCancel(pdev);
    regs->SR1 &= ~I2C_SR1_ARLO;
    stmI2cMasterTxRxDone(pdev, -EBUSY);
}

static void stmI2cMasterUnexpectedError(struct StmI2cDev *pdev)
{
    struct StmI2c *regs = pdev->cfg->regs;

    osLog(LOG_ERROR, "Unexpected I2C ERR interrupt: SR1 = %04lX, SR2 = %04lX\n",
            regs->SR1, regs->SR2);

    stmI2cMasterDmaCancel(pdev);
    regs->SR1 = 0;
    stmI2cMasterTxRxDone(pdev, -EIO);
}

static void stmI2cIsrEvent(struct StmI2cDev *pdev)
{
    struct StmI2c *regs = pdev->cfg->regs;
    uint16_t sr1 = regs->SR1;

    if (pdev->state.mode == STM_I2C_SLAVE) {
        if (sr1 & I2C_SR1_ADDR) {
            stmI2cSlaveAddrMatched(pdev);
        } else if (sr1 & I2C_SR1_RXNE) {
            stmI2cSlaveRxBufNotEmpty(pdev);
        } else if (sr1 & I2C_SR1_TXE) {
            stmI2cSlaveTxBufEmpty(pdev);
        } else if (sr1 & I2C_SR1_BTF) {
            if (regs->SR2 & I2C_SR2_TRA)
                stmI2cSlaveTxBufEmpty(pdev);
           else
                stmI2cSlaveRxBufNotEmpty(pdev);
        } else if (sr1 & I2C_SR1_STOPF) {
            stmI2cSlaveStopRxed(pdev);
        }
        /* TODO: other flags */
    } else if (pdev->state.mode == STM_I2C_MASTER) {
        if (sr1 & I2C_SR1_SB)
            stmI2cMasterSentStart(pdev);
        else if (sr1 & I2C_SR1_ADDR)
            stmI2cMasterSentAddr(pdev);
    }
}

static void stmI2cIsrError(struct StmI2cDev *pdev)
{
    struct StmI2c *regs = pdev->cfg->regs;
    uint16_t sr1 = regs->SR1;

    if (pdev->state.mode == STM_I2C_SLAVE) {
        if (sr1 & I2C_SR1_AF)
            stmI2cSlaveNakRxed(pdev);
        /* TODO: other flags */
    } else if (pdev->state.mode == STM_I2C_MASTER) {
        if (sr1 & I2C_SR1_AF)
            stmI2cMasterNakRxed(pdev);
        else if (sr1 & I2C_SR1_BERR)
            stmI2cMasterBusError(pdev);
        else if (sr1 & I2C_SR1_ARLO)
            stmI2cMasterArbitrationLoss(pdev);
        else
            stmI2cMasterUnexpectedError(pdev);
    }
}

#define DECLARE_IRQ_HANDLERS(_n)                \
    extern void I2C##_n##_EV_IRQHandler();      \
    extern void I2C##_n##_ER_IRQHandler();      \
                                                \
    extern void I2C##_n##_EV_IRQHandler()       \
    {                                           \
        stmI2cIsrEvent(&mStmI2cDevs[_n - 1]);  \
    }                                           \
                                                \
    extern void I2C##_n##_ER_IRQHandler()       \
    {                                           \
        stmI2cIsrError(&mStmI2cDevs[_n - 1]);  \
    }

DECLARE_IRQ_HANDLERS(1);
DECLARE_IRQ_HANDLERS(2);
DECLARE_IRQ_HANDLERS(3);

static inline struct Gpio* stmI2cGpioInit(const struct StmI2cBoardCfg *board, const struct StmI2cGpioCfg *cfg)
{
    struct Gpio* gpio = gpioRequest(cfg->gpioNum);
    gpioConfigAlt(gpio, board->gpioSpeed, board->gpioPull, GPIO_OUT_OPEN_DRAIN,
            cfg->func);

    return gpio;
}

int i2cMasterRequest(uint32_t busId, uint32_t speed)
{
    if (busId >= ARRAY_SIZE(mStmI2cDevs))
        return -EINVAL;

    const struct StmI2cBoardCfg *board = boardStmI2cCfg(busId);
    if (!board)
        return -EINVAL;

    struct StmI2cDev *pdev = &mStmI2cDevs[busId];
    struct I2cStmState *state = &pdev->state;
    const struct StmI2cCfg *cfg = &mStmI2cCfgs[busId];

    if (state->mode == STM_I2C_DISABLED) {
        state->mode = STM_I2C_MASTER;

        pdev->cfg = cfg;
        pdev->board = board;
        pdev->next = 2;
        pdev->last = 1;
        atomicBitsetInit(mXfersValid, I2C_MAX_QUEUE_DEPTH);

        pdev->scl = stmI2cGpioInit(board, &board->gpioScl);
        pdev->sda = stmI2cGpioInit(board, &board->gpioSda);

        pwrUnitClock(PERIPH_BUS_APB1, cfg->clock, true);

        stmI2cDisable(pdev);

        pwrUnitReset(PERIPH_BUS_APB1, cfg->clock, true);
        pwrUnitReset(PERIPH_BUS_APB1, cfg->clock, false);

        stmI2cIrqEnable(pdev, I2C_CR2_ITEVTEN | I2C_CR2_ITERREN);
        stmI2cSpeedSet(pdev, speed);
        atomicWriteByte(&state->masterState, STM_I2C_MASTER_IDLE);

        NVIC_EnableIRQ(cfg->irqEr);
        NVIC_EnableIRQ(cfg->irqEv);

        stmI2cEnable(pdev);
        return 0;
    } else {
        return -EBUSY;
    }
}

int i2cMasterRelease(uint32_t busId)
{
    if (busId >= ARRAY_SIZE(mStmI2cDevs))
        return -EINVAL;

    struct StmI2cDev *pdev = &mStmI2cDevs[busId];
    struct I2cStmState *state = &pdev->state;
    const struct StmI2cCfg *cfg = pdev->cfg;

    if (state->mode == STM_I2C_MASTER) {
        if (atomicReadByte(&state->masterState) == STM_I2C_MASTER_IDLE) {
            state->mode = STM_I2C_DISABLED;
            stmI2cIrqEnable(pdev, I2C_CR2_ITERREN | I2C_CR2_ITEVTEN);
            stmI2cDisable(pdev);
            pwrUnitClock(PERIPH_BUS_APB1, cfg->clock, false);

            gpioRelease(pdev->scl);
            gpioRelease(pdev->sda);

            return 0;
        } else {
            return -EBUSY;
        }
    } else {
        return -EINVAL;
    }
}


int i2cMasterTxRx(uint32_t busId, uint32_t addr,
        const void *txBuf, size_t txSize, void *rxBuf, size_t rxSize,
        I2cCallbackF callback, void *cookie)
{
    uint32_t id;

    if (busId >= ARRAY_SIZE(mStmI2cDevs))
        return -EINVAL;
    else if (addr & 0x80)
        return -ENXIO;

    struct StmI2cDev *pdev = &mStmI2cDevs[busId];
    struct I2cStmState *state = &pdev->state;

    if (state->mode != STM_I2C_MASTER)
        return -EINVAL;

    struct StmI2cXfer *xfer = stmI2cGetXfer();

    if (xfer) {
        xfer->busId = busId;
        xfer->addr = addr;
        xfer->txBuf = txBuf;
        xfer->txSize = txSize;
        xfer->rxBuf = rxBuf;
        xfer->rxSize = rxSize;
        xfer->callback = callback;
        xfer->cookie = cookie;
        xfer->tid = osGetCurrentTid();

        do {
            id = atomicAdd32bits(&pdev->last, 1);
        } while (!id);

        // after this point the transfer can be picked up by the transfer
        // complete interrupt
        atomicWrite32bits(&xfer->id, id);

        // only initiate transfer here if we are in IDLE. Otherwise the transfer
        // completion interrupt will start the next transfer (not necessarily
        // this one)
        if (atomicCmpXchgByte((uint8_t *)&state->masterState,
                STM_I2C_MASTER_IDLE, STM_I2C_MASTER_START)) {
            // it is possible for this transfer to already be complete by the
            // time we get here. if so, transfer->id will have been set to 0.
            if (atomicCmpXchg32bits(&xfer->id, id, 0)) {
                pdev->addr = xfer->addr;
                state->tx.cbuf = xfer->txBuf;
                state->tx.offset = 0;
                state->tx.size = xfer->txSize;
                state->tx.callback = xfer->callback;
                state->tx.cookie = xfer->cookie;
                state->rx.buf = xfer->rxBuf;
                state->rx.offset = 0;
                state->rx.size = xfer->rxSize;
                state->rx.callback = NULL;
                state->rx.cookie = NULL;
                state->tid = xfer->tid;
                if (pdev->board->sleepDev >= 0)
                    platRequestDevInSleepMode(pdev->board->sleepDev, 12);
                stmI2cPutXfer(xfer);
                stmI2cStartEnable(pdev);
            }
        }
        return 0;
    } else {
        return -EBUSY;
    }
}

int i2cSlaveRequest(uint32_t busId, uint32_t addr)
{
    if (busId >= ARRAY_SIZE(mStmI2cDevs))
        return -EINVAL;

    const struct StmI2cBoardCfg *board = boardStmI2cCfg(busId);
    if (!board)
        return -EINVAL;

    struct StmI2cDev *pdev = &mStmI2cDevs[busId];
    const struct StmI2cCfg *cfg = &mStmI2cCfgs[busId];

    if (pdev->state.mode == STM_I2C_DISABLED) {
        pdev->state.mode = STM_I2C_SLAVE;

        pdev->addr = addr;
        pdev->cfg = cfg;
        pdev->board = board;

        pdev->scl = stmI2cGpioInit(board, &board->gpioScl);
        pdev->sda = stmI2cGpioInit(board, &board->gpioSda);

        return 0;
    } else {
        return -EBUSY;
    }
}

int i2cSlaveRelease(uint32_t busId)
{
    if (busId >= ARRAY_SIZE(mStmI2cDevs))
        return -EINVAL;

    struct StmI2cDev *pdev = &mStmI2cDevs[busId];
    const struct StmI2cCfg *cfg = pdev->cfg;

    if (pdev->state.mode == STM_I2C_SLAVE) {
        pdev->state.mode = STM_I2C_DISABLED;
        stmI2cIrqDisable(pdev, I2C_CR2_ITERREN | I2C_CR2_ITEVTEN);
        stmI2cAckDisable(pdev);
        stmI2cDisable(pdev);
        pwrUnitClock(PERIPH_BUS_APB1, cfg->clock, false);

        gpioRelease(pdev->scl);
        gpioRelease(pdev->sda);

        return 0;
    } else {
        return -EBUSY;
    }
}

void i2cSlaveEnableRx(uint32_t busId, void *rxBuf, size_t rxSize,
        I2cCallbackF callback, void *cookie)
{
    struct StmI2cDev *pdev = &mStmI2cDevs[busId];
    const struct StmI2cCfg *cfg = pdev->cfg;
    struct I2cStmState *state = &pdev->state;

    if (pdev->state.mode == STM_I2C_SLAVE) {
        state->rx.buf = rxBuf;
        state->rx.offset = 0;
        state->rx.size = rxSize;
        state->rx.callback = callback;
        state->rx.cookie = cookie;
        state->slaveState = STM_I2C_SLAVE_RX_ARMED;
        state->tid = osGetCurrentTid();

        pwrUnitClock(PERIPH_BUS_APB1, cfg->clock, true);
        pwrUnitReset(PERIPH_BUS_APB1, cfg->clock, true);
        pwrUnitReset(PERIPH_BUS_APB1, cfg->clock, false);

        NVIC_EnableIRQ(cfg->irqEr);
        NVIC_EnableIRQ(cfg->irqEv);

        stmI2cEnable(pdev);
        cfg->regs->OAR1 = I2C_OAR1_ADD7(pdev->addr);
        stmI2cIrqEnable(pdev, I2C_CR2_ITERREN | I2C_CR2_ITEVTEN);
        stmI2cAckEnable(pdev);
    }
}

static int i2cSlaveTx(uint32_t busId, const void *txBuf, uint8_t byte,
        size_t txSize, I2cCallbackF callback, void *cookie)
{
    struct StmI2cDev *pdev = &mStmI2cDevs[busId];
    struct I2cStmState *state = &pdev->state;

    if (pdev->state.mode == STM_I2C_SLAVE) {
        if (state->slaveState == STM_I2C_SLAVE_RX)
            return -EBUSY;

        if (txBuf) {
            state->tx.cbuf = txBuf;
            state->tx.preamble = false;
        } else {
            state->tx.byte = byte;
            state->tx.preamble = true;
        }
        state->tx.offset = 0;
        state->tx.size = txSize;
        state->tx.callback = callback;
        state->tx.cookie = cookie;

        if (state->slaveState == STM_I2C_SLAVE_TX_ARMED) {
            state->slaveState = STM_I2C_SLAVE_TX;
            stmI2cSlaveTxNextByte(pdev);
            stmI2cIrqEnable(pdev, I2C_CR2_ITBUFEN);
        } else {
            state->slaveState = STM_I2C_SLAVE_TX;
        }

        return 0;
    } else {
        return -EBUSY;
    }
}

int i2cSlaveTxPreamble(uint32_t busId, uint8_t byte, I2cCallbackF callback,
        void *cookie)
{
    return i2cSlaveTx(busId, NULL, byte, 0, callback, cookie);
}

int i2cSlaveTxPacket(uint32_t busId, const void *txBuf, size_t txSize,
        I2cCallbackF callback, void *cookie)
{
    return i2cSlaveTx(busId, txBuf, 0, txSize, callback, cookie);
}
