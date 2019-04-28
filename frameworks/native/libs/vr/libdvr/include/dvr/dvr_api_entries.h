// dvr_api_entries.h
//
// Defines the DVR platform library API entries.
//
// Do not include this header directly.

#ifndef DVR_V1_API_ENTRY
#error Do not include this header directly.
#endif

// Do not delete this line: BEGIN CODEGEN OUTPUT
// Display manager client
DVR_V1_API_ENTRY(DisplayManagerCreate);
DVR_V1_API_ENTRY(DisplayManagerDestroy);
DVR_V1_API_ENTRY(DisplayManagerGetEventFd);
DVR_V1_API_ENTRY(DisplayManagerTranslateEpollEventMask);
DVR_V1_API_ENTRY(DisplayManagerGetSurfaceState);
DVR_V1_API_ENTRY(DisplayManagerGetReadBufferQueue);
DVR_V1_API_ENTRY(ConfigurationDataGet);
DVR_V1_API_ENTRY(ConfigurationDataDestroy);
DVR_V1_API_ENTRY(SurfaceStateCreate);
DVR_V1_API_ENTRY(SurfaceStateDestroy);
DVR_V1_API_ENTRY(SurfaceStateGetSurfaceCount);
DVR_V1_API_ENTRY(SurfaceStateGetUpdateFlags);
DVR_V1_API_ENTRY(SurfaceStateGetSurfaceId);
DVR_V1_API_ENTRY(SurfaceStateGetProcessId);
DVR_V1_API_ENTRY(SurfaceStateGetQueueCount);
DVR_V1_API_ENTRY(SurfaceStateGetQueueIds);
DVR_V1_API_ENTRY(SurfaceStateGetZOrder);
DVR_V1_API_ENTRY(SurfaceStateGetVisible);
DVR_V1_API_ENTRY(SurfaceStateGetAttributeCount);
DVR_V1_API_ENTRY(SurfaceStateGetAttributes);

// Write buffer
DVR_V1_API_ENTRY(WriteBufferCreateEmpty);
DVR_V1_API_ENTRY(WriteBufferDestroy);
DVR_V1_API_ENTRY(WriteBufferIsValid);
DVR_V1_API_ENTRY(WriteBufferClear);
DVR_V1_API_ENTRY(WriteBufferGetId);
DVR_V1_API_ENTRY(WriteBufferGetAHardwareBuffer);
DVR_V1_API_ENTRY(WriteBufferPost);
DVR_V1_API_ENTRY(WriteBufferGain);
DVR_V1_API_ENTRY(WriteBufferGainAsync);
DVR_V1_API_ENTRY(WriteBufferGetNativeHandle);

// Read buffer
DVR_V1_API_ENTRY(ReadBufferCreateEmpty);
DVR_V1_API_ENTRY(ReadBufferDestroy);
DVR_V1_API_ENTRY(ReadBufferIsValid);
DVR_V1_API_ENTRY(ReadBufferClear);
DVR_V1_API_ENTRY(ReadBufferGetId);
DVR_V1_API_ENTRY(ReadBufferGetAHardwareBuffer);
DVR_V1_API_ENTRY(ReadBufferAcquire);
DVR_V1_API_ENTRY(ReadBufferRelease);
DVR_V1_API_ENTRY(ReadBufferReleaseAsync);
DVR_V1_API_ENTRY(ReadBufferGetNativeHandle);

// Buffer
DVR_V1_API_ENTRY(BufferDestroy);
DVR_V1_API_ENTRY(BufferGetAHardwareBuffer);
DVR_V1_API_ENTRY(BufferGetNativeHandle);
DVR_V1_API_ENTRY(BufferGlobalLayoutVersionGet);

// Write buffer queue
DVR_V1_API_ENTRY(WriteBufferQueueDestroy);
DVR_V1_API_ENTRY(WriteBufferQueueGetCapacity);
DVR_V1_API_ENTRY(WriteBufferQueueGetId);
DVR_V1_API_ENTRY(WriteBufferQueueGetExternalSurface);  // deprecated
DVR_V1_API_ENTRY(WriteBufferQueueCreateReadQueue);
DVR_V1_API_ENTRY(WriteBufferQueueDequeue);
DVR_V1_API_ENTRY(WriteBufferQueueResizeBuffer);

// Read buffer queue
DVR_V1_API_ENTRY(ReadBufferQueueDestroy);
DVR_V1_API_ENTRY(ReadBufferQueueGetCapacity);
DVR_V1_API_ENTRY(ReadBufferQueueGetId);
DVR_V1_API_ENTRY(ReadBufferQueueCreateReadQueue);
DVR_V1_API_ENTRY(ReadBufferQueueDequeue);
DVR_V1_API_ENTRY(ReadBufferQueueSetBufferAvailableCallback);
DVR_V1_API_ENTRY(ReadBufferQueueSetBufferRemovedCallback);
DVR_V1_API_ENTRY(ReadBufferQueueHandleEvents);

// V-Sync client
DVR_V1_API_ENTRY(VSyncClientCreate);
DVR_V1_API_ENTRY(VSyncClientDestroy);
DVR_V1_API_ENTRY(VSyncClientGetSchedInfo);

// Display surface
DVR_V1_API_ENTRY(SurfaceCreate);
DVR_V1_API_ENTRY(SurfaceDestroy);
DVR_V1_API_ENTRY(SurfaceGetId);
DVR_V1_API_ENTRY(SurfaceSetAttributes);
DVR_V1_API_ENTRY(SurfaceCreateWriteBufferQueue);
DVR_V1_API_ENTRY(SetupGlobalBuffer);
DVR_V1_API_ENTRY(DeleteGlobalBuffer);
DVR_V1_API_ENTRY(GetGlobalBuffer);

// Pose client
DVR_V1_API_ENTRY(PoseClientCreate);
DVR_V1_API_ENTRY(PoseClientDestroy);
DVR_V1_API_ENTRY(PoseClientGet);
DVR_V1_API_ENTRY(PoseClientGetVsyncCount);
DVR_V1_API_ENTRY(PoseClientGetController);

// Virtual touchpad client
DVR_V1_API_ENTRY(VirtualTouchpadCreate);
DVR_V1_API_ENTRY(VirtualTouchpadDestroy);
DVR_V1_API_ENTRY(VirtualTouchpadAttach);
DVR_V1_API_ENTRY(VirtualTouchpadDetach);
DVR_V1_API_ENTRY(VirtualTouchpadTouch);
DVR_V1_API_ENTRY(VirtualTouchpadButtonState);

// VR HWComposer client
DVR_V1_API_ENTRY(HwcClientCreate);
DVR_V1_API_ENTRY(HwcClientDestroy);
DVR_V1_API_ENTRY(HwcFrameDestroy);
DVR_V1_API_ENTRY(HwcFrameGetDisplayId);
DVR_V1_API_ENTRY(HwcFrameGetDisplayWidth);
DVR_V1_API_ENTRY(HwcFrameGetDisplayHeight);
DVR_V1_API_ENTRY(HwcFrameGetDisplayRemoved);
DVR_V1_API_ENTRY(HwcFrameGetActiveConfig);
DVR_V1_API_ENTRY(HwcFrameGetColorMode);
DVR_V1_API_ENTRY(HwcFrameGetColorTransform);
DVR_V1_API_ENTRY(HwcFrameGetPowerMode);
DVR_V1_API_ENTRY(HwcFrameGetVsyncEnabled);
DVR_V1_API_ENTRY(HwcFrameGetLayerCount);
DVR_V1_API_ENTRY(HwcFrameGetLayerId);
DVR_V1_API_ENTRY(HwcFrameGetLayerBuffer);
DVR_V1_API_ENTRY(HwcFrameGetLayerFence);
DVR_V1_API_ENTRY(HwcFrameGetLayerDisplayFrame);
DVR_V1_API_ENTRY(HwcFrameGetLayerCrop);
DVR_V1_API_ENTRY(HwcFrameGetLayerBlendMode);
DVR_V1_API_ENTRY(HwcFrameGetLayerAlpha);
DVR_V1_API_ENTRY(HwcFrameGetLayerType);
DVR_V1_API_ENTRY(HwcFrameGetLayerApplicationId);
DVR_V1_API_ENTRY(HwcFrameGetLayerZOrder);
DVR_V1_API_ENTRY(HwcFrameGetLayerCursor);
DVR_V1_API_ENTRY(HwcFrameGetLayerTransform);
DVR_V1_API_ENTRY(HwcFrameGetLayerDataspace);
DVR_V1_API_ENTRY(HwcFrameGetLayerColor);
DVR_V1_API_ENTRY(HwcFrameGetLayerNumVisibleRegions);
DVR_V1_API_ENTRY(HwcFrameGetLayerVisibleRegion);
DVR_V1_API_ENTRY(HwcFrameGetLayerNumDamagedRegions);
DVR_V1_API_ENTRY(HwcFrameGetLayerDamagedRegion);

// New entries added at the end to allow the DVR platform library API
// to be updated before updating VrCore.

// Virtual touchpad client
DVR_V1_API_ENTRY(VirtualTouchpadScroll);

// Read the native display metrics from the hardware composer
DVR_V1_API_ENTRY(GetNativeDisplayMetrics);

// Performance
DVR_V1_API_ENTRY(PerformanceSetSchedulerPolicy);

// Pose client
DVR_V1_API_ENTRY(PoseClientSensorsEnable);

// Read buffer queue
DVR_V1_API_ENTRY(ReadBufferQueueGetEventFd);

// Create write buffer queue locally
DVR_V1_API_ENTRY(WriteBufferQueueCreate);

// Gets an ANativeWindow from DvrWriteBufferQueue.
DVR_V1_API_ENTRY(WriteBufferQueueGetANativeWindow);

// Dvr{Read,Write}BufferQueue API for asynchronous IPC.
DVR_V1_API_ENTRY(WriteBufferQueueGainBuffer);
DVR_V1_API_ENTRY(WriteBufferQueuePostBuffer);
DVR_V1_API_ENTRY(ReadBufferQueueAcquireBuffer);
DVR_V1_API_ENTRY(ReadBufferQueueReleaseBuffer);

// Pose client
DVR_V1_API_ENTRY(PoseClientGetDataReader);
DVR_V1_API_ENTRY(PoseClientDataCapture);
DVR_V1_API_ENTRY(PoseClientDataReaderDestroy);
