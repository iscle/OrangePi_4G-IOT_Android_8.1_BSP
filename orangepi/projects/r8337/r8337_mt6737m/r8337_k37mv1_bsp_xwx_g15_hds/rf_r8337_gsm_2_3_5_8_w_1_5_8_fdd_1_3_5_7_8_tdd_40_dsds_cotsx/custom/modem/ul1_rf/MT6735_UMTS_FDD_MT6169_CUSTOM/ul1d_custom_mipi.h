/*****************************************************************************
*  Copyright Statement:
*  --------------------
*  This software is protected by Copyright and the information contained
*  herein is confidential. The software may not be copied and the information
*  contained herein may not be used or disclosed except with the written
*  permission of MediaTek Inc. (C) 2005
*
*  BY OPENING THIS FILE, BUYER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
*  THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
*  RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO BUYER ON
*  AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
*  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
*  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
*  NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
*  SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
*  SUPPLIED WITH THE MEDIATEK SOFTWARE, AND BUYER AGREES TO LOOK ONLY TO SUCH
*  THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. MEDIATEK SHALL ALSO
*  NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE RELEASES MADE TO BUYER'S
*  SPECIFICATION OR TO CONFORM TO A PARTICULAR STANDARD OR OPEN FORUM.
*
*  BUYER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND CUMULATIVE
*  LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
*  AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
*  OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY BUYER TO
*  MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE. 
*
*  THE TRANSACTION CONTEMPLATED HEREUNDER SHALL BE CONSTRUED IN ACCORDANCE
*  WITH THE LAWS OF THE STATE OF CALIFORNIA, USA, EXCLUDING ITS CONFLICT OF
*  LAWS PRINCIPLES.  ANY DISPUTES, CONTROVERSIES OR CLAIMS ARISING THEREOF AND
*  RELATED THERETO SHALL BE SETTLED BY ARBITRATION IN SAN FRANCISCO, CA, UNDER
*  THE RULES OF THE INTERNATIONAL CHAMBER OF COMMERCE (ICC).
*
*****************************************************************************/

/*****************************************************************************
 *
 * Filename:
 * ---------
 *	ul1d_custom_mipi.h
 *
 * Project:
 * --------
 * MT6290
 *
 * Description:
 * ------------
 *   
 *
 * Author:
 * -------
 * -------
 *
 ****************************************************************************/

#ifndef  _UL1D_MIPI_DATA_H_
#define  _UL1D_MIPI_DATA_H_

/*===============================================================================*/
#include "kal_general_types.h"
#include "ul1d_rf_cid.h"
/*===============================================================================*/

#if (IS_3G_MIPI_SUPPORT)

/*Enable/Disable 3G FDD MIPI function*/
#define IS_3G_MIPI_ENABLE  1

/////////////////////////
//   MIPI Define
/////////////////////////

/////////////////////////
//Should not modify
//(2ASM + 4ATs for RX main & diversity; RX main:2ATs, RX diversity:2ATs)
#define UL1_MIPI_RX_EVENT_NUM    24
#define UL1_MIPI_RX_DATA_NUM     28

//(1ASM + 2AT + PA for TX)
#define UL1_MIPI_TX_EVENT_NUM    16
#define UL1_MIPI_TX_DATA_NUM     18

#define UL1_MIPI_TPC_EVENT_NUM   8
#define UL1_MIPI_TPC_DATA_NUM   40

//(For MIPI PA META UI tuning)
#define UL1_META_MIPI_PA_SECTION_NUM       8
#define UL1_META_MIPI_PA_SECTION_DATA_NUM  4

//(PA setting of TPC set)
#define UL1_MIPI_PA_SECTION_NUM       8
#define UL1_MIPI_PA_SECTION_DATA_NUM  5

#define MIPI_SUBBAND_NUM   5    /*Max subband number is 5*/
////////////////////////

//the first band is UMTSBandNone, actually support 5 bands
#define UL1_MIPI_MAX_BAND_NUM   (5+1)

#define MIPI_DATA_NULL      0x0000

//port slectiong
#define UL1_MIPI_PORT0          0x0003 /*3G module device D*/
#define UL1_MIPI_PORT1          0x0002 /*3G module device C*/

#define MIPI_PORT0_MSK      (0x1<<(UL1_MIPI_PORT0))
#define MIPI_PORT1_MSK      (0x1<<(UL1_MIPI_PORT1))

#define MIPI_DATA_IDX(start,stop)  (((stop)<<8)|(start))

//event type
#define MIPI_TRX_ON         0x0001
#define MIPI_TRX_OFF        0x0002
#define MIPI_TPC_SET        0x0003 
#define MIPI_EVENT_NULL     0x0000

//element type
#define MIPI_NULL           0x0000
#define MIPI_ASM            0x0001
#define MIPI_ANT            0x0002
#define MIPI_PA             0x0003

#define UMTS_MIPI_END       0xFFFF

//data write seq. format
#define SEQ_NULL             0x0000
#define REG_0_W              0x0001
#define REG_W                0x0002
#define REG_W_EXT_1ST        0x0003
#define REG_W_EXT_BYTE       0x0004
#define WAIT                 0x0005

//TPC PA SECTION DATA PATTERN
#define MIPI_PA_SECTION_DATA0   0x10000000
#define MIPI_PA_SECTION_DATA1   0x10000001
#define MIPI_PA_SECTION_DATA2   0x10000002
#define MIPI_PA_SECTION_DATA3   0x10000003

#define MIPI_OFFSET         (77)   //~20us, 77 chips

#define US2CHIPCNT(us)       ((us)*3.84)

#define MIPI_MAX_INITIAL_CW_NUM   30

#define MIPI_MAX_SLEEP_CW_NUM     20

typedef struct
{
   kal_uint16 mipi_data_st;//mipi data start index
   kal_uint16 mipi_data_sp;//mipi data stop index
}UL1_MIPI_DATA_STSP;

typedef struct
{
   kal_uint16 addr;
   kal_uint32 data;
}UL1_MIPI_ADDR_DATA_EXPAND_TABLE_T;

typedef struct
{
   kal_uint16 mipi_elm_type;     //mipi element type
   UL1_MIPI_DATA_STSP mipi_data_stsp;
   kal_uint16 mipi_evt_type;     //event type
   kal_uint32 mipi_evt_offset;   //event offset
}UL1_MIPI_EVENT_TABLE_T;

typedef struct
{
   kal_uint16 mipi_subband_freq; // Port where data to send
   UL1_MIPI_ADDR_DATA_EXPAND_TABLE_T mipi_data; // mipi data
}UL1_MIPI_DATA_EXPAND_TABLE_T;       //expanded by sub-freq

typedef struct
{
   kal_uint16 mipi_elm_type;                                     //mipi element type
   kal_uint16 mipi_port_sel;                                     //0:for Port0, 1:for Port1 
   kal_uint16 mipi_data_seq;                                     // data write sequence format
   kal_uint16 mipi_usid;                                         //mipi USID   
   UL1_MIPI_DATA_EXPAND_TABLE_T mipi_subband_data[MIPI_SUBBAND_NUM]; // mipi data
}UL1_MIPI_DATA_SUBBAND_TABLE_T;

typedef struct
{
   kal_uint16 mipi_elm_type;                                     //mipi element type
   kal_uint16 mipi_port_sel;                                     //0:for Port0, 1:for Port1 
   kal_uint16 mipi_data_seq;                                     // data write sequence format
   kal_uint16 mipi_usid;                                         //mipi USID      
   UL1_MIPI_ADDR_DATA_EXPAND_TABLE_T mipi_data;                  // mipi data
}UL1_MIPI_DATA_TABLE_T;

typedef struct
{
   UL1_MIPI_EVENT_TABLE_T         umts_mipi_tpc_event[UL1_MIPI_TPC_EVENT_NUM];
   UL1_MIPI_DATA_SUBBAND_TABLE_T  umts_mipi_tpc_data[UL1_MIPI_TPC_DATA_NUM];    
}UL1_UMTS_MIPI_TPC_T;

typedef struct
{
   kal_uint16 mipi_subband_freq;
   kal_uint32 mipi_pa_tpc_data[UL1_MIPI_PA_SECTION_NUM][UL1_MIPI_PA_SECTION_DATA_NUM];
}UL1_MIPI_PA_TPC_SECTION_TABLE_T;



extern UL1_MIPI_DATA_TABLE_T          UMTS_MIPI_INITIAL_CW[];
extern UL1_MIPI_DATA_TABLE_T          UMTS_MIPI_INITIAL_CW_set0[];
extern UL1_MIPI_DATA_TABLE_T          UMTS_MIPI_INITIAL_CW_set1[];
extern UL1_MIPI_DATA_TABLE_T          UMTS_MIPI_SLEEP_CW[];
extern UL1_MIPI_EVENT_TABLE_T*        UMTS_MIPI_RX_EVENT_TABLE[];
extern UL1_MIPI_EVENT_TABLE_T*        UMTS_MIPI_RX_EVENT_TABLE_set0[];
extern UL1_MIPI_EVENT_TABLE_T*        UMTS_MIPI_RX_EVENT_TABLE_set1[];
extern UL1_MIPI_DATA_SUBBAND_TABLE_T* UMTS_MIPI_RX_DATA_TABLE[];
extern UL1_MIPI_DATA_SUBBAND_TABLE_T* UMTS_MIPI_RX_DATA_TABLE_set0[];
extern UL1_MIPI_DATA_SUBBAND_TABLE_T* UMTS_MIPI_RX_DATA_TABLE_set1[];
extern UL1_MIPI_EVENT_TABLE_T*        UMTS_MIPI_TX_EVENT_TABLE[];
extern UL1_MIPI_EVENT_TABLE_T*        UMTS_MIPI_TX_EVENT_TABLE_set0[];
extern UL1_MIPI_EVENT_TABLE_T*        UMTS_MIPI_TX_EVENT_TABLE_set1[];
extern UL1_MIPI_DATA_SUBBAND_TABLE_T* UMTS_MIPI_TX_DATA_TABLE[];
extern UL1_MIPI_DATA_SUBBAND_TABLE_T* UMTS_MIPI_TX_DATA_TABLE_set0[];
extern UL1_MIPI_DATA_SUBBAND_TABLE_T* UMTS_MIPI_TX_DATA_TABLE_set1[];
extern UL1_MIPI_EVENT_TABLE_T*        UMTS_MIPI_TPC_EVENT_TABLE[];
extern UL1_MIPI_DATA_SUBBAND_TABLE_T*         UMTS_MIPI_TPC_DATA_TABLE[];
extern UL1_MIPI_DATA_TABLE_T*         UMTS_MIPI_ASM_ISOLATION_DATA_TABLE[];

extern UL1_UMTS_MIPI_TPC_T*           UMTS_MIPI_TPC_TABLE[];
extern UL1_UMTS_MIPI_TPC_T*           UMTS_MIPI_TPC_TABLE_set0[];
extern UL1_UMTS_MIPI_TPC_T*           UMTS_MIPI_TPC_TABLE_set1[];

//#define MIPI_INITIAL_CW_NUM (sizeof(UMTS_MIPI_INITIAL_CW)/sizeof(MIPI_DATA_TABLE_T))
//#define MIPI_SLEEP_CW_NUM (sizeof(MIPI_SLEEP_CW)/sizeof(MIPI_DATA_TABLE_T))
    
/////////////////////////
#endif


#endif /* End of #ifndef _UL1D_MIPI_DATA_H_ */
