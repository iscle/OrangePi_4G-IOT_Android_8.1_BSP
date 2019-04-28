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
 *   mml1_custom_rf.h
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

#ifndef  _MML1_CUSTOM_RF_H_
#define  _MML1_CUSTOM_RF_H_
/* --------------------------------------------------------------------------- */
/*MT6735*/
/*MT6735*/ /*----------------------------------------------------*/
/*MT6735*/ /*   Enable or disable the clock1, 2, 3, and 4 output */
/*MT6735*/ /*   1 : Enable                                       */
/*MT6735*/ /*   0 : Disable                                      */
/*MT6735*/ /*----------------------------------------------------*/
/*MT6735*/ #define MML1_CLK1_EN                         1 /* CLK1 is enabled for BB */
/*MT6735*/ #define MML1_CLK2_EN                         1
/*MT6735*/ #define MML1_CLK3_EN                         1
/*MT6735*/ #define MML1_CLK4_EN                         1
/*MT6735*/
/*MT6735*/ #if defined(__TAS_FOR_C2K_ONOFF_SUPPORT__)
/*MT6735*/ #define MML1_TAS_FOR_C2K_2GCS_PREFER_EN      0  /* 0: off   1: enable 2G control TAS during 2G CS call*/
/*MT6735*/ #endif

/* --------------------------------------------------------------------------- */
#endif

