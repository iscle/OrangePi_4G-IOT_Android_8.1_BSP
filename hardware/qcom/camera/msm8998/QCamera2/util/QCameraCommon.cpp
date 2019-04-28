/* Copyright (c) 2016, The Linux Foundation. All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are
* met:
*     * Redistributions of source code must retain the above copyright
*       notice, this list of conditions and the following disclaimer.
*     * Redistributions in binary form must reproduce the above
*       copyright notice, this list of conditions and the following
*       disclaimer in the documentation and/or other materials provided
*       with the distribution.
*     * Neither the name of The Linux Foundation nor the names of its
*       contributors may be used to endorse or promote products derived
*       from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
* ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
* BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
* SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
* BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
* WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
* OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
* IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*
*/

#define LOG_TAG "QCameraCommon"

#include <cutils/properties.h>

// System dependencies
#include <utils/Errors.h>
#include <stdlib.h>
#include <string.h>
#include <utils/Log.h>
#include <math.h>


// Camera dependencies
#include "QCameraCommon.h"

extern "C" {
#include "mm_camera_dbg.h"
}

using namespace android;

namespace qcamera {

#ifndef TRUE
#define TRUE 1
#endif

#ifndef FALSE
#define FALSE 0
#endif

#define ASPECT_RATIO_TOLERANCE 0.01

/*===========================================================================
 * FUNCTION   : QCameraCommon
 *
 * DESCRIPTION: default constructor of QCameraCommon
 *
 * PARAMETERS : None
 *
 * RETURN     : None
 *==========================================================================*/
QCameraCommon::QCameraCommon() :
    m_pCapability(NULL)
{
}

/*===========================================================================
 * FUNCTION   : ~QCameraCommon
 *
 * DESCRIPTION: destructor of QCameraCommon
 *
 * PARAMETERS : None
 *
 * RETURN     : None
 *==========================================================================*/
QCameraCommon::~QCameraCommon()
{
}

/*===========================================================================
 * FUNCTION   : init
 *
 * DESCRIPTION: Init function for QCameraCommon
 *
 * PARAMETERS :
 *   @pCapability : Capabilities
 *
 * RETURN     : int32_t type of status
 *              NO_ERROR  -- success
 *              none-zero failure code
 *==========================================================================*/
int32_t QCameraCommon::init(cam_capability_t *pCapability)
{
    m_pCapability = pCapability;

    return NO_ERROR;
}

/*===========================================================================
 * FUNCTION   : calculateLCM
 *
 * DESCRIPTION: Get the LCM of 2 numbers
 *
 * PARAMETERS :
 *   @num1   : First number
 *   @num2   : second number
 *
 * RETURN     : int32_t type (LCM)
 *
 *==========================================================================*/
uint32_t QCameraCommon::calculateLCM(int32_t num1, int32_t num2)
{
   uint32_t lcm = 0;
   uint32_t temp = 0;

   if ((num1 < 1) && (num2 < 1)) {
       return 0;
   } else if (num1 < 1) {
       return num2;
   } else if (num2 < 1) {
       return num1;
   }

   if (num1 > num2) {
       lcm = num1;
   } else {
       lcm = num2;
   }
   temp = lcm;

   while (1) {
       if (((lcm % num1) == 0) && ((lcm % num2) == 0)) {
           break;
       }
       lcm += temp;
   }
   return lcm;
}

/*===========================================================================
 * FUNCTION   : getAnalysisInfo
 *
 * DESCRIPTION: Get the Analysis information based on
 *     current mode and feature mask
 *
 * PARAMETERS :
 *   @fdVideoEnabled : Whether fdVideo enabled currently
 *   @hal3           : Whether hal3 or hal1
 *   @featureMask    : Feature mask
 *   @pAnalysis_info : Analysis info to be filled
 *
 * RETURN     : int32_t type of status
 *              NO_ERROR  -- success
 *              none-zero failure code
 *==========================================================================*/
int32_t QCameraCommon::getAnalysisInfo(
        bool fdVideoEnabled,
        cam_feature_mask_t featureMask,
        cam_analysis_info_t *pAnalysisInfo)
{
    if (!pAnalysisInfo) {
        return BAD_VALUE;
    }

    pAnalysisInfo->valid = 0;

    if ((fdVideoEnabled == TRUE) &&
            (m_pCapability->analysis_info[CAM_ANALYSIS_INFO_FD_VIDEO].hw_analysis_supported) &&
            (m_pCapability->analysis_info[CAM_ANALYSIS_INFO_FD_VIDEO].valid)) {
        *pAnalysisInfo =
                m_pCapability->analysis_info[CAM_ANALYSIS_INFO_FD_VIDEO];
    } else if (m_pCapability->analysis_info[CAM_ANALYSIS_INFO_FD_STILL].valid) {
        *pAnalysisInfo =
                m_pCapability->analysis_info[CAM_ANALYSIS_INFO_FD_STILL];
    }

    if ((featureMask & CAM_QCOM_FEATURE_PAAF) &&
      (m_pCapability->analysis_info[CAM_ANALYSIS_INFO_PAAF].valid)) {
        cam_analysis_info_t *pPaafInfo =
          &m_pCapability->analysis_info[CAM_ANALYSIS_INFO_PAAF];

        if (!pAnalysisInfo->valid) {
            *pAnalysisInfo = *pPaafInfo;
        } else {
            pAnalysisInfo->analysis_max_res.width =
                MAX(pAnalysisInfo->analysis_max_res.width,
                pPaafInfo->analysis_max_res.width);
            pAnalysisInfo->analysis_max_res.height =
                MAX(pAnalysisInfo->analysis_max_res.height,
                pPaafInfo->analysis_max_res.height);
            pAnalysisInfo->analysis_recommended_res.width =
                MAX(pAnalysisInfo->analysis_recommended_res.width,
                pPaafInfo->analysis_recommended_res.width);
            pAnalysisInfo->analysis_recommended_res.height =
                MAX(pAnalysisInfo->analysis_recommended_res.height,
                pPaafInfo->analysis_recommended_res.height);
            pAnalysisInfo->analysis_padding_info.height_padding =
                calculateLCM(pAnalysisInfo->analysis_padding_info.height_padding,
                pPaafInfo->analysis_padding_info.height_padding);
            pAnalysisInfo->analysis_padding_info.width_padding =
                calculateLCM(pAnalysisInfo->analysis_padding_info.width_padding,
                pPaafInfo->analysis_padding_info.width_padding);
            pAnalysisInfo->analysis_padding_info.plane_padding =
                calculateLCM(pAnalysisInfo->analysis_padding_info.plane_padding,
                pPaafInfo->analysis_padding_info.plane_padding);
            pAnalysisInfo->analysis_padding_info.min_stride =
                MAX(pAnalysisInfo->analysis_padding_info.min_stride,
                pPaafInfo->analysis_padding_info.min_stride);
            pAnalysisInfo->analysis_padding_info.min_stride =
                ALIGN(pAnalysisInfo->analysis_padding_info.min_stride,
                pAnalysisInfo->analysis_padding_info.width_padding);

            pAnalysisInfo->analysis_padding_info.min_scanline =
                MAX(pAnalysisInfo->analysis_padding_info.min_scanline,
                pPaafInfo->analysis_padding_info.min_scanline);
            pAnalysisInfo->analysis_padding_info.min_scanline =
                ALIGN(pAnalysisInfo->analysis_padding_info.min_scanline,
                pAnalysisInfo->analysis_padding_info.height_padding);

            pAnalysisInfo->hw_analysis_supported |=
                pPaafInfo->hw_analysis_supported;
        }
    }
    return pAnalysisInfo->valid ? NO_ERROR : BAD_VALUE;
}

/*===========================================================================
 * FUNCTION   : getMatchingDimension
 *
 * DESCRIPTION: Get dimension closest to the current, but with matching aspect ratio
 *
 * PARAMETERS :
 *   @exp_dim : The dimension corresponding to desired aspect ratio
 *   @cur_dim : The dimension which has to be modified
 *
 * RETURN     : cam_dimension_t new dimensions as per desired aspect ratio
 *==========================================================================*/
cam_dimension_t QCameraCommon::getMatchingDimension(
        cam_dimension_t exp_dim,
        cam_dimension_t cur_dim)
{
    cam_dimension_t expected_dim = cur_dim;
    if ((exp_dim.width != 0) && (exp_dim.height != 0)) {
        double cur_ratio, expected_ratio;

        cur_ratio = (double)cur_dim.width / (double)cur_dim.height;
        expected_ratio = (double)exp_dim.width / (double)exp_dim.height;
        if (fabs(cur_ratio - expected_ratio) > ASPECT_RATIO_TOLERANCE) {
            if (cur_ratio < expected_ratio) {
                expected_dim.height = (int32_t)((double)cur_dim.width / expected_ratio);
            } else {
                expected_dim.width = (int32_t)((double)cur_dim.height * expected_ratio);
            }
            expected_dim.width &= ~0x1;
            expected_dim.height &= ~0x1;
        }
        LOGD("exp ratio: %f, cur ratio: %f, new dim: %d x %d",
                expected_ratio, cur_ratio, exp_dim.width, exp_dim.height);
    }
    return expected_dim;
}



/*===========================================================================
 * FUNCTION   : isVideoUBWCEnabled
 *
 * DESCRIPTION: Function to get UBWC hardware support for video.
 *
 * PARAMETERS : None
 *
 * RETURN     : TRUE -- UBWC format supported
 *              FALSE -- UBWC is not supported.
 *==========================================================================*/

bool QCameraCommon::isVideoUBWCEnabled()
{
#ifdef UBWC_PRESENT
    char prop[PROPERTY_VALUE_MAX];
    int pFormat;
    memset(prop, 0, sizeof(prop));
    /* Checking the property set by video
     * to disable/enable UBWC */
    property_get("video.disable.ubwc", prop, "0");
    pFormat = atoi(prop);
    if (pFormat == 0) {
        return TRUE;
    }
    return FALSE;
#else
    return FALSE;
#endif
}

}; // namespace qcamera
