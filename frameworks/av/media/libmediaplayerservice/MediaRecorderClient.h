/*
 **
 ** Copyright 2008, The Android Open Source Project
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */

#ifndef ANDROID_MEDIARECORDERCLIENT_H
#define ANDROID_MEDIARECORDERCLIENT_H

#include <media/IMediaRecorder.h>

#include <android/hardware/media/omx/1.0/IOmx.h>

namespace android {

struct MediaRecorderBase;
class MediaPlayerService;
class ICameraRecordingProxy;

class MediaRecorderClient : public BnMediaRecorder
{
    typedef ::android::hardware::media::omx::V1_0::IOmx IOmx;

    class ServiceDeathNotifier :
            public IBinder::DeathRecipient,
            public ::android::hardware::hidl_death_recipient
    {
    public:
        ServiceDeathNotifier(
                const sp<IBinder>& service,
                const sp<IMediaRecorderClient>& listener,
                int which);
        ServiceDeathNotifier(
                const sp<IOmx>& omx,
                const sp<IMediaRecorderClient>& listener,
                int which);
        virtual ~ServiceDeathNotifier();
        virtual void binderDied(const wp<IBinder>& who);
        virtual void serviceDied(
                uint64_t cookie,
                const wp<::android::hidl::base::V1_0::IBase>& who);
        void unlinkToDeath();
    private:
        int mWhich;
        sp<IBinder> mService;
        sp<IOmx> mOmx;
        wp<IMediaRecorderClient> mListener;
    };

    void clearDeathNotifiers_l();

public:
    virtual     status_t   setCamera(const sp<hardware::ICamera>& camera,
                                    const sp<ICameraRecordingProxy>& proxy);
    virtual     status_t   setPreviewSurface(const sp<IGraphicBufferProducer>& surface);
    virtual     status_t   setVideoSource(int vs);
    virtual     status_t   setAudioSource(int as);
    virtual     status_t   setOutputFormat(int of);
    virtual     status_t   setVideoEncoder(int ve);
    virtual     status_t   setAudioEncoder(int ae);
    virtual     status_t   setOutputFile(int fd);
    virtual     status_t   setNextOutputFile(int fd);
    virtual     status_t   setVideoSize(int width, int height);
    virtual     status_t   setVideoFrameRate(int frames_per_second);
    virtual     status_t   setParameters(const String8& params);
    virtual     status_t   setListener(
                              const sp<IMediaRecorderClient>& listener);
    virtual     status_t   setClientName(const String16& clientName);
    virtual     status_t   prepare();
    virtual     status_t   getMaxAmplitude(int* max);
    virtual     status_t   getMetrics(Parcel* reply);
    virtual     status_t   start();
    virtual     status_t   stop();
    virtual     status_t   reset();
    virtual     status_t   pause();
    virtual     status_t   resume();
    virtual     status_t   init();
    virtual     status_t   close();
    virtual     status_t   release();
    virtual     status_t   dump(int fd, const Vector<String16>& args);
    virtual     status_t   setInputSurface(const sp<PersistentSurface>& surface);
    virtual     sp<IGraphicBufferProducer> querySurfaceMediaSource();

private:
    friend class           MediaPlayerService;  // for accessing private constructor

                           MediaRecorderClient(
                                   const sp<MediaPlayerService>& service,
                                                               pid_t pid,
                                                               const String16& opPackageName);
    virtual                ~MediaRecorderClient();

    sp<ServiceDeathNotifier> mCameraDeathListener;
    sp<ServiceDeathNotifier> mCodecDeathListener;

    pid_t                  mPid;
    Mutex                  mLock;
    MediaRecorderBase      *mRecorder;
    sp<MediaPlayerService> mMediaPlayerService;
};

}; // namespace android

#endif // ANDROID_MEDIARECORDERCLIENT_H
