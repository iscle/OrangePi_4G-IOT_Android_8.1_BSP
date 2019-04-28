#ifndef MTK_REPEATER_SOURCE_H_

#define MTK_REPEATER_SOURCE_H_

#include <media/stagefright/foundation/ABase.h>
#include <media/stagefright/foundation/AHandlerReflector.h>
#include <media/stagefright/MediaSource.h>

#define SUSPEND_VIDEO_IF_IDLE   0

namespace android {

// This MediaSource delivers frames at a constant rate by repeating buffers
// if necessary.
struct MtkRepeaterSource : public MediaSource {
    MtkRepeaterSource(const sp<MediaSource> &source, double rateHz);

    virtual status_t start(MetaData *params);
    virtual status_t stop();
    virtual sp<MetaData> getFormat();

    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options);

    void onMessageReceived(const sp<AMessage> &msg);

    // If MtkRepeaterSource is currently dormant, because SurfaceFlinger didn't
    // send updates in a while, this is its wakeup call.

    void wakeUp(bool bExit = false);


    double getFrameRate() const;
    void setFrameRate(double rateHz);

    static const size_t kHistorySize = 8;


protected:
    virtual ~MtkRepeaterSource();

private:
    enum {
        kWhatRead,
    };

    Mutex mLock;
    Condition mCondition;

    bool mStarted;

    sp<MediaSource> mSource;
    double mRateHz;

    sp<ALooper> mLooper;
    sp<AHandlerReflector<MtkRepeaterSource> > mReflector;

    MediaBuffer *mBuffer;
    status_t mResult;
    int64_t mLastBufferUpdateUs;

    int64_t mStartTimeUs;
    int32_t mFrameCount;

    void postRead();

    DISALLOW_EVIL_CONSTRUCTORS(MtkRepeaterSource);

private:
    int64_t mStartCountTime;
    int32_t mReadInCount;
    int32_t mReadOutCountNew;
    int32_t mReadOutCountRpt;
    bool mEableLogRepeatUseCount;
    int32_t vp_only_count;
    bool mStopping;
    bool scenario_enable;
    bool mWFDFrameLog;

    int32_t mLastGotVideoTimeMs;
    int64_t mLastSendTimeUs;
    int64_t timeStamp;
    int32_t mDropFrames;

    enum wfd_scenario{
        VP_ONLY,
        VP_UI,
        UI_ONLY
    };
    wfd_scenario previousScenario;
    wfd_scenario scenario;

    void read_pro(int64_t timeUs);
    void read_fps(int64_t timeUs, int64_t readTimeUs);
    status_t stop_l();
    struct PLL {
        PLL();

        // reset PLL to new PLL
        void reset(float fps = -1);
        // keep current estimate, but restart phase
        void restart();
        // returns period
        nsecs_t addSample(nsecs_t time);

    private:
        nsecs_t mPeriod;
        nsecs_t mPhase;

        bool    mPrimed;        // have an estimate for the period
        size_t  mSamplesUsedForPriming;

        nsecs_t mLastTime;      // last input time
        nsecs_t mRefitAt;       // next input time to fit at

        size_t  mNumSamples;    // can go past kHistorySize
        nsecs_t mTimes[kHistorySize];

        void test();
        // returns whether fit was successful
        bool fit(nsecs_t phase, nsecs_t period, size_t numSamples,
                int64_t *a, int64_t *b, int64_t *err);
        void prime(size_t numSamples);
    };

    PLL mPLL;
    double mVideoRateHz;
    int64_t mLastTimeUs;

};
}  // namespace android

#endif // REPEATER_SOURCE_H_
