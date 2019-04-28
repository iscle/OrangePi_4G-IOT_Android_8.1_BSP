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
package com.android.car;

import static android.hardware.input.InputManager.INJECT_INPUT_EVENT_MODE_ASYNC;

import android.car.input.CarInputHandlingService;
import android.car.input.CarInputHandlingService.InputFilter;
import android.car.input.ICarInputListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.CallLog.Calls;
import android.speech.RecognizerIntent;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;

import com.android.car.hal.InputHalService;
import com.android.car.hal.VehicleHal;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CarInputService implements CarServiceBase, InputHalService.InputListener {

    public interface KeyEventListener {
        boolean onKeyEvent(KeyEvent event);
    }

    private static final long LONG_PRESS_TIME_MS = 1000;
    private static final boolean DBG = false;

    private final Context mContext;
    private final InputHalService mInputHalService;
    private final TelecomManager mTelecomManager;
    private final InputManager mInputManager;

    private KeyEventListener mVoiceAssistantKeyListener;
    private KeyEventListener mLongVoiceAssistantKeyListener;
    private long mLastVoiceKeyDownTime = 0;

    private long mLastCallKeyDownTime = 0;

    private KeyEventListener mInstrumentClusterKeyListener;

    private KeyEventListener mVolumeKeyListener;

    private ICarInputListener mCarInputListener;
    private boolean mCarInputListenerBound = false;
    private final Map<Integer, Set<Integer>> mHandledKeys = new HashMap<>();

    private int mKeyEventCount = 0;

    private final Binder mCallback = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == CarInputHandlingService.INPUT_CALLBACK_BINDER_CODE) {
                data.setDataPosition(0);
                InputFilter[] handledKeys = (InputFilter[]) data.createTypedArray(
                        InputFilter.CREATOR);
                if (handledKeys != null) {
                    setHandledKeys(handledKeys);
                }
                return true;
            }
            return false;
        }
    };

    private final ServiceConnection mInputServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (DBG) {
                Log.d(CarLog.TAG_INPUT, "onServiceConnected, name: "
                        + name + ", binder: " + binder);
            }
            mCarInputListener = ICarInputListener.Stub.asInterface(binder);

            try {
                binder.linkToDeath(() -> CarServiceUtils.runOnMainSync(() -> {
                    Log.w(CarLog.TAG_INPUT, "Input service died. Trying to rebind...");
                    mCarInputListener = null;
                    // Try to rebind with input service.
                    mCarInputListenerBound = bindCarInputService();
                }), 0);
            } catch (RemoteException e) {
                Log.e(CarLog.TAG_INPUT, e.getMessage(), e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(CarLog.TAG_INPUT, "onServiceDisconnected, name: " + name);
            mCarInputListener = null;
            // Try to rebind with input service.
            mCarInputListenerBound = bindCarInputService();
        }
    };

    public CarInputService(Context context, InputHalService inputHalService) {
        mContext = context;
        mInputHalService = inputHalService;
        mTelecomManager = context.getSystemService(TelecomManager.class);
        mInputManager = context.getSystemService(InputManager.class);
    }

    private synchronized void setHandledKeys(InputFilter[] handledKeys) {
        mHandledKeys.clear();
        for (InputFilter handledKey : handledKeys) {
            Set<Integer> displaySet = mHandledKeys.get(handledKey.mTargetDisplay);
            if (displaySet == null) {
                displaySet = new HashSet<Integer>();
                mHandledKeys.put(handledKey.mTargetDisplay, displaySet);
            }
            displaySet.add(handledKey.mKeyCode);
        }
    }

    /**
     * Set listener for listening voice assistant key event. Setting to null stops listening.
     * If listener is not set, default behavior will be done for short press.
     * If listener is set, short key press will lead into calling the listener.
     * @param listener
     */
    public void setVoiceAssistantKeyListener(KeyEventListener listener) {
        synchronized (this) {
            mVoiceAssistantKeyListener = listener;
        }
    }

    /**
     * Set listener for listening long voice assistant key event. Setting to null stops listening.
     * If listener is not set, default behavior will be done for long press.
     * If listener is set, short long press will lead into calling the listener.
     * @param listener
     */
    public void setLongVoiceAssistantKeyListener(KeyEventListener listener) {
        synchronized (this) {
            mLongVoiceAssistantKeyListener = listener;
        }
    }

    public void setInstrumentClusterKeyListener(KeyEventListener listener) {
        synchronized (this) {
            mInstrumentClusterKeyListener = listener;
        }
    }

    public void setVolumeKeyListener(KeyEventListener listener) {
        synchronized (this) {
            mVolumeKeyListener = listener;
        }
    }

    @Override
    public void init() {
        if (!mInputHalService.isKeyInputSupported()) {
            Log.w(CarLog.TAG_INPUT, "Hal does not support key input.");
            return;
        }


        mInputHalService.setInputListener(this);
        mCarInputListenerBound = bindCarInputService();
    }

    @Override
    public void release() {
        synchronized (this) {
            mVoiceAssistantKeyListener = null;
            mLongVoiceAssistantKeyListener = null;
            mInstrumentClusterKeyListener = null;
            mKeyEventCount = 0;
            if (mCarInputListenerBound) {
                mContext.unbindService(mInputServiceConnection);
                mCarInputListenerBound = false;
            }
        }
    }

    @Override
    public void onKeyEvent(KeyEvent event, int targetDisplay) {
        synchronized (this) {
            mKeyEventCount++;
        }
        if (handleSystemEvent(event)) {
            // System event handled, nothing more to do here.
            return;
        }
        if (mCarInputListener != null && isCustomEventHandler(event, targetDisplay)) {
            try {
                mCarInputListener.onKeyEvent(event, targetDisplay);
            } catch (RemoteException e) {
                Log.e(CarLog.TAG_INPUT, "Error while calling car input service", e);
            }
            // Custom input service handled the event, nothing more to do here.
            return;
        }

        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_VOICE_ASSIST:
                handleVoiceAssistKey(event);
                return;
            case KeyEvent.KEYCODE_CALL:
                handleCallKey(event);
                return;
            default:
                break;
        }
        if (targetDisplay == InputHalService.DISPLAY_INSTRUMENT_CLUSTER) {
            handleInstrumentClusterKey(event);
        } else {
            handleMainDisplayKey(event);
        }
    }

    private synchronized boolean isCustomEventHandler(KeyEvent event, int targetDisplay) {
        Set<Integer> displaySet = mHandledKeys.get(targetDisplay);
        if (displaySet == null) {
            return false;
        }
        return displaySet.contains(event.getKeyCode());
    }

    private boolean handleSystemEvent(KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                handleVolumeKey(event);
                return true;
            default:
                return false;
        }
    }

    private void handleVoiceAssistKey(KeyEvent event) {
        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN) {
            long now = SystemClock.elapsedRealtime();
            synchronized (this) {
                mLastVoiceKeyDownTime = now;
            }
        } else if (action == KeyEvent.ACTION_UP) {
            // if no listener, do not handle long press
            KeyEventListener listener = null;
            KeyEventListener shortPressListener = null;
            KeyEventListener longPressListener = null;
            long downTime;
            synchronized (this) {
                shortPressListener = mVoiceAssistantKeyListener;
                longPressListener = mLongVoiceAssistantKeyListener;
                downTime = mLastVoiceKeyDownTime;
            }
            if (shortPressListener == null && longPressListener == null) {
                launchDefaultVoiceAssistantHandler();
            } else {
                long duration = SystemClock.elapsedRealtime() - downTime;
                listener = (duration > LONG_PRESS_TIME_MS
                        ? longPressListener : shortPressListener);
                if (listener != null) {
                    listener.onKeyEvent(event);
                } else {
                    launchDefaultVoiceAssistantHandler();
                }
            }
        }
    }

    private void handleCallKey(KeyEvent event) {
        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN) {
            // Only handle if it's ringing when button down.
            if (mTelecomManager != null && mTelecomManager.isRinging()) {
                Log.i(CarLog.TAG_INPUT, "call key while rinning. Answer the call!");
                mTelecomManager.acceptRingingCall();
                return;
            }

            long now = SystemClock.elapsedRealtime();
            synchronized (this) {
                mLastCallKeyDownTime = now;
            }
        } else if (action == KeyEvent.ACTION_UP) {
            long downTime;
            synchronized (this) {
                downTime = mLastCallKeyDownTime;
            }
            long duration = SystemClock.elapsedRealtime() - downTime;
            if (duration > LONG_PRESS_TIME_MS) {
                dialLastCallHandler();
            } else {
                launchDialerHandler();
            }
        }
    }

    private void launchDialerHandler() {
        Log.i(CarLog.TAG_INPUT, "call key, launch dialer intent");
        Intent dialerIntent = new Intent(Intent.ACTION_DIAL);
        mContext.startActivityAsUser(dialerIntent, null, UserHandle.CURRENT_OR_SELF);
    }

    private void dialLastCallHandler() {
        Log.i(CarLog.TAG_INPUT, "call key, dialing last call");

        String lastNumber = Calls.getLastOutgoingCall(mContext);
        if (lastNumber != null && !lastNumber.isEmpty()) {
            Intent callLastNumberIntent = new Intent(Intent.ACTION_CALL)
                    .setData(Uri.fromParts("tel", lastNumber, null))
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivityAsUser(callLastNumberIntent, null, UserHandle.CURRENT_OR_SELF);
        }
    }

    private void launchDefaultVoiceAssistantHandler() {
        Log.i(CarLog.TAG_INPUT, "voice key, launch default intent");
        Intent voiceIntent =
                new Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE);
        mContext.startActivityAsUser(voiceIntent, null, UserHandle.CURRENT_OR_SELF);
    }

    private void handleInstrumentClusterKey(KeyEvent event) {
        KeyEventListener listener = null;
        synchronized (this) {
            listener = mInstrumentClusterKeyListener;
        }
        if (listener == null) {
            return;
        }
        listener.onKeyEvent(event);
    }

    private void handleVolumeKey(KeyEvent event) {
        KeyEventListener listener;
        synchronized (this) {
            listener = mVolumeKeyListener;
        }
        if (listener != null) {
            listener.onKeyEvent(event);
        }
    }

    private void handleMainDisplayKey(KeyEvent event) {
        mInputManager.injectInputEvent(event, INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("*Input Service*");
        writer.println("mCarInputListenerBound:" + mCarInputListenerBound);
        writer.println("mCarInputListener:" + mCarInputListener);
        writer.println("mLastVoiceKeyDownTime:" + mLastVoiceKeyDownTime +
                ",mKeyEventCount:" + mKeyEventCount);
    }

    private boolean bindCarInputService() {
        String carInputService = mContext.getString(R.string.inputService);
        if (TextUtils.isEmpty(carInputService)) {
            Log.i(CarLog.TAG_INPUT, "Custom input service was not configured");
            return false;
        }

        Log.d(CarLog.TAG_INPUT, "bindCarInputService, component: " + carInputService);

        Intent intent = new Intent();
        Bundle extras = new Bundle();
        extras.putBinder(CarInputHandlingService.INPUT_CALLBACK_BINDER_KEY, mCallback);
        intent.putExtras(extras);
        intent.setComponent(ComponentName.unflattenFromString(carInputService));
        return mContext.bindService(intent, mInputServiceConnection, Context.BIND_AUTO_CREATE);
    }
}
