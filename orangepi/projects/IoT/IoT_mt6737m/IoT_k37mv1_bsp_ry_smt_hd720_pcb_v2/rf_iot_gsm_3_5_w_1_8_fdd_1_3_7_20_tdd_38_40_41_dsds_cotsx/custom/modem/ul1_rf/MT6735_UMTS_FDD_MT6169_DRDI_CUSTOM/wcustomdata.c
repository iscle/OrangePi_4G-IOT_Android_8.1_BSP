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
 *	wcustomdata.c
 *
 * Project:
 * --------
 *  Dual Mode 3G project
 *
 * Description:
 * ------------
 *  Dynamic RF parameters data input file
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
#include "wcustomdata.h"
#include "ul1cal.h"


#if (IS_3G_DRDI_SUPPORT)
/***************************************************************************
 * Global Data
 ***************************************************************************/
/* Look up table from action id to action function
 * See the enum #Ul1CustomActionId for members of
 * the table */
const Ul1CustomFunction ul1CustomActionTable[UL1CUSTOM_MAX_PROC_ACTIONS] =
{
   NULL,

#if UL1CUSTOM_GPIO_ENABLE
   UL1CUSTOM_DynamicInitByGPIO,
#else
   NULL, /* Null action */
#endif

#if UL1CUSTOM_ADC_ENABLE
   UL1CUSTOM_DynamicInitByADC,
#else
   NULL, /* Null action */
#endif

#if UL1CUSTOM_NVRAM_BARCODE_ENABLE
   UL1CUSTOM_DynamicInitByNvramBarcode
#else
   NULL /* Null action */
#endif
};

/***************************************************************************
 * Data array of pointers pointed to data array for custom data
 ***************************************************************************/
//********************************************
// UMTS Band Indicator Custom data definition
//********************************************
/* Pointer array for customer to input 3G band support parameters
 * with pointers for each of the configuration set to be detected */
#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
UMTSBand *ul1d_rf_bandind_array_ptr[UL1CUSTOM_TOTAL_REAL_SET_NUMS];
#else
UMTSBand *ul1d_rf_bandind_array_ptr[UL1CUSTOM_TOTAL_SET_NUMS];
#endif

//**************************************
// UMTS BPI data Custom data definition
//**************************************
/* Pointer array for customer to input 3G BPI data parameters
 * with pointers for each of the configuration set to be detected */
#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
Ul1CustomDynamicInitUmtsBpiData *ul1d_rf_bpidata_array_ptr[UL1CUSTOM_TOTAL_REAL_SET_NUMS];
#else
Ul1CustomDynamicInitUmtsBpiData *ul1d_rf_bpidata_array_ptr[UL1CUSTOM_TOTAL_SET_NUMS];
#endif

//***************************************************
// UMTS LNA port/Tx path data Custom data definition
//***************************************************
/* Pointer array for customer to input 3G LNA port and TX path parameters by band
 * with pointers for each of the configuration set to be detected */
#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
Ul1CustomDynamicInitLnaPortTxPath *ul1d_rf_lna_txpath_array_ptr[UL1CUSTOM_TOTAL_REAL_SET_NUMS];
#else
Ul1CustomDynamicInitLnaPortTxPath *ul1d_rf_lna_txpath_array_ptr[UL1CUSTOM_TOTAL_SET_NUMS];
#endif

//*************************************************
// UMTS Tx calibration data Custom data definition
//*************************************************
// Tx Ramp data
/* Pointer array for customer to input 3G TX Ramp calibration data
 * with pointers for each of the configuration set to be detected */
#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
Ul1CustomDynamicInitUmtsTxRampData *ul1d_rf_txramp_array_ptr[UL1CUSTOM_TOTAL_REAL_SET_NUMS];
#else
Ul1CustomDynamicInitUmtsTxRampData *ul1d_rf_txramp_array_ptr[UL1CUSTOM_TOTAL_SET_NUMS];
#endif

// Tx PA oct-level data
/* Pointer array for customer to input 3G TX oct-level calibration data
 * with pointers for each of the configuration set to be detected */
#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
Ul1CustomDynamicInitUmtsTxPaOctLvlData *ul1d_rf_txoctlvl_array_ptr[UL1CUSTOM_TOTAL_REAL_SET_NUMS];
#else
Ul1CustomDynamicInitUmtsTxPaOctLvlData *ul1d_rf_txoctlvl_array_ptr[UL1CUSTOM_TOTAL_SET_NUMS];
#endif

//*************************************************
// UMTS Rx calibration data Custom data definition
//*************************************************
/* Pointer array for customer to input 3G RX path loss calibration data
 * with pointers for each of the configuration set to be detected */
#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
Ul1CustomDynamicInitUmtsRxPathLossData *ul1d_rf_rxpathloss_array_ptr[UL1CUSTOM_TOTAL_REAL_SET_NUMS];
#else
Ul1CustomDynamicInitUmtsRxPathLossData *ul1d_rf_rxpathloss_array_ptr[UL1CUSTOM_TOTAL_SET_NUMS];
#endif

//*********************************************
// UMTS Temperature DAC Custom data definition
//*********************************************
/* Pointer array for customer to input 3G Temp. DAC calibration data
 * with pointers for each of the configuration set to be detected */
#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
Ul1CustomDynamicInitUmtsTempDac *ul1d_rf_utempdac_array_ptr[UL1CUSTOM_TOTAL_REAL_SET_NUMS];
#else
Ul1CustomDynamicInitUmtsTempDac *ul1d_rf_utempdac_array_ptr[UL1CUSTOM_TOTAL_SET_NUMS];
#endif

//******************************************************
// UMTS Tx PRACH TM Compensation Custom data definition
//******************************************************
/* Pointer array for customer to input 3G Tx PRACH TM Compensation calibration data
 * with pointers for each of the configuration set to be detected */
#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
Ul1CustomDynamicInitUmtsTxPrachTmCompData *ul1d_rf_txprachtmcomp_array_ptr[UL1CUSTOM_TOTAL_REAL_SET_NUMS];
#else
Ul1CustomDynamicInitUmtsTxPrachTmCompData *ul1d_rf_txprachtmcomp_array_ptr[UL1CUSTOM_TOTAL_SET_NUMS];
#endif

//******************************************************
// UMTS AFC DAC Custom data definition
//******************************************************
/* Pointer array for customer to input 3G AFC DAC calibration data
 * with pointers for each of the configuration set to be detected */
#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
Ul1CustomDynamicInitUmtsAfcDac *ul1d_rf_afcdac_array_ptr[UL1CUSTOM_TOTAL_REAL_SET_NUMS];
#else
Ul1CustomDynamicInitUmtsAfcDac *ul1d_rf_afcdac_array_ptr[UL1CUSTOM_TOTAL_SET_NUMS];
#endif

//******************************************************
// UMTS AFC CAP Custom data definition
//******************************************************
/* Pointer array for customer to input 3G AFC CAP calibration data
 * with pointers for each of the configuration set to be detected */
#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
Ul1CustomDynamicInitUmtsAfcCap *ul1d_rf_afccap_array_ptr[UL1CUSTOM_TOTAL_REAL_SET_NUMS];
#else
Ul1CustomDynamicInitUmtsAfcCap *ul1d_rf_afccap_array_ptr[UL1CUSTOM_TOTAL_SET_NUMS];
#endif


#if (IS_3G_MIPI_SUPPORT)
//******************************************************
// UMTS MIPI Custom data definition
//******************************************************
/* Pointer array for customer to input 3G MIPI data
 * with pointers for each of the configuration set to be detected */
   #if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
Ul1CustomDynamicInitMipiData *ul1d_mipiData_array_ptr[UL1CUSTOM_TOTAL_REAL_SET_NUMS];
   #else
Ul1CustomDynamicInitMipiData *ul1d_mipiData_array_ptr[UL1CUSTOM_TOTAL_SET_NUMS];
   #endif

#endif

/***************************************************************************
 * Data definition for custom to input data
 ***************************************************************************/
//********************************************
// UMTS Band Indicator Custom data definition
//********************************************

#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
UMTSBand ul1CustomBand[UL1CUSTOM_TOTAL_REAL_SET_NUMS][UL1CUSTOM_MAX_RF_SUPPORT_BAND_NUM] =
{
   /* The first set, Set 0 */
   {
      UMTSBand1,     // Sub-index 0 in a set
      UMTSBand2,     // Sub-index 1 in a set
      UMTSBand5,     // Sub-index 2 in a set
      UMTSBand8,     // Sub-index 3 in a set
      UMTSBandNone   // Sub-index 4 in a set      
   },
   /* The second set, Set 1 */
   {
      UMTSBand1,     // Sub-index 0 in a set
      UMTSBand2,     // Sub-index 1 in a set
      UMTSBand4,     // Sub-index 2 in a set
      UMTSBand5,     // Sub-index 3 in a set
      UMTSBand8      // Sub-index 4 in a set      
   }
   /*   End of second Set */
};
#else
UMTSBand ul1CustomBand[UL1CUSTOM_TOTAL_SET_NUMS][UL1CUSTOM_MAX_RF_SUPPORT_BAND_NUM] =
{
   /* The first set, Set 0 */
   {
      UMTSBand1,     // Sub-index 0 in a set
      UMTSBand2,     // Sub-index 1 in a set
      UMTSBand4,     // Sub-index 2 in a set
      UMTSBand5,     // Sub-index 3 in a set
      UMTSBand8      // Sub-index 4 in a set      
   },
   /* The Second set, Set 1 */
   {
      UMTSBand1,     // Sub-index 0 in a set
      UMTSBand2,     // Sub-index 1 in a set
      UMTSBand4,     // Sub-index 2 in a set
      UMTSBand5,     // Sub-index 3 in a set
      UMTSBand8      // Sub-index 4 in a set     
   },
   /* The Third set, Set 2 */
   {
      UMTSBand1,     // Sub-index 0 in a set
      UMTSBand5,     // Sub-index 1 in a set
      UMTSBandNone,  // Sub-index 2 in a set
      UMTSBandNone,  // Sub-index 3 in a set
      UMTSBandNone   // Sub-index 4 in a set      
   },
   /* The Fourth set, Set 3 */
   {
      UMTSBand1,     // Sub-index 0 in a set
      UMTSBand2,     // Sub-index 1 in a set
      UMTSBand5,     // Sub-index 2 in a set
      UMTSBand8,     // Sub-index 3 in a set
      UMTSBandNone   // Sub-index 4 in a set      
   },
   /* The Fifth set, Set 4 */
   {
      UMTSBand1,     // Sub-index 0 in a set
      UMTSBand2,     // Sub-index 1 in a set
      UMTSBand5,     // Sub-index 2 in a set
      UMTSBand8,     // Sub-index 3 in a set
      UMTSBandNone   // Sub-index 4 in a set      
   },
   /* The Sixth set, Set 5 */
   {
      UMTSBand1,     // Sub-index 0 in a set
      UMTSBand2,     // Sub-index 1 in a set
      UMTSBand5,     // Sub-index 2 in a set
      UMTSBand8,     // Sub-index 3 in a set
      UMTSBandNone   // Sub-index 4 in a set      
   }
   /*   End of Sixth Set */
};
#endif

//**************************************
// UMTS BPI data Custom data definition
//**************************************

#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
Ul1CustomDynamicInitUmtsBpiData ul1CustomUmtsBpiData[UL1CUSTOM_TOTAL_REAL_SET_NUMS][UL1CUSTOM_MAX_RF_SUPPORT_BAND_NUM] =
{
   /* Example: first Set, Set 0 */
   {
      // Sub-index 0 //
      {
         UMTSBand1,

          // BPI logics
          //PR1          PR2            PR2B      PR3      PR3A           PT1          PT2          PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000005,  0x00000005,  0x00000005,     0,       0,   0x00000005,  0x00000001,   0x00000001,      0,         0,         0x0,        0x0,     0x0,              0,           0        },

      },

      // Sub-index 1 //
      {
         UMTSBand2,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000001,  0x00000001,  0x00000001,      0,       0,     0x00000001,  0x00000001,  0x00000001,      0,       0,      0x0,       0x0,             0x0,           0,          0       },
      },

      // Sub-index 2 //
      {
         UMTSBand5,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000000,  0x00000000,  0x00000000,      0,       0,     0x00000000,  0x00000000,  0x00000000,      0,       0,      0x0,       0x0,             0x0,           0,          0       },
      },

      // Sub-index 3 //
      {
         UMTSBand8,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000000,  0x00000000,  0x00000000,      0,       0,     0x00000000,  0x00000000,  0x00000000,      0,       0, 0x00004000, 0x00004000,   0x00004000,         0,          0       },
      },
      // Sub-index 4 //
      {
         UMTSBandNone,
      }         
   },
   /* The second set, Set 1 */
   {
      // Sub-index 0 //
      {
         UMTSBand1,

          // BPI logics
          //PR1          PR2            PR2B      PR3      PR3A           PT1          PT2          PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000005,  0x00000005,  0x00000005,     0,       0,   0x00000005,  0x00000001,   0x00000001,      0,         0,         0x0,        0x0,     0x0,              0,           0        },

      },

      // Sub-index 1 //
      {
         UMTSBand2,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000000,  0x00000000,  0x00000000,      0,       0,     0x00000000,  0x00000000,  0x00000000,      0,       0,      0x0,       0x0,             0x0,           0,          0       },
      },

      // Sub-index 2 //
      {
         UMTSBand4,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000000,  0x00000000,  0x00000000,      0,       0,     0x00000000,  0x00000000,  0x00000000,      0,       0,      0x0,       0x0,             0x0,           0,          0       },
      },

      // Sub-index 3 //
      {
         UMTSBand5,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000000,  0x00000000,  0x00000000,      0,       0,     0x00000000,  0x00000000,  0x00000000,      0,       0, 0x00000000, 0x00000000,   0x00000000,         0,          0       },
      },
      // Sub-index 4 //
      {
         UMTSBand8,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000000,  0x00000000,  0x00000000,      0,       0,     0x00000000,  0x00000000,  0x00000000,      0,       0, 0x00000000, 0x00000000,   0x00000000,         0,          0       },
      }        
   }
/*Example Set1 */
};
#else
Ul1CustomDynamicInitUmtsBpiData ul1CustomUmtsBpiData[UL1CUSTOM_TOTAL_SET_NUMS][UL1CUSTOM_MAX_RF_SUPPORT_BAND_NUM] =
{
   /* The first set, Set 0 */
   {
      // Sub-index 0 //
      {
         UMTSBand1,

          // BPI logics
          //PR1          PR2            PR2B      PR3      PR3A           PT1          PT2          PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000005,  0x00000005,  0x00000005,     0,       0,   0x00000005,  0x00000001,   0x00000001,      0,         0,         0x0,        0x0,     0x0,              0,           0        },

      },

      // Sub-index 1 //
      {
         UMTSBand2,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000000,  0x00000000,  0x00000000,      0,       0,     0x00000000,  0x00000000,  0x00000000,      0,       0,      0x0,       0x0,             0x0,           0,          0       },
      },

      // Sub-index 2 //
      {
         UMTSBand4,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000000,  0x00000000,  0x00000000,      0,       0,     0x00000000,  0x00000000,  0x00000000,      0,       0,      0x0,       0x0,             0x0,           0,          0       },
      },

      // Sub-index 3 //
      {
         UMTSBand5,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000000,  0x00000000,  0x00000000,      0,       0,     0x00000000,  0x00000000,  0x00000000,      0,       0, 0x00000000, 0x00000000,   0x00000000,         0,          0       },
      },
      // Sub-index 4 //
      {
         UMTSBand8,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000000,  0x00000000,  0x00000000,      0,       0,     0x00000000,  0x00000000,  0x00000000,      0,       0, 0x00000000, 0x00000000,   0x00000000,         0,          0       },
      }        
   },
   /* Example: Second Set, Set 1 */
   {
      // Sub-index 0 //
      {
         UMTSBand1,

          // BPI logics
          //PR1          PR2            PR2B      PR3      PR3A           PT1          PT2          PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000005,  0x00000005,  0x00000005,     0,       0,   0x00000005,  0x00000001,   0x00000001,      0,         0,         0x0,        0x0,     0x0,              0,           0        },

      },

      // Sub-index 1 //
      {
         UMTSBand2,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000000,  0x00000000,  0x00000000,      0,       0,     0x00000000,  0x00000000,  0x00000000,      0,       0,      0x0,       0x0,             0x0,           0,          0       },
      },

      // Sub-index 2 //
      {
         UMTSBand4,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000000,  0x00000000,  0x00000000,      0,       0,     0x00000000,  0x00000000,  0x00000000,      0,       0,      0x0,       0x0,             0x0,           0,          0       },
      },

      // Sub-index 3 //
      {
         UMTSBand5,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000000,  0x00000000,  0x00000000,      0,       0,     0x00000000,  0x00000000,  0x00000000,      0,       0, 0x00000000, 0x00000000,   0x00000000,         0,          0       },
      },
      // Sub-index 4 //
      {
         UMTSBand8,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000000,  0x00000000,  0x00000000,      0,       0,     0x00000000,  0x00000000,  0x00000000,      0,       0, 0x00000000, 0x00000000,   0x00000000,         0,          0       },
      }         
   },
   /* Example: Third Set, Set 2 */
   {
      // Sub-index 1 //
      {
         UMTSBand1,

          // BPI logics
          //PR1          PR2            PR2B      PR3      PR3A           PT1          PT2          PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x02880000,  0x02880000,  0x02880000,      0,       0,     0x00880000,  0x00880200,  0x00880200,      0,       0,      0x0,     0x0,     0x0,           0,          0        },

      },

      // Sub-index 1 //
      {
         UMTSBand5,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00480000,  0x00480000,  0x00480000,      0,       0,     0x00480000,  0x00480040,  0x00480040,      0,       0,      0x00030000,     0x00030000,     0x00030000,           0,          0       },
      },

      // Sub-index 2 //
      {
         UMTSBandNone,
      },

      // Sub-index 3 //
      {
         UMTSBandNone,
      },
      // Sub-index 4 //
      {
         UMTSBandNone,
      }         
   },
   /* Example: Fourth Set, Set 3 */
   {
      // Sub-index 0 //
      {
         UMTSBand1,

          // BPI logics
          //PR1          PR2            PR2B      PR3      PR3A           PT1          PT2          PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000005,  0x00000005,  0x00000005,     0,       0,   0x00000005,  0x00000001,   0x00000001,      0,         0,         0x0,        0x0,     0x0,              0,           0        },

      },

      // Sub-index 1 //
      {
         UMTSBand2,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000001,  0x00000001,  0x00000001,      0,       0,     0x00000001,  0x00000001,  0x00000001,      0,       0,      0x0,       0x0,             0x0,           0,          0       },
      },

      // Sub-index 2 //
      {
         UMTSBand5,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000000,  0x00000000,  0x00000000,      0,       0,     0x00000000,  0x00000000,  0x00000000,      0,       0,      0x0,       0x0,             0x0,           0,          0       },
      },

      // Sub-index 3 //
      {
         UMTSBand8,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000000,  0x00000000,  0x00000000,      0,       0,     0x00000000,  0x00000000,  0x00000000,      0,       0, 0x00004000, 0x00004000,   0x00004000,         0,          0       },
      },
      // Sub-index 4 //
      {
         UMTSBandNone,
      }         
   },
   /* Example: Fifth Set, Set 4 */
   {
      // Sub-index 0 //
      {
         UMTSBand1,

          // BPI logics
          //PR1          PR2            PR2B      PR3      PR3A           PT1          PT2          PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000005,  0x00000005,  0x00000005,     0,       0,   0x00000005,  0x00000001,   0x00000001,      0,         0,         0x0,        0x0,     0x0,              0,           0        },

      },

      // Sub-index 1 //
      {
         UMTSBand2,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000001,  0x00000001,  0x00000001,      0,       0,     0x00000001,  0x00000001,  0x00000001,      0,       0,      0x0,       0x0,             0x0,           0,          0       },
      },

      // Sub-index 2 //
      {
         UMTSBand5,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000000,  0x00000000,  0x00000000,      0,       0,     0x00000000,  0x00000000,  0x00000000,      0,       0,      0x0,       0x0,             0x0,           0,          0       },
      },

      // Sub-index 3 //
      {
         UMTSBand8,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000000,  0x00000000,  0x00000000,      0,       0,     0x00000000,  0x00000000,  0x00000000,      0,       0, 0x00004000, 0x00004000,   0x00004000,         0,          0       },
      },
      // Sub-index 4 //
      {
         UMTSBandNone,
      }         
   },
   /* Example: Sixth Set, Set 5 */
   {
      // Sub-index 0 //
      {
         UMTSBand1,

          // BPI logics
          //PR1          PR2            PR2B      PR3      PR3A           PT1          PT2          PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000005,  0x00000005,  0x00000005,     0,       0,   0x00000005,  0x00000001,   0x00000001,      0,         0,         0x0,        0x0,     0x0,              0,           0        },

      },

      // Sub-index 1 //
      {
         UMTSBand2,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000001,  0x00000001,  0x00000001,      0,       0,     0x00000001,  0x00000001,  0x00000001,      0,       0,      0x0,       0x0,             0x0,           0,          0       },
      },

      // Sub-index 2 //
      {
         UMTSBand5,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000000,  0x00000000,  0x00000000,      0,       0,     0x00000000,  0x00000000,  0x00000000,      0,       0,      0x0,       0x0,             0x0,           0,          0       },
      },

      // Sub-index 3 //
      {
         UMTSBand8,

          // BPI logics
          //PR1               PR2         PR2B      PR3      PR3A           PT1          PT2       PT2B      PT3      PT3A     RXD_PR1     RXD_PR2     RXD_PR2B     RXD_PR3     RXD_PR3A  
         {0x00000000,  0x00000000,  0x00000000,      0,       0,     0x00000000,  0x00000000,  0x00000000,      0,       0, 0x00004000, 0x00004000,   0x00004000,         0,          0       },
      },
      // Sub-index 4 //
      {
         UMTSBandNone,
      }         
   }
/*Example Set5 */
};
#endif

//***************************************************
// UMTS LNA port/Tx path data Custom data definition
//***************************************************

#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
Ul1CustomDynamicInitLnaPortTxPath ul1CustomUmtsLnaPortTxPath[UL1CUSTOM_TOTAL_REAL_SET_NUMS][UL1CUSTOM_MAX_RF_SUPPORT_BAND_NUM] =
{
   /* The first set, Set 0 */
   {
      // Sub-index 0 
      {
         UMTSBand1,
         LNA_HB_3,      
         TX_MB_2,       
         LNA_RXD_HB_1     
      },
      // Sub-index 1 
      {
         UMTSBand2,
         LNA_MB_1,
         TX_MB_2,
         NON_USED_BAND
      },
      // Sub-index 2 
      {
         UMTSBand5,
         LNA_LB_2,
         TX_LB_2,
         LNA_RXD_LB_1
      },
      // Sub-index 3 
      {
         UMTSBand8,
         LNA_LB_1,
         TX_LB_2,
         LNA_RXD_LB_3
      },
      // Sub-index 4 
      {
         UMTSBandNone
      }     
   },
   /* The second set, Set 1 */
   {
      // Sub-index 0 
      {
         UMTSBand1,
         LNA_HB_3,      
         TX_MB_2,       
         LNA_RXD_HB_1      
      },
      // Sub-index 1 
      {
         UMTSBand2,
         LNA_MB_1,
         TX_MB_2,
         NON_USED_BAND
      },
      // Sub-index 2 
      {
         UMTSBand4,
         LNA_HB_3,
         TX_MB_2,
         LNA_RXD_HB_1
      },
      // Sub-index 3 
      {
         UMTSBand5,
         LNA_LB_2,
         TX_LB_2,
         LNA_RXD_LB_1
      },
      // Sub-index 4 
      {
         UMTSBand8,
         LNA_LB_1,
         TX_LB_2,
         NON_USED_BAND
      }     
   }
/*End of The Second set, Set 1 */
};
#else
Ul1CustomDynamicInitLnaPortTxPath ul1CustomUmtsLnaPortTxPath[UL1CUSTOM_TOTAL_SET_NUMS][UL1CUSTOM_MAX_RF_SUPPORT_BAND_NUM] =
{
   /* The first set, Set 0 */
   {
      // Sub-index 0 
      {
         UMTSBand1,
         LNA_HB_3,      
         TX_MB_2,       
         LNA_RXD_HB_1      
      },
      // Sub-index 1 
      {
         UMTSBand2,
         LNA_MB_1,
         TX_MB_2,
         NON_USED_BAND
      },
      // Sub-index 2 
      {
         UMTSBand4,
         LNA_HB_3,
         TX_MB_2,
         LNA_RXD_HB_1
      },
      // Sub-index 3 
      {
         UMTSBand5,
         LNA_LB_2,
         TX_LB_2,
         LNA_RXD_LB_1
      },
      // Sub-index 4 
      {
         UMTSBand8,
         LNA_LB_1,
         TX_LB_2,
         NON_USED_BAND
      }     
   },   
   /* The Second set, Set 1 */
   {
      // Sub-index 0 
      {
         UMTSBand1,
         LNA_HB_3,      
         TX_MB_2,       
         LNA_RXD_HB_1      
      },
      // Sub-index 1 
      {
         UMTSBand2,
         LNA_MB_1,
         TX_MB_2,
         NON_USED_BAND
      },
      // Sub-index 2 
      {
         UMTSBand4,
         LNA_HB_3,
         TX_MB_2,
         LNA_RXD_HB_1
      },
      // Sub-index 3 
      {
         UMTSBand5,
         LNA_LB_2,
         TX_LB_2,
         LNA_RXD_LB_1
      },
      // Sub-index 4 
      {
         UMTSBand8,
         LNA_LB_1,
         TX_LB_2,
         NON_USED_BAND
      }    
   },
   /* The Third set, Set 2 */
   {
      // Sub-index 1 
      {
         UMTSBand1,
         LNA_HB_2,      
         TX_HIGH_BAND2,       
         LNA_RXD_MB_1     
      },
      // Sub-index 1 
      {
         UMTSBand5,
         LNA_LB_1,
         TX_LOW_BAND1,
         LNA_RXD_LB_2
      },
      // Sub-index 2 
      {
         UMTSBandNone
      },
      // Sub-index 3 
      {
         UMTSBandNone
      },
      // Sub-index 4 
      {
         UMTSBandNone
      }     
   },
   /* The Fourth set, Set 3 */
   {
      // Sub-index 0 
      {
         UMTSBand1,
         LNA_HB_3,      
         TX_MB_2,       
         LNA_RXD_HB_1     
      },
      // Sub-index 1 
      {
         UMTSBand2,
         LNA_MB_1,
         TX_MB_2,
         NON_USED_BAND
      },
      // Sub-index 2 
      {
         UMTSBand5,
         LNA_LB_2,
         TX_LB_2,
         LNA_RXD_LB_1
      },
      // Sub-index 3 
      {
         UMTSBand8,
         LNA_LB_1,
         TX_LB_2,
         LNA_RXD_LB_3
      },
      // Sub-index 4 
      {
         UMTSBandNone
      }   
   },
   /* The Fifth set, Set 4 */
   {
      // Sub-index 0 
      {
         UMTSBand1,
         LNA_HB_3,      
         TX_MB_2,       
         LNA_RXD_HB_1     
      },
      // Sub-index 1 
      {
         UMTSBand2,
         LNA_MB_1,
         TX_MB_2,
         NON_USED_BAND
      },
      // Sub-index 2 
      {
         UMTSBand5,
         LNA_LB_2,
         TX_LB_2,
         LNA_RXD_LB_1
      },
      // Sub-index 3 
      {
         UMTSBand8,
         LNA_LB_1,
         TX_LB_2,
         LNA_RXD_LB_3
      },
      // Sub-index 4 
      {
         UMTSBandNone
      }
   },
   /* The Sixth set, Set 5 */
   {
      // Sub-index 0 
      {
         UMTSBand1,
         LNA_HB_3,      
         TX_MB_2,       
         LNA_RXD_HB_1     
      },
      // Sub-index 1 
      {
         UMTSBand2,
         LNA_MB_1,
         TX_MB_2,
         NON_USED_BAND
      },
      // Sub-index 2 
      {
         UMTSBand5,
         LNA_LB_2,
         TX_LB_2,
         LNA_RXD_LB_1
      },
      // Sub-index 3 
      {
         UMTSBand8,
         LNA_LB_1,
         TX_LB_2,
         LNA_RXD_LB_3
      },
      // Sub-index 4 
      {
         UMTSBandNone
      }     
   }
/*End of The Sixth set, Set 5 */
   
};
#endif

//*************************************************
// UMTS Tx calibration data Custom data definition
//*************************************************
// Tx Ramp data
#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
Ul1CustomDynamicInitUmtsTxRampData ul1CustomUmtsTxRampData[UL1CUSTOM_TOTAL_REAL_SET_NUMS] =
{
   /* The first set, Set 0 */
   {
        {NULL},   //First  Band TXRampData
//      {NULL},   //Second Band TXRampData
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Second set, Set 1 */
   {
        {NULL},   //First  Band TXRampData
//      {NULL},   //Second Band TXRampData
//      {NULL},
//      {NULL},      
//      {NULL}
   }
};
#else
Ul1CustomDynamicInitUmtsTxRampData ul1CustomUmtsTxRampData[UL1CUSTOM_TOTAL_SET_NUMS] =
{
   /* The first set, Set 0 */
   {
        {NULL},   //First  Band TXRampData
//      {NULL},   //Second Band TXRampData
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Second set, Set 1 */
   {
        {NULL},   //First  Band TXRampData
//      {NULL},   //Second Band TXRampData
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Third set, Set 2 */
   {
        {NULL},   //First  Band TXRampData
//      {NULL},   //Second Band TXRampData
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Fourth set, Set 3 */
   {
        {NULL},   //First  Band TXRampData
//      {NULL},   //Second Band TXRampData
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Fifth set, Set 4 */
   {
        {NULL},   //First  Band TXRampData
//      {NULL},   //Second Band TXRampData
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Sixth set, Set 5 */
   {
        {NULL},   //First  Band TXRampData
//      {NULL},   //Second Band TXRampData
//      {NULL},
//      {NULL},      
//      {NULL}
   }
};
#endif

// Tx PA oct-level data
#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
Ul1CustomDynamicInitUmtsTxPaOctLvlData ul1CustomTxPaOctLvlData[UL1CUSTOM_TOTAL_REAL_SET_NUMS] =
{
   /* The first set, Set 0 */
   {
        {NULL},   //First  Band TX PA Oct Level Data
//      {NULL},   //Second Band TX PA Oct Level Data
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Second set, Set 1 */
   {
        {NULL},   //First  Band TX PA Oct Level Data
//      {NULL},   //Second Band TX PA Oct Level Data
//      {NULL},
//      {NULL},      
//      {NULL}
   }
};
#else
Ul1CustomDynamicInitUmtsTxPaOctLvlData ul1CustomTxPaOctLvlData[UL1CUSTOM_TOTAL_SET_NUMS] =
{
   /* The first set, Set 0 */
   {
        {NULL},   //First  Band TX PA Oct Level Data
//      {NULL},   //Second Band TX PA Oct Level Data
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Second set, Set 1 */
   {
        {NULL},   //First  Band TX PA Oct Level Data
//      {NULL},   //Second Band TX PA Oct Level Data
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Third set, Set 2 */
   {
        {NULL},   //First  Band TX PA Oct Level Data
//      {NULL},   //Second Band TX PA Oct Level Data
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Fourth set, Set 3 */
   {
        {NULL},   //First  Band TX PA Oct Level Data
//      {NULL},   //Second Band TX PA Oct Level Data
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Fifth set, Set 4 */
   {
        {NULL},   //First  Band TX PA Oct Level Data
//      {NULL},   //Second Band TX PA Oct Level Data
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Sixth set, Set 5 */
   {
        {NULL},   //First  Band TX PA Oct Level Data
//      {NULL},   //Second Band TX PA Oct Level Data
//      {NULL},
//      {NULL},      
//      {NULL}
   },
};
#endif

//*************************************************
// UMTS Rx calibration data Custom data definition
//*************************************************
#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
Ul1CustomDynamicInitUmtsRxPathLossData ul1CustomUmtsRxPathLossData[UL1CUSTOM_TOTAL_REAL_SET_NUMS] =
{
   /* The first set, Set 0 */
   {
        {NULL},   //First  Band RX Path Loss Level Data
//      {NULL},   //Second Band RX Path Loss Level Data
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Second set, Set 1 */
   {
        {NULL},   //First  Band RX Path Loss Level Data
//      {NULL},   //Second Band RX Path Loss Level Data
//      {NULL},
//      {NULL},      
//      {NULL}
   }
};
#else
Ul1CustomDynamicInitUmtsRxPathLossData ul1CustomUmtsRxPathLossData[UL1CUSTOM_TOTAL_SET_NUMS] =
{
   /* The first set, Set 0 */
   {
        {NULL},   //First  Band RX Path Loss Level Data
//      {NULL},   //Second Band RX Path Loss Level Data
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Second set, Set 1 */
   {
        {NULL},   //First  Band RX Path Loss Level Data
//      {NULL},   //Second Band RX Path Loss Level Data
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Third set, Set 2 */
   {
        {NULL},   //First  Band RX Path Loss Level Data
//      {NULL},   //Second Band RX Path Loss Level Data
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Fourth set, Set 3 */
   {
        {NULL},   //First  Band RX Path Loss Level Data
//      {NULL},   //Second Band RX Path Loss Level Data
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Fifth set, Set 4 */
   {
        {NULL},   //First  Band RX Path Loss Level Data
//      {NULL},   //Second Band RX Path Loss Level Data
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Sixth set, Set 5 */
   {
        {NULL},   //First  Band RX Path Loss Level Data
//      {NULL},   //Second Band RX Path Loss Level Data
//      {NULL},
//      {NULL},      
//      {NULL}
   },
};
#endif

//*********************************************
// UMTS Temperature DAC Custom data definition
//*********************************************
#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
Ul1CustomDynamicInitUmtsTempDac ul1CustomUmtsTempDacData[UL1CUSTOM_TOTAL_REAL_SET_NUMS] =
{
        {NULL},   //First   Temperature DAC Data
        {NULL}    //Second  Temperature DAC Data
};
#else
Ul1CustomDynamicInitUmtsTempDac ul1CustomUmtsTempDacData[UL1CUSTOM_TOTAL_SET_NUMS] =
{

        {NULL},   //First   Temperature DAC Data
        {NULL},   //Second  Temperature DAC Data
        {NULL},
        {NULL},
        {NULL},
        {NULL}

};
#endif

//*********************************************
// UMTS TXPRACH Compensation Custom data definition
//*********************************************
#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
Ul1CustomDynamicInitUmtsTxPrachTmCompData ul1CustomUmtsTxPrachTmCompData[UL1CUSTOM_TOTAL_REAL_SET_NUMS] =
{
   /* The first set, Set 0 */
   {
        {NULL},   //First  Tx PRACH Temperature Compensation Data
//      {NULL},   //Second Tx PRACH Temperature Compensation Data
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Second set, Set 1 */
   {
        {NULL},   //First  Tx PRACH Temperature Compensation Data
//      {NULL},   //Second Tx PRACH Temperature Compensation Data
//      {NULL},
//      {NULL},      
//      {NULL}
   }
};
#else
Ul1CustomDynamicInitUmtsTxPrachTmCompData ul1CustomUmtsTxPrachTmCompData[UL1CUSTOM_TOTAL_SET_NUMS] =
{

   /* The first set, Set 0 */
   {
        {NULL},   //First  Tx PRACH Temperature Compensation Data
//      {NULL},   //Second Tx PRACH Temperature Compensation Data
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Second set, Set 1 */
   {
        {NULL},   //First  Tx PRACH Temperature Compensation Data
//      {NULL},   //Second Tx PRACH Temperature Compensation Data
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Third set, Set 2 */
   {
        {NULL},   //First  Tx PRACH Temperature Compensation Data
//      {NULL},   //Second Tx PRACH Temperature Compensation Data
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Fourth set, Set 3 */
   {
        {NULL},   //First  Tx PRACH Temperature Compensation Data
//      {NULL},   //Second Tx PRACH Temperature Compensation Data
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Fifth set, Set 4 */
   {
        {NULL},   //First  Tx PRACH Temperature Compensation Data
//      {NULL},   //Second Tx PRACH Temperature Compensation Data
//      {NULL},
//      {NULL},      
//      {NULL}
   },
   /* The Sixth set, Set 5 */
   {
        {NULL},   //First  Tx PRACH Temperature Compensation Data
//      {NULL},   //Second Tx PRACH Temperature Compensation Data
//      {NULL},
//      {NULL},      
//      {NULL}
   }

};
#endif


//******************************************************
// UMTS AFC DAC Custom data definition
//******************************************************
#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
Ul1CustomDynamicInitUmtsAfcDac ul1CustomUmtsAfcDacData[UL1CUSTOM_TOTAL_REAL_SET_NUMS] =
{
   /* The first set, Set 0 */
   {
        NULL,
   },
   /* The Second set, Set 1 */
   {
        NULL,
   }
};
#else
Ul1CustomDynamicInitUmtsAfcDac ul1CustomUmtsAfcDacData[UL1CUSTOM_TOTAL_SET_NUMS] =
{

   /* The first set, Set 0 */
   {
        NULL,
   },
   /* The Second set, Set 1 */
   {
        NULL,
   },
   /* The Third set, Set 2 */
   {
        NULL,
   },
   /* The Fourth set, Set 3 */
   {
        NULL,
   },
   /* The Fifth set, Set 4 */
   {
        NULL,
   },
   /* The Sixth set, Set 5 */
   {
        NULL,
   }

};
#endif
    
//******************************************************
// UMTS AFC CAP Custom data definition
//******************************************************
#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
Ul1CustomDynamicInitUmtsAfcCap ul1CustomUmtsAfcCapData[UL1CUSTOM_TOTAL_REAL_SET_NUMS] = 
{
   /* The first set, Set 0 */
   {
       NULL,
   },
   /* The Second set, Set 1 */
   {
       NULL,
   }
};
#else
Ul1CustomDynamicInitUmtsAfcCap ul1CustomUmtsAfcCapData[UL1CUSTOM_TOTAL_SET_NUMS] = 
{

   /* The first set, Set 0 */
   {
       NULL,
   },
   /* The Second set, Set 1 */
   {
       NULL,
   },
   /* The Third set, Set 2 */
   {
       NULL,
   },
   /* The Fourth set, Set 3 */
   {
       NULL,
   },
   /* The Fifth set, Set 4 */
   {
       NULL,
   },
   /* The Sixth set, Set 5 */
   {
       NULL,
   },

};
#endif

#if (IS_3G_MIPI_SUPPORT)
   #if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
Ul1CustomDynamicInitMipiData ul1CustomMipiData[UL1CUSTOM_TOTAL_REAL_SET_NUMS] = 
{
   /* The first set, Set 0 */
   {
        MIPI_ENABLE
       ,UMTS_MIPI_INITIAL_CW_ReMap_set0        //UMTS_MIPI_INITIAL_CW;   
       ,NULL                             //UMTS_MIPI_SLEEP_CW;
       ,UMTS_MIPI_RX_EVENT_TABLE_ReMap_set0    //UMTS_MIPI_RX_EVENT_TABLE;
       ,UMTS_MIPI_RX_DATA_TABLE_ReMap_set0     //UMTS_MIPI_RX_DATA_TABLE;
       ,UMTS_MIPI_TX_EVENT_TABLE_ReMap_set0    //UMTS_MIPI_TX_EVENT_TABLE; 
       ,UMTS_MIPI_TX_DATA_TABLE_ReMap_set0     //UMTS_MIPI_TX_DATA_TABLE; 
       ,UMTS_MIPI_TPC_TABLE_ReMap_set0         //UMTS_MIPI_TPC_TABLE;
       ,NULL                             //UMTS_MIPI_ASM_ISOLATION_DATA_TABLE; 
   },
   /* The second set, Set 1 */
   {
        MIPI_ENABLE
       ,UMTS_MIPI_INITIAL_CW_ReMap_set1        //UMTS_MIPI_INITIAL_CW;   
       ,NULL                             //UMTS_MIPI_SLEEP_CW;
       ,UMTS_MIPI_RX_EVENT_TABLE_ReMap_set1    //UMTS_MIPI_RX_EVENT_TABLE;
       ,UMTS_MIPI_RX_DATA_TABLE_ReMap_set1     //UMTS_MIPI_RX_DATA_TABLE;
       ,UMTS_MIPI_TX_EVENT_TABLE_ReMap_set1    //UMTS_MIPI_TX_EVENT_TABLE; 
       ,UMTS_MIPI_TX_DATA_TABLE_ReMap_set1     //UMTS_MIPI_TX_DATA_TABLE; 
       ,UMTS_MIPI_TPC_TABLE_ReMap_set1         //UMTS_MIPI_TPC_TABLE;
       ,NULL                             //UMTS_MIPI_ASM_ISOLATION_DATA_TABLE; 
   }
};
   #else
Ul1CustomDynamicInitMipiData ul1CustomMipiData[UL1CUSTOM_TOTAL_SET_NUMS] = 
{
   /* The first set, Set 0 */
   {
        MIPI_ENABLE
       ,UMTS_MIPI_INITIAL_CW_set0        //UMTS_MIPI_INITIAL_CW;   
       ,NULL                             //UMTS_MIPI_SLEEP_CW;
       ,UMTS_MIPI_RX_EVENT_TABLE_set0    //UMTS_MIPI_RX_EVENT_TABLE;
       ,UMTS_MIPI_RX_DATA_TABLE_set0     //UMTS_MIPI_RX_DATA_TABLE;
       ,UMTS_MIPI_TX_EVENT_TABLE_set0    //UMTS_MIPI_TX_EVENT_TABLE; 
       ,UMTS_MIPI_TX_DATA_TABLE_set0     //UMTS_MIPI_TX_DATA_TABLE; 
       ,UMTS_MIPI_TPC_TABLE_set0         //UMTS_MIPI_TPC_TABLE;
       ,NULL                             //UMTS_MIPI_ASM_ISOLATION_DATA_TABLE; 
   },
   /* The second set, Set 1 */
   {
        MIPI_ENABLE
       ,UMTS_MIPI_INITIAL_CW_set1        //UMTS_MIPI_INITIAL_CW;   
       ,NULL                             //UMTS_MIPI_SLEEP_CW;
       ,UMTS_MIPI_RX_EVENT_TABLE_set1    //UMTS_MIPI_RX_EVENT_TABLE;
       ,UMTS_MIPI_RX_DATA_TABLE_set1     //UMTS_MIPI_RX_DATA_TABLE;
       ,UMTS_MIPI_TX_EVENT_TABLE_set1    //UMTS_MIPI_TX_EVENT_TABLE; 
       ,UMTS_MIPI_TX_DATA_TABLE_set1     //UMTS_MIPI_TX_DATA_TABLE; 
       ,UMTS_MIPI_TPC_TABLE_set1         //UMTS_MIPI_TPC_TABLE;    
       ,NULL                             //UMTS_MIPI_ASM_ISOLATION_DATA_TABLE; 
   },
   /* The third set, Set 2 */
   {
        MIPI_ENABLE
       ,UMTS_MIPI_INITIAL_CW_set2        //UMTS_MIPI_INITIAL_CW;   
       ,NULL                             //UMTS_MIPI_SLEEP_CW;
       ,UMTS_MIPI_RX_EVENT_TABLE_set2    //UMTS_MIPI_RX_EVENT_TABLE;
       ,UMTS_MIPI_RX_DATA_TABLE_set2     //UMTS_MIPI_RX_DATA_TABLE;
       ,UMTS_MIPI_TX_EVENT_TABLE_set2    //UMTS_MIPI_TX_EVENT_TABLE; 
       ,UMTS_MIPI_TX_DATA_TABLE_set2     //UMTS_MIPI_TX_DATA_TABLE; 
       ,UMTS_MIPI_TPC_TABLE_set2         //UMTS_MIPI_TPC_TABLE;
       ,NULL                             //UMTS_MIPI_ASM_ISOLATION_DATA_TABLE; 
   },
   /* The fourth set, Set 3 */
   {
        MIPI_ENABLE
       ,UMTS_MIPI_INITIAL_CW_set3        //UMTS_MIPI_INITIAL_CW;   
       ,NULL                             //UMTS_MIPI_SLEEP_CW;
       ,UMTS_MIPI_RX_EVENT_TABLE_set3    //UMTS_MIPI_RX_EVENT_TABLE;
       ,UMTS_MIPI_RX_DATA_TABLE_set3     //UMTS_MIPI_RX_DATA_TABLE;
       ,UMTS_MIPI_TX_EVENT_TABLE_set3    //UMTS_MIPI_TX_EVENT_TABLE; 
       ,UMTS_MIPI_TX_DATA_TABLE_set3     //UMTS_MIPI_TX_DATA_TABLE; 
       ,UMTS_MIPI_TPC_TABLE_set3         //UMTS_MIPI_TPC_TABLE;
       ,NULL                             //UMTS_MIPI_ASM_ISOLATION_DATA_TABLE; 
   },
   /* The fifth set, Set 4 */
   {
        MIPI_ENABLE
       ,UMTS_MIPI_INITIAL_CW_set4        //UMTS_MIPI_INITIAL_CW;   
       ,NULL                             //UMTS_MIPI_SLEEP_CW;
       ,UMTS_MIPI_RX_EVENT_TABLE_set4    //UMTS_MIPI_RX_EVENT_TABLE;
       ,UMTS_MIPI_RX_DATA_TABLE_set4     //UMTS_MIPI_RX_DATA_TABLE;
       ,UMTS_MIPI_TX_EVENT_TABLE_set4    //UMTS_MIPI_TX_EVENT_TABLE; 
       ,UMTS_MIPI_TX_DATA_TABLE_set4     //UMTS_MIPI_TX_DATA_TABLE; 
       ,UMTS_MIPI_TPC_TABLE_set4         //UMTS_MIPI_TPC_TABLE;
       ,NULL                             //UMTS_MIPI_ASM_ISOLATION_DATA_TABLE; 
   },
   /* The sixth set, Set 5 */
   {
        MIPI_ENABLE
       ,UMTS_MIPI_INITIAL_CW_set5        //UMTS_MIPI_INITIAL_CW;   
       ,NULL                             //UMTS_MIPI_SLEEP_CW;
       ,UMTS_MIPI_RX_EVENT_TABLE_set5    //UMTS_MIPI_RX_EVENT_TABLE;
       ,UMTS_MIPI_RX_DATA_TABLE_set5     //UMTS_MIPI_RX_DATA_TABLE;
       ,UMTS_MIPI_TX_EVENT_TABLE_set5    //UMTS_MIPI_TX_EVENT_TABLE; 
       ,UMTS_MIPI_TX_DATA_TABLE_set5     //UMTS_MIPI_TX_DATA_TABLE; 
       ,UMTS_MIPI_TPC_TABLE_set5         //UMTS_MIPI_TPC_TABLE;
       ,NULL                             //UMTS_MIPI_ASM_ISOLATION_DATA_TABLE; 
   }
};
   #endif
#endif

//***************************************
// AuxADC voltage to level look-up table
//***************************************
kal_uint32 ul1custom_adc_volt_to_lvl[/*number of supported ADC levels*/][2] =
{
   /* Upper Bound */                  /* Lower Bound */
   {UL1CUSTOM_ADC_LVL0,               0},

   /* Don't remove the above line: insert your new added level setting definition
    * bellow or delete the unused level bellow */

   {UL1CUSTOM_ADC_LVL1,               UL1CUSTOM_ADC_LVL0},
   {UL1CUSTOM_ADC_LVL2,               UL1CUSTOM_ADC_LVL1},

   /* Insert your new added level setting definition above or
    * delete the unused level above, and then change lower bound
    * UL1CUSTOM_ADC_LVL6 below to the last upper bound in the above lines */

   {UL1CUSTOM_ADC_MAX_INPUT_VOLTAGE,  UL1CUSTOM_ADC_LVL2}
};


//***************************************
// Rxd Path Setting
//***************************************
#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
kal_uint32 ul1custom_rxdpathsetting[UL1CUSTOM_TOTAL_REAL_SET_NUMS][2] =
{
   /* The first set, Set 0 */   
   /*Rx_Main_Path_Onoff */    /* Rx_Diversity_Path_Onoff */
   {0xFFFFFFFF,                0xFFFFFFFF},
   /* The second set, Set 1  */     
   {0xFFFFFFFF,                0xFFFFFFFF}
};
#else
kal_uint32 ul1custom_rxdpathsetting[UL1CUSTOM_TOTAL_SET_NUMS][2] =
{
   /* The first set, Set 0 */   
   /*Rx_Main_Path_Onoff */    /* Rx_Diversity_Path_Onoff */
   {0xFFFFFFFF,                0xFFFFFFFF},
   /* The second set, Set 1  */     
   {0xFFFFFFFF,                0xFFFFFFFF},
   /* The third set, Set 2  */     
   {0xFFFFFFFF,                0xFFFFFFFF},
   /* The fourth set, Set 3  */     
   {0xFFFFFFFF,                0xFFFFFFFF},
   /* The fifth set, Set 4  */     
   {0xFFFFFFFF,                0xFFFFFFFF},
   /* The sixth set, Set 5  */     
   {0xFFFFFFFF,                0xFFFFFFFF}   

};
#endif


//***************************************
// PA Drift Setting
// PA_DIRFT_COMPENSATION
//***************************************
#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
kal_uint32 ul1custom_pa_drift_compensation[UL1CUSTOM_TOTAL_REAL_SET_NUMS] = 
{
   /* The first set, Set 0 */
   0x00000000,
   /* The second set, Set 1*/
   0x00000000
};
#else
kal_uint32 ul1custom_pa_drift_compensation[UL1CUSTOM_TOTAL_SET_NUMS] = 
{
   /* The first set, Set 0 */
   0x00000000,
   /* The second set, Set 1*/
   0x00000000,
   /* The third set, Set 2*/
   0x00000000,
   /* The fourth set, Set 3*/
   0x00000000,
   /* The fifth set, Set 4*/
   0x00000000,
   /* The sixth set, Set 5*/
   0x00000000
   
};
#endif



/*******************************************************************************
** Global Functions
*******************************************************************************/
void UL1CUSTOM_ReplaceAuxAdcCalibrate(kal_uint32 adcDigitalValue, kal_int32 *volt)
{
   /* Empty function */
}

/*******************************************************************************
** Global Functions
** for Feature phone/data-card GPIO Pin number access
** Not Recommend to modify
*******************************************************************************/
void UL1CUSTOM_GPIO_NON_SMART_PHONE_PIN_ACCESS(kal_int16 *gpio_pin)
{
#if (!UL1CUSTOM_SMART_PHONE_ENABLE)
#if   (UL1CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE == 0x1)
	extern const char GPIO_FDD_BAND_SUPPORT_DETECT_1ST_PIN;
	gpio_pin[0]=GPIO_FDD_BAND_SUPPORT_DETECT_1ST_PIN;
	gpio_pin[1]=-1;
	gpio_pin[2]=-1;
#elif (UL1CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE == 0x2)
	extern const char GPIO_FDD_BAND_SUPPORT_DETECT_1ST_PIN;
	extern const char GPIO_FDD_BAND_SUPPORT_DETECT_2ND_PIN;
	gpio_pin[0]=GPIO_FDD_BAND_SUPPORT_DETECT_1ST_PIN;
	gpio_pin[1]=GPIO_FDD_BAND_SUPPORT_DETECT_2ND_PIN;
	gpio_pin[2]=-1;	
#elif (UL1CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE == 0x3)
	extern const char GPIO_FDD_BAND_SUPPORT_DETECT_1ST_PIN;
	extern const char GPIO_FDD_BAND_SUPPORT_DETECT_2ND_PIN;
	extern const char GPIO_FDD_BAND_SUPPORT_DETECT_3RD_PIN;
	gpio_pin[0]=GPIO_FDD_BAND_SUPPORT_DETECT_1ST_PIN;
	gpio_pin[1]=GPIO_FDD_BAND_SUPPORT_DETECT_2ND_PIN;
	gpio_pin[2]=GPIO_FDD_BAND_SUPPORT_DETECT_3RD_PIN;	
#endif
#endif // (!UL1CUSTOM_SMART_PHONE_ENABLE)		
}

#endif /*IS_3G_DRDI_SUPPORT*/

#endif /* #ifdef __MTK_TARGET__ */

/* END OF FILE */
