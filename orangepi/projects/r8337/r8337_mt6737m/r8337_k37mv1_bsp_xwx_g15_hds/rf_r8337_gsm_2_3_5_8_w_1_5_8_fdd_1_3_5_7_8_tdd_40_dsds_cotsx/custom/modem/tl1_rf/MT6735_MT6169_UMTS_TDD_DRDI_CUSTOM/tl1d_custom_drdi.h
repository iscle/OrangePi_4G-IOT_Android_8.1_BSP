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
 *  tl1d_custom_drdi.h
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

#ifndef  _TL1D_CUSTOM_DRDI_H_
#define  _TL1D_CUSTOM_DRDI_H_


/*******************************************************************************
** Includes
*******************************************************************************/
#include "kal_general_types.h"
#include "mml1_custom_drdi.h"
#include "tl1d_custom_rf.h"
#include "tl1d_custom_mipi.h"


/********************
 * Common
 *******************/


 /*********************************************************************/ 
/* Dynamic Radio-setting Dedicated Images                            */ 
/* Let DRDI GPIO/ADC/Barcode BPI setting overwrite NVRAN BPI setting */ 
/*********************************************************************/

#define TL1_CUSTOM_DYNAMIC_INIT_ENABLE    (IS_MML1_DRDI_ENABLE)

/*****************************************************************************
* Constant    : TL1_CUSTOM_TOTAL_SET_NUMS
* Group       : Real target, Internals, TL1 common operation
* Description : Constant to be used as the number of total configuration sets
*****************************************************************************/

#define TL1_CUSTOM_TOTAL_SET_NUMS         MML1_CUSTOM_TOTAL_SET_NUMS
#define TL1_CUSTOM_REMAP_REAL_SET_NUMS    MML1_DRDI_REMAP_TDS_REAL_SET_NUMS			


#if (defined(_T12193_C_) && !defined(__AST_TL1_TDD_RF_PARAMETER_SUPPORT__)) \
	|| (defined(__3G_TDD_DRDI_DEFINE__) && defined(__AST_TL1_TDD_RF_PARAMETER_SUPPORT__))

/***************************************************************************
 * Global Data
 ***************************************************************************/
#if (TL1_CUSTOM_DYNAMIC_INIT_ENABLE == 1) 

kal_uint16 TDS_DRDI_STATUS_DEFAULT[] = {0x1};

#else

kal_uint16 TDS_DRDI_STATUS_DEFAULT[] = {0x0};


#endif

#if (IS_MML1_DRDI_ENABLE && IS_MML1_DRDI_REMAP_ENABLE)
kal_uint16 TDSCDMA_DRDI_REMAP_ENABLE = 0x11;     //DRDI& REMAPPING ENABLE
#elif (IS_MML1_DRDI_ENABLE)
kal_uint16 TDSCDMA_DRDI_REMAP_ENABLE = 0x1;        //DRDI only
#else
kal_uint16 TDSCDMA_DRDI_REMAP_ENABLE = 0;        //disable DRDI & REMAPPING
#endif
kal_uint16 gTd_DRDI_Array_len = TL1_CUSTOM_TOTAL_SET_NUMS;

/***************************************************************************
 * Data array of pointers pointed to data array for custom data
 ***************************************************************************/

/* Pointer array for customer to input TL1 RF Table
 * with pointers for each of the configuration set to be detected */

T_TD_CUSTOMIZATION_STRUCT         *tl1_rf_data_array_ptr[TL1_CUSTOM_TOTAL_SET_NUMS];

//*********************************************
// TL1 MIPI Custom Setting
//*********************************************
/* Pointer array for customer to input TL1 MIPI data */

T_MIPI_CUSTOMIZATION_STRUCT         *tl1_rf_mipi_data_array_ptr[TL1_CUSTOM_TOTAL_SET_NUMS];

//********************************************
// TL1 RF Table Custom data definition
//********************************************
const T_TD_CUSTOMIZATION_STRUCT tl1CustomRfData_set0 =
{//Set 0 
 //T_BPI_SETTING 
 {
  //TX_2G34   TX_2G01/TX_1G90 
 // [7:4]     [3:0] 	
 	 (1<<4)     +   2,                                 //PA_STAGE          data[0]
 	 																						       //3:PA_HGAIN PA_MGAIN PA_LGAIN
 	 																						       //2:PA_HGAIN PA_MGAIN
 	 																						       //1:PA_HGAIN
 

   {	
  /////////////////////DBB_BPI_AREA1 begin (fix for TX Control )/////////////////////////////
		// V1	       V2	        V3		        	 RFconflict 								
     (1<<0) + (1<<28),          //TX_CTRL_BITMASK   data[1]	
     (0<<0) + (1<<28),          //TX_2G34 PA_HGAIN  data[2]	
     (0<<0) + (1<<28),          //TX_2G34 PA_MGAIN  data[3]	
     (0<<0) + (1<<28),          //TX_2G34 PA_LGAIN  data[4]	
     (1<<0) + (1<<28),          //TX_2G01 PA_HGAIN  data[5]	
     (1<<0) + (1<<28),          //TX_2G01 PA_MGAIN  data[6]	
     (1<<0) + (1<<28),          //TX_2G01 PA_LGAIN  data[7]	
     (1<<0) + (1<<28),          //TX_1G90 PA_HGAIN  data[8]	
     (1<<0) + (1<<28),          //TX_1G90 PA_MGAIN  data[9]	
     (1<<0) + (1<<28),          //TX_1G90 PA_LGAIN  data[10] 
     (0<<0) + (0<<28),          //TX_DISABLE	      data[11]
	/////////////////////DBB_BPI_AREA1 end///////////////////////////////////////////////////
	  },
	  
		{
	/////////////////////DBB_BPI_AREA2 begin (fix for RX Control)///////////////////////////
		// V1	       V2	        V3		    		  RFconflict										
     (1<<0) + (1<<28),           //RX_CTRL_BITMASK   data[12]	
     (0<<0) + (1<<28),           //RX_2G34           data[13]	
     (1<<0) + (1<<28),           //RX_2G01           data[14]	
     (1<<0) + (1<<28),           //RX_1G90           data[15]	
     (0<<0) + (0<<28),           //RX_DISABLE        data[16]
	 /////////////////////DBB_BPI_AREA2 end///////////////////////////////////////////////////
    },

    {
  /////////////////////DBB_BPI_AREA3 begin/////////////////////////////////////////////////   
	  //TXEN	 VEN																		   
     (0<<0),                      //PA_BITMASK        data[17]  
     (0<<0),                      //PA_ENABLE_2G34    data[18]  
     (0<<0),                      //PA_ENABLE_2G01    data[19]  
     (0<<0),                      //PA_ENABLE_1G90    data[20]  
     (0<<0)	                      //PA_DISABLE        data[21]  
  /////////////////////DBB_BPI_AREA3 end///////////////////////////////////////////////////
    },

    {
  /////////////////////DBB_BPI_AREA4 begin/////////////////////////////////////////////////   
 //enable/disable Other fuction 
     (0<<0),                        //?_BITMASK         data[22]
     (TD_CLPC_EN<<0),               //?_ENABLE          data[23]    
     (TD_MIPI_EN<<0)                //?_DISABLE         data[24]   
  /////////////////////DBB_BPI_AREA4 end///////////////////////////////////////////////////                                                                                     
    },

    {
  /////////////////////DBB_BPI_AREA5 begin/////////////////////////////////////////////////   
  //enable/disable Other fuction 
     (0<<0),                              //?_BITMASK         data[25]
     (0<<0),                              //?_ENABLE          data[26]    
     (0<<0)                               //?_DISABLE         data[27]   
  /////////////////////DBB_BPI_AREA5 end///////////////////////////////////////////////////
    }
 },

//T_ABB_APC_VOLT 
 {
     0x33, //HGv = 1.6v
     0x11, //MGv = 0.5V
     0x11, //LGv = 0.5v
     0x33, //UPAV = 2.0v not use
 },
//T_PA_VPA_VOLT_CONFIG 
 {
     0x32, //B34 HGv = 3v
     0x32, //B34 MGv = 3V
     0x0,  //B34 LGv = 0v
     0x3A, //B39 HGv = 3.4V
     0x3A, //B39 HGv = 3.4V
     0x0,  //B39 HGv = 0v not use
 },
//T_RF_PORT_SEL
 {
     RF_TX_MB2,  //Tx2G01
     RF_TX_MB2,  //Tx1G90
     RF_TX_HB2,  //Tx2G34
     RF_RX_MB2,  //Rx2G01
     RF_RX_MB2,  //Rx1G90
     RF_RX_HB3   //Rx2G34
 },

//T_IQ_SWAP_CFG_STRUCT
 {
     0,//RX :0    IQswap diable     1:    IQswap enable
     0 //TX :0    IQswap diable     1:    IQswap enable
 }
};

const T_TD_CUSTOMIZATION_STRUCT tl1CustomRfData_set1 =
{//Set 1
 //T_BPI_SETTING 
 {
  //TX_2G34   TX_2G01/TX_1G90 
 // [7:4]     [3:0] 	
 	 (1<<4)     +   2,                                 //PA_STAGE          data[0]
 	 																						       //3:PA_HGAIN PA_MGAIN PA_LGAIN
 	 																						       //2:PA_HGAIN PA_MGAIN
 	 																						       //1:PA_HGAIN
 

   {	
  /////////////////////DBB_BPI_AREA1 begin (fix for TX Control )/////////////////////////////
		// V1	       V2	        V3		        	 RFconflict 								
   (1<<0) + (1<<1) + (1<<28), 		 //TX_CTRL_BITMASK	 data[1]  
   (0<<0) + (0<<1) + (1<<28), 		 //TX_2G34 PA_HGAIN  data[2]  
   (0<<0) + (0<<1) + (1<<28), 		 //TX_2G34 PA_MGAIN  data[3]  
   (0<<0) + (0<<1) + (1<<28), 		 //TX_2G34 PA_LGAIN  data[4]  
   (0<<0) + (0<<1) + (1<<28), 		 //TX_2G01 PA_HGAIN  data[5]  
   (0<<0) + (0<<1) + (1<<28), 		 //TX_2G01 PA_MGAIN  data[6]  
   (0<<0) + (0<<1) + (1<<28), 		 //TX_2G01 PA_LGAIN  data[7]  
   (0<<0) + (0<<1) + (1<<28), 		 //TX_1G90 PA_HGAIN  data[8]  
   (0<<0) + (0<<1) + (1<<28), 		 //TX_1G90 PA_MGAIN  data[9]  
   (0<<0) + (0<<1) + (1<<28), 		 //TX_1G90 PA_LGAIN  data[10] 
   (0<<0) + (0<<1) + (0<<28), 		 //TX_DISABLE		data[11]
  ////////////////////DBB_BPI_AREA1 end///////////////////////////////////////////////////
	},
	
	  {
  /////////////////////DBB_BPI_AREA2 begin (fix for RX Control)///////////////////////////
	  // V1 		 V2 		  V3					RFconflict										  
   (1<<0) + (1<<1) + (1<<28), 		  //RX_CTRL_BITMASK   data[12]	  
   (0<<0) + (0<<1) + (1<<28), 		  //RX_2G34 		  data[13]	  
   (0<<0) + (0<<1) + (1<<28), 		  //RX_2G01 		  data[14]	  
   (0<<0) + (0<<1) + (1<<28), 		  //RX_1G90 		  data[15]	  
   (0<<0) + (0<<1) + (0<<28), 		  //RX_DISABLE		  data[16]
	 /////////////////////DBB_BPI_AREA2 end///////////////////////////////////////////////////
    },

    {
  /////////////////////DBB_BPI_AREA3 begin/////////////////////////////////////////////////   
	  //TXEN	 VEN																		   
     (0<<0),                      //PA_BITMASK        data[17]  
     (0<<0),                      //PA_ENABLE_2G34    data[18]  
     (0<<0),                      //PA_ENABLE_2G01    data[19]  
     (0<<0),                      //PA_ENABLE_1G90    data[20]  
     (0<<0)	                      //PA_DISABLE        data[21]  
  /////////////////////DBB_BPI_AREA3 end///////////////////////////////////////////////////
    },

    {
  /////////////////////DBB_BPI_AREA4 begin/////////////////////////////////////////////////   
 //enable/disable Other fuction 
     (0<<0),                        //?_BITMASK         data[22]
     (TD_CLPC_EN<<0),               //?_ENABLE          data[23]    
     (TD_MIPI_EN<<0)                //?_DISABLE         data[24]   
  /////////////////////DBB_BPI_AREA4 end///////////////////////////////////////////////////                                                                                     
    },

    {
  /////////////////////DBB_BPI_AREA5 begin/////////////////////////////////////////////////   
  //enable/disable Other fuction 
     (0<<0),                              //?_BITMASK         data[25]
     (0<<0),                              //?_ENABLE          data[26]    
     (0<<0)                               //?_DISABLE         data[27]   
  /////////////////////DBB_BPI_AREA5 end///////////////////////////////////////////////////
    }
 },

//T_ABB_APC_VOLT 
 {
     0x33, //HGv = 1.6v
     0x11, //MGv = 0.5V
     0x11, //LGv = 0.5v
     0x33, //UPAV = 2.0v not use
 },
//T_PA_VPA_VOLT_CONFIG 
 {
     0x32, //B34 HGv = 3v
     0x32, //B34 MGv = 3V
     0x0,  //B34 LGv = 0v
     0x3A, //B39 HGv = 3.4V
     0x3A, //B39 HGv = 3.4V
     0x0,  //B39 HGv = 0v not use
 },
//T_RF_PORT_SEL
 {
     RF_TX_MB2,  //Tx2G01
     RF_TX_MB2,  //Tx1G90
     RF_TX_HB2,  //Tx2G34
     RF_RX_MB2,  //Rx2G01
     RF_RX_MB2,  //Rx1G90
     RF_RX_HB3   //Rx2G34
 },

//T_IQ_SWAP_CFG_STRUCT
 {
     0,//RX :0    IQswap diable     1:    IQswap enable
     0 //TX :0    IQswap diable     1:    IQswap enable
 }
};


T_TD_CUSTOMIZATION_STRUCT* tl1CustomRfData[TL1_CUSTOM_TOTAL_SET_NUMS]=
{
    &tl1CustomRfData_set0,   //evb
    &tl1CustomRfData_set1,   //sku6
    &tl1CustomRfData_set0,   //sku5
    &tl1CustomRfData_set0,   //sku3
    &tl1CustomRfData_set0,   //sku2
    &tl1CustomRfData_set0    //sku1		
};


const T_MIPI_CUSTOMIZATION_STRUCT tl1CustomMipiData_set0 =
{//Set 0
		//MIPI Event Num
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
					/*			  module	  | 	port		  , 		   low 16bit	,		   high 16bit	*/
					{  /*  0 */  DATA_NULL	  | 		 (0<<15),					  0,					 0},
					{  /*  1 */  DATA_NULL	  | 		 (0<<15),					  0,					 0},
					{  /*  2 */  DATA_NULL	  | 		 (0<<15),					  0,					 0},
					{  /*  3 */  DATA_NULL	  | 		 (0<<15),					  0,					 0},
					{  /*  4 */  DATA_NULL	  | 		 (0<<15),					  0,					 0},
					{  /*  5 */  DATA_NULL	  | 		 (0<<15),					  0,					 0},
					{  /*  6 */  DATA_NULL	  | 		 (0<<15),					  0,					 0},
					{  /*  7 */  DATA_NULL	  | 		 (0<<15),					  0,					 0},
					{  /*  8 */  DATA_NULL	  | 		 (0<<15),					  0,					 0},
					{  /*  9 */  DATA_NULL	  | 		 (0<<15),					  0,					 0},
					{  /*  0 */  DATA_NULL	  | 		 (0<<15),					  0,					 0},
					{  /*  1 */  DATA_NULL	  | 		 (0<<15),					  0,					 0}
		},
		//MIPI Event table
		{
				//Rx ON Event
				{
					/*			  module	   start	stop*/
					{  /*  0 */  MIPI_ASM  ,	 0	,	  1 },
					{  /*  1 */  DATA_NULL ,	 0	,	  0 },
					{  /*  2 */  DATA_NULL ,	 0	,	  0 },
					{  /*  3 */  DATA_NULL ,	 0	,	  0 }
				},
				//Rx Off Event
				{	
					/*			  module	   start	stop*/
					{  /*  0 */  MIPI_ASM  ,	 0	,	  1 },
					{  /*  1 */  DATA_NULL ,	 0	,	  0 },
					{  /*  2 */  DATA_NULL ,	 0	,	  0 },
					{  /*  3 */  DATA_NULL ,	 0	,	  0 }
				},
				//Tx ON Event
				{
					/*			  module	   start	stop*/
					{  /*  0 */  MIPI_PA   ,	 0	,	  0 },
					{  /*  1 */  MIPI_ASM  ,	 1	,	  2 },
					{  /*  2 */  DATA_NULL ,	 0	,	  0 },
					{  /*  3 */  DATA_NULL ,	 0	,	  0 }
				},
				//Tx Off Event
				{
					/*			  module	   start	stop*/
					{  /*  0 */  MIPI_ASM  ,	 0	,	  1 },
					{  /*  1 */  MIPI_PA   ,	 2	,	  3 },
					{  /*  2 */  DATA_NULL ,	 0	,	  0 },
					{  /*  3 */  DATA_NULL ,	 0	,	  0 }
				}		
		},
		//MIPI Data table
		{
				//Rx On Data
				{
					/*			  module	  | 	port		  , 	band34 low 16bit	,	band34 high 16bit	,	band39 low 16bit   ,	band39 high 16bit  */
					{  /*  0 */  MIPI_ASM	  | (MIPI_PORT1<<15),				 0x3800,				0x0B5C, 			   0x3800,				  0x0B5C},
					{  /*  1 */  MIPI_ASM	  | (MIPI_PORT1<<15),				 0x0C00,				0x0B40, 			   0x0B00,				  0x0B40},
					{  /*  2 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  3 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  4 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  5 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  6 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  7 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  8 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  9 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  0 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  1 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0}
				},
				//Rx Off Data
				{
					/*			  module	  | 	port		  , 	band34 low 16bit	,	band34 high 16bit	,	band39 low 16bit   ,	band39 high 16bit  */
					{  /*  0 */  MIPI_ASM	  | (MIPI_PORT1<<15),				 0x0000,				0x0B40, 			   0x0000,				  0x0B40},
					{  /*  1 */  MIPI_ASM	  | (MIPI_PORT1<<15),				 0x3800,				0x0B5C, 			   0x3800,				  0x0B5C},
					{  /*  2 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  3 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  4 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  5 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  6 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  7 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  8 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  9 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  0 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  1 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0}
				},			
				//Tx On Data
				{
					/*			  module	  | 	port		  , 	band34 low 16bit	,	band34 high 16bit	,	band39 low 16bit   ,	band39 high 16bit  */
					{  /*  0 */  MIPI_PA	  | (MIPI_PORT0<<15),		 PA_REG_NUM_B34,		   PA_SEL_FLAG, 	   PA_REG_NUM_B39,			 PA_SEL_FLAG},
					{  /*  1 */  MIPI_ASM	  | (MIPI_PORT1<<15),				 0x3800,				0x0B5C, 			   0x3800,				  0x0B5C},
					{  /*  2 */  MIPI_ASM	  | (MIPI_PORT1<<15),				 0x0400,				0x0B40, 			   0x0400,				  0x0B40},
					{  /*  3 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  4 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  5 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  6 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  7 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  8 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  9 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  0 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  1 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0}
				},			  
			//Tx Off Data
				{ 
					/*			  module	  | 	port		  , 	band34 low 16bit	,	band34 high 16bit	,	band39 low 16bit   ,	band39 high 16bit  */
					{  /*  0 */  MIPI_ASM	  | (MIPI_PORT1<<15),				 0x0000,				0x0B40, 			   0x0000,				  0x0B40},
					{  /*  2 */  MIPI_ASM	  | (MIPI_PORT1<<15),				 0x3800,				0x0B5C, 			   0x3800,				  0x0B5C},
					{  /*  1 */  MIPI_PA	  | (MIPI_PORT0<<15),				 0x0000,				0x0F40, 			   0x0000,				  0x0F40},
					{  /*  3 */  MIPI_PA	  | (MIPI_PORT0<<15),				 0x0000,				0x0F41, 			   0x0000,				  0x0F41},
					{  /*  4 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  5 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  6 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  7 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  8 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  9 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  0 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0},
					{  /*  1 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0}
				}			  
		},
		//PA MODE SETTING
		{		  
				{
					/*			  module	  | 	port		  , 	band34 low 16bit	,	band34 high 16bit	,	band39 low 16bit   ,	band39 high 16bit  */
					{  /*  0 */  MIPI_PA	  | (MIPI_PORT0<<15),				   0x0C00,				  0x0F40,				 0x0C00,				0x0F40},//FOR PA HIGH MODE
					{  /*  1 */  MIPI_PA	  | (MIPI_PORT0<<15),				   0x1F00,				  0x0F41,				 0x1F00,				0x0F41},//FOR PA HIGH MODE
					{  /*  2 */  MIPI_PA	  | (MIPI_PORT0<<15),				   0xDD00,				  0x0F42,				 0xDB00,				0x0F42},//FOR PA HIGH MODE
					{  /*  3 */  MIPI_PA	  | (MIPI_PORT0<<15),				   0x2500,				  0x0F43,				 0x2500,				0x0F43},//FOR PA HIGH MODE
					{  /*  4 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0}//FOR PA HIGH MODE
					
			  },
				{
					/*			  module	  | 	port		  , 	band34 low 16bit	,	band34 high 16bit	,	band39 low 16bit   ,	band39 high 16bit  */
					{  /*  0 */  MIPI_PA	  | (MIPI_PORT0<<15),				   0x0C00,				  0x0F40,				 0x0C00,				0x0F40},//FOR PA MIDDLE MODE
					{  /*  1 */  MIPI_PA	  | (MIPI_PORT0<<15),				   0x1F00,				  0x0F41,				 0x1F00,				0x0F41},//FOR PA MIDDLE MODE
					{  /*  2 */  MIPI_PA	  | (MIPI_PORT0<<15),				   0x5800,				  0x0F42,				 0x8800,				0x0F42},//FOR PA MIDDLE MODE
					{  /*  3 */  MIPI_PA	  | (MIPI_PORT0<<15),				   0x2400,				  0x0F43,				 0x2000,				0x0F43},//FOR PA MIDDLE MODE
					{  /*  4 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0}//FOR PA MIDDLE MODE
				},
				{
					/*			  module	  | 	port		  , 	band34 low 16bit	,	band34 high 16bit	,	band39 low 16bit   ,	band39 high 16bit  */
					{  /*  0 */  MIPI_PA	  | (MIPI_PORT0<<15),				   0x0C00,				  0x0F40,				 0x0C00,				0x0F40},//FOR PA LOW MODE
					{  /*  1 */  MIPI_PA	  | (MIPI_PORT0<<15),				   0x1F00,				  0x0F41,				 0x1F00,				0x0F41},//FOR PA LOW MODE
					{  /*  2 */  MIPI_PA	  | (MIPI_PORT0<<15),				   0xC600,				  0x0F42,				 0xC600,				0x0F42},//FOR PA LOW MODE
					{  /*  3 */  MIPI_PA	  | (MIPI_PORT0<<15),				   0x0000,				  0x0F43,				 0x0000,				0x0F43},//FOR PA LOW MODE
					{  /*  4 */  DATA_NULL	  | 		 (0<<15),					  0,					 0, 					0,					   0}//FOR PA LOW MODE
				}	
		}
	
};

T_MIPI_CUSTOMIZATION_STRUCT * tl1CustomMipiData[TL1_CUSTOM_TOTAL_SET_NUMS] =
{
		&tl1CustomMipiData_set0, //evb
		&tl1CustomMipiData_set0, //sku6
		&tl1CustomMipiData_set0, //sku5
		&tl1CustomMipiData_set0, //sku3
		&tl1CustomMipiData_set0, //sku2
		&tl1CustomMipiData_set0  //sku1
};

#else

extern kal_uint16 TDSCDMA_DRDI_REMAP_ENABLE;
extern kal_uint16 TDS_DRDI_STATUS_DEFAULT[];
extern kal_uint16 gTd_DRDI_Array_len;

#endif




#endif /* _TL1D_CUSTOM_DRDI_H_*/
