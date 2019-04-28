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
 *   tl1d_custom_mipi.h
 *
 * Project:
 * --------
 *   MT6169
 *
 * Description:
 * ------------
 *   MT6169 3G TDS RF constance definition
 *
 * Author:
 * -------
 *
 *
 *==============================================================================
 *******************************************************************************/

#ifndef  _TL1D_CUSTOM_MIPI_H_
#define  _TL1D_CUSTOM_MIPI_H_
/* --------------------------------------------------------------------------- */


/*--------------------------------------------------------*/
/*   MIPI Module                                          */
/*--------------------------------------------------------*/
/*MT6169*/   #define  MIPI_PA       0xf         //PA slave id
/*MT6169*/   #define  MIPI_ASM      0xb         //ASM slave id
/*MT6169*/   #define  DATA_NULL     0xffff
/*MT6169*/   #define  DEVICE_NULL     0xf0f0
/*MT6169*/   #define  PA_SEL_FLAG        0xf000      //PA mode selection 表示PA MODE通过设定PA积存器
/*MT6169*/   #define  PA_REG_NUM_B34         4           //RegNum
/*MT6169*/   #define  PA_REG_NUM_B39         4           //RegNum

/*--------------------------------------------------------*/
/*   MIPI Port                                            */
/*--------------------------------------------------------*/
/*MT6169*/   #define  MIPI_PORT0    0
/*MT6169*/   #define  MIPI_PORT1    1

typedef  struct {
kal_uint16 uwDeviceID1;
kal_uint16 uwDeviceID2;
kal_uint16 uwDeviceID3;
kal_uint16 uwDeviceID4;
} T_MIPI_DEVICE_STRUCT;

typedef  struct {
kal_uint16 uwElementType;
kal_uint16 uwDataStart;
kal_uint16 uwDataStop;
} T_MIPI_EVENT_ITEM_STRUCT;

typedef  struct {
kal_uint16 uwElementTypeAndPort;
kal_uint16 uwDataLowBand34;
kal_uint16 uwDataHighBand34;
kal_uint16 uwDataLowBand39;
kal_uint16 uwDataHighBand39;
} T_MIPI_DATA_ITEM_STRUCT;

typedef  struct {
kal_uint16 uwElementTypeAndPort;
kal_uint16 uwDataLow;
kal_uint16 uwDataHigh;
} T_MIPI_INIT_ITEM_STRUCT;


typedef  struct {
T_MIPI_EVENT_ITEM_STRUCT uwEvent1;
T_MIPI_EVENT_ITEM_STRUCT uwEvent2;
T_MIPI_EVENT_ITEM_STRUCT uwEvent3;
T_MIPI_EVENT_ITEM_STRUCT uwEvent4;
} T_MIPI_EVENT_STRUCT;

typedef  struct {
T_MIPI_DATA_ITEM_STRUCT uwData1;
T_MIPI_DATA_ITEM_STRUCT uwData2;
T_MIPI_DATA_ITEM_STRUCT uwData3;
T_MIPI_DATA_ITEM_STRUCT uwData4;
T_MIPI_DATA_ITEM_STRUCT uwData5;
T_MIPI_DATA_ITEM_STRUCT uwData6;
T_MIPI_DATA_ITEM_STRUCT uwData7;
T_MIPI_DATA_ITEM_STRUCT uwData8;
T_MIPI_DATA_ITEM_STRUCT uwData9;
T_MIPI_DATA_ITEM_STRUCT uwData10;
T_MIPI_DATA_ITEM_STRUCT uwData11;
T_MIPI_DATA_ITEM_STRUCT uwData12;
} T_MIPI_DATA_STRUCT;

typedef  struct {
T_MIPI_INIT_ITEM_STRUCT uwData1;
T_MIPI_INIT_ITEM_STRUCT uwData2;
T_MIPI_INIT_ITEM_STRUCT uwData3;
T_MIPI_INIT_ITEM_STRUCT uwData4;
T_MIPI_INIT_ITEM_STRUCT uwData5;
T_MIPI_INIT_ITEM_STRUCT uwData6;
T_MIPI_INIT_ITEM_STRUCT uwData7;
T_MIPI_INIT_ITEM_STRUCT uwData8;
T_MIPI_INIT_ITEM_STRUCT uwData9;
T_MIPI_INIT_ITEM_STRUCT uwData10;
T_MIPI_INIT_ITEM_STRUCT uwData11;
T_MIPI_INIT_ITEM_STRUCT uwData12;
} T_MIPI_INIT_DATA_STRUCT;

typedef  struct {
T_MIPI_DATA_ITEM_STRUCT uwData1;
T_MIPI_DATA_ITEM_STRUCT uwData2;
T_MIPI_DATA_ITEM_STRUCT uwData3;
T_MIPI_DATA_ITEM_STRUCT uwData4;
T_MIPI_DATA_ITEM_STRUCT uwData5;
} T_MIPI_PA_DATA_STRUCT;

typedef  struct {
T_MIPI_EVENT_STRUCT  eRxOnEvent;
T_MIPI_EVENT_STRUCT  eRxOffEvent;
T_MIPI_EVENT_STRUCT  eTxOnEvent;
T_MIPI_EVENT_STRUCT  eTxOffEvent;
} T_MIPI_EVENT_ALL_STRUCT;

typedef  struct {
T_MIPI_DATA_STRUCT	 eRxOnData;
T_MIPI_DATA_STRUCT	 eRxOffData;
T_MIPI_DATA_STRUCT	 eTxOnData;
T_MIPI_DATA_STRUCT	 eTxOffData;
} T_MIPI_DATA_ALL_STRUCT;

typedef  struct {
T_MIPI_PA_DATA_STRUCT	 ePAHighMode;
T_MIPI_PA_DATA_STRUCT	 ePAMiddleMode;
T_MIPI_PA_DATA_STRUCT	 ePALowMode;
} T_MIPI_PA_MODE_STRUCT;

typedef  struct {
kal_uint16  uwInitRegNum;	
T_MIPI_DEVICE_STRUCT  eMipiDeviceID;
T_MIPI_INIT_DATA_STRUCT  eMipiInit;	
T_MIPI_EVENT_ALL_STRUCT eMipiEvent;
T_MIPI_DATA_ALL_STRUCT  eMipiData;
T_MIPI_PA_MODE_STRUCT   ePaData;
} T_MIPI_CUSTOMIZATION_STRUCT;



#if (defined(_T12193_C_) && !defined(__3G_TDD_MIPI_SUPPORT__)) \
	|| (defined(__3G_TDD_MIPI_SUPPORT__) && defined(__3G_TDD_MIPI_DEFINE__))

T_MIPI_CUSTOMIZATION_STRUCT AST_TL1_RFFE_PARAMETER_DEFAULT[] ={
{
	//Init Register Number
	0,
	//MIPI DEVICE ID
	{
			MIPI_PA, //fixed, do not change      
			MIPI_ASM, //fixed, do not change
			DEVICE_NULL,
			DEVICE_NULL,
	},
	//MIPI Init data
	{
				/*            module      |     port          ,            low 16bit    ,          high 16bit   */
				{  /*  0 */  DATA_NULL    |          (0<<15),                     0,                     0},
				{  /*  1 */  DATA_NULL    |          (0<<15),                     0,                     0},
				{  /*  2 */  DATA_NULL    |          (0<<15),                     0,                     0},
				{  /*  3 */  DATA_NULL    |          (0<<15),                     0,                     0},
				{  /*  4 */  DATA_NULL    |          (0<<15),                     0,                     0},
				{  /*  5 */  DATA_NULL    |          (0<<15),                     0,                     0},
				{  /*  6 */  DATA_NULL    |          (0<<15),                     0,                     0},
				{  /*  7 */  DATA_NULL    |          (0<<15),                     0,                     0},
				{  /*  8 */  DATA_NULL    |          (0<<15),                     0,                     0},
				{  /*  9 */  DATA_NULL    |          (0<<15),                     0,                     0},
				{  /*  0 */  DATA_NULL    |          (0<<15),                     0,                     0},
				{  /*  1 */  DATA_NULL    |          (0<<15),                     0,                     0}
	},
	//MIPI Event table
	{
			//Rx ON Event
			{
				/*            module       start    stop*/
				{  /*  0 */  MIPI_ASM  ,     0  ,     1 },
				{  /*  1 */  DATA_NULL ,     0  ,     0 },
				{  /*  2 */  DATA_NULL ,     0  ,     0 },
				{  /*  3 */  DATA_NULL ,     0  ,     0 }
			},
			//Rx Off Event
			{	
				/*            module       start    stop*/
				{  /*  0 */  MIPI_ASM  ,     0  ,     1 },
				{  /*  1 */  DATA_NULL ,     0  ,     0 },
				{  /*  2 */  DATA_NULL ,     0  ,     0 },
				{  /*  3 */  DATA_NULL ,     0  ,     0 }
			},
			//Tx ON Event
			{
				/*            module       start    stop*/
				{  /*  0 */  MIPI_PA   ,     0  ,     0 },
				{  /*  1 */  MIPI_ASM  ,     1  ,     2 },
				{  /*  2 */  DATA_NULL ,     0  ,     0 },
				{  /*  3 */  DATA_NULL ,     0  ,     0 }
			},
			//Tx Off Event
			{
				/*            module       start    stop*/
				{  /*  0 */  MIPI_ASM  ,     0  ,     1 },
				{  /*  1 */  MIPI_PA   ,     2  ,     3 },
				{  /*  2 */  DATA_NULL ,     0  ,     0 },
				{  /*  3 */  DATA_NULL ,     0  ,     0 }
			}		
	},
	//MIPI Data table
	{
			//Rx On Data
			{
				/*            module      |     port          ,     band34 low 16bit    ,   band34 high 16bit   ,   band39 low 16bit   ,    band39 high 16bit  */
				{  /*  0 */  MIPI_ASM     | (MIPI_PORT1<<15),                0x3800,                0x0B5C,                0x3800,                0x0B5C},
				{  /*  1 */  MIPI_ASM     | (MIPI_PORT1<<15),                0x0C00,                0x0B40,                0x0B00,                0x0B40},
				{  /*  2 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  3 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  4 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  5 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  6 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  7 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  8 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  9 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  0 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  1 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0}
			},
			//Rx Off Data
			{
				/*            module      |     port          ,     band34 low 16bit    ,   band34 high 16bit   ,   band39 low 16bit   ,    band39 high 16bit  */
				{  /*  0 */  MIPI_ASM     | (MIPI_PORT1<<15),                0x0000,                0x0B40,                0x0000,                0x0B40},
				{  /*  1 */  MIPI_ASM     | (MIPI_PORT1<<15),                0x3800,                0x0B5C,                0x3800,                0x0B5C},
				{  /*  2 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  3 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  4 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  5 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  6 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  7 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  8 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  9 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  0 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  1 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0}
			},			
			//Tx On Data
			{
				/*            module      |     port          ,     band34 low 16bit    ,   band34 high 16bit   ,   band39 low 16bit   ,    band39 high 16bit  */
				{  /*  0 */  MIPI_PA      | (MIPI_PORT0<<15),        PA_REG_NUM_B34,           PA_SEL_FLAG,        PA_REG_NUM_B39,           PA_SEL_FLAG},
				{  /*  1 */  MIPI_ASM     | (MIPI_PORT1<<15),                0x3800,                0x0B5C,                0x3800,                0x0B5C},
				{  /*  2 */  MIPI_ASM     | (MIPI_PORT1<<15),                0x0400,                0x0B40,                0x0400,                0x0B40},
				{  /*  3 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  4 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  5 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  6 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  7 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  8 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  9 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  0 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  1 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0}
			},			  
  		//Tx Off Data
			{ 
				/*            module      |     port          ,     band34 low 16bit    ,   band34 high 16bit   ,   band39 low 16bit   ,    band39 high 16bit  */
				{  /*  0 */  MIPI_ASM     | (MIPI_PORT1<<15),                0x0000,                0x0B40,                0x0000,                0x0B40},
				{  /*  2 */  MIPI_ASM     | (MIPI_PORT1<<15),                0x3800,                0x0B5C,                0x3800,                0x0B5C},
				{  /*  1 */  MIPI_PA      | (MIPI_PORT0<<15),                0x0000,                0x0F40,                0x0000,                0x0F40},
				{  /*  3 */  MIPI_PA      | (MIPI_PORT0<<15),                0x0000,                0x0F41,                0x0000,                0x0F41},
				{  /*  4 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  5 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  6 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  7 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  8 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  9 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  0 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0},
				{  /*  1 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0}
			}			  
	},
	//PA MODE SETTING
	{		  
			{
				/*            module      |     port          ,     band34 low 16bit    ,   band34 high 16bit   ,   band39 low 16bit   ,    band39 high 16bit  */
				{  /*  0 */  MIPI_PA      | (MIPI_PORT0<<15),	               0x0C00,                0x0F40,                0x0C00,                0x0F40},//FOR PA HIGH MODE
				{  /*  1 */  MIPI_PA      | (MIPI_PORT0<<15),	               0x1F00,                0x0F41,                0x1F00,                0x0F41},//FOR PA HIGH MODE
				{  /*  2 */  MIPI_PA      | (MIPI_PORT0<<15),	               0xDD00,                0x0F42,                0xDB00,                0x0F42},//FOR PA HIGH MODE
				{  /*  3 */  MIPI_PA      | (MIPI_PORT0<<15),	               0x2500,                0x0F43,                0x2500,                0x0F43},//FOR PA HIGH MODE
				{  /*  4 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0}//FOR PA HIGH MODE
				
		  },
			{
				/*            module      |     port          ,     band34 low 16bit    ,   band34 high 16bit   ,   band39 low 16bit   ,    band39 high 16bit  */
				{  /*  0 */  MIPI_PA      | (MIPI_PORT0<<15),	               0x0C00,                0x0F40,                0x0C00,                0x0F40},//FOR PA MIDDLE MODE
				{  /*  1 */  MIPI_PA      | (MIPI_PORT0<<15),	               0x1F00,                0x0F41,                0x1F00,                0x0F41},//FOR PA MIDDLE MODE
				{  /*  2 */  MIPI_PA      | (MIPI_PORT0<<15),	               0x5800,                0x0F42,                0x8800,                0x0F42},//FOR PA MIDDLE MODE
				{  /*  3 */  MIPI_PA      | (MIPI_PORT0<<15),	               0x2400,                0x0F43,                0x2000,                0x0F43},//FOR PA MIDDLE MODE
				{  /*  4 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0}//FOR PA MIDDLE MODE
			},
			{
				/*            module      |     port          ,     band34 low 16bit    ,   band34 high 16bit   ,   band39 low 16bit   ,    band39 high 16bit  */
				{  /*  0 */  MIPI_PA      | (MIPI_PORT0<<15),	               0x0C00,                0x0F40,                0x0C00,                0x0F40},//FOR PA LOW MODE
				{  /*  1 */  MIPI_PA      | (MIPI_PORT0<<15),	               0x1F00,                0x0F41,                0x1F00,                0x0F41},//FOR PA LOW MODE
				{  /*  2 */  MIPI_PA      | (MIPI_PORT0<<15),	               0xC600,                0x0F42,                0xC600,                0x0F42},//FOR PA LOW MODE
				{  /*  3 */  MIPI_PA      | (MIPI_PORT0<<15),	               0x0000,                0x0F43,                0x0000,                0x0F43},//FOR PA LOW MODE
				{  /*  4 */  DATA_NULL    |          (0<<15),                     0,                     0,                     0,                     0}//FOR PA LOW MODE
			}	
	}
}};


#else
extern  const T_MIPI_CUSTOMIZATION_STRUCT    AST_TL1_RFFE_PARAMETER_DEFAULT[];

#endif//end defined(_T12193_C_) || defined(__AST_TL1_TDD_RF_PARAMETER_DEFINE__)

#endif
