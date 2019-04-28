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

#define LOG_TAG "QCameraHALPP"

// Camera dependencies
#include <sys/stat.h>
#include "QCameraTrace.h"
#include "QCameraHALPP.h"
#include "QCameraQueue.h"
extern "C" {
#include "mm_camera_dbg.h"
}

namespace qcamera {

/*===========================================================================
 * FUNCTION   : QCameraHALPP
 *
 * DESCRIPTION: constructor of QCameraHALPP.
 *
 * PARAMETERS : None
 *
 * RETURN     : None
 *==========================================================================*/
QCameraHALPP::QCameraHALPP()
    : m_iuputQ(releaseInputDataCb, this),
      m_outgoingQ(releaseOngoingDataCb, this),
      m_halPPBufNotifyCB(NULL),
      m_halPPGetOutputCB(NULL),
      m_pQCameraPostProc(NULL)
{
}

/*===========================================================================
 * FUNCTION   : ~QCameraHALPP
 *
 * DESCRIPTION: destructor of QCameraHALPP.
 *
 * PARAMETERS : None
 *
 * RETURN     : None
 *==========================================================================*/
QCameraHALPP::~QCameraHALPP()
{
}

/*===========================================================================
 * FUNCTION   : init
 *
 * DESCRIPTION: initialization of QCameraHALPP
 *
 * PARAMETERS :
 *   @bufNotifyCb      : call back function after HALPP process done and return frame
 *   @getOutputCb      : call back function to request output buffer
 *   @pUserData        : Parent of HALPP, i.e. QCameraPostProc
 *
 * RETURN     : int32_t type of status
 *              NO_ERROR  -- success
 *              none-zero failure code
 *==========================================================================*/
int32_t QCameraHALPP::init(halPPBufNotify bufNotifyCb, halPPGetOutput getOutputCb, void *pUserData)
{
    int32_t rc = NO_ERROR;
    // connect HALPP call back function
    m_halPPBufNotifyCB = bufNotifyCb;
    m_halPPGetOutputCB = getOutputCb;
    m_pQCameraPostProc = (QCameraPostProcessor*)pUserData;
    return rc;
}

/*===========================================================================
 * FUNCTION   : deinit
 *
 * DESCRIPTION: de-initialization of QCameraHALPP
 *
 * PARAMETERS :
 *
 * RETURN     : int32_t type of status
 *              NO_ERROR  -- success
 *              none-zero failure code
 *==========================================================================*/
int32_t QCameraHALPP::deinit()
{
    int32_t rc = NO_ERROR;
    m_halPPBufNotifyCB = NULL;
    m_halPPGetOutputCB = NULL;
    m_pQCameraPostProc = NULL;
    return rc;
}

/*===========================================================================
 * FUNCTION   : start
 *
 * DESCRIPTION: starting QCameraHALPP
 *
 * PARAMETERS :
 *
 * RETURN     : int32_t type of status
 *              NO_ERROR  -- success
 *              none-zero failure code
 *==========================================================================*/
int32_t QCameraHALPP::start()
{
    int32_t rc = NO_ERROR;
    LOGD("E");

    LOGD("X");
    return rc;
}

/*===========================================================================
 * FUNCTION   : stop
 *
 * DESCRIPTION: stop QCameraHALPP
 *
 * PARAMETERS :
 *
 * RETURN     : int32_t type of status
 *              NO_ERROR  -- success
 *              none-zero failure code
 *==========================================================================*/
int32_t QCameraHALPP::stop()
{
    int32_t rc = NO_ERROR;
    LOGD("E");

    LOGD("X");
    return rc;
}

/*===========================================================================
 * FUNCTION   : flushQ
 *
 * DESCRIPTION: flush m_iuputQ and m_outgoingQ.
 *
 * PARAMETERS : None
 *
 * RETURN     : None
 *==========================================================================*/
int32_t QCameraHALPP::flushQ()
{
    int32_t rc = NO_ERROR;
    m_iuputQ.flush();
    m_outgoingQ.flush();
    return rc;
}

/*===========================================================================
 * FUNCTION   : initQ
 *
 * DESCRIPTION: init m_iuputQ and m_outgoingQ.
 *
 * PARAMETERS : None
 *
 * RETURN     : None
 *==========================================================================*/
int32_t QCameraHALPP::initQ()
{
    int32_t rc = NO_ERROR;
    m_iuputQ.init();
    m_outgoingQ.init();
    return rc;
}

/*===========================================================================
 * FUNCTION   : getFrameVector
 *
 * DESCRIPTION: get vector of input frames from map
 *
 * PARAMETERS :
 *   @frameIndex      : frame index (key of the map)
 *
 * RETURN     : vector pointer
 *==========================================================================*/
std::vector<qcamera_hal_pp_data_t*>*
        QCameraHALPP::getFrameVector(uint32_t frameIndex)
{
    std::vector<qcamera_hal_pp_data_t*> *pVector = NULL;
    // Search vector of input frames in frame map
    if (m_frameMap.find(frameIndex) != m_frameMap.end()) {
        pVector = m_frameMap[frameIndex];
    }
    return pVector;
}

/*===========================================================================
 * FUNCTION   : releaseData
 *
 * DESCRIPTION: release buffer in qcamera_hal_pp_data_t
 *
 * PARAMETERS :
 *   @pData      : hal pp data
 *
 * RETURN     : None
 *==========================================================================*/
void QCameraHALPP::releaseData(qcamera_hal_pp_data_t *pData)
{
    if (pData) {
        if (pData->src_reproc_frame) {
            if (!pData->reproc_frame_release) {
                m_pQCameraPostProc->releaseSuperBuf(pData->src_reproc_frame);
            }
            free(pData->src_reproc_frame);
            pData->src_reproc_frame = NULL;
        }
        mm_camera_super_buf_t *frame = pData->frame;
        if (frame) {
            if (pData->halPPAllocatedBuf && pData->bufs) {
                free(pData->bufs);
            } else {
                m_pQCameraPostProc->releaseSuperBuf(frame);
            }
            free(frame);
            frame = NULL;
        }
        if (pData->snapshot_heap) {
            pData->snapshot_heap->deallocate();
            delete pData->snapshot_heap;
            pData->snapshot_heap = NULL;
        }
        if (pData->metadata_heap) {
            pData->metadata_heap->deallocate();
            delete pData->metadata_heap;
            pData->metadata_heap = NULL;
        }
        if (NULL != pData->src_reproc_bufs) {
            delete [] pData->src_reproc_bufs;
        }
        if ((pData->offline_reproc_buf != NULL)
                && (pData->offline_buffer)) {
            free(pData->offline_reproc_buf);
            pData->offline_reproc_buf = NULL;
            pData->offline_buffer = false;
        }
    }
}

/*===========================================================================
 * FUNCTION   : releaseOngoingDataCb
 *
 * DESCRIPTION: callback function to release ongoing data node
 *
 * PARAMETERS :
 *   @pData     : ptr to ongoing job data
 *   @pUserData : user data ptr (QCameraHALPP)
 *
 * RETURN     : None
 *==========================================================================*/
void QCameraHALPP::releaseOngoingDataCb(void *pData, void *pUserData)
{
    if (pUserData != NULL && pData != NULL) {
        QCameraHALPP *pme = (QCameraHALPP *)pUserData;
        pme->releaseData((qcamera_hal_pp_data_t*)pData);
    }
}

/*===========================================================================
 * FUNCTION   : releaseInputDataCb
 *
 * DESCRIPTION: callback function to release input data node
 *
 * PARAMETERS :
 *   @pData     : ptr to input job data
 *   @pUserData : user data ptr (QCameraHALPP)
 *
 * RETURN     : None
 *==========================================================================*/
void QCameraHALPP::releaseInputDataCb(void *pData, void *pUserData)
{
    if (pUserData != NULL && pData != NULL) {
        QCameraHALPP *pme = (QCameraHALPP *)pUserData;
        // what enqueued to the input queue is just the frame index
        // we need to use hash map to find the vector of frames and release the buffers
        uint32_t *pFrameIndex = (uint32_t *)pData;
        uint32_t frameIndex = *pFrameIndex;
        std::vector<qcamera_hal_pp_data_t*> *pVector = pme->getFrameVector(frameIndex);
        if (pVector != NULL) {
            for (size_t i = 0; i < pVector->size(); i++) {
                if (pVector->at(i) != NULL) {
                    pme->releaseData(pVector->at(i));
                }
            }
            delete pVector;
            pVector = NULL;
        }
        delete pFrameIndex;
        pFrameIndex = NULL;
    }
}

void QCameraHALPP::dumpYUVtoFile(const uint8_t* pBuf, const char *name, ssize_t buf_len)
{
    LOGD("E.");

    int file_fd = open(name, O_RDWR | O_CREAT, 0777);
    if (file_fd > 0) {
        fchmod(file_fd, S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH);
        ssize_t writen_bytes = 0;
        writen_bytes = write(file_fd, pBuf, buf_len);
        close(file_fd);
        LOGD("dump output frame to file: %s, size:%d", name, buf_len);
    }

    LOGD("X.");
}

} // namespace qcamera
