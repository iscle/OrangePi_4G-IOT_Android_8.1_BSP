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

package com.android.tv.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;

import com.android.tv.ApplicationSingletons;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.tuner.tvinput.TunerTvInputService;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A utility class related to input setup.
 */
public class SetupUtils {
    private static final String TAG = "SetupUtils";
    private static final boolean DEBUG = false;

    // Known inputs are inputs which are shown in SetupView before. When a new input is installed,
    // the input will not be included in "PREF_KEY_KNOWN_INPUTS".
    private static final String PREF_KEY_KNOWN_INPUTS = "known_inputs";
    // Set up inputs are inputs whose setup activity has been launched and finished successfully.
    private static final String PREF_KEY_SET_UP_INPUTS = "set_up_inputs";
    // Recognized inputs means that the user already knows the inputs are installed.
    private static final String PREF_KEY_RECOGNIZED_INPUTS = "recognized_inputs";
    private static final String PREF_KEY_IS_FIRST_TUNE = "is_first_tune";
    private static SetupUtils sSetupUtils;

    private final TvApplication mTvApplication;
    private final SharedPreferences mSharedPreferences;
    private final Set<String> mKnownInputs;
    private final Set<String> mSetUpInputs;
    private final Set<String> mRecognizedInputs;
    private boolean mIsFirstTune;
    private final String mTunerInputId;

    private SetupUtils(TvApplication tvApplication) {
        mTvApplication = tvApplication;
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(tvApplication);
        mSetUpInputs = new ArraySet<>();
        mSetUpInputs.addAll(mSharedPreferences.getStringSet(PREF_KEY_SET_UP_INPUTS,
                Collections.emptySet()));
        mKnownInputs = new ArraySet<>();
        mKnownInputs.addAll(mSharedPreferences.getStringSet(PREF_KEY_KNOWN_INPUTS,
                Collections.emptySet()));
        mRecognizedInputs = new ArraySet<>();
        mRecognizedInputs.addAll(mSharedPreferences.getStringSet(PREF_KEY_RECOGNIZED_INPUTS,
                mKnownInputs));
        mIsFirstTune = mSharedPreferences.getBoolean(PREF_KEY_IS_FIRST_TUNE, true);
        mTunerInputId = TvContract.buildInputId(new ComponentName(tvApplication,
                TunerTvInputService.class));
    }

    /**
     * Gets an instance of {@link SetupUtils}.
     */
    public static SetupUtils getInstance(Context context) {
        if (sSetupUtils != null) {
            return sSetupUtils;
        }
        sSetupUtils = new SetupUtils((TvApplication) context.getApplicationContext());
        return sSetupUtils;
    }

    /**
     * Additional work after the setup of TV input.
     */
    public void onTvInputSetupFinished(final String inputId,
            @Nullable final Runnable postRunnable) {
        // When TIS adds several channels, ChannelDataManager.Listener.onChannelList
        // Updated() can be called several times. In this case, it is hard to detect
        // which one is the last callback. To reduce error prune, we update channel
        // list again and make all channels of {@code inputId} browsable.
        onSetupDone(inputId);
        final ChannelDataManager manager = mTvApplication.getChannelDataManager();
        if (!manager.isDbLoadFinished()) {
            manager.addListener(new ChannelDataManager.Listener() {
                @Override
                public void onLoadFinished() {
                    manager.removeListener(this);
                    updateChannelsAfterSetup(mTvApplication, inputId, postRunnable);
                }

                @Override
                public void onChannelListUpdated() { }

                @Override
                public void onChannelBrowsableChanged() { }
            });
        } else {
            updateChannelsAfterSetup(mTvApplication, inputId, postRunnable);
        }
    }

    private static void updateChannelsAfterSetup(Context context, final String inputId,
            final Runnable postRunnable) {
        ApplicationSingletons appSingletons = TvApplication.getSingletons(context);
        final ChannelDataManager manager = appSingletons.getChannelDataManager();
        manager.updateChannels(new Runnable() {
            @Override
            public void run() {
                Channel firstChannelForInput = null;
                boolean browsableChanged = false;
                for (Channel channel : manager.getChannelList()) {
                    if (channel.getInputId().equals(inputId)) {
                        if (!channel.isBrowsable()) {
                            manager.updateBrowsable(channel.getId(), true, true);
                            browsableChanged = true;
                        }
                        if (firstChannelForInput == null) {
                            firstChannelForInput = channel;
                        }
                    }
                }
                if (firstChannelForInput != null) {
                    Utils.setLastWatchedChannel(context, firstChannelForInput);
                }
                if (browsableChanged) {
                    manager.notifyChannelBrowsableChanged();
                    manager.applyUpdatedValuesToDb();
                }
                if (postRunnable != null) {
                    postRunnable.run();
                }
            }
        });
    }

    /**
     * Marks the channels in newly installed inputs browsable.
     */
    @UiThread
    public void markNewChannelsBrowsable() {
        Set<String> newInputsWithChannels = new HashSet<>();
        TvInputManagerHelper tvInputManagerHelper = mTvApplication.getTvInputManagerHelper();
        ChannelDataManager channelDataManager = mTvApplication.getChannelDataManager();
        SoftPreconditions.checkState(channelDataManager.isDbLoadFinished());
        for (TvInputInfo input : tvInputManagerHelper.getTvInputInfos(true, true)) {
            String inputId = input.getId();
            if (!isSetupDone(inputId) && channelDataManager.getChannelCountForInput(inputId) > 0) {
                onSetupDone(inputId);
                newInputsWithChannels.add(inputId);
                if (DEBUG) {
                    Log.d(TAG, "New input " + inputId + " has "
                            + channelDataManager.getChannelCountForInput(inputId)
                            + " channels");
                }
            }
        }
        if (!newInputsWithChannels.isEmpty()) {
            for (Channel channel : channelDataManager.getChannelList()) {
                if (newInputsWithChannels.contains(channel.getInputId())) {
                    channelDataManager.updateBrowsable(channel.getId(), true);
                }
            }
            channelDataManager.applyUpdatedValuesToDb();
        }
    }

    public boolean isFirstTune() {
        return mIsFirstTune;
    }

    /**
     * Returns true, if the input with {@code inputId} is newly installed.
     */
    public boolean isNewInput(String inputId) {
        return !mKnownInputs.contains(inputId);
    }

    /**
     * Marks an input with {@code inputId} as a known input. Once it is marked, {@link #isNewInput}
     * will return false.
     */
    public void markAsKnownInput(String inputId) {
        mKnownInputs.add(inputId);
        mRecognizedInputs.add(inputId);
        mSharedPreferences.edit().putStringSet(PREF_KEY_KNOWN_INPUTS, mKnownInputs)
                .putStringSet(PREF_KEY_RECOGNIZED_INPUTS, mRecognizedInputs).apply();
    }

    /**
     * Returns {@code true}, if {@code inputId}'s setup has been done before.
     */
    public boolean isSetupDone(String inputId) {
        boolean done = mSetUpInputs.contains(inputId);
        if (DEBUG) {
            Log.d(TAG, "isSetupDone: (input=" + inputId + ", result= " + done + ")");
        }
        return done;
    }

    /**
     * Returns true, if there is any newly installed input.
     */
    public boolean hasNewInput(TvInputManagerHelper inputManager) {
        for (TvInputInfo input : inputManager.getTvInputInfos(true, true)) {
            if (isNewInput(input.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether the given input is already recognized by the user or not.
     */
    private boolean isRecognizedInput(String inputId) {
        return mRecognizedInputs.contains(inputId);
    }

    /**
     * Marks all the inputs as recognized inputs. Once it is marked, {@link #isRecognizedInput} will
     * return {@code true}.
     */
    public void markAllInputsRecognized(TvInputManagerHelper inputManager) {
        for (TvInputInfo input : inputManager.getTvInputInfos(true, true)) {
            mRecognizedInputs.add(input.getId());
        }
        mSharedPreferences.edit().putStringSet(PREF_KEY_RECOGNIZED_INPUTS, mRecognizedInputs)
                .apply();
    }

    /**
     * Checks whether there are any unrecognized inputs.
     */
    public boolean hasUnrecognizedInput(TvInputManagerHelper inputManager) {
        for (TvInputInfo input : inputManager.getTvInputInfos(true, true)) {
            if (!isRecognizedInput(input.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Grants permission for writing EPG data to all verified packages.
     *
     * @param context The Context used for granting permission.
     */
    public static void grantEpgPermissionToSetUpPackages(Context context) {
        // Find all already-verified packages.
        Set<String> setUpPackages = new HashSet<>();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        for (String input : sp.getStringSet(PREF_KEY_SET_UP_INPUTS,
                Collections.<String>emptySet())) {
            if (!TextUtils.isEmpty(input)) {
                ComponentName componentName = ComponentName.unflattenFromString(input);
                if (componentName != null) {
                    setUpPackages.add(componentName.getPackageName());
                }
            }
        }

        for (String packageName : setUpPackages) {
            grantEpgPermission(context, packageName);
        }
    }

    /**
     * Grants permission for writing EPG data to a given package.
     *
     * @param context The Context used for granting permission.
     * @param packageName The name of the package to give permission.
     */
    public static void grantEpgPermission(Context context, String packageName) {
        if (DEBUG) {
            Log.d(TAG, "grantEpgPermission(context=" + context + ", packageName=" + packageName
                    + ")");
        }
        try {
            int modeFlags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
            context.grantUriPermission(packageName, TvContract.Channels.CONTENT_URI, modeFlags);
            context.grantUriPermission(packageName, TvContract.Programs.CONTENT_URI, modeFlags);
        } catch (SecurityException e) {
            Log.e(TAG, "Either TvProvider does not allow granting of Uri permissions or the app"
                    + " does not have permission.", e);
        }
    }

    /**
     * Called when Live channels app is launched. Once it is called, {@link
     * #isFirstTune} will return false.
     */
    public void onTuned() {
        if (!mIsFirstTune) {
            return;
        }
        mIsFirstTune = false;
        mSharedPreferences.edit().putBoolean(PREF_KEY_IS_FIRST_TUNE, false).apply();
    }

    /**
     * Called when input list is changed. It mainly handles input removals.
     */
    public void onInputListUpdated(TvInputManager manager) {
        // mRecognizedInputs > mKnownInputs > mSetUpInputs.
        Set<String> removedInputList = new HashSet<>(mRecognizedInputs);
        for (TvInputInfo input : manager.getTvInputList()) {
            removedInputList.remove(input.getId());
        }
        // A USB tuner device can be temporarily unplugged. We do not remove the USB tuner input
        // from the known inputs so that the input won't appear as a new input whenever the user
        // plugs in the USB tuner device again.
        removedInputList.remove(mTunerInputId);

        if (!removedInputList.isEmpty()) {
            boolean inputPackageDeleted = false;
            for (String input : removedInputList) {
                try {
                    // Just after booting, input list from TvInputManager are not reliable.
                    // So we need to double-check package existence. b/29034900
                    mTvApplication.getPackageManager().getPackageInfo(
                            ComponentName.unflattenFromString(input)
                            .getPackageName(), PackageManager.GET_ACTIVITIES);
                    Log.i(TAG, "TV input (" + input + ") is removed but package is not deleted");
                } catch (NameNotFoundException e) {
                    Log.i(TAG, "TV input (" + input + ") and its package are removed");
                    mRecognizedInputs.remove(input);
                    mSetUpInputs.remove(input);
                    mKnownInputs.remove(input);
                    inputPackageDeleted = true;
                }
            }
            if (inputPackageDeleted) {
                mSharedPreferences.edit().putStringSet(PREF_KEY_SET_UP_INPUTS, mSetUpInputs)
                        .putStringSet(PREF_KEY_KNOWN_INPUTS, mKnownInputs)
                        .putStringSet(PREF_KEY_RECOGNIZED_INPUTS, mRecognizedInputs).apply();
            }
        }
    }

    /**
     * Called when an setup is done. Once it is called, {@link #isSetupDone} returns {@code true}
     * for {@code inputId}.
     */
    private void onSetupDone(String inputId) {
        SoftPreconditions.checkState(inputId != null);
        if (DEBUG) Log.d(TAG, "onSetupDone: input=" + inputId);
        if (!mRecognizedInputs.contains(inputId)) {
            Log.i(TAG, "An unrecognized input's setup has been done. inputId=" + inputId);
            mRecognizedInputs.add(inputId);
            mSharedPreferences.edit().putStringSet(PREF_KEY_RECOGNIZED_INPUTS, mRecognizedInputs)
                    .apply();
        }
        if (!mKnownInputs.contains(inputId)) {
            Log.i(TAG, "An unknown input's setup has been done. inputId=" + inputId);
            mKnownInputs.add(inputId);
            mSharedPreferences.edit().putStringSet(PREF_KEY_KNOWN_INPUTS, mKnownInputs).apply();
        }
        if (!mSetUpInputs.contains(inputId)) {
            mSetUpInputs.add(inputId);
            mSharedPreferences.edit().putStringSet(PREF_KEY_SET_UP_INPUTS, mSetUpInputs).apply();
        }
    }
}