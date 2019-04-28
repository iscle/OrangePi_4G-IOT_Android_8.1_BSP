/*******************************************************************************
*  Copyright Statement:
*  --------------------
*  This software is protected by Copyright and the information contained
*  herein is confidential. The software may not be copied and the information
*  contained herein may not be used or disclosed except with the written
*  permission of MediaTek Inc. (C) 2001
*
*******************************************************************************/

/*******************************************************************************
 *
 * Filename:
 * ---------
 *   mml1_custom_mipi.h
 *
 * Project:
 * --------
 *   MT6735 EVB
 *
 * Description:
 * ------------
 *   MT6735 EVB Multi-Mode Multi-RAT L1 constance definition
 *
 * Author:
 * -------
 *
 *
 *==============================================================================
 *******************************************************************************/
#ifndef  _MML1_CUSTOM_MIPI_H_
#define  _MML1_CUSTOM_MIPI_H_

#include "mml1_mipi_public.h"
#include "mml1_custom_drdi.h"

/* =============================================== */
/*   USID Default Value                            */
/* =============================================== */
#define MIPI_USID_INIT0                0x0000
#define MIPI_USID_ASM0                 0x000B
#define MIPI_USID_ASM1                 0x000A
#define MIPI_USID_PA0                  0x000F
#define MIPI_USID_PA1                  0x000E
#define MIPI_USID_ANT0                 0x0006
#define MIPI_USID_ET                   0x000C

/* =============================================== */
/* The followings define all supported bands' VPA source configurations */
#define VPA_SOURCE_NOT_SUPPORTED    0
#define VPA_SOURCE_HW_V_BATTERY     1
#define VPA_SOURCE_HW_PMIC          2
#define VPA_SOURCE_HW_ETM_SW_APT    3
#define VPA_SOURCE_HW_ETM_SW_ET     4
#define VPA_SOURCE_HW_PMIC_ETM_APT  5

// ETM vendor / chip information definitions   --------------------------------------  Start
#define ETM_NONE_CHIP                 0

#define ETM_MTK_A60935                1

#define ETM_QUANTANCE_Q845            11
#define ETM_QUANTANCE_Q846            12

#define ETM_CHIP_TYPE                 ETM_QUANTANCE_Q846
// ETM vendor / chip information definitions   --------------------------------------  End

extern MML1_MIPI_INITIAL_CW_T  MML1_MIPI_INITIAL_CW[MML1_MIPI_MAX_INITIAL_CW_NUM];
extern MML1_MIPI_USID_CHANGE_T MML1_MIPI_USID_CHANGE_TABLE[MML1_MIPI_MAX_USID_CHANGE_NUM];

   #if (IS_MML1_DRDI_ENABLE && IS_MML1_DRDI_REMAP_ENABLE)
extern MML1_MIPI_INITIAL_CW_T  MML1_MIPI_INITIAL_CW_ReMap_set0[MML1_MIPI_MAX_INITIAL_CW_NUM];
extern MML1_MIPI_USID_CHANGE_T MML1_MIPI_USID_CHANGE_TABLE_ReMap_set0[MML1_MIPI_MAX_USID_CHANGE_NUM];
   #else
extern MML1_MIPI_INITIAL_CW_T  MML1_MIPI_INITIAL_CW_set0[MML1_MIPI_MAX_INITIAL_CW_NUM];
extern MML1_MIPI_INITIAL_CW_T  MML1_MIPI_INITIAL_CW_set1[MML1_MIPI_MAX_INITIAL_CW_NUM];
extern MML1_MIPI_USID_CHANGE_T MML1_MIPI_USID_CHANGE_TABLE_set0[MML1_MIPI_MAX_USID_CHANGE_NUM];
extern MML1_MIPI_USID_CHANGE_T MML1_MIPI_USID_CHANGE_TABLE_set1[MML1_MIPI_MAX_USID_CHANGE_NUM];
   #endif

#endif


