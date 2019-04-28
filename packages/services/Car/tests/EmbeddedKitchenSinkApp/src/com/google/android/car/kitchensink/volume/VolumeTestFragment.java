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
package com.google.android.car.kitchensink.volume;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.media.CarAudioManager;
import android.content.Context;
import android.media.AudioManager;
import android.media.IVolumeController;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;

import com.google.android.car.kitchensink.CarEmulator;
import com.google.android.car.kitchensink.R;

public class VolumeTestFragment extends Fragment{
    private static final String TAG = "CarVolumeTest";
    private static final int MSG_VOLUME_CHANGED = 0;
    private static final int MSG_REQUEST_FOCUS = 1;
    private static final int MSG_FOCUS_CHANGED= 2;

    private ListView mVolumeList;
    private Button mRefreshButton;
    private AudioManager mAudioManager;
    private VolumeAdapter mAdapter;

    private CarAudioManager mCarAudioManager;
    private Car mCar;
    private CarEmulator mCarEmulator;

    private Button mVolumeUp;
    private Button mVolumeDown;

    private final VolumeController mVolumeController = new VolumeController();
    private final Handler mHandler = new VolumeHandler();

    private class VolumeHandler extends Handler {
        private AudioFocusListener mFocusListener;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_VOLUME_CHANGED:
                    initVolumeInfo();
                    break;
                case MSG_REQUEST_FOCUS:
                    int stream = msg.arg1;
                    if (mFocusListener != null) {
                        mAudioManager.abandonAudioFocus(mFocusListener);
                        mVolumeInfos[mStreamIndexMap.get(stream)].mHasFocus = false;
                        mAdapter.notifyDataSetChanged();
                    }

                    mFocusListener = new AudioFocusListener(stream);
                    mAudioManager.requestAudioFocus(mFocusListener, stream,
                            AudioManager.AUDIOFOCUS_GAIN);
                    break;
                case MSG_FOCUS_CHANGED:
                    int focusStream = msg.arg1;
                    mVolumeInfos[mStreamIndexMap.get(focusStream)].mHasFocus = true;
                    mAdapter.refreshVolumes(mVolumeInfos);
                    break;

            }
        }
    }

    private VolumeInfo[] mVolumeInfos = new VolumeInfo[LOGICAL_STREAMS.length + 1];
    private SparseIntArray mStreamIndexMap = new SparseIntArray(LOGICAL_STREAMS.length);

    private class AudioFocusListener implements AudioManager.OnAudioFocusChangeListener {
        private final int mStream;
        public AudioFocusListener(int stream) {
            mStream = stream;
        }
        @Override
        public void onAudioFocusChange(int focusChange) {
            if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_FOCUS_CHANGED, mStream, 0));
            } else {
                Log.e(TAG, "Audio focus request failed");
            }
        }
    }

    public static class VolumeInfo {
        public int logicalStream;
        public String mId;
        public String mMax;
        public String mCurrent;
        public String mLogicalMax;
        public String mLogicalCurrent;
        public boolean mHasFocus;
    }

    private static final int LOGICAL_STREAMS[] = {
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_SYSTEM
            //            AudioManager.STREAM_DTMF,
    };

    private static String streamToName (int stream) {
        switch (stream) {
            case AudioManager.STREAM_ALARM: return "Alarm";
            case AudioManager.STREAM_MUSIC: return "Music";
            case AudioManager.STREAM_NOTIFICATION: return "Notification";
            case AudioManager.STREAM_RING: return "Ring";
            case AudioManager.STREAM_VOICE_CALL: return "Call";
            case AudioManager.STREAM_SYSTEM: return "System";
            default: return "Unknown";
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.volume_test, container, false);

        mVolumeList = (ListView) v.findViewById(R.id.volume_list);
        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);

        mRefreshButton = (Button) v.findViewById(R.id.refresh);
        mAdapter = new VolumeAdapter(getContext(), R.layout.volume_item, mVolumeInfos, this);
        mVolumeList.setAdapter(mAdapter);

        mRefreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                initVolumeInfo();
            }
        });

        mCarEmulator = CarEmulator.create(getContext());
        mCar = mCarEmulator.getCar();
        try {
            mCarAudioManager = (CarAudioManager) mCar.getCarManager(Car.AUDIO_SERVICE);
            initVolumeInfo();
            mCarAudioManager.setVolumeController(mVolumeController);
        } catch (CarNotConnectedException e) {
            throw new RuntimeException(e); // Should never occur in car emulator.
        }

        mVolumeUp = (Button) v.findViewById(R.id.volume_up);
        mVolumeDown = (Button) v.findViewById(R.id.volume_down);

        mVolumeUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mCarEmulator.injectKey(KeyEvent.KEYCODE_VOLUME_UP);
            }
        });

        mVolumeDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mCarEmulator.injectKey(KeyEvent.KEYCODE_VOLUME_DOWN);
            }
        });

        return v;
    }

    public void adjustVolumeByOne(int logicalStream, boolean up) {
        if (mCarAudioManager == null) {
            Log.e(TAG, "CarAudioManager is null");
            return;
        }
        int current = 0;
        try {
            current = mCarAudioManager.getStreamVolume(logicalStream);
        } catch (CarNotConnectedException e) {
            Log.e(TAG, "car not connected", e);
        }
        setStreamVolume(logicalStream, current + (up ? 1 : -1));
    }

    public void requestFocus(int logicalStream) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_REQUEST_FOCUS, logicalStream));
    }

    public void setStreamVolume(int logicalStream, int volume) {
        if (mCarAudioManager == null) {
            Log.e(TAG, "CarAudioManager is null");
            return;
        }
        try {
            mCarAudioManager.setStreamVolume(logicalStream, volume, 0);
        } catch (CarNotConnectedException e) {
            Log.e(TAG, "car not connected", e);
        }

        Log.d(TAG, "Set stream " + logicalStream + " volume " + volume);
    }


    private void initVolumeInfo() {
        if (mVolumeInfos[0] == null) {
            mVolumeInfos[0] = new VolumeInfo();
            mVolumeInfos[0].mId = "Stream";
            mVolumeInfos[0].mCurrent = "Current";
            mVolumeInfos[0].mMax = "Max";
            mVolumeInfos[0].mLogicalMax = "Android_Max";
            mVolumeInfos[0].mLogicalCurrent = "Android_Current";

        }
        int i = 1;
        for (int stream : LOGICAL_STREAMS) {
            if (mVolumeInfos[i] == null) {
                mVolumeInfos[i] = new VolumeInfo();
            }
            mVolumeInfos[i].logicalStream = stream;
            mStreamIndexMap.put(stream, i);
            mVolumeInfos[i].mId = streamToName(stream);

            int current = 0;
            int max = 0;
            try {
                current = mCarAudioManager.getStreamVolume(stream);
                max = mCarAudioManager.getStreamMaxVolume(stream);
            } catch (CarNotConnectedException e) {
                Log.e(TAG, "car not connected", e);
            }

            mVolumeInfos[i].mCurrent = String.valueOf(current);
            mVolumeInfos[i].mMax = String.valueOf(max);

            mVolumeInfos[i].mLogicalMax = String.valueOf(mAudioManager.getStreamMaxVolume(stream));
            mVolumeInfos[i].mLogicalCurrent = String.valueOf(mAudioManager.getStreamVolume(stream));

            Log.d(TAG, stream + " max: " + mVolumeInfos[i].mMax + " current: "
                    + mVolumeInfos[i].mCurrent);
            i++;
        }
        mAdapter.refreshVolumes(mVolumeInfos);
    }

    @Override
    public void onDestroy() {
        if (mCar != null) {
            mCar.disconnect();
        }
        super.onDestroy();
    }

    private class VolumeController extends IVolumeController.Stub {

        @Override
        public void displaySafeVolumeWarning(int flags) throws RemoteException {}

        @Override
        public void volumeChanged(int streamType, int flags) throws RemoteException {
            mHandler.sendMessage(mHandler.obtainMessage(MSG_VOLUME_CHANGED, streamType));
        }

        @Override
        public void masterMuteChanged(int flags) throws RemoteException {}

        @Override
        public void setLayoutDirection(int layoutDirection) throws RemoteException {
        }

        @Override
        public void dismiss() throws RemoteException {
        }

        @Override
        public void setA11yMode(int mode) throws RemoteException {
        }
    }
}
