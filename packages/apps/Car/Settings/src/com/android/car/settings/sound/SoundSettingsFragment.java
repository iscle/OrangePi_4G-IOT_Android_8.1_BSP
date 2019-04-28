/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.car.settings.sound;

import android.app.Activity;
import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.media.CarAudioManager;
import android.annotation.DrawableRes;
import android.annotation.StringRes;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.media.AudioAttributes;
import android.media.IVolumeController;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import com.android.car.settings.common.BaseFragment;
import com.android.car.settings.common.TypedPagedListAdapter;
import com.android.car.settings.R;
import com.android.car.view.PagedListView;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Activity hosts sound related settings.
 */
public class SoundSettingsFragment extends BaseFragment {
    private static final String TAG = "SoundSettingsFragment";
    private Car mCar;
    private CarAudioManager mCarAudioManager;
    private PagedListView mListView;
    private TypedPagedListAdapter mPagedListAdapter;

    private final ArrayList<VolumeLineItem> mVolumeLineItems = new ArrayList<>();
    private final VolumeCallback
            mVolumeCallback = new VolumeCallback();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final HashSet<StreamItem> mUniqueStreamItems = new HashSet<>();

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                mCarAudioManager = (CarAudioManager) mCar.getCarManager(Car.AUDIO_SERVICE);
                mCarAudioManager.setVolumeController(mVolumeCallback);

                mUniqueStreamItems.add(new StreamItem(
                        mCarAudioManager.getAudioAttributesForCarUsage(
                                mCarAudioManager.CAR_AUDIO_USAGE_MUSIC)
                                .getVolumeControlStream(),
                        R.string.media_volume_title,
                        com.android.internal.R.drawable.ic_audio_media));
                mUniqueStreamItems.add(new StreamItem(
                        mCarAudioManager.getAudioAttributesForCarUsage(
                                mCarAudioManager.CAR_AUDIO_USAGE_SYSTEM_SOUND)
                                .getVolumeControlStream(),
                        R.string.ring_volume_title,
                        com.android.internal.R.drawable.ic_audio_ring_notif));
                mUniqueStreamItems.add(new StreamItem(
                        mCarAudioManager.getAudioAttributesForCarUsage(
                                mCarAudioManager.CAR_AUDIO_USAGE_NAVIGATION_GUIDANCE)
                                .getVolumeControlStream(),
                        R.string.navi_volume_title,
                        R.drawable.ic_audio_navi));
            } catch (CarNotConnectedException e) {
                Log.e(TAG, "Car is not connected!", e);
                return;
            }

            for (StreamItem streamItem : mUniqueStreamItems) {
                mVolumeLineItems.add(new VolumeLineItem(
                        getContext(),
                        mCarAudioManager,
                        streamItem.volumeStream,
                        streamItem.nameStringId,
                        streamItem.iconId));
            }
            // if list is already initiated, update it's content.
            if (mPagedListAdapter != null) {
                mPagedListAdapter.updateList(new ArrayList<>(mVolumeLineItems));
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            try {
                mCarAudioManager.setVolumeController(null);
            } catch (CarNotConnectedException e) {
            Log.e(TAG, "Car is not connected!", e);
            return;
            }
            mCarAudioManager = null;
        }
    };

    public static SoundSettingsFragment getInstance() {
        SoundSettingsFragment soundSettingsFragment = new SoundSettingsFragment();
        Bundle bundle = BaseFragment.getBundle();
        bundle.putInt(EXTRA_TITLE_ID, R.string.sound_settings);
        bundle.putInt(EXTRA_LAYOUT, R.layout.list);
        bundle.putInt(EXTRA_ACTION_BAR_LAYOUT, R.layout.action_bar);
        soundSettingsFragment.setArguments(bundle);
        return soundSettingsFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mCar = Car.createCar(getContext(), mServiceConnection);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mListView = (PagedListView) getView().findViewById(R.id.list);
        mListView.setDarkMode();
        mPagedListAdapter = new TypedPagedListAdapter(getContext());
        mListView.setAdapter(mPagedListAdapter);
        if (!mVolumeLineItems.isEmpty()) {
            mPagedListAdapter.updateList(new ArrayList<>(mVolumeLineItems));
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mCar.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        for (VolumeLineItem item : mVolumeLineItems) {
            item.stop();
        }
        mVolumeLineItems.clear();
        mCar.disconnect();
    }

    /**
     * The interface has a terrible name, it is actually a callback, so here name it accordingly.
     */
    private final class VolumeCallback extends IVolumeController.Stub {
        @Override
        public void displaySafeVolumeWarning(int flags) throws RemoteException {
        }

        @Override
        public void volumeChanged(int streamType, int flags) throws RemoteException {
            Activity activity = getActivity();
            if (activity == null) {
                Log.w(TAG, "no activity attached.");
                return;
            }

            for (VolumeLineItem item : mVolumeLineItems) {
                if (streamType == item.getStreamType()) {
                    handler.post(() -> mPagedListAdapter.notifyDataSetChanged());
                    return;
                }
            }

        }

        // this is not mute of this stream
        @Override
        public void masterMuteChanged(int flags) throws RemoteException {
        }

        @Override
        public void setLayoutDirection(int layoutDirection) throws RemoteException {
        }

        @Override
        public void dismiss() throws RemoteException {
        }

        @Override
        public void setA11yMode(int mode) {
        }
    }

    /**
     * Wraps information needed to render an audio stream, keyed by volumeControlStream.
     */
    private static final class StreamItem {
        final int volumeStream;

        @StringRes
        final int nameStringId;

        @DrawableRes
        final int iconId;

        StreamItem(
                int volumeStream,
                @StringRes int nameStringId,
                @DrawableRes int iconId) {
            this.volumeStream = volumeStream;
            this.nameStringId = nameStringId;
            this.iconId = iconId;
        }

        @Override
        public int hashCode() {
            return volumeStream;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof  StreamItem)) {
                return false;
            }
            return volumeStream == ((StreamItem) o).volumeStream;
        }
    }
}
