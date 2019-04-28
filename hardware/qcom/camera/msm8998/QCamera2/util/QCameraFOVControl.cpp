/* Copyright (c) 2016-2017, The Linux Foundation. All rights reserved.
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

#define LOG_TAG "QCameraFOVControl"

#include <stdlib.h>
#include <cutils/properties.h>
#include <utils/Errors.h>
#include "QCameraFOVControl.h"
#include "QCameraDualCamSettings.h"


extern "C" {
#include "mm_camera_dbg.h"
}

namespace qcamera {

/*===========================================================================
 * FUNCTION   : QCameraFOVControl constructor
 *
 * DESCRIPTION: class constructor
 *
 * PARAMETERS : none
 *
 * RETURN     : void
 *
 *==========================================================================*/
QCameraFOVControl::QCameraFOVControl()
{
    mZoomTranslator = NULL;
    memset(&mDualCamParams,    0, sizeof(dual_cam_params_t));
    memset(&mFovControlConfig, 0, sizeof(fov_control_config_t));
    memset(&mFovControlData,   0, sizeof(fov_control_data_t));
    memset(&mFovControlResult, 0, sizeof(fov_control_result_t));
}


/*===========================================================================
 * FUNCTION   : QCameraFOVControl destructor
 *
 * DESCRIPTION: class destructor
 *
 * PARAMETERS : none
 *
 * RETURN     : void
 *
 *==========================================================================*/
QCameraFOVControl::~QCameraFOVControl()
{
    // De-initialize zoom translator lib
    if (mZoomTranslator && mZoomTranslator->isInitialized()) {
        mZoomTranslator->deInit();
    }
}


/*===========================================================================
 * FUNCTION   : create
 *
 * DESCRIPTION: This is a static method to create FOV-control object. It calls
 *              private constructor of the class and only returns a valid object
 *              if it can successfully initialize the FOV-control.
 *
 * PARAMETERS :
 *  @capsMain : The capabilities for the main camera
 *  @capsAux  : The capabilities for the aux camera
 *
 * RETURN     : Valid object pointer if succeeds
 *              NULL if fails
 *
 *==========================================================================*/
QCameraFOVControl* QCameraFOVControl::create(
        cam_capability_t *capsMainCam,
        cam_capability_t *capsAuxCam)
{
    QCameraFOVControl *pFovControl  = NULL;

    if (capsMainCam && capsAuxCam) {
        // Create FOV control object
        pFovControl = new QCameraFOVControl();

        if (pFovControl) {
            bool  success = false;
            if (pFovControl->validateAndExtractParameters(capsMainCam, capsAuxCam)) {
                // Based on focal lengths, map main and aux camera to wide and tele
                if (pFovControl->mDualCamParams.paramsMain.focalLengthMm <
                    pFovControl->mDualCamParams.paramsAux.focalLengthMm) {
                    pFovControl->mFovControlData.camWide  = CAM_TYPE_MAIN;
                    pFovControl->mFovControlData.camTele  = CAM_TYPE_AUX;
                    pFovControl->mFovControlData.camState = STATE_WIDE;
                } else {
                    pFovControl->mFovControlData.camWide  = CAM_TYPE_AUX;
                    pFovControl->mFovControlData.camTele  = CAM_TYPE_MAIN;
                    pFovControl->mFovControlData.camState = STATE_TELE;
                }

                // Initialize the master info to main camera
                pFovControl->mFovControlResult.camMasterPreview  = CAM_TYPE_MAIN;
                pFovControl->mFovControlResult.camMaster3A       = CAM_TYPE_MAIN;

                // Check if LPM is enabled
                char prop[PROPERTY_VALUE_MAX];
                int lpmEnable = 1;
                property_get("persist.dualcam.lpm.enable", prop, "1");
                lpmEnable = atoi(prop);
                if ((lpmEnable == 0) || (DUALCAM_LPM_ENABLE == 0)) {
                    pFovControl->mFovControlData.lpmEnabled = false;
                } else {
                    pFovControl->mFovControlData.lpmEnabled = true;
                }

                // Open the external zoom translation library if requested
                if (FOVC_USE_EXTERNAL_ZOOM_TRANSLATOR) {
                    pFovControl->mZoomTranslator =
                            QCameraExtZoomTranslator::create();
                    if (!pFovControl->mZoomTranslator) {
                        LOGE("Unable to open zoom translation lib");
                    }
                }
                success = true;
            }

            if (!success) {
                LOGE("FOV-control: Failed to create an object");
                delete pFovControl;
                pFovControl = NULL;
            }
        } else {
            LOGE("FOV-control: Failed to allocate memory for FOV-control object");
        }
    }

    return pFovControl;
}


/*===========================================================================
 * FUNCTION    : consolidateCapabilities
 *
 * DESCRIPTION : Combine the capabilities from main and aux cameras to return
 *               the consolidated capabilities.
 *
 * PARAMETERS  :
 * @capsMainCam: Capabilities for the main camera
 * @capsAuxCam : Capabilities for the aux camera
 *
 * RETURN      : Consolidated capabilities
 *
 *==========================================================================*/
cam_capability_t QCameraFOVControl::consolidateCapabilities(
        cam_capability_t *capsMainCam,
        cam_capability_t *capsAuxCam)
{
    cam_capability_t capsConsolidated;
    memset(&capsConsolidated, 0, sizeof(cam_capability_t));

    if ((capsMainCam != NULL) &&
        (capsAuxCam  != NULL)) {

        memcpy(&capsConsolidated, capsMainCam, sizeof(cam_capability_t));

        // Consolidate preview sizes
        uint32_t previewSizesTblCntMain  = capsMainCam->preview_sizes_tbl_cnt;
        uint32_t previewSizesTblCntAux   = capsAuxCam->preview_sizes_tbl_cnt;
        uint32_t previewSizesTblCntFinal = 0;

        for (uint32_t i = 0; i < previewSizesTblCntMain; ++i) {
            for (uint32_t j = 0; j < previewSizesTblCntAux; ++j) {
                if ((capsMainCam->preview_sizes_tbl[i].width ==
                     capsAuxCam->preview_sizes_tbl[j].width) &&
                    (capsMainCam->preview_sizes_tbl[i].height ==
                     capsAuxCam->preview_sizes_tbl[j].height)) {
                    if (previewSizesTblCntFinal != i) {
                        capsConsolidated.preview_sizes_tbl[previewSizesTblCntFinal].width =
                           capsAuxCam->preview_sizes_tbl[j].width;
                        capsConsolidated.preview_sizes_tbl[previewSizesTblCntFinal].height =
                           capsMainCam->preview_sizes_tbl[j].height;
                    }
                    ++previewSizesTblCntFinal;
                    break;
                }
            }
        }
        capsConsolidated.preview_sizes_tbl_cnt = previewSizesTblCntFinal;

        // Consolidate video sizes
        uint32_t videoSizesTblCntMain  = capsMainCam->video_sizes_tbl_cnt;
        uint32_t videoSizesTblCntAux   = capsAuxCam->video_sizes_tbl_cnt;
        uint32_t videoSizesTblCntFinal = 0;

        for (uint32_t i = 0; i < videoSizesTblCntMain; ++i) {
            for (uint32_t j = 0; j < videoSizesTblCntAux; ++j) {
                if ((capsMainCam->video_sizes_tbl[i].width ==
                     capsAuxCam->video_sizes_tbl[j].width) &&
                    (capsMainCam->video_sizes_tbl[i].height ==
                     capsAuxCam->video_sizes_tbl[j].height)) {
                    if (videoSizesTblCntFinal != i) {
                        capsConsolidated.video_sizes_tbl[videoSizesTblCntFinal].width =
                           capsAuxCam->video_sizes_tbl[j].width;
                        capsConsolidated.video_sizes_tbl[videoSizesTblCntFinal].height =
                           capsMainCam->video_sizes_tbl[j].height;
                    }
                    ++videoSizesTblCntFinal;
                    break;
                }
            }
        }
        capsConsolidated.video_sizes_tbl_cnt = videoSizesTblCntFinal;

        // Consolidate livesnapshot sizes
        uint32_t livesnapshotSizesTblCntMain  = capsMainCam->livesnapshot_sizes_tbl_cnt;
        uint32_t livesnapshotSizesTblCntAux   = capsAuxCam->livesnapshot_sizes_tbl_cnt;
        uint32_t livesnapshotSizesTblCntFinal = 0;

        for (uint32_t i = 0; i < livesnapshotSizesTblCntMain; ++i) {
            for (uint32_t j = 0; j < livesnapshotSizesTblCntAux; ++j) {
                if ((capsMainCam->livesnapshot_sizes_tbl[i].width ==
                     capsAuxCam->livesnapshot_sizes_tbl[j].width) &&
                    (capsMainCam->livesnapshot_sizes_tbl[i].height ==
                     capsAuxCam->livesnapshot_sizes_tbl[j].height)) {
                    if (livesnapshotSizesTblCntFinal != i) {
                       capsConsolidated.livesnapshot_sizes_tbl[livesnapshotSizesTblCntFinal].width=
                          capsAuxCam->livesnapshot_sizes_tbl[j].width;
                       capsConsolidated.livesnapshot_sizes_tbl[livesnapshotSizesTblCntFinal].height=
                          capsMainCam->livesnapshot_sizes_tbl[j].height;
                    }
                    ++livesnapshotSizesTblCntFinal;
                    break;
                }
            }
        }
        capsConsolidated.livesnapshot_sizes_tbl_cnt = livesnapshotSizesTblCntFinal;

        // Consolidate picture size
        // Find max picture dimension for main camera
        cam_dimension_t maxPicDimMain;
        maxPicDimMain.width  = 0;
        maxPicDimMain.height = 0;

        for(uint32_t i = 0; i < (capsMainCam->picture_sizes_tbl_cnt - 1); ++i) {
            if ((maxPicDimMain.width * maxPicDimMain.height) <
                    (capsMainCam->picture_sizes_tbl[i].width *
                            capsMainCam->picture_sizes_tbl[i].height)) {
                maxPicDimMain.width  = capsMainCam->picture_sizes_tbl[i].width;
                maxPicDimMain.height = capsMainCam->picture_sizes_tbl[i].height;
            }
        }

        // Find max picture dimension for aux camera
        cam_dimension_t maxPicDimAux;
        maxPicDimAux.width  = 0;
        maxPicDimAux.height = 0;

        for(uint32_t i = 0; i < (capsAuxCam->picture_sizes_tbl_cnt - 1); ++i) {
            if ((maxPicDimAux.width * maxPicDimAux.height) <
                    (capsAuxCam->picture_sizes_tbl[i].width *
                            capsAuxCam->picture_sizes_tbl[i].height)) {
                maxPicDimAux.width  = capsAuxCam->picture_sizes_tbl[i].width;
                maxPicDimAux.height = capsAuxCam->picture_sizes_tbl[i].height;
            }
        }

        LOGH("MAIN Max picture wxh %dx%d", maxPicDimMain.width, maxPicDimMain.height);
        LOGH("AUX Max picture wxh %dx%d", maxPicDimAux.width, maxPicDimAux.height);

        // Choose the larger of the two max picture dimensions
        if ((maxPicDimAux.width * maxPicDimAux.height) >
                (maxPicDimMain.width * maxPicDimMain.height)) {
            capsConsolidated.picture_sizes_tbl_cnt = capsAuxCam->picture_sizes_tbl_cnt;
            memcpy(capsConsolidated.picture_sizes_tbl, capsAuxCam->picture_sizes_tbl,
                    (capsAuxCam->picture_sizes_tbl_cnt * sizeof(cam_dimension_t)));
        }
        LOGH("Consolidated Max picture wxh %dx%d", capsConsolidated.picture_sizes_tbl[0].width,
                capsConsolidated.picture_sizes_tbl[0].height);

        // Consolidate supported preview formats
        uint32_t supportedPreviewFmtCntMain  = capsMainCam->supported_preview_fmt_cnt;
        uint32_t supportedPreviewFmtCntAux   = capsAuxCam->supported_preview_fmt_cnt;
        uint32_t supportedPreviewFmtCntFinal = 0;
        for (uint32_t i = 0; i < supportedPreviewFmtCntMain; ++i) {
            for (uint32_t j = 0; j < supportedPreviewFmtCntAux; ++j) {
                if (capsMainCam->supported_preview_fmts[i] ==
                        capsAuxCam->supported_preview_fmts[j]) {
                    if (supportedPreviewFmtCntFinal != i) {
                        capsConsolidated.supported_preview_fmts[supportedPreviewFmtCntFinal] =
                            capsAuxCam->supported_preview_fmts[j];
                    }
                    ++supportedPreviewFmtCntFinal;
                    break;
                }
            }
        }
        capsConsolidated.supported_preview_fmt_cnt = supportedPreviewFmtCntFinal;

        // Consolidate supported picture formats
        uint32_t supportedPictureFmtCntMain  = capsMainCam->supported_picture_fmt_cnt;
        uint32_t supportedPictureFmtCntAux   = capsAuxCam->supported_picture_fmt_cnt;
        uint32_t supportedPictureFmtCntFinal = 0;
        for (uint32_t i = 0; i < supportedPictureFmtCntMain; ++i) {
            for (uint32_t j = 0; j < supportedPictureFmtCntAux; ++j) {
                if (capsMainCam->supported_picture_fmts[i] ==
                        capsAuxCam->supported_picture_fmts[j]) {
                    if (supportedPictureFmtCntFinal != i) {
                        capsConsolidated.supported_picture_fmts[supportedPictureFmtCntFinal] =
                            capsAuxCam->supported_picture_fmts[j];
                    }
                    ++supportedPictureFmtCntFinal;
                    break;
                }
            }
        }
        capsConsolidated.supported_picture_fmt_cnt = supportedPictureFmtCntFinal;

        if (mZoomTranslator) {
            // Copy the opaque calibration data pointer and size
            mFovControlData.zoomTransInitData.calibData =
                    capsConsolidated.related_cam_calibration.dc_otp_params;
            mFovControlData.zoomTransInitData.calibDataSize =
                    capsConsolidated.related_cam_calibration.dc_otp_size;
        }
    }
    return capsConsolidated;
}


/*===========================================================================
 * FUNCTION    : resetVars
 *
 * DESCRIPTION : Reset the variables used in FOV-control.
 *
 * PARAMETERS  : None
 *
 * RETURN      : None
 *
 *==========================================================================*/
void QCameraFOVControl::resetVars()
{
    // Copy the FOV-control settings for camera/camcorder from QCameraFOVControlSettings.h
    if (mFovControlData.camcorderMode) {
        mFovControlConfig.snapshotPPConfig.enablePostProcess =
                FOVC_CAMCORDER_SNAPSHOT_PP_ENABLE;
    } else {
        mFovControlConfig.snapshotPPConfig.enablePostProcess = FOVC_CAM_SNAPSHOT_PP_ENABLE;
        mFovControlConfig.snapshotPPConfig.zoomMin           = FOVC_CAM_SNAPSHOT_PP_ZOOM_MIN;
        mFovControlConfig.snapshotPPConfig.zoomMax           = FOVC_CAM_SNAPSHOT_PP_ZOOM_MAX;
        mFovControlConfig.snapshotPPConfig.luxMin            = FOVC_CAM_SNAPSHOT_PP_LUX_MIN;
    }
    mFovControlConfig.auxSwitchBrightnessMin  = FOVC_AUXCAM_SWITCH_LUX_MIN;
    mFovControlConfig.auxSwitchFocusDistCmMin = FOVC_AUXCAM_SWITCH_FOCUS_DIST_CM_MIN;

    mFovControlData.fallbackEnabled = FOVC_MAIN_CAM_FALLBACK_MECHANISM;

    mFovControlConfig.zoomStableCountThreshold       = FOVC_ZOOM_STABLE_COUNT_THRESHOLD;
    mFovControlConfig.focusDistStableCountThreshold  = FOVC_FOCUS_DIST_STABLE_COUNT_THRESHOLD;
    mFovControlConfig.brightnessStableCountThreshold = FOVC_BRIGHTNESS_STABLE_COUNT_THRESHOLD;

    // Reset variables
    mFovControlData.zoomStableCount       = 0;
    mFovControlData.brightnessStableCount = 0;
    mFovControlData.focusDistStableCount  = 0;
    mFovControlData.zoomDirection         = ZOOM_STABLE;
    mFovControlData.fallbackToWide        = false;

    mFovControlData.status3A.main.af.status   = AF_INVALID;
    mFovControlData.status3A.aux.af.status    = AF_INVALID;

    mFovControlData.afStatusMain = CAM_AF_STATE_INACTIVE;
    mFovControlData.afStatusAux  = CAM_AF_STATE_INACTIVE;

    mFovControlData.wideCamStreaming = false;
    mFovControlData.teleCamStreaming = false;

    mFovControlData.spatialAlignResult.readyStatus = 0;
    mFovControlData.spatialAlignResult.activeCameras = 0;
    mFovControlData.spatialAlignResult.camMasterHint = 0;
    mFovControlData.spatialAlignResult.shiftWide.shiftHorz = 0;
    mFovControlData.spatialAlignResult.shiftWide.shiftVert = 0;
    mFovControlData.spatialAlignResult.shiftTele.shiftHorz = 0;
    mFovControlData.spatialAlignResult.shiftTele.shiftVert = 0;

    // WA for now until the QTI solution is in place writing the spatial alignment ready status
    mFovControlData.spatialAlignResult.readyStatus = 1;
}

/*===========================================================================
 * FUNCTION    : updateConfigSettings
 *
 * DESCRIPTION : Update the config settings such as margins and preview size
 *               and recalculate the transition parameters.
 *
 * PARAMETERS  :
 * @capsMainCam: Capabilities for the main camera
 * @capsAuxCam : Capabilities for the aux camera
 *
 * RETURN :
 * NO_ERROR           : Success
 * INVALID_OPERATION  : Failure
 *
 *==========================================================================*/
int32_t QCameraFOVControl::updateConfigSettings(
        parm_buffer_t* paramsMainCam,
        parm_buffer_t* paramsAuxCam)
{
    int32_t rc = INVALID_OPERATION;

    if (paramsMainCam &&
        paramsAuxCam  &&
        paramsMainCam->is_valid[CAM_INTF_META_STREAM_INFO] &&
        paramsAuxCam->is_valid[CAM_INTF_META_STREAM_INFO]) {

        cam_stream_size_info_t camMainStreamInfo;
        READ_PARAM_ENTRY(paramsMainCam, CAM_INTF_META_STREAM_INFO, camMainStreamInfo);
        mFovControlData.camcorderMode = false;

        // Identify if in camera or camcorder mode
        for (int i = 0; i < MAX_NUM_STREAMS; ++i) {
            if (camMainStreamInfo.type[i] == CAM_STREAM_TYPE_VIDEO) {
                mFovControlData.camcorderMode = true;
            }
        }

        // Get the margins for the main camera. If video stream is present, the margins correspond
        // to video stream. Otherwise, margins are copied from preview stream.
        for (int i = 0; i < MAX_NUM_STREAMS; ++i) {
            if (camMainStreamInfo.type[i] == CAM_STREAM_TYPE_VIDEO) {
                mFovControlData.camMainWidthMargin  = camMainStreamInfo.margins[i].widthMargins;
                mFovControlData.camMainHeightMargin = camMainStreamInfo.margins[i].heightMargins;
            }
            if (camMainStreamInfo.type[i] == CAM_STREAM_TYPE_PREVIEW) {
                // Update the preview dimension and ISP output size
                mFovControlData.previewSize = camMainStreamInfo.stream_sizes[i];
                mFovControlData.ispOutSize  = camMainStreamInfo.stream_sz_plus_margin[i];
                if (!mFovControlData.camcorderMode) {
                    mFovControlData.camMainWidthMargin  =
                            camMainStreamInfo.margins[i].widthMargins;
                    mFovControlData.camMainHeightMargin =
                            camMainStreamInfo.margins[i].heightMargins;
                    break;
                }
            }
        }

        // Get the margins for the aux camera. If video stream is present, the margins correspond
        // to the video stream. Otherwise, margins are copied from preview stream.
        cam_stream_size_info_t camAuxStreamInfo;
        READ_PARAM_ENTRY(paramsAuxCam, CAM_INTF_META_STREAM_INFO, camAuxStreamInfo);
        for (int i = 0; i < MAX_NUM_STREAMS; ++i) {
            if (camAuxStreamInfo.type[i] == CAM_STREAM_TYPE_VIDEO) {
                mFovControlData.camAuxWidthMargin  = camAuxStreamInfo.margins[i].widthMargins;
                mFovControlData.camAuxHeightMargin = camAuxStreamInfo.margins[i].heightMargins;
            }
            if (camAuxStreamInfo.type[i] == CAM_STREAM_TYPE_PREVIEW) {
                // Update the preview dimension
                mFovControlData.previewSize = camAuxStreamInfo.stream_sizes[i];
                if (!mFovControlData.camcorderMode) {
                    mFovControlData.camAuxWidthMargin  = camAuxStreamInfo.margins[i].widthMargins;
                    mFovControlData.camAuxHeightMargin = camAuxStreamInfo.margins[i].heightMargins;
                    break;
                }
            }
        }

#if 0 // Update to 07.01.01.253.071
        // Get the sensor out dimensions
        cam_dimension_t sensorDimMain = {0,0};
        cam_dimension_t sensorDimAux  = {0,0};
        if (paramsMainCam->is_valid[CAM_INTF_PARM_RAW_DIMENSION]) {
            READ_PARAM_ENTRY(paramsMainCam, CAM_INTF_PARM_RAW_DIMENSION, sensorDimMain);
        }
        if (paramsAuxCam->is_valid[CAM_INTF_PARM_RAW_DIMENSION]) {
            READ_PARAM_ENTRY(paramsAuxCam, CAM_INTF_PARM_RAW_DIMENSION, sensorDimAux);
        }
#endif // Update to 07.01.01.253.071

        // Reset the internal variables
        resetVars();

        // Recalculate the transition parameters
        if (calculateBasicFovRatio() && combineFovAdjustment()) {

            calculateDualCamTransitionParams();

            // Set initial camera state
            float zoom = findZoomRatio(mFovControlData.zoomWide) /
                    (float)mFovControlData.zoomRatioTable[0];
            if (zoom > mFovControlData.transitionParams.cutOverWideToTele) {
                mFovControlResult.camMasterPreview  = mFovControlData.camTele;
                mFovControlResult.camMaster3A       = mFovControlData.camTele;
                mFovControlResult.activeCameras     = (uint32_t)mFovControlData.camTele;
                mFovControlData.camState            = STATE_TELE;
                LOGD("start camera state: TELE");
            } else {
                mFovControlResult.camMasterPreview  = mFovControlData.camWide;
                mFovControlResult.camMaster3A       = mFovControlData.camWide;
                mFovControlResult.activeCameras     = (uint32_t)mFovControlData.camWide;
                mFovControlData.camState            = STATE_WIDE;
                LOGD("start camera state: WIDE");
            }
            mFovControlResult.snapshotPostProcess = false;

            // Deinit zoom translation lib if needed
            if (mZoomTranslator && mZoomTranslator->isInitialized()) {
                if (mZoomTranslator->deInit() != NO_ERROR) {
                    ALOGW("deinit failed for zoom translation lib");
                }
            }

#if 0 // Update to 07.01.01.253.071
            // Initialize the zoom translation lib
            if (mZoomTranslator) {
                // Set the initialization data
                mFovControlData.zoomTransInitData.previewDimension.width =
                        mFovControlData.previewSize.width;
                mFovControlData.zoomTransInitData.previewDimension.height =
                        mFovControlData.previewSize.height;
                mFovControlData.zoomTransInitData.ispOutDimension.width =
                        mFovControlData.ispOutSize.width;
                mFovControlData.zoomTransInitData.ispOutDimension.height =
                        mFovControlData.ispOutSize.height;
                mFovControlData.zoomTransInitData.sensorOutDimensionMain.width =
                        sensorDimMain.width;
                mFovControlData.zoomTransInitData.sensorOutDimensionMain.height =
                        sensorDimMain.height;
                mFovControlData.zoomTransInitData.sensorOutDimensionAux.width =
                        sensorDimAux.width;
                mFovControlData.zoomTransInitData.sensorOutDimensionAux.height =
                        sensorDimAux.height;
                mFovControlData.zoomTransInitData.zoomRatioTable =
                        mFovControlData.zoomRatioTable;
                mFovControlData.zoomTransInitData.zoomRatioTableCount =
                        mFovControlData.zoomRatioTableCount;
                mFovControlData.zoomTransInitData.mode = mFovControlData.camcorderMode ?
                        MODE_CAMCORDER : MODE_CAMERA;

                if(mZoomTranslator->init(mFovControlData.zoomTransInitData) != NO_ERROR) {
                    LOGE("init failed for zoom translation lib");

                    // deinitialize the zoom translator and set to NULL
                    mZoomTranslator->deInit();
                    mZoomTranslator = NULL;
                }
            }
#endif // Update to 07.01.01.253.071

            // FOV-control config is complete for the current use case
            mFovControlData.configCompleted = true;
            rc = NO_ERROR;
        }
    }

    return rc;
}


/*===========================================================================
 * FUNCTION   : translateInputParams
 *
 * DESCRIPTION: Translate a subset of input parameters from main camera. As main
 *              and aux cameras have different properties/params, this translation
 *              is needed before the input parameters are sent to the aux camera.
 *
 * PARAMETERS :
 * @paramsMainCam : Input parameters for main camera
 * @paramsAuxCam  : Input parameters for aux camera
 *
 * RETURN :
 * NO_ERROR           : Success
 * INVALID_OPERATION  : Failure
 *
 *==========================================================================*/
int32_t QCameraFOVControl::translateInputParams(
        parm_buffer_t* paramsMainCam,
        parm_buffer_t* paramsAuxCam)
{
    int32_t rc = INVALID_OPERATION;
    if (paramsMainCam && paramsAuxCam) {
        // First copy all the parameters from main to aux and then translate the subset
        memcpy(paramsAuxCam, paramsMainCam, sizeof(parm_buffer_t));

        // Translate zoom
        if (paramsMainCam->is_valid[CAM_INTF_PARM_ZOOM]) {
            uint32_t userZoom = 0;
            READ_PARAM_ENTRY(paramsMainCam, CAM_INTF_PARM_ZOOM, userZoom);
            convertUserZoomToWideAndTele(userZoom);

            // Update zoom values in the param buffers
            uint32_t zoomMain = isMainCamFovWider() ?
                    mFovControlData.zoomWide : mFovControlData.zoomTele;
            ADD_SET_PARAM_ENTRY_TO_BATCH(paramsMainCam, CAM_INTF_PARM_ZOOM, zoomMain);

            uint32_t zoomAux = isMainCamFovWider() ?
                    mFovControlData.zoomTele : mFovControlData.zoomWide;
            ADD_SET_PARAM_ENTRY_TO_BATCH(paramsAuxCam, CAM_INTF_PARM_ZOOM, zoomAux);

            // Write the user zoom in main and aux param buffers
            // The user zoom will always correspond to the wider camera
            paramsMainCam->is_valid[CAM_INTF_PARM_DC_USERZOOM] = 1;
            paramsAuxCam->is_valid[CAM_INTF_PARM_DC_USERZOOM]  = 1;

            ADD_SET_PARAM_ENTRY_TO_BATCH(paramsMainCam, CAM_INTF_PARM_DC_USERZOOM,
                    mFovControlData.zoomWide);
            ADD_SET_PARAM_ENTRY_TO_BATCH(paramsAuxCam, CAM_INTF_PARM_DC_USERZOOM,
                    mFovControlData.zoomWide);

            // Generate FOV-control result
            generateFovControlResult();
        }

        // Translate focus areas
        if (paramsMainCam->is_valid[CAM_INTF_PARM_AF_ROI]) {
            cam_roi_info_t roiAfMain;
            cam_roi_info_t roiAfAux;
            READ_PARAM_ENTRY(paramsMainCam, CAM_INTF_PARM_AF_ROI, roiAfMain);
            if (roiAfMain.num_roi > 0) {
                roiAfAux = translateFocusAreas(roiAfMain, CAM_TYPE_AUX);
                roiAfMain = translateFocusAreas(roiAfMain, CAM_TYPE_MAIN);
                ADD_SET_PARAM_ENTRY_TO_BATCH(paramsAuxCam, CAM_INTF_PARM_AF_ROI, roiAfAux);
                ADD_SET_PARAM_ENTRY_TO_BATCH(paramsMainCam, CAM_INTF_PARM_AF_ROI, roiAfMain);
            }
        }

        // Translate metering areas
        if (paramsMainCam->is_valid[CAM_INTF_PARM_AEC_ROI]) {
            cam_set_aec_roi_t roiAecMain;
            cam_set_aec_roi_t roiAecAux;
            READ_PARAM_ENTRY(paramsMainCam, CAM_INTF_PARM_AEC_ROI, roiAecMain);
            if (roiAecMain.aec_roi_enable == CAM_AEC_ROI_ON) {
                roiAecAux = translateMeteringAreas(roiAecMain, CAM_TYPE_AUX);
                roiAecMain = translateMeteringAreas(roiAecMain, CAM_TYPE_MAIN);
                ADD_SET_PARAM_ENTRY_TO_BATCH(paramsAuxCam, CAM_INTF_PARM_AEC_ROI, roiAecAux);
                ADD_SET_PARAM_ENTRY_TO_BATCH(paramsMainCam, CAM_INTF_PARM_AEC_ROI, roiAecMain);
            }
        }
        rc = NO_ERROR;
    }
    return rc;
}


/*===========================================================================
 * FUNCTION   : processResultMetadata
 *
 * DESCRIPTION: Process the metadata from main and aux cameras to generate the
 *              result metadata. The result metadata should be the metadata
 *              coming from the master camera. If aux camera is master, the
 *              subset of the metadata needs to be translated to main as that's
 *              the only camera seen by the application.
 *
 * PARAMETERS :
 * @metaMain  : metadata for main camera
 * @metaAux   : metadata for aux camera
 *
 * RETURN :
 * Result metadata for the logical camera. After successfully processing main
 * and aux metadata, the result metadata points to either main or aux metadata
 * based on which one was the master. In case of failure, it returns NULL.
 *==========================================================================*/
metadata_buffer_t* QCameraFOVControl::processResultMetadata(
        metadata_buffer_t*  metaMain,
        metadata_buffer_t*  metaAux)
{
    metadata_buffer_t* metaResult = NULL;

    if (metaMain || metaAux) {
        metadata_buffer_t *meta   = metaMain ? metaMain : metaAux;
        cam_sync_type_t masterCam = mFovControlResult.camMasterPreview;

        mMutex.lock();
        // Book-keep the needed metadata from main camera and aux camera
        IF_META_AVAILABLE(cam_sac_output_info_t, spatialAlignOutput,
                CAM_INTF_META_DC_SAC_OUTPUT_INFO, meta) {

            // Get master camera hint
            if (spatialAlignOutput->is_master_hint_valid) {
                uint8_t master = spatialAlignOutput->master_hint;
                if (master == CAM_ROLE_WIDE) {
                    mFovControlData.spatialAlignResult.camMasterHint = mFovControlData.camWide;
                } else if (master == CAM_ROLE_TELE) {
                    mFovControlData.spatialAlignResult.camMasterHint = mFovControlData.camTele;
                }
            }

            // Get master camera used for the preview in the frame corresponding to this metadata
            if (spatialAlignOutput->is_master_preview_valid) {
                uint8_t master = spatialAlignOutput->master_preview;
                if (master == CAM_ROLE_WIDE) {
                    masterCam = mFovControlData.camWide;
                    mFovControlData.spatialAlignResult.camMasterPreview = masterCam;
                } else if (master == CAM_ROLE_TELE) {
                    masterCam = mFovControlData.camTele;
                    mFovControlData.spatialAlignResult.camMasterPreview = masterCam;
                }
            }

            // Get master camera used for 3A in the frame corresponding to this metadata
            if (spatialAlignOutput->is_master_3A_valid) {
                uint8_t master = spatialAlignOutput->master_3A;
                if (master == CAM_ROLE_WIDE) {
                    mFovControlData.spatialAlignResult.camMaster3A = mFovControlData.camWide;
                } else if (master == CAM_ROLE_TELE) {
                    mFovControlData.spatialAlignResult.camMaster3A = mFovControlData.camTele;
                }
            }

            // Get spatial alignment ready status
            if (spatialAlignOutput->is_ready_status_valid) {
                mFovControlData.spatialAlignResult.readyStatus = spatialAlignOutput->ready_status;
            }
        }

        metadata_buffer_t *metaWide = isMainCamFovWider() ? metaMain : metaAux;
        metadata_buffer_t *metaTele = isMainCamFovWider() ? metaAux : metaMain;

        // Get spatial alignment output info for wide camera
        if (metaWide) {
            IF_META_AVAILABLE(cam_sac_output_info_t, spatialAlignOutput,
                CAM_INTF_META_DC_SAC_OUTPUT_INFO, metaWide) {
                // Get spatial alignment output shift for wide camera

                if (spatialAlignOutput->is_output_shift_valid) {
                    // Calculate the spatial alignment shift for the current stream dimensions based
                    // on the reference resolution used for the output shift.
                    float horzShiftFactor = (float)mFovControlData.previewSize.width /
                            spatialAlignOutput->reference_res_for_output_shift.width;
                    float vertShiftFactor = (float)mFovControlData.previewSize.height /
                            spatialAlignOutput->reference_res_for_output_shift.height;

                    mFovControlData.spatialAlignResult.shiftWide.shiftHorz =
                            spatialAlignOutput->output_shift.shift_horz * horzShiftFactor;
                    mFovControlData.spatialAlignResult.shiftWide.shiftVert =
                            spatialAlignOutput->output_shift.shift_vert * vertShiftFactor;

                    LOGD("SAC output shift for Wide: x:%d, y:%d",
                            mFovControlData.spatialAlignResult.shiftWide.shiftHorz,
                            mFovControlData.spatialAlignResult.shiftWide.shiftVert);
                }

                // Get the AF roi shift for wide camera
                if (spatialAlignOutput->is_focus_roi_shift_valid) {
                    // Calculate the spatial alignment shift for the current stream dimensions based
                    // on the reference resolution used for the output shift.
                    float horzShiftFactor = (float)mFovControlData.previewSize.width /
                            spatialAlignOutput->reference_res_for_focus_roi_shift.width;
                    float vertShiftFactor = (float)mFovControlData.previewSize.height /
                            spatialAlignOutput->reference_res_for_focus_roi_shift.height;

                    mFovControlData.spatialAlignResult.shiftAfRoiWide.shiftHorz =
                            spatialAlignOutput->focus_roi_shift.shift_horz * horzShiftFactor;
                    mFovControlData.spatialAlignResult.shiftAfRoiWide.shiftVert =
                            spatialAlignOutput->focus_roi_shift.shift_vert * vertShiftFactor;

                    LOGD("SAC AF ROI shift for Wide: x:%d, y:%d",
                            mFovControlData.spatialAlignResult.shiftAfRoiWide.shiftHorz,
                            mFovControlData.spatialAlignResult.shiftAfRoiWide.shiftVert);
                }
            }
        }

        // Get spatial alignment output info for tele camera
        if (metaTele) {
            IF_META_AVAILABLE(cam_sac_output_info_t, spatialAlignOutput,
                CAM_INTF_META_DC_SAC_OUTPUT_INFO, metaTele) {

                // Get spatial alignment output shift for tele camera
                if (spatialAlignOutput->is_output_shift_valid) {
                    // Calculate the spatial alignment shift for the current stream dimensions based
                    // on the reference resolution used for the output shift.
                    float horzShiftFactor = (float)mFovControlData.previewSize.width /
                            spatialAlignOutput->reference_res_for_output_shift.width;
                    float vertShiftFactor = (float)mFovControlData.previewSize.height /
                            spatialAlignOutput->reference_res_for_output_shift.height;

                    mFovControlData.spatialAlignResult.shiftTele.shiftHorz =
                            spatialAlignOutput->output_shift.shift_horz * horzShiftFactor;
                    mFovControlData.spatialAlignResult.shiftTele.shiftVert =
                            spatialAlignOutput->output_shift.shift_vert * vertShiftFactor;

                    LOGD("SAC output shift for Tele: x:%d, y:%d",
                            mFovControlData.spatialAlignResult.shiftTele.shiftHorz,
                            mFovControlData.spatialAlignResult.shiftTele.shiftVert);
                }

                // Get the AF roi shift for tele camera
                if (spatialAlignOutput->is_focus_roi_shift_valid) {
                    // Calculate the spatial alignment shift for the current stream dimensions based
                    // on the reference resolution used for the output shift.
                    float horzShiftFactor = (float)mFovControlData.previewSize.width /
                            spatialAlignOutput->reference_res_for_focus_roi_shift.width;
                    float vertShiftFactor = (float)mFovControlData.previewSize.height /
                            spatialAlignOutput->reference_res_for_focus_roi_shift.height;

                    mFovControlData.spatialAlignResult.shiftAfRoiTele.shiftHorz =
                            spatialAlignOutput->focus_roi_shift.shift_horz * horzShiftFactor;
                    mFovControlData.spatialAlignResult.shiftAfRoiTele.shiftVert =
                            spatialAlignOutput->focus_roi_shift.shift_vert * vertShiftFactor;

                    LOGD("SAC AF ROI shift for Tele: x:%d, y:%d",
                            mFovControlData.spatialAlignResult.shiftAfRoiTele.shiftHorz,
                            mFovControlData.spatialAlignResult.shiftAfRoiTele.shiftVert);
                }
            }
        }

        // Update the camera streaming status
        if (metaWide) {
            mFovControlData.wideCamStreaming = true;
            IF_META_AVAILABLE(uint8_t, enableLPM, CAM_INTF_META_DC_LOW_POWER_ENABLE, metaWide) {
                if (*enableLPM) {
                    // If LPM enabled is 1, this is probably the last metadata returned
                    // before going into LPM state
                    mFovControlData.wideCamStreaming = false;

                    // Update active cameras requested by spatial alignment
                    mFovControlData.spatialAlignResult.activeCameras &= ~mFovControlData.camWide;
                } else {
                    mFovControlData.spatialAlignResult.activeCameras |= mFovControlData.camWide;
                }
            }
        }

        if (metaTele) {
            mFovControlData.teleCamStreaming = true;
            IF_META_AVAILABLE(uint8_t, enableLPM, CAM_INTF_META_DC_LOW_POWER_ENABLE, metaTele) {
                if (*enableLPM) {
                    // If LPM enabled is 1, this is probably the last metadata returned
                    // before going into LPM state
                    mFovControlData.teleCamStreaming = false;

                    // Update active cameras requested by spatial alignment
                    mFovControlData.spatialAlignResult.activeCameras &= ~mFovControlData.camTele;
                } else {
                    mFovControlData.spatialAlignResult.activeCameras |= mFovControlData.camTele;
                }
            }
        }

        // Get AF status
        if (metaMain) {
            IF_META_AVAILABLE(uint32_t, afState, CAM_INTF_META_AF_STATE, metaMain) {
                if ((*afState) != CAM_AF_STATE_INACTIVE) {
                    mFovControlData.status3A.main.af.status = AF_VALID;
                } else {
                    mFovControlData.status3A.main.af.status = AF_INVALID;
                }
                mFovControlData.afStatusMain = *afState;
                LOGD("AF state: Main cam: %d", mFovControlData.afStatusMain);
            }

            IF_META_AVAILABLE(float, luxIndex, CAM_INTF_META_AEC_LUX_INDEX, metaMain) {
                mFovControlData.status3A.main.ae.luxIndex = *luxIndex;
                LOGD("Lux Index: Main cam: %f", mFovControlData.status3A.main.ae.luxIndex);
            }

            IF_META_AVAILABLE(int32_t, objDist, CAM_INTF_META_AF_OBJ_DIST_CM, metaMain) {
                mFovControlData.status3A.main.af.focusDistCm = (*objDist < 0) ? 0 : *objDist;
                LOGD("Obj Dist: Main cam: %d", mFovControlData.status3A.main.af.focusDistCm);
            }
        }
        if (metaAux) {
            IF_META_AVAILABLE(uint32_t, afState, CAM_INTF_META_AF_STATE, metaAux) {
                if ((*afState) != CAM_AF_STATE_INACTIVE) {
                    mFovControlData.status3A.aux.af.status = AF_VALID;
                } else {
                    mFovControlData.status3A.aux.af.status = AF_INVALID;
                }
                mFovControlData.afStatusAux = *afState;
                LOGD("AF state: Aux cam: %d", mFovControlData.afStatusAux);
            }

            IF_META_AVAILABLE(float, luxIndex, CAM_INTF_META_AEC_LUX_INDEX, metaAux) {
                mFovControlData.status3A.aux.ae.luxIndex = *luxIndex;
                LOGD("Lux Index: Aux cam: %f", mFovControlData.status3A.aux.ae.luxIndex);
            }

            IF_META_AVAILABLE(int32_t, objDist, CAM_INTF_META_AF_OBJ_DIST_CM, metaAux) {
                mFovControlData.status3A.aux.af.focusDistCm = (*objDist < 0) ? 0 : *objDist;
                LOGD("Obj Dist: Aux cam: %d", mFovControlData.status3A.aux.af.focusDistCm);
            }
        }

        if ((masterCam == CAM_TYPE_AUX) && metaAux) {
            // Translate face detection ROI from aux camera
            IF_META_AVAILABLE(cam_face_detection_data_t, metaFD,
                    CAM_INTF_META_FACE_DETECTION, metaAux) {
                cam_face_detection_data_t metaFDTranslated;
                metaFDTranslated = translateRoiFD(*metaFD, CAM_TYPE_AUX);
                ADD_SET_PARAM_ENTRY_TO_BATCH(metaAux, CAM_INTF_META_FACE_DETECTION,
                        metaFDTranslated);
            }
            metaResult = metaAux;
        }
        else if ((masterCam == CAM_TYPE_MAIN) && metaMain) {
            // Translate face detection ROI from main camera
            IF_META_AVAILABLE(cam_face_detection_data_t, metaFD,
                    CAM_INTF_META_FACE_DETECTION, metaMain) {
                cam_face_detection_data_t metaFDTranslated;
                metaFDTranslated = translateRoiFD(*metaFD, CAM_TYPE_MAIN);
                ADD_SET_PARAM_ENTRY_TO_BATCH(metaMain, CAM_INTF_META_FACE_DETECTION,
                        metaFDTranslated);
            }
            metaResult = metaMain;
        } else {
            // Metadata for the master camera was dropped
            metaResult = NULL;
        }

        // If snapshot postprocess is enabled, consolidate the AF status to be sent to the app
        // when in the transition state.
        // Only return focused if both are focused.
        if ((mFovControlResult.snapshotPostProcess == true) &&
                    (mFovControlData.camState == STATE_TRANSITION) &&
                    metaResult) {
            if (((mFovControlData.afStatusMain == CAM_AF_STATE_FOCUSED_LOCKED) ||
                    (mFovControlData.afStatusMain == CAM_AF_STATE_NOT_FOCUSED_LOCKED)) &&
                    ((mFovControlData.afStatusAux == CAM_AF_STATE_FOCUSED_LOCKED) ||
                    (mFovControlData.afStatusAux == CAM_AF_STATE_NOT_FOCUSED_LOCKED))) {
                // If both indicate focused, return focused.
                // If either one indicates 'not focused', return 'not focused'.
                if ((mFovControlData.afStatusMain == CAM_AF_STATE_FOCUSED_LOCKED) &&
                        (mFovControlData.afStatusAux  == CAM_AF_STATE_FOCUSED_LOCKED)) {
                    ADD_SET_PARAM_ENTRY_TO_BATCH(metaResult, CAM_INTF_META_AF_STATE,
                            CAM_AF_STATE_FOCUSED_LOCKED);
                } else {
                    ADD_SET_PARAM_ENTRY_TO_BATCH(metaResult, CAM_INTF_META_AF_STATE,
                            CAM_AF_STATE_NOT_FOCUSED_LOCKED);
                }
            } else {
                // If either one indicates passive state or active scan, return that state
                if ((mFovControlData.afStatusMain != CAM_AF_STATE_FOCUSED_LOCKED) &&
                        (mFovControlData.afStatusMain != CAM_AF_STATE_NOT_FOCUSED_LOCKED)) {
                    ADD_SET_PARAM_ENTRY_TO_BATCH(metaResult, CAM_INTF_META_AF_STATE,
                            mFovControlData.afStatusMain);
                } else {
                    ADD_SET_PARAM_ENTRY_TO_BATCH(metaResult, CAM_INTF_META_AF_STATE,
                            mFovControlData.afStatusAux);
                }
            }
            IF_META_AVAILABLE(uint32_t, afState, CAM_INTF_META_AF_STATE, metaResult) {
                LOGD("Result AF state: %d", *afState);
            }
        }

        mMutex.unlock();

        // Generate FOV-control result only if the result meta is valid
        if (metaResult) {
            generateFovControlResult();
        }
    }
    return metaResult;
}


/*===========================================================================
 * FUNCTION   : generateFovControlResult
 *
 * DESCRIPTION: Generate FOV control result
 *
 * PARAMETERS : None
 *
 * RETURN     : None
 *
 *==========================================================================*/
void QCameraFOVControl::generateFovControlResult()
{
    Mutex::Autolock lock(mMutex);

    float zoom = findZoomRatio(mFovControlData.zoomWide) / (float)mFovControlData.zoomRatioTable[0];
    uint32_t zoomWide     = mFovControlData.zoomWide;
    uint32_t zoomWidePrev = mFovControlData.zoomWidePrev;

    if (mFovControlData.configCompleted == false) {
        // Return as invalid result if the FOV-control configuration is not yet complete
        mFovControlResult.isValid = false;
        return;
    }

    // Update previous zoom value
    mFovControlData.zoomWidePrev = mFovControlData.zoomWide;

    uint32_t  currentBrightness = 0;
    uint32_t  currentFocusDist  = 0;

    if (mFovControlResult.camMasterPreview == CAM_TYPE_MAIN) {
        currentBrightness = mFovControlData.status3A.main.ae.luxIndex;
        currentFocusDist  = mFovControlData.status3A.main.af.focusDistCm;
    } else if (mFovControlResult.camMasterPreview == CAM_TYPE_AUX) {
        currentBrightness = mFovControlData.status3A.aux.ae.luxIndex;
        currentFocusDist  = mFovControlData.status3A.aux.af.focusDistCm;
    }

    float transitionLow     = mFovControlData.transitionParams.transitionLow;
    float transitionHigh    = mFovControlData.transitionParams.transitionHigh;
    float cutOverWideToTele = mFovControlData.transitionParams.cutOverWideToTele;
    float cutOverTeleToWide = mFovControlData.transitionParams.cutOverTeleToWide;

    cam_sync_type_t camWide = mFovControlData.camWide;
    cam_sync_type_t camTele = mFovControlData.camTele;

    uint16_t thresholdBrightness = mFovControlConfig.auxSwitchBrightnessMin;
    uint16_t thresholdFocusDist  = mFovControlConfig.auxSwitchFocusDistCmMin;

    if (zoomWide == zoomWidePrev) {
        mFovControlData.zoomDirection = ZOOM_STABLE;
        ++mFovControlData.zoomStableCount;
    } else if (zoomWide > zoomWidePrev) {
        mFovControlData.zoomDirection   = ZOOM_IN;
        mFovControlData.zoomStableCount = 0;
    } else {
        mFovControlData.zoomDirection   = ZOOM_OUT;
        mFovControlData.zoomStableCount = 0;
    }

    // Update snapshot post-process flags
    if (mFovControlConfig.snapshotPPConfig.enablePostProcess &&
        (zoom >= mFovControlConfig.snapshotPPConfig.zoomMin) &&
        (zoom <= mFovControlConfig.snapshotPPConfig.zoomMax)) {
        mFovControlResult.snapshotPostProcessZoomRange = true;
    } else {
        mFovControlResult.snapshotPostProcessZoomRange = false;
    }

    if (mFovControlResult.snapshotPostProcessZoomRange &&
        (currentBrightness >= mFovControlConfig.snapshotPPConfig.luxMin) &&
        (currentFocusDist  >= mFovControlConfig.snapshotPPConfig.focusDistanceMin)) {
        mFovControlResult.snapshotPostProcess = true;
    } else {
        mFovControlResult.snapshotPostProcess = false;
    }

    switch (mFovControlData.camState) {
        case STATE_WIDE:
            // If the scene continues to be bright, update stable count; reset otherwise
            if (currentBrightness >= thresholdBrightness) {
                ++mFovControlData.brightnessStableCount;
            } else {
                mFovControlData.brightnessStableCount = 0;
            }

            // If the scene continues to be non-macro, update stable count; reset otherwise
            if (currentFocusDist >= thresholdFocusDist) {
                ++mFovControlData.focusDistStableCount;
            } else {
                mFovControlData.focusDistStableCount = 0;
            }

            // Reset fallback to main flag if zoom is less than cutover point
            if (zoom <= cutOverTeleToWide) {
                mFovControlData.fallbackToWide = false;
            }

            // Check if the scene is good for aux (bright and far focused)
            if ((currentBrightness >= thresholdBrightness) &&
                (currentFocusDist >= thresholdFocusDist)) {
                // Lower constraint if zooming in or if snapshot postprocessing is true
                if (mFovControlResult.snapshotPostProcess ||
                    (((zoom >= transitionLow) ||
                     (sacRequestedDualZone())) &&
                    (mFovControlData.zoomDirection == ZOOM_IN) &&
                    (mFovControlData.fallbackToWide == false))) {
                    mFovControlData.camState = STATE_TRANSITION;
                    mFovControlResult.activeCameras = (camWide | camTele);
                }
                // Higher constraint if not zooming in
                else if ((zoom > cutOverWideToTele) &&
                    (mFovControlData.brightnessStableCount >=
                            mFovControlConfig.brightnessStableCountThreshold) &&
                    (mFovControlData.focusDistStableCount  >=
                            mFovControlConfig.focusDistStableCountThreshold)) {
                    // Enter the transition state
                    mFovControlData.camState = STATE_TRANSITION;
                    mFovControlResult.activeCameras = (camWide | camTele);

                    // Reset fallback to wide flag
                    mFovControlData.fallbackToWide = false;

                    // Reset zoom stable count
                    mFovControlData.zoomStableCount = 0;
                }
            }
            break;

        case STATE_TRANSITION:
            // Reset brightness stable count
            mFovControlData.brightnessStableCount = 0;
            // Reset focus distance stable count
            mFovControlData.focusDistStableCount  = 0;

            // Set the master info
            // Switch to wide
            if ((mFovControlResult.camMasterPreview == camTele) &&
                canSwitchMasterTo(CAM_TYPE_WIDE)) {
                mFovControlResult.camMasterPreview = camWide;
                mFovControlResult.camMaster3A      = camWide;
            }
            // switch to tele
            else if ((mFovControlResult.camMasterPreview == camWide) &&
                    canSwitchMasterTo(CAM_TYPE_TELE)) {
                mFovControlResult.camMasterPreview = camTele;
                mFovControlResult.camMaster3A      = camTele;
            }

            // Change the transition state if necessary.
            // If fallback to wide is initiated, try to move to wide state
            if (mFovControlData.fallbackEnabled && mFovControlData.fallbackToWide) {
                if (mFovControlResult.camMasterPreview == camWide) {
                    mFovControlData.camState        = STATE_WIDE;
                    mFovControlResult.activeCameras = camWide;
                }
            }
            // If snapshot post processing is required, do not change the state.
            else if (mFovControlResult.snapshotPostProcess == false) {
                if ((zoom < transitionLow) &&
                        !sacRequestedDualZone() &&
                        (mFovControlResult.camMasterPreview == camWide)) {
                    mFovControlData.camState        = STATE_WIDE;
                    mFovControlResult.activeCameras = camWide;
                } else if ((zoom > transitionHigh) &&
                        !sacRequestedDualZone() &&
                        (mFovControlResult.camMasterPreview == camTele)) {
                    mFovControlData.camState        = STATE_TELE;
                    mFovControlResult.activeCameras = camTele;
                } else if (mFovControlData.zoomStableCount >=
                        mFovControlConfig.zoomStableCountThreshold) {
                    // If the zoom is stable put the non-master camera to LPM for power optimization
                    if (mFovControlResult.camMasterPreview == camWide) {
                        mFovControlData.camState        = STATE_WIDE;
                        mFovControlResult.activeCameras = camWide;
                    } else {
                        mFovControlData.camState        = STATE_TELE;
                        mFovControlResult.activeCameras = camTele;
                    }
                }
            }
            break;

        case STATE_TELE:
            // If the scene continues to be dark, update stable count; reset otherwise
            if (currentBrightness < thresholdBrightness) {
                ++mFovControlData.brightnessStableCount;
            } else {
                mFovControlData.brightnessStableCount = 0;
            }

            // If the scene continues to be macro, update stable count; reset otherwise
            if (currentFocusDist < thresholdFocusDist) {
                ++mFovControlData.focusDistStableCount;
            } else {
                mFovControlData.focusDistStableCount = 0;
            }

            // Lower constraint if zooming out or if the snapshot postprocessing is true
            if (mFovControlResult.snapshotPostProcess ||
                    (((zoom <= transitionHigh) || sacRequestedDualZone()) &&
                    (mFovControlData.zoomDirection == ZOOM_OUT))) {
                mFovControlData.camState = STATE_TRANSITION;
                mFovControlResult.activeCameras = (camWide | camTele);
            }
            // Higher constraint if not zooming out. Only if fallback is enabled
            else if (((currentBrightness < thresholdBrightness) ||
                    (currentFocusDist < thresholdFocusDist)) &&
                    mFovControlData.fallbackEnabled) {
                // Enter transition state if brightness or focus distance is below threshold
                if ((mFovControlData.brightnessStableCount >=
                        mFovControlConfig.brightnessStableCountThreshold) ||
                    (mFovControlData.focusDistStableCount  >=
                        mFovControlConfig.focusDistStableCountThreshold)) {
                    mFovControlData.camState = STATE_TRANSITION;
                    mFovControlResult.activeCameras = (camWide | camTele);

                    // Reset zoom stable count and set fallback flag to true
                    mFovControlData.zoomStableCount = 0;
                    mFovControlData.fallbackToWide  = true;
                    LOGD("Low light/Macro scene - fall back to Wide from Tele");
                }
            }
            break;
    }

    // Update snapshot postprocess result based on fall back to wide decision
    if (mFovControlData.fallbackEnabled && mFovControlData.fallbackToWide) {
        mFovControlResult.snapshotPostProcess = false;
    }

    mFovControlResult.isValid = true;
    // Debug print for the FOV-control result
    LOGD("Effective zoom: %f", zoom);
    LOGD("zoom direction: %d", (uint32_t)mFovControlData.zoomDirection);
    LOGD("zoomWide: %d, zoomTele: %d", zoomWide, mFovControlData.zoomTele);
    LOGD("Snapshot postprocess: %d", mFovControlResult.snapshotPostProcess);
    LOGD("Master camera            : %s", (mFovControlResult.camMasterPreview == CAM_TYPE_MAIN) ?
            "CAM_TYPE_MAIN" : "CAM_TYPE_AUX");
    LOGD("Master camera for preview: %s",
            (mFovControlResult.camMasterPreview == camWide ) ? "Wide" : "Tele");
    LOGD("Master camera for 3A     : %s",
            (mFovControlResult.camMaster3A == camWide ) ? "Wide" : "Tele");
    LOGD("Wide camera status : %s",
            (mFovControlResult.activeCameras & camWide) ? "Active" : "LPM");
    LOGD("Tele camera status : %s",
            (mFovControlResult.activeCameras & camTele) ? "Active" : "LPM");
    LOGD("transition state: %s", ((mFovControlData.camState == STATE_WIDE) ? "STATE_WIDE" :
            ((mFovControlData.camState == STATE_TELE) ? "STATE_TELE" : "STATE_TRANSITION" )));
}


/*===========================================================================
 * FUNCTION   : getFovControlResult
 *
 * DESCRIPTION: Return FOV-control result
 *
 * PARAMETERS : None
 *
 * RETURN     : FOV-control result
 *
 *==========================================================================*/
 fov_control_result_t QCameraFOVControl::getFovControlResult()
{
    Mutex::Autolock lock(mMutex);
    fov_control_result_t fovControlResult = mFovControlResult;
    return fovControlResult;
}


/*===========================================================================
 * FUNCTION    : isMainCamFovWider
 *
 * DESCRIPTION : Check if the main camera FOV is wider than aux
 *
 * PARAMETERS  : None
 *
 * RETURN      :
 * true        : If main cam FOV is wider than tele
 * false       : If main cam FOV is narrower than tele
 *
 *==========================================================================*/
inline bool QCameraFOVControl::isMainCamFovWider()
{
    if (mDualCamParams.paramsMain.focalLengthMm <
            mDualCamParams.paramsAux.focalLengthMm) {
        return true;
    } else {
        return false;
    }
}


/*===========================================================================
 * FUNCTION    : sacRequestedDualZone
 *
 * DESCRIPTION : Check if Spatial alignment block requested both the cameras to be active.
 *               The request is valid only when LPM is enabled.
 *
 * PARAMETERS  : None
 *
 * RETURN      :
 * true        : If dual zone is requested with LPM enabled
 * false       : If LPM is disabled or if dual zone is not requested with LPM enabled
 *
 *==========================================================================*/
inline bool QCameraFOVControl::sacRequestedDualZone()
{
    bool ret = false;
    cam_sync_type_t camWide = mFovControlData.camWide;
    cam_sync_type_t camTele = mFovControlData.camTele;

    // Return true if Spatial alignment block requested both the cameras to be active
    // in the case of lpm enabled
    if ((mFovControlData.spatialAlignResult.activeCameras == (camWide | camTele)) &&
            (mFovControlData.lpmEnabled)) {
        ret = true;
    }
    return ret;
}


/*===========================================================================
 * FUNCTION    : canSwitchMasterTo
 *
 * DESCRIPTION : Check if the master can be switched to the camera- cam.
 *
 * PARAMETERS  :
 * @cam        : cam type
 *
 * RETURN      :
 * true        : If master can be switched
 * false       : If master cannot be switched
 *
 *==========================================================================*/
bool QCameraFOVControl::canSwitchMasterTo(
        cam_type cam)
{
    bool ret = false;
    float zoom = findZoomRatio(mFovControlData.zoomWide) / (float)mFovControlData.zoomRatioTable[0];
    float cutOverWideToTele = mFovControlData.transitionParams.cutOverWideToTele;
    float cutOverTeleToWide = mFovControlData.transitionParams.cutOverTeleToWide;
    af_status afStatusAux   = mFovControlData.status3A.aux.af.status;

    char prop[PROPERTY_VALUE_MAX];
    int override = 0;
    property_get("persist.camera.fovc.override", prop, "0");
    override = atoi(prop);
    if(override) {
        afStatusAux = AF_VALID;
    }

    if (cam == CAM_TYPE_WIDE) {
        if (mFovControlData.availableSpatialAlignSolns & CAM_SPATIAL_ALIGN_OEM) {
            // In case of OEM Spatial alignment solution, check the spatial alignment ready
            if (mFovControlData.wideCamStreaming && isSpatialAlignmentReady()) {
                ret = true;
            }
        } else {
            // In case of QTI Spatial alignment solution and no spatial alignment solution,
            // check the fallback flag or if the zoom level has crossed the threhold.
            if ((mFovControlData.fallbackEnabled && mFovControlData.fallbackToWide) ||
                    (zoom < cutOverTeleToWide)) {
                 if (mFovControlData.wideCamStreaming) {
                    ret = true;
                 }
            }
        }
    } else if (cam == CAM_TYPE_TELE) {
        if (mFovControlData.fallbackEnabled && mFovControlData.fallbackToWide) {
            // If fallback to wide is initiated, don't switch the master to tele
            ret = false;
        } else if (mFovControlData.availableSpatialAlignSolns & CAM_SPATIAL_ALIGN_OEM) {
            // In case of OEM Spatial alignment solution, check the spatial alignment ready and
            // af status
            if (mFovControlData.teleCamStreaming &&
                    isSpatialAlignmentReady() &&
                    (afStatusAux == AF_VALID)) {
                ret = true;
            }
        } else if (mFovControlData.availableSpatialAlignSolns & CAM_SPATIAL_ALIGN_QTI) {
            // In case of QTI Spatial alignment solution check the spatial alignment ready flag,
            // af status and if the zoom level has crossed the threhold.
            if ((zoom > cutOverWideToTele) &&
                    isSpatialAlignmentReady() &&
                    (afStatusAux == AF_VALID)) {
                ret = true;
            }
        } else {
            // In case of no spatial alignment solution check af status and
            // if the zoom level has crossed the threhold.
            if ((zoom > cutOverWideToTele) &&
                    (afStatusAux == AF_VALID)) {
                ret = true;
            }
        }
    } else {
        LOGE("Request to switch to invalid cam type");
    }
    return ret;
}

/*===========================================================================
 * FUNCTION    : isSpatialAlignmentReady
 *
 * DESCRIPTION : Check if the spatial alignment is ready.
 *               For QTI solution, check ready_status flag
 *               For OEM solution, check camMasterHint
 *               If the spatial alignment solution is not needed, return true
 *
 * PARAMETERS  : None
 *
 * RETURN      :
 * true        : If spatial alignment is ready
 * false       : If spatial alignment is not yet ready
 *
 *==========================================================================*/
bool QCameraFOVControl::isSpatialAlignmentReady()
{
    bool ret = true;
    cam_sync_type_t camWide = mFovControlData.camWide;
    cam_sync_type_t camTele = mFovControlData.camTele;

    if (mFovControlData.availableSpatialAlignSolns & CAM_SPATIAL_ALIGN_OEM) {
        uint8_t currentMaster = (uint8_t)mFovControlResult.camMasterPreview;
        uint8_t camMasterHint = mFovControlData.spatialAlignResult.camMasterHint;

        if (((currentMaster == camWide) && (camMasterHint == camTele)) ||
                ((currentMaster == camTele) && (camMasterHint == camWide))){
            ret = true;
        } else {
            ret = false;
        }
    } else if (mFovControlData.availableSpatialAlignSolns & CAM_SPATIAL_ALIGN_QTI) {
        if (mFovControlData.spatialAlignResult.readyStatus) {
            ret = true;
        } else {
            ret = false;
        }
    }

    char prop[PROPERTY_VALUE_MAX];
    int override = 0;
    property_get("persist.camera.fovc.override", prop, "0");
    override = atoi(prop);
    if(override) {
        ret = true;
    }

    return ret;
}


/*===========================================================================
 * FUNCTION    : validateAndExtractParameters
 *
 * DESCRIPTION : Validates a subset of parameters from capabilities and
 *               saves those parameters for decision making.
 *
 * PARAMETERS  :
 *  @capsMain  : The capabilities for the main camera
 *  @capsAux   : The capabilities for the aux camera
 *
 * RETURN      :
 * true        : Success
 * false       : Failure
 *
 *==========================================================================*/
bool QCameraFOVControl::validateAndExtractParameters(
        cam_capability_t  *capsMainCam,
        cam_capability_t  *capsAuxCam)
{
    bool rc = false;
    if (capsMainCam && capsAuxCam) {

        mFovControlConfig.percentMarginHysterisis  = 5;
        mFovControlConfig.percentMarginMain        = 25;
        mFovControlConfig.percentMarginAux         = 15;
        mFovControlConfig.waitTimeForHandoffMs     = 1000;

        mDualCamParams.paramsMain.sensorStreamWidth =
                capsMainCam->related_cam_calibration.main_cam_specific_calibration.\
                native_sensor_resolution_width;
        mDualCamParams.paramsMain.sensorStreamHeight =
                capsMainCam->related_cam_calibration.main_cam_specific_calibration.\
                native_sensor_resolution_height;

        mDualCamParams.paramsAux.sensorStreamWidth   =
                capsMainCam->related_cam_calibration.aux_cam_specific_calibration.\
                native_sensor_resolution_width;
        mDualCamParams.paramsAux.sensorStreamHeight  =
                capsMainCam->related_cam_calibration.aux_cam_specific_calibration.\
                native_sensor_resolution_height;

        mDualCamParams.paramsMain.focalLengthMm = capsMainCam->focal_length;
        mDualCamParams.paramsAux.focalLengthMm  = capsAuxCam->focal_length;

        mDualCamParams.paramsMain.pixelPitchUm = capsMainCam->pixel_pitch_um;
        mDualCamParams.paramsAux.pixelPitchUm  = capsAuxCam->pixel_pitch_um;

        if ((capsMainCam->min_focus_distance > 0) &&
                (capsAuxCam->min_focus_distance > 0)) {
            // Convert the min focus distance from diopters to cm
            // and choose the max of both sensors.
            uint32_t minFocusDistCmMain = (100.0f / capsMainCam->min_focus_distance);
            uint32_t minFocusDistCmAux  = (100.0f / capsAuxCam->min_focus_distance);
            mDualCamParams.minFocusDistanceCm = (minFocusDistCmMain > minFocusDistCmAux) ?
                    minFocusDistCmMain : minFocusDistCmAux;
        }

        if (capsMainCam->related_cam_calibration.relative_position_flag == 0) {
            mDualCamParams.positionAux = CAM_POSITION_RIGHT;
        } else {
            mDualCamParams.positionAux = CAM_POSITION_LEFT;
        }

        if ((capsMainCam->avail_spatial_align_solns & CAM_SPATIAL_ALIGN_QTI) ||
                (capsMainCam->avail_spatial_align_solns & CAM_SPATIAL_ALIGN_OEM)) {
            mFovControlData.availableSpatialAlignSolns =
                    capsMainCam->avail_spatial_align_solns;
        } else {
            LOGW("Spatial alignment not supported");
        }

        if (capsMainCam->zoom_supported > 0) {
            mFovControlData.zoomRatioTable      = capsMainCam->zoom_ratio_tbl;
            mFovControlData.zoomRatioTableCount = capsMainCam->zoom_ratio_tbl_cnt;
        } else {
            LOGE("zoom feature not supported");
            return false;
        }
        rc = true;
    }

    return rc;
}

/*===========================================================================
 * FUNCTION   : calculateBasicFovRatio
 *
 * DESCRIPTION: Calculate the FOV ratio between main and aux cameras
 *
 * PARAMETERS : None
 *
 * RETURN     :
 * true       : Success
 * false      : Failure
 *
 *==========================================================================*/
bool QCameraFOVControl::calculateBasicFovRatio()
{
    float fovWide = 0.0f;
    float fovTele = 0.0f;
    bool rc = false;

    if ((mDualCamParams.paramsMain.focalLengthMm > 0.0f) &&
         (mDualCamParams.paramsAux.focalLengthMm > 0.0f)) {
        if (mDualCamParams.paramsMain.focalLengthMm <
            mDualCamParams.paramsAux.focalLengthMm) {
            fovWide = (mDualCamParams.paramsMain.sensorStreamWidth *
                        mDualCamParams.paramsMain.pixelPitchUm) /
                        mDualCamParams.paramsMain.focalLengthMm;

            fovTele = (mDualCamParams.paramsAux.sensorStreamWidth *
                        mDualCamParams.paramsAux.pixelPitchUm) /
                        mDualCamParams.paramsAux.focalLengthMm;
        } else {
            fovWide = (mDualCamParams.paramsAux.sensorStreamWidth *
                        mDualCamParams.paramsAux.pixelPitchUm) /
                        mDualCamParams.paramsAux.focalLengthMm;

            fovTele = (mDualCamParams.paramsMain.sensorStreamWidth *
                        mDualCamParams.paramsMain.pixelPitchUm) /
                        mDualCamParams.paramsMain.focalLengthMm;
        }
        if (fovTele > 0.0f) {
            mFovControlData.basicFovRatio = (fovWide / fovTele);
            rc = true;
        }
    }

    LOGD("Main cam focalLengthMm : %f", mDualCamParams.paramsMain.focalLengthMm);
    LOGD("Aux  cam focalLengthMm : %f", mDualCamParams.paramsAux.focalLengthMm);
    LOGD("Main cam sensorStreamWidth : %u", mDualCamParams.paramsMain.sensorStreamWidth);
    LOGD("Main cam sensorStreamHeight: %u", mDualCamParams.paramsMain.sensorStreamHeight);
    LOGD("Main cam pixelPitchUm      : %f", mDualCamParams.paramsMain.pixelPitchUm);
    LOGD("Main cam focalLengthMm     : %f", mDualCamParams.paramsMain.focalLengthMm);
    LOGD("Aux cam sensorStreamWidth  : %u", mDualCamParams.paramsAux.sensorStreamWidth);
    LOGD("Aux cam sensorStreamHeight : %u", mDualCamParams.paramsAux.sensorStreamHeight);
    LOGD("Aux cam pixelPitchUm       : %f", mDualCamParams.paramsAux.pixelPitchUm);
    LOGD("Aux cam focalLengthMm      : %f", mDualCamParams.paramsAux.focalLengthMm);
    LOGD("fov wide : %f", fovWide);
    LOGD("fov tele : %f", fovTele);
    LOGD("BasicFovRatio : %f", mFovControlData.basicFovRatio);

    return rc;
}


/*===========================================================================
 * FUNCTION   : combineFovAdjustment
 *
 * DESCRIPTION: Calculate the final FOV adjustment by combining basic FOV ratio
 *              with the margin info
 *
 * PARAMETERS : None
 *
 * RETURN     :
 * true       : Success
 * false      : Failure
 *
 *==========================================================================*/
bool QCameraFOVControl::combineFovAdjustment()
{
    float ratioMarginWidth;
    float ratioMarginHeight;
    float adjustedRatio;
    bool rc = false;

    ratioMarginWidth = (1.0 + (mFovControlData.camMainWidthMargin)) /
            (1.0 + (mFovControlData.camAuxWidthMargin));
    ratioMarginHeight = (1.0 + (mFovControlData.camMainHeightMargin)) /
            (1.0 + (mFovControlData.camAuxHeightMargin));

    adjustedRatio = (ratioMarginHeight < ratioMarginWidth) ? ratioMarginHeight : ratioMarginWidth;

    if (adjustedRatio > 0.0f) {
        mFovControlData.transitionParams.cutOverFactor =
                (mFovControlData.basicFovRatio / adjustedRatio);
        rc = true;
    }

    LOGD("Main cam margin for width  : %f", mFovControlData.camMainWidthMargin);
    LOGD("Main cam margin for height : %f", mFovControlData.camMainHeightMargin);
    LOGD("Aux  cam margin for width  : %f", mFovControlData.camAuxWidthMargin);
    LOGD("Aux  cam margin for height : %f", mFovControlData.camAuxHeightMargin);
    LOGD("Width  margin ratio : %f", ratioMarginWidth);
    LOGD("Height margin ratio : %f", ratioMarginHeight);

    return rc;
}


/*===========================================================================
 * FUNCTION   : calculateDualCamTransitionParams
 *
 * DESCRIPTION: Calculate the transition parameters needed to switch the camera
 *              between main and aux
 *
 * PARAMETERS :
 * @fovAdjustBasic       : basic FOV ratio
 * @zoomTranslationFactor: translation factor for main, aux zoom
 *
 * RETURN     : none
 *
 *==========================================================================*/
void QCameraFOVControl::calculateDualCamTransitionParams()
{
    float percentMarginWide;
    float percentMarginTele;

    if (isMainCamFovWider()) {
        percentMarginWide = mFovControlConfig.percentMarginMain;
        percentMarginTele = mFovControlConfig.percentMarginAux;
    } else {
        percentMarginWide = mFovControlConfig.percentMarginAux;
        percentMarginTele = mFovControlConfig.percentMarginMain;
    }

    mFovControlData.transitionParams.cropRatio = mFovControlData.basicFovRatio;

    mFovControlData.transitionParams.cutOverWideToTele =
            mFovControlData.transitionParams.cutOverFactor +
            (mFovControlConfig.percentMarginHysterisis / 100.0) * mFovControlData.basicFovRatio;

    mFovControlData.transitionParams.cutOverTeleToWide =
            mFovControlData.transitionParams.cutOverFactor;

    mFovControlData.transitionParams.transitionHigh =
            mFovControlData.transitionParams.cutOverWideToTele +
            (percentMarginWide / 100.0) * mFovControlData.basicFovRatio;

    mFovControlData.transitionParams.transitionLow =
            mFovControlData.transitionParams.cutOverTeleToWide -
            (percentMarginTele / 100.0) * mFovControlData.basicFovRatio;

    if (mFovControlConfig.snapshotPPConfig.enablePostProcess) {
        // Expand the transition zone if necessary to account for
        // the snapshot post-process settings
        if (mFovControlConfig.snapshotPPConfig.zoomMax >
                mFovControlData.transitionParams.transitionHigh) {
            mFovControlData.transitionParams.transitionHigh =
                mFovControlConfig.snapshotPPConfig.zoomMax;
        }
        if (mFovControlConfig.snapshotPPConfig.zoomMin <
                mFovControlData.transitionParams.transitionLow) {
            mFovControlData.transitionParams.transitionLow =
                mFovControlConfig.snapshotPPConfig.zoomMin;
        }

        // Set aux switch brightness threshold as the lower of aux switch and
        // snapshot post-process thresholds
        if (mFovControlConfig.snapshotPPConfig.luxMin < mFovControlConfig.auxSwitchBrightnessMin) {
            mFovControlConfig.auxSwitchBrightnessMin = mFovControlConfig.snapshotPPConfig.luxMin;
        }
    }

    LOGD("transition param: TransitionLow  %f", mFovControlData.transitionParams.transitionLow);
    LOGD("transition param: TeleToWide     %f", mFovControlData.transitionParams.cutOverTeleToWide);
    LOGD("transition param: WideToTele     %f", mFovControlData.transitionParams.cutOverWideToTele);
    LOGD("transition param: TransitionHigh %f", mFovControlData.transitionParams.transitionHigh);
}


/*===========================================================================
 * FUNCTION   : findZoomValue
 *
 * DESCRIPTION: For the input zoom ratio, find the zoom value.
 *              Zoom table contains zoom ratios where the indices
 *              in the zoom table indicate the corresponding zoom values.
 * PARAMETERS :
 * @zoomRatio : Zoom ratio
 *
 * RETURN     : Zoom value
 *
 *==========================================================================*/
uint32_t QCameraFOVControl::findZoomValue(
        uint32_t zoomRatio)
{
    uint32_t zoom = 0;
    for (uint32_t i = 0; i < mFovControlData.zoomRatioTableCount; ++i) {
        if (zoomRatio <= mFovControlData.zoomRatioTable[i]) {
            zoom = i;
            break;
        }
    }
    return zoom;
}


/*===========================================================================
 * FUNCTION   : findZoomRatio
 *
 * DESCRIPTION: For the input zoom value, find the zoom ratio.
 *              Zoom table contains zoom ratios where the indices
 *              in the zoom table indicate the corresponding zoom values.
 * PARAMETERS :
 * @zoom      : zoom value
 *
 * RETURN     : zoom ratio
 *
 *==========================================================================*/
uint32_t QCameraFOVControl::findZoomRatio(
        uint32_t zoom)
{
    return mFovControlData.zoomRatioTable[zoom];
}


/*===========================================================================
 * FUNCTION   : readjustZoomForTele
 *
 * DESCRIPTION: Calculate the zoom value for the tele camera based on zoom value
 *              for the wide camera
 *
 * PARAMETERS :
 * @zoomWide  : Zoom value for wide camera
 *
 * RETURN     : Zoom value for tele camera
 *
 *==========================================================================*/
uint32_t QCameraFOVControl::readjustZoomForTele(
        uint32_t zoomWide)
{
    uint32_t zoomRatioWide;
    uint32_t zoomRatioTele;

    zoomRatioWide = findZoomRatio(zoomWide);
    zoomRatioTele  = zoomRatioWide / mFovControlData.transitionParams.cutOverFactor;

    return(findZoomValue(zoomRatioTele));
}


/*===========================================================================
 * FUNCTION   : readjustZoomForWide
 *
 * DESCRIPTION: Calculate the zoom value for the wide camera based on zoom value
 *              for the tele camera
 *
 * PARAMETERS :
 * @zoomTele  : Zoom value for tele camera
 *
 * RETURN     : Zoom value for wide camera
 *
 *==========================================================================*/
uint32_t QCameraFOVControl::readjustZoomForWide(
        uint32_t zoomTele)
{
    uint32_t zoomRatioWide;
    uint32_t zoomRatioTele;

    zoomRatioTele = findZoomRatio(zoomTele);
    zoomRatioWide = zoomRatioTele * mFovControlData.transitionParams.cutOverFactor;

    return(findZoomValue(zoomRatioWide));
}


/*===========================================================================
 * FUNCTION   : convertUserZoomToWideAndTele
 *
 * DESCRIPTION: Calculate the zoom value for the wide and tele cameras
 *              based on the input user zoom value
 *
 * PARAMETERS :
 * @zoom      : User zoom value
 *
 * RETURN     : none
 *
 *==========================================================================*/
void QCameraFOVControl::convertUserZoomToWideAndTele(
        uint32_t zoom)
{
    Mutex::Autolock lock(mMutex);

    // If the zoom translation library is present and initialized,
    // use it to get wide and tele zoom values
    if (mZoomTranslator && mZoomTranslator->isInitialized()) {
        uint32_t zoomWide = 0;
        uint32_t zoomTele = 0;
        if (mZoomTranslator->getZoomValues(zoom, &zoomWide, &zoomTele) != NO_ERROR) {
            LOGE("getZoomValues failed from zoom translation lib");
            // Use zoom translation logic from FOV-control
            mFovControlData.zoomWide = zoom;
            mFovControlData.zoomTele = readjustZoomForTele(mFovControlData.zoomWide);
        } else {
            // Use the zoom values provided by zoom translation lib
            mFovControlData.zoomWide = zoomWide;
            mFovControlData.zoomTele = zoomTele;
        }
    } else {
        mFovControlData.zoomWide = zoom;
        mFovControlData.zoomTele = readjustZoomForTele(mFovControlData.zoomWide);
    }
}


/*===========================================================================
 * FUNCTION   : translateFocusAreas
 *
 * DESCRIPTION: Translate the focus areas from main to aux camera.
 *
 * PARAMETERS :
 * @roiAfMain : Focus area ROI for main camera
 * @cam       : Cam type
 *
 * RETURN     : Translated focus area ROI for aux camera
 *
 *==========================================================================*/
cam_roi_info_t QCameraFOVControl::translateFocusAreas(
        cam_roi_info_t  roiAfMain,
        cam_sync_type_t cam)
{
    float fovRatio;
    float zoomWide;
    float zoomTele;
    float AuxDiffRoiLeft;
    float AuxDiffRoiTop;
    float AuxRoiLeft;
    float AuxRoiTop;
    cam_roi_info_t roiAfTrans = roiAfMain;
    int32_t shiftHorzAdjusted;
    int32_t shiftVertAdjusted;
    float zoom = findZoomRatio(mFovControlData.zoomWide) / (float)mFovControlData.zoomRatioTable[0];

    zoomWide = findZoomRatio(mFovControlData.zoomWide);
    zoomTele = findZoomRatio(mFovControlData.zoomTele);

    if (cam == mFovControlData.camWide) {
        fovRatio = 1.0f;
    } else {
        fovRatio = (zoomTele / zoomWide) * mFovControlData.transitionParams.cropRatio;
    }

    // Acquire the mutex in order to read the spatial alignment result which is written
    // by another thread
    mMutex.lock();
    if (cam == mFovControlData.camWide) {
        shiftHorzAdjusted = mFovControlData.spatialAlignResult.shiftAfRoiWide.shiftHorz;
        shiftVertAdjusted = mFovControlData.spatialAlignResult.shiftAfRoiWide.shiftVert;
    } else {
        shiftHorzAdjusted = (mFovControlData.transitionParams.cropRatio / zoom) *
                mFovControlData.spatialAlignResult.shiftAfRoiTele.shiftHorz;
        shiftVertAdjusted = (mFovControlData.transitionParams.cropRatio / zoom) *
                mFovControlData.spatialAlignResult.shiftAfRoiTele.shiftVert;
    }
    mMutex.unlock();

    for (int i = 0; i < roiAfMain.num_roi; ++i) {
        roiAfTrans.roi[i].width  = roiAfMain.roi[i].width * fovRatio;
        roiAfTrans.roi[i].height = roiAfMain.roi[i].height * fovRatio;

        AuxDiffRoiLeft = (roiAfTrans.roi[i].width - roiAfMain.roi[i].width) / 2.0f;
        AuxRoiLeft = roiAfMain.roi[i].left - AuxDiffRoiLeft;
        AuxDiffRoiTop = (roiAfTrans.roi[i].height - roiAfMain.roi[i].height) / 2.0f;
        AuxRoiTop = roiAfMain.roi[i].top - AuxDiffRoiTop;

        roiAfTrans.roi[i].left = AuxRoiLeft - shiftHorzAdjusted;
        roiAfTrans.roi[i].top  = AuxRoiTop - shiftVertAdjusted;

        // Check the ROI bounds and correct if necessory
        // If ROI is out of bounds, revert to default ROI
        if ((roiAfTrans.roi[i].left >= mFovControlData.previewSize.width) ||
            (roiAfTrans.roi[i].top >= mFovControlData.previewSize.height) ||
            (roiAfTrans.roi[i].width >= mFovControlData.previewSize.width) ||
            (roiAfTrans.roi[i].height >= mFovControlData.previewSize.height)) {
            // TODO : use default ROI when available from AF. This part of the code
            // is still being worked upon. WA - set it to main cam ROI
            roiAfTrans = roiAfMain;
            LOGW("AF ROI translation failed, reverting to the default ROI");
        } else {
            if (roiAfTrans.roi[i].left < 0) {
                roiAfTrans.roi[i].left = 0;
                LOGW("AF ROI translation failed");
            }
            if (roiAfTrans.roi[i].top < 0) {
                roiAfTrans.roi[i].top = 0;
                LOGW("AF ROI translation failed");
            }
            if ((roiAfTrans.roi[i].left + roiAfTrans.roi[i].width) >=
                        mFovControlData.previewSize.width) {
                roiAfTrans.roi[i].width =
                        mFovControlData.previewSize.width - roiAfTrans.roi[i].left;
                LOGW("AF ROI translation failed");
            }
            if ((roiAfTrans.roi[i].top + roiAfTrans.roi[i].height) >=
                        mFovControlData.previewSize.height) {
                roiAfTrans.roi[i].height =
                        mFovControlData.previewSize.height - roiAfTrans.roi[i].top;
                LOGW("AF ROI translation failed");
            }
        }

        LOGD("Translated AF ROI-%d %s: L:%d, T:%d, W:%d, H:%d", i,
                (cam == CAM_TYPE_MAIN) ? "main cam" : "aux  cam", roiAfTrans.roi[i].left,
                roiAfTrans.roi[i].top, roiAfTrans.roi[i].width, roiAfTrans.roi[i].height);
    }
    return roiAfTrans;
}


/*===========================================================================
 * FUNCTION   : translateMeteringAreas
 *
 * DESCRIPTION: Translate the AEC metering areas from main to aux camera.
 *
 * PARAMETERS :
 * @roiAfMain : AEC ROI for main camera
 * @cam       : Cam type
 *
 * RETURN     : Translated AEC ROI for aux camera
 *
 *==========================================================================*/
cam_set_aec_roi_t QCameraFOVControl::translateMeteringAreas(
        cam_set_aec_roi_t roiAecMain,
        cam_sync_type_t cam)
{
    float fovRatio;
    float zoomWide;
    float zoomTele;
    float AuxDiffRoiX;
    float AuxDiffRoiY;
    float AuxRoiX;
    float AuxRoiY;
    cam_set_aec_roi_t roiAecTrans = roiAecMain;
    int32_t shiftHorzAdjusted;
    int32_t shiftVertAdjusted;
    float zoom = findZoomRatio(mFovControlData.zoomWide) / (float)mFovControlData.zoomRatioTable[0];

    zoomWide = findZoomRatio(mFovControlData.zoomWide);
    zoomTele = findZoomRatio(mFovControlData.zoomTele);

    if (cam == mFovControlData.camWide) {
        fovRatio = 1.0f;
    } else {
        fovRatio = (zoomTele / zoomWide) * mFovControlData.transitionParams.cropRatio;
    }

    // Acquire the mutex in order to read the spatial alignment result which is written
    // by another thread
    mMutex.lock();
    if (cam == mFovControlData.camWide) {
        shiftHorzAdjusted = mFovControlData.spatialAlignResult.shiftAfRoiWide.shiftHorz;
        shiftVertAdjusted = mFovControlData.spatialAlignResult.shiftAfRoiWide.shiftVert;
    } else {
        shiftHorzAdjusted = (mFovControlData.transitionParams.cropRatio / zoom) *
                mFovControlData.spatialAlignResult.shiftAfRoiTele.shiftHorz;
        shiftVertAdjusted = (mFovControlData.transitionParams.cropRatio / zoom) *
                mFovControlData.spatialAlignResult.shiftAfRoiTele.shiftVert;
    }
    mMutex.unlock();

    for (int i = 0; i < roiAecMain.num_roi; ++i) {
        AuxDiffRoiX = fovRatio * ((float)roiAecMain.cam_aec_roi_position.coordinate[i].x -
                          (mFovControlData.previewSize.width / 2));
        AuxRoiX = (mFovControlData.previewSize.width / 2) + AuxDiffRoiX;

        AuxDiffRoiY = fovRatio * ((float)roiAecMain.cam_aec_roi_position.coordinate[i].y -
                          (mFovControlData.previewSize.height / 2));
        AuxRoiY = (mFovControlData.previewSize.height / 2) + AuxDiffRoiY;

        roiAecTrans.cam_aec_roi_position.coordinate[i].x = AuxRoiX - shiftHorzAdjusted;
        roiAecTrans.cam_aec_roi_position.coordinate[i].y = AuxRoiY - shiftVertAdjusted;

        // Check the ROI bounds and correct if necessory
        if ((AuxRoiX < 0) ||
            (AuxRoiY < 0)) {
            roiAecTrans.cam_aec_roi_position.coordinate[i].x = 0;
            roiAecTrans.cam_aec_roi_position.coordinate[i].y = 0;
            LOGW("AEC ROI translation failed");
        } else if ((AuxRoiX >= mFovControlData.previewSize.width) ||
            (AuxRoiY >= mFovControlData.previewSize.height)) {
            // Clamp the Aux AEC ROI co-ordinates to max possible value
            if (AuxRoiX >= mFovControlData.previewSize.width) {
                roiAecTrans.cam_aec_roi_position.coordinate[i].x =
                        mFovControlData.previewSize.width - 1;
            }
            if (AuxRoiY >= mFovControlData.previewSize.height) {
                roiAecTrans.cam_aec_roi_position.coordinate[i].y =
                        mFovControlData.previewSize.height - 1;
            }
            LOGW("AEC ROI translation failed");
        }

        LOGD("Translated AEC ROI-%d %s: x:%d, y:%d", i,
                (cam == CAM_TYPE_MAIN) ? "main cam" : "aux  cam",
                roiAecTrans.cam_aec_roi_position.coordinate[i].x,
                roiAecTrans.cam_aec_roi_position.coordinate[i].y);
    }
    return roiAecTrans;
}


/*===========================================================================
 * FUNCTION   : translateRoiFD
 *
 * DESCRIPTION: Translate face detection ROI from aux metadata to main
 *
 * PARAMETERS :
 * @faceDetectionInfo  : face detection data from aux metadata. This face
 *                       detection data is overwritten with the translated
 *                       FD ROI.
 * @cam                : Cam type
 *
 * RETURN     : none
 *
 *==========================================================================*/
cam_face_detection_data_t QCameraFOVControl::translateRoiFD(
        cam_face_detection_data_t metaFD,
        cam_sync_type_t cam)
{
    cam_face_detection_data_t metaFDTranslated = metaFD;
    int32_t shiftHorz = 0;
    int32_t shiftVert = 0;

    if (cam == mFovControlData.camWide) {
        shiftHorz = mFovControlData.spatialAlignResult.shiftWide.shiftHorz;
        shiftVert = mFovControlData.spatialAlignResult.shiftWide.shiftVert;
    } else {
        shiftHorz = mFovControlData.spatialAlignResult.shiftTele.shiftHorz;
        shiftVert = mFovControlData.spatialAlignResult.shiftTele.shiftVert;
    }

    for (int i = 0; i < metaFDTranslated.num_faces_detected; ++i) {
        metaFDTranslated.faces[i].face_boundary.left += shiftHorz;
        metaFDTranslated.faces[i].face_boundary.top  += shiftVert;
    }

    // If ROI is out of bounds, remove that FD ROI from the list
    for (int i = 0; i < metaFDTranslated.num_faces_detected; ++i) {
        if ((metaFDTranslated.faces[i].face_boundary.left < 0) ||
            (metaFDTranslated.faces[i].face_boundary.left >= mFovControlData.previewSize.width) ||
            (metaFDTranslated.faces[i].face_boundary.top < 0) ||
            (metaFDTranslated.faces[i].face_boundary.top >= mFovControlData.previewSize.height) ||
            ((metaFDTranslated.faces[i].face_boundary.left +
                    metaFDTranslated.faces[i].face_boundary.width) >=
                    mFovControlData.previewSize.width) ||
            ((metaFDTranslated.faces[i].face_boundary.top +
                    metaFDTranslated.faces[i].face_boundary.height) >=
                    mFovControlData.previewSize.height)) {
            // Invalid FD ROI detected
            LOGD("Failed translating FD ROI %s: L:%d, T:%d, W:%d, H:%d",
                    (cam == CAM_TYPE_MAIN) ? "main cam" : "aux  cam",
                    metaFDTranslated.faces[i].face_boundary.left,
                    metaFDTranslated.faces[i].face_boundary.top,
                    metaFDTranslated.faces[i].face_boundary.width,
                    metaFDTranslated.faces[i].face_boundary.height);

            // Remove it by copying the last FD ROI at this index
            if (i < (metaFDTranslated.num_faces_detected - 1)) {
                metaFDTranslated.faces[i] =
                        metaFDTranslated.faces[metaFDTranslated.num_faces_detected - 1];
                // Decrement the current index to process the newly copied FD ROI.
                --i;
            }
            --metaFDTranslated.num_faces_detected;
        }
        else {
            LOGD("Translated FD ROI-%d %s: L:%d, T:%d, W:%d, H:%d", i,
                    (cam == CAM_TYPE_MAIN) ? "main cam" : "aux  cam",
                    metaFDTranslated.faces[i].face_boundary.left,
                    metaFDTranslated.faces[i].face_boundary.top,
                    metaFDTranslated.faces[i].face_boundary.width,
                    metaFDTranslated.faces[i].face_boundary.height);
        }
    }
    return metaFDTranslated;
}


/*===========================================================================
 * FUNCTION      : getFrameMargins
 *
 * DESCRIPTION   : Return frame margin data for the requested camera
 *
 * PARAMETERS    :
 * @masterCamera : Master camera id
 *
 * RETURN        : Frame margins
 *
 *==========================================================================*/
cam_frame_margins_t QCameraFOVControl::getFrameMargins(
        int8_t masterCamera)
{
    cam_frame_margins_t frameMargins;
    memset(&frameMargins, 0, sizeof(cam_frame_margins_t));

    if (masterCamera == CAM_TYPE_MAIN) {
        frameMargins.widthMargins  = mFovControlData.camMainWidthMargin;
        frameMargins.heightMargins = mFovControlData.camMainHeightMargin;
    } else if (masterCamera == CAM_TYPE_AUX) {
        frameMargins.widthMargins  = mFovControlData.camAuxWidthMargin;
        frameMargins.heightMargins = mFovControlData.camAuxHeightMargin;
    }

    return frameMargins;
}
}; // namespace qcamera
