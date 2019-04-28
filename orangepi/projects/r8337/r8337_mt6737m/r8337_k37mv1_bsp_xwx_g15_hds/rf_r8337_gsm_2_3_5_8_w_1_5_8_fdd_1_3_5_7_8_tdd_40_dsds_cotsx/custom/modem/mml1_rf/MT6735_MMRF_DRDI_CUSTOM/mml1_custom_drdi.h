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
 *  mml1_custom_drdi.h
 *
 * Project:
 * --------
 *  MOLY
 *
 * Description:
 * ------------
 *  Dynamic Radio-setting Dedicated Image.
 *  DRDI parameters custom macro definitions
 *
 * Author:
 * -------
 * -------
 *
 *******************************************************************************/

#ifndef  _MML1_CUSTOM_DRDI_H_
#define  _MML1_CUSTOM_DRDI_H_


/*******************************************************************************
** Includes
*******************************************************************************/
#include "kal_general_types.h"
#include "mml1_rf_global.h"
 
/********************
 * Common
 *******************/
/*****************************************************************************
* Constant    : MML1_CUSTOM_SMART_PHONE_ENABLE
* Group       : Real target, Customization, MML1 common operation
* Description : Constant to seperate SMART_PHONE / FEATURE_PHONE / DATACARD
*               for common modem image feature
* Examples	  : MT6575/6577, MT6589/6572/6582
*				MML1_CUSTOM_SMART_PHONE_ENABLE	(1)
* 				MT6290
*				MML1_CUSTOM_SMART_PHONE_ENABLE	(0)
*****************************************************************************/
#define MML1_CUSTOM_SMART_PHONE_ENABLE     (1)
//NEED TO MODIFY

/*****************************************************************************
* Constant    : MML1_CUSTOM_GPIO_ENABLE
*               MML1_CUSTOM_ADC_ENABLE
* Group       : Real target, Customization, MML1 common operation
* Description : Constants to enable "Dynamic Initialization RF parameters"
*               mechanism
*****************************************************************************/
#define MML1_CUSTOM_GPIO_ENABLE            (0)
#define MML1_CUSTOM_ADC_ENABLE             (1)


/*****************************************************************************
* Constant    : IS_MML1_DRDI_ENABLE
* Group       : Real target, Customization, MML1 common operation
* Description : Constants to enable "Dynamic Initialization RF parameters"
*               mechanism for all RAT
*****************************************************************************/
#define IS_MML1_DRDI_ENABLE                (MML1_CUSTOM_GPIO_ENABLE|MML1_CUSTOM_ADC_ENABLE)


/*****************************************************************************
* Constant    : IS_MML1_DRDI_REMAP_ENABLE
* Group       : Real target, Customization, MML1 common operation
* Description : Constants to enable "Dynamic Initialization RF parameters"
*               Re-Mapping Table mechanism for all RAT
*****************************************************************************/
#define IS_MML1_DRDI_REMAP_ENABLE          (0)


/*****************************************************************************
* Constant    : MML1_CUSTOM_GPIO_SET_NUMS
*               MML1_CUSTOM_ADC_SET_NUMS
* Group       : Real target, Customization, MML1 common operation
* Description : Constants to define number of sets can be represented by each
*               mechanism respectively
*****************************************************************************/
#define MML1_CUSTOM_GPIO_SET_NUMS          (0)
#define MML1_CUSTOM_ADC_SET_NUMS           (6)


/*****************************************************************************
* Constant    : MML1_CUSTOM_FIRST_INDEX
*               MML1_CUSTOM_SECOND_INDEX
* Group       : Real target, Customization, MML1 common operation
* Description : Constants to first, second and third index for the
*               representation in configuration set index table
*               The value can be set:                                                                      
*                  MML1_CUSTOM_NULL_ACTION_ID                                                                     
*                  MML1_CUSTOM_GPIO_DETECTION_ID                                                               
*                  MML1_CUSTOM_ADC_DETECTION_ID                                                                                                                         
*****************************************************************************/
#define MML1_CUSTOM_FIRST_INDEX            (MML1_CUSTOM_ADC_DETECTION_ID)
#define MML1_CUSTOM_SECOND_INDEX           (MML1_CUSTOM_NULL_ACTION_ID)


/*****************************************************************************
* Constant    : MML1_CUSTOM_DEBUG_ENABLE
* Group       : Real target, Customization, MML1 common operation
* Description : Constants to enable "Dynamic Initialization RF parameters"
*               debug info logging
*               For trace output to debug ( MML1_CustomDynamicDebug() )
*****************************************************************************/
#define MML1_CUSTOM_DEBUG_ENABLE           (1)


/********************
 * ADC
 *******************/
/* Sample of ADC votlage to level look-up table

---------------------------------------------------------------------
ADC levels - 8

Level   Level(V)        Level(uV)       Upper(uV)       Lower(uV)
0       0.089285        89285           178571          0
1       0.357142        357142          535713          178571
2       0.714284        714284          892855          535713
3       1.071426        1071426         1249997         892855
4       1.428568        1428568         1607139         1249997
5       1.78571         1785710         1964281         1607139
6       2.142852        2142852         2321423         1964281
7       2.410711        2410711         2500000         2321423

---------------------------------------------------------------------
ADC levels - 6

Level   Level(V)        Level(uV)       Upper(uV)       Lower(uV)
0       0.125           125000          250000          0
1       0.5             500000          750000          250000
2       1               1000000         1250000         750000
3       1.5             1500000         1750000         1250000
4       2               2000000         2250000         1750000
5       2.375           2375000         2500000         2250000

---------------------------------------------------------------------
ADC levels - 4

Level   Level(V)        Level(uV)       Upper(uV)       Lower(uV)
0       0.208333        208333          416666          0
1       0.833332        833332          1249999         416666
2       1.666665        1666665         2083332         1249999
3       2.291666        2291666         2500000         2083332


---------------------------------------------------------------------
ADC levels - 2

Level   Level(V)        Level(uV)       Upper(uV)       Lower(uV)
0       0.625           625000          1250000         0
1       1.875           1875000         2500000         1250000

*/

/*****************************************************************************
* Constant    : MM1_CUSTOM_ADC_LEVEL_TOTAL
* Group       : Real target, Customization, MML1 common operation
* Description : Customization constant to be used for customer to determine
*               number of ADC levels to be used to distinguish between the
*               RF HW configurations
*****************************************************************************/
#define MML1_CUSTOM_ADC_LEVEL_TOTAL          (6)

/*****************************************************************************
* Constant    : MML1_CUSTOM_ADC_MEAS_COUNT_2_ORDER
* Group       : Real target, Customization, MML1 common operation
* Description : Customization constant to be used for customer to determine
*               number of ADC channel measurement counts (in 2's order)
*               ex: 7 => 2^7 = 128
*****************************************************************************/
#define MML1_CUSTOM_ADC_MEAS_COUNT_2_ORDER   (7) //2^7 = 128

#define MML1_CUSTOM_ADC_LVL0                 (MML1_CUSTOM_ADC_VOLT_LVL_MIN_LVL_STEP)
#define MML1_CUSTOM_ADC_LVL1                 (MML1_CUSTOM_ADC_LVL0 + MML1_CUSTOM_ADC_VOLT_LVL_STEP_SIZE)
#define MML1_CUSTOM_ADC_LVL2                 (MML1_CUSTOM_ADC_LVL1 + MML1_CUSTOM_ADC_VOLT_LVL_STEP_SIZE)
#define MML1_CUSTOM_ADC_LVL3                 (MML1_CUSTOM_ADC_LVL2 + MML1_CUSTOM_ADC_VOLT_LVL_STEP_SIZE)
#define MML1_CUSTOM_ADC_LVL4                 (MML1_CUSTOM_ADC_LVL3 + MML1_CUSTOM_ADC_VOLT_LVL_STEP_SIZE)
#define MML1_CUSTOM_ADC_LVL5                 (MML1_CUSTOM_ADC_LVL4 + MML1_CUSTOM_ADC_VOLT_LVL_STEP_SIZE)
#define MML1_CUSTOM_ADC_LVL6                 (MML1_CUSTOM_ADC_LVL5 + MML1_CUSTOM_ADC_VOLT_LVL_STEP_SIZE)


/*****************************************************************************
* Constant    : MML1_DRDI_REMAP_rat_REAL_SET_NUMS
* Group       : Real target, Internals, MML1 common operation
* Description : Constant to be used as the number of each RAT configuration sets
*****************************************************************************/
#if (IS_MML1_DRDI_ENABLE && IS_MML1_DRDI_REMAP_ENABLE)  /* !!NOTE: this remapping table real set number should follow the rule!! */
#define MML1_DRDI_REMAP_GGE_REAL_SET_NUMS           (3) /* 1 <= MML1_DRDI_REMAP_GGE_REAL_SET_NUMS  <= MML1_CUSTOM_TOTAL_SET_NUMS */
#define MML1_DRDI_REMAP_UMTS_REAL_SET_NUMS          (2) /* 1 <= MML1_DRDI_REMAP_UMTS_REAL_SET_NUMS <= MML1_CUSTOM_TOTAL_SET_NUMS */
#define MML1_DRDI_REMAP_TDS_REAL_SET_NUMS           (2) /* 1 <= MML1_DRDI_REMAP_TDS_REAL_SET_NUMS  <= MML1_CUSTOM_TOTAL_SET_NUMS */
#define MML1_DRDI_REMAP_LTE_REAL_SET_NUMS           (6) /* 1 <= MML1_DRDI_REMAP_LTE_REAL_SET_NUMS  <= MML1_CUSTOM_TOTAL_SET_NUMS */
#define MML1_DRDI_REMAP_MMRF_REAL_SET_NUMS          (1) /* 1 <= MML1_DRDI_REMAP_MMRF_REAL_SET_NUMS <= MML1_CUSTOM_TOTAL_SET_NUMS */
#else                                                   /* !!NOTE: for DRDI disable remapping table real set number should be 1  */
#define MML1_DRDI_REMAP_GGE_REAL_SET_NUMS           (1) /* DO NOT MODIFY This Part!!                                             */
#define MML1_DRDI_REMAP_UMTS_REAL_SET_NUMS          (1) /* DO NOT MODIFY This Part!!                                             */
#define MML1_DRDI_REMAP_TDS_REAL_SET_NUMS           (1) /* DO NOT MODIFY This Part!!                                             */
#define MML1_DRDI_REMAP_LTE_REAL_SET_NUMS           (1) /* DO NOT MODIFY This Part!!                                             */
#define MML1_DRDI_REMAP_MMRF_REAL_SET_NUMS          (1) /* DO NOT MODIFY This Part!!                                             */
#endif

/****************************************************************************
 * Below defines not allowed customer to modify
 * Below defines not allowed customer to modify
 * Below defines not allowed customer to modify
 * Below defines not allowed customer to modify
 * Below defines not allowed customer to modify
 ****************************************************************************/

/********************
 * ADC
 *******************/
/*****************************************************************************
* Constant    : MML1_CUSTOM_ADC_BITS
* Group       : Real target, Customization, MML1 common operation
* Description : Customization constant to be used for customer to determine
*               the bits of ADC in use
*****************************************************************************/
#define MML1_CUSTOM_ADC_BITS                            (12)      // ADC is 12 bit (1/4096) per step

/*****************************************************************************
* Constant    : MML1_CUSTOM_ADC_MAX_INPUT_VOLTAGE
* Group       : Real target, Internals, MML1 common operation
* Description : Constant to be used to determine the maximum input voltage
*               on the board, in micro volt unit
*****************************************************************************/
#define MML1_CUSTOM_ADC_MAX_INPUT_VOLTAGE               (1500000) // uV

/*****************************************************************************
* Constant    : MML1_CUSTOM_ADC_INACCURACY_MARGIN
* Group       : Real target, Customization, MML1 common operation
* Description : Customization constant to be used for customer to determine
*               the inaccuracy margin (in micro volt unit) on the board
*****************************************************************************/
#define MML1_CUSTOM_ADC_INACCURACY_MARGIN               (50000)   // uV uint

/*******************************************************************************************
* Constant    : MML1_CUSTOM_ADC_VOLT_LVL_RESOLUTION_THRESHOLD
*               MML1_CUSTOM_ADC_VOLT_LVL_STEP_SIZE
*               MML1_CUSTOM_ADC_VOLT_LVL_MIN_LVL_STEP
* Group       : Real target, Internals, MML1 common operation
* Description : Constant to be used to determine the each step level of
*               ADC voltage to level look-up table on the board, in micro volt unit
*
*               MML1_CUSTOM_ADC_VOLT_LVL_RESOLUTION_THRESHOLD - two times inaccuracy margin
*               MML1_CUSTOM_ADC_VOLT_LVL_STEP_SIZE - step size of consecutive levels
*               MML1_CUSTOM_ADC_VOLT_LVL_MIN_LVL_STEP - the first level upper bound to 0 volt
*******************************************************************************************/
#define MML1_CUSTOM_ADC_VOLT_LVL_RESOLUTION_THRESHOLD   (MML1_CUSTOM_ADC_INACCURACY_MARGIN * 2)
#define MML1_CUSTOM_ADC_VOLT_LVL_STEP_SIZE              ((MML1_CUSTOM_ADC_MAX_INPUT_VOLTAGE) / (MML1_CUSTOM_ADC_LEVEL_TOTAL - 1))
#define MML1_CUSTOM_ADC_VOLT_LVL_MIN_LVL_STEP           (MML1_CUSTOM_ADC_VOLT_LVL_STEP_SIZE / 2)



/****************************************************************************
 * Below defines not allowed customer to modify
 * Below defines not allowed customer to modify
 * Below defines not allowed customer to modify
 * Below defines not allowed customer to modify
 * Below defines not allowed customer to modify
 ****************************************************************************/

/*****************************************************************************
* Constant    : MML1_CUSTOM_BARCODE_ENABLE
* Group       : Real target, Customization, MML1 common operation
* Description : Constants to enable "Dynamic Initialization RF parameters"
*               mechanism
*****************************************************************************/
#define MML1_CUSTOM_BARCODE_ENABLE         (0) //Not support this customization Now, please not modify


/*****************************************************************************
* Constant    : MML1_CUSTOM_BARCODE_SET_NUMS
* Group       : Real target, Customization, MML1 common operation
* Description : Constants to define number of sets can be represented by each
*               mechanism respectively
*****************************************************************************/
#define MML1_CUSTOM_BARCODE_SET_NUMS       (3)


/*****************************************************************************
* Constant    : MML1_CUSTOM_THIRD_INDEX
* Group       : Real target, Customization, MML1 common operation
* Description : Constants to first, second and third index for the
*               representation in configuration set index table
*               The value can be set:                                                                      
*                  MML1_CUSTOM_NULL_ACTION_ID                                                                                                                            
*****************************************************************************/
#define MML1_CUSTOM_THIRD_INDEX            (MML1_CUSTOM_NULL_ACTION_ID)

/********************
 * NVRAM Barcode
 *******************/
/*****************************************************************************
* Constant    : MML1_CUSTOM_BARCODE_READ_DIGIT_NUM
* Group       : Real target, Customization, MML1 common operation
* Description : Customization constant to be used for customer to determine
*               the n:th digit of UE barcode to detect the RF configurations
*               n starts from 0
*****************************************************************************/
#define MML1_CUSTOM_BARCODE_READ_DIGIT_NUM   (0)

/*****************************************************************************
* Constant    : MML1_CUSTOM_BARCODE_DIGIT_VALUE_X
* Group       : Real target, Customization, MML1 common operation
* Description : Customization constant to be used for customer to determine
*               at most three (for now) kinds of ASM representation barcode
*               digit number (in ASCII) to detect the RF configurations
*****************************************************************************/
#define MML1_CUSTOM_BARCODE_DIGIT_VALUE_1    (8) // ex: for MURATA_SP7T
#define MML1_CUSTOM_BARCODE_DIGIT_VALUE_2    (7) // ex: for MURATA_SP10T
#define MML1_CUSTOM_BARCODE_DIGIT_VALUE_3    (3) // ex: for RFMD1291



/*****************************************
 * Error check pre-compile processing 
 *****************************************/
#if ( (MML1_CUSTOM_ADC_VOLT_LVL_STEP_SIZE - MML1_CUSTOM_ADC_VOLT_LVL_MIN_LVL_STEP) < MML1_CUSTOM_ADC_VOLT_LVL_RESOLUTION_THRESHOLD)
#error "Too much ADC voltage level, please decrease the defined MML1_CUSTOM_ADC_LEVEL_TOTAL value"
#endif

#if (MML1_CUSTOM_ADC_LVL1 < MML1_CUSTOM_ADC_INACCURACY_MARGIN)
#error "Too large inaccuracy error margin, please redefine MML1_CUSTOM_ADC_INACCURACY_MARGIN"
#endif


/********************
 * Common
 *******************/
/* Error check pre-compile processing to check rediculous index definitions */
#if (MML1_CUSTOM_THIRD_INDEX != MML1_CUSTOM_NULL_ACTION_ID)
   #if (MML1_CUSTOM_SECOND_INDEX == MML1_CUSTOM_NULL_ACTION_ID)  || (MML1_CUSTOM_FIRST_INDEX == MML1_CUSTOM_NULL_ACTION_ID)
   #error "Should not define MML1_CUSTOM_SECOND_INDEX or MML1_CUSTOM_FIRST_INDEX to MML1_CUSTOM_NULL_ACTION_ID while MML1_CUSTOM_THIRD_INDEX is not MML1_CUSTOM_NULL_ACTION_ID"
   #endif
#endif

#if (MML1_CUSTOM_SECOND_INDEX != MML1_CUSTOM_NULL_ACTION_ID)
   #if (MML1_CUSTOM_FIRST_INDEX == MML1_CUSTOM_NULL_ACTION_ID)
   #error "Should not define MML1_CUSTOM_FIRST_INDEX to MML1_CUSTOM_NULL_ACTION_ID while MML1_CUSTOM_SECOND_INDEX is not MML1_CUSTOM_NULL_ACTION_ID"
   #endif
#endif

/* Define the first index base */
#if (MML1_CUSTOM_FIRST_INDEX == MML1_CUSTOM_NULL_ACTION_ID)
   #if (MML1_CUSTOM_SECOND_INDEX == MML1_CUSTOM_NULL_ACTION_ID) && (MML1_CUSTOM_THIRD_INDEX == MML1_CUSTOM_NULL_ACTION_ID)
   #define MML1_CUSTOM_FIRST_INDEX_BASE  (0)
   #else
   #error "MML1_CUSTOM_FIRST_INDEX can not be defined to MML1_CUSTOM_NULL_ACTION_ID while either MML1_CUSTOM_SECOND_INDEX or MML1_CUSTOM_THIRD_INDEX not MML1_CUSTOM_NULL_ACTION_ID"
   #endif 
#else
   #define MML1_CUSTOM_FIRST_INDEX_BASE  (1)
#endif

/********************
 * GPIO
 *******************/
/*****************************************************************************
* Constant    : MML1_CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE
* Group       : Real target, Customization, MML1 common operation
* Description : Customization constant to be used for customer to determine
*               the number of GPIO detection pins in use
*****************************************************************************/
//#define MML1_CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE    (2)

/* Define MML1_CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE by Compile time Check */
#if (MML1_CUSTOM_GPIO_SET_NUMS > 8)
   #error "MML1_CUSTOM_GPIO_SET_NUMS should <= 8"
#elif (MML1_CUSTOM_GPIO_SET_NUMS < 0)
   #error "MML1_CUSTOM_GPIO_SET_NUMS should >= 0"
#else
  #if (MML1_CUSTOM_GPIO_SET_NUMS == 0)
     #define MML1_CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE    (0)
  #elif (MML1_CUSTOM_GPIO_SET_NUMS <= 2)
     #define MML1_CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE    (1)
  #elif (MML1_CUSTOM_GPIO_SET_NUMS <= 4)
     #define MML1_CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE    (2)
  #else
     #define MML1_CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE    (3)
  #endif
#endif

/*****************************************************************************
* Constant    : MML1_CUSTOM_GPIO_NUMS_IN_CALC
*               MML1_CUSTOM_ADC_NUMS_IN_CALC
*               MML1_CUSTOM_BARCODE_NUMS_IN_CALC
* Group       : Real target, Internals, MML1 common operation
* Description : Constant for the second and third index base to be calculated
*****************************************************************************/
#if MML1_CUSTOM_GPIO_ENABLE
#define MML1_CUSTOM_GPIO_NUMS_IN_CALC      (MML1_CUSTOM_GPIO_SET_NUMS)
#else
#define MML1_CUSTOM_GPIO_NUMS_IN_CALC      (1)
#endif

#if MML1_CUSTOM_ADC_ENABLE
#define MML1_CUSTOM_ADC_NUMS_IN_CALC       (MML1_CUSTOM_ADC_SET_NUMS)
#else
#define MML1_CUSTOM_ADC_NUMS_IN_CALC       (1)
#endif

#if MML1_CUSTOM_BARCODE_ENABLE
#define MML1_CUSTOM_BARCODE_NUMS_IN_CALC   (MML1_CUSTOM_BARCODE_SET_NUMS)
#else
#define MML1_CUSTOM_BARCODE_NUMS_IN_CALC   (1)
#endif

/*****************************************************************************
* Constant    : MML1_CUSTOM_TOTAL_SET_NUMS
* Group       : Real target, Internals, MML1 common operation
* Description : Constant to be used as the number of total configuration sets
*****************************************************************************/
#define MML1_CUSTOM_TOTAL_SET_NUMS         (MML1_CUSTOM_GPIO_NUMS_IN_CALC * MML1_CUSTOM_ADC_NUMS_IN_CALC * MML1_CUSTOM_BARCODE_NUMS_IN_CALC)

/*****************************************************************************
* Constant    : MML1_CUSTOM_ADC_CALIBRATE_ENABLE
* Group       : Real target, Customization, MML1 common operation
* Description : Customization constant to be used for customer to determine
*               if AuxADC calibration is enabled or not
*****************************************************************************/
#define MML1_CUSTOM_ADC_CALIBRATE_ENABLE   (0)

/******************************************************************************
 * Data structure definition
 ******************************************************************************/

/******************************************************************************
 * Function Prototypes
 ******************************************************************************/
void MML1_CUSTOM_GPIO_NON_SMART_PHONE_PIN_ACCESS(kal_int16 *gpio_pin);
void MML1_CUSTOM_ADC_PIN_ACCESS(kal_uint16 *adc_channel_num);
void MML1_CUSTOM_ADC_PARAM(kal_uint16 *adcMeasCountOrder, kal_uint32 *adcMaxVolt, kal_uint16 *adcBit);
void MML1_CUSTOM_GET_CUSTOM_PARAM(Mml1RfDrdiCustomParam *drdiCustomParam);


/****************************************************************************
 * Custom Data Extern
 ****************************************************************************/
#if (IS_MML1_DRDI_ENABLE&&IS_MML1_DRDI_REMAP_ENABLE)
extern Mml1CustomDynamicInitMipiData *mml1_mipiData_array_ptr[MML1_DRDI_REMAP_MMRF_REAL_SET_NUMS];
extern Mml1CustomDynamicInitMipiData  mml1CustomMipiData[MML1_DRDI_REMAP_MMRF_REAL_SET_NUMS];
#else
extern Mml1CustomDynamicInitMipiData *mml1_mipiData_array_ptr[MML1_CUSTOM_TOTAL_SET_NUMS];
extern Mml1CustomDynamicInitMipiData  mml1CustomMipiData[MML1_CUSTOM_TOTAL_SET_NUMS];
#endif

#if defined(__EXT_VRF18_BUCK_SUPPORT__)
   #if (IS_MML1_DRDI_ENABLE && IS_MML1_DRDI_REMAP_ENABLE)
extern Mml1CustomDynamicInitPmicData *mml1_pmicData_array_ptr[MML1_DRDI_REMAP_MMRF_REAL_SET_NUMS];
extern Mml1CustomDynamicInitPmicData  mml1CustomPmicData[MML1_DRDI_REMAP_MMRF_REAL_SET_NUMS];
   #else
extern Mml1CustomDynamicInitPmicData *mml1_pmicData_array_ptr[MML1_CUSTOM_TOTAL_SET_NUMS];
extern Mml1CustomDynamicInitPmicData  mml1CustomPmicData[MML1_CUSTOM_TOTAL_SET_NUMS];
   #endif
#endif

/****************************************************************************
 * Remapping Table Data Extern
 ****************************************************************************/
extern kal_uint16 MML1_DRDI_GGE_ReMapTable[MML1_CUSTOM_TOTAL_SET_NUMS];
extern kal_uint16 MML1_DRDI_UMTS_ReMapTable[MML1_CUSTOM_TOTAL_SET_NUMS];
extern kal_uint16 MML1_DRDI_TDS_ReMapTable[MML1_CUSTOM_TOTAL_SET_NUMS];
extern kal_uint16 MML1_DRDI_LTE_ReMapTable[MML1_CUSTOM_TOTAL_SET_NUMS];
extern kal_uint16 MML1_DRDI_MMRF_ReMapTable[MML1_CUSTOM_TOTAL_SET_NUMS];

#endif /* _MML1_CUSTOM_DRDI_H_*/
