/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.telecom.components;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.media.IAudioService;
import android.media.ToneGenerator;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.telecom.Log;

import com.android.internal.telephony.CallerInfoAsyncQuery;
import com.android.server.telecom.AsyncRingtonePlayer;
import com.android.server.telecom.BluetoothAdapterProxy;
import com.android.server.telecom.BluetoothPhoneServiceImpl;
import com.android.server.telecom.CallerInfoAsyncQueryFactory;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.ClockProxy;
import com.android.server.telecom.DefaultDialerCache;
import com.android.server.telecom.HeadsetMediaButton;
import com.android.server.telecom.HeadsetMediaButtonFactory;
import com.android.server.telecom.InCallTonePlayer;
import com.android.server.telecom.InCallWakeLockControllerFactory;
import com.android.server.telecom.CallAudioManager;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.PhoneNumberUtilsAdapterImpl;
import com.android.server.telecom.ProximitySensorManagerFactory;
import com.android.server.telecom.InCallWakeLockController;
import com.android.server.telecom.ProximitySensorManager;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.TelecomWakeLock;
import com.android.server.telecom.Timeouts;
import com.android.server.telecom.ui.IncomingCallNotifier;
import com.android.server.telecom.ui.MissedCallNotifierImpl;
import com.android.server.telecom.ui.NotificationChannelManager;

/**
 * Implementation of the ITelecom interface.
 */
public class TelecomService extends Service implements TelecomSystem.Component {

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(this, "onBind");
        initializeTelecomSystem(this);
        synchronized (getTelecomSystem().getLock()) {
            return getTelecomSystem().getTelecomServiceImpl().getBinder();
        }
    }

    /**
     * This method is to be called by components (Activitys, Services, ...) to initialize the
     * Telecom singleton. It should only be called on the main thread. As such, it is atomic
     * and needs no synchronization -- it will either perform its initialization, after which
     * the {@link TelecomSystem#getInstance()} will be initialized, or some other invocation of
     * this method on the main thread will have happened strictly prior to it, and this method
     * will be a benign no-op.
     *
     * @param context
     */
    static void initializeTelecomSystem(Context context) {
        if (TelecomSystem.getInstance() == null) {
            NotificationChannelManager notificationChannelManager =
                    new NotificationChannelManager();
            notificationChannelManager.createChannels(context);
            TelecomSystem.setInstance(
                    new TelecomSystem(
                            context,
                            new MissedCallNotifierImpl.MissedCallNotifierImplFactory() {
                                @Override
                                public MissedCallNotifierImpl makeMissedCallNotifierImpl(
                                        Context context,
                                        PhoneAccountRegistrar phoneAccountRegistrar,
                                        DefaultDialerCache defaultDialerCache) {
                                    return new MissedCallNotifierImpl(context,
                                            phoneAccountRegistrar, defaultDialerCache);
                                }
                            },
                            new CallerInfoAsyncQueryFactory() {
                                @Override
                                public CallerInfoAsyncQuery startQuery(int token, Context context,
                                        String number,
                                        CallerInfoAsyncQuery.OnQueryCompleteListener listener,
                                        Object cookie) {
                                    Log.i(TelecomSystem.getInstance(),
                                            "CallerInfoAsyncQuery.startQuery number=%s cookie=%s",
                                            Log.pii(number), cookie);
                                    return CallerInfoAsyncQuery.startQuery(
                                            token, context, number, listener, cookie);
                                }
                            },
                            new HeadsetMediaButtonFactory() {
                                @Override
                                public HeadsetMediaButton create(
                                        Context context,
                                        CallsManager callsManager,
                                        TelecomSystem.SyncRoot lock) {
                                    return new HeadsetMediaButton(context, callsManager, lock);
                                }
                            },
                            new ProximitySensorManagerFactory() {
                                @Override
                                public ProximitySensorManager create(
                                        Context context,
                                        CallsManager callsManager) {
                                    return new ProximitySensorManager(
                                            new TelecomWakeLock(
                                                    context,
                                                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                                                    ProximitySensorManager.class.getSimpleName()),
                                            callsManager);
                                }
                            },
                            new InCallWakeLockControllerFactory() {
                                @Override
                                public InCallWakeLockController create(Context context,
                                        CallsManager callsManager) {
                                    return new InCallWakeLockController(
                                            new TelecomWakeLock(context,
                                                    PowerManager.FULL_WAKE_LOCK,
                                                    InCallWakeLockController.class.getSimpleName()),
                                            callsManager);
                                }
                            },
                            new CallAudioManager.AudioServiceFactory() {
                                @Override
                                public IAudioService getAudioService() {
                                    return IAudioService.Stub.asInterface(
                                            ServiceManager.getService(Context.AUDIO_SERVICE));
                                }
                            },
                            new BluetoothPhoneServiceImpl.BluetoothPhoneServiceImplFactory() {
                                @Override
                                public BluetoothPhoneServiceImpl makeBluetoothPhoneServiceImpl(
                                        Context context, TelecomSystem.SyncRoot lock,
                                        CallsManager callsManager,
                                        PhoneAccountRegistrar phoneAccountRegistrar) {
                                    return new BluetoothPhoneServiceImpl(context, lock,
                                            callsManager, new BluetoothAdapterProxy(),
                                            phoneAccountRegistrar);
                                }
                            },
                            new Timeouts.Adapter(),
                            new AsyncRingtonePlayer(),
                            new PhoneNumberUtilsAdapterImpl(),
                            new IncomingCallNotifier(context),
                            ToneGenerator::new,
                            new ClockProxy() {
                                @Override
                                public long currentTimeMillis() {
                                    return System.currentTimeMillis();
                                }

                                @Override
                                public long elapsedRealtime() {
                                    return SystemClock.elapsedRealtime();
                                }
                            }));
        }
        if (BluetoothAdapter.getDefaultAdapter() != null) {
            context.startService(new Intent(context, BluetoothPhoneService.class));
        }
    }

    @Override
    public TelecomSystem getTelecomSystem() {
        return TelecomSystem.getInstance();
    }
}
