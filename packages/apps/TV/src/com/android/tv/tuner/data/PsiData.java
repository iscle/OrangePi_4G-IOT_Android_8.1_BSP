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

import com.android.tv.tuner.data.nano.Track.AtscAudioTrack;
import com.android.tv.tuner.data.nano.Track.AtscCaptionTrack;

import java.util.List;

/**
 * Collection of MPEG PSI table items.
 */
public class PsiData {

    private PsiData() {
    }

    public static class PatItem {
        private final int mProgramNo;
        private final int mPmtPid;

        public PatItem(int programNo, int pmtPid) {
            mProgramNo = programNo;
            mPmtPid = pmtPid;
        }

        public int getProgramNo() {
            return mProgramNo;
        }

        public int getPmtPid() {
            return mPmtPid;
        }

        @Override
        public String toString() {
            return String.format("Program No: %x PMT Pid: %x", mProgramNo, mPmtPid);
        }
    }

    public static class PmtItem {
        public static final int ES_PID_PCR = 0x100;

        private final int mStreamType;
        private final int mEsPid;
        private final List<AtscAudioTrack> mAudioTracks;
        private final List<AtscCaptionTrack> mCaptionTracks;

        public PmtItem(int streamType, int esPid,
                List<AtscAudioTrack> audioTracks, List<AtscCaptionTrack> captionTracks) {
            mStreamType = streamType;
            mEsPid = esPid;
            mAudioTracks = audioTracks;
            mCaptionTracks = captionTracks;
        }

        public int getStreamType() {
            return mStreamType;
        }

        public int getEsPid() {
            return mEsPid;
        }

        public List<AtscAudioTrack> getAudioTracks() {
            return mAudioTracks;
        }

        public List<AtscCaptionTrack> getCaptionTracks() {
            return mCaptionTracks;
        }

        @Override
        public String toString() {
            return String.format("Stream Type: %x ES Pid: %x AudioTracks: %s CaptionTracks: %s",
                    mStreamType, mEsPid, mAudioTracks, mCaptionTracks);
        }
    }
}
