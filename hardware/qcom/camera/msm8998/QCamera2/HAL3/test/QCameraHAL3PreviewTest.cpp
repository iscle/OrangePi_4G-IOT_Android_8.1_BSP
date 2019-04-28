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

#include "QCameraHAL3PreviewTest.h"
#include "QCameraHAL3MainTestContext.h"

namespace qcamera {
extern hal3_camera_lib_test *CamObj_handle;
int req_sent;
extern pthread_cond_t mRequestAppCond;
int test_case_end;
bool thread_exit;
extern std::list<uint32_t> PreviewQueue;
int preview_buffer_allocated;
extern pthread_mutex_t TestAppLock, mCaptureRequestLock;
int snapshot_buffer = -1;


QCameraHAL3PreviewTest::QCameraHAL3PreviewTest(int camid) :
    QCameraHAL3Test(0),
    mPreviewHandle(NULL),
    mCaptureHandle(NULL),
    mPreviewStream(NULL),
    nobuffer(0),
    mCamId(camid),
    ir_mode(0),
    svhdr_mode(0)
{

}

void QCameraHAL3PreviewTest::initTest(hal3_camera_lib_test *handle,
                                int testcase, int camid, int w, int h)
{
    int i;
    CamObj_handle = handle; thread_exit = 0; test_case_end = 0;
    LOGD("\n buffer thread created for testcase %d  %d and %d ",testcase, w, h);
    configurePreviewStream(&(handle->test_obj) , camid, w, h);
    LOGD("\n preview stream configured");
    constructDefaultRequest(&(handle->test_obj), camid);
    LOGD("Default stream setting read ");
    printf("\npipeline_depth is %d", mPipelineDepthPreview);
    mPreviewHandle = new native_handle_t *[mPipelineDepthPreview];
    for (i = 0; i < mPipelineDepthPreview; i++)
        mPreviewHandle[i] = new native_handle_t;
    for (i = 0, req_sent = 1; i < mPipelineDepthPreview; i++, req_sent++) {
        previewAllocateBuffers(width, height, i);
        PreviewQueue.push_back(i);
    }
    LOGD(" Request Number is preview : %d ",mRequest.frame_number);
    mRequest.frame_number = 0;
    previewProcessThreadCreate(handle);
}

void QCameraHAL3PreviewTest::snapshotCaptureRequest(hal3_camera_lib_test *handle,
        int testcase, int camid, int w, int h)
{
    LOGD("Requested Capture Sizes for testcase:%d are :%d  X %d",testcase, w, h);
    captureRequestRepeat(handle, camid, MENU_START_CAPTURE);
    pthread_mutex_unlock(&mCaptureRequestLock);
}

void QCameraHAL3PreviewTest::configurePreviewStream(hal3_camera_test_obj_t *my_test_obj,
                                int camid, int w, int h)
{
    camera3_device_t *device_handle = my_test_obj->device;
    mPreviewStream =  new camera3_stream_t;
    memset(mPreviewStream, 0, sizeof(camera3_stream_t));
    mPreviewStream = initStream(CAMERA3_STREAM_OUTPUT, camid, w, h, 0,
            HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED, HAL3_DATASPACE_UNKNOWN);

    mPreviewConfig = configureStream(CAMERA3_STREAM_CONFIGURATION_NORMAL_MODE, 1);
    mPreviewConfig.streams[0] = mPreviewStream;
    device_handle->ops->configure_streams(my_test_obj->device, &(mPreviewConfig));
    mPipelineDepthPreview = mPreviewConfig.streams[0]->max_buffers;
    preview_buffer_allocated = mPipelineDepthPreview;
}

void QCameraHAL3PreviewTest::constructDefaultRequest(
        hal3_camera_test_obj_t *my_test_obj, int camid)
{
    camera3_device_t *device_handle = my_test_obj->device;
    LOGD("Camera ID : %d",camid);
    mMetaDataPtr[0]= device_handle->ops->construct_default_request_settings(my_test_obj->device,
            CAMERA3_TEMPLATE_PREVIEW);
    mMetaDataPtr[1] = device_handle->ops->construct_default_request_settings(my_test_obj->device,
            CAMERA3_TEMPLATE_STILL_CAPTURE);
}

void QCameraHAL3PreviewTest::captureRequestRepeat(
        hal3_camera_lib_test *my_hal3test_obj, int camid, int testcase)
{
    int num1, num2;
    int ir_mode_changed;
    int svhdr_mode_changed;
    int32_t set_svhdr_mode;
    int32_t set_ir_mode;
    hal3_camera_test_obj_t *my_test_obj = &(my_hal3test_obj->test_obj);
    camera3_device_t *device_handle = my_test_obj->device;

    if (testcase == MENU_START_PREVIEW) {
        if (PreviewQueue.empty()) {
            LOGE("no preview buffer for CamID : %d", camid);
        }
        else {
            if (test_case_end == 0) {
                LOGD(" Request Number is preview : %d ",mRequest.frame_number);
                pthread_mutex_lock(&mCaptureRequestLock);
                num2 = PreviewQueue.front();
                PreviewQueue.pop_front();
                num1 = mRequest.frame_number;
                ir_mode_changed = get_ir_mode(ir_mode);
                svhdr_mode_changed = get_svhdr_mode(svhdr_mode);
                ALOGE("setting IR mode :%d",ir_mode_changed);
                ALOGE("setting SVHDR mode :%d",svhdr_mode_changed);
                if (num1 < 1) {
                    (mRequest).settings = mMetaDataPtr[0];
                }
                else if(ir_mode_changed == 1) {
                    hal3app_preview_settings = mMetaDataPtr[0];
                    if(ir_mode == 0) {
                        ALOGE("setting IR mode off");
                        set_ir_mode = (int32_t)QCAMERA3_IR_MODE_OFF;
                    }
                    else {
                        ALOGE("setting IR mode On");
                        set_ir_mode = (int32_t)QCAMERA3_IR_MODE_ON;
                    }
                    hal3app_preview_settings.update(QCAMERA3_IR_MODE, &set_ir_mode, 1);
                    (mRequest).settings = hal3app_preview_settings.release();
                }
                else if(svhdr_mode_changed == 1) {
                    hal3app_preview_settings = mMetaDataPtr[0];
                    if(svhdr_mode == 0) {
                        ALOGE("setting HDR mode off");
                        set_svhdr_mode = (int32_t)QCAMERA3_VIDEO_HDR_MODE_OFF;
                    }
                    else {
                        ALOGE("setting HDR mode On");
                        set_svhdr_mode = (int32_t)QCAMERA3_VIDEO_HDR_MODE_ON;
                    }
                    hal3app_preview_settings.update(QCAMERA3_VIDEO_HDR_MODE, &set_svhdr_mode, 1);
                    (mRequest).settings = hal3app_preview_settings.release();
                }
                else {
                    (mRequest).settings = NULL;
                }
                (mRequest).input_buffer = NULL;
                (mRequest).num_output_buffers = 1;
                mPreviewStreamBuffs.stream = mPreviewStream;
                mPreviewStreamBuffs.status = 0;
                mPreviewStreamBuffs.buffer =
                        (const native_handle_t**)&mPreviewHandle[num2];
                mPreviewStreamBuffs.release_fence = -1;
                mPreviewStreamBuffs.acquire_fence = -1;
                (mRequest).output_buffers = &(mPreviewStreamBuffs);
                LOGD("Calling HAL3APP repeat capture request %d and %d and free buffer :%d "
                        , num1, num2, PreviewQueue.size());

                device_handle->ops->process_capture_request(my_test_obj->device, &(mRequest));
                (mRequest.frame_number)++;
                pthread_mutex_unlock(&mCaptureRequestLock);
            }
        }
    }
    else {
        snapshot_buffer = mRequest.frame_number;
        (mRequest).settings = mMetaDataPtr[1];
        mSnapshotStreamBuffs = hal3appGetStreamBuffs(mSnapshotStream);
        mSnapshotStreamBuffs.buffer = (const native_handle_t**)&mCaptureHandle;
        mRequest = hal3appGetRequestSettings(&mSnapshotStreamBuffs, 1);
        LOGD("Calling snap HAL3APP repeat capture request repeat %d  ", snapshot_buffer);
        device_handle->ops->process_capture_request(my_test_obj->device, &(mRequest));
        (mRequest.frame_number)++;
    }
}

void QCameraHAL3PreviewTest::previewTestEnd(
        hal3_camera_lib_test *my_hal3test_obj, int camid)
{
    test_case_end = 1;
    hal3_camera_test_obj_t *my_test_obj = &(my_hal3test_obj->test_obj);
    camera3_device_t *device_handle = my_test_obj->device;
    device_handle->ops->flush(my_test_obj->device);
    LOGD("%s Closing Camera %d", __func__, camid);
    ioctl(mPreviewMeminfo.ion_fd, ION_IOC_FREE, &mPreviewMeminfo.ion_handle);
    close(mPreviewMeminfo.ion_fd);
    mPreviewMeminfo.ion_fd = -1;
    LOGD("%s Closing thread", __func__);
    thread_exit = 1;
}

void QCameraHAL3PreviewTest::previewAllocateBuffers(int width, int height, int num)
{
    mPreviewHandle[num] = allocateBuffers(width, height, &mPreviewMeminfo);
}

void QCameraHAL3PreviewTest::snapshotAllocateBuffers(int width, int height)
{
    mCaptureHandle = allocateBuffers(width, height, &mCaptureMemInfo);
}

bool QCameraHAL3PreviewTest::previewProcessThreadCreate(
        hal3_camera_lib_test *handle)
{
    if(handle == NULL) {
        LOGD("Camera Hanle is NULL");
    }
    processThreadCreate(this, MENU_START_PREVIEW);
    return 1;
}

int QCameraHAL3PreviewTest::get_ir_mode(int ir_mode)
{
    static int prev_irmode;
    if(ir_mode == prev_irmode)
        return 0;
    else {
        prev_irmode = ir_mode;
        return 1;
    }
}

int QCameraHAL3PreviewTest::get_svhdr_mode(int hdr_mode)
{
    static int prev_hdrmode;
    if(hdr_mode == prev_hdrmode)
        return 0;
    else {
        prev_hdrmode = hdr_mode;
        return 1;
    }
}

QCameraHAL3PreviewTest::~QCameraHAL3PreviewTest()
{

}

}
