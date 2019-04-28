#define LOG_TAG "android.hardware.nfc@1.0-service"

#include <android/hardware/nfc/1.0/INfc.h>

#include <hidl/LegacySupport.h>

// Generated HIDL files
using android::hardware::nfc::V1_0::INfc;
using android::hardware::defaultPassthroughServiceImplementation;

int main() {
    return defaultPassthroughServiceImplementation<INfc>();
}
