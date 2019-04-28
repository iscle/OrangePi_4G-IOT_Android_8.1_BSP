/*
 * Copyright 2012, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef MTK_MEDIA_PULLER_H_

#define MTK_MEDIA_PULLER_H_

#include <media/stagefright/foundation/AHandler.h>

#include <media/stagefright/MediaBuffer.h>


namespace android {

struct MediaSource;

struct MtkMediaPuller : public AHandler {
    enum {
        kWhatEOS,
        kWhatAccessUnit
    };

    MtkMediaPuller(const sp<MediaSource> &source, const sp<AMessage> &notify);

    status_t start();
    void stopAsync(const sp<AMessage> &notify);

    void pause();
    void resume();

protected:
    virtual void onMessageReceived(const sp<AMessage> &msg);
    virtual ~MtkMediaPuller();

private:
    enum {
        kWhatStart,
        kWhatStop,
        kWhatPull,
        kWhatPause,
        kWhatResume,
    };

    sp<MediaSource> mSource;
    sp<AMessage> mNotify;
    int32_t mPullGeneration;
    bool mIsAudio;
    bool mPaused;
    bool mWFDFrameLog;

    status_t postSynchronouslyAndReturnError(const sp<AMessage> &msg);
    void schedulePull();

    DISALLOW_EVIL_CONSTRUCTORS(MtkMediaPuller);

private:
    int64_t mFirstDeltaMs;
    void read_pro(bool isVideo, int64_t timeUs, MediaBuffer *mbuf, const sp<ABuffer>& Abuf);

};

}  // namespace android

#endif  // MTK_MEDIA_PULLER_H_
