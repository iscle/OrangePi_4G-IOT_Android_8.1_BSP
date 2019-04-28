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
 *	el1_rf_custom.h
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
 *
 *
 *============================================================================*/

#ifndef  _LTE_CUSTOM_MIPI_H_
#define  _LTE_CUSTOM_MIPI_H_

/*===============================================================================*/
#include "kal_general_types.h"
#include "mml1_custom_mipi.h"

/*===============================================================================*/

/////////////////////////
// define MIPI function enable
/////////////////////////
#define IS_MIPI_FRONT_END_ENABLE  1

/////////////////////////
// define MIPI bypass feature enable
/////////////////////////
#define IS_MIPI_BYPASS_FEATURE_ENABLE  1


///////////////////////////////////////////////////////////////////
//define MIPI T/R ON/OFF event timing for build time check
///////////////////////////////////////////////////////////////////
/*MIPI ASM     */
/*FDD RX ON    */
#define  LTE_FDD_MIPI_ASM_RX_ON0   US2OFFCNT(10)
#define  LTE_FDD_MIPI_ASM_RX_ON1   0

/*MIPI ASM     */
/*FDD RX OFF    */
#define  LTE_FDD_MIPI_ASM_RX_OFF0  US2OFFCNT(5)

/*MIPI ASM     */
/*TDD RX ON    */
#define  LTE_TDD_MIPI_ASM_RX_ON0   US2OFFCNT(10)
#define  LTE_TDD_MIPI_ASM_RX_ON1   0

/*MIPI ASM     */
/*TDD RX OFF    */
#define  LTE_TDD_MIPI_ASM_RX_OFF0  US2OFFCNT(5)
#define  LTE_TDD_MIPI_ASM_RX_OFF1  0

/*MIPI ASM     */
/*FDD TX ON    */
#define  LTE_FDD_MIPI_ASM_TX_ON0   US2OFFCNT(10)
#define  LTE_FDD_MIPI_ASM_TX_ON1   0

/*MIPI ASM     */
/*FDD TX OFF    */
// We do not turn off ASM for FDD mode

/*MIPI ASM     */
/*TDD TX ON    */
#define  LTE_TDD_MIPI_ASM_TX_ON0   US2OFFCNT(13)
#define  LTE_TDD_MIPI_ASM_TX_ON1   0

/*MIPI ASM     */
/*TDD TX OFF    */
#define  LTE_TDD_MIPI_ASM_TX_OFF0  US2OFFCNT(5)
#define  LTE_TDD_MIPI_ASM_TX_OFF1  0

/*MIPI PA     */
/*FDD TX ON    */
#define  LTE_FDD_MIPI_PA_TX_ON0    US2OFFCNT(12)
#define  LTE_FDD_MIPI_PA_TX_ON1    US2OFFCNT(9)
#define  LTE_FDD_MIPI_PA_TX_ON2    0

/*MIPI PA     */
/*FDD TX OFF    */
#define  LTE_FDD_MIPI_PA_TX_OFF0   US2OFFCNT(20)
#define  LTE_FDD_MIPI_PA_TX_OFF1   0

/*MIPI PA     */
/*TDD TX ON    */
#define  LTE_TDD_MIPI_PA_TX_ON0    US2OFFCNT(13)
#define  LTE_TDD_MIPI_PA_TX_ON1    US2OFFCNT(9)
#define  LTE_TDD_MIPI_PA_TX_ON2    0

/*MIPI PA     */
/*TDD TX OFF    */
#define  LTE_TDD_MIPI_PA_TX_OFF0   US2OFFCNT(20)
#define  LTE_TDD_MIPI_PA_TX_OFF1   0


/*** MIPI BYPASS Feature ***/
#define  LTE_MIPI_BYPASS_BAND_INDICATOR1          LTE_Band38
#define  LTE_MIPI_BYPASS_BAND_INDICATOR2          LTE_Band40
#define  LTE_MIPI_BYPASS_BAND_INDICATOR3          LTE_Band41
#define  LTE_MIPI_BYPASS_BAND_INDICATOR4          LTE_BandNone
#define  LTE_MIPI_BYPASS_BAND_INDICATOR5          LTE_BandNone

/*
Real power difference between bypass path and filter path
Unit: S(6,8) dB
If bypass > filter by 0.5dB, the value is  128 (= 0.5*256)
If bypass < filter by 1.5dB, the value is -384 (=-1.5*256)
*/
#define  LTE_MIPI_BYPASS_BAND_INDICATOR1_POWER_COMP      768
#define  LTE_MIPI_BYPASS_BAND_INDICATOR2_POWER_COMP      768
#define  LTE_MIPI_BYPASS_BAND_INDICATOR3_POWER_COMP      768
#define  LTE_MIPI_BYPASS_BAND_INDICATOR4_POWER_COMP      0
#define  LTE_MIPI_BYPASS_BAND_INDICATOR5_POWER_COMP      0
/*
Expected power difference between bypass path and filter path
Unit: S(6,8) dB
If bypass > filter by 0.5dB, the value is  128 (= 0.5*256)
If bypass < filter by 1.5dB, the value is -384 (=-1.5*256)
*/
#define  LTE_MIPI_BYPASS_BAND_INDICATOR1_COUPLER_COMP    512
#define  LTE_MIPI_BYPASS_BAND_INDICATOR2_COUPLER_COMP    512
#define  LTE_MIPI_BYPASS_BAND_INDICATOR3_COUPLER_COMP    512
#define  LTE_MIPI_BYPASS_BAND_INDICATOR4_COUPLER_COMP    0
#define  LTE_MIPI_BYPASS_BAND_INDICATOR5_COUPLER_COMP    0


/////////////////////////
//   MIPI Define
/////////////////////////
//Should not modify
#define LTE_MIPI_RX_EVENT_NUM         25
#define LTE_MIPI_RX_DATA_NUM          50

#define LTE_MIPI_TX_EVENT_NUM         25
#define LTE_MIPI_TX_DATA_NUM          60

#define LTE_MIPI_TPC_EVENT_NUM        6
#define LTE_MIPI_TPC_DATA_NUM         6

#define LTE_MIPI_SUBBAND_NUM          10
#define LTE_MIPI_SUBBAND_NUM_PER_DATA 5

#define LTE_MIPI_TPC_SECTION_DATA_NUM 5
#define LTE_MIPI_TPC_SECTION_NUM      (8+1)

#define LTE_MIPI_DATA_NULL      0x0000

//port slectiong
#define LTE_MIPI_PORT0          0x0002
#define LTE_MIPI_PORT1          0x0003

//event type
#define LTE_MIPI_TRX_ON         0x0001
#define LTE_MIPI_TRX_OFF        0x0002
#define LTE_MIPI_TPC_SET        0x0003
#define LTE_MIPI_EVENT_NULL     0x0000

//element type
#define LTE_MIPI_NULL           0x0000
#define LTE_MIPI_ASM            0x0001
#define LTE_MIPI_ANT            0x0002
#define LTE_MIPI_PA             0x0003
#define LTE_MIPI_PA_SEC         0x0004
#define LTE_MIPI_END_PATTERN    0xFFFF

//data write seq. format
#define LTE_REG_0_W             MML1_REG_0_W
#define LTE_REG_W               MML1_REG_W
#define LTE_REG_W_EXT_1ST       MML1_REG_W_EXT_1ST
#define LTE_REG_W_EXT_BYTE      MML1_REG_W_EXT_BYTE
#define LTE_REG_W_EXT_END       MML1_REG_W_EXT_END
#define LTE_IMM_BSI_WAIT        MML1_IMM_BSI_WAIT

//TPC PA SECTION DATA PATTERN
#define LTE_MIPI_PA_SECTION_USID    0x30000000
#define LTE_MIPI_PA_SECTION_DATA0   0x10000000
#define LTE_MIPI_PA_SECTION_DATA1   0x10000001
#define LTE_MIPI_PA_SECTION_DATA2   0x10000002
#define LTE_MIPI_PA_SECTION_DATA3   0x10000003
#define LTE_MIPI_PA_SECTION_DATA4   0x10000004
#define LTE_MIPI_PA_SECTION_ADDRESS 0x40000000
#define LTE_MIPI_ET_SECTION_DATA    0x20000000


#define US2OFFCNT(us)       ((us)*26)
#define WAITUSCNT(us)       ((us)*52)

typedef struct
{
   kal_uint16 mipi_data_st;                         // mipi data start index
   kal_uint16 mipi_data_sp;                         // mipi data stop index
} LTE_MIPI_DATA_STSP;

typedef struct
{
   kal_uint16 mipi_elm_type;                        // mipi element type
   LTE_MIPI_DATA_STSP mipi_data_stsp;
   kal_uint16 mipi_evt_type;                        // event type
   kal_uint32 mipi_evt_offset;                      // event offset
}LTE_MIPI_EVENT_TABLE_T;

typedef struct
{
   kal_uint16 mipi_subband_freq;                    // Port where data to send
   kal_uint16 mipi_addr;                            // mipi address
   kal_uint32 mipi_data;                            // mipi data
}LTE_MIPI_DATA_EXPAND_TABLE_T;                      // expanded by sub-freq

typedef struct
{
   kal_uint16 mipi_addr;                            // Port where data to send
   kal_uint32 mipi_data;                            // mipi data
}LTE_MIPI_adda_DATA_EXPAND_TABLE_T;                 // expanded by sub-freq

typedef struct
{
   kal_uint16 mipi_elm_type;                        // mipi element type
   kal_uint16 mipi_port_sel;                        // 0:for Port0, 1:for Port1 
   kal_uint16 mipi_data_seq;                        // data write sequence format
   kal_uint16 mipi_usid;
   LTE_MIPI_DATA_EXPAND_TABLE_T mipi_subband_data[LTE_MIPI_SUBBAND_NUM_PER_DATA];  // mipi data
}LTE_MIPI_DATA_SUBBAND_TABLE_T;

typedef struct
{
   kal_uint16 mipi_elm_type;                        // mipi element type
   kal_uint16 mipi_port_sel;                        // 0:for Port0, 1:for Port1 
   kal_uint16 mipi_data_seq;                        // data write sequence format
   kal_uint32 mipi_usid;                            // mipi usid
   kal_uint32 mipi_addr;                            // mipi addr
   kal_uint32 mipi_data;                            // mipi data
}LTE_MIPI_DATA_TABLE_T;

typedef struct
{
   kal_uint16 mipi_elm_type;                        // mipi element type
   kal_uint16 mipi_port_sel;                        // 0:for Port0, 1:for Port1 
   kal_uint16 mipi_data_seq;                        // data write sequence format
   kal_uint16 mipi_usid;                            // usid
   LTE_MIPI_adda_DATA_EXPAND_TABLE_T mipi_ad_data;  // mipi address & data
   kal_uint32 mipi_wait_time;                       // mipi data
}LTE_MIPI_IMM_DATA_TABLE_T;

typedef struct
{
   LTE_MIPI_adda_DATA_EXPAND_TABLE_T mipi_tpc_sec_data[LTE_MIPI_TPC_SECTION_DATA_NUM];
}LTE_MIPI_TPC_SECTION_DATA_T;

typedef struct
{
   kal_uint16 mipi_subband_freq;
   kal_uint16 mipi_usid;
   LTE_MIPI_TPC_SECTION_DATA_T mipi_tpc_section_table[LTE_MIPI_TPC_SECTION_NUM];
}LTE_MIPI_TPC_SECTION_TABLE_T;

/////////////////////////

#endif /* End of #ifndef _LTE_CUSTOM_MIPI_H_ */
