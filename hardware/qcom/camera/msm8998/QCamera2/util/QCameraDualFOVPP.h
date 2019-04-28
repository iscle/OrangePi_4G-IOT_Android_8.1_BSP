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

#ifndef __QCAMERA_DUAL_FOV_PP_H__
#define __QCAMERA_DUAL_FOV_PP_H__

// Camera dependencies
#include "QCameraHALPP.h"

#define WIDE_TELE_CAMERA_NUMBER 2
enum halPPInputType {
    WIDE_INPUT = 0,
    TELE_INPUT = 1
};

enum dualfov_af_status_t {
    AF_STATUS_VALID,
    AF_STATUS_INVALID
};

typedef struct _cam_frame_size_t {
    uint32_t width;
    uint32_t height;
    uint32_t stride;
    uint32_t scanline;
    uint32_t frame_len;
} cam_frame_size_t;

typedef struct _dualfov_input_params_t {
    cam_frame_size_t wide;
    cam_frame_size_t tele;

    uint32_t user_zoom;

    dualfov_af_status_t af_status;
} dualfov_input_params_t;

typedef struct _dualfov_output_params_t {
    cam_frame_size_t out;
    uint32_t result;
} dualfov_output_params_t;


namespace qcamera {

class QCameraDualFOVPP : public QCameraHALPP
{
public:
    QCameraDualFOVPP();
    ~QCameraDualFOVPP();
    int32_t init(halPPBufNotify bufNotifyCb, halPPGetOutput getOutputCb, void *pUserData,
            void *pStaticParam);
    int32_t deinit();
    int32_t start();
    int32_t feedInput(qcamera_hal_pp_data_t *pInputData);
    int32_t feedOutput(qcamera_hal_pp_data_t *pOutputData);
    int32_t process();
protected:
    bool canProcess();
private:
    mm_camera_buf_def_t* getSnapshotBuf(qcamera_hal_pp_data_t* pData,
            QCameraStream* &pSnapshotStream);
    mm_camera_buf_def_t* getMetadataBuf(qcamera_hal_pp_data_t* pData,
            QCameraStream* &pMetadataStream);
    void getInputParams(mm_camera_buf_def_t *pMainMetaBuf, mm_camera_buf_def_t *pAuxMetaBuf,
            QCameraStream* pMainSnapshotStream, QCameraStream* pAuxSnapshotStream,
            dualfov_input_params_t& inParams);
    int32_t doDualFovPPInit();
    int32_t doDualFovPPProcess(const uint8_t* pWide, const uint8_t* pTele,
            dualfov_input_params_t inParams, uint8_t* pOut);
    uint32_t getUserZoomRatio(int32_t zoom_level);
    void dumpYUVtoFile(const uint8_t* pBuf, cam_frame_len_offset_t offset, uint32_t idx,
            const char* name_prefix);
    void dumpInputParams(const dualfov_input_params_t& p);
    void dumpOISData(metadata_buffer_t*  pMetadata);


private:
    void *m_dlHandle;
    const cam_capability_t *m_pCaps;
}; // QCameraDualFOVPP class
}; // namespace qcamera

#endif /* __QCAMERA_DUAL_FOV_PP_H__ */



