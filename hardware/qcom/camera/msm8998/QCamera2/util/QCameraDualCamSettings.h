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

#ifndef __QCAMERADUALCAMSETTINGS_H__
#define __QCAMERADUALCAMSETTINGS_H__

typedef enum {
    NONE,
    SENSOR_SLEEP,
    ISPIF_FRAME_DROP
} dual_cam_low_power_mode;


// Dual camera settings

// This setting should be 1 if hw sync is in place, 0 otherwise.
#define DUALCAM_HW_SYNC_ENABLED                 (1)

// This setting enables/disables LPM. When disabled(0), none of the cameras will go
// into low power mode.
#define DUALCAM_LPM_ENABLE                      (1)

// This setting indicates low power modes for two cameras. Possible low power modes are listed
// under dual_cam_low_power_mode.
#define DUALCAM_LPM_MAIN                   (ISPIF_FRAME_DROP)
#define DUALCAM_LPM_AUX                    (SENSOR_SLEEP)


// FOV-control settings

// Main camera fallback mechanism for low light and macro scene
// If set to 1, low light and macro scene will force the transition from narrow FOV to
// wide FOV camera
// If set to 0, these conditions are ignored and the forced transition from narrow FOV to
// wide FOV camera will not take place
#define FOVC_MAIN_CAM_FALLBACK_MECHANISM        (1)

// Use external zoom translator. Setting this to 1 allows opening an external lib containing
// implementation for custom zoom translation for user zoom to wide zoom and tele zoom. This
// would override the default zoom translation logic present in the FOV-control.
#define FOVC_USE_EXTERNAL_ZOOM_TRANSLATOR       (0)

// camera mode settings

// This setting will enable the snapshot postprocessing.
// If set to 1, it will enable capturing snapshots from both the cameras and feeding those
// to snapshot postprocessing algorithm.
// If set to 0, a snapshot is captured only from the master camera session at any time.
#define FOVC_CAM_SNAPSHOT_PP_ENABLE             (1)

// This setting indicates the minimum zoom value for the snapshot postprocessing.
// Snapshot postprocessing is only enabled for zoom equal to and higher than this value.
#define FOVC_CAM_SNAPSHOT_PP_ZOOM_MIN           (1.5)

// This setting indicates the maximum zoom value for the snapshot postprocessing.
// Snapshot postprocessing is only enabled for zoom equal to and lower than this value.
#define FOVC_CAM_SNAPSHOT_PP_ZOOM_MAX           (2.75)

// This setting indicates the minimum lux value for snapshot postprocessing.
// If the current lux is lower than this value, snapshot postprocessing will be disabled
#define FOVC_CAM_SNAPSHOT_PP_LUX_MIN            (100)

// camcorder mode settings

// This setting will enable the snapshot postprocessing.
// If set to 1, it will enable capturing snapshots from both the cameras and feeding those
// to snapshot postprocessing algorithm.
// If set to 0, a snapshot is captured only from the master camera session at any time.
#define FOVC_CAMCORDER_SNAPSHOT_PP_ENABLE       (0)

// Main and Aux camera switch settings
// These values indicates the lux amd min focus distance thresholds to switch the preview
// from main to aux camera. If the current lux or focus distance values are lower than
// these thresholds, camera preview will not switch from main to aux.
#define FOVC_AUXCAM_SWITCH_LUX_MIN              (100)
#define FOVC_AUXCAM_SWITCH_FOCUS_DIST_CM_MIN    (15)

// This setting indicates the threshold for zoom stable count in terms of number of frames.
// This is a power optimization setting. When in the transition zone, if the zoom doesn't
// change for thse many frames, the non-master camera is put in LPM.
#define FOVC_ZOOM_STABLE_COUNT_THRESHOLD        (15)

// This setting indicates the threshold for focus distance stable count in terms of number of frames
// This threshold is used for the macro scene focus and change the camera state accordingly.
#define FOVC_FOCUS_DIST_STABLE_COUNT_THRESHOLD  (15)

// This setting indicates the threshold for brightness stable count in terms of number of frames.
// This threshold is used for the low light condition and change the camera state accordingly.
#define FOVC_BRIGHTNESS_STABLE_COUNT_THRESHOLD  (15)

#endif /* __QCAMERADUALCAM_H__ */
