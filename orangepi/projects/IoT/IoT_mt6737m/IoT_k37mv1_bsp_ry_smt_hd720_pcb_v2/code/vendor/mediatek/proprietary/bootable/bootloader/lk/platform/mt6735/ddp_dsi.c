/* Copyright Statement:
*
* This software/firmware and related documentation ("MediaTek Software") are
* protected under relevant copyright laws. The information contained herein
* is confidential and proprietary to MediaTek Inc. and/or its licensors.
* Without the prior written permission of MediaTek inc. and/or its licensors,
* any reproduction, modification, use or disclosure of MediaTek Software,
* and information contained herein, in whole or in part, shall be strictly prohibited.
*/
/* MediaTek Inc. (C) 2015. All rights reserved.
*
* BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
* THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
* RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
* AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
* NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
* SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
* SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
* THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
* THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
* CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
* SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
* STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
* CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
* AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
* OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
* MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
*/
#define LOG_TAG "DSI"

#define ENABLE_DSI_INTERRUPT 0
#include <string.h>
#include <platform/mt_gpt.h>
#include <platform/ddp_info.h>


#include <platform/mt_typedefs.h>
#include <platform/sync_write.h>

#include <platform/disp_drv_platform.h>
#include <platform/disp_drv_log.h>
/* #include <debug.h> */
/* #include <platform/ddp_path.h> */

#include <platform/ddp_manager.h>
/* #include <platform/ddp_dump.h> */



#include <platform/ddp_reg.h>
#include <platform/ddp_dsi.h>

#include <debug.h>

/*...below is new dsi driver...*/
#define IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII 0
static int dsi_reg_op_debug;
static int mipi_reg_op_debug;

#define DSI_OUTREG32(cmdq, addr, val) \
    {\
        if (dsi_reg_op_debug) \
            DISPMSG("[dsi/reg]0x%08x=0x%08x, cmdq:0x%08x\n", (unsigned int)addr, val, (unsigned int)cmdq);\
        if (cmdq) \
            {} \
        else \
            mt_reg_sync_writel(val, addr); }


#define BIT_TO_VALUE(TYPE, bit)  \
        do {    TYPE r;\
            *(unsigned int *)(&r) = ((unsigned int)0x00000000);   \
            r.bit = ~(r.bit);\
            r;\
            } while (0);\

#define DSI_MASKREG32(cmdq, REG, MASK, VALUE)  \
    {\
        if (cmdq)   \
            {} \
        else\
            DSI_OUTREG32(cmdq, REG, (INREG32(REG)&~(MASK))|(VALUE));\
    }

#define DSI_OUTREGBIT(cmdq, TYPE, REG, bit, value)  \
    {\
    if (cmdq)\
    {do {\
    } while (0); } \
    else\
    {\
        do {    \
            TYPE r = *((TYPE *)&INREG32(&REG));   \
            r.bit = value;    \
            DSI_OUTREG32(cmdq, &REG, AS_UINT32(&r));      \
            } while (0);\
    } }

#ifdef MACH_FPGA
#define MIPITX_INREG32(addr)                                \
    {                                                       \
        unsigned int val = 0;                                       \
        if(mipi_reg_op_debug)                               \
        {                                                   \
            DISPMSG("[mipitx/inreg]0x%08x=0x%08x\n", addr, val);    \
        }                                                   \
        val;                                                    \
    }

)
#define MIPITX_OUTREG32(addr, val) \
    {\
        if (mipi_reg_op_debug) \
        {   DISPMSG("[mipitx/reg]0x%08x=0x%08x\n", addr, val); } \
    }
#define MIPITX_OUTREGBIT(TYPE, REG, bit, value)  \
    {\
        do {    \
            TYPE r;\
            *(unsigned int *)(&r) = ((unsigned int)0x00000000);   \
            r.bit = value;    \
            MIPITX_OUTREG32(&REG, AS_UINT32(&r));     \
            } while (0);\
    }
#define MIPITX_MASKREG32(x, y, z)  MIPITX_OUTREG32(x, (MIPITX_INREG32(x)&~(y))|(z))
#else
#define MIPITX_INREG32(addr)                                \
    {                                                       \
        unsigned int val = 0;                                       \
        val = INREG32(addr);                                \
        if(mipi_reg_op_debug)                               \
        {                                                   \
            DISPMSG("[mipitx/inreg]0x%08x=0x%08x\n", addr, val);    \
        }                                                   \
        val;                                                    \
    }

#define MIPITX_OUTREG32(addr, val) \
    {\
        if (mipi_reg_op_debug) \
        {   \
            DISPMSG("[mipitx/reg]0x%08x=0x%08x\n", (unsigned int)addr, val);\
        } \
        mt_reg_sync_writel(val, addr);\
    }

#define MIPITX_OUTREGBIT(TYPE, REG, bit, value)  \
    {\
        do {    \
            TYPE r;\
            r = *((TYPE *)&INREG32(&REG));    \
            r.bit = value;    \
            MIPITX_OUTREG32(&REG, AS_UINT32(&r));     \
            } while (0);\
    }

#define MIPITX_MASKREG32(x, y, z)  MIPITX_OUTREG32(x, (MIPITX_INREG32(x)&~(y))|(z))
#endif

#define DSI_POLLREG32(cmdq, addr, mask, value)  \
    do {\
        {} \
    } while (0);

#define DSI_INREG32(type, addr)                                              \
        ({                                                               \
            unsigned int var = 0;                                            \
            union p_regs                                                     \
            {                                                                \
                type p_reg;                                                  \
                unsigned int *p_uint;                                        \
            } p_temp1;                                                       \
            p_temp1.p_reg  = (type)(addr);                                   \
            var = INREG32(p_temp1.p_uint);                                   \
            var;                                                             \
        })

#define DSI_READREG32(type, dst, src)                                \
            {                                                                    \
                union p_regs                                                     \
                {                                                                \
                    type p_reg;                                              \
                    unsigned int *p_uint;                                        \
                } p_temp1, p_temp2;                                              \
                p_temp1.p_reg  = (type)(dst);                                \
                p_temp2.p_reg  = (type)(src);                                \
                OUTREG32(p_temp1.p_uint, INREG32(p_temp2.p_uint)); }


typedef struct {
	void *handle;
	bool enable;
	DSI_REGS regBackup;
	unsigned int cmdq_size;
	LCM_DSI_PARAMS dsi_params;
} t_dsi_context;

t_dsi_context _dsi_context[DSI_INTERFACE_NUM];
#define DSI_MODULE_BEGIN(x) (0)
#define DSI_MODULE_END(x)       (0)
#define DSI_MODULE_to_ID(x) (0)
#define DIFF_CLK_LANE_LP 0X10
static PDSI_REGS const DSI_REG[2] = { (PDSI_REGS) (DSI0_BASE), (PDSI_REGS) (0) };
static PDSI_PHY_REGS const DSI_PHY_REG[2] =
{ (PDSI_PHY_REGS) (MIPI_TX0_BASE), (PDSI_PHY_REGS) (0) };
static PDSI_CMDQ_REGS const DSI_CMDQ_REG[2] =
{ (PDSI_CMDQ_REGS) (DSI0_BASE + 0x200), (PDSI_CMDQ_REGS) (0 + 0x200) };
static PDSI_VM_CMDQ_REGS const DSI_VM_CMD_REG[2] =
{ (PDSI_VM_CMDQ_REGS) (DSI0_BASE + 0x134), (PDSI_VM_CMDQ_REGS) (0 + 0x134) };

void DSI_PHY_clk_setting(DISP_MODULE_ENUM module, void *cmdq, LCM_DSI_PARAMS *dsi_params);

static void _DSI_INTERNAL_IRQ_Handler(DISP_MODULE_ENUM module, unsigned int param)
{
}


static DSI_STATUS DSI_Reset(DISP_MODULE_ENUM module, void *cmdq)
{
	int i = 0;

	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		DSI_OUTREGBIT(cmdq, DSI_COM_CTRL_REG, DSI_REG[i]->DSI_COM_CTRL.ADDR, REGS.DSI_RESET, 1);
		DSI_OUTREGBIT(cmdq, DSI_COM_CTRL_REG, DSI_REG[i]->DSI_COM_CTRL.ADDR, REGS.DSI_RESET, 0);
	}

	return DSI_STATUS_OK;
}

static int _dsi_is_video_mode(DISP_MODULE_ENUM module)
{
	int i = DSI_MODULE_to_ID(module);

	if (DSI_REG[i]->DSI_MODE_CTRL.REGS.MODE == CMD_MODE)
		return 0;
	else
		return 1;
}

static DSI_STATUS DSI_SetMode(DISP_MODULE_ENUM module, void *cmdq, unsigned int mode)
{
	int i = 0;

	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		DSI_OUTREGBIT(cmdq, DSI_MODE_CTRL_REG, DSI_REG[i]->DSI_MODE_CTRL.ADDR, REGS.MODE, mode);
	}

	return DSI_STATUS_OK;
}

static void DSI_WaitForNotBusy(DISP_MODULE_ENUM module, void *cmdq)
{
	int i = 0;
	unsigned int tmp = 0;
	if (cmdq) {
		for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
			DSI_POLLREG32(cmdq, &DSI_REG[i]->DSI_INTSTA.ADDR, 0x80000000, 0x0);
		}
		return;
	}


	/*...dsi video is always in busy state... */
	if (_dsi_is_video_mode(module)) {
		return;
	}
	/* TODO: */
	i = DSI_MODULE_BEGIN(module);
	while (1) {
		tmp = INREG32(&DSI_REG[i]->DSI_INTSTA.ADDR);
		if (!(tmp & 0x80000000))
			break;
	}
}

void DSI_lane0_ULP_mode(DISP_MODULE_ENUM module, void *cmdq, bool enter)
{
	int i = 0;

	ASSERT(cmdq == NULL);

	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		if (enter) {
			DSI_OUTREGBIT(cmdq, DSI_PHY_LD0CON_REG, DSI_REG[i]->DSI_PHY_LD0CON.ADDR,
			              REGS.L0_RM_TRIG_EN, 0);
			mdelay(1);
			DSI_OUTREGBIT(cmdq, DSI_PHY_LD0CON_REG, DSI_REG[i]->DSI_PHY_LD0CON.ADDR,
			              REGS.L0_ULPM_EN, 0);
			DSI_OUTREGBIT(cmdq, DSI_PHY_LD0CON_REG, DSI_REG[i]->DSI_PHY_LD0CON.ADDR,
			              REGS.L0_ULPM_EN, 1);
			/* mdelay(1); */
		} else {
			DSI_OUTREGBIT(cmdq, DSI_PHY_LD0CON_REG, DSI_REG[i]->DSI_PHY_LD0CON.ADDR,
			              REGS.L0_ULPM_EN, 0);
			mdelay(1);
			DSI_OUTREGBIT(cmdq, DSI_PHY_LD0CON_REG, DSI_REG[i]->DSI_PHY_LD0CON.ADDR,
			              REGS.L0_WAKEUP_EN, 1);
			mdelay(1);
			DSI_OUTREGBIT(cmdq, DSI_PHY_LD0CON_REG, DSI_REG[i]->DSI_PHY_LD0CON.ADDR,
			              REGS.L0_WAKEUP_EN, 0);
			mdelay(1);
		}
	}
}


void DSI_clk_ULP_mode(DISP_MODULE_ENUM module, void *cmdq, bool enter)
{
	int i = 0;

	ASSERT(cmdq == NULL);

	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		if (enter) {
			DSI_OUTREGBIT(cmdq, DSI_PHY_LCCON_REG, DSI_REG[i]->DSI_PHY_LCCON.ADDR,
			              REGS.LC_ULPM_EN, 0);
			DSI_OUTREGBIT(cmdq, DSI_PHY_LCCON_REG, DSI_REG[i]->DSI_PHY_LCCON.ADDR,
			              REGS.LC_ULPM_EN, 1);
			mdelay(1);
		} else {
			DSI_OUTREGBIT(cmdq, DSI_PHY_LCCON_REG, DSI_REG[i]->DSI_PHY_LCCON.ADDR,
			              REGS.LC_ULPM_EN, 0);
			mdelay(1);
			DSI_OUTREGBIT(cmdq, DSI_PHY_LCCON_REG, DSI_REG[i]->DSI_PHY_LCCON.ADDR,
			              REGS.LC_WAKEUP_EN, 1);
			mdelay(1);
			DSI_OUTREGBIT(cmdq, DSI_PHY_LCCON_REG, DSI_REG[i]->DSI_PHY_LCCON.ADDR,
			              REGS.LC_WAKEUP_EN, 0);
			mdelay(1);
		}
	}
}

bool DSI_clk_HS_state(DISP_MODULE_ENUM module, void *cmdq)
{
	int i = DSI_MODULE_to_ID(module);
	DSI_PHY_LCCON_REG tmpreg;

	DSI_READREG32(PDSI_PHY_LCCON_REG, &tmpreg.ADDR, &DSI_REG[i]->DSI_PHY_LCCON.ADDR);
	return tmpreg.REGS.LC_HS_TX_EN ? TRUE : FALSE;
}

void DSI_clk_HS_mode(DISP_MODULE_ENUM module, void *cmdq, bool enter)
{
	int i = 0;

	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		if (enter) {    /* && !DSI_clk_HS_state(i, cmdq)) */
			DSI_OUTREGBIT(cmdq, DSI_PHY_LCCON_REG, DSI_REG[i]->DSI_PHY_LCCON.ADDR,
			              REGS.LC_HS_TX_EN, 1);
		} else if (!enter) {    /* && DSI_clk_HS_state(i, cmdq)) */
			DSI_OUTREGBIT(cmdq, DSI_PHY_LCCON_REG, DSI_REG[i]->DSI_PHY_LCCON.ADDR,
			              REGS.LC_HS_TX_EN, 0);
		}
	}
}

const char *_dsi_cmd_mode_parse_state(unsigned int state)
{
	switch (state) {
		case 0x0001:
			return "idle";
		case 0x0002:
			return "Reading command queue for header";
		case 0x0004:
			return "Sending type-0 command";
		case 0x0008:
			return "Waiting frame data from RDMA for type-1 command";
		case 0x0010:
			return "Sending type-1 command";
		case 0x0020:
			return "Sending type-2 command";
		case 0x0040:
			return "Reading command queue for data";
		case 0x0080:
			return "Sending type-3 command";
		case 0x0100:
			return "Sending BTA";
		case 0x0200:
			return "Waiting RX-read data ";
		case 0x0400:
			return "Waiting SW RACK for RX-read data";
		case 0x0800:
			return "Waiting TE";
		case 0x1000:
			return "Get TE ";
		case 0x2000:
			return "Waiting external TE";
		case 0x4000:
			return "Waiting SW RACK for TE";
		default:
			return "unknown";
	}
}

DSI_STATUS DSI_DumpRegisters(DISP_MODULE_ENUM module, void *cmdq, int level)
{
	UINT32 i;

	if (level >= 0) {
		if (module == DISP_MODULE_DSI0 /* || module == DISP_MODULE_DSIDUAL */) {
			unsigned int DSI_DBG6_Status = (INREG32(DSI0_BASE + 0x160)) & 0xffff;
			dprintf(CRITICAL, "DSI0 state:%s\n",
			        _dsi_cmd_mode_parse_state(DSI_DBG6_Status));
			dprintf(CRITICAL, "DSI Mode: lane num: transfer count: status: ");
		}
#if 0
		if (module == DISP_MODULE_DSI1 || module == DISP_MODULE_DSIDUAL) {
			unsigned int DSI_DBG6_Status = (INREG32(DSI1_BASE + 0x160)) & 0xffff;
			dprintf(CRITICAL, "DSI1 state:%s\n",
			        _dsi_cmd_mode_parse_state(DSI_DBG6_Status));
			dprintf(CRITICAL, "DSI Mode: lane num: transfer count: status: ");
		}
#endif
	}
	if (level >= 1) {
		if (module == DISP_MODULE_DSI0 /* || module == DISP_MODULE_DSIDUAL */) {
			dprintf(CRITICAL, "---------- Start dump DSI0 registers ----------\n");

			for (i = 0; i < sizeof(DSI_REGS); i += 16) {
				dprintf(CRITICAL, "DSI+%04x : 0x%08x  0x%08x  0x%08x  0x%08x\n", i,
				        INREG32(DSI0_BASE + i), INREG32(DSI0_BASE + i + 0x4),
				        INREG32(DSI0_BASE + i + 0x8), INREG32(DSI0_BASE + i + 0xc));
			}

			for (i = 0; i < sizeof(DSI_CMDQ_REGS); i += 16) {
				dprintf(CRITICAL, "DSI_CMD+%04x : 0x%08x  0x%08x  0x%08x  0x%08x\n",
				        i, INREG32((DSI0_BASE + 0x200 + i)),
				        INREG32((DSI0_BASE + 0x200 + i + 0x4)),
				        INREG32((DSI0_BASE + 0x200 + i + 0x8)),
				        INREG32((DSI0_BASE + 0x200 + i + 0xc)));
			}

			for (i = 0; i < sizeof(DSI_PHY_REGS); i += 16) {
				dprintf(CRITICAL,
				        "DSI_PHY+%04x : 0x%08x    0x%08x  0x%08x  0x%08x\n", i,
				        INREG32((MIPI_TX0_BASE + i)),
				        INREG32((MIPI_TX0_BASE + i + 0x4)),
				        INREG32((MIPI_TX0_BASE + i + 0x8)),
				        INREG32((MIPI_TX0_BASE + i + 0xc)));
			}
		}
#if 0
		if (module == DISP_MODULE_DSI1 || module == DISP_MODULE_DSIDUAL) {
			unsigned int DSI_DBG6_Status = (INREG32(DSI1_BASE + 0x160)) & 0xffff;

			dprintf(CRITICAL, "---------- Start dump DSI1 registers ----------\n");

			for (i = 0; i < sizeof(DSI_REGS); i += 16) {
				dprintf(CRITICAL, "DSI+%04x : 0x%08x  0x%08x  0x%08x  0x%08x\n", i,
				        INREG32(DSI1_BASE + i), INREG32(DSI1_BASE + i + 0x4),
				        INREG32(DSI1_BASE + i + 0x8), INREG32(DSI1_BASE + i + 0xc));
			}

			for (i = 0; i < sizeof(DSI_CMDQ_REGS); i += 16) {
				dprintf(CRITICAL, "DSI_CMD+%04x : 0x%08x  0x%08x  0x%08x  0x%08x\n",
				        i, INREG32((DSI1_BASE + 0x200 + i)),
				        INREG32((DSI1_BASE + 0x200 + i + 0x4)),
				        INREG32((DSI1_BASE + 0x200 + i + 0x8)),
				        INREG32((DSI1_BASE + 0x200 + i + 0xc)));
			}

			for (i = 0; i < sizeof(DSI_PHY_REGS); i += 16) {
				dprintf(CRITICAL,
				        "DSI_PHY+%04x : 0x%08x    0x%08x  0x%08x  0x%08x\n", i,
				        INREG32((MIPI_TX1_BASE + i)),
				        INREG32((MIPI_TX1_BASE + i + 0x4)),
				        INREG32((MIPI_TX1_BASE + i + 0x8)),
				        INREG32((MIPI_TX1_BASE + i + 0xc)));
			}
		}
#endif
	}

	return DSI_STATUS_OK;
}

DSI_STATUS DSI_SleepOut(DISP_MODULE_ENUM module, void *cmdq)
{
	int i = 0;

	/* TODO: can we just start dsi0 for dsi dual? */
	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		DSI_OUTREGBIT(cmdq, DSI_MODE_CTRL_REG, DSI_REG[i]->DSI_MODE_CTRL.ADDR, REGS.SLEEP_MODE, 1);
		DSI_OUTREGBIT(cmdq, DSI_PHY_TIMCON4_REG, DSI_REG[i]->DSI_PHY_TIMECON4.ADDR, REGS.ULPS_WAKEUP, 0x22E09); /* cycle to 1ms for 520MHz */
	}

	return DSI_STATUS_OK;
}


DSI_STATUS DSI_Wakeup(DISP_MODULE_ENUM module, void *cmdq)
{
	int i = 0;

	/* TODO: can we just start dsi0 for dsi dual? */
	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		DSI_OUTREGBIT(cmdq, DSI_START_REG, DSI_REG[i]->DSI_START.ADDR, REGS.SLEEPOUT_START, 0);
		DSI_OUTREGBIT(cmdq, DSI_START_REG, DSI_REG[i]->DSI_START.ADDR, REGS.SLEEPOUT_START, 1);
		mdelay(1);

		DSI_OUTREGBIT(cmdq, DSI_START_REG, DSI_REG[i]->DSI_START.ADDR, REGS.SLEEPOUT_START, 0);
		DSI_OUTREGBIT(cmdq, DSI_MODE_CTRL_REG, DSI_REG[i]->DSI_MODE_CTRL.ADDR, REGS.SLEEP_MODE, 0);
	}

	return DSI_STATUS_OK;
}

DSI_STATUS DSI_BackupRegisters(DISP_MODULE_ENUM module, void *cmdq)
{
	int i = 0;
	DSI_REGS *regs = NULL;

	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		regs = &(_dsi_context[i].regBackup);

		DSI_OUTREG32(cmdq, &regs->DSI_INTEN.ADDR, AS_UINT32(&DSI_REG[i]->DSI_INTEN.ADDR));
		DSI_OUTREG32(cmdq, &regs->DSI_MODE_CTRL.ADDR, AS_UINT32(&DSI_REG[i]->DSI_MODE_CTRL.ADDR));
		DSI_OUTREG32(cmdq, &regs->DSI_TXRX_CTRL.ADDR, AS_UINT32(&DSI_REG[i]->DSI_TXRX_CTRL.ADDR));
		DSI_OUTREG32(cmdq, &regs->DSI_PSCTRL.ADDR, AS_UINT32(&DSI_REG[i]->DSI_PSCTRL.ADDR));

		DSI_OUTREG32(cmdq, &regs->DSI_VSA_NL.ADDR, AS_UINT32(&DSI_REG[i]->DSI_VSA_NL.ADDR));
		DSI_OUTREG32(cmdq, &regs->DSI_VBP_NL.ADDR, AS_UINT32(&DSI_REG[i]->DSI_VBP_NL.ADDR));
		DSI_OUTREG32(cmdq, &regs->DSI_VFP_NL.ADDR, AS_UINT32(&DSI_REG[i]->DSI_VFP_NL.ADDR));
		DSI_OUTREG32(cmdq, &regs->DSI_VACT_NL.ADDR, AS_UINT32(&DSI_REG[i]->DSI_VACT_NL.ADDR));

		DSI_OUTREG32(cmdq, &regs->DSI_HSA_WC.ADDR, AS_UINT32(&DSI_REG[i]->DSI_HSA_WC.ADDR));
		DSI_OUTREG32(cmdq, &regs->DSI_HBP_WC.ADDR, AS_UINT32(&DSI_REG[i]->DSI_HBP_WC.ADDR));
		DSI_OUTREG32(cmdq, &regs->DSI_HFP_WC.ADDR, AS_UINT32(&DSI_REG[i]->DSI_HFP_WC.ADDR));
		DSI_OUTREG32(cmdq, &regs->DSI_BLLP_WC.ADDR, AS_UINT32(&DSI_REG[i]->DSI_BLLP_WC.ADDR));

		DSI_OUTREG32(cmdq, &regs->DSI_HSTX_CKL_WC, AS_UINT32(&DSI_REG[i]->DSI_HSTX_CKL_WC));
		DSI_OUTREG32(cmdq, &regs->DSI_MEM_CONTI.ADDR, AS_UINT32(&DSI_REG[i]->DSI_MEM_CONTI.ADDR));

		DSI_OUTREG32(cmdq, &regs->DSI_PHY_TIMECON0.ADDR,
		             AS_UINT32(&DSI_REG[i]->DSI_PHY_TIMECON0.ADDR));
		DSI_OUTREG32(cmdq, &regs->DSI_PHY_TIMECON1.ADDR,
		             AS_UINT32(&DSI_REG[i]->DSI_PHY_TIMECON1.ADDR));
		DSI_OUTREG32(cmdq, &regs->DSI_PHY_TIMECON2.ADDR,
		             AS_UINT32(&DSI_REG[i]->DSI_PHY_TIMECON2.ADDR));
		DSI_OUTREG32(cmdq, &regs->DSI_PHY_TIMECON3.ADDR,
		             AS_UINT32(&DSI_REG[i]->DSI_PHY_TIMECON3.ADDR));
		DSI_OUTREG32(cmdq, &regs->DSI_PHY_TIMECON4.ADDR,
		             AS_UINT32(&DSI_REG[i]->DSI_PHY_TIMECON4.ADDR));
	}

	return DSI_STATUS_OK;
}

DSI_STATUS DSI_RestoreRegisters(DISP_MODULE_ENUM module, void *cmdq)
{
#if 1
	int i = 0;
	DSI_REGS *regs = NULL;

	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		regs = &(_dsi_context[i].regBackup);

		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_INTEN.ADDR, AS_UINT32(&regs->DSI_INTEN.ADDR));
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_MODE_CTRL.ADDR, AS_UINT32(&regs->DSI_MODE_CTRL.ADDR));
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_TXRX_CTRL.ADDR, AS_UINT32(&regs->DSI_TXRX_CTRL.ADDR));
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_PSCTRL.ADDR, AS_UINT32(&regs->DSI_PSCTRL.ADDR));

		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_VSA_NL.ADDR, AS_UINT32(&regs->DSI_VSA_NL.ADDR));
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_VBP_NL.ADDR, AS_UINT32(&regs->DSI_VBP_NL.ADDR));
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_VFP_NL.ADDR, AS_UINT32(&regs->DSI_VFP_NL.ADDR));
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_VACT_NL.ADDR, AS_UINT32(&regs->DSI_VACT_NL.ADDR));

		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_HSA_WC.ADDR, AS_UINT32(&regs->DSI_HSA_WC.ADDR));
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_HBP_WC.ADDR, AS_UINT32(&regs->DSI_HBP_WC.ADDR));
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_HFP_WC.ADDR, AS_UINT32(&regs->DSI_HFP_WC.ADDR));
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_BLLP_WC.ADDR, AS_UINT32(&regs->DSI_BLLP_WC.ADDR));

		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_HSTX_CKL_WC, AS_UINT32(&regs->DSI_HSTX_CKL_WC));
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_MEM_CONTI.ADDR, AS_UINT32(&regs->DSI_MEM_CONTI.ADDR));

		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_PHY_TIMECON0.ADDR,
		             AS_UINT32(&regs->DSI_PHY_TIMECON0.ADDR));
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_PHY_TIMECON1.ADDR,
		             AS_UINT32(&regs->DSI_PHY_TIMECON1.ADDR));
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_PHY_TIMECON2.ADDR,
		             AS_UINT32(&regs->DSI_PHY_TIMECON2.ADDR));
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_PHY_TIMECON3.ADDR,
		             AS_UINT32(&regs->DSI_PHY_TIMECON3.ADDR));
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_PHY_TIMECON4.ADDR,
		             AS_UINT32(&regs->DSI_PHY_TIMECON4.ADDR));
	}
#endif
	return DSI_STATUS_OK;
}


void DSI_PHY_clk_switch(DISP_MODULE_ENUM module, void *cmdq, int on)
{
#if 1
	int i = 0;

	/* can't use cmdq for this */
	ASSERT(cmdq == NULL);

	if (on) {
		DSI_PHY_clk_setting(module, cmdq, &(_dsi_context[i].dsi_params));
	} else {
		for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
			/* pre_oe/oe = 1 */
			MIPITX_OUTREGBIT(MIPITX_DSI_SW_CTRL_CON0_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_SW_CTRL_CON0.ADDR,
			                 REGS.SW_LNTC_LPTX_PRE_OE, 1);
			MIPITX_OUTREGBIT(MIPITX_DSI_SW_CTRL_CON0_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_SW_CTRL_CON0.ADDR,
			                 REGS.SW_LNTC_LPTX_OE, 1);
			MIPITX_OUTREGBIT(MIPITX_DSI_SW_CTRL_CON0_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_SW_CTRL_CON0.ADDR,
			                 REGS.SW_LNTC_HSTX_PRE_OE, 1);
			MIPITX_OUTREGBIT(MIPITX_DSI_SW_CTRL_CON0_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_SW_CTRL_CON0.ADDR,
			                 REGS.SW_LNTC_HSTX_OE, 1);
			MIPITX_OUTREGBIT(MIPITX_DSI_SW_CTRL_CON1_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_SW_CTRL_CON0.ADDR,
			                 REGS.SW_LNT0_LPTX_PRE_OE, 1);
			MIPITX_OUTREGBIT(MIPITX_DSI_SW_CTRL_CON1_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_SW_CTRL_CON0.ADDR,
			                 REGS.SW_LNT0_LPTX_OE, 1);
			MIPITX_OUTREGBIT(MIPITX_DSI_SW_CTRL_CON1_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_SW_CTRL_CON0.ADDR,
			                 REGS.SW_LNT1_LPTX_PRE_OE, 1);
			MIPITX_OUTREGBIT(MIPITX_DSI_SW_CTRL_CON1_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_SW_CTRL_CON0.ADDR,
			                 REGS.SW_LNT1_LPTX_OE, 1);
			MIPITX_OUTREGBIT(MIPITX_DSI_SW_CTRL_CON1_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_SW_CTRL_CON0.ADDR,
			                 REGS.SW_LNT2_LPTX_PRE_OE, 1);
			MIPITX_OUTREGBIT(MIPITX_DSI_SW_CTRL_CON1_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_SW_CTRL_CON0.ADDR,
			                 REGS.SW_LNT2_LPTX_OE, 1);
			MIPITX_OUTREGBIT(MIPITX_DSI_SW_CTRL_CON1_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_SW_CTRL_CON0.ADDR,
			                 REGS.SW_LNT2_HSTX_PRE_OE, 1);
			MIPITX_OUTREGBIT(MIPITX_DSI_SW_CTRL_CON1_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_SW_CTRL_CON0.ADDR,
			                 REGS.SW_LNT2_HSTX_OE, 1);

			/* switch to mipi tx sw mode */
			MIPITX_OUTREGBIT(MIPITX_DSI_SW_CTRL_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_SW_CTRL_EN.ADDR, REGS.SW_CTRL_EN, 1);

			/* disable mipi clock */
			MIPITX_OUTREGBIT(MIPITX_DSI_PLL_CON0_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON0.ADDR, REGS.RG_DSI0_MPPLL_PLL_EN,
			                 0);
			mdelay(1);
			MIPITX_OUTREGBIT(MIPITX_DSI_PLL_TOP_REG, DSI_PHY_REG[i]->MIPITX_DSI_PLL_TOP.ADDR,
			                 REGS.RG_MPPLL_PRESERVE, 0);
			MIPITX_OUTREGBIT(MIPITX_DSI_TOP_CON_REG, DSI_PHY_REG[i]->MIPITX_DSI_TOP_CON.ADDR,
			                 REGS.RG_DSI_PAD_TIE_LOW_EN, 1);


			MIPITX_OUTREGBIT(MIPITX_DSI_CLOCK_LANE_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_CLOCK_LANE.ADDR,
			                 REGS.RG_DSI_LNTC_LDOOUT_EN, 0);
			MIPITX_OUTREGBIT(MIPITX_DSI_DATA_LANE0_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_DATA_LANE0.ADDR,
			                 REGS.RG_DSI_LNT0_LDOOUT_EN, 0);
			MIPITX_OUTREGBIT(MIPITX_DSI_DATA_LANE1_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_DATA_LANE1.ADDR,
			                 REGS.RG_DSI_LNT1_LDOOUT_EN, 0);
			MIPITX_OUTREGBIT(MIPITX_DSI_DATA_LANE2_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_DATA_LANE2.ADDR,
			                 REGS.RG_DSI_LNT2_LDOOUT_EN, 0);
			MIPITX_OUTREGBIT(MIPITX_DSI_DATA_LANE3_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_DATA_LANE3.ADDR,
			                 REGS.RG_DSI_LNT3_LDOOUT_EN, 0);
			MIPITX_OUTREGBIT(MIPITX_DSI_PLL_PWR_REG, DSI_PHY_REG[i]->MIPITX_DSI_PLL_PWR.ADDR,
			                 REGS.DA_DSI_MPPLL_SDM_ISO_EN, 1);
			MIPITX_OUTREGBIT(MIPITX_DSI_PLL_PWR_REG, DSI_PHY_REG[i]->MIPITX_DSI_PLL_PWR.ADDR,
			                 REGS.DA_DSI_MPPLL_SDM_PWR_ON, 0);
			MIPITX_OUTREGBIT(MIPITX_DSI_TOP_CON_REG, DSI_PHY_REG[i]->MIPITX_DSI_TOP_CON.ADDR,
			                 REGS.RG_DSI_LNT_HS_BIAS_EN, 0);

			MIPITX_OUTREGBIT(MIPITX_DSI_CON_REG, DSI_PHY_REG[i]->MIPITX_DSI_CON.ADDR,
			                 REGS.RG_DSI_CKG_LDOOUT_EN, 0);
			MIPITX_OUTREGBIT(MIPITX_DSI_CON_REG, DSI_PHY_REG[i]->MIPITX_DSI_CON.ADDR,
			                 REGS.RG_DSI_LDOCORE_EN, 0);

			MIPITX_OUTREGBIT(MIPITX_DSI_BG_CON_REG, DSI_PHY_REG[i]->MIPITX_DSI_BG_CON.ADDR,
			                 REGS.RG_DSI_BG_CKEN, 0);
			MIPITX_OUTREGBIT(MIPITX_DSI_BG_CON_REG, DSI_PHY_REG[i]->MIPITX_DSI_BG_CON.ADDR,
			                 REGS.RG_DSI_BG_CORE_EN, 0);

			MIPITX_OUTREGBIT(MIPITX_DSI_PLL_CON0_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON0.ADDR, REGS.RG_DSI0_MPPLL_PREDIV,
			                 0);
			MIPITX_OUTREGBIT(MIPITX_DSI_PLL_CON0_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON0.ADDR, REGS.RG_DSI0_MPPLL_TXDIV0,
			                 0);
			MIPITX_OUTREGBIT(MIPITX_DSI_PLL_CON0_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON0.ADDR, REGS.RG_DSI0_MPPLL_TXDIV1,
			                 0);
			MIPITX_OUTREGBIT(MIPITX_DSI_PLL_CON0_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON0.ADDR, REGS.RG_DSI0_MPPLL_POSDIV,
			                 0);


			MIPITX_OUTREG32(&DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON1.ADDR, 0x00000000);
			MIPITX_OUTREG32(&DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON2.ADDR, 0x50000000);
			MIPITX_OUTREGBIT(MIPITX_DSI_SW_CTRL_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_SW_CTRL_EN.ADDR, REGS.SW_CTRL_EN, 0);
			mdelay(1);
		}
	}
#endif
}

DSI_STATUS DSI_BIST_Pattern_Test(DISP_MODULE_ENUM module, void *cmdq, bool enable,
                                 unsigned int color)
{
	int i = 0;
	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		if (enable) {
			DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_BIST_PATTERN, color);
			/* DSI_OUTREG32(&DSI_REG->DSI_BIST_CON.ADDR, AS_UINT32(&temp_reg)); */
			/* DSI_OUTREGBIT(DSI_BIST_CON_REG, DSI_REG->DSI_BIST_CON.ADDR, REGS.SELF_PAT_MODE, 1); */
			DSI_OUTREGBIT(cmdq, DSI_BIST_CON_REG, DSI_REG[i]->DSI_BIST_CON.ADDR,
			              REGS.SELF_PAT_MODE, 1);
		} else {
			DSI_OUTREGBIT(cmdq, DSI_BIST_CON_REG, DSI_REG[i]->DSI_BIST_CON.ADDR,
			              REGS.SELF_PAT_MODE, 0);
		}

		if (!_dsi_is_video_mode(module)) {
			DSI_T0_INS t0 = {{0}};
			t0.HDR.CONFG = 0x09;
			t0.HDR.Data_ID = 0x39;
			t0.HDR.Data0 = 0x2c;
			t0.HDR.Data1 = 0;

			DSI_OUTREG32(cmdq, &DSI_CMDQ_REG[i]->data[0].ADDR, AS_UINT32(&t0.ADDR));
			DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_CMDQ_SIZE.ADDR, 1);

			/* DSI_OUTREGBIT(DSI_START_REG,DSI_REG->DSI_START.ADDR,REGS.DSI_START,0); */
			DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_START.ADDR, 0);
			DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_START.ADDR, 1);
			/* DSI_OUTREGBIT(DSI_START_REG,DSI_REG->DSI_START.ADDR,REGS.DSI_START,1); */
		}
	}
	return 0;
}

void DSI_Config_VDO_Timing(DISP_MODULE_ENUM module, void *cmdq, LCM_DSI_PARAMS *dsi_params)
{
	int i = 0;
	unsigned int horizontal_sync_active_byte;
	unsigned int horizontal_backporch_byte;
	unsigned int horizontal_frontporch_byte;
	unsigned int horizontal_bllp_byte;
	unsigned int dsiTmpBufBpp;

	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		if (dsi_params->data_format.format == LCM_DSI_FORMAT_RGB565) {
			dsiTmpBufBpp = 2;
		} else {
			dsiTmpBufBpp = 3;
		}

		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_VSA_NL.ADDR, dsi_params->vertical_sync_active);
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_VBP_NL.ADDR, dsi_params->vertical_backporch);
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_VFP_NL.ADDR, dsi_params->vertical_frontporch);
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_VACT_NL.ADDR, dsi_params->vertical_active_line);

		horizontal_sync_active_byte =
		    (dsi_params->horizontal_sync_active * dsiTmpBufBpp - 4);

		if (dsi_params->mode == SYNC_EVENT_VDO_MODE || dsi_params->mode == BURST_VDO_MODE
		        || dsi_params->switch_mode == SYNC_EVENT_VDO_MODE
		        || dsi_params->switch_mode == BURST_VDO_MODE) {
			ASSERT((dsi_params->horizontal_backporch +
			        dsi_params->horizontal_sync_active) * dsiTmpBufBpp > 9);
			horizontal_backporch_byte =
			    ((dsi_params->horizontal_backporch +
			      dsi_params->horizontal_sync_active) * dsiTmpBufBpp - 10);
		} else {
			ASSERT(dsi_params->horizontal_sync_active * dsiTmpBufBpp > 9);
			horizontal_sync_active_byte =
			    (dsi_params->horizontal_sync_active * dsiTmpBufBpp - 10);

			ASSERT(dsi_params->horizontal_backporch * dsiTmpBufBpp > 9);
			horizontal_backporch_byte =
			    (dsi_params->horizontal_backporch * dsiTmpBufBpp - 10);
		}

		ASSERT(dsi_params->horizontal_frontporch * dsiTmpBufBpp > 11);
		horizontal_frontporch_byte =
		    (dsi_params->horizontal_frontporch * dsiTmpBufBpp - 12);
		horizontal_bllp_byte = (dsi_params->horizontal_bllp * dsiTmpBufBpp);

		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_HSA_WC.ADDR,
		             ALIGN_TO((horizontal_sync_active_byte), 4));
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_HBP_WC.ADDR,
		             ALIGN_TO((horizontal_backporch_byte), 4));
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_HFP_WC.ADDR,
		             ALIGN_TO((horizontal_frontporch_byte), 4));
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_BLLP_WC.ADDR, ALIGN_TO((horizontal_bllp_byte), 4));
	}
}

void DSI_PHY_CLK_LP_PerLine_config(DISP_MODULE_ENUM module, cmdqRecHandle cmdq,
                                   LCM_DSI_PARAMS *dsi_params)
{
	int i;
	DSI_PHY_TIMCON0_REG timcon0;    /* LPX */
	DSI_PHY_TIMCON2_REG timcon2;    /* CLK_HS_TRAIL, CLK_HS_ZERO */
	DSI_PHY_TIMCON3_REG timcon3;    /* CLK_HS_EXIT, CLK_HS_POST, CLK_HS_PREP */
	DSI_HSA_WC_REG hsa;
	DSI_HBP_WC_REG hbp;
	DSI_HFP_WC_REG hfp, new_hfp;
	DSI_BLLP_WC_REG bllp;
	DSI_PSCTRL_REG ps;
	UINT32 hstx_ckl_wc, new_hstx_ckl_wc;
	UINT32 v_a, v_b, v_c, lane_num;
	LCM_DSI_MODE_CON dsi_mode;

	new_hstx_ckl_wc = 0;

	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		lane_num = dsi_params->LANE_NUM;
		dsi_mode = dsi_params->mode;

		if (dsi_mode == CMD_MODE) {
			continue;
		}
		/* vdo mode */
		DSI_OUTREG32(cmdq, &hsa.ADDR, AS_UINT32(&DSI_REG[i]->DSI_HSA_WC.ADDR));
		DSI_OUTREG32(cmdq, &hbp.ADDR, AS_UINT32(&DSI_REG[i]->DSI_HBP_WC.ADDR));
		DSI_OUTREG32(cmdq, &hfp.ADDR, AS_UINT32(&DSI_REG[i]->DSI_HFP_WC.ADDR));
		DSI_OUTREG32(cmdq, &bllp.ADDR, AS_UINT32(&DSI_REG[i]->DSI_BLLP_WC.ADDR));
		DSI_OUTREG32(cmdq, &ps.ADDR, AS_UINT32(&DSI_REG[i]->DSI_PSCTRL.ADDR));
		DSI_OUTREG32(cmdq, &hstx_ckl_wc, AS_UINT32(&DSI_REG[i]->DSI_HSTX_CKL_WC));
		DSI_OUTREG32(cmdq, &timcon0.ADDR, AS_UINT32(&DSI_REG[i]->DSI_PHY_TIMECON0.ADDR));
		DSI_OUTREG32(cmdq, &timcon2.ADDR, AS_UINT32(&DSI_REG[i]->DSI_PHY_TIMECON2.ADDR));
		DSI_OUTREG32(cmdq, &timcon3.ADDR, AS_UINT32(&DSI_REG[i]->DSI_PHY_TIMECON3.ADDR));

		/* 1. sync_pulse_mode */
		/* Total    WC(A) = HSA_WC + HBP_WC + HFP_WC + PS_WC + 32 */
		/* CLK init WC(B) = (CLK_HS_EXIT + LPX + CLK_HS_PREP + CLK_HS_ZERO)*lane_num */
		/* CLK end  WC(C) = (CLK_HS_POST + CLK_HS_TRAIL)*lane_num */
		/* HSTX_CKLP_WC = A - B */
		/* Limitation: B + C < HFP_WC */
		if (dsi_mode == SYNC_PULSE_VDO_MODE) {
			v_a = hsa.REGS.HSA_WC + hbp.REGS.HBP_WC + hfp.REGS.HFP_WC + ps.REGS.DSI_PS_WC + 32;
			v_b =
			    (timcon3.REGS.CLK_HS_EXIT + timcon0.REGS.LPX + timcon3.REGS.CLK_HS_PRPR +
			     timcon2.REGS.CLK_ZERO) * lane_num;
			v_c = (timcon3.REGS.CLK_HS_POST + timcon2.REGS.CLK_TRAIL) * lane_num;

			DISPCHECK("===>v_a-v_b=0x%x,HSTX_CKLP_WC=0x%x\n", (v_a - v_b), hstx_ckl_wc);
			DISPCHECK("===>v_b+v_c=0x%x,HFP_WC=0x%x\n", (v_b + v_c), AS_UINT32(&hfp.ADDR));
			DISPCHECK("===>Will Reconfig in order to fulfill LP clock lane per line\n");

			DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_HFP_WC.ADDR, (v_b + v_c + DIFF_CLK_LANE_LP));   /* B+C < HFP ,here diff is 0x10; */
			DSI_OUTREG32(cmdq, &new_hfp.ADDR, AS_UINT32(&DSI_REG[i]->DSI_HFP_WC.ADDR));
			v_a = hsa.REGS.HSA_WC + hbp.REGS.HBP_WC + new_hfp.REGS.HFP_WC + ps.REGS.DSI_PS_WC + 32;
			DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_HSTX_CKL_WC, (v_a - v_b));
			DSI_OUTREG32(cmdq, &new_hstx_ckl_wc,
			             AS_UINT32(&DSI_REG[i]->DSI_HSTX_CKL_WC));
			DISPCHECK("===>new HSTX_CKL_WC=0x%x, HFP_WC=0x%x\n", new_hstx_ckl_wc,
			          new_hfp.REGS.HFP_WC);
		}
		/* 2. sync_event_mode */
		/* Total    WC(A) = HBP_WC + HFP_WC + PS_WC + 26 */
		/* CLK init WC(B) = (CLK_HS_EXIT + LPX + CLK_HS_PREP + CLK_HS_ZERO)*lane_num */
		/* CLK end  WC(C) = (CLK_HS_POST + CLK_HS_TRAIL)*lane_num */
		/* HSTX_CKLP_WC = A - B */
		/* Limitation: B + C < HFP_WC */
		else if (dsi_mode == SYNC_EVENT_VDO_MODE) {
			v_a = hbp.REGS.HBP_WC + hfp.REGS.HFP_WC + ps.REGS.DSI_PS_WC + 26;
			v_b =
			    (timcon3.REGS.CLK_HS_EXIT + timcon0.REGS.LPX + timcon3.REGS.CLK_HS_PRPR +
			     timcon2.REGS.CLK_ZERO) * lane_num;
			v_c = (timcon3.REGS.CLK_HS_POST + timcon2.REGS.CLK_TRAIL) * lane_num;

			DISPCHECK("===>v_a-v_b=0x%x,HSTX_CKLP_WC=0x%x\n", (v_a - v_b), hstx_ckl_wc);
			DISPCHECK("===>v_b+v_c=0x%x,HFP_WC=0x%x\n", (v_b + v_c), AS_UINT32(&hfp.ADDR));
			DISPCHECK("===>Will Reconfig in order to fulfill LP clock lane per line\n");

			DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_HFP_WC.ADDR, (v_b + v_c + DIFF_CLK_LANE_LP));   /* B+C < HFP ,here diff is 0x10; */
			DSI_OUTREG32(cmdq, &new_hfp.ADDR, AS_UINT32(&DSI_REG[i]->DSI_HFP_WC.ADDR));
			v_a = hbp.REGS.HBP_WC + new_hfp.REGS.HFP_WC + ps.REGS.DSI_PS_WC + 26;
			DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_HSTX_CKL_WC, (v_a - v_b));
			DSI_OUTREG32(cmdq, &new_hstx_ckl_wc,
			             AS_UINT32(&DSI_REG[i]->DSI_HSTX_CKL_WC));
			DISPCHECK("===>new HSTX_CKL_WC=0x%x, HFP_WC=0x%x\n", new_hstx_ckl_wc,
			          (unsigned int)new_hfp.REGS.HFP_WC);

		}
		/* 3. burst_mode */
		/* Total    WC(A) = HBP_WC + HFP_WC + PS_WC + BLLP_WC + 32 */
		/* CLK init WC(B) = (CLK_HS_EXIT + LPX + CLK_HS_PREP + CLK_HS_ZERO)*lane_num */
		/* CLK end  WC(C) = (CLK_HS_POST + CLK_HS_TRAIL)*lane_num */
		/* HSTX_CKLP_WC = A - B */
		/* Limitation: B + C < HFP_WC */
		else if (dsi_mode == BURST_VDO_MODE) {
			v_a = hbp.REGS.HBP_WC + hfp.REGS.HFP_WC + ps.REGS.DSI_PS_WC + bllp.REGS.BLLP_WC + 32;
			v_b =
			    (timcon3.REGS.CLK_HS_EXIT + timcon0.REGS.LPX + timcon3.REGS.CLK_HS_PRPR +
			     timcon2.REGS.CLK_ZERO) * lane_num;
			v_c = (timcon3.REGS.CLK_HS_POST + timcon2.REGS.CLK_TRAIL) * lane_num;

			DISPCHECK("===>v_a-v_b=0x%x,HSTX_CKLP_WC=0x%x\n", (v_a - v_b), hstx_ckl_wc);
			DISPCHECK("===>v_b+v_c=0x%x,HFP_WC=0x%x\n", (v_b + v_c), AS_UINT32(&hfp.ADDR));
			DISPCHECK("===>Will Reconfig in order to fulfill LP clock lane per line\n");

			DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_HFP_WC.ADDR, (v_b + v_c + DIFF_CLK_LANE_LP));   /* B+C < HFP ,here diff is 0x10; */
			DSI_OUTREG32(cmdq, &new_hfp.ADDR, AS_UINT32(&DSI_REG[i]->DSI_HFP_WC.ADDR));
			v_a = hbp.REGS.HBP_WC + new_hfp.REGS.HFP_WC + ps.REGS.DSI_PS_WC + bllp.REGS.BLLP_WC + 32;
			DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_HSTX_CKL_WC, (v_a - v_b));
			DSI_OUTREG32(cmdq, &new_hstx_ckl_wc,
			             AS_UINT32(&DSI_REG[i]->DSI_HSTX_CKL_WC));
			DISPCHECK("===>new HSTX_CKL_WC=0x%x, HFP_WC=0x%x\n", new_hstx_ckl_wc,
			          (unsigned int)new_hfp.REGS.HFP_WC);
		}
	}

}


int _dsi_ps_type_to_bpp(LCM_PS_TYPE ps)
{
	int bpp = 0;

	switch (ps) {
		case LCM_PACKED_PS_16BIT_RGB565:
			bpp = 2;
			break;
		case LCM_LOOSELY_PS_18BIT_RGB666:
			bpp = 3;
			break;
		case LCM_PACKED_PS_24BIT_RGB888:
			bpp = 3;
			break;
		case LCM_PACKED_PS_18BIT_RGB666:
			bpp = 3;
			break;
		default:
			bpp = 3;
			break;
	}

	return bpp;
}

DSI_STATUS DSI_PS_Control(DISP_MODULE_ENUM module, void *cmdq, LCM_DSI_PARAMS *dsi_params, int w,
                          int h)
{
	int i = 0;
	unsigned int ps_sel_bitvalue = 0;
	/* /TODO: parameter checking */
	ASSERT(dsi_params->PS <= LCM_PACKED_PS_18BIT_RGB666);

	if (dsi_params->PS > LCM_LOOSELY_PS_18BIT_RGB666) {
		ps_sel_bitvalue = (5 - dsi_params->PS);
	} else {
		ps_sel_bitvalue = dsi_params->PS;
	}
#if 0
	if (module == DISP_MODULE_DSIDUAL) {
		w = w / 2;
	}
#endif
	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		DSI_OUTREGBIT(cmdq, DSI_VACT_NL_REG, DSI_REG[i]->DSI_VACT_NL.ADDR, REGS.VACT_NL, h);
		if (dsi_params->ufoe_enable && dsi_params->ufoe_params.lr_mode_en != 1) {
			if (dsi_params->ufoe_params.compress_ratio == 3) {  /* 1/3 */
				unsigned int ufoe_internal_width = w + w % 4;
				if (ufoe_internal_width % 3 == 0) {
					DSI_OUTREGBIT(cmdq, DSI_PSCTRL_REG, DSI_REG[i]->DSI_PSCTRL.ADDR,
					              REGS.DSI_PS_WC,
					              (ufoe_internal_width / 3) *
					              _dsi_ps_type_to_bpp(dsi_params->PS));
				} else {
					unsigned int temp_w = ufoe_internal_width / 3 + 1;
					temp_w = ((temp_w % 2) == 1) ? (temp_w + 1) : temp_w;
					DSI_OUTREGBIT(cmdq, DSI_PSCTRL_REG, DSI_REG[i]->DSI_PSCTRL.ADDR,
					              REGS.DSI_PS_WC,
					              temp_w * _dsi_ps_type_to_bpp(dsi_params->PS));
				}
			} else  /* 1/2 */
				DSI_OUTREGBIT(cmdq, DSI_PSCTRL_REG, DSI_REG[i]->DSI_PSCTRL.ADDR,
				              REGS.DSI_PS_WC,
				              (w +
				               w % 4) / 2 * _dsi_ps_type_to_bpp(dsi_params->PS));
		} else {
			DSI_OUTREGBIT(cmdq, DSI_PSCTRL_REG, DSI_REG[i]->DSI_PSCTRL.ADDR, REGS.DSI_PS_WC,
			              w * _dsi_ps_type_to_bpp(dsi_params->PS));
		}


		DSI_OUTREGBIT(cmdq, DSI_PSCTRL_REG, DSI_REG[i]->DSI_PSCTRL.ADDR, REGS.DSI_PS_SEL,
		              ps_sel_bitvalue);
	}

	return DSI_STATUS_OK;
}

DSI_STATUS DSI_TXRX_Control(DISP_MODULE_ENUM module, void *cmdq, LCM_DSI_PARAMS *dsi_params)
{
	int i = 0;
	unsigned int lane_num_bitvalue = 0;
	int lane_num = dsi_params->LANE_NUM;
	int vc_num = 0;
	bool null_packet_en = FALSE;
#if 0//change by jst for riot hdmi debug 20170912
	bool dis_eotp_en = FALSE;
#else
	bool dis_eotp_en = TRUE;
#endif
	bool hstx_cklp_en = FALSE;
	int max_return_size = 0;

	switch (lane_num) {
		case LCM_ONE_LANE:
			lane_num_bitvalue = 0x1;
			break;
		case LCM_TWO_LANE:
			lane_num_bitvalue = 0x3;
			break;
		case LCM_THREE_LANE:
			lane_num_bitvalue = 0x7;
			break;
		case LCM_FOUR_LANE:
			lane_num_bitvalue = 0xF;
			break;
	}

	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		DSI_OUTREGBIT(cmdq, DSI_TXRX_CTRL_REG, DSI_REG[i]->DSI_TXRX_CTRL.ADDR,
		              REGS.VC_NUM, vc_num);
		DSI_OUTREGBIT(cmdq, DSI_TXRX_CTRL_REG, DSI_REG[i]->DSI_TXRX_CTRL.ADDR,
		              REGS.DIS_EOT, dis_eotp_en);
		DSI_OUTREGBIT(cmdq, DSI_TXRX_CTRL_REG, DSI_REG[i]->DSI_TXRX_CTRL.ADDR,
		              REGS.BLLP_EN, null_packet_en);
		DSI_OUTREGBIT(cmdq, DSI_TXRX_CTRL_REG, DSI_REG[i]->DSI_TXRX_CTRL.ADDR,
		              REGS.MAX_RTN_SIZE, max_return_size);
		DSI_OUTREGBIT(cmdq, DSI_TXRX_CTRL_REG, DSI_REG[i]->DSI_TXRX_CTRL.ADDR,
		              REGS.HSTX_CKLP_EN, hstx_cklp_en);
		DSI_OUTREGBIT(cmdq, DSI_TXRX_CTRL_REG, DSI_REG[i]->DSI_TXRX_CTRL.ADDR,
		              REGS.LANE_NUM, lane_num_bitvalue);
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_MEM_CONTI.ADDR, DSI_WMEM_CONTI_ID);
		if (CMD_MODE == dsi_params->mode) {
			if (dsi_params->ext_te_edge == LCM_POLARITY_FALLING) {
				/*use ext te falling edge*/
				DSI_OUTREGBIT(cmdq, DSI_TXRX_CTRL_REG, DSI_REG[i]->DSI_TXRX_CTRL.ADDR, REGS.EXT_TE_EDGE, 1);
			}
			DSI_OUTREGBIT(cmdq, DSI_TXRX_CTRL_REG,DSI_REG[i]->DSI_TXRX_CTRL.ADDR,REGS.EXT_TE_EN,      1);
		}
	}

	return DSI_STATUS_OK;
}

void DSI_PHY_clk_setting(DISP_MODULE_ENUM module, void *cmdq, LCM_DSI_PARAMS *dsi_params)
{
	DISPFUNC();
#ifdef MACH_FPGA
#if 0
	MIPITX_OUTREG32(0x10215044, 0x88492483);
	MIPITX_OUTREG32(0x10215040, 0x00000002);
	mdelay(10);
	MIPITX_OUTREG32(0x10215000, 0x00000403);
	MIPITX_OUTREG32(0x10215068, 0x00000003);
	MIPITX_OUTREG32(0x10215068, 0x00000001);

	mdelay(10);
	MIPITX_OUTREG32(0x10215050, 0x00000000);
	mdelay(10);
	MIPITX_OUTREG32(0x10215054, 0x00000003);
	MIPITX_OUTREG32(0x10215058, 0x60000000);
	MIPITX_OUTREG32(0x1021505c, 0x00000000);

	MIPITX_OUTREG32(0x10215004, 0x00000803);
	MIPITX_OUTREG32(0x10215008, 0x00000801);
	MIPITX_OUTREG32(0x1021500c, 0x00000801);
	MIPITX_OUTREG32(0x10215010, 0x00000801);
	MIPITX_OUTREG32(0x10215014, 0x00000801);

	MIPITX_OUTREG32(0x10215050, 0x00000001);

	mdelay(10);


	MIPITX_OUTREG32(0x10215064, 0x00000020);
	return 0;
	/* mipitx1 */

	MIPITX_OUTREG32(0x10216044, 0x88492483);
	MIPITX_OUTREG32(0x10216040, 0x00000002);
	mdelay(10);
	MIPITX_OUTREG32(0x10216000, 0x00000403);
	MIPITX_OUTREG32(0x10216068, 0x00000003);
	MIPITX_OUTREG32(0x10216068, 0x00000001);

	mdelay(10);
	MIPITX_OUTREG32(0x10216050, 0x00000000);
	mdelay(10);
	MIPITX_OUTREG32(0x10216054, 0x00000003);
	MIPITX_OUTREG32(0x10216058, 0x40000000);
	MIPITX_OUTREG32(0x1021605c, 0x00000000);

	MIPITX_OUTREG32(0x10216004, 0x00000803);
	MIPITX_OUTREG32(0x10216008, 0x00000801);
	MIPITX_OUTREG32(0x1021600c, 0x00000801);
	MIPITX_OUTREG32(0x10216010, 0x00000801);
	MIPITX_OUTREG32(0x10216014, 0x00000801);

	MIPITX_OUTREG32(0x10216050, 0x00000001);

	mdelay(10);


	MIPITX_OUTREG32(0x10216064, 0x00000020);
	return 0;
#endif
#else

	int i = 0;
	unsigned int data_Rate = dsi_params->PLL_CLOCK * 2;
	unsigned int txdiv = 0;
	unsigned int txdiv0 = 0;
	unsigned int txdiv1 = 0;
	unsigned int pcw = 0;
	/* unsigned int fmod = 30;//Fmod = 30KHz by default */
	unsigned int delta1 = 5;    /* Delta1 is SSC range, default is 0%~-5% */
	unsigned int pdelta1 = 0;
#if 0
	u32 m_hw_res3 = 0;
	u32 temp1 = 0;
	u32 temp2 = 0;
	u32 temp3 = 0;
	u32 temp4 = 0;
	u32 temp5 = 0;
	u32 lnt = 0;
#endif

	/* temp1~5 is used for impedence calibration, not enable now */
#if 0
	m_hw_res3 = INREG32(0xF0206180);
	temp1 = (m_hw_res3 >> 28) & 0xF;
	temp2 = (m_hw_res3 >> 24) & 0xF;
	temp3 = (m_hw_res3 >> 20) & 0xF;
	temp4 = (m_hw_res3 >> 16) & 0xF;
	temp5 = (m_hw_res3 >> 12) & 0xF;
#endif

	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		/* step 1 */
		/* MIPITX_MASKREG32(APMIXED_BASE+0x00, (0x1<<6), 1); */

		/* step 2 */
		MIPITX_OUTREGBIT(MIPITX_DSI_BG_CON_REG, DSI_PHY_REG[i]->MIPITX_DSI_BG_CON.ADDR,
		                 REGS.RG_DSI_BG_CORE_EN, 1);
		MIPITX_OUTREGBIT(MIPITX_DSI_BG_CON_REG, DSI_PHY_REG[i]->MIPITX_DSI_BG_CON.ADDR,
		                 REGS.RG_DSI_BG_CKEN, 1);

		/* step 3 */
		udelay(30);

		/* step 4 */
		MIPITX_OUTREGBIT(MIPITX_DSI_TOP_CON_REG, DSI_PHY_REG[i]->MIPITX_DSI_TOP_CON.ADDR,
		                 REGS.RG_DSI_LNT_HS_BIAS_EN, 1);

		/* step 5 */
		MIPITX_OUTREGBIT(MIPITX_DSI_CON_REG, DSI_PHY_REG[i]->MIPITX_DSI_CON.ADDR,
		                 REGS.RG_DSI_CKG_LDOOUT_EN, 1);
		MIPITX_OUTREGBIT(MIPITX_DSI_CON_REG, DSI_PHY_REG[i]->MIPITX_DSI_CON.ADDR,
		                 REGS.RG_DSI_LDOCORE_EN, 1);

		/* step 6 */
		MIPITX_OUTREGBIT(MIPITX_DSI_PLL_PWR_REG, DSI_PHY_REG[i]->MIPITX_DSI_PLL_PWR.ADDR,
		                 REGS.DA_DSI_MPPLL_SDM_PWR_ON, 1);

		/* step 7 */
		MIPITX_OUTREGBIT(MIPITX_DSI_PLL_PWR_REG, DSI_PHY_REG[i]->MIPITX_DSI_PLL_PWR.ADDR,
		                 REGS.DA_DSI_MPPLL_SDM_ISO_EN, 0);

		if (0 != data_Rate) {
			if (data_Rate > 1250) {
				DISPCHECK("mipitx Data Rate exceed limitation(%d)\n", data_Rate);
				ASSERT(0);
			} else if (data_Rate >= 500) {
				txdiv = 1;
				txdiv0 = 0;
				txdiv1 = 0;
			} else if (data_Rate >= 250) {
				txdiv = 2;
				txdiv0 = 1;
				txdiv1 = 0;
			} else if (data_Rate >= 125) {
				txdiv = 4;
				txdiv0 = 2;
				txdiv1 = 0;
			} else if (data_Rate > 62) {
				txdiv = 8;
				txdiv0 = 2;
				txdiv1 = 1;
			} else if (data_Rate >= 50) {
				txdiv = 16;
				txdiv0 = 2;
				txdiv1 = 2;
			} else {
				DISPCHECK("dataRate is too low(%d)\n", data_Rate);
				ASSERT(0);
			}

			/* step 8 */
			MIPITX_OUTREGBIT(MIPITX_DSI_PLL_CON0_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON0.ADDR, REGS.RG_DSI0_MPPLL_TXDIV0,
			                 txdiv0);
			MIPITX_OUTREGBIT(MIPITX_DSI_PLL_CON0_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON0.ADDR, REGS.RG_DSI0_MPPLL_TXDIV1,
			                 txdiv1);
			MIPITX_OUTREGBIT(MIPITX_DSI_PLL_CON0_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON0.ADDR, REGS.RG_DSI0_MPPLL_PREDIV,
			                 0);

			/* step 9 */
			MIPITX_OUTREGBIT(MIPITX_DSI_PLL_CON1_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON1.ADDR,
			                 REGS.RG_DSI0_MPPLL_SDM_FRA_EN, 1);

			/* step 10 */
			/* PLL PCW config */
			/*
			   PCW bit 24~30 = floor(pcw)
			   PCW bit 16~23 = (pcw - floor(pcw))*256
			   PCW bit 8~15 = (pcw*256 - floor(pcw)*256)*256
			   PCW bit 8~15 = (pcw*256*256 - floor(pcw)*256*256)*256
			 */
			/* pcw = data_Rate*4*txdiv/(26*2);//Post DIV =4, so need data_Rate*4 */
			pcw = data_Rate * txdiv / 13;

			MIPITX_OUTREGBIT(MIPITX_DSI_PLL_CON2_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON2.ADDR,
			                 REGS.RG_DSI0_MPPLL_SDM_PCW_H, (pcw & 0x7F));
			MIPITX_OUTREGBIT(MIPITX_DSI_PLL_CON2_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON2.ADDR,
			                 REGS.RG_DSI0_MPPLL_SDM_PCW_16_23,
			                 ((256 * (data_Rate * txdiv % 13) / 13) & 0xFF));
			MIPITX_OUTREGBIT(MIPITX_DSI_PLL_CON2_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON2.ADDR,
			                 REGS.RG_DSI0_MPPLL_SDM_PCW_8_15,
			                 ((256 * (256 * (data_Rate * txdiv % 13) % 13) /
			                   13) & 0xFF));
			MIPITX_OUTREGBIT(MIPITX_DSI_PLL_CON2_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON2.ADDR,
			                 REGS.RG_DSI0_MPPLL_SDM_PCW_0_7,
			                 ((256 *
			                   (256 * (256 * (data_Rate * txdiv % 13) % 13) % 13) /
			                   13) & 0xFF));

			if (1 != dsi_params->ssc_disable) {
				MIPITX_OUTREGBIT(MIPITX_DSI_PLL_CON1_REG,
				                 DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON1.ADDR,
				                 REGS.RG_DSI0_MPPLL_SDM_SSC_PH_INIT, 1);
				MIPITX_OUTREGBIT(MIPITX_DSI_PLL_CON1_REG, DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON1.ADDR, REGS.RG_DSI0_MPPLL_SDM_SSC_PRD, 0x1B1); /* PRD=ROUND(pmod) = 433; */
				if (0 != dsi_params->ssc_range) {
					delta1 = dsi_params->ssc_range;
				}
				ASSERT(delta1 <= 8);
				pdelta1 = (delta1 * data_Rate * txdiv * 262144 + 281664) / 563329;
				MIPITX_OUTREGBIT(MIPITX_DSI_PLL_CON3_REG,
				                 DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON3.ADDR,
				                 REGS.RG_DSI0_MPPLL_SDM_SSC_DELTA, pdelta1);
				MIPITX_OUTREGBIT(MIPITX_DSI_PLL_CON3_REG,
				                 DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON3.ADDR,
				                 REGS.RG_DSI0_MPPLL_SDM_SSC_DELTA1, pdelta1);
				/* DSI_OUTREGBIT(MIPITX_DSI_PLL_CON1_REG,DSI_PHY_REG->MIPITX_DSI_PLL_CON1.ADDR,REGS.RG_DSI0_MPPLL_SDM_FRA_EN,1); */
				DISPMSG
				("[dsi_drv.c] PLL config:data_rate=%d,txdiv=%d,pcw=%d,delta1=%d,pdelta1=0x%x\n",
				 data_Rate, txdiv,
				 DSI_INREG32(PMIPITX_DSI_PLL_CON2_REG, &DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON2.ADDR),
				 delta1, pdelta1);
			}
		} else {
			DISPERR("[dsi_dsi.c] PLL clock should not be 0!!!\n");
			ASSERT(0);
		}

		if ((0 != data_Rate) && (1 != dsi_params->ssc_disable)) {
			MIPITX_OUTREGBIT(MIPITX_DSI_PLL_CON1_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON1.ADDR,
			                 REGS.RG_DSI0_MPPLL_SDM_SSC_EN, 1);
		} else {
			MIPITX_OUTREGBIT(MIPITX_DSI_PLL_CON1_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON1.ADDR,
			                 REGS.RG_DSI0_MPPLL_SDM_SSC_EN, 0);
		}

		/* step 11 */
		MIPITX_OUTREGBIT(MIPITX_DSI_CLOCK_LANE_REG, DSI_PHY_REG[i]->MIPITX_DSI_CLOCK_LANE.ADDR,
		                 REGS.RG_DSI_LNTC_LDOOUT_EN, 1);

		/* step 12 */
		if (dsi_params->LANE_NUM > 0) {
			MIPITX_OUTREGBIT(MIPITX_DSI_DATA_LANE0_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_DATA_LANE0.ADDR,
			                 REGS.RG_DSI_LNT0_LDOOUT_EN, 1);
		}
		/* step 13 */
		if (dsi_params->LANE_NUM > 1) {
			MIPITX_OUTREGBIT(MIPITX_DSI_DATA_LANE1_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_DATA_LANE1.ADDR,
			                 REGS.RG_DSI_LNT1_LDOOUT_EN, 1);
		}
		/* step 14 */
		if (dsi_params->LANE_NUM > 2) {
			MIPITX_OUTREGBIT(MIPITX_DSI_DATA_LANE2_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_DATA_LANE2.ADDR,
			                 REGS.RG_DSI_LNT2_LDOOUT_EN, 1);
		}
		/* step 15 */
		if (dsi_params->LANE_NUM > 3) {
			MIPITX_OUTREGBIT(MIPITX_DSI_DATA_LANE3_REG,
			                 DSI_PHY_REG[i]->MIPITX_DSI_DATA_LANE3.ADDR,
			                 REGS.RG_DSI_LNT3_LDOOUT_EN, 1);
		}
		/* step 16 */
		MIPITX_OUTREGBIT(MIPITX_DSI_PLL_CON0_REG, DSI_PHY_REG[i]->MIPITX_DSI_PLL_CON0.ADDR,
		                 REGS.RG_DSI0_MPPLL_PLL_EN, 1);

		/* step 17 */
		udelay(20);

		MIPITX_OUTREGBIT(MIPITX_DSI_PLL_CHG_REG, DSI_PHY_REG[i]->MIPITX_DSI_PLL_CHG.ADDR,
		                 REGS.RG_DSI0_MPPLL_SDM_PCW_CHG, 0);
		MIPITX_OUTREGBIT(MIPITX_DSI_PLL_CHG_REG, DSI_PHY_REG[i]->MIPITX_DSI_PLL_CHG.ADDR,
		                 REGS.RG_DSI0_MPPLL_SDM_PCW_CHG, 1);

		/* step 18 */
		MIPITX_OUTREGBIT(MIPITX_DSI_TOP_CON_REG, DSI_PHY_REG[i]->MIPITX_DSI_TOP_CON.ADDR,
		                 REGS.RG_DSI_PAD_TIE_LOW_EN, 0);

		/* DONT_KNOW_WHY, for dsi 8 lane, it seems that if we wait more here, the 2 dsi port's data will be always right. othersie will have random color wrong issue */
		udelay(200);
	}
#endif
}


void DSI_PHY_TIMCONFIG(DISP_MODULE_ENUM module, void *cmdq, LCM_DSI_PARAMS *dsi_params)
{
	int i = 0;
#if 0
	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_PHY_TIMECON0.ADDR, 0x140f0708);
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_PHY_TIMECON1.ADDR, 0x10280c20);
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_PHY_TIMECON2.ADDR, 0x14280000);
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_PHY_TIMECON3.ADDR, 0x00101a06);
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_PHY_TIMECON4.ADDR, 0x00023000);
	}
	return;
#endif

	DSI_PHY_TIMCON0_REG timcon0;
	DSI_PHY_TIMCON1_REG timcon1;
	DSI_PHY_TIMCON2_REG timcon2;
	DSI_PHY_TIMCON3_REG timcon3;
#if 0
	unsigned int div1 = 0;
	unsigned int div2 = 0;
	unsigned int pre_div = 0;
	unsigned int post_div = 0;
	unsigned int fbk_sel = 0;
	unsigned int fbk_div = 0;
#endif
	unsigned int lane_no = dsi_params->LANE_NUM;

	/* unsigned int div2_real; */
	unsigned int cycle_time;
	unsigned int ui;
	unsigned int hs_trail_m, hs_trail_n;

	if (0 != dsi_params->PLL_CLOCK) {
		ui = 1000 / (dsi_params->PLL_CLOCK * 2) + 0x01;
		cycle_time = 8000 / (dsi_params->PLL_CLOCK * 2) + 0x01;
		DISP_LOG_PRINT(ANDROID_LOG_INFO, "DSI",
		               "[DISP] - kernel - DSI_PHY_TIMCONFIG, Cycle Time = %d(ns), Unit Interval = %d(ns). , lane# = %d\n",
		               cycle_time, ui, lane_no);
	} else {
		DISPERR("[dsi_dsi.c] PLL clock should not be 0!!!\n");
		ASSERT(0);
	}

	/* div2_real=div2 ? div2*0x02 : 0x1; */
	/* cycle_time = (1000 * div2 * div1 * pre_div * post_div)/ (fbk_sel * (fbk_div+0x01) * 26) + 1; */
	/* ui = (1000 * div2 * div1 * pre_div * post_div)/ (fbk_sel * (fbk_div+0x01) * 26 * 2) + 1; */
#define NS_TO_CYCLE(n, c)   ((n) / (c))

	hs_trail_m = 1;
	hs_trail_n =
	    (dsi_params->HS_TRAIL == 0) ? NS_TO_CYCLE(((hs_trail_m * 0x4 * ui) + 0x50),
	            cycle_time) : dsi_params->HS_TRAIL;
	/* +3 is recommended from designer becauase of HW latency */
	timcon0.REGS.HS_TRAIL = (hs_trail_m > hs_trail_n) ? hs_trail_m : hs_trail_n;

	timcon0.REGS.HS_PRPR =
	    (dsi_params->HS_PRPR == 0) ? NS_TO_CYCLE((0x40 + 0x5 * ui),
	            cycle_time) : dsi_params->HS_PRPR;
	/* HS_PRPR can't be 1. */
	if (timcon0.REGS.HS_PRPR < 1)
		timcon0.REGS.HS_PRPR = 1;

	timcon0.REGS.HS_ZERO =
	    (dsi_params->HS_ZERO == 0) ? NS_TO_CYCLE((0xC8 + 0x0a * ui),
	            cycle_time) : dsi_params->HS_ZERO;
	if (timcon0.REGS.HS_ZERO > timcon0.REGS.HS_PRPR)
		timcon0.REGS.HS_ZERO -= timcon0.REGS.HS_PRPR;

	timcon0.REGS.LPX = (dsi_params->LPX == 0) ? NS_TO_CYCLE(0x50, cycle_time) : dsi_params->LPX;
	if (timcon0.REGS.LPX < 1)
		timcon0.REGS.LPX = 1;

	/* timcon1.TA_SACK         = (dsi_params->TA_SACK == 0) ? 1 : dsi_params->TA_SACK; */
	timcon1.REGS.TA_GET = (dsi_params->TA_GET == 0) ? (0x5 * timcon0.REGS.LPX) : dsi_params->TA_GET;
	timcon1.REGS.TA_SURE =
	    (dsi_params->TA_SURE == 0) ? (0x3 * timcon0.REGS.LPX / 0x2) : dsi_params->TA_SURE;
	timcon1.REGS.TA_GO = (dsi_params->TA_GO == 0) ? (0x4 * timcon0.REGS.LPX) : dsi_params->TA_GO;
	/* -------------------------------------------------------------- */
	/* NT35510 need fine tune timing */
	/* Data_hs_exit = 60 ns + 128UI */
	/* Clk_post = 60 ns + 128 UI. */
	/* -------------------------------------------------------------- */
	timcon1.REGS.DA_HS_EXIT =
	    (dsi_params->DA_HS_EXIT == 0) ? (0x2 * timcon0.REGS.LPX) : dsi_params->DA_HS_EXIT;

	timcon2.REGS.CLK_TRAIL =
	    ((dsi_params->CLK_TRAIL == 0) ? NS_TO_CYCLE(0x60,
	            cycle_time) : dsi_params->CLK_TRAIL) + 0x01;
	/* CLK_TRAIL can't be 1. */
	if (timcon2.REGS.CLK_TRAIL < 2)
		timcon2.REGS.CLK_TRAIL = 2;

	/* timcon2.LPX_WAIT        = (dsi_params->LPX_WAIT == 0) ? 1 : dsi_params->LPX_WAIT; */
	timcon2.REGS.CONT_DET = dsi_params->CONT_DET;
	timcon2.REGS.CLK_ZERO =
	    (dsi_params->CLK_ZERO == 0) ? NS_TO_CYCLE(0x190, cycle_time) : dsi_params->CLK_ZERO;

	timcon3.REGS.CLK_HS_PRPR =
	    (dsi_params->CLK_HS_PRPR == 0) ? NS_TO_CYCLE(0x40,
	            cycle_time) : dsi_params->CLK_HS_PRPR;
	if (timcon3.REGS.CLK_HS_PRPR < 1)
		timcon3.REGS.CLK_HS_PRPR = 1;
	timcon3.REGS.CLK_HS_EXIT =
	    (dsi_params->CLK_HS_EXIT == 0) ? (0x2 * timcon0.REGS.LPX) : dsi_params->CLK_HS_EXIT;
	timcon3.REGS.CLK_HS_POST =
	    (dsi_params->CLK_HS_POST == 0) ? NS_TO_CYCLE((0x60 + 0x34 * ui),
	            cycle_time) : dsi_params->CLK_HS_POST;

	DISP_LOG_PRINT(ANDROID_LOG_INFO, "DSI",
	               "[DISP] - kernel - DSI_PHY_TIMCONFIG, HS_TRAIL = %d, HS_ZERO = %d, HS_PRPR = %d, LPX = %d, TA_GET = %d, TA_SURE = %d, TA_GO = %d, CLK_TRAIL = %d, CLK_ZERO = %d, CLK_HS_PRPR = %d\n",
	               (unsigned int)timcon0.REGS.HS_TRAIL, (unsigned int)timcon0.REGS.HS_ZERO,
	               (unsigned int)timcon0.REGS.HS_PRPR, (unsigned int)timcon0.REGS.LPX,
	               (unsigned int)timcon1.REGS.TA_GET, (unsigned int)timcon1.REGS.TA_SURE,
	               (unsigned int)timcon1.REGS.TA_GO, (unsigned int)timcon2.REGS.CLK_TRAIL,
	               (unsigned int)timcon2.REGS.CLK_ZERO, (unsigned int)timcon3.REGS.CLK_HS_PRPR);

	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		DSI_OUTREGBIT(cmdq, DSI_PHY_TIMCON0_REG, DSI_REG[i]->DSI_PHY_TIMECON0.ADDR, REGS.LPX,
		              timcon0.REGS.LPX);
		DSI_OUTREGBIT(cmdq, DSI_PHY_TIMCON0_REG, DSI_REG[i]->DSI_PHY_TIMECON0.ADDR, REGS.HS_PRPR,
		              timcon0.REGS.HS_PRPR);
		DSI_OUTREGBIT(cmdq, DSI_PHY_TIMCON0_REG, DSI_REG[i]->DSI_PHY_TIMECON0.ADDR, REGS.HS_ZERO,
		              timcon0.REGS.HS_ZERO);
		DSI_OUTREGBIT(cmdq, DSI_PHY_TIMCON0_REG, DSI_REG[i]->DSI_PHY_TIMECON0.ADDR, REGS.HS_TRAIL,
		              timcon0.REGS.HS_TRAIL);

		DSI_OUTREGBIT(cmdq, DSI_PHY_TIMCON1_REG, DSI_REG[i]->DSI_PHY_TIMECON1.ADDR, REGS.TA_GO,
		              timcon1.REGS.TA_GO);
		DSI_OUTREGBIT(cmdq, DSI_PHY_TIMCON1_REG, DSI_REG[i]->DSI_PHY_TIMECON1.ADDR, REGS.TA_SURE,
		              timcon1.REGS.TA_SURE);
		DSI_OUTREGBIT(cmdq, DSI_PHY_TIMCON1_REG, DSI_REG[i]->DSI_PHY_TIMECON1.ADDR, REGS.TA_GET,
		              timcon1.REGS.TA_GET);
		DSI_OUTREGBIT(cmdq, DSI_PHY_TIMCON1_REG, DSI_REG[i]->DSI_PHY_TIMECON1.ADDR, REGS.DA_HS_EXIT,
		              timcon1.REGS.DA_HS_EXIT);

		DSI_OUTREGBIT(cmdq, DSI_PHY_TIMCON2_REG, DSI_REG[i]->DSI_PHY_TIMECON2.ADDR, REGS.CONT_DET,
		              timcon2.REGS.CONT_DET);
		DSI_OUTREGBIT(cmdq, DSI_PHY_TIMCON2_REG, DSI_REG[i]->DSI_PHY_TIMECON2.ADDR, REGS.CLK_ZERO,
		              timcon2.REGS.CLK_ZERO);
		DSI_OUTREGBIT(cmdq, DSI_PHY_TIMCON2_REG, DSI_REG[i]->DSI_PHY_TIMECON2.ADDR, REGS.CLK_TRAIL,
		              timcon2.REGS.CLK_TRAIL);

		DSI_OUTREGBIT(cmdq, DSI_PHY_TIMCON3_REG, DSI_REG[i]->DSI_PHY_TIMECON3.ADDR, REGS.CLK_HS_PRPR,
		              timcon3.REGS.CLK_HS_PRPR);
		DSI_OUTREGBIT(cmdq, DSI_PHY_TIMCON3_REG, DSI_REG[i]->DSI_PHY_TIMECON3.ADDR, REGS.CLK_HS_POST,
		              timcon3.REGS.CLK_HS_POST);
		DSI_OUTREGBIT(cmdq, DSI_PHY_TIMCON3_REG, DSI_REG[i]->DSI_PHY_TIMECON3.ADDR, REGS.CLK_HS_EXIT,
		              timcon3.REGS.CLK_HS_EXIT);
		dprintf(INFO, "%s, 0x%08x,0x%08x,0x%08x,0x%08x\n", __func__,
		        INREG32(DSI0_BASE + 0x110), INREG32(DSI0_BASE + 0x114),
		        INREG32(DSI0_BASE + 0x118), INREG32(DSI0_BASE + 0x11c));
	}
}

DSI_STATUS DSI_Start(DISP_MODULE_ENUM module, void *cmdq)
{
	int i = 0;
	if (module != DISP_MODULE_DSIDUAL) {
		for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
			DSI_OUTREGBIT(cmdq, DSI_START_REG, DSI_REG[i]->DSI_START.ADDR, REGS.DSI_START, 0);
			DSI_OUTREGBIT(cmdq, DSI_START_REG, DSI_REG[i]->DSI_START.ADDR, REGS.DSI_START, 1);
		}
	}
#if 0
	else {
		/* TODO: do we need this? */
		DSI_OUTREGBIT(cmdq, DSI_START_REG, DSI_REG[0]->DSI_START.ADDR, REGS.DSI_START, 0);
		DSI_OUTREGBIT(cmdq, DSI_START_REG, DSI_REG[0]->DSI_START.ADDR, REGS.DSI_START, 1);
	}
#endif
	return DSI_STATUS_OK;
}

DSI_STATUS DSI_EnableVM_CMD(DISP_MODULE_ENUM module, void *cmdq)
{
	int i = 0;
	if (module != DISP_MODULE_DSIDUAL) {
		for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
			DSI_OUTREGBIT(cmdq, DSI_START_REG, DSI_REG[i]->DSI_START.ADDR, REGS.VM_CMD_START, 0);
			DSI_OUTREGBIT(cmdq, DSI_START_REG, DSI_REG[i]->DSI_START.ADDR, REGS.VM_CMD_START, 1);
		}
	}
#if 0
	else {
		DSI_OUTREGBIT(cmdq, DSI_START_REG, DSI_REG[0]->DSI_START.ADDR, REGS.VM_CMD_START, 0);
		DSI_OUTREGBIT(cmdq, DSI_START_REG, DSI_REG[0]->DSI_START.ADDR, REGS.VM_CMD_START, 1);
	}
#endif
	return DSI_STATUS_OK;
}


/* / return value: the data length we got */
UINT32 DSI_dcs_read_lcm_reg_v2(DISP_MODULE_ENUM module, void *cmdq, UINT8 cmd, UINT8 *buffer,
                               UINT8 buffer_size)
{
	int d = 0;
	UINT32 max_try_count = 5;
	UINT32 recv_data_cnt;
	unsigned int read_timeout_ms;
	unsigned char packet_type;
	DSI_RX_DATA_REG read_data0;
	DSI_RX_DATA_REG read_data1;
	DSI_RX_DATA_REG read_data2;
	DSI_RX_DATA_REG read_data3;
	DSI_T0_INS t0 = {{0}};
	DISPFUNC();
#if ENABLE_DSI_INTERRUPT
	static const long WAIT_TIMEOUT = 2 * HZ;    /* 2 sec */
	long ret;
#endif

	for (d = DSI_MODULE_BEGIN(module); d <= DSI_MODULE_END(module); d++) {
		if (DSI_REG[d]->DSI_MODE_CTRL.REGS.MODE)
			return 0;

		if (buffer == NULL || buffer_size == 0)
			return 0;

		do {
			if (max_try_count == 0)
				return 0;
			max_try_count--;
			recv_data_cnt = 0;
			read_timeout_ms = 20;

			DSI_WaitForNotBusy(module, cmdq);

			t0.HDR.CONFG = 0x04;    /* /BTA */
			t0.HDR.Data0 = cmd;
			if (buffer_size < 0x3)
				t0.HDR.Data_ID = DSI_DCS_READ_PACKET_ID;
			else
				t0.HDR.Data_ID = DSI_GERNERIC_READ_LONG_PACKET_ID;
			t0.HDR.Data1 = 0;

			DSI_OUTREG32(cmdq, &DSI_CMDQ_REG[d]->data[0].ADDR, AS_UINT32(&t0.ADDR));
			DSI_OUTREG32(cmdq, &DSI_REG[d]->DSI_CMDQ_SIZE.ADDR, 1);

			/* /clear read ACK */
			/* DSI_REG->DSI_RACK.REGS.DSI_RACK = 1; */
			/* DSI_REG->DSI_INTSTA.REGS.RD_RDY = 1; */
			/* DSI_REG->DSI_INTSTA.REGS.CMD_DONE = 1; */
			/* DSI_REG->DSI_INTEN.REGS.RD_RDY =  1; */
			/* DSI_REG->DSI_INTEN.REGS.CMD_DONE=  1; */
			DSI_OUTREGBIT(cmdq, DSI_RACK_REG, DSI_REG[d]->DSI_RACK.ADDR, REGS.DSI_RACK, 1);
			DSI_OUTREGBIT(cmdq, DSI_INT_STATUS_REG, DSI_REG[d]->DSI_INTSTA.ADDR, REGS.RD_RDY, 0);
			DSI_OUTREGBIT(cmdq, DSI_INT_STATUS_REG, DSI_REG[d]->DSI_INTSTA.ADDR, REGS.CMD_DONE, 0);
			/* DSI_OUTREGBIT(cmdq, DSI_INT_ENABLE_REG,DSI_REG[d]->DSI_INTEN.ADDR,REGS.RD_RDY,1); */
			/* DSI_OUTREGBIT(cmdq, DSI_INT_ENABLE_REG,DSI_REG[d]->DSI_INTEN.ADDR,REGS.CMD_DONE,1); */



			DSI_OUTREG32(cmdq, &DSI_REG[d]->DSI_START.ADDR, 0);
			DSI_OUTREG32(cmdq, &DSI_REG[d]->DSI_START.ADDR, 1);

			/* / the following code is to */
			/* / 1: wait read ready */
			/* / 2: ack read ready */
			/* / 3: wait for CMDQ_DONE */
			/* / 3: read data */
#if ENABLE_DSI_INTERRUPT
			ret =
			    wait_event_interruptible_timeout(_dsi_dcs_read_wait_queue,
			                                     !_IsEngineBusy(), WAIT_TIMEOUT);
			if (0 == ret) {
				xlog_printk(ANDROID_LOG_WARN, "DSI",
				            " Wait for DSI engine read ready timeout!!!\n");

				DSI_DumpRegisters(module, NULL, 2);

				/* /do necessary reset here */
				/* DSI_REG->DSI_RACK.REGS.DSI_RACK = 1; */
				DSI_OUTREGBIT(cmdq, DSI_RACK_REG, DSI_REG[d]->DSI_RACK.ADDR,
				              REGS.DSI_RACK, 1);
				DSI_Reset();
				return 0;
			}
#else

			DISP_LOG_PRINT(ANDROID_LOG_INFO, "DSI",
			               " Start polling DSI read ready!!!\n");
			while (DSI_REG[d]->DSI_INTSTA.REGS.RD_RDY == 0) {   /* /read clear */
				/* /keep polling */
				mdelay(1);
				read_timeout_ms--;

				if (read_timeout_ms == 0) {
					DISP_LOG_PRINT(ANDROID_LOG_INFO, "DSI",
					               " Polling DSI read ready timeout!!!\n");
					DSI_DumpRegisters(module, cmdq, 2);

					/* /do necessary reset here */
					/* DSI_REG->DSI_RACK.REGS.DSI_RACK = 1; */
					DSI_OUTREGBIT(cmdq, DSI_RACK_REG, DSI_REG[d]->DSI_RACK.ADDR,
					              REGS.DSI_RACK, 1);
					DSI_Reset(module, cmdq);
					return 0;
				}
			}

			DISP_LOG_PRINT(ANDROID_LOG_INFO, "DSI", " End polling DSI read ready!!!\n");


			/* DSI_REG->DSI_RACK.REGS.DSI_RACK = 1; */
			DSI_OUTREGBIT(cmdq, DSI_RACK_REG, DSI_REG[d]->DSI_RACK.ADDR, REGS.DSI_RACK, 1);

			/* /clear interrupt status */
			/* DSI_REG->DSI_INTSTA.REGS.RD_RDY = 1; */
			DSI_OUTREGBIT(cmdq, DSI_INT_STATUS_REG, DSI_REG[d]->DSI_INTSTA.ADDR, REGS.RD_RDY, 0);
			/* /STOP DSI */
			DSI_OUTREG32(cmdq, &DSI_REG[d]->DSI_START.ADDR, 0);
#endif

			/* DSI_REG->DSI_INTEN.REGS.RD_RDY =  0; */
			/* DSI_OUTREGBIT(cmdq, DSI_INT_ENABLE_REG,DSI_REG[d]->DSI_INTEN.ADDR,REGS.RD_RDY,1); */

			DSI_OUTREG32(cmdq, &read_data0.ADDR, AS_UINT32(&DSI_REG[d]->DSI_RX_DATA0.ADDR));
			DSI_OUTREG32(cmdq, &read_data1.ADDR, AS_UINT32(&DSI_REG[d]->DSI_RX_DATA1.ADDR));
			DSI_OUTREG32(cmdq, &read_data2.ADDR, AS_UINT32(&DSI_REG[d]->DSI_RX_DATA2.ADDR));
			DSI_OUTREG32(cmdq, &read_data3.ADDR, AS_UINT32(&DSI_REG[d]->DSI_RX_DATA3.ADDR));

			{
				/* DISP_LOG_PRINT(ANDROID_LOG_INFO, "DSI", " DSI_RX_STA : 0x%x\n", AS_UINT32(&DSI_REG[d]->DSI_RX_STA.ADDR)); */
				DISPMSG("DSI_CMDQ_SIZE : 0x%x\n",
				        (unsigned int)DSI_REG[d]->DSI_CMDQ_SIZE.REGS.CMDQ_SIZE);
				DISPMSG("DSI_CMDQ_DATA0 : 0x%x\n", (unsigned int)DSI_CMDQ_REG[d]->data[0].REGS.byte0);
				DISPMSG("DSI_CMDQ_DATA1 : 0x%x\n", (unsigned int)DSI_CMDQ_REG[d]->data[0].REGS.byte1);
				DISPMSG("DSI_CMDQ_DATA2 : 0x%x\n", (unsigned int)DSI_CMDQ_REG[d]->data[0].REGS.byte2);
				DISPMSG("DSI_CMDQ_DATA3 : 0x%x\n", (unsigned int)DSI_CMDQ_REG[d]->data[0].REGS.byte3);
				DISPMSG("DSI_RX_DATA0 : 0x%x\n", AS_UINT32(&DSI_REG[d]->DSI_RX_DATA0.ADDR));
				DISPMSG("DSI_RX_DATA1 : 0x%x\n", AS_UINT32(&DSI_REG[d]->DSI_RX_DATA1.ADDR));
				DISPMSG("DSI_RX_DATA2 : 0x%x\n", AS_UINT32(&DSI_REG[d]->DSI_RX_DATA2.ADDR));
				DISPMSG("DSI_RX_DATA3 : 0x%x\n", AS_UINT32(&DSI_REG[d]->DSI_RX_DATA3.ADDR));

				DISPMSG("read_data0, %x,%x,%x,%x\n",
				        (unsigned int)read_data0.REGS.byte0, (unsigned int)read_data0.REGS.byte1,
				        (unsigned int)read_data0.REGS.byte2, (unsigned int)read_data0.REGS.byte3);
				DISPMSG("read_data1, %x,%x,%x,%x\n",
				        (unsigned int)read_data1.REGS.byte0, (unsigned int)read_data1.REGS.byte1,
				        (unsigned int)read_data1.REGS.byte2, (unsigned int)read_data1.REGS.byte3);
				DISPMSG("read_data2, %x,%x,%x,%x\n",
				        (unsigned int)read_data2.REGS.byte0, (unsigned int)read_data2.REGS.byte1,
				        (unsigned int)read_data2.REGS.byte2, (unsigned int)read_data2.REGS.byte3);
				DISPMSG("read_data3, %x,%x,%x,%x\n",
				        (unsigned int)read_data3.REGS.byte0, (unsigned int)read_data3.REGS.byte1,
				        (unsigned int)read_data3.REGS.byte2, (unsigned int)read_data3.REGS.byte3);
			}

			packet_type = read_data0.REGS.byte0;

			DISP_LOG_PRINT(ANDROID_LOG_INFO, "DSI", " DSI read packet_type is 0x%x\n",
			               packet_type);

			if (packet_type == 0x1A || packet_type == 0x1C) {
				recv_data_cnt = read_data0.REGS.byte1 + read_data0.REGS.byte2 * 16;
				if (recv_data_cnt > 10) {
					DISP_LOG_PRINT(ANDROID_LOG_WARN, "DSI",
					               " DSI read long packet data  exceeds 4 bytes\n");
					recv_data_cnt = 10;
				}

				if (recv_data_cnt > buffer_size) {
					DISP_LOG_PRINT(ANDROID_LOG_WARN, "DSI",
					               " DSI read long packet data  exceeds buffer size: %d\n",
					               buffer_size);
					recv_data_cnt = buffer_size;
				}
				DISP_LOG_PRINT(ANDROID_LOG_WARN, "DSI",
				               " DSI read long packet size: %d\n", recv_data_cnt);
				if (recv_data_cnt <= 4) {
					memcpy((void *)buffer, (void *)&read_data1.ADDR,
					       recv_data_cnt);
				} else if (recv_data_cnt <= 8) {
					memcpy((void *)buffer, (void *)&read_data1.ADDR, 4);
					memcpy((void *)((uint8_t *) buffer + 4),
					       (void *)&read_data2.ADDR, recv_data_cnt - 4);
				} else {/* recv_data_cnt>8 && recv_data_cnt<=10 */
					memcpy((void *)buffer, (void *)&read_data1.ADDR, 4);
					memcpy((void *)((uint8_t *) buffer + 4),
					       (void *)&read_data2.ADDR, 4);
					memcpy((void *)((uint8_t *) buffer + 8),
					       (void *)&read_data3.ADDR, recv_data_cnt - 8);
				}
			} else {
				recv_data_cnt = 2;
				if (recv_data_cnt > buffer_size) {
					DISP_LOG_PRINT(ANDROID_LOG_WARN, "DSI",
					               " DSI read short packet data  exceeds buffer size: %d\n",
					               buffer_size);
					recv_data_cnt = buffer_size;
				}
				memcpy((void *)buffer, (void *)&read_data0.REGS.byte1, recv_data_cnt);
			}
		} while (packet_type != 0x1C && packet_type != 0x21 && packet_type != 0x22
		         && packet_type != 0x1A);
		/* / here: we may receive a ACK packet which packet type is 0x02 (incdicates some error happened) */
		/* / therefore we try re-read again until no ACK packet */
		/* / But: if it is a good way to keep re-trying ??? */
	}

	return recv_data_cnt;
}


void DSI_set_null(DISP_MODULE_ENUM module, void *cmdq, unsigned cmd, unsigned char count,
                  unsigned char *para_list, unsigned char force_update)
{
	UINT32 i = 0;
	int d = 0;
	UINT32 goto_addr, mask_para, set_para;
	DSI_T2_INS t2 = {{0}};

	/* DISPFUNC(); */
	for (d = DSI_MODULE_BEGIN(module); d <= DSI_MODULE_END(module); d++) {
		if (0 != DSI_REG[d]->DSI_MODE_CTRL.REGS.MODE) { /* not in cmd mode */
		} else {
			DSI_WaitForNotBusy(module, cmdq);

			/* null packet */
			t2.HDR.CONFG = 2;
			t2.HDR.Data_ID = DSI_NULL_PACKET_ID;
			t2.HDR.WC16 = count;

			DSI_OUTREG32(cmdq, &DSI_CMDQ_REG[d]->data[0].ADDR, AS_UINT32(&t2.ADDR));
			DISPMSG("[DSI] start: 0x%08x\n", AS_UINT32(&DSI_CMDQ_REG[d]->data[0].ADDR));

			for (i = 0; i < count; i++) {
				goto_addr = (UINT32) (&DSI_CMDQ_REG[d]->data[1].REGS.byte0) + i;
				mask_para = (0xFF << ((goto_addr & 0x3) * 8));
				set_para = (para_list[i] << ((goto_addr & 0x3) * 8));
				DSI_MASKREG32(cmdq, goto_addr & (~0x3), mask_para, set_para);

				if ((i & 0x3) == 0x3)
					DISPMSG("[DSI] cmd: 0x%08x\n",
					        AS_UINT32(&DSI_CMDQ_REG[d]->data[1 + (i / 4)].ADDR));
			}

			DSI_OUTREG32(cmdq, &DSI_REG[d]->DSI_CMDQ_SIZE.ADDR, 1 + (count) / 4);
			DISPMSG("[DSI] size: 0x%08x\n", AS_UINT32(&DSI_REG[d]->DSI_CMDQ_SIZE.ADDR));

			if (force_update) {
				DSI_Start(module, cmdq);
				DSI_WaitForNotBusy(module, cmdq);
			}
		}
	}
}


void DSI_set_cmdq_V2(DISP_MODULE_ENUM module, void *cmdq, unsigned cmd, unsigned char count,
                     unsigned char *para_list, unsigned char force_update)
{
	UINT32 i = 0;
	int d = 0;
	UINT32 goto_addr, mask_para, set_para;
	DSI_T0_INS t0 = {{0}};
	DSI_T2_INS t2 = {{0}};
	/* DISPFUNC(); */
	for (d = DSI_MODULE_BEGIN(module); d <= DSI_MODULE_END(module); d++) {
		if (0 != DSI_REG[d]->DSI_MODE_CTRL.REGS.MODE) { /* not in cmd mode */
			DSI_VM_CMD_CON_REG vm_cmdq;
			memset(&vm_cmdq.ADDR, 0, sizeof(DSI_VM_CMD_CON_REG));

			DSI_READREG32(PDSI_VM_CMD_CON_REG, &vm_cmdq.ADDR, &DSI_REG[d]->DSI_VM_CMD_CON.ADDR);
			if (cmd < 0xB0) {
				if (count > 1) {
					vm_cmdq.REGS.LONG_PKT = 1;
					vm_cmdq.REGS.CM_DATA_ID = DSI_DCS_LONG_PACKET_ID;
					vm_cmdq.REGS.CM_DATA_0 = count + 1;
					DSI_OUTREG32(cmdq, &DSI_REG[d]->DSI_VM_CMD_CON.ADDR,
					             AS_UINT32(&vm_cmdq.ADDR));

					goto_addr = (UINT32) (&DSI_VM_CMD_REG[d]->data[0].REGS.byte0);
					mask_para = (0xFF << ((goto_addr & 0x3) * 8));
					set_para = (cmd << ((goto_addr & 0x3) * 8));
					DSI_MASKREG32(cmdq, goto_addr & (~0x3), mask_para,
					              set_para);

					for (i = 0; i < count; i++) {
						goto_addr =
						    (UINT32) (&DSI_VM_CMD_REG[d]->data[0].REGS.byte1) +
						    i;
						mask_para = (0xFF << ((goto_addr & 0x3) * 8));
						set_para =
						    (para_list[i] << ((goto_addr & 0x3) * 8));
						DSI_MASKREG32(cmdq, goto_addr & (~0x3), mask_para,
						              set_para);
					}
				} else {
					vm_cmdq.REGS.LONG_PKT = 0;
					vm_cmdq.REGS.CM_DATA_0 = cmd;
					if (count) {
						vm_cmdq.REGS.CM_DATA_ID = DSI_DCS_SHORT_PACKET_ID_1;
						vm_cmdq.REGS.CM_DATA_1 = para_list[0];
					} else {
						vm_cmdq.REGS.CM_DATA_ID = DSI_DCS_SHORT_PACKET_ID_0;
						vm_cmdq.REGS.CM_DATA_1 = 0;
					}
					DSI_OUTREG32(cmdq, &DSI_REG[d]->DSI_VM_CMD_CON.ADDR,
					             AS_UINT32(&vm_cmdq.ADDR));
				}
			} else {
				if (count > 1) {
					vm_cmdq.REGS.LONG_PKT = 1;
					vm_cmdq.REGS.CM_DATA_ID = DSI_GERNERIC_LONG_PACKET_ID;
					vm_cmdq.REGS.CM_DATA_0 = count + 1;
					DSI_OUTREG32(cmdq, &DSI_REG[d]->DSI_VM_CMD_CON.ADDR,
					             AS_UINT32(&vm_cmdq.ADDR));

					goto_addr = (UINT32) (&DSI_VM_CMD_REG[d]->data[0].REGS.byte0);
					mask_para = (0xFF << ((goto_addr & 0x3) * 8));
					set_para = (cmd << ((goto_addr & 0x3) * 8));
					DSI_MASKREG32(cmdq, goto_addr & (~0x3), mask_para,
					              set_para);

					for (i = 0; i < count; i++) {
						goto_addr =
						    (UINT32) (&DSI_VM_CMD_REG[d]->data[0].REGS.byte1) +
						    i;
						mask_para = (0xFF << ((goto_addr & 0x3) * 8));
						set_para =
						    (para_list[i] << ((goto_addr & 0x3) * 8));
						DSI_MASKREG32(cmdq, goto_addr & (~0x3), mask_para,
						              set_para);
					}
				} else {
					vm_cmdq.REGS.LONG_PKT = 0;
					vm_cmdq.REGS.CM_DATA_0 = cmd;
					if (count) {
						vm_cmdq.REGS.CM_DATA_ID = DSI_GERNERIC_SHORT_PACKET_ID_2;
						vm_cmdq.REGS.CM_DATA_1 = para_list[0];
					} else {
						vm_cmdq.REGS.CM_DATA_ID = DSI_GERNERIC_SHORT_PACKET_ID_1;
						vm_cmdq.REGS.CM_DATA_1 = 0;
					}
					DSI_OUTREG32(cmdq, &DSI_REG[d]->DSI_VM_CMD_CON.ADDR,
					             AS_UINT32(&vm_cmdq.ADDR));
				}
			}
			/* start DSI VM CMDQ */
			if (force_update) {
				DSI_EnableVM_CMD(module, cmdq);
			}
		} else {
#ifdef ENABLE_DSI_ERROR_REPORT
			if ((para_list[0] & 1)) {
				memset(_dsi_cmd_queue, 0, sizeof(_dsi_cmd_queue));
				memcpy(_dsi_cmd_queue, para_list, count);
				_dsi_cmd_queue[(count + 3) / 4 * 4] = 0x4;
				count = (count + 3) / 4 * 4 + 4;
				para_list = (unsigned char *)_dsi_cmd_queue;
			} else {
				para_list[0] |= 4;
			}
#endif
			DSI_WaitForNotBusy(module, cmdq);

			if (cmd < 0xB0) {
				if (count > 1) {
					t2.HDR.CONFG = 2;
					t2.HDR.Data_ID = DSI_DCS_LONG_PACKET_ID;
					t2.HDR.WC16 = count + 1;

					DSI_OUTREG32(cmdq, &DSI_CMDQ_REG[d]->data[0].ADDR,
					             AS_UINT32(&t2.ADDR));

					goto_addr = (UINT32) (&DSI_CMDQ_REG[d]->data[1].REGS.byte0);
					mask_para = (0xFF << ((goto_addr & 0x3) * 8));
					set_para = (cmd << ((goto_addr & 0x3) * 8));
					DSI_MASKREG32(cmdq, goto_addr & (~0x3), mask_para,
					              set_para);

					for (i = 0; i < count; i++) {
						goto_addr =
						    (UINT32) (&DSI_CMDQ_REG[d]->data[1].REGS.byte1) + i;
						mask_para = (0xFF << ((goto_addr & 0x3) * 8));
						set_para =
						    (para_list[i] << ((goto_addr & 0x3) * 8));
						DSI_MASKREG32(cmdq, goto_addr & (~0x3), mask_para,
						              set_para);

					}

					DSI_OUTREG32(cmdq, &DSI_REG[d]->DSI_CMDQ_SIZE.ADDR,
					             2 + (count) / 4);
				} else {
					t0.HDR.CONFG = 0;
					t0.HDR.Data0 = cmd;
					if (count) {
						t0.HDR.Data_ID = DSI_DCS_SHORT_PACKET_ID_1;
						t0.HDR.Data1 = para_list[0];
					} else {
						t0.HDR.Data_ID = DSI_DCS_SHORT_PACKET_ID_0;
						t0.HDR.Data1 = 0;
					}

					DSI_OUTREG32(cmdq, &DSI_CMDQ_REG[d]->data[0].ADDR,
					             AS_UINT32(&t0.ADDR));
					DSI_OUTREG32(cmdq, &DSI_REG[d]->DSI_CMDQ_SIZE.ADDR, 1);
				}
			} else {
				if (count > 1) {
					t2.HDR.CONFG = 2;
					t2.HDR.Data_ID = DSI_GERNERIC_LONG_PACKET_ID;
					t2.HDR.WC16 = count + 1;

					DSI_OUTREG32(cmdq, &DSI_CMDQ_REG[d]->data[0].ADDR,
					             AS_UINT32(&t2.ADDR));

					goto_addr = (UINT32) (&DSI_CMDQ_REG[d]->data[1].REGS.byte0);
					mask_para = (0xFF << ((goto_addr & 0x3) * 8));
					set_para = (cmd << ((goto_addr & 0x3) * 8));
					DSI_MASKREG32(cmdq, goto_addr & (~0x3), mask_para,
					              set_para);

					for (i = 0; i < count; i++) {
						goto_addr =
						    (UINT32) (&DSI_CMDQ_REG[d]->data[1].REGS.byte1) + i;
						mask_para = (0xFF << ((goto_addr & 0x3) * 8));
						set_para =
						    (para_list[i] << ((goto_addr & 0x3) * 8));
						DSI_MASKREG32(cmdq, goto_addr & (~0x3), mask_para,
						              set_para);

					}

					DSI_OUTREG32(cmdq, &DSI_REG[d]->DSI_CMDQ_SIZE.ADDR,
					             2 + (count) / 4);
				} else {
					t0.HDR.CONFG = 0;
					t0.HDR.Data0 = cmd;
					if (count) {
						t0.HDR.Data_ID = DSI_GERNERIC_SHORT_PACKET_ID_2;
						t0.HDR.Data1 = para_list[0];
					} else {
						t0.HDR.Data_ID = DSI_GERNERIC_SHORT_PACKET_ID_1;
						t0.HDR.Data1 = 0;
					}
					DSI_OUTREG32(cmdq, &DSI_CMDQ_REG[d]->data[0].ADDR,
					             AS_UINT32(&t0.ADDR));
					DSI_OUTREG32(cmdq, &DSI_REG[d]->DSI_CMDQ_SIZE.ADDR, 1);
				}
			}
			if (force_update) {
				DSI_Start(module, cmdq);
				DSI_WaitForNotBusy(module, cmdq);
			}
		}
	}
}


void DSI_set_cmdq_V3(DISP_MODULE_ENUM module, void *cmdq, LCM_setting_table_V3 *para_tbl,
		     unsigned int size, unsigned char force_update)
{
	UINT32 i;
	/* UINT32 layer, layer_state, lane_num; */
	UINT32 goto_addr, mask_para, set_para;
	/* UINT32 fbPhysAddr, fbVirAddr; */
	DSI_T0_INS t0 = {{0}};
	/* DSI_T1_INS t1 = {{0}}; */
	DSI_T2_INS t2 = {{0}};

	UINT32 index = 0;

	unsigned char data_id, cmd, count;
	unsigned char *para_list;

	do {
		data_id = para_tbl[index].id;
		cmd = para_tbl[index].cmd;
		count = para_tbl[index].count;
		para_list = para_tbl[index].para_list;

		if (data_id == REGFLAG_ESCAPE_ID && cmd == REGFLAG_DELAY_MS_V3)
		{
			udelay(1000 * count);
			DISP_LOG_PRINT(ANDROID_LOG_INFO, "DSI", "DSI_set_cmdq_V3[%d]. Delay %d (ms) \n", index, count);

			continue;
		}

		DSI_WaitForNotBusy(module, cmdq);
		{
			OUTREG32(&DSI_CMDQ_REG[0]->data[0].ADDR, 0);
			if (count > 1)
			{
				t2.HDR.CONFG = 2;
				t2.HDR.Data_ID = data_id;
				t2.HDR.WC16 = count + 1;

				DSI_OUTREG32(cmdq,&DSI_CMDQ_REG[0]->data[0].ADDR, AS_UINT32(&t2.ADDR));

				goto_addr = (unsigned long)(&DSI_CMDQ_REG[0]->data[1].REGS.byte0);
				mask_para = (0xFFu<<((goto_addr&0x3u)*8));
				set_para = (cmd<<((goto_addr&0x3u)*8));
				DSI_MASKREG32(cmdq,goto_addr&(~0x3u), mask_para, set_para);

				for(i=0; i<count; i++)
				{
					goto_addr = (unsigned long)(&DSI_CMDQ_REG[0]->data[1].REGS.byte1) + i;
					mask_para = (0xFFu<<((goto_addr&0x3u)*8));
					set_para = (para_list[i]<<((goto_addr&0x3u)*8));
					DSI_MASKREG32(cmdq, goto_addr&(~0x3u), mask_para, set_para);
				}

				DSI_OUTREG32(cmdq, &DSI_REG[0]->DSI_CMDQ_SIZE.ADDR, 2+(count)/4);
			}
			else
			{
				t0.HDR.CONFG = 0;
				t0.HDR.Data0 = cmd;
				if (count)
				{
					t0.HDR.Data_ID = data_id;
					t0.HDR.Data1 = para_list[0];
				}
				else
				{
					t0.HDR.Data_ID = data_id;
					t0.HDR.Data1 = 0;
				}
				DSI_OUTREG32(cmdq, &DSI_CMDQ_REG[0]->data[0].ADDR, AS_UINT32(&t0.ADDR));
				DSI_OUTREG32(cmdq, &DSI_REG[0]->DSI_CMDQ_SIZE.ADDR, 1);
			}

			if(force_update)
			{
				DSI_Start(module, cmdq);
				DSI_WaitForNotBusy(module, cmdq);
			}
		}
	} while (++index < size);
}



void DSI_set_cmdq(DISP_MODULE_ENUM module, void *cmdq, unsigned int *pdata, unsigned int queue_size,
                  unsigned char force_update)
{
	DISPFUNC();
	/* _WaitForEngineNotBusy(); */
	unsigned int i = 0;
	unsigned int j = 0;
	char *module_name = ddp_get_module_name(module);
	DISPCHECK("DSI_set_cmdq, module=%s, cmdq=0x%08x\n", module_name, (unsigned int)cmdq);

	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		if (0 != DSI_REG[i]->DSI_MODE_CTRL.REGS.MODE) {
#if 0
			/* not in cmd mode */
			DSI_VM_CMD_CON_REG vm_cmdq;
			OUTREG32(&vm_cmdq.ADDR, AS_UINT32(&DSI_REG[i]->DSI_VM_CMD_CON.ADDR));
			dprintf(INFO, "set cmdq in VDO mode\n");
			if (queue_size > 1) {   /* long packet */
				vm_cmdq.REGS.LONG_PKT = 1;
				vm_cmdq.REGS.CM_DATA_ID = ((pdata[0] >> 8) & 0xFF);
				vm_cmdq.REGS.CM_DATA_0 = ((pdata[0] >> 16) & 0xFF);
				vm_cmdq.REGS.CM_DATA_1 = 0;
				OUTREG32(&DSI_REG[i]->DSI_VM_CMD_CON.ADDR, AS_UINT32(&vm_cmdq.ADDR));
				for (j = 0; j < queue_size - 1; j++) {
					OUTREG32(&DSI_VM_CMD_REG->data[j].ADDR,
					         AS_UINT32((pdata + j + 1)));
				}
			} else {
				vm_cmdq.REGS.LONG_PKT = 0;
				vm_cmdq.REGS.CM_DATA_ID = ((pdata[0] >> 8) & 0xFF);
				vm_cmdq.REGS.CM_DATA_0 = ((pdata[0] >> 16) & 0xFF);
				vm_cmdq.REGS.CM_DATA_1 = ((pdata[0] >> 24) & 0xFF);
				OUTREG32(&DSI_REG->DSI_VM_CMD_CON.ADDR, AS_UINT32(&vm_cmdq.ADDR));
			}
			/* start DSI VM CMDQ */
			if (force_update) {
				MMProfileLogEx(MTKFB_MMP_Events.DSICmd, MMProfileFlagStart,
				               *(unsigned int *)(&DSI_VM_CMD_REG->data[0].ADDR),
				               *(unsigned int *)(&DSI_VM_CMD_REG->data[1].ADDR));
				DSI_EnableVM_CMD();

				/* must wait VM CMD done? */
				MMProfileLogEx(MTKFB_MMP_Events.DSICmd, MMProfileFlagEnd,
				               *(unsigned int *)(&DSI_VM_CMD_REG->data[2].ADDR),
				               *(unsigned int *)(&DSI_VM_CMD_REG->data[3].ADDR));
			}
#endif
		} else {
			ASSERT(queue_size <= 32);
			DSI_WaitForNotBusy(module, cmdq);
#ifdef ENABLE_DSI_ERROR_REPORT
			if ((pdata[0] & 1)) {
				memcpy(_dsi_cmd_queue, pdata, queue_size * 4);
				_dsi_cmd_queue[queue_size++] = 0x4;
				pdata = (unsigned int *)_dsi_cmd_queue;
			} else {
				pdata[0] |= 4;
			}
#endif

			for (j = 0; j < queue_size; j++) {
				DSI_OUTREG32(cmdq, &DSI_CMDQ_REG[i]->data[j].ADDR,
				             AS_UINT32((pdata + j)));
			}

			DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_CMDQ_SIZE.ADDR, queue_size);

			for (i = 0; i < queue_size; i++)
				dprintf(INFO,
				        "[DISP] - kernel - DSI_set_cmdq. DSI_CMDQ+%04x : 0x%08x\n",
				        i * 4, INREG32(DSI0_BASE + 0x200 + i * 4));

			if (force_update) {
				DSI_Start(module, cmdq);
				DSI_WaitForNotBusy(module, cmdq);
			}
		}
	}
}



void DSI_set_rar(DISP_MODULE_ENUM module, void *cmdq)
{
	int i = 0;
	char *module_name = ddp_get_module_name(module);
	DSI_PHY_LD0CON_REG phy_ld0con;
	memset(&phy_ld0con.ADDR, 0, sizeof(DSI_PHY_LD0CON_REG));

	DISPMSG("DSI_set_rar, module=%s, cmdq=0x%08x\n", module_name, (unsigned int)cmdq);

	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		DSI_READREG32(PDSI_PHY_LD0CON_REG, &phy_ld0con.ADDR, &DSI_REG[i]->DSI_PHY_LD0CON.ADDR);
		phy_ld0con.REGS.L0_RM_TRIG_EN = 1;
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_PHY_LD0CON.ADDR, AS_UINT32(&phy_ld0con.ADDR));
		mdelay(1);
		phy_ld0con.REGS.L0_RM_TRIG_EN = 0;
		DSI_OUTREG32(cmdq, &DSI_REG[i]->DSI_PHY_LD0CON.ADDR, AS_UINT32(&phy_ld0con.ADDR));
		mdelay(1);
	}
}

void _copy_dsi_params(LCM_DSI_PARAMS *src, LCM_DSI_PARAMS *dst)
{
	memcpy((LCM_DSI_PARAMS *) dst, (LCM_DSI_PARAMS *) src, sizeof(LCM_DSI_PARAMS));
}

int ddp_dsi_init(DISP_MODULE_ENUM module, void *cmdq)
{
	int i = 0;
	DISPFUNC();
	/* DSI_OUTREG32(cmdq, 0x10000048, 0x80000000); */
	ddp_enable_module_clock(module);

	/* DSI_OUTREG32(MMSYS_CONFIG_BASE+0xC08, 0xffffffff); */


	memset(&_dsi_context, 0, sizeof(_dsi_context));


#if 0
	disp_register_module_irq_callback(DISP_MODULE_DSI0, _DSI_INTERNAL_IRQ_Handler);
	disp_register_module_irq_callback(DISP_MODULE_DSI1, _DSI_INTERNAL_IRQ_Handler);
	disp_register_module_irq_callback(DISP_MODULE_DSIDUAL, _DSI_INTERNAL_IRQ_Handler);

	init_waitqueue_head(&_dsi_cmd_done_wait_queue[0]);
	init_waitqueue_head(&_dsi_cmd_done_wait_queue[1]);
	init_waitqueue_head(&_dsi_dcs_read_wait_queue[0]);
	init_waitqueue_head(&_dsi_dcs_read_wait_queue[1]);
	init_waitqueue_head(&_dsi_wait_bta_te[0]);
	init_waitqueue_head(&_dsi_wait_bta_te[1]);
	init_waitqueue_head(&_dsi_wait_ext_te[0]);
	init_waitqueue_head(&_dsi_wait_ext_te[1]);
	init_waitqueue_head(&_dsi_wait_vm_done_queue[0]);
	init_waitqueue_head(&_dsi_wait_vm_done_queue[1]);
#endif
	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		DISPCHECK("dsi%d init finished\n", i);
	}


	return DSI_STATUS_OK;
}

int ddp_dsi_deinit(DISP_MODULE_ENUM module, void *cmdq)
{
	int i = 0;

	memset(&_dsi_context, 0, sizeof(_dsi_context));
	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		DISPCHECK("dsi%d init finished\n", i);
	}
	DSI_SetMode(module, NULL, CMD_MODE);
	DSI_clk_HS_mode(module, NULL, FALSE);
	ddp_disable_module_clock(module);

	DSI_PHY_clk_switch(module, NULL, false);

	return 0;
}

void _dump_dsi_params(LCM_DSI_PARAMS *dsi_config)
{
	if (dsi_config) {
		switch (dsi_config->mode) {
			case CMD_MODE:
				DISPCHECK("[DDPDSI] DSI Mode: CMD_MODE\n");
				break;
			case SYNC_PULSE_VDO_MODE:
				DISPCHECK("[DDPDSI] DSI Mode: SYNC_PULSE_VDO_MODE\n");
				break;
			case SYNC_EVENT_VDO_MODE:
				DISPCHECK("[DDPDSI] DSI Mode: SYNC_EVENT_VDO_MODE\n");
				break;
			case BURST_VDO_MODE:
				DISPCHECK("[DDPDSI] DSI Mode: BURST_VDO_MODE\n");
				break;
			default:
				DISPCHECK("[DDPDSI] DSI Mode: Unknown\n");
				break;
		}

		DISPCHECK("[DDPDSI] LANE_NUM: %d,format: %d,vertical_sync_active: %d\n",
		          dsi_config->LANE_NUM, dsi_config->data_format.format, dsi_config->vertical_sync_active);
		DISPCHECK
		("[DDPDSI] vact: %d, vbp: %d, vfp: %d, vact_line: %d, hact: %d, hbp: %d, hfp: %d, hblank: %d\n",
		 dsi_config->vertical_sync_active, dsi_config->vertical_backporch,
		 dsi_config->vertical_frontporch, dsi_config->vertical_active_line,
		 dsi_config->horizontal_sync_active, dsi_config->horizontal_backporch,
		 dsi_config->horizontal_frontporch, dsi_config->horizontal_blanking_pixel);
		DISPCHECK
		("[DDPDSI] pll_select: %d, pll_div1: %d, pll_div2: %d, fbk_div: %d,fbk_sel: %d, rg_bir: %d\n",
		 dsi_config->pll_select, dsi_config->pll_div1, dsi_config->pll_div2,
		 dsi_config->fbk_div, dsi_config->fbk_sel, dsi_config->rg_bir);
		DISPCHECK
		("[DDPDSI] rg_bic: %d, rg_bp: %d, PLL_CLOCK: %d, dsi_clock: %d, ssc_range: %d,	ssc_disable: %d, compatibility_for_nvk: %d, cont_clock: %d\n",
		 dsi_config->rg_bic, dsi_config->rg_bp, dsi_config->PLL_CLOCK,
		 dsi_config->dsi_clock, dsi_config->ssc_range, dsi_config->ssc_disable,
		 dsi_config->compatibility_for_nvk, dsi_config->cont_clock);
		DISPCHECK
		("[DDPDSI] lcm_ext_te_enable: %d, noncont_clock: %d, noncont_clock_period: %d\n",
		 dsi_config->lcm_ext_te_enable, dsi_config->noncont_clock,
		 dsi_config->noncont_clock_period);
	}

	return;
}

void DSI_Set_VM_CMD(DISP_MODULE_ENUM module, cmdqRecHandle cmdq)
{

	int i = 0;
	if (module != DISP_MODULE_DSIDUAL) {
		for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
			DSI_OUTREGBIT(cmdq, DSI_VM_CMD_CON_REG, DSI_REG[i]->DSI_VM_CMD_CON.ADDR,
			              REGS.TS_VFP_EN, 1);
			DSI_OUTREGBIT(cmdq, DSI_VM_CMD_CON_REG, DSI_REG[i]->DSI_VM_CMD_CON.ADDR,
			              REGS.VM_CMD_EN, 1);
		}
	}
#if 0
	else {
		DSI_OUTREGBIT(cmdq, DSI_VM_CMD_CON_REG, DSI_REG[i]->DSI_VM_CMD_CON.ADDR, REGS.TS_VFP_EN, 1);
		DSI_OUTREGBIT(cmdq, DSI_VM_CMD_CON_REG, DSI_REG[i]->DSI_VM_CMD_CON.ADDR, REGS.VM_CMD_EN, 1);
	}
#endif
	return;
}

int ddp_dsi_config(DISP_MODULE_ENUM module, disp_ddp_path_config *config, void *cmdq_handle)
{
	int i = 0;
	DISPFUNC();

	if (!config->dst_dirty)
		return 0;

	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		_copy_dsi_params(&(config->dsi_config), &(_dsi_context[i].dsi_params));
		_dump_dsi_params(&(_dsi_context[i].dsi_params));
	}

	DSI_PHY_clk_setting(module, NULL, &(config->dsi_config));
	DSI_TXRX_Control(module, NULL, &(config->dsi_config));
	DSI_PS_Control(module, NULL, &(config->dsi_config), config->dst_w, config->dst_h);
	DSI_PHY_TIMCONFIG(module, NULL, &(config->dsi_config));

	/* if(config->dsi_config.mode != CMD_MODE) */
	if (config->dsi_config.mode != CMD_MODE || ((config->dsi_config.switch_mode_enable == 1)
	        && (config->dsi_config.switch_mode !=
	            CMD_MODE))) {
		DSI_Config_VDO_Timing(module, NULL, &(config->dsi_config));
		DSI_Set_VM_CMD(module, cmdq_handle);
	}
#if 0
	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		DSI_OUTREGBIT(cmdq_handle, DSI_INT_ENABLE_REG, DSI_REG[i]->DSI_INTEN.ADDR, REGS.CMD_DONE, 1);
		DSI_OUTREGBIT(cmdq_handle, DSI_INT_ENABLE_REG, DSI_REG[i]->DSI_INTEN.ADDR, REGS.RD_RDY, 1);
		DSI_OUTREGBIT(cmdq_handle, DSI_INT_ENABLE_REG, DSI_REG[i]->DSI_INTEN.ADDR, REGS.TE_RDY, 1);
		/* DSI_OUTREGBIT(cmdq_handle, DSI_INT_ENABLE_REG,DSI_REG[i]->DSI_INTEN.ADDR,REGS.EXT_TE,1); */
		DSI_OUTREGBIT(cmdq_handle, DSI_INT_ENABLE_REG, DSI_REG[i]->DSI_INTEN.ADDR, REGS.VM_DONE, 1);
	}
#endif

	/* Enable clk low power per Line ; */
	if (config->dsi_config.clk_lp_per_line_enable) {
		DSI_PHY_CLK_LP_PerLine_config(module, NULL, &(config->dsi_config));
	}

	DSI_BackupRegisters(module, cmdq_handle);
	/* DSI_BIST_Pattern_Test(FALSE, 0x00ffff00); */
	return 0;
}

int ddp_dsi_stop(DISP_MODULE_ENUM module, void *cmdq_handle)
{
	/* ths caller should call wait_event_or_idle for frame stop event then. */
	if (_dsi_is_video_mode(module)) {
		DSI_SetMode(module, cmdq_handle, CMD_MODE);
	}

	return 0;
}

int ddp_dsi_reset(DISP_MODULE_ENUM module, void *cmdq_handle)
{
	DSI_Reset(module, cmdq_handle);

	return 0;
}

static int s_isDsiPowerOn;

int ddp_dsi_power_on(DISP_MODULE_ENUM module, void *cmdq_handle)
{
	int i = 0;
	int ret = 0;

	if (!s_isDsiPowerOn) {
		if (module == DISP_MODULE_DSI0 || module == DISP_MODULE_DSI1) {
			ddp_enable_module_clock(module);

			if (ret > 0) {
				DISP_LOG_PRINT(ANDROID_LOG_WARN, "DSI",
				               "DSI0 power manager API return FALSE\n");
			}
		}

		s_isDsiPowerOn = TRUE;
	}

	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		if (_dsi_context[i].dsi_params.mode == CMD_MODE) {
			DSI_PHY_clk_switch(module, NULL, true);

			/* restore dsi register */
			DSI_RestoreRegisters(module, NULL);

			/* enable sleep-out mode */
			DSI_SleepOut(module, NULL);

			/* enter wakeup */
			DSI_Wakeup(module, NULL);

			DSI_Reset(module, NULL);
		} else {
			/* initialize clock setting */
			DSI_PHY_clk_switch(module, NULL, true);

			/* restore dsi register */
			DSI_RestoreRegisters(module, NULL);

			/* enable sleep-out mode */
			DSI_SleepOut(module, NULL);

			/* enter wakeup */
			DSI_Wakeup(module, NULL);
			DSI_clk_HS_mode(module, NULL, false);

			DSI_Reset(module, NULL);
		}
	}

	return DSI_STATUS_OK;
}


int ddp_dsi_power_off(DISP_MODULE_ENUM module, void *cmdq_handle)
{
	int i = 0;
	int ret = 0;

	if (!s_isDsiPowerOn) {
		if (module == DISP_MODULE_DSI0 || module == DISP_MODULE_DSI1) {
			ddp_disable_module_clock(module);

			if (ret > 0) {
				DISP_LOG_PRINT(ANDROID_LOG_WARN, "DSI0",
				               "DSI0 power manager API return FALSE\n");
			}
		}
		s_isDsiPowerOn = TRUE;
	}

	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		if (_dsi_context[i].dsi_params.mode == CMD_MODE) {
			/* no need this, we will make dsi is in idle when ddp_dsi_stop() returns */
			/* DSI_CHECK_RET(DSI_WaitForNotBusy(module, NULL)); */
			DSI_CHECK_RET(DSI_BackupRegisters(module, NULL));

			/* disable HS mode */
			DSI_clk_HS_mode(module, NULL, false);
			/* enter ULPS mode */
			DSI_lane0_ULP_mode(module, NULL, 1);
			DSI_clk_ULP_mode(module, NULL, 1);
			/* disable mipi pll */
			DSI_PHY_clk_switch(module, NULL, false);
		} else {
			/* backup dsi register */
			/* no need this, we will make dsi is in idle when ddp_dsi_stop() returns */
			/* DSI_CHECK_RET(DSI_WaitForNotBusy()); */
			DSI_BackupRegisters(module, NULL);

			/* disable HS mode */
			DSI_clk_HS_mode(module, NULL, false);
			/* enter ULPS mode */
			DSI_lane0_ULP_mode(module, NULL, 1);
			DSI_clk_ULP_mode(module, NULL, 1);

			/* disable mipi pll */
			DSI_PHY_clk_switch(module, NULL, false);
		}
	}

	return DSI_STATUS_OK;
}


int ddp_dsi_is_busy(DISP_MODULE_ENUM module)
{
	int i = 0;
	int busy = 0;
	DSI_INT_STATUS_REG status;
	DISPFUNC();

	for (i = DSI_MODULE_BEGIN(module); i <= DSI_MODULE_END(module); i++) {
		status = DSI_REG[i]->DSI_INTSTA;

		if (status.REGS.BUSY)
			busy++;
	}

	return busy;
}

int ddp_dsi_is_idle(DISP_MODULE_ENUM module)
{
	return !ddp_dsi_is_busy(module);
}

int ddp_dsi_dump(DISP_MODULE_ENUM module, int level)
{
	DSI_DumpRegisters(module, NULL, level);
	return 0;
}

int ddp_dsi_start(DISP_MODULE_ENUM module, void *cmdq)
{
	int i = 0;
#if 0
	if (module == DISP_MODULE_DSIDUAL) {
		DSI_OUTREGBIT(cmdq, DSI_START_REG, DSI_REG[0]->DSI_START.ADDR, REGS.DSI_START, 0);
		DSI_OUTREGBIT(cmdq, DSI_START_REG, DSI_REG[1]->DSI_START.ADDR, REGS.DSI_START, 0);
		DSI_OUTREGBIT(cmdq, DSI_COM_CTRL_REG, DSI_REG[0]->DSI_COM_CTRL.ADDR, REGS.DSI_DUAL_EN, 1);
		DSI_OUTREGBIT(cmdq, DSI_COM_CTRL_REG, DSI_REG[1]->DSI_COM_CTRL.ADDR, REGS.DSI_DUAL_EN, 1);
		DSI_SetMode(module, cmdq, _dsi_context[i].dsi_params.mode);
		DSI_clk_HS_mode(module, cmdq, TRUE);
	} else
#endif
	{
		DSI_SetMode(module, cmdq, _dsi_context[i].dsi_params.mode);
		DSI_clk_HS_mode(module, cmdq, TRUE);
	}

	return 0;
}

int ddp_dsi_trigger(DISP_MODULE_ENUM module, void *cmdq)
{
#if 0
	static int j;
	DSI_OUTREG32(NULL, 0x14012178, 0x00000000 | (0xFF << (j * 8)));
	DSI_OUTREG32(NULL, 0x1401217C, 0x00000040);
	j++;
#endif
	DSI_Start(module, cmdq);

	return 0;
}

static void lcm_set_reset_pin(UINT32 value)
{
	OUTREG32(MMSYS_CONFIG_BASE + 0x150, value);
}

static void lcm_udelay(UINT32 us)
{
	udelay(us);
}

static void lcm_mdelay(UINT32 ms)
{
	mdelay(ms);
}

static void lcm_rar(UINT32 ms)
{
	DSI_set_rar(DISP_MODULE_DSI0, NULL);
	mdelay(ms);
}

void DSI_set_null_Wrapper_DSI0(unsigned cmd, unsigned char count, unsigned char *para_list,
                               unsigned char force_update)
{
	DSI_set_null(DISP_MODULE_DSI0, NULL, cmd, count, para_list, force_update);
}

void DSI_set_null_Wrapper_DSI1(unsigned cmd, unsigned char count, unsigned char *para_list,
                               unsigned char force_update)
{
	DSI_set_null(DISP_MODULE_DSI1, NULL, cmd, count, para_list, force_update);
}

void DSI_set_null_Wrapper_DSIDual(unsigned cmd, unsigned char count, unsigned char *para_list,
                                  unsigned char force_update)
{
	DSI_set_null(DISP_MODULE_DSIDUAL, NULL, cmd, count, para_list, force_update);
}

void DSI_set_cmdq_V2_Wrapper_DSI0(unsigned cmd, unsigned char count, unsigned char *para_list,
                                  unsigned char force_update)
{
	DSI_set_cmdq_V2(DISP_MODULE_DSI0, NULL, cmd, count, para_list, force_update);
}

void DSI_set_cmdq_V2_Wrapper_DSI1(unsigned cmd, unsigned char count, unsigned char *para_list,
                                  unsigned char force_update)
{
	DSI_set_cmdq_V2(DISP_MODULE_DSI1, NULL, cmd, count, para_list, force_update);
}

void DSI_set_cmdq_V2_Wrapper_DSIDual(unsigned cmd, unsigned char count, unsigned char *para_list,
                                     unsigned char force_update)
{
	DSI_set_cmdq_V2(DISP_MODULE_DSIDUAL, NULL, cmd, count, para_list, force_update);
}

void DSI_set_cmdq_V3_Wrapper_DSI0(LCM_setting_table_V3 *para_tbl, unsigned int size,
                                  unsigned char force_update)
{
	DSI_set_cmdq_V3(DISP_MODULE_DSI0, NULL, para_tbl, size, force_update);
}

void DSI_set_cmdq_V3_Wrapper_DSI1(LCM_setting_table_V3 *para_tbl, unsigned int size,
                                  unsigned char force_update)
{
	DSI_set_cmdq_V3(DISP_MODULE_DSI1, NULL, para_tbl, size, force_update);
}

void DSI_set_cmdq_V3_Wrapper_DSIDual(LCM_setting_table_V3 *para_tbl, unsigned int size,
                                     unsigned char force_update)
{
	DSI_set_cmdq_V3(DISP_MODULE_DSIDUAL, NULL, para_tbl, size, force_update);
}

void DSI_set_cmdq_wrapper_DSI0(unsigned int *pdata, unsigned int queue_size,
                               unsigned char force_update)
{
	DSI_set_cmdq(DISP_MODULE_DSI0, NULL, pdata, queue_size, force_update);
}

void DSI_set_cmdq_wrapper_DSI1(unsigned int *pdata, unsigned int queue_size,
                               unsigned char force_update)
{
	DSI_set_cmdq(DISP_MODULE_DSI1, NULL, pdata, queue_size, force_update);
}

void DSI_set_cmdq_wrapper_DSIDual(unsigned int *pdata, unsigned int queue_size,
                                  unsigned char force_update)
{
	DSI_set_cmdq(DISP_MODULE_DSIDUAL, NULL, pdata, queue_size, force_update);
}

unsigned int DSI_dcs_read_lcm_reg_v2_wrapper_DSI0(UINT8 cmd, UINT8 *buffer, UINT8 buffer_size)
{
	return DSI_dcs_read_lcm_reg_v2(DISP_MODULE_DSI0, NULL, cmd, buffer, buffer_size);
}

unsigned int DSI_dcs_read_lcm_reg_v2_wrapper_DSI1(UINT8 cmd, UINT8 *buffer, UINT8 buffer_size)
{
	return DSI_dcs_read_lcm_reg_v2(DISP_MODULE_DSI1, NULL, cmd, buffer, buffer_size);
}

unsigned int DSI_dcs_read_lcm_reg_v2_wrapper_DSIDUAL(UINT8 cmd, UINT8 *buffer, UINT8 buffer_size)
{
	return DSI_dcs_read_lcm_reg_v2(DISP_MODULE_DSIDUAL, NULL, cmd, buffer, buffer_size);
}

static LCM_UTIL_FUNCS lcm_utils_dsi0;
#if 0
static LCM_UTIL_FUNCS lcm_utils_dsi1;
static LCM_UTIL_FUNCS lcm_utils_dsidual;
#endif


int ddp_dsi_set_lcm_utils(DISP_MODULE_ENUM module, LCM_DRIVER *lcm_drv)
{
	LCM_UTIL_FUNCS *utils = NULL;
	if (lcm_drv == NULL) {
		DISPERR("lcm_drv is null\n");
		return -1;
	}

	if (module == DISP_MODULE_DSI0) {
		utils = &lcm_utils_dsi0;
	}
#if 0
	else if (module == DISP_MODULE_DSI1) {
		utils = &lcm_utils_dsi1;
	}

	else if (module == DISP_MODULE_DSIDUAL) {
		utils = &lcm_utils_dsidual;
	}
#endif
	else {
		DISPERR("wrong module: %d\n", module);
		return -1;
	}

	utils->set_reset_pin = lcm_set_reset_pin;
	utils->udelay = lcm_udelay;
	utils->mdelay = lcm_mdelay;
	utils->rar = lcm_rar;

	if (module == DISP_MODULE_DSI0) {
		utils->dsi_set_cmdq = DSI_set_cmdq_wrapper_DSI0;
		utils->dsi_set_cmdq_V2 = DSI_set_cmdq_V2_Wrapper_DSI0;
		utils->dsi_set_null = DSI_set_null_Wrapper_DSI0;
		utils->dsi_dcs_read_lcm_reg_v2 = DSI_dcs_read_lcm_reg_v2_wrapper_DSI0;
		utils->dsi_set_cmdq_V3	= DSI_set_cmdq_V3_Wrapper_DSI0;
	}
#if 0
	else if (module == DISP_MODULE_DSI1) {
		utils->dsi_set_cmdq = DSI_set_cmdq_wrapper_DSI1;
		utils->dsi_set_cmdq_V2 = DSI_set_cmdq_V2_Wrapper_DSI1;
		utils->dsi_set_null = DSI_set_null_Wrapper_DSI1;
		utils->dsi_dcs_read_lcm_reg_v2 = DSI_dcs_read_lcm_reg_v2_wrapper_DSI1;
		utils->dsi_set_cmdq_V3	= DSI_set_cmdq_V3_Wrapper_DSI1;
	} else if (module == DISP_MODULE_DSIDUAL) {
		/* TODO: Ugly workaround, hope we can found better resolution */
		LCM_PARAMS lcm_param;
		lcm_drv->get_params(&lcm_param);
		if (lcm_param.lcm_cmd_if == LCM_INTERFACE_DSI0) {
			utils->dsi_set_cmdq = DSI_set_cmdq_wrapper_DSI0;
			utils->dsi_set_cmdq_V2 = DSI_set_cmdq_V2_Wrapper_DSI0;
			utils->dsi_set_null = DSI_set_null_Wrapper_DSI0;
			utils->dsi_dcs_read_lcm_reg_v2 = DSI_dcs_read_lcm_reg_v2_wrapper_DSI0;
			utils->dsi_set_cmdq_V3	= DSI_set_cmdq_V3_Wrapper_DSI0;
		} else if (lcm_param.lcm_cmd_if == LCM_INTERFACE_DSI1) {
			utils->dsi_set_cmdq = DSI_set_cmdq_wrapper_DSI1;
			utils->dsi_set_cmdq_V2 = DSI_set_cmdq_V2_Wrapper_DSI1;
			utils->dsi_set_null = DSI_set_null_Wrapper_DSI1;
			utils->dsi_dcs_read_lcm_reg_v2 = DSI_dcs_read_lcm_reg_v2_wrapper_DSI1;
			utils->dsi_set_cmdq_V3	= DSI_set_cmdq_V3_Wrapper_DSI1;
		} else {
			utils->dsi_set_cmdq = DSI_set_cmdq_wrapper_DSIDual;
			utils->dsi_set_cmdq_V2 = DSI_set_cmdq_V2_Wrapper_DSIDual;
			utils->dsi_set_null = DSI_set_null_Wrapper_DSIDual;
			utils->dsi_dcs_read_lcm_reg_v2 = DSI_dcs_read_lcm_reg_v2_wrapper_DSIDUAL;
			utils->dsi_set_cmdq_V3	= DSI_set_cmdq_V3_Wrapper_DSIDual;
		}
	}
#endif
	utils->set_gpio_out = mt_set_gpio_out;
	utils->set_gpio_mode = mt_set_gpio_mode;
	utils->set_gpio_dir = mt_set_gpio_dir;
	utils->set_gpio_pull_enable = (int (*)(unsigned int, unsigned char))mt_set_gpio_pull_enable;
	lcm_drv->set_util_funcs(utils);

	return 0;
}

static int ddp_dsi_polling_irq(DISP_MODULE_ENUM module, int bit, int timeout)
{
	unsigned int cnt = 0;
	unsigned int irq_reg_base = 0;
	unsigned int reg_val = 0;

	if (module == DISP_MODULE_DSI0 /* || module == DISP_MODULE_DSIDUAL */)
		irq_reg_base = DISP_REG_DSI_INSTA;
#if 0
	else
		irq_reg_base = 0x1401C00C;
#endif
	/* DISPCHECK("dsi polling irq, module=%d, bit=0x%08x, timeout=%d, irq_regbase=0x%08x\n", module, bit, timeout, irq_reg_base); */

	if (timeout <= 0) {
		while ((DISP_REG_GET(irq_reg_base) & bit) == 0);
		cnt = 1;
	} else {
		/* time need to update */
		cnt = timeout * 1000 / 100;
		while (cnt > 0) {
			cnt--;
			reg_val = DISP_REG_GET(irq_reg_base);
			/* DISPMSG("reg_val=0x%08x\n", reg_val); */
			if (reg_val & bit) {
				DSI_OUTREG32(NULL, irq_reg_base, ~reg_val);
				break;
			}
			udelay(100);
		}
	}

	DISPMSG("DSI polling interrupt ret =%d\n", cnt);

	if (cnt == 0)
		DSI_DumpRegisters(module, NULL, 2);

	return cnt;
}

DDP_MODULE_DRIVER ddp_driver_dsi0 = {
	.module = DISP_MODULE_DSI0,
	.init = ddp_dsi_init,
	.deinit = ddp_dsi_deinit,
	.config = ddp_dsi_config,
	.trigger = ddp_dsi_trigger,
	.start = ddp_dsi_start,
	.stop = ddp_dsi_stop,
	.reset = ddp_dsi_reset,
	.power_on = ddp_dsi_power_on,
	.power_off = ddp_dsi_power_off,
	.is_idle = ddp_dsi_is_idle,
	.is_busy = ddp_dsi_is_busy,
	.dump_info = ddp_dsi_dump,
	.set_lcm_utils = ddp_dsi_set_lcm_utils,
	.polling_irq = ddp_dsi_polling_irq
};

#if 0
DDP_MODULE_DRIVER ddp_driver_dsi1 = {
	.module = DISP_MODULE_DSI1,
	.init = ddp_dsi_init,
	.deinit = ddp_dsi_deinit,
	.config = ddp_dsi_config,
	.trigger = ddp_dsi_trigger,
	.start = ddp_dsi_start,
	.stop = ddp_dsi_stop,
	.reset = ddp_dsi_reset,
	.power_on = ddp_dsi_power_on,
	.power_off = ddp_dsi_power_off,
	.is_idle = ddp_dsi_is_idle,
	.is_busy = ddp_dsi_is_busy,
	.dump_info = ddp_dsi_dump,
	.set_lcm_utils = ddp_dsi_set_lcm_utils,
	.polling_irq = ddp_dsi_polling_irq
};


DDP_MODULE_DRIVER ddp_driver_dsidual = {
	.module = DISP_MODULE_DSIDUAL,
	.init = ddp_dsi_init,
	.deinit = ddp_dsi_deinit,
	.config = ddp_dsi_config,
	.trigger = ddp_dsi_trigger,
	.start = ddp_dsi_start,
	.stop = ddp_dsi_stop,
	.reset = ddp_dsi_reset,
	.power_on = ddp_dsi_power_on,
	.power_off = ddp_dsi_power_off,
	.is_idle = ddp_dsi_is_idle,
	.is_busy = ddp_dsi_is_busy,
	.dump_info = ddp_dsi_dump,
	.set_lcm_utils = ddp_dsi_set_lcm_utils,
	.polling_irq = ddp_dsi_polling_irq
};
#endif
