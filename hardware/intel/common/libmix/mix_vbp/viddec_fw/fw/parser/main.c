#include "fw_pvt.h"
#include "viddec_fw_parser_ipclib_config.h"
#include "viddec_fw_common_defs.h"
#include "viddec_fw_parser.h"
#include "viddec_fw_debug.h"

/* This define makes sure that the structure is stored in Local memory.
   This is shared memory between host and FW.*/
volatile dmem_t _dmem __attribute__ ((section (".exchange")));
/* Debug index should be disbaled for Production FW */
uint32_t dump_ptr=0;
uint32_t timer=0;

/* Auto Api definitions */
ismd_api_group viddec_fw_api_array[2];

extern void viddec_fw_parser_register_callbacks(void);

/*------------------------------------------------------------------------------
 * Function:  initialize firmware SVEN TX Output
 *------------------------------------------------------------------------------
 */
int SMDEXPORT viddec_fw_parser_sven_init(struct SVEN_FW_Globals  *sven_fw_globals )
{
    extern int sven_fw_set_globals(struct SVEN_FW_Globals  *fw_globals );
    return(sven_fw_set_globals(sven_fw_globals));
}

/*------------------------------------------------------------------------------
 * Function:  viddec_fw_check_watermark_boundary
 * This function figures out if we crossesd watermark boundary on input data.
 * before represents the ES Queue data when we started and current represents ES Queue data
 * when we are ready to swap.Threshold is the amount of data specified by the driver to trigger an
 * interrupt. 
 * We return true if threshold is between before and current.
 *------------------------------------------------------------------------------
 */
static inline uint32_t viddec_fw_check_watermark_boundary(uint32_t before, uint32_t current, uint32_t threshold)
{
    return ((before >= threshold) && (current < threshold));
}

/*------------------------------------------------------------------------------
 * Function:  viddec_fw_get_total_input_Q_data
 * This function figures out how much data is available in input queue of the FW
 *------------------------------------------------------------------------------
 */
static uint32_t viddec_fw_get_total_input_Q_data(uint32_t indx)
{
    FW_IPC_Handle *fwipc = GET_IPC_HANDLE(_dmem);
    uint32_t ret;
    int32_t pos=0;
    FW_IPC_ReceiveQue   *rcv_q;

    rcv_q = &fwipc->rcv_q[indx];
    /* count the cubby buffer which we already read if present */
    ret = (_dmem.stream_info[indx].buffered_data) ? CONFIG_IPC_MESSAGE_MAX_SIZE:0;
    ret += ipc_mq_read_avail(&rcv_q->mq, (int32_t *)&pos);
    return ret;
}

/*------------------------------------------------------------------------------
 * Function:  mfd_round_robin
 * Params:
 *        [in]  pri: Priority of the stream
 *        [in] indx: stream id number of the last stream that was scheduled. 
 *        [out] qnum: Stream id of priority(pri) which has data.
 * This function is responsible for figuring out which stream needs to be scheduled next.
 * It starts after the last scheduled stream and walks through all streams until it finds
 * a stream which is of required priority, in start state, has space on output and data in
 * input.
 * If no such stream is found qnum is not updated and return value is 0.
 * If a stream is found then qnum is updated with that id and function returns 1.
 *------------------------------------------------------------------------------
 */

uint32_t mfd_round_robin(uint32_t pri, int32_t *qnum, int32_t indx)
{
    FW_IPC_Handle *fwipc = GET_IPC_HANDLE(_dmem);
    int32_t i = CONFIG_IPC_FW_MAX_RX_QUEUES;
    uint32_t ret = 0;
    /* Go through all queues until we find a valid queue of reqd priority */
    while(i>0)
    {
        indx++;
        if(indx >=  CONFIG_IPC_FW_MAX_RX_QUEUES) indx = 0;

        /* We should look only at queues which match priority and 
           in running state */
        if( (_dmem.stream_info[indx].state == 1)
            && (_dmem.stream_info[indx].priority == pri))
        {
            uint32_t inpt_avail=0, output_avail=0, wklds_avail =0 , pos;
            FW_IPC_ReceiveQue   *rcv_q;
            rcv_q = &fwipc->rcv_q[indx];
            inpt_avail = (_dmem.stream_info[indx].buffered_data > 0) || (ipc_mq_read_avail(&rcv_q->mq, (int32_t *)&pos) > 0);
            /* we have to check for two workloads to protect against error cases where we might have to push both current and next workloads */
            output_avail = FwIPC_SpaceAvailForMessage(fwipc, &fwipc->snd_q[indx], CONFIG_IPC_MESSAGE_MAX_SIZE, &pos) >= 2;
            pos = 0;
            /* Need at least current and next to proceed */
            wklds_avail =  (ipc_mq_read_avail(&fwipc->wkld_q[indx].mq, (int32_t *)&pos) >= (CONFIG_IPC_MESSAGE_MAX_SIZE << 1));
            if(inpt_avail && output_avail && wklds_avail)
            {/* Success condition: we have some data on input and enough space on output queue */
                *qnum = indx;
                ret =1;
                break;
            }
        }
        i--;
    }
    return ret;
}
static inline void mfd_setup_emitter(FW_IPC_Handle *fwipc, FW_IPC_ReceiveQue *rcv_q, mfd_pk_strm_cxt *cxt)
{
    int32_t ret1=0,ret=0;
    /* We don't check return values for the peek as round robin guarantee's that we have required free workloads */
    ret = FwIPC_PeekReadMessage(fwipc, rcv_q, (char *)&(cxt->wkld1), sizeof(ipc_msg_data), 0);
    ret1 = FwIPC_PeekReadMessage(fwipc, rcv_q, (char *)&(cxt->wkld2), sizeof(ipc_msg_data), 1);
    viddec_emit_update(&(cxt->pm.emitter), cxt->wkld1.phys, cxt->wkld2.phys, cxt->wkld1.len, cxt->wkld2.len);
}

static inline void mfd_init_swap_memory(viddec_pm_cxt_t *pm, uint32_t codec_type, uint32_t start_addr, uint32_t clean)
{
    uint32_t *persist_mem;
    persist_mem = (uint32_t *)(start_addr | GV_DDR_MEM_MASK);
    viddec_pm_init_context(pm,codec_type, persist_mem, clean);
    pm->sc_prefix_info.first_sc_detect = 1;
    viddec_emit_init(&(pm->emitter));
}

void output_omar_wires( unsigned int value )
{
#ifdef RTL_SIMULATION
    reg_write(CONFIG_IPC_ROFF_HOST_DOORBELL, value );
#endif    
}

/*------------------------------------------------------------------------------
 * Function:  viddec_fw_init_swap_memory
 * This function is responsible for seeting the swap memory to a good state for current stream.
 * The swap parameter tells us whether we need to dma the context to local memory.
 * We call init on emitter and parser manager which inturn calls init of the codec we are opening the stream for.
 *------------------------------------------------------------------------------
 */

void viddec_fw_init_swap_memory(unsigned int stream_id, unsigned int swap, unsigned int clean)
{
    mfd_pk_strm_cxt *cxt;
    mfd_stream_info *cxt_swap;
    cxt = (mfd_pk_strm_cxt *)&(_dmem.srm_cxt);
    cxt_swap = (mfd_stream_info *)&(_dmem.stream_info[stream_id]);

    if(swap)
    {/* Swap context into local memory */
        cp_using_dma(cxt_swap->ddr_cxt, (uint32_t) &(cxt->pm), sizeof(viddec_pm_cxt_t), false, false);
    }
    
    {
        mfd_init_swap_memory(&(cxt->pm), cxt_swap->strm_type, cxt_swap->ddr_cxt+cxt_swap->cxt_size, clean);
        cxt_swap->wl_time = 0;
        cxt_swap->es_time = 0;
    }
    if(swap)
    {/* Swap context into DDR */
        cp_using_dma(cxt_swap->ddr_cxt, (uint32_t) &(cxt->pm), sizeof(viddec_pm_cxt_t), true, false);
    }
}

/*------------------------------------------------------------------------------
 * Function: viddec_fw_push_current_frame_to_output
 * This is a helper function to read a workload from input queue and push to output queue.
 * This is called when are done with a frame.
 *------------------------------------------------------------------------------
 */
static inline void viddec_fw_push_current_frame_to_output(FW_IPC_Handle *fwipc, uint32_t cur)
{
    ipc_msg_data wkld_to_push;
    FwIPC_ReadMessage(fwipc, &fwipc->wkld_q[cur], (char *)&(wkld_to_push), sizeof(ipc_msg_data));
    FwIPC_SendMessage(fwipc, cur, (char *)&(wkld_to_push),  sizeof(ipc_msg_data));    
}

/*------------------------------------------------------------------------------
 * Function: viddec_fw_get_next_stream_to_schedule
 * This is a helper function to figure out which active stream needs to be scheduled next.
 * If none of the streams are active it returns -1.
 *------------------------------------------------------------------------------
 */
static inline int viddec_fw_get_next_stream_to_schedule(void)
{
    int32_t cur = -1;

    if(mfd_round_robin(viddec_stream_priority_REALTIME, &cur, _dmem.g_pk_data.high_id))
    {
        /* On success store the stream id */
        _dmem.g_pk_data.high_id = cur;
    }
    else
    {
        /* Check Low priority Queues, Since we couldn't find a valid realtime stream */
        if(mfd_round_robin(viddec_stream_priority_BACKGROUND, &cur, _dmem.g_pk_data.low_id))
        {
            _dmem.g_pk_data.low_id = cur;
        }
    }

    return cur;
}

/*------------------------------------------------------------------------------
 * Function: viddec_fw_update_pending_interrupt_flag
 * This is a helper function to figure out if we need to mark an interrupt pending for this stream.
 * We update status value here if we find any of the interrupt conditions are true.
 * If this stream has a interrupt pending which we could not send to host, we don't overwrite past status info.
 *------------------------------------------------------------------------------
 */
static inline void viddec_fw_update_pending_interrupt_flag(int32_t cur, mfd_stream_info *cxt_swap, uint8_t pushed_a_workload,
                                                           uint32_t es_Q_data_at_start)
{
    if(_dmem.int_status[cur].mask)
    {
        if(!cxt_swap->pending_interrupt)
        {
            uint32_t es_Q_data_now;
            uint8_t wmark_boundary_reached=false;
            es_Q_data_now = viddec_fw_get_total_input_Q_data((uint32_t)cur);
            wmark_boundary_reached = viddec_fw_check_watermark_boundary(es_Q_data_at_start, es_Q_data_now, cxt_swap->low_watermark);
            _dmem.int_status[cur].status = 0;
            if(pushed_a_workload)
            {
                _dmem.int_status[cur].status |= VIDDEC_FW_WKLD_DATA_AVAIL;
            }
            if(wmark_boundary_reached)
            {
                _dmem.int_status[cur].status |= VIDDEC_FW_INPUT_WATERMARK_REACHED;
            }
            cxt_swap->pending_interrupt = ( _dmem.int_status[cur].status != 0);
        }
    }
    else
    {
        cxt_swap->pending_interrupt = false;
    }
}

static inline void viddec_fw_handle_error_and_inband_messages(int32_t cur, uint32_t pm_ret)
{
    FW_IPC_Handle *fwipc = GET_IPC_HANDLE(_dmem);

    viddec_fw_push_current_frame_to_output(fwipc, cur);
    switch(pm_ret)
    {
        case PM_EOS:
        case PM_OVERFLOW:
        {
            viddec_fw_init_swap_memory(cur, false, true);
        }
        break;
        case PM_DISCONTINUITY:
        {
            viddec_fw_init_swap_memory(cur, false, false);
        }
        break;
        default:
            break;
    }
}

void viddec_fw_debug_scheduled_stream_state(int32_t indx, int32_t start)
{
    FW_IPC_Handle *fwipc = GET_IPC_HANDLE(_dmem);
    uint32_t inpt_avail=0, output_avail=0, wklds_avail =0 , pos;
    FW_IPC_ReceiveQue   *rcv_q;
    uint32_t message;

    message = (start) ? SVEN_MODULE_EVENT_GV_FW_PK_SCHDL_STRM_START: SVEN_MODULE_EVENT_GV_FW_PK_SCHDL_STRM_END;
    rcv_q = &fwipc->rcv_q[indx];
    inpt_avail = ipc_mq_read_avail(&rcv_q->mq, (int32_t *)&pos);
    inpt_avail += ((_dmem.stream_info[indx].buffered_data > 0) ? CONFIG_IPC_MESSAGE_MAX_SIZE: 0);
    inpt_avail = inpt_avail >> 4;
    pos = 0;
    output_avail = ipc_mq_read_avail(&fwipc->snd_q[indx].mq, (int32_t *)&pos);
    output_avail = output_avail >> 4;
    pos = 0;
    wklds_avail =  ipc_mq_read_avail(&fwipc->wkld_q[indx].mq, (int32_t *)&pos);
    wklds_avail = wklds_avail >> 4;
    WRITE_SVEN(message, (int)indx, (int)inpt_avail, (int)output_avail,
               (int)wklds_avail, 0, 0);
}

/*------------------------------------------------------------------------------
 * Function:  viddec_fw_process_async_queues(A.K.A -> Parser Kernel)
 * This function is responsible for handling the asynchronous queues.
 * 
 * The first step is to figure out which stream to run. The current algorithm
 * will go through all high priority queues for a valid stream, if not found we 
 * go through lower priority queues. 
 *
 * If a valid stream is found we swap the required context from DDR to DMEM and do all necessary
 * things to setup the stream.
 * Once a stream is setup we call the parser manager and wait until a wrkld is created or no more input
 * data left.
 * Once we find a wkld we push it to host and save the current context to DDR.
 *------------------------------------------------------------------------------
 */

static inline int32_t viddec_fw_process_async_queues()
{
    int32_t cur = -1;

    cur = viddec_fw_get_next_stream_to_schedule();
	
    if(cur != -1)
    {
        FW_IPC_Handle *fwipc = GET_IPC_HANDLE(_dmem);
        FW_IPC_ReceiveQue   *rcv_q;
        /* bits captured by OMAR */
        output_omar_wires( 0x0 );
        rcv_q = &fwipc->rcv_q[cur];
        {
            mfd_pk_strm_cxt *cxt;
            mfd_stream_info *cxt_swap;
            cxt = (mfd_pk_strm_cxt *)&(_dmem.srm_cxt);
            cxt_swap = (mfd_stream_info *)&(_dmem.stream_info[cur]);
            
            /* Step 1: Swap rodata to local memory. Not doing this currently as all the rodata fits in local memory. */
            {/* Step 2: Swap context into local memory */
                cp_using_dma(cxt_swap->ddr_cxt, (uint32_t) &(cxt->pm), sizeof(viddec_pm_cxt_t), false, false);
            }
            /* Step 3:setup emitter by reading input data and workloads and initialising it */
            mfd_setup_emitter(fwipc, &fwipc->wkld_q[cur], cxt);
            viddec_fw_debug_scheduled_stream_state(cur, true);
            /* Step 4: Call Parser Manager until workload done or No more ES buffers */
            {
                ipc_msg_data *data = 0;
                uint8_t stream_active = true, pushed_a_workload=false;
                uint32_t pm_ret = PM_SUCCESS, es_Q_data_at_start;
                uint32_t start_time, time=0;

                start_time = set_wdog(VIDDEC_WATCHDOG_COUNTER_MAX);
                timer=0;
                es_Q_data_at_start = viddec_fw_get_total_input_Q_data((uint32_t)cur);
                do
                {
                    output_omar_wires( 0x1 );
                    {
                        uint32_t es_t0,es_t1;
                        get_wdog(&es_t0);
                        pm_ret = viddec_pm_parse_es_buffer(&(cxt->pm), cxt_swap->strm_type, data);
                        get_wdog(&es_t1);
                        cxt_swap->es_time += get_total_ticks(es_t0, es_t1);
                    }
                    switch(pm_ret)
                    {
                        case PM_EOS:
                        case PM_WKLD_DONE:
                        case PM_OVERFLOW:
                        case PM_DISCONTINUITY:
                        {/* Finished a frame worth of data or encountered fatal error*/
                            stream_active = false;
                        }
                        break;
                        case PM_NO_DATA:
                        {
                            uint32_t next_ret=0;
                            if ( (NULL != data) && (0 != cxt_swap->es_time) )
                            {
                                /* print performance info for this buffer */
                                WRITE_SVEN(SVEN_MODULE_EVENT_GV_FW_PK_ES_DONE, (int)cur, (int)cxt_swap->es_time, (int)cxt->input.phys,
                                           (int)cxt->input.len, (int)cxt->input.id, (int)cxt->input.flags );
                                cxt_swap->es_time = 0;
                            }
                            
                            next_ret = FwIPC_ReadMessage(fwipc, rcv_q, (char *)&(cxt->input), sizeof(ipc_msg_data));
                            if(next_ret != 0)
                            {
                                data = &(cxt->input);
                                WRITE_SVEN(SVEN_MODULE_EVENT_GV_FW_PK_ES_START, (int)cur, (int)cxt_swap->wl_time,
                                           (int)cxt->input.phys, (int)cxt->input.len, (int)cxt->input.id, (int)cxt->input.flags );
                            }
                            else
                            {/* No data on input queue */
                                cxt_swap->buffered_data = 0;
                                stream_active = false;
                            }
                        }
                        break;
                        default:
                        {/* Not done with current buffer */
                            data = NULL;
                        }
                        break;
                    }
                }while(stream_active);
                get_wdog(&time);
                cxt_swap->wl_time += get_total_ticks(start_time, time);
                /* Step 5: If workload done push workload out */
                switch(pm_ret)
                {
                    case PM_EOS:
                    case PM_WKLD_DONE:
                    case PM_OVERFLOW:
                    case PM_DISCONTINUITY:
                    {/* Push current workload as we are done with the frame */
                        cxt_swap->buffered_data = (PM_WKLD_DONE == pm_ret) ? true: false;
                        viddec_pm_update_time(&(cxt->pm), cxt_swap->wl_time);

                        /* xmit performance info for this workload output */
                        WRITE_SVEN( SVEN_MODULE_EVENT_GV_FW_PK_WL_DONE, (int)cur, (int)cxt_swap->wl_time, (int)cxt->wkld1.phys,
                                    (int)cxt->wkld1.len, (int)cxt->wkld1.id, (int)cxt->wkld1.flags );
                        cxt_swap->wl_time = 0;

                        viddec_fw_push_current_frame_to_output(fwipc, cur);
                        if(pm_ret != PM_WKLD_DONE)
                        {
                            viddec_fw_handle_error_and_inband_messages(cur, pm_ret);
                        }
                        pushed_a_workload = true;
                    }
                    break;
                    default:
                        break;
                }
                /* Update information on whether we have active interrupt for this stream */
                viddec_fw_update_pending_interrupt_flag(cur, cxt_swap, pushed_a_workload, es_Q_data_at_start);
            }
            viddec_fw_debug_scheduled_stream_state(cur, false);
            /* Step 6: swap context into DDR */
            {
                cp_using_dma(cxt_swap->ddr_cxt, (uint32_t) &(cxt->pm), sizeof(viddec_pm_cxt_t), true, false);
            }
        }

    }
    return cur;
}


/*------------------------------------------------------------------------------
 * Function:  process_command
 * This magic function figures out which function to excute based on autoapi.
 *------------------------------------------------------------------------------
 */

static inline void process_command(uint32_t cmd_id, unsigned char *command)
{
    int32_t groupid = ((cmd_id >> 24) - 13) & 0xff;
    int32_t funcid = cmd_id & 0xffffff;
    /* writing func pointer to hsot doorbell */
    output_omar_wires( (int) viddec_fw_api_array[groupid].unmarshal[funcid] );
    WRITE_SVEN( SVEN_MODULE_EVENT_GV_FW_AUTOAPI_CMD,(int) cmd_id, (int) command, ((int *)command)[0],
                ((int *)command)[1], ((int *)command)[2], ((int *)command)[3] );

    viddec_fw_api_array[groupid].unmarshal[funcid](0, command);

}

/*------------------------------------------------------------------------------
 * Function:  viddec_fw_process_sync_queues(A.K.A auto api)
 * Params:
 *       [in] msg: common sync structure where all required parameters are present for autoapi.
 *
 * This function is responsible for handling synchronous messages. All synchronous messages
 * are handled through auto api.
 * what are synchronous messages?  Anything releated to teardown or opening a stream Ex: open, close, flush etc.
 *
 * Only once synchronous message at a time. When a synchronous message its id is usually in cp doorbell. Once
 * we are done handling synchronous message through auto api we release doorbell to let the host write next 
 * message.
 *------------------------------------------------------------------------------
 */

static inline int32_t viddec_fw_process_sync_queues(unsigned char *msg)
{
    int32_t ret = -1;

    if(0 == reg_read(CONFIG_IPC_ROFF_RISC_DOORBELL_STATUS))
    {
        uint32_t command1=0;
        command1 = reg_read(CONFIG_IPC_ROFF_RISC_RX_DOORBELL);
        process_command(command1, msg);
        reg_write(CONFIG_IPC_ROFF_RISC_DOORBELL_STATUS, 0x2); /* Inform Host we are done with this message */
        ret = 0;
    }
    return ret;
}

/*------------------------------------------------------------------------------
 * Function:  viddec_fw_check_for_pending_int
 * This function walks through all active streams to see if atleast one stream has a pending interrupt
 * and returns true if it finds one.
 *------------------------------------------------------------------------------
 */
static inline uint32_t viddec_fw_check_for_pending_int(void)
{
    uint32_t i=0, ret=false;
    /* start from 0 to max streams that fw can handle*/
    while(i < FW_SUPPORTED_STREAMS)
    {
        if(_dmem.stream_info[i].state == 1)
        {
            if((_dmem.stream_info[i].pending_interrupt) && _dmem.int_status[i].mask)
            {
                ret = true;
            }
            else
            {/* If this is not in INT state clear the status before sending it to host */
                _dmem.int_status[i].status = 0;
            }
        }
        i++;
    }
    return ret;
}

/*------------------------------------------------------------------------------
 * Function:  viddec_fw_clear_processed_int
 * This function walks through all active streams to clear pending interrupt state.This is
 * called after a INT was issued.
 *------------------------------------------------------------------------------
 */
static inline void viddec_fw_clear_processed_int(void)
{
    uint32_t i=0;
    /* start from 0 to max streams that fw can handle*/
    while(i < FW_SUPPORTED_STREAMS)
    {
        //if(_dmem.stream_info[i].state == 1)
        _dmem.stream_info[i].pending_interrupt = false;
        i++;
    }
    return;
}

/*------------------------------------------------------------------------------
 * Function:  viddec_fw_int_host
 * This function interrupts host if data is available for host or any other status
 * is valid which the host configures the FW to.
 * There is only one interrupt line so this is a shared Int for all streams, Host should 
 * look at status of all streams when it receives a Int.
 * The FW will interrupt the host only if host doorbell is free, in other words the host 
 * should always make the doorbell free at the End of its ISR.
 *------------------------------------------------------------------------------
 */

static inline int32_t viddec_fw_int_host()
{
    /* We Interrupt the host only if host is ready to receive an interrupt */
    if((reg_read(CONFIG_IPC_ROFF_HOST_DOORBELL_STATUS) & GV_DOORBELL_STATS) == GV_DOORBELL_STATS)
    {
        if(viddec_fw_check_for_pending_int())
        {
            /* If a pending interrupt is found trigger INT */
            reg_write(CONFIG_IPC_ROFF_HOST_DOORBELL, VIDDEC_FW_PARSER_IPC_HOST_INT);
            /* Clear all stream's pending Interrupt info since we use a global INT for all streams */
            viddec_fw_clear_processed_int();
        }
    }
    return 1;
}
volatile unsigned int stack_corrupted __attribute__ ((section (".stckovrflwchk")));
/*------------------------------------------------------------------------------
 * Function:  main
 * This function is the main firmware function. Its a infinite loop where it polls
 * for messages and processes them if they are available. Currently we ping pong between
 * synchronous and asynchronous messages one at a time. If we have multiple aysnchronous
 * queues we always process only one between synchronous messages.
 *
 * For multiple asynchronous queues we round robin through the high priorities first and pick
 * the first one available. Next time when we come around for asynchronous message we start
 * from the next stream onwards so this guarantees that we give equal time slices for same
 * priority queues. If no high priority queues are active we go to low priority queues and repeat
 * the same process.
 *------------------------------------------------------------------------------
 */

int main(void)
{
    unsigned char *msg = (uint8_t *)&(_dmem.buf.data[0]);
    
    /* We wait until host reads sync message */
    reg_write(CONFIG_IPC_ROFF_HOST_RX_DOORBELL, GV_FW_IPC_HOST_SYNC);

    while ( GV_DOORBELL_STATS != reg_read(CONFIG_IPC_ROFF_HOST_DOORBELL_STATUS) )
    { /*poll register until done bit is set */
      /* Host re-writes Vsparc DRAM (BSS) in this loop and will hit the DONE bit when complete */
    }
    enable_intr();
    /* Initialize State for queues */
    viddec_fw_parser_register_callbacks();
    FwIPC_Initialize(GET_IPC_HANDLE(_dmem), (volatile char *)msg);
    _dmem.g_pk_data.high_id = _dmem.g_pk_data.low_id = -1;
    viddec_pm_init_ops();
    stack_corrupted = 0xDEADBEEF;
    while(1)
    {
        viddec_fw_process_sync_queues(msg);
        viddec_fw_process_async_queues();
        viddec_fw_int_host();
#if 0
        if(stack_corrupted != 0xDEADBEEF)
        {
            WRITE_SVEN(SVEN_MODULE_EVENT_GV_FW_FATAL_STACK_CORRPON, 0, 0, 0, 0, 0, 0);
            while(1);
        }
#endif
    }
    return 1;
}
