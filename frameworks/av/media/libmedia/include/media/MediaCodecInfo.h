/*
 * Copyright 2014, The Android Open Source Project
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

#ifndef MEDIA_CODEC_INFO_H_

#define MEDIA_CODEC_INFO_H_

#include <android-base/macros.h>
#include <binder/Parcel.h>
#include <media/stagefright/foundation/ABase.h>
#include <media/stagefright/foundation/AString.h>

#include <sys/types.h>
#include <utils/Errors.h>
#include <utils/KeyedVector.h>
#include <utils/RefBase.h>
#include <utils/Vector.h>
#include <utils/StrongPointer.h>

namespace android {

struct AMessage;
class Parcel;

typedef KeyedVector<AString, AString> CodecSettings;

struct MediaCodecInfoWriter;
struct MediaCodecListWriter;

struct MediaCodecInfo : public RefBase {
    struct ProfileLevel {
        uint32_t mProfile;
        uint32_t mLevel;
        bool operator <(const ProfileLevel &o) const {
            return mProfile < o.mProfile || (mProfile == o.mProfile && mLevel < o.mLevel);
        }
    };

    struct CapabilitiesWriter;

    struct Capabilities : public RefBase {
        enum {
            // decoder flags
            kFlagSupportsAdaptivePlayback = 1 << 0,
            kFlagSupportsSecurePlayback = 1 << 1,
            kFlagSupportsTunneledPlayback = 1 << 2,

            // encoder flags
            kFlagSupportsIntraRefresh = 1 << 0,

        };

        void getSupportedProfileLevels(Vector<ProfileLevel> *profileLevels) const;
        void getSupportedColorFormats(Vector<uint32_t> *colorFormats) const;
        uint32_t getFlags() const;
        const sp<AMessage> getDetails() const;

    protected:
        Vector<ProfileLevel> mProfileLevels;
        SortedVector<ProfileLevel> mProfileLevelsSorted;
        Vector<uint32_t> mColorFormats;
        SortedVector<uint32_t> mColorFormatsSorted;
        uint32_t mFlags;
        sp<AMessage> mDetails;

        Capabilities();

    private:
        // read object from parcel even if object creation fails
        static sp<Capabilities> FromParcel(const Parcel &parcel);
        status_t writeToParcel(Parcel *parcel) const;

        DISALLOW_COPY_AND_ASSIGN(Capabilities);

        friend struct MediaCodecInfo;
        friend struct MediaCodecInfoWriter;
        friend struct CapabilitiesWriter;
    };

    /**
     * This class is used for modifying information inside a `Capabilities`
     * object. An object of type `CapabilitiesWriter` can be obtained by calling
     * `MediaCodecInfoWriter::addMime()` or
     * `MediaCodecInfoWriter::updateMime()`.
     */
    struct CapabilitiesWriter {
        /**
         * Add a key-value pair to the list of details. If the key already
         * exists, the old value will be replaced.
         *
         * A pair added by this function will be accessible by
         * `Capabilities::getDetails()`. Call `AMessage::getString()` with the
         * same key to retrieve the value.
         *
         * @param key The key.
         * @param value The string value.
         */
        void addDetail(const char* key, const char* value);
        /**
         * Add a key-value pair to the list of details. If the key already
         * exists, the old value will be replaced.
         *
         * A pair added by this function will be accessible by
         * `Capabilities::getDetails()`. Call `AMessage::getInt32()` with the
         * same key to retrieve the value.
         *
         * @param key The key.
         * @param value The `int32_t` value.
         */
        void addDetail(const char* key, int32_t value);
        /**
         * Add a profile-level pair. If this profile-level pair already exists,
         * it will be ignored.
         *
         * @param profile The "profile" component.
         * @param level The "level" component.
         */
        void addProfileLevel(uint32_t profile, uint32_t level);
        /**
         * Add a color format. If this color format already exists, it will be
         * ignored.
         *
         * @param format The color format.
         */
        void addColorFormat(uint32_t format);
        /**
         * Add flags. The underlying operation is bitwise-or. In other words,
         * bits that have already been set will be ignored.
         *
         * @param flags The additional flags.
         */
        void addFlags(uint32_t flags);
    private:
        /**
         * The associated `Capabilities` object.
         */
        Capabilities* mCap;
        /**
         * Construct a writer for the given `Capabilities` object.
         *
         * @param cap The `Capabilities` object to be written to.
         */
        CapabilitiesWriter(Capabilities* cap);

        friend MediaCodecInfoWriter;
    };

    bool isEncoder() const;
    void getSupportedMimes(Vector<AString> *mimes) const;
    const sp<Capabilities> getCapabilitiesFor(const char *mime) const;
    const char *getCodecName() const;

    /**
     * Return the name of the service that hosts the codec. This value is not
     * visible at the Java level.
     *
     * Currently, this is the "instance name" of the IOmx service.
     */
    const char *getOwnerName() const;

    /**
     * Serialization over Binder
     */
    static sp<MediaCodecInfo> FromParcel(const Parcel &parcel);
    status_t writeToParcel(Parcel *parcel) const;

private:
    AString mName;
    AString mOwner;
    bool mIsEncoder;
    KeyedVector<AString, sp<Capabilities> > mCaps;

    ssize_t getCapabilityIndex(const char *mime) const;

    /**
     * Construct an `MediaCodecInfo` object. After the construction, its
     * information can be set via an `MediaCodecInfoWriter` object obtained from
     * `MediaCodecListWriter::addMediaCodecInfo()`.
     */
    MediaCodecInfo();

    DISALLOW_COPY_AND_ASSIGN(MediaCodecInfo);

    friend class MediaCodecListOverridesTest;
    friend struct MediaCodecInfoWriter;
    friend struct MediaCodecListWriter;
};

/**
 * This class is to be used by a `MediaCodecListBuilderBase` instance to
 * populate information inside the associated `MediaCodecInfo` object.
 *
 * The only place where an instance of `MediaCodecInfoWriter` can be constructed
 * is `MediaCodecListWriter::addMediaCodecInfo()`. A `MediaCodecListBuilderBase`
 * instance should call `MediaCodecListWriter::addMediaCodecInfo()` on the given
 * `MediaCodecListWriter` object given as an input to
 * `MediaCodecListBuilderBase::buildMediaCodecList()`.
 */
struct MediaCodecInfoWriter {
    /**
     * Set the name of the codec.
     *
     * @param name The new name.
     */
    void setName(const char* name);
    /**
     * Set the owner name of the codec.
     *
     * This "owner name" is the name of the `IOmx` instance that supports this
     * codec.
     *
     * @param owner The new owner name.
     */
    void setOwner(const char* owner);
    /**
     * Set whether this codec is an encoder or a decoder.
     *
     * @param isEncoder Whether this codec is an encoder or a decoder.
     */
    void setEncoder(bool isEncoder = true);
    /**
     * Add a mime to an indexed list and return a `CapabilitiesWriter` object
     * that can be used for modifying the associated `Capabilities`.
     *
     * If the mime already exists, this function will return the
     * `CapabilitiesWriter` associated with the mime.
     *
     * @param[in] mime The name of a new mime to add.
     * @return writer The `CapabilitiesWriter` object for modifying the
     * `Capabilities` associated with the mime. `writer` will be valid
     * regardless of whether `mime` already exists or not.
     */
    std::unique_ptr<MediaCodecInfo::CapabilitiesWriter> addMime(
            const char* mime);
    /**
     * Remove a mime.
     *
     * @param mime The name of the mime to remove.
     * @return `true` if `mime` is removed; `false` if `mime` is not found.
     */
    bool removeMime(const char* mime);
private:
    /**
     * The associated `MediaCodecInfo`.
     */
    MediaCodecInfo* mInfo;
    /**
     * Construct the `MediaCodecInfoWriter` object associated with the given
     * `MediaCodecInfo` object.
     *
     * @param info The underlying `MediaCodecInfo` object.
     */
    MediaCodecInfoWriter(MediaCodecInfo* info);

    DISALLOW_COPY_AND_ASSIGN(MediaCodecInfoWriter);

    friend struct MediaCodecListWriter;
};

}  // namespace android

#endif  // MEDIA_CODEC_INFO_H_


