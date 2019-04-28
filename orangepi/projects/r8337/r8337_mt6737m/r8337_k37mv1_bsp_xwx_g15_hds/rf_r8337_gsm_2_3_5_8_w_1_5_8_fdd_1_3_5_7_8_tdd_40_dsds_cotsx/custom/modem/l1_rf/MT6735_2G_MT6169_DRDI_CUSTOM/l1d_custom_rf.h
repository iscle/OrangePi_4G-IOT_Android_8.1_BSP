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
 *   l1d_custom_rf.h
 *
 * Project:
 * --------
 *   MT6169
 *
 * Description:
 * ------------
 *   MT6169 2G RF constance definition
 *
 * Author:
 * -------
 *
 *
 *==============================================================================
 *******************************************************************************/

#ifndef  _L1D_CUSTOM_RF_H_
#define  _L1D_CUSTOM_RF_H_
/* --------------------------------------------------------------------------- */

#if !defined(MT6169_2G_RF)
   #error "rf files mismatch with compile option!"
#endif

   #if IS_MIPI_SUPPORT
#include "l1d_custom_mipi.h"
   #endif

/*--------------------------------------------------------*/
/*   Event Timing Define                                  */
/*--------------------------------------------------------*/
/*MT6169*/ #define  QB_SR1               173
/*MT6169*/ #define  QB_SR2               58
/*MT6169*/ #define  QB_SR2M              39
/*MT6169*/ #define  QB_SR3               2//5    /* SR3 should be larger than (QB_RX_FSYNC_2_FENA+2QB) */
/*MT6169*/ #define  QB_PR1               175  /* QB_PR1>QB_SR0 to prevent RF conflict among 2/3G    */
/*MT6169*/ #define  QB_PR2               50
/*MT6169*/ #define  QB_PR3               7
/*MT6169*/ #define  QB_ST1               255
/*MT6169*/ #define  QB_ST2B              30   /* ST2B should be larger  than (QB_TX_FENA_2_FSYNC+8QB)*/
/*MT6169*/ #define  QB_ST3               38   /* ST3  should be larger  than (QB_TX_FSYNC_2_FENA+7QB)*/
/*MT6169*/ #define  QB_PT1               257  /* QB_PT1>QB_ST1 to prevent RF conflict among 2/3G     */
/*MT6169*/ #define  QB_PT2               56
/*MT6169*/ #define  QB_PT2B              6
/*MT6169*/ #define  QB_PT3               40
/*MT6169*/ #define  QB_ST2M_G8           12
/*MT6169*/ #define  QB_ST2M_8G           12
/*MT6169*/ #define  QB_PT2M1_G8          -1
/*MT6169*/ #define  QB_PT2M2_G8          -3
/*MT6169*/ #define  QB_PT2M1_8G          10
/*MT6169*/ #define  QB_PT2M2_8G          4
/*MT6169*/
/*MT6169*/
/*MT6169*/ #define  QB_APCON             14 //OH:11
/*MT6169*/ #define  QB_APCMID            18 //OH:18
/*MT6169*/ #define  QB_APCOFF            7  //56: 6
/*MT6169*/ #if IS_2G_MIPI_ENABLE
/*MT6169*/ #define  QB_APCDACON          25 //0/*MIPI ENABLE*/
/*MT6169*/ #else
/*MT6169*/ #define  QB_APCDACON          18 //0/*MIPI DISABLE*/
/*MT6169*/ #endif
/*MT6169*/ #define  TX_PROPAGATION_DELAY 47 //56:47 / OH:46
/*MT6169*/
/*MT6169*/ /*--------------------------------------------------*/
/*MT6169*/ /*   define  BPI data for MT6290                    */
/*MT6169*/ /*--------------------------------------------------*/
/*MT6169*/ /*  PRCB : bit   pin                                */
/*MT6169*/ /*         16    ANTENNA_CONFLICT_2G                */
/*MT6169*/ /*         17    SPI_SWITCH_TO_2G                   */
/*MT6169*/ /*         18    GSM_ERR_DET_ID                     */
/*MT6169*/ /*         19    SP3T_V1 TXOUT_B2B39 TXOUT_B7B38    */
/*MT6169*/ /*         20    SP3T_V2 W_PA_B7_EN                 */
/*MT6169*/ /*         21    ASM_VCTRL_A Main_V1                */
/*MT6169*/ /*         22    ASM_VCTRL_B Main_V2                */
/*MT6169*/ /*         23    ASM_VCTRL_C Main_V3                */
/*MT6169*/ /*         24    WG_GGE_PA_ENABLE                   */
/*MT6169*/ /*         29    GSM_ERR_DET_ID(7206_ERR_DETECT_2G) */
/*MT6169*/ /*         30    GSM_ERR_DET_ID(OGT_ERR_DETECT_2G)  */
/*MT6169*/ /*--------------------------------------------------*/
/*MT6169*/
/*MT6169*/ /*------------------------------------------------------*/
/*MT6169*/ /*  GSM_ERR_DET_ID(Pin:29) has no dedicate output pin,  */
/*MT6169*/ /*  and it is mapped to bit "29" for SW control.        */
/*MT6169*/ /*  For accurate RF conflict detection, this value must */
/*MT6169*/ /*  set "29" and is unchangable.                        */
/*MT6169*/ /*------------------------------------------------------*/
/*MT6169*/ #define  GSM_ERR_DET_ID         (               29) /* For accurate RF conflict detection(2G/FDD), this value must set "29" */
/*MT6169*/                                                     /* and is unchangable.                                                  */
/*MT6169*/ #define  PDATA_GSM_ERR_DET      (1<<GSM_ERR_DET_ID)
/*MT6169*/
/*MT6169*/ #define  PDATA_GMSK              0x00000000
/*MT6169*/
/*MT6169*/ #if IS_2G_MIPI_ENABLE
/*MT6169*/ #define  PDATA_8PSK              0x00000000                              /*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_GSM850_PR1       (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_GSM850_PR2       (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_GSM850_PR3       (0x00000000                             )/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_GSM_PR1          (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_GSM_PR2          (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_GSM_PR3          (0x00000000                             )/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_DCS_PR1          (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_DCS_PR2          (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_DCS_PR3          (0x00000000                             )/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_PCS_PR1          (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_PCS_PR2          (0x00000001           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_PCS_PR3          (0x00000000                             )/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_GSM850_PT1       (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_GSM850_PT2       (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_GSM850_PT2B      (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_GSM850_PT3       (0x00000000                             )/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_GSM850_PT2M1_G8  (0x00000000|PDATA_8PSK|PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_GSM850_PT2M2_G8  (0x00000000|PDATA_8PSK|PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_GSM850_PT2M1_8G  (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_GSM850_PT2M2_8G  (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_GSM_PT1          (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_GSM_PT2          (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_GSM_PT2B         (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_GSM_PT3          (0x00000000                             )/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_GSM_PT2M1_G8     (0x00000000|PDATA_8PSK|PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_GSM_PT2M2_G8     (0x00000000|PDATA_8PSK|PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_GSM_PT2M1_8G     (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_GSM_PT2M2_8G     (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_DCS_PT1          (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_DCS_PT2          (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_DCS_PT2B         (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_DCS_PT3          (0x00000000                             )/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_DCS_PT2M1_G8     (0x00000000|PDATA_8PSK|PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_DCS_PT2M2_G8     (0x00000000|PDATA_8PSK|PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_DCS_PT2M1_8G     (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_DCS_PT2M2_8G     (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_PCS_PT1          (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_PCS_PT2          (0x00000001           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_PCS_PT2B         (0x00000001           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_PCS_PT3          (0x00000000                             )/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_PCS_PT2M1_G8     (0x00000001|PDATA_8PSK|PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_PCS_PT2M2_G8     (0x00000001|PDATA_8PSK|PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_PCS_PT2M1_8G     (0x00000001           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_PCS_PT2M2_8G     (0x00000001           |PDATA_GSM_ERR_DET)/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_INIT             (0x00000000                             )/*MIPI ENABLE*/
/*MT6169*/ #define  PDATA_IDLE             (0x00000000                             )/*MIPI ENABLE*/
/*MT6169*/ #else
/*MT6169*/ #define  PDATA_8PSK              0x00800000                              /*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_GSM850_PR1       (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_GSM850_PR2       (0x00480000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_GSM850_PR3       (0x00000000                             )/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_GSM_PR1          (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_GSM_PR2          (0x00A80000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_GSM_PR3          (0x00000000                             )/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_DCS_PR1          (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_DCS_PR2          (0x00C80000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_DCS_PR3          (0x00000000                             )/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_PCS_PR1          (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_PCS_PR2          (0x00E80000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_PCS_PR3          (0x00000000                             )/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_GSM850_PT1       (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_GSM850_PT2       (0x00280000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_GSM850_PT2B      (0x01280000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_GSM850_PT3       (0x00000000                             )/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_GSM850_PT2M1_G8  (0x01280000|PDATA_8PSK|PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_GSM850_PT2M2_G8  (0x01280000|PDATA_8PSK|PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_GSM850_PT2M1_8G  (0x00280000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_GSM850_PT2M2_8G  (0x01280000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_GSM_PT1          (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_GSM_PT2          (0x00280000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_GSM_PT2B         (0x01280000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_GSM_PT3          (0x00000000                             )/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_GSM_PT2M1_G8     (0x01280000|PDATA_8PSK|PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_GSM_PT2M2_G8     (0x01280000|PDATA_8PSK|PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_GSM_PT2M1_8G     (0x00280000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_GSM_PT2M2_8G     (0x01280000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_DCS_PT1          (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_DCS_PT2          (0x00C00000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_DCS_PT2B         (0x01680000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_DCS_PT3          (0x00000000                             )/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_DCS_PT2M1_G8     (0x01680000|PDATA_8PSK|PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_DCS_PT2M2_G8     (0x01680000|PDATA_8PSK|PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_DCS_PT2M1_8G     (0x00680000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_DCS_PT2M2_8G     (0x01680000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_PCS_PT1          (0x00000000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_PCS_PT2          (0x00C00000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_PCS_PT2B         (0x01680000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_PCS_PT3          (0x00000000                             )/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_PCS_PT2M1_G8     (0x01680000|PDATA_8PSK|PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_PCS_PT2M2_G8     (0x01680000|PDATA_8PSK|PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_PCS_PT2M1_8G     (0x00680000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_PCS_PT2M2_8G     (0x01680000           |PDATA_GSM_ERR_DET)/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_INIT             (0x00000000                             )/*MIPI DISABLE*/
/*MT6169*/ #define  PDATA_IDLE             (0x00000000                             )/*MIPI DISABLE*/
/*MT6169*/ #endif
/*MT6169*/
/*MT6169*//*----------------------------------------------*/
/*MT6169*//*   APC Compensate Thresholds                  */
/*MT6169*//*----------------------------------------------*/
/*MT6169*/
/*MT6169*/ #define    SECONDS2FRAME(n)                     ((int)((n)*1000/4.615))
/*MT6169*/ #define    VOLT2UVOLT(n)                        ((int)((n)*1000000))
/*MT6169*/ #define    TEMP2MTEMP(n)                        ((int)((n)*1000))
/*MT6169*/
/*MT6169*/ #define    BAT_VOLTAGE_SAMPLE_PERIOD_SECOND     180
/*MT6169*/ #define    BAT_VOLTAGE_SAMPLE_PERIOD            SECONDS2FRAME(BAT_VOLTAGE_SAMPLE_PERIOD_SECOND)
/*MT6169*/ #define    BAT_VOLTAGE_AVERAGE_COUNT            1
/*MT6169*/ #define    BAT_LOW_VOLTAGE_TRHESHOLD            (3.5)
/*MT6169*/ #define    BAT_HIGH_VOLTAGE_TRHESHOLD           (4.0)
/*MT6169*/ #define    BAT_LOW_VOLTAGE                      VOLT2UVOLT(BAT_LOW_VOLTAGE_TRHESHOLD)
/*MT6169*/ #define    BAT_HIGH_VOLTAGE                     VOLT2UVOLT(BAT_HIGH_VOLTAGE_TRHESHOLD)
/*MT6169*/
/*MT6169*/ #define    BAT_TEMPERATURE_SAMPLE_PERIOD_SECOND 180
/*MT6169*/ #define    BAT_TEMPERATURE_SAMPLE_PERIOD        SECONDS2FRAME(BAT_TEMPERATURE_SAMPLE_PERIOD_SECOND)
/*MT6169*/ #define    BAT_TEMPERATURE_AVERAGE_COUNT        1
/*MT6169*/ #define    BAT_LOW_TEMPERATURE_TRHESHOLD        (0)
/*MT6169*/ #define    BAT_HIGH_TEMPERATURE_TRHESHOLD       (50)
/*MT6169*/ #define    BAT_LOW_TEMPERATURE                  TEMP2MTEMP(BAT_LOW_TEMPERATURE_TRHESHOLD)
/*MT6169*/ #define    BAT_HIGH_TEMPERATURE                 TEMP2MTEMP(BAT_HIGH_TEMPERATURE_TRHESHOLD)
/*MT6169*/
/*MT6169*/ #define    RF_TEMPERATURE_SAMPLE_PERIOD_SECOND  1
/*MT6169*/ #define    RF_TEMPERATURE_SAMPLE_PERIOD         SECONDS2FRAME(RF_TEMPERATURE_SAMPLE_PERIOD_SECOND)
/*MT6169*/ #define    RF_TEMPERATURE_AVERAGE_COUNT         1
/*MT6169*/
/*MT6169*//*----------------------------------------------*/
/*MT6169*//*   Voltage Compensate Parameter               */
/*MT6169*//*----------------------------------------------*/
/*MT6169*/
/*MT6169*/ #define    MINUTES2FRAME(n)                     ((int)((n)*13000))
/*MT6169*/ #define    AP_UPDATE_VOLTINFO_PERIOD            MINUTES2FRAME(5)
/*MT6169*/
/*MT6169*//*----------------------------------------------*/
/*MT6169*//*   Crystal parameter                          */
/*MT6169*//*----------------------------------------------*/
/*MT6169*/ #if  IS_AFC_VCXO_SUPPORT
/*MT6169*/ #define Custom_RF_XO_CapID   156 /* RF SOP, Range:0~255 */
/*MT6169*/ #else
/*MT6169*/ #define Custom_RF_XO_CapID   0   /* For MT7206 with VCTCXO */
/*MT6169*/ #endif
/*MT6169*/
/*MT6169*/ /**************************************/
/*MT6169*/ /* Define your band mode selection on */
/*MT6169*/ /* High Band and Low Band receivers   */
/*MT6169*/ /*  IORX_HB1 : High Band  DCS/PCS     */
/*MT6169*/ /*  IORX_HB2 : High Band  DCS/PCS     */
/*MT6169*/ /*  IORX_HB3 : High Band  DCS/PCS     */
/*MT6169*/ /*  IORX_MB1 : Mid  Band  DCS/PCS     */
/*MT6169*/ /*  IORX_MB2 : Mid  Band  DCS/PCS     */
/*MT6169*/ /*  IORX_LB1 : Low  Band  GSM850/GSM  */
/*MT6169*/ /*  IORX_LB2 : Low  Band  GSM850/GSM  */
/*MT6169*/ /*  IORX_LB3 : Low  Band  GSM850/GSM  */
/*MT6169*/ /**************************************/
/*MT6169*/
/*MT6169*/ #if IS_2G_MIPI_ENABLE
/*MT6169*/ #define GSM850_PATH_SEL IORX_LB2/*MIPI ENABLE*/
/*MT6169*/ #define GSM_PATH_SEL    IORX_LB1/*MIPI ENABLE*/
/*MT6169*/ #define DCS_PATH_SEL    IORX_HB3/*MIPI ENABLE*/
/*MT6169*/ #define PCS_PATH_SEL    IORX_MB1/*MIPI ENABLE*/
/*MT6169*/ #else
/*MT6169*/ #define GSM850_PATH_SEL IORX_LB1/*MIPI DISABLE*/
/*MT6169*/ #define GSM_PATH_SEL    IORX_LB2/*MIPI DISABLE*/
/*MT6169*/ #define DCS_PATH_SEL    IORX_MB1/*MIPI DISABLE*/
/*MT6169*/ #define PCS_PATH_SEL    IORX_HB1/*MIPI DISABLE*/
/*MT6169*/ #endif
/*MT6169*/
/*MT6169*/ /**************************************/
/*MT6169*/ /* Define your band mode selection on */
/*MT6169*/ /* High Band and Low Band receivers   */
/*MT6169*/ /*  IOTX_HB1 : High Band  DCS/PCS     */
/*MT6169*/ /*  IOTX_HB2 : High Band  DCS/PCS     */
/*MT6169*/ /*  IOTX_MB1 : Mid  Band  DCS/PCS     */
/*MT6169*/ /*  IOTX_MB2 : Mid  Band  DCS/PCS     */
/*MT6169*/ /*  IOTX_LB1 : Low  Band  GSM850/GSM  */
/*MT6169*/ /*  IOTX_LB2 : Low  Band  GSM850/GSM  */
/*MT6169*/ /*  IOTX_LB3 : Low  Band  GSM850/GSM  */
/*MT6169*/ /*  IOTX_LB4 : Low  Band  GSM850/GSM  */
/*MT6169*/ /**************************************/
/*MT6169*/
/*MT6169*/ #if IS_2G_MIPI_ENABLE
/*MT6169*/ #define GSM850_PORT_SEL IOTX_LB3/*MIPI ENABLE*/
/*MT6169*/ #define GSM_PORT_SEL    IOTX_LB3/*MIPI ENABLE*/
/*MT6169*/ #define DCS_PORT_SEL    IOTX_MB1/*MIPI ENABLE*/
/*MT6169*/ #define PCS_PORT_SEL    IOTX_MB1/*MIPI ENABLE*/
/*MT6169*/ #else
/*MT6169*/ #define GSM850_PORT_SEL IOTX_LB3/*MIPI DISABLE*/
/*MT6169*/ #define GSM_PORT_SEL    IOTX_LB3/*MIPI DISABLE*/
/*MT6169*/ #define DCS_PORT_SEL    IOTX_MB2/*MIPI DISABLE*/
/*MT6169*/ #define PCS_PORT_SEL    IOTX_MB2/*MIPI DISABLE*/
/*MT6169*/ #endif
/*MT6169*/
/*MT6169*//*======================================================================================== */
/*MT6169*/
/*MT6169*/ /*----------------------------------------------*/
/*MT6169*/ /*   TX Power Control (TXPC) Support            */
/*MT6169*/ /*----------------------------------------------*/
/*MT6169*/
/*MT6169*/ #define IS_BSI_CLOSED_LOOP_TXPC_ON      0
/*MT6169*/
/*MT6169*/ #define TXPC_EPSK_TP_SLOPE_LB          ((15<<8)+16) /* Unit: degree/dB. Temperature increment that causes 1-dB EPSK TX power drop */
/*MT6169*/ #define TXPC_EPSK_TP_SLOPE_HB          ((15<<8)+14) /* Two slope method : [( temp<20:slpoe1)<<8 + (temp>=20:slpoe2)], slope must < 256 */
/*MT6169*/
/*MT6169*/ /*----------------------------------------------*/
/*MT6169*/ /*   DCXO LPM parameter                         */
/*MT6169*/ /*----------------------------------------------*/
/*MT6169*/ #define CUSTOM_CLOAD_FREQ_OFFSET   88940 /*in unit of Hz*/
/*MT6169*/
/*MT6169*/ /*----------------------------------------------------*/
/*MT6169*/ /*   Enable or disable the clock1, 2, 3, and 4 output */
/*MT6169*/ /*   1 : Enable                                       */
/*MT6169*/ /*   0 : Disable                                      */
/*MT6169*/ /*----------------------------------------------------*/
/*MT6169*/ #define CLK1_EN                         1 /* CLK1 is enabled for BB */
/*MT6169*/ #define CLK2_EN                         0
/*MT6169*/ #define CLK3_EN                         0
/*MT6169*/ #define CLK4_EN                         0
/*MT6169*/
/*MT6169*/ /*----------------------------------------------*/
/*MT6169*/ /*   TX power rollback parameter                */
/*MT6169*/ /*----------------------------------------------*/
/*MT6169*/ /*Unit: 1/8 dB*/
/*MT6169*/ /*GSM850 GMSK*/
/*MT6169*/ #define GSM850_TX_ROLLBACK_2T_GMSK      8
/*MT6169*/ #define GSM850_TX_ROLLBACK_3T_GMSK     24
/*MT6169*/ #define GSM850_TX_ROLLBACK_4T_GMSK     32
/*MT6169*/ #define GSM850_TX_ROLLBACK_5T_GMSK     40
/*MT6169*/
/*MT6169*/ /*GSM GMSK*/
/*MT6169*/ #define GSM_TX_ROLLBACK_2T_GMSK         8
/*MT6169*/ #define GSM_TX_ROLLBACK_3T_GMSK        24
/*MT6169*/ #define GSM_TX_ROLLBACK_4T_GMSK        32
/*MT6169*/ #define GSM_TX_ROLLBACK_5T_GMSK        40
/*MT6169*/
/*MT6169*/ /*DCS GMSK*/
/*MT6169*/ #define DCS_TX_ROLLBACK_2T_GMSK         8
/*MT6169*/ #define DCS_TX_ROLLBACK_3T_GMSK        24
/*MT6169*/ #define DCS_TX_ROLLBACK_4T_GMSK        32
/*MT6169*/ #define DCS_TX_ROLLBACK_5T_GMSK        40
/*MT6169*/
/*MT6169*/ /*PCS GMSK*/
/*MT6169*/ #define PCS_TX_ROLLBACK_2T_GMSK         8
/*MT6169*/ #define PCS_TX_ROLLBACK_3T_GMSK        24
/*MT6169*/ #define PCS_TX_ROLLBACK_4T_GMSK        32
/*MT6169*/ #define PCS_TX_ROLLBACK_5T_GMSK        40
/*MT6169*/
/*MT6169*/ /*GSM850 EPSK*/
/*MT6169*/ #define GSM850_TX_ROLLBACK_2T_EPSK      8
/*MT6169*/ #define GSM850_TX_ROLLBACK_3T_EPSK     24
/*MT6169*/ #define GSM850_TX_ROLLBACK_4T_EPSK     32
/*MT6169*/ #define GSM850_TX_ROLLBACK_5T_EPSK     40
/*MT6169*/
/*MT6169*/ /*GSM EPSK*/
/*MT6169*/ #define GSM_TX_ROLLBACK_2T_EPSK         8
/*MT6169*/ #define GSM_TX_ROLLBACK_3T_EPSK        24
/*MT6169*/ #define GSM_TX_ROLLBACK_4T_EPSK        32
/*MT6169*/ #define GSM_TX_ROLLBACK_5T_EPSK        40
/*MT6169*/
/*MT6169*/ /*DCS EPSK*/
/*MT6169*/ #define DCS_TX_ROLLBACK_2T_EPSK         8
/*MT6169*/ #define DCS_TX_ROLLBACK_3T_EPSK        24
/*MT6169*/ #define DCS_TX_ROLLBACK_4T_EPSK        32
/*MT6169*/ #define DCS_TX_ROLLBACK_5T_EPSK        40
/*MT6169*/
/*MT6169*/ /*PCS EPSK*/
/*MT6169*/ #define PCS_TX_ROLLBACK_2T_EPSK         8
/*MT6169*/ #define PCS_TX_ROLLBACK_3T_EPSK        24
/*MT6169*/ #define PCS_TX_ROLLBACK_4T_EPSK        32
/*MT6169*/ #define PCS_TX_ROLLBACK_5T_EPSK        40
/*MT6169*/ /*============================================================================== */
/*MT6169*/
/*MT6169*//*============================================================================== */
/*MT6169*/ /*----------------------------------------------*/
/*MT6169*/ /*   TX Power Offset parameter (TPO)                         */
/*MT6169*/ /*----------------------------------------------*/
/*MT6169*/ 
/*MT6169*/ #define TPO_2G_ENABLE          0
/*MT6169*/ #define TPO_2G_META_ENABLE     0
/*MT6169*/ #define TPO_2G_TABLE_ON_MASK            0x0
/*MT6169*/ 
/*MT6169*/ /*============================================================================== */
/*MT6169*/ /*----------------------------------------------*/
/*MT6169*/ /*   One-bin Support Definition                 */
/*MT6169*/ /*----------------------------------------------*/
/*MT6169*/ #if IS_2G_DRDI_SUPPORT
/*MT6169*/    #if IS_L1_RF_DRDI_CUSTOM_SETTING_FROM_MML1
/*MT6169*/ #include "mml1_custom_drdi.h"
/*MT6169*/ /* Constants to enable "Dynamic Initialization RF parameters" mechanism                 */
/*MT6169*/ #define L1D_CUSTOM_GPIO_ENABLE            MML1_CUSTOM_GPIO_ENABLE
/*MT6169*/ #define L1D_CUSTOM_ADC_ENABLE             MML1_CUSTOM_ADC_ENABLE
/*MT6169*/ #define L1D_CUSTOM_BARCODE_ENABLE         MML1_CUSTOM_BARCODE_ENABLE
/*MT6169*/
/*MT6169*/ #define L1D_CUSTOM_DYNAMIC_SUPPORT        (L1D_CUSTOM_GPIO_ENABLE||L1D_CUSTOM_ADC_ENABLE||L1D_CUSTOM_BARCODE_ENABLE)
/*MT6169*/
/*MT6169*/       #if (IS_MML1_DRDI_ENABLE && IS_MML1_DRDI_REMAP_ENABLE)
/*MT6166*/ #define L1D_CUSTOM_TOTAL_SET_NUMS         MML1_DRDI_REMAP_GGE_REAL_SET_NUMS
/*MT6169*/       #else
/*MT6169*/ #define L1D_CUSTOM_GPIO_SET_CALC          MML1_CUSTOM_GPIO_NUMS_IN_CALC
/*MT6169*/ #define L1D_CUSTOM_ADC_SET_CALC           MML1_CUSTOM_ADC_NUMS_IN_CALC
/*MT6169*/ #define L1D_CUSTOM_BARCODE_SET_CALC       MML1_CUSTOM_BARCODE_NUMS_IN_CALC
/*MT6169*/ #define L1D_CUSTOM_TOTAL_SET_NUMS         (L1D_CUSTOM_GPIO_SET_CALC*L1D_CUSTOM_ADC_SET_CALC*L1D_CUSTOM_BARCODE_SET_CALC)
/*MT6169*/       #endif
/*MT6169*/       #if IS_MIPI_SUPPORT && L1D_CUSTOM_DYNAMIC_SUPPORT
/*MT6169*/ #define IS_MIPI_DRDI_SUPPORT              1
/*MT6169*/       #else
/*MT6169*/ #define IS_MIPI_DRDI_SUPPORT              0
/*MT6169*/       #endif
/*MT6169*/
/*MT6169*/       #if IS_TX_POWER_OFFSET_SUPPORT && L1D_CUSTOM_DYNAMIC_SUPPORT
/*MT6169*/ #define IS_TX_POWER_OFFSET_DRDI_SUPPORT   1
/*MT6169*/       #else
/*MT6169*/ #define IS_TX_POWER_OFFSET_DRDI_SUPPORT   0
/*MT6169*/       #endif
/*MT6169*/
/*MT6169*/       #if L1D_CUSTOM_DYNAMIC_SUPPORT
/*MT6169*/ #define L1D_CUSTOM_PDATA_DRDI_SUPPORT     1 /* Do not modify this define */
/*MT6169*/       #endif
/*MT6169*/
/*MT6169*/ #define L1D_CUSTOM_DEBUG_ENABLE           0 /* Enable  this , DRDI debug message will show at L1 log */
/*MT6169*/
/*MT6169*/       #if IS_MIPI_DRDI_SUPPORT
/*MT6169*/          #if IS_2G_MIPI_ENABLE
/*MT6169*/          #else
/*MT6169*/ #error "Must set IS_2G_MIPI_ENABLE( at l1d_custom_mipi.h ) to 1"
/*MT6169*/          #endif
/*MT6169*/       #endif
/*MT6169*/ 
/*MT6169*/    #else /*IS_L1_RF_DRDI_CUSTOM_SETTING_FROM_MML1*/
/*MT6169*/ /* Constants to enable "Dynamic Initialization RF parameters" mechanism                 */
/*MT6169*/ #define L1D_CUSTOM_GPIO_ENABLE            0
/*MT6169*/ #define L1D_CUSTOM_ADC_ENABLE             0
/*MT6169*/ #define L1D_CUSTOM_BARCODE_ENABLE         0
/*MT6169*/
/*MT6169*/ #define L1D_CUSTOM_DYNAMIC_SUPPORT        (L1D_CUSTOM_GPIO_ENABLE|L1D_CUSTOM_ADC_ENABLE|L1D_CUSTOM_BARCODE_ENABLE)
/*MT6169*/
/*MT6169*/ /* Constants to define number of sets can be represented by each mechanism respectively */
/*MT6169*/ #define L1D_CUSTOM_GPIO_SET_NUMS          2
/*MT6169*/ #define L1D_CUSTOM_ADC_SET_NUMS           2
/*MT6169*/ #define L1D_CUSTOM_BARCODE_SET_NUMS       2
/*MT6169*/
/*MT6169*/ /* Constants to first, second, and third index for the representation in configuration  */
/*MT6169*/ /* set index table                                                                      */
/*MT6169*/
/*MT6169*/ /* The value can be set:                                                                */
/*MT6169*/ /* L1D_CUSTOM_GPIO_DETECTION_ID                                                         */
/*MT6169*/ /* L1D_CUSTOM_ADC_DETECTION_ID                                                          */
/*MT6169*/ /* L1D_CUSTOM_BARCODE_DETECTION_ID                                                      */
/*MT6169*/ /* L1D_CUSTOM_NULL_ACTION                                                               */
/*MT6169*/ /* Note:                                                                                */
/*MT6169*/ /* 1. Should not define L1D_CUSTOM_FIRST_INDEX to L1D_CUSTOM_NULL_ACTION                */
/*MT6169*/ /*    while L1D_CUSTOM_SECOND_INDEX or L1D_CUSTOM_THIRD_INDEX is not L1D_CUSTOM_NULL_ACTION */
/*MT6169*/ /* 2. Should not define L1D_CUSTOM_SECOND_INDEX or L1D_CUSTOM_FIRST_INDEX to            */
/*MT6169*/ /*    L1D_CUSTOM_NULL_ACTION while L1D_CUSTOM_THIRD_INDEX is not L1D_CUSTOM_NULL_ACTION */
/*MT6169*/ #define L1D_CUSTOM_FIRST_INDEX            L1D_CUSTOM_NULL_ACTION
/*MT6169*/ #define L1D_CUSTOM_SECOND_INDEX           L1D_CUSTOM_NULL_ACTION
/*MT6169*/ #define L1D_CUSTOM_THIRD_INDEX            L1D_CUSTOM_NULL_ACTION
/*MT6169*/
/*MT6169*/ /* For trace output to debug ( L1D_CustomDynamicDebug() )                               */
/*MT6169*/ #define L1D_CUSTOM_DEBUG_ENABLE           0
/*MT6169*/
/*MT6169*/ /*------*/
/*MT6169*/ /* GPIO */
/*MT6169*/ /*------*/
/*MT6169*/ /* Customization constant to be used for customer to determine the number of GPIO       */
/*MT6169*/ /* detection pins in use                                                                */
/*MT6169*/ #define L1D_CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE    1
/*MT6169*/ /*--------------------------------------------------------------------------------------*/
/*MT6169*/
/*MT6169*/ /*-----*/
/*MT6169*/ /* ADC */
/*MT6169*/ /*-----*/
/*MT6169*/ /*Customization constant to be used for customer to determine the bits of ADC in use    */
/*MT6169*/ #define L1D_CUSTOM_ADC_BITS                          12      // ADC is 12 bit (1/4096) per step
/*MT6169*/
/*MT6169*/ /* Constant to be used to determine the maximum input voltage on the board              */
/*MT6169*/ /* (in micro volt unit)                                                                 */
/*MT6169*/ #define L1D_CUSTOM_ADC_MAX_INPUT_VOLTAGE             1500000 // uV
/*MT6169*/
/*MT6169*/ /* Customization constant to be used for customer to determine the inaccuracy margin    */
/*MT6169*/ /* on the board (in micro volt unit)                                                    */
/*MT6169*/ #define L1D_CUSTOM_ADC_INACCURACY_MARGIN             25000   // uV uint
/*MT6169*/
/*MT6169*/ /* Constant to be used to determine the each step level of ADC voltage to level         */
/*MT6169*/ /* look-up table on the board (in micro volt unit)                                      */
/*MT6169*/ /* L1D_CUSTOM_ADC_VOLT_LVL_RESOLUTION_THRESHOLD - two times inaccuracy margin           */
/*MT6169*/ /* L1D_CUSTOM_ADC_VOLT_LVL_STEP_SIZE            - step size of consecutive levels       */
/*MT6169*/ /* L1D_CUSTOM_ADC_VOLT_LVL_MIN_LVL_STEP         - the first level upper bound to 0 volt */
/*MT6169*/ #define L1D_CUSTOM_ADC_VOLT_LVL_RESOLUTION_THRESHOLD (L1D_CUSTOM_ADC_INACCURACY_MARGIN * 2)
/*MT6169*/ #define L1D_CUSTOM_ADC_VOLT_LVL_STEP_SIZE            ((L1D_CUSTOM_ADC_MAX_INPUT_VOLTAGE) / (L1D_CUSTOM_ADC_LEVEL_TOTAL-1))
/*MT6169*/ #define L1D_CUSTOM_ADC_VOLT_LVL_MIN_LVL_STEP         (L1D_CUSTOM_ADC_VOLT_LVL_STEP_SIZE / 2)
/*MT6169*/
/*MT6169*/ /* Sample of ADC votlage to level look-up table                                         */
/*MT6169*/
/*MT6169*/ /* ADC levels - 4                                                                       */
/*MT6169*/ /* Level   Upper(uV)       Lower(uV)                                                    */
/*MT6169*/ /* 0       250000          0                                                            */
/*MT6169*/ /* 1       750000          250000                                                       */
/*MT6169*/ /* 2       1250000         750000                                                       */
/*MT6169*/ /* 3       1500000         1250000                                                      */
/*MT6169*/
/*MT6169*/ /* Customization constant to be used for customer to determine number of ADC levels to  */
/*MT6169*/ /* be used to distinguish between the RF HW configurations                              */
/*MT6169*/
/*MT6169*/ #define L1D_CUSTOM_ADC_LEVEL_TOTAL        4
/*MT6169*/
/*MT6169*/ /* Customization constant to be used for customer to determine number of ADC channel    */
/*MT6169*/ /* measurement counts (in 2's order) ex: 7 => 2^7 = 128                                 */
/*MT6169*/ #define L1D_CUSTOM_ADC_MEAS_COUNT_2_ORDER 7 //2^7 = 128
/*MT6169*/
/*MT6169*/ #define L1D_CUSTOM_ADC_LVL0               (L1D_CUSTOM_ADC_VOLT_LVL_MIN_LVL_STEP)
/*MT6169*/ #define L1D_CUSTOM_ADC_LVL1               (L1D_CUSTOM_ADC_LVL0 + L1D_CUSTOM_ADC_VOLT_LVL_STEP_SIZE)
/*MT6169*/ #define L1D_CUSTOM_ADC_LVL2               (L1D_CUSTOM_ADC_LVL1 + L1D_CUSTOM_ADC_VOLT_LVL_STEP_SIZE)
/*MT6169*/ #define L1D_CUSTOM_ADC_LVL3               (L1D_CUSTOM_ADC_LVL2 + L1D_CUSTOM_ADC_VOLT_LVL_STEP_SIZE)
/*MT6169*/ #define L1D_CUSTOM_ADC_LVL4               (L1D_CUSTOM_ADC_LVL3 + L1D_CUSTOM_ADC_VOLT_LVL_STEP_SIZE)
/*MT6169*/ #define L1D_CUSTOM_ADC_LVL5               (L1D_CUSTOM_ADC_LVL4 + L1D_CUSTOM_ADC_VOLT_LVL_STEP_SIZE)
/*MT6169*/ #define L1D_CUSTOM_ADC_LVL6               (L1D_CUSTOM_ADC_LVL5 + L1D_CUSTOM_ADC_VOLT_LVL_STEP_SIZE)
/*MT6169*/ /*--------------------------------------------------------------------------------------*/
/*MT6169*/
/*MT6169*/ /*---------*/
/*MT6169*/ /* BarCode */
/*MT6169*/ /*---------*/
/*MT6169*/ /* Customization constant to be used for customer to determine the n-th digit of        */
/*MT6169*/ /* UE barcode to detect the RF configurations; n starts from 0                          */
/*MT6169*/ #define L1D_CUSTOM_BARCODE_READ_DIGIT_NUM 0
/*MT6169*/
/*MT6169*/ /* Customization constant to be used for customer to determine at most three (for now)  */
/*MT6169*/ /* kinds of ASM representation barcode digit number (in ASCII) to detect the RF         */
/*MT6169*/ /* configurations                                                                       */
/*MT6169*/ #define L1D_CUSTOM_BARCODE_DIGIT_VALUE_1  0
/*MT6169*/ #define L1D_CUSTOM_BARCODE_DIGIT_VALUE_2  1
/*MT6169*/ #define L1D_CUSTOM_BARCODE_DIGIT_VALUE_3  2
/*MT6169*/ /*--------------------------------------------------------------------------------------*/
/*MT6169*/
/*MT6169*/ /* Constants for the second and third index base to be calculated */
/*MT6169*/       #if L1D_CUSTOM_GPIO_ENABLE
/*MT6169*/ #define L1D_CUSTOM_GPIO_NUMS_IN_CALC      L1D_CUSTOM_GPIO_SET_NUMS
/*MT6169*/       #else
/*MT6169*/ #define L1D_CUSTOM_GPIO_NUMS_IN_CALC      1
/*MT6169*/       #endif
/*MT6169*/
/*MT6169*/       #if L1D_CUSTOM_ADC_ENABLE
/*MT6169*/ #define L1D_CUSTOM_ADC_NUMS_IN_CALC       L1D_CUSTOM_ADC_SET_NUMS
/*MT6169*/       #else
/*MT6169*/ #define L1D_CUSTOM_ADC_NUMS_IN_CALC       1
/*MT6169*/       #endif
/*MT6169*/
/*MT6169*/       #if L1D_CUSTOM_BARCODE_ENABLE
/*MT6169*/ #define L1D_CUSTOM_BARCODE_NUMS_IN_CALC   L1D_CUSTOM_BARCODE_SET_NUMS
/*MT6169*/       #else
/*MT6169*/ #define L1D_CUSTOM_BARCODE_NUMS_IN_CALC   1
/*MT6169*/       #endif
/*MT6169*/
/*MT6169*/ #define L1D_CUSTOM_TOTAL_SET_NUMS         L1D_CUSTOM_GPIO_NUMS_IN_CALC*L1D_CUSTOM_ADC_NUMS_IN_CALC*L1D_CUSTOM_BARCODE_NUMS_IN_CALC
/*MT6169*/
/*MT6169*/ /* Customization constant to be used for customer to determine if the AuxADC calibration*/
/*MT6169*/ /* is enabled or not                                                                    */
/*MT6169*/ #define L1D_CUSTOM_ADC_CALIBRATE_ENABLE   0
/*MT6169*/    #endif /*IS_L1_RF_DRDI_CUSTOM_SETTING_FROM_MML1 */
/*MT6169*/
/*MT6169*/    #if L1D_CUSTOM_DYNAMIC_SUPPORT
/*MT6169*/       #if IS_L1_RF_DRDI_CUSTOM_SETTING_FROM_MML1
/*MT6169*/       #else
/*MT6169*/ #error "MT6290 and latter chipsets, always need MMLI DRDI API , please check IS_L1_RF_DRDI_CUSTOM_SETTING_FROM_MML1 at l1d_cid.h "
/*MT6169*/       #endif
/*MT6169*/    #endif
/*MT6169*/ #endif
/*MT6169*/ /*============================================================================== */
/*MT6169*/ #if (IS_2G_TAS_SUPPORT||IS_2G_TAS_FOR_C2K_ONOFF_SUPPORT)
/*MT6169*/ /*------------------------------------------------------*/
/*MT6169*/ /*  Definition for the antenna swap                     */
/*MT6169*/ /*------------------------------------------------------*/
/*MT6169*/
/*MT6169*/ #define L1D_FORCE_TX_ANTENNA_ENABLE        0              /* 0: off 1: Don't change antenna                                             */
/*MT6169*/ #define L1D_FORCE_TX_ANTENNA_IDX           0              /* The antenna which user forces to stay                                      */
/*MT6169*/ #if IS_2G_TAS_SUPPORT
/*MT6169*/ #define L1D_EN_TAS                         1              /* 0: off   1: enable TS feature                                              */
/*MT6169*/ #endif
/*MT6169*/ #define L1D_EN_TAS_WITH_TEST_SIM           0              /* 0: off   1: enable TS feature with Test SIM                                */
/*MT6169*/ #define L1D_EN_BAND                        0xF            /* b0:band 850,b1:band 900,b2:band 1800,b3:band 1900                          */
/*MT6169*/ #define L1D_ANT_SEL_INIT                   0              /* Default antenna                                                            */
/*MT6169*/ #define L1D_EN_TAS_FOR_C2K                 0              /* 0: off   1: enable TAS for C2K feature                                     */
/*MT6169*/
/*MT6169*/ #define L1D_TAS_PIN_NULL                  -1                 // Do not modify this define
/*MT6169*/ #define L1D_TAS_PIN1                       0                 // the 1st BPI pin number for the antenna swap control
/*MT6169*/ #define L1D_TAS_PIN2                       L1D_TAS_PIN_NULL  // the 2nd BPI pin number for the antenna swap control
/*MT6169*/ #define L1D_TAS_PIN3                       L1D_TAS_PIN_NULL  // the 3rd BPI pin number for the antenna swap control
/*MT6169*/
/*MT6169*/
/*MT6169*/ #define PDATA_L1D_TAS_MASK        L1D_TAS_BPI_PIN_GEN(1,1,1)
/*MT6169*/
/*MT6169*/ /* ------------- PDATA_L1D_GSM850_TAS#  --------------*/
/*MT6169*/ #define PDATA_L1D_GSM850_TAS1     L1D_TAS_BPI_PIN_GEN(1,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_GSM850_TAS2     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_GSM850_TAS3     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_GSM850_TAS4     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_GSM850_TAS5     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_GSM850_TAS6     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_GSM850_TAS7     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/
/*MT6169*/ /* ------------- PDATA_L1D_GSM900_TAS#  --------------*/
/*MT6169*/ #define PDATA_L1D_GSM900_TAS1     L1D_TAS_BPI_PIN_GEN(1,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_GSM900_TAS2     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_GSM900_TAS3     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_GSM900_TAS4     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_GSM900_TAS5     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_GSM900_TAS6     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_GSM900_TAS7     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/
/*MT6169*/ /* ------------- PDATA_L1D_DCS1800_TAS#  --------------*/
/*MT6169*/ #define PDATA_L1D_DCS1800_TAS1     L1D_TAS_BPI_PIN_GEN(1,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_DCS1800_TAS2     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_DCS1800_TAS3     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_DCS1800_TAS4     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_DCS1800_TAS5     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_DCS1800_TAS6     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_DCS1800_TAS7     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/
/*MT6169*/ /* ------------- PDATA_L1D_PCS1900_TAS#  --------------*/
/*MT6169*/ #define PDATA_L1D_PCS1900_TAS1     L1D_TAS_BPI_PIN_GEN(1,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_PCS1900_TAS2     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_PCS1900_TAS3     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_PCS1900_TAS4     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_PCS1900_TAS5     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_PCS1900_TAS6     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/ #define PDATA_L1D_PCS1900_TAS7     L1D_TAS_BPI_PIN_GEN(0,0,0)//If not using , must set (0,0,0)
/*MT6169*/
/*MT6169*/ #endif
#endif

