/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <stdio.h>
#include <stdlib.h>
#include <error.h>
#include <errno.h>
#include <memory.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <cutils/log.h>

#include "assert.h"

#include "VideoCapture.h"


// NOTE:  This developmental code does not properly clean up resources in case of failure
//        during the resource setup phase.  Of particular note is the potential to leak
//        the file descriptor.  This must be fixed before using this code for anything but
//        experimentation.
bool VideoCapture::open(const char* deviceName) {
    // If we want a polling interface for getting frames, we would use O_NONBLOCK
//    int mDeviceFd = open(deviceName, O_RDWR | O_NONBLOCK, 0);
    mDeviceFd = ::open(deviceName, O_RDWR, 0);
    if (mDeviceFd < 0) {
        ALOGE("failed to open device %s (%d = %s)", deviceName, errno, strerror(errno));
        return false;
    }

    v4l2_capability caps;
    {
        int result = ioctl(mDeviceFd, VIDIOC_QUERYCAP, &caps);
        if (result  < 0) {
            ALOGE("failed to get device caps for %s (%d = %s)", deviceName, errno, strerror(errno));
            return false;
        }
    }

    // Report device properties
    ALOGI("Open Device: %s (fd=%d)", deviceName, mDeviceFd);
    ALOGI("  Driver: %s", caps.driver);
    ALOGI("  Card: %s", caps.card);
    ALOGI("  Version: %u.%u.%u",
            (caps.version >> 16) & 0xFF,
            (caps.version >> 8)  & 0xFF,
            (caps.version)       & 0xFF);
    ALOGI("  All Caps: %08X", caps.capabilities);
    ALOGI("  Dev Caps: %08X", caps.device_caps);

    // Enumerate the available capture formats (if any)
    ALOGI("Supported capture formats:");
    v4l2_fmtdesc formatDescriptions;
    formatDescriptions.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    for (int i=0; true; i++) {
        formatDescriptions.index = i;
        if (ioctl(mDeviceFd, VIDIOC_ENUM_FMT, &formatDescriptions) == 0) {
            ALOGI("  %2d: %s 0x%08X 0x%X",
                   i,
                   formatDescriptions.description,
                   formatDescriptions.pixelformat,
                   formatDescriptions.flags
            );
        } else {
            // No more formats available
            break;
        }
    }

    // Verify we can use this device for video capture
    if (!(caps.capabilities & V4L2_CAP_VIDEO_CAPTURE) ||
        !(caps.capabilities & V4L2_CAP_STREAMING)) {
        // Can't do streaming capture.
        ALOGE("Streaming capture not supported by %s.", deviceName);
        return false;
    }

    // Set our desired output format
    v4l2_format format;
    format.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    format.fmt.pix.pixelformat = V4L2_PIX_FMT_UYVY; // Could/should we request V4L2_PIX_FMT_NV21?
    format.fmt.pix.width = 720;                     // TODO:  Can we avoid hard coding dimensions?
    format.fmt.pix.height = 240;                    // For now, this works with available hardware
    format.fmt.pix.field = V4L2_FIELD_ALTERNATE;    // TODO:  Do we need to specify this?
    ALOGI("Requesting format %c%c%c%c (0x%08X)",
          ((char*)&format.fmt.pix.pixelformat)[0],
          ((char*)&format.fmt.pix.pixelformat)[1],
          ((char*)&format.fmt.pix.pixelformat)[2],
          ((char*)&format.fmt.pix.pixelformat)[3],
          format.fmt.pix.pixelformat);
    if (ioctl(mDeviceFd, VIDIOC_S_FMT, &format) < 0) {
        ALOGE("VIDIOC_S_FMT: %s", strerror(errno));
    }

    // Report the current output format
    format.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    if (ioctl(mDeviceFd, VIDIOC_G_FMT, &format) == 0) {

        mFormat = format.fmt.pix.pixelformat;
        mWidth  = format.fmt.pix.width;
        mHeight = format.fmt.pix.height;
        mStride = format.fmt.pix.bytesperline;

        ALOGI("Current output format:  fmt=0x%X, %dx%d, pitch=%d",
               format.fmt.pix.pixelformat,
               format.fmt.pix.width,
               format.fmt.pix.height,
               format.fmt.pix.bytesperline
        );
    } else {
        ALOGE("VIDIOC_G_FMT: %s", strerror(errno));
        return false;
    }

    // Make sure we're initialized to the STOPPED state
    mRunMode = STOPPED;
    mFrameReady = false;

    // Ready to go!
    return true;
}


void VideoCapture::close() {
    ALOGD("VideoCapture::close");
    // Stream should be stopped first!
    assert(mRunMode == STOPPED);

    if (isOpen()) {
        ALOGD("closing video device file handled %d", mDeviceFd);
        ::close(mDeviceFd);
        mDeviceFd = -1;
    }
}


bool VideoCapture::startStream(std::function<void(VideoCapture*, imageBuffer*, void*)> callback) {
    // Set the state of our background thread
    int prevRunMode = mRunMode.fetch_or(RUN);
    if (prevRunMode & RUN) {
        // The background thread is already running, so we can't start a new stream
        ALOGE("Already in RUN state, so we can't start a new streaming thread");
        return false;
    }

    // Tell the L4V2 driver to prepare our streaming buffers
    v4l2_requestbuffers bufrequest;
    bufrequest.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    bufrequest.memory = V4L2_MEMORY_MMAP;
    bufrequest.count = 1;
    if (ioctl(mDeviceFd, VIDIOC_REQBUFS, &bufrequest) < 0) {
        ALOGE("VIDIOC_REQBUFS: %s", strerror(errno));
        return false;
    }

    // Get the information on the buffer that was created for us
    memset(&mBufferInfo, 0, sizeof(mBufferInfo));
    mBufferInfo.type     = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    mBufferInfo.memory   = V4L2_MEMORY_MMAP;
    mBufferInfo.index    = 0;
    if (ioctl(mDeviceFd, VIDIOC_QUERYBUF, &mBufferInfo) < 0) {
        ALOGE("VIDIOC_QUERYBUF: %s", strerror(errno));
        return false;
    }

    ALOGI("Buffer description:");
    ALOGI("  offset: %d", mBufferInfo.m.offset);
    ALOGI("  length: %d", mBufferInfo.length);

    // Get a pointer to the buffer contents by mapping into our address space
    mPixelBuffer = mmap(
            NULL,
            mBufferInfo.length,
            PROT_READ | PROT_WRITE,
            MAP_SHARED,
            mDeviceFd,
            mBufferInfo.m.offset
    );
    if( mPixelBuffer == MAP_FAILED) {
        ALOGE("mmap: %s", strerror(errno));
        return false;
    }
    memset(mPixelBuffer, 0, mBufferInfo.length);
    ALOGI("Buffer mapped at %p", mPixelBuffer);

    // Queue the first capture buffer
    if (ioctl(mDeviceFd, VIDIOC_QBUF, &mBufferInfo) < 0) {
        ALOGE("VIDIOC_QBUF: %s", strerror(errno));
        return false;
    }

    // Start the video stream
    int type = mBufferInfo.type;
    if (ioctl(mDeviceFd, VIDIOC_STREAMON, &type) < 0) {
        ALOGE("VIDIOC_STREAMON: %s", strerror(errno));
        return false;
    }

    // Remember who to tell about new frames as they arrive
    mCallback = callback;

    // Fire up a thread to receive and dispatch the video frames
    mCaptureThread = std::thread([this](){ collectFrames(); });

    ALOGD("Stream started.");
    return true;
}


void VideoCapture::stopStream() {
    // Tell the background thread to stop
    int prevRunMode = mRunMode.fetch_or(STOPPING);
    if (prevRunMode == STOPPED) {
        // The background thread wasn't running, so set the flag back to STOPPED
        mRunMode = STOPPED;
    } else if (prevRunMode & STOPPING) {
        ALOGE("stopStream called while stream is already stopping.  Reentrancy is not supported!");
        return;
    } else {
        // Block until the background thread is stopped
        if (mCaptureThread.joinable()) {
            mCaptureThread.join();
        }

        // Stop the underlying video stream (automatically empties the buffer queue)
        int type = mBufferInfo.type;
        if (ioctl(mDeviceFd, VIDIOC_STREAMOFF, &type) < 0) {
            ALOGE("VIDIOC_STREAMOFF: %s", strerror(errno));
        }

        ALOGD("Capture thread stopped.");
    }

    // Unmap the buffers we allocated
    munmap(mPixelBuffer, mBufferInfo.length);

    // Tell the L4V2 driver to release our streaming buffers
    v4l2_requestbuffers bufrequest;
    bufrequest.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    bufrequest.memory = V4L2_MEMORY_MMAP;
    bufrequest.count = 0;
    ioctl(mDeviceFd, VIDIOC_REQBUFS, &bufrequest);

    // Drop our reference to the frame delivery callback interface
    mCallback = nullptr;
}


void VideoCapture::markFrameReady() {
    mFrameReady = true;
};


bool VideoCapture::returnFrame() {
    // We're giving the frame back to the system, so clear the "ready" flag
    mFrameReady = false;

    // Requeue the buffer to capture the next available frame
    if (ioctl(mDeviceFd, VIDIOC_QBUF, &mBufferInfo) < 0) {
        ALOGE("VIDIOC_QBUF: %s", strerror(errno));
        return false;
    }

    return true;
}


// This runs on a background thread to receive and dispatch video frames
void VideoCapture::collectFrames() {
    // Run until our atomic signal is cleared
    while (mRunMode == RUN) {
        // Wait for a buffer to be ready
        if (ioctl(mDeviceFd, VIDIOC_DQBUF, &mBufferInfo) < 0) {
            ALOGE("VIDIOC_DQBUF: %s", strerror(errno));
            break;
        }

        markFrameReady();

        // If a callback was requested per frame, do that now
        if (mCallback) {
            mCallback(this, &mBufferInfo, mPixelBuffer);
        }
    }

    // Mark ourselves stopped
    ALOGD("VideoCapture thread ending");
    mRunMode = STOPPED;
}
