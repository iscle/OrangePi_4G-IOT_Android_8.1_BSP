/*************************************************************************************
 * INTEL CONFIDENTIAL
 * Copyright 2008-2009 Intel Corporation All Rights Reserved. 
 * The source code contained or described herein and all documents related 
 * to the source code ("Material") are owned by Intel Corporation or its 
 * suppliers or licensors. Title to the Material remains with Intel 
 * Corporation or its suppliers and licensors. The Material contains trade 
 * secrets and proprietary and confidential information of Intel or its 
 * suppliers and licensors. The Material is protected by worldwide copyright 
 * and trade secret laws and treaty provisions. No part of the Material may 
 * be used, copied, reproduced, modified, published, uploaded, posted, 
 * transmitted, distributed, or disclosed in any way without Intel’s prior 
 * express written permission.
 *
 * No license under any patent, copyright, trade secret or other intellectual 
 * property right is granted to or conferred upon you by disclosure or delivery
 * of the Materials, either expressly, by implication, inducement, estoppel or
 * otherwise. Any license under such intellectual property rights must be express
 * and approved by Intel in writing.
 ************************************************************************************/

#ifndef MIX_RESULT_H
#define MIX_RESULT_H

#include <glib.h>

typedef gint32 MIX_RESULT;

#define MIX_SUCCEEDED(result_code) ((((MIX_RESULT)(result_code)) & 0x80000000) == 0)

typedef enum {
	/** General success */
	MIX_RESULT_SUCCESS 				= 	(MIX_RESULT) 0x00000000,
        MIX_RESULT_SUCCESS_CHG = (MIX_RESULT)0x00000001,

	/** Module specific success starting number */

	/** Starting success number for Audio */
	MIX_RESULT_SUCCESS_AUDIO_START			=	(MIX_RESULT) 0x00010000,
	/** Starting success number for Video */
	MIX_RESULT_SUCCESS_VIDEO_START			=	(MIX_RESULT) 0x00020000,
	/** Starting success number for DRM */
	MIX_RESULT_SUCCESS_DRM_START			= 	(MIX_RESULT) 0x00030000
} MIX_SUCCESS_COMMON;

typedef enum {
	/** General failure */
	MIX_RESULT_FAIL					= 	(MIX_RESULT) 0x80000000,
	MIX_RESULT_NULL_PTR				=	(MIX_RESULT) 0x80000001,
	MIX_RESULT_LPE_NOTAVAIL			= 	(MIX_RESULT) 0X80000002,
	MIX_RESULT_DIRECT_NOTAVAIL		=	(MIX_RESULT) 0x80000003,
	MIX_RESULT_NOT_SUPPORTED		=	(MIX_RESULT) 0x80000004,
	MIX_RESULT_CONF_MISMATCH		=	(MIX_RESULT) 0x80000005,
	MIX_RESULT_RESUME_NEEDED		=	(MIX_RESULT) 0x80000007,
	MIX_RESULT_WRONGMODE			= 	(MIX_RESULT) 0x80000008,
	MIX_RESULT_RESOURCES_NOTAVAIL = (MIX_RESULT)0x80000009,
        MIX_RESULT_INVALID_PARAM = (MIX_RESULT)0x8000000a,
        MIX_RESULT_ALREADY_INIT = (MIX_RESULT)0x8000000b,
        MIX_RESULT_WRONG_STATE = (MIX_RESULT)0x8000000c,
        MIX_RESULT_NOT_INIT = (MIX_RESULT)0x8000000d,
        MIX_RESULT_NOT_CONFIGURED = (MIX_RESULT)0x8000000e,
        MIX_RESULT_STREAM_NOTAVAIL = (MIX_RESULT)0x8000000f,
        MIX_RESULT_CODEC_NOTAVAIL = (MIX_RESULT)0x80000010,
        MIX_RESULT_CODEC_NOTSUPPORTED = (MIX_RESULT)0x80000011,
        MIX_RESULT_INVALID_COUNT = (MIX_RESULT)0x80000012,
        MIX_RESULT_NOT_ACP = (MIX_RESULT)0x80000013,
	MIX_RESULT_INVALID_DECODE_MODE = (MIX_RESULT)0x80000014,
        MIX_RESULT_INVALID_STREAM_NAME = (MIX_RESULT)0x80000015,
        MIX_RESULT_NO_MEMORY = (MIX_RESULT)0x80000016,
        MIX_RESULT_NEED_RETRY = (MIX_RESULT)0x80000017,
        MIX_RESULT_SYSTEM_ERRNO = (MIX_RESULT)0x80000018,

	/** Module specific errors starting number */

	/** Starting error number for Audio */
	MIX_RESULT_ERROR_AUDIO_START			=	(MIX_RESULT) 0x80010000,
	/** Starting error number for Video */
	MIX_RESULT_ERROR_VIDEO_START			=	(MIX_RESULT) 0x80020000,
	/** Starting error number for DRM */
	MIX_RESULT_ERROR_DRM_START				= 	(MIX_RESULT) 0x80030000
} MIX_ERROR_COMMON;

  /* New success code should be added just above this line */
//  MIX_RESULT_IAM_DISABLED,            /* 0x80000008 */
//  MIX_RESULT_IAM_NOTAVAIL,            /* 0x80000009 */
//  MIX_RESULT_IAM_REG_FAILED,          /* 0x8000000f */



#endif	// MIX_RESULT_H
