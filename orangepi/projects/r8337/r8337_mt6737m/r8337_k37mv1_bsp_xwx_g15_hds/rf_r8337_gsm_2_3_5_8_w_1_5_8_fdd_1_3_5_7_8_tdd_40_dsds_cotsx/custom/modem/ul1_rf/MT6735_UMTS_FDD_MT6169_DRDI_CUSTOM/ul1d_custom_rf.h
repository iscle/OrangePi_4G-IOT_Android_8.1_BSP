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
 *	ul1d_custom_rf.h
 *
 * Project:
 * --------
 *   MT6169
 *
 * Description:
 * ------------
 *   MT6169 UMTS FDD RF
 *
 * Author:
 * -------
 * -------
 *

 *******************************************************************************/
#ifndef  _UL1D_CUSTOM_RF_H_
#define  _UL1D_CUSTOM_RF_H_
/* ------------------------------------------------------------------------- */
#if !defined(MT6169_RF)
   #error "rf files mismatch with compile option!"
#endif

#include "ul1d_custom_mipi.h"

/*MT6169*/
/*MT6169*/ #define  PA_SECTION   3
/*MT6169*/
/*MT6169*/ /*--------------------------------------------------------*/
/*MT6169*/ /*   Event Timing Define                                  */
/*MT6169*/ /*--------------------------------------------------------*/
/*MT6169*/ #define  TC_PR1               MICROSECOND_TO_CHIP(200)
/*MT6169*/ #define  TC_PR2               MICROSECOND_TO_CHIP(100)
/*MT6169*/ #define  TC_PR2B              MICROSECOND_TO_CHIP( 50)
/*MT6169*/ #define  TC_PR3               MICROSECOND_TO_CHIP( 20)
/*MT6169*/
/*MT6169*/ #define  TC_PT1               MICROSECOND_TO_CHIP(200)
/*MT6169*/ #define  TC_PT2               MICROSECOND_TO_CHIP(100)
/*MT6169*/ #define  TC_PT2B              MICROSECOND_TO_CHIP( 50)
/*MT6169*/ #define  TC_PT3               MICROSECOND_TO_CHIP( 10)
/*MT6169*/
/*MT6169*/
/*MT6169*/ /* the following parameter is chip resolution */
/*MT6169*/ #define MAX_OFFSET        (24*4) //this value must be equal to max of the following 4 offset value
/*MT6169*/ /* Set VM timing same as PGABSI_OFFSET1 */
/*MT6169*/ // Rich modification for the vm_offset as 37
/*MT6169*/ #define VM_OFFSET         (42)//(33)   //54 //63 chips
/*MT6169*/ #define VBIAS_OFFSET      (59)   //59 chips
/*MT6169*/ #define DC2DC_OFFSET      (24*4)
/*MT6169*/ #define VGA_OFFSET        (24*4)
/*MT6169*/
/*MT6169*/
/*---------------------------------------------------------------------*/
/*   define  BPI data for MT6169  (7206_VERSION A_BPI Table_0225.xlsx) */
/*---------------------------------------------------------------------*/
/*    PRCB : bit  BPI   pin function                                   */
/*            0    0    Reserved                                         */
/*            1    1    Reserved                                        */
/*            2    2    PRXB1(L)_B3(H)                                   */
/*            3    3    Reserved                                         */
/*            4    4    Reserved                                  */
/*            5    5    Reserved                                         */
/*            6    6    Reserved                                         */
/*            7    7    SKY13416_V1                                     */
/*            8    8    SKY13416_V2                                     */
/*            9    9    SKY13416_V3                                    */
/*            10   10   Reserved                                         */
/*            11   11   CXM599_CLTA                                 */
/*            12   12   Reserved                                    */
/*            13   13   Reserved                            */
/*            14   14   CXM599_CLTB                                         */
/*            15   15   Reserved                                         */
/*            16   16   Reserved                                         */
/*            17   17   Reserved                                         */
/*            18   18   Reserved                             */
/*            19   19   Reserved                                          */
/*            20   20   Reserved                                          */
/*            21   21   Reserved                                          */
/*            22   22   Reserved                                         */
/*            23   23   Reserved                                          */
/*            24   24   Reserved                                          */
/*            25   25   Reserved                                          */
/*            26   26   Reserved                                         */
/*            27   27   Reserved                                         */
/*            28   28   Reserved                                         */
/*            29   29   Reserved                                         */
/*            30   30   Reserved                                         */
/*            31   31   Reserved                                         */
/*---------------------------------------------------------------------*/

 //* --------------------- PDATA_BAND1 Start ---------------------------*/
#define    PDATA_BAND1_PR1      0x00000005     
#define    PDATA_BAND1_PR2      0x00000005     
#define    PDATA_BAND1_PR2B     PDATA_BAND1_PR2
#define    PDATA_BAND1_PR3      0x00000000     
#define    PDATA_BAND1_PT1      PDATA_BAND1_PR1
#define    PDATA_BAND1_PT2      0x00000001     
#define    PDATA_BAND1_PT2B     PDATA_BAND1_PT2
#define    PDATA_BAND1_PT3      0x00000000     
 /* --------------------- PDATA_BAND1 End ------------------------------*/
 /* --------------------- PDATA_BAND1 RXD Start ------------------------*/
#define    PDATA2_BAND1_PR1     0x00000000      
#define    PDATA2_BAND1_PR2     0x00000000      
#define    PDATA2_BAND1_PR2B    PDATA2_BAND1_PR2
#define    PDATA2_BAND1_PR3     0x00000000      
 /* --------------------- PDATA_BAND1 RXD End --------------------------*/
 /* --------------------- PDATA_BAND2 Start ----------------------------*/
#define    PDATA_BAND2_PR1      0x00000001     
#define    PDATA_BAND2_PR2      0x00000001     
#define    PDATA_BAND2_PR2B     PDATA_BAND2_PR2
#define    PDATA_BAND2_PR3      0x00000000     
#define    PDATA_BAND2_PT1      PDATA_BAND2_PR1
#define    PDATA_BAND2_PT2      0x00000001     
#define    PDATA_BAND2_PT2B     PDATA_BAND2_PT2
#define    PDATA_BAND2_PT3      0x00000000     
 /* --------------------- PDATA_BAND2 End ------------------------------*/
 /* --------------------- PDATA_BAND2 RXD Start ------------------------*/
#define    PDATA2_BAND2_PR1     0x00000000      
#define    PDATA2_BAND2_PR2     0x00000000      
#define    PDATA2_BAND2_PR2B    PDATA2_BAND2_PR2
#define    PDATA2_BAND2_PR3     0x00000000      
 /* --------------------- PDATA_BAND2 RXD End --------------------------*/
/* --------------------- PDATA_BAND3 Start ---------------------------*/
#define    PDATA_BAND3_PR1      0x00000000     
#define    PDATA_BAND3_PR2      0x00000000     
#define    PDATA_BAND3_PR2B     PDATA_BAND3_PR2
#define    PDATA_BAND3_PR3      0x00000000     
#define    PDATA_BAND3_PT1      PDATA_BAND3_PR1
#define    PDATA_BAND3_PT2      0x00000000     
#define    PDATA_BAND3_PT2B     PDATA_BAND3_PT2
#define    PDATA_BAND3_PT3      0x00000000     
/* --------------------- PDATA_BAND3 End ------------------------------*/
/* --------------------- PDATA_BAND3 RXD Start ------------------------*/
#define    PDATA2_BAND3_PR1     0x00000000      
#define    PDATA2_BAND3_PR2     0x00000000      
#define    PDATA2_BAND3_PR2B    PDATA2_BAND3_PR2
#define    PDATA2_BAND3_PR3     0x00000000      
/* --------------------- PDATA_BAND3 RXD End --------------------------*/
 /* --------------------- PDATA_BAND5 Start ----------------------------*/
#define    PDATA_BAND5_PR1      0x00000000     
#define    PDATA_BAND5_PR2      0x00000000     
#define    PDATA_BAND5_PR2B     PDATA_BAND5_PR2
#define    PDATA_BAND5_PR3      0x00000000     
#define    PDATA_BAND5_PT1      PDATA_BAND5_PR1
#define    PDATA_BAND5_PT2      0x00000000     
#define    PDATA_BAND5_PT2B     PDATA_BAND5_PT2
#define    PDATA_BAND5_PT3      0x00000000
 /* --------------------- PDATA_BAND5 End ------------------------------*/
 /* --------------------- PDATA_BAND5 RXD Start ------------------------*/
#define    PDATA2_BAND5_PR1     0x00000000      
#define    PDATA2_BAND5_PR2     0x00000000      
#define    PDATA2_BAND5_PR2B    PDATA2_BAND5_PR2
#define    PDATA2_BAND5_PR3     0x00000000      
 /* --------------------- PDATA_BAND5 RXD End --------------------------*/
/* --------------------- PDATA_BAND6 Start ----------------------------*/
#define    PDATA_BAND6_PR1      PDATA_BAND5_PR1
#define    PDATA_BAND6_PR2      PDATA_BAND5_PR2
#define    PDATA_BAND6_PR2B     PDATA_BAND5_PR2B
#define    PDATA_BAND6_PR3      PDATA_BAND5_PR3
#define    PDATA_BAND6_PT1      PDATA_BAND5_PT1
#define    PDATA_BAND6_PT2      PDATA_BAND5_PT2
#define    PDATA_BAND6_PT2B     PDATA_BAND5_PT2B
#define    PDATA_BAND6_PT3      PDATA_BAND5_PT3
/* --------------------- PDATA_BAND6 End ------------------------------*/
/* --------------------- PDATA_BAND6 RXD Start ------------------------*/
#define    PDATA2_BAND6_PR1     PDATA2_BAND5_PR1
#define    PDATA2_BAND6_PR2     PDATA2_BAND5_PR2
#define    PDATA2_BAND6_PR2B    PDATA2_BAND5_PR2B
#define    PDATA2_BAND6_PR3     PDATA2_BAND5_PR3
/* --------------------- PDATA_BAND6 RXD End --------------------------*/
 /* --------------------- PDATA_BAND8 Start ----------------------------*/
#define    PDATA_BAND8_PR1      0x00000000     
#define    PDATA_BAND8_PR2      0x00000000     
#define    PDATA_BAND8_PR2B     PDATA_BAND8_PR2
#define    PDATA_BAND8_PR3      0x00000000     
#define    PDATA_BAND8_PT1      PDATA_BAND8_PR1
#define    PDATA_BAND8_PT2      0x00000000     
#define    PDATA_BAND8_PT2B     PDATA_BAND8_PT2
#define    PDATA_BAND8_PT3      0x00000000
 /* --------------------- PDATA_BAND8 End ------------------------------*/
 /* --------------------- PDATA_BAND8 RXD Start ------------------------*/
#define    PDATA2_BAND8_PR1     0x00004000      
#define    PDATA2_BAND8_PR2     0x00004000      
#define    PDATA2_BAND8_PR2B    PDATA2_BAND8_PR2
#define    PDATA2_BAND8_PR3     0x00000000      
 /* --------------------- PDATA_BAND8 RXD End --------------------------*/ 
/*MT6169*/ /****************************************************************************/
/*MT6169*/ /* Define your band mode selection on one of five main path LNA ports.      */
/*MT6169*/ /* Each of the 5 independent LNA/mixer/divider are either dedicated to      */
/*MT6169*/ /* either high (VCO divide-by-2) or low (VCO divide-by-4) band.             */
/*MT6169*/ /* TBD: Complete the description later.                                     */
/*MT6169*/ /****************************************************************************/
/*MT6169*/ #define    BAND1_CHANNEL_SEL    LNA_HB_3
/*MT6169*/ #define    BAND2_CHANNEL_SEL    LNA_MB_1
/*MT6169*/ #define    BAND3_CHANNEL_SEL    NON_USED_BAND
/*MT6169*/ #define    BAND4_CHANNEL_SEL    NON_USED_BAND
/*MT6169*/ #define    BAND5_CHANNEL_SEL    LNA_LB_2
/*MT6169*/ #define    BAND6_CHANNEL_SEL    NON_USED_BAND
/*MT6169*/ #define    BAND8_CHANNEL_SEL    LNA_LB_1
/*MT6169*/ #define    BAND9_CHANNEL_SEL    NON_USED_BAND
/*MT6169*/ #define    BAND10_CHANNEL_SEL   NON_USED_BAND
/*MT6169*/ #define    BAND11_CHANNEL_SEL   NON_USED_BAND
/*MT6169*/ #define    BAND19_CHANNEL_SEL   NON_USED_BAND
/*MT6169*/
/*MT6169*/  /****************************************************************************/
/*MT6169*/  /* RXD. Descript it later.                                                   */
/*MT6169*/  /****************************************************************************/
/*MT6169*/ #define    BAND1_CHANNEL2_SEL    LNA_RXD_HB_1 
/*MT6169*/ #define    BAND2_CHANNEL2_SEL    NON_USED_BAND 
/*MT6169*/ #define    BAND3_CHANNEL2_SEL    NON_USED_BAND
/*MT6169*/ #define    BAND4_CHANNEL2_SEL    NON_USED_BAND
/*MT6169*/ #define    BAND5_CHANNEL2_SEL    LNA_RXD_LB_1 
/*MT6169*/ #define    BAND6_CHANNEL2_SEL    NON_USED_BAND
/*MT6169*/ #define    BAND8_CHANNEL2_SEL    LNA_RXD_LB_3 
/*MT6169*/ #define    BAND9_CHANNEL2_SEL    NON_USED_BAND
/*MT6169*/ #define    BAND10_CHANNEL2_SEL   NON_USED_BAND
/*MT6169*/ #define    BAND11_CHANNEL2_SEL   NON_USED_BAND
/*MT6169*/ #define    BAND19_CHANNEL2_SEL   NON_USED_BAND
/*MT6169*/ /************************************************************/
/*MT6169*/ /* Define your tx output selection                          */
/*MT6169*/ /* There are two high band and one low band to choose.      */
/*MT6169*/ /* All options are listed below:                            */
/*MT6169*/ /* TX_HIGH_BAND3/TX_HIGH_BAND2/TX_LOW_BAND1/TX_NULL_BAND    */
/*MT6169*/ /************************************************************/
/*MT6169*/ #define    BAND1_OUTPUT_SEL     TX_MB_2
/*MT6169*/ #define    BAND2_OUTPUT_SEL     TX_MB_2
/*MT6169*/ #define    BAND3_OUTPUT_SEL     TX_NULL_BAND
/*MT6169*/ #define    BAND4_OUTPUT_SEL     TX_NULL_BAND
/*MT6169*/ #define    BAND5_OUTPUT_SEL     TX_LB_2
/*MT6169*/ #define    BAND6_OUTPUT_SEL     TX_NULL_BAND
/*MT6169*/ #define    BAND8_OUTPUT_SEL     TX_LB_2
/*MT6169*/ #define    BAND9_OUTPUT_SEL     TX_NULL_BAND
/*MT6169*/ #define    BAND10_OUTPUT_SEL    TX_NULL_BAND
/*MT6169*/ #define    BAND11_OUTPUT_SEL    TX_NULL_BAND
/*MT6169*/ #define    BAND19_OUTPUT_SEL    TX_NULL_BAND
/*MT6169*/
/*MT6169*/ /************************************************************/
/*MT6169*/ /* For using the V-battery as instead setting               */
/*MT6169*/ /************************************************************/
/*MT6169*/ #define    PMU_PASETTING         KAL_TRUE
/*MT6169*/ /************************************************************/
/*MT6169*/ /* For RXD single test, customer may use the RXD only,      */
/*MT6169*/ /* need to write RX_MAIN_PATH_ON & RX_DIVERSITY_PATH_ON to  */
/*MT6169*/ /* 0xFFFFFFFF after test                               */
/*MT6169*/ /************************************************************/
/*MT6169*/ #define    RX_DIVERSITY_ALWAYS_ON KAL_FALSE
/*MT6169*/ /************************************************************/
/*MT6169*/ /* For PA drift compensation by different band's PA         */
/*MT6169*/ /************************************************************/
/*MT6169*/ #define    PA_DIRFT_COMPENSATION 0x00000000
/*MT6169*/
/*MT6169*/ /************************************************************/
/*MT6169*/ /* For MPR back off for SAR& lowering PA temerature& UPA/DPA*/
/*MT6169*/ /* PAPR concern                                             */
/*MT6169*/ /************************************************************/
/*MT6169*/ #define    MPR_BACK_OFF_HSDPA_BAND1  MPRSetting2
/*MT6169*/ #define    MPR_BACK_OFF_HSDPA_BAND2  MPRSetting2
/*MT6169*/ #define    MPR_BACK_OFF_HSDPA_BAND3  MPRSetting2
/*MT6169*/ #define    MPR_BACK_OFF_HSDPA_BAND4  MPRSetting2
/*MT6169*/ #define    MPR_BACK_OFF_HSDPA_BAND5  MPRSetting2
/*MT6169*/ #define    MPR_BACK_OFF_HSDPA_BAND6  MPRSetting2
/*MT6169*/ #define    MPR_BACK_OFF_HSDPA_BAND8  MPRSetting2
/*MT6169*/ #define    MPR_BACK_OFF_HSDPA_BAND9  MPRSetting2
/*MT6169*/ #define    MPR_BACK_OFF_HSDPA_BAND10 MPRSetting2
/*MT6169*/ #define    MPR_BACK_OFF_HSDPA_BAND11 MPRSetting2
/*MT6169*/ #define    MPR_BACK_OFF_HSDPA_BAND19 MPRSetting2
/*MT6169*/ #define    MPR_BACK_OFF_HSUPA_BAND1  MPRSetting2
/*MT6169*/ #define    MPR_BACK_OFF_HSUPA_BAND2  MPRSetting2
/*MT6169*/ #define    MPR_BACK_OFF_HSUPA_BAND3  MPRSetting2
/*MT6169*/ #define    MPR_BACK_OFF_HSUPA_BAND4  MPRSetting2
/*MT6169*/ #define    MPR_BACK_OFF_HSUPA_BAND5  MPRSetting2
/*MT6169*/ #define    MPR_BACK_OFF_HSUPA_BAND6  MPRSetting2
/*MT6169*/ #define    MPR_BACK_OFF_HSUPA_BAND8  MPRSetting2
/*MT6169*/ #define    MPR_BACK_OFF_HSUPA_BAND9  MPRSetting2
/*MT6169*/ #define    MPR_BACK_OFF_HSUPA_BAND10 MPRSetting2
/*MT6169*/ #define    MPR_BACK_OFF_HSUPA_BAND11 MPRSetting2
/*MT6169*/ #define    MPR_BACK_OFF_HSUPA_BAND19 MPRSetting2
/*MT6169*/
/*MT6169*/ /************************************************************/
/*MT6169*/ /* At MT6589+MT6320PMIC, Vrf18_1(MD1) can use bulk/LDO mode */
/*MT6169*/ /* take bulk mode as default value*/
/*MT6169*/ /************************************************************/
/*MT6169*/ #define    ULTRA_LOW_COST_EN 0
/*MT6169*/
#if (IS_3G_TAS_SUPPORT)
/************************************************************/
/* For 3G Transmit Antenna Switch feature (TAS)             */
/************************************************************/
#define UMTS_TAS_BPI_PIN_NULL    -1/* Do NOT modify */

#define UMTS_TAS_BPI_PIN_1       0                    /* the 1st BPI pin number for TAS*/
#define UMTS_TAS_BPI_PIN_2       UMTS_TAS_BPI_PIN_NULL/* the 2nd BPI pin number for TAS*/
#define UMTS_TAS_BPI_PIN_3       UMTS_TAS_BPI_PIN_NULL/* the 3rd BPI pin number for TAS*/

#define UMTS_TAS_BPI_PIN_MASK    UL1D_TAS_BPI_PIN_GEN(1/*PIN_1 EN*/, 1/*PIN_2 EN*/, 1/*PIN_3 EN*/)

/* ------------- PDATA_TAS0~7 --------------*/
#define PDATA_TAS0               UL1D_TAS_BPI_PIN_GEN(0/*PIN_1 EN*/, 0/*PIN_2 EN*/, 0/*PIN_3 EN*/)
#define PDATA_TAS1               UL1D_TAS_BPI_PIN_GEN(1/*PIN_1 EN*/, 0/*PIN_2 EN*/, 0/*PIN_3 EN*/)
#define PDATA_TAS2               PDATA_TAS0
#define PDATA_TAS3               PDATA_TAS0
#define PDATA_TAS4               PDATA_TAS0
#define PDATA_TAS5               PDATA_TAS0
#define PDATA_TAS6               PDATA_TAS0
#define PDATA_TAS7               PDATA_TAS0


/************************************************************/
/* TAS BPI and Antenna Mapping                              */
/* PDATA_Band1_TAS1 --> Antenna RXD                         */
/* PDATA_Band1_TAS2 --> Antenna AUX                         */
/* PDATA_Band1_TAS3 --> Spare                               */
/*                                                          */
/************************************************************/

/* ------------- PDATA_BAND1_TAS1/2/3 Start --------------*/
#define PDATA_Band1_TAS1         PDATA_TAS1
#define PDATA_Band1_TAS2         PDATA_TAS2
#define PDATA_Band1_TAS3         PDATA_TAS3

/* ------------- PDATA_BAND2_TAS1/2/3 Start --------------*/
#define PDATA_Band2_TAS1         PDATA_TAS1
#define PDATA_Band2_TAS2         PDATA_TAS2
#define PDATA_Band2_TAS3         PDATA_TAS3

/* ------------- PDATA_BAND3_TAS1/2/3 Start --------------*/
#define PDATA_Band3_TAS1         PDATA_TAS1
#define PDATA_Band3_TAS2         PDATA_TAS2
#define PDATA_Band3_TAS3         PDATA_TAS3

/* ------------- PDATA_BAND4_TAS1/2/3 Start --------------*/
#define PDATA_Band4_TAS1         PDATA_TAS1
#define PDATA_Band4_TAS2         PDATA_TAS2
#define PDATA_Band4_TAS3         PDATA_TAS3

/* ------------- PDATA_BAND5_TAS1/2/3 Start --------------*/
#define PDATA_Band5_TAS1         PDATA_TAS1
#define PDATA_Band5_TAS2         PDATA_TAS2
#define PDATA_Band5_TAS3         PDATA_TAS3

/* ------------- PDATA_BAND6_TAS1/2/3 Start --------------*/
#define PDATA_Band6_TAS1         PDATA_TAS1
#define PDATA_Band6_TAS2         PDATA_TAS2
#define PDATA_Band6_TAS3         PDATA_TAS3

/* ------------- PDATA_BAND7_TAS1/2/3 Start --------------*/
#define PDATA_Band7_TAS1         PDATA_TAS1
#define PDATA_Band7_TAS2         PDATA_TAS2
#define PDATA_Band7_TAS3         PDATA_TAS3

//* ------------- PDATA_BAND8_TAS1/2/3 Start --------------*/
#define PDATA_Band8_TAS1         PDATA_TAS1
#define PDATA_Band8_TAS2         PDATA_TAS2
#define PDATA_Band8_TAS3         PDATA_TAS3

/* ------------- PDATA_BAND9_TAS1/2/3 Start --------------*/
#define PDATA_Band9_TAS1         PDATA_TAS1
#define PDATA_Band9_TAS2         PDATA_TAS2
#define PDATA_Band9_TAS3         PDATA_TAS3

/* ------------- PDATA_BAND10_TAS1/2/3 Start --------------*/
#define PDATA_Band10_TAS1         PDATA_TAS1
#define PDATA_Band10_TAS2         PDATA_TAS2
#define PDATA_Band10_TAS3         PDATA_TAS3

/* ------------- PDATA_BAND11_TAS1/2/3 Start --------------*/
#define PDATA_Band11_TAS1         PDATA_TAS1
#define PDATA_Band11_TAS2         PDATA_TAS2
#define PDATA_Band11_TAS3         PDATA_TAS3

/* ------------- PDATA_BAND19_TAS1/2/3 Start --------------*/
#define PDATA_Band19_TAS1         PDATA_TAS1
#define PDATA_Band19_TAS2         PDATA_TAS2
#define PDATA_Band19_TAS3         PDATA_TAS3


#endif/*IS_3G_TAS_SUPPORT*/


/*MT6169*/ #define    RX_HIGHBAND1_INDICATOR UMTSBand1
/*MT6169*/ #define    RX_HIGHBAND2_INDICATOR UMTSBand2
/*MT6169*/ #define    RX_HIGHBAND3_INDICATOR UMTSBandNone
/*MT6169*/ #define    RX_LOWBAND1_INDICATOR UMTSBand5
/*MT6169*/ #define    RX_LOWBAND2_INDICATOR UMTSBand8


/*============================================================================== */

#endif
