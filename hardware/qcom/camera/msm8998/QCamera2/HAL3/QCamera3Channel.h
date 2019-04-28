/* Copyright (c) 2012-2016, The Linux Foundation. All rights reserved.
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

#ifndef __QCAMERA3_CHANNEL_H__
#define __QCAMERA3_CHANNEL_H__

// System dependencies
#include <utils/List.h>
#include <utils/Mutex.h>
#include <utils/Vector.h>
#include "gralloc_priv.h"
#include <sys/stat.h>
// Camera dependencies
#include "cam_intf.h"
#include "cam_types.h"
#include "hardware/camera3.h"
#include "QCamera3HALHeader.h"
#include "QCamera3Mem.h"
#include "QCamera3PostProc.h"
#include "QCamera3Stream.h"
#include "QCamera3StreamMem.h"

#include "HdrPlusClient.h"

extern "C" {
#include "mm_camera_interface.h"
#include "mm_jpeg_interface.h"
}

using namespace android;

#define MIN_STREAMING_BUFFER_NUM 7+11

#define QCAMERA_DUMP_FRM_PREVIEW          1
#define QCAMERA_DUMP_FRM_VIDEO            (1<<1)
#define QCAMERA_DUMP_FRM_INPUT_JPEG       (1<<2)
#define QCAMERA_DUMP_FRM_CALLBACK         (1<<3)
#define QCAMERA_DUMP_FRM_OUTPUT_JPEG      (1<<5)
#define QCAMERA_DUMP_FRM_INPUT_REPROCESS  (1<<6)

typedef int64_t nsecs_t;

namespace qcamera {

typedef void (*channel_cb_routine)(mm_camera_super_buf_t *metadata,
                                camera3_stream_buffer_t *buffer,
                                uint32_t frame_number, bool isInputBuffer,
                                void *userdata);

typedef void (*channel_cb_buffer_err)(QCamera3Channel* ch, uint32_t frameNumber,
                                camera3_buffer_status_t err,
                                void *userdata);

class QCamera3Channel
{
public:
    QCamera3Channel(uint32_t cam_handle,
                   uint32_t channel_handle,
                   mm_camera_ops_t *cam_ops,
                   channel_cb_routine cb_routine,
                   channel_cb_buffer_err cb_buf_err,
                   cam_padding_info_t *paddingInfo,
                   cam_feature_mask_t postprocess_mask,
                   void *userData, uint32_t numBuffers);
    virtual ~QCamera3Channel();

    virtual int32_t start();
    virtual int32_t stop();
    virtual int32_t setBatchSize(uint32_t);
    virtual int32_t queueBatchBuf();
    virtual int32_t setPerFrameMapUnmap(bool enable);
    int32_t bufDone(mm_camera_super_buf_t *recvd_frame);
    virtual int32_t setBundleInfo(const cam_bundle_config_t &bundleInfo);

    virtual uint32_t getStreamTypeMask();
    uint32_t getStreamID(uint32_t streamMask);
    void destroy();
    virtual int32_t initialize(cam_is_type_t isType) = 0;
    virtual int32_t request(buffer_handle_t * /*buffer*/,
                uint32_t /*frameNumber*/,
                int &/*indexUsed*/){ return 0;};
    virtual int32_t request(buffer_handle_t * /*buffer*/,
                uint32_t /*frameNumber*/,
                camera3_stream_buffer_t* /*pInputBuffer*/,
                metadata_buffer_t* /*metadata*/,
                int & /*indexUsed*/,
                __unused bool internalRequest = false,
                __unused bool meteringOnly = false){ return 0;};
    virtual void streamCbRoutine(mm_camera_super_buf_t *super_frame,
                            QCamera3Stream *stream) = 0;

    virtual int32_t registerBuffer(buffer_handle_t *buffer, cam_is_type_t isType) = 0;
    virtual QCamera3StreamMem *getStreamBufs(uint32_t len) = 0;
    virtual void putStreamBufs() = 0;
    virtual int32_t flush();

    QCamera3Stream *getStreamByHandle(uint32_t streamHandle);
    uint32_t getMyHandle() const {return m_handle;};
    uint32_t getNumOfStreams() const {return m_numStreams;};
    uint32_t getNumBuffers() const {return mNumBuffers;};
    QCamera3Stream *getStreamByIndex(uint32_t index);

    static void streamCbRoutine(mm_camera_super_buf_t *super_frame,
                QCamera3Stream *stream, void *userdata);
    void dumpYUV(mm_camera_buf_def_t *frame, cam_dimension_t dim,
            cam_frame_len_offset_t offset, uint8_t name);
    static bool isUBWCEnabled();
    void setUBWCEnabled(bool val);
    static cam_format_t getStreamDefaultFormat(cam_stream_type_t type,
            uint32_t width, uint32_t height, bool forcePreviewUBWC, cam_is_type_t isType);
    virtual int32_t timeoutFrame(__unused uint32_t frameNumber) = 0;

    void setNRMode(uint8_t nrMode) { mNRMode = nrMode; }
    uint8_t getNRMode() { return mNRMode; }

    void *mUserData;
    cam_padding_info_t mPaddingInfo;
    QCamera3Stream *mStreams[MAX_STREAM_NUM_IN_BUNDLE];
    uint32_t m_numStreams;
protected:

    int32_t addStream(cam_stream_type_t streamType,
                      cam_format_t streamFormat,
                      cam_dimension_t streamDim,
                      cam_rotation_t streamRotation,
                      uint8_t minStreamBufnum,
                      cam_feature_mask_t postprocessMask,
                      cam_is_type_t isType,
                      uint32_t batchSize = 0);

    int32_t allocateStreamInfoBuf(camera3_stream_t *stream);

    uint32_t m_camHandle;
    mm_camera_ops_t *m_camOps;
    bool m_bIsActive;
    bool m_bUBWCenable;

    uint32_t m_handle;


    mm_camera_buf_notify_t mDataCB;


    QCamera3HeapMemory *mStreamInfoBuf;
    channel_cb_routine mChannelCB;
    channel_cb_buffer_err mChannelCbBufErr;
    //cam_padding_info_t *mPaddingInfo;
    cam_feature_mask_t mPostProcMask;
    uint32_t mYUVDump;
    cam_is_type_t mIsType;
    uint32_t mNumBuffers;
    /* Enable unmapping of buffer before issuing buffer callback. Default value
     * for this flag is true and is selectively set to false for the usecases
     * such as HFR to avoid any performance hit due to mapping/unmapping */
    bool    mPerFrameMapUnmapEnable;
    uint32_t mFrmNum;
    uint32_t mDumpFrmCnt;
    uint32_t mSkipMode;
    uint32_t mDumpSkipCnt;
    uint8_t mNRMode;
    bool    mMapStreamBuffers; // Whether to mmap all stream buffers
};

/* QCamera3ProcessingChannel is used to handle all streams that are directly
 * generated by hardware and given to frameworks without any postprocessing at HAL.
 * It also handles input streams that require reprocessing by hardware and then
 * returned to frameworks. */
class QCamera3ProcessingChannel : public QCamera3Channel
{
public:
   QCamera3ProcessingChannel(uint32_t cam_handle,
           uint32_t channel_handle,
           mm_camera_ops_t *cam_ops,
           channel_cb_routine cb_routine,
           channel_cb_buffer_err cb_buffer_err,
           cam_padding_info_t *paddingInfo,
           void *userData,
           camera3_stream_t *stream,
           cam_stream_type_t stream_type,
           cam_feature_mask_t postprocess_mask,
           QCamera3Channel *metadataChannel,
           uint32_t numBuffers = MAX_INFLIGHT_REQUESTS);

    ~QCamera3ProcessingChannel();

    virtual int32_t initialize(cam_is_type_t isType);
    virtual int32_t request(buffer_handle_t *buffer,
            uint32_t frameNumber,
            camera3_stream_buffer_t* pInputBuffer,
            metadata_buffer_t* metadata, int &indexUsed,
            __unused bool internalRequest, __unused bool meteringOnly);
    virtual void streamCbRoutine(mm_camera_super_buf_t *super_frame,
            QCamera3Stream *stream);
    virtual QCamera3StreamMem *getStreamBufs(uint32_t len);
    virtual void putStreamBufs();
    virtual int32_t registerBuffer(buffer_handle_t *buffer, cam_is_type_t isType);
    // Register a buffer and get the buffer def for the registered buffer.
    virtual int32_t registerBufferAndGetBufDef(buffer_handle_t *buffer, mm_camera_buf_def_t *frame);
    // Unregister a buffer.
    virtual void unregisterBuffer(mm_camera_buf_def_t *frame);
    virtual int32_t stop();

    virtual reprocess_type_t getReprocessType() = 0;

    virtual void reprocessCbRoutine(buffer_handle_t *resultBuffer,
            uint32_t resultFrameNumber);

    int32_t queueReprocMetadata(mm_camera_super_buf_t *metadata);
    virtual int32_t metadataBufDone(mm_camera_super_buf_t *recvd_frame);
    int32_t translateStreamTypeAndFormat(camera3_stream_t *stream,
            cam_stream_type_t &streamType,
            cam_format_t &streamFormat);
    int32_t setReprocConfig(reprocess_config_t &reproc_cfg,
            camera3_stream_buffer_t *pInputBuffer,
            metadata_buffer_t *metadata,
            cam_format_t streamFormat, cam_dimension_t dim);
    int32_t setFwkInputPPData(qcamera_fwk_input_pp_data_t *src_frame,
            camera3_stream_buffer_t *pInputBuffer,
            reprocess_config_t *reproc_cfg,
            metadata_buffer_t *metadata,
            buffer_handle_t *output_buffer,
            uint32_t frameNumber);
    int32_t checkStreamCbErrors(mm_camera_super_buf_t *super_frame,
            QCamera3Stream *stream);
    int32_t getStreamSize(cam_dimension_t &dim);
    virtual int32_t timeoutFrame(uint32_t frameNumber);

    QCamera3PostProcessor m_postprocessor; // post processor
    void showDebugFPS(int32_t streamType);

    int32_t releaseOfflineMemory(uint32_t resultFrameNumber);
protected:
    uint8_t mDebugFPS;
    int mFrameCount;
    int mLastFrameCount;
    nsecs_t mLastFpsTime;
    bool isWNREnabled() {return m_bWNROn;};
    void startPostProc(const reprocess_config_t &reproc_cfg);
    void issueChannelCb(buffer_handle_t *resultBuffer,
            uint32_t resultFrameNumber);

    QCamera3StreamMem mMemory; //output buffer allocated by fwk
    camera3_stream_t *mCamera3Stream;
    uint32_t mNumBufs;
    cam_stream_type_t mStreamType;
    cam_format_t mStreamFormat;
    uint8_t mIntent;

    bool mPostProcStarted;
    reprocess_type_t mReprocessType; // Only valid when mPostProcStarted is true.
    bool mInputBufferConfig;   // Set when the processing channel is configured
                               // for processing input(framework) buffers

    QCamera3Channel *m_pMetaChannel;
    mm_camera_super_buf_t *mMetaFrame;
    QCamera3StreamMem mOfflineMemory;      //reprocessing input buffer
    QCamera3StreamMem mOfflineMetaMemory; //reprocessing metadata buffer
    List<uint32_t> mFreeOfflineMetaBuffersList;
    Mutex mFreeOfflineMetaBuffersLock;
    android::List<mm_camera_super_buf_t *> mOutOfSequenceBuffers;

private:

    bool m_bWNROn;
};

/* QCamera3RegularChannel is used to handle all streams that are directly
 * generated by hardware and given to frameworks without any postprocessing at HAL.
 * Examples are: all IMPLEMENTATION_DEFINED streams, CPU_READ streams. */
class QCamera3RegularChannel : public QCamera3ProcessingChannel
{
public:
    QCamera3RegularChannel(uint32_t cam_handle,
                    uint32_t channel_handle,
                    mm_camera_ops_t *cam_ops,
                    channel_cb_routine cb_routine,
                    channel_cb_buffer_err cb_buffer_err,
                    cam_padding_info_t *paddingInfo,
                    void *userData,
                    camera3_stream_t *stream,
                    cam_stream_type_t stream_type,
                    cam_feature_mask_t postprocess_mask,
                    QCamera3Channel *metadataChannel,
                    uint32_t numBuffers = MAX_INFLIGHT_REQUESTS);

    virtual ~QCamera3RegularChannel();

    virtual int32_t setBatchSize(uint32_t batchSize);
    virtual uint32_t getStreamTypeMask();
    virtual int32_t queueBatchBuf();
    virtual int32_t initialize(cam_is_type_t isType);
    using QCamera3ProcessingChannel::request;
    virtual int32_t request(buffer_handle_t *buffer, uint32_t frameNumber,
                    int &indexUsed);
    virtual reprocess_type_t getReprocessType();

private:
    int32_t initialize(struct private_handle_t *priv_handle);

    uint32_t mBatchSize;
    cam_rotation_t mRotation;
};

/* QCamera3MetadataChannel is for metadata stream generated by camera daemon. */
class QCamera3MetadataChannel : public QCamera3Channel
{
public:
    QCamera3MetadataChannel(uint32_t cam_handle,
                    uint32_t channel_handle,
                    mm_camera_ops_t *cam_ops,
                    channel_cb_routine cb_routine,
                    channel_cb_buffer_err cb_buffer_err,
                    cam_padding_info_t *paddingInfo,
                    cam_feature_mask_t postprocess_mask,
                    void *userData,
                    uint32_t numBuffers = MIN_STREAMING_BUFFER_NUM);
    virtual ~QCamera3MetadataChannel();

    virtual int32_t initialize(cam_is_type_t isType);

    virtual int32_t request(buffer_handle_t *buffer, uint32_t frameNumber,
                    int &indexUsed);
    virtual void streamCbRoutine(mm_camera_super_buf_t *super_frame,
                            QCamera3Stream *stream);

    virtual QCamera3StreamMem *getStreamBufs(uint32_t le);
    virtual void putStreamBufs();
    virtual int32_t registerBuffer(buffer_handle_t * /*buffer*/, cam_is_type_t /*isType*/)
            { return NO_ERROR; };
    virtual int32_t timeoutFrame(__unused uint32_t frameNumber) {return NO_ERROR; };

    void enableDepthData(bool enable) { mDepthDataPresent = enable; }

private:
    QCamera3StreamMem *mMemory;
    bool mDepthDataPresent;
};

/* QCamera3RawChannel is for opaqueu/cross-platform raw stream containing
 * vendor specific bayer data or 16-bit unpacked bayer data */
class QCamera3RawChannel : public QCamera3RegularChannel
{
public:
    QCamera3RawChannel(uint32_t cam_handle,
                    uint32_t channel_handle,
                    mm_camera_ops_t *cam_ops,
                    channel_cb_routine cb_routine,
                    channel_cb_buffer_err cb_buffer_err,
                    cam_padding_info_t *paddingInfo,
                    void *userData,
                    camera3_stream_t *stream,
                    cam_feature_mask_t postprocess_mask,
                    QCamera3Channel *metadataChannel,
                    bool raw_16 = false,
                    uint32_t numBuffers = MAX_INFLIGHT_REQUESTS);

    virtual ~QCamera3RawChannel();

    virtual int32_t initialize(cam_is_type_t isType);

    virtual void streamCbRoutine(mm_camera_super_buf_t *super_frame,
                            QCamera3Stream *stream);

    virtual reprocess_type_t getReprocessType();

private:
    bool mRawDump;
    bool mIsRaw16;

    void dumpRawSnapshot(mm_camera_buf_def_t *frame);
    void convertLegacyToRaw16(mm_camera_buf_def_t *frame);
    void convertMipiToRaw16(mm_camera_buf_def_t *frame);
};

/*
 * QCamera3RawDumpChannel is for internal use only for Raw dump
 */

class QCamera3RawDumpChannel : public QCamera3Channel
{
public:
    QCamera3RawDumpChannel(uint32_t cam_handle,
                    uint32_t channel_handle,
                    mm_camera_ops_t *cam_ops,
                    cam_dimension_t rawDumpSize,
                    cam_padding_info_t *paddingInfo,
                    void *userData,
                    cam_feature_mask_t postprocess_mask, uint32_t numBuffers = 3U);
    virtual ~QCamera3RawDumpChannel();
    virtual int32_t initialize(cam_is_type_t isType);
    virtual void streamCbRoutine(mm_camera_super_buf_t *super_frame,
                            QCamera3Stream *stream);
    virtual QCamera3StreamMem *getStreamBufs(uint32_t le);
    virtual void putStreamBufs();
    virtual int32_t registerBuffer(buffer_handle_t * /*buffer*/, cam_is_type_t /*isType*/)
            { return NO_ERROR; };
    virtual int32_t timeoutFrame(__unused uint32_t frameNumber) {return NO_ERROR;};
    virtual int32_t request(buffer_handle_t *buffer, uint32_t frameNumber,
            int &indexUsed);
    void dumpRawSnapshot(mm_camera_buf_def_t *frame);

public:
    cam_dimension_t mDim;

private:
    bool mRawDump;
    QCamera3StreamMem *mMemory;
};

/*
 * QCamera3HdrPlusRawSrcChannel is for internal use only for providing RAW input buffers to HDR+
 * client for prototyping Paintbox HDR+.
 */

class QCamera3HdrPlusRawSrcChannel : public QCamera3RawDumpChannel
{
public:
    QCamera3HdrPlusRawSrcChannel(
                    uint32_t cam_handle,
                    uint32_t channel_handle,
                    mm_camera_ops_t *cam_ops,
                    cam_dimension_t rawDumpSize,
                    cam_padding_info_t *paddingInfo,
                    void *userData,
                    cam_feature_mask_t postprocess_mask,
                    std::shared_ptr<HdrPlusClient> hdrPlusClient,
                    uint32_t hdrPlusStreamId,   // HDR+ stream ID for RAW input
                    uint32_t numBuffers = 3U);
    virtual ~QCamera3HdrPlusRawSrcChannel();

    virtual void streamCbRoutine(mm_camera_super_buf_t *super_frame,
                            QCamera3Stream *stream) override;

private:
    // Send a RAW frame to HDR+ service as HDR+ input buffers.
    void sendRawToHdrPlusService(mm_camera_buf_def_t *frame);
    std::shared_ptr<HdrPlusClient> mHdrPlusClient;
    // HDR+ stream ID.
    uint32_t mHdrPlusStreamId;

};

/* QCamera3YUVChannel is used to handle flexible YUV streams that are directly
 * generated by hardware and given to frameworks without any postprocessing at HAL.
 * It is also used to handle input buffers that generate YUV outputs */
class QCamera3YUVChannel : public QCamera3ProcessingChannel
{
public:
    QCamera3YUVChannel(uint32_t cam_handle,
            uint32_t channel_handle,
            mm_camera_ops_t *cam_ops,
            channel_cb_routine cb_routine,
            channel_cb_buffer_err cb_buffer_err,
            cam_padding_info_t *paddingInfo,
            void *userData,
            camera3_stream_t *stream,
            cam_stream_type_t stream_type,
            cam_feature_mask_t postprocess_mask,
            QCamera3Channel *metadataChannel);
    ~QCamera3YUVChannel();
    virtual int32_t initialize(cam_is_type_t isType);
    using QCamera3ProcessingChannel::request;
    virtual int32_t request(buffer_handle_t *buffer,
            uint32_t frameNumber,
            camera3_stream_buffer_t* pInputBuffer,
            metadata_buffer_t* metadata, bool &needMetadata,
            int &indexUsed, bool internalRequest, bool meteringOnly);
    virtual reprocess_type_t getReprocessType();
    virtual void streamCbRoutine(mm_camera_super_buf_t *super_frame,
            QCamera3Stream *stream);
    virtual void putStreamBufs();
    virtual void reprocessCbRoutine(buffer_handle_t *resultBuffer,
        uint32_t resultFrameNumber);

private:
    typedef struct {
        uint32_t frameNumber;
        bool offlinePpFlag;
        buffer_handle_t *output;
        mm_camera_super_buf_t *callback_buffer;
    } PpInfo;

    // Whether offline postprocessing is required for this channel
    bool mBypass;
    uint32_t mFrameLen;

    // Current edge, noise, and crop region setting
    cam_edge_application_t mEdgeMode;
    uint32_t mNoiseRedMode;
    cam_crop_region_t mCropRegion;

    // Mutex to protect mOfflinePpFlagMap and mFreeHeapBufferList
    Mutex mOfflinePpLock;
    // Map between free number and whether the request needs to be
    // postprocessed.
    List<PpInfo> mOfflinePpInfoList;
    // Heap buffer index list
    List<uint32_t> mFreeHeapBufferList;

private:
    bool needsFramePostprocessing(metadata_buffer_t* meta);
    int32_t handleOfflinePpCallback(uint32_t resultFrameNumber,
            Vector<mm_camera_super_buf_t *>& pendingCbs);
    mm_camera_super_buf_t* getNextPendingCbBuffer();
};

/* QCamera3PicChannel is for JPEG stream, which contains a YUV stream generated
 * by the hardware, and encoded to a JPEG stream */
class QCamera3PicChannel : public QCamera3ProcessingChannel
{
public:
    QCamera3PicChannel(uint32_t cam_handle,
            uint32_t channel_handle,
            mm_camera_ops_t *cam_ops,
            channel_cb_routine cb_routine,
            channel_cb_buffer_err cb_buffer_err,
            cam_padding_info_t *paddingInfo,
            void *userData,
            camera3_stream_t *stream,
            cam_feature_mask_t postprocess_mask,
            bool is4KVideo,
            bool isInputStreamConfigured,
            QCamera3Channel *metadataChannel,
            uint32_t numBuffers = MAX_INFLIGHT_REQUESTS);
    ~QCamera3PicChannel();

    virtual int32_t initialize(cam_is_type_t isType);
    virtual int32_t flush();
    virtual int32_t request(buffer_handle_t *buffer,
            uint32_t frameNumber,
            camera3_stream_buffer_t* pInputBuffer,
            metadata_buffer_t* metadata,
            int &indexUsed, bool internalRequest, bool meteringOnly);
    virtual void streamCbRoutine(mm_camera_super_buf_t *super_frame,
            QCamera3Stream *stream);

    virtual QCamera3StreamMem *getStreamBufs(uint32_t le);
    virtual void putStreamBufs();
    virtual reprocess_type_t getReprocessType();
    virtual int32_t timeoutFrame(uint32_t frameNumber);

    QCamera3Exif *getExifData(metadata_buffer_t *metadata,
            jpeg_settings_t *jpeg_settings);
    void overrideYuvSize(uint32_t width, uint32_t height);
    static void jpegEvtHandle(jpeg_job_status_t status,
            uint32_t /*client_hdl*/,
            uint32_t jobId,
            mm_jpeg_output_t *p_output,
            void *userdata);
    static void dataNotifyCB(mm_camera_super_buf_t *recvd_frame,
            void *userdata);

    // Reprocessing is done with the metadata. This function frees the metadata or returns the
    // metadata to the metadata channel.
    int32_t metadataBufDone(mm_camera_super_buf_t *recvd_frame) override;

    // Get a YUV buffer for a request from camera service.
    int32_t getYuvBufferForRequest(mm_camera_buf_def_t *frame, uint32_t frameNumber);

    // Return a YUV buffer (from getYuvBufferForRequest) and request jpeg encoding.
    int32_t returnYuvBufferAndEncode(mm_camera_buf_def_t *frame,
            buffer_handle_t *outBuffer, uint32_t frameNumber,
            std::shared_ptr<metadata_buffer_t> metadata);

    // Return a YUV buffer (from getYuvBufferForRequest) without requesting jpeg encoding.
    int32_t returnYuvBuffer(mm_camera_buf_def_t *frame);

private:
    int32_t queueJpegSetting(uint32_t out_buf_index, metadata_buffer_t *metadata);

public:
    cam_dimension_t m_max_pic_dim;

private:
    uint32_t mNumSnapshotBufs;
    uint32_t mYuvWidth, mYuvHeight;
    int32_t mCurrentBufIndex;
    bool mInputBufferHint;
    QCamera3StreamMem *mYuvMemory;
    // Keep a list of free buffers
    Mutex mFreeBuffersLock;
    List<uint32_t> mFreeBufferList;
    uint32_t mFrameLen;

    // The metadata passed in via returnYuvBufferAndEncode and is allocated externally.
    // These should not be returned to metadata channel.
    Mutex mPendingExternalMetadataLock;
    std::vector<std::shared_ptr<metadata_buffer_t>> mPendingExternalMetadata;
};

// reprocess channel class
class QCamera3ReprocessChannel : public QCamera3Channel
{
public:
    QCamera3ReprocessChannel(uint32_t cam_handle,
                            uint32_t channel_handle,
                            mm_camera_ops_t *cam_ops,
                            channel_cb_routine cb_routine,
                            channel_cb_buffer_err cb_buffer_err,
                            cam_padding_info_t *paddingInfo,
                            cam_feature_mask_t postprocess_mask,
                            void *userData, void *ch_hdl);
    QCamera3ReprocessChannel();
    virtual ~QCamera3ReprocessChannel();
    // offline reprocess
    virtual int32_t start();
    virtual int32_t stop();
    int32_t doReprocessOffline(qcamera_fwk_input_pp_data_t *frame,
            bool isPriorityFrame = false);
    int32_t doReprocess(int buf_fd,void *buffer, size_t buf_length, int32_t &ret_val,
                        mm_camera_super_buf_t *meta_buf);
    int32_t overrideMetadata(qcamera_hal3_pp_buffer_t *pp_buffer,
            mm_camera_buf_def_t *meta_buffer,
            jpeg_settings_t *jpeg_settings,
            qcamera_fwk_input_pp_data_t &fwk_frame);
    int32_t overrideFwkMetadata(qcamera_fwk_input_pp_data_t *frame);
    virtual QCamera3StreamMem *getStreamBufs(uint32_t len);
    virtual void putStreamBufs();
    virtual int32_t initialize(cam_is_type_t isType);
    int32_t unmapOfflineBuffers(bool all);
    int32_t bufDone(mm_camera_super_buf_t *recvd_frame);
    virtual void streamCbRoutine(mm_camera_super_buf_t *super_frame,
                            QCamera3Stream *stream);
    static void dataNotifyCB(mm_camera_super_buf_t *recvd_frame,
                                       void* userdata);
    int32_t addReprocStreamsFromSource(cam_pp_feature_config_t &pp_config,
           const reprocess_config_t &src_config,
           cam_is_type_t is_type,
           QCamera3Channel *pMetaChannel);
    QCamera3Stream *getStreamBySrcHandle(uint32_t srcHandle);
    QCamera3Stream *getSrcStreamBySrcHandle(uint32_t srcHandle);
    virtual int32_t registerBuffer(buffer_handle_t * buffer, cam_is_type_t isType);
    virtual int32_t timeoutFrame(__unused uint32_t frameNumber) {return NO_ERROR;};

public:
    void *inputChHandle;

private:
    typedef struct {
        QCamera3Stream *stream;
        cam_mapping_buf_type type;
        uint32_t index;
    } OfflineBuffer;

    int32_t resetToCamPerfNormal(uint32_t frameNumber);
    Mutex mOfflineBuffersLock; // Lock for offline buffers
    android::List<OfflineBuffer> mOfflineBuffers;
    android::List<OfflineBuffer> mOfflineMetaBuffers;
    int32_t mOfflineBuffersIndex;
    int32_t mOfflineMetaIndex;
    uint32_t mFrameLen;
    Mutex mFreeBuffersLock; // Lock for free heap buffers
    List<int32_t> mFreeBufferList; // Free heap buffers list
    reprocess_type_t mReprocessType;
    uint32_t mSrcStreamHandles[MAX_STREAM_NUM_IN_BUNDLE];
    QCamera3ProcessingChannel *m_pSrcChannel; // ptr to source channel for reprocess
    QCamera3Channel *m_pMetaChannel;
    QCamera3StreamMem *mMemory;
    QCamera3StreamMem mGrallocMemory;
    Vector<uint32_t> mPriorityFrames;
    Mutex            mPriorityFramesLock;
    bool             mReprocessPerfMode;
};


/* QCamera3SupportChannel is for HAL internal consumption only */
class QCamera3SupportChannel : public QCamera3Channel
{
public:
    QCamera3SupportChannel(uint32_t cam_handle,
                    uint32_t channel_handle,
                    mm_camera_ops_t *cam_ops,
                    cam_padding_info_t *paddingInfo,
                    cam_feature_mask_t postprocess_mask,
                    cam_stream_type_t streamType,
                    cam_dimension_t *dim,
                    cam_format_t streamFormat,
                    uint8_t hw_analysis_supported,
                    cam_color_filter_arrangement_t color_arrangement,
                    void *userData,
                    uint32_t numBuffers = MIN_STREAMING_BUFFER_NUM
                    );
    virtual ~QCamera3SupportChannel();

    virtual int32_t initialize(cam_is_type_t isType);

    virtual int32_t request(buffer_handle_t *buffer, uint32_t frameNumber,
                    int &indexUsed);
    virtual void streamCbRoutine(mm_camera_super_buf_t *super_frame,
                            QCamera3Stream *stream);

    virtual QCamera3StreamMem *getStreamBufs(uint32_t le);
    virtual void putStreamBufs();
    virtual int32_t registerBuffer(buffer_handle_t * /*buffer*/, cam_is_type_t /*isType*/)
            { return NO_ERROR; };
    virtual int32_t timeoutFrame(__unused uint32_t frameNumber) {return NO_ERROR;};

    static cam_dimension_t kDim;
private:
    QCamera3StreamMem *mMemory;
    cam_dimension_t mDim;
    cam_stream_type_t mStreamType;
    cam_format_t mStreamFormat;
    uint8_t mHwAnalysisSupported;
    cam_color_filter_arrangement_t mColorArrangement;
};

class QCamera3DepthChannel : public QCamera3ProcessingChannel {
public:

    QCamera3DepthChannel(QCamera3DepthChannel const&) = delete;
    QCamera3DepthChannel& operator=(QCamera3DepthChannel const&) = delete;

    QCamera3DepthChannel(uint32_t cam_handle,
            uint32_t channel_handle,
            mm_camera_ops_t *cam_ops,
            channel_cb_routine cb_routine,
            channel_cb_buffer_err cb_buf_err,
            cam_padding_info_t *paddingInfo,
            cam_feature_mask_t postprocess_mask,
            void *userData, uint32_t numBuffers,
            camera3_stream_t *stream,
            QCamera3Channel *metadataChannel) :
                QCamera3ProcessingChannel(cam_handle, channel_handle, cam_ops,
                        cb_routine, cb_buf_err, paddingInfo, userData, stream,
                        CAM_STREAM_TYPE_DEFAULT, postprocess_mask,
                        metadataChannel, numBuffers),
                        mStream(stream), mGrallocMem(0) {}
    ~QCamera3DepthChannel();

    int32_t mapBuffer(buffer_handle_t *buffer, uint32_t frameNumber);
    int32_t populateDepthData(const cam_depth_data_t &data,
            uint32_t frameNumber);
    buffer_handle_t *getOldestFrame(uint32_t &frameNumber);
    int32_t unmapBuffer(uint32_t frameNumber);
    int32_t unmapAllBuffers();
    camera3_stream_t *getStream() { return mStream; }

    int32_t start() override { return NO_ERROR; };
    int32_t stop() override { return NO_ERROR; };
    int32_t setBatchSize(uint32_t) override { return NO_ERROR; }
    int32_t queueBatchBuf() override { return NO_ERROR; }
    int32_t setPerFrameMapUnmap(bool) override { return NO_ERROR; }
    uint32_t getStreamTypeMask() override { return 0; };
    int32_t initialize(cam_is_type_t) { return NO_ERROR; }
    void streamCbRoutine(mm_camera_super_buf_t *, QCamera3Stream *) override {}
    int32_t registerBuffer(buffer_handle_t *, cam_is_type_t) override
            { return NO_ERROR; }
    QCamera3StreamMem *getStreamBufs(uint32_t) override { return NULL; }
    void putStreamBufs() override {}
    int32_t timeoutFrame(uint32_t) override { return NO_ERROR; }
    int32_t flush() override { return NO_ERROR; }
    reprocess_type_t getReprocessType() override { return REPROCESS_TYPE_NONE; }
    int32_t setBundleInfo(const cam_bundle_config_t &) override
            { return NO_ERROR; }

private:
    camera3_stream_t *mStream;
    QCamera3GrallocMemory mGrallocMem;
};

}; // namespace qcamera

#endif /* __QCAMERA_CHANNEL_H__ */
