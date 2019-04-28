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
 *   l1d_custom_mipi.h
 *
 * Project:
 * --------
 *   MT6169
 *
 * Description:
 * ------------
 *   MT6169 2G MIPI constance definition
 *
 * Author:
 * -------
 *
 *
 *==============================================================================
 *******************************************************************************/

#ifndef  _L1D_CUSTOM_MIPI_H_
#define  _L1D_CUSTOM_MIPI_H_
/* --------------------------------------------------------------------------- */

   #if IS_MIPI_SUPPORT
/*--------------------------------------------------------*/
/*   Enable/Disable MIPI Function Define                  */
/*--------------------------------------------------------*/
#define IS_2G_MIPI_ENABLE                1
      #if IS_2G_MIPI_ENABLE
/*--------------------------------------------------------*/
/*   Event RX Timing Define                               */
/*--------------------------------------------------------*/
/*MT6169*/ #define  QB_MIPI_RX_ON0       165
/*MT6169*/ #define  QB_MIPI_RX_ON1       50
/*MT6169*/ //#define  QB_MIPI_RX_ON2       173
/*MT6169*/ //#define  QB_MIPI_RX_ON3       173
/*MT6169*/ //#define  QB_MIPI_RX_ON4       173
/*MT6169*/ //#define  QB_MIPI_RX_ON5       173
/*MT6169*/ //#define  QB_MIPI_RX_ON6       173
/*MT6169*/ //#define  QB_MIPI_RX_ON7       173
/*MT6169*/ #define  QB_MIPI_RX_OFF0      8//7
/*MT6169*/ //#define  QB_MIPI_RX_OFF1      173
/*MT6169*/ //#define  QB_MIPI_RX_OFF2      173
/*MT6169*/ //#define  QB_MIPI_RX_OFF3      173
/*MT6169*/ //#define  QB_MIPI_RX_OFF4      173
/*MT6169*/
/*--------------------------------------------------------*/
/*   Event Tx Timing Define                               */
/*--------------------------------------------------------*/
/*MT6169*/ #define  QB_MIPI_TX_ON0       23
/*MT6169*/ #define  QB_MIPI_TX_ON1       246
/*MT6169*/ #define  QB_MIPI_TX_ON2       16
/*MT6169*/ //#define  QB_MIPI_TX_ON3       173
/*MT6169*/ //#define  QB_MIPI_TX_ON4       173
/*MT6169*/ //#define  QB_MIPI_TX_ON5       173
/*MT6169*/ //#define  QB_MIPI_TX_ON6       173
/*MT6169*/ //#define  QB_MIPI_TX_ON7       173
/*MT6169*/ #define  QB_MIPI_TX_OFF0      20
/*MT6169*/ #define  QB_MIPI_TX_OFF1      27
/*MT6169*/ //#define  QB_MIPI_TX_OFF2      173
/*MT6169*/ //#define  QB_MIPI_TX_OFF3      173
/*MT6169*/ //#define  QB_MIPI_TX_OFF4      173
/*MT6169*/
/*--------------------------------------------------------*/
/*   Event TxMid Timing Define                            */
/*--------------------------------------------------------*/
/*MT6169*/ #define  QB_MIPI_TXMID0       20
/*MT6169*/ //#define  QB_MIPI_TXMID1       173
/*MT6169*/
/*--------------------------------------------------------*/
/*   Event TxMid Timing For Interslot Ramping Optimize    */
/*--------------------------------------------------------*/
/*MT6169*/ #define  QB_MIPI_TXMID0_GG       QB_MIPI_TXMID0     /* Interslot Ramping Timing for GMSK->GMSK */
/*MT6169*/ //#define  QB_MIPI_TXMID1_GG       QB_MIPI_TXMID1
/*MT6169*/ #define  QB_MIPI_TXMID0_88       QB_MIPI_TXMID0     /* Interslot Ramping Timing for 8PSK->8PSK */
/*MT6169*/ //#define  QB_MIPI_TXMID1_88       QB_MIPI_TXMID1
/*MT6169*/ #define  QB_MIPI_TXMID0_8G       QB_MIPI_TXMID0     /* Interslot Ramping Timing for 8PSK->GMSK */
/*MT6169*/ //#define  QB_MIPI_TXMID1_8G       QB_MIPI_TXMID1
/*MT6169*/ #define  QB_MIPI_TXMID0_G8       QB_MIPI_TXMID0     /* Interslot Ramping Timing for GMSK->8PSK */
/*MT6169*/ //#define  QB_MIPI_TXMID1_G8       QB_MIPI_TXMID1
/*MT6169*/
      #endif//IS_2G_MIPI_ENABLE
   #endif//end MIPI support
#endif
