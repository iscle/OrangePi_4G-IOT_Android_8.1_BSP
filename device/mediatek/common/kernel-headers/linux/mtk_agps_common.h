/*****************************************************************************
 *
 * Filename:
 * ---------
 *   mtk_agps_common.h
 *
 * Project:
 * --------
 *   DUMA
 *
 * Description:
 * ------------
 *   Data types used by MTK AGPS
 *
 * Author:
 * -------
 *  Chunhui Li(MTK80143)
 *
 *============================================================================
 *             HISTORY
 * Below this line, this part is controlled by CC/CQ. DO NOT MODIFY!!
 *------------------------------------------------------------------------------
 * $Revision:$
 * $Modtime:$
 * $Log:$
 *
 * 12 05 2012 archilis.wang
 * [ALPS00393352] Please help analzye the root cause of  failed item of ASSET
 * Review: http://mtksap20:8080/go?page=NewReview&reviewid=49629
 *
 * 04 24 2011 nina.hsu
 * [ALPS00043332] [MT6573][AGPS]  Add GPS_MNL_PROCESS_STATUS definition
 * [MT6573][AGPS]
 *      Add GPS_MNL_PROCESS_STATUS definition for reporting GPS status
 *    .
 *
 * 08 20 2010 qiuhuan.zhao
 * [ALPS00123522] [GPS] Android 2.2 porting
 * Android 2.2 Gps driver porting.
 *
 *------------------------------------------------------------------------------
 * Upper this line, this part is controlled by CC/CQ. DO NOT MODIFY!!
 *============================================================================
 ****************************************************************************/

#ifndef MTK_AGPS_COMMON_H
#define MTK_AGPS_COMMON_H

#ifdef __cplusplus
extern "C" {
#endif

#if ( defined(__ARMCC_VERSION) && (__ARMCC_VERSION < 200000 ))
#else
#pragma pack(1)
#endif

//AGPS socket port
#define MTK_MNL2SUPL      "/data/agps_supl/mnl2supl"
#define MTK_PROFILE2MNL   "/data/agps_supl/profile2mnl"

//System_property for SUPL_Enable, SUPL_SI_Enable
#define GPS_MNL_SUPL_NAME "gps.supl.status"
#define GPS_MNL_PROCESS_STATUS "gps.mnl_process.status"

// AGPS Module Enum
typedef enum
{
  MTK_MOD_GPS = 0,
  MTK_MOD_SUPL,
  MTK_MOD_WIFI,
  MTK_MOD_CELLID,
  MTK_MOD_CNT,
} MTK_AGPS_MOD_E;

// AGPS Msg type
typedef enum
{
  MTK_AGPS_SUPL_ASSIST_REQ= 0,
  MTK_AGPS_SUPL_NI_REQ,
  MTK_AGPS_SUPL_PMTK_DATA,
  MTK_AGPS_SUPL_END,
  MTK_AGPS_SUPL_MNL_STATUS,
  MTK_AGPS_SUPL_GPEVT,
  MTK_AGPS_SUPL_CNT,
  MTK_AGPS_AIDING_CELLINFO,
  MTK_AGPS_AIDING_WIFIIFNO,
  MTK_AGPS_SUPL_RAW_DBG,
} MTK_AGPS_SUPL_MSG_T;

// GPEVT msg data enum
typedef enum
{
  GPEVT_TYPE_UNKNOWN = 0,                      //  0
  GPEVT_SUPL_SLP_CONNECT_BEGIN,                //  1
  GPEVT_SUPL_SLP_CONNECTED,                    //  2
  GPEVT_SUPL_SSL_CONNECT_BEGIN,                //  3
  GPEVT_SUPL_SSL_CONNECTED,                    //  4
  GPEVT_SUPL_ASSIST_DATA_RECEIVED,             //  5
  GPEVT_SUPL_ASSIST_DATA_VALID,                //  6
  GPEVT_SUPL_FIRST_POS_FIX,                    //  7
  GPEVT_SUPL_MEAS_TIME_OUT,                    //  8
  GPEVT_SUPL_MEAS_RESPONSE_SENT,               //  9
  GPEVT_SUPL_SSL_CLOSED,                       // 10
  GPEVT_SUPL_SLP_DISCONNECTED,                 // 11

  GPEVT_CP_MOLR_SENT,                          // 12
  GPEVT_CP_MTLR_RECEIVED,                      // 13
  GPEVT_CP_ASSIST_DATA_RECEIVED,               // 14
  GPEVT_CP_ASSIST_DATA_VALID,                  // 15
  GPEVT_CP_FIRST_POS_FIX,                      // 16
  GPEVT_CP_MEAS_TIME_OUT,                      // 17
  GPEVT_CP_MEAS_RESPONSE_SENT,                 // 18

  GPEVT_GNSS_HW_START,                         // 19
  GPEVT_GNSS_HW_STOP,                          // 20
  GPEVT_GNSS_RESET_STORED_SATELLITE_DATA,      // 21

  GPEVT_EPO_SERVER_CONNECT_BEGIN,              // 22
  GPEVT_EPO_SERVER_CONNECTED,                  // 23
  GPEVT_EPO_DATA_RECEIVED,                     // 24
  GPEVT_EPO_SERVER_DISCONNECTED,               // 25
  GPEVT_EPO_DATA_VALID,                        // 26

  GPEVT_HOT_STILL_DATA_VALID,                  // 27

  GPEVT_TYPE_MAX                               // 28
}gpevt_type;

// AGPS data struct
typedef struct
{
    unsigned char srcMod;
    unsigned char dstMod;
    unsigned short type;
    unsigned short length;
    char data[1];
} mtk_agps_msg, *pmtk_agps_msg,  MTK_AGPS_MSG_T, *PMTK_AGPS_MSG_T;

#define MAX_AGPS_MAX_MESSAGES             72
#define MTK_AGPS_PMTK_MAX_SIZE            (256+sizeof(mtk_agps_msg))
#define MTK_AGPS_PMTK_MAX_LEN             (256+sizeof(mtk_agps_msg))
#define MTK_AGPS_PMTK_HDR_LEN            sizeof(MTK_AGPS_MSG_T)
#define MTK_AGPS_MSG_MAX_LEN             (MTK_AGPS_PMTK_MAX_LEN + MTK_AGPS_PMTK_HDR_LEN)

#define MTK_AGPS_SUPLMSG_TIMEOUT          6000

#if ( defined(__ARMCC_VERSION) && (__ARMCC_VERSION < 200000 ))
#else
#pragma pack()
#endif

#ifdef __cplusplus
}
#endif

#endif /* MTK_AGPS_COMMON_H */
