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

package com.google.android.car.kitchensink.radio;

import android.annotation.Nullable;
import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.media.CarAudioManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioMetadata;
import android.hardware.radio.RadioTuner;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.android.car.kitchensink.R;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class RadioTestFragment extends Fragment {
    private static final String TAG = "CAR.RADIO.KS";
    private static final boolean DBG = true;
    private static final int MAX_LOG_MESSAGES = 100;

    private final AudioManager.OnAudioFocusChangeListener mRadioFocusListener =
            new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    addLog(Log.INFO, "Radio focus change:" + focusChange);
                }
            };

    private final AudioManager.OnAudioFocusChangeListener mSecondaryFocusListener =
            new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    addLog(Log.INFO, "Secondary focus change:" + focusChange);
                }
            };

    private final RadioTuner.Callback mRadioCallback = new RadioTuner.Callback() {
        @Override
        public void onError(int status) {
            addLog(Log.WARN, "Radio tuner error " + status);
        }

        @Override
        public void onConfigurationChanged(RadioManager.BandConfig config) {
            addLog(Log.INFO, "Radio tuner configuration changed. config:" + config);
        }

        @Override
        public void onMetadataChanged(RadioMetadata metadata) {
            addLog(Log.INFO, "Radio tuner metadata changed. metadata:" + metadata);
            if (metadata == null) {
                resetMessages();
                updateMessages();
                return;
            }
            mArtist = metadata.getString(RadioMetadata.METADATA_KEY_ARTIST);
            mSong = metadata.getString(RadioMetadata.METADATA_KEY_TITLE);
            mStation = metadata.getString(RadioMetadata.METADATA_KEY_RDS_PS);
            updateMessages();
        }

        @Override
        public void onProgramInfoChanged(RadioManager.ProgramInfo info) {
            addLog(Log.INFO, "Radio tuner program info. info:" + info);
            mChannel = String.valueOf(info.getChannel());
            onMetadataChanged(info.getMetadata());
            updateMessages();
        }

    };
    private final LinkedList<String> mLogMessages = new LinkedList<>();

    private Button mOpenRadio;
    private Button mCloseRadio;
    private Button mGetRadioFocus;
    private Button mReleaseRadioFocus;
    private Button mGetFocus;
    private Button mReleaseFocus;
    private Button mRadioNext;
    private Button mRadioPrev;
    private Button mRadioScanCancel;
    private Button mRadioGetProgramInfo;
    private Button mRadioTuneToStation;
    private Button mRadioStepUp;
    private Button mRadioStepDown;
    private EditText mStationFrequency;
    private ToggleButton mToggleMuteRadio;
    private ToggleButton mRadioBand;
    private TextView mStationInfo;
    private TextView mChannelInfo;
    private TextView mSongInfo;
    private TextView mArtistInfo;
    private TextView mLog;

    private Car mCar;
    private CarAudioManager mCarAudioManager;
    private AudioAttributes mRadioAudioAttrib;
    private AudioManager mAudioManager;
    private boolean mHasRadioFocus;
    private boolean mHasSecondaryFocus;
    private RadioTuner mRadioTuner;
    private RadioManager mRadioManager;
    private RadioManager.FmBandDescriptor mFmDescriptor;
    private RadioManager.AmBandDescriptor mAmDescriptor;
    private String mStation;
    private String mChannel;
    private String mSong;
    private String mArtist;
    private String mNaString;

    private RadioManager.BandConfig mFmConfig;
    private RadioManager.BandConfig mAmConfig;

    private final List<RadioManager.ModuleProperties> mModules = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        if (DBG) {
            Log.i(TAG, "onCreateView");
        }

        init();
        View view = inflater.inflate(R.layout.radio, container, false);

        mOpenRadio = (Button) view.findViewById(R.id.button_open_radio);
        mCloseRadio = (Button) view.findViewById(R.id.button_close_radio);
        mGetRadioFocus = (Button) view.findViewById(R.id.button_get_radio_focus);
        mReleaseRadioFocus = (Button) view.findViewById(R.id.button_release_radio_focus);
        mGetFocus = (Button) view.findViewById(R.id.button_get_focus_in_radio);
        mReleaseFocus = (Button) view.findViewById(R.id.button_release_focus_in_radio);
        mRadioNext = (Button) view.findViewById(R.id.button_radio_next);
        mRadioPrev = (Button) view.findViewById(R.id.button_radio_prev);
        mRadioScanCancel = (Button) view.findViewById(R.id.button_radio_scan_cancel);
        mRadioGetProgramInfo = (Button) view.findViewById(R.id.button_radio_get_program_info);
        mRadioTuneToStation = (Button) view.findViewById(R.id.button_radio_tune_to_station);
        mRadioStepUp = (Button) view.findViewById(R.id.button_radio_step_up);
        mRadioStepDown = (Button) view.findViewById(R.id.button_radio_step_down);

        mStationFrequency = (EditText) view.findViewById(R.id.edittext_station_frequency);

        mToggleMuteRadio = (ToggleButton) view.findViewById(R.id.togglebutton_mute_radio);
        mToggleMuteRadio.setChecked(true);
        mRadioBand = (ToggleButton) view.findViewById(R.id.button_band_selection);

        mStationInfo = (TextView) view.findViewById(R.id.radio_station_info);
        mChannelInfo = (TextView) view.findViewById(R.id.radio_channel_info);
        mSongInfo = (TextView) view.findViewById(R.id.radio_song_info);
        mArtistInfo = (TextView) view.findViewById(R.id.radio_artist_info);

        mLog = (TextView) view.findViewById(R.id.radio_log);
        mLog.setMovementMethod(new ScrollingMovementMethod());

        mNaString = getContext().getString(R.string.radio_na);

        addHandlers();
        updateStates();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        resetMessages();
        updateStates();
        updateMessages();
        resetLog();
    }

    private void init() {
        mCar = Car.createCar(getContext(), new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                try {
                    mCarAudioManager = (CarAudioManager) mCar.getCarManager(Car.AUDIO_SERVICE);
                    mRadioAudioAttrib = mCarAudioManager.getAudioAttributesForCarUsage(
                            CarAudioManager.CAR_AUDIO_USAGE_RADIO);
                } catch (CarNotConnectedException e) {
                    Log.e(TAG, "Car not connected", e);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        });
        mCar.connect();
        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        initializeRadio();
    }

    private void initializeRadio() {
        mRadioManager = (RadioManager) getContext().getSystemService(Context.RADIO_SERVICE);

        if (mRadioManager == null) {
            throw new IllegalStateException("RadioManager could not be loaded.");
        }

        int status = mRadioManager.listModules(mModules);
        if (status != RadioManager.STATUS_OK) {
            throw new IllegalStateException("Load modules failed with status: " + status);
        }

        if (mModules.size() == 0) {
            throw new IllegalStateException("No radio modules on device.");
        }

        boolean isDebugLoggable = Log.isLoggable(TAG, Log.DEBUG);

        // Load the possible radio bands. For now, just accept FM and AM bands.
        for (RadioManager.BandDescriptor band : mModules.get(0).getBands()) {
            if (isDebugLoggable) {
                Log.d(TAG, "loading band: " + band.toString());
            }

            if (mFmDescriptor == null && band.isFmBand()) {
                mFmDescriptor = (RadioManager.FmBandDescriptor) band;
            }

            if (mAmDescriptor == null && band.isAmBand()) {
                mAmDescriptor = (RadioManager.AmBandDescriptor) band;
            }
        }

        if (mFmDescriptor == null && mAmDescriptor == null) {
            throw new IllegalStateException("No AM and FM radio bands could be loaded.");
        }

        mFmConfig = new RadioManager.FmBandConfig.Builder(mFmDescriptor)
                .setStereo(true)
                .build();
        mAmConfig = new RadioManager.AmBandConfig.Builder(mAmDescriptor)
                .setStereo(true)
                .build();
    }

    private void addHandlers() {
        mOpenRadio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleRadioStart();
                updateStates();
            }
        });
        mCloseRadio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleRadioEnd();
                mToggleMuteRadio.setChecked(true);
                updateStates();
            }
        });
        mToggleMuteRadio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DBG) {
                    Log.i(TAG, "Toggle mute radio");
                }
                mRadioTuner.setMute(!mRadioTuner.getMute());
                updateStates();
            }
        });
        mGetRadioFocus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DBG) {
                    Log.i(TAG, "Get radio focus");
                }
                try {
                    mCarAudioManager.requestAudioFocus(mRadioFocusListener, mRadioAudioAttrib,
                            AudioManager.AUDIOFOCUS_GAIN, 0);
                } catch (CarNotConnectedException e) {
                  //ignore for now
                }
                mHasRadioFocus = true;
                updateStates();
            }
        });
        mReleaseRadioFocus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DBG) {
                    Log.i(TAG, "Release radio focus");
                }
                mCarAudioManager.abandonAudioFocus(mRadioFocusListener, mRadioAudioAttrib);
                mHasRadioFocus = false;
                updateStates();
            }
        });
        mGetFocus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DBG) {
                    Log.i(TAG, "Get secondary focus");
                }
                mAudioManager.requestAudioFocus(mSecondaryFocusListener,
                        AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                mHasSecondaryFocus = true;
                updateStates();
            }
        });
        mReleaseFocus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DBG) {
                    Log.i(TAG, "Release secondary focus");
                }
                mAudioManager.abandonAudioFocus(mSecondaryFocusListener);
                mHasSecondaryFocus = false;
                updateStates();
            }
        });
        mRadioNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DBG) {
                    Log.i(TAG, "Next radio station");
                }
                if (mRadioTuner != null) {
                    mRadioTuner.scan(RadioTuner.DIRECTION_UP, true);
                }
                updateStates();
            }
        });
        mRadioPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DBG) {
                    Log.i(TAG, "Previous radio station");
                }
                if (mRadioTuner != null) {
                    mRadioTuner.scan(RadioTuner.DIRECTION_DOWN, true);
                }
                updateStates();
            }
        });
        mRadioScanCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DBG) {
                    Log.i(TAG, "Cancel radio scan");
                }
                if (mRadioTuner != null) {
                    mRadioTuner.cancel();
                }
                updateStates();
            }
        });
        mRadioTuneToStation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DBG) {
                    Log.i(TAG, "Tuning to station");
                }
                String station = mStationFrequency.getText().toString().trim();
                if (mRadioTuner != null && !(station.equals(""))) {
                    mRadioTuner.tune(Integer.parseInt(station), 0);
                }
                resetMessages();
                updateMessages();
                updateStates();
            }
        });
        mRadioStepUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DBG) {
                    Log.i(TAG, "Step up");
                }
                if (mRadioTuner != null) {
                    mRadioTuner.step(RadioTuner.DIRECTION_UP, false);
                }
                resetMessages();
                updateMessages();
                updateStates();
            }
        });
        mRadioStepDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DBG) {
                    Log.i(TAG, "Step down");
                }
                if (mRadioTuner != null) {
                    mRadioTuner.step(RadioTuner.DIRECTION_DOWN, false);
                }
                resetMessages();
                updateMessages();
                updateStates();
            }
        });
        mRadioGetProgramInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DBG) {
                    Log.i(TAG, "getProgramInformation");
                }
                if (mRadioTuner != null) {
                    RadioManager.ProgramInfo[] programInfos = new RadioManager.ProgramInfo[1];
                    mRadioTuner.getProgramInformation(programInfos);
                    addLog(Log.INFO, "mRadioTuner.getProgramInformation() =>" + programInfos[0]);
                }
            }
        });
        mRadioBand.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (DBG) {
                    Log.i(TAG, "Changing radio band");
                }
                if (mRadioTuner != null) {
                    mRadioTuner.setConfiguration(mRadioBand.isChecked() ? mFmConfig : mAmConfig);
                }
                resetMessages();
                updateMessages();
                updateStates();
            }
        });
    }

    private void updateStates() {
        mOpenRadio.setEnabled(mRadioTuner == null);
        mCloseRadio.setEnabled(mRadioTuner != null);
        mToggleMuteRadio.setEnabled(mRadioTuner != null);
        mGetRadioFocus.setEnabled(!mHasRadioFocus);
        mReleaseRadioFocus.setEnabled(mHasRadioFocus);
        mGetFocus.setEnabled(!mHasSecondaryFocus);
        mReleaseFocus.setEnabled(mHasSecondaryFocus);
        mRadioNext.setEnabled(mRadioTuner != null);
        mRadioPrev.setEnabled(mRadioTuner != null);
        mRadioBand.setEnabled(mRadioTuner != null);
        mRadioScanCancel.setEnabled(mRadioTuner != null);
        mRadioTuneToStation.setEnabled(mRadioTuner != null);
        mRadioStepUp.setEnabled(mRadioTuner != null);
        mRadioStepDown.setEnabled(mRadioTuner != null);
        mStationFrequency.setEnabled(mRadioTuner != null);
        mRadioGetProgramInfo.setEnabled(mRadioTuner != null);
    }

    private void updateMessages() {
        mStationInfo.setText(getContext().getString
                (R.string.radio_station_info, mStation == null ? mNaString : mStation));
        mChannelInfo.setText(getContext().getString
                (R.string.radio_channel_info, mChannel == null ? mNaString : mChannel));
        mArtistInfo.setText(getContext().getString
                (R.string.radio_artist_info, mArtist == null ? mNaString : mArtist));
        mSongInfo.setText(getContext().getString
                (R.string.radio_song_info, mSong == null ? mNaString : mSong));
    }

    private void resetMessages() {
        mStation = null;
        mChannel = null;
        mSong = null;
        mArtist = null;
    }

    private void handleRadioStart() {
        if (mCarAudioManager == null) {
            return;
        }
        if (DBG) {
            Log.i(TAG, "Radio start");
        }
        if (mRadioTuner != null) {
            Log.w(TAG, "Radio tuner already open");
            mRadioTuner.close();
            mRadioTuner = null;
        }
        mRadioTuner = mRadioManager.openTuner(mModules.get(0).getId(),
                mRadioBand.isChecked() ? mFmConfig : mAmConfig,
                true, mRadioCallback /* callback */, null /* handler */);
    }

    private void handleRadioEnd() {
        if (mCarAudioManager == null) {
            return;
        }
        if (DBG) {
            Log.i(TAG, "Radio end");
        }
        mRadioTuner.close();
        mRadioTuner = null;
    }

    private void resetLog() {
        synchronized (this) {
            mLogMessages.clear();
        }
    }

    private void addLog(int priority, String message) {
        Log.println(priority, TAG, message);
        synchronized (this) {
            mLogMessages.add(message);
            if (mLogMessages.size() > MAX_LOG_MESSAGES) {
                mLogMessages.poll();
            }
            mLog.setText(TextUtils.join("\n", mLogMessages));
        }
    }
}
