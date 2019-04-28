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

package com.android.server.telecom.tests;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import com.android.internal.telecom.IConnectionService;
import com.android.internal.telecom.IInCallService;

import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import android.app.AppOpsManager;
import android.app.NotificationManager;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.IContentProvider;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.location.Country;
import android.location.CountryDetector;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IInterface;
import android.os.UserHandle;
import android.os.UserManager;
import android.telecom.CallAudioState;
import android.telecom.ConnectionService;
import android.telecom.Log;
import android.telecom.InCallService;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.test.mock.MockContext;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Controls a test {@link Context} as would be provided by the Android framework to an
 * {@code Activity}, {@code Service} or other system-instantiated component.
 *
 * The {@link Context} created by this object is "hollow" but its {@code applicationContext}
 * property points to an application context implementing all the nontrivial functionality.
 */
public class ComponentContextFixture implements TestFixture<Context> {

    public class FakeApplicationContext extends MockContext {
        @Override
        public PackageManager getPackageManager() {
            return mPackageManager;
        }

        @Override
        public String getPackageName() {
            return "com.android.server.telecom.tests";
        }

        @Override
        public String getPackageResourcePath() {
            return "/tmp/i/dont/know";
        }

        @Override
        public Context getApplicationContext() {
            return mApplicationContextSpy;
        }

        @Override
        public File getFilesDir() {
            try {
                return File.createTempFile("temp", "temp").getParentFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public boolean bindServiceAsUser(
                Intent serviceIntent,
                ServiceConnection connection,
                int flags,
                UserHandle userHandle) {
            // TODO: Implement "as user" functionality
            return bindService(serviceIntent, connection, flags);
        }

        @Override
        public boolean bindService(
                Intent serviceIntent,
                ServiceConnection connection,
                int flags) {
            if (mServiceByServiceConnection.containsKey(connection)) {
                throw new RuntimeException("ServiceConnection already bound: " + connection);
            }
            IInterface service = mServiceByComponentName.get(serviceIntent.getComponent());
            if (service == null) {
                throw new RuntimeException("ServiceConnection not found: "
                        + serviceIntent.getComponent());
            }
            mServiceByServiceConnection.put(connection, service);
            connection.onServiceConnected(serviceIntent.getComponent(), service.asBinder());
            return true;
        }

        @Override
        public void unbindService(
                ServiceConnection connection) {
            IInterface service = mServiceByServiceConnection.remove(connection);
            if (service == null) {
                throw new RuntimeException("ServiceConnection not found: " + connection);
            }
            connection.onServiceDisconnected(mComponentNameByService.get(service));
        }

        @Override
        public Object getSystemService(String name) {
            switch (name) {
                case Context.AUDIO_SERVICE:
                    return mAudioManager;
                case Context.TELEPHONY_SERVICE:
                    return mTelephonyManager;
                case Context.APP_OPS_SERVICE:
                    return mAppOpsManager;
                case Context.NOTIFICATION_SERVICE:
                    return mNotificationManager;
                case Context.STATUS_BAR_SERVICE:
                    return mStatusBarManager;
                case Context.USER_SERVICE:
                    return mUserManager;
                case Context.TELEPHONY_SUBSCRIPTION_SERVICE:
                    return mSubscriptionManager;
                case Context.TELECOM_SERVICE:
                    return mTelecomManager;
                case Context.CARRIER_CONFIG_SERVICE:
                    return mCarrierConfigManager;
                case Context.COUNTRY_DETECTOR:
                    return mCountryDetector;
                default:
                    return null;
            }
        }

        @Override
        public String getSystemServiceName(Class<?> svcClass) {
            if (svcClass == UserManager.class) {
                return Context.USER_SERVICE;
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public int getUserId() {
            return 0;
        }

        @Override
        public Resources getResources() {
            return mResources;
        }

        @Override
        public String getOpPackageName() {
            return "com.android.server.telecom.tests";
        }

        @Override
        public ApplicationInfo getApplicationInfo() {
            return mTestApplicationInfo;
        }

        @Override
        public ContentResolver getContentResolver() {
            return new ContentResolver(mApplicationContextSpy) {
                @Override
                protected IContentProvider acquireProvider(Context c, String name) {
                    Log.i(this, "acquireProvider %s", name);
                    return getOrCreateProvider(name);
                }

                @Override
                public boolean releaseProvider(IContentProvider icp) {
                    return true;
                }

                @Override
                protected IContentProvider acquireUnstableProvider(Context c, String name) {
                    Log.i(this, "acquireUnstableProvider %s", name);
                    return getOrCreateProvider(name);
                }

                private IContentProvider getOrCreateProvider(String name) {
                    if (!mIContentProviderByUri.containsKey(name)) {
                        mIContentProviderByUri.put(name, mock(IContentProvider.class));
                    }
                    return mIContentProviderByUri.get(name);
                }

                @Override
                public boolean releaseUnstableProvider(IContentProvider icp) {
                    return false;
                }

                @Override
                public void unstableProviderDied(IContentProvider icp) {
                }
            };
        }

        @Override
        public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
            // TODO -- this is called by WiredHeadsetManager!!!
            return null;
        }

        @Override
        public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter,
                String broadcastPermission, Handler scheduler) {
            return null;
        }

        @Override
        public Intent registerReceiverAsUser(BroadcastReceiver receiver, UserHandle handle,
                IntentFilter filter, String broadcastPermission, Handler scheduler) {
            return null;
        }

        @Override
        public void sendBroadcast(Intent intent) {
            // TODO -- need to ensure this is captured
        }

        @Override
        public void sendBroadcast(Intent intent, String receiverPermission) {
            // TODO -- need to ensure this is captured
        }

        @Override
        public void sendBroadcastAsUser(Intent intent, UserHandle userHandle) {
            // TODO -- need to ensure this is captured
        }

        @Override
        public void sendOrderedBroadcastAsUser(Intent intent, UserHandle user,
                String receiverPermission, BroadcastReceiver resultReceiver, Handler scheduler,
                int initialCode, String initialData, Bundle initialExtras) {
            // TODO -- need to ensure this is captured
        }

        @Override
        public void sendOrderedBroadcastAsUser(Intent intent, UserHandle user,
                String receiverPermission, int appOp, BroadcastReceiver resultReceiver,
                Handler scheduler, int initialCode, String initialData, Bundle initialExtras) {
        }

        @Override
        public Context createPackageContextAsUser(String packageName, int flags, UserHandle user)
                throws PackageManager.NameNotFoundException {
            return this;
        }

        @Override
        public int checkCallingOrSelfPermission(String permission) {
            return PackageManager.PERMISSION_GRANTED;
        }

        @Override
        public void enforceCallingOrSelfPermission(String permission, String message) {
            // Don't bother enforcing anything in mock.
        }

        @Override
        public void enforcePermission(
                String permission, int pid, int uid, String message) {
            // By default, don't enforce anything in mock.
        }

        @Override
        public void startActivityAsUser(Intent intent, UserHandle userHandle) {
            // For capturing
        }
    }

    public class FakeAudioManager extends AudioManager {

        private boolean mMute = false;
        private boolean mSpeakerphoneOn = false;
        private int mAudioStreamValue = 1;
        private int mMode = AudioManager.MODE_NORMAL;
        private int mRingerMode = AudioManager.RINGER_MODE_NORMAL;

        public FakeAudioManager(Context context) {
            super(context);
        }

        @Override
        public void setMicrophoneMute(boolean value) {
            mMute = value;
        }

        @Override
        public boolean isMicrophoneMute() {
            return mMute;
        }

        @Override
        public void setSpeakerphoneOn(boolean value) {
            mSpeakerphoneOn = value;
        }

        @Override
        public boolean isSpeakerphoneOn() {
            return mSpeakerphoneOn;
        }

        @Override
        public void setMode(int mode) {
            mMode = mode;
        }

        @Override
        public int getMode() {
            return mMode;
        }

        @Override
        public void setRingerModeInternal(int ringerMode) {
            mRingerMode = ringerMode;
        }

        @Override
        public int getRingerModeInternal() {
            return mRingerMode;
        }

        @Override
        public void setStreamVolume(int streamTypeUnused, int index, int flagsUnused){
            mAudioStreamValue = index;
        }

        @Override
        public int getStreamVolume(int streamValueUnused) {
            return mAudioStreamValue;
        }
    }

    private final Multimap<String, ComponentName> mComponentNamesByAction =
            ArrayListMultimap.create();
    private final Map<ComponentName, IInterface> mServiceByComponentName = new HashMap<>();
    private final Map<ComponentName, ServiceInfo> mServiceInfoByComponentName = new HashMap<>();
    private final Map<IInterface, ComponentName> mComponentNameByService = new HashMap<>();
    private final Map<ServiceConnection, IInterface> mServiceByServiceConnection = new HashMap<>();

    private final Context mContext = new MockContext() {
        @Override
        public Context getApplicationContext() {
            return mApplicationContextSpy;
        }

        @Override
        public Resources getResources() {
            return mResources;
        }
    };

    // The application context is the most important object this class provides to the system
    // under test.
    private final Context mApplicationContext = new FakeApplicationContext();

    // We then create a spy on the application context allowing standard Mockito-style
    // when(...) logic to be used to add specific little responses where needed.

    private final Resources mResources = mock(Resources.class);
    private final Context mApplicationContextSpy = spy(mApplicationContext);
    private final PackageManager mPackageManager = mock(PackageManager.class);
    private final AudioManager mAudioManager = spy(new FakeAudioManager(mContext));
    private final TelephonyManager mTelephonyManager = mock(TelephonyManager.class);
    private final AppOpsManager mAppOpsManager = mock(AppOpsManager.class);
    private final NotificationManager mNotificationManager = mock(NotificationManager.class);
    private final UserManager mUserManager = mock(UserManager.class);
    private final StatusBarManager mStatusBarManager = mock(StatusBarManager.class);
    private final SubscriptionManager mSubscriptionManager = mock(SubscriptionManager.class);
    private final CarrierConfigManager mCarrierConfigManager = mock(CarrierConfigManager.class);
    private final CountryDetector mCountryDetector = mock(CountryDetector.class);
    private final Map<String, IContentProvider> mIContentProviderByUri = new HashMap<>();
    private final Configuration mResourceConfiguration = new Configuration();
    private final ApplicationInfo mTestApplicationInfo = new ApplicationInfo();

    private TelecomManager mTelecomManager = null;

    public ComponentContextFixture() {
        MockitoAnnotations.initMocks(this);
        when(mResources.getConfiguration()).thenReturn(mResourceConfiguration);
        mResourceConfiguration.setLocale(Locale.TAIWAN);

        // TODO: Move into actual tests
        doReturn(false).when(mAudioManager).isWiredHeadsetOn();

        doAnswer(new Answer<List<ResolveInfo>>() {
            @Override
            public List<ResolveInfo> answer(InvocationOnMock invocation) throws Throwable {
                return doQueryIntentServices(
                        (Intent) invocation.getArguments()[0],
                        (Integer) invocation.getArguments()[1]);
            }
        }).when(mPackageManager).queryIntentServices((Intent) any(), anyInt());

        doAnswer(new Answer<List<ResolveInfo>>() {
            @Override
            public List<ResolveInfo> answer(InvocationOnMock invocation) throws Throwable {
                return doQueryIntentServices(
                        (Intent) invocation.getArguments()[0],
                        (Integer) invocation.getArguments()[1]);
            }
        }).when(mPackageManager).queryIntentServicesAsUser((Intent) any(), anyInt(), anyInt());

        when(mTelephonyManager.getSubIdForPhoneAccount((PhoneAccount) any())).thenReturn(1);

        when(mTelephonyManager.getNetworkOperatorName()).thenReturn("label1");

        doAnswer(new Answer<Void>(){
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                return null;
            }
        }).when(mAppOpsManager).checkPackage(anyInt(), anyString());

        when(mNotificationManager.matchesCallFilter(any(Bundle.class))).thenReturn(true);

        when(mUserManager.getSerialNumberForUser(any(UserHandle.class))).thenReturn(-1L);

        doReturn(null).when(mApplicationContextSpy).registerReceiver(any(BroadcastReceiver.class),
                any(IntentFilter.class));
    }

    @Override
    public Context getTestDouble() {
        return mContext;
    }

    public void addConnectionService(
            ComponentName componentName,
            IConnectionService service)
            throws Exception {
        addService(ConnectionService.SERVICE_INTERFACE, componentName, service);
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.permission = android.Manifest.permission.BIND_CONNECTION_SERVICE;
        serviceInfo.packageName = componentName.getPackageName();
        serviceInfo.name = componentName.getClassName();
        mServiceInfoByComponentName.put(componentName, serviceInfo);
    }

    public void addInCallService(
            ComponentName componentName,
            IInCallService service)
            throws Exception {
        addService(InCallService.SERVICE_INTERFACE, componentName, service);
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.permission = android.Manifest.permission.BIND_INCALL_SERVICE;
        serviceInfo.packageName = componentName.getPackageName();
        serviceInfo.name = componentName.getClassName();
        mServiceInfoByComponentName.put(componentName, serviceInfo);
    }

    public void putResource(int id, final String value) {
        when(mResources.getText(eq(id))).thenReturn(value);
        when(mResources.getString(eq(id))).thenReturn(value);
        when(mResources.getString(eq(id), any())).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                return String.format(value, Arrays.copyOfRange(args, 1, args.length));
            }
        });
    }

    public void putBooleanResource(int id, boolean value) {
        when(mResources.getBoolean(eq(id))).thenReturn(value);
    }

    public void setTelecomManager(TelecomManager telecomManager) {
        mTelecomManager = telecomManager;
    }

    private void addService(String action, ComponentName name, IInterface service) {
        mComponentNamesByAction.put(action, name);
        mServiceByComponentName.put(name, service);
        mComponentNameByService.put(service, name);
    }

    private List<ResolveInfo> doQueryIntentServices(Intent intent, int flags) {
        List<ResolveInfo> result = new ArrayList<>();
        for (ComponentName componentName : mComponentNamesByAction.get(intent.getAction())) {
            ResolveInfo resolveInfo = new ResolveInfo();
            resolveInfo.serviceInfo = mServiceInfoByComponentName.get(componentName);
            resolveInfo.serviceInfo.metaData = new Bundle();
            resolveInfo.serviceInfo.metaData.putBoolean(
                    TelecomManager.METADATA_INCLUDE_EXTERNAL_CALLS, true);
            result.add(resolveInfo);
        }
        return result;
    }
}
