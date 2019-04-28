/*
 * Copyright 2016 The Android Open Source Project
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

#ifndef ANDROID_SURFACEREPLAYER_H
#define ANDROID_SURFACEREPLAYER_H

#include "BufferQueueScheduler.h"
#include "Color.h"
#include "Event.h"

#include <frameworks/native/cmds/surfacereplayer/proto/src/trace.pb.h>

#include <gui/SurfaceComposerClient.h>
#include <gui/SurfaceControl.h>

#include <utils/Errors.h>
#include <utils/StrongPointer.h>

#include <stdatomic.h>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <queue>
#include <thread>
#include <unordered_map>
#include <utility>

namespace android {

const auto DEFAULT_PATH = "/data/local/tmp/SurfaceTrace.dat";
const auto RAND_COLOR_SEED = 700;
const auto DEFAULT_THREADS = 3;

typedef int32_t layer_id;
typedef int32_t display_id;

typedef google::protobuf::RepeatedPtrField<SurfaceChange> SurfaceChanges;
typedef google::protobuf::RepeatedPtrField<DisplayChange> DisplayChanges;

class Replayer {
  public:
    Replayer(const std::string& filename, bool replayManually = false,
            int numThreads = DEFAULT_THREADS, bool wait = true, nsecs_t stopHere = -1);
    Replayer(const Trace& trace, bool replayManually = false, int numThreads = DEFAULT_THREADS,
            bool wait = true, nsecs_t stopHere = -1);

    status_t replay();

  private:
    status_t initReplay();

    void waitForConsoleCommmand();
    static void stopAutoReplayHandler(int signal);

    status_t dispatchEvent(int index);

    status_t doTransaction(const Transaction& transaction, const std::shared_ptr<Event>& event);
    status_t createSurfaceControl(const SurfaceCreation& create,
            const std::shared_ptr<Event>& event);
    status_t deleteSurfaceControl(const SurfaceDeletion& delete_,
            const std::shared_ptr<Event>& event);
    status_t injectVSyncEvent(const VSyncEvent& vsyncEvent, const std::shared_ptr<Event>& event);
    void createDisplay(const DisplayCreation& create, const std::shared_ptr<Event>& event);
    void deleteDisplay(const DisplayDeletion& delete_, const std::shared_ptr<Event>& event);
    void updatePowerMode(const PowerModeUpdate& update, const std::shared_ptr<Event>& event);

    status_t doSurfaceTransaction(const SurfaceChanges& surfaceChange);
    void doDisplayTransaction(const DisplayChanges& displayChange);

    status_t setPosition(layer_id id, const PositionChange& pc);
    status_t setSize(layer_id id, const SizeChange& sc);
    status_t setAlpha(layer_id id, const AlphaChange& ac);
    status_t setLayer(layer_id id, const LayerChange& lc);
    status_t setCrop(layer_id id, const CropChange& cc);
    status_t setFinalCrop(layer_id id, const FinalCropChange& fcc);
    status_t setMatrix(layer_id id, const MatrixChange& mc);
    status_t setOverrideScalingMode(layer_id id, const OverrideScalingModeChange& osmc);
    status_t setTransparentRegionHint(layer_id id, const TransparentRegionHintChange& trgc);
    status_t setLayerStack(layer_id id, const LayerStackChange& lsc);
    status_t setHiddenFlag(layer_id id, const HiddenFlagChange& hfc);
    status_t setOpaqueFlag(layer_id id, const OpaqueFlagChange& ofc);
    status_t setSecureFlag(layer_id id, const SecureFlagChange& sfc);
    status_t setDeferredTransaction(layer_id id, const DeferredTransactionChange& dtc);

    void setDisplaySurface(display_id id, const DispSurfaceChange& dsc);
    void setDisplayLayerStack(display_id id, const LayerStackChange& lsc);
    void setDisplaySize(display_id id, const SizeChange& sc);
    void setDisplayProjection(display_id id, const ProjectionChange& pc);

    void doDeleteSurfaceControls();
    void waitUntilTimestamp(int64_t timestamp);
    void waitUntilDeferredTransactionLayerExists(
            const DeferredTransactionChange& dtc, std::unique_lock<std::mutex>& lock);
    status_t loadSurfaceComposerClient();

    Trace mTrace;
    bool mLoaded = false;
    int32_t mIncrementIndex = 0;
    int64_t mCurrentTime = 0;
    int32_t mNumThreads = DEFAULT_THREADS;

    Increment mCurrentIncrement;

    std::string mLastInput;

    static atomic_bool sReplayingManually;
    bool mWaitingForNextVSync;
    bool mWaitForTimeStamps;
    nsecs_t mStopTimeStamp;
    bool mHasStopped;

    std::mutex mLayerLock;
    std::condition_variable mLayerCond;
    std::unordered_map<layer_id, sp<SurfaceControl>> mLayers;
    std::unordered_map<layer_id, HSV> mColors;

    std::mutex mPendingLayersLock;
    std::vector<layer_id> mLayersPendingRemoval;

    std::mutex mBufferQueueSchedulerLock;
    std::unordered_map<layer_id, std::shared_ptr<BufferQueueScheduler>> mBufferQueueSchedulers;

    std::mutex mDisplayLock;
    std::condition_variable mDisplayCond;
    std::unordered_map<display_id, sp<IBinder>> mDisplays;

    sp<SurfaceComposerClient> mComposerClient;
    std::queue<std::shared_ptr<Event>> mPendingIncrements;
};

}  // namespace android
#endif
