#ifndef ANDROID_AUDIOPOLICYSERVICECUSTOMIMPL_H
#define ANDROID_AUDIOPOLICYSERVICECUSTOMIMPL_H

#include <cutils/misc.h>
#include <cutils/config_utils.h>
#include <cutils/compiler.h>
#include <utils/String8.h>
#include <utils/Vector.h>
#include <utils/SortedVector.h>
#include <binder/BinderService.h>
#include <system/audio.h>
#include <system/audio_policy.h>
#include <media/IAudioPolicyService.h>
#include <media/ToneGenerator.h>
#include <media/AudioEffect.h>
#include <media/AudioPolicy.h>
#include "managerdefault/AudioPolicyManager.h"

namespace android {
class AudioPolicyServiceCustomImpl {
    public:
        virtual ~AudioPolicyServiceCustomImpl();
        static AudioPolicyServiceCustomImpl* get();
        static status_t gainTable_getAudioData(int par1, size_t len, void *ptr);
        static status_t common_setVolumeLog(audio_stream_type_t stream, float volume, audio_io_handle_t IO);
        static status_t common_getDecodedData(String8 strPara, size_t len, void *ptr);
    protected:
        AudioPolicyServiceCustomImpl();
    private:
        static AudioPolicyServiceCustomImpl *mAudioPolicyServiceCustomImpl;
        static Mutex gInstanceLock;
};

};

#endif // ANDROID_AUDIOPOLICYSERVICE_CUSTOM_H
