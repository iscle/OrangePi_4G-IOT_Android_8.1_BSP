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

#ifndef __QCAMERA_HAL_PP_H__
#define __QCAMERA_HAL_PP_H__

// Camera dependencies
#include "QCamera2HWI.h"
#include "QCameraPostProc.h"

// STL dependencies
#include <unordered_map>
#include <vector>
#include <sys/stat.h>
extern "C" {
#include "mm_camera_interface.h"
#include "mm_jpeg_interface.h"
}

namespace qcamera {

/** halPPBufNotify: function definition for frame notify
*   handling
*    @pOutput  : received qcamera_hal_pp_data_t data
*    @pUserData: user data pointer
**/
typedef void (*halPPBufNotify) (qcamera_hal_pp_data_t *pOutput,
                                        void *pUserData);

/** halPPGetOutput: function definition for get output buffer
*    @frameIndex: output frame index should match input frame index
*    @pUserData: user data pointer
**/
typedef void (*halPPGetOutput) (uint32_t frameIndex, void *pUserData);

class QCameraHALPP
{
public:
    virtual ~QCameraHALPP();
    virtual int32_t init(halPPBufNotify bufNotifyCb, halPPGetOutput getOutputCb, void *pUserData, void *pStaticParam) = 0;
    virtual int32_t init(halPPBufNotify bufNotifyCb, halPPGetOutput getOutputCb, void *pUserData);
    virtual int32_t deinit();
    virtual int32_t start();
    virtual int32_t stop();
    virtual int32_t flushQ();
    virtual int32_t initQ();
    virtual int32_t feedInput(qcamera_hal_pp_data_t *pInputData) = 0;
    virtual int32_t feedOutput(qcamera_hal_pp_data_t *pOutputData) = 0;
    virtual int32_t process() = 0;

protected:
    QCameraHALPP();
    virtual bool canProcess() = 0;
    virtual void releaseData(qcamera_hal_pp_data_t *pData);
    std::vector<qcamera_hal_pp_data_t*>* getFrameVector(uint32_t frameIndex);
    static void releaseInputDataCb(void *pData, void *pUserData);
    static void releaseOngoingDataCb(void *pData, void *pUserData);
    void dumpYUVtoFile(const uint8_t* pBuf, const char *name, ssize_t buf_len);

protected:
    QCameraQueue m_iuputQ;
    QCameraQueue m_outgoingQ;

    // hash map with frame index as key, and vecotr of input frames as value
    std::unordered_map<uint32_t, std::vector<qcamera_hal_pp_data_t*>*> m_frameMap;

    halPPBufNotify m_halPPBufNotifyCB;
    halPPGetOutput m_halPPGetOutputCB;
    QCameraPostProcessor *m_pQCameraPostProc;
}; // QCameraHALPP class
}; // namespace qcamera

#endif /* __QCAMERA_HAL_PP_H__ */



