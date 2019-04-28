/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2017. All rights reserved.
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
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
package com.mediatek.powerhalmgr;

import android.os.Looper;

public class PowerHalMgr {
    public static final int MTK_HINT_ALWAYS_ENABLE              = 0x0FFFFFFF;

    public static final int CMD_GET_CLUSTER_NUM                 = 1;
    public static final int CMD_GET_CLUSTER_CPU_NUM             = 2;
    public static final int CMD_GET_CLUSTER_CPU_FREQ_MIN        = 3;
    public static final int CMD_GET_CLUSTER_CPU_FREQ_MAX        = 4;
    public static final int CMD_GET_GPU_FREQ_COUNT              = 5;
    public static final int CMD_GET_FOREGROUND_PID              = 6;
    public static final int CMD_GET_FOREGROUND_TYPE             = 7;
    public static final int CMD_GET_WALT_FOLLOW                 = 8;
    public static final int CMD_GET_WALT_DURATION               = 9;

    /* cpu */
    public static final int CMD_SET_CLUSTER_CPU_CORE_MIN        = 1;
    public static final int CMD_SET_CLUSTER_CPU_CORE_MAX        = 2;
    public static final int CMD_SET_CLUSTER_CPU_FREQ_MIN        = 3;
    public static final int CMD_SET_CLUSTER_CPU_FREQ_MAX        = 4;
    public static final int CMD_SET_CPU_PERF_MODE               = 5;

    /* gpu */
    public static final int CMD_SET_GPU_FREQ_MIN                = 6;
    public static final int CMD_SET_GPU_FREQ_MAX                = 7;

    /* vcore */
    public static final int CMD_SET_VCORE_BW_THRES              = 8;
    public static final int CMD_SET_VCORE_BW_ENABLED            = 9;
    public static final int CMD_SET_VCORE_MIN                   = 10;

    /* state */
    public static final int CMD_SET_SCREEN_OFF_STATE            = 11;

    /* DVFS */
    public static final int CMD_SET_CPUFREQ_HISPEED_FREQ        = 12;
    public static final int CMD_SET_CPUFREQ_MIN_SAMPLE_TIME     = 13;
    public static final int CMD_SET_CPUFREQ_ABOVE_HISPEED_DELAY = 14;
    public static final int CMD_SET_DVFS_POWER_MODE             = 15;

    /* HPS */
    public static final int CMD_SET_HPS_UP_THRESHOLD            = 16;
    public static final int CMD_SET_HPS_DOWN_THRESHOLD          = 17;
    public static final int CMD_SET_HPS_UP_TIMES                = 18;
    public static final int CMD_SET_HPS_DOWN_TIMES              = 19;
    public static final int CMD_SET_HPS_RUSH_BOOST              = 20;
    public static final int CMD_SET_HPS_HEAVY_TASK              = 21;
    public static final int CMD_SET_HPS_POWER_MODE              = 22;

    /* PPM */
    public static final int CMD_SET_PPM_ROOT_CLUSTER            = 23;
    public static final int CMD_SET_PPM_NORMALIZED_PERF_INDEX   = 24;
    public static final int CMD_SET_PPM_MODE                    = 25;
    public static final int CMD_SET_PPM_HICA_VAR                = 26;

    /* sched */
    public static final int CMD_SET_SCHED_HTASK_THRESH          = 27;
    public static final int CMD_SET_SCHED_AVG_HTASK_AC          = 28;
    public static final int CMD_SET_SCHED_AVG_HTASK_THRESH      = 29;
    public static final int CMD_SET_SCHED_MODE                  = 30;
    public static final int CMD_SET_IDLE_PREFER                 = 31;
    public static final int CMD_SET_SCHED_LB_ENABLE             = 32;
    public static final int CMD_SET_GLOBAL_CPUSET               = 33;
    public static final int CMD_SET_ROOT_BOOST_VALUE            = 34;
    public static final int CMD_SET_TA_BOOST_VALUE              = 35;
    public static final int CMD_SET_FG_BOOST_VALUE              = 36;
    public static final int CMD_SET_BG_BOOST_VALUE              = 37;

    /* customized */
    public static final int CMD_SET_PACK_BOOST_MODE             = 38;
    public static final int CMD_SET_PACK_BOOST_TIMEOUT          = 39;
    public static final int CMD_SET_DFPS                        = 40;
    public static final int CMD_SET_SPORTS_MODE                 = 41;
    public static final int CMD_SET_PPM_HOLD_TIME_L_ONLY        = 42;
    public static final int CMD_SET_VCORE_BW_THRES_DDR3         = 43;
    public static final int CMD_SET_VCORE_MIN_DDR3              = 44;
    public static final int CMD_SET_VCORE                       = 45;
    public static final int CMD_SET_PPM_LIMIT_BIG               = 46;
    public static final int CMD_SET_PPM_SPORTS_MODE             = 47;
    public static final int CMD_SET_PPM_HOLD_TIME_LL_ONLY       = 48;
    public static final int CMD_SET_HPS_RBOOST_THRESH           = 49;
    public static final int CMD_SET_SMART_FORCE_ISOLATE         = 50;
    public static final int CMD_SET_STUNE_THRESH                = 51;
    public static final int CMD_SET_DCM_MODE                    = 52;
    public static final int CMD_SET_DCS_MODE                    = 53;
    public static final int CMD_SET_FSTB_FPS                    = 54;
    public static final int CMD_SET_FPSGO_ENABLE                = 55;
    public static final int CMD_SET_FSTB_FORCE_VAG              = 56;
    public static final int CMD_SET_GED_BENCHMARK_ON            = 57;
    public static final int CMD_SET_GX_BOOST                    = 58;
    public static final int CMD_SET_FBT_FLOOR_BOUND             = 59;
    public static final int CMD_SET_FBT_KMIN                    = 60;
    public static final int CMD_SET_VIDEO_MODE                  = 61;
    public static final int CMD_SET_OPP_DDR                     = 62;
    public static final int CMD_SET_EXT_LAUNCH_MON              = 63;
    public static final int CMD_SET_WALT_FOLLOW                 = 64;
    public static final int CMD_SET_MTK_PREFER_IDLE             = 65;
    public static final int CMD_SET_STUNE_TA_PERFER_IDLE        = 66;
    public static final int CMD_SET_STUNE_FG_PERFER_IDLE        = 67;
    public static final int CMD_SET_DISP_DECOUPLE               = 68;
    public static final int CMD_SET_IO_BOOST_VALUE              = 69;
    public static final int CMD_SET_WIPHY_CAM                   = 70;
    public static final int CMD_SET_SPCOND_RESV_I               = 71;
    public static final int CMD_SET_SPCOND_RESV_II              = 72;
    public static final int CMD_SET_SPCOND_RESV_III             = 73;
    public static final int CMD_SET_SPCOND_RESV_IV              = 74;
    public static final int CMD_SET_SPCOND_RESV_V               = 75;
    public static final int CMD_SET_SPCOND_RESV_VI              = 76;
    public static final int CMD_SET_SPCOND_RESV_VII             = 77;
    public static final int CMD_SET_SPCOND_RESV_VIII            = 78;
    public static final int CMD_SET_SPCOND_RESV_IX              = 79;
    public static final int CMD_SET_SPCOND_RESV_X               = 80;
    public static final int CMD_SET_SPCOND_RESV_XI              = 81;
    public static final int CMD_SET_SPCOND_RESV_XII             = 82;
    public static final int CMD_SET_SPCOND_RESV_XIII            = 83;
    public static final int CMD_SET_SPCOND_RESV_XIV             = 84;
    public static final int CMD_SET_SPCOND_RESV_XV              = 85;
    public static final int CMD_SET_SPCOND_RESV_XVI             = 86;
    public static final int CMD_SET_SPCOND_RESV_XVII            = 87;
    public static final int CMD_SET_SPCOND_RESV_XVIII           = 88;
    public static final int CMD_SET_SPCOND_RESV_XIX             = 89;
    public static final int CMD_SET_SPCOND_RESV_XX              = 90;

    public static final int STATE_PAUSED                        = 0;
    public static final int STATE_RESUMED                       = 1;
    public static final int STATE_DESTORYED                     = 2;
    public static final int STATE_DEAD                          = 3;
    public static final int STATE_STOPPED                       = 4;

    public static final int SCREEN_OFF_DISABLE                  = 0;
    public static final int SCREEN_OFF_ENABLE                   = 1;
    public static final int SCREEN_OFF_WAIT_RESTORE             = 2;

    public static final int DFPS_MODE_DEFAULT                   = 0;
    public static final int DFPS_MODE_FRR                       = 1;
    public static final int DFPS_MODE_ARR                       = 2;
    public static final int DFPS_MODE_INTERNAL_SW               = 3;
    public static final int DFPS_MODE_MAXIMUM                   = 4;

    public static final int DISP_MODE_DEFAULT                   = 0;
    public static final int DISP_MODE_EN                        = 1;
    public static final int DISP_MODE_NUM                       = 2;

    public int scnReg() { return -1; }
    public void scnConfig(int handle, int cmd, int param_1, int param_2, int param_3, int param_4) {}
    public void scnUnreg(int handle) {}
    public void scnEnable(int handle, int timeout) {}
    public void scnDisable(int handle) {}
}
