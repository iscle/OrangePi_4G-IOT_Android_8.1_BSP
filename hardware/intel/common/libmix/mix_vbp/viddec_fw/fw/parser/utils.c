#include "fw_pvt.h"
#include "viddec_fw_parser_ipclib_config.h"

extern uint32_t timer;

/*------------------------------------------------------------------------------
 * Function:  memcpy
 * This is a memory-copy function.
 *------------------------------------------------------------------------------
 */
/* NOTE: we are inventing memcpy since we don't want to include string libs as part of FW Due to size limitations*/
void *memcpy(void *dest, const void *src, uint32_t n)
{
    uint8_t *ptr8_frm, *ptr8_to;
    uint32_t *ptr32_frm, *ptr32_to;
    uint32_t bytes_left=n,trail = 0;
    uint32_t align=0;

    ptr8_frm = (uint8_t *)src;
    ptr8_to = (uint8_t *)dest;

    trail = ((uint32_t)ptr8_frm) & 0x3;
    if((trail == (((uint32_t)ptr8_to) & 0x3)) && (n > 4))
    {
        /* check to see what's the offset bytes to go to a word alignment */
        bytes_left -= trail;
        while(align > 0){
            *ptr8_to ++ = *ptr8_frm ++;
            trail--;
        }
        /* check to see if rest of bytes is a multiple of 4. */
        trail = bytes_left & 0x3;
        bytes_left = (bytes_left >> 2) << 2;
        ptr32_to = (uint32_t *)ptr8_to;
        ptr32_frm = (uint32_t *)ptr8_frm;
        /* copy word by word */
        while(bytes_left > 0){
            *ptr32_to ++ = *ptr32_frm ++;
            bytes_left -= 4;
        }
        /* If there are any trailing bytes do a byte copy */
        ptr8_to = (uint8_t *)ptr32_to;
        ptr8_frm = (uint8_t *)ptr32_frm;
        while(trail > 0){
            *ptr8_to ++ = *ptr8_frm ++;
            trail--;
        }
    }
    else
    {/* case when src and dest addr are not on same alignment.
        Just do a byte copy */
        while(bytes_left > 0){
            *ptr8_to ++ = *ptr8_frm ++;
            bytes_left -= 1;
        }
    }
    return dest;
}

/*------------------------------------------------------------------------------
 * Function:  memset
 * This is a function to copy specificed value into memory array.
 *------------------------------------------------------------------------------
 */
/* NOTE: we are inventing memset since we don't want to include string libs as part of FW Due to size limitations*/
void *memset(void *s, int32_t c, uint32_t n)
{
    uint8_t *ptr8 = (uint8_t *)s;
    uint32_t *ptr32, data;
    uint32_t mask = 0, bytes_left = n;

    mask = c & 0xFF;
    mask |= (mask << 8);
    mask |= (mask << 16);
    if(n >= 4)
    {
        uint32_t trail=0;
        trail = 4 - (((uint32_t)ptr8) & 0x3);
        if(trail < 4)
        {
            ptr32 = (uint32_t *)(((uint32_t)ptr8) & ~0x3);
            data = (*ptr32 >> (8*trail)) << (8*trail);
            data |= (mask >> (32 - (8*trail)));
            *ptr32 = data;
            bytes_left -= trail;
            ptr8 += trail;
        }
        ptr32 = (uint32_t *)((uint32_t)ptr8);
        while(bytes_left >= 4)
        {
            *ptr32 = mask;
            ptr32++;
            bytes_left -=4;
        }
        if(bytes_left > 0)
        {
            data = (*ptr32 << (8*bytes_left)) >> (8*bytes_left);
            data |= (mask << (32 - (8*bytes_left)));
            *ptr32=data;
        }
    }
    
    return s;
}

/*------------------------------------------------------------------------------
 * Function:  cp_using_dma
 * This is a function to copy data from local memory to/from system memory.
 * Params:
 *         [in] ddr_addr  : Word aligned ddr address.
 *         [in] local_addr: Word aligned local address.
 *         [in] size      : No of bytes to transfer.
 *         [in] to_ddr    : Direction of copy, if true copy to ddr else copy to local memory.
 *         [in] swap      : Enable or disable byte swap(endian).
 *         [out] return   : Actual number of bytes copied, which can be more than what was requested
 *                          since we can only copy words at a time.                   
 * Limitations: DMA can transfer Words only, Local addr & DDR addr should be word aligned.
 *------------------------------------------------------------------------------
 */
uint32_t cp_using_dma(uint32_t ddr_addr, uint32_t local_addr, uint32_t size, char to_ddr, char swap)
{
    uint32_t val=0, wrote = size;

    while((reg_read(DMA_CONTROL_STATUS) & DMA_CTRL_STATUS_BUSY) != 0)
    {
        /* wait if DMA is busy with a transcation Error condition??*/
    }

    reg_write(DMA_SYSTEM_ADDRESS, (ddr_addr & ~3) & ~GV_DDR_MEM_MASK);
    reg_write(DMA_LOCAL_ADDRESS, (local_addr & 0xfffc));
    //wrote += (ddr_addr & 0x3);
    wrote = (wrote+3)>>2;/* make number of bytes multiple of 4 */
    val=(wrote & 0xffff) << 2;
    reg_write(DMA_CONTROL_STATUS, DMA_CTRL_STATUS_DONE);
    val |= DMA_CTRL_STATUS_START;
	/* If size > 64 use 128 byte burst speed */
    if(wrote > 64)
        val |= (1<<18);
    if(swap) /* Endian swap if needed */
        val |= DMA_CTRL_STATUS_SWAP;
    if(to_ddr)
        val = val | DMA_CTRL_STATUS_DIRCN;
    reg_write(DMA_CONTROL_STATUS, val);
    while((reg_read(DMA_CONTROL_STATUS) & DMA_CTRL_STATUS_DONE) == 0)
    {
		/* wait till DMA is done */
    }
    reg_write(DMA_CONTROL_STATUS, DMA_CTRL_STATUS_DONE);

    return (wrote << 2);
}

/*------------------------------------------------------------------------------
 * Function:  cp_using_dma
 * This is a function to copy data from local memory to/from system memory.
 * Params:
 *         [in] ddr_addr  : Word aligned ddr address.
 *         [in] local_addr: Word aligned local address.
 *         [in] size      : No of bytes to transfer.
 *         [in] to_ddr    : Direction of copy, if true copy to ddr else copy to local memory.
 *         [in] swap      : Enable or disable byte swap(endian).
 *         [out] return   : Actual number of bytes copied, which can be more than what was requested
 *                          since we can only copy words at a time.                   
 * Limitations: DMA can transfer Words only, Local addr & DDR addr should be word aligned.
 *------------------------------------------------------------------------------
 */
uint32_t cp_using_dma_phys(uint32_t ddr_addr, uint32_t local_addr, uint32_t size, char to_ddr, char swap)
{
    uint32_t val=0, wrote = size;

    while((reg_read(DMA_CONTROL_STATUS) & DMA_CTRL_STATUS_BUSY) != 0)
    {
        /* wait if DMA is busy with a transcation Error condition??*/
    }

    reg_write(DMA_SYSTEM_ADDRESS, (ddr_addr & ~3));
    reg_write(DMA_LOCAL_ADDRESS, (local_addr & 0xfffc));
    //wrote += (ddr_addr & 0x3);
    wrote = (wrote+3)>>2;/* make number of bytes multiple of 4 */
    val=(wrote & 0xffff) << 2;
    reg_write(DMA_CONTROL_STATUS, DMA_CTRL_STATUS_DONE);
    val |= DMA_CTRL_STATUS_START;
	/* If size > 64 use 128 byte burst speed */
    if(wrote > 64)
        val |= (1<<18);
    if(swap) /* Endian swap if needed */
        val |= DMA_CTRL_STATUS_SWAP;
    if(to_ddr)
        val = val | DMA_CTRL_STATUS_DIRCN;
    reg_write(DMA_CONTROL_STATUS, val);
    while((reg_read(DMA_CONTROL_STATUS) & DMA_CTRL_STATUS_DONE) == 0)
    {
		/* wait till DMA is done */
    }
    reg_write(DMA_CONTROL_STATUS, DMA_CTRL_STATUS_DONE);

    return (wrote << 2);
}

void update_ctrl_reg(uint8_t enable, uint32_t mask)
{
    uint32_t read_val = 0;
    read_val = reg_read(CONFIG_CP_CONTROL_REG);
    if(enable)
    {
        read_val = read_val | mask;
    }
    else
    {
        read_val = read_val & ~mask;        
    }
    reg_write(CONFIG_CP_CONTROL_REG, read_val);    
    return;
    
}

extern uint32_t sven_get_timestamp();

uint32_t set_wdog(uint32_t offset)
{
#ifdef B0_TIMER_FIX
    update_ctrl_reg(0, WATCH_DOG_ENABLE);
    reg_write(INT_REG, INT_WDOG_ENABLE);
    reg_write(WATCH_DOG_COUNTER, offset & WATCH_DOG_MASK);
    update_ctrl_reg(1, WATCH_DOG_ENABLE);
    return offset & WATCH_DOG_MASK;
#else
    return sven_get_timestamp();    
#endif    
}

void get_wdog(uint32_t *value)
{
#ifdef B0_TIMER_FIX    
    *value = reg_read(WATCH_DOG_COUNTER) & WATCH_DOG_MASK;
    reg_write(INT_REG, ~INT_WDOG_ENABLE);
    update_ctrl_reg(0, WATCH_DOG_ENABLE);
#else
    *value = sven_get_timestamp();
#endif    
}

uint32_t get_total_ticks(uint32_t start, uint32_t end)
{
    uint32_t value;    
#ifdef B0_TIMER_FIX
    value = (start-end) + (start*timer);
    timer=0;
#else
    value = end-start;/* convert to 1 MHz clocks */
#endif
    return value;
}
