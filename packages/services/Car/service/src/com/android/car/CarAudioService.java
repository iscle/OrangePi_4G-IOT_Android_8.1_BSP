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
package com.android.car;

import android.car.Car;
import android.car.VehicleZoneUtil;
import android.car.media.CarAudioManager;
import android.car.media.CarAudioManager.OnParameterChangeListener;
import android.car.media.ICarAudio;
import android.car.media.ICarAudioCallback;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.IVolumeController;
import android.media.audiopolicy.AudioMix;
import android.media.audiopolicy.AudioMixingRule;
import android.media.audiopolicy.AudioPolicy;
import android.media.audiopolicy.AudioPolicy.AudioPolicyFocusListener;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.android.car.hal.AudioHalService;
import com.android.car.hal.AudioHalService.AudioHalFocusListener;
import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class CarAudioService extends ICarAudio.Stub implements CarServiceBase,
        AudioHalFocusListener, OnParameterChangeListener {

    public interface AudioContextChangeListener {
        /**
         * Notifies the current primary audio context (app holding focus).
         * If there is no active context, context will be 0.
         * Will use context like CarAudioManager.CAR_AUDIO_USAGE_*
         */
        void onContextChange(int primaryFocusContext, int primaryFocusPhysicalStream);
    }

    private final long mFocusResponseWaitTimeoutMs;

    private final int mNumConsecutiveHalFailuresForCanError;

    private static final String TAG_FOCUS = CarLog.TAG_AUDIO + ".FOCUS";

    private static final boolean DBG = false;
    private static final boolean DBG_DYNAMIC_AUDIO_ROUTING = false;

    /**
     * For no focus play case, wait this much to send focus request. This ugly time is necessary
     * as focus could have been already requested by app but the event is not delivered to car
     * service yet. In such case, requesting focus in advance can lead into request with wrong
     * context. So let it wait for this much to make sure that focus change is delivered.
     */
    private static final long NO_FOCUS_PLAY_WAIT_TIME_MS = 100;

    private static final String RADIO_ROUTING_SOURCE_PREFIX = "RADIO_";

    private final AudioHalService mAudioHal;
    private final Context mContext;
    private final HandlerThread mFocusHandlerThread;
    private final CarAudioFocusChangeHandler mFocusHandler;
    private final SystemFocusListener mSystemFocusListener;
    private final CarVolumeService mVolumeService;
    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private AudioPolicy mAudioPolicy;
    @GuardedBy("mLock")
    private FocusState mCurrentFocusState = FocusState.STATE_LOSS;
    /** Focus state received, but not handled yet. Once handled, this will be set to null. */
    @GuardedBy("mLock")
    private FocusState mFocusReceived = null;
    @GuardedBy("mLock")
    private FocusRequest mLastFocusRequestToCar = null;
    @GuardedBy("mLock")
    private LinkedList<AudioFocusInfo> mPendingFocusChanges = new LinkedList<>();
    @GuardedBy("mLock")
    private AudioFocusInfo mPrimaryFocusInfo = null;
    /** previous top which may be in ducking state */
    @GuardedBy("mLock")
    private AudioFocusInfo mSecondaryFocusInfo = null;

    private AudioRoutingPolicy mAudioRoutingPolicy;
    private final AudioManager mAudioManager;
    private final CanBusErrorNotifier mCanBusErrorNotifier;
    private final BottomAudioFocusListener mBottomAudioFocusListener =
            new BottomAudioFocusListener();
    private final CarProxyAndroidFocusListener mCarProxyAudioFocusListener =
            new CarProxyAndroidFocusListener();
    private final MediaMuteAudioFocusListener mMediaMuteAudioFocusListener =
            new MediaMuteAudioFocusListener();

    @GuardedBy("mLock")
    private boolean mRadioOrExtSourceActive = false;
    @GuardedBy("mLock")
    private int mCurrentAudioContexts = 0;
    @GuardedBy("mLock")
    private int mCurrentPrimaryAudioContext = 0;
    @GuardedBy("mLock")
    private int mCurrentPrimaryPhysicalStream = 0;
    @GuardedBy("mLock")
    private AudioContextChangeListener mAudioContextChangeListener;
    @GuardedBy("mLock")
    private CarAudioContextChangeHandler mCarAudioContextChangeHandler;
    @GuardedBy("mLock")
    private boolean mIsRadioExternal;
    @GuardedBy("mLock")
    private int mNumConsecutiveHalFailures;

    @GuardedBy("mLock")
    private boolean mExternalRoutingHintSupported;
    @GuardedBy("mLock")
    private Map<String, AudioHalService.ExtRoutingSourceInfo> mExternalRoutingTypes;
    @GuardedBy("mLock")
    private Set<String> mExternalRadioRoutingTypes;
    @GuardedBy("mLock")
    private String mDefaultRadioRoutingType;
    @GuardedBy("mLock")
    private Set<String> mExternalNonRadioRoutingTypes;
    @GuardedBy("mLock")
    private int mRadioPhysicalStream;
    @GuardedBy("mLock")
    private int[] mExternalRoutings = {0, 0, 0, 0};
    private int[] mExternalRoutingsScratch = {0, 0, 0, 0};
    private final int[] mExternalRoutingsForFocusRelease = {0, 0, 0, 0};
    private final ExtSourceInfo mExtSourceInfoScratch = new ExtSourceInfo();
    @GuardedBy("mLock")
    private int mSystemSoundPhysicalStream;
    @GuardedBy("mLock")
    private boolean mSystemSoundPhysicalStreamActive;

    private final boolean mUseDynamicRouting;

    private final AudioAttributes mAttributeBottom =
            CarAudioAttributesUtil.getAudioAttributesForCarUsage(
                    CarAudioAttributesUtil.CAR_AUDIO_USAGE_CARSERVICE_BOTTOM);
    private final AudioAttributes mAttributeCarExternal =
            CarAudioAttributesUtil.getAudioAttributesForCarUsage(
                    CarAudioAttributesUtil.CAR_AUDIO_USAGE_CARSERVICE_CAR_PROXY);

    @GuardedBy("mLock")
    private final BinderInterfaceContainer<ICarAudioCallback> mAudioParamListeners =
        new BinderInterfaceContainer<>();
    @GuardedBy("mLock")
    private HashSet<String> mAudioParamKeys;

    public CarAudioService(Context context, AudioHalService audioHal,
            CarInputService inputService, CanBusErrorNotifier errorNotifier) {
        mAudioHal = audioHal;
        mContext = context;
        mFocusHandlerThread = new HandlerThread(CarLog.TAG_AUDIO);
        mSystemFocusListener = new SystemFocusListener();
        mFocusHandlerThread.start();
        mFocusHandler = new CarAudioFocusChangeHandler(mFocusHandlerThread.getLooper());
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mCanBusErrorNotifier =  errorNotifier;
        Resources res = context.getResources();
        mFocusResponseWaitTimeoutMs = (long) res.getInteger(R.integer.audioFocusWaitTimeoutMs);
        mNumConsecutiveHalFailuresForCanError =
                (int) res.getInteger(R.integer.consecutiveHalFailures);
        mUseDynamicRouting = res.getBoolean(R.bool.audioUseDynamicRouting);
        mVolumeService = new CarVolumeService(mContext, this, mAudioHal, inputService);
    }

    @Override
    public AudioAttributes getAudioAttributesForCarUsage(int carUsage) {
        return CarAudioAttributesUtil.getAudioAttributesForCarUsage(carUsage);
    }

    @Override
    public void init() {
        AudioPolicy.Builder builder = new AudioPolicy.Builder(mContext);
        builder.setLooper(Looper.getMainLooper());
        boolean isFocusSupported = mAudioHal.isFocusSupported();
        if (isFocusSupported) {
            builder.setAudioPolicyFocusListener(mSystemFocusListener);
            FocusState currentState = FocusState.create(mAudioHal.getCurrentFocusState());
            int r = mAudioManager.requestAudioFocus(mBottomAudioFocusListener, mAttributeBottom,
                    AudioManager.AUDIOFOCUS_GAIN, AudioManager.AUDIOFOCUS_FLAG_DELAY_OK);
            synchronized (mLock) {
                mCurrentFocusState = currentState;
                mCurrentAudioContexts = 0;
            }
        }
        int audioHwVariant = mAudioHal.getHwVariant();
        AudioRoutingPolicy audioRoutingPolicy = AudioRoutingPolicy.create(mContext, audioHwVariant);
        if (mUseDynamicRouting) {
            setupDynamicRouting(audioRoutingPolicy, builder);
        }
        AudioPolicy audioPolicy = null;
        if (isFocusSupported || mUseDynamicRouting) {
            audioPolicy = builder.build();
        }
        mAudioHal.setFocusListener(this);
        mAudioHal.setAudioRoutingPolicy(audioRoutingPolicy);
        mAudioHal.setOnParameterChangeListener(this);
        // get call outside lock as it can take time
        HashSet<String> externalRadioRoutingTypes = new HashSet<>();
        HashSet<String> externalNonRadioRoutingTypes = new HashSet<>();
        Map<String, AudioHalService.ExtRoutingSourceInfo> externalRoutingTypes =
                mAudioHal.getExternalAudioRoutingTypes();
        if (externalRoutingTypes != null) {
            for (String routingType : externalRoutingTypes.keySet()) {
                if (routingType.startsWith(RADIO_ROUTING_SOURCE_PREFIX)) {
                    externalRadioRoutingTypes.add(routingType);
                } else {
                    externalNonRadioRoutingTypes.add(routingType);
                }
            }
        }
        // select default radio routing. AM_FM -> AM_FM_HD -> whatever with AM or FM -> first one
        String defaultRadioRouting = null;
        if (externalRadioRoutingTypes.contains(CarAudioManager.CAR_RADIO_TYPE_AM_FM)) {
            defaultRadioRouting = CarAudioManager.CAR_RADIO_TYPE_AM_FM;
        } else if (externalRadioRoutingTypes.contains(CarAudioManager.CAR_RADIO_TYPE_AM_FM_HD)) {
            defaultRadioRouting = CarAudioManager.CAR_RADIO_TYPE_AM_FM_HD;
        } else {
            for (String radioType : externalRadioRoutingTypes) {
                // set to 1st one
                if (defaultRadioRouting == null) {
                    defaultRadioRouting = radioType;
                }
                if (radioType.contains("AM") || radioType.contains("FM")) {
                    defaultRadioRouting = radioType;
                    break;
                }
            }
        }
        if (defaultRadioRouting == null) { // no radio type defined. fall back to AM_FM
            defaultRadioRouting = CarAudioManager.CAR_RADIO_TYPE_AM_FM;
        }
        synchronized (mLock) {
            if (audioPolicy != null) {
                mAudioPolicy = audioPolicy;
            }
            mRadioPhysicalStream = audioRoutingPolicy.getPhysicalStreamForLogicalStream(
                    CarAudioManager.CAR_AUDIO_USAGE_RADIO);
            mSystemSoundPhysicalStream = audioRoutingPolicy.getPhysicalStreamForLogicalStream(
                    CarAudioManager.CAR_AUDIO_USAGE_SYSTEM_SOUND);
            mSystemSoundPhysicalStreamActive = false;
            mAudioRoutingPolicy = audioRoutingPolicy;
            mIsRadioExternal = mAudioHal.isRadioExternal();
            if (externalRoutingTypes != null) {
                mExternalRoutingHintSupported = true;
                mExternalRoutingTypes = externalRoutingTypes;
            } else {
                mExternalRoutingHintSupported = false;
                mExternalRoutingTypes = new HashMap<>();
            }
            mExternalRadioRoutingTypes = externalRadioRoutingTypes;
            mExternalNonRadioRoutingTypes = externalNonRadioRoutingTypes;
            mDefaultRadioRoutingType = defaultRadioRouting;
            Arrays.fill(mExternalRoutings, 0);
            populateParameterKeysLocked();
        }
        mVolumeService.init();

        // Register audio policy only after this class is fully initialized.
        int r = mAudioManager.registerAudioPolicy(audioPolicy);
        if (r != 0) {
            throw new RuntimeException("registerAudioPolicy failed " + r);
        }
    }

    private void setupDynamicRouting(AudioRoutingPolicy audioRoutingPolicy,
            AudioPolicy.Builder audioPolicyBuilder) {
        AudioDeviceInfo[] deviceInfos = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        if (deviceInfos.length == 0) {
            Log.e(CarLog.TAG_AUDIO, "setupDynamicRouting, no output device available, ignore");
            return;
        }
        int numPhysicalStreams = audioRoutingPolicy.getPhysicalStreamsCount();
        AudioDeviceInfo[] devicesToRoute = new AudioDeviceInfo[numPhysicalStreams];
        for (AudioDeviceInfo info : deviceInfos) {
            if (DBG_DYNAMIC_AUDIO_ROUTING) {
                Log.v(CarLog.TAG_AUDIO, String.format(
                        "output device=%s id=%d name=%s addr=%s type=%s",
                        info.toString(), info.getId(), info.getProductName(), info.getAddress(),
                        info.getType()));
            }
            if (info.getType() == AudioDeviceInfo.TYPE_BUS) {
                int addressNumeric = parseDeviceAddress(info.getAddress());
                if (addressNumeric >= 0 && addressNumeric < numPhysicalStreams) {
                    devicesToRoute[addressNumeric] = info;
                    Log.i(CarLog.TAG_AUDIO, String.format(
                            "valid bus found, devie=%s id=%d name=%s addr=%s",
                            info.toString(), info.getId(), info.getProductName(), info.getAddress())
                            );
                }
            }
        }
        for (int i = 0; i < numPhysicalStreams; i++) {
            AudioDeviceInfo info = devicesToRoute[i];
            if (info == null) {
                Log.e(CarLog.TAG_AUDIO, "setupDynamicRouting, cannot find device for address " + i);
                return;
            }
            int sampleRate = getMaxSampleRate(info);
            int channels = getMaxChannles(info);
            AudioFormat mixFormat = new AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(channels)
                .build();
            Log.i(CarLog.TAG_AUDIO, String.format(
                    "Physical stream %d, sampleRate:%d, channles:0x%s", i, sampleRate,
                    Integer.toHexString(channels)));
            int[] logicalStreams = audioRoutingPolicy.getLogicalStreamsForPhysicalStream(i);
            AudioMixingRule.Builder mixingRuleBuilder = new AudioMixingRule.Builder();
            for (int logicalStream : logicalStreams) {
                mixingRuleBuilder.addRule(
                        CarAudioAttributesUtil.getAudioAttributesForCarUsage(logicalStream),
                        AudioMixingRule.RULE_MATCH_ATTRIBUTE_USAGE);
            }
            AudioMix audioMix = new AudioMix.Builder(mixingRuleBuilder.build())
                .setFormat(mixFormat)
                .setDevice(info)
                .setRouteFlags(AudioMix.ROUTE_FLAG_RENDER)
                .build();
            audioPolicyBuilder.addMix(audioMix);
        }
    }

    /**
     * Parse device address. Expected format is BUS%d_%s, address, usage hint
     * @return valid address (from 0 to positive) or -1 for invalid address.
     */
    private int parseDeviceAddress(String address) {
        String[] words = address.split("_");
        int addressParsed = -1;
        if (words[0].startsWith("BUS")) {
            try {
                addressParsed = Integer.parseInt(words[0].substring(3));
            } catch (NumberFormatException e) {
                //ignore
            }
        }
        if (addressParsed < 0) {
            return -1;
        }
        return addressParsed;
    }

    private int getMaxSampleRate(AudioDeviceInfo info) {
        int[] sampleRates = info.getSampleRates();
        if (sampleRates == null || sampleRates.length == 0) {
            return 48000;
        }
        int sampleRate = sampleRates[0];
        for (int i = 1; i < sampleRates.length; i++) {
            if (sampleRates[i] > sampleRate) {
                sampleRate = sampleRates[i];
            }
        }
        return sampleRate;
    }

    private int getMaxChannles(AudioDeviceInfo info) {
        int[] channelMasks = info.getChannelMasks();
        if (channelMasks == null) {
            return AudioFormat.CHANNEL_OUT_STEREO;
        }
        int channels = AudioFormat.CHANNEL_OUT_MONO;
        int numChannels = 1;
        for (int i = 0; i < channelMasks.length; i++) {
            int currentNumChannles = VehicleZoneUtil.getNumberOfZones(channelMasks[i]);
            if (currentNumChannles > numChannels) {
                numChannels = currentNumChannles;
                channels = channelMasks[i];
            }
        }
        return channels;
    }

    @Override
    public void release() {
        mFocusHandler.cancelAll();
        mAudioManager.abandonAudioFocus(mBottomAudioFocusListener);
        mAudioManager.abandonAudioFocus(mCarProxyAudioFocusListener);
        AudioPolicy audioPolicy;
        synchronized (mLock) {
            mAudioParamKeys = null;
            mCurrentFocusState = FocusState.STATE_LOSS;
            mLastFocusRequestToCar = null;
            mPrimaryFocusInfo = null;
            mPendingFocusChanges.clear();
            mRadioOrExtSourceActive = false;
            if (mCarAudioContextChangeHandler != null) {
                mCarAudioContextChangeHandler.cancelAll();
                mCarAudioContextChangeHandler = null;
            }
            mAudioContextChangeListener = null;
            mCurrentPrimaryAudioContext = 0;
            audioPolicy = mAudioPolicy;
            mAudioPolicy = null;
            mExternalRoutingTypes.clear();
            mExternalRadioRoutingTypes.clear();
            mExternalNonRadioRoutingTypes.clear();
        }
        if (audioPolicy != null) {
            mAudioManager.unregisterAudioPolicyAsync(audioPolicy);
        }
        mVolumeService.release();
    }

    public synchronized void setAudioContextChangeListener(Looper looper,
            AudioContextChangeListener listener) {
        if (looper == null || listener == null) {
            throw new IllegalArgumentException("looper or listener null");
        }
        if (mCarAudioContextChangeHandler != null) {
            mCarAudioContextChangeHandler.cancelAll();
        }
        mCarAudioContextChangeHandler = new CarAudioContextChangeHandler(looper);
        mAudioContextChangeListener = listener;
    }

    @Override
    public void dump(PrintWriter writer) {
        synchronized (mLock) {
            writer.println("*CarAudioService*");
            writer.println(" mCurrentFocusState:" + mCurrentFocusState +
                    " mLastFocusRequestToCar:" + mLastFocusRequestToCar);
            writer.println(" mCurrentAudioContexts:0x" +
                    Integer.toHexString(mCurrentAudioContexts));
            writer.println(" mRadioOrExtSourceActive:" +
                    mRadioOrExtSourceActive);
            writer.println(" mCurrentPrimaryAudioContext:" + mCurrentPrimaryAudioContext +
                    " mCurrentPrimaryPhysicalStream:" + mCurrentPrimaryPhysicalStream);
            writer.println(" mIsRadioExternal:" + mIsRadioExternal);
            writer.println(" mNumConsecutiveHalFailures:" + mNumConsecutiveHalFailures);
            writer.println(" media muted:" + mMediaMuteAudioFocusListener.isMuted());
            writer.println(" mAudioPolicy:" + mAudioPolicy);
            mAudioRoutingPolicy.dump(writer);
            writer.println(" mExternalRoutingHintSupported:" + mExternalRoutingHintSupported);
            if (mExternalRoutingHintSupported) {
                writer.println(" mDefaultRadioRoutingType:" + mDefaultRadioRoutingType);
                writer.println(" Routing Types:");
                for (Entry<String, AudioHalService.ExtRoutingSourceInfo> entry :
                    mExternalRoutingTypes.entrySet()) {
                    writer.println("  type:" + entry.getKey() + " info:" + entry.getValue());
                }
            }
            if (mAudioParamKeys != null) {
                writer.println("** Audio parameter keys**");
                for (String key : mAudioParamKeys) {
                    writer.println("  " + key);
                }
            }
        }
        writer.println("** Dump CarVolumeService**");
        mVolumeService.dump(writer);
    }

    @Override
    public void onFocusChange(int focusState, int streams, int externalFocus) {
        synchronized (mLock) {
            mFocusReceived = FocusState.create(focusState, streams, externalFocus);
            // wake up thread waiting for focus response.
            mLock.notifyAll();
        }
        mFocusHandler.handleFocusChange();
    }

    @Override
    public void onStreamStatusChange(int streamNumber, boolean streamActive) {
        if (DBG) {
            Log.d(TAG_FOCUS, "onStreamStatusChange stream:" + streamNumber + ", active:" +
                    streamActive);
        }
        mFocusHandler.handleStreamStateChange(streamNumber, streamActive);
    }

    @Override
    public void setStreamVolume(int streamType, int index, int flags) {
        enforceAudioVolumePermission();
        mVolumeService.setStreamVolume(streamType, index, flags);
    }

    @Override
    public void setVolumeController(IVolumeController controller) {
        enforceAudioVolumePermission();
        mVolumeService.setVolumeController(controller);
    }

    @Override
    public int getStreamMaxVolume(int streamType) {
        enforceAudioVolumePermission();
        return mVolumeService.getStreamMaxVolume(streamType);
    }

    @Override
    public int getStreamMinVolume(int streamType) {
        enforceAudioVolumePermission();
        return mVolumeService.getStreamMinVolume(streamType);
    }

    @Override
    public int getStreamVolume(int streamType) {
        enforceAudioVolumePermission();
        return mVolumeService.getStreamVolume(streamType);
    }

    @Override
    public boolean isMediaMuted() {
        return mMediaMuteAudioFocusListener.isMuted();
    }

    @Override
    public boolean setMediaMute(boolean mute) {
        enforceAudioVolumePermission();
        boolean currentState = isMediaMuted();
        if (mute == currentState) {
            return currentState;
        }
        if (mute) {
            return mMediaMuteAudioFocusListener.mute();
        } else {
            return mMediaMuteAudioFocusListener.unMute();
        }
    }

    @Override
    public AudioAttributes getAudioAttributesForRadio(String radioType) {
        synchronized (mLock) {
            if (!mExternalRadioRoutingTypes.contains(radioType)) { // type not exist
                throw new IllegalArgumentException("Specified radio type is not available:" +
                        radioType);
            }
        }
      return CarAudioAttributesUtil.getCarRadioAttributes(radioType);
    }

    @Override
    public AudioAttributes getAudioAttributesForExternalSource(String externalSourceType) {
        synchronized (mLock) {
            if (!mExternalNonRadioRoutingTypes.contains(externalSourceType)) { // type not exist
                throw new IllegalArgumentException("Specified ext source type is not available:" +
                        externalSourceType);
            }
        }
        return CarAudioAttributesUtil.getCarExtSourceAttributes(externalSourceType);
    }

    @Override
    public String[] getSupportedExternalSourceTypes() {
        synchronized (mLock) {
            return mExternalNonRadioRoutingTypes.toArray(
                    new String[mExternalNonRadioRoutingTypes.size()]);
        }
    }

    @Override
    public String[] getSupportedRadioTypes() {
        synchronized (mLock) {
            return mExternalRadioRoutingTypes.toArray(
                    new String[mExternalRadioRoutingTypes.size()]);
        }
    }

    @Override
    public void onParameterChange(String parameters) {
        for (BinderInterfaceContainer.BinderInterface<ICarAudioCallback> client :
            mAudioParamListeners.getInterfaces()) {
            try {
                client.binderInterface.onParameterChange(parameters);
            } catch (RemoteException e) {
                // ignore. death handler will handle it.
            }
        }
    }

    @Override
    public String[] getParameterKeys() {
        enforceAudioSettingsPermission();
        return mAudioHal.getAudioParameterKeys();
    }

    @Override
    public void setParameters(String parameters) {
        enforceAudioSettingsPermission();
        if (parameters == null) {
            throw new IllegalArgumentException("null parameters");
        }
        String[] keyValues = parameters.split(";");
        synchronized (mLock) {
            for (String keyValue : keyValues) {
                String[] keyValuePair = keyValue.split("=");
                if (keyValuePair.length != 2) {
                    throw new IllegalArgumentException("Wrong audio parameter:" + parameters);
                }
                assertPamameterKeysLocked(keyValuePair[0]);
            }
        }
        mAudioHal.setAudioParameters(parameters);
    }

    @Override
    public String getParameters(String keys) {
        enforceAudioSettingsPermission();
        if (keys == null) {
            throw new IllegalArgumentException("null keys");
        }
        synchronized (mLock) {
            for (String key : keys.split(";")) {
                assertPamameterKeysLocked(key);
            }
        }
        return mAudioHal.getAudioParameters(keys);
    }

    @Override
    public void registerOnParameterChangeListener(ICarAudioCallback callback) {
        enforceAudioSettingsPermission();
        if (callback == null) {
            throw new IllegalArgumentException("callback null");
        }
        mAudioParamListeners.addBinder(callback);
    }

    @Override
    public void unregisterOnParameterChangeListener(ICarAudioCallback callback) {
        if (callback == null) {
            return;
        }
        mAudioParamListeners.removeBinder(callback);
    }

    private void populateParameterKeysLocked() {
        String[] keys = mAudioHal.getAudioParameterKeys();
        mAudioParamKeys = new HashSet<>();
        if (keys == null) { // not supported
            return;
        }
        for (String key : keys) {
            mAudioParamKeys.add(key);
        }
    }

    private void assertPamameterKeysLocked(String key) {
        if (!mAudioParamKeys.contains(key)) {
            throw new IllegalArgumentException("Audio parameter not available:" + key);
        }
    }

    private void enforceAudioSettingsPermission() {
        if (mContext.checkCallingOrSelfPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "requires permission " + Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        }
    }

    /**
     * API for system to control mute with lock.
     * @param mute
     * @return the current mute state
     */
    public void muteMediaWithLock(boolean lock) {
        mMediaMuteAudioFocusListener.mute(lock);
    }

    public void unMuteMedia() {
        // unmute always done with lock
        mMediaMuteAudioFocusListener.unMute(true);
    }

    public AudioRoutingPolicy getAudioRoutingPolicy() {
        return mAudioRoutingPolicy;
    }

    private void enforceAudioVolumePermission() {
        if (mContext.checkCallingOrSelfPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "requires permission " + Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        }
    }

    private void doHandleCarFocusChange() {
        int newFocusState = AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_INVALID;
        AudioFocusInfo topInfo;
        boolean systemSoundActive = false;
        synchronized (mLock) {
            if (mFocusReceived == null) {
                // already handled
                return;
            }
            if (mFocusReceived.equals(mCurrentFocusState)) {
                // no change
                mFocusReceived = null;
                return;
            }
            if (DBG) {
                Log.d(TAG_FOCUS, "focus change from car:" + mFocusReceived);
            }
            systemSoundActive = mSystemSoundPhysicalStreamActive;
            topInfo = mPrimaryFocusInfo;
            if (!mFocusReceived.equals(mCurrentFocusState.focusState)) {
                newFocusState = mFocusReceived.focusState;
            }
            mCurrentFocusState = mFocusReceived;
            mFocusReceived = null;
            if (mLastFocusRequestToCar != null &&
                    (mLastFocusRequestToCar.focusRequest ==
                        AudioHalService.VEHICLE_AUDIO_FOCUS_REQUEST_GAIN ||
                    mLastFocusRequestToCar.focusRequest ==
                        AudioHalService.VEHICLE_AUDIO_FOCUS_REQUEST_GAIN_TRANSIENT ||
                    mLastFocusRequestToCar.focusRequest ==
                        AudioHalService.VEHICLE_AUDIO_FOCUS_REQUEST_GAIN_TRANSIENT_MAY_DUCK) &&
                    (mCurrentFocusState.streams & mLastFocusRequestToCar.streams) !=
                    mLastFocusRequestToCar.streams) {
                Log.w(TAG_FOCUS, "streams mismatch, requested:0x" + Integer.toHexString(
                        mLastFocusRequestToCar.streams) + " got:0x" +
                        Integer.toHexString(mCurrentFocusState.streams));
                // treat it as focus loss as requested streams are not there.
                newFocusState = AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_LOSS;
            }
            mLastFocusRequestToCar = null;
            if (mRadioOrExtSourceActive &&
                    (mCurrentFocusState.externalFocus &
                    AudioHalService.VEHICLE_AUDIO_EXT_FOCUS_CAR_PLAY_ONLY_FLAG) == 0) {
                // radio flag dropped
                newFocusState = AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_LOSS;
                mRadioOrExtSourceActive = false;
            }
            if (newFocusState == AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_LOSS ||
                    newFocusState == AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_LOSS_TRANSIENT ||
                    newFocusState ==
                        AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_LOSS_TRANSIENT_EXLCUSIVE) {
                // clear second one as there can be no such item in these LOSS.
                mSecondaryFocusInfo = null;
            }
        }
        switch (newFocusState) {
            case AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_GAIN:
                doHandleFocusGainFromCar(mCurrentFocusState, topInfo, systemSoundActive);
                break;
            case AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_GAIN_TRANSIENT:
                doHandleFocusGainTransientFromCar(mCurrentFocusState, topInfo, systemSoundActive);
                break;
            case AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_LOSS:
                doHandleFocusLossFromCar(mCurrentFocusState, topInfo);
                break;
            case AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_LOSS_TRANSIENT:
                doHandleFocusLossTransientFromCar(mCurrentFocusState);
                break;
            case AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_LOSS_TRANSIENT_CAN_DUCK:
                doHandleFocusLossTransientCanDuckFromCar(mCurrentFocusState);
                break;
            case AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_LOSS_TRANSIENT_EXLCUSIVE:
                doHandleFocusLossTransientExclusiveFromCar(mCurrentFocusState);
                break;
        }
    }

    private void doHandleFocusGainFromCar(FocusState currentState, AudioFocusInfo topInfo,
            boolean systemSoundActive) {
        if (isFocusFromCarServiceBottom(topInfo)) {
            if (systemSoundActive) { // focus requested for system sound
                if (DBG) {
                    Log.d(TAG_FOCUS, "focus gain due to system sound");
                }
                return;
            }
            Log.w(TAG_FOCUS, "focus gain from car:" + currentState +
                    " while bottom listener is top");
            mFocusHandler.handleFocusReleaseRequest();
        } else {
            mAudioManager.abandonAudioFocus(mCarProxyAudioFocusListener);
        }
    }

    private void doHandleFocusGainTransientFromCar(FocusState currentState,
            AudioFocusInfo topInfo, boolean systemSoundActive) {
        if ((currentState.externalFocus &
                (AudioHalService.VEHICLE_AUDIO_EXT_FOCUS_CAR_PERMANENT_FLAG |
                        AudioHalService.VEHICLE_AUDIO_EXT_FOCUS_CAR_TRANSIENT_FLAG)) == 0) {
            mAudioManager.abandonAudioFocus(mCarProxyAudioFocusListener);
        } else {
            if (isFocusFromCarServiceBottom(topInfo) || isFocusFromCarProxy(topInfo)) {
                if (systemSoundActive) { // focus requested for system sound
                    if (DBG) {
                        Log.d(TAG_FOCUS, "focus gain tr due to system sound");
                    }
                    return;
                }
                Log.w(TAG_FOCUS, "focus gain transient from car:" + currentState +
                        " while bottom listener or car proxy is top");
                mFocusHandler.handleFocusReleaseRequest();
            }
        }
    }

    private void doHandleFocusLossFromCar(FocusState currentState, AudioFocusInfo topInfo) {
        if (DBG) {
            Log.d(TAG_FOCUS, "doHandleFocusLossFromCar current:" + currentState +
                    " top:" + dumpAudioFocusInfo(topInfo));
        }
        if (isFocusFromCarProxy(topInfo)) {
            // already car proxy is top. Nothing to do.
            return;
        }
        boolean shouldRequestProxyFocus = false;
        if ((currentState.externalFocus &
                AudioHalService.VEHICLE_AUDIO_EXT_FOCUS_CAR_PERMANENT_FLAG) != 0) {
            // If car is playing something persistent, the car proxy should have focus
            shouldRequestProxyFocus = true;
        }
        if (!isFocusFromCarServiceBottom(topInfo)) {
            // If a car source was being ducked, it should get the primary focus back
            shouldRequestProxyFocus = true;
        }
        if (shouldRequestProxyFocus) {
            requestCarProxyFocus(AudioManager.AUDIOFOCUS_GAIN, 0);
        }
    }

    private void doHandleFocusLossTransientFromCar(FocusState currentState) {
        requestCarProxyFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, 0);
    }

    private void doHandleFocusLossTransientCanDuckFromCar(FocusState currentState) {
        requestCarProxyFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, 0);
    }

    private void doHandleFocusLossTransientExclusiveFromCar(FocusState currentState) {
        requestCarProxyFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                AudioManager.AUDIOFOCUS_FLAG_LOCK);
    }

    private void requestCarProxyFocus(int androidFocus, int flags) {
        mAudioManager.requestAudioFocus(mCarProxyAudioFocusListener, mAttributeCarExternal,
                androidFocus, flags, mAudioPolicy);
    }

    private void doHandleStreamStatusChange(int streamNumber, boolean streamActive) {
        synchronized (mLock) {
            if (streamNumber != mSystemSoundPhysicalStream) {
                return;
            }
            mSystemSoundPhysicalStreamActive = streamActive;
        }
        doHandleAndroidFocusChange(true /*triggeredByStreamChange*/);
    }

    private boolean checkFocusUsage(AudioFocusInfo info, int expectedUsage) {
        if (info == null) {
            return false;
        }

        AudioAttributes attributes = info.getAttributes();
        if (attributes == null) {
            return false;
        }

        int actualUsage = CarAudioAttributesUtil.getCarUsageFromAudioAttributes(attributes);
        if (actualUsage == expectedUsage) {
            return info.getPackageName().equals(mContext.getOpPackageName());
        }
        return false;
    }

    private boolean isFocusFromCarServiceBottom(AudioFocusInfo info) {
        return checkFocusUsage(info, CarAudioAttributesUtil.CAR_AUDIO_USAGE_CARSERVICE_BOTTOM);
    }

    private boolean isFocusFromCarProxy(AudioFocusInfo info) {
        return checkFocusUsage(info, CarAudioAttributesUtil.CAR_AUDIO_USAGE_CARSERVICE_CAR_PROXY);
    }

    private boolean isFocusFromExternalRadioOrExternalSource(AudioFocusInfo info) {
        if (info == null) {
            return false;
        }

        AudioAttributes attributes = info.getAttributes();
        if (attributes == null) {
            return false;
        }

        int focusUsage = CarAudioAttributesUtil.getCarUsageFromAudioAttributes(attributes);
        switch (focusUsage) {
            case CarAudioManager.CAR_AUDIO_USAGE_RADIO:
                return mIsRadioExternal;
            case CarAudioManager.CAR_AUDIO_USAGE_EXTERNAL_AUDIO_SOURCE:
                return true;
            default:
                return false;
        }
    }

    /**
     * Re-evaluate current focus state and send focus request to car if new focus was requested.
     * @return true if focus change was requested to car.
     */
    private boolean reevaluateCarAudioFocusAndSendFocusLocked() {
        if (mPrimaryFocusInfo == null) {
            if (mSystemSoundPhysicalStreamActive) {
                return requestFocusForSystemSoundOnlyCaseLocked();
            } else {
                requestFocusReleaseForSystemSoundLocked();
                return false;
            }
        }
        if (mPrimaryFocusInfo.getLossReceived() != 0) {
            // top one got loss. This should not happen.
            Log.e(TAG_FOCUS, "Top focus holder got loss " +  dumpAudioFocusInfo(mPrimaryFocusInfo));
            return false;
        }
        if (isFocusFromCarServiceBottom(mPrimaryFocusInfo) || isFocusFromCarProxy(mPrimaryFocusInfo)) {
            // allow system sound only when car is not holding focus.
            if (mSystemSoundPhysicalStreamActive && isFocusFromCarServiceBottom(mPrimaryFocusInfo)) {
                return requestFocusForSystemSoundOnlyCaseLocked();
            }
            switch (mCurrentFocusState.focusState) {
                case AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_GAIN:
                case AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_GAIN_TRANSIENT:
                    //should not have focus. So enqueue release
                    mFocusHandler.handleFocusReleaseRequest();
                    break;
                case AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_LOSS:
                    doHandleFocusLossFromCar(mCurrentFocusState, mPrimaryFocusInfo);
                    break;
                case AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_LOSS_TRANSIENT:
                    doHandleFocusLossTransientFromCar(mCurrentFocusState);
                    break;
                case AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_LOSS_TRANSIENT_CAN_DUCK:
                    doHandleFocusLossTransientCanDuckFromCar(mCurrentFocusState);
                    break;
                case AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_LOSS_TRANSIENT_EXLCUSIVE:
                    doHandleFocusLossTransientExclusiveFromCar(mCurrentFocusState);
                    break;
            }
            mRadioOrExtSourceActive = false;
            return false;
        }
        mFocusHandler.cancelFocusReleaseRequest();
        AudioAttributes attrib = mPrimaryFocusInfo.getAttributes();
        int logicalStreamTypeForTop = CarAudioAttributesUtil.getCarUsageFromAudioAttributes(attrib);
        int physicalStreamTypeForTop = mAudioRoutingPolicy.getPhysicalStreamForLogicalStream(
                (logicalStreamTypeForTop < CarAudioAttributesUtil.CAR_AUDIO_USAGE_CARSERVICE_BOTTOM)
                ? logicalStreamTypeForTop : CarAudioManager.CAR_AUDIO_USAGE_MUSIC);

        boolean muteMedia = false;
        String primaryExtSource = CarAudioAttributesUtil.getExtRouting(attrib);
        // update primary context and notify if necessary
        int primaryContext = AudioHalService.logicalStreamWithExtTypeToHalContextType(
                logicalStreamTypeForTop, primaryExtSource);
        if (logicalStreamTypeForTop ==
                CarAudioAttributesUtil.CAR_AUDIO_USAGE_CARSERVICE_MEDIA_MUTE) {
                muteMedia = true;
        }
        // other apps having focus
        int focusToRequest = AudioHalService.VEHICLE_AUDIO_FOCUS_REQUEST_RELEASE;
        int extFocus = AudioHalService.VEHICLE_AUDIO_EXT_FOCUS_NONE_FLAG;
        int streamsToRequest = 0x1 << physicalStreamTypeForTop;
        boolean primaryIsExternal = isFocusFromExternalRadioOrExternalSource(mPrimaryFocusInfo);
        if (primaryIsExternal) {
            streamsToRequest = 0;
            mRadioOrExtSourceActive = true;
            if (fixExtSourceAndContext(
                    mExtSourceInfoScratch.set(primaryExtSource, primaryContext))) {
                primaryExtSource = mExtSourceInfoScratch.source;
                primaryContext = mExtSourceInfoScratch.context;
            }
        } else {
            mRadioOrExtSourceActive = false;
            primaryExtSource = null;
        }
        // save the current context now but it is sent to context change listener after focus
        // response from car
        if (mCurrentPrimaryAudioContext != primaryContext) {
            mCurrentPrimaryAudioContext = primaryContext;
             mCurrentPrimaryPhysicalStream = physicalStreamTypeForTop;
        }

        boolean secondaryIsExternal = false;
        int secondaryContext = 0;
        String secondaryExtSource = null;
        switch (mPrimaryFocusInfo.getGainRequest()) {
            case AudioManager.AUDIOFOCUS_GAIN:
                focusToRequest = AudioHalService.VEHICLE_AUDIO_FOCUS_REQUEST_GAIN;
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                focusToRequest = AudioHalService.VEHICLE_AUDIO_FOCUS_REQUEST_GAIN_TRANSIENT;
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                focusToRequest =
                    AudioHalService.VEHICLE_AUDIO_FOCUS_REQUEST_GAIN_TRANSIENT_MAY_DUCK;
                if (mSecondaryFocusInfo == null) {
                    break;
                }
                AudioAttributes secondAttrib = mSecondaryFocusInfo.getAttributes();
                if (secondAttrib == null) {
                    break;
                }
                int logicalStreamTypeForSecond =
                        CarAudioAttributesUtil.getCarUsageFromAudioAttributes(secondAttrib);
                if (logicalStreamTypeForSecond ==
                        CarAudioAttributesUtil.CAR_AUDIO_USAGE_CARSERVICE_MEDIA_MUTE) {
                    muteMedia = true;
                    break;
                }
                secondaryIsExternal = isFocusFromExternalRadioOrExternalSource(mSecondaryFocusInfo);
                if (secondaryIsExternal) {
                    secondaryExtSource = CarAudioAttributesUtil.getExtRouting(secondAttrib);
                    secondaryContext = AudioHalService.logicalStreamWithExtTypeToHalContextType(
                            logicalStreamTypeForSecond, secondaryExtSource);
                    if (fixExtSourceAndContext(
                            mExtSourceInfoScratch.set(secondaryExtSource, secondaryContext))) {
                        secondaryExtSource = mExtSourceInfoScratch.source;
                        secondaryContext = mExtSourceInfoScratch.context;
                    }
                    int secondaryExtPhysicalStreamFlag =
                            getPhysicalStreamFlagForExtSourceLocked(secondaryExtSource);
                    if ((secondaryExtPhysicalStreamFlag & streamsToRequest) != 0) {
                        // secondary stream is the same as primary. cannot keep secondary
                        secondaryIsExternal = false;
                        secondaryContext = 0;
                        secondaryExtSource = null;
                        break;
                    }
                    mRadioOrExtSourceActive = true;
                } else {
                    secondaryContext = AudioHalService.logicalStreamWithExtTypeToHalContextType(
                            logicalStreamTypeForSecond, null);
                }
                switch (mCurrentFocusState.focusState) {
                    case AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_GAIN:
                        streamsToRequest |= mCurrentFocusState.streams;
                        focusToRequest = AudioHalService.VEHICLE_AUDIO_FOCUS_REQUEST_GAIN;
                        break;
                    case AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_GAIN_TRANSIENT:
                        streamsToRequest |= mCurrentFocusState.streams;
                        focusToRequest = AudioHalService.VEHICLE_AUDIO_FOCUS_REQUEST_GAIN_TRANSIENT;
                        break;
                    case AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_LOSS:
                    case AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_LOSS_TRANSIENT:
                    case AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_LOSS_TRANSIENT_CAN_DUCK:
                        break;
                    case AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_LOSS_TRANSIENT_EXLCUSIVE:
                        doHandleFocusLossTransientExclusiveFromCar(mCurrentFocusState);
                        return false;
                }
                break;
            default:
                streamsToRequest = 0;
                break;
        }
        int audioContexts = primaryContext | secondaryContext;
        if (muteMedia) {
            boolean addMute = true;
            if (primaryIsExternal) {
                if ((getPhysicalStreamFlagForExtSourceLocked(primaryExtSource) &
                        (0x1 << mRadioPhysicalStream)) != 0) {
                    // cannot mute as primary is media
                    addMute = false;
                }
            } else if (secondaryIsExternal) {
                if ((getPhysicalStreamFlagForExtSourceLocked(secondaryExtSource) &
                        (0x1 << mRadioPhysicalStream)) != 0) {
                    mRadioOrExtSourceActive = false;
                }
            } else {
                mRadioOrExtSourceActive = false;
            }
            if (addMute) {
                audioContexts &= ~(AudioHalService.AUDIO_CONTEXT_RADIO_FLAG |
                        AudioHalService.AUDIO_CONTEXT_MUSIC_FLAG |
                        AudioHalService.AUDIO_CONTEXT_CD_ROM_FLAG |
                        AudioHalService.AUDIO_CONTEXT_AUX_AUDIO_FLAG);
                extFocus = AudioHalService.VEHICLE_AUDIO_EXT_FOCUS_CAR_MUTE_MEDIA_FLAG;
                streamsToRequest &= ~(0x1 << mRadioPhysicalStream);
            }
        } else if (mRadioOrExtSourceActive) {
            boolean shouldDropSecondaryContext = false;
            if (primaryIsExternal) {
                int primaryExtPhysicalStreamFlag =
                        getPhysicalStreamFlagForExtSourceLocked(primaryExtSource);
                if (secondaryIsExternal) {
                    int secondaryPhysicalStreamFlag =
                            getPhysicalStreamFlagForExtSourceLocked(secondaryExtSource);
                    if (primaryExtPhysicalStreamFlag == secondaryPhysicalStreamFlag) {
                        // overlap, drop secondary
                        shouldDropSecondaryContext = true;
                        secondaryExtSource = null;
                    }
                    streamsToRequest = 0;
                } else { // primary only
                    if (streamsToRequest == primaryExtPhysicalStreamFlag) {
                        // cannot keep secondary
                        shouldDropSecondaryContext = true;
                    }
                    streamsToRequest &= ~primaryExtPhysicalStreamFlag;
                }
            }
            extFocus = AudioHalService.VEHICLE_AUDIO_EXT_FOCUS_CAR_PLAY_ONLY_FLAG;
            if (shouldDropSecondaryContext) {
                audioContexts &= ~secondaryContext;
                secondaryContext = 0;
            }
        } else if (streamsToRequest == 0) {
            if (mSystemSoundPhysicalStreamActive) {
                return requestFocusForSystemSoundOnlyCaseLocked();
            } else {
                mCurrentAudioContexts = 0;
                mFocusHandler.handleFocusReleaseRequest();
                return false;
            }
        }
        if (mSystemSoundPhysicalStreamActive) {
            boolean addSystemStream = true;
            if (primaryIsExternal && getPhysicalStreamNumberForExtSourceLocked(primaryExtSource) ==
                    mSystemSoundPhysicalStream) {
                addSystemStream = false;
            }
            if (secondaryIsExternal && getPhysicalStreamNumberForExtSourceLocked(secondaryExtSource)
                    == mSystemSoundPhysicalStream) {
                addSystemStream = false;
            }
            int systemSoundFlag = 0x1 << mSystemSoundPhysicalStream;
            // stream already added by focus. Cannot distinguish system sound play from other sound
            // in this stream.
            if ((streamsToRequest & systemSoundFlag) != 0) {
                addSystemStream = false;
            }
            if (addSystemStream) {
                streamsToRequest |= systemSoundFlag;
                audioContexts |= AudioHalService.AUDIO_CONTEXT_SYSTEM_SOUND_FLAG;
                if (focusToRequest == AudioHalService.VEHICLE_AUDIO_FOCUS_REQUEST_RELEASE) {
                    focusToRequest =
                            AudioHalService.VEHICLE_AUDIO_FOCUS_REQUEST_GAIN_TRANSIENT_NO_DUCK;
                }
            }
        }
        boolean routingHintChanged = sendExtRoutingHintToCarIfNecessaryLocked(primaryExtSource,
                secondaryExtSource);
        return sendFocusRequestToCarIfNecessaryLocked(focusToRequest, streamsToRequest, extFocus,
                audioContexts, routingHintChanged);
    }

    /**
     * Fix external source info if it is not valid.
     * @param extSourceInfo
     * @return true if value is not valid and was updated.
     */
    private boolean fixExtSourceAndContext(ExtSourceInfo extSourceInfo) {
        if (!mExternalRoutingTypes.containsKey(extSourceInfo.source)) {
            Log.w(CarLog.TAG_AUDIO, "External source not available:" + extSourceInfo.source);
            // fall back to radio
            extSourceInfo.source = mDefaultRadioRoutingType;
            extSourceInfo.context = AudioHalService.AUDIO_CONTEXT_RADIO_FLAG;
            return true;
        }
        if (extSourceInfo.context == AudioHalService.AUDIO_CONTEXT_RADIO_FLAG &&
                !extSourceInfo.source.startsWith(RADIO_ROUTING_SOURCE_PREFIX)) {
            Log.w(CarLog.TAG_AUDIO, "Expecting Radio source:" + extSourceInfo.source);
            extSourceInfo.source = mDefaultRadioRoutingType;
            return true;
        }
        return false;
    }

    private int getPhysicalStreamFlagForExtSourceLocked(String extSource) {
        return 0x1 << getPhysicalStreamNumberForExtSourceLocked(extSource);
    }

    private int getPhysicalStreamNumberForExtSourceLocked(String extSource) {
        AudioHalService.ExtRoutingSourceInfo info = mExternalRoutingTypes.get(
                extSource);
        if (info != null) {
            return info.physicalStreamNumber;
        } else {
            return mRadioPhysicalStream;
        }
    }

    private boolean sendExtRoutingHintToCarIfNecessaryLocked(String primarySource,
            String secondarySource) {
        if (!mExternalRoutingHintSupported) {
            return false;
        }
        if (DBG) {
            Log.d(TAG_FOCUS, "Setting external routing hint, primary:" + primarySource +
                    " secondary:" + secondarySource);
        }
        Arrays.fill(mExternalRoutingsScratch, 0);
        fillExtRoutingPositionLocked(mExternalRoutingsScratch, primarySource);
        fillExtRoutingPositionLocked(mExternalRoutingsScratch, secondarySource);
        if (Arrays.equals(mExternalRoutingsScratch, mExternalRoutings)) {
            return false;
        }
        System.arraycopy(mExternalRoutingsScratch, 0, mExternalRoutings, 0,
                mExternalRoutingsScratch.length);
        if (DBG) {
            Log.d(TAG_FOCUS, "Set values:" + Arrays.toString(mExternalRoutingsScratch));
        }
        try {
            mAudioHal.setExternalRoutingSource(mExternalRoutings);
        } catch (IllegalArgumentException e) {
            //ignore. can happen with mocking.
            return false;
        }
        return true;
    }

    private void fillExtRoutingPositionLocked(int[] array, String extSource) {
        if (extSource == null) {
            return;
        }
        AudioHalService.ExtRoutingSourceInfo info = mExternalRoutingTypes.get(
                extSource);
        if (info == null) {
            return;
        }
        int pos = info.bitPosition;
        if (pos < 0) {
            return;
        }
        int index = pos / 32;
        int bitPosInInt = pos % 32;
        array[index] |= (0x1 << bitPosInInt);
    }

    private boolean requestFocusForSystemSoundOnlyCaseLocked() {
        int focusRequest = AudioHalService.VEHICLE_AUDIO_FOCUS_REQUEST_GAIN_TRANSIENT_NO_DUCK;
        int streamsToRequest = 0x1 << mSystemSoundPhysicalStream;
        int extFocus = 0;
        int audioContexts = AudioHalService.AUDIO_CONTEXT_SYSTEM_SOUND_FLAG;
        mCurrentPrimaryAudioContext = audioContexts;
        return sendFocusRequestToCarIfNecessaryLocked(focusRequest, streamsToRequest, extFocus,
                audioContexts, false /*forceSend*/);
    }

    private void requestFocusReleaseForSystemSoundLocked() {
        switch (mCurrentFocusState.focusState) {
            case AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_GAIN:
            case AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_GAIN_TRANSIENT:
                mFocusHandler.handleFocusReleaseRequest();
            default: // ignore
                break;
        }
    }

    private void doSendFocusRequestToCarLocked(int focusToRequest,
            int streamsToRequest, int extFocus, int audioContexts) {
        if (DBG) {
            Log.d(TAG_FOCUS, String.format("audio focus request. focusToRequest = %d, " +
                "streamsToRequest = 0x%x, extFocus = 0x%x, audioContexts = 0x%x",
                focusToRequest, streamsToRequest, extFocus, audioContexts));
        }
        try {
            mAudioHal.requestAudioFocusChange(
                    focusToRequest,
                    streamsToRequest,
                    extFocus,
                    audioContexts);
        } catch (IllegalArgumentException e) {
            // can happen when mocking ends. ignore. timeout will handle it properly.
        }
        try {
            mLock.wait(mFocusResponseWaitTimeoutMs);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    private boolean sendFocusRequestToCarIfNecessaryLocked(int focusToRequest,
            int streamsToRequest, int extFocus, int audioContexts, boolean forceSend) {
        if (needsToSendFocusRequestLocked(focusToRequest, streamsToRequest, extFocus,
                audioContexts) || forceSend) {
            mLastFocusRequestToCar = FocusRequest.create(focusToRequest, streamsToRequest,
                    extFocus);
            mCurrentAudioContexts = audioContexts;
            if (((mCurrentFocusState.streams & streamsToRequest) == streamsToRequest) &&
                    ((mCurrentFocusState.streams & ~streamsToRequest) != 0)) {
                // stream is reduced, so do not release it immediately
                try {
                    Thread.sleep(NO_FOCUS_PLAY_WAIT_TIME_MS);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            if (DBG) {
                Log.d(TAG_FOCUS, "focus request to car:" + mLastFocusRequestToCar + " context:0x" +
                        Integer.toHexString(audioContexts));
            }
            doSendFocusRequestToCarLocked(focusToRequest, streamsToRequest, extFocus,
                    audioContexts);
            return true;
        }
        return false;
    }

    private boolean needsToSendFocusRequestLocked(int focusToRequest, int streamsToRequest,
            int extFocus, int audioContexts) {
        if (streamsToRequest != mCurrentFocusState.streams) {
            return true;
        }
        if (audioContexts != mCurrentAudioContexts) {
            return true;
        }
        if ((extFocus & mCurrentFocusState.externalFocus) != extFocus) {
            return true;
        }
        switch (focusToRequest) {
            case AudioHalService.VEHICLE_AUDIO_FOCUS_REQUEST_GAIN:
                if (mCurrentFocusState.focusState ==
                    AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_GAIN) {
                    return false;
                }
                break;
            case AudioHalService.VEHICLE_AUDIO_FOCUS_REQUEST_GAIN_TRANSIENT:
            case AudioHalService.VEHICLE_AUDIO_FOCUS_REQUEST_GAIN_TRANSIENT_MAY_DUCK:
            case AudioHalService.VEHICLE_AUDIO_FOCUS_REQUEST_GAIN_TRANSIENT_NO_DUCK:
                if (mCurrentFocusState.focusState ==
                        AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_GAIN ||
                    mCurrentFocusState.focusState ==
                        AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_GAIN_TRANSIENT) {
                    return false;
                }
                break;
            case AudioHalService.VEHICLE_AUDIO_FOCUS_REQUEST_RELEASE:
                if (mCurrentFocusState.focusState ==
                        AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_LOSS ||
                        mCurrentFocusState.focusState ==
                        AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_LOSS_TRANSIENT_EXLCUSIVE) {
                    return false;
                }
                break;
        }
        return true;
    }

    private void doHandleAndroidFocusChange(boolean triggeredByStreamChange) {
        boolean focusRequested = false;
        synchronized (mLock) {
            AudioFocusInfo newTopInfo = null;
            if (mPendingFocusChanges.isEmpty()) {
                if (!triggeredByStreamChange) {
                    // no entry. It was handled already.
                    if (DBG) {
                        Log.d(TAG_FOCUS, "doHandleAndroidFocusChange, mPendingFocusChanges empty");
                    }
                    return;
                }
            } else {
                newTopInfo = mPendingFocusChanges.getFirst();
                mPendingFocusChanges.clear();
                if (mPrimaryFocusInfo != null &&
                        newTopInfo.getClientId().equals(mPrimaryFocusInfo.getClientId()) &&
                        newTopInfo.getGainRequest() == mPrimaryFocusInfo.getGainRequest() &&
                        isAudioAttributesSame(
                                newTopInfo.getAttributes(), mPrimaryFocusInfo.getAttributes()) &&
                                !triggeredByStreamChange) {
                    if (DBG) {
                        Log.d(TAG_FOCUS, "doHandleAndroidFocusChange, no change in top state:" +
                                dumpAudioFocusInfo(mPrimaryFocusInfo));
                    }
                    // already in top somehow, no need to make any change
                    return;
                }
            }
            if (newTopInfo != null) {
                if (newTopInfo.getGainRequest() ==
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) {
                    mSecondaryFocusInfo = mPrimaryFocusInfo;
                } else {
                    mSecondaryFocusInfo = null;
                }
                if (DBG) {
                    Log.d(TAG_FOCUS, "top focus changed to:" + dumpAudioFocusInfo(newTopInfo));
                }
                mPrimaryFocusInfo = newTopInfo;
            }
            focusRequested = handleCarFocusRequestAndResponseLocked();
        }
        // handle it if there was response or force handle it for timeout.
        if (focusRequested) {
            doHandleCarFocusChange();
        }
    }

    private boolean handleCarFocusRequestAndResponseLocked() {
        boolean focusRequested = reevaluateCarAudioFocusAndSendFocusLocked();
        if (DBG) {
            if (!focusRequested) {
                Log.i(TAG_FOCUS, "focus not requested for top focus:" +
                        dumpAudioFocusInfo(mPrimaryFocusInfo) + " currentState:" + mCurrentFocusState);
            }
        }
        if (focusRequested) {
            if (mFocusReceived == null) {
                Log.w(TAG_FOCUS, "focus response timed out, request sent "
                        + mLastFocusRequestToCar);
                // no response. so reset to loss.
                mFocusReceived = FocusState.STATE_LOSS;
                mCurrentAudioContexts = 0;
                mNumConsecutiveHalFailures++;
                mCurrentPrimaryAudioContext = 0;
                mCurrentPrimaryPhysicalStream = 0;
            } else {
                mNumConsecutiveHalFailures = 0;
            }
            // send context change after getting focus response.
            if (mCarAudioContextChangeHandler != null) {
                mCarAudioContextChangeHandler.requestContextChangeNotification(
                        mAudioContextChangeListener, mCurrentPrimaryAudioContext,
                        mCurrentPrimaryPhysicalStream);
            }
            checkCanStatus();
        }
        return focusRequested;
    }

    private void doHandleFocusRelease() {
        boolean sent = false;
        synchronized (mLock) {
            if (mCurrentFocusState != FocusState.STATE_LOSS) {
                if (DBG) {
                    Log.d(TAG_FOCUS, "focus release to car");
                }
                mLastFocusRequestToCar = FocusRequest.STATE_RELEASE;
                sent = true;
                if (mExternalRoutingHintSupported) {
                    mAudioHal.setExternalRoutingSource(mExternalRoutingsForFocusRelease);
                }
                doSendFocusRequestToCarLocked(AudioHalService.VEHICLE_AUDIO_FOCUS_REQUEST_RELEASE,
                        0, 0, 0);
                mCurrentPrimaryAudioContext = 0;
                mCurrentPrimaryPhysicalStream = 0;
                if (mCarAudioContextChangeHandler != null) {
                    mCarAudioContextChangeHandler.requestContextChangeNotification(
                            mAudioContextChangeListener, mCurrentPrimaryAudioContext,
                            mCurrentPrimaryPhysicalStream);
                }
            } else if (DBG) {
                Log.d(TAG_FOCUS, "doHandleFocusRelease: do not send, already loss");
            }
        }
        // handle it if there was response.
        if (sent) {
            doHandleCarFocusChange();
        }
    }

    private void checkCanStatus() {
        if (mCanBusErrorNotifier == null) {
            // TODO(b/36189057): create CanBusErrorNotifier from unit-tests and remove this code
            return;
        }

        // If CAN bus recovers, message will be removed.
        if (mNumConsecutiveHalFailures >= mNumConsecutiveHalFailuresForCanError) {
            mCanBusErrorNotifier.reportFailure(this);
        } else {
            mCanBusErrorNotifier.removeFailureReport(this);
        }
    }

    private static boolean isAudioAttributesSame(AudioAttributes one, AudioAttributes two) {
        if (one.getContentType() != two.getContentType()) {
            return false;
        }
        if (one.getUsage() != two.getUsage()) {
            return false;
        }
        return true;
    }

    private static String dumpAudioFocusInfo(AudioFocusInfo info) {
        if (info == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("afi package:" + info.getPackageName());
        builder.append("client id:" + info.getClientId());
        builder.append(",gain:" + info.getGainRequest());
        builder.append(",loss:" + info.getLossReceived());
        builder.append(",flag:" + info.getFlags());
        AudioAttributes attrib = info.getAttributes();
        if (attrib != null) {
            builder.append("," + attrib.toString());
        }
        return builder.toString();
    }

    private class SystemFocusListener extends AudioPolicyFocusListener {
        @Override
        public void onAudioFocusGrant(AudioFocusInfo afi, int requestResult) {
            if (afi == null) {
                return;
            }
            if (DBG) {
                Log.d(TAG_FOCUS, "onAudioFocusGrant " + dumpAudioFocusInfo(afi) +
                        " result:" + requestResult);
            }
            if (requestResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                synchronized (mLock) {
                    mPendingFocusChanges.addFirst(afi);
                }
                mFocusHandler.handleAndroidFocusChange();
            }
        }

        @Override
        public void onAudioFocusLoss(AudioFocusInfo afi, boolean wasNotified) {
            if (DBG) {
                Log.d(TAG_FOCUS, "onAudioFocusLoss " + dumpAudioFocusInfo(afi) +
                        " notified:" + wasNotified);
            }
            // ignore loss as tracking gain is enough. At least bottom listener will be
            // always there and getting focus grant. So it is safe to ignore this here.
        }
    }

    /**
     * Focus listener to take focus away from android apps as a proxy to car.
     */
    private class CarProxyAndroidFocusListener implements AudioManager.OnAudioFocusChangeListener {
        @Override
        public void onAudioFocusChange(int focusChange) {
            // Do not need to handle car's focus loss or gain separately. Focus monitoring
            // through system focus listener will take care all cases.
        }
    }

    /**
     * Focus listener kept at the bottom to check if there is any focus holder.
     *
     */
    private class BottomAudioFocusListener implements AudioManager.OnAudioFocusChangeListener {
        @Override
        public void onAudioFocusChange(int focusChange) {
        }
    }

    private class MediaMuteAudioFocusListener implements AudioManager.OnAudioFocusChangeListener {

        private final AudioAttributes mMuteAudioAttrib =
                CarAudioAttributesUtil.getAudioAttributesForCarUsage(
                        CarAudioAttributesUtil.CAR_AUDIO_USAGE_CARSERVICE_MEDIA_MUTE);

        /** not muted */
        private final static int MUTE_STATE_UNMUTED = 0;
        /** muted. other app requesting focus GAIN will unmute it */
        private final static int MUTE_STATE_MUTED = 1;
        /** locked. only system can unlock and send it to muted or unmuted state */
        private final static int MUTE_STATE_LOCKED = 2;

        private int mMuteState = MUTE_STATE_UNMUTED;

        @Override
        public void onAudioFocusChange(int focusChange) {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                // mute does not persist when there is other media kind app taking focus
                unMute();
            }
        }

        public boolean mute() {
            return mute(false);
        }

        /**
         * Mute with optional lock
         * @param lock Take focus with lock. Normal apps cannot take focus. Setting this will
         *             essentially mute all audio.
         * @return Final mute state
         */
        public synchronized boolean mute(boolean lock) {
            int result = AudioManager.AUDIOFOCUS_REQUEST_FAILED;
            boolean lockRequested = false;
            if (lock) {
                AudioPolicy audioPolicy = null;
                synchronized (CarAudioService.this) {
                    audioPolicy = mAudioPolicy;
                }
                if (audioPolicy != null) {
                    result =  mAudioManager.requestAudioFocus(this, mMuteAudioAttrib,
                            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                            AudioManager.AUDIOFOCUS_FLAG_LOCK |
                            AudioManager.AUDIOFOCUS_FLAG_DELAY_OK,
                            audioPolicy);
                    lockRequested = true;
                }
            }
            if (!lockRequested) {
                result = mAudioManager.requestAudioFocus(this, mMuteAudioAttrib,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                        AudioManager.AUDIOFOCUS_FLAG_DELAY_OK);
            }
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ||
                    result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
                if (lockRequested) {
                    mMuteState = MUTE_STATE_LOCKED;
                } else {
                    mMuteState = MUTE_STATE_MUTED;
                }
            } else {
                mMuteState = MUTE_STATE_UNMUTED;
            }
            return mMuteState != MUTE_STATE_UNMUTED;
        }

        public boolean unMute() {
            return unMute(false);
        }

        /**
         * Unmute. If locked, unmute will only succeed when unlock is set to true.
         * @param unlock
         * @return Final mute state
         */
        public synchronized boolean unMute(boolean unlock) {
            if (!unlock && mMuteState == MUTE_STATE_LOCKED) {
                // cannot unlock
                return true;
            }
            mMuteState = MUTE_STATE_UNMUTED;
            mAudioManager.abandonAudioFocus(this);
            return false;
        }

        public synchronized boolean isMuted() {
            return mMuteState != MUTE_STATE_UNMUTED;
        }
    }

    private class CarAudioContextChangeHandler extends Handler {
        private static final int MSG_CONTEXT_CHANGE = 0;

        private CarAudioContextChangeHandler(Looper looper) {
            super(looper);
        }

        private void requestContextChangeNotification(AudioContextChangeListener listener,
                int primaryContext, int physicalStream) {
            Message msg = obtainMessage(MSG_CONTEXT_CHANGE, primaryContext, physicalStream,
                    listener);
            sendMessage(msg);
        }

        private void cancelAll() {
            removeMessages(MSG_CONTEXT_CHANGE);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_CONTEXT_CHANGE: {
                    AudioContextChangeListener listener = (AudioContextChangeListener) msg.obj;
                    int context = msg.arg1;
                    int physicalStream = msg.arg2;
                    listener.onContextChange(context, physicalStream);
                } break;
            }
        }
    }

    private class CarAudioFocusChangeHandler extends Handler {
        private static final int MSG_FOCUS_CHANGE = 0;
        private static final int MSG_STREAM_STATE_CHANGE = 1;
        private static final int MSG_ANDROID_FOCUS_CHANGE = 2;
        private static final int MSG_FOCUS_RELEASE = 3;

        /** Focus release is always delayed this much to handle repeated acquire / release. */
        private static final long FOCUS_RELEASE_DELAY_MS = 500;

        private CarAudioFocusChangeHandler(Looper looper) {
            super(looper);
        }

        private void handleFocusChange() {
            cancelFocusReleaseRequest();
            Message msg = obtainMessage(MSG_FOCUS_CHANGE);
            sendMessage(msg);
        }

        private void handleStreamStateChange(int streamNumber, boolean streamActive) {
            cancelFocusReleaseRequest();
            removeMessages(MSG_STREAM_STATE_CHANGE);
            Message msg = obtainMessage(MSG_STREAM_STATE_CHANGE, streamNumber,
                    streamActive ? 1 : 0);
            sendMessageDelayed(msg,
                    streamActive ? NO_FOCUS_PLAY_WAIT_TIME_MS : FOCUS_RELEASE_DELAY_MS);
        }

        private void handleAndroidFocusChange() {
            cancelFocusReleaseRequest();
            Message msg = obtainMessage(MSG_ANDROID_FOCUS_CHANGE);
            sendMessage(msg);
        }

        private void handleFocusReleaseRequest() {
            if (DBG) {
                Log.d(TAG_FOCUS, "handleFocusReleaseRequest");
            }
            cancelFocusReleaseRequest();
            Message msg = obtainMessage(MSG_FOCUS_RELEASE);
            sendMessageDelayed(msg, FOCUS_RELEASE_DELAY_MS);
        }

        private void cancelFocusReleaseRequest() {
            removeMessages(MSG_FOCUS_RELEASE);
        }

        private void cancelAll() {
            removeMessages(MSG_FOCUS_CHANGE);
            removeMessages(MSG_STREAM_STATE_CHANGE);
            removeMessages(MSG_ANDROID_FOCUS_CHANGE);
            removeMessages(MSG_FOCUS_RELEASE);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_FOCUS_CHANGE:
                    doHandleCarFocusChange();
                    break;
                case MSG_STREAM_STATE_CHANGE:
                    doHandleStreamStatusChange(msg.arg1, msg.arg2 == 1);
                    break;
                case MSG_ANDROID_FOCUS_CHANGE:
                    doHandleAndroidFocusChange(false /* triggeredByStreamChange */);
                    break;
                case MSG_FOCUS_RELEASE:
                    doHandleFocusRelease();
                    break;
            }
        }
    }

    /** Wrapper class for holding the current focus state from car. */
    private static class FocusState {
        public final int focusState;
        public final int streams;
        public final int externalFocus;

        private FocusState(int focusState, int streams, int externalFocus) {
            this.focusState = focusState;
            this.streams = streams;
            this.externalFocus = externalFocus;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof FocusState)) {
                return false;
            }
            FocusState that = (FocusState) o;
            return this.focusState == that.focusState && this.streams == that.streams &&
                    this.externalFocus == that.externalFocus;
        }

        @Override
        public String toString() {
            return "FocusState, state:" + focusState +
                    " streams:0x" + Integer.toHexString(streams) +
                    " externalFocus:0x" + Integer.toHexString(externalFocus);
        }

        public static FocusState create(int focusState, int streams, int externalAudios) {
            return new FocusState(focusState, streams, externalAudios);
        }

        public static FocusState create(int[] state) {
            return create(state[AudioHalService.FOCUS_STATE_ARRAY_INDEX_STATE],
                          state[AudioHalService.FOCUS_STATE_ARRAY_INDEX_STREAMS],
                          state[AudioHalService.FOCUS_STATE_ARRAY_INDEX_EXTERNAL_FOCUS]);
        }

        public static FocusState STATE_LOSS =
                new FocusState(AudioHalService.VEHICLE_AUDIO_FOCUS_STATE_LOSS, 0, 0);
    }

    /** Wrapper class for holding the focus requested to car. */
    private static class FocusRequest {
        public final int focusRequest;
        public final int streams;
        public final int externalFocus;

        private FocusRequest(int focusRequest, int streams, int externalFocus) {
            this.focusRequest = focusRequest;
            this.streams = streams;
            this.externalFocus = externalFocus;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof FocusRequest)) {
                return false;
            }
            FocusRequest that = (FocusRequest) o;
            return this.focusRequest == that.focusRequest && this.streams == that.streams &&
                    this.externalFocus == that.externalFocus;
        }

        @Override
        public String toString() {
            return "FocusRequest, request:" + focusRequest +
                    " streams:0x" + Integer.toHexString(streams) +
                    " externalFocus:0x" + Integer.toHexString(externalFocus);
        }

        public static FocusRequest create(int focusRequest, int streams, int externalFocus) {
            switch (focusRequest) {
                case AudioHalService.VEHICLE_AUDIO_FOCUS_REQUEST_RELEASE:
                    return STATE_RELEASE;
            }
            return new FocusRequest(focusRequest, streams, externalFocus);
        }

        public static FocusRequest STATE_RELEASE =
                new FocusRequest(AudioHalService.VEHICLE_AUDIO_FOCUS_REQUEST_RELEASE, 0, 0);
    }

    private static class ExtSourceInfo {

        public String source;
        public int context;

        public ExtSourceInfo set(String source, int context) {
            this.source = source;
            this.context = context;
            return this;
        }
    }
}
