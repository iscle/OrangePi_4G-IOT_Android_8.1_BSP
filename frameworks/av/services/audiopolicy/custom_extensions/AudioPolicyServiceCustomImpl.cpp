#define LOG_TAG "AudioPolicyServiceCustomImpl"

#include "Configuration.h"
#include <stdint.h>
#include <sys/time.h>
#include <binder/IServiceManager.h>
#include <utils/Log.h>
#include <cutils/properties.h>
#include <binder/IPCThreadState.h>
#include <utils/String16.h>
#include <utils/threads.h>
#include "AudioPolicyService.h"
#include "ServiceUtilities.h"
#include <hardware_legacy/power.h>
#include <media/AudioEffect.h>
#include <media/AudioParameter.h>
#include <system/audio.h>
#include <system/audio_policy.h>
#include "AudioPolicyServiceCustomImpl.h"
#if defined(MTK_AUDIO)
#include <cutils/log.h>
#include <AudioPolicyParameters.h>
#include "AudioToolkit.h"
#endif
#include <media/MtkLogger.h>
#define MTK_LOG_LEVEL_SILENCE 4 // only enable if necessary

namespace android {

Mutex AudioPolicyServiceCustomImpl::gInstanceLock;
AudioPolicyServiceCustomImpl *AudioPolicyServiceCustomImpl::mAudioPolicyServiceCustomImpl = NULL;

AudioPolicyServiceCustomImpl *AudioPolicyServiceCustomImpl::get()
{
    Mutex::Autolock _l(gInstanceLock);

    if (mAudioPolicyServiceCustomImpl == NULL)
    {
        mAudioPolicyServiceCustomImpl = new AudioPolicyServiceCustomImpl();
    }
    ALOG_ASSERT(mAudioPolicyServiceCustomImpl != NULL, "Allocate AudioPolicyServiceCustomImpl fail");
    return mAudioPolicyServiceCustomImpl;
}

AudioPolicyServiceCustomImpl::AudioPolicyServiceCustomImpl()
{
    InitializeMTKLogLevel("af.policy.debug");
}

AudioPolicyServiceCustomImpl::~AudioPolicyServiceCustomImpl()
{
    ALOGD("%s()", __FUNCTION__);
}

status_t AudioPolicyServiceCustomImpl::common_setVolumeLog(audio_stream_type_t stream, float volume, audio_io_handle_t IO)
{
#if defined(MTK_AUDIO_DEBUG)
    if (stream > AUDIO_STREAM_DTMF) {
        MTK_ALOGS(MT_AUDIO_ENG_BUILD_LEVEL, "AudioCommandThread() processing set volume stream %d, \
            volume %f, output %d", stream, volume, IO);
    } else {
        MTK_ALOGS(MT_AUDIO_USERDEBUG_BUILD_LEVEL, "AudioCommandThread() processing set volume stream %d, \
                volume %f, output %d", stream, volume, IO);
    }
    return NO_ERROR;
#else
    (void) stream;
    (void) volume;
    (void) IO;
    return INVALID_OPERATION;
#endif
}

status_t AudioPolicyServiceCustomImpl::common_getDecodedData(String8 strPara, size_t len, void *ptr)
{
#if defined(MTK_AUDIO_GAIN_TABLE) || defined(MTK_AUDIO_GAIN_NVRAM)
    size_t sz_in = strPara.size();
    size_t sz_needed = Base64_OutputSize(false, sz_in);
    size_t sz_dec;
    status_t ret = NO_ERROR;

    if (sz_in <= 0) {
        return NO_ERROR;
    }

    //ALOGD("%s in, len = %d", __FUNCTION__, len);
    unsigned char *buf_dec = new unsigned char[sz_needed];
    sz_dec = Base64_Decode(strPara.string(), buf_dec, sz_in);

    if (sz_dec > sz_needed || sz_dec <= sz_needed - 3) {
        ALOGE("%s(), Decode Error!!!after decode (%s), sz_in(%zu), sz_needed(%zu), sz_dec(%zu)",
            __FUNCTION__, buf_dec, sz_in, sz_needed, sz_dec);
    } else {
        // sz_needed-3 < sz_dec <= sz_needed
        //ALOGD("%s(), after decode, sz_in(%d), sz_dec(%d) len(%d) sizeof(ret)=%d",
        //    __FUNCTION__, sz_in, sz_dec, len, sizeof(ret));
        //print_hex_buffer (sz_dec, buf_dec);
    }

    if ((len == 0) || (len == sz_dec-sizeof(ret))) {
       if (len) {
           ret = (status_t)*(buf_dec);
           unsigned char *buff = (buf_dec + 4);
           memcpy(ptr, buff, len);
       } else {
          const char * IntPtr = (char *)buf_dec;
          ret = atoi(IntPtr);
          //ALOGD("%s len = 0 ret(%d)", __FUNCTION__, ret);
       }
    } else {
       ALOGD("%s decoded buffer isn't right format", __FUNCTION__);
    }

    if (buf_dec != NULL) {
        delete[] buf_dec;
    }

    return ret;
#else
    (void) strPara;
    (void) len;
    (void) ptr;
    return INVALID_OPERATION;
#endif
}

status_t AudioPolicyServiceCustomImpl::gainTable_getAudioData(int par1, size_t len, void *ptr)
{
#if defined(MTK_AUDIO_GAIN_TABLE) || defined(MTK_AUDIO_GAIN_NVRAM)
    sp<IAudioFlinger> af = AudioSystem::get_audio_flinger();
    if (af == 0) {
        return PERMISSION_DENIED;
    }

    String8 keyToGetAudioGain;
    if (par1 == GET_AUDIO_POLICY_VOL_FROM_VER1_DATA) {
        keyToGetAudioGain = String8("GetAudioGainFromNvRam=");
    } else {
        ALOGE("%s Error type par1 = %d", __FUNCTION__, par1);
        return BAD_VALUE;
    }

    String8 returnValue = af->getParameters(0, keyToGetAudioGain);
    String8 newval; //remove "GetBuffer="
    newval.appendFormat("%s", returnValue.string() + keyToGetAudioGain.size());
    //ALOGD ("%s(), newval = %s", __FUNCTION__, newval.string());

    return common_getDecodedData(newval, len, ptr);
#else
    (void) par1;
    (void) len;
    (void) ptr;
    return INVALID_OPERATION;
#endif
}

} // namespace android
