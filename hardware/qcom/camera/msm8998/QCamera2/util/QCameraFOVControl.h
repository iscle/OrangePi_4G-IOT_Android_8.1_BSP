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

#ifndef __QCAMERAFOVCONTROL_H__
#define __QCAMERAFOVCONTROL_H__

#include <utils/Mutex.h>
#include "cam_intf.h"
#include "QCameraExtZoomTranslator.h"

using namespace android;

namespace qcamera {

typedef enum {
    AE_SETTLED,
    AE_CONVERGING
} ae_status;

typedef enum {
    AF_VALID,
    AF_INVALID
} af_status;

typedef enum {
    CAM_POSITION_LEFT,
    CAM_POSITION_RIGHT
} cam_relative_position;

typedef enum {
    STATE_WIDE,
    STATE_TRANSITION,
    STATE_TELE
} dual_cam_state;

typedef enum {
    ZOOM_STABLE,
    ZOOM_IN,
    ZOOM_OUT
} dual_cam_zoom_dir;

typedef enum {
    CAM_TYPE_WIDE,
    CAM_TYPE_TELE
} cam_type;



typedef struct {
    ae_status status;
    float     luxIndex;
} ae_info;

typedef struct {
    af_status status;
    uint32_t  focusDistCm;
} af_info;

typedef struct {
    ae_info ae;
    af_info af;
} status_3A_t;

typedef struct {
    status_3A_t main;
    status_3A_t aux;
} dual_cam_3A_status_t;

typedef struct {
    int32_t shiftHorz;
    int32_t shiftVert;
} spatial_align_shift_t;

typedef struct {
    uint8_t               readyStatus;
    uint8_t               camMasterHint;
    uint8_t               camMasterPreview;
    uint8_t               camMaster3A;
    uint32_t              activeCameras;
    spatial_align_shift_t shiftWide;
    spatial_align_shift_t shiftTele;
    spatial_align_shift_t shiftAfRoiWide;
    spatial_align_shift_t shiftAfRoiTele;
} spatial_align_result_t;

typedef struct {
    float    cropRatio;
    float    cutOverFactor;
    float    cutOverWideToTele;
    float    cutOverTeleToWide;
    float    transitionHigh;
    float    transitionLow;
    uint32_t waitTimeForHandoffMs;
} dual_cam_transition_params_t;

typedef struct {
    bool                         configCompleted;
    uint32_t                     zoomMain;
    uint32_t                     zoomAux;
    uint32_t                     zoomWide;
    uint32_t                     zoomTele;
    uint32_t                     zoomWidePrev;
    uint32_t                     zoomMainPrev;
    uint32_t                    *zoomRatioTable;
    uint32_t                     zoomRatioTableCount;
    uint32_t                     zoomStableCount;
    dual_cam_zoom_dir            zoomDirection;
    zoom_trans_init_data         zoomTransInitData;
    cam_sync_type_t              camWide;
    cam_sync_type_t              camTele;
    dual_cam_state               camState;
    dual_cam_3A_status_t         status3A;
    cam_dimension_t              previewSize;
    cam_dimension_t              ispOutSize;
    spatial_align_result_t       spatialAlignResult;
    uint32_t                     availableSpatialAlignSolns;
    float                        camMainWidthMargin;
    float                        camMainHeightMargin;
    float                        camAuxWidthMargin;
    float                        camAuxHeightMargin;
    bool                         camcorderMode;
    bool                         wideCamStreaming;
    bool                         teleCamStreaming;
    bool                         fallbackEnabled;
    bool                         fallbackToWide;
    float                        basicFovRatio;
    uint32_t                     brightnessStableCount;
    uint32_t                     focusDistStableCount;
    dual_cam_transition_params_t transitionParams;
    uint32_t                     afStatusMain;
    uint32_t                     afStatusAux;
    bool                         lpmEnabled;
} fov_control_data_t;

typedef struct {
    bool     enablePostProcess;
    float    zoomMin;
    float    zoomMax;
    uint16_t luxMin;
    uint16_t focusDistanceMin;
} snapshot_pp_config_t;

typedef struct {
    float    percentMarginHysterisis;
    float    percentMarginAux;
    float    percentMarginMain;
    uint32_t waitTimeForHandoffMs;
    uint16_t auxSwitchBrightnessMin;
    uint16_t auxSwitchFocusDistCmMin;
    uint16_t zoomStableCountThreshold;
    uint16_t focusDistStableCountThreshold;
    uint16_t brightnessStableCountThreshold;
    snapshot_pp_config_t snapshotPPConfig;
} fov_control_config_t;

typedef struct{
    uint32_t sensorStreamWidth;
    uint32_t sensorStreamHeight;
    float    focalLengthMm;
    float    pixelPitchUm;
} intrinsic_cam_params_t;

typedef struct {
    uint32_t               minFocusDistanceCm;
    cam_relative_position  positionAux;
    intrinsic_cam_params_t paramsMain;
    intrinsic_cam_params_t paramsAux;
} dual_cam_params_t;

typedef struct {
    bool            isValid;
    cam_sync_type_t camMasterPreview;
    cam_sync_type_t camMaster3A;
    uint32_t        activeCameras;
    bool            snapshotPostProcess;
    bool            snapshotPostProcessZoomRange;
} fov_control_result_t;


class QCameraFOVControl {
public:
    ~QCameraFOVControl();
    static QCameraFOVControl* create(cam_capability_t *capsMainCam, cam_capability_t* capsAuxCam);
    int32_t updateConfigSettings(parm_buffer_t* paramsMainCam, parm_buffer_t* paramsAuxCam);
    cam_capability_t consolidateCapabilities(cam_capability_t* capsMainCam,
            cam_capability_t* capsAuxCam);
    int32_t translateInputParams(parm_buffer_t* paramsMainCam, parm_buffer_t *paramsAuxCam);
    metadata_buffer_t* processResultMetadata(metadata_buffer_t* metaMainCam,
            metadata_buffer_t* metaAuxCam);
    fov_control_result_t getFovControlResult();
    cam_frame_margins_t getFrameMargins(int8_t masterCamera);

private:
    QCameraFOVControl();
    bool validateAndExtractParameters(cam_capability_t  *capsMainCam,
            cam_capability_t  *capsAuxCam);
    bool calculateBasicFovRatio();
    bool combineFovAdjustment();
    void  calculateDualCamTransitionParams();
    void convertUserZoomToWideAndTele(uint32_t zoom);
    uint32_t readjustZoomForTele(uint32_t zoomWide);
    uint32_t readjustZoomForWide(uint32_t zoomTele);
    uint32_t findZoomRatio(uint32_t zoom);
    inline uint32_t findZoomValue(uint32_t zoomRatio);
    cam_face_detection_data_t translateRoiFD(cam_face_detection_data_t faceDetectionInfo,
            cam_sync_type_t cam);
    cam_roi_info_t translateFocusAreas(cam_roi_info_t roiAfMain, cam_sync_type_t cam);
    cam_set_aec_roi_t translateMeteringAreas(cam_set_aec_roi_t roiAecMain, cam_sync_type_t cam);
    void generateFovControlResult();
    bool isMainCamFovWider();
    bool isSpatialAlignmentReady();
    void resetVars();
    bool canSwitchMasterTo(cam_type cam);
    bool sacRequestedDualZone();

    Mutex                           mMutex;
    fov_control_config_t            mFovControlConfig;
    fov_control_data_t              mFovControlData;
    fov_control_result_t            mFovControlResult;
    dual_cam_params_t               mDualCamParams;
    QCameraExtZoomTranslator       *mZoomTranslator;
};

}; // namespace qcamera

#endif /* __QCAMERAFOVCONTROL_H__ */
