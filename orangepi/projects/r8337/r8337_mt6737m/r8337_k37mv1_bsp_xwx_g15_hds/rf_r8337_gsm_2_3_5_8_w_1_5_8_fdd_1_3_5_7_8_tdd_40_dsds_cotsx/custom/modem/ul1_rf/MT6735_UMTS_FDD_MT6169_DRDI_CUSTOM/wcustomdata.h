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
 *	wcustomdata.h
 *
 * Project:
 * --------
 *  Dual Mode 3G Project
 *
 * Description:
 * ------------
 *  Dynamic RF parameters custom macro definitions
 *
 * Author:
 * -------
 * -------
 *
 *******************************************************************************/

#ifndef  _WCUSTOMDATA_H_
#define  _WCUSTOMDATA_H_


/*******************************************************************************
** Includes
*******************************************************************************/
#include "kal_general_types.h"
#include "ul1cal.h"

#include "mml1_custom_drdi.h"
/****************************************************************************
 * Csutomization Defines
 ****************************************************************************/

/********************
 * Common
 *******************/
/*****************************************************************************
* Constant    : UL1CUSTOM_SMART_PHONE_ENABLE
* Group       : Real target, Customization, UL1D common operation
* Description : Constant to seperate SMART_PHONE / FEATURE_PHONE / DATACARD
*               for common modem image feature
* Examples	  : MT6575/6577, MT6589/6572/6582
*				UL1CUSTOM_SMART_PHONE_ENABLE	(1)
* 				MT6280
*				UL1CUSTOM_SMART_PHONE_ENABLE	(0)
*****************************************************************************/
#define UL1CUSTOM_SMART_PHONE_ENABLE	MML1_CUSTOM_SMART_PHONE_ENABLE

/*****************************************************************************
* Constant    : UL1CUSTOM_GPIO_ENABLE
*               UL1CUSTOM_ADC_ENABLE
*               UL1CUSTOM_NVRAM_BARCODE_ENABLE
* Group       : Real target, Customization, UL1D common operation
* Description : Constants to enable "Dynamic Initialization RF parameters"
*               mechanism
*****************************************************************************/
#define UL1CUSTOM_GPIO_ENABLE           MML1_CUSTOM_GPIO_ENABLE
#define UL1CUSTOM_ADC_ENABLE            MML1_CUSTOM_ADC_ENABLE
#define UL1CUSTOM_NVRAM_BARCODE_ENABLE  MML1_CUSTOM_BARCODE_ENABLE

/*****************************************************************************
* Constant    : UL1CUSTOM_GPIO_SET_NUMS
*               UL1CUSTOM_ADC_SET_NUMS
*               UL1CUSTOM_NVRAM_BARCODE_SET_NUMS
* Group       : Real target, Customization, UL1D common operation
* Description : Constants to define number of sets can be represented by each
*               mechanism respectively
*****************************************************************************/
#define UL1CUSTOM_GPIO_SET_NUMS           MML1_CUSTOM_GPIO_SET_NUMS
#define UL1CUSTOM_ADC_SET_NUMS            MML1_CUSTOM_ADC_SET_NUMS
#define UL1CUSTOM_NVRAM_BARCODE_SET_NUMS  MML1_CUSTOM_BARCODE_SET_NUMS

/*****************************************************************************
* Constant    : UL1CUSTOM_FIRST_INDEX
*               UL1CUSTOM_SECOND_INDEX
*               UL1CUSTOM_THIRD_INDEX
* Group       : Real target, Customization, UL1D common operation
* Description : Constants to first, second and third index for the
*               representation in configuration set index table
*****************************************************************************/
#define UL1CUSTOM_FIRST_INDEX   MML1_CUSTOM_FIRST_INDEX
#define UL1CUSTOM_SECOND_INDEX  MML1_CUSTOM_SECOND_INDEX
#define UL1CUSTOM_THIRD_INDEX   MML1_CUSTOM_THIRD_INDEX

/*****************************************************************************
* Constant    : UL1CUSTOM_DEBUG_ENABLE
* Group       : Real target, Customization, UL1D common operation
* Description : Constants to enable "Dynamic Initialization RF parameters"
*               debug info logging, which will save debug info into NVRAM
*               LID NVRAM_EF_UL1_CUSTOM_DYNAMIC_INIT_DEBUG_LID
*****************************************************************************/
#define UL1CUSTOM_DEBUG_ENABLE  MML1_CUSTOM_DEBUG_ENABLE


/********************
 * GPIO
 *******************/
/*****************************************************************************
* Constant    : UL1CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE
* Group       : Real target, Customization, UL1D common operation
* Description : Customization constant to be used for customer to determine
*               the number of GPIO detection pins in use
*****************************************************************************/
#define UL1CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE  MML1_CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE

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
* Constant    : UL1CUSTOM_ADC_LEVEL_TOTAL
* Group       : Real target, Customization, UL1D common operation
* Description : Customization constant to be used for customer to determine
*               number of ADC levels to be used to distinguish between the
*               RF HW configurations
*****************************************************************************/
#define UL1CUSTOM_ADC_LEVEL_TOTAL        MML1_CUSTOM_ADC_LEVEL_TOTAL

/*****************************************************************************
* Constant    : UL1CUSTOM_ADC_MEAS_COUNT_2_ORDER
* Group       : Real target, Customization, UL1D common operation
* Description : Customization constant to be used for customer to determine
*               number of ADC channel measurement counts (in 2's order)
*               ex: 7 => 2^7 = 128
*****************************************************************************/
#define UL1CUSTOM_ADC_MEAS_COUNT_2_ORDER  MML1_CUSTOM_ADC_MEAS_COUNT_2_ORDER //2^7 = 128

#define UL1CUSTOM_ADC_LVL0  MML1_CUSTOM_ADC_LVL0
#define UL1CUSTOM_ADC_LVL1  MML1_CUSTOM_ADC_LVL1
#define UL1CUSTOM_ADC_LVL2  MML1_CUSTOM_ADC_LVL2
#define UL1CUSTOM_ADC_LVL3  MML1_CUSTOM_ADC_LVL3
#define UL1CUSTOM_ADC_LVL4  MML1_CUSTOM_ADC_LVL4
#define UL1CUSTOM_ADC_LVL5  MML1_CUSTOM_ADC_LVL5
#define UL1CUSTOM_ADC_LVL6  MML1_CUSTOM_ADC_LVL6

/********************
 * NVRAM Barcode
 *******************/
/*****************************************************************************
* Constant    : UL1CUSTOM_BARCODE_READ_DIGIT_NUM
* Group       : Real target, Customization, UL1D common operation
* Description : Customization constant to be used for customer to determine
*               the n:th digit of UE barcode to detect the RF configurations
*               n starts from 0
*****************************************************************************/
#define UL1CUSTOM_BARCODE_READ_DIGIT_NUM  MML1_CUSTOM_BARCODE_READ_DIGIT_NUM

/*****************************************************************************
* Constant    : UL1CUSTOM_BARCODE_DIGIT_VALUE_X
* Group       : Real target, Customization, UL1D common operation
* Description : Customization constant to be used for customer to determine
*               at most three (for now) kinds of ASM representation barcode
*               digit number (in ASCII) to detect the RF configurations
*****************************************************************************/
#define UL1CUSTOM_BARCODE_DIGIT_VALUE_1   MML1_CUSTOM_BARCODE_DIGIT_VALUE_1 // ex: for MURATA_SP7T
#define UL1CUSTOM_BARCODE_DIGIT_VALUE_2   MML1_CUSTOM_BARCODE_DIGIT_VALUE_2 // ex: for MURATA_SP10T
#define UL1CUSTOM_BARCODE_DIGIT_VALUE_3   MML1_CUSTOM_BARCODE_DIGIT_VALUE_3 // ex: for RFMD1291

/****************************************************************************
 * Custom Data Extern
 ****************************************************************************/





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
* Constant    : UL1CUSTOM_ADC_BITS
* Group       : Real target, Customization, UL1D common operation
* Description : Customization constant to be used for customer to determine
*               the bits of ADC in use
*****************************************************************************/
#define UL1CUSTOM_ADC_BITS                MML1_CUSTOM_ADC_BITS // MT6573/MT6575 ADC is 12 bit (1/4096) per step

/*****************************************************************************
* Constant    : UL1CUSTOM_ADC_MAX_INPUT_VOLTAGE
* Group       : Real target, Internals, UL1D common operation
* Description : Constant to be used to determine the maximum input voltage
*               on the board, in micro volt unit
*****************************************************************************/
#define UL1CUSTOM_ADC_MAX_INPUT_VOLTAGE   MML1_CUSTOM_ADC_MAX_INPUT_VOLTAGE // uV unit

/*****************************************************************************
* Constant    : UL1CUSTOM_ADC_INACCURACY_MARGIN
* Group       : Real target, Customization, UL1D common operation
* Description : Customization constant to be used for customer to determine
*               the inaccuracy margin (in micro volt unit) on the board
*****************************************************************************/
#define UL1CUSTOM_ADC_INACCURACY_MARGIN   MML1_CUSTOM_ADC_INACCURACY_MARGIN // uV uint

/*******************************************************************************************
* Constant    : UL1CUSTOM_ADC_VOLT_LVL_RESOLUTION_THRESHOLD
*               UL1CUSTOM_ADC_VOLT_LVL_STEP_SIZE
*               UL1CUSTOM_ADC_VOLT_LVL_MIN_LVL_STEP
* Group       : Real target, Internals, UL1D common operation
* Description : Constant to be used to determine the each step level of
*               ADC voltage to level look-up table on the board, in micro volt unit
*
*               UL1CUSTOM_ADC_VOLT_LVL_RESOLUTION_THRESHOLD - two times inaccuracy margin
*               UL1CUSTOM_ADC_VOLT_LVL_STEP_SIZE - step size of consecutive levels
*               UL1CUSTOM_ADC_VOLT_LVL_MIN_LVL_STEP - the first level upper bound to 0 volt
*******************************************************************************************/
#define UL1CUSTOM_ADC_VOLT_LVL_RESOLUTION_THRESHOLD  MML1_CUSTOM_ADC_VOLT_LVL_RESOLUTION_THRESHOLD
#define UL1CUSTOM_ADC_VOLT_LVL_STEP_SIZE             MML1_CUSTOM_ADC_VOLT_LVL_STEP_SIZE
#define UL1CUSTOM_ADC_VOLT_LVL_MIN_LVL_STEP          MML1_CUSTOM_ADC_VOLT_LVL_MIN_LVL_STEP


/* Error check pre-compile processing */
/* Error check pre-compile processing */
/* Error check pre-compile processing */
#if ( (UL1CUSTOM_ADC_VOLT_LVL_STEP_SIZE - UL1CUSTOM_ADC_VOLT_LVL_MIN_LVL_STEP) < UL1CUSTOM_ADC_VOLT_LVL_RESOLUTION_THRESHOLD)
#error "Too much ADC voltage level, please decrease the defined UL1CUSTOM_ADC_LEVEL_TOTAL value"
#endif

#if (UL1CUSTOM_ADC_LVL1 < UL1CUSTOM_ADC_INACCURACY_MARGIN)
#error "Too large inaccuracy error margin, please redefine UL1CUSTOM_ADC_INACCURACY_MARGIN"
#endif

/********************
 * Common
 *******************/
/* Error check pre-compile processing to check rediculous index definitions */
#if (UL1CUSTOM_THIRD_INDEX != UL1CUSTOM_NULL_ACTION)
   #if (UL1CUSTOM_SECOND_INDEX == UL1CUSTOM_NULL_ACTION)  || (UL1CUSTOM_FIRST_INDEX == UL1CUSTOM_NULL_ACTION)
   #error "Should not define UL1CUSTOM_SECOND_INDEX or UL1CUSTOM_FIRST_INDEX to UL1CUSTOM_NULL_ACTION_ID while UL1CUSTOM_THIRD_INDEX is not UL1CUSTOM_NULL_ACTION_ID"
   #endif
#endif

#if (UL1CUSTOM_SECOND_INDEX != UL1CUSTOM_NULL_ACTION)
   #if (UL1CUSTOM_FIRST_INDEX == UL1CUSTOM_NULL_ACTION)
   #error "Should not define UL1CUSTOM_FIRST_INDEX to UL1CUSTOM_NULL_ACTION_ID while UL1CUSTOM_SECOND_INDEX is not UL1CUSTOM_NULL_ACTION_ID"
   #endif
#endif

/* Define the first index base */
#if (UL1CUSTOM_FIRST_INDEX == UL1CUSTOM_NULL_ACTION)
   #if (UL1CUSTOM_SECOND_INDEX == UL1CUSTOM_NULL_ACTION) && (UL1CUSTOM_THIRD_INDEX == UL1CUSTOM_NULL_ACTION)
   #define UL1CUSTOM_FIRST_INDEX_BASE  MML1_CUSTOM_FIRST_INDEX_BASE
   #else
   #error "UL1CUSTOM_FIRST_INDEX can not be defined to UL1CUSTOM_NULL_ACTION_ID while either UL1CUSTOM_SECOND_INDEX or UL1CUSTOM_THIRD_INDEX not UL1CUSTOM_NULL_ACTION_ID"
   #endif 
#else
   #define UL1CUSTOM_FIRST_INDEX_BASE  MML1_CUSTOM_FIRST_INDEX_BASE
#endif

/*****************************************************************************
* Constant    : UL1CUSTOM_GPIO_NUMS_IN_CALC
*               UL1CUSTOM_ADC_NUMS_IN_CALC
*               UL1CUSTOM_NVRAM_BARCODE_NUMS_IN_CALC
* Group       : Real target, Internals, UL1D common operation
* Description : Constant for the second and third index base to be calculated
*****************************************************************************/
#define UL1CUSTOM_GPIO_NUMS_IN_CALC            MML1_CUSTOM_GPIO_NUMS_IN_CALC

#define UL1CUSTOM_ADC_NUMS_IN_CALC             MML1_CUSTOM_ADC_NUMS_IN_CALC

#define UL1CUSTOM_NVRAM_BARCODE_NUMS_IN_CALC   MML1_CUSTOM_BARCODE_NUMS_IN_CALC

/*****************************************************************************
* Constant    : UL1CUSTOM_TOTAL_SET_NUMS
* Group       : Real target, Internals, UL1D common operation
* Description : Constant to be used as the number of total configuration sets
*****************************************************************************/

#define UL1CUSTOM_TOTAL_REAL_SET_NUMS  MML1_DRDI_REMAP_UMTS_REAL_SET_NUMS
#define UL1CUSTOM_TOTAL_SET_NUMS       MML1_CUSTOM_TOTAL_SET_NUMS

/*****************************************************************************
* Constant    : UL1CUSTOM_ADC_CALIBARTE_ENABLE
* Group       : Real target, Customization, UL1D common operation
* Description : Customization constant to be used for customer to determine
*               if AuxADC calibration is enabled or not
*****************************************************************************/
#define UL1CUSTOM_ADC_CALIBARTE_ENABLE    MML1_CUSTOM_ADC_CALIBRATE_ENABLE


/****************************************************************************
 * Function Prototypes
 ****************************************************************************/
void UL1CUSTOM_ReplaceAuxAdcCalibrate(kal_uint32 adcDigitalValue, kal_int32 *volt);


/***************************************************************************
 * Global Function extern
 ***************************************************************************/
extern void UL1CUSTOM_DynamicInitByGPIO(void *data);
extern void UL1CUSTOM_DynamicInitByADC(void *data);
extern void UL1CUSTOM_DynamicInitByNvramBarcode(void *data);
extern void UL1CUSTOM_GPIO_NON_SMART_PHONE_PIN_ACCESS(kal_int16 *gpio_pin);

#endif /* _WCUSTOMDATA_H_ */

