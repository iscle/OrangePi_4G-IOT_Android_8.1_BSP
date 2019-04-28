#define LOG_TAG "Fence"

#include <inttypes.h>
#include <sync/sync.h>
#include <ui/Fence.h>
#include <unistd.h>

#include <cutils/log.h>
#include <utils/String8.h>

// ---------------------------------------------------------------------------

namespace android {

void Fence::dump(int fd) {
    if (-1 == fd) return;

    struct sync_fence_info_data *info = sync_fence_info(fd);
    if (info) {
        struct sync_pt_info *pt_info = NULL;
        // status: active(0) signaled(1) error(<0)
        ALOGI("fence(%s) status(%d)", info->name, info->status);

        // iterate active/error sync points
        while ((pt_info = sync_pt_info(info, pt_info))) {
            if (NULL != pt_info && pt_info->status <= 0) {
                uint64_t ts_sec = pt_info->timestamp_ns / 1000000000LL;
                uint64_t ts_usec = (pt_info->timestamp_ns % 1000000000LL) / 1000LL;

                ALOGI("sync point: timeline(%s) drv(%s) status(%d) sync_drv(%u) timestamp(%" PRIu64 ".%06" PRIu64 ")",
                    pt_info->obj_name, pt_info->driver_name, pt_info->status,
                    *reinterpret_cast<uint32_t*>(pt_info->driver_data), ts_sec, ts_usec);
            }
        }
        sync_fence_info_free(info);
    }
}

} // namespace android
