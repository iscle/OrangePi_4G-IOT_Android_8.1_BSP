#define LOG_TAG "BufferQueue"
#define ATRACE_TAG ATRACE_TAG_GRAPHICS
//#define LOG_NDEBUG 0
//#define MTK_LOG_ENABLE 1
#include <cmath>
#include <dlfcn.h>

#include <cutils/properties.h>
#include <cutils/log.h>
#include <binder/IPCThreadState.h>
#include <gui/IGraphicBufferConsumer.h>
#include <gui/BufferQueueCore.h>
#include <gui/IConsumerListener.h>
#include <gui/mediatek/BufferQueueDebug.h>
#include <gui/mediatek/BufferQueueMonitor.h>

#include <gedkpi/GedKpiWrap_def.h>


#define IONDBG_LENGDTH 48
#define IONDBG_PREFIX_LENGDTH 17

#undef BQ_LOGV
#undef BQ_LOGD
#undef BQ_LOGI
#undef BQ_LOGW
#undef BQ_LOGE
//#define BQ_LOGV(x, ...) ALOGV("[%s](this:%p,id:%d,api:%d,p:%d,c:%d) " x, mConsumerName.string(), mBq.unsafe_get(), mId, mConnectedApi, mProducerPid, mConsumerPid, ##__VA_ARGS__)
//#define BQ_LOGD(x, ...) ALOGD("[%s](this:%p,id:%d,api:%d,p:%d,c:%d) " x, mConsumerName.string(), mBq.unsafe_get(), mId, mConnectedApi, mProducerPid, mConsumerPid, ##__VA_ARGS__)
#define BQ_LOGI(x, ...) ALOGI("[%s](this:%p,id:%d,api:%d,p:%d,c:%d) " x, mConsumerName.string(), mBq.unsafe_get(), mId, mConnectedApi, mProducerPid, mConsumerPid, ##__VA_ARGS__)
//#define BQ_LOGW(x, ...) ALOGW("[%s](this:%p,id:%d,api:%d,p:%d,c:%d) " x, mConsumerName.string(), mBq.unsafe_get(), mId, mConnectedApi, mProducerPid, mConsumerPid, ##__VA_ARGS__)
#define BQ_LOGE(x, ...) ALOGE("[%s](this:%p,id:%d,api:%d,p:%d,c:%d) " x, mConsumerName.string(), mBq.unsafe_get(), mId, mConnectedApi, mProducerPid, mConsumerPid, ##__VA_ARGS__)
//#define BQP_LOGV(x, ...) LOG_PRI(ANDROID_LOG_VORBOSE, "BufferQueueProducer", "[%s](this:%p,id:%d,api:%d,p:%d,c:%d) " x, mConsumerName.string(), mBq.unsafe_get(), mId, mConnectedApi, mProducerPid, mConsumerPid, ##__VA_ARGS__)
//#define BQP_LOGD(x, ...) LOG_PRI(ANDROID_LOG_DEBUG, "BufferQueueProducer", "[%s](this:%p,id:%d,api:%d,p:%d,c:%d) " x, mConsumerName.string(), mBq.unsafe_get(), mId, mConnectedApi, mProducerPid, mConsumerPid, ##__VA_ARGS__)
#define BQP_LOGI(x, ...) LOG_PRI(ANDROID_LOG_INFO, "BufferQueueProducer", "[%s](this:%p,id:%d,api:%d,p:%d,c:%d) " x, mConsumerName.string(), mBq.unsafe_get(), mId, mConnectedApi, mProducerPid, mConsumerPid, ##__VA_ARGS__)
//#define BQP_LOGW(x, ...) LOG_PRI(ANDROID_LOG_WARNING, "BufferQueueProducer", "[%s](this:%p,id:%d,api:%d,p:%d,c:%d) " x, mConsumerName.string(), mBq.unsafe_get(), mId, mConnectedApi, mProducerPid, mConsumerPid, ##__VA_ARGS__)
#define BQP_LOGE(x, ...) LOG_PRI(ANDROID_LOG_ERROR, "BufferQueueProducer", "[%s](this:%p,id:%d,api:%d,p:%d,c:%d) " x, mConsumerName.string(), mBq.unsafe_get(), mId, mConnectedApi, mProducerPid, mConsumerPid, ##__VA_ARGS__)
//#define BQC_LOGV(x, ...) LOG_PRI(ANDROID_LOG_VORBOSE, "BufferQueueConsumer", "[%s](this:%p,id:%d,api:%d,p:%d,c:%d) " x, mConsumerName.string(), mBq.unsafe_get(), mId, mConnectedApi, mProducerPid, mConsumerPid, ##__VA_ARGS__)
//#define BQC_LOGD(x, ...) LOG_PRI(ANDROID_LOG_DEBUG, "BufferQueueConsumer", "[%s](this:%p,id:%d,api:%d,p:%d,c:%d) " x, mConsumerName.string(), mBq.unsafe_get(), mId, mConnectedApi, mProducerPid, mConsumerPid, ##__VA_ARGS__)
#define BQC_LOGI(x, ...) LOG_PRI(ANDROID_LOG_INFO, "BufferQueueConsumer", "[%s](this:%p,id:%d,api:%d,p:%d,c:%d) " x, mConsumerName.string(), mBq.unsafe_get(), mId, mConnectedApi, mProducerPid, mConsumerPid, ##__VA_ARGS__)
//#define BQC_LOGW(x, ...) LOG_PRI(ANDROID_LOG_WARNING, "BufferQueueConsumer", "[%s](this:%p,id:%d,api:%d,p:%d,c:%d) " x, mConsumerName.string(), mBq.unsafe_get(), mId, mConnectedApi, mProducerPid, mConsumerPid, ##__VA_ARGS__)
//#define BQC_LOGE(x, ...) LOG_PRI(ANDROID_LOG_ERROR, "BufferQueueConsumer", "[%s](this:%p,id:%d,api:%d,p:%d,c:%d) " x, mConsumerName.string(), mBq.unsafe_get(), mId, mConnectedApi, mProducerPid, mConsumerPid, ##__VA_ARGS__)
namespace android {
// -----------------------------------------------------------------------------

status_t getProcessName(int pid, String8& name) {
    FILE *fp = fopen(String8::format("/proc/%d/cmdline", pid), "r");
    if (NULL != fp) {
        const size_t size = 64;
        char proc_name[size];
        fgets(proc_name, size, fp);
        fclose(fp);
        name = proc_name;
        return NO_ERROR;
    }
    return INVALID_OPERATION;
}
BufferQueueDebug::BufferQueueDebug() :
    mBq(NULL),
    mId(-1),
    mConnectedApi(BufferQueueCore::NO_CONNECTED_API),
    mPid(-1),
    mProducerPid(-1),
    mConsumerPid(-1),
    mLine(false),
    mLineCnt(0),
    mDump(NULL),
    mGeneralDump(false),
    mDebugLog(false)
{
    mGedKpiCreateWrap = NULL;
    mGedKpiDestroyWrap = NULL;
    mGedKpiDequeueBufferTagWrap = NULL;
    mGedKpiQueueBufferTagWrap = NULL;
    mGedKpiAcquireBufferTagWrap = NULL;
    mGedHnd = NULL;
}

BufferQueueDebug::~BufferQueueDebug() {
    if (mDump != NULL) {
        delete mDump;
    }
}
// BufferQueueCore part
// -----------------------------------------------------------------------------
void BufferQueueDebug::onConstructor(
        wp<BufferQueueCore> bq, const String8& consumerName, const uint64_t& bqId) {
    mBq = bq;
    mPid = getpid();
    mConsumerName = consumerName;
    if (sscanf(consumerName.string(), "unnamed-%*d-%d", &mId) != 1) {
        BQ_LOGE("id info cannot be read from '%s'", consumerName.string());
    }
    if (NO_ERROR == getProcessName(mPid, mConsumerProcName)) {
        BQ_LOGI("BufferQueue core=(%d:%s)", mPid, mConsumerProcName.string());
    } else {
        BQ_LOGI("BufferQueue core=(%d:\?\?\?)", mPid);
    }

    mDump = GuiDebugModuleLoader::getInstance().CreateBQDumpInstance();
    if (mDump != NULL)
        mDump->setName(consumerName);

    mGedKpiCreateWrap = GuiDebugModuleLoader::getInstance().GedKpiCreate();
    mGedKpiDestroyWrap = GuiDebugModuleLoader::getInstance().GedKpiDestroy();
    mGedKpiDequeueBufferTagWrap = GuiDebugModuleLoader::getInstance().GedKpiDequeue();
    mGedKpiQueueBufferTagWrap = GuiDebugModuleLoader::getInstance().GedKpiQueue();
    mGedKpiAcquireBufferTagWrap = GuiDebugModuleLoader::getInstance().GedKpiAcquire();

    // check property for drawing debug line
    char value[PROPERTY_VALUE_MAX];
    property_get("debug.bq.line", value, "GOD'S IN HIS HEAVEN, ALL'S RIGHT WITH THE WORLD.");
    mLine = (-1 != consumerName.find(value));
    mLineCnt = 0;
    if (true == mLine) {
        BQ_LOGI("switch on debug line");
    }

    BufferQueueMonitor::getInstance().monitor(mBq);

    if (mGedKpiCreateWrap) {
        mGedHnd = mGedKpiCreateWrap(bqId);
        if (mGedHnd == nullptr)
            BQ_LOGE("an error ged handle");
    }
    else
    {
        ALOGE("finding createWrapPrototype() failed");
    }
}
void BufferQueueDebug::onDestructor() {
    BQ_LOGI("~BufferQueueCore");

    BufferQueueMonitor::getInstance().unmonitor(mBq);

    if (mGedHnd != nullptr)
    {
        if (mGedKpiCreateWrap) {
            mGedKpiDestroyWrap(mGedHnd);
        }
        else
        {
            ALOGE("finding createWrapPrototype() failed");
        }
    }
}
void BufferQueueDebug::onDump(String8 &result, const String8& prefix) const {
    char value[PROPERTY_VALUE_MAX];
    property_get("debug.bq.log", value, "0");
    mDebugLog = (atoi(value) == 0) ? false : true;
    if (mDump != NULL)
        mDump->dump(result, prefix.string());
}
void BufferQueueDebug::onFreeBufferLocked(const int slot) {
    if (mDump != NULL)
        mDump->onFreeBuffer(slot);
}
// BufferQueueConsumer part
// -----------------------------------------------------------------------------
void BufferQueueDebug::onSetConsumerName(const String8& consumerName) {
    mConsumerName = consumerName;
    if (mConsumerName.length() > IONDBG_LENGDTH - 1) {
        String8 shortened;
        int diff_length = IONDBG_LENGDTH - IONDBG_PREFIX_LENGDTH - 2;
        shortened.append(mConsumerName.string(), IONDBG_PREFIX_LENGDTH);
        shortened.append("_");
        shortened.append(mConsumerName.string() + (mConsumerName.length() - diff_length), diff_length);
        mMiniConusmerName = shortened;
    } else {
        mMiniConusmerName = mConsumerName;
    }

    // update dump info
    if (mDump != NULL)
        mDump->setName(mConsumerName);

    // check property for drawing debug line
    BQC_LOGI("setConsumerName: %s", mConsumerName.string());
    char value[PROPERTY_VALUE_MAX];
    property_get("debug.bq.line", value, "GOD'S IN HIS HEAVEN, ALL'S RIGHT WITH THE WORLD.");
    mLine = (-1 != mConsumerName.find(value));
    mLineCnt = 0;
    if (true == mLine) {
        BQ_LOGI("switch on debug line");
    }
}
void BufferQueueDebug::onAcquire(
        const int buf,
        const sp<GraphicBuffer>& gb,
        const sp<Fence>& fence,
        const int64_t& timestamp,
        const uint32_t& transform,
        const BufferItem* const buffer) {
    // also inform acquireBuffer to mDump
    if (mDump != NULL)
        mDump->onAcquireBuffer(buf, gb, fence, timestamp, transform);

    // draw white debug line
    if (true == mLine) {
        if (buffer->mFence.get())
            buffer->mFence->waitForever("BufferItemConsumer::acquireBuffer");
        if (mDump != NULL)
            mDump->drawDebugLineToGraphicBuffer(gb, mLineCnt, 0xFF);
        mLineCnt += 1;
    }

    // notify ged about acquire events for fast DVFS
    const sp<BufferQueueCore> core = mBq.promote();
    if (core != nullptr && mGedHnd != nullptr && gb != nullptr) {
        if (mGedKpiAcquireBufferTagWrap)
        {
            const int32_t err = mGedKpiAcquireBufferTagWrap(mGedHnd, mProducerPid, reinterpret_cast<intptr_t>(gb->handle));
            if (err != GED_OK)
                BQP_LOGE("ged queue fail: hnd:%p api:%d pid:%d size:%d" PRIu64 ,
                    mGedHnd, mConnectedApi, mProducerPid, static_cast<int>(core->mQueue.size()));
        }
    } else {
        if (core == nullptr)
            BQP_LOGE("queueBuffer: BufferQueueCore promoting failed");
        else if (gb == nullptr)
            BQP_LOGE("queueBuffer: gb is null");
        else
        {
            if (mGedKpiCreateWrap)
                BQP_LOGE("an error ged handle");
        }
    }
}
void BufferQueueDebug::onRelease(const int buf) {
    if (mDump != NULL)
        mDump->onReleaseBuffer(buf);
}
void BufferQueueDebug::onConsumerConnect(
        const sp<IConsumerListener>& consumerListener,
        const bool controlledByApp) {
    // check if local or remote connection by the consumer listener
    // (in most cases, consumer side is a local connection)
    mConsumerPid = (NULL != consumerListener->asBinder(consumerListener)->localBinder())
                 ? getpid()
                 : IPCThreadState::self()->getCallingPid();
    String8 name;
    if (NO_ERROR == getProcessName(mConsumerPid, mConsumerProcName)) {
        BQC_LOGI("connect(C): consumer=(%d:%s) controlledByApp=%s",
            mConsumerPid, mConsumerProcName.string(), controlledByApp ? "true" : "false");
    } else {
        BQC_LOGI("connect(C): consumer=(%d:\?\?\?) controlledByApp=%s",
            mConsumerPid, controlledByApp ? "true" : "false");
    }
}
void BufferQueueDebug::onConsumerDisconnectHead() {
    mConsumerPid = -1;
}
void BufferQueueDebug::onConsumerDisconnectTail() {
    if (mDump != NULL)
        mDump->onConsumerDisconnect();
}
// BufferQueueProducer part
// -----------------------------------------------------------------------------
void BufferQueueDebug::setIonInfo(const sp<GraphicBuffer>& gb) {
    if (gb->handle != NULL) {
        if (mDump != NULL)
            mDump->onSetIonInfo(gb, mProducerPid, mId, IONDBG_LENGDTH, mMiniConusmerName);
    } else {
        BQP_LOGE("handle of graphic buffer is NULL when producer set ION info");
    }
}
void BufferQueueDebug::onDequeue(sp<GraphicBuffer>& gb, sp<Fence>& fence) {
    if (mGedHnd != nullptr && gb != nullptr) {
        const int32_t dupFenceFd = fence->isValid() ? fence->dup() : -1;
        if (mGedKpiDequeueBufferTagWrap)
        {
            const int32_t err = mGedKpiDequeueBufferTagWrap(
                mGedHnd, mConnectedApi, dupFenceFd, mProducerPid, reinterpret_cast<intptr_t>(gb->handle));
            if (err != GED_OK)
                BQP_LOGE("ged dequeue fail: hnd:%p api:%d pid:%d" PRIu64 ,
                    mGedHnd, mConnectedApi, mProducerPid);
        }
        ::close(dupFenceFd);
    } else {
        if (gb == nullptr)
            BQP_LOGE("queueBuffer: gb is null");
        else
        {
            if (mGedKpiCreateWrap)
                BQP_LOGE("an error ged handle line:%d", __LINE__);
        }
    }
}
void BufferQueueDebug::onQueue(const sp<GraphicBuffer>& gb, const sp<Fence>& fence) {
    // count FPS after queueBuffer() success, for producer side
    if (mDump != NULL) {
        String8 result;
        if (mDump->onCheckFps(&result))
            BQP_LOGI("%s", result.string());

    }

    const sp<BufferQueueCore> core = mBq.promote();
    if (core != nullptr && mGedHnd != nullptr && gb != nullptr) {
        const int32_t dupFenceFd = fence->isValid() ? fence->dup() : -1;
        if (mGedKpiQueueBufferTagWrap)
        {
            const int32_t err = mGedKpiQueueBufferTagWrap(
                mGedHnd, mConnectedApi, dupFenceFd, mProducerPid, static_cast<int32_t>(core->mQueue.size()), reinterpret_cast<intptr_t>(gb->handle));
            if (err != GED_OK)
                BQP_LOGE("ged queue fail: hnd:%p api:%d pid:%d size:%d" PRIu64 ,
                    mGedHnd, mConnectedApi, mProducerPid, static_cast<int>(core->mQueue.size()));
        }
        ::close(dupFenceFd);
    } else {
        if (core == nullptr)
            BQP_LOGE("queueBuffer: BufferQueueCore promoting failed");
        else if (gb == nullptr)
            BQP_LOGE("queueBuffer: gb is null");
        else
        {
            if (mGedKpiCreateWrap)
                BQP_LOGE("an error ged handle");
        }
    }
}
void BufferQueueDebug::onProducerConnect(
        const sp<IBinder>& token, const int api, bool producerControlledByApp) {
    mProducerPid = (token != NULL && NULL != token->localBinder())
        ? getpid()
        : IPCThreadState::self()->getCallingPid();
    mConnectedApi = api;
    if (NO_ERROR == getProcessName(mProducerPid, mProducerProcName)) {
        BQP_LOGI("connect(P): api=%d producer=(%d:%s) producerControlledByApp=%s", mConnectedApi,
                mProducerPid, mProducerProcName.string(), producerControlledByApp ? "true" : "false");
    } else {
        BQP_LOGI("connect(P): api=%d producer=(%d:\?\?\?) producerControlledByApp=%s", mConnectedApi,
                mProducerPid, producerControlledByApp ? "true" : "false");
    }
}
void BufferQueueDebug::onProducerDisconnect() {
    mProducerPid = -1;
}
// -----------------------------------------------------------------------------
ANDROID_SINGLETON_STATIC_INSTANCE(GuiDebugModuleLoader);

GuiDebugModuleLoader::GuiDebugModuleLoader() :
    mBQDumpSoHandle(NULL),
    mCreateBQDumpInstancePtr(NULL)
{
    typedef BufferQueueDumpAPI *(*createBQdumpPrototype)();
    mBQDumpSoHandle = dlopen("libgui_debug.so", RTLD_LAZY);
    if (mBQDumpSoHandle) {
        mCreateBQDumpInstancePtr = reinterpret_cast<createBQdumpPrototype>(dlsym(mBQDumpSoHandle, "createInstance"));
        if (mCreateBQDumpInstancePtr == NULL) {
            ALOGD("Can't load func mCreateBQdumpPtr");
        }
    } else {
        ALOGD("Can't load libgui_debug");
    }

    mGedKpiCreate = NULL;
    mGedKpiDestroy = NULL;
    mGedKpiDequeueBuffer = NULL;
    mGedKpiQueueBuffer = NULL;
    mGedKpiAcquireBuffer = NULL;
    // used to notify ged about queue/acquire events for fast DVFS
    mGedKpiSoHandle = dlopen("libged_kpi.so", RTLD_LAZY);
    if (mGedKpiSoHandle) {
        mGedKpiCreate = reinterpret_cast<createWrapPrototype>(dlsym(mGedKpiSoHandle, "ged_kpi_create_wrap"));
        if (NULL == mGedKpiCreate) {
            ALOGE("finding createWrapPrototype() failed [%s]", dlerror());
        }

        mGedKpiDestroy = reinterpret_cast<destroyWrapPrototype>(dlsym(mGedKpiSoHandle, "ged_kpi_destroy_wrap"));
        if (NULL == mGedKpiDestroy) {
            ALOGE("finding destroyWrapPrototype() failed [%s]", dlerror());
        }

        mGedKpiDequeueBuffer = reinterpret_cast<dequeueBufferTagWrapPrototype>(dlsym(mGedKpiSoHandle, "ged_kpi_dequeue_buffer_tag_wrap"));
        if (NULL == mGedKpiDequeueBuffer) {
            ALOGE("finding dequeueBufferTagWrapPrototype() failed [%s]", dlerror());
        }

        mGedKpiQueueBuffer = reinterpret_cast<queueBufferTagWrapPrototype>(dlsym(mGedKpiSoHandle, "ged_kpi_queue_buffer_tag_wrap"));
        if (NULL == mGedKpiQueueBuffer) {
            ALOGE("finding queueBufferTagWrapPrototype() failed [%s]", dlerror());
        }

        mGedKpiAcquireBuffer = reinterpret_cast<acquireBufferTagWrapPrototype>(dlsym(mGedKpiSoHandle, "ged_kpi_acquire_buffer_tag_wrap"));
        if (NULL == mGedKpiAcquireBuffer) {
            ALOGE("finding acquireBufferTagWrapPrototype() failed [%s]", dlerror());
        }
    } else {
        ALOGE("open libged_kpi.so failed");
    }
}

GuiDebugModuleLoader::~GuiDebugModuleLoader() {
    if (mBQDumpSoHandle != NULL) {
        dlclose(mBQDumpSoHandle);
    }

    if(mGedKpiSoHandle != NULL) {
        dlclose(mGedKpiSoHandle);
    }
}

BufferQueueDumpAPI* GuiDebugModuleLoader::CreateBQDumpInstance() {
    if (mCreateBQDumpInstancePtr) {
        return mCreateBQDumpInstancePtr();
    } else {
        return NULL;
    }
}

createWrapPrototype GuiDebugModuleLoader::GedKpiCreate() {
    if (mGedKpiCreate) {
        return mGedKpiCreate;
    } else {
        return NULL;
    }
}

destroyWrapPrototype GuiDebugModuleLoader::GedKpiDestroy() {
    if (mGedKpiDestroy) {
        return mGedKpiDestroy;
    } else {
        return NULL;
    }
}

dequeueBufferTagWrapPrototype GuiDebugModuleLoader::GedKpiDequeue() {
    if (mGedKpiDequeueBuffer) {
        return mGedKpiDequeueBuffer;
    } else {
        return NULL;
    }
}

queueBufferTagWrapPrototype GuiDebugModuleLoader::GedKpiQueue() {
    if (mGedKpiQueueBuffer) {
        return mGedKpiQueueBuffer;
    } else {
        return NULL;
    }
}

acquireBufferTagWrapPrototype GuiDebugModuleLoader::GedKpiAcquire() {
    if (mGedKpiAcquireBuffer) {
        return mGedKpiAcquireBuffer;
    } else {
        return NULL;
    }
}

}; // namespace android
