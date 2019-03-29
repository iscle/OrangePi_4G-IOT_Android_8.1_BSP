/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define TAG "NativeMediaDrm"

#include <utils/Log.h>
#include <sys/types.h>

#include <string>
#include <vector>

#include <assert.h>
#include <jni.h>
#include <JNIHelp.h>

#include <android/native_window_jni.h>

#include "AMediaObjects.h"

#include "media/NdkMediaCodec.h"
#include "media/NdkMediaCrypto.h"
#include "media/NdkMediaDrm.h"
#include "media/NdkMediaExtractor.h"
#include "media/NdkMediaFormat.h"
#include "media/NdkMediaMuxer.h"

typedef std::vector<uint8_t> Uuid;

struct fields_t {
    jfieldID surface;
    jfieldID mimeType;
    jfieldID audioUrl;
    jfieldID videoUrl;
};

struct PlaybackParams {
    jobject surface;
    jstring mimeType;
    jstring audioUrl;
    jstring videoUrl;
};

static fields_t gFieldIds;
static bool gGotVendorDefinedEvent = false;

static const size_t kPlayTimeSeconds = 30;
static const size_t kUuidSize = 16;

static const uint8_t kClearKeyUuid[kUuidSize] = {
    0x10, 0x77, 0xef, 0xec, 0xc0, 0xb2, 0x4d, 0x02,
    0xac, 0xe3, 0x3c, 0x1e, 0x52, 0xe2, 0xfb, 0x4b
};

static const uint8_t kClearkeyPssh[] = {
    // BMFF box header (4 bytes size + 'pssh')
    0x00, 0x00, 0x00, 0x34, 0x70, 0x73, 0x73, 0x68,
    // full box header (version = 1 flags = 0)
    0x01, 0x00, 0x00, 0x00,
    // system id
    0x10, 0x77, 0xef, 0xec, 0xc0, 0xb2, 0x4d, 0x02,
    0xac, 0xe3, 0x3c, 0x1e, 0x52, 0xe2, 0xfb, 0x4b,
    // number of key ids
    0x00, 0x00, 0x00, 0x01,
    // key id
    0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30,
    0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30,
    // size of data, must be zero
    0x00, 0x00, 0x00, 0x00
};

static const uint8_t kKeyRequestData[] = {
    0x7b, 0x22, 0x6b, 0x69, 0x64,
    0x73, 0x22, 0x3a, 0x5b, 0x22,
    0x4d, 0x44, 0x41, 0x77, 0x4d,
    0x44, 0x41, 0x77, 0x4d, 0x44,
    0x41, 0x77, 0x4d, 0x44, 0x41,
    0x77, 0x4d, 0x44, 0x41, 0x77,
    0x4d, 0x41, 0x22, 0x5d, 0x2c,
    0x22, 0x74, 0x79, 0x70, 0x65,
    0x22, 0x3a, 0x22, 0x74, 0x65,
    0x6d, 0x70, 0x6f, 0x72, 0x61,
    0x72, 0x79, 0x22, 0x7d
};

static const size_t kKeyRequestSize = sizeof(kKeyRequestData);

// base 64 encoded JSON response string, must not contain padding character '='
static const char kResponse[] = "{\"keys\":[{\"kty\":\"oct\"," \
        "\"kid\":\"MDAwMDAwMDAwMDAwMDAwMA\",\"k\":" \
        "\"Pwoz80CYueIrwHjgobXoVA\"}]}";

static bool isUuidSizeValid(Uuid uuid) {
    return (uuid.size() == kUuidSize);
}

static std::vector<uint8_t> jbyteArrayToVector(
    JNIEnv* env, jbyteArray const &byteArray) {
    uint8_t* buffer = reinterpret_cast<uint8_t*>(
        env->GetByteArrayElements(byteArray, /*is_copy*/NULL));
    std::vector<uint8_t> vector;
    for (jsize i = 0; i < env->GetArrayLength(byteArray); ++i) {
        vector.push_back(buffer[i]);
    }
    return vector;
}

static Uuid jbyteArrayToUuid(JNIEnv* env, jbyteArray const &uuid) {
    Uuid juuid;
    juuid.resize(0);
    if (uuid != NULL) {
        juuid = jbyteArrayToVector(env, uuid);
    }
    return juuid;
}

extern "C" jboolean Java_android_media_cts_NativeClearKeySystemTest_isCryptoSchemeSupportedNative(
    JNIEnv* env, jclass /*clazz*/, jbyteArray uuid) {

    if (NULL == uuid) {
        jniThrowException(env, "java/lang/NullPointerException", "null uuid");
        return JNI_FALSE;
    }

    Uuid juuid = jbyteArrayToUuid(env, uuid);
    if (isUuidSizeValid(juuid)) {
         return AMediaDrm_isCryptoSchemeSupported(&juuid[0], NULL);
    } else {
          jniThrowExceptionFmt(env, "java/lang/IllegalArgumentException",
                  "invalid UUID size, expected %u bytes", kUuidSize);
    }
    return JNI_FALSE;
}

void initPlaybackParams(JNIEnv* env, const jobject &playbackParams, PlaybackParams &params) {
    params.surface = env->GetObjectField(
        playbackParams, gFieldIds.surface);

    params.mimeType = static_cast<jstring>(env->GetObjectField(
        playbackParams, gFieldIds.mimeType));

    params.audioUrl = static_cast<jstring>(env->GetObjectField(
        playbackParams, gFieldIds.audioUrl));

    params.videoUrl = static_cast<jstring>(env->GetObjectField(
        playbackParams, gFieldIds.videoUrl));
}

extern "C" jboolean Java_android_media_cts_NativeClearKeySystemTest_testGetPropertyStringNative(
    JNIEnv* env, jclass clazz, jbyteArray uuid,
    jstring name, jobject outValue) {

    if (NULL == uuid || NULL == name || NULL == outValue) {
        jniThrowException(env, "java/lang/NullPointerException",
                "One or more null input parameters");
        return JNI_FALSE;
    }

    Uuid juuid = jbyteArrayToUuid(env, uuid);
    if (!isUuidSizeValid(juuid)) {
        jniThrowExceptionFmt(env, "java/lang/IllegalArgumentException",
                "invalid UUID size, expected %u bytes", kUuidSize);
        return JNI_FALSE;
    }

    AMediaObjects aMediaObjects;
    aMediaObjects.setDrm(AMediaDrm_createByUUID(&juuid[0]));
    if (NULL == aMediaObjects.getDrm()) {
        jniThrowException(env, "java/lang/RuntimeException", "null MediaDrm");
        return JNI_FALSE;
    }

    const char *utf8_name = env->GetStringUTFChars(name, NULL);
    const char *utf8_outValue = NULL;
    media_status_t status = AMediaDrm_getPropertyString(aMediaObjects.getDrm(),
            utf8_name, &utf8_outValue);
    env->ReleaseStringUTFChars(name, utf8_name);

    if (NULL != utf8_outValue) {
        clazz = env->GetObjectClass(outValue);
        jmethodID mId = env->GetMethodID (clazz, "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuffer;");
        jstring outString = env->NewStringUTF(
                static_cast<const char *>(utf8_outValue));
        env->CallObjectMethod(outValue, mId, outString);
    } else {
        jniThrowExceptionFmt(env, "java/lang/RuntimeException",
                "get property string returns %d", status);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" jboolean Java_android_media_cts_NativeClearKeySystemTest__testPsshNative(
    JNIEnv* env, jclass /*clazz*/, jbyteArray uuid, jstring videoUrl) {

    if (NULL == uuid || NULL == videoUrl) {
        jniThrowException(env, "java/lang/NullPointerException",
                "null uuid or null videoUrl");
        return JNI_FALSE;
    }

    Uuid juuid = jbyteArrayToUuid(env, uuid);
    if (!isUuidSizeValid(juuid)) {
        jniThrowExceptionFmt(env, "java/lang/IllegalArgumentException",
                "invalid UUID size, expected %u bytes", kUuidSize);
        return JNI_FALSE;
    }

    AMediaObjects aMediaObjects;
    aMediaObjects.setVideoExtractor(AMediaExtractor_new());
    const char* url = env->GetStringUTFChars(videoUrl, 0);
    if (url) {
        media_status_t status = AMediaExtractor_setDataSource(
            aMediaObjects.getVideoExtractor(), url);
        env->ReleaseStringUTFChars(videoUrl, url);

        if (status != AMEDIA_OK) {
            jniThrowExceptionFmt(env, "java/lang/RuntimeException",
                    "set video data source error=%d", status);
            return JNI_FALSE;
        }
    }

    PsshInfo* psshInfo = AMediaExtractor_getPsshInfo(aMediaObjects.getVideoExtractor());
    if (psshInfo == NULL) {
        jniThrowException(env, "java/lang/RuntimeException", "null psshInfo");
        return JNI_FALSE;
    }

    jboolean testResult = JNI_FALSE;
    for (size_t i = 0; i < psshInfo->numentries; i++) {
        PsshEntry *entry = &psshInfo->entries[i];

        if (0 == memcmp(entry->uuid, kClearKeyUuid, sizeof(entry->uuid))) {
            aMediaObjects.setDrm(AMediaDrm_createByUUID(&juuid[0]));
            if (aMediaObjects.getDrm()) {
                testResult = JNI_TRUE;
            } else {
                ALOGE("Failed to create media drm=%zd", i);
                testResult = JNI_FALSE;
            }
            break;
        }
    }
    return testResult;
}

static bool isVideo(const char* mime) {
    return !strncmp(mime, "video/", 6) ? true : false;
}

static bool isAudio(const char* mime) {
    return !strncmp(mime, "audio/", 6) ? true : false;
}

static void addTrack(const AMediaFormat* format,
        const char* mime, const AMediaCrypto* crypto,
        const ANativeWindow* window, AMediaCodec** codec) {

    *codec = AMediaCodec_createDecoderByType(mime);
    if (codec == NULL) {
        ALOGE("cannot create codec for %s", mime);
        return;
    }

    AMediaCodec_configure(*codec, format,
            const_cast<ANativeWindow*>(window),
            const_cast<AMediaCrypto*>(crypto), 0);
}

static void addTracks(const AMediaExtractor* extractor,
        const AMediaCrypto* crypto, const ANativeWindow* window,
        AMediaCodec** codec) {
    size_t numTracks = AMediaExtractor_getTrackCount(
        const_cast<AMediaExtractor*>(extractor));

    AMediaFormat* trackFormat = NULL;
    for (size_t i = 0; i < numTracks; ++i) {
        trackFormat = AMediaExtractor_getTrackFormat(
            const_cast<AMediaExtractor*>(extractor), i);
        if (trackFormat) {
            ALOGV("track %zd format: %s", i,
                    AMediaFormat_toString(trackFormat));

            const char* mime = "";
            if (!AMediaFormat_getString(
                trackFormat, AMEDIAFORMAT_KEY_MIME, &mime)) {
                ALOGE("no mime type");

                AMediaFormat_delete(trackFormat);
                return;
            } else if (isAudio(mime) || isVideo(mime)) {
                AMediaExtractor_selectTrack(
                    const_cast<AMediaExtractor*>(extractor), i);
                ALOGV("track %zd codec format: %s", i,
                        AMediaFormat_toString(trackFormat));

                addTrack(trackFormat, mime, crypto, window, codec);
                AMediaCodec_start(*codec);
                AMediaCodec_flush(*codec);
                AMediaExtractor_seekTo(
                    const_cast<AMediaExtractor*>(extractor), 0,
                            AMEDIAEXTRACTOR_SEEK_PREVIOUS_SYNC);
            }
            AMediaFormat_delete(trackFormat);
        }
    }
}

static int64_t getSystemNanoTime() {
    timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return now.tv_sec * 1000000000LL + now.tv_nsec;
}

static void fillDecoder(AMediaCodec* codec, AMediaExtractor* extractor,
        int64_t* presentationTimeUs, bool* eosReached) {
    media_status_t status = AMEDIA_OK;

    ssize_t bufferIndex = AMediaCodec_dequeueInputBuffer(codec, 2000);
    if (bufferIndex >= 0) {
        size_t bufsize;
        uint8_t* buf = AMediaCodec_getInputBuffer(codec, bufferIndex, &bufsize);

        int sampleSize = AMediaExtractor_readSampleData(extractor, buf, bufsize);
        if (sampleSize < 0) {
            sampleSize = 0;
            *eosReached = true;
        }

        *presentationTimeUs = AMediaExtractor_getSampleTime(extractor);

        AMediaCodecCryptoInfo *cryptoInfo =
            AMediaExtractor_getSampleCryptoInfo(extractor);

        if (cryptoInfo) {
            status = AMediaCodec_queueSecureInputBuffer(
                codec, bufferIndex, 0, cryptoInfo,
                *presentationTimeUs,
                *eosReached ? AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM : 0);
            AMediaCodecCryptoInfo_delete(cryptoInfo);
        } else {
            status = AMediaCodec_queueInputBuffer(
                codec, bufferIndex, 0, sampleSize,
                *presentationTimeUs,
                *eosReached ? AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM : 0);
        }
        AMediaExtractor_advance(extractor);
    }
}

static bool drainDecoder(AMediaCodec* codec, int64_t presentationTimeUs,
    int64_t* startTimeNano) {

    AMediaCodecBufferInfo info;
    ssize_t bufferIndex  = AMediaCodec_dequeueOutputBuffer(codec, &info, 0);
    if (bufferIndex >= 0) {
        if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
            return true;  // eos reached
        }

        if (*startTimeNano < 0) {
            *startTimeNano = getSystemNanoTime() - (presentationTimeUs * 1000);
        }
        int64_t delay = (*startTimeNano + presentationTimeUs * 1000) -
                getSystemNanoTime();
        if (delay > 0) {
            usleep(delay / 1000);
        }

        AMediaCodec_releaseOutputBuffer(codec, bufferIndex, info.size != 0);
    } else if (bufferIndex == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
        ALOGV("output buffers changed");
    } else if (bufferIndex == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
        AMediaFormat* format = AMediaCodec_getOutputFormat(codec);
        ALOGV("format changed to: %s", AMediaFormat_toString(format));
        AMediaFormat_delete(format);
    } else if (bufferIndex == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
         ALOGV("no output buffer right now");
         usleep(20000);
    } else {
         ALOGV("unexpected info code: %zd", bufferIndex);
    }
    return false;
}

static jboolean playContent(JNIEnv* env, const AMediaObjects& aMediaObjects,
        PlaybackParams& params, const AMediaDrmSessionId& sessionId, Uuid uuid) {

    ANativeWindow *window = ANativeWindow_fromSurface(env, params.surface);
    AMediaExtractor* audioExtractor = aMediaObjects.getAudioExtractor();
    AMediaExtractor* videoExtractor = aMediaObjects.getVideoExtractor();

    AMediaCodec* audioCodec = NULL;
    AMediaCodec* videoCodec = NULL;
    AMediaCrypto* crypto = NULL;

    crypto = AMediaCrypto_new(&uuid[0], sessionId.ptr, sessionId.length);
    if (crypto == NULL) {
        jniThrowException(env, "java/lang/RuntimeException",
                "failed to create crypto object");
        return JNI_FALSE;
    }

    addTracks(audioExtractor, NULL, NULL, &audioCodec);

    addTracks(videoExtractor, crypto, window, &videoCodec);

    bool sawAudioInputEos = false;
    bool sawAudioOutputEos = false;
    bool sawVideoInputEos = false;
    bool sawVideoOutputEos = false;
    int64_t videoPresentationTimeUs = 0;
    int64_t videoStartTimeNano = -1;
    struct timespec timeSpec;
    clock_gettime(CLOCK_MONOTONIC, &timeSpec);
    time_t startTimeSec = timeSpec.tv_sec;

    while (!sawAudioOutputEos && !sawVideoOutputEos) {
        if (!sawVideoInputEos) {
            fillDecoder(videoCodec, videoExtractor,
                    &videoPresentationTimeUs, &sawVideoInputEos);
        }

        if (!sawAudioInputEos) {
            // skip audio, still need to advance the audio extractor
            AMediaExtractor_advance(audioExtractor);
        }

        if (!sawVideoOutputEos) {
            sawVideoOutputEos = drainDecoder(videoCodec, videoPresentationTimeUs,
                    &videoStartTimeNano);
        }

        clock_gettime(CLOCK_MONOTONIC, &timeSpec);
        if (timeSpec.tv_sec >= static_cast<time_t>(
            (startTimeSec + kPlayTimeSeconds))) {
            // stop reading samples and drain the output buffers
            sawAudioInputEos = sawVideoInputEos = true;
            sawAudioOutputEos = true; // ignore audio
        }
    }

    if (audioCodec) {
        AMediaCodec_stop(audioCodec);
        AMediaCodec_delete(audioCodec);
    }
    if (videoCodec) {
        AMediaCodec_stop(videoCodec);
        AMediaCodec_delete(videoCodec);
    }

    AMediaCrypto_delete(crypto);
    ANativeWindow_release(window);
    return JNI_TRUE;
}

static void listener(
    AMediaDrm* /*drm*/, const AMediaDrmSessionId* /*sessionId*/,
    AMediaDrmEventType eventType,
    int /*extra*/, const uint8_t* /*data*/, size_t /*dataSize*/) {

    switch (eventType) {
        case EVENT_PROVISION_REQUIRED:
            ALOGD("EVENT_PROVISION_REQUIRED received");
            break;
        case EVENT_KEY_REQUIRED:
            ALOGD("EVENT_KEY_REQUIRED received");
            break;
        case EVENT_KEY_EXPIRED:
            ALOGD("EVENT_KEY_EXPIRED received");
            break;
        case EVENT_VENDOR_DEFINED:
            gGotVendorDefinedEvent = true;
            ALOGD("EVENT_VENDOR_DEFINED received");
            break;
        default:
            ALOGD("Unknown event received");
            break;
    }
}

static void acquireLicense(
    JNIEnv* env, const AMediaObjects& aMediaObjects, const AMediaDrmSessionId& sessionId,
    AMediaDrmKeyType keyType) {
    // Pointer to keyRequest memory, which remains until the next
    // AMediaDrm_getKeyRequest call or until the drm object is released.
    const uint8_t* keyRequest;
    size_t keyRequestSize = 0;
    std::string errorMessage;

    // The server recognizes "video/mp4" but not "video/avc".
    media_status_t status = AMediaDrm_getKeyRequest(aMediaObjects.getDrm(),
            &sessionId, kClearkeyPssh, sizeof(kClearkeyPssh),
            "video/mp4" /*mimeType*/, keyType,
            NULL, 0, &keyRequest, &keyRequestSize);
    if (status != AMEDIA_OK) {
        errorMessage.assign("getKeyRequest failed, error = %d");
        goto errorOut;
    }

    if (kKeyRequestSize != keyRequestSize) {
        ALOGE("Invalid keyRequestSize %zd", kKeyRequestSize);
        errorMessage.assign("Invalid key request size, error = %d");
        status = AMEDIA_DRM_NEED_KEY;
        goto errorOut;
    }

    if (memcmp(kKeyRequestData, keyRequest, kKeyRequestSize) != 0) {
        errorMessage.assign("Invalid key request data is returned, error = %d");
        status = AMEDIA_DRM_NEED_KEY;
        goto errorOut;
    }

    AMediaDrmKeySetId keySetId;
    gGotVendorDefinedEvent = false;
    status = AMediaDrm_provideKeyResponse(aMediaObjects.getDrm(), &sessionId,
            reinterpret_cast<const uint8_t*>(kResponse),
            sizeof(kResponse), &keySetId);
    if (status == AMEDIA_OK) {
        return;  // success
    }

    errorMessage.assign("provideKeyResponse failed, error = %d");

errorOut:
    AMediaDrm_closeSession(aMediaObjects.getDrm(), &sessionId);
    jniThrowExceptionFmt(env, "java/lang/RuntimeException", errorMessage.c_str(), status);
}

extern "C" jboolean Java_android_media_cts_NativeClearKeySystemTest_testClearKeyPlaybackNative(
    JNIEnv* env, jclass /*clazz*/, jbyteArray uuid, jobject playbackParams) {
    if (NULL == uuid || NULL == playbackParams) {
        jniThrowException(env, "java/lang/NullPointerException",
                "null uuid or null playback parameters");
        return JNI_FALSE;
    }

    Uuid juuid = jbyteArrayToUuid(env, uuid);
    if (!isUuidSizeValid(juuid)) {
        jniThrowExceptionFmt(env, "java/lang/IllegalArgumentException",
                "invalid UUID size, expected %u bytes", kUuidSize);
        return JNI_FALSE;
    }

    PlaybackParams params;
    initPlaybackParams(env, playbackParams, params);

    AMediaObjects aMediaObjects;
    media_status_t status = AMEDIA_OK;
    aMediaObjects.setDrm(AMediaDrm_createByUUID(&juuid[0]));
    if (NULL == aMediaObjects.getDrm()) {
        jniThrowException(env, "java/lang/RuntimeException", "null MediaDrm");
        return JNI_FALSE;
    }

    status = AMediaDrm_setOnEventListener(aMediaObjects.getDrm(), listener);
    if (status != AMEDIA_OK) {
        jniThrowException(env, "java/lang/RuntimeException",
                "setOnEventListener failed");
        return JNI_FALSE;
    }

    aMediaObjects.setAudioExtractor(AMediaExtractor_new());
    const char* url = env->GetStringUTFChars(params.audioUrl, 0);
    if (url) {
        status = AMediaExtractor_setDataSource(
            aMediaObjects.getAudioExtractor(), url);
        env->ReleaseStringUTFChars(params.audioUrl, url);

        if (status != AMEDIA_OK) {
            jniThrowExceptionFmt(env, "java/lang/RuntimeException",
                    "set audio data source error=%d", status);
            return JNI_FALSE;
        }
    }

    aMediaObjects.setVideoExtractor(AMediaExtractor_new());
    url = env->GetStringUTFChars(params.videoUrl, 0);
    if (url) {
        status = AMediaExtractor_setDataSource(
            aMediaObjects.getVideoExtractor(), url);
        env->ReleaseStringUTFChars(params.videoUrl, url);

        if (status != AMEDIA_OK) {
            jniThrowExceptionFmt(env, "java/lang/RuntimeException",
                    "set video data source error=%d", status);
            return JNI_FALSE;
        }
    }

    AMediaDrmSessionId sessionId;
    status = AMediaDrm_openSession(aMediaObjects.getDrm(), &sessionId);
    if (status != AMEDIA_OK) {
        jniThrowException(env, "java/lang/RuntimeException",
                "openSession failed");
        return JNI_FALSE;
    }

    acquireLicense(env, aMediaObjects, sessionId, KEY_TYPE_STREAMING);

    // Check if the event listener has received the expected event sent by
    // provideKeyResponse. This is for testing AMediaDrm_setOnEventListener().
    const char *utf8_outValue = NULL;
    status = AMediaDrm_getPropertyString(aMediaObjects.getDrm(),
            "listenerTestSupport", &utf8_outValue);
    if (status == AMEDIA_OK && NULL != utf8_outValue) {
        std::string eventType(utf8_outValue);
        if (eventType.compare("true") == 0) {
            int count = 0;
            while (!gGotVendorDefinedEvent && count++ < 5) {
               // Prevents race condition when the event arrives late
               usleep(2000);
            }
            if (!gGotVendorDefinedEvent) {
                ALOGE("Event listener did not receive the expected event.");
                jniThrowExceptionFmt(env, "java/lang/RuntimeException",
                        "Event listener did not receive the expected event.");
                AMediaDrm_closeSession(aMediaObjects.getDrm(), &sessionId);
                return JNI_FALSE;
           }
        }
    }

    playContent(env, aMediaObjects, params, sessionId, juuid);

    status = AMediaDrm_closeSession(aMediaObjects.getDrm(), &sessionId);
    if (status != AMEDIA_OK) {
        jniThrowException(env, "java/lang/RuntimeException",
                "closeSession failed");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" jboolean Java_android_media_cts_NativeClearKeySystemTest_testQueryKeyStatusNative(
    JNIEnv* env, jclass /*clazz*/, jbyteArray uuid) {

    if (NULL == uuid) {
        jniThrowException(env, "java/lang/NullPointerException", "null uuid");
        return JNI_FALSE;
    }

    Uuid juuid = jbyteArrayToUuid(env, uuid);
    if (!isUuidSizeValid(juuid)) {
        jniThrowExceptionFmt(env, "java/lang/IllegalArgumentException",
                "invalid UUID size, expected %u bytes", kUuidSize);
        return JNI_FALSE;
    }

    AMediaObjects aMediaObjects;
    media_status_t status = AMEDIA_OK;
    aMediaObjects.setDrm(AMediaDrm_createByUUID(&juuid[0]));
    if (NULL == aMediaObjects.getDrm()) {
        jniThrowException(env, "java/lang/RuntimeException", "failed to create drm");
        return JNI_FALSE;
    }

    AMediaDrmSessionId sessionId;
    status = AMediaDrm_openSession(aMediaObjects.getDrm(), &sessionId);
    if (status != AMEDIA_OK) {
        jniThrowException(env, "java/lang/RuntimeException",
                "openSession failed");
        return JNI_FALSE;
    }

    size_t numPairs = 3;
    AMediaDrmKeyValue keyStatus[numPairs];

    // Test default key status, should return zero key status
    status = AMediaDrm_queryKeyStatus(aMediaObjects.getDrm(), &sessionId, keyStatus, &numPairs);
    if (status != AMEDIA_OK) {
        jniThrowExceptionFmt(env, "java/lang/RuntimeException",
                "AMediaDrm_queryKeyStatus failed, error = %d", status);
        AMediaDrm_closeSession(aMediaObjects.getDrm(), &sessionId);
        return JNI_FALSE;
    }

    if (numPairs != 0) {
        jniThrowExceptionFmt(env, "java/lang/RuntimeException",
                "AMediaDrm_queryKeyStatus failed, no policy should be defined");
        AMediaDrm_closeSession(aMediaObjects.getDrm(), &sessionId);
        return JNI_FALSE;
    }

    acquireLicense(env, aMediaObjects, sessionId, KEY_TYPE_STREAMING);

    // Test short buffer
    numPairs = 2;
    status = AMediaDrm_queryKeyStatus(aMediaObjects.getDrm(), &sessionId, keyStatus, &numPairs);
    if (status != AMEDIA_DRM_SHORT_BUFFER) {
        jniThrowExceptionFmt(env, "java/lang/RuntimeException",
                "AMediaDrm_queryKeyStatus should return AMEDIA_DRM_SHORT_BUFFER, error = %d",
                        status);
        AMediaDrm_closeSession(aMediaObjects.getDrm(), &sessionId);
        return JNI_FALSE;
    }

    // Test valid key status
    numPairs = 3;
    status = AMediaDrm_queryKeyStatus(aMediaObjects.getDrm(), &sessionId, keyStatus, &numPairs);
    if (status != AMEDIA_OK) {
        jniThrowExceptionFmt(env, "java/lang/RuntimeException",
                "AMediaDrm_queryKeyStatus failed, error = %d", status);
        AMediaDrm_closeSession(aMediaObjects.getDrm(), &sessionId);
        return JNI_FALSE;
    }

    for (size_t i = 0; i < numPairs; ++i) {
        ALOGI("AMediaDrm_queryKeyStatus: key=%s, value=%s", keyStatus[i].mKey, keyStatus[i].mValue);
    }

    if (numPairs !=  3) {
        jniThrowExceptionFmt(env, "java/lang/RuntimeException",
                "AMediaDrm_queryKeyStatus returns %zd key status, expecting 3", numPairs);
        AMediaDrm_closeSession(aMediaObjects.getDrm(), &sessionId);
        return JNI_FALSE;
    }

    status = AMediaDrm_closeSession(aMediaObjects.getDrm(), &sessionId);
    if (status != AMEDIA_OK) {
        jniThrowException(env, "java/lang/RuntimeException",
                "closeSession failed");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

static JNINativeMethod gMethods[] = {
    { "isCryptoSchemeSupportedNative", "([B)Z",
            (void *)Java_android_media_cts_NativeClearKeySystemTest_isCryptoSchemeSupportedNative },

    { "testClearKeyPlaybackNative",
            "([BLandroid/media/cts/NativeClearKeySystemTest$PlaybackParams;)Z",
            (void *)Java_android_media_cts_NativeClearKeySystemTest_testClearKeyPlaybackNative },

    { "testGetPropertyStringNative",
            "([BLjava/lang/String;Ljava/lang/StringBuffer;)Z",
            (void *)Java_android_media_cts_NativeClearKeySystemTest_testGetPropertyStringNative },

    { "testPsshNative", "([BLjava/lang/String;)Z",
            (void *)Java_android_media_cts_NativeClearKeySystemTest__testPsshNative },

    { "testQueryKeyStatusNative", "([B)Z",
            (void *)Java_android_media_cts_NativeClearKeySystemTest_testQueryKeyStatusNative },
};

int register_android_media_cts_NativeClearKeySystemTest(JNIEnv* env) {
    jint result = JNI_ERR;
    jclass testClass =
        env->FindClass("android/media/cts/NativeClearKeySystemTest");
    if (testClass) {
        jclass playbackParamsClass = env->FindClass(
            "android/media/cts/NativeClearKeySystemTest$PlaybackParams");
        if (playbackParamsClass) {
            jclass surfaceClass =
                env->FindClass("android/view/Surface");
            if (surfaceClass) {
                gFieldIds.surface = env->GetFieldID(playbackParamsClass,
                        "surface", "Landroid/view/Surface;");
            } else {
                gFieldIds.surface = NULL;
            }
            gFieldIds.mimeType = env->GetFieldID(playbackParamsClass,
                    "mimeType", "Ljava/lang/String;");
            gFieldIds.audioUrl = env->GetFieldID(playbackParamsClass,
                    "audioUrl", "Ljava/lang/String;");
            gFieldIds.videoUrl = env->GetFieldID(playbackParamsClass,
                    "videoUrl", "Ljava/lang/String;");
        } else {
            ALOGE("PlaybackParams class not found");
        }

    } else {
        ALOGE("NativeClearKeySystemTest class not found");
    }

    result = env->RegisterNatives(testClass, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
    return result;
}
