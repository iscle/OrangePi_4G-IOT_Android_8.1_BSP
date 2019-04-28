#define LOG_TAG "BufferQueueMonitor"
#define ATRACE_TAG ATRACE_TAG_GRAPHICS

#include <cutils/log.h>
// #include <cutils/process_name.h>
#include <gui/BufferQueueCore.h>
#include <gui/mediatek/BufferQueueMonitor.h>

//#define BQM_LOGV(x, ...) ALOGV("[BufferQueueMonitor] " x, ##__VA_ARGS__)
//#define BQM_LOGD(x, ...) ALOGD("[BufferQueueMonitor] " x, ##__VA_ARGS__)
#define BQM_LOGI(x, ...) ALOGI("[BufferQueueMonitor] " x, ##__VA_ARGS__)
//#define BQM_LOGW(x, ...) ALOGW("[BufferQueueMonitor] " x, ##__VA_ARGS__)
//#define BQM_LOGE(x, ...) ALOGE("[BufferQueueMonitor] " x, ##__VA_ARGS__)

namespace android {


IMPLEMENT_META_GUIEXTMONITOR(BufferQueueMonitor, wp<BufferQueueCore>, "BQM");

status_t BufferQueueMonitor::dump(String8& result, const char* /*prefix*/) {
    size_t listSz;
    Vector<sp<BufferQueueCore>> bqCores;

    // add strong ref to another list
    // avoid ~BufferQueueCore() happens in the mutex scope, which causes deadlock
    {
        Mutex::Autolock _l(mLock);
        listSz = mItemList.size();

        for (size_t i = 0; i < listSz; i++) {
            wp<BufferQueueCore> pBq = mItemList.keyAt(i);
            sp<BufferQueueCore> bq = pBq.promote();
            if (bq != NULL) {
                bqCores.add(bq);
            } else {
                BQM_LOGI("kickDump() failed because BufferQueue(%p) is dead", pBq.unsafe_get());
            }
        }
    }

    result.appendFormat("\t  [%p]    BufferQueueCnt : %zu\n", this, listSz);
    result.append("\t  -----------------------\n");

    listSz = bqCores.size();
    for (size_t i = 0; i < listSz; i++) {
        sp<BufferQueueCore> bq = bqCores.itemAt(i);
        if (bq != NULL) {
            String8 prefix = String8("            ");
            result.appendFormat("           %zu)\n",i+1);
            bq->dumpState(prefix, &result);
        }
    }

    result.append("\t  -----------------------\n");
    return NO_ERROR;
}


}; // namespace android
