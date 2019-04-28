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
 *   lte_custom_rf.h
 *
 * Project:
 * --------
 *   MT6169
 *
 * Description:
 * ------------
 *   MT6169 LTE FDD/TDD RF
 *
 * Author:
 * -------
 * -------
 *

 *******************************************************************************/
#ifndef  _LTE_CUSTOM_RF_H_
#define  _LTE_CUSTOM_RF_H_
/* ------------------------------------------------------------------------- */
#if !defined(MT6169_LTE_RF)
   #error "rf files mismatch with compile option!"
#endif

#include "el1_rf_public.h"

/*MT6169*//*--------------------------------------------------------*/
/*MT6169*//*   FDD Mode Event Timing Define                         */
/*MT6169*//*--------------------------------------------------------*/
/*MT6169*/#define  TC_FPR1               MICROSECOND_TO_26M(105)
/*MT6169*/#define  TC_FPR2               MICROSECOND_TO_26M(26)
/*MT6169*/#define  TC_FPR3               MICROSECOND_TO_26M(15)
/*MT6169*/
/*MT6169*/#define  TC_FPT1               MICROSECOND_TO_26M(9)
/*MT6169*/#define  TC_FPT2               MICROSECOND_TO_26M(8)
/*MT6169*/#define  TC_FPT3               MICROSECOND_TO_26M(5)
/*MT6169*/
/*MT6169*//*--------------------------------------------------------*/
/*MT6169*//*   TDD Mode Event Timing Define                         */
/*MT6169*//*--------------------------------------------------------*/
/*MT6169*/#define  TC_TPR1               MICROSECOND_TO_26M(21)
/*MT6169*/#define  TC_TPR2               MICROSECOND_TO_26M(20)
/*MT6169*/#define  TC_TPR3               MICROSECOND_TO_26M(1)
/*MT6169*/
/*MT6169*/#define  TC_TPT1               MICROSECOND_TO_26M(5)
/*MT6169*/#define  TC_TPT2               MICROSECOND_TO_26M(4)
/*MT6169*/#define  TC_TPT3               MICROSECOND_TO_26M(1)
/*MT6169*/
/*MT6169*/
/*MT6169*//*------------------------------------------------------*/
/*MT6169*//*   define  BPI data for MT6169                        */
/*MT6169*//*------------------------------------------------------*/
/*MT6169*//*    PRCB : bit  BPI   pin function                    */
/*MT6169*//*            0    0    Reserved                        */
/*MT6169*//*            1    1    Reserved                        */
/*MT6169*//*            2    2    Reserved                        */
/*MT6169*//*            3    3    Reserved                        */
/*MT6169*//*            4    4    Reserved                        */
/*MT6169*//*            5    5    Reserved                        */
/*MT6169*//*            6    6    Reserved                        */
/*MT6169*//*            7    7    Reserved                        */
/*MT6169*//*            8    8    Reserved                        */
/*MT6169*//*            9    9    Reserved                        */
/*MT6169*//*            10   10   Reserved                        */
/*MT6169*//*            11   11   Reserved                        */
/*MT6169*//*            12   12   Reserved                        */
/*MT6169*//*            13   13   Reserved                        */
/*MT6169*//*            14   14   Reserved                        */
/*MT6169*//*            15   15   Reserved                        */
/*MT6169*//*            16   16   Reserved                        */
/*MT6169*//*            17   17   Reserved                        */
/*MT6169*//*            18   18   Reserved                        */
/*MT6169*//*            19   19   Reserved                        */
/*MT6169*//*            20   20   Reserved                        */
/*MT6169*//*            21   21   Reserved                        */
/*MT6169*//*            22   22   Reserved                        */
/*MT6169*//*            23   23   Reserved                        */
/*MT6169*//*            24   24   Reserved                        */
/*MT6169*//*            25   25   Reserved                        */
/*MT6169*//*            26   26   Reserved                        */
/*MT6169*//*            27   27   Reserved                        */
/*MT6169*//*------------------------------------------------------*/
/*MT6169*/
/*MT6169*///Do not change LTE_PDATA_OFF setting!!
/*MT6169*/#define    LTE_PDATA_OFF            0x00000000
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND1 Start --------------------*/
/*MT6169*/#define    PDATA_LTE_Band1_PR1      0x00000005	
/*MT6169*/#define    PDATA_LTE_Band1_PR2      0x00000005
/*MT6169*/#define    PDATA_LTE_Band1_PR3      LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define    PDATA_LTE_Band1_PT1      0x00000001	
/*MT6169*/#define    PDATA_LTE_Band1_PT2      0x00000001	
/*MT6169*/#define    PDATA_LTE_Band1_PT3      LTE_PDATA_OFF
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND3 Start --------------------*/
/*MT6169*/#define    PDATA_LTE_Band3_PR1      0x00000080	
/*MT6169*/#define    PDATA_LTE_Band3_PR2      0x00000080	
/*MT6169*/#define    PDATA_LTE_Band3_PR3      LTE_PDATA_OFF
/*MT6169*/                         
/*MT6169*/#define    PDATA_LTE_Band3_PT1      0x00000000	
/*MT6169*/#define    PDATA_LTE_Band3_PT2      0x00000000	
/*MT6169*/#define    PDATA_LTE_Band3_PT3      LTE_PDATA_OFF
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND5 Start --------------------*/
/*MT6169*/#define    PDATA_LTE_Band5_PR1      0x00000000	
/*MT6169*/#define    PDATA_LTE_Band5_PR2      0x00000000	
/*MT6169*/#define    PDATA_LTE_Band5_PR3      LTE_PDATA_OFF
/*MT6169*/                         
/*MT6169*/#define    PDATA_LTE_Band5_PT1      0x00000000	
/*MT6169*/#define    PDATA_LTE_Band5_PT2      0x00000000	
/*MT6169*/#define    PDATA_LTE_Band5_PT3      LTE_PDATA_OFF
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND7 Start --------------------*/
/*MT6169*/#define    PDATA_LTE_Band7_PR1      0x00000100	
/*MT6169*/#define    PDATA_LTE_Band7_PR2      0x00000100	
/*MT6169*/#define    PDATA_LTE_Band7_PR3      LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define    PDATA_LTE_Band7_PT1      0x00000000	
/*MT6169*/#define    PDATA_LTE_Band7_PT2      0x00000000	
/*MT6169*/#define    PDATA_LTE_Band7_PT3      LTE_PDATA_OFF
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND8 Start --------------------*/
/*MT6169*/#define    PDATA_LTE_Band8_PR1     0x00004000	
/*MT6169*/#define    PDATA_LTE_Band8_PR2     0x00004000	
/*MT6169*/#define    PDATA_LTE_Band8_PR3     LTE_PDATA_OFF
/*MT6169*/                          
/*MT6169*/#define    PDATA_LTE_Band8_PT1     0x00000000	
/*MT6169*/#define    PDATA_LTE_Band8_PT2     0x00000000	
/*MT6169*/#define    PDATA_LTE_Band8_PT3     LTE_PDATA_OFF
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND27 Start --------------------*/
/*MT6169*/#define    PDATA_LTE_Band27_PR1     0x00000000
/*MT6169*/#define    PDATA_LTE_Band27_PR2     0x00000000	
/*MT6169*/#define    PDATA_LTE_Band27_PR3     LTE_PDATA_OFF
/*MT6169*/                          
/*MT6169*/#define    PDATA_LTE_Band27_PT1     0x00000000	
/*MT6169*/#define    PDATA_LTE_Band27_PT2     0x00000000	
/*MT6169*/#define    PDATA_LTE_Band27_PT3     LTE_PDATA_OFF
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND28 Start --------------------*/
/*MT6169*/#define    PDATA_LTE_Band28_PR1     0x00000825	
/*MT6169*/#define    PDATA_LTE_Band28_PR2     0x00000825	
/*MT6169*/#define    PDATA_LTE_Band28_PR3     LTE_PDATA_OFF
/*MT6169*/                          
/*MT6169*/#define    PDATA_LTE_Band28_PT1     0x00000021	
/*MT6169*/#define    PDATA_LTE_Band28_PT2     0x00000021	
/*MT6169*/#define    PDATA_LTE_Band28_PT3     LTE_PDATA_OFF
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND28_2 Start --------------------*/
/*MT6169*/#define    PDATA_LTE_Band28_2_PR1     0x00000821	
/*MT6169*/#define    PDATA_LTE_Band28_2_PR2     0x00000821	
/*MT6169*/#define    PDATA_LTE_Band28_2_PR3     LTE_PDATA_OFF
/*MT6169*/                          
/*MT6169*/#define    PDATA_LTE_Band28_2_PT1     0x00000021	
/*MT6169*/#define    PDATA_LTE_Band28_2_PT2     0x00000021	
/*MT6169*/#define    PDATA_LTE_Band28_2_PT3     LTE_PDATA_OFF
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND38 Start --------------------*/
/*MT6169*/#define    PDATA_LTE_Band38_PR1     0x00000200	
/*MT6169*/#define    PDATA_LTE_Band38_PR2     0x00000200	
/*MT6169*/#define    PDATA_LTE_Band38_PR3     LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define    PDATA_LTE_Band38_PT1     0x00000000	
/*MT6169*/#define    PDATA_LTE_Band38_PT2     0x00000000	
/*MT6169*/#define    PDATA_LTE_Band38_PT3     LTE_PDATA_OFF
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND39 Start --------------------*/
/*MT6169*/#define    PDATA_LTE_Band39_PR1     0x00000281	
/*MT6169*/#define    PDATA_LTE_Band39_PR2     0x00000281	
/*MT6169*/#define    PDATA_LTE_Band39_PR3     LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define    PDATA_LTE_Band39_PT1     0x00000001	
/*MT6169*/#define    PDATA_LTE_Band39_PT2     0x00000001	
/*MT6169*/#define    PDATA_LTE_Band39_PT3     LTE_PDATA_OFF
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND40 Start --------------------*/
/*MT6169*/#define    PDATA_LTE_Band40_PR1     0x0000034C	
/*MT6169*/#define    PDATA_LTE_Band40_PR2     0x0000034C	
/*MT6169*/#define    PDATA_LTE_Band40_PR3     LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define    PDATA_LTE_Band40_PT1     0x00000000	
/*MT6169*/#define    PDATA_LTE_Band40_PT2     0x00000000	
/*MT6169*/#define    PDATA_LTE_Band40_PT3     LTE_PDATA_OFF
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND41 Start --------------------*/
/*MT6169*/#define    PDATA_LTE_Band41_PR1     0x00000200	
/*MT6169*/#define    PDATA_LTE_Band41_PR2     0x00000200	
/*MT6169*/#define    PDATA_LTE_Band41_PR3     LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define    PDATA_LTE_Band41_PT1     0x00000000	
/*MT6169*/#define    PDATA_LTE_Band41_PT2     0x00000000	
/*MT6169*/#define    PDATA_LTE_Band41_PT3     LTE_PDATA_OFF
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND41_2 Start --------------------*/
/*MT6169*/#define    PDATA_LTE_Band41_2_PR1     0x00000200	
/*MT6169*/#define    PDATA_LTE_Band41_2_PR2     0x00000200	
/*MT6169*/#define    PDATA_LTE_Band41_2_PR3     LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define    PDATA_LTE_Band41_2_PT1     0x00000000	
/*MT6169*/#define    PDATA_LTE_Band41_2_PT2     0x00000000	
/*MT6169*/#define    PDATA_LTE_Band41_2_PT3     LTE_PDATA_OFF
/*MT6169*/
/*MT6169*//*------------------------------------------------------*/
/*MT6169*//*  RX & RXD IO Port Definition & supported freq range  */
/*MT6169*//*  HB1-HB3 => freq: 1805MHz ~ 2690MHz                  */
/*MT6169*//*  MB1,MB2 => freq: 1475MHz ~ 2170MHz                  */
/*MT6169*//*  LB1-LB3 => freq: 734MHz ~ 960MHz                    */
/*MT6169*//*------------------------------------------------------*/
/*MT6169*/#define    LTE_Band1_RX_IO_SEL          RX_IO_HB3	
/*MT6169*/#define    LTE_Band3_RX_IO_SEL          RX_IO_HB3
/*MT6169*/#define    LTE_Band5_RX_IO_SEL          RX_IO_LB2	
/*MT6169*/#define    LTE_Band7_RX_IO_SEL          RX_IO_HB2
/*MT6169*/#define    LTE_Band8_RX_IO_SEL          RX_IO_LB1
/*MT6169*/#define    LTE_Band27_RX_IO_SEL         RX_IO_LB3
/*MT6169*/#define    LTE_Band28_RX_IO_SEL         RX_IO_LB3
/*MT6169*/#define    LTE_Band28_2_RX_IO_SEL       RX_IO_LB3
/*MT6169*/#define    LTE_Band38_RX_IO_SEL         RX_IO_HB1
/*MT6169*/#define    LTE_Band39_RX_IO_SEL         RX_IO_MB2
/*MT6169*/#define    LTE_Band40_RX_IO_SEL         RX_IO_HB1
/*MT6169*/#define    LTE_Band41_RX_IO_SEL         RX_IO_HB1
/*MT6169*/#define    LTE_Band41_2_RX_IO_SEL       RX_IO_HB2

/*MT6169*/
/*MT6169*/#define    LTE_Band1_RXD_IO_SEL         RXD_IO_HB1
/*MT6169*/#define    LTE_Band3_RXD_IO_SEL         RXD_IO_MB1
/*MT6169*/#define    LTE_Band5_RXD_IO_SEL         RXD_IO_LB1
/*MT6169*/#define    LTE_Band7_RXD_IO_SEL         RXD_IO_HB2
/*MT6169*/#define    LTE_Band8_RXD_IO_SEL         RXD_IO_LB3
/*MT6169*/#define    LTE_Band27_RXD_IO_SEL        RXD_IO_LB2
/*MT6169*/#define    LTE_Band28_RXD_IO_SEL        RXD_IO_LB2
/*MT6169*/#define    LTE_Band28_2_RXD_IO_SEL      RXD_IO_LB2
/*MT6169*/#define    LTE_Band38_RXD_IO_SEL        RXD_IO_HB3
/*MT6169*/#define    LTE_Band39_RXD_IO_SEL        RXD_IO_MB2
/*MT6169*/#define    LTE_Band40_RXD_IO_SEL        RXD_IO_HB3
/*MT6169*/#define    LTE_Band41_RXD_IO_SEL        RXD_IO_HB3
/*MT6169*/#define    LTE_Band41_2_RXD_IO_SEL      RXD_IO_HB3

/*MT6169*/
/*MT6169*//*------------------------------------------------------*/
/*MT6169*//*  TX IO Port Definition & supported freq range        */
/*MT6169*//*  HB1,HB2 => freq: 1710MHz ~ 2690MHz                  */
/*MT6169*//*  MB1,MB2 => freq: 1400MHz ~ 2025MHz                  */
/*MT6169*//*  LB1-LB4 => freq: 699MHz ~ 915MHz                    */
/*MT6169*//*------------------------------------------------------*/
/*MT6169*/#define    LTE_Band1_TX_IO_SEL          TX_IO_MB2
/*MT6169*/#define    LTE_Band3_TX_IO_SEL          TX_IO_MB2
/*MT6169*/#define    LTE_Band5_TX_IO_SEL          TX_IO_LB2
/*MT6169*/#define    LTE_Band7_TX_IO_SEL          TX_IO_HB1
/*MT6169*/#define    LTE_Band8_TX_IO_SEL          TX_IO_LB2
/*MT6169*/#define    LTE_Band27_TX_IO_SEL         TX_IO_LB2
/*MT6169*/#define    LTE_Band28_TX_IO_SEL         TX_IO_LB2
/*MT6169*/#define    LTE_Band28_2_TX_IO_SEL       TX_IO_LB2
/*MT6169*/#define    LTE_Band38_TX_IO_SEL         TX_IO_HB1
/*MT6169*/#define    LTE_Band39_TX_IO_SEL         TX_IO_MB2	
/*MT6169*/#define    LTE_Band40_TX_IO_SEL         TX_IO_HB1
/*MT6169*/#define    LTE_Band41_TX_IO_SEL         TX_IO_HB1
/*MT6169*/#define    LTE_Band41_2_TX_IO_SEL       TX_IO_HB1
/*MT6169*/
/*MT6169*//*------------------------------------------------------*/
/*MT6169*//*  BAND_INDICATOR1 ~ BAND_INDICATOR14                  */
/*MT6169*//*------------------------------------------------------*/
/*MT6169*/#define    BAND_INDICATOR1              LTE_Band1
/*MT6169*/#define    BAND_INDICATOR2              LTE_Band3
/*MT6169*/#define    BAND_INDICATOR3              LTE_Band5
/*MT6169*/#define    BAND_INDICATOR4              LTE_Band7
/*MT6169*/#define    BAND_INDICATOR5              LTE_Band8
/*MT6169*/#define    BAND_INDICATOR6              LTE_Band28
/*MT6169*/#define    BAND_INDICATOR7              LTE_Band38
/*MT6169*/#define    BAND_INDICATOR8              LTE_Band39
/*MT6169*/#define    BAND_INDICATOR9              LTE_Band40
/*MT6169*/#define    BAND_INDICATOR10             LTE_Band41
/*MT6169*/#define    BAND_INDICATOR11             LTE_BandNone
/*MT6169*/#define    BAND_INDICATOR12             LTE_BandNone
/*MT6169*/#define    BAND_INDICATOR13             LTE_BandNone
/*MT6169*/#define    BAND_INDICATOR14             LTE_BandNone
/*MT6169*/
/*MT6169*/#if defined(__CDMA2000_RAT__)
/*MT6169*//*------------------------------------------------------*/
/*MT6169*//*  BAND_INDICATOR1 ~ BAND_INDICATOR14 for SVLTE        */
/*MT6169*//*------------------------------------------------------*/
/*MT6169*/#define    BAND_SVLTE_INDICATOR1        LTE_Band1
/*MT6169*/#define    BAND_SVLTE_INDICATOR2        LTE_Band3
/*MT6169*/#define    BAND_SVLTE_INDICATOR3        LTE_Band5
/*MT6169*/#define    BAND_SVLTE_INDICATOR4        LTE_Band7
/*MT6169*/#define    BAND_SVLTE_INDICATOR5        LTE_BandNone
/*MT6169*/#define    BAND_SVLTE_INDICATOR6        LTE_BandNone
/*MT6169*/#define    BAND_SVLTE_INDICATOR7        LTE_Band38
/*MT6169*/#define    BAND_SVLTE_INDICATOR8        LTE_Band39
/*MT6169*/#define    BAND_SVLTE_INDICATOR9        LTE_Band40
/*MT6169*/#define    BAND_SVLTE_INDICATOR10       LTE_Band41
/*MT6169*/#define    BAND_SVLTE_INDICATOR11       LTE_BandNone
/*MT6169*/#define    BAND_SVLTE_INDICATOR12       LTE_BandNone
/*MT6169*/#define    BAND_SVLTE_INDICATOR13       LTE_BandNone
/*MT6169*/#define    BAND_SVLTE_INDICATOR14       LTE_BandNone
/*MT6169*/#endif
/*MT6169*/
/*MT6169*//*-------------------------------------*/
/*MT6169*//*         PA Related Config           */
/*MT6169*//*-------------------------------------*/
/*MT6169*/#define NUM_PA_MODE              3
/*MT6169*/#define NUM_HYSTERESIS           2
/*MT6169*/
/*MT6169*//*-------------------------------------------------------------------*/
/*MT6169*//*  MPR value for each band setting: 36.101 6.2.3 6.2.5 with S(8.8)  */
/*MT6169*//*-------------------------------------------------------------------*/
/*MT6169*///* ------------- MPR_BAND1 Start --------------------*/
/*MT6169*/#define LTE_Band1_MPR_QPSK1      0x0100 // 0x0100
/*MT6169*/#define LTE_Band1_MPR_16QAM0     0x0100 // 0x0100
/*MT6169*/#define LTE_Band1_MPR_16QAM1     0x0200 // 0x0200
/*MT6169*/#define LTE_Band1_DELTA_TC0      0x0000 // 0x0180
/*MT6169*/#define LTE_Band1_DELTA_TC1      0x0000 // 0x0180
/*MT6169*/
/*MT6169*///* ------------- MPR_BAND2 Start --------------------*/
/*MT6169*/#define LTE_Band2_MPR_QPSK1      0x0100 // 0x0100
/*MT6169*/#define LTE_Band2_MPR_16QAM0     0x0100 // 0x0100
/*MT6169*/#define LTE_Band2_MPR_16QAM1     0x0200 // 0x0200
/*MT6169*/#define LTE_Band2_DELTA_TC0      0x0000 // 0x0180
/*MT6169*/#define LTE_Band2_DELTA_TC1      0x0000 // 0x0180
/*MT6169*/
/*MT6169*///* ------------- MPR_BAND3 Start --------------------*/
/*MT6169*/#define LTE_Band3_MPR_QPSK1      0x0100
/*MT6169*/#define LTE_Band3_MPR_16QAM0     0x0100
/*MT6169*/#define LTE_Band3_MPR_16QAM1     0x0200
/*MT6169*/#define LTE_Band3_DELTA_TC0      0x0000
/*MT6169*/#define LTE_Band3_DELTA_TC1      0x0000
/*MT6169*/
/*MT6169*/
/*MT6169*///* ------------- MPR_BAND5 Start --------------------*/
/*MT6169*/#define LTE_Band5_MPR_QPSK1      0x0100 // 0x0100
/*MT6169*/#define LTE_Band5_MPR_16QAM0     0x0100 // 0x0100
/*MT6169*/#define LTE_Band5_MPR_16QAM1     0x0200 // 0x0200
/*MT6169*/#define LTE_Band5_DELTA_TC0      0x0000 // 0x0180
/*MT6169*/#define LTE_Band5_DELTA_TC1      0x0000 // 0x0180
/*MT6169*/
/*MT6169*///* ------------- MPR_BAND7 Start --------------------*/
/*MT6169*/#define LTE_Band7_MPR_QPSK1      0x0100
/*MT6169*/#define LTE_Band7_MPR_16QAM0     0x0100
/*MT6169*/#define LTE_Band7_MPR_16QAM1     0x0200
/*MT6169*/#define LTE_Band7_DELTA_TC0      0x0000
/*MT6169*/#define LTE_Band7_DELTA_TC1      0x0000
/*MT6169*/
/*MT6169*///* ------------- MPR_BAND8 Start --------------------*/
/*MT6169*/#define LTE_Band8_MPR_QPSK1      0x0100
/*MT6169*/#define LTE_Band8_MPR_16QAM0     0x0100
/*MT6169*/#define LTE_Band8_MPR_16QAM1     0x0200
/*MT6169*/#define LTE_Band8_DELTA_TC0      0x0000
/*MT6169*/#define LTE_Band8_DELTA_TC1      0x0000
/*MT6169*/
/*MT6169*///* ------------- MPR_BAND17 Start -------------------*/
/*MT6169*/#define LTE_Band17_MPR_QPSK1     0x0100
/*MT6169*/#define LTE_Band17_MPR_16QAM0    0x0100
/*MT6169*/#define LTE_Band17_MPR_16QAM1    0x0200
/*MT6169*/#define LTE_Band17_DELTA_TC0     0x0000
/*MT6169*/#define LTE_Band17_DELTA_TC1     0x0000
/*MT6169*/
/*MT6169*///* ------------- MPR_BAND20 Start -------------------*/
/*MT6169*/#define LTE_Band20_MPR_QPSK1     0x0100
/*MT6169*/#define LTE_Band20_MPR_16QAM0    0x0100
/*MT6169*/#define LTE_Band20_MPR_16QAM1    0x0200
/*MT6169*/#define LTE_Band20_DELTA_TC0     0x0000
/*MT6169*/#define LTE_Band20_DELTA_TC1     0x0000
/*MT6169*/
/*MT6169*///* ------------- MPR_BAND27 Start --------------------*/
/*MT6169*/#define LTE_Band27_MPR_QPSK1      0x0100
/*MT6169*/#define LTE_Band27_MPR_16QAM0     0x0100
/*MT6169*/#define LTE_Band27_MPR_16QAM1     0x0200
/*MT6169*/#define LTE_Band27_DELTA_TC0      0x0000
/*MT6169*/#define LTE_Band27_DELTA_TC1      0x0000
/*MT6169*/
/*MT6169*///* ------------- MPR_BAND28 Start --------------------*/
/*MT6169*/#define LTE_Band28_MPR_QPSK1      0x0100
/*MT6169*/#define LTE_Band28_MPR_16QAM0     0x0100
/*MT6169*/#define LTE_Band28_MPR_16QAM1     0x0200
/*MT6169*/#define LTE_Band28_DELTA_TC0      0x0000
/*MT6169*/#define LTE_Band28_DELTA_TC1      0x0000
/*MT6169*/
/*MT6169*///* ------------- MPR_BAND38 Start -------------------*/
/*MT6169*/#define LTE_Band38_MPR_QPSK1     0x0100
/*MT6169*/#define LTE_Band38_MPR_16QAM0    0x0100
/*MT6169*/#define LTE_Band38_MPR_16QAM1    0x0200
/*MT6169*/#define LTE_Band38_DELTA_TC0     0x0000
/*MT6169*/#define LTE_Band38_DELTA_TC1     0x0000
/*MT6169*/
/*MT6169*///* ------------- MPR_BAND39 Start -------------------*/
/*MT6169*/#define LTE_Band39_MPR_QPSK1     0x0100
/*MT6169*/#define LTE_Band39_MPR_16QAM0    0x0100
/*MT6169*/#define LTE_Band39_MPR_16QAM1    0x0200
/*MT6169*/#define LTE_Band39_DELTA_TC0     0x0000
/*MT6169*/#define LTE_Band39_DELTA_TC1     0x0000
/*MT6169*/
/*MT6169*///* ------------- MPR_BAND40 Start -------------------*/
/*MT6169*/#define LTE_Band40_MPR_QPSK1     0x0100
/*MT6169*/#define LTE_Band40_MPR_16QAM0    0x0100
/*MT6169*/#define LTE_Band40_MPR_16QAM1    0x0200
/*MT6169*/#define LTE_Band40_DELTA_TC0     0x0000
/*MT6169*/#define LTE_Band40_DELTA_TC1     0x0000
/*MT6169*/
/*MT6169*///* ------------- MPR_BAND41 Start -------------------*/
/*MT6169*/#define LTE_Band41_MPR_QPSK1     0x0100
/*MT6169*/#define LTE_Band41_MPR_16QAM0    0x0100
/*MT6169*/#define LTE_Band41_MPR_16QAM1    0x0200
/*MT6169*/#define LTE_Band41_DELTA_TC0     0x0000
/*MT6169*/#define LTE_Band41_DELTA_TC1     0x0000
/*MT6169*/
/*MT6169*//* ------------- AMPR Value -------------------*/
/*MT6169*/#define LTE_Band1_NS5                                                                                                        \
/*MT6169*//*NS5_101520*/                                                                                                               \
/*MT6169*/{    0x0100,         0,         0,         0,         0,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0,         0,         0,         0,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0}
/*MT6169*/
/*MT6169*/#define LTE_Band2_NS3                                                                                                        \
/*MT6169*//* NS3_03  ,    NS3_05,    NS3_10,    NS3_15,    NS3_20*/                                                                    \
/*MT6169*/{    0x0100,    0x0100,    0x0100,    0x0100,    0x0100,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0,         0,         0,         0,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0}
/*MT6169*/
/*MT6169*/#define LTE_Band4_NS3                                                                                                        \
/*MT6169*//* NS3_03  ,    NS3_05,    NS3_10,    NS3_15,    NS3_20*/                                                                    \
/*MT6169*/{    0x0100,    0x0100,    0x0100,    0x0100,    0x0100,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0,         0,         0,         0,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0}
/*MT6169*/
/*MT6169*/#define LTE_Band10_NS3                                                                                                       \
/*MT6169*//* NS3_03  ,    NS3_05,    NS3_10,    NS3_15,    NS3_20*/                                                                    \
/*MT6169*/{    0x0100,    0x0100,    0x0100,    0x0100,    0x0100,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0,         0,         0,         0,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0}
/*MT6169*/
/*MT6169*/#define LTE_Band13_NS7                                                                                                       \
/*MT6169*//*NS7_10_A0, NS7_10_A1, NS7_10_B0, NS7_10_B1, NS7_10_C*/                                                                     \
/*MT6169*/{    0x0800,    0x0C00,    0x0C00,    0x0600,    0x0300,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0,         0,         0,         0,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0}
/*MT6169*/
/*MT6169*/#define LTE_Band19_NS8                                                                                                       \
/*MT6169*//*  NS8_1015*/                                                                                                               \
/*MT6169*/{    0x0300,         0,         0,         0,         0,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0,         0,         0,         0,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0}
/*MT6169*/
/*MT6169*/#define LTE_Band20_NS10                                                                                                      \
/*MT6169*//*  NS10_15A, NS10_20A*/                                                                                                 \
/*MT6169*/{    0x0200,    0x0500,         0,         0,         0,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0,         0,         0,         0,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0}
/*MT6169*/
/*MT6169*/#define LTE_Band21_NS9                                                                                                       \
/*MT6169*//*NS9_10150, NS9_10151*/                                                                                                  \
/*MT6169*/{    0x0100,    0x0200,         0,         0,         0,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0,         0,         0,         0,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0}
/*MT6169*/
/*MT6169*/#define LTE_Band23_NS3                                                                                                       \
/*MT6169*//* NS3_03  ,    NS3_05,    NS3_10,    NS3_15,    NS3_20*/                                                                    \
/*MT6169*/{    0x0100,    0x0100,    0x0100,    0x0100,    0x0100,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0,         0,         0,         0,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0}
/*MT6169*/
/*MT6169*/#define LTE_Band23_NS11                                                                                                      \
/*MT6169*//*  NS11_3A,   NS11_3B,   NS11_5A,  NS11_5B0,   NS11_5C,  NS11_10A, NS11_15A0,NS11_15A10,NS11_15A11,NS11_15A21,NS11_15A3 ,*/ \
/*MT6169*/{    0x0500,    0x0100,    0x0700,    0x0400,    0x0100,    0x0C00,    0x0F00,    0x0700,    0x0A00,    0x0600,    0x0F00,   \
/*MT6169*//*NS11_15B0,NS11_15B10,NS11_15B2 , NS11_15B3, NS11_20A0,NS11_20A10,NS11_20A11,NS11_20A21,NS11_20A3*/                         \
/*MT6169*/     0x0A00,    0x0600,    0x0200,    0x0680,    0x0F00,    0x0700,    0x0A00,    0x0700,    0x0F00,         0,         0,   \
/*MT6169*/          0,         0}
/*MT6169*/
/*MT6169*/#define LTE_Band23_NS20                                                                                                      \
/*MT6169*//*  NS20_5A, NS20_5B00, NS20_5B01,  NS20_5B1, NS20_10A0,NS20_10A10,NS20_10A11, NS20_10A2, NS20_10B0, NS20_10B1,NS20_15A00,*/ \
/*MT6169*/{    0x1100,    0x0100,    0x0400,    0x0200,    0x1000,    0x0200,    0x0500,    0x0600,    0x0400,    0x0200,    0x0B00,   \
/*MT6169*//*NS20_15A01,NS20_15A10,NS20_15A11, NS20_15A2, NS20_15A3, NS20_20A0,NS20_20A10,NS20_20A11, NS20_20A2, NS20_20A3, NS20_20A4,*/\
/*MT6169*/     0x0600,    0x0100,    0x0700,    0x0500,    0x0600,    0x1100,    0x0C00,    0x0600,    0x0900,    0x0700,    0x0500,   \
/*MT6169*//*NS20_20A5,*/                                                                                                               \
/*MT6169*/     0x1000,         0}
/*MT6169*/
/*MT6169*/#define LTE_Band25_NS3                                                                                                       \
/*MT6169*//* NS3_03  ,    NS3_05,    NS3_10,    NS3_15,    NS3_20*/                                                                    \
/*MT6169*/{    0x0100,    0x0100,    0x0100,    0x0100,    0x0100,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0,         0,         0,         0,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0}
/*MT6169*/
/*MT6169*/#define LTE_Band26_NS121314                                                                                                  \
/*MT6169*//* NS12_1A0,  NS12_1A1,   NS12_1B,  NS12_3A0,  NS12_3A1,   NS12_3B,  NS12_5A0,  NS12_5A1,   NS12_5B,  NS13_5A0,  NS13_5A1,*/ \
/*MT6169*/{    0x0300,    0x0600,    0x0300,    0x0400,    0x0300,    0x0300,    0x0500,    0x0300,    0x0300,    0x0300,    0x0200,   \
/*MT6169*//*NS14_10A0, NS14_10A1, NS14_15A0, NS14_15A1*/                                                                               \
/*MT6169*/     0x0300,    0x0100,    0x0300,    0x0100,         0,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0}
/*MT6169*/
/*MT6169*/#define LTE_Band26_NS15                                                                                                      \
/*MT6169*//* NS150_1C,  NS150_3A,  NS150_3B,  NS150_3C,  NS150_5A,  NS150_5B,  NS150_5C, NS150_10A, NS150_10B, NS150_10C, NS150_15A,*/ \
/*MT6169*/{    0x0300,    0x0400,    0x0400,    0x0900,    0x0400,    0x0500,    0x0900,    0x0400,    0x0600,    0x0900,    0x0400,   \
/*MT6169*//*NS150_15B, NS150_15C,  NS151_5C, NS151_10A, NS151_10B, NS151_10C, NS151_15A, NS151_15B, NS151_15C*/                        \
/*MT6169*/     0x0500,    0x0900,    0x0200,    0x0400,    0x0400,    0x0900,    0x0400,    0x0500,    0x0900,         0,         0,   \
/*MT6169*/          0,         0}
/*MT6169*/
/*MT6169*/#define LTE_Band27_NS16                                                                                                      \
/*MT6169*//* NS160_3A,  NS160_3B,  NS160_5A,  NS160_5B,  NS160_5C,  NS160_5D, NS160_10A,NS160_10B0,NS160_10B1, NS160_10D, NS160_10E,*/ \
/*MT6169*/{    0x0200,    0x0100,    0x0500,    0x0100,    0x0200,    0x0300,    0x0500,    0x0300,    0x0700,    0x0300,    0x0100,   \
/*MT6169*//* NS161_5A,  NS161_5B,  NS161_5C,NS161_10A0,NS161_10A1,NS161_10C,  NS161_10D, NS161_10E, NS162_10A, NS162_10B, NS162_10C,*/ \
/*MT6169*/     0x0200,    0x0300,    0x0100,    0x0500,    0x0200,    0x0400,    0x0500,    0x0100,    0x0100,    0x0200,    0x0100,   \
/*MT6169*//*NS162_10D*/                                                                                                                \
/*MT6169*/     0x0300,         0}
/*MT6169*/
/*MT6169*/#define LTE_Band28_NS18                                                                                                      \
/*MT6169*//*  NS18_5 , NS18_101520*/                                                                                                   \
/*MT6169*/{    0x0100,    0x0400,         0,         0,         0,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0,         0,         0,         0,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0}
/*MT6169*/
/*MT6169*/#define LTE_Band35_NS3                                                                                                       \
/*MT6169*//* NS3_03  ,    NS3_05,    NS3_10,    NS3_15,    NS3_20*/                                                                    \
/*MT6169*/{    0x0100,    0x0100,    0x0100,    0x0100,    0x0100,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0,         0,         0,         0,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0}
/*MT6169*/
/*MT6169*/#define LTE_Band36_NS3                                                                                                       \
/*MT6169*//* NS3_03  ,    NS3_05,    NS3_10,    NS3_15,    NS3_20*/                                                                    \
/*MT6169*/{    0x0100,    0x0100,    0x0100,    0x0100,    0x0100,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0,         0,         0,         0,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0}
/*MT6169*/
/*MT6169*/#define LTE_Band41_NS4                                                                                                       \
/*MT6169*//*    NS4_5,  NS4_10_A,  NS4_10_B,  NS4_10_C,  NS4_15_A,  NS4_15_B,  NS4_15_C,  NS4_20_A,  NS4_20_B,  NS4_20_C*/             \
/*MT6169*/{    0x0100,    0x0300,    0x0200,    0x0300,    0x0300,    0x0200,    0x0300,    0x0300,    0x0200,    0x0300,         0,   \
/*MT6169*/          0,         0,         0,         0,         0,         0,         0,         0,         0,         0,         0,   \
/*MT6169*/          0,         0}
/*MT6169*/
/*MT6169*//*------------------------------------------------------*/
/*MT6169*//*  Definition for the band splitting                   */
/*MT6169*//*------------------------------------------------------*/
/*MT6169*/
/*MT6169*/// How to set the band-splitting frequency?
/*MT6169*/// For example, the DL frequencies of the 1st and 2nd sub-bands are 758~780.4MHz and 780.5~802.9MHz, so we define
/*MT6169*/// BAND_SPLIT_INDICATOR1_DL_END1 as 7805 [ = (780.4+0.1)*10 ]
/*MT6169*/// BAND_SPLIT_INDICATOR1_DL_END2 as 8030 [ = (802.9+0.1)*10 ]
/*MT6169*///
/*MT6169*/// [Note]
/*MT6169*/// 1. The unit for the frequency definition is 100kHz
/*MT6169*/// 2. BAND_SPLIT_INDICATOR1_DL_ENDn defines the end DL frequency of each part of splitting band PLUS 0.1 MHz
/*MT6169*/// 3. Since there are only TWO sub-bands, BAND_SPLIT_INDICATOR1_DL_END3 should be 0
/*MT6169*/// 4. The way to define the UL frequency is the same as the way DL does
/*MT6169*///
/*MT6169*/#define BAND_SPLIT_INDICATOR1         LTE_Band28
/*MT6169*/#define BAND_SPLIT_INDICATOR1_NUM     2     // the num of part of splitting band
/*MT6169*/#define BAND_SPLIT_INDICATOR1_DL_END1 7806  // the end DL frequency of the 1st part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split
/*MT6169*/#define BAND_SPLIT_INDICATOR1_DL_END2 8030  // the end DL frequency of the 2nd part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split
/*MT6169*/#define BAND_SPLIT_INDICATOR1_DL_END3 0     // the end DL frequency of the 3rd part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split or only two parts are split
/*MT6169*/#define BAND_SPLIT_INDICATOR1_UL_END1 7256  // the end UL frequency of the 1st part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split
/*MT6169*/#define BAND_SPLIT_INDICATOR1_UL_END2 7480  // the end UL frequency of the 2nd part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split
/*MT6169*/#define BAND_SPLIT_INDICATOR1_UL_END3 0     // the end UL frequency of the 3rd part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split or only two parts are split
/*MT6169*/#define BAND_INDICATOR1_POWER_COMP    0     // It is the real HW power difference between bypass path and filter path, Unit: S(6,8) dB
/*MT6169*/                                            // If bypass > filter by 1.5dB, the value is  384 (= 1.5*256)
/*MT6169*/                                            // If bypass < filter by 0.5dB, the value is -128 (=-0.5*256)
/*MT6169*/#define BAND_INDICATOR1_COUPLER_COMP  0     // It is the expected power difference between bypass path and filter path, Unit: S(6,8) dB
/*MT6169*/                                            // If bypass > filter by 0.5dB, the value is  128 (= 0.5*256)
/*MT6169*/                                            // If bypass < filter by 1.5dB, the value is -384 (=-1.5*256)
/*MT6169*/
/*MT6169*/#define BAND_SPLIT_INDICATOR2         LTE_Band41
/*MT6169*/#define BAND_SPLIT_INDICATOR2_NUM     2   // the num of part of splitting band
/*MT6169*/#define BAND_SPLIT_INDICATOR2_DL_END1 26301   // the end DL frequency of the 1st part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split
/*MT6169*/#define BAND_SPLIT_INDICATOR2_DL_END2 26900   // the end DL frequency of the 2nd part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split
/*MT6169*/#define BAND_SPLIT_INDICATOR2_DL_END3 0   // the end DL frequency of the 3rd part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split or only two parts are split
/*MT6169*/#define BAND_SPLIT_INDICATOR2_UL_END1 26301   // the end UL frequency of the 1st part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split
/*MT6169*/#define BAND_SPLIT_INDICATOR2_UL_END2 26900   // the end UL frequency of the 2nd part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split
/*MT6169*/#define BAND_SPLIT_INDICATOR2_UL_END3 0   // the end UL frequency of the 3rd part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split or only two parts are split
/*MT6169*/#define BAND_INDICATOR2_POWER_COMP    512 // It is the real HW power difference between bypass path and filter path, Unit: S(6,8) dB
/*MT6169*/                                          // If bypass > filter by 1.5dB, the value is  384 (= 1.5*256)
/*MT6169*/                                          // If bypass < filter by 0.5dB, the value is -128 (=-0.5*256)
/*MT6169*/#define BAND_INDICATOR2_COUPLER_COMP  0   // It is the expected power difference between bypass path and filter path, Unit: S(6,8) dB
/*MT6169*/                                          // If bypass > filter by 0.5dB, the value is  128 (= 0.5*256)
/*MT6169*/                                          // If bypass < filter by 1.5dB, the value is -384 (=-1.5*256)
/*MT6169*/
/*MT6169*/#define BAND_SPLIT_INDICATOR3         LTE_BandNone
/*MT6169*/#define BAND_SPLIT_INDICATOR3_NUM     0   // the num of part of splitting band
/*MT6169*/#define BAND_SPLIT_INDICATOR3_DL_END1 0   // the end DL frequency of the 1st part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split
/*MT6169*/#define BAND_SPLIT_INDICATOR3_DL_END2 0   // the end DL frequency of the 2nd part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split
/*MT6169*/#define BAND_SPLIT_INDICATOR3_DL_END3 0   // the end DL frequency of the 3rd part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split or only two parts are split
/*MT6169*/#define BAND_SPLIT_INDICATOR3_UL_END1 0   // the end UL frequency of the 1st part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split
/*MT6169*/#define BAND_SPLIT_INDICATOR3_UL_END2 0   // the end UL frequency of the 2nd part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split
/*MT6169*/#define BAND_SPLIT_INDICATOR3_UL_END3 0   // the end UL frequency of the 3rd part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split or only two parts are split
/*MT6169*/#define BAND_INDICATOR3_POWER_COMP    512 // It is the real HW power difference between bypass path and filter path, Unit: S(6,8) dB
/*MT6169*/                                          // If bypass > filter by 1.5dB, the value is  384 (= 1.5*256)
/*MT6169*/                                          // If bypass < filter by 0.5dB, the value is -128 (=-0.5*256)
/*MT6169*/#define BAND_INDICATOR3_COUPLER_COMP  0   // It is the expected power difference between bypass path and filter path, Unit: S(6,8) dB
/*MT6169*/                                          // If bypass > filter by 0.5dB, the value is  128 (= 0.5*256)
/*MT6169*/                                          // If bypass < filter by 1.5dB, the value is -384 (=-1.5*256)
/*MT6169*/
/*MT6169*/#define BAND_SPLIT_INDICATOR4         LTE_BandNone
/*MT6169*/#define BAND_SPLIT_INDICATOR4_NUM     0 // the num of part of splitting band
/*MT6169*/#define BAND_SPLIT_INDICATOR4_DL_END1 0 // the end DL frequency of the 1st part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split
/*MT6169*/#define BAND_SPLIT_INDICATOR4_DL_END2 0 // the end DL frequency of the 2nd part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split
/*MT6169*/#define BAND_SPLIT_INDICATOR4_DL_END3 0 // the end DL frequency of the 3rd part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split or only two parts are split
/*MT6169*/#define BAND_SPLIT_INDICATOR4_UL_END1 0 // the end UL frequency of the 1st part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split
/*MT6169*/#define BAND_SPLIT_INDICATOR4_UL_END2 0 // the end UL frequency of the 2nd part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split
/*MT6169*/#define BAND_SPLIT_INDICATOR4_UL_END3 0 // the end UL frequency of the 3rd part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split or only two parts are split
/*MT6169*/#define BAND_INDICATOR4_POWER_COMP    0 // It is the real HW power difference between bypass path and filter path, Unit: S(6,8) dB
/*MT6169*/                                        // If bypass > filter by 1.5dB, the value is  384 (= 1.5*256)
/*MT6169*/                                        // If bypass < filter by 0.5dB, the value is -128 (=-0.5*256)
/*MT6169*/#define BAND_INDICATOR4_COUPLER_COMP  0 // It is the expected power difference between bypass path and filter path, Unit: S(6,8) dB
/*MT6169*/                                        // If bypass > filter by 0.5dB, the value is  128 (= 0.5*256)
/*MT6169*/                                        // If bypass < filter by 1.5dB, the value is -384 (=-1.5*256)
/*MT6169*/
/*MT6169*/#define BAND_SPLIT_INDICATOR5         LTE_BandNone
/*MT6169*/#define BAND_SPLIT_INDICATOR5_NUM     0 // the num of part of splitting band
/*MT6169*/#define BAND_SPLIT_INDICATOR5_DL_END1 0 // the end DL frequency of the 1st part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split
/*MT6169*/#define BAND_SPLIT_INDICATOR5_DL_END2 0 // the end DL frequency of the 2nd part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split
/*MT6169*/#define BAND_SPLIT_INDICATOR5_DL_END3 0 // the end DL frequency of the 3rd part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split or only two parts are split
/*MT6169*/#define BAND_SPLIT_INDICATOR5_UL_END1 0 // the end UL frequency of the 1st part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split
/*MT6169*/#define BAND_SPLIT_INDICATOR5_UL_END2 0 // the end UL frequency of the 2nd part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split
/*MT6169*/#define BAND_SPLIT_INDICATOR5_UL_END3 0 // the end UL frequency of the 3rd part of splitting band PLUS 0.1MHz, unit: 100KHz, set to 0 if no part is split or only two parts are split
/*MT6169*/#define BAND_INDICATOR5_POWER_COMP    0 // It is the real HW power difference between bypass path and filter path, Unit: S(6,8) dB
/*MT6169*/                                        // If bypass > filter by 1.5dB, the value is  384 (= 1.5*256)
/*MT6169*/                                        // If bypass < filter by 0.5dB, the value is -128 (=-0.5*256)
/*MT6169*/#define BAND_INDICATOR5_COUPLER_COMP  0 // It is the expected power difference between bypass path and filter path, Unit: S(6,8) dB
/*MT6169*/                                        // If bypass > filter by 0.5dB, the value is  128 (= 0.5*256)
/*MT6169*/                                        // If bypass < filter by 1.5dB, the value is -384 (=-1.5*256)
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND28_2 Start -----------------*/
/*MT6169*/                                          // already defined band 28 of 2 sub-band 
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND28_3 Start -----------------*/
/*MT6169*/#define PDATA_LTE_Band28_3_PR1        0
/*MT6169*/#define PDATA_LTE_Band28_3_PR2        0
/*MT6169*/#define PDATA_LTE_Band28_3_PR3        LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define PDATA_LTE_Band28_3_PT1        0
/*MT6169*/#define PDATA_LTE_Band28_3_PT2        0
/*MT6169*/#define PDATA_LTE_Band28_3_PT3        LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define LTE_Band28_3_RX_IO_SEL        RX_IO_NON_USED
/*MT6169*/#define LTE_Band28_3_RXD_IO_SEL       RXD_IO_NON_USED
/*MT6169*/#define LTE_Band28_3_TX_IO_SEL        TX_IO_NON_USED
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND28_Bypass Start ------------*/
/*MT6169*/#define PDATA_LTE_Band28_BYPASS_PR1   0
/*MT6169*/#define PDATA_LTE_Band28_BYPASS_PR2   0
/*MT6169*/#define PDATA_LTE_Band28_BYPASS_PR3   LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define PDATA_LTE_Band28_BYPASS_PT1   0
/*MT6169*/#define PDATA_LTE_Band28_BYPASS_PT2   0
/*MT6169*/#define PDATA_LTE_Band28_BYPASS_PT3   LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define LTE_Band28_BYPASS_RX_IO_SEL   RX_IO_NON_USED
/*MT6169*/#define LTE_Band28_BYPASS_RXD_IO_SEL  RXD_IO_NON_USED
/*MT6169*/#define LTE_Band28_BYPASS_TX_IO_SEL   TX_IO_NON_USED
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND40_2 Start -----------------*/
/*MT6169*/#define PDATA_LTE_Band40_2_PR1        0
/*MT6169*/#define PDATA_LTE_Band40_2_PR2        0
/*MT6169*/#define PDATA_LTE_Band40_2_PR3        LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define PDATA_LTE_Band40_2_PT1        0
/*MT6169*/#define PDATA_LTE_Band40_2_PT2        0
/*MT6169*/#define PDATA_LTE_Band40_2_PT3        LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define LTE_Band40_2_RX_IO_SEL        RX_IO_NON_USED
/*MT6169*/#define LTE_Band40_2_RXD_IO_SEL       RXD_IO_NON_USED
/*MT6169*/#define LTE_Band40_2_TX_IO_SEL        TX_IO_NON_USED
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND40_3 Start -----------------*/
/*MT6169*/#define PDATA_LTE_Band40_3_PR1        0
/*MT6169*/#define PDATA_LTE_Band40_3_PR2        0
/*MT6169*/#define PDATA_LTE_Band40_3_PR3        LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define PDATA_LTE_Band40_3_PT1        0
/*MT6169*/#define PDATA_LTE_Band40_3_PT2        0
/*MT6169*/#define PDATA_LTE_Band40_3_PT3        LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define LTE_Band40_3_RX_IO_SEL        RX_IO_NON_USED
/*MT6169*/#define LTE_Band40_3_RXD_IO_SEL       RXD_IO_NON_USED
/*MT6169*/#define LTE_Band40_3_TX_IO_SEL        TX_IO_NON_USED
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND40_Bypass Start ------------*/
/*MT6169*/#define PDATA_LTE_Band40_BYPASS_PR1   0
/*MT6169*/#define PDATA_LTE_Band40_BYPASS_PR2   0
/*MT6169*/#define PDATA_LTE_Band40_BYPASS_PR3   LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define PDATA_LTE_Band40_BYPASS_PT1   0   //LPF path, switch to B41 PRX at B40 TX for B40 TX pulling issue.
/*MT6169*/#define PDATA_LTE_Band40_BYPASS_PT2   0   //LPF path, switch to B41 PRX at B40 TX for B40 TX pulling issue.
/*MT6169*/#define PDATA_LTE_Band40_BYPASS_PT3   LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define LTE_Band40_BYPASS_RX_IO_SEL   RX_IO_NON_USED
/*MT6169*/#define LTE_Band40_BYPASS_RXD_IO_SEL  RXD_IO_NON_USED
/*MT6169*/#define LTE_Band40_BYPASS_TX_IO_SEL   TX_IO_NON_USED
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND41_3 Start -----------------*/
/*MT6169*/#define PDATA_LTE_Band41_3_PR1        0
/*MT6169*/#define PDATA_LTE_Band41_3_PR2        0
/*MT6169*/#define PDATA_LTE_Band41_3_PR3        LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define PDATA_LTE_Band41_3_PT1        0
/*MT6169*/#define PDATA_LTE_Band41_3_PT2        0
/*MT6169*/#define PDATA_LTE_Band41_3_PT3        LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define LTE_Band41_3_RX_IO_SEL        RX_IO_NON_USED
/*MT6169*/#define LTE_Band41_3_RXD_IO_SEL       RXD_IO_NON_USED
/*MT6169*/#define LTE_Band41_3_TX_IO_SEL        TX_IO_NON_USED
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND41_Bypass Start ------------*/
/*MT6169*/#define PDATA_LTE_Band41_BYPASS_PR1   0
/*MT6169*/#define PDATA_LTE_Band41_BYPASS_PR2   0
/*MT6169*/#define PDATA_LTE_Band41_BYPASS_PR3   LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define PDATA_LTE_Band41_BYPASS_PT1   0    //LPF path
/*MT6169*/#define PDATA_LTE_Band41_BYPASS_PT2   0    //LPF path
/*MT6169*/#define PDATA_LTE_Band41_BYPASS_PT3   LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define LTE_Band41_BYPASS_RX_IO_SEL   RX_IO_NON_USED
/*MT6169*/#define LTE_Band41_BYPASS_RXD_IO_SEL  RXD_IO_NON_USED
/*MT6169*/#define LTE_Band41_BYPASS_TX_IO_SEL   TX_IO_NON_USED
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND38_2 Start -----------------*/
/*MT6169*/#define PDATA_LTE_Band38_2_PR1        0
/*MT6169*/#define PDATA_LTE_Band38_2_PR2        0
/*MT6169*/#define PDATA_LTE_Band38_2_PR3        LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define PDATA_LTE_Band38_2_PT1        0
/*MT6169*/#define PDATA_LTE_Band38_2_PT2        0
/*MT6169*/#define PDATA_LTE_Band38_2_PT3        LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define LTE_Band38_2_RX_IO_SEL        RX_IO_NON_USED
/*MT6169*/#define LTE_Band38_2_RXD_IO_SEL       RXD_IO_NON_USED
/*MT6169*/#define LTE_Band38_2_TX_IO_SEL        TX_IO_NON_USED
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND38_3 Start -----------------*/
/*MT6169*/#define PDATA_LTE_Band38_3_PR1        0
/*MT6169*/#define PDATA_LTE_Band38_3_PR2        0
/*MT6169*/#define PDATA_LTE_Band38_3_PR3        LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define PDATA_LTE_Band38_3_PT1        0
/*MT6169*/#define PDATA_LTE_Band38_3_PT2        0
/*MT6169*/#define PDATA_LTE_Band38_3_PT3        LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define LTE_Band38_3_RX_IO_SEL        RX_IO_NON_USED
/*MT6169*/#define LTE_Band38_3_RXD_IO_SEL       RXD_IO_NON_USED
/*MT6169*/#define LTE_Band38_3_TX_IO_SEL        TX_IO_NON_USED
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND38_Bypass Start ------------*/
/*MT6169*/#define PDATA_LTE_Band38_BYPASS_PR1   0
/*MT6169*/#define PDATA_LTE_Band38_BYPASS_PR2   0
/*MT6169*/#define PDATA_LTE_Band38_BYPASS_PR3   LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define PDATA_LTE_Band38_BYPASS_PT1   0    //LPF path
/*MT6169*/#define PDATA_LTE_Band38_BYPASS_PT2   0    //LPF path
/*MT6169*/#define PDATA_LTE_Band38_BYPASS_PT3   LTE_PDATA_OFF
/*MT6169*/
/*MT6169*/#define LTE_Band38_BYPASS_RX_IO_SEL   RX_IO_NON_USED
/*MT6169*/#define LTE_Band38_BYPASS_RXD_IO_SEL  RXD_IO_NON_USED
/*MT6169*/#define LTE_Band38_BYPASS_TX_IO_SEL   TX_IO_NON_USED
/*MT6169*/
/*MT6169*//*------------------------------------------------------*/
/*MT6169*//*  Definition for transmit antenna selection           */
/*MT6169*//*------------------------------------------------------*/
/*MT6169*/
/*MT6169*/#if defined(__TAS_FOR_C2K_ONOFF_SUPPORT__)
/*MT6169*/#define LTE_TAS_ENA                   0                //1/0: enable/disable the transmit antenna selection control
/*MT6169*/#define LTE_TAS_FOR_C2K_ENA           0                //1/0: enable/disable the TAS feature for C2K
/*MT6169*/#else
/*MT6169*/   #if defined(__TAS_SUPPORT__)
/*MT6169*/#define LTE_TAS_ENA                   1                //1/0: enable/disable the transmit antenna selection control
/*MT6169*/#define LTE_TAS_FOR_C2K_ENA           0                //1/0: enable/disable the TAS feature for C2K
/*MT6169*/   #else
/*MT6169*/#define LTE_TAS_ENA                   0                //1/0: enable/disable the transmit antenna selection control
/*MT6169*/#define LTE_TAS_FOR_C2K_ENA           0                //1/0: enable/disable the TAS feature for C2K
/*MT6169*/   #endif
/*MT6169*/#endif
/*MT6169*/#define LTE_TAS_WITH_TEST_SIM_ENA     0                //1/0: enable/disable the transmit antenna selection control when the test sim is inserted
/*MT6169*/
/*MT6169*/#define LTE_TAS_PIN_NULL              -1               //Do not modify this definition
/*MT6169*/#define LTE_TAS_PIN1                  LTE_TAS_PIN_NULL //the 1st BPI pin number for the transmit antenna selection control
/*MT6169*/#define LTE_TAS_PIN2                  LTE_TAS_PIN_NULL //the 2nd BPI pin number for the transmit antenna selection control
/*MT6169*/#define LTE_TAS_PIN3                  LTE_TAS_PIN_NULL //the 3rd BPI pin number for the transmit antenna selection control
/*MT6169*/
/*MT6169*/#define LTE_TAS_MASK                  LTE_TAS_BPI_PIN_GEN(1,1,1)
/*MT6169*/#define LTE_TAS_INIT_ANT              0    //the initial TAS index, 0  : the settings of PDATA_LTE_Bandx_Pxx (the original ones)
/*MT6169*/                                           //                       1~7: the settings of PDATA_LTE_Bandx_TAS1~7
/*MT6169*/
/*MT6169*/#define LTE_FORCE_TX_ANTENNA_ENABLE   0    //Force the antenna index or not
/*MT6169*/#define LTE_FORCE_TX_ANTENNA_IDX      0    //Force to which antenna index
/*MT6169*/
/*MT6169*//*------------------------------------------------------*/
/*MT6169*//*  BAND_TAS_INDICATOR1 ~ BAND_TAS_INDICATOR14          */
/*MT6169*//*------------------------------------------------------*/
/*MT6169*/#define BAND_TAS_INDICATOR1           LTE_BandNone
/*MT6169*/#define BAND_TAS_INDICATOR2           LTE_BandNone
/*MT6169*/#define BAND_TAS_INDICATOR3           LTE_BandNone
/*MT6169*/#define BAND_TAS_INDICATOR4           LTE_BandNone
/*MT6169*/#define BAND_TAS_INDICATOR5           LTE_BandNone
/*MT6169*/#define BAND_TAS_INDICATOR6           LTE_BandNone
/*MT6169*/#define BAND_TAS_INDICATOR7           LTE_BandNone
/*MT6169*/#define BAND_TAS_INDICATOR8           LTE_BandNone
/*MT6169*/#define BAND_TAS_INDICATOR9           LTE_BandNone
/*MT6169*/#define BAND_TAS_INDICATOR10          LTE_BandNone
/*MT6169*/#define BAND_TAS_INDICATOR11          LTE_BandNone
/*MT6169*/#define BAND_TAS_INDICATOR12          LTE_BandNone
/*MT6169*/#define BAND_TAS_INDICATOR13          LTE_BandNone
/*MT6169*/#define BAND_TAS_INDICATOR14          LTE_BandNone
/*MT6169*/
/*MT6169*///LTE_TAS_BPI_PIN_GEN(var1, var2, var3) => use to generate the transmit antenna selection BPI control logic
/*MT6169*///                                         var1: the value (1 or 0) for the 1st transmit antenna selection BPI pin (LTE_TAS_PIN1)
/*MT6169*///                                         var2: the value (1 or 0) for the 2nd transmit antenna selection BPI pin (LTE_TAS_PIN2)
/*MT6169*///                                         var3: the value (1 or 0) for the 3rd transmit antenna selection BPI pin (LTE_TAS_PIN3)
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND1_TAS Start ----------------*/
/*MT6169*/#define PDATA_LTE_Band1_TAS1          LTE_TAS_BPI_PIN_GEN(0, 0, 0)
/*MT6169*/#define PDATA_LTE_Band1_TAS2          LTE_TAS_BPI_PIN_GEN(0, 0, 0)
/*MT6169*/#define PDATA_LTE_Band1_TAS3          LTE_TAS_BPI_PIN_GEN(0, 0, 0)
/*MT6169*/#define PDATA_LTE_Band1_TAS4          LTE_TAS_BPI_PIN_GEN(0, 0, 0)
/*MT6169*/#define PDATA_LTE_Band1_TAS5          LTE_TAS_BPI_PIN_GEN(0, 0, 0)
/*MT6169*/#define PDATA_LTE_Band1_TAS6          LTE_TAS_BPI_PIN_GEN(0, 0, 0)
/*MT6169*/#define PDATA_LTE_Band1_TAS7          LTE_TAS_BPI_PIN_GEN(0, 0, 0)
/*MT6169*/
/*MT6169*///* ------------- PDATA_BAND38_TAS Start ---------------*/
/*MT6169*/#define PDATA_LTE_Band38_TAS1         LTE_TAS_BPI_PIN_GEN(0, 0, 0)
/*MT6169*/#define PDATA_LTE_Band38_TAS2         LTE_TAS_BPI_PIN_GEN(0, 0, 0)
/*MT6169*/#define PDATA_LTE_Band38_TAS3         LTE_TAS_BPI_PIN_GEN(0, 0, 0)
/*MT6169*/#define PDATA_LTE_Band38_TAS4         LTE_TAS_BPI_PIN_GEN(0, 0, 0)
/*MT6169*/#define PDATA_LTE_Band38_TAS5         LTE_TAS_BPI_PIN_GEN(0, 0, 0)
/*MT6169*/#define PDATA_LTE_Band38_TAS6         LTE_TAS_BPI_PIN_GEN(0, 0, 0)
/*MT6169*/#define PDATA_LTE_Band38_TAS7         LTE_TAS_BPI_PIN_GEN(0, 0, 0)
/*MT6169*/
/*============================================================================== */
#endif
