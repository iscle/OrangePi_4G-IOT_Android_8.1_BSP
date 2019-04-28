/*
 * Copyright 2012, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef MEDIA_CODEC_LIST_H_

#define MEDIA_CODEC_LIST_H_

#include <media/stagefright/foundation/ABase.h>
#include <media/stagefright/foundation/AString.h>
#include <media/IMediaCodecList.h>
#include <media/MediaCodecInfo.h>

#include <sys/types.h>
#include <utils/Errors.h>
#include <utils/KeyedVector.h>
#include <utils/Vector.h>
#include <utils/StrongPointer.h>

namespace android {

extern const char *kMaxEncoderInputBuffers;

struct AMessage;

struct MediaCodecListBuilderBase;

struct MediaCodecList : public BnMediaCodecList {
    static sp<IMediaCodecList> getInstance();

    virtual ssize_t findCodecByType(
            const char *type, bool encoder, size_t startIndex = 0) const;

    virtual ssize_t findCodecByName(const char *name) const;

    virtual size_t countCodecs() const;

    virtual sp<MediaCodecInfo> getCodecInfo(size_t index) const {
        if (index >= mCodecInfos.size()) {
            ALOGE("b/24445127");
            return NULL;
        }
        return mCodecInfos[index];
    }

    virtual const sp<AMessage> getGlobalSettings() const;

    // to be used by MediaPlayerService alone
    static sp<IMediaCodecList> getLocalInstance();

    // only to be used by getLocalInstance
    static void *profilerThreadWrapper(void * /*arg*/);

    enum Flags {
        kPreferSoftwareCodecs   = 1,
        kHardwareCodecsOnly     = 2,
    };

    static void findMatchingCodecs(
            const char *mime,
            bool createEncoder,
            uint32_t flags,
            Vector<AString> *matchingCodecs,
            Vector<AString> *owners = nullptr);

    static bool isSoftwareCodec(const AString &componentName);

private:
    class BinderDeathObserver : public IBinder::DeathRecipient {
        void binderDied(const wp<IBinder> &the_late_who __unused);
    };

    static sp<BinderDeathObserver> sBinderDeathObserver;

    static sp<IMediaCodecList> sCodecList;
    static sp<IMediaCodecList> sRemoteList;

    status_t mInitCheck;

    sp<AMessage> mGlobalSettings;
    std::vector<sp<MediaCodecInfo> > mCodecInfos;

    /**
     * This constructor will call `buildMediaCodecList()` from the given
     * `MediaCodecListBuilderBase` object.
     */
    MediaCodecList(MediaCodecListBuilderBase* builder);

    ~MediaCodecList();

    status_t initCheck() const;

    MediaCodecList(const MediaCodecList&) = delete;
    MediaCodecList& operator=(const MediaCodecList&) = delete;

    friend MediaCodecListWriter;
};

/**
 * This class is to be used by a `MediaCodecListBuilderBase` instance to add
 * information to the associated `MediaCodecList` object.
 */
struct MediaCodecListWriter {
    /**
     * Add a key-value pair to a `MediaCodecList`'s global settings.
     *
     * @param key Key.
     * @param value Value.
     */
    void addGlobalSetting(const char* key, const char* value);
    /**
     * Create an add a new `MediaCodecInfo` object to a `MediaCodecList`, and
     * return a `MediaCodecInfoWriter` object associated with the newly added
     * `MediaCodecInfo`.
     *
     * @return The `MediaCodecInfoWriter` object associated with the newly
     * added `MediaCodecInfo` object.
     */
    std::unique_ptr<MediaCodecInfoWriter> addMediaCodecInfo();
private:
    /**
     * The associated `MediaCodecList` object.
     */
    MediaCodecList* mList;

    /**
     * Construct this writer object associated with the given `MediaCodecList`
     * object.
     *
     * @param list The "base" `MediaCodecList` object.
     */
    MediaCodecListWriter(MediaCodecList* list);

    friend MediaCodecList;
};

/**
 * This interface is to be used by `MediaCodecList` to fill its members with
 * appropriate information. `buildMediaCodecList()` will be called from a
 * `MediaCodecList` object during its construction.
 */
struct MediaCodecListBuilderBase {
    /**
     * Build the `MediaCodecList` via the given `MediaCodecListWriter` interface.
     *
     * @param writer The writer interface.
     * @return The status of the construction. `NO_ERROR` means success.
     */
    virtual status_t buildMediaCodecList(MediaCodecListWriter* writer) = 0;

    /**
     * The default destructor does nothing.
     */
    virtual ~MediaCodecListBuilderBase();
};

}  // namespace android

#endif  // MEDIA_CODEC_LIST_H_

