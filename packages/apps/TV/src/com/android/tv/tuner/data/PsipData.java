/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.tuner.data;

import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.text.format.DateUtils;

import com.android.tv.tuner.data.nano.Track.AtscAudioTrack;
import com.android.tv.tuner.data.nano.Track.AtscCaptionTrack;
import com.android.tv.tuner.ts.SectionParser;
import com.android.tv.tuner.util.ConvertUtils;
import com.android.tv.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Collection of ATSC PSIP table items.
 */
public class PsipData {

    private PsipData() {
    }

    public static class PsipSection {
        private final int mTableId;
        private final int mTableIdExtension;
        private final int mSectionNumber;
        private final boolean mCurrentNextIndicator;

        public static PsipSection create(byte[] data) {
            if (data.length < 9) {
                return null;
            }
            int tableId = data[0] & 0xff;
            int tableIdExtension = (data[3] & 0xff) << 8 | (data[4] & 0xff);
            int sectionNumber = data[6] & 0xff;
            boolean currentNextIndicator = (data[5] & 0x01) != 0;
            return new PsipSection(tableId, tableIdExtension, sectionNumber, currentNextIndicator);
        }

        private PsipSection(int tableId, int tableIdExtension, int sectionNumber,
                boolean currentNextIndicator) {
            mTableId = tableId;
            mTableIdExtension = tableIdExtension;
            mSectionNumber = sectionNumber;
            mCurrentNextIndicator = currentNextIndicator;
        }

        public int getTableId() {
            return mTableId;
        }

        public int getTableIdExtension() {
            return mTableIdExtension;
        }

        public int getSectionNumber() {
            return mSectionNumber;
        }

        // This is for indicating that the section sent is applicable.
        // We only consider a situation where currentNextIndicator is expected to have a true value.
        // So, we are not going to compare this variable in hashCode() and equals() methods.
        public boolean getCurrentNextIndicator() {
            return mCurrentNextIndicator;
        }

        @Override
        public int hashCode() {
            int result = 17;
            result = 31 * result + mTableId;
            result = 31 * result + mTableIdExtension;
            result = 31 * result + mSectionNumber;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof PsipSection) {
                PsipSection another = (PsipSection) obj;
                return mTableId == another.getTableId()
                        && mTableIdExtension == another.getTableIdExtension()
                        && mSectionNumber == another.getSectionNumber();
            }
            return false;
        }
    }

    /**
     * {@link TvTracksInterface} for serving the audio and caption tracks.
     */
    public interface TvTracksInterface {
        /**
         * Set the flag that tells the caption tracks have been found in this section container.
         */
        void setHasCaptionTrack();

        /**
         * Returns whether or not the caption tracks have been found in this section container.
         * If true, zero caption track will be interpreted as a clearance of the caption tracks.
         */
        boolean hasCaptionTrack();

        /**
         * Returns the audio tracks received.
         */
        List<AtscAudioTrack> getAudioTracks();

        /**
         * Returns the caption tracks received.
         */
        List<AtscCaptionTrack> getCaptionTracks();
    }

    public static class MgtItem {
        public static final int TABLE_TYPE_EIT_RANGE_START = 0x0100;
        public static final int TABLE_TYPE_EIT_RANGE_END = 0x017f;
        public static final int TABLE_TYPE_CHANNEL_ETT = 0x0004;
        public static final int TABLE_TYPE_ETT_RANGE_START = 0x0200;
        public static final int TABLE_TYPE_ETT_RANGE_END = 0x027f;

        private final int mTableType;
        private final int mTableTypePid;

        public MgtItem(int tableType, int tableTypePid) {
            mTableType = tableType;
            mTableTypePid = tableTypePid;
        }

        public int getTableType() {
            return mTableType;
        }

        public int getTableTypePid() {
            return mTableTypePid;
        }
    }

    public static class VctItem {
        private final String mShortName;
        private final String mLongName;
        private final int mServiceType;
        private final int mChannelTsid;
        private final int mProgramNumber;
        private final int mMajorChannelNumber;
        private final int mMinorChannelNumber;
        private final int mSourceId;
        private String mDescription;

        public VctItem(String shortName, String longName, int serviceType, int channelTsid,
                int programNumber, int majorChannelNumber, int minorChannelNumber, int sourceId) {
            mShortName = shortName;
            mLongName = longName;
            mServiceType = serviceType;
            mChannelTsid = channelTsid;
            mProgramNumber = programNumber;
            mMajorChannelNumber = majorChannelNumber;
            mMinorChannelNumber = minorChannelNumber;
            mSourceId = sourceId;
        }

        public String getShortName() {
            return mShortName;
        }

        public String getLongName() {
            return mLongName;
        }

        public int getServiceType() {
            return mServiceType;
        }

        public int getChannelTsid() {
            return mChannelTsid;
        }

        public int getProgramNumber() {
            return mProgramNumber;
        }

        public int getMajorChannelNumber() {
            return mMajorChannelNumber;
        }

        public int getMinorChannelNumber() {
            return mMinorChannelNumber;
        }

        public int getSourceId() {
            return mSourceId;
        }

        @Override
        public String toString() {
            return String
                    .format(Locale.US, "ShortName: %s LongName: %s ServiceType: %d ChannelTsid: %x "
                            + "ProgramNumber:%d %d-%d SourceId: %x",
                    mShortName, mLongName, mServiceType, mChannelTsid,
                    mProgramNumber, mMajorChannelNumber, mMinorChannelNumber, mSourceId);
        }

        public void setDescription(String description) {
            mDescription = description;
        }

        public String getDescription() {
            return mDescription;
        }
    }

    public static class SdtItem {
        private final String mServiceName;
        private final String mServiceProviderName;
        private final int mServiceType;
        private final int mServiceId;
        private final int mOriginalNetWorkId;

        public SdtItem(String serviceName, String serviceProviderName, int serviceType,
                       int serviceId, int originalNetWorkId) {
            mServiceName = serviceName;
            mServiceProviderName = serviceProviderName;
            mServiceType = serviceType;
            mServiceId = serviceId;
            mOriginalNetWorkId = originalNetWorkId;
        }

        public String getServiceName() {
            return mServiceName;
        }

        public String getServiceProviderName() {
            return mServiceProviderName;
        }

        public int getServiceType() {
            return mServiceType;
        }

        public int getServiceId() {
            return mServiceId;
        }

        public int getOriginalNetworkId() {
            return mOriginalNetWorkId;
        }

        @Override
        public String toString() {
            return String.format("ServiceName: %s ServiceProviderName:%s ServiceType:%d "
                            + "OriginalNetworkId:%d",
                    mServiceName, mServiceProviderName, mServiceType, mOriginalNetWorkId);
        }
    }

    /**
     * A base class for descriptors of Ts packets.
     */
    public abstract static class TsDescriptor {
        public abstract int getTag();
    }

    public static class ContentAdvisoryDescriptor extends TsDescriptor {
        private final List<RatingRegion> mRatingRegions;

        public ContentAdvisoryDescriptor(List<RatingRegion> ratingRegions) {
            mRatingRegions = ratingRegions;
        }

        @Override
        public int getTag() {
            return SectionParser.DESCRIPTOR_TAG_CONTENT_ADVISORY;
        }

        public List<RatingRegion> getRatingRegions() {
            return mRatingRegions;
        }
    }

    public static class CaptionServiceDescriptor extends TsDescriptor {
        private final List<AtscCaptionTrack> mCaptionTracks;

        public CaptionServiceDescriptor(List<AtscCaptionTrack> captionTracks) {
            mCaptionTracks = captionTracks;
        }

        @Override
        public int getTag() {
            return SectionParser.DESCRIPTOR_TAG_CAPTION_SERVICE;
        }

        public List<AtscCaptionTrack> getCaptionTracks() {
            return mCaptionTracks;
        }
    }

    public static class ExtendedChannelNameDescriptor extends TsDescriptor {
        private final String mLongChannelName;

        public ExtendedChannelNameDescriptor(String longChannelName) {
            mLongChannelName = longChannelName;
        }

        @Override
        public int getTag() {
            return SectionParser.DESCRIPTOR_TAG_EXTENDED_CHANNEL_NAME;
        }

        public String getLongChannelName() {
            return mLongChannelName;
        }
    }

    public static class GenreDescriptor extends TsDescriptor {
        private final String[] mBroadcastGenres;
        private final String[] mCanonicalGenres;

        public GenreDescriptor(String[] broadcastGenres, String[] canonicalGenres) {
            mBroadcastGenres = broadcastGenres;
            mCanonicalGenres = canonicalGenres;
        }

        @Override
        public int getTag() {
            return SectionParser.DESCRIPTOR_TAG_GENRE;
        }

        public String[] getBroadcastGenres() {
            return mBroadcastGenres;
        }

        public String[] getCanonicalGenres() {
            return mCanonicalGenres;
        }
    }

    public static class Ac3AudioDescriptor extends TsDescriptor {
        // See A/52 Annex A. Table A4.2
        private static final byte SAMPLE_RATE_CODE_48000HZ = 0;
        private static final byte SAMPLE_RATE_CODE_44100HZ = 1;
        private static final byte SAMPLE_RATE_CODE_32000HZ = 2;

        private final byte mSampleRateCode;
        private final byte mBsid;
        private final byte mBitRateCode;
        private final byte mSurroundMode;
        private final byte mBsmod;
        private final int mNumChannels;
        private final boolean mFullSvc;
        private final byte mLangCod;
        private final byte mLangCod2;
        private final byte mMainId;
        private final byte mPriority;
        private final byte mAsvcflags;
        private final String mText;
        private final String mLanguage;
        private final String mLanguage2;

        public Ac3AudioDescriptor(byte sampleRateCode, byte bsid, byte bitRateCode,
                byte surroundMode, byte bsmod, int numChannels, boolean fullSvc, byte langCod,
                byte langCod2, byte mainId, byte priority, byte asvcflags, String text,
                String language, String language2) {
            mSampleRateCode = sampleRateCode;
            mBsid = bsid;
            mBitRateCode = bitRateCode;
            mSurroundMode = surroundMode;
            mBsmod = bsmod;
            mNumChannels = numChannels;
            mFullSvc = fullSvc;
            mLangCod = langCod;
            mLangCod2 = langCod2;
            mMainId = mainId;
            mPriority = priority;
            mAsvcflags = asvcflags;
            mText = text;
            mLanguage = language;
            mLanguage2 = language2;
        }

        @Override
        public int getTag() {
            return SectionParser.DESCRIPTOR_TAG_AC3_AUDIO_STREAM;
        }

        public byte getSampleRateCode() {
            return mSampleRateCode;
        }

        public int getSampleRate() {
            switch (mSampleRateCode) {
                case SAMPLE_RATE_CODE_48000HZ:
                    return 48000;
                case SAMPLE_RATE_CODE_44100HZ:
                    return 44100;
                case SAMPLE_RATE_CODE_32000HZ:
                    return 32000;
                default:
                    return 0;
            }
        }

        public byte getBsid() {
            return mBsid;
        }

        public byte getBitRateCode() {
            return mBitRateCode;
        }

        public byte getSurroundMode() {
            return mSurroundMode;
        }

        public byte getBsmod() {
            return mBsmod;
        }

        public int getNumChannels() {
            return mNumChannels;
        }

        public boolean isFullSvc() {
            return mFullSvc;
        }

        public byte getLangCod() {
            return mLangCod;
        }

        public byte getLangCod2() {
            return mLangCod2;
        }

        public byte getMainId() {
            return mMainId;
        }

        public byte getPriority() {
            return mPriority;
        }

        public byte getAsvcflags() {
            return mAsvcflags;
        }

        public String getText() {
            return mText;
        }

        public String getLanguage() {
            return mLanguage;
        }

        public String getLanguage2() {
            return mLanguage2;
        }

        @Override
        public String toString() {
            return String.format(Locale.US,
                    "AC3 audio stream sampleRateCode: %d, bsid: %d, bitRateCode: %d, "
                    + "surroundMode: %d, bsmod: %d, numChannels: %d, fullSvc: %s, langCod: %d, "
                    + "langCod2: %d, mainId: %d, priority: %d, avcflags: %d, text: %s, language: %s"
                    + ", language2: %s", mSampleRateCode, mBsid, mBitRateCode, mSurroundMode,
                    mBsmod, mNumChannels, mFullSvc, mLangCod, mLangCod2, mMainId, mPriority,
                    mAsvcflags, mText, mLanguage, mLanguage2);
        }
    }

    public static class Iso639LanguageDescriptor extends TsDescriptor {
        private final List<AtscAudioTrack> mAudioTracks;

        public Iso639LanguageDescriptor(List<AtscAudioTrack> audioTracks) {
            mAudioTracks = audioTracks;
        }

        @Override
        public int getTag() {
            return SectionParser.DESCRIPTOR_TAG_ISO639LANGUAGE;
        }

        public List<AtscAudioTrack> getAudioTracks() {
            return mAudioTracks;
        }

        @Override
        public String toString() {
            return String.format("%s %s", getClass().getName(), mAudioTracks);
        }
    }

    public static class ServiceDescriptor extends TsDescriptor {
        private final int mServiceType;
        private final String mServiceProviderName;
        private final String mServiceName;

        public ServiceDescriptor(int serviceType, String serviceProviderName, String serviceName) {
            mServiceType = serviceType;
            mServiceProviderName = serviceProviderName;
            mServiceName = serviceName;
        }

        @Override
        public int getTag() {
            return SectionParser.DVB_DESCRIPTOR_TAG_SERVICE;
        }

        public int getServiceType() {
            return mServiceType;
        }

        public String getServiceProviderName() {
            return mServiceProviderName;
        }

        public String getServiceName() {
            return mServiceName;
        }

        @Override
        public String toString() {
            return String.format(
                    "Service descriptor, service type: %d, "
                            + "service provider name: %s, "
                            + "service name: %s", mServiceType, mServiceProviderName, mServiceName);
        }
    }

    public static class ShortEventDescriptor extends TsDescriptor {
        private final String mLanguage;
        private final String mEventName;
        private final String mText;

        public ShortEventDescriptor(String language, String eventName, String text) {
            mLanguage = language;
            mEventName = eventName;
            mText = text;
        }

        public String getEventName() {
            return mEventName;
        }

        @Override
        public int getTag() {
            return SectionParser.DVB_DESCRIPTOR_TAG_SHORT_EVENT;
        }

        @Override
        public String toString() {
            return String.format("ShortEvent Descriptor, language:%s, event name: %s, "
                    + "text:%s", mLanguage, mEventName, mText);
        }
    }

    public static class ParentalRatingDescriptor extends TsDescriptor {
        private final HashMap<String, Integer> mRatings;

        public ParentalRatingDescriptor(HashMap<String, Integer> ratings) {
            mRatings = ratings;
        }

        @Override
        public int getTag() {
            return SectionParser.DVB_DESCRIPTOR_TAG_PARENTAL_RATING;
        }

        public HashMap<String, Integer> getRatings() {
            return mRatings;
        }

        @Override
        public String toString() {
            return String.format("Parental rating descriptor, ratings:" + mRatings);
        }
    }

    public static class RatingRegion {
        private final int mName;
        private final String mDescription;
        private final List<RegionalRating> mRegionalRatings;

        public RatingRegion(int name, String description, List<RegionalRating> regionalRatings) {
            mName = name;
            mDescription = description;
            mRegionalRatings = regionalRatings;
        }

        public int getName() {
            return mName;
        }

        public String getDescription() {
            return mDescription;
        }

        public List<RegionalRating> getRegionalRatings() {
            return mRegionalRatings;
        }
    }

    public static class RegionalRating {
        private final int mDimension;
        private final int mRating;

        public RegionalRating(int dimension, int rating) {
            mDimension = dimension;
            mRating = rating;
        }

        public int getDimension() {
            return mDimension;
        }

        public int getRating() {
            return mRating;
        }
    }

    public static class EitItem implements Comparable<EitItem>, TvTracksInterface {
        public static final long INVALID_PROGRAM_ID = -1;

        // A program id is a primary key of TvContract.Programs table. So it must be positive.
        private final long mProgramId;
        private final int mEventId;
        private final String mTitleText;
        private String mDescription;
        private final long mStartTime;
        private final int mLengthInSecond;
        private final String mContentRating;
        private final List<AtscAudioTrack> mAudioTracks;
        private final List<AtscCaptionTrack> mCaptionTracks;
        private boolean mHasCaptionTrack;
        private final String mBroadcastGenre;
        private final String mCanonicalGenre;

        public EitItem(long programId, int eventId, String titleText, long startTime,
                int lengthInSecond, String contentRating, List<AtscAudioTrack> audioTracks,
                List<AtscCaptionTrack> captionTracks, String broadcastGenre, String canonicalGenre,
                String description) {
            mProgramId = programId;
            mEventId = eventId;
            mTitleText = titleText;
            mStartTime = startTime;
            mLengthInSecond = lengthInSecond;
            mContentRating = contentRating;
            mAudioTracks = audioTracks;
            mCaptionTracks = captionTracks;
            mBroadcastGenre = broadcastGenre;
            mCanonicalGenre = canonicalGenre;
            mDescription = description;
        }

        public long getProgramId() {
            return mProgramId;
        }

        public int getEventId() {
            return mEventId;
        }

        public String getTitleText() {
            return mTitleText;
        }

        public void setDescription(String description) {
            mDescription = description;
        }

        public String getDescription() {
            return mDescription;
        }

        public long getStartTime() {
            return mStartTime;
        }

        public int getLengthInSecond() {
            return mLengthInSecond;
        }

        public long getStartTimeUtcMillis() {
            return ConvertUtils.convertGPSTimeToUnixEpoch(mStartTime) * DateUtils.SECOND_IN_MILLIS;
        }

        public long getEndTimeUtcMillis() {
            return ConvertUtils.convertGPSTimeToUnixEpoch(
                    mStartTime + mLengthInSecond) * DateUtils.SECOND_IN_MILLIS;
        }

        public String getContentRating() {
            return mContentRating;
        }

        @Override
        public List<AtscAudioTrack> getAudioTracks() {
            return mAudioTracks;
        }

        @Override
        public List<AtscCaptionTrack> getCaptionTracks() {
            return mCaptionTracks;
        }

        public String getBroadcastGenre() {
            return mBroadcastGenre;
        }

        public String getCanonicalGenre() {
            return mCanonicalGenre;
        }

        @Override
        public void setHasCaptionTrack() {
            mHasCaptionTrack = true;
        }

        @Override
        public boolean hasCaptionTrack() {
            return mHasCaptionTrack;
        }

        @Override
        public int compareTo(@NonNull EitItem item) {
            // The list of caption tracks and the program ids are not compared in here because the
            // channels in TIF have the concept of the caption and audio tracks while the programs
            // do not and the programs in TIF only have a program id since they are the rows of
            // Content Provider.
            int ret = mEventId - item.getEventId();
            if (ret != 0) {
                return ret;
            }
            ret = StringUtils.compare(mTitleText, item.getTitleText());
            if (ret != 0) {
                return ret;
            }
            if (mStartTime > item.getStartTime()) {
                return 1;
            } else if (mStartTime < item.getStartTime()) {
                return -1;
            }
            if (mLengthInSecond > item.getLengthInSecond()) {
                return 1;
            } else if (mLengthInSecond < item.getLengthInSecond()) {
                return -1;
            }

            // Compares content ratings
            ret = StringUtils.compare(mContentRating, item.getContentRating());
            if (ret != 0) {
                return ret;
            }

            // Compares broadcast genres
            ret = StringUtils.compare(mBroadcastGenre, item.getBroadcastGenre());
            if (ret != 0) {
                return ret;
            }
            // Compares canonical genres
            ret = StringUtils.compare(mCanonicalGenre, item.getCanonicalGenre());
            if (ret != 0) {
                return ret;
            }

            // Compares descriptions
            return StringUtils.compare(mDescription, item.getDescription());
        }

        public String getAudioLanguage() {
            if (mAudioTracks == null) {
                return "";
            }
            ArrayList<String> languages = new ArrayList<>();
            for (AtscAudioTrack audioTrack : mAudioTracks) {
                languages.add(audioTrack.language);
            }
            return TextUtils.join(",", languages);
        }

        @Override
        public String toString() {
            return String.format(Locale.US,
                    "EitItem programId: %d, eventId: %d, title: %s, startTime: %10d, "
                            + "length: %6d, rating: %s, audio tracks: %d, caption tracks: %d, "
                            + "genres (broadcast: %s, canonical: %s), description: %s",
                    mProgramId, mEventId, mTitleText, mStartTime, mLengthInSecond, mContentRating,
                    mAudioTracks != null ? mAudioTracks.size() : 0,
                    mCaptionTracks != null ? mCaptionTracks.size() : 0,
                    mBroadcastGenre, mCanonicalGenre, mDescription);
        }
    }

    public static class EttItem {
        public final int eventId;
        public final String text;

        public EttItem(int eventId, String text) {
            this.eventId = eventId;
            this.text = text;
        }
    }
}
