#ifndef ANDROID_HARDWARE_CONFIGSTORE_V1_0_SURFACEFLINGERCONFIGS_H
#define ANDROID_HARDWARE_CONFIGSTORE_V1_0_SURFACEFLINGERCONFIGS_H

#include <android/hardware/configstore/1.0/ISurfaceFlingerConfigs.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>

namespace android {
namespace hardware {
namespace configstore {
namespace V1_0 {
namespace implementation {

using ::android::hardware::configstore::V1_0::ISurfaceFlingerConfigs;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

struct SurfaceFlingerConfigs : public ISurfaceFlingerConfigs {
    // Methods from
    // ::android::hardware::configstore::V1_0::ISurfaceFlingerConfigs follow.
    Return<void> vsyncEventPhaseOffsetNs(vsyncEventPhaseOffsetNs_cb _hidl_cb) override;
    Return<void> vsyncSfEventPhaseOffsetNs(vsyncSfEventPhaseOffsetNs_cb _hidl_cb) override;
    Return<void> useContextPriority(useContextPriority_cb _hidl_cb) override;
    Return<void> hasWideColorDisplay(hasWideColorDisplay_cb _hidl_cb) override;
    Return<void> hasHDRDisplay(hasHDRDisplay_cb _hidl_cb) override;
    Return<void> presentTimeOffsetFromVSyncNs(presentTimeOffsetFromVSyncNs_cb _hidl_cb) override;
    Return<void> useHwcForRGBtoYUV(useHwcForRGBtoYUV_cb _hidl_cb) override;
    Return<void> maxVirtualDisplaySize(maxVirtualDisplaySize_cb _hidl_cb) override;
    Return<void> hasSyncFramework(hasSyncFramework_cb _hidl_cb) override;
    Return<void> useVrFlinger(useVrFlinger_cb _hidl_cb) override;
    Return<void> maxFrameBufferAcquiredBuffers(maxFrameBufferAcquiredBuffers_cb _hidl_cb) override;
    Return<void> startGraphicsAllocatorService(startGraphicsAllocatorService_cb _hidl_cb) override;

    // Methods from ::android::hidl::base::V1_0::IBase follow.
};

}  // namespace implementation
}  // namespace V1_0
}  // namespace configstore
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_CONFIGSTORE_V1_0_SURFACEFLINGERCONFIGS_H
