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

package com.android.tv.tuner.ts;

import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import com.android.tv.tuner.data.PsiData.PatItem;
import com.android.tv.tuner.data.PsiData.PmtItem;
import com.android.tv.tuner.data.PsipData.EitItem;
import com.android.tv.tuner.data.PsipData.EttItem;
import com.android.tv.tuner.data.PsipData.MgtItem;
import com.android.tv.tuner.data.PsipData.SdtItem;
import com.android.tv.tuner.data.PsipData.VctItem;
import com.android.tv.tuner.data.TunerChannel;
import com.android.tv.tuner.ts.SectionParser.OutputListener;
import com.android.tv.tuner.util.ByteArrayBuffer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Parses MPEG-2 TS packets.
 */
public class TsParser {
    private static final String TAG = "TsParser";
    private static final boolean DEBUG = false;

    public static final int ATSC_SI_BASE_PID = 0x1ffb;
    public static final int PAT_PID = 0x0000;
    public static final int DVB_SDT_PID = 0x0011;
    public static final int DVB_EIT_PID = 0x0012;
    private static final int TS_PACKET_START_CODE = 0x47;
    private static final int TS_PACKET_TEI_MASK = 0x80;
    private static final int TS_PACKET_SIZE = 188;

    /*
     * Using a SparseArray removes the need to auto box the int key for mStreamMap
     * in feedTdPacket which is called 100 times a second. This greatly reduces the
     * number of objects created and the frequency of garbage collection.
     * Other maps might be suitable for a SparseArray, but the performance
     * trade offs must be considered carefully.
     * mStreamMap is the only one called at such a high rate.
     */
    private final SparseArray<Stream> mStreamMap = new SparseArray<>();
    private final Map<Integer, VctItem> mSourceIdToVctItemMap = new HashMap<>();
    private final Map<Integer, String> mSourceIdToVctItemDescriptionMap = new HashMap<>();
    private final Map<Integer, VctItem> mProgramNumberToVctItemMap = new HashMap<>();
    private final Map<Integer, List<PmtItem>> mProgramNumberToPMTMap = new HashMap<>();
    private final Map<Integer, List<EitItem>> mSourceIdToEitMap = new HashMap<>();
    private final Map<Integer, SdtItem> mProgramNumberToSdtItemMap = new HashMap<>();
    private final Map<EventSourceEntry, List<EitItem>> mEitMap = new HashMap<>();
    private final Map<EventSourceEntry, List<EttItem>> mETTMap = new HashMap<>();
    private final TreeSet<Integer> mEITPids = new TreeSet<>();
    private final TreeSet<Integer> mETTPids = new TreeSet<>();
    private final SparseBooleanArray mProgramNumberHandledStatus = new SparseBooleanArray();
    private final SparseBooleanArray mVctItemHandledStatus = new SparseBooleanArray();
    private final TsOutputListener mListener;
    private final boolean mIsDvbSignal;

    private int mVctItemCount;
    private int mHandledVctItemCount;
    private int mVctSectionParsedCount;
    private boolean[] mVctSectionParsed;

    public interface TsOutputListener {
        void onPatDetected(List<PatItem> items);
        void onEitPidDetected(int pid);
        void onVctItemParsed(VctItem channel, List<PmtItem> pmtItems);
        void onEitItemParsed(VctItem channel, List<EitItem> items);
        void onEttPidDetected(int pid);
        void onAllVctItemsParsed();
        void onSdtItemParsed(SdtItem channel, List<PmtItem> pmtItems);
    }

    private abstract class Stream {
        private static final int INVALID_CONTINUITY_COUNTER = -1;
        private static final int NUM_CONTINUITY_COUNTER = 16;

        protected int mContinuityCounter = INVALID_CONTINUITY_COUNTER;
        protected final ByteArrayBuffer mPacket = new ByteArrayBuffer(TS_PACKET_SIZE);

        public void feedData(byte[] data, int continuityCounter, boolean startIndicator) {
            if ((mContinuityCounter + 1) % NUM_CONTINUITY_COUNTER != continuityCounter) {
                mPacket.setLength(0);
            }
            mContinuityCounter = continuityCounter;
            handleData(data, startIndicator);
        }

        protected abstract void handleData(byte[] data, boolean startIndicator);
        protected abstract void resetDataVersions();
    }

    private class SectionStream extends Stream {
        private final SectionParser mSectionParser;
        private final int mPid;

        public SectionStream(int pid) {
            mPid = pid;
            mSectionParser = new SectionParser(mSectionListener);
        }

        @Override
        protected void handleData(byte[] data, boolean startIndicator) {
            int startPos = 0;
            if (mPacket.length() == 0) {
                if (startIndicator) {
                    startPos = (data[0] & 0xff) + 1;
                } else {
                    // Don't know where the section starts yet. Wait until start indicator is on.
                    return;
                }
            } else {
                if (startIndicator) {
                    startPos = 1;
                }
            }

            // When a broken packet is encountered, parsing will stop and return right away.
            if (startPos >= data.length) {
                mPacket.setLength(0);
                return;
            }
            mPacket.append(data, startPos, data.length - startPos);
            mSectionParser.parseSections(mPacket);
        }

        @Override
        protected void resetDataVersions() {
            mSectionParser.resetVersionNumbers();
        }

        private final OutputListener mSectionListener = new OutputListener() {
            @Override
            public void onPatParsed(List<PatItem> items) {
                for (PatItem i : items) {
                    startListening(i.getPmtPid());
                }
                if (mListener != null) {
                    mListener.onPatDetected(items);
                }
            }

            @Override
            public void onPmtParsed(int programNumber, List<PmtItem> items) {
                mProgramNumberToPMTMap.put(programNumber, items);
                if (DEBUG) {
                    Log.d(TAG, "onPMTParsed, programNo " + programNumber + " handledStatus is "
                            + mProgramNumberHandledStatus.get(programNumber, false));
                }
                int statusIndex = mProgramNumberHandledStatus.indexOfKey(programNumber);
                if (statusIndex < 0) {
                    mProgramNumberHandledStatus.put(programNumber, false);
                }
                if (!mProgramNumberHandledStatus.get(programNumber)) {
                    VctItem vctItem = mProgramNumberToVctItemMap.get(programNumber);
                    if (vctItem != null) {
                        // When PMT is parsed later than VCT.
                        mProgramNumberHandledStatus.put(programNumber, true);
                        handleVctItem(vctItem, items);
                        mHandledVctItemCount++;
                        if (mHandledVctItemCount >= mVctItemCount
                                && mVctSectionParsedCount >= mVctSectionParsed.length
                                && mListener != null) {
                            mListener.onAllVctItemsParsed();
                        }
                    }
                    SdtItem sdtItem = mProgramNumberToSdtItemMap.get(programNumber);
                    if (sdtItem != null) {
                        // When PMT is parsed later than SDT.
                        mProgramNumberHandledStatus.put(programNumber, true);
                        handleSdtItem(sdtItem, items);
                    }
                }
            }

            @Override
            public void onMgtParsed(List<MgtItem> items) {
                for (MgtItem i : items) {
                    if (mStreamMap.get(i.getTableTypePid()) != null) {
                        continue;
                    }
                    if (i.getTableType() >= MgtItem.TABLE_TYPE_EIT_RANGE_START
                            && i.getTableType() <= MgtItem.TABLE_TYPE_EIT_RANGE_END) {
                        startListening(i.getTableTypePid());
                        mEITPids.add(i.getTableTypePid());
                        if (mListener != null) {
                            mListener.onEitPidDetected(i.getTableTypePid());
                        }
                    } else if (i.getTableType() == MgtItem.TABLE_TYPE_CHANNEL_ETT ||
                            (i.getTableType() >= MgtItem.TABLE_TYPE_ETT_RANGE_START
                                    && i.getTableType() <= MgtItem.TABLE_TYPE_ETT_RANGE_END)) {
                        startListening(i.getTableTypePid());
                        mETTPids.add(i.getTableTypePid());
                        if (mListener != null) {
                            mListener.onEttPidDetected(i.getTableTypePid());
                        }
                    }
                }
            }

            @Override
            public void onVctParsed(List<VctItem> items, int sectionNumber, int lastSectionNumber) {
                if (mVctSectionParsed == null) {
                    mVctSectionParsed = new boolean[lastSectionNumber + 1];
                } else if (mVctSectionParsed[sectionNumber]) {
                    // The current section was handled before.
                    if (DEBUG) {
                        Log.d(TAG, "Duplicate VCT section found.");
                    }
                    return;
                }
                mVctSectionParsed[sectionNumber] = true;
                mVctSectionParsedCount++;
                mVctItemCount += items.size();
                for (VctItem i : items) {
                    if (DEBUG) Log.d(TAG, "onVCTParsed " + i);
                    if (i.getSourceId() != 0) {
                        mSourceIdToVctItemMap.put(i.getSourceId(), i);
                        i.setDescription(mSourceIdToVctItemDescriptionMap.get(i.getSourceId()));
                    }
                    int programNumber = i.getProgramNumber();
                    mProgramNumberToVctItemMap.put(programNumber, i);
                    List<PmtItem> pmtList = mProgramNumberToPMTMap.get(programNumber);
                    if (pmtList != null) {
                        mProgramNumberHandledStatus.put(programNumber, true);
                        handleVctItem(i, pmtList);
                        mHandledVctItemCount++;
                        if (mHandledVctItemCount >= mVctItemCount
                                && mVctSectionParsedCount >= mVctSectionParsed.length
                                && mListener != null) {
                            mListener.onAllVctItemsParsed();
                        }
                    } else {
                        mProgramNumberHandledStatus.put(programNumber, false);
                        Log.i(TAG, "onVCTParsed, but PMT for programNo " + programNumber
                                + " is not found yet.");
                    }
                }
            }

            @Override
            public void onEitParsed(int sourceId, List<EitItem> items) {
                if (DEBUG) Log.d(TAG, "onEITParsed " + sourceId);
                EventSourceEntry entry = new EventSourceEntry(mPid, sourceId);
                mEitMap.put(entry, items);
                handleEvents(sourceId);
            }

            @Override
            public void onEttParsed(int sourceId, List<EttItem> descriptions) {
                if (DEBUG) {
                    Log.d(TAG, String.format("onETTParsed sourceId: %d, descriptions.size(): %d",
                            sourceId, descriptions.size()));
                }
                for (EttItem item : descriptions) {
                    if (item.eventId == 0) {
                        // Channel description
                        mSourceIdToVctItemDescriptionMap.put(sourceId, item.text);
                        VctItem vctItem = mSourceIdToVctItemMap.get(sourceId);
                        if (vctItem != null) {
                            vctItem.setDescription(item.text);
                            List<PmtItem> pmtItems =
                                    mProgramNumberToPMTMap.get(vctItem.getProgramNumber());
                            if (pmtItems != null) {
                                handleVctItem(vctItem, pmtItems);
                            }
                        }
                    }
                }

                // Event Information description
                EventSourceEntry entry = new EventSourceEntry(mPid, sourceId);
                mETTMap.put(entry, descriptions);
                handleEvents(sourceId);
            }

            @Override
            public void onSdtParsed(List<SdtItem> sdtItems) {
                for (SdtItem sdtItem : sdtItems) {
                    if (DEBUG) Log.d(TAG, "onSdtParsed " + sdtItem);
                    int programNumber = sdtItem.getServiceId();
                    mProgramNumberToSdtItemMap.put(programNumber, sdtItem);
                    List<PmtItem> pmtList = mProgramNumberToPMTMap.get(programNumber);
                    if (pmtList != null) {
                        mProgramNumberHandledStatus.put(programNumber, true);
                        handleSdtItem(sdtItem, pmtList);
                    } else {
                        mProgramNumberHandledStatus.put(programNumber, false);
                        Log.i(TAG, "onSdtParsed, but PMT for programNo " + programNumber
                                + " is not found yet.");
                    }
                }
            }
        };
    }

    private static class EventSourceEntry {
        public final int pid;
        public final int sourceId;

        public EventSourceEntry(int pid, int sourceId) {
            this.pid = pid;
            this.sourceId = sourceId;
        }

        @Override
        public int hashCode() {
            int result = 17;
            result = 31 * result + pid;
            result = 31 * result + sourceId;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof EventSourceEntry) {
                EventSourceEntry another = (EventSourceEntry) obj;
                return pid == another.pid && sourceId == another.sourceId;
            }
            return false;
        }
    }

    private void handleVctItem(VctItem channel, List<PmtItem> pmtItems) {
        if (DEBUG) {
            Log.d(TAG, "handleVctItem " + channel);
        }
        if (mListener != null) {
            mListener.onVctItemParsed(channel, pmtItems);
        }
        int sourceId = channel.getSourceId();
        int statusIndex = mVctItemHandledStatus.indexOfKey(sourceId);
        if (statusIndex < 0) {
            mVctItemHandledStatus.put(sourceId, false);
            return;
        }
        if (!mVctItemHandledStatus.valueAt(statusIndex)) {
            List<EitItem> eitItems = mSourceIdToEitMap.get(sourceId);
            if (eitItems != null) {
                // When VCT is parsed later than EIT.
                mVctItemHandledStatus.put(sourceId, true);
                handleEitItems(channel, eitItems);
            }
        }
    }

    private void handleEitItems(VctItem channel, List<EitItem> items) {
        if (mListener != null) {
            mListener.onEitItemParsed(channel, items);
        }
    }

    private void handleSdtItem(SdtItem channel, List<PmtItem> pmtItems) {
        if (DEBUG) {
            Log.d(TAG, "handleSdtItem " + channel);
        }
        if (mListener != null) {
            mListener.onSdtItemParsed(channel, pmtItems);
        }
    }

    private void handleEvents(int sourceId) {
        Map<Integer, EitItem> itemSet = new HashMap<>();
        for (int pid : mEITPids) {
            List<EitItem> eitItems = mEitMap.get(new EventSourceEntry(pid, sourceId));
            if (eitItems != null) {
                for (EitItem item : eitItems) {
                    item.setDescription(null);
                    itemSet.put(item.getEventId(), item);
                }
            }
        }
        for (int pid : mETTPids) {
            List<EttItem> ettItems = mETTMap.get(new EventSourceEntry(pid, sourceId));
            if (ettItems != null) {
                for (EttItem ettItem : ettItems) {
                    if (ettItem.eventId != 0) {
                        EitItem item = itemSet.get(ettItem.eventId);
                        if (item != null) {
                            item.setDescription(ettItem.text);
                        }
                    }
                }
            }
        }
        List<EitItem> items = new ArrayList<>(itemSet.values());
        mSourceIdToEitMap.put(sourceId, items);
        VctItem channel = mSourceIdToVctItemMap.get(sourceId);
        if (channel != null && mProgramNumberHandledStatus.get(channel.getProgramNumber())) {
            mVctItemHandledStatus.put(sourceId, true);
            handleEitItems(channel, items);
        } else {
            mVctItemHandledStatus.put(sourceId, false);
            if (!mIsDvbSignal) {
                // Log only when zapping to non-DVB channels, since there is not VCT in DVB signal.
                Log.i(TAG, "onEITParsed, but VCT for sourceId " + sourceId + " is not found yet.");
            }
        }
    }

    /**
     * Creates MPEG-2 TS parser.
     *
     * @param listener TsOutputListener
     */
    public TsParser(TsOutputListener listener, boolean isDvbSignal) {
        startListening(PAT_PID);
        startListening(ATSC_SI_BASE_PID);
        mIsDvbSignal = isDvbSignal;
        if (isDvbSignal) {
            startListening(DVB_EIT_PID);
            startListening(DVB_SDT_PID);
        }
        mListener = listener;
    }

    private void startListening(int pid) {
        mStreamMap.put(pid, new SectionStream(pid));
    }

    private boolean feedTSPacket(byte[] tsData, int pos) {
        if (tsData.length < pos + TS_PACKET_SIZE) {
            if (DEBUG) Log.d(TAG, "Data should include a single TS packet.");
            return false;
        }
        if (tsData[pos] != TS_PACKET_START_CODE) {
            if (DEBUG) Log.d(TAG, "Invalid ts packet.");
            return false;
        }
        if ((tsData[pos + 1] & TS_PACKET_TEI_MASK) != 0) {
            if (DEBUG) Log.d(TAG, "Erroneous ts packet.");
            return false;
        }

        // For details for the structure of TS packet, see H.222.0 Table 2-2.
        int pid = ((tsData[pos + 1] & 0x1f) << 8) | (tsData[pos + 2] & 0xff);
        boolean hasAdaptation = (tsData[pos + 3] & 0x20) != 0;
        boolean hasPayload = (tsData[pos + 3] & 0x10) != 0;
        boolean payloadStartIndicator = (tsData[pos + 1] & 0x40) != 0;
        int continuityCounter = tsData[pos + 3] & 0x0f;
        Stream stream = mStreamMap.get(pid);
        int payloadPos = pos;
        payloadPos += hasAdaptation ? 5 + (tsData[pos + 4] & 0xff) : 4;
        if (!hasPayload || stream == null) {
            // We are not interested in this packet.
            return false;
        }
        if (payloadPos >= pos + TS_PACKET_SIZE) {
            if (DEBUG) Log.d(TAG, "Payload should be included in a single TS packet.");
            return false;
        }
        stream.feedData(Arrays.copyOfRange(tsData, payloadPos, pos + TS_PACKET_SIZE),
                continuityCounter, payloadStartIndicator);
        return true;
    }

    /**
     * Feeds MPEG-2 TS data to parse.
     * @param tsData buffer for ATSC TS stream
     * @param pos the offset where buffer starts
     * @param length The length of available data
     */
    public void feedTSData(byte[] tsData, int pos, int length) {
        for (; pos <= length - TS_PACKET_SIZE; pos += TS_PACKET_SIZE) {
            feedTSPacket(tsData, pos);
        }
    }

    /**
     * Retrieves the channel information regardless of being well-formed.
     * @return {@link List} of {@link TunerChannel}
     */
    public List<TunerChannel> getMalFormedChannels() {
        List<TunerChannel> incompleteChannels = new ArrayList<>();
        for (int i = 0; i < mProgramNumberHandledStatus.size(); i++) {
            if (!mProgramNumberHandledStatus.valueAt(i)) {
                int programNumber = mProgramNumberHandledStatus.keyAt(i);
                List<PmtItem> pmtList = mProgramNumberToPMTMap.get(programNumber);
                if (pmtList != null) {
                    TunerChannel tunerChannel = new TunerChannel(programNumber, pmtList);
                    incompleteChannels.add(tunerChannel);
                }
            }
        }
        return incompleteChannels;
    }

    /**
     * Reset the versions so that data with old version number can be handled.
     */
    public void resetDataVersions() {
        for (int eitPid : mEITPids) {
            Stream stream = mStreamMap.get(eitPid);
            if (stream != null) {
                stream.resetDataVersions();
            }
        }
    }
}
