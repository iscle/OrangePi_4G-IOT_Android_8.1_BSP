#include <alloca.h>
#include <stdbool.h>
#include <string.h>

#include <variant/variant.h>

#include <plat/pwr.h>
#include <plat/gpio.h>
#include <plat/cmsis.h>

#include <bl.h>
#include <gpio.h>

struct StmUdid
{
    volatile uint32_t U_ID[3];
};

struct StmSpi {
    volatile uint32_t CR1;
    volatile uint32_t CR2;
    volatile uint32_t SR;
    volatile uint32_t DR;
    volatile uint32_t CRCPR;
    volatile uint32_t RXCRCR;
    volatile uint32_t TXCRCR;
    volatile uint32_t I2SCFGR;
    volatile uint32_t I2SPR;
};

struct StmGpio {
    volatile uint32_t MODER;
    volatile uint32_t OTYPER;
    volatile uint32_t OSPEEDR;
    volatile uint32_t PUPDR;
    volatile uint32_t IDR;
    volatile uint32_t ODR;
    volatile uint32_t BSRR;
    volatile uint32_t LCKR;
    volatile uint32_t AFR[2];
};

struct StmFlash
{
    volatile uint32_t ACR;
    volatile uint32_t KEYR;
    volatile uint32_t OPTKEYR;
    volatile uint32_t SR;
    volatile uint32_t CR;
    volatile uint32_t OPTCR;
};

struct StmCrc
{
    volatile uint32_t DR;
    volatile uint32_t IDR;
    volatile uint32_t CR;
};

struct StmRcc {
    volatile uint32_t CR;
    volatile uint32_t PLLCFGR;
    volatile uint32_t CFGR;
    volatile uint32_t CIR;
    volatile uint32_t AHB1RSTR;
    volatile uint32_t AHB2RSTR;
    volatile uint32_t AHB3RSTR;
    uint8_t unused0[4];
    volatile uint32_t APB1RSTR;
    volatile uint32_t APB2RSTR;
    uint8_t unused1[8];
    volatile uint32_t AHB1ENR;
    volatile uint32_t AHB2ENR;
    volatile uint32_t AHB3ENR;
    uint8_t unused2[4];
    volatile uint32_t APB1ENR;
    volatile uint32_t APB2ENR;
    uint8_t unused3[8];
    volatile uint32_t AHB1LPENR;
    volatile uint32_t AHB2LPENR;
    volatile uint32_t AHB3LPENR;
    uint8_t unused4[4];
    volatile uint32_t APB1LPENR;
    volatile uint32_t APB2LPENR;
    uint8_t unused5[8];
    volatile uint32_t BDCR;
    volatile uint32_t CSR;
    uint8_t unused6[8];
    volatile uint32_t SSCGR;
    volatile uint32_t PLLI2SCFGR;
};

typedef void (*FlashEraseF)(volatile uint32_t *, uint32_t, volatile uint32_t *);
typedef void (*FlashWriteF)(volatile uint8_t *, uint8_t, volatile uint32_t *);

static struct StmSpi *SPI;
static struct StmRcc *RCC;
static struct Gpio *wakeupGpio;
static uint32_t mOldApb2State;
static uint32_t mOldAhb1State;

#define FLASH_ACR_LAT(x)    ((x) & FLASH_ACR_LAT_MASK)
#define FLASH_ACR_LAT_MASK  0x0F
#define FLASH_ACR_PRFTEN    0x00000100
#define FLASH_ACR_ICEN      0x00000200
#define FLASH_ACR_DCEN      0x00000400
#define FLASH_ACR_ICRST     0x00000800
#define FLASH_ACR_DCRST     0x00001000

#define FLASH_SR_EOP        0x00000001
#define FLASH_SR_OPERR      0x00000002
#define FLASH_SR_WRPERR     0x00000010
#define FLASH_SR_PGAERR     0x00000020
#define FLASH_SR_PGPERR     0x00000040
#define FLASH_SR_PGSERR     0x00000080
#define FLASH_SR_RDERR      0x00000100
#define FLASH_SR_BSY        0x00010000

#define FLASH_CR_PG         0x00000001
#define FLASH_CR_SER        0x00000002
#define FLASH_CR_MER        0x00000004
#define FLASH_CR_SNB(x)     (((x) << FLASH_CR_SNB_SHIFT) & FLASH_CR_SNB_MASK)
#define FLASH_CR_SNB_MASK   0x00000078
#define FLASH_CR_SNB_SHIFT  3
#define FLASH_CR_PSIZE(x)   (((x) << FLASH_CR_PSIZE_SHIFT) & FLASH_CR_PSIZE_MASK)
#define FLASH_CR_PSIZE_MASK 0x00000300
#define FLASH_CR_PSIZE_SHIFT 8
#define FLASH_CR_PSIZE_8    0x0
#define FLASH_CR_PSIZE_16   0x1
#define FLASH_CR_PSIZE_32   0x2
#define FLASH_CR_PSIZE_64   0x3
#define FLASH_CR_STRT       0x00010000
#define FLASH_CR_EOPIE      0x01000000
#define FLASH_CR_ERRIE      0x02000000
#define FLASH_CR_LOCK       0x80000000

//stm defines
#define BL_MAX_FLASH_CODE   1024

/*
 * Return the address of the erase code and the length of the code
 *
 * This code needs to run out of ram and not flash since accessing flash
 * while erasing is undefined (best case the processor stalls, worst case
 * it starts executing garbage)
 *
 * This function is used to get a pointer to the actual code that does the
 * erase and polls for completion (so we can copy it to ram) as well as the
 * length of the code (so we know how much space to allocate for it)
 *
 * void FlashEraseF(volatile uint32_t *addr, uint32_t value, volatile uint32_t *status)
 * {
 *     *addr = value;
 *     while (*status & FLASH_SR_BSY) ;
 * }
 */
static void __attribute__((naked)) blGetFlashEraseCode(uint16_t **addr, uint32_t *size)
{
    asm volatile (
        "  push {lr}          \n"
        "  bl   9f            \n"
        "  str  r1, [r0, #0]  \n" // *addr = value
        "1:                   \n"
        "  ldr  r3, [r2, #0]  \n" // r3 = *status
        "  lsls r3, #15       \n" // r3 <<= 15
        "  bmi  1b            \n" // if (r3 < 0) goto 1
        "  bx   lr            \n" // return
        "9:                   \n"
        "  bic  lr, #0x1      \n"
        "  adr  r3, 9b        \n"
        "  sub  r3, lr        \n"
        "  str  lr, [r0]      \n"
        "  str  r3, [r1]      \n"
        "  pop {pc}           \n"
    );
}

static void _blEraseSectors(uint32_t sector_cnt, uint8_t *erase_mask)
{
    struct StmFlash *flash = (struct StmFlash *)FLASH_BASE;
    uint16_t *code_src, *code;
    uint32_t i, code_length;
    FlashEraseF func;

    blGetFlashEraseCode(&code_src, &code_length);

    if (code_length < BL_MAX_FLASH_CODE) {
        code = (uint16_t *)(((uint32_t)alloca(code_length + 1) + 1) & ~0x1);
        func = (FlashEraseF)((uint8_t *)code+1);

        for (i = 0; i < code_length / sizeof(uint16_t); i++)
            code[i] = code_src[i];

        for (i = 0; i < sector_cnt; i++) {
            if (erase_mask[i]) {
                flash->CR = (flash->CR & ~(FLASH_CR_SNB_MASK)) |
                    FLASH_CR_SNB(i) | FLASH_CR_SER;
                func(&flash->CR, flash->CR | FLASH_CR_STRT, &flash->SR);
                flash->CR &= ~(FLASH_CR_SNB_MASK | FLASH_CR_SER);
            }
        }
    }
}

bool blEraseSectors(uint32_t sector_cnt, uint8_t *erase_mask, uint32_t key1, uint32_t key2)
{
    struct StmFlash *flash = (struct StmFlash *)FLASH_BASE;
    uint32_t acr_cache, cr_cache;
    // disable interrupts
    // otherwise an interrupt during flash write/erase will stall the processor
    // until the write/erase completes
    uint32_t int_state = blDisableInts();

    // wait for flash to not be busy (should never be set at this point)
    while (flash->SR & FLASH_SR_BSY);

    cr_cache = flash->CR;

    if (flash->CR & FLASH_CR_LOCK) {
        // unlock flash
        flash->KEYR = key1;
        flash->KEYR = key2;
    }

    if (!(flash->CR & FLASH_CR_LOCK)) {
        flash->CR = FLASH_CR_PSIZE(FLASH_CR_PSIZE_8);
        acr_cache = flash->ACR;

        // disable and flush data and instruction caches
        flash->ACR &= ~(FLASH_ACR_DCEN | FLASH_ACR_ICEN);
        flash->ACR |= (FLASH_ACR_DCRST | FLASH_ACR_ICRST);

        _blEraseSectors(sector_cnt, erase_mask);

        flash->ACR = acr_cache;
        flash->CR = cr_cache;

        // restore interrupts
        blRestoreInts(int_state);
        return true;
    }
    return false;
}

/*
 * Return the address of the write code and the length of the code
 *
 * This code needs to run out of ram and not flash since accessing flash
 * while writing to flash is undefined (best case the processor stalls, worst
 * case it starts executing garbage)
 *
 * This function is used to get a pointer to the actual code that does the
 * write and polls for completion (so we can copy it to ram) as well as the
 * length of the code (so we know how much space to allocate for it)
 *
 * void FlashWriteF(volatile uint8_t *addr, uint8_t value, volatile uint32_t *status)
 * {
 *     *addr = value;
 *     while (*status & FLASH_SR_BSY) ;
 * }
 */
static void __attribute__((naked)) blGetFlashWriteCode(uint16_t **addr, uint32_t *size)
{
    asm volatile (
        "  push {lr}          \n"
        "  bl   9f            \n"
        "  strb r1, [r0, #0]  \n" // *addr = value
        "1:                   \n"
        "  ldr  r3, [r2, #0]  \n" // r3 = *status
        "  lsls r3, #15       \n" // r3 <<= 15
        "  bmi  1b            \n" // if (r3 < 0) goto 1
        "  bx   lr            \n" // return
        "9:                   \n"
        "  bic  lr, #0x1      \n"
        "  adr  r3, 9b        \n"
        "  sub  r3, lr        \n"
        "  str  lr, [r0]      \n"
        "  str  r3, [r1]      \n"
        "  pop {pc}           \n"
    );
}

static void blWriteBytes(uint8_t *dst, const uint8_t *src, uint32_t length)
{
    struct StmFlash *flash = (struct StmFlash *)FLASH_BASE;
    uint16_t *code_src, *code;
    uint32_t i, code_length;
    FlashWriteF func;

    blGetFlashWriteCode(&code_src, &code_length);

    if (code_length < BL_MAX_FLASH_CODE) {
        code = (uint16_t *)(((uint32_t)alloca(code_length+1) + 1) & ~0x1);
        func = (FlashWriteF)((uint8_t *)code+1);

        for (i = 0; i < code_length / sizeof(uint16_t); i++)
            code[i] = code_src[i];

        flash->CR |= FLASH_CR_PG;

        for (i = 0; i < length; i++) {
            if (dst[i] != src[i])
                func(&dst[i], src[i], &flash->SR);
        }

        flash->CR &= ~FLASH_CR_PG;
    }
}

bool blPlatProgramFlash(uint8_t *dst, const uint8_t *src, uint32_t length, uint32_t key1, uint32_t key2)
{
    struct StmFlash *flash = (struct StmFlash *)FLASH_BASE;
    uint32_t acr_cache, cr_cache;
    // disable interrupts
    // otherwise an interrupt during flash write will stall the processor
    // until the write completes
    uint32_t int_state = blDisableInts();

    // wait for flash to not be busy (should never be set at this point)
    while (flash->SR & FLASH_SR_BSY);

    cr_cache = flash->CR;

    if (flash->CR & FLASH_CR_LOCK) {
        // unlock flash
        flash->KEYR = key1;
        flash->KEYR = key2;
    }

    if (flash->CR & FLASH_CR_LOCK) {
        // unlock failed, restore interrupts
        blRestoreInts(int_state);

        return false;
    }

    flash->CR = FLASH_CR_PSIZE(FLASH_CR_PSIZE_8);

    acr_cache = flash->ACR;

    // disable and flush data and instruction caches
    flash->ACR &= ~(FLASH_ACR_DCEN | FLASH_ACR_ICEN);
    flash->ACR |= (FLASH_ACR_DCRST | FLASH_ACR_ICRST);

    blWriteBytes(dst, src, length);

    flash->ACR = acr_cache;
    flash->CR = cr_cache;

    blRestoreInts(int_state);
    return true;
}

uint32_t blDisableInts(void)
{
    uint32_t state;

    asm volatile (
        "mrs %0, PRIMASK    \n"
        "cpsid i            \n"
        :"=r"(state)
    );

    return state;
}

void blRestoreInts(uint32_t state)
{
    asm volatile(
        "msr PRIMASK, %0   \n"
        ::"r"((uint32_t)state)
    );
}

void blReboot(void)
{
    SCB->AIRCR = 0x05FA0004;
    //we never get here
    while(1);
}

void blResetRxData()
{
    (void)SPI->DR;
    while (!(SPI->SR & 1));
    (void)SPI->DR;
}

uint8_t blSpiTxRxByte(uint32_t val)
{
    while (!(SPI->SR & 2));
    SPI->DR = val;
    while (!(SPI->SR & 1));
    return SPI->DR;
}

uint32_t blGetSnum(uint32_t *snum, uint32_t length)
{
    struct StmUdid *reg = (struct StmUdid *)UDID_BASE;
    uint32_t i;

    if (length > 3)
        length = 3;

    for (i = 0; i < length; i++)
        snum[i] = reg->U_ID[i];

    return (length << 2);
}

void blSetup()
{
    SPI = (struct StmSpi*)SPI1_BASE;
    RCC = (struct StmRcc*)RCC_BASE;
    struct Gpio *gpio;
    int i;

    //SPI1 & GPIOA on
    mOldApb2State = RCC->APB2ENR;
    mOldAhb1State = RCC->AHB1ENR;
    RCC->APB2ENR |= PERIPH_APB2_SPI1;
    RCC->AHB1ENR |= PERIPH_AHB1_GPIOA;

    //reset units
    RCC->APB2RSTR |= PERIPH_APB2_SPI1;
    RCC->AHB1RSTR |= PERIPH_AHB1_GPIOA;
    RCC->APB2RSTR &=~ PERIPH_APB2_SPI1;
    RCC->AHB1RSTR &=~ PERIPH_AHB1_GPIOA;

    //configure GPIOA for SPI A4..A7 for AF_SPI1 use (function 5), int pin as not func, high speed, no pullups, not open drain, proper directions
    for (i=4; i<=7; i++) {
        gpio = gpioRequest(GPIO_PA(i));
        gpioConfigAlt(gpio, GPIO_SPEED_HIGH, GPIO_PULL_NONE, GPIO_OUT_PUSH_PULL, GPIO_AF_SPI1);
        gpioRelease(gpio);
    }

    wakeupGpio = gpioRequest(SH_INT_WAKEUP);
    gpioConfigInput(wakeupGpio, GPIO_SPEED_HIGH, GPIO_PULL_NONE);
}

void blCleanup()
{
    gpioRelease(wakeupGpio);
    //reset units & return APB2 & AHB1 to initial state
    RCC->APB2RSTR |= PERIPH_APB2_SPI1;
    RCC->AHB1RSTR |= PERIPH_AHB1_GPIOA;
    RCC->APB2RSTR &=~ PERIPH_APB2_SPI1;
    RCC->AHB1RSTR &=~ PERIPH_AHB1_GPIOA;
    RCC->APB2ENR = mOldApb2State;
    RCC->AHB1ENR = mOldAhb1State;
}

bool blHostActive()
{
    return !gpioGet(wakeupGpio);
}

void blConfigIo()
{
    //config SPI
    SPI->CR1 = 0x00000040; //spi is on, configured same as bootloader would
    SPI->CR2 = 0x00000000; //spi is on, configured same as bootloader would
}

bool blSyncWait(uint32_t syncCode)
{
    uint32_t nRetries;
    //wait for sync
    for (nRetries = 10000; nRetries; nRetries--) {
        if (SPI->SR & 1) {
            if (SPI->DR == syncCode)
                break;
            (void)SPI->SR; //re-read to clear overlfow condition (if any)
        }
    }
    return nRetries > 0;
}

void __attribute__((noreturn)) __blEntry(void);
void __attribute__((noreturn)) __blEntry(void)
{
    extern char __code_start[], __bss_end[], __bss_start[], __data_end[], __data_start[], __data_data[];
    uint32_t appBase = ((uint32_t)&__code_start) & ~1;

    //make sure we're the vector table and no ints happen (BL does not use them)
    blDisableInts();
    SCB->VTOR = (uint32_t)&BL;

    //init things a little for the higher levels
    memset(__bss_start, 0, __bss_end - __bss_start);
    memcpy(__data_start, __data_data, __data_end - __data_start);

    blMain(appBase);

    //call OS with ints off
    blDisableInts();
    SCB->VTOR = appBase;
    asm volatile(
        "LDR SP, [%0, #0]    \n"
        "LDR PC, [%0, #4]    \n"
        :
        :"r"(appBase)
        :"memory", "cc"
    );

    //we should never return here
    while(1);
}

static void blSpuriousIntHandler(void)
{
    //BAD!
    blReboot();
}

extern uint8_t __stack_top[];
uint64_t __attribute__ ((section (".stack"))) _STACK[BL_STACK_SIZE / sizeof(uint64_t)];

const struct BlVecTable __attribute__((section(".blvec"))) __BL_VEC =
{
    .blStackTop             = (uint32_t)&__stack_top,
    .blEntry                = &__blEntry,
    .blNmiHandler           = &blSpuriousIntHandler,
    .blHardFaultHandler     = &blSpuriousIntHandler,
    .blMmuFaultHandler      = &blSpuriousIntHandler,
    .blBusFaultHandler      = &blSpuriousIntHandler,
    .blUsageFaultHandler    = &blSpuriousIntHandler,
};
