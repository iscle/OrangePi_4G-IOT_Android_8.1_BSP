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

import com.android.tv.tuner.data.PsiData.PatItem;
import com.android.tv.tuner.data.PsiData.PmtItem;
import com.android.tv.tuner.data.PsipData.EitItem;
import com.android.tv.tuner.data.PsipData.SdtItem;
import com.android.tv.tuner.data.PsipData.VctItem;
import com.android.tv.tuner.data.TunerChannel;
import com.android.tv.tuner.data.nano.Track.AtscAudioTrack;
import com.android.tv.tuner.data.nano.Track.AtscCaptionTrack;
import com.android.tv.tuner.source.FileTsStreamer;
import com.android.tv.tuner.ts.TsParser;
import com.android.tv.tuner.tvinput.EventDetector.EventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PSIP event detector for a file source.
 *
 * <p>Uses {@link TsParser} to analyze input MPEG-2 transport stream, detects and reports
 * various PSIP-related events via {@link TsParser.TsOutputListener}.
 */
public class FileSourceEventDetector {
    private static final String TAG = "FileSourceEventDetector";
    private static final boolean DEBUG = true;
    public static final int ALL_PROGRAM_NUMBERS = 0;

    private TsParser mTsParser;
    private final Set<Integer> mVctProgramNumberSet = new HashSet<>();
    private final Set<Integer> mSdtProgramNumberSet = new HashSet<>();
    private final SparseArray<TunerChannel> mChannelMap = new SparseArray<>();
    private final SparseBooleanArray mVctCaptionTracksFound = new SparseBooleanArray();
    private final SparseBooleanArray mEitCaptionTracksFound = new SparseBooleanArray();
    private final EventListener mEventListener;
    private final boolean mEnableDvbSignal;
    private FileTsStreamer.StreamProvider mStreamProvider;
    private int mProgramNumber = ALL_PROGRAM_NUMBERS;

    public FileSourceEventDetector(EventDetector.EventListener listener, boolean enableDvbSignal) {
        mEventListener = listener;
        mEnableDvbSignal = enableDvbSignal;
    }

    /**
     * Starts detecting channel and program information.
     *
     * @param provider MPEG-2 transport stream source.
     * @param programNumber The program number if this is for handling tune request. For scanning
     *            purpose, supply {@link #ALL_PROGRAM_NUMBERS}.
     */
    public void start(FileTsStreamer.StreamProvider provider, int programNumber) {
        mStreamProvider = provider;
        mProgramNumber = programNumber;
        reset();
    }

    private void reset() {
        mTsParser = new TsParser(mTsOutputListener, mEnableDvbSignal); // TODO: Use TsParser.reset()
        mStreamProvider.clearPidFilter();
        mVctProgramNumberSet.clear();
        mSdtProgramNumberSet.clear();
        mVctCaptionTracksFound.clear();
        mEitCaptionTracksFound.clear();
        mChannelMap.clear();
    }

    public void feedTSStream(byte[] data, int startOffset, int length) {
        if (mStreamProvider.isFilterEmpty()) {
            startListening(TsParser.ATSC_SI_BASE_PID);
            startListening(TsParser.PAT_PID);
        }
        if (mTsParser != null) {
            mTsParser.feedTSData(data, startOffset, length);
        }
    }

    private void startListening(int pid) {
        if (mStreamProvider.isInFilter(pid)) {
            return;
        }
        mStreamProvider.addPidFilter(pid);
    }

    private final TsParser.TsOutputListener mTsOutputListener = new TsParser.TsOutputListener() {
        @Override
        public void onPatDetected(List<PatItem> items) {
            for (PatItem i : items) {
                if (mProgramNumber == ALL_PROGRAM_NUMBERS || mProgramNumber == i.getProgramNo()) {
                    mStreamProvider.addPidFilter(i.getPmtPid());
                }
            }
        }

        @Override
        public void onEitPidDetected(int pid) {
            startListening(pid);
        }

        @Override
        public void onEitItemParsed(VctItem channel, List<EitItem> items) {
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
            for (EitItem item : items) {
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
                for (EitItem item : items) {
                    item.setHasCaptionTrack();
                }
            }
            if (tunerChannel != null && mEventListener != null) {
                mEventListener.onEventDetected(tunerChannel, items);
            }
        }

        @Override
        public void onEttPidDetected(int pid) {
            startListening(pid);
        }

        @Override
        public void onAllVctItemsParsed() {
            // do nothing.
        }

        @Override
        public void onVctItemParsed(VctItem channel, List<PmtItem> pmtItems) {
            if (DEBUG) {
                Log.d(TAG, "onVctItemParsed VCT " + channel);
                Log.d(TAG, "                PMT " + pmtItems);
            }

            // Merges the audio and caption tracks located in PMT items into the tracks of the given
            // tuner channel.
            TunerChannel tunerChannel = TunerChannel.forFile(channel, pmtItems);
            List<AtscAudioTrack> audioTracks = new ArrayList<>();
            List<AtscCaptionTrack> captionTracks = new ArrayList<>();
            for (PmtItem pmtItem : pmtItems) {
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
            tunerChannel.setFilepath(mStreamProvider.getFilepath());
            tunerChannel.setAudioTracks(audioTracks);
            tunerChannel.setCaptionTracks(captionTracks);

            mChannelMap.put(tunerChannel.getProgramNumber(), tunerChannel);
            boolean found = mVctProgramNumberSet.contains(channelProgramNumber);
            if (!found) {
                mVctProgramNumberSet.add(channelProgramNumber);
            }
            if (mEventListener != null) {
                mEventListener.onChannelDetected(tunerChannel, !found);
            }
        }

        @Override
        public void onSdtItemParsed(SdtItem channel, List<PmtItem> pmtItems) {
            if (DEBUG) {
                Log.d(TAG, "onSdtItemParsed SDT " + channel);
                Log.d(TAG, "                PMT " + pmtItems);
            }

            // Merges the audio and caption tracks located in PMT items into the tracks of the given
            // tuner channel.
            TunerChannel tunerChannel = TunerChannel.forDvbFile(channel, pmtItems);
            List<AtscAudioTrack> audioTracks = new ArrayList<>();
            List<AtscCaptionTrack> captionTracks = new ArrayList<>();
            for (PmtItem pmtItem : pmtItems) {
                if (pmtItem.getAudioTracks() != null) {
                    audioTracks.addAll(pmtItem.getAudioTracks());
                }
                if (pmtItem.getCaptionTracks() != null) {
                    captionTracks.addAll(pmtItem.getCaptionTracks());
                }
            }
            int channelProgramNumber = channel.getServiceId();
            tunerChannel.setFilepath(mStreamProvider.getFilepath());
            tunerChannel.setAudioTracks(audioTracks);
            tunerChannel.setCaptionTracks(captionTracks);
            mChannelMap.put(tunerChannel.getProgramNumber(), tunerChannel);
            boolean found = mSdtProgramNumberSet.contains(channelProgramNumber);
            if (!found) {
                mSdtProgramNumberSet.add(channelProgramNumber);
            }
            if (mEventListener != null) {
                mEventListener.onChannelDetected(tunerChannel, !found);
            }
        }
    };
}
