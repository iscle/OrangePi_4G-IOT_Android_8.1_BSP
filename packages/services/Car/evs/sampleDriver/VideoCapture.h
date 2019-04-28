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
#include <atomic>
#include <thread>
#include <functional>
#include <linux/videodev2.h>


typedef v4l2_buffer imageBuffer;


class VideoCapture {
public:
    bool open(const char* deviceName);
    void close();

    bool startStream(std::function<void(VideoCapture*, imageBuffer*, void*)> callback = nullptr);
    void stopStream();

    // Valid only after open()
    __u32   getWidth()          { return mWidth; };
    __u32   getHeight()         { return mHeight; };
    __u32   getStride()         { return mStride; };
    __u32   getV4LFormat()      { return mFormat; };

    // NULL until stream is started
    void* getLatestData()       { return mPixelBuffer; };

    bool isFrameReady()         { return mFrameReady; };
    void markFrameConsumed()    { returnFrame(); };

    bool isOpen()               { return mDeviceFd >= 0; };

private:
    void collectFrames();
    void markFrameReady();
    bool returnFrame();

    int mDeviceFd = -1;

    v4l2_buffer mBufferInfo = {};
    void* mPixelBuffer = nullptr;

    __u32   mFormat = 0;
    __u32   mWidth  = 0;
    __u32   mHeight = 0;
    __u32   mStride = 0;

    std::function<void(VideoCapture*, imageBuffer*, void*)> mCallback;

    std::thread mCaptureThread;             // The thread we'll use to dispatch frames
    std::atomic<int> mRunMode;              // Used to signal the frame loop (see RunModes below)
    std::atomic<bool> mFrameReady;          // Set when a frame has been delivered

    // Careful changing these -- we're using bit-wise ops to manipulate these
    enum RunModes {
        STOPPED     = 0,
        RUN         = 1,
        STOPPING    = 2,
    };
};

