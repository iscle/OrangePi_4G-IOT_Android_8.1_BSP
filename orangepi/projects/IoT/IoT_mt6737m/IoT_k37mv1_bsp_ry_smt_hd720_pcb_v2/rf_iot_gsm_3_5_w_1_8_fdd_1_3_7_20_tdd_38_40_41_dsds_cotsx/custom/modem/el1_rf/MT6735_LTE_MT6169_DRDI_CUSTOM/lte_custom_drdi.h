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
 *  lte_custom_drdi.h
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

#ifndef  _LTE_CUSTOM_DRDI_H_
#define  _LTE_CUSTOM_DRDI_H_


/*******************************************************************************
** Includes
*******************************************************************************/
#include "kal_general_types.h"
#include "el1_drdi.h"
#include "mml1_custom_drdi.h"

/*********************************************************************/ 
/* Dynamic Radio-setting Dedicated Images                            */ 
/* Let DRDI GPIO/ADC/Barcode BPI setting overwrite NVRAN BPI setting */ 
/*********************************************************************/
#define EL1_CUSTOM_DYNAMIC_INIT_ENABLE        1    //Enable LTE DRDI on K52 COMBO

/*----------------------------------------------*/
/*   One-bin Support Definition                 */
/*----------------------------------------------*/
/* Constants to enable "Dynamic Initialization RF parameters" mechanism                 */
//#define EL1_CUSTOM_DYNAMIC_SUPPORT        (EL1_CUSTOM_GPIO_ENABLE|EL1_CUSTOM_ADC_ENABLE|EL1_CUSTOM_BARCODE_ENABLE)
//NEED TO MODIFY

/********************
 * Common
 *******************/
/*****************************************************************************
* Constant    : EL1_CUSTOM_SMART_PHONE_ENABLE
* Group       : Real target, Customization, EL1 common operation
* Description : Constant to seperate SMART_PHONE / FEATURE_PHONE / DATACARD
*               for common modem image feature
* Examples	  : MT6575/6577, MT6589/6572/6582
*				EL1_CUSTOM_SMART_PHONE_ENABLE	(1)
* 				MT6290
*				EL1_CUSTOM_SMART_PHONE_ENABLE	(0)
*****************************************************************************/
#define EL1_CUSTOM_SMART_PHONE_ENABLE     MML1_CUSTOM_SMART_PHONE_ENABLE
//NEED TO MODIFY

/*****************************************************************************
* Constant    : EL1_CUSTOM_GPIO_ENABLE
*               EL1_CUSTOM_ADC_ENABLE
*               EL1_CUSTOM_BARCODE_ENABLE
* Group       : Real target, Customization, EL1 common operation
* Description : Constants to enable "Dynamic Initialization RF parameters"
*               mechanism
*****************************************************************************/
#define EL1_CUSTOM_GPIO_ENABLE            MML1_CUSTOM_GPIO_ENABLE
#define EL1_CUSTOM_ADC_ENABLE             MML1_CUSTOM_ADC_ENABLE
#define EL1_CUSTOM_BARCODE_ENABLE         MML1_CUSTOM_BARCODE_ENABLE


/*****************************************************************************
* Constant    : EL1_CUSTOM_GPIO_SET_NUMS
*               EL1_CUSTOM_ADC_SET_NUMS
*               EL1_CUSTOM_BARCODE_SET_NUMS
* Group       : Real target, Customization, EL1 common operation
* Description : Constants to define number of sets can be represented by each
*               mechanism respectively
*****************************************************************************/
#define EL1_CUSTOM_GPIO_SET_NUMS          MML1_CUSTOM_GPIO_SET_NUMS
#define EL1_CUSTOM_ADC_SET_NUMS           MML1_CUSTOM_ADC_SET_NUMS
#define EL1_CUSTOM_BARCODE_SET_NUMS       MML1_CUSTOM_BARCODE_SET_NUMS


/*****************************************************************************
* Constant    : EL1_CUSTOM_FIRST_INDEX
*               EL1_CUSTOM_SECOND_INDEX
*               EL1_CUSTOM_THIRD_INDEX
* Group       : Real target, Customization, EL1 common operation
* Description : Constants to first, second and third index for the
*               representation in configuration set index table
*               The value can be set:                                                                      
*                  EL1_CUSTOM_NULL_ACTION_ID                                                                     
*                  EL1_CUSTOM_GPIO_DETECTION_ID                                                               
*                  EL1_CUSTOM_ADC_DETECTION_ID                                                                
*                  EL1_CUSTOM_BARCODE_DETECTION_ID                                                            
*****************************************************************************/
#define EL1_CUSTOM_FIRST_INDEX            MML1_CUSTOM_FIRST_INDEX
#define EL1_CUSTOM_SECOND_INDEX           MML1_CUSTOM_SECOND_INDEX
#define EL1_CUSTOM_THIRD_INDEX            MML1_CUSTOM_THIRD_INDEX

/*****************************************************************************
* Constant    : EL1_CUSTOM_DEBUG_ENABLE
* Group       : Real target, Customization, EL1 common operation
* Description : Constants to enable "Dynamic Initialization RF parameters"
*               debug info logging
*               For trace output to debug ( EL1_CustomDynamicDebug() )
*****************************************************************************/
#define EL1_CUSTOM_DEBUG_ENABLE           MML1_CUSTOM_DEBUG_ENABLE



/********************
 * GPIO
 *******************/
/*****************************************************************************
* Constant    : EL1_CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE
* Group       : Real target, Customization, EL1 common operation
* Description : Customization constant to be used for customer to determine
*               the number of GPIO detection pins in use
*****************************************************************************/
#define EL1_CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE    MML1_CUSTOM_GPIO_NUM_OF_DETECT_PINS_IN_USE





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
* Constant    : EL1_CUSTOM_ADC_LEVEL_TOTAL
* Group       : Real target, Customization, EL1 common operation
* Description : Customization constant to be used for customer to determine
*               number of ADC levels to be used to distinguish between the
*               RF HW configurations
*****************************************************************************/
#define EL1_CUSTOM_ADC_LEVEL_TOTAL          MML1_CUSTOM_ADC_LEVEL_TOTAL

/*****************************************************************************
* Constant    : EL1_CUSTOM_ADC_MEAS_COUNT_2_ORDER
* Group       : Real target, Customization, EL1 common operation
* Description : Customization constant to be used for customer to determine
*               number of ADC channel measurement counts (in 2's order)
*               ex: 7 => 2^7 = 128
*****************************************************************************/
#define EL1_CUSTOM_ADC_MEAS_COUNT_2_ORDER   MML1_CUSTOM_ADC_MEAS_COUNT_2_ORDER

#define EL1_CUSTOM_ADC_LVL0                 MML1_CUSTOM_ADC_LVL0
#define EL1_CUSTOM_ADC_LVL1                 MML1_CUSTOM_ADC_LVL1
#define EL1_CUSTOM_ADC_LVL2                 MML1_CUSTOM_ADC_LVL2
#define EL1_CUSTOM_ADC_LVL3                 MML1_CUSTOM_ADC_LVL3
#define EL1_CUSTOM_ADC_LVL4                 MML1_CUSTOM_ADC_LVL4
#define EL1_CUSTOM_ADC_LVL5                 MML1_CUSTOM_ADC_LVL5
#define EL1_CUSTOM_ADC_LVL6                 MML1_CUSTOM_ADC_LVL6

/********************
 * NVRAM Barcode
 *******************/
/*****************************************************************************
* Constant    : EL1_CUSTOM_BARCODE_READ_DIGIT_NUM
* Group       : Real target, Customization, EL1 common operation
* Description : Customization constant to be used for customer to determine
*               the n:th digit of UE barcode to detect the RF configurations
*               n starts from 0
*****************************************************************************/
#define EL1_CUSTOM_BARCODE_READ_DIGIT_NUM   MML1_CUSTOM_BARCODE_READ_DIGIT_NUM

/*****************************************************************************
* Constant    : EL1_CUSTOM_BARCODE_DIGIT_VALUE_X
* Group       : Real target, Customization, EL1 common operation
* Description : Customization constant to be used for customer to determine
*               at most three (for now) kinds of ASM representation barcode
*               digit number (in ASCII) to detect the RF configurations
*****************************************************************************/
#define EL1_CUSTOM_BARCODE_DIGIT_VALUE_1    MML1_CUSTOM_BARCODE_DIGIT_VALUE_1
#define EL1_CUSTOM_BARCODE_DIGIT_VALUE_2    MML1_CUSTOM_BARCODE_DIGIT_VALUE_2
#define EL1_CUSTOM_BARCODE_DIGIT_VALUE_3    MML1_CUSTOM_BARCODE_DIGIT_VALUE_3

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
* Constant    : EL1_CUSTOM_ADC_BITS
* Group       : Real target, Customization, EL1 common operation
* Description : Customization constant to be used for customer to determine
*               the bits of ADC in use
*****************************************************************************/
#define EL1_CUSTOM_ADC_BITS                            MML1_CUSTOM_ADC_BITS                // ADC is 12 bit (1/4096) per step

/*****************************************************************************
* Constant    : EL1_CUSTOM_ADC_MAX_INPUT_VOLTAGE
* Group       : Real target, Internals, EL1 common operation
* Description : Constant to be used to determine the maximum input voltage
*               on the board, in micro volt unit
*****************************************************************************/
#define EL1_CUSTOM_ADC_MAX_INPUT_VOLTAGE               MML1_CUSTOM_ADC_MAX_INPUT_VOLTAGE   // uV

/*****************************************************************************
* Constant    : EL1_CUSTOM_ADC_INACCURACY_MARGIN
* Group       : Real target, Customization, EL1 common operation
* Description : Customization constant to be used for customer to determine
*               the inaccuracy margin (in micro volt unit) on the board
*****************************************************************************/
#define EL1_CUSTOM_ADC_INACCURACY_MARGIN               MML1_CUSTOM_ADC_INACCURACY_MARGIN   // uV uint

/*******************************************************************************************
* Constant    : EL1_CUSTOM_ADC_VOLT_LVL_RESOLUTION_THRESHOLD
*               EL1_CUSTOM_ADC_VOLT_LVL_STEP_SIZE
*               EL1_CUSTOM_ADC_VOLT_LVL_MIN_LVL_STEP
* Group       : Real target, Internals, EL1 common operation
* Description : Constant to be used to determine the each step level of
*               ADC voltage to level look-up table on the board, in micro volt unit
*
*               EL1_CUSTOM_ADC_VOLT_LVL_RESOLUTION_THRESHOLD - two times inaccuracy margin
*               EL1_CUSTOM_ADC_VOLT_LVL_STEP_SIZE - step size of consecutive levels
*               EL1_CUSTOM_ADC_VOLT_LVL_MIN_LVL_STEP - the first level upper bound to 0 volt
*******************************************************************************************/
#define EL1_CUSTOM_ADC_VOLT_LVL_RESOLUTION_THRESHOLD   (EL1_CUSTOM_ADC_INACCURACY_MARGIN * 2)
#define EL1_CUSTOM_ADC_VOLT_LVL_STEP_SIZE              ((EL1_CUSTOM_ADC_MAX_INPUT_VOLTAGE) / (EL1_CUSTOM_ADC_LEVEL_TOTAL - 1))
#define EL1_CUSTOM_ADC_VOLT_LVL_MIN_LVL_STEP           (EL1_CUSTOM_ADC_VOLT_LVL_STEP_SIZE / 2)




/*****************************************
 * Error check pre-compile processing 
 *****************************************/
#if ( (EL1_CUSTOM_ADC_VOLT_LVL_STEP_SIZE - EL1_CUSTOM_ADC_VOLT_LVL_MIN_LVL_STEP) < EL1_CUSTOM_ADC_VOLT_LVL_RESOLUTION_THRESHOLD)
#error "Too much ADC voltage level, please decrease the defined EL1_CUSTOM_ADC_LEVEL_TOTAL value"
#endif

#if (EL1_CUSTOM_ADC_LVL1 < EL1_CUSTOM_ADC_INACCURACY_MARGIN)
#error "Too large inaccuracy error margin, please redefine EL1_CUSTOM_ADC_INACCURACY_MARGIN"
#endif


/********************
 * Common
 *******************/
/* Error check pre-compile processing to check rediculous index definitions */
#if (EL1_CUSTOM_THIRD_INDEX != EL1_CUSTOM_NULL_ACTION_ID)
   #if (EL1_CUSTOM_SECOND_INDEX == EL1_CUSTOM_NULL_ACTION_ID)  || (EL1_CUSTOM_FIRST_INDEX == EL1_CUSTOM_NULL_ACTION_ID)
   #error "Should not define EL1_CUSTOM_SECOND_INDEX or EL1_CUSTOM_FIRST_INDEX to EL1_CUSTOM_NULL_ACTION_ID while EL1_CUSTOM_THIRD_INDEX is not EL1_CUSTOM_NULL_ACTION_ID"
   #endif
#endif

#if (EL1_CUSTOM_SECOND_INDEX != EL1_CUSTOM_NULL_ACTION_ID)
   #if (EL1_CUSTOM_FIRST_INDEX == EL1_CUSTOM_NULL_ACTION_ID)
   #error "Should not define EL1_CUSTOM_FIRST_INDEX to EL1_CUSTOM_NULL_ACTION_ID while EL1_CUSTOM_SECOND_INDEX is not EL1_CUSTOM_NULL_ACTION_ID"
   #endif
#endif

/* Define the first index base */
#if (EL1_CUSTOM_FIRST_INDEX == EL1_CUSTOM_NULL_ACTION_ID)
   #if (EL1_CUSTOM_SECOND_INDEX == EL1_CUSTOM_NULL_ACTION_ID) && (EL1_CUSTOM_THIRD_INDEX == EL1_CUSTOM_NULL_ACTION_ID)
   #define EL1_CUSTOM_FIRST_INDEX_BASE  (0)
   #else
   #error "EL1_CUSTOM_FIRST_INDEX can not be defined to EL1_CUSTOM_NULL_ACTION_ID while either EL1_CUSTOM_SECOND_INDEX or EL1_CUSTOM_THIRD_INDEX not EL1_CUSTOM_NULL_ACTION_ID"
   #endif 
#else
   #define EL1_CUSTOM_FIRST_INDEX_BASE  (1)
#endif

 


/*****************************************************************************
* Constant    : EL1_CUSTOM_GPIO_NUMS_IN_CALC
*               EL1_CUSTOM_ADC_NUMS_IN_CALC
*               EL1_CUSTOM_BARCODE_NUMS_IN_CALC
* Group       : Real target, Internals, EL1 common operation
* Description : Constant for the second and third index base to be calculated
*****************************************************************************/
#if EL1_CUSTOM_GPIO_ENABLE
#define EL1_CUSTOM_GPIO_NUMS_IN_CALC      (EL1_CUSTOM_GPIO_SET_NUMS)
#else
#define EL1_CUSTOM_GPIO_NUMS_IN_CALC      (1)
#endif

#if EL1_CUSTOM_ADC_ENABLE
#define EL1_CUSTOM_ADC_NUMS_IN_CALC       (EL1_CUSTOM_ADC_SET_NUMS)
#else
#define EL1_CUSTOM_ADC_NUMS_IN_CALC       (1)
#endif

#if EL1_CUSTOM_BARCODE_ENABLE
#define EL1_CUSTOM_BARCODE_NUMS_IN_CALC   (EL1_CUSTOM_BARCODE_SET_NUMS)
#else
#define EL1_CUSTOM_BARCODE_NUMS_IN_CALC   (1)
#endif

/*****************************************************************************
* Constant    : EL1_CUSTOM_TOTAL_SET_NUMS
* Group       : Real target, Internals, EL1 common operation
* Description : Constant to be used as the number of total configuration sets
*****************************************************************************/
#define EL1_CUSTOM_TOTAL_SET_NUMS         (EL1_CUSTOM_GPIO_NUMS_IN_CALC * EL1_CUSTOM_ADC_NUMS_IN_CALC * EL1_CUSTOM_BARCODE_NUMS_IN_CALC)

/*****************************************************************************
* Constant    : EL1_CUSTOM_ADC_CALIBRATE_ENABLE
* Group       : Real target, Customization, EL1 common operation
* Description : Customization constant to be used for customer to determine
*               if AuxADC calibration is enabled or not
*****************************************************************************/
#define EL1_CUSTOM_ADC_CALIBRATE_ENABLE   MML1_CUSTOM_ADC_CALIBRATE_ENABLE


/******************************************************************************
 * Function Prototypes
 ******************************************************************************/
void EL1_CUSTOM_ReplaceAuxAdcCalibrate(kal_uint32 adcDigitalValue, kal_int32 *volt);
void EL1_CUSTOM_GPIO_NON_SMART_PHONE_PIN_ACCESS(kal_int16 *gpio_pin);
void EL1_CUSTOM_ADC_PIN_ACCESS(kal_uint16 *adc_channel_num);


/******************************************************************************
 * Global Function extern
 ******************************************************************************/
extern void EPHY_CUSTOM_DynamicInitByGPIO(void *data);
extern void EPHY_CUSTOM_DynamicInitByADC(void *data);
extern void EPHY_CUSTOM_DynamicInitByBarcode(void *data);


/******************************************************************************
 * Customer Data Extern
 ******************************************************************************/
/*** Extern RF CalData for DRDI ***/
//Extern Temperature DAC
extern LTE_TemperatureDac_T  TempDacTable;

//Extern TxRampData 
extern LTE_sRAMPDATA         LTE_BandNone_RampData;
extern LTE_sRAMPDATA         LTE_Band1_RampData;
extern LTE_sRAMPDATA         LTE_Band2_RampData;
extern LTE_sRAMPDATA         LTE_Band38_RampData;
extern LTE_sRAMPDATA         LTE_Band39_RampData;
//Extern PaOctLevData 
extern LTE_sPAOCTLVLSETTING  LTE_BandNone_PaOctLevData;
extern LTE_sPAOCTLVLSETTING  LTE_Band1_PaOctLevData;
extern LTE_sPAOCTLVLSETTING  LTE_Band2_PaOctLevData;
extern LTE_sPAOCTLVLSETTING  LTE_Band38_PaOctLevData;
extern LTE_sPAOCTLVLSETTING  LTE_Band39_PaOctLevData;
//Extern RxRssiGainTable
extern LTE_RSSIBandGainTable LTE_BandNone_RSSIGainTbl;
extern LTE_RSSIBandGainTable LTE_Band1_RSSIGainTbl;
extern LTE_RSSIBandGainTable LTE_Band2_RSSIGainTbl;
extern LTE_RSSIBandGainTable LTE_Band38_RSSIGainTbl;
extern LTE_RSSIBandGainTable LTE_Band39_RSSIGainTbl;


/*** Extern MIPI custom data for DRDI ***/
/* Common */
/* Set 0 */ //reuse Set1 table
extern LTE_MIPI_DATA_TABLE_T*         LTE_MIPI_TPC_DATA_TABLE[];
extern LTE_MIPI_IMM_DATA_TABLE_T      LTE_MIPI_INITIAL_CW[];
extern LTE_MIPI_IMM_DATA_TABLE_T      LTE_MIPI_SLEEP_CW[];
extern LTE_MIPI_IMM_DATA_TABLE_T      LTE_MIPI_ASM_ISOLATION_DATA[];
extern LTE_MIPI_DATA_TABLE_T*         LTE_MIPI_BYPASS_TPC_DATA_TABLE[];

extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_RX_EVENT_TABLE_Set0[];
extern LTE_MIPI_DATA_SUBBAND_TABLE_T* LTE_MIPI_RX_DATA_TABLE_Set0[];
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_TX_EVENT_TABLE_Set0[];
extern LTE_MIPI_DATA_SUBBAND_TABLE_T* LTE_MIPI_TX_DATA_TABLE_Set0[];
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_TPC_EVENT_TABLE_Set0[];
extern LTE_MIPI_DATA_TABLE_T*         LTE_MIPI_TPC_DATA_TABLE_Set0[];
extern LTE_MIPI_TPC_SECTION_TABLE_T*  LTE_MIPI_PA_TPC_SECTION_DATA_Set0[];

extern kal_uint32 LTE_MIPI_RX_EVENT_SIZE_TABLE_Set0[];
extern kal_uint32 LTE_MIPI_RX_DATA_SIZE_TABLE_Set0[];
extern kal_uint32 LTE_MIPI_TX_EVENT_SIZE_TABLE_Set0[];
extern kal_uint32 LTE_MIPI_TX_DATA_SIZE_TABLE_Set0[];
extern kal_uint32 LTE_MIPI_TPC_EVENT_SIZE_TABLE_Set0[];
extern kal_uint32 LTE_MIPI_PA_TPC_SECTION_DATA_SIZE_Set0[];

/* Set 1 */
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_RX_EVENT_TABLE_Set1[];
extern LTE_MIPI_DATA_SUBBAND_TABLE_T* LTE_MIPI_RX_DATA_TABLE_Set1[];
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_TX_EVENT_TABLE_Set1[];
extern LTE_MIPI_DATA_SUBBAND_TABLE_T* LTE_MIPI_TX_DATA_TABLE_Set1[];
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_TPC_EVENT_TABLE_Set1[];
extern LTE_MIPI_DATA_TABLE_T*         LTE_MIPI_TPC_DATA_TABLE_Set1[];
extern LTE_MIPI_TPC_SECTION_TABLE_T*  LTE_MIPI_PA_TPC_SECTION_DATA_Set1[];

extern kal_uint32 LTE_MIPI_RX_EVENT_SIZE_TABLE_Set1[];
extern kal_uint32 LTE_MIPI_RX_DATA_SIZE_TABLE_Set1[];
extern kal_uint32 LTE_MIPI_TX_EVENT_SIZE_TABLE_Set1[];
extern kal_uint32 LTE_MIPI_TX_DATA_SIZE_TABLE_Set1[];
extern kal_uint32 LTE_MIPI_TPC_EVENT_SIZE_TABLE_Set1[];
extern kal_uint32 LTE_MIPI_PA_TPC_SECTION_DATA_SIZE_Set1[];

/* Set 2 */
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_RX_EVENT_TABLE_Set2[];
extern LTE_MIPI_DATA_SUBBAND_TABLE_T* LTE_MIPI_RX_DATA_TABLE_Set2[];
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_TX_EVENT_TABLE_Set2[];
extern LTE_MIPI_DATA_SUBBAND_TABLE_T* LTE_MIPI_TX_DATA_TABLE_Set2[];
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_TPC_EVENT_TABLE_Set2[];
extern LTE_MIPI_DATA_TABLE_T*         LTE_MIPI_TPC_DATA_TABLE_Set2[];
extern LTE_MIPI_TPC_SECTION_TABLE_T*  LTE_MIPI_PA_TPC_SECTION_DATA_Set2[];

extern kal_uint32 LTE_MIPI_RX_EVENT_SIZE_TABLE_Set2[];
extern kal_uint32 LTE_MIPI_RX_DATA_SIZE_TABLE_Set2[];
extern kal_uint32 LTE_MIPI_TX_EVENT_SIZE_TABLE_Set2[];
extern kal_uint32 LTE_MIPI_TX_DATA_SIZE_TABLE_Set2[];
extern kal_uint32 LTE_MIPI_TPC_EVENT_SIZE_TABLE_Set2[];
extern kal_uint32 LTE_MIPI_PA_TPC_SECTION_DATA_SIZE_Set2[];

/* Set 3 */
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_RX_EVENT_TABLE_Set3[];
extern LTE_MIPI_DATA_SUBBAND_TABLE_T* LTE_MIPI_RX_DATA_TABLE_Set3[];
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_TX_EVENT_TABLE_Set3[];
extern LTE_MIPI_DATA_SUBBAND_TABLE_T* LTE_MIPI_TX_DATA_TABLE_Set3[];
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_TPC_EVENT_TABLE_Set3[];
extern LTE_MIPI_DATA_TABLE_T*         LTE_MIPI_TPC_DATA_TABLE_Set3[];
extern LTE_MIPI_TPC_SECTION_TABLE_T*  LTE_MIPI_PA_TPC_SECTION_DATA_Set3[];

extern kal_uint32 LTE_MIPI_RX_EVENT_SIZE_TABLE_Set3[];
extern kal_uint32 LTE_MIPI_RX_DATA_SIZE_TABLE_Set3[];
extern kal_uint32 LTE_MIPI_TX_EVENT_SIZE_TABLE_Set3[];
extern kal_uint32 LTE_MIPI_TX_DATA_SIZE_TABLE_Set3[];
extern kal_uint32 LTE_MIPI_TPC_EVENT_SIZE_TABLE_Set3[];
extern kal_uint32 LTE_MIPI_PA_TPC_SECTION_DATA_SIZE_Set3[];

/* Set 4 */
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_RX_EVENT_TABLE_Set4[];
extern LTE_MIPI_DATA_SUBBAND_TABLE_T* LTE_MIPI_RX_DATA_TABLE_Set4[];
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_TX_EVENT_TABLE_Set4[];
extern LTE_MIPI_DATA_SUBBAND_TABLE_T* LTE_MIPI_TX_DATA_TABLE_Set4[];
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_TPC_EVENT_TABLE_Set4[];
extern LTE_MIPI_DATA_TABLE_T*         LTE_MIPI_TPC_DATA_TABLE_Set4[];
extern LTE_MIPI_TPC_SECTION_TABLE_T*  LTE_MIPI_PA_TPC_SECTION_DATA_Set4[];

extern kal_uint32 LTE_MIPI_RX_EVENT_SIZE_TABLE_Set4[];
extern kal_uint32 LTE_MIPI_RX_DATA_SIZE_TABLE_Set4[];
extern kal_uint32 LTE_MIPI_TX_EVENT_SIZE_TABLE_Set4[];
extern kal_uint32 LTE_MIPI_TX_DATA_SIZE_TABLE_Set4[];
extern kal_uint32 LTE_MIPI_TPC_EVENT_SIZE_TABLE_Set4[];
extern kal_uint32 LTE_MIPI_PA_TPC_SECTION_DATA_SIZE_Set4[];

/* Set 5 */
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_RX_EVENT_TABLE_Set5[];
extern LTE_MIPI_DATA_SUBBAND_TABLE_T* LTE_MIPI_RX_DATA_TABLE_Set5[];
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_TX_EVENT_TABLE_Set5[];
extern LTE_MIPI_DATA_SUBBAND_TABLE_T* LTE_MIPI_TX_DATA_TABLE_Set5[];
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_TPC_EVENT_TABLE_Set5[];
extern LTE_MIPI_DATA_TABLE_T*         LTE_MIPI_TPC_DATA_TABLE_Set5[];
extern LTE_MIPI_TPC_SECTION_TABLE_T*  LTE_MIPI_PA_TPC_SECTION_DATA_Set5[];

extern kal_uint32 LTE_MIPI_RX_EVENT_SIZE_TABLE_Set5[];
extern kal_uint32 LTE_MIPI_RX_DATA_SIZE_TABLE_Set5[];
extern kal_uint32 LTE_MIPI_TX_EVENT_SIZE_TABLE_Set5[];
extern kal_uint32 LTE_MIPI_TX_DATA_SIZE_TABLE_Set5[];
extern kal_uint32 LTE_MIPI_TPC_EVENT_SIZE_TABLE_Set5[];
extern kal_uint32 LTE_MIPI_PA_TPC_SECTION_DATA_SIZE_Set5[];

/*** Extern MIPI BYPASS custom data for DRDI ***/
/* Common */
/* Set 0 */ //reuse Set1 table
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_BYPASS_TX_EVENT_TABLE_Set0[];
extern LTE_MIPI_DATA_SUBBAND_TABLE_T* LTE_MIPI_BYPASS_TX_DATA_TABLE_Set0[];
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_BYPASS_TPC_EVENT_TABLE_Set0[];
extern LTE_MIPI_DATA_TABLE_T*         LTE_MIPI_BYPASS_TPC_DATA_TABLE_Set0[];
extern LTE_MIPI_TPC_SECTION_TABLE_T*  LTE_MIPI_BYPASS_PA_TPC_SECTION_DATA_Set0[];

extern kal_uint32 LTE_MIPI_BYPASS_TX_EVENT_SIZE_TABLE_Set0[];
extern kal_uint32 LTE_MIPI_BYPASS_TX_DATA_SIZE_TABLE_Set0[];
extern kal_uint32 LTE_MIPI_BYPASS_TPC_EVENT_SIZE_TABLE_Set0[];
extern kal_uint32 LTE_MIPI_BYPASS_PA_TPC_SECTION_DATA_SIZE_Set0[];

/* Set 1 */
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_BYPASS_TX_EVENT_TABLE_Set1[];
extern LTE_MIPI_DATA_SUBBAND_TABLE_T* LTE_MIPI_BYPASS_TX_DATA_TABLE_Set1[];
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_BYPASS_TPC_EVENT_TABLE_Set1[];
extern LTE_MIPI_DATA_TABLE_T*         LTE_MIPI_BYPASS_TPC_DATA_TABLE_Set1[];
extern LTE_MIPI_TPC_SECTION_TABLE_T*  LTE_MIPI_BYPASS_PA_TPC_SECTION_DATA_Set1[];

extern kal_uint32 LTE_MIPI_BYPASS_TX_EVENT_SIZE_TABLE_Set1[];
extern kal_uint32 LTE_MIPI_BYPASS_TX_DATA_SIZE_TABLE_Set1[];
extern kal_uint32 LTE_MIPI_BYPASS_TPC_EVENT_SIZE_TABLE_Set1[];
extern kal_uint32 LTE_MIPI_BYPASS_PA_TPC_SECTION_DATA_SIZE_Set1[];
/* Set 2 */
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_BYPASS_TX_EVENT_TABLE_Set2[];
extern LTE_MIPI_DATA_SUBBAND_TABLE_T* LTE_MIPI_BYPASS_TX_DATA_TABLE_Set2[];
extern LTE_MIPI_EVENT_TABLE_T*        LTE_MIPI_BYPASS_TPC_EVENT_TABLE_Set2[];
extern LTE_MIPI_DATA_TABLE_T*         LTE_MIPI_BYPASS_TPC_DATA_TABLE_Set2[];
extern LTE_MIPI_TPC_SECTION_TABLE_T*  LTE_MIPI_BYPASS_PA_TPC_SECTION_DATA_Set2[];

extern kal_uint32 LTE_MIPI_BYPASS_TX_EVENT_SIZE_TABLE_Set2[];
extern kal_uint32 LTE_MIPI_BYPASS_TX_DATA_SIZE_TABLE_Set2[];
extern kal_uint32 LTE_MIPI_BYPASS_TPC_EVENT_SIZE_TABLE_Set2[];
extern kal_uint32 LTE_MIPI_BYPASS_PA_TPC_SECTION_DATA_SIZE_Set2[];

#endif /* _LTE_CUSTOM_DRDI_H_*/
