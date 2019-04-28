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
 *	mml1_custom_drdi.c
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
 *	  
 *
 *******************************************************************************/

#ifdef __MTK_TARGET__

/*******************************************************************************
 * Includes                                                                    *
 *******************************************************************************/
#include "kal_general_types.h"
#include "mml1_custom_mipi.h"
#include "mml1_custom_drdi.h"

/*******************************************************************************
 * Global Data                                                                 *
 *******************************************************************************/

/* Look up table from action id to action function           
 * See the enum #Mml1CustomFunction for members of the table */
const Mml1CustomFunction mml1CustomActionTable[MML1_CUSTOM_MAX_PROC_ACTIONS] =
{
    NULL,
 
#if MML1_CUSTOM_GPIO_ENABLE
    MML1_RF_DRDI_CUSTOM_DynamicInitByGPIO,
#else
    NULL, /* Null action */
#endif
 
#if MML1_CUSTOM_ADC_ENABLE
    MML1_RF_DRDI_CUSTOM_DynamicInitByADC,
#else
    NULL, /* Null action */
#endif
 
#if MML1_CUSTOM_BARCODE_ENABLE
    MML1_RF_DRDI_CUSTOM_DynamicInitByBarcode
#else
    NULL  /* Null action */
#endif
};


/*****************************************
 * AuxADC voltage to level look-up table *
 *****************************************/
kal_uint32 mml1_custom_adc_volt_to_lvl[/*number of supported ADC levels*/][2] =
{
   /* Upper Bound */                  /* Lower Bound */
   {MML1_CUSTOM_ADC_LVL0,               0},
 
   /* Don't remove the above line: insert your new added level setting definition
    * bellow or delete the unused level bellow */
 
   {MML1_CUSTOM_ADC_LVL1,               MML1_CUSTOM_ADC_LVL0},
   {MML1_CUSTOM_ADC_LVL2,               MML1_CUSTOM_ADC_LVL1},
   {MML1_CUSTOM_ADC_LVL3,               MML1_CUSTOM_ADC_LVL2},
   {MML1_CUSTOM_ADC_LVL4,               MML1_CUSTOM_ADC_LVL3},
   
   /* Insert your new added level setting definition above or
    * delete the unused level above, and then change lower bound
    * EL1_CUSTOM_ADC_LVL6 below to the last upper bound in the above lines */
 
   {MML1_CUSTOM_ADC_MAX_INPUT_VOLTAGE,  MML1_CUSTOM_ADC_LVL4}
};
 

/*****************************************
 * Barcode digits array                  *
 *****************************************/
kal_char mml1_custom_barcode_digits[MML1_CUSTOM_BARCODE_NUMS_IN_CALC] =
{
   '8', //Set 0, ex; for MURATA_SP7T
#if 0
/* under construction !*/
/* under construction !*/
#endif
};


/*******************************************************************************
 * Global Functions                                                            *
 *******************************************************************************/

/**
 * @brief get GPIO pin port number
 *
 * for Feature phone/data-card GPIO Pin number access
 * NOT Recommend to modify
 *
 * @param gpio_pin buffer to save the GPIO pin number
 * @return None
 */
void MML1_CUSTOM_GPIO_NON_SMART_PHONE_PIN_ACCESS(kal_int16 *gpio_pin)
{
   /*PS: If link error happens, PLEASE check if codegen(DWS) generates the following variables */
#if (!MML1_CUSTOM_SMART_PHONE_ENABLE)
#if (MML1_CUSTOM_GPIO_ENABLE)
#if   (MML1_CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE == 0x1)
   // extern const char is from gpio_var.c (codegen)
   extern const char GPIO_FDD_BAND_SUPPORT_DETECT_1ST_PIN;
   gpio_pin[0] = GPIO_FDD_BAND_SUPPORT_DETECT_1ST_PIN;
   gpio_pin[1] = -1;
   gpio_pin[2] = -1;
#elif (MML1_CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE == 0x2)
   extern const char GPIO_FDD_BAND_SUPPORT_DETECT_1ST_PIN;
   extern const char GPIO_FDD_BAND_SUPPORT_DETECT_2ND_PIN;
   gpio_pin[0] = GPIO_FDD_BAND_SUPPORT_DETECT_1ST_PIN;
   gpio_pin[1] = GPIO_FDD_BAND_SUPPORT_DETECT_2ND_PIN;
   gpio_pin[2] = -1;	
#elif (MML1_CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE == 0x3)
   extern const char GPIO_FDD_BAND_SUPPORT_DETECT_1ST_PIN;
   extern const char GPIO_FDD_BAND_SUPPORT_DETECT_2ND_PIN;
   extern const char GPIO_FDD_BAND_SUPPORT_DETECT_3RD_PIN;
   gpio_pin[0] = GPIO_FDD_BAND_SUPPORT_DETECT_1ST_PIN;
   gpio_pin[1] = GPIO_FDD_BAND_SUPPORT_DETECT_2ND_PIN;
   gpio_pin[2] = GPIO_FDD_BAND_SUPPORT_DETECT_3RD_PIN;	
#endif
#endif
#endif // (!MML1_CUSTOM_SMART_PHONE_ENABLE)		
}

/**
 * @brief get ADC pin port number
 *
 * for Feature phone/data-card ADC Pin number access
 * NOT Recommend to modify
 *
 * @param adc_channel_num variable to save ADC channel number
 * @return None
 */
void MML1_CUSTOM_ADC_PIN_ACCESS(kal_uint16 *adc_channel_num)
{
   /*PS: If link error happens, PLEASE check if codegen(DWS) generates the following variables */
#if (!MML1_CUSTOM_SMART_PHONE_ENABLE)
#if (MML1_CUSTOM_ADC_ENABLE)
   // extern const char is from adc_var.c (codegen)
   extern const char ADC_FDD_RF_PARAMS_DYNAMIC_CUSTOM_CH;
   adc_channel_num[0] = ADC_FDD_RF_PARAMS_DYNAMIC_CUSTOM_CH;
#else
   adc_channel_num[0] = 0xFFFF;
#endif
#endif
}

/**
 * @brief get ADC custom parameters
 * NOT Recommend to modify
 */
void MML1_CUSTOM_ADC_PARAM(kal_uint16 *adcMeasCountOrder, kal_uint32 *adcMaxVolt, kal_uint16 *adcBit)
{
   *adcMeasCountOrder = MML1_CUSTOM_ADC_MEAS_COUNT_2_ORDER;
   *adcMaxVolt        = MML1_CUSTOM_ADC_MAX_INPUT_VOLTAGE;
   *adcBit            = MML1_CUSTOM_ADC_BITS;
}

/**
 * @brief Get MML1 Custom parameters
 * 
 * This function is used to save all custom parameters about GPIO, ADC,
 * and Barcode Setting.
 * NOT Recommend to modify.
 * 
 * @param drdiCustomParam Input data structure to save all custom parameters
 */
void MML1_CUSTOM_GET_CUSTOM_PARAM(Mml1RfDrdiCustomParam *drdiCustomParam)
{
   // DO NOT MODIFY THIS FUNCTION !!!!!!
   drdiCustomParam->mml1_custom_debug_enable                    = MML1_CUSTOM_DEBUG_ENABLE;
   drdiCustomParam->mml1_custom_gpio_set_nums                   = MML1_CUSTOM_GPIO_SET_NUMS;            
   drdiCustomParam->mml1_custom_adc_set_nums                    = MML1_CUSTOM_ADC_SET_NUMS;             
   drdiCustomParam->mml1_custom_nvram_barcode_set_nums          = MML1_CUSTOM_BARCODE_SET_NUMS;   
   drdiCustomParam->mml1_custom_gpio_nums_in_calc               = MML1_CUSTOM_GPIO_NUMS_IN_CALC;
   drdiCustomParam->mml1_custom_ADC_nums_in_calc                = MML1_CUSTOM_ADC_NUMS_IN_CALC;
   drdiCustomParam->mml1_custom_barcode_nums_in_calc            = MML1_CUSTOM_BARCODE_NUMS_IN_CALC;
   drdiCustomParam->mml1_custom_first_index                     = MML1_CUSTOM_FIRST_INDEX;
   drdiCustomParam->mml1_custom_second_index                    = MML1_CUSTOM_SECOND_INDEX;
   drdiCustomParam->mml1_custom_third_index                     = MML1_CUSTOM_THIRD_INDEX;
   drdiCustomParam->mml1_custom_first_index_base                = MML1_CUSTOM_FIRST_INDEX_BASE;
   drdiCustomParam->mml1_custom_gpio_num_of_detect_pins_in_use  = MML1_CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE;
   drdiCustomParam->mml1_custom_adc_level_total                 = MML1_CUSTOM_ADC_LEVEL_TOTAL;
   drdiCustomParam->mml1_custom_barcode_read_digit_num          = MML1_CUSTOM_BARCODE_READ_DIGIT_NUM;
   drdiCustomParam->mml1_custom_barcode_digit_value_1           = MML1_CUSTOM_BARCODE_DIGIT_VALUE_1;
   drdiCustomParam->mml1_custom_barcode_digit_value_2           = MML1_CUSTOM_BARCODE_DIGIT_VALUE_2;
   drdiCustomParam->mml1_custom_barcode_digit_value_3           = MML1_CUSTOM_BARCODE_DIGIT_VALUE_3;
   drdiCustomParam->mml1_custom_adc_calibrate_enable            = MML1_CUSTOM_ADC_CALIBRATE_ENABLE;
   drdiCustomParam->mml1_custom_adc_bits                        = MML1_CUSTOM_ADC_BITS;
   drdiCustomParam->mml1_custom_adc_meas_count_2_order          = MML1_CUSTOM_ADC_MEAS_COUNT_2_ORDER;
   drdiCustomParam->mml1_custom_total_set_nums                  = MML1_CUSTOM_TOTAL_SET_NUMS;
   drdiCustomParam->mml1_custom_adc_max_input_voltage           = MML1_CUSTOM_ADC_MAX_INPUT_VOLTAGE;
}

/***************************************************************************
 * Data array of pointers pointed to data array for custom data
 ***************************************************************************/
#if (IS_MML1_DRDI_ENABLE && IS_MML1_DRDI_REMAP_ENABLE)
Mml1CustomDynamicInitMipiData         *mml1_mipiData_array_ptr[MML1_DRDI_REMAP_MMRF_REAL_SET_NUMS];
#else
Mml1CustomDynamicInitMipiData         *mml1_mipiData_array_ptr[MML1_CUSTOM_TOTAL_SET_NUMS];
#endif

#if defined(__EXT_VRF18_BUCK_SUPPORT__)
   #if (IS_MML1_DRDI_ENABLE && IS_MML1_DRDI_REMAP_ENABLE)
Mml1CustomDynamicInitPmicData       *mml1_pmicData_array_ptr[MML1_DRDI_REMAP_MMRF_REAL_SET_NUMS];
   #else
Mml1CustomDynamicInitPmicData       *mml1_pmicData_array_ptr[MML1_CUSTOM_TOTAL_SET_NUMS];
   #endif
#endif
/***************************************************************************
 * Data definition for custom to input data
 ***************************************************************************/
#if (IS_MML1_DRDI_ENABLE && IS_MML1_DRDI_REMAP_ENABLE)
Mml1CustomDynamicInitMipiData mml1CustomMipiData[MML1_DRDI_REMAP_MMRF_REAL_SET_NUMS] = 
{
   /* The first  set, ReMap Set 0 */
   {
      MML1_MIPI_INITIAL_CW_ReMap_set0,           //MML1_MIPI_INITIAL_CW;   
      MML1_MIPI_USID_CHANGE_TABLE_ReMap_set0,    //MML1_MIPI_USID_CHANGE_TABLE;
   },
   /* The second set, ReMap Set 1 */
   /* The third  set, ReMap Set 2 */
   /* The fourth set, ReMap Set 3 */
   /* The fifth  set, ReMap Set 4 */
   /* The sixth  set, ReMap Set 5 */
};
#else
Mml1CustomDynamicInitMipiData mml1CustomMipiData[MML1_CUSTOM_TOTAL_SET_NUMS] = 
{
   /* The first  set, Set 0, for EVB */
   {
      MML1_MIPI_INITIAL_CW_set0,           //MML1_MIPI_INITIAL_CW;   
      MML1_MIPI_USID_CHANGE_TABLE_set0,    //MML1_MIPI_USID_CHANGE_TABLE;
   },
   #if IS_MML1_DRDI_ENABLE
   /* The second set, Set 1, for SKU6 */
   {
      MML1_MIPI_INITIAL_CW_set1,           //MML1_MIPI_INITIAL_CW;   
      MML1_MIPI_USID_CHANGE_TABLE_set1,    //MML1_MIPI_USID_CHANGE_TABLE;    
   },
   /* The third  set, Set 2, for SKU5 */
   {
      MML1_MIPI_INITIAL_CW_set2,           //MML1_MIPI_INITIAL_CW;   
      MML1_MIPI_USID_CHANGE_TABLE_set2,    //MML1_MIPI_USID_CHANGE_TABLE;    
   },
   /* The fourth set, Set 3, for SKU3 */
   {
      MML1_MIPI_INITIAL_CW_set3,           //MML1_MIPI_INITIAL_CW;   
      MML1_MIPI_USID_CHANGE_TABLE_set3,    //MML1_MIPI_USID_CHANGE_TABLE;    
   },
   /* The fifth  set, Set 4, for SKU2 */
   {
      MML1_MIPI_INITIAL_CW_set4,           //MML1_MIPI_INITIAL_CW;   
      MML1_MIPI_USID_CHANGE_TABLE_set4,    //MML1_MIPI_USID_CHANGE_TABLE;    
   },
   /* The sixth  set, Set 5, for SKU1 */
   {
      MML1_MIPI_INITIAL_CW_set5,           //MML1_MIPI_INITIAL_CW;   
      MML1_MIPI_USID_CHANGE_TABLE_set5,    //MML1_MIPI_USID_CHANGE_TABLE;    
   },
   /*** APPEND new set of custom data HERE ***/
   #endif
};
#endif

#if defined(__EXT_VRF18_BUCK_SUPPORT__)
   #if (IS_MML1_DRDI_ENABLE && IS_MML1_DRDI_REMAP_ENABLE)
Mml1CustomDynamicInitPmicData mml1CustomPmicData[MML1_DRDI_REMAP_MMRF_REAL_SET_NUMS] = 
{
   {  /* The first  set,  ReMap Set 0 */
      KAL_FALSE, //extVrf18Enable
   },
   {  /* The second  set, ReMap Set 1 */
      KAL_TRUE,  //extVrf18Enable
   },
};
   #else
Mml1CustomDynamicInitPmicData mml1CustomPmicData[MML1_CUSTOM_TOTAL_SET_NUMS] = 
{
   {  /* The first  set, Set 0 */
      KAL_FALSE, //extVrf18Enable
   },
   {  /* The second set, Set 1 */
      KAL_FALSE, //extVrf18Enable
   },
   {  /* The third  set, Set 2 */
      KAL_FALSE,  //extVrf18Enable
   },
   {  /* The fourth set, Set 3 */
      KAL_FALSE,  //extVrf18Enable
   },
   {  /* The fifth  set, Set 4 */
      KAL_TRUE,  //extVrf18Enable
   },
   {  /* The sixth  set, Set 5 */
      KAL_TRUE,  //extVrf18Enable
   },
};
   #endif
#endif

/***************************************************************************
 * DRDI Remapping Table Definition
 ***************************************************************************/
kal_uint16 MML1_DRDI_GGE_ReMapTable[MML1_CUSTOM_TOTAL_SET_NUMS] =
{  /* 00  ~  (MML1_CUSTOM_TOTAL_SET_NUMS-1) */
   /* 00 */
       0,
   #if IS_MML1_DRDI_ENABLE
   /*     01, 02, 03, 04, 05, 06, 07, 08, 09*/
           1,  2,  2,  2,  2,
   /* 10, 11, 12, 13, 14, 15, 16, 17, 18, 09*/
   /* 20, 21, 22, 23, 24, 25, 26, 27, 28, 09*/   
   /* 30, 31, 32, 33, 34, 35, 36, 37, 38, 39*/
   /* 40, 41, 42, 43, 44, 45, 46, 47, 48, 49*/
   /* 50, 51, 52, 53, 54, 55, 56, 57, 58, 59*/
   /* 60, 61, 62, 63  */
   #endif
};

kal_uint16 MML1_DRDI_UMTS_ReMapTable[MML1_CUSTOM_TOTAL_SET_NUMS] =
{  /* 00  ~  (MML1_CUSTOM_TOTAL_SET_NUMS-1) */
   /* 00 */
       0,
   #if IS_MML1_DRDI_ENABLE
   /*     01, 02, 03, 04, 05, 06, 07, 08, 09*/
           1,  0,  0,  0,  0,
   /* 10, 11, 12, 13, 14, 15, 16, 17, 18, 09*/
   /* 20, 21, 22, 23, 24, 25, 26, 27, 28, 09*/   
   /* 30, 31, 32, 33, 34, 35, 36, 37, 38, 39*/
   /* 40, 41, 42, 43, 44, 45, 46, 47, 48, 49*/
   /* 50, 51, 52, 53, 54, 55, 56, 57, 58, 59*/
   /* 60, 61, 62, 63  */
   #endif
};

kal_uint16 MML1_DRDI_TDS_ReMapTable[MML1_CUSTOM_TOTAL_SET_NUMS] =
{  /* 00  ~  (MML1_CUSTOM_TOTAL_SET_NUMS-1) */
   /* 00 */
       0,
   #if IS_MML1_DRDI_ENABLE
   /*     01, 02, 03, 04, 05, 06, 07, 08, 09*/
           1,  0,  0,  0,  0,
   /* 10, 11, 12, 13, 14, 15, 16, 17, 18, 09*/
   /* 20, 21, 22, 23, 24, 25, 26, 27, 28, 09*/   
   /* 30, 31, 32, 33, 34, 35, 36, 37, 38, 39*/
   /* 40, 41, 42, 43, 44, 45, 46, 47, 48, 49*/
   /* 50, 51, 52, 53, 54, 55, 56, 57, 58, 59*/
   /* 60, 61, 62, 63  */
   #endif
};

kal_uint16 MML1_DRDI_LTE_ReMapTable[MML1_CUSTOM_TOTAL_SET_NUMS] =
{  /* 00  ~  (MML1_CUSTOM_TOTAL_SET_NUMS-1) */
   /* 00 */
       0,
   #if IS_MML1_DRDI_ENABLE
   /*     01, 02, 03, 04, 05, 06, 07, 08, 09*/
           1,  2,  3,  4,  5,
   /* 10, 11, 12, 13, 14, 15, 16, 17, 18, 09*/
   /* 20, 21, 22, 23, 24, 25, 26, 27, 28, 09*/   
   /* 30, 31, 32, 33, 34, 35, 36, 37, 38, 39*/
   /* 40, 41, 42, 43, 44, 45, 46, 47, 48, 49*/
   /* 50, 51, 52, 53, 54, 55, 56, 57, 58, 59*/
   /* 60, 61, 62, 63  */
   #endif
};

kal_uint16 MML1_DRDI_MMRF_ReMapTable[MML1_CUSTOM_TOTAL_SET_NUMS] =
{  /* 00  ~  (MML1_CUSTOM_TOTAL_SET_NUMS-1) */
   /* 00 */
       0,
   #if IS_MML1_DRDI_ENABLE
   /*     01, 02, 03, 04, 05, 06, 07, 08, 09*/
           0,  0,  0,  0,  0,
   /* 10, 11, 12, 13, 14, 15, 16, 17, 18, 09*/
   /* 20, 21, 22, 23, 24, 25, 26, 27, 28, 09*/   
   /* 30, 31, 32, 33, 34, 35, 36, 37, 38, 39*/
   /* 40, 41, 42, 43, 44, 45, 46, 47, 48, 49*/
   /* 50, 51, 52, 53, 54, 55, 56, 57, 58, 59*/
   /* 60, 61, 62, 63  */
   #endif
};

#endif /* #ifdef __MTK_TARGET__ */

/* END OF FILE */
