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

package com.android.tv.tuner.tvinput;

import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import com.android.tv.tuner.TunerHal;
import com.android.tv.tuner.ts.TsParser;
import com.android.tv.tuner.data.PsiData;
import com.android.tv.tuner.data.PsipData;
import com.android.tv.tuner.data.TunerChannel;
import com.android.tv.tuner.data.nano.Track.AtscAudioTrack;
import com.android.tv.tuner.data.nano.Track.AtscCaptionTrack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects channels and programs that are emerged or changed while parsing ATSC PSIP information.
 */
public class EventDetector {
    private static final String TAG = "EventDetector";
    private static final boolean DEBUG = false;
    public static final int ALL_PROGRAM_NUMBERS = -1;

    private final TunerHal mTunerHal;

    private TsParser mTsParser;
    private final Set<Integer> mPidSet = new HashSet<>();

    // To prevent channel duplication
    private final Set<Integer> mVctProgramNumberSet = new HashSet<>();
    private final Set<Integer> mSdtProgramNumberSet = new HashSet<>();
    private final SparseArray<TunerChannel> mChannelMap = new SparseArray<>();
    private final SparseBooleanArray mVctCaptionTracksFound = new SparseBooleanArray();
    private final SparseBooleanArray mEitCaptionTracksFound = new SparseBooleanArray();
    private final List<EventListener> mEventListeners = new ArrayList<>();
    private int mFrequency;
    private String mModulation;
    private int mProgramNumber = ALL_PROGRAM_NUMBERS;

    private final TsParser.TsOutputListener mTsOutputListener = new TsParser.TsOutputListener() {
        @Override
        public void onPatDetected(List<PsiData.PatItem> items) {
            for (PsiData.PatItem i : items) {
                if (mProgramNumber == ALL_PROGRAM_NUMBERS || mProgramNumber == i.getProgramNo()) {
                    mTunerHal.addPidFilter(i.getPmtPid(), TunerHal.FILTER_TYPE_OTHER);
                }
            }
        }

        @Override
        public void onEitPidDetected(int pid) {
            startListening(pid);
        }

        @Override
        public void onEitItemParsed(PsipData.VctItem channel, List<PsipData.EitItem> items) {
            TunerChannel tunerChannel = mChannelMap.get(channel.getProgramNumber());
            if (DEBUG) {
                Log.d(TAG, "onEitItemParsed tunerChannel:" + tunerChannel + " "
                        + channel.getProgramNumber());
            }
            int channelSourceId = channel.getSourceId();

            // Source id 0 is useful for cases where a cable operator wishes to define a channel for
            // which no EPG data is currently available.
            // We don't handle such a case.
            if (channelSourceId == 0) {
                return;
            }

            // If at least a one caption track have been found in EIT items for the given channel,
            // we starts to interpret the zero tracks as a clearance of the caption tracks.
            boolean captionTracksFound = mEitCaptionTracksFound.get(channelSourceId);
            for (PsipData.EitItem item : items) {
                if (captionTracksFound) {
                    break;
                }
                List<AtscCaptionTrack> captionTracks = item.getCaptionTracks();
                if (captionTracks != null && !captionTracks.isEmpty()) {
                    captionTracksFound = true;
                }
            }
            mEitCaptionTracksFound.put(channelSourceId, captionTracksFound);
            if (captionTracksFound) {
                for (PsipData.EitItem item : items) {
                    item.setHasCaptionTrack();
                }
            }
            if (tunerChannel != null && !mEventListeners.isEmpty()) {
                for (EventListener eventListener : mEventListeners) {
                    eventListener.onEventDetected(tunerChannel, items);
                }
            }
        }

        @Override
        public void onEttPidDetected(int pid) {
            startListening(pid);
        }

        @Override
        public void onAllVctItemsParsed() {
            if (!mEventListeners.isEmpty()) {
                for (EventListener eventListener : mEventListeners) {
                    eventListener.onChannelScanDone();
                }
            }
        }

        @Override
        public void onVctItemParsed(PsipData.VctItem channel, List<PsiData.PmtItem> pmtItems) {
            if (DEBUG) {
                Log.d(TAG, "onVctItemParsed VCT " + channel);
                Log.d(TAG, "                PMT " + pmtItems);
            }

            // Merges the audio and caption tracks located in PMT items into the tracks of the given
            // tuner channel.
            TunerChannel tunerChannel = new TunerChannel(channel, pmtItems);
            List<AtscAudioTrack> audioTracks = new ArrayList<>();
            List<AtscCaptionTrack> captionTracks = new ArrayList<>();
            for (PsiData.PmtItem pmtItem : pmtItems) {
                if (pmtItem.getAudioTracks() != null) {
                    audioTracks.addAll(pmtItem.getAudioTracks());
                }
                if (pmtItem.getCaptionTracks() != null) {
                    captionTracks.addAll(pmtItem.getCaptionTracks());
                }
            }
            int channelProgramNumber = channel.getProgramNumber();

            // If at least a one caption track have been found in VCT items for the given channel,
            // we starts to interpret the zero tracks as a clearance of the caption tracks.
            boolean captionTracksFound = mVctCaptionTracksFound.get(channelProgramNumber)
                    || !captionTracks.isEmpty();
            mVctCaptionTracksFound.put(channelProgramNumber, captionTracksFound);
            if (captionTracksFound) {
                tunerChannel.setHasCaptionTrack();
            }
            tunerChannel.setAudioTracks(audioTracks);
            tunerChannel.setCaptionTracks(captionTracks);
            tunerChannel.setFrequency(mFrequency);
            tunerChannel.setModulation(mModulation);
            mChannelMap.put(tunerChannel.getProgramNumber(), tunerChannel);
            boolean found = mVctProgramNumberSet.contains(channelProgramNumber);
            if (!found) {
                mVctProgramNumberSet.add(channelProgramNumber);
            }
            if (!mEventListeners.isEmpty()) {
                for (EventListener eventListener : mEventListeners) {
                    eventListener.onChannelDetected(tunerChannel, !found);
                }
            }
        }

        @Override
        public void onSdtItemParsed(PsipData.SdtItem channel, List<PsiData.PmtItem> pmtItems) {
            if (DEBUG) {
                Log.d(TAG, "onSdtItemParsed SDT " + channel);
                Log.d(TAG, "                PMT " + pmtItems);
            }

            // Merges the audio and caption tracks located in PMT items into the tracks of the given
            // tuner channel.
            TunerChannel tunerChannel = new TunerChannel(channel, pmtItems);
            List<AtscAudioTrack> audioTracks = new ArrayList<>();
            List<AtscCaptionTrack> captionTracks = new ArrayList<>();
            for (PsiData.PmtItem pmtItem : pmtItems) {
                if (pmtItem.getAudioTracks() != null) {
                    audioTracks.addAll(pmtItem.getAudioTracks());
                }
                if (pmtItem.getCaptionTracks() != null) {
                    captionTracks.addAll(pmtItem.getCaptionTracks());
                }
            }
            int channelProgramNumber = channel.getServiceId();
            tunerChannel.setAudioTracks(audioTracks);
            tunerChannel.setCaptionTracks(captionTracks);
            tunerChannel.setFrequency(mFrequency);
            tunerChannel.setModulation(mModulation);
            mChannelMap.put(tunerChannel.getProgramNumber(), tunerChannel);
            boolean found = mSdtProgramNumberSet.contains(channelProgramNumber);
            if (!found) {
                mSdtProgramNumberSet.add(channelProgramNumber);
            }
            if (!mEventListeners.isEmpty()) {
                for (EventListener eventListener : mEventListeners) {
                    eventListener.onChannelDetected(tunerChannel, !found);
                }
            }
        }
    };

    /**
     * Listener for detecting ATSC TV channels and receiving EPG data.
     */
    public interface EventListener {

        /**
         * Fired when new information of an ATSC TV channel arrived.
         *
         * @param channel an ATSC TV channel
         * @param channelArrivedAtFirstTime tells whether this channel arrived at first time
         */
        void onChannelDetected(TunerChannel channel, boolean channelArrivedAtFirstTime);

        /**
         * Fired when new program events of an ATSC TV channel arrived.
         *
         * @param channel an ATSC TV channel
         * @param items a list of EIT items that were received
         */
        void onEventDetected(TunerChannel channel, List<PsipData.EitItem> items);

        /**
         * Fired when information of all detectable ATSC TV channels in current frequency arrived.
         */
        void onChannelScanDone();
    }

    /**
     * Creates a detector for ATSC TV channles and program information.
     *
     * @param usbTunerInteface {@link TunerHal}
     */
    public EventDetector(TunerHal usbTunerInteface) {
        mTunerHal = usbTunerInteface;
    }

    private void reset() {
        // TODO: Use TsParser.reset()
        int deliverySystemType = mTunerHal.getDeliverySystemType();
        mTsParser =
                new TsParser(
                        mTsOutputListener,
                        TunerHal.isDvbDeliverySystem(mTunerHal.getDeliverySystemType()));
        mPidSet.clear();
        mVctProgramNumberSet.clear();
        mSdtProgramNumberSet.clear();
        mVctCaptionTracksFound.clear();
        mEitCaptionTracksFound.clear();
        mChannelMap.clear();
    }

    /**
     * Starts detecting channel and program information.
     *
     * @param frequency The frequency to listen to.
     * @param modulation The modulation type.
     * @param programNumber The program number if this is for handling tune request. For scanning
     *            purpose, supply {@link #ALL_PROGRAM_NUMBERS}.
     */
    public void startDetecting(int frequency, String modulation, int programNumber) {
        reset();
        mFrequency = frequency;
        mModulation = modulation;
        mProgramNumber = programNumber;
    }

    private void startListening(int pid) {
        if (mPidSet.contains(pid)) {
            return;
        }
        mPidSet.add(pid);
        mTunerHal.addPidFilter(pid, TunerHal.FILTER_TYPE_OTHER);
    }

    /**
     * Feeds ATSC TS stream to detect channel and program information.
     * @param data buffer for ATSC TS stream
     * @param startOffset the offset where buffer starts
     * @param length The length of available data
     */
    public void feedTSStream(byte[] data, int startOffset, int length) {
        if (mPidSet.isEmpty()) {
            startListening(TsParser.ATSC_SI_BASE_PID);
        }
        if (mTsParser != null) {
            mTsParser.feedTSData(data, startOffset, length);
        }
    }

    /**
     * Retrieves the channel information regardless of being well-formed.
     * @return {@link List} of {@link TunerChannel}
     */
    public List<TunerChannel> getMalFormedChannels() {
        return mTsParser.getMalFormedChannels();
    }

    /**
     * Registers an EventListener.
     * @param eventListener the listener to be registered
     */
    public void registerListener(EventListener eventListener) {
        if (mTsParser != null) {
            // Resets the version numbers so that the new listener can receive the EIT items.
            // Otherwise, each EIT session is handled only once unless there is a new version.
            mTsParser.resetDataVersions();
        }
        mEventListeners.add(eventListener);
    }

    /**
     * Unregisters an EventListener.
     * @param eventListener the listener to be unregistered
     */
    public void unregisterListener(EventListener eventListener) {
        boolean removed = mEventListeners.remove(eventListener);
        if (!removed && DEBUG) {
            Log.d(TAG, "Cannot unregister a non-registered listener!");
        }
    }
}
