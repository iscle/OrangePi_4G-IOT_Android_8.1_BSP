#include "viddec_pm_parse.h"
#include "viddec_fw_debug.h"

#define FIRST_STARTCODE_BYTE        0x00
#define SECOND_STARTCODE_BYTE       0x00
#define THIRD_STARTCODE_BYTE        0x01

/* BIG ENDIAN: Must be the second and fourth byte of the bytestream for this to work */
/* LITTLE ENDIAN: Must be the second and fourth byte of the bytestream for this to work */
/* these are little-endian defines */
#define SC_BYTE_MASK0               0x00ff0000  /* little-endian */
#define SC_BYTE_MASK1               0x000000ff  /* little-endian */

/* Parse for Sc code of pattern 0x00 0x00 0xXX in the current buffer. Returns either sc found or success.
   The conext is updated with current phase and sc_code position in the buffer.
*/
uint32_t viddec_parse_sc(void *in, void *pcxt, void *sc_state)
{
    uint8_t *ptr;
    uint32_t size;
    uint32_t data_left=0, phase = 0, ret = 0;
    viddec_sc_parse_cubby_cxt_t *cxt;
    /* What is phase?: phase is a value between [0-4], we keep track of consecutive '0's with this.
       Any time a '0' is found its incremented by 1(uptp 2) and reset to '0' if a zero not found.
       if 0xXX code is found and current phase is 2, its changed to 3 which means we found the pattern
       we are looking for. Its incremented to 4 once we see a byte after this pattern */
    cxt = ( viddec_sc_parse_cubby_cxt_t *)in;
    size = 0;
    data_left = cxt->size;
    ptr = cxt->buf;
    phase = cxt->phase;
    cxt->sc_end_pos = -1;
    pcxt=pcxt;
 
    /* parse until there is more data and start code not found */
    while((data_left > 0) &&(phase < 3))
    {
        /* Check if we are byte aligned & phase=0, if thats the case we can check
           work at a time instead of byte*/
        if(((((uint32_t)ptr) & 0x3) == 0) && (phase == 0))
        {
            while(data_left > 3)
            {
                uint32_t data;
                char mask1 = 0, mask2=0;
                
                data = *((uint32_t *)ptr);
#ifndef MFDBIGENDIAN
                data = SWAP_WORD(data);
#endif
                mask1 = (FIRST_STARTCODE_BYTE != (data & SC_BYTE_MASK0));
                mask2 = (FIRST_STARTCODE_BYTE != (data & SC_BYTE_MASK1));
                /* If second byte and fourth byte are not zero's then we cannot have a start code here as we need
                   two consecutive zero bytes for a start code pattern */
                if(mask1 && mask2)
                {/* Success so skip 4 bytes and start over */
                    ptr+=4;size+=4;data_left-=4;
                    continue;
                }
                else
                {
                    break;
                }
            }
        }
 
        /* At this point either data is not on a word boundary or phase > 0 or On a word boundary but we detected
           two zero bytes in the word so we look one byte at a time*/
        if(data_left > 0)
        {
            if(*ptr == FIRST_STARTCODE_BYTE)
            {/* Phase can be 3 only if third start code byte is found */
                phase++;
                ptr++;size++;data_left--;
                if(phase > 2)
                {
                    phase = 2;

                    if ( (((uint32_t)ptr) & 0x3) == 0 )
                    {
                       while( data_left > 3 )
                       {
                           if(*((uint32_t *)ptr) != 0)
                           {
                               break;
                           }
                           ptr+=4;size+=4;data_left-=4;
                       }
                    }
                }
            }
            else
            {
                if((*ptr == THIRD_STARTCODE_BYTE) && (phase == 2))
                {/* Match for start code so update context with byte position */
                    phase = 3;
                    cxt->sc_end_pos = size;
                }
                else
                {
                    phase = 0;
                }
                ptr++;size++;data_left--;
            }
        }
    }
    if((data_left > 0) && (phase == 3))
    {
        viddec_sc_prefix_state_t *state = (viddec_sc_prefix_state_t *)sc_state;
        cxt->sc_end_pos++;
        state->next_sc = cxt->buf[cxt->sc_end_pos];
        state->second_scprfx_length = 3;
        phase++;
        ret = 1;
    }
    cxt->phase = phase;
    /* Return SC found only if phase is 4, else always success */
    return ret;
}
