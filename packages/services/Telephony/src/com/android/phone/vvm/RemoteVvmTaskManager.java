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
 * limitations under the License
 */

package com.android.phone.vvm;

import android.annotation.Nullable;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.VisualVoicemailService;
import android.telephony.VisualVoicemailSms;
import android.text.TextUtils;

import com.android.phone.Assert;
import com.android.phone.R;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Service to manage tasks issued to the {@link VisualVoicemailService}. This service will bind to
 * the default dialer on a visual voicemail event if it implements the VisualVoicemailService. The
 * service will hold all resource for the VisualVoicemailService until {@link
 * VisualVoicemailService.VisualVoicemailTask#finish()} has been called on all issued tasks.
 *
 * If the service is already running it will be reused for new events. The service will stop itself
 * after all events are handled.
 */
public class RemoteVvmTaskManager extends Service {

    private static final String TAG = "RemoteVvmTaskManager";

    private static final String ACTION_START_CELL_SERVICE_CONNECTED =
            "ACTION_START_CELL_SERVICE_CONNECTED";
    private static final String ACTION_START_SMS_RECEIVED = "ACTION_START_SMS_RECEIVED";
    private static final String ACTION_START_SIM_REMOVED = "ACTION_START_SIM_REMOVED";

    // TODO(b/35766990): Remove after VisualVoicemailService API is stabilized.
    private static final String ACTION_VISUAL_VOICEMAIL_SERVICE_EVENT =
            "com.android.phone.vvm.ACTION_VISUAL_VOICEMAIL_SERVICE_EVENT";
    private static final String EXTRA_WHAT = "what";

    // TODO(twyen): track task individually to have time outs.
    private int mTaskReferenceCount;

    private RemoteServiceConnection mConnection;

    /**
     * Handles incoming messages from the VisualVoicemailService.
     */
    private Messenger mMessenger;

    public static void startCellServiceConnected(Context context,
            PhoneAccountHandle phoneAccountHandle) {
        Intent intent = new Intent(ACTION_START_CELL_SERVICE_CONNECTED, null, context,
                RemoteVvmTaskManager.class);
        intent.putExtra(VisualVoicemailService.DATA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
        context.startService(intent);
    }

    public static void startSmsReceived(Context context, VisualVoicemailSms sms) {
        Intent intent = new Intent(ACTION_START_SMS_RECEIVED, null, context,
                RemoteVvmTaskManager.class);
        intent.putExtra(VisualVoicemailService.DATA_PHONE_ACCOUNT_HANDLE,
                sms.getPhoneAccountHandle());
        intent.putExtra(VisualVoicemailService.DATA_SMS, sms);
        context.startService(intent);
    }

    public static void startSimRemoved(Context context, PhoneAccountHandle phoneAccountHandle) {
        Intent intent = new Intent(ACTION_START_SIM_REMOVED, null, context,
                RemoteVvmTaskManager.class);
        intent.putExtra(VisualVoicemailService.DATA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
        context.startService(intent);
    }

    public static boolean hasRemoteService(Context context, int subId) {
        return getRemotePackage(context, subId) != null;
    }

    @Nullable
    public static ComponentName getRemotePackage(Context context, int subId) {
        ComponentName broadcastPackage = getBroadcastPackage(context);
        if (broadcastPackage != null) {
            return broadcastPackage;
        }

        Intent bindIntent = newBindIntent(context);

        TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
        List<String> packages = new ArrayList<>();
        packages.add(telecomManager.getDefaultDialerPackage());
        PersistableBundle carrierConfig = context
                .getSystemService(CarrierConfigManager.class).getConfigForSubId(subId);
        packages.add(
                carrierConfig.getString(CarrierConfigManager.KEY_CARRIER_VVM_PACKAGE_NAME_STRING));
        String[] vvmPackages = carrierConfig
                .getStringArray(CarrierConfigManager.KEY_CARRIER_VVM_PACKAGE_NAME_STRING_ARRAY);
        if (vvmPackages != null && vvmPackages.length > 0) {
            for (String packageName : vvmPackages) {
                packages.add(packageName);
            }
        }
        packages.add(context.getResources().getString(R.string.system_visual_voicemail_client));
        packages.add(telecomManager.getSystemDialerPackage());
        for (String packageName : packages) {
            if (TextUtils.isEmpty(packageName)) {
                continue;
            }
            bindIntent.setPackage(packageName);
            ResolveInfo info = context.getPackageManager()
                    .resolveService(bindIntent, PackageManager.MATCH_ALL);
            if (info == null) {
                continue;
            }
            if (info.serviceInfo == null) {
                VvmLog.w(TAG,
                        "Component " + info.getComponentInfo() + " is not a service, ignoring");
                continue;
            }
            if (!android.Manifest.permission.BIND_VISUAL_VOICEMAIL_SERVICE
                    .equals(info.serviceInfo.permission)) {
                VvmLog.w(TAG, "package " + info.serviceInfo.packageName
                        + " does not enforce BIND_VISUAL_VOICEMAIL_SERVICE, ignoring");
                continue;
            }

            return info.getComponentInfo().getComponentName();

        }
        return null;
    }

    @Nullable
    private static ComponentName getBroadcastPackage(Context context) {
        Intent broadcastIntent = new Intent(ACTION_VISUAL_VOICEMAIL_SERVICE_EVENT);
        broadcastIntent.setPackage(
                context.getSystemService(TelecomManager.class).getDefaultDialerPackage());
        List<ResolveInfo> info = context.getPackageManager()
                .queryBroadcastReceivers(broadcastIntent, PackageManager.MATCH_ALL);
        if (info == null) {
            return null;
        }
        if (info.isEmpty()) {
            return null;
        }
        return info.get(0).getComponentInfo().getComponentName();
    }

    @Override
    public void onCreate() {
        Assert.isMainThread();
        mMessenger = new Messenger(new Handler() {
            @Override
            public void handleMessage(Message msg) {
                Assert.isMainThread();
                switch (msg.what) {
                    case VisualVoicemailService.MSG_TASK_ENDED:
                        mTaskReferenceCount--;
                        checkReference();
                        break;
                    default:
                        VvmLog.wtf(TAG, "unexpected message " + msg.what);
                }
            }
        });
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        Assert.isMainThread();
        mTaskReferenceCount++;

        PhoneAccountHandle phoneAccountHandle = intent.getExtras()
                .getParcelable(VisualVoicemailService.DATA_PHONE_ACCOUNT_HANDLE);
        int subId = PhoneAccountHandleConverter.toSubId(phoneAccountHandle);
        ComponentName remotePackage = getRemotePackage(this, subId);
        if (remotePackage == null) {
            VvmLog.i(TAG, "No service to handle " + intent.getAction() + ", ignoring");
            checkReference();
            return START_NOT_STICKY;
        }

        switch (intent.getAction()) {
            case ACTION_START_CELL_SERVICE_CONNECTED:
                send(remotePackage, VisualVoicemailService.MSG_ON_CELL_SERVICE_CONNECTED,
                        intent.getExtras());
                break;
            case ACTION_START_SMS_RECEIVED:
                send(remotePackage, VisualVoicemailService.MSG_ON_SMS_RECEIVED, intent.getExtras());
                break;
            case ACTION_START_SIM_REMOVED:
                send(remotePackage, VisualVoicemailService.MSG_ON_SIM_REMOVED, intent.getExtras());
                break;
            default:
                Assert.fail("Unexpected action +" + intent.getAction());
                break;
        }
        // Don't rerun service if processed is killed.
        return START_NOT_STICKY;
    }

    @Override
    @Nullable
    public IBinder onBind(Intent intent) {
        return null;
    }

    private int getTaskId() {
        // TODO(twyen): generate unique IDs. Reference counting is used now so it doesn't matter.
        return 1;
    }

    /**
     * Class for interacting with the main interface of the service.
     */
    private class RemoteServiceConnection implements ServiceConnection {

        private final Queue<Message> mTaskQueue = new LinkedList<>();

        private boolean mConnected;

        /**
         * A handler in the VisualVoicemailService
         */
        private Messenger mRemoteMessenger;

        public void enqueue(Message message) {
            mTaskQueue.add(message);
            if (mConnected) {
                runQueue();
            }
        }

        public boolean isConnected() {
            return mConnected;
        }

        public void onServiceConnected(ComponentName className,
                IBinder service) {
            mRemoteMessenger = new Messenger(service);
            mConnected = true;
            runQueue();
        }

        public void onServiceDisconnected(ComponentName className) {
            mConnection = null;
            mConnected = false;
            mRemoteMessenger = null;
            VvmLog.e(TAG, "Service disconnected, " + mTaskReferenceCount + " tasks dropped.");
            mTaskReferenceCount = 0;
            checkReference();
        }

        private void runQueue() {
            Assert.isMainThread();
            Message message = mTaskQueue.poll();
            while (message != null) {
                message.replyTo = mMessenger;
                message.arg1 = getTaskId();

                try {
                    mRemoteMessenger.send(message);
                } catch (RemoteException e) {
                    VvmLog.e(TAG, "Error sending message to remote service", e);
                }
                message = mTaskQueue.poll();
            }
        }
    }

    private void send(ComponentName remotePackage, int what, Bundle extras) {
        Assert.isMainThread();

        if (getBroadcastPackage(this) != null) {
            /*
             * Temporarily use a broadcast to notify dialer VVM events instead of using the
             * VisualVoicemailService.
             * b/35766990 The VisualVoicemailService is undergoing API changes. The dialer is in
             * a different repository so it can not be updated in sync with android SDK. It is also
             * hard to make a manifest service to work in the intermittent state.
             */
            VvmLog.i(TAG, "sending broadcast " + what + " to " + remotePackage);
            Intent intent = new Intent(ACTION_VISUAL_VOICEMAIL_SERVICE_EVENT);
            intent.putExtras(extras);
            intent.putExtra(EXTRA_WHAT, what);
            intent.setComponent(remotePackage);
            sendBroadcast(intent);
            return;
        }

        Message message = Message.obtain();
        message.what = what;
        message.setData(new Bundle(extras));
        if (mConnection == null) {
            mConnection = new RemoteServiceConnection();
        }
        mConnection.enqueue(message);

        if (!mConnection.isConnected()) {
            Intent intent = newBindIntent(this);
            intent.setComponent(remotePackage);
            VvmLog.i(TAG, "Binding to " + intent.getComponent());
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private void checkReference() {
        if (mConnection == null) {
            return;
        }
        if (mTaskReferenceCount == 0) {
            unbindService(mConnection);
            mConnection = null;
        }
    }

    private static Intent newBindIntent(Context context) {
        Intent intent = new Intent();
        intent.setAction(VisualVoicemailService.SERVICE_INTERFACE);
        return intent;
    }
}
