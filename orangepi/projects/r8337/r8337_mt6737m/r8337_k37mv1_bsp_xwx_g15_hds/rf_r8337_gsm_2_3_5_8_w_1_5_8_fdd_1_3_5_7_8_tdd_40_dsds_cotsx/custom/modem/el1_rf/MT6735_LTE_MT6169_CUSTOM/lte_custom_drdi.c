/*******************************************************************************
*  Copyright Statement:
*  --------------------
*  This software is protected by Copyright and the information contained
*  herein is confidential. The software may not be copied and the information
*  contained herein may not be used or disclosed except with the written
*  permission of MediaTek Inc. (C) 2012
*
*******************************************************************************/

/*******************************************************************************
 *
 * Filename:
 * ---------
 *	lte_custom_drdi.c
 *
 * Project:
 * --------
 *  MOLY
 *
 * Description:
 * ------------
 *  Dynamic Radio-setting Dedicated Image.
 *  RF parameters data input file
 *
 * Author:
 * -------
 * -------
 *
 *******************************************************************************/

#ifdef __MTK_TARGET__

/*******************************************************************************
** Includes
*******************************************************************************/
#include "lte_custom_drdi.h"
#include "lte_custom_mipi.h"   // for DRDI of MIPI usage
#include "lte_custom_rf.h"

/***************************************************************************
 * Global Data
 ***************************************************************************/
/* Look up table from action id to action function
 * See the enum #El1CustomFunction for members of
 * the table */
const El1CustomFunction el1CustomActionTable[EL1_CUSTOM_MAX_PROC_ACTIONS] =
{
   NULL,

#if EL1_CUSTOM_GPIO_ENABLE
   EPHY_CUSTOM_DynamicInitByGPIO,
#else
   NULL, /* Null action */
#endif

#if EL1_CUSTOM_ADC_ENABLE
   EPHY_CUSTOM_DynamicInitByADC,
#else
   NULL, /* Null action */
#endif

#if EL1_CUSTOM_BARCODE_ENABLE
   EPHY_CUSTOM_DynamicInitByBarcode
#else
   NULL  /* Null action */
#endif
};

#define MICROSECOND_TO_26M(x)                      ((kal_int32)((x)*26))

/***************************************************************************
 * Data array of pointers pointed to data array for custom data
 ***************************************************************************/
//********************************************
// LTE Band Indicator Custom data definition
//********************************************
/* Pointer array for customer to input 4G band support parameters
 * with pointers for each of the configuration set to be detected */
LTE_Band                                 *el1_rf_bandind_array_ptr[EL1_CUSTOM_TOTAL_SET_NUMS];

#if defined(__CDMA2000_RAT__)
// LTE Band Indicator Custom data definition for SVLTE
//********************************************
/* Pointer array for customer to input 4G band support parameters
 * with pointers for each of the configuration set to be detected */
LTE_Band                                 *el1_rf_svlte_bandind_array_ptr[EL1_CUSTOM_TOTAL_SET_NUMS];
#endif

//**************************************
// LTE BPI data Custom data definition
//**************************************
/* Pointer array for customer to input 4G BPI data parameters
 * with pointers for each of the configuration set to be detected */
El1CustomDynamicInitLteBpiData           *el1_rf_bpidata_array_ptr[EL1_CUSTOM_TOTAL_SET_NUMS];

//**************************************
// LTE BPI Timing Custom data definition
//**************************************
/* Pointer array for customer to input 4G BPI data parameters
 * with pointers for each of the configuration set to be detected */
El1CustomDynamicInitLteBpiTiming         *el1_rf_bpitiming_array_ptr[EL1_CUSTOM_TOTAL_SET_NUMS];

//***************************************************
// LTE LNA port/Tx path data Custom data definition
//***************************************************
/* Pointer array for customer to input 4G LNA port and TX path parameters by band
 * with pointers for each of the configuration set to be detected */
El1CustomDynamicInitLteRxLnaPortTxPath   *el1_rf_lna_txpath_array_ptr[EL1_CUSTOM_TOTAL_SET_NUMS];

//***************************************************
// LTE splitting band Custom data definition
//***************************************************
/* Pointer array for customer to input 4G splitting band parameters
 * with pointers for each of the configuration set to be detected */
El1CustomDynamicInitLteSplitBandInd      *el1_rf_splitbandind_array_ptr[EL1_CUSTOM_TOTAL_SET_NUMS];

//***************************************************
// LTE BPI/LNA port/Tx path data Custom data definition
//***************************************************
/* Pointer array for customer to input 4G BPI, LNA port, and TX path parameters by band
 * with pointers for each of the configuration set to be detected */
El1CustomDynamicInitLteSplitRfData       *el1_rf_splitrfdata_array_ptr[EL1_CUSTOM_TOTAL_SET_NUMS];

//***************************************************
// LTE Transmit Antenna Selection data Custom data definition
//***************************************************
/* Pointer array for customer to input Transmit Antenna Seletction parameters
 * with pointers for each of the configuration set to be detected */
El1CustomDynamicInitLteTASParameter       *el1_rf_tas_parameter_array_ptr[EL1_CUSTOM_TOTAL_SET_NUMS];

//***************************************************
// LTE Init Antenna data Custom data definition
//***************************************************
/* Pointer array for customer to input Init Antenna parameters
 * with pointers for each of the configuration set to be detected */
El1CustomDynamicInitAntParameter         *el1_rf_init_ant_parameter_array_ptr[EL1_CUSTOM_TOTAL_SET_NUMS];

//*************************************************
// LTE Tx calibration data Custom data definition
//*************************************************
// Tx Ramp data
/* Pointer array for customer to input 4G TX Ramp calibration data
 * with pointers for each of the configuration set to be detected */
El1CustomDynamicInitLteTxRampData        *el1_rf_txramp_array_ptr[EL1_CUSTOM_TOTAL_SET_NUMS];

// Tx PA oct-level data
/* Pointer array for customer to input 4G TX oct-level calibration data
 * with pointers for each of the configuration set to be detected */
El1CustomDynamicInitLteTxPaOctLvlData    *el1_rf_txoctlvl_array_ptr[EL1_CUSTOM_TOTAL_SET_NUMS];

//*************************************************
// LTE Rx calibration data Custom data definition
//*************************************************
/* Pointer array for customer to input 4G RX path loss calibration data
 * with pointers for each of the configuration set to be detected */
El1CustomDynamicInitLteRxPathLossData    *el1_rf_rxpathloss_array_ptr[EL1_CUSTOM_TOTAL_SET_NUMS];

//*********************************************
// LTE Temperature DAC Custom data definition
//*********************************************
/* Pointer array for customer to input 4G Temp. DAC calibration data
 * with pointers for each of the configuration set to be detected */
El1CustomDynamicInitLteTempDac           *el1_rf_tempdac_array_ptr[EL1_CUSTOM_TOTAL_SET_NUMS];

//******************************************************
// LTE Tx PRACH TM Compensation Custom data definition
//******************************************************
/* Pointer array for customer to input 4G Tx PRACH TM Compensation calibration data
 * with pointers for each of the configuration set to be detected */
//El1CustomDynamicInitLteTxPrachTmCompData *el1_rf_txprachtmcomp_array_ptr[EL1_CUSTOM_TOTAL_SET_NUMS];



/***************************************************************************
 * Data definition for custom to input data
 ***************************************************************************/
//********************************************
// LTE Band Indicator Custom data definition
//********************************************
LTE_Band el1CustomLteBand[EL1_CUSTOM_TOTAL_SET_NUMS][EL1_CUSTOM_MAX_RF_SUPPORT_BAND_NUM] =
{
 //{/*Set XX*/ band_idx 0  , band_idx 1  , band_idx 2  , band_idx 3  , band_idx 4  , band_idx 5  , band_idx 6  , band_idx 7  , band_idx 8  , band_idx 9  , band_idx 10 , band_idx 11 , band_idx 12 , band_idx 13 },
   {/*Set  0*/ LTE_Band1   , LTE_Band38  , LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone}, 
#if EL1_CUSTOM_DYNAMIC_INIT_ENABLE
   {/*Set  1*/ LTE_Band2   , LTE_Band39  , LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone}, 
#endif
   /*** APPEND new set of custom data HERE ***/      
};

#if defined(__CDMA2000_RAT__)
//********************************************
// LTE Band Indicator Custom data definition for SVLTE
//********************************************
LTE_Band el1CustomLteBandSVLTE[EL1_CUSTOM_TOTAL_SET_NUMS][EL1_CUSTOM_MAX_RF_SUPPORT_BAND_NUM] =
{
 //{/*Set XX*/ band_idx 0  , band_idx 1  , band_idx 2  , band_idx 3  , band_idx 4  , band_idx 5  , band_idx 6  , band_idx 7  , band_idx 8  , band_idx 9  , band_idx 10 , band_idx 11 , band_idx 12 , band_idx 13 },
   {/*Set  0*/ LTE_Band1   , LTE_Band38  , LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone}, 
   #if EL1_CUSTOM_DYNAMIC_INIT_ENABLE
   {/*Set  1*/ LTE_Band2   , LTE_Band39  , LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone, LTE_BandNone}, 
   #endif
   /*** APPEND new set of custom data HERE ***/      
};
#endif

//**************************************
// LTE BPI data Custom data definition
//**************************************
El1CustomDynamicInitLteBpiData el1CustomLteBpiData[EL1_CUSTOM_TOTAL_SET_NUMS][EL1_CUSTOM_MAX_RF_SUPPORT_BAND_NUM] =
{
 //{/*Set XX*//*band_idx XX*/{Band        , {PR1       , PR2       , PR3           , PT1       , PT2       , PT3           }},
   {/*Set  0*//*band_idx  0*/{LTE_Band1   , {0x0FFFFFFF, 0x0FFFFFFF, LTE_PDATA_OFF , 0x0FFFFFFF, 0x0FFFFFFF, LTE_PDATA_OFF }},
              /*band_idx  1*/{LTE_Band38  , {0x0EEEEEEE, 0x0EEEEEEE, LTE_PDATA_OFF , 0x0EEEEEEE, 0x0EEEEEEE, LTE_PDATA_OFF }},
              /*band_idx  2*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }},
              /*band_idx  3*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }},
              /*band_idx  4*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }},
              /*band_idx  5*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }},
              /*band_idx  6*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }},
              /*band_idx  7*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }},
              /*band_idx  8*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }},
              /*band_idx  9*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }},
              /*band_idx 10*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }},
              /*band_idx 11*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }},
              /*band_idx 12*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }},
              /*band_idx 13*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }}
   },
#if EL1_CUSTOM_DYNAMIC_INIT_ENABLE
   {/*Set  1*//*band_idx  0*/{LTE_Band2   , {0x0FFFFFFF, 0x0FFFFFFF, LTE_PDATA_OFF , 0x0FFFFFFF, 0x0FFFFFFF, LTE_PDATA_OFF }},
              /*band_idx  1*/{LTE_Band39  , {0x0EEEEEEE, 0x0EEEEEEE, LTE_PDATA_OFF , 0x0EEEEEEE, 0x0EEEEEEE, LTE_PDATA_OFF }},
              /*band_idx  2*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }},
              /*band_idx  3*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }},
              /*band_idx  4*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }},
              /*band_idx  5*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }},
              /*band_idx  6*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }},
              /*band_idx  7*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }},
              /*band_idx  8*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }},
              /*band_idx  9*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }},
              /*band_idx 10*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }},
              /*band_idx 11*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }},
              /*band_idx 12*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }},
              /*band_idx 13*/{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }}
   },
#endif
   /*** APPEND new set of custom data HERE ***/
};



//**************************************
// LTE BPI Timin Custom data definition
//**************************************
El1CustomDynamicInitLteBpiTiming el1CustomLteBpiTiming[EL1_CUSTOM_TOTAL_SET_NUMS] =
{

 //{/*Set  0*/ { {TC_FPR1               , TC_FPR2               , TC_FPR3               , TC_FPT1               , TC_FPT2             , TC_FPT3            }},
    /*Set  0*/{{ MICROSECOND_TO_26M(105), MICROSECOND_TO_26M(26), MICROSECOND_TO_26M(15), MICROSECOND_TO_26M(9), MICROSECOND_TO_26M(8), MICROSECOND_TO_26M(5),
 //{/*Set  0*/ { {TC_TPR1               , TC_TPR2               , TC_TPR3               , TC_TPT1              , TC_TPT2              , TC_TPT3            }},
                 MICROSECOND_TO_26M(21) , MICROSECOND_TO_26M(20), MICROSECOND_TO_26M(1) , MICROSECOND_TO_26M(5), MICROSECOND_TO_26M(4), MICROSECOND_TO_26M(1)
              }},
#if EL1_CUSTOM_DYNAMIC_INIT_ENABLE

 //{/*Set  1*/ { {TC_FPR1               , TC_FPR2               , TC_FPR3               , TC_FPT1               , TC_FPT2             , TC_FPT3            }},
    /*Set  1*/{{ MICROSECOND_TO_26M(105), MICROSECOND_TO_26M(26), MICROSECOND_TO_26M(15), MICROSECOND_TO_26M(9), MICROSECOND_TO_26M(8), MICROSECOND_TO_26M(5),
 //{/*Set  1*/ { {TC_TPR1               , TC_TPR2               , TC_TPR3               , TC_TPT1              , TC_TPT2              , TC_TPT3            }},
                 MICROSECOND_TO_26M(21) , MICROSECOND_TO_26M(20), MICROSECOND_TO_26M(1) , MICROSECOND_TO_26M(5), MICROSECOND_TO_26M(4), MICROSECOND_TO_26M(1)
              }},
#endif
   /*** APPEND new set of custom data HERE ***/
};
//***************************************************
// LTE RX LNA port/Tx path data Custom data definition
//***************************************************
El1CustomDynamicInitLteRxLnaPortTxPath el1CustomLteRxLnaPortTxPath[EL1_CUSTOM_TOTAL_SET_NUMS][EL1_CUSTOM_MAX_RF_SUPPORT_BAND_NUM] =
{
 //{/*Set XX*//*band_idx XX*/{Band        , RX_IO         , RXD_IO         , TX_IO         },
   {/*Set  0*//*band_idx  0*/{LTE_Band1   , RX_IO_LB3     , RXD_IO_LB3     , TX_IO_LB4     },
              /*band_idx  1*/{LTE_Band38  , RX_IO_LB3     , RXD_IO_LB3     , TX_IO_LB4     },
              /*band_idx  2*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED},
              /*band_idx  3*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED},
              /*band_idx  4*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED},
              /*band_idx  5*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED},
              /*band_idx  6*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED},
              /*band_idx  7*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED},
              /*band_idx  8*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED},
              /*band_idx  9*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED},
              /*band_idx 10*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED},
              /*band_idx 11*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED},
              /*band_idx 12*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED},
              /*band_idx 13*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}
   }, 
#if EL1_CUSTOM_DYNAMIC_INIT_ENABLE
   {/*Set  1*//*band_idx  0*/{LTE_Band2   , RX_IO_LB3     , RXD_IO_LB3     , TX_IO_LB4     },
              /*band_idx  1*/{LTE_Band39  , RX_IO_LB3     , RXD_IO_LB3     , TX_IO_LB4     },
              /*band_idx  2*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED},
              /*band_idx  3*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED},
              /*band_idx  4*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED},
              /*band_idx  5*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED},
              /*band_idx  6*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED},
              /*band_idx  7*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED},
              /*band_idx  8*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED},
              /*band_idx  9*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED},
              /*band_idx 10*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED},
              /*band_idx 11*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED},
              /*band_idx 12*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED},
              /*band_idx 13*/{LTE_BandNone, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}
   },
#endif 
   /*** APPEND new set of custom data HERE ***/
};

//***************************************************
// LTE Splitting Band Custom data definition
//***************************************************
El1CustomDynamicInitLteSplitBandInd el1CustomLteSplitBandInd[EL1_CUSTOM_TOTAL_SET_NUMS][EL1_CUSTOM_MAX_RF_SPLIT_BAND] =
{
 //{/*Set XX*//*BAND_SPLIT_INDICATORXX*/{Band        , SplitNum, SplitBandEndDL     , SplitBandEndUL     , PowerCompensation, CouplerCompensation
 //                                                              (Unit: 100KHz)       (Unit: 100KHz)       (Unit: S(6,8) dB)  (Unit: S(6,8) dB)
   {/*Set  0*//*BAND_SPLIT_INDICATOR1 */{LTE_BandNone, 0       , {    0,    0,    0}, {    0,    0,    0}, 0                , 0},
              /*BAND_SPLIT_INDICATOR2 */{LTE_BandNone, 0       , {    0,    0,    0}, {    0,    0,    0}, 0                , 0},
              /*BAND_SPLIT_INDICATOR3 */{LTE_BandNone, 0       , {    0,    0,    0}, {    0,    0,    0}, 0                , 0},
              /*BAND_SPLIT_INDICATOR4 */{LTE_BandNone, 0       , {    0,    0,    0}, {    0,    0,    0}, 0                , 0},
              /*BAND_SPLIT_INDICATOR5 */{LTE_BandNone, 0       , {    0,    0,    0}, {    0,    0,    0}, 0                , 0},
   },
#if EL1_CUSTOM_DYNAMIC_INIT_ENABLE
   {/*Set  1*//*BAND_SPLIT_INDICATOR1 */{LTE_Band28  , 2       , { 7880, 8030,    0}, { 7330, 7480,    0}, 0                , 0},
              /*BAND_SPLIT_INDICATOR2 */{LTE_Band41  , 3       , {25900,26200,26900}, {25900,26200,26900}, 0                , 0},
              /*BAND_SPLIT_INDICATOR3 */{LTE_BandNone, 0       , {    0,    0,    0}, {    0,    0,    0}, 0                , 0},
              /*BAND_SPLIT_INDICATOR4 */{LTE_BandNone, 0       , {    0,    0,    0}, {    0,    0,    0}, 0                , 0},
              /*BAND_SPLIT_INDICATOR5 */{LTE_BandNone, 0       , {    0,    0,    0}, {    0,    0,    0}, 0                , 0},
   },
#endif
};

//***************************************************
// LTE BPI/RX LNA port/Tx path data Custom data definition
//***************************************************
El1CustomDynamicInitLteSplitRfData el1CustomLteSplitRfData[EL1_CUSTOM_TOTAL_SET_NUMS][EL1_CUSTOM_MAX_RF_SPLIT_NUM] =
{
 //{/*Set XX*//*BAND_SPLIT_INDICATORXX*/{Band        , {PR1       , PR2       , PR3           , PT1       , PT2       , PT3           }, RX_IO         , RXD_IO         , TX_IO         },
   {/*Set  0*//*BAND_SPLIT_INDICATOR1 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of 2nd part of splitting band
              /*BAND_SPLIT_INDICATOR1 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of 3rd part of splitting band
              /*BAND_SPLIT_INDICATOR1 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of TX bypass path
              /*BAND_SPLIT_INDICATOR2 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of 2nd part of splitting band
              /*BAND_SPLIT_INDICATOR2 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of 3rd part of splitting band
              /*BAND_SPLIT_INDICATOR2 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of TX bypass path
              /*BAND_SPLIT_INDICATOR3 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of 2nd part of splitting band
              /*BAND_SPLIT_INDICATOR3 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of 3rd part of splitting band
              /*BAND_SPLIT_INDICATOR3 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of TX bypass path
              /*BAND_SPLIT_INDICATOR4 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of 2nd part of splitting band
              /*BAND_SPLIT_INDICATOR4 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of 3rd part of splitting band
              /*BAND_SPLIT_INDICATOR4 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of TX bypass path
              /*BAND_SPLIT_INDICATOR5 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of 2nd part of splitting band
              /*BAND_SPLIT_INDICATOR5 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of 3rd part of splitting band
              /*BAND_SPLIT_INDICATOR5 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of TX bypass path
   },
#if EL1_CUSTOM_DYNAMIC_INIT_ENABLE
   {/*Set  1*//*BAND_SPLIT_INDICATOR1 */{LTE_Band28  , {0x004B0000, 0x004B0000, LTE_PDATA_OFF , 0x004B0000, 0x004B0040, LTE_PDATA_OFF }, RX_IO_LB3     , RXD_IO_LB1     , TX_IO_LB2     }, // PDATA,IO of 2nd part of splitting band
              /*BAND_SPLIT_INDICATOR1 */{LTE_Band28  , {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of 3rd part of splitting band
              /*BAND_SPLIT_INDICATOR1 */{LTE_Band28  , {0x0       , 0x0       , LTE_PDATA_OFF , 0x001A0000, 0x001A0000, LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of TX bypass path
              /*BAND_SPLIT_INDICATOR2 */{LTE_Band41  , {0x01688100, 0x01688100, LTE_PDATA_OFF , 0x00280410, 0x00280410, LTE_PDATA_OFF }, RX_IO_HB1     , RXD_IO_HB3     , TX_IO_HB1     }, // PDATA,IO of 2nd part of splitting band
              /*BAND_SPLIT_INDICATOR2 */{LTE_Band41  , {0x01F58400, 0x01F58400, LTE_PDATA_OFF , 0x00340110, 0x00340110, LTE_PDATA_OFF }, RX_IO_HB1     , RXD_IO_HB3     , TX_IO_HB1     }, // PDATA,IO of 3rd part of splitting band
              /*BAND_SPLIT_INDICATOR2 */{LTE_Band41  , {0x0       , 0x0       , LTE_PDATA_OFF , 0x00340110, 0x00340110, LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of TX bypass path
              /*BAND_SPLIT_INDICATOR3 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of 2nd part of splitting band
              /*BAND_SPLIT_INDICATOR3 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of 3rd part of splitting band
              /*BAND_SPLIT_INDICATOR3 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of TX bypass path
              /*BAND_SPLIT_INDICATOR4 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of 2nd part of splitting band
              /*BAND_SPLIT_INDICATOR4 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of 3rd part of splitting band
              /*BAND_SPLIT_INDICATOR4 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of TX bypass path
              /*BAND_SPLIT_INDICATOR5 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of 2nd part of splitting band
              /*BAND_SPLIT_INDICATOR5 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of 3rd part of splitting band
              /*BAND_SPLIT_INDICATOR5 */{LTE_BandNone, {0x0       , 0x0       , LTE_PDATA_OFF , 0x0       , 0x0       , LTE_PDATA_OFF }, RX_IO_NON_USED, RXD_IO_NON_USED, TX_IO_NON_USED}, // PDATA,IO of TX bypass path
   },         
#endif
   /*** APPEND new set of custom data HERE ***/
};

//***************************************************
// LTE Transmit Antenna Selection data Custom data definition
//***************************************************
El1CustomDynamicInitLteTASParameter el1CustomLteTASParameter[EL1_CUSTOM_TOTAL_SET_NUMS] =
{
   {  /*Set  0*/
      {
         0,               // (LTE_TAS_ENA|(LTE_TAS_FOR_C2K_ENA<<1)),
         0,               // LTE_TAS_WITH_TEST_SIM_ENA,
         0x0000,          // MSB 16 bits of LTE_TAS_MASK,
         0x0000,          // LSB 16 bits of LTE_TAS_MASK,
         0,               // LTE_TAS_INIT_ANT,
         0,               // LTE_FORCE_TX_ANTENNA_ENABLE,
         0,               // LTE_FORCE_TX_ANTENNA_IDX,
         {
            LTE_BandNone, // BAND_TAS_INDICATOR1,
            LTE_BandNone, // BAND_TAS_INDICATOR2,
            LTE_BandNone, // BAND_TAS_INDICATOR3,
            LTE_BandNone, // BAND_TAS_INDICATOR4,
            LTE_BandNone, // BAND_TAS_INDICATOR5,
            LTE_BandNone, // BAND_TAS_INDICATOR6,
            LTE_BandNone, // BAND_TAS_INDICATOR7,
            LTE_BandNone, // BAND_TAS_INDICATOR8,
            LTE_BandNone, // BAND_TAS_INDICATOR9,
            LTE_BandNone, // BAND_TAS_INDICATOR10,
            LTE_BandNone, // BAND_TAS_INDICATOR11,
            LTE_BandNone, // BAND_TAS_INDICATOR12,
            LTE_BandNone, // BAND_TAS_INDICATOR13,
            LTE_BandNone, // BAND_TAS_INDICATOR14,
         },
      },
      {
         //                       TAS1        TAS2        TAS3        TAS4        TAS5        TAS6        TAS7
         /*BAND_TAS_INDICATOR1 */ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR2 */ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR3 */ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR4 */ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR5 */ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR6 */ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR7 */ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR8 */ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR9 */ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR10*/ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR11*/ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR12*/ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR13*/ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR14*/ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
      },
   },
#if EL1_CUSTOM_DYNAMIC_INIT_ENABLE
   {  /*Set  1*/
      {
         0,               // (LTE_TAS_ENA|(LTE_TAS_FOR_C2K_ENA<<1)),
         0,               // LTE_TAS_WITH_TEST_SIM_ENA,
         0x0000,          // MSB 16 bits of LTE_TAS_MASK,
         0x0000,          // LSB 16 bits of LTE_TAS_MASK,
         0,               // LTE_TAS_INIT_ANT,
         0,               // LTE_FORCE_TX_ANTENNA_ENABLE,
         0,               // LTE_FORCE_TX_ANTENNA_IDX,
         {
            LTE_BandNone, // BAND_TAS_INDICATOR1,
            LTE_BandNone, // BAND_TAS_INDICATOR2,
            LTE_BandNone, // BAND_TAS_INDICATOR3,
            LTE_BandNone, // BAND_TAS_INDICATOR4,
            LTE_BandNone, // BAND_TAS_INDICATOR5,
            LTE_BandNone, // BAND_TAS_INDICATOR6,
            LTE_BandNone, // BAND_TAS_INDICATOR7,
            LTE_BandNone, // BAND_TAS_INDICATOR8,
            LTE_BandNone, // BAND_TAS_INDICATOR9,
            LTE_BandNone, // BAND_TAS_INDICATOR10,
            LTE_BandNone, // BAND_TAS_INDICATOR11,
            LTE_BandNone, // BAND_TAS_INDICATOR12,
            LTE_BandNone, // BAND_TAS_INDICATOR13,
            LTE_BandNone, // BAND_TAS_INDICATOR14,
         },
      },
      {
         //                       TAS1        TAS2        TAS3        TAS4        TAS5        TAS6        TAS7
         /*BAND_TAS_INDICATOR1 */ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR2 */ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR3 */ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR4 */ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR5 */ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR6 */ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR7 */ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR8 */ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR9 */ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR10*/ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR11*/ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR12*/ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR13*/ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
         /*BAND_TAS_INDICATOR14*/ 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
      },
   },
#endif
};

//***************************************************
// LTE Init Antenna data Custom data definition
//***************************************************
El1CustomDynamicInitAntParameter el1CustomLteInitAntParameter[EL1_CUSTOM_TOTAL_SET_NUMS][LTE_ERF_TAS_BAND_NUM] =
{
   {  /*Set  0*/
      //                              LTE_TAS_INIT_ANT_INDICATOR,  LTE_TAS_INIT_ANT_INDEX
      {   /*LTE_TAS_INIT_ANT_INDICATOR1 */  LTE_BandNone,          LTE_InitAnt_Invalid,  }, 
      {   /*LTE_TAS_INIT_ANT_INDICATOR2 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR3 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR4 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR5 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR6 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR7 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR8 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR9 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR10 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR11 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR12 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR13 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR14 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },      
   },
#if EL1_CUSTOM_DYNAMIC_INIT_ENABLE
   {  /*Set  1*/
      //                              LTE_TAS_INIT_ANT_INDICATOR,  LTE_TAS_INIT_ANT_INDEX
      {   /*LTE_TAS_INIT_ANT_INDICATOR1 */  LTE_BandNone,          LTE_InitAnt_Invalid,  }, 
      {   /*LTE_TAS_INIT_ANT_INDICATOR2 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR3 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR4 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR5 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR6 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR7 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR8 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR9 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR10 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR11 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR12 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR13 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR14 */ LTE_BandNone,          LTE_InitAnt_Invalid,  }, 
   },
   {  /*Set  2*/
      //                              LTE_TAS_INIT_ANT_INDICATOR,  LTE_TAS_INIT_ANT_INDEX
      {   /*LTE_TAS_INIT_ANT_INDICATOR1 */  LTE_BandNone,          LTE_InitAnt_Invalid,  }, 
      {   /*LTE_TAS_INIT_ANT_INDICATOR2 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR3 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR4 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR5 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR6 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR7 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR8 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR9 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR10 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR11 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR12 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR13 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR14 */ LTE_BandNone,          LTE_InitAnt_Invalid,  }, 
   },
   {  /*Set  3*/
      //                              LTE_TAS_INIT_ANT_INDICATOR,  LTE_TAS_INIT_ANT_INDEX
      {   /*LTE_TAS_INIT_ANT_INDICATOR1 */  LTE_BandNone,          LTE_InitAnt_Invalid,  }, 
      {   /*LTE_TAS_INIT_ANT_INDICATOR2 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR3 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR4 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR5 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR6 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR7 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR8 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR9 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR10 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR11 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR12 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR13 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR14 */ LTE_BandNone,          LTE_InitAnt_Invalid,  }, 
   },
   {  /*Set  4*/
      //                              LTE_TAS_INIT_ANT_INDICATOR,  LTE_TAS_INIT_ANT_INDEX
      {   /*LTE_TAS_INIT_ANT_INDICATOR1 */  LTE_BandNone,          LTE_InitAnt_Invalid,  }, 
      {   /*LTE_TAS_INIT_ANT_INDICATOR2 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR3 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR4 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR5 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR6 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR7 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR8 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR9 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR10 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR11 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR12 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR13 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR14 */ LTE_BandNone,          LTE_InitAnt_Invalid,  }, 
   },
   {  /*Set  5*/
      //                              LTE_TAS_INIT_ANT_INDICATOR,  LTE_TAS_INIT_ANT_INDEX
      {   /*LTE_TAS_INIT_ANT_INDICATOR1 */  LTE_BandNone,          LTE_InitAnt_Invalid,  }, 
      {   /*LTE_TAS_INIT_ANT_INDICATOR2 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR3 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR4 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR5 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR6 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR7 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR8 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR9 */  LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR10 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR11 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR12 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR13 */ LTE_BandNone,          LTE_InitAnt_Invalid,  },
      {   /*LTE_TAS_INIT_ANT_INDICATOR14 */ LTE_BandNone,          LTE_InitAnt_Invalid,  }, 
   },
#endif
};


//*************************************************
// LTE Tx calibration data Custom data definition
//*************************************************
// Tx Ramp data
El1CustomDynamicInitLteTxRampData el1CustomLteTxRampData[EL1_CUSTOM_TOTAL_SET_NUMS] =
{
   {/*Set  0*/{NULL                     ,   //  Tx Ramp Data, Band_ind=0
               NULL                     ,   //  Tx Ramp Data, Band_ind=1
               NULL                     ,   //  Tx Ramp Data, Band_ind=2
               NULL                     ,   //  Tx Ramp Data, Band_ind=3
               NULL                     ,   //  Tx Ramp Data, Band_ind=4
               NULL                     ,   //  Tx Ramp Data, Band_ind=5
               NULL                     ,   //  Tx Ramp Data, Band_ind=6
               NULL                     ,   //  Tx Ramp Data, Band_ind=7
               NULL                     ,   //  Tx Ramp Data, Band_ind=8
               NULL                     ,   //  Tx Ramp Data, Band_ind=9
               NULL                     ,   //  Tx Ramp Data, Band_ind=10
               NULL                     ,   //  Tx Ramp Data, Band_ind=11
               NULL                     ,   //  Tx Ramp Data, Band_ind=12
               NULL                     }   //  Tx Ramp Data, Band_ind=13
   }, 
#if EL1_CUSTOM_DYNAMIC_INIT_ENABLE
   {/*Set  1*/{NULL                     ,   //  Tx Ramp Data, Band_ind=0
               NULL                     ,   //  Tx Ramp Data, Band_ind=1
               NULL                     ,   //  Tx Ramp Data, Band_ind=2
               NULL                     ,   //  Tx Ramp Data, Band_ind=3
               NULL                     ,   //  Tx Ramp Data, Band_ind=4
               NULL                     ,   //  Tx Ramp Data, Band_ind=5
               NULL                     ,   //  Tx Ramp Data, Band_ind=6
               NULL                     ,   //  Tx Ramp Data, Band_ind=7
               NULL                     ,   //  Tx Ramp Data, Band_ind=8
               NULL                     ,   //  Tx Ramp Data, Band_ind=9
               NULL                     ,   //  Tx Ramp Data, Band_ind=10
               NULL                     ,   //  Tx Ramp Data, Band_ind=11
               NULL                     ,   //  Tx Ramp Data, Band_ind=12
               NULL                     }   //  Tx Ramp Data, Band_ind=13
   },
#endif
   /*** APPEND new set of custom data HERE ***/
};

// Tx PA oct-level data
El1CustomDynamicInitLteTxPaOctLvlData el1CustomLteTxPaOctLvlData[EL1_CUSTOM_TOTAL_SET_NUMS] =
{
   {/*Set  0*/{NULL                         ,   //  Tx Pa Oct Data, Band_ind=0
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=1
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=2
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=3
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=4
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=5
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=6
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=7
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=8
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=9
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=10
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=11
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=12
               NULL                         }   //  Tx Pa Oct Data, Band_ind=13
   }, 
#if EL1_CUSTOM_DYNAMIC_INIT_ENABLE
   {/*Set  1*/{NULL                         ,   //  Tx Pa Oct Data, Band_ind=0
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=1
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=2
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=3
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=4
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=5
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=6
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=7
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=8
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=9
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=10
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=11
               NULL                         ,   //  Tx Pa Oct Data, Band_ind=12
               NULL                         }   //  Tx Pa Oct Data, Band_ind=13
   },  
#endif
   /*** APPEND new set of custom data HERE ***/
};


//*************************************************
// LTE Rx calibration data Custom data definition
//*************************************************
El1CustomDynamicInitLteRxPathLossData el1CustomLteRxPathLossData[EL1_CUSTOM_TOTAL_SET_NUMS] =
{
   {/*Set  0*/{NULL                       ,   //  Rx Path Loss Data, Band_ind=0
               NULL                       ,   //  Rx Path Loss Data, Band_ind=1
               NULL                       ,   //  Rx Path Loss Data, Band_ind=2
               NULL                       ,   //  Rx Path Loss Data, Band_ind=3
               NULL                       ,   //  Rx Path Loss Data, Band_ind=4
               NULL                       ,   //  Rx Path Loss Data, Band_ind=5
               NULL                       ,   //  Rx Path Loss Data, Band_ind=6
               NULL                       ,   //  Rx Path Loss Data, Band_ind=7
               NULL                       ,   //  Rx Path Loss Data, Band_ind=8
               NULL                       ,   //  Rx Path Loss Data, Band_ind=9
               NULL                       ,   //  Rx Path Loss Data, Band_ind=10
               NULL                       ,   //  Rx Path Loss Data, Band_ind=11
               NULL                       ,   //  Rx Path Loss Data, Band_ind=12
               NULL                       }   //  Rx Path Loss Data, Band_ind=13
   },     
#if EL1_CUSTOM_DYNAMIC_INIT_ENABLE
   {/*Set  1*/{NULL                       ,   //  Rx Path Loss Data, Band_ind=0
               NULL                       ,   //  Rx Path Loss Data, Band_ind=1
               NULL                       ,   //  Rx Path Loss Data, Band_ind=2
               NULL                       ,   //  Rx Path Loss Data, Band_ind=3
               NULL                       ,   //  Rx Path Loss Data, Band_ind=4
               NULL                       ,   //  Rx Path Loss Data, Band_ind=5
               NULL                       ,   //  Rx Path Loss Data, Band_ind=6
               NULL                       ,   //  Rx Path Loss Data, Band_ind=7
               NULL                       ,   //  Rx Path Loss Data, Band_ind=8
               NULL                       ,   //  Rx Path Loss Data, Band_ind=9
               NULL                       ,   //  Rx Path Loss Data, Band_ind=10
               NULL                       ,   //  Rx Path Loss Data, Band_ind=11
               NULL                       ,   //  Rx Path Loss Data, Band_ind=12
               NULL                       }   //  Rx Path Loss Data, Band_ind=13
   },
#endif    
   /*** APPEND new set of custom data HERE ***/
};


//*********************************************
// LTE Temperature DAC Custom data definition
//*********************************************
El1CustomDynamicInitLteTempDac el1CustomLteTempDacData[EL1_CUSTOM_TOTAL_SET_NUMS] =
{
   {&TempDacTable}     ,   //  Temperature DAC Data, Set 0
#if EL1_CUSTOM_DYNAMIC_INIT_ENABLE
   {&TempDacTable}     ,   //  Temperature DAC Data, Set 1
#endif
   /*** APPEND new set of custom data HERE ***/
   //{&TempDacTable_SetX},   //  Temperature DAC Data, Set X
};


//***************************************
// AuxADC voltage to level look-up table
//***************************************
kal_uint32 el1_custom_adc_volt_to_lvl[/*number of supported ADC levels*/][2] =
{
   /* Upper Bound */                  /* Lower Bound */
   {EL1_CUSTOM_ADC_LVL0,               0},

   /* Don't remove the above line: insert your new added level setting definition
    * bellow or delete the unused level bellow */

   {EL1_CUSTOM_ADC_LVL1,               EL1_CUSTOM_ADC_LVL0},
   {EL1_CUSTOM_ADC_LVL2,               EL1_CUSTOM_ADC_LVL1},

   /* Insert your new added level setting definition above or
    * delete the unused level above, and then change lower bound
    * EL1_CUSTOM_ADC_LVL6 below to the last upper bound in the above lines */

   {EL1_CUSTOM_ADC_MAX_INPUT_VOLTAGE,  EL1_CUSTOM_ADC_LVL2}
};


//***************************************
// Barcode digits array 
//***************************************
kal_char el1_custom_barcode_digits[EL1_CUSTOM_BARCODE_NUMS_IN_CALC] =
{
   '8', //Set 0, ex; for MURATA_SP7T
#if 0
/* under construction !*/
/* under construction !*/
#endif
};

#if 0
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
/* under construction !*/
#endif

/*******************************************************************************
** Global Functions
*******************************************************************************/
void EL1_CUSTOM_ReplaceAuxAdcCalibrate(kal_uint32 adcDigitalValue, kal_int32 *volt)
{
   /* Empty function */
}

/*******************************************************************************
** Global Functions
** for Feature phone/data-card GPIO Pin number access
** Not Recommend to modify
*******************************************************************************/
void EL1_CUSTOM_GPIO_NON_SMART_PHONE_PIN_ACCESS(kal_int16 *gpio_pin)
{
   /*PS: If link error happens, PLEASE check if codegen(DWS) generates the following variables */
#if (!EL1_CUSTOM_SMART_PHONE_ENABLE)
#if   (EL1_CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE == 0x1)
   // extern const char is from gpio_var.c (codegen)
	 extern const char GPIO_FDD_BAND_SUPPORT_DETECT_1ST_PIN;
   gpio_pin[0] = GPIO_FDD_BAND_SUPPORT_DETECT_1ST_PIN;
   gpio_pin[1] = -1;
   gpio_pin[2] = -1;
#elif (EL1_CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE == 0x2)
   extern const char GPIO_FDD_BAND_SUPPORT_DETECT_1ST_PIN;
   extern const char GPIO_FDD_BAND_SUPPORT_DETECT_2ND_PIN;
   gpio_pin[0] = GPIO_FDD_BAND_SUPPORT_DETECT_1ST_PIN;
   gpio_pin[1] = GPIO_FDD_BAND_SUPPORT_DETECT_2ND_PIN;
   gpio_pin[2] = -1;	
#elif (EL1_CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE == 0x3)
   extern const char GPIO_FDD_BAND_SUPPORT_DETECT_1ST_PIN;
   extern const char GPIO_FDD_BAND_SUPPORT_DETECT_2ND_PIN;
   extern const char GPIO_FDD_BAND_SUPPORT_DETECT_3RD_PIN;
   gpio_pin[0] = GPIO_FDD_BAND_SUPPORT_DETECT_1ST_PIN;
   gpio_pin[1] = GPIO_FDD_BAND_SUPPORT_DETECT_2ND_PIN;
   gpio_pin[2] = GPIO_FDD_BAND_SUPPORT_DETECT_3RD_PIN;	
#endif
#endif // (!EL1_CUSTOM_SMART_PHONE_ENABLE)		
}


/*******************************************************************************
** Global Functions
** for Feature phone/data-card ADC Pin number access
** Not Recommend to modify
*******************************************************************************/
void EL1_CUSTOM_ADC_PIN_ACCESS(kal_uint16 *adc_channel_num)
{
   /*PS: If link error happens, PLEASE check if codegen(DWS) generates the following variables */
#if (!EL1_CUSTOM_SMART_PHONE_ENABLE)
#if (EL1_CUSTOM_ADC_ENABLE)
   // extern const char is from adc_var.c (codegen)
   extern const char ADC_FDD_RF_PARAMS_DYNAMIC_CUSTOM_CH;
   adc_channel_num[0] = ADC_FDD_RF_PARAMS_DYNAMIC_CUSTOM_CH;
#else
   adc_channel_num[0] = 0xFFFF;
#endif
#endif
}

/*******************************************************************************
** DRDI for MIPI Custom Setting
*******************************************************************************/
//Collect all custom MIPI data/event table for DRDI usage
El1CustomDynamicInitLteMipiEventData el1CustomLteMipiEventDataTable[EL1_CUSTOM_TOTAL_SET_NUMS] =
{
   {  /*Set 0*/  // reuse the setting of Set1 temporarily
      LTE_DRDI_MIPI_DISABLE             ,     // LTE_DRDI_MIPI_DISABLE or LTE_DRDI_MIPI_ENABLE
      LTE_MIPI_RX_EVENT_TABLE_Set1      ,     // LTE_MIPI_RX_EVENT_TABLE,     
      LTE_MIPI_RX_DATA_TABLE_Set1       ,     // LTE_MIPI_RX_DATA_TABLE,      
      LTE_MIPI_TX_EVENT_TABLE_Set1      ,     // LTE_MIPI_TX_EVENT_TABLE,     
      LTE_MIPI_TX_DATA_TABLE_Set1       ,     // LTE_MIPI_TX_DATA_TABLE,      
      LTE_MIPI_TPC_EVENT_TABLE_Set1     ,     // LTE_MIPI_TPC_EVENT_TABLE,    
      LTE_MIPI_TPC_DATA_TABLE_Set1      ,     // LTE_MIPI_TPC_DATA_TABLE,     
      LTE_MIPI_PA_TPC_SECTION_DATA_Set1 ,     // LTE_MIPI_PA_TPC_SECTION_DATA,
      LTE_MIPI_INITIAL_CW_Set1          ,     // LTE_MIPI_INITIAL_CW,         
      LTE_MIPI_SLEEP_CW_Set1            ,     // LTE_MIPI_SLEEP_CW,           
      LTE_MIPI_ASM_ISOLATION_DATA_Set1  ,     // LTE_MIPI_ASM_ISOLATION_DATA, 
      LTE_MIPI_RX_EVENT_SIZE_TABLE_Set1 ,     // LTE_MIPI_RX_EVENT_SIZE_TABLE,
      LTE_MIPI_RX_DATA_SIZE_TABLE_Set1  ,     // LTE_MIPI_RX_DATA_SIZE_TABLE,
      LTE_MIPI_TX_EVENT_SIZE_TABLE_Set1 ,     // LTE_MIPI_TX_EVENT_SIZE_TABLE,
      LTE_MIPI_TX_DATA_SIZE_TABLE_Set1  ,     // LTE_MIPI_TX_DATA_SIZE_TABLE,
      LTE_MIPI_TPC_EVENT_SIZE_TABLE_Set1,     // LTE_MIPI_TPC_EVENT_SIZE_TABLE,
      LTE_MIPI_PA_TPC_SECTION_DATA_SIZE_Set1, // LTE_MIPI_PA_TPC_SECTION_DATA_SIZE,
   },
#if EL1_CUSTOM_DYNAMIC_INIT_ENABLE
   {  /*Set 1*/
      LTE_DRDI_MIPI_DISABLE             ,     // LTE_DRDI_MIPI_DISABLE or LTE_DRDI_MIPI_ENABLE
      LTE_MIPI_RX_EVENT_TABLE_Set1      ,     // LTE_MIPI_RX_EVENT_TABLE,     
      LTE_MIPI_RX_DATA_TABLE_Set1       ,     // LTE_MIPI_RX_DATA_TABLE,      
      LTE_MIPI_TX_EVENT_TABLE_Set1      ,     // LTE_MIPI_TX_EVENT_TABLE,     
      LTE_MIPI_TX_DATA_TABLE_Set1       ,     // LTE_MIPI_TX_DATA_TABLE,      
      LTE_MIPI_TPC_EVENT_TABLE_Set1     ,     // LTE_MIPI_TPC_EVENT_TABLE,    
      LTE_MIPI_TPC_DATA_TABLE_Set1      ,     // LTE_MIPI_TPC_DATA_TABLE,     
      LTE_MIPI_PA_TPC_SECTION_DATA_Set1 ,     // LTE_MIPI_PA_TPC_SECTION_DATA,
      LTE_MIPI_INITIAL_CW_Set1          ,     // LTE_MIPI_INITIAL_CW,         
      LTE_MIPI_SLEEP_CW_Set1            ,     // LTE_MIPI_SLEEP_CW,           
      LTE_MIPI_ASM_ISOLATION_DATA_Set1  ,     // LTE_MIPI_ASM_ISOLATION_DATA, 
      LTE_MIPI_RX_EVENT_SIZE_TABLE_Set1 ,     // LTE_MIPI_RX_EVENT_SIZE_TABLE,
      LTE_MIPI_RX_DATA_SIZE_TABLE_Set1  ,     // LTE_MIPI_RX_DATA_SIZE_TABLE,
      LTE_MIPI_TX_EVENT_SIZE_TABLE_Set1 ,     // LTE_MIPI_TX_EVENT_SIZE_TABLE,
      LTE_MIPI_TX_DATA_SIZE_TABLE_Set1  ,     // LTE_MIPI_TX_DATA_SIZE_TABLE,
      LTE_MIPI_TPC_EVENT_SIZE_TABLE_Set1,     // LTE_MIPI_TPC_EVENT_SIZE_TABLE,
      LTE_MIPI_PA_TPC_SECTION_DATA_SIZE_Set1, // LTE_MIPI_PA_TPC_SECTION_DATA_SIZE,
   },
#endif
   /*** APPEND new set of custom data HERE ***/   
};

//*********************************************
// LTE MIPI Custom Setting
//*********************************************
/* Pointer array for customer to input 4G MIPI data */
El1CustomDynamicInitLteMipiEventData   *el1_rf_mipi_evtdata_array_ptr[EL1_CUSTOM_TOTAL_SET_NUMS];


/*******************************************************************************
** DRDI for MIPI BYPASS Custom Setting
*******************************************************************************/
//Collect all custom MIPI BYPASS data/event table for DRDI usage
El1CustomDynamicInitLteMipiBypassEventData el1CustomLteMipiBypassEventDataTable[EL1_CUSTOM_TOTAL_SET_NUMS] =
{
   {  /*Set 0*/  // reuse the setting of Set1 temporarily
      LTE_DRDI_MIPI_BYPASS_DISABLE                  ,  // LTE_DRDI_MIPI_BYPASS_DISABLE or LTE_DRDI_MIPI_BYPASS_ENABLE    
      LTE_MIPI_BYPASS_TX_EVENT_TABLE_Set1           ,  // LTE_MIPI_BYPASS_TX_EVENT_TABLE,     
      LTE_MIPI_BYPASS_TX_DATA_TABLE_Set1            ,  // LTE_MIPI_BYPASS_TX_DATA_TABLE,      
      LTE_MIPI_BYPASS_TPC_EVENT_TABLE_Set1          ,  // LTE_MIPI_BYPASS_TPC_EVENT_TABLE,    
      LTE_MIPI_BYPASS_TPC_DATA_TABLE_Set1           ,  // LTE_MIPI_BYPASS_TPC_DATA_TABLE,     
      LTE_MIPI_BYPASS_PA_TPC_SECTION_DATA_Set1      ,  // LTE_MIPI_BYPASS_PA_TPC_SECTION_DATA,
      LTE_MIPI_BYPASS_TX_EVENT_SIZE_TABLE_Set1      ,  // LTE_MIPI_BYPASS_TX_EVENT_SIZE_TABLE,
      LTE_MIPI_BYPASS_TX_DATA_SIZE_TABLE_Set1       ,  // LTE_MIPI_BYPASS_TX_DATA_SIZE_TABLE,
      LTE_MIPI_BYPASS_TPC_EVENT_SIZE_TABLE_Set1     ,  // LTE_MIPI_BYPASS_TPC_EVENT_SIZE_TABLE,
      LTE_MIPI_BYPASS_PA_TPC_SECTION_DATA_SIZE_Set1 ,  // LTE_MIPI_BYPASS_PA_TPC_SECTION_DATA_SIZE,
   },
#if EL1_CUSTOM_DYNAMIC_INIT_ENABLE
   {  /*Set 1*/
      LTE_DRDI_MIPI_BYPASS_DISABLE                  ,  // LTE_DRDI_MIPI_BYPASS_DISABLE or LTE_DRDI_MIPI_BYPASS_ENABLE     
      LTE_MIPI_BYPASS_TX_EVENT_TABLE_Set1           ,  // LTE_MIPI_BYPASS_TX_EVENT_TABLE,     
      LTE_MIPI_BYPASS_TX_DATA_TABLE_Set1            ,  // LTE_MIPI_BYPASS_TX_DATA_TABLE,      
      LTE_MIPI_BYPASS_TPC_EVENT_TABLE_Set1          ,  // LTE_MIPI_BYPASS_TPC_EVENT_TABLE,    
      LTE_MIPI_BYPASS_TPC_DATA_TABLE_Set1           ,  // LTE_MIPI_BYPASS_TPC_DATA_TABLE,     
      LTE_MIPI_BYPASS_PA_TPC_SECTION_DATA_Set1      ,  // LTE_MIPI_BYPASS_PA_TPC_SECTION_DATA,
      LTE_MIPI_BYPASS_TX_EVENT_SIZE_TABLE_Set1      ,  // LTE_MIPI_BYPASS_TX_EVENT_SIZE_TABLE,
      LTE_MIPI_BYPASS_TX_DATA_SIZE_TABLE_Set1       ,  // LTE_MIPI_BYPASS_TX_DATA_SIZE_TABLE,
      LTE_MIPI_BYPASS_TPC_EVENT_SIZE_TABLE_Set1     ,  // LTE_MIPI_BYPASS_TPC_EVENT_SIZE_TABLE,
      LTE_MIPI_BYPASS_PA_TPC_SECTION_DATA_SIZE_Set1 ,  // LTE_MIPI_BYPASS_PA_TPC_SECTION_DATA_SIZE,
   },
#endif
   /*** APPEND new set of custom data HERE ***/   
};

//Collect all custom MIPI BYPASS Band setting and corresponding Power/Coupler compensation
El1CustomDynamicInitLteMipiBypassInfo el1CustomLteMipiBypassInfo[EL1_CUSTOM_TOTAL_SET_NUMS][EL1_CUSTOM_MIPI_BYPASS_MAX_SUPPORT_BAND_NUM] =
{
 //{/*Set XX*//*band_idx XX*/{Band        ,  PowerCompensation, CouplerCompensation
 //                                          (Unit: S(6,8) dB)  (Unit: S(6,8) dB)
   {/*Set  0*//*band_idx  0*/{LTE_BandNone,                 0,                 0},
              /*band_idx  1*/{LTE_BandNone,                 0,                 0},
              /*band_idx  2*/{LTE_BandNone,                 0,                 0},
              /*band_idx  3*/{LTE_BandNone,                 0,                 0},
              /*band_idx  4*/{LTE_BandNone,                 0,                 0},
   },
#if EL1_CUSTOM_DYNAMIC_INIT_ENABLE
   {/*Set  1*//*band_idx  0*/{LTE_Band38  ,                 0,                 0},
              /*band_idx  1*/{LTE_Band40  ,                 0,                 0},
              /*band_idx  2*/{LTE_BandNone,                 0,                 0},
              /*band_idx  3*/{LTE_BandNone,                 0,                 0},
              /*band_idx  4*/{LTE_BandNone,                 0,                 0},
   },
#endif
   /*** APPEND new set of custom data HERE ***/ 
};


//*********************************************
// LTE MIPI BYPASS Custom Setting
//*********************************************
/* Pointer array for customer to input 4G MIPI BYPASS data */
El1CustomDynamicInitLteMipiBypassEventData   *el1_rf_mipi_bypass_evtdata_array_ptr[EL1_CUSTOM_TOTAL_SET_NUMS];
El1CustomDynamicInitLteMipiBypassInfo        *el1_rf_mipi_bypass_info_ptr[EL1_CUSTOM_TOTAL_SET_NUMS];


#if IS_4G_TX_POWER_OFFSET_SUPPORT

ERf_TPO_SETTING el1CustomLteTpoInputData[EL1_CUSTOM_TOTAL_SET_NUMS] =
{
	{{/*Set  0*/
	    0,         //TPO_4G_ENABLE, Enable:1, Disable:0
        0,         //TPO_4G_META_ENABLE, Enable:1, Disable:0
	  0x0,         //TPO_4G_TABLE_ON_MASK
	  {
        4,		   //TPO_4G_BIT_NUM_FOR_TABLE_0
        5,         //TPO_4G_BIT_NUM_FOR_TABLE_1
	  }}
	},
#if EL1_CUSTOM_DYNAMIC_INIT_ENABLE
    {{/*Set  1*/
	    0,         //TPO_4G_ENABLE, Enable:1, Disable:0
        0,         //TPO_4G_META_ENABLE, Enable:1, Disable:0
	  0x0,         //TPO_4G_TABLE_ON_MASK
	  {
        4,		   //TPO_4G_BIT_NUM_FOR_TABLE_0
        5,         //TPO_4G_BIT_NUM_FOR_TABLE_1
	  }}
	},
#endif
};


ERf_TXPOWEROFFSETDATA el1CustomLteTxPowerOffsetData[EL1_CUSTOM_TOTAL_SET_NUMS][EL1_CUSTOM_MAX_RF_SUPPORT_BAND_NUM] =
{
  ///*Set XX*//*band_idx XX*/ {         Band,{{{               TX POWER OFFSET TABLE 0                         }},{{                  TX POWER OFFSET TABLE 1                      }},}},                                                      
   {/*Set  0*//*band_idx  0*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx  1*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx  2*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx  3*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx  4*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx  5*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx  6*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx  7*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx  8*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx  9*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx 10*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx 11*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx 12*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx 13*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
   },  
#if EL1_CUSTOM_DYNAMIC_INIT_ENABLE
   {/*Set  1*//*band_idx  0*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx  1*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx  2*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx  3*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx  4*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx  5*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx  6*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx  7*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx  8*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx  9*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx 10*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx 11*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx 12*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
              /*band_idx 13*/ { LTE_BandNone,{{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},{{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }},}},
   }, 	
#endif
};

//*********************************************
// LTE TX POWER OFFSET Custom Setting
//*********************************************
/* Pointer array to input 4G TX POWER OFFSET data */
ERf_TPO_SETTING              *el1_rf_tpo_input_data_array_ptr[EL1_CUSTOM_TOTAL_SET_NUMS];
ERf_TXPOWEROFFSETDATA        *el1_rf_tx_power_offset_data_array_ptr[EL1_CUSTOM_TOTAL_SET_NUMS];

#endif /* #if IS_4G_TX_POWER_OFFSET_SUPPORT */
#endif /* #ifdef __MTK_TARGET__ */

/* END OF FILE */
