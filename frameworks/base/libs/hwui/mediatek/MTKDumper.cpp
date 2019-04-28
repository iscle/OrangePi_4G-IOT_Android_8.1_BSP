/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

#include "MTKDumper.h"

#include <GLES3/gl3.h>
#include <SkCanvas.h>
#include <SkImageEncoder.h>
#include <sk_tool_utils.h>
#include <utils/Condition.h>
#include <utils/String8.h>
#include <utils/Thread.h>
#include <utils/Trace.h>


namespace android {

using namespace uirenderer;
ANDROID_SINGLETON_STATIC_INSTANCE(Dumper);

namespace uirenderer {

#define MAX_BUFFER_SIZE 64 * 1048576 // max 64MB for all threads
#define TARGET_SIZE 102480.f // 240 * 427

///////////////////////////////////////////////////////////////////////////////
// DumpBarrier
///////////////////////////////////////////////////////////////////////////////

class DumpBarrier {
    public:
        DumpBarrier(Condition::WakeUpType type = Condition::WAKE_UP_ALL) : mType(type), mSignaled(false) { }
        ~DumpBarrier() { }

        void signal() {
            Mutex::Autolock l(mLock);
            mSignaled = true;
            mCondition.signal(mType);
        }

        void wait() {
            Mutex::Autolock l(mLock);
            while (!mSignaled) {
                mCondition.wait(mLock);
            }
            mSignaled = false;
        }

    private:
        Condition::WakeUpType mType;
        volatile bool mSignaled;
        mutable Mutex mLock;
        mutable Condition mCondition;
};

///////////////////////////////////////////////////////////////////////////////
// DumpTask
///////////////////////////////////////////////////////////////////////////////

class DumpTask {
public:
    DumpTask(int w, int h, const char* f, bool c):
        width(w), height(h), size(width * height * 4), compress(c), flip(true) {
        memcpy(filename, f, 512);
        bitmap.setInfo(SkImageInfo::MakeN32Premul(width, height));
        bitmap.allocPixels();
    }

    DumpTask(const SkBitmap* b, const char* f, bool c):
        width(b->width()), height(b->height()), size(b->getSize()), compress(c), flip(false) {
        memcpy(filename, f, 512);

        nsecs_t start = systemTime(SYSTEM_TIME_MONOTONIC);
        copyBitmap(&bitmap, kN32_SkColorType, *b);
        nsecs_t end = systemTime(SYSTEM_TIME_MONOTONIC);
        ALOGD("copyTo %dx%d, time %" PRId64 "ms", width, height, nanoseconds_to_milliseconds(end - start));
    }

    ~DumpTask() {
    }

    bool copyBitmap(SkBitmap* dst, SkColorType dstColorType, const SkBitmap& src) {
        SkPixmap srcPM;
        if (!src.peekPixels(&srcPM)) {
            return false;
        }

        SkBitmap tmpDst;
        SkImageInfo dstInfo = srcPM.info().makeColorType(dstColorType);
        if (!tmpDst.setInfo(dstInfo)) {
            return false;
        }
        if (!tmpDst.tryAllocPixels()) {
            return false;
        }

        SkPixmap dstPM;
        if (!tmpDst.peekPixels(&dstPM)) {
            return false;
        }
        if (!srcPM.readPixels(dstPM)) {
            return false;
        }

        dst->swap(tmpDst);
        return true;
    }

    void preProcess() {
        // for ARGB only
        if (bitmap.readyToDraw() && compress) {
            nsecs_t start = systemTime(SYSTEM_TIME_MONOTONIC);
            float ratio = sqrt(TARGET_SIZE / width / height) ;

            if (ratio < 1) {
                int w = (int)(width * ratio + 0.5);;
                int h = (int)(height * ratio + 0.5);
                SkBitmap dst;
                dst.setInfo(SkImageInfo::MakeN32Premul(w, h));
                dst.allocPixels();
                dst.eraseColor(0);

                SkPaint paint;
                SkCanvas canvas(dst);
                canvas.scale(ratio, ratio);
                canvas.drawBitmap(bitmap, 0.0f, 0.0f, &paint);
                copyBitmap(&bitmap, kN32_SkColorType, dst);
                nsecs_t end = systemTime(SYSTEM_TIME_MONOTONIC);
                ALOGD("scale ratio %f, %dx%d, time %" PRId64 "ms",
                    ratio, bitmap.width(), bitmap.height(), nanoseconds_to_milliseconds(end - start));
            } else {
                ALOGD("scale ratio %f >= 1, %dx%d not needed", ratio, bitmap.width(), bitmap.height());
            }
        }
    }

    void onProcess() {
        nsecs_t start = systemTime(SYSTEM_TIME_MONOTONIC);
        if (flip) {
            SkBitmap dst;
            dst.setInfo(SkImageInfo::MakeN32Premul(bitmap.width(), bitmap.height()));
            dst.allocPixels();
            dst.eraseColor(0);

            SkPaint paint;
            SkCanvas canvas(dst);
            canvas.scale(1.0f, -1.0f);
            canvas.translate(0.0f, -bitmap.height());
            canvas.drawBitmap(bitmap, 0.0f, 0.0f, &paint);
            copyBitmap(&bitmap, kN32_SkColorType, dst);
        }
        if (!sk_tool_utils::EncodeImageToFile(filename, bitmap,
                SkEncodedImageFormat::kPNG, quality)) {
            ALOGE("Failed to encode image %s\n", filename);
            char* lastPeriod = strrchr(filename, '/');
            if (lastPeriod) {
                char file[512];
                // folder /data/HWUI_dump/ will be created by script
                sprintf(file, "/data/HWUI_dump/%s", lastPeriod + 1);
                if (!sk_tool_utils::EncodeImageToFile(filename, bitmap,
                        SkEncodedImageFormat::kPNG, quality)) {
                    ALOGE("Failed to encode image %s\n", file);
                }
            }
        }
        nsecs_t end = systemTime(SYSTEM_TIME_MONOTONIC);
        ALOGD("encodeFile %dx%d, time %" PRId64 "ms",
            bitmap.width(), bitmap.height(), nanoseconds_to_milliseconds(end - start));
    }

    int width;
    int height;
    size_t size;
    char filename[512];
    SkBitmap bitmap;
    bool compress;
    bool flip;
    int quality = 60;
};

///////////////////////////////////////////////////////////////////////////////
// DumperThread
///////////////////////////////////////////////////////////////////////////////

class DumperThread: public Thread {
public:
     DumperThread(const String8 name): mSignal(Condition::WAKE_UP_ONE), mName(name) { }

     bool addTask(DumpTask *task) {
         if (!isRunning()) {
             run(mName.string(), PRIORITY_DEFAULT);
         }

         Mutex::Autolock l(mLock);
         ssize_t index = mTasks.add(task);
         mSignal.signal();

         return index >= 0;
     }
     size_t getTaskCount() const {
         Mutex::Autolock l(mLock);
         return mTasks.size();
     }
     void exit() {
         {
             Mutex::Autolock l(mLock);
             for (size_t i = 0; i < mTasks.size(); i++) {
                 const DumpTask* task = mTasks.itemAt(i);
                 delete task;
             }
             mTasks.clear();
         }
         requestExit();
         mSignal.signal();
     }

 private:
     virtual bool threadLoop() {
         mSignal.wait();
         Vector<DumpTask*> tasks;
         {
             Mutex::Autolock l(mLock);
             tasks = mTasks;
             mTasks.clear();
         }

         for (size_t i = 0; i < tasks.size(); i++) {
             DumpTask* task = tasks.itemAt(i);
             task->onProcess();
             delete task;
         }
         return true;
     }

     // Lock for the list of tasks
     mutable Mutex mLock;
     Vector<DumpTask *> mTasks;

     // Signal used to wake up the thread when a new
     // task is available in the list
     mutable DumpBarrier mSignal;

     const String8 mName;
 };

///////////////////////////////////////////////////////////////////////////////
// Dumper
///////////////////////////////////////////////////////////////////////////////

Dumper::Dumper() : mPid(getpid())
                 , mProcessName(nullptr)
                 , mThreadCount(sysconf(_SC_NPROCESSORS_CONF) / 2) {
    // Get the number of available CPUs. This value does not change over time.
    ALOGD("Dumper init %d threads <%p>", mThreadCount, this);
    for (int i = 0; i < mThreadCount; i++) {
        String8 name;
        name.appendFormat("HwuiDumperThread%d", i + 1);
        mThreads.add(new DumperThread(name));
    }

    // Get process name
    FILE *f;
    char processName[256];
    bool success = true;

    f = fopen("/proc/self/cmdline", "r");
    if (!f) {
        ALOGE("Can't get application name");
        success = false;
    } else {
        if (fgets(processName, 256, f) == nullptr) {
            ALOGE("fgets failed");
            success = false;
        }
        fclose(f);
    }

    if (success) {
        mProcessName = new char[strlen(processName) + 1];
        memmove(mProcessName, processName, strlen(processName) + 1);
        ALOGD("<%s> is running.", mProcessName);
    }
}

Dumper::~Dumper() {
    for (size_t i = 0; i < mThreads.size(); i++) {
        mThreads[i]->exit();
    }
    if (mProcessName) {
        delete []mProcessName;
        mProcessName = nullptr;
    }
}

bool Dumper::addTask(DumpTask *task) {
    task->preProcess();
    if (mThreads.size() > 0) {
        size_t minQueueSize = MAX_BUFFER_SIZE / mThreadCount / task->bitmap.getSize();
        sp<DumperThread> thread;
        for (size_t i = 0; i < mThreads.size(); i++) {
            if (mThreads[i]->getTaskCount() < minQueueSize) {
                thread = mThreads[i];
                minQueueSize = mThreads[i]->getTaskCount();
            }
        }

        if (thread.get() == nullptr)
            return false;

        return thread->addTask(task);
    }
    return false;
}

bool Dumper::dumpDisplayList(int width, int height, const char* name,
    int frameCount, void* renderer) {
    char file[512];
    sprintf(file, "/data/data/%s/dp_%p_%09d.png", mProcessName, renderer, frameCount);

    ALOGD("%s [%s]: %dx%d %s", __FUNCTION__, name, width, height, file);
    return dumpImage(width, height, file);
}

bool Dumper::dumpDraw(int width, int height, int frameCount, int index,
        void* renderer, void* drawOp, int sub) {
    char file[512];
    sprintf(file, "/data/data/%s/draw_%p_%09d_%02d_%02d_%p.png",
        mProcessName, renderer, frameCount, index, sub, drawOp);

    ALOGD("%s: %dx%d %s", __FUNCTION__, width, height, file);
    return dumpImage(width, height, file);
}

bool Dumper::dumpLayer(int width, int height, int fbo, int frameCount,
        void* renderer, void* layer) {
    char file[512];
    nsecs_t time = systemTime(SYSTEM_TIME_MONOTONIC);
    sprintf(file, "/data/data/%s/layer_%p_%p_%d_%dx%d_%09d_%09u.png",
        mProcessName, renderer, layer, fbo, width, height, frameCount, (unsigned int) time / 1000);

    ALOGD("%s: %dx%d %s", __FUNCTION__, width, height, file);
    return dumpImage(width, height, file);
}

bool Dumper::dumpTexture(int texture, int width, int height, const SkBitmap *bitmap, bool isLayer) {
    char file[512];

    if (isLayer) {
        nsecs_t time = systemTime(SYSTEM_TIME_MONOTONIC);
        sprintf(file, "/data/data/%s/texLayer_%d_%dx%d_%u.png",
            mProcessName, texture, width, height, (unsigned int) time / 1000);
    } else {
        sprintf(file, "/data/data/%s/tex_%d_%dx%d_%p.png",
            mProcessName, texture, width, height, bitmap);
    }

    ALOGD("%s: %dx%d %s", __FUNCTION__, width, height, file);

    DumpTask* task = new DumpTask(bitmap, file, isLayer);
    if (!task->bitmap.readyToDraw()) {
        ALOGE("%s: failed to copy bitmap %p\n", __FUNCTION__, bitmap);
        delete task;
        return false;
    }

    if (addTask(task)) {
        // dumper will help to delete task when task finished
    } else {
        task->onProcess();
        delete task;
    }

    return true;
}

bool Dumper::dumpAlphaTexture(int width, int height, uint8_t *data, const char *prefix, bool isA8) {
    static int count = 0;

    char file[512];
    SkBitmap bitmap;
    SkBitmap bitmapCopy;

    sprintf(file, "/data/data/%s/%s_%04d.png", mProcessName, prefix, count++);

    ALOGD("%s: %dx%d %s", __FUNCTION__, width, height, file);
    if (isA8)
        bitmap.setInfo(SkImageInfo::MakeA8(width, height));
    else
        bitmap.setInfo(SkImageInfo::MakeN32Premul(width, height));
    bitmap.setPixels(data);

    DumpTask* task = new DumpTask(&bitmap, file, false);

    if (!task->bitmap.readyToDraw()) {
        ALOGE("%s: failed to copy data %p", __FUNCTION__, data);
        delete task;
        return false;
    }

    // dump directlly because pixelbuffer becomes invalid if using multi-thread
    task->onProcess();
    delete task;

    return true;
}

bool Dumper::dumpImage(int width, int height, const char *filename) {
    DumpTask* task = new DumpTask(width, height, filename, true);
    GLenum error;
    nsecs_t start = systemTime(SYSTEM_TIME_MONOTONIC);
    glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, task->bitmap.getPixels());
    nsecs_t end = systemTime(SYSTEM_TIME_MONOTONIC);
    ALOGD("%s: readpixel %dx%d time %" PRId64 "ms",
        __FUNCTION__, width, height, nanoseconds_to_milliseconds(end - start));

    if ((error = glGetError()) != GL_NO_ERROR) {
        ALOGE("%s: get GL error 0x%x \n", __FUNCTION__, error);
        delete task;
        return false;
    }

    if (addTask(task)) {
        // dumper will help to delete task when task finished
    } else {
        task->onProcess();
        delete task;
    }

    return true;
}

}; // namespace uirenderer
}; // namespace android
