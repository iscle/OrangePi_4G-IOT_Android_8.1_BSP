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
package android.car.input;

import android.annotation.CallSuper;
import android.annotation.MainThread;
import android.annotation.SystemApi;
import android.app.Service;
import android.car.CarLibLog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;

/**
 * A service that is used for handling of input events.
 *
 * <p>To extend this class, you must declare the service in your manifest file with
 * the {@code android.car.permission.BIND_CAR_INPUT_SERVICE} permission
 * <pre>
 * &lt;service android:name=".MyCarInputService"
 *          android:permission="android.car.permission.BIND_CAR_INPUT_SERVICE">
 * &lt;/service></pre>
 * <p>Also, you will need to register this service in the following configuration file:
 * {@code packages/services/Car/service/res/values/config.xml}
 *
 * @hide
 */
@SystemApi
public abstract class CarInputHandlingService extends Service {
    private static final String TAG = CarLibLog.TAG_INPUT;
    private static final boolean DBG = false;

    public static final String INPUT_CALLBACK_BINDER_KEY = "callback_binder";
    public static final int INPUT_CALLBACK_BINDER_CODE = IBinder.FIRST_CALL_TRANSACTION;

    private final InputFilter[] mHandledKeys;

    private InputBinder mInputBinder;

    protected CarInputHandlingService(InputFilter[] handledKeys) {
        if (handledKeys == null) {
            throw new IllegalArgumentException("handledKeys is null");
        }

        mHandledKeys = new InputFilter[handledKeys.length];
        System.arraycopy(handledKeys, 0, mHandledKeys, 0, handledKeys.length);
    }

    @Override
    @CallSuper
    public IBinder onBind(Intent intent) {
        if (DBG) {
            Log.d(TAG, "onBind, intent: " + intent);
        }

        doCallbackIfPossible(intent.getExtras());

        if (mInputBinder == null) {
            mInputBinder = new InputBinder();
        }

        return mInputBinder;
    }

    private void doCallbackIfPossible(Bundle extras) {
        if (extras == null) {
            Log.i(TAG, "doCallbackIfPossible: extras are null");
            return;
        }
        IBinder callbackBinder = extras.getBinder(INPUT_CALLBACK_BINDER_KEY);
        if (callbackBinder == null) {
            Log.i(TAG, "doCallbackIfPossible: callback IBinder is null");
            return;
        }
        Parcel dataIn = Parcel.obtain();
        dataIn.writeTypedArray(mHandledKeys, 0);
        try {
            callbackBinder.transact(INPUT_CALLBACK_BINDER_CODE, dataIn, null, IBinder.FLAG_ONEWAY);
        } catch (RemoteException e) {
            Log.e(TAG, "doCallbackIfPossible: callback failed", e);
        }
    }

    /**
     * Called when key event has been received.
     */
    @MainThread
    protected abstract void onKeyEvent(KeyEvent keyEvent, int targetDisplay);

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("**" + getClass().getSimpleName() + "**");
        writer.println("input binder: " + mInputBinder);
    }

    private class InputBinder extends ICarInputListener.Stub {
        private final EventHandler mEventHandler;

        InputBinder() {
            mEventHandler = new EventHandler(CarInputHandlingService.this);
        }

        @Override
        public void onKeyEvent(KeyEvent keyEvent, int targetDisplay) throws RemoteException {
            mEventHandler.doKeyEvent(keyEvent, targetDisplay);
        }
    }

    private static class EventHandler extends Handler {
        private static final int KEY_EVENT = 0;
        private final WeakReference<CarInputHandlingService> mRefService;

        EventHandler(CarInputHandlingService service) {
            mRefService = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            CarInputHandlingService service = mRefService.get();
            if (service == null) {
                return;
            }

            if (msg.what == KEY_EVENT) {
                service.onKeyEvent((KeyEvent) msg.obj, msg.arg1);
            } else {
                throw new IllegalArgumentException("Unexpected message: " + msg);
            }
        }

        void doKeyEvent(KeyEvent event, int targetDisplay) {
            sendMessage(obtainMessage(KEY_EVENT, targetDisplay, 0, event));
        }
    }

    /**
     * Filter for input events that are handled by custom service.
     */
    public static class InputFilter implements Parcelable {
        public final int mKeyCode;
        public final int mTargetDisplay;

        public InputFilter(int keyCode, int targetDisplay) {
            mKeyCode = keyCode;
            mTargetDisplay = targetDisplay;
        }

        // Parcelling part
        InputFilter(Parcel in) {
            mKeyCode = in.readInt();
            mTargetDisplay = in.readInt();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mKeyCode);
            dest.writeInt(mTargetDisplay);
        }

        public static final Parcelable.Creator CREATOR = new Parcelable.Creator() {
            public InputFilter createFromParcel(Parcel in) {
                return new InputFilter(in);
            }

            public InputFilter[] newArray(int size) {
                return new InputFilter[size];
            }
        };
    }
}
