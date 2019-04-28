/*
 * Copyright (C) 2015 MediaTek Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */

#ifndef __CMDQ_ENGINE_H__
#define __CMDQ_ENGINE_H__

enum CMDQ_ENG_ENUM {
	/* ISP */
	CMDQ_ENG_ISP_IMGI = 0,
	CMDQ_ENG_ISP_IMGO,	/* 1 */
	CMDQ_ENG_ISP_IMG2O,	/* 2 */

	/* IPU */
	CMDQ_ENG_IPUI,			/* 3 */
	CMDQ_ENG_IPUO,			/* 4 */

	/* MDP */
	CMDQ_ENG_MDP_CAMIN,		/* 5 */
	CMDQ_ENG_MDP_RDMA0,		/* 6 */
	CMDQ_ENG_MDP_RDMA1,		/* 7 */
	CMDQ_ENG_MDP_IPUIN,		/* 8 */
	CMDQ_ENG_MDP_RSZ0,		/* 9 */
	CMDQ_ENG_MDP_RSZ1,		/* 10 */
	CMDQ_ENG_MDP_TDSHP0,		/* 11 */
	CMDQ_ENG_MDP_COLOR0,		/* 12 */
	CMDQ_ENG_MDP_PATH0_SOUT,	/* 13 */
	CMDQ_ENG_MDP_PATH1_SOUT,	/* 14 */
	CMDQ_ENG_MDP_WROT0,		/* 15 */
	CMDQ_ENG_MDP_WROT1,		/* 16 */

	/* JPEG & VENC */
	CMDQ_ENG_JPEG_ENC,		/* 17 */
	CMDQ_ENG_VIDEO_ENC,		/* 18 */
	CMDQ_ENG_JPEG_DEC,		/* 19 */
	CMDQ_ENG_JPEG_REMDC,		/* 20 */

	/* DISP */
	CMDQ_ENG_DISP_UFOE,		/* 21 */
	CMDQ_ENG_DISP_AAL,		/* 22 */
	CMDQ_ENG_DISP_COLOR0,		/* 23 */
	CMDQ_ENG_DISP_RDMA0,		/* 24 */
	CMDQ_ENG_DISP_RDMA1,		/* 25 */
	CMDQ_ENG_DISP_WDMA0,		/* 26 */
	CMDQ_ENG_DISP_WDMA1,		/* 27 */
	CMDQ_ENG_DISP_OVL0,		/* 28 */
	CMDQ_ENG_DISP_OVL1,		/* 29 */
	CMDQ_ENG_DISP_OVL2,		/* 30 */
	CMDQ_ENG_DISP_GAMMA,		/* 31 */
	CMDQ_ENG_DISP_DSI0_VDO,		/* 32 */
	CMDQ_ENG_DISP_DSI0_CMD,		/* 33 */
	CMDQ_ENG_DISP_DSI0,		/* 34 */
	CMDQ_ENG_DISP_DPI,		/* 35 */
	CMDQ_ENG_DISP_2L_OVL0,		/* 36 */
	CMDQ_ENG_DISP_2L_OVL1,		/* 37 */
	CMDQ_ENG_DISP_2L_OVL2,		/* 38 */

	/* ISP */
	CMDQ_ENG_DPE,			/* 39 */
	CMDQ_ENG_RSC,			/* 40 */
	CMDQ_ENG_GEPF,			/* 41 */
	CMDQ_ENG_EAF,			/* 42 */

	/* temp: CMDQ internal usage */
	CMDQ_ENG_CMDQ,			/* 43 */
	CMDQ_ENG_DISP_MUTEX,		/* 44 */
	CMDQ_ENG_MMSYS_CONFIG,		/* 45 */

	/* Dummy Engine */
	CMDQ_ENG_MDP_RSZ2,		/* 46 */
	CMDQ_ENG_MDP_TDSHP1,		/* 47 */
	CMDQ_ENG_MDP_MOUT0,		/* 48 */
	CMDQ_ENG_MDP_MOUT1,		/* 49 */
	CMDQ_ENG_MDP_WDMA,		/* 50 */

	CMDQ_ENG_DISP_COLOR1,		/* 51 */
	CMDQ_ENG_DISP_RDMA2,		/* 52 */
	CMDQ_ENG_DISP_MERGE,		/* 53 */
	CMDQ_ENG_DISP_SPLIT0,		/* 54 */
	CMDQ_ENG_DISP_SPLIT1,		/* 55 */
	CMDQ_ENG_DISP_DSI1_VDO,		/* 56 */
	CMDQ_ENG_DISP_DSI1_CMD,		/* 57 */
	CMDQ_ENG_DISP_DSI1,		/* 58 */

	CMDQ_MAX_ENGINE_COUNT	/* ALWAYS keep at the end */
};

#define CMDQ_ENG_ISP_GROUP_BITS	((1LL << CMDQ_ENG_ISP_IMGI) |	\
				 (1LL << CMDQ_ENG_ISP_IMGO) |	\
				 (1LL << CMDQ_ENG_ISP_IMG2O))

#define CMDQ_ENG_MDP_GROUP_BITS	((1LL << CMDQ_ENG_MDP_CAMIN) |	\
				 (1LL << CMDQ_ENG_MDP_RDMA0) |	\
				 (1LL << CMDQ_ENG_MDP_RDMA1) |	\
				 (1LL << CMDQ_ENG_MDP_RSZ0) |	\
				 (1LL << CMDQ_ENG_MDP_RSZ1) |	\
				 (1LL << CMDQ_ENG_MDP_RSZ2) |	\
				 (1LL << CMDQ_ENG_MDP_TDSHP0) |	\
				 (1LL << CMDQ_ENG_MDP_TDSHP1) |	\
				 (1LL << CMDQ_ENG_MDP_COLOR0) |	\
				 (1LL << CMDQ_ENG_MDP_WROT0) |	\
				 (1LL << CMDQ_ENG_MDP_WROT1) |	\
				 (1LL << CMDQ_ENG_MDP_WDMA))

#define CMDQ_ENG_DISP_GROUP_BITS	((1LL << CMDQ_ENG_DISP_UFOE) |		\
					 (1LL << CMDQ_ENG_DISP_AAL) |		\
					 (1LL << CMDQ_ENG_DISP_COLOR0) |	\
					 (1LL << CMDQ_ENG_DISP_COLOR1) |	\
					 (1LL << CMDQ_ENG_DISP_RDMA0) |		\
					 (1LL << CMDQ_ENG_DISP_RDMA1) |		\
					 (1LL << CMDQ_ENG_DISP_RDMA2) |		\
					 (1LL << CMDQ_ENG_DISP_WDMA0) |		\
					 (1LL << CMDQ_ENG_DISP_WDMA1) |		\
					 (1LL << CMDQ_ENG_DISP_OVL0) |		\
					 (1LL << CMDQ_ENG_DISP_OVL1) |		\
					 (1LL << CMDQ_ENG_DISP_OVL2) |		\
					 (1LL << CMDQ_ENG_DISP_2L_OVL0) |	\
					 (1LL << CMDQ_ENG_DISP_2L_OVL1) |	\
					 (1LL << CMDQ_ENG_DISP_2L_OVL2) |	\
					 (1LL << CMDQ_ENG_DISP_GAMMA) |		\
					 (1LL << CMDQ_ENG_DISP_MERGE) |		\
					 (1LL << CMDQ_ENG_DISP_SPLIT0) |	\
					 (1LL << CMDQ_ENG_DISP_SPLIT1) |	\
					 (1LL << CMDQ_ENG_DISP_DSI0_VDO) |	\
					 (1LL << CMDQ_ENG_DISP_DSI1_VDO) |	\
					 (1LL << CMDQ_ENG_DISP_DSI0_CMD) |	\
					 (1LL << CMDQ_ENG_DISP_DSI1_CMD) |	\
					 (1LL << CMDQ_ENG_DISP_DSI0) |		\
					 (1LL << CMDQ_ENG_DISP_DSI1) |		\
					 (1LL << CMDQ_ENG_DISP_DPI))

#define CMDQ_ENG_VENC_GROUP_BITS	((1LL << CMDQ_ENG_VIDEO_ENC))

#define CMDQ_ENG_JPEG_GROUP_BITS	((1LL << CMDQ_ENG_JPEG_ENC) |	\
					 (1LL << CMDQ_ENG_JPEG_REMDC) |	\
					 (1LL << CMDQ_ENG_JPEG_DEC))

#define CMDQ_ENG_DPE_GROUP_BITS		(1LL << CMDQ_ENG_DPE)
#define CMDQ_ENG_RSC_GROUP_BITS		(1LL << CMDQ_ENG_RSC)
#define CMDQ_ENG_GEPF_GROUP_BITS	(1LL << CMDQ_ENG_GEPF)

#define CMDQ_ENG_ISP_GROUP_FLAG(flag)   ((flag) & (CMDQ_ENG_ISP_GROUP_BITS))
#define CMDQ_ENG_MDP_GROUP_FLAG(flag)   ((flag) & (CMDQ_ENG_MDP_GROUP_BITS))
#define CMDQ_ENG_DISP_GROUP_FLAG(flag)  ((flag) & (CMDQ_ENG_DISP_GROUP_BITS))
#define CMDQ_ENG_JPEG_GROUP_FLAG(flag)  ((flag) & (CMDQ_ENG_JPEG_GROUP_BITS))
#define CMDQ_ENG_VENC_GROUP_FLAG(flag)	((flag) & (CMDQ_ENG_VENC_GROUP_BITS))
#define CMDQ_ENG_DPE_GROUP_FLAG(flag)	((flag) & (CMDQ_ENG_DPE_GROUP_BITS))
#define CMDQ_ENG_RSC_GROUP_FLAG(flag)	((flag) & (CMDQ_ENG_RSC_GROUP_BITS))
#define CMDQ_ENG_GEPF_GROUP_FLAG(flag)	((flag) & (CMDQ_ENG_GEPF_GROUP_BITS))

#define CMDQ_FOREACH_GROUP(ACTION_struct)\
	ACTION_struct(CMDQ_GROUP_ISP, ISP)	\
	ACTION_struct(CMDQ_GROUP_MDP, MDP)	\
	ACTION_struct(CMDQ_GROUP_DISP, DISP)	\
	ACTION_struct(CMDQ_GROUP_JPEG, JPEG)	\
	ACTION_struct(CMDQ_GROUP_VENC, VENC)	\
	ACTION_struct(CMDQ_GROUP_DPE, DPE)	\
	ACTION_struct(CMDQ_GROUP_RSC, RSC)	\
	ACTION_struct(CMDQ_GROUP_GEPF, GEPF)	\

#define MDP_GENERATE_ENUM(_enum, _string) _enum,

enum CMDQ_GROUP_ENUM {
	CMDQ_FOREACH_GROUP(MDP_GENERATE_ENUM)
	CMDQ_MAX_GROUP_COUNT,	/* ALWAYS keep at the end */
};

#endif				/* __CMDQ_ENGINE_H__ */
