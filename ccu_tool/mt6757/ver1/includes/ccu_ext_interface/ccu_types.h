/*****************************************************************************
*  Copyright Statement:
*  --------------------
*  This software is protected by Copyright and the information contained
*  herein is confidential. The software may not be copied and the information
*  contained herein may not be used or disclosed except with the written
*  permission of MediaTek Inc. (C) 2016
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

#ifndef CCU_TYPES_H
#define CCU_TYPES_H

//******************************************************************************/
// typedef unsigned char   BOOLEAN;    // uint8_t
typedef unsigned char       MUINT8;
typedef unsigned short      MUINT16;
typedef unsigned int        MUINT32;
//typedef unsigned long  MUINT64;
//
typedef signed char         MINT8;
typedef signed short        MINT16;
typedef signed int          MINT32;
//typedef signed long    MINT64;
//
typedef float		    MFLOAT;
typedef double		    MDOUBLE;
//
typedef void                MVOID;
typedef int                 MBOOL;

#ifndef MTRUE
#define MTRUE 1
#endif

#ifndef MFALSE
#define MFALSE 0
#endif

#ifndef MNULL
#define MNULL 0
#endif

/******************************************************************************
*Sensor Types
******************************************************************************/
// #define CCU_CODE_SLIM
// typedef unsigned char   BOOLEAN;    // uint8_t
typedef unsigned char        U8;    // uint8_t
typedef unsigned short      U16;    // uint16_t
typedef unsigned int        U32;    // uint32_t
//typedef unsigned long long  U64;    // uint64_t
typedef char                 I8;    // int8_t
typedef short               I16;    // int16_t
typedef int                 I32;    // int32_t
//typedef long long           I64;    // int64_t

#ifndef NULL
#define NULL                0
#endif  // NULL

#if defined(_WIN32)
#define inline              __inline
#endif  // defined(WIN32)

/******************************************************************************
* Error code
******************************************************************************/
#define ERR_NONE                    (0)
#define ERR_INVALID                 (-1)
#define ERR_TIMEOUT                 (-2)

/******************************************************************************
* For easier sensor customization/porting.
* The KAL data types are defined in
* "vendor/mediatek/proprietary/external/mbimd/include/kal.h".
******************************************************************************/
#ifndef __CCU_SENSOR_PORTING_TYPES__
#define __CCU_SENSOR_PORTING_TYPES__
typedef long                LONG;
typedef unsigned char       UBYTE;
typedef short               SHORT;
typedef signed char         S8;
typedef signed short        S16;
typedef signed int          S32;
typedef signed long long    S64;
typedef unsigned char       UINT8;
typedef unsigned short      UINT16;
typedef unsigned int        UINT32;
typedef unsigned short      USHORT;
typedef signed char         INT8;
typedef signed short        INT16;
typedef signed int          INT32;
typedef unsigned int        DWORD;
typedef void                VOID;
typedef unsigned char       BYTE;
typedef float               FLOAT;
// typedef unsigned int            *UINT32P;
// typedef volatile unsigned short *UINT16P;
// typedef volatile unsigned char  *UINT8P;
// typedef unsigned char           *U8P;
typedef unsigned char       kal_bool;
typedef unsigned char       kal_uint8;
typedef unsigned short      kal_uint16;
typedef unsigned int        kal_uint32;
typedef unsigned long long  kal_uint64;
typedef char                kal_int8;
typedef short               kal_int16;
typedef int                 kal_int32;
typedef long long           kal_int64;
typedef char                kal_char;
#define KAL_FALSE           (0)
#define KAL_TRUE            (1)

typedef unsigned char       u8;
typedef unsigned short      u16;
typedef unsigned int        u32;
#endif


#endif  // CCU_TYPES_H

