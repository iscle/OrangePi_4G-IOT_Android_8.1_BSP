/*
// Copyright (c) 2014 Intel Corporation 
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
*/
#ifndef DISPLAY_ANALYZER_H
#define DISPLAY_ANALYZER_H

#include <utils/threads.h>
#include <utils/Vector.h>


namespace android {
namespace intel {


class DisplayAnalyzer {
public:
    DisplayAnalyzer();
    virtual ~DisplayAnalyzer();

public:
    bool initialize();
    void deinitialize();
    void analyzeContents(size_t numDisplays, hwc_display_contents_1_t** displays);
    bool isVideoExtModeActive();
    bool isVideoExtModeEnabled();
    bool isVideoLayer(hwc_layer_1_t &layer);
    bool isVideoFullScreen(int device, hwc_layer_1_t &layer);
    bool isOverlayAllowed();
    int  getVideoInstances();
    void postHotplugEvent(bool connected);
    void postVideoEvent(int instanceID, int state);
    void postInputEvent(bool active);
    void postVideoEvent(int instances, int instanceID, bool preparing, bool playing);
    void postBlankEvent(bool blank);
    void postIdleEntryEvent();
    bool isPresentationLayer(hwc_layer_1_t &layer);
    bool isProtectedLayer(hwc_layer_1_t &layer);
    bool ignoreVideoSkipFlag();
    int  getFirstVideoInstanceSessionID();

private:
    enum DisplayEventType {
        HOTPLUG_EVENT,
        BLANK_EVENT,
        VIDEO_EVENT,
        TIMING_EVENT,
        INPUT_EVENT,
        DPMS_EVENT,
        IDLE_ENTRY_EVENT,
        IDLE_EXIT_EVENT,
        VIDEO_CHECK_EVENT,
    };

    struct Event {
        int type;

        struct VideoEvent {
            int instanceID;
            int state;
        };

        union {
            bool bValue;
            int  nValue;
            VideoEvent videoEvent;
        };
    };
    inline void postEvent(Event& e);
    inline bool getEvent(Event& e);
    void handlePendingEvents();
    void handleHotplugEvent(bool connected);
    void handleBlankEvent(bool blank);
    void handleVideoEvent(int instanceID, int state);
    void handleTimingEvent();
    void handleInputEvent(bool active);
    void handleDpmsEvent(int delayCount);
    void handleIdleEntryEvent(int count);
    void handleIdleExitEvent();
    void handleVideoCheckEvent();

    void blankSecondaryDevice();
    void handleVideoExtMode();
    void checkVideoExtMode();
    void enterVideoExtMode();
    void exitVideoExtMode();
    bool hasProtectedLayer();
    inline void setCompositionType(hwc_display_contents_1_t *content, int type);
    inline void setCompositionType(int device, int type, bool reset);

private:
    // Video playback state, must match defintion in Multi Display Service
    enum
    {
        VIDEO_PLAYBACK_IDLE,
        VIDEO_PLAYBACK_STARTING,
        VIDEO_PLAYBACK_STARTED,
        VIDEO_PLAYBACK_STOPPING,
        VIDEO_PLAYBACK_STOPPED,
    };

    enum
    {
        // number of flips before display can be powered off in video extended mode
        DELAY_BEFORE_DPMS_OFF = 0,
    };

private:
    bool mInitialized;
    bool mVideoExtModeEnabled;
    bool mVideoExtModeEligible;
    bool mVideoExtModeActive;
    bool mBlankDevice;
    bool mOverlayAllowed;
    bool mActiveInputState;
    // workaround HWC_SKIP_LAYER set during rotation for extended video mode
    // by default if layer has HWC_SKIP_LAYER flag it should not be processed by HWC
    bool mIgnoreVideoSkipFlag;
    bool mProtectedVideoSession;
    // map video instance ID to video state
    KeyedVector<int, int> mVideoStateMap;
    int mCachedNumDisplays;
    hwc_display_contents_1_t** mCachedDisplays;
    Vector<Event> mPendingEvents;
    Mutex mEventMutex;
    Condition mEventHandledCondition;
};

} // namespace intel
} // namespace android



#endif /* DISPLAY_ANALYZER_H */
