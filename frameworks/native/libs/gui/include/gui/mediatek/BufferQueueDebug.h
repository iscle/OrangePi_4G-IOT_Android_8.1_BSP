#ifndef ANDROID_GUI_BUFFERQUEUEDEBUG_H
#define ANDROID_GUI_BUFFERQUEUEDEBUG_H

#include <gui/IGraphicBufferConsumer.h>
#include <utils/RefBase.h>
#include <utils/Singleton.h>
#include <gui_debug/BufferQueueDumpAPI.h>

#include <gedkpi/GedKpiWrap_def.h>

namespace android {
// ----------------------------------------------------------------------------

typedef GED_KPI_HANDLE (*createWrapPrototype)(uint64_t BBQ_ID);
typedef void (*destroyWrapPrototype)(GED_KPI_HANDLE hKPI);
typedef GED_ERROR (*dequeueBufferTagWrapPrototype)(GED_KPI_HANDLE hKPI, int32_t BBQ_api_type, int32_t fence, int32_t pid, intptr_t buffer_addr);
typedef GED_ERROR (*queueBufferTagWrapPrototype)(GED_KPI_HANDLE hKPI, int32_t BBQ_api_type, int32_t fence, int32_t pid, int32_t QedBuffer_length, intptr_t buffer_addr);
typedef GED_ERROR (*acquireBufferTagWrapPrototype)(GED_KPI_HANDLE hKPI, int32_t pid, intptr_t buffer_addr);

class String8;
class BufferQueueCore;
struct BufferQueueDebug : public RefBase {
    // debug target BQ info
    wp<BufferQueueCore> mBq;
    int32_t mId;
    int mConnectedApi;
    String8 mConsumerName;
    String8 mMiniConusmerName;

    // process info
    int32_t mPid;
    int32_t mProducerPid;
    int32_t mConsumerPid;
    String8 mProducerProcName;
    String8 mConsumerProcName;

    // if debug line enabled
    bool mLine;
    // debug line count
    uint32_t mLineCnt;

    BufferQueueDumpAPI* mDump;

    GED_KPI_HANDLE (*mGedKpiCreateWrap)(uint64_t BBQ_ID);
    void (*mGedKpiDestroyWrap)(GED_KPI_HANDLE hKPI);
    GED_ERROR (*mGedKpiDequeueBufferTagWrap)(GED_KPI_HANDLE hKPI, int32_t BBQ_api_type, int32_t fence, int32_t pid, intptr_t buffer_addr);
    GED_ERROR (*mGedKpiQueueBufferTagWrap)(GED_KPI_HANDLE hKPI, int32_t BBQ_api_type, int32_t fence, int32_t pid, int32_t QedBuffer_length, intptr_t buffer_addr);
    GED_ERROR (*mGedKpiAcquireBufferTagWrap)(GED_KPI_HANDLE hKPI, int32_t pid, intptr_t buffer_addr);

    // used to notify ged about queue/acquire events for fast DVFS
    GED_KPI_HANDLE mGedHnd;

    // generate path for file dump
    static void getDumpFileName(String8& fileName, const String8& name);

    status_t drawDebugLineToGraphicBuffer(
        const sp<GraphicBuffer>& gb, uint32_t cnt, uint8_t val = 0xff);

    // whether dump mechanism of general buffer queue is enabled or not
    bool mGeneralDump;
    // used to notify ged about queue/acquire events for fast DVFS

    BufferQueueDebug();
    virtual ~BufferQueueDebug();
    // BufferQueueCore part
    void onConstructor(wp<BufferQueueCore> bq,
        const String8& consumerName,
        const uint64_t& bqId);
    void onDestructor();
    void onDump(String8 &result, const String8& prefix) const;
    void onFreeBufferLocked(const int slot);
    // BufferQueueConsumer part
    void onConsumerDisconnectHead();
    void onConsumerDisconnectTail();
    void onSetConsumerName(const String8& consumerName);
    void onAcquire(
            const int buf,
            const sp<GraphicBuffer>& gb,
            const sp<Fence>& fence,
            const int64_t& timestamp,
            const uint32_t& transform,
            const BufferItem* const buffer);
    void onRelease(const int buf);
    void onConsumerConnect(
            const sp<IConsumerListener>& consumerListener,
            const bool controlledByApp);
    // BufferQueueProducer part
    void setIonInfo(const sp<GraphicBuffer>& gb);
    void onDequeue(sp<GraphicBuffer>& gb, sp<Fence>& fence);
    void onQueue(const sp<GraphicBuffer>& gb, const sp<Fence>& fence);
    void onProducerConnect(
            const sp<IBinder>& token,
            const int api,
            bool producerControlledByApp);
    void onProducerDisconnect();
    mutable bool mDebugLog;
};
status_t getProcessName(int pid, String8& name);

// -----------------------------------------------------------------------------
// GuiDebug loader for dl open libgui_debug
class GuiDebugModuleLoader : public Singleton<GuiDebugModuleLoader> {
public:
    GuiDebugModuleLoader();
    ~GuiDebugModuleLoader();
    BufferQueueDumpAPI* CreateBQDumpInstance();

    createWrapPrototype GedKpiCreate();
    destroyWrapPrototype GedKpiDestroy();
    dequeueBufferTagWrapPrototype GedKpiDequeue();
    queueBufferTagWrapPrototype GedKpiQueue();
    acquireBufferTagWrapPrototype GedKpiAcquire();
private:
    // for buffer dump
    void* mBQDumpSoHandle;
    BufferQueueDumpAPI* (*mCreateBQDumpInstancePtr)();

    //for Ged Kpi
    void* mGedKpiSoHandle;
    GED_KPI_HANDLE (*mGedKpiCreate)(uint64_t BBQ_ID);
    void (*mGedKpiDestroy)(GED_KPI_HANDLE hKPI);
    GED_ERROR (*mGedKpiDequeueBuffer)(GED_KPI_HANDLE hKPI, int32_t BBQ_api_type, int32_t fence, int32_t pid, intptr_t buffer_addr);
    GED_ERROR (*mGedKpiQueueBuffer)(GED_KPI_HANDLE hKPI, int32_t BBQ_api_type, int32_t fence, int32_t pid, int32_t QedBuffer_length, intptr_t buffer_addr);
    GED_ERROR (*mGedKpiAcquireBuffer)(GED_KPI_HANDLE hKPI, int32_t pid, intptr_t buffer_addr);
};

// ----------------------------------------------------------------------------
}; // namespace android
#endif // ANDROID_GUI_BUFFERQUEUEDEBUG_H
