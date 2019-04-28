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

#define LOG_TAG "DualFOVPP"
// System dependencies
#include <dlfcn.h>
#include <utils/Errors.h>
#include <stdio.h>
#include <stdlib.h>
// Camera dependencies
#include "QCameraDualFOVPP.h"
#include "QCameraTrace.h"
#include "cam_intf.h"
extern "C" {
#include "mm_camera_dbg.h"
}

#define LIB_PATH_LENGTH 100

namespace qcamera {

/*===========================================================================
 * FUNCTION   : QCameraDualFOVPP
 *
 * DESCRIPTION: constructor of QCameraDualFOVPP.
 *
 * PARAMETERS : None
 *
 * RETURN     : None
 *==========================================================================*/
QCameraDualFOVPP::QCameraDualFOVPP()
    : QCameraHALPP()
{
    m_dlHandle = NULL;
    m_pCaps = NULL;
}

/*===========================================================================
 * FUNCTION   : ~QCameraDualFOVPP
 *
 * DESCRIPTION: destructor of QCameraDualFOVPP.
 *
 * PARAMETERS : None
 *
 * RETURN     : None
 *==========================================================================*/
QCameraDualFOVPP::~QCameraDualFOVPP()
{
}

/*===========================================================================
 * FUNCTION   : init
 *
 * DESCRIPTION: initialization of QCameraDualFOVPP
 *
 * PARAMETERS :
 *   @bufNotifyCb      : call back function after HALPP process
 *   @getOutputCb      : call back function to request output buffe
 *   @pUserData        : Parent of HALPP, i.e. QCameraPostProc
 *   @pStaticParam     : holds dual camera calibration data in an array and its size
 *                       (expected size is 264 bytes)
 *
 * RETURN     : int32_t type of status
 *              NO_ERROR  -- success
 *              none-zero failure code
 *==========================================================================*/
int32_t QCameraDualFOVPP::init(halPPBufNotify bufNotifyCb, halPPGetOutput getOutputCb,
        void *pUserData, void *pStaticParam)
{
    LOGD("E");
    int32_t rc = NO_ERROR;
    QCameraHALPP::init(bufNotifyCb, getOutputCb, pUserData);

    m_pCaps = (cam_capability_t *)pStaticParam;

    /* we should load 3rd libs here, with dlopen/dlsym */
    doDualFovPPInit();

    LOGD("X");
    return rc;
}

/*===========================================================================
 * FUNCTION   : deinit
 *
 * DESCRIPTION: de initialization of QCameraDualFOVPP
 *
 * PARAMETERS : None
 *
 * RETURN     : int32_t type of status
 *              NO_ERROR  -- success
 *              none-zero failure code
 *==========================================================================*/
int32_t QCameraDualFOVPP::deinit()
{
    int32_t rc = NO_ERROR;
    LOGD("E");

    m_dlHandle = NULL;

    QCameraHALPP::deinit();
    LOGD("X");
    return rc;
}

/*===========================================================================
 * FUNCTION   : start
 *
 * DESCRIPTION: starting QCameraDualFOVPP
 *
 * PARAMETERS :
 *
 * RETURN     : int32_t type of status
 *              NO_ERROR  -- success
 *              none-zero failure code
 *==========================================================================*/
int32_t QCameraDualFOVPP::start()
{
    int32_t rc = NO_ERROR;
    LOGD("E");

    rc = QCameraHALPP::start();

    LOGD("X");
    return rc;
}


/*===========================================================================
 * FUNCTION   : feedInput
 *
 * DESCRIPTION: function to feed input data.
 *              Enqueue the frame index to inputQ if it is new frame
 *              Also, add the input image data to frame hash map
 *
 * PARAMETERS :
 *   @pInput  : ptr to input data
 *
 * RETURN     : int32_t type of status
 *              NO_ERROR  -- success
 *              none-zero failure code
 *==========================================================================*/
int32_t QCameraDualFOVPP::feedInput(qcamera_hal_pp_data_t *pInputData)
{
    int32_t rc = NO_ERROR;
    LOGD("E");
    if (NULL != pInputData) {
        QCameraStream* pSnapshotStream = NULL;
        mm_camera_buf_def_t *pInputSnapshotBuf = getSnapshotBuf(pInputData, pSnapshotStream);
        if (pInputSnapshotBuf != NULL) {
            uint32_t frameIndex = pInputSnapshotBuf->frame_idx;
            std::vector<qcamera_hal_pp_data_t*> *pVector = getFrameVector(frameIndex);
            if(pVector == NULL) {
                LOGD("insert new frame index = %d", frameIndex);
                uint32_t *pFrameIndex = new uint32_t;
                *pFrameIndex = frameIndex;
                // new the vector first
                pVector = new std::vector<qcamera_hal_pp_data_t*>(WIDE_TELE_CAMERA_NUMBER);
                pVector->at(WIDE_INPUT) = NULL;
                pVector->at(TELE_INPUT) = NULL;
                // Add vector to the hash map
                m_frameMap[frameIndex] = pVector;
                // Enqueue the frame index (i.e. key of vector) to queue
                if (false == m_iuputQ.enqueue((void*)pFrameIndex)) {
                    LOGE("Input Q is not active!!!");
                    releaseData(pInputData);
                    m_frameMap.erase(frameIndex);
                    delete pFrameIndex;
                    delete pVector;
                    rc = INVALID_OPERATION;
                    return rc;
                }
            }
            pInputData->frameIndex = frameIndex;
            // Check if frame is from main wide camera
            bool bIsMain = true;
            uint32_t mainHandle = get_main_camera_handle(
                    pInputData->src_reproc_frame->camera_handle);
            if (mainHandle == 0) {
                bIsMain = false;
            }
            LOGD("mainHandle = %d, is main frame = %d", mainHandle, bIsMain);
            // Add input data to vector
            if (bIsMain) {
                pVector->at(WIDE_INPUT) = pInputData;
            } else {
                pVector->at(TELE_INPUT) = pInputData;
            }

            // request output buffer only if both wide and tele input data are recieved
            if (pVector->at(0) != NULL && pVector->at(1) != NULL) {
                m_halPPGetOutputCB(frameIndex, m_pQCameraPostProc);
            }
        }
    } else {
        LOGE("pInput is NULL");
        rc = UNEXPECTED_NULL;
    }
    LOGD("X");
    return rc;
}

/*===========================================================================
 * FUNCTION   : feedOutput
 *
 * DESCRIPTION: function to feed output buffer and metadata
 *
 * PARAMETERS :
 *   @pOutput     : ptr to output data
 *
 * RETURN     : int32_t type of status
 *              NO_ERROR  -- success
 *              none-zero failure code
 *==========================================================================*/
int32_t QCameraDualFOVPP::feedOutput(qcamera_hal_pp_data_t *pOutputData)
{
    int32_t rc = NO_ERROR;
    LOGD("E");
    if (NULL != pOutputData) {
        uint32_t frameIndex = pOutputData->frameIndex;
        std::vector<qcamera_hal_pp_data_t*> *pVector = getFrameVector(frameIndex);
        // Get the main (Wide) input frame in order to get output buffer len,
        // and copy metadata buffer.
        if (pVector != NULL && pVector->at(WIDE_INPUT) != NULL) {
            qcamera_hal_pp_data_t *pInputData = pVector->at(WIDE_INPUT);
            mm_camera_super_buf_t *pInputFrame = pInputData->frame;
            QCameraStream* pSnapshotStream = NULL;
            QCameraStream* pMetadataStream = NULL;
            mm_camera_buf_def_t *pInputSnapshotBuf = getSnapshotBuf(pInputData, pSnapshotStream);
            mm_camera_buf_def_t *pInputMetadataBuf = getMetadataBuf(pInputData, pMetadataStream);
            mm_camera_super_buf_t *pOutputFrame = pOutputData->frame;
            mm_camera_buf_def_t *pOutputBufDefs = pOutputData->bufs;

            if (pInputSnapshotBuf == NULL || pInputMetadataBuf == NULL) {
                LOGE("cannot get sanpshot or metadata buf def");
                releaseData(pOutputData);
                return UNEXPECTED_NULL;
            }
            if (pSnapshotStream == NULL || pMetadataStream == NULL) {
                LOGE("cannot get sanpshot or metadata stream");
                releaseData(pOutputData);
                return UNEXPECTED_NULL;
            }

            // Copy main input frame info to output frame
            pOutputFrame->camera_handle = pInputFrame->camera_handle;
            pOutputFrame->ch_id = pInputFrame->ch_id;
            pOutputFrame->num_bufs = HAL_PP_NUM_BUFS;//snapshot and metadata
            pOutputFrame->bUnlockAEC = pInputFrame->bUnlockAEC;
            pOutputFrame->bReadyForPrepareSnapshot = pInputFrame->bReadyForPrepareSnapshot;

            // Reconstruction of output_frame super buffer
            pOutputFrame->bufs[0] = &pOutputBufDefs[0];
            pOutputFrame->bufs[1] = &pOutputBufDefs[1];

            // Allocate heap buffer for output image frame
            cam_frame_len_offset_t offset;
            memset(&offset, 0, sizeof(cam_frame_len_offset_t));
            LOGD("pInputSnapshotBuf->frame_len = %d", pInputSnapshotBuf->frame_len);
            rc = pOutputData->snapshot_heap->allocate(1, pInputSnapshotBuf->frame_len);
            if (rc < 0) {
                LOGE("Unable to allocate heap memory for image buf");
                releaseData(pOutputData);
                return NO_MEMORY;
            }
            pSnapshotStream->getFrameOffset(offset);
            memcpy(&pOutputBufDefs[0], pInputSnapshotBuf, sizeof(mm_camera_buf_def_t));
            LOGD("pOutputFrame->bufs[0]->fd = %d, pOutputFrame->bufs[0]->buffer = %x",
                    pOutputFrame->bufs[0]->fd, pOutputFrame->bufs[0]->buffer);
            pOutputData->snapshot_heap->getBufDef(offset, pOutputBufDefs[0], 0);
            LOGD("pOutputFrame->bufs[0]->fd = %d, pOutputFrame->bufs[0]->buffer = %x",
                    pOutputFrame->bufs[0]->fd, pOutputFrame->bufs[0]->buffer);

            // Allocate heap buffer for output metadata
            LOGD("pInputMetadataBuf->frame_len = %d", pInputMetadataBuf->frame_len);
            rc = pOutputData->metadata_heap->allocate(1, pInputMetadataBuf->frame_len);
            if (rc < 0) {
                LOGE("Unable to allocate heap memory for metadata buf");
                releaseData(pOutputData);
                return NO_MEMORY;
            }
            memset(&offset, 0, sizeof(cam_frame_len_offset_t));
            pMetadataStream->getFrameOffset(offset);
            memcpy(&pOutputBufDefs[1], pInputMetadataBuf, sizeof(mm_camera_buf_def_t));
            pOutputData->metadata_heap->getBufDef(offset, pOutputBufDefs[1], 0);
            // copy the whole metadata
            memcpy(pOutputBufDefs[1].buffer, pInputMetadataBuf->buffer,
                    pInputMetadataBuf->frame_len);

            // Enqueue output_data to m_outgoingQ
            if (false == m_outgoingQ.enqueue((void *)pOutputData)) {
                LOGE("outgoing Q is not active!!!");
                releaseData(pOutputData);
                rc = INVALID_OPERATION;
            }
        }
    } else {
        LOGE("pOutput is NULL");
        rc = UNEXPECTED_NULL;
    }
    LOGD("X");
    return rc;
}

/*===========================================================================
 * FUNCTION   : process
 *
 * DESCRIPTION: function to start CP FOV blending process
 *
 * PARAMETERS : None
 *
 * RETURN     : int32_t type of status
 *              NO_ERROR  -- success
 *              none-zero failure code
 *==========================================================================*/
int32_t QCameraDualFOVPP::process()
{
    int32_t rc = NO_ERROR;

    /* dump in/out frames */
    char prop[PROPERTY_VALUE_MAX];
    memset(prop, 0, sizeof(prop));
    property_get("persist.camera.dualfov.dumpimg", prop, "0");
    int dumpimg = atoi(prop);

    LOGD("E");

    // TODO: dequeue from m_inputQ and start process logic
    // Start the blending process when it is ready
    if (canProcess()) {
        LOGI("start Dual FOV process");
        uint32_t *pFrameIndex = (uint32_t *)m_iuputQ.dequeue();
        if (pFrameIndex == NULL) {
            LOGE("frame index is null");
            return UNEXPECTED_NULL;
        }
        uint32_t frameIndex = *pFrameIndex;
        std::vector<qcamera_hal_pp_data_t*> *pVector = getFrameVector(frameIndex);
        // Search vector of input frames in frame map
        if (pVector == NULL) {
            LOGE("Cannot find vecotr of input frames");
            return UNEXPECTED_NULL;
        }
        // Get input and output frame buffer
        qcamera_hal_pp_data_t *pInputMainData =
                (qcamera_hal_pp_data_t *)pVector->at(WIDE_INPUT);
        if (pInputMainData == NULL) {
            LOGE("Cannot find input main data");
            return UNEXPECTED_NULL;
        }
        if (pInputMainData->src_reproc_frame == NULL) {
            LOGI("process pInputMainData->src_reproc_frame = NULL");
        }
        //mm_camera_super_buf_t *input_main_frame = input_main_data->frame;
        qcamera_hal_pp_data_t *pInputAuxData =
                (qcamera_hal_pp_data_t *)pVector->at(TELE_INPUT);
        if (pInputAuxData == NULL) {
            LOGE("Cannot find input aux data");
            return UNEXPECTED_NULL;
        }

        //mm_camera_super_buf_t *input_aux_frame = input_aux_data->frame;
        qcamera_hal_pp_data_t *pOutputData =
                (qcamera_hal_pp_data_t*)m_outgoingQ.dequeue();
        if (pOutputData == NULL) {
            LOGE("Cannot find output data");
            return UNEXPECTED_NULL;
        }

        QCameraStream* pMainSnapshotStream = NULL;
        QCameraStream* pMainMetadataStream = NULL;
        QCameraStream* pAuxSnapshotStream  = NULL;
        QCameraStream* pAuxMetadataStream  = NULL;

        mm_camera_buf_def_t *main_snapshot_buf =
                getSnapshotBuf(pInputMainData, pMainSnapshotStream);
        if (main_snapshot_buf == NULL) {
            LOGE("main_snapshot_buf is NULL");
            return UNEXPECTED_NULL;
        }
        mm_camera_buf_def_t *main_meta_buf = getMetadataBuf(pInputMainData, pMainMetadataStream);
        if (main_meta_buf == NULL) {
            LOGE("main_meta_buf is NULL");
            return UNEXPECTED_NULL;
        }
        mm_camera_buf_def_t *aux_snapshot_buf = getSnapshotBuf(pInputAuxData, pAuxSnapshotStream);
        if (aux_snapshot_buf == NULL) {
            LOGE("aux_snapshot_buf is NULL");
            return UNEXPECTED_NULL;
        }
        mm_camera_buf_def_t *aux_meta_buf = getMetadataBuf(pInputAuxData, pAuxMetadataStream);
        if (aux_meta_buf == NULL) {
            LOGE("aux_meta_buf is NULL");
            return UNEXPECTED_NULL;
        }

        mm_camera_super_buf_t *output_frame = pOutputData->frame;
        mm_camera_buf_def_t *output_snapshot_buf = output_frame->bufs[0];

        // Use offset info from reproc stream
        if (pMainSnapshotStream == NULL) {
            LOGE("pMainSnapshotStream is NULL");
            return UNEXPECTED_NULL;
        }
        cam_frame_len_offset_t frm_offset;
        pMainSnapshotStream->getFrameOffset(frm_offset);
        LOGI("Stream type:%d, stride:%d, scanline:%d, frame len:%d",
                pMainSnapshotStream->getMyType(),
                frm_offset.mp[0].stride, frm_offset.mp[0].scanline,
                frm_offset.frame_len);

        if (dumpimg) {
            dumpYUVtoFile((uint8_t *)main_snapshot_buf->buffer, frm_offset,
                    main_snapshot_buf->frame_idx, "wide");
            dumpYUVtoFile((uint8_t *)aux_snapshot_buf->buffer,  frm_offset,
                    aux_snapshot_buf->frame_idx,  "tele");
        }

        //Get input and output parameter
        dualfov_input_params_t inParams;
        if (pAuxSnapshotStream == NULL) {
            LOGE("pAuxSnapshotStream is NULL");
            return UNEXPECTED_NULL;
        }
        getInputParams(main_meta_buf, aux_meta_buf, pMainSnapshotStream, pAuxSnapshotStream,
                inParams);
        dumpInputParams(inParams);

        doDualFovPPProcess((const uint8_t *)main_snapshot_buf->buffer,
                        (const uint8_t *)aux_snapshot_buf->buffer,
                        inParams,
                        (uint8_t *)output_snapshot_buf->buffer);

        if (dumpimg) {
            dumpYUVtoFile((uint8_t *)output_snapshot_buf->buffer, frm_offset,
                    main_snapshot_buf->frame_idx, "out");
        }

        /* clean and invalidate caches, for input and output buffers*/
        pOutputData->snapshot_heap->cleanInvalidateCache(0);

        QCameraMemory *pMem = (QCameraMemory *)main_snapshot_buf->mem_info;
        pMem->invalidateCache(main_snapshot_buf->buf_idx);

        pMem = (QCameraMemory *)aux_snapshot_buf->mem_info;
        pMem->invalidateCache(aux_snapshot_buf->buf_idx);


        // Calling cb function to return output_data after processed.
        m_halPPBufNotifyCB(pOutputData, m_pQCameraPostProc);

        // also send input buffer to postproc.
        m_halPPBufNotifyCB(pInputMainData, m_pQCameraPostProc);
        m_halPPBufNotifyCB(pInputAuxData, m_pQCameraPostProc);
        //releaseData(pInputMainData);
        //releaseData(pInputAuxData);

        // Release internal resource
        m_frameMap.erase(frameIndex);
        delete pFrameIndex;
        delete pVector;
    }
    LOGD("X");
    return rc;
}

/*===========================================================================
 * FUNCTION   : getSnapshotBuf
 *
 * DESCRIPTION: function to get snapshot buf def and the stream from frame
 * PARAMETERS :
 *   @pData           : input frame super buffer
 *   @pSnapshotStream : stream of snapshot that found
 * RETURN             : snapshot buf def
 *==========================================================================*/
mm_camera_buf_def_t* QCameraDualFOVPP::getSnapshotBuf(qcamera_hal_pp_data_t* pData,
        QCameraStream* &pSnapshotStream)
{
    mm_camera_buf_def_t *pBufDef = NULL;
    if (pData == NULL) {
        LOGE("Cannot find input frame super buffer");
        return pBufDef;
    }
    mm_camera_super_buf_t *pFrame = pData->frame;
    QCameraChannel *pChannel = m_pQCameraPostProc->getChannelByHandle(pFrame->ch_id);
    if (pChannel == NULL) {
        LOGE("Cannot find channel");
        return pBufDef;
    }
    // Search for input snapshot frame buf
    for (uint32_t i = 0; i < pFrame->num_bufs; i++) {
        pSnapshotStream = pChannel->getStreamByHandle(pFrame->bufs[i]->stream_id);
        if (pSnapshotStream != NULL) {
            if (pSnapshotStream->isTypeOf(CAM_STREAM_TYPE_SNAPSHOT) ||
                pSnapshotStream->isOrignalTypeOf(CAM_STREAM_TYPE_SNAPSHOT)) {
                    pBufDef = pFrame->bufs[i];
                    break;
            }
        }
    }
    return pBufDef;
}

/*===========================================================================
 * FUNCTION   : getMetadataBuf
 *
 * DESCRIPTION: function to get metadata buf def and the stream from frame
 * PARAMETERS :
 *   @pData     : input frame super buffer
 *   @pMetadataStream : stream of metadata that found
 * RETURN     : metadata buf def
 *==========================================================================*/
mm_camera_buf_def_t* QCameraDualFOVPP::getMetadataBuf(qcamera_hal_pp_data_t *pData,
        QCameraStream* &pMetadataStream)
{
    mm_camera_buf_def_t *pBufDef = NULL;
    if (pData == NULL) {
        LOGE("Cannot find input frame super buffer");
        return pBufDef;
    }
    mm_camera_super_buf_t* pFrame = pData->frame;
    QCameraChannel *pChannel =
            m_pQCameraPostProc->getChannelByHandle(pData->src_reproc_frame->ch_id);
    LOGD("src_reproc_frame num_bufs = %d", pFrame->num_bufs);
    if (pChannel == NULL) {
            LOGE("Cannot find src_reproc_frame channel");
            return pBufDef;
    }
    for (uint32_t i = 0;
            (i < pData->src_reproc_frame->num_bufs); i++) {
        pMetadataStream = pChannel->getStreamByHandle(pData->src_reproc_frame->bufs[i]->stream_id);
        if (pData->src_reproc_frame->bufs[i]->stream_type == CAM_STREAM_TYPE_METADATA) {
            pBufDef = pData->src_reproc_frame->bufs[i];
            LOGD("find metadata stream and buf from src_reproc_frame");
            break;
        }
    }
    if (pBufDef == NULL) {
        LOGD("frame num_bufs = %d", pFrame->num_bufs);
        pChannel = m_pQCameraPostProc->getChannelByHandle(pFrame->ch_id);
        if (pChannel == NULL) {
            LOGE("Cannot find frame channel");
            return pBufDef;
        }
        for (uint32_t i = 0; i < pFrame->num_bufs; i++) {
            pMetadataStream = pChannel->getStreamByHandle(pFrame->bufs[i]->stream_id);
            if (pMetadataStream != NULL) {
                LOGD("bufs[%d] stream_type = %d", i, pFrame->bufs[i]->stream_type);
                if (pFrame->bufs[i]->stream_type == CAM_STREAM_TYPE_METADATA) {
                    pBufDef = pFrame->bufs[i];
                    break;
                }
            }
        }
    }
    return pBufDef;
}

/*===========================================================================
 * FUNCTION   : canProcess
 *
 * DESCRIPTION: function to release internal resources
 * RETURN     : If CP FOV can start blending process
 *==========================================================================*/
bool QCameraDualFOVPP::canProcess()
{
    LOGD("E");
    bool ready = false;
    if(!m_iuputQ.isEmpty() && !m_outgoingQ.isEmpty()) {
        ready = true;
    }
    LOGD("X");
    return ready;
}

/*===========================================================================
 * FUNCTION   : getInputParams
 *
 * DESCRIPTION: Helper function to get input params from input metadata
 *==========================================================================*/
void QCameraDualFOVPP::getInputParams(mm_camera_buf_def_t *pMainMetaBuf,
        mm_camera_buf_def_t *pAuxMetaBuf, QCameraStream* pMainSnapshotStream,
        QCameraStream* pAuxSnapshotStream, dualfov_input_params_t& inParams)
{
    LOGD("E");
    memset(&inParams, 0, sizeof(dualfov_input_params_t));
    metadata_buffer_t *pMainMeta = (metadata_buffer_t *)pMainMetaBuf->buffer;
    metadata_buffer_t *pAuxMeta = (metadata_buffer_t *)pAuxMetaBuf->buffer;

    // Wide frame size
    cam_frame_len_offset_t offset;
    pMainSnapshotStream->getFrameOffset(offset);
    inParams.wide.width     = offset.mp[0].width;
    inParams.wide.height    = offset.mp[0].height;
    inParams.wide.stride    = offset.mp[0].stride;
    inParams.wide.scanline  = offset.mp[0].scanline;
    inParams.wide.frame_len = offset.frame_len;

    // Tele frame size
    pAuxSnapshotStream->getFrameOffset(offset);
    inParams.tele.width     = offset.mp[0].width;
    inParams.tele.height    = offset.mp[0].height;
    inParams.tele.stride    = offset.mp[0].stride;
    inParams.tele.scanline  = offset.mp[0].scanline;
    inParams.tele.frame_len = offset.frame_len;

    // user_zoom
    int32_t zoom_level = -1; // 0 means zoom 1x.
    IF_META_AVAILABLE(int32_t, userZoom, CAM_INTF_PARM_ZOOM, pMainMeta) {
        zoom_level = *userZoom;
        LOGD("zoom level in main meta:%d", zoom_level);
    }
    inParams.user_zoom= getUserZoomRatio(zoom_level);
    LOGI("dual fov total zoom ratio: %d", inParams.user_zoom);

    IF_META_AVAILABLE(int32_t, auxUserZoom, CAM_INTF_PARM_ZOOM, pAuxMeta) {
        LOGD("zoom level in aux meta:%d", *auxUserZoom);
    }

    IF_META_AVAILABLE(uint32_t, afState, CAM_INTF_META_AF_STATE, pMainMeta) {
        if (((*afState) == CAM_AF_STATE_FOCUSED_LOCKED) ||
            ((*afState) == CAM_AF_STATE_PASSIVE_FOCUSED)) {
            inParams.af_status = AF_STATUS_VALID;
        } else {
            inParams.af_status = AF_STATUS_INVALID;
        }
        LOGD("af state:%d, output af status:%d", *afState, inParams.af_status);
    }

    IF_META_AVAILABLE(uint32_t, auxAfState, CAM_INTF_META_AF_STATE, pAuxMeta) {
        int aux_af_status = 0;
        if (((*auxAfState) == CAM_AF_STATE_FOCUSED_LOCKED) ||
            ((*auxAfState) == CAM_AF_STATE_PASSIVE_FOCUSED)) {
            aux_af_status = AF_STATUS_VALID;
        } else {
            aux_af_status = AF_STATUS_INVALID;
        }
        LOGD("aux af state:%d, output af status:%d", *auxAfState, aux_af_status);
    }


    LOGD("X");
}


int32_t QCameraDualFOVPP::doDualFovPPInit()
{
    LOGD("E");
    int rc = NO_ERROR;

    LOGD("X");
    return rc;
}

int32_t QCameraDualFOVPP::doDualFovPPProcess(const uint8_t* pWide, const uint8_t* pTele,
                                                    dualfov_input_params_t inParams,
                                                    uint8_t* pOut)
{
    LOGW("E.");

    // trace begin

    // half image from main, and half image from tele

    // Y
    memcpy(pOut, pWide, inParams.wide.stride * inParams.wide.scanline / 2);
    memcpy(pOut  + inParams.wide.stride * inParams.wide.scanline / 2,
           pTele + inParams.wide.stride * inParams.wide.scanline / 2,
           inParams.wide.stride * inParams.wide.scanline / 2);

    // UV
    uint32_t uv_offset = inParams.wide.stride * inParams.wide.scanline;
    memcpy(pOut  + uv_offset,
           pWide + uv_offset,
           inParams.wide.stride * (inParams.wide.scanline / 2) / 2);
    memcpy(pOut  + uv_offset + inParams.wide.stride * (inParams.wide.scanline / 2) / 2,
           pTele + uv_offset + inParams.wide.stride * (inParams.wide.scanline / 2) / 2,
           inParams.wide.stride * (inParams.wide.scanline / 2) / 2);

    // trace end

    LOGW("X.");
    return NO_ERROR;
}

uint32_t QCameraDualFOVPP::getUserZoomRatio(int32_t zoom_level)
{
    uint32_t zoom_ratio = 4096;

    LOGD("E. input zoom level:%d", zoom_level);

    if (zoom_level < 0) {
        LOGW("invalid zoom evel!");
        /* got the zoom value from QCamera2HWI Parameters */
        zoom_level = 0;
    }

    // user_zoom_ratio = qcom_zoom_ratio * 4096 / 100
    if (m_pCaps != NULL) {
        zoom_ratio *= m_pCaps->zoom_ratio_tbl[zoom_level];
        zoom_ratio /= 100;
        LOGD("converted zoom ratio:%d", zoom_ratio);
    }

    LOGD("X. zoom_ratio:%d", zoom_ratio);
    return zoom_ratio;
}

void QCameraDualFOVPP::dumpYUVtoFile(const uint8_t* pBuf, cam_frame_len_offset_t offset, uint32_t idx, const char* name_prefix)
{
    LOGD("E.");
    char filename[256];

    snprintf(filename, sizeof(filename), QCAMERA_DUMP_FRM_LOCATION"%s_%dx%d_%d.yuv",
                name_prefix, offset.mp[0].stride, offset.mp[0].scanline, idx);

    QCameraHALPP::dumpYUVtoFile(pBuf,(const char*)filename, offset.frame_len);

    LOGD("X.");
}

void QCameraDualFOVPP::dumpInputParams(const dualfov_input_params_t& p)
{
    LOGD("E");

    const cam_frame_size_t* s = NULL;

    s = &p.wide;
    LOGD("wide frame size: %d, %d, stride:%d, scanline:%d",
            s->width, s->height, s->stride, s->scanline);

    s = &p.tele;
    LOGD("wide frame size: %d, %d, stride:%d, scanline:%d",
            s->width, s->height, s->stride, s->scanline);

    LOGD("zoom ratio: %f", p.user_zoom / 4096.0);
    LOGD("X");
}


/*===========================================================================
 * FUNCTION   : dumpOISData
 *
 * DESCRIPTION: Read Sensor OIS data from metadata and dump it
 *
 * PARAMETERS :
 * @pMetadata : Frame metadata
 *
 * RETURN     : None
 *
 *==========================================================================*/
void QCameraDualFOVPP::dumpOISData(metadata_buffer_t*  pMetadata)
{
    if (!pMetadata) {
        LOGD("OIS data not available");
        return;
    }

    IF_META_AVAILABLE(cam_ois_data_t, pOisData, CAM_INTF_META_OIS_READ_DATA, pMetadata) {
        LOGD("Ois Data: data size: %d", pOisData->size);
        uint8_t *data = pOisData->data;
        if (pOisData->size == 8) {
            LOGD("Ois Data Buffer : %d %d %d %d %d %d %d %d ",
                    data[0], data[1], data[2], data[3], data[4], data[5], data[6], data[7]);
        }
    }
    return;
}


} // namespace qcamera
