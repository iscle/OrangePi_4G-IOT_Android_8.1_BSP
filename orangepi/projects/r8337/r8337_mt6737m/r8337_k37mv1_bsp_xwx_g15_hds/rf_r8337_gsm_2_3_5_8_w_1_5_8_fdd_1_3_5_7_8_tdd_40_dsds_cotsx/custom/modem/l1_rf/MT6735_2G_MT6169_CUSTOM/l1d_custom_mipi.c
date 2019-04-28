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
 *   l1d_custom_mipi.c
 *
 * Project:
 * --------
 *   MT6169
 *
 * Description:
 * ------------
 *   MT6169 MIPI Table
 *
 * Author:
 * -------
 *
 *
 *==============================================================================
 *
 *******************************************************************************/

#include "l1d_cid.h"
#include "m12190.h"
#include "l1d_mipi_data.h"
#include "l1d_custom_mipi.h"
#include "l1d_mipi.h"
#include "mml1_custom_mipi.h"

/*===============================================================================================*/
#if IS_MIPI_SUPPORT

/*----------------------------------------*/
/* MIPI Control Table for Qual Band       */
/*----------------------------------------*/
sGGE_MIPI_CTRL_TABLE_BAND GGE_MIPI_CTRL_TABLE_GSM850=
{
   {  
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_rx_ctrl_table.mipi_rxctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_ASM ,  {   0  ,  1   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_RX_ON0   },
         {  /*  1 */  GGE_MIPI_ASM ,  {   2  ,  2   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_RX_ON1   },
         {  /*  2 */  GGE_MIPI_ASM ,  {   3  ,  4   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_RX_OFF0  },
         {  /*  3 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  4 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
      },
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_rx_ctrl_table.mipi_rxctrl_data[] */
      {  	/*    No.    elm type  ,  port select    	   ,  data format      	        ,usid		      ,{  { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    251           , 0x1C, 0x38 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    251           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    251           , 0x00, 0x0E }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    251           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    251           , 0x1C, 0xB8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   },
   {  
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_tx_ctrl_table.mipi_txctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON0   },
         {  /*  1 */  GGE_MIPI_PA  ,  {   1  ,  1   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_TX_OFF0  },
         {  /*  2 */  GGE_MIPI_ASM ,  {   2  ,  3   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON1   },
         {  /*  3 */  GGE_MIPI_ASM ,  {   4  ,  4   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON2   },
         {  /*  4 */  GGE_MIPI_ASM ,  {   5  ,  6   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_TX_OFF1  },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
      },
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_tx_ctrl_table.mipi_txctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format                 ,usid		   , {  { subband arfcn    , addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    251           , 0x01, GGE_MIPI_PA_G8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    251           , 0x01, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    251           , 0x1C, 0x38 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    251           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    251           , 0x00, 0x0A }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    251           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    251           , 0x1C, 0xB8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      },
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_tx_ctrl_table.mipi_txctrl_pa_data */
      {  /* GMSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x23        ,   0x23        ,   0x23        ,   0x23        ,   0x23           },
         /* 8PSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x27        ,   0x27        ,   0x27        ,   0x27        ,   0x27           },
      },
   },
   {
   #if IS_MIPI_INTERSLOT_RAMPING_OPTIMIZE_SUPPORT
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_txmid_ctrl_table.mipi_txmidctrl_event[][] */
      {  
         ///////////////////////////////////////////////////////////////////////////////////
         /* Only the "element type", "data idx", "event type" in GMSK->GMSK,              */
         /* and "event timing" in GMSK->GMSK, 8PSK->8PSK, 8PSK->GMSK, GMSK->8PSK are used.*/
         ///////////////////////////////////////////////////////////////////////////////////
         /* GMSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_GG },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_88 },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_8G },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* GMSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_G8 },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
      },
   #else
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_txmid_ctrl_table.mipi_txmidctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0 },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0        },
      },
   #endif   
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_txmid_ctrl_table.mipi_txmidctrl_data[] */
      {  /* No.       elm type  ,  port select        ,  data format                 ,usid		   , {  { subband arfcn    , addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    251           , 0x01, GGE_MIPI_PA_G8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   }
};

sGGE_MIPI_CTRL_TABLE_BAND GGE_MIPI_CTRL_TABLE_GSM900=
{
   {
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_rx_ctrl_table.mipi_rxctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_ASM ,  {   0  ,  1   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_RX_ON0   },
         {  /*  1 */  GGE_MIPI_ASM ,  {   2  ,  2   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_RX_ON1   },
         {  /*  2 */  GGE_MIPI_ASM ,  {   3  ,  4   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_RX_OFF0  },
         {  /*  3 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  4 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
      },
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_rx_ctrl_table.mipi_rxctrl_data[] */
      {  	/*    No.    elm type  ,  port select    	   ,  data format      	        ,usid		      ,{  { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    124           , 0x1C, 0x38 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    124           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    124           , 0x00, 0x0D }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    124           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    124           , 0x1C, 0xB8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   },
   {
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_tx_ctrl_table.mipi_txctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON0   },
         {  /*  1 */  GGE_MIPI_PA  ,  {   1  ,  1   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_TX_OFF0  },
         {  /*  2 */  GGE_MIPI_ASM ,  {   2  ,  3   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON1   },
         {  /*  3 */  GGE_MIPI_ASM ,  {   4  ,  4   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON2   },
         {  /*  4 */  GGE_MIPI_ASM ,  {   5  ,  6   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_TX_OFF1  },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
      },
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_tx_ctrl_table.mipi_txctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format                 ,usid		  , {  { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    124           , 0x01, GGE_MIPI_PA_G8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    124           , 0x01, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    124           , 0x1C, 0x38 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    124           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    124           , 0x00, 0x0A }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    124           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    124           , 0x1C, 0xB8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      },
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_tx_ctrl_table.mipi_txctrl_pa_data */
      {  /* GMSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x23        ,   0x23        ,   0x23        ,   0x23        ,   0x23           },
         /* 8PSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x27        ,   0x27        ,   0x27        ,   0x27        ,   0x27           },
      }
   },
   {
   #if IS_MIPI_INTERSLOT_RAMPING_OPTIMIZE_SUPPORT   
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_txmid_ctrl_table.mipi_txmidctrl_event[][] */
      {  
         ///////////////////////////////////////////////////////////////////////////////////
         /* Only the "element type", "data idx", "event type" in GMSK->GMSK,              */
         /* and "event timing" in GMSK->GMSK, 8PSK->8PSK, 8PSK->GMSK, GMSK->8PSK are used.*/
         ///////////////////////////////////////////////////////////////////////////////////      
         /* GMSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_GG },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_88 },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
          /* 8PSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_8G },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* GMSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_G8 },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
      },
   #else   
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_txmid_ctrl_table.mipi_txmidctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0 },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0        },
      },
   #endif
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_txmid_ctrl_table.mipi_txmidctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format                 ,usid		  , {  { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    124           , 0x01, GGE_MIPI_PA_G8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   }
};

sGGE_MIPI_CTRL_TABLE_BAND GGE_MIPI_CTRL_TABLE_DCS1800_NOTCH_SWITCH=
{
   {
      /* GGE_MIPI_CTRL_TABLE_DCS1800_NOTCH_SWITCH.mipi_rx_ctrl_table.mipi_rxctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  2 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  3 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  4 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
      },
      /* GGE_MIPI_CTRL_TABLE_DCS1800_NOTCH_SWITCH.mipi_rx_ctrl_table.mipi_rxctrl_data[] */
      {  	/*    No.    elm type  ,  port select    	   ,  data format      	        ,usid		      ,{  { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   },
   {
      /* GGE_MIPI_CTRL_TABLE_DCS1800_NOTCH_SWITCH.mipi_tx_ctrl_table.mipi_txctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  2 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  3 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  4 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
      },
      /* GGE_MIPI_CTRL_TABLE_DCS1800_NOTCH_SWITCH.mipi_tx_ctrl_table.mipi_txctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format                 ,usid		  , {  { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      },
      /* GGE_MIPI_CTRL_TABLE_DCS1800_NOTCH_SWITCH.mipi_tx_ctrl_table.mipi_txctrl_pa_data */
      {  /* GMSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x00        ,   0x00        ,   0x00        ,   0x00        ,   0x00           },
         /* 8PSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x00        ,   0x00        ,   0x00        ,   0x00        ,   0x00           },
      }       
   },
   {
   #if IS_MIPI_INTERSLOT_RAMPING_OPTIMIZE_SUPPORT
      /* GGE_MIPI_CTRL_TABLE_DCS1800_NOTCH_SWITCH.mipi_txmid_ctrl_table.mipi_txmidctrl_event[][] */
      {  
         ///////////////////////////////////////////////////////////////////////////////////
         /* Only the "element type", "data idx", "event type" in GMSK->GMSK,              */
         /* and "event timing" in GMSK->GMSK, 8PSK->8PSK, 8PSK->GMSK, GMSK->8PSK are used.*/
         ///////////////////////////////////////////////////////////////////////////////////      
         /* GMSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* GMSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
      },
   #else
      /* GGE_MIPI_CTRL_TABLE_DCS1800_NOTCH_SWITCH.mipi_txmid_ctrl_table.mipi_txmidctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0        },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0        },
      },
   #endif
      /* GGE_MIPI_CTRL_TABLE_DCS1800_NOTCH_SWITCH.mipi_txmid_ctrl_table.mipi_txmidctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format                 ,usid		  , {  { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   }
};

sGGE_MIPI_CTRL_TABLE_BAND GGE_MIPI_CTRL_TABLE_DCS1800=
{
   {
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_rx_ctrl_table.mipi_rxctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_ASM ,  {   0  ,  1   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_RX_ON0   },
         {  /*  1 */  GGE_MIPI_ASM ,  {   2  ,  2   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_RX_ON1   },
         {  /*  2 */  GGE_MIPI_ASM ,  {   3  ,  4   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_RX_OFF0  },
         {  /*  3 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  4 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
      },
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_rx_ctrl_table.mipi_rxctrl_data[] */
      {  	/*    No.    elm type  ,  port select    	   ,  data format      	        ,usid		      ,{  { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    885           , 0x1C, 0x38 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    885           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    885           , 0x00, 0x01 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    885           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    885           , 0x1C, 0xB8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   },
   {
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_tx_ctrl_table.mipi_txctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON0   },
         {  /*  1 */  GGE_MIPI_PA  ,  {   1  ,  1   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_TX_OFF0  },
         {  /*  2 */  GGE_MIPI_ASM ,  {   2  ,  3   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON1   },
         {  /*  3 */  GGE_MIPI_ASM ,  {   4  ,  4   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON2   },
         {  /*  4 */  GGE_MIPI_ASM ,  {   5  ,  6   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_TX_OFF1  },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
      },
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_tx_ctrl_table.mipi_txctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format                 ,usid		  , {  { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    885           , 0x01, GGE_MIPI_PA_G8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    885           , 0x01, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    885           , 0x1C, 0x38 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    885           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    885           , 0x00, 0x08 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    885           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    885           , 0x1C, 0xB8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      },
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_tx_ctrl_table.mipi_txctrl_pa_data */
      {  /* GMSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x33        ,   0x33        ,   0x33        ,   0x33        ,   0x33           },
         /* 8PSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x37        ,   0x37        ,   0x37        ,   0x37        ,   0x37           },
      }
   },
   {
   #if IS_MIPI_INTERSLOT_RAMPING_OPTIMIZE_SUPPORT
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_txmid_ctrl_table.mipi_txmidctrl_event[][] */
      {  
         ///////////////////////////////////////////////////////////////////////////////////
         /* Only the "element type", "data idx", "event type" in GMSK->GMSK,              */
         /* and "event timing" in GMSK->GMSK, 8PSK->8PSK, 8PSK->GMSK, GMSK->8PSK are used.*/
         ///////////////////////////////////////////////////////////////////////////////////      
         /* GMSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_GG },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_88 },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_8G },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* GMSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_G8 },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
      },
   #else
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_txmid_ctrl_table.mipi_txmidctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0 },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0        },
      },
   #endif
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_txmid_ctrl_table.mipi_txmidctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format                 ,usid		  , {  { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    885           , 0x01, GGE_MIPI_PA_G8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   }
};


sGGE_MIPI_CTRL_TABLE_BAND GGE_MIPI_CTRL_TABLE_PCS1900=
{
   {
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_rx_ctrl_table.mipi_rxctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_ASM ,  {   0  ,  1   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_RX_ON0   },
         {  /*  1 */  GGE_MIPI_ASM ,  {   2  ,  2   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_RX_ON1   },
         {  /*  2 */  GGE_MIPI_ASM ,  {   3  ,  4   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_RX_OFF0  },
         {  /*  3 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  4 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
      },
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_rx_ctrl_table.mipi_rxctrl_data[] */
      {  	/*    No.    elm type  ,  port select    	   ,  data format      	        ,usid		      ,{  { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    810           , 0x1C, 0x38 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    810           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    810           , 0x00, 0x10 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    810           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    810           , 0x1C, 0xB8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   },
   {
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_tx_ctrl_table.mipi_txctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON0   },
         {  /*  1 */  GGE_MIPI_PA  ,  {   1  ,  1   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_TX_OFF0  },
         {  /*  2 */  GGE_MIPI_ASM ,  {   2  ,  3   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON1   },
         {  /*  3 */  GGE_MIPI_ASM ,  {   4  ,  4   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON2   },
         {  /*  4 */  GGE_MIPI_ASM ,  {   5  ,  6   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_TX_OFF1  },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
      },
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_tx_ctrl_table.mipi_txctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format                 ,usid		  , {  { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    810           , 0x01, GGE_MIPI_PA_G8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    810           , 0x01, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    810           , 0x1C, 0x38 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    810           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    810           , 0x00, 0x08 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    810           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT1     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    810           , 0x1C, 0xB8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      },
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_tx_ctrl_table.mipi_txctrl_pa_data */
      {  /* GMSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x33        ,   0x33        ,   0x33        ,   0x33        ,   0x33           },
         /* 8PSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x37        ,   0x37        ,   0x37        ,   0x37        ,   0x37           },
      }       
   },
   {
   #if IS_MIPI_INTERSLOT_RAMPING_OPTIMIZE_SUPPORT   
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_txmid_ctrl_table.mipi_txmidctrl_event[][] */
      {  
         ///////////////////////////////////////////////////////////////////////////////////
         /* Only the "element type", "data idx", "event type" in GMSK->GMSK,              */
         /* and "event timing" in GMSK->GMSK, 8PSK->8PSK, 8PSK->GMSK, GMSK->8PSK are used.*/
         ///////////////////////////////////////////////////////////////////////////////////      
         /* GMSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_GG },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_88 },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_8G },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* GMSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_G8 },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
      },
   #else   
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_txmid_ctrl_table.mipi_txmidctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0 },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0        },
      },
   #endif      
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_txmid_ctrl_table.mipi_txmidctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format                 ,usid		  , {  { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    810           , 0x01, GGE_MIPI_PA_G8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   }
};

   sGGE_MIPIEVENT* GGE_MIPI_CTRL_TABLE_RX_EVENT[] =
   {  
      0,                                                                                        /* FrequencyBand400  */
      &GGE_MIPI_CTRL_TABLE_GSM850.mipi_rx_ctrl_table.mipi_rxctrl_event[0],                      /* FrequencyBand850  */
      &GGE_MIPI_CTRL_TABLE_GSM900.mipi_rx_ctrl_table.mipi_rxctrl_event[0],                      /* FrequencyBand900  */
      &GGE_MIPI_CTRL_TABLE_DCS1800.mipi_rx_ctrl_table.mipi_rxctrl_event[0],                     /* FrequencyBand1800 */
      &GGE_MIPI_CTRL_TABLE_PCS1900.mipi_rx_ctrl_table.mipi_rxctrl_event[0],                     /* FrequencyBand1900 */   
   };
   
   sGGE_MIPIDATA_SUBBAND* GGE_MIPI_CTRL_TABLE_RX_DATA[] =
   {  
      0,                                                                                        /* FrequencyBand400  */
      &GGE_MIPI_CTRL_TABLE_GSM850.mipi_rx_ctrl_table.mipi_rxctrl_data[0],                       /* FrequencyBand850  */
      &GGE_MIPI_CTRL_TABLE_GSM900.mipi_rx_ctrl_table.mipi_rxctrl_data[0],                       /* FrequencyBand900  */
      &GGE_MIPI_CTRL_TABLE_DCS1800.mipi_rx_ctrl_table.mipi_rxctrl_data[0],                      /* FrequencyBand1800 */
      &GGE_MIPI_CTRL_TABLE_PCS1900.mipi_rx_ctrl_table.mipi_rxctrl_data[0],                      /* FrequencyBand1900 */  
   };
   
   sGGE_MIPIEVENT* GGE_MIPI_CTRL_TABLE_TX_EVENT[] =
   {  
      0,                                                                                        /* FrequencyBand400  */
      &GGE_MIPI_CTRL_TABLE_GSM850.mipi_tx_ctrl_table.mipi_txctrl_event[0],                      /* FrequencyBand850  */
      &GGE_MIPI_CTRL_TABLE_GSM900.mipi_tx_ctrl_table.mipi_txctrl_event[0],                      /* FrequencyBand900  */
      &GGE_MIPI_CTRL_TABLE_DCS1800.mipi_tx_ctrl_table.mipi_txctrl_event[0],                     /* FrequencyBand1800 */
      &GGE_MIPI_CTRL_TABLE_PCS1900.mipi_tx_ctrl_table.mipi_txctrl_event[0],                     /* FrequencyBand1900 */
   };
   
   sGGE_MIPIDATA_SUBBAND* GGE_MIPI_CTRL_TABLE_TX_DATA_NOTCH_SWITCH[] =
   {  
      0,                                                                                        /* FrequencyBand400  */
      &GGE_MIPI_CTRL_TABLE_GSM850.mipi_tx_ctrl_table.mipi_txctrl_data[0],                       /* FrequencyBand850  */
      &GGE_MIPI_CTRL_TABLE_GSM900.mipi_tx_ctrl_table.mipi_txctrl_data[0],                       /* FrequencyBand900  */
      &GGE_MIPI_CTRL_TABLE_DCS1800_NOTCH_SWITCH.mipi_tx_ctrl_table.mipi_txctrl_data[0],         /* FrequencyBand1800 */
      &GGE_MIPI_CTRL_TABLE_PCS1900.mipi_tx_ctrl_table.mipi_txctrl_data[0],                      /* FrequencyBand1900 */
   };

   sGGE_MIPIDATA_SUBBAND* GGE_MIPI_CTRL_TABLE_TX_DATA[] =
   {  
      0,                                                                                        /* FrequencyBand400  */
      &GGE_MIPI_CTRL_TABLE_GSM850.mipi_tx_ctrl_table.mipi_txctrl_data[0],                       /* FrequencyBand850  */
      &GGE_MIPI_CTRL_TABLE_GSM900.mipi_tx_ctrl_table.mipi_txctrl_data[0],                       /* FrequencyBand900  */
      &GGE_MIPI_CTRL_TABLE_DCS1800.mipi_tx_ctrl_table.mipi_txctrl_data[0],                      /* FrequencyBand1800 */
      &GGE_MIPI_CTRL_TABLE_PCS1900.mipi_tx_ctrl_table.mipi_txctrl_data[0],                      /* FrequencyBand1900 */
   };
   
   sGGE_MIPIPADATA* GGE_MIPI_CTRL_TABLE_PA_DATA[] =
   {
      0,                                                                                        /* FrequencyBand400  */
      &GGE_MIPI_CTRL_TABLE_GSM850.mipi_tx_ctrl_table.mipi_txctrl_pa_data,                       /* FrequencyBand850  */
      &GGE_MIPI_CTRL_TABLE_GSM900.mipi_tx_ctrl_table.mipi_txctrl_pa_data,                       /* FrequencyBand900  */
      &GGE_MIPI_CTRL_TABLE_DCS1800.mipi_tx_ctrl_table.mipi_txctrl_pa_data,                      /* FrequencyBand1800 */
      &GGE_MIPI_CTRL_TABLE_PCS1900.mipi_tx_ctrl_table.mipi_txctrl_pa_data,                      /* FrequencyBand1900 */
   };

   
   sGGE_MIPIEVENT* GGE_MIPI_CTRL_TABLE_TXMID_EVENT[] =
   {
      0,                                                                                        /* FrequencyBand400  */
   #if IS_MIPI_INTERSLOT_RAMPING_OPTIMIZE_SUPPORT
      &GGE_MIPI_CTRL_TABLE_GSM850.mipi_txmid_ctrl_table.mipi_txmidctrl_event[0][0],             /* FrequencyBand850  */
      &GGE_MIPI_CTRL_TABLE_GSM900.mipi_txmid_ctrl_table.mipi_txmidctrl_event[0][0],             /* FrequencyBand900  */
      &GGE_MIPI_CTRL_TABLE_DCS1800.mipi_txmid_ctrl_table.mipi_txmidctrl_event[0][0],            /* FrequencyBand1800 */
      &GGE_MIPI_CTRL_TABLE_PCS1900.mipi_txmid_ctrl_table.mipi_txmidctrl_event[0][0],            /* FrequencyBand1900 */
   #else
      &GGE_MIPI_CTRL_TABLE_GSM850.mipi_txmid_ctrl_table.mipi_txmidctrl_event[0],                /* FrequencyBand850  */
      &GGE_MIPI_CTRL_TABLE_GSM900.mipi_txmid_ctrl_table.mipi_txmidctrl_event[0],                /* FrequencyBand900  */
      &GGE_MIPI_CTRL_TABLE_DCS1800.mipi_txmid_ctrl_table.mipi_txmidctrl_event[0],               /* FrequencyBand1800 */
      &GGE_MIPI_CTRL_TABLE_PCS1900.mipi_txmid_ctrl_table.mipi_txmidctrl_event[0],               /* FrequencyBand1900 */
   #endif
   };
   
   sGGE_MIPIDATA_SUBBAND* GGE_MIPI_CTRL_TABLE_TXMID_DATA[] =
   {
      0,                                                                                        /* FrequencyBand400  */
      &GGE_MIPI_CTRL_TABLE_GSM850.mipi_txmid_ctrl_table.mipi_txmidctrl_data[0],                 /* FrequencyBand850  */
      &GGE_MIPI_CTRL_TABLE_GSM900.mipi_txmid_ctrl_table.mipi_txmidctrl_data[0],                 /* FrequencyBand900  */
      &GGE_MIPI_CTRL_TABLE_DCS1800.mipi_txmid_ctrl_table.mipi_txmidctrl_data[0],                /* FrequencyBand1800 */
      &GGE_MIPI_CTRL_TABLE_PCS1900.mipi_txmid_ctrl_table.mipi_txmidctrl_data[0],                /* FrequencyBand1900 */
   };
   
   sGGE_MIPI_CTRL_TABLE_BAND* GGE_MIPI_CTRL_TABLE[]=
   {
      0,                                              /* FrequencyBand400  */
      &GGE_MIPI_CTRL_TABLE_GSM850,                    /* FrequencyBand850  */
      &GGE_MIPI_CTRL_TABLE_GSM900,                    /* FrequencyBand900  */
      &GGE_MIPI_CTRL_TABLE_DCS1800,                   /* FrequencyBand1800 */
      &GGE_MIPI_CTRL_TABLE_PCS1900,                   /* FrequencyBand1900 */
   };


   #if IS_MIPI_DRDI_SUPPORT
/*
   2G MIPI DRDI 
   Step 1:
   Add your each "band MIPI control table" at below,
   Ex: 
   GGE_MIPI_CTRL_TABLE_GSM850_set0
   GGE_MIPI_CTRL_TABLE_GSM900_set0
   GGE_MIPI_CTRL_TABLE_DCS1800_set0
   GGE_MIPI_CTRL_TABLE_PCS1900_set0
   GGE_MIPI_CTRL_TABLE_GSM850_set1
   GGE_MIPI_CTRL_TABLE_GSM900_set1
   GGE_MIPI_CTRL_TABLE_DCS1800_set1
   GGE_MIPI_CTRL_TABLE_PCS1900_set1
   ...and more(if need)
*/
sGGE_MIPI_CTRL_TABLE_BAND GGE_MIPI_CTRL_TABLE_GSM850_set0=
{
   {  
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_rx_ctrl_table.mipi_rxctrl_event[] */
      {  /*                element       data idx          event type       ,  event timing      */
         /*    No.          type   ,  { start, stop },                         ( QB )            */
         {  /*  0 */  GGE_MIPI_ASM ,  {   0  ,  1   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_RX_ON0   },
         {  /*  1 */  GGE_MIPI_ASM ,  {   2  ,  2   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_RX_ON1   },
         {  /*  2 */  GGE_MIPI_ASM ,  {   3  ,  4   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_RX_OFF0  },
         {  /*  3 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  4 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
      },
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_rx_ctrl_table.mipi_rxctrl_data[] */
      {     /*    No.    elm type  ,  port select        ,  data format               ,usid           ,{  { subband arfcn    , addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    251           , 0x1C, 0x38 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    251           , 0x00, 0x0F }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    251           , 0x00, 0x06 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    251           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    251           , 0x1C, 0xB8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   },
   {  
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_tx_ctrl_table.mipi_txctrl_event[] */
      {  /*                element       data idx          event type       ,  event timing      */
         /*    No.          type   ,  { start, stop },                         ( QB )            */
         {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON0   },
         {  /*  1 */  GGE_MIPI_PA  ,  {   1  ,  1   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_TX_OFF0  },
         {  /*  2 */  GGE_MIPI_ASM ,  {   2  ,  3   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON1   },
         {  /*  3 */  GGE_MIPI_ASM ,  {   4  ,  4   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON2   },
         {  /*  4 */  GGE_MIPI_ASM ,  {   5  ,  6   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_TX_OFF1  },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
      },
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_tx_ctrl_table.mipi_txctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           , {  { subband arfcn    , addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    251           , 0x01, GGE_MIPI_PA_G8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    251           , 0x01, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    251           , 0x1C, 0x38 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    251           , 0x00, 0x0F }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    251           , 0x00, 0x0A }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    251           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    251           , 0x1C, 0xB8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      },
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_tx_ctrl_table.mipi_txctrl_pa_data */
      {  /* GMSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x23        ,   0x23        ,   0x23        ,   0x23        ,   0x23           },
         /* 8PSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x27        ,   0x27        ,   0x27        ,   0x27        ,   0x27           },
      },
   },
   {
   #if IS_MIPI_INTERSLOT_RAMPING_OPTIMIZE_SUPPORT
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_txmid_ctrl_table.mipi_txmidctrl_event[][] */
      {  
         ///////////////////////////////////////////////////////////////////////////////////
         /* Only the "element type", "data idx", "event type" in GMSK->GMSK,              */
         /* and "event timing" in GMSK->GMSK, 8PSK->8PSK, 8PSK->GMSK, GMSK->8PSK are used.*/
         ///////////////////////////////////////////////////////////////////////////////////
         /* GMSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_GG },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_88 },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_8G },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* GMSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_G8 },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
      },
   #else
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_txmid_ctrl_table.mipi_txmidctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0 },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0        },
      },
   #endif   
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_txmid_ctrl_table.mipi_txmidctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           , {  { subband arfcn    , addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    251           , 0x01, GGE_MIPI_PA_G8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   }
};

sGGE_MIPI_CTRL_TABLE_BAND GGE_MIPI_CTRL_TABLE_GSM900_set0=
{
   {
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_rx_ctrl_table.mipi_rxctrl_event[] */
      {  /*                element       data idx          event type       ,  event timing      */
         /*    No.          type   ,  { start, stop },                         ( QB )            */
         {  /*  0 */  GGE_MIPI_ASM ,  {   0  ,  1   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_RX_ON0   },
         {  /*  1 */  GGE_MIPI_ASM ,  {   2  ,  2   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_RX_ON1   },
         {  /*  2 */  GGE_MIPI_ASM ,  {   3  ,  4   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_RX_OFF0  },
         {  /*  3 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  4 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
      },
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_rx_ctrl_table.mipi_rxctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           ,{  { subband arfcn    , addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    124           , 0x1C, 0x38 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    124           , 0x00, 0x0F }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    124           , 0x00, 0x02 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    124           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    124           , 0x1C, 0xB8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   },
   {
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_tx_ctrl_table.mipi_txctrl_event[] */
      {  /*                element       data idx          event type       ,  event timing      */
         /*    No.          type   ,  { start, stop },                         ( QB )            */
         {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON0   },
         {  /*  1 */  GGE_MIPI_PA  ,  {   1  ,  1   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_TX_OFF0  },
         {  /*  2 */  GGE_MIPI_ASM ,  {   2  ,  3   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON1   },
         {  /*  3 */  GGE_MIPI_ASM ,  {   4  ,  4   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON2   },
         {  /*  4 */  GGE_MIPI_ASM ,  {   5  ,  6   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_TX_OFF1  },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
      },
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_tx_ctrl_table.mipi_txctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           , {  { subband arfcn    , addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    124           , 0x01, GGE_MIPI_PA_G8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    124           , 0x01, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    124           , 0x1C, 0x38 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    124           , 0x00, 0x0F }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    124           , 0x00, 0x0A }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    124           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    124           , 0x1C, 0xB8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      },
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_tx_ctrl_table.mipi_txctrl_pa_data */
      {  /* GMSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x23        ,   0x23        ,   0x23        ,   0x23        ,   0x23           },
         /* 8PSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x27        ,   0x27        ,   0x27        ,   0x27        ,   0x27           },
      }
   },
   {
   #if IS_MIPI_INTERSLOT_RAMPING_OPTIMIZE_SUPPORT   
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_txmid_ctrl_table.mipi_txmidctrl_event[][] */
      {  
         ///////////////////////////////////////////////////////////////////////////////////
         /* Only the "element type", "data idx", "event type" in GMSK->GMSK,              */
         /* and "event timing" in GMSK->GMSK, 8PSK->8PSK, 8PSK->GMSK, GMSK->8PSK are used.*/
         ///////////////////////////////////////////////////////////////////////////////////      
         /* GMSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_GG },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_88 },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
          /* 8PSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_8G },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* GMSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_G8 },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
      },
   #else   
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_txmid_ctrl_table.mipi_txmidctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0 },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0        },
      },
   #endif
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_txmid_ctrl_table.mipi_txmidctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           , {  { subband arfcn    , addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    124           , 0x01, GGE_MIPI_PA_G8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   }
};

sGGE_MIPI_CTRL_TABLE_BAND GGE_MIPI_CTRL_TABLE_DCS1800_NOTCH_SWITCH_set0=
{
   {
      /* GGE_MIPI_CTRL_TABLE_DCS1800_NOTCH_SWITCH.mipi_rx_ctrl_table.mipi_rxctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  2 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  3 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  4 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
      },
      /* GGE_MIPI_CTRL_TABLE_DCS1800_NOTCH_SWITCH.mipi_rx_ctrl_table.mipi_rxctrl_data[] */
      {  	/*    No.    elm type  ,  port select    	   ,  data format      	        ,usid		      ,{  { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   },
   {
      /* GGE_MIPI_CTRL_TABLE_DCS1800_NOTCH_SWITCH.mipi_tx_ctrl_table.mipi_txctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  2 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  3 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  4 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
      },
      /* GGE_MIPI_CTRL_TABLE_DCS1800_NOTCH_SWITCH.mipi_tx_ctrl_table.mipi_txctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format                 ,usid		  , {  { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      },
      /* GGE_MIPI_CTRL_TABLE_DCS1800_NOTCH_SWITCH.mipi_tx_ctrl_table.mipi_txctrl_pa_data */
      {  /* GMSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x00        ,   0x00        ,   0x00        ,   0x00        ,   0x00           },
         /* 8PSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x00        ,   0x00        ,   0x00        ,   0x00        ,   0x00           },
      }
   },
   {
   #if IS_MIPI_INTERSLOT_RAMPING_OPTIMIZE_SUPPORT
      /* GGE_MIPI_CTRL_TABLE_DCS1800_NOTCH_SWITCH.mipi_txmid_ctrl_table.mipi_txmidctrl_event[][] */
      {  
         ///////////////////////////////////////////////////////////////////////////////////
         /* Only the "element type", "data idx", "event type" in GMSK->GMSK,              */
         /* and "event timing" in GMSK->GMSK, 8PSK->8PSK, 8PSK->GMSK, GMSK->8PSK are used.*/
         ///////////////////////////////////////////////////////////////////////////////////      
         /* GMSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* GMSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
      },
   #else
      /* GGE_MIPI_CTRL_TABLE_DCS1800_NOTCH_SWITCH.mipi_txmid_ctrl_table.mipi_txmidctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0        },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0        },
      },
   #endif
      /* GGE_MIPI_CTRL_TABLE_DCS1800_NOTCH_SWITCH.mipi_txmid_ctrl_table.mipi_txmidctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           , {  { subband arfcn    , addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   }
};

sGGE_MIPI_CTRL_TABLE_BAND GGE_MIPI_CTRL_TABLE_DCS1800_set0=
{
   {
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_rx_ctrl_table.mipi_rxctrl_event[] */
      {  /*                element       data idx          event type       ,  event timing      */
         /*    No.          type   ,  { start, stop },                         ( QB )            */
         {  /*  0 */  GGE_MIPI_ASM ,  {   0  ,  1   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_RX_ON0   },
         {  /*  1 */  GGE_MIPI_ASM ,  {   2  ,  2   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_RX_ON1   },
         {  /*  2 */  GGE_MIPI_ASM ,  {   3  ,  4   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_RX_OFF0  },
         {  /*  3 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  4 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
      },
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_rx_ctrl_table.mipi_rxctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           ,{  { subband arfcn    , addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    885           , 0x1C, 0x38 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    885           , 0x00, 0x0F }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    885           , 0x00, 0x04 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    885           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    885           , 0x1C, 0xB8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   },
   {
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_tx_ctrl_table.mipi_txctrl_event[] */
      {  /*                element       data idx          event type       ,  event timing      */
         /*    No.          type   ,  { start, stop },                         ( QB )            */
         {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON0   },
         {  /*  1 */  GGE_MIPI_PA  ,  {   1  ,  1   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_TX_OFF0  },
         {  /*  2 */  GGE_MIPI_ASM ,  {   2  ,  3   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON1   },
         {  /*  3 */  GGE_MIPI_ASM ,  {   4  ,  4   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON2   },
         {  /*  4 */  GGE_MIPI_ASM ,  {   5  ,  6   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_TX_OFF1  },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
      },
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_tx_ctrl_table.mipi_txctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           , {  { subband arfcn    , addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    885           , 0x01, GGE_MIPI_PA_G8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    885           , 0x01, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    885           , 0x1C, 0x38 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    885           , 0x00, 0x0F }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    885           , 0x00, 0x09 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    885           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    885           , 0x1C, 0xB8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      },
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_tx_ctrl_table.mipi_txctrl_pa_data */
      {  /* GMSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x33        ,   0x33        ,   0x33        ,   0x33        ,   0x33           },
         /* 8PSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x37        ,   0x37        ,   0x37        ,   0x37        ,   0x37           },
      }
   },
   {
   #if IS_MIPI_INTERSLOT_RAMPING_OPTIMIZE_SUPPORT
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_txmid_ctrl_table.mipi_txmidctrl_event[][] */
      {  
         ///////////////////////////////////////////////////////////////////////////////////
         /* Only the "element type", "data idx", "event type" in GMSK->GMSK,              */
         /* and "event timing" in GMSK->GMSK, 8PSK->8PSK, 8PSK->GMSK, GMSK->8PSK are used.*/
         ///////////////////////////////////////////////////////////////////////////////////      
         /* GMSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_GG },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_88 },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_8G },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* GMSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_G8 },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
      },
   #else
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_txmid_ctrl_table.mipi_txmidctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0 },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0        },
      },
   #endif
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_txmid_ctrl_table.mipi_txmidctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           , {  { subband arfcn    , addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    885           , 0x01, GGE_MIPI_PA_G8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   }
};

sGGE_MIPI_CTRL_TABLE_BAND GGE_MIPI_CTRL_TABLE_PCS1900_set0=
{
   {
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_rx_ctrl_table.mipi_rxctrl_event[] */
      {  /*                element       data idx          event type       ,  event timing      */
         /*    No.          type   ,  { start, stop },                         ( QB )            */
         {  /*  0 */  GGE_MIPI_ASM ,  {   0  ,  1   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_RX_ON0   },
         {  /*  1 */  GGE_MIPI_ASM ,  {   2  ,  2   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_RX_ON1   },
         {  /*  2 */  GGE_MIPI_ASM ,  {   3  ,  4   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_RX_OFF0  },
         {  /*  3 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  4 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0       },
      },
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_rx_ctrl_table.mipi_rxctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           ,{  { subband arfcn    , addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    810           , 0x1C, 0x38 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    810           , 0x00, 0x0F }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    810           , 0x00, 0x03 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    810           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 ,{  {    810           , 0x1C, 0xB8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   },
   {
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_tx_ctrl_table.mipi_txctrl_event[] */
      {  /*                element       data idx          event type       ,  event timing      */
         /*    No.          type   ,  { start, stop },                         ( QB )            */
         {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON0   },
         {  /*  1 */  GGE_MIPI_PA  ,  {   1  ,  1   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_TX_OFF0  },
         {  /*  2 */  GGE_MIPI_ASM ,  {   2  ,  3   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON1   },
         {  /*  3 */  GGE_MIPI_ASM ,  {   4  ,  4   },  GGE_MIPI_TRX_ON      ,   QB_MIPI_TX_ON2   },
         {  /*  4 */  GGE_MIPI_ASM ,  {   5  ,  6   },  GGE_MIPI_TRX_OFF     ,   QB_MIPI_TX_OFF1  },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
      },
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_tx_ctrl_table.mipi_txctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           , {  { subband arfcn    , addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    810           , 0x01, GGE_MIPI_PA_G8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    810           , 0x01, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    810           , 0x1C, 0x38 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    810           , 0x00, 0x0F }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    810           , 0x00, 0x09 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    810           , 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_ASM ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_ASM0 , {  {    810           , 0x1C, 0xB8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      },
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_tx_ctrl_table.mipi_txctrl_pa_data */
      {  /* GMSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x33        ,   0x33        ,   0x33        ,   0x33        ,   0x33           },
         /* 8PSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x37        ,   0x37        ,   0x37        ,   0x37        ,   0x37           },
      }       
   },
   {
   #if IS_MIPI_INTERSLOT_RAMPING_OPTIMIZE_SUPPORT   
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_txmid_ctrl_table.mipi_txmidctrl_event[][] */
      {  
         ///////////////////////////////////////////////////////////////////////////////////
         /* Only the "element type", "data idx", "event type" in GMSK->GMSK,              */
         /* and "event timing" in GMSK->GMSK, 8PSK->8PSK, 8PSK->GMSK, GMSK->8PSK are used.*/
         ///////////////////////////////////////////////////////////////////////////////////      
         /* GMSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_GG },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_88 },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_8G },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* GMSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0_G8 },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
      },
   #else   
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_txmid_ctrl_table.mipi_txmidctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_PA  ,  {   0  ,  0   },  GGE_MIPI_TXMID       ,  QB_MIPI_TXMID0 },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0        },
      },
   #endif      
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_txmid_ctrl_table.mipi_txmidctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           , {  { subband arfcn    , addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_PA  ,  GGE_MIPI_PORT0     ,  GGE_MIPI_REG_W            ,MIPI_USID_PA0  , {  {    810           , 0x01, GGE_MIPI_PA_G8 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   }
};

sGGE_MIPI_CTRL_TABLE_BAND GGE_MIPI_CTRL_TABLE_GSM850_set1=
{
   {
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_rx_ctrl_table.mipi_rxctrl_event[] */
      {  /*                element       data idx          event type       ,  event timing      */
         /*    No.          type   ,  { start, stop },                         ( QB )            */
         {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  2 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  3 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  4 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
      },
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_rx_ctrl_table.mipi_rxctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           ,{  {     subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   },
   {
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_tx_ctrl_table.mipi_txctrl_event[] */
      {  /*                element       data idx          event type       ,  event timing      */
         /*    No.          type   ,  { start, stop },                         ( QB )            */
         {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  2 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  3 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  4 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
      },
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_tx_ctrl_table.mipi_txctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           , {  {     subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      },
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_tx_ctrl_table.mipi_txctrl_pa_data */
      {  /* GMSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x00        ,   0x00        ,   0x00        ,   0x00        ,   0x00           },
         /* 8PSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x00        ,   0x00        ,   0x00        ,   0x00        ,   0x00           },
      }       
   },
   {
#if IS_MIPI_INTERSLOT_RAMPING_OPTIMIZE_SUPPORT
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_txmid_ctrl_table.mipi_txmidctrl_event[][] */
      {  
         ///////////////////////////////////////////////////////////////////////////////////
         /* Only the "element type", "data idx", "event type" in GMSK->GMSK,              */
         /* and "event timing" in GMSK->GMSK, 8PSK->8PSK, 8PSK->GMSK, GMSK->8PSK are used.*/
         ///////////////////////////////////////////////////////////////////////////////////
         /* GMSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* GMSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
      },
#else
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_txmid_ctrl_table.mipi_txmidctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0        },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0        },
      },
#endif   
      /* GGE_MIPI_CTRL_TABLE_GSM850.mipi_txmid_ctrl_table.mipi_txmidctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format                 ,usid         , {  {     subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   }
};

sGGE_MIPI_CTRL_TABLE_BAND GGE_MIPI_CTRL_TABLE_GSM900_set1=
{
   {
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_rx_ctrl_table.mipi_rxctrl_event[] */
      {  /*                element       data idx          event type       ,  event timing      */
         /*    No.          type   ,  { start, stop },                         ( QB )            */
         {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  2 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  3 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  4 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
      },
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_rx_ctrl_table.mipi_rxctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           ,{  {     subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   },
   {
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_tx_ctrl_table.mipi_txctrl_event[] */
      {  /*                element       data idx          event type       ,  event timing      */
         /*    No.          type   ,  { start, stop },                         ( QB )            */
         {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  2 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  3 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  4 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
      },
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_tx_ctrl_table.mipi_txctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           , {  {     subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      },
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_tx_ctrl_table.mipi_txctrl_pa_data */
      {  /* GMSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x00        ,   0x00        ,   0x00        ,   0x00        ,   0x00           },
         /* 8PSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x00        ,   0x00        ,   0x00        ,   0x00        ,   0x00           },
      }       
   },
   {
#if IS_MIPI_INTERSLOT_RAMPING_OPTIMIZE_SUPPORT
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_txmid_ctrl_table.mipi_txmidctrl_event[][] */
      {  
         ///////////////////////////////////////////////////////////////////////////////////
         /* Only the "element type", "data idx", "event type" in GMSK->GMSK,              */
         /* and "event timing" in GMSK->GMSK, 8PSK->8PSK, 8PSK->GMSK, GMSK->8PSK are used.*/
         ///////////////////////////////////////////////////////////////////////////////////
         /* GMSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* GMSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
      },
#else
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_txmid_ctrl_table.mipi_txmidctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0        },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0        },
      },
#endif   
      /* GGE_MIPI_CTRL_TABLE_GSM900.mipi_txmid_ctrl_table.mipi_txmidctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           , {  {     subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   }
};

sGGE_MIPI_CTRL_TABLE_BAND GGE_MIPI_CTRL_TABLE_DCS1800_NOTCH_SWITCH_set1=
{
   {
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_rx_ctrl_table.mipi_rxctrl_event[] */
      {  /*                element       data idx          event type       ,  event timing      */
         /*    No.          type   ,  { start, stop },                         ( QB )            */
         {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  2 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  3 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  4 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
      },
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_rx_ctrl_table.mipi_rxctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           ,{  {     subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   },
   {
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_tx_ctrl_table.mipi_txctrl_event[] */
      {  /*                element       data idx          event type       ,  event timing      */
         /*    No.          type   ,  { start, stop },                         ( QB )            */
         {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  2 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  3 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  4 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
      },
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_tx_ctrl_table.mipi_txctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           , {  {     subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      },
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_tx_ctrl_table.mipi_txctrl_pa_data */
      {  /* GMSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x00        ,   0x00        ,   0x00        ,   0x00        ,   0x00           },
         /* 8PSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x00        ,   0x00        ,   0x00        ,   0x00        ,   0x00           },
      }       
   },
   {
#if IS_MIPI_INTERSLOT_RAMPING_OPTIMIZE_SUPPORT
       /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_txmid_ctrl_table.mipi_txmidctrl_event[][] */
       {  
          ///////////////////////////////////////////////////////////////////////////////////
          /* Only the "element type", "data idx", "event type" in GMSK->GMSK,              */
          /* and "event timing" in GMSK->GMSK, 8PSK->8PSK, 8PSK->GMSK, GMSK->8PSK are used.*/
          ///////////////////////////////////////////////////////////////////////////////////
          /* GMSK->GMSK */
          {  /*            element       data idx       event type       ,  event timing  */
             /*    No.      type   ,  { start, stop },                         ( QB )     */
             {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
             {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
          },
          /* 8PSK->8PSK */
          {  /*            element       data idx       event type       ,  event timing  */
             /*    No.      type   ,  { start, stop },                         ( QB )     */
             {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
             {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
          },
          /* 8PSK->GMSK */
          {  /*            element       data idx       event type       ,  event timing  */
             /*    No.      type   ,  { start, stop },                         ( QB )     */
             {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
             {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
          },
          /* GMSK->8PSK */
          {  /*            element       data idx       event type       ,  event timing  */
             /*    No.      type   ,  { start, stop },                         ( QB )     */
             {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
             {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
          },
       },
#else
       /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_txmid_ctrl_table.mipi_txmidctrl_event[] */
       {  /*            element       data idx       event type       ,  event timing  */
          /*    No.      type   ,  { start, stop },                         ( QB )     */
          {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0        },
          {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0        },
       },
#endif   
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_txmid_ctrl_table.mipi_txmidctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format                 ,usid        , {  { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   }
};

sGGE_MIPI_CTRL_TABLE_BAND GGE_MIPI_CTRL_TABLE_DCS1800_set1=
{
   {
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_rx_ctrl_table.mipi_rxctrl_event[] */
      {  /*                element       data idx          event type       ,  event timing      */
         /*    No.          type   ,  { start, stop },                         ( QB )            */
         {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  2 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  3 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  4 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
      },
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_rx_ctrl_table.mipi_rxctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           ,{  {     subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   },
   {
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_tx_ctrl_table.mipi_txctrl_event[] */
      {  /*                element       data idx          event type       ,  event timing      */
         /*    No.          type   ,  { start, stop },                         ( QB )            */
         {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  2 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  3 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  4 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
      },
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_tx_ctrl_table.mipi_txctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           , {  {     subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      },
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_tx_ctrl_table.mipi_txctrl_pa_data */
      {  /* GMSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x00        ,   0x00        ,   0x00        ,   0x00        ,   0x00           },
         /* 8PSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x00        ,   0x00        ,   0x00        ,   0x00        ,   0x00           },
      }       
   },
   {
#if IS_MIPI_INTERSLOT_RAMPING_OPTIMIZE_SUPPORT
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_txmid_ctrl_table.mipi_txmidctrl_event[][] */
      {  
         ///////////////////////////////////////////////////////////////////////////////////
         /* Only the "element type", "data idx", "event type" in GMSK->GMSK,              */
         /* and "event timing" in GMSK->GMSK, 8PSK->8PSK, 8PSK->GMSK, GMSK->8PSK are used.*/
         ///////////////////////////////////////////////////////////////////////////////////
         /* GMSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* GMSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
      },
#else
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_txmid_ctrl_table.mipi_txmidctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0        },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0        },
      },
#endif   
      /* GGE_MIPI_CTRL_TABLE_DCS1800.mipi_txmid_ctrl_table.mipi_txmidctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format                 ,usid        ,  {  {     subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   }
};

sGGE_MIPI_CTRL_TABLE_BAND GGE_MIPI_CTRL_TABLE_PCS1900_set1=
{
   {
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_rx_ctrl_table.mipi_rxctrl_event[] */
      {  /*                element       data idx          event type       ,  event timing      */
         /*    No.          type   ,  { start, stop },                         ( QB )            */
         {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  2 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  3 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  4 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
      },
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_rx_ctrl_table.mipi_rxctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           ,{  {     subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0,{  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   },
   {
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_tx_ctrl_table.mipi_txctrl_event[] */
      {  /*                element       data idx          event type       ,  event timing      */
         /*    No.          type   ,  { start, stop },                         ( QB )            */
         {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  2 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  3 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  4 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  5 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  6 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  7 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  8 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /*  9 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 10 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 11 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         {  /* 12 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
      },
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_tx_ctrl_table.mipi_txctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           , {  {     subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  2 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  3 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  4 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  5 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  6 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  7 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  8 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  9 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 10 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 11 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 12 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 13 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 14 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 15 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 16 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 17 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 18 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 19 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 20 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 21 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 22 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 23 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 24 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 25 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 26 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 27 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 28 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /* 29 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      },
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_tx_ctrl_table.mipi_txctrl_pa_data */
      {  /* GMSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x00        ,   0x00        ,   0x00        ,   0x00        ,   0x00           },
         /* 8PSK Data */
         /* subband0 data , subband1 data , subband2 data , subband3 data , subband4 data  */
         {    0x00        ,   0x00        ,   0x00        ,   0x00        ,   0x00           },
      }       
   },
   {
#if IS_MIPI_INTERSLOT_RAMPING_OPTIMIZE_SUPPORT   
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_txmid_ctrl_table.mipi_txmidctrl_event[][] */
      {  
         ///////////////////////////////////////////////////////////////////////////////////
         /* Only the "element type", "data idx", "event type" in GMSK->GMSK,              */
         /* and "event timing" in GMSK->GMSK, 8PSK->8PSK, 8PSK->GMSK, GMSK->8PSK are used.*/
         ///////////////////////////////////////////////////////////////////////////////////
         /* GMSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* 8PSK->GMSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
         /* GMSK->8PSK */
         {  /*            element       data idx       event type       ,  event timing  */
            /*    No.      type   ,  { start, stop },                         ( QB )     */
            {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
            {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0           },
         },
      },
#else   
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_txmid_ctrl_table.mipi_txmidctrl_event[] */
      {  /*            element       data idx       event type       ,  event timing  */
         /*    No.      type   ,  { start, stop },                         ( QB )     */
         {  /*  0 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0        },
         {  /*  1 */  GGE_MIPI_NULL,  {   0  ,  0   },  GGE_MIPI_EVENT_NULL  ,        0        },
      },
#endif      
      /* GGE_MIPI_CTRL_TABLE_PCS1900.mipi_txmid_ctrl_table.mipi_txmidctrl_data[] */
      {     /* No.       elm type  ,  port select        ,  data format               ,usid           , {  {     subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data }, { subband arfcn, addr, data },   */
         {  /*  0 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
         {  /*  1 */  GGE_MIPI_NULL,  GGE_MIPI_DATA_NULL ,  GGE_MIPI_REG_W_NULL       ,MIPI_USID_INIT0, {  {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, {    GGE_NULL_ARFCN, 0x00, 0x00 }, }  },
      }
   }
};

/*
   2G MIPI DRDI 
   Step 2:
   Fill your "band MIPI control table" into "MIPI control table set" at below,
   Ex:
   GGE_MIPI_CTRL_TABLE_SET0
   GGE_MIPI_CTRL_TABLE_SET1
   ...and more(if need)
*/

sGGE_MIPI_CTRL_TABLE_BAND* GGE_MIPI_CTRL_TABLE_SET0[FrequencyBandCount]=
{ 
#if IS_DSDA_DCS_TX_NOTCH_SWITCH_SUPPORT
   &GGE_MIPI_CTRL_TABLE_DCS1800_NOTCH_SWITCH_set0,      /* FrequencyBand1800_TX_NOTCH */
#else
   0,                                                   /* FrequencyBand400  */
#endif
   &GGE_MIPI_CTRL_TABLE_GSM850_set0,                    /* FrequencyBand850  */
   &GGE_MIPI_CTRL_TABLE_GSM900_set0,                    /* FrequencyBand900  */
   &GGE_MIPI_CTRL_TABLE_DCS1800_set0,                   /* FrequencyBand1800 */
   &GGE_MIPI_CTRL_TABLE_PCS1900_set0,                   /* FrequencyBand1900 */
};

sGGE_MIPI_CTRL_TABLE_BAND* GGE_MIPI_CTRL_TABLE_SET1[FrequencyBandCount]=
{ 
#if IS_DSDA_DCS_TX_NOTCH_SWITCH_SUPPORT
   &GGE_MIPI_CTRL_TABLE_DCS1800_NOTCH_SWITCH_set1,      /* FrequencyBand1800_TX_NOTCH */
#else
   0,                                                   /* FrequencyBand400  */
#endif                                                  /* FrequencyBand400  */
   &GGE_MIPI_CTRL_TABLE_GSM850_set1,                    /* FrequencyBand850  */
   &GGE_MIPI_CTRL_TABLE_GSM900_set1,                    /* FrequencyBand900  */
   &GGE_MIPI_CTRL_TABLE_DCS1800_set1,                   /* FrequencyBand1800 */
   &GGE_MIPI_CTRL_TABLE_PCS1900_set1,                   /* FrequencyBand1900 */
};

/*
   2G MIPI DRDI 
   Step 3:
   Fill your "MIPI control table set" into "MIPI control table set array" at below,
*/

sGGE_MIPI_CTRL_TABLE_SET* GGE_MIPI_CTRL_TABLE_SET_ARRAY[L1D_CUSTOM_TOTAL_SET_NUMS]=
{ 
   (sGGE_MIPI_CTRL_TABLE_SET*)GGE_MIPI_CTRL_TABLE_SET0,
   (sGGE_MIPI_CTRL_TABLE_SET*)GGE_MIPI_CTRL_TABLE_SET1,
};

#endif
   
#endif /* IS_MIPI_SUPPORT */

/*=========================================================================================*/

