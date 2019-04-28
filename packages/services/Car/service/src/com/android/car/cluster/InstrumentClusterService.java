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
package com.android.car.cluster;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.car.Car;
import android.car.CarAppFocusManager;
import android.car.cluster.CarInstrumentClusterManager;
import android.car.cluster.IInstrumentClusterManagerCallback;
import android.car.cluster.IInstrumentClusterManagerService;
import android.car.cluster.renderer.IInstrumentCluster;
import android.car.cluster.renderer.IInstrumentClusterCallback;
import android.car.cluster.renderer.IInstrumentClusterNavigation;
import android.car.cluster.renderer.InstrumentClusterRenderingService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.Process;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;

import com.android.car.AppFocusService;
import com.android.car.AppFocusService.FocusOwnershipCallback;
import com.android.car.CarInputService;
import com.android.car.CarInputService.KeyEventListener;
import com.android.car.CarLog;
import com.android.car.CarServiceBase;
import com.android.car.R;
import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service responsible for interaction with car's instrument cluster.
 *
 * @hide
 */
@SystemApi
public class InstrumentClusterService implements CarServiceBase,
        FocusOwnershipCallback, KeyEventListener {

    private static final String TAG = CarLog.TAG_CLUSTER;
    private static final Boolean DBG = false;

    private final Context mContext;

    private final AppFocusService mAppFocusService;
    private final CarInputService mCarInputService;
    private final PackageManager mPackageManager;
    private final Object mSync = new Object();

    private final ClusterServiceCallback mClusterCallback = new ClusterServiceCallback();
    private final ClusterManagerService mClusterManagerService = new ClusterManagerService();

    @GuardedBy("mSync")
    private ContextOwner mNavContextOwner;
    @GuardedBy("mSync")
    private IInstrumentCluster mRendererService;
    @GuardedBy("mSync")
    private final HashMap<String, ClusterActivityInfo> mActivityInfoByCategory = new HashMap<>();
    @GuardedBy("mSync")
    private final HashMap<IBinder, ManagerCallbackInfo> mManagerCallbacks = new HashMap<>();

    private boolean mRendererBound = false;

    private final ServiceConnection mRendererServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (DBG) {
                Log.d(TAG, "onServiceConnected, name: " + name + ", binder: " + binder);
            }
            IInstrumentCluster service = IInstrumentCluster.Stub.asInterface(binder);
            ContextOwner navContextOwner;
            synchronized (mSync) {
                mRendererService = service;
                navContextOwner = mNavContextOwner;
            }
            if (navContextOwner !=  null && service != null) {
                notifyNavContextOwnerChanged(service, navContextOwner.uid, navContextOwner.pid);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected, name: " + name);
            synchronized (mSync) {
                mRendererService = null;
            }
            // Try to rebind with instrument cluster.
            mRendererBound = bindInstrumentClusterRendererService();
        }
    };

    public InstrumentClusterService(Context context, AppFocusService appFocusService,
            CarInputService carInputService) {
        mContext = context;
        mAppFocusService = appFocusService;
        mCarInputService = carInputService;
        mPackageManager = mContext.getPackageManager();
    }

    @Override
    public void init() {
        if (DBG) {
            Log.d(TAG, "init");
        }

        mAppFocusService.registerContextOwnerChangedCallback(this /* FocusOwnershipCallback */);
        mCarInputService.setInstrumentClusterKeyListener(this /* KeyEventListener */);
        mRendererBound = bindInstrumentClusterRendererService();
    }

    @Override
    public void release() {
        if (DBG) {
            Log.d(TAG, "release");
        }

        mAppFocusService.unregisterContextOwnerChangedCallback(this);
        if (mRendererBound) {
            mContext.unbindService(mRendererServiceConnection);
            mRendererBound = false;
        }
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("**" + getClass().getSimpleName() + "**");
        writer.println("bound with renderer: " + mRendererBound);
        writer.println("renderer service: " + mRendererService);
    }

    @Override
    public void onFocusAcquired(int appType, int uid, int pid) {
        if (appType != CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION) {
            return;
        }

        IInstrumentCluster service;
        synchronized (mSync) {
            mNavContextOwner = new ContextOwner(uid, pid);
            service = mRendererService;
        }

        if (service != null) {
            notifyNavContextOwnerChanged(service, uid, pid);
        }
    }

    @Override
    public void onFocusAbandoned(int appType, int uid, int pid) {
        if (appType != CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION) {
            return;
        }

        IInstrumentCluster service;
        synchronized (mSync) {
            if (mNavContextOwner == null
                    || mNavContextOwner.uid != uid
                    || mNavContextOwner.pid != pid) {
                return;  // Nothing to do here, no active focus or not owned by this client.
            }

            mNavContextOwner = null;
            service = mRendererService;
        }

        if (service != null) {
            notifyNavContextOwnerChanged(service, 0, 0);
        }
    }

    private static void notifyNavContextOwnerChanged(IInstrumentCluster service, int uid, int pid) {
        try {
            service.setNavigationContextOwner(uid, pid);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to call setNavigationContextOwner", e);
        }
    }

    private boolean bindInstrumentClusterRendererService() {
        String rendererService = mContext.getString(R.string.instrumentClusterRendererService);
        if (TextUtils.isEmpty(rendererService)) {
            Log.i(TAG, "Instrument cluster renderer was not configured");
            return false;
        }

        Log.d(TAG, "bindInstrumentClusterRendererService, component: " + rendererService);

        Intent intent = new Intent();
        intent.setComponent(ComponentName.unflattenFromString(rendererService));
        Bundle extras = new Bundle();
        extras.putBinder(
                InstrumentClusterRenderingService.EXTRA_KEY_CALLBACK_SERVICE,
                mClusterCallback);
        intent.putExtras(extras);
        return mContext.bindService(intent, mRendererServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Nullable
    public IInstrumentClusterNavigation getNavigationService() {
        IInstrumentCluster service;
        synchronized (mSync) {
            service = mRendererService;
        }

        try {
            return service == null ? null : service.getNavigationService();
        } catch (RemoteException e) {
            Log.e(TAG, "getNavigationServiceBinder" , e);
            return null;
        }
    }

    public IInstrumentClusterManagerService.Stub getManagerService() {
        return mClusterManagerService;
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (DBG) {
            Log.d(TAG, "InstrumentClusterService#onKeyEvent: " + event);
        }

        IInstrumentCluster service;
        synchronized (mSync) {
            service = mRendererService;
        }

        if (service != null) {
            try {
                service.onKeyEvent(event);
            } catch (RemoteException e) {
                Log.e(TAG, "onKeyEvent", e);
            }
        }
        return true;
    }

    private static class ContextOwner {
        final int uid;
        final int pid;

        ContextOwner(int uid, int pid) {
            this.uid = uid;
            this.pid = pid;
        }
    }

    private static class ClusterActivityInfo {
        Bundle launchOptions;  // ActivityOptions
        Bundle state;          // ClusterActivityState
    }

    private void enforcePermission(String permission) {
        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();
        if (Binder.getCallingUid() == Process.myUid()) {
            if (mContext.checkCallingOrSelfPermission(permission) != PERMISSION_GRANTED) {
                throw new SecurityException("Permission " + permission + " is not granted to "
                        + "client {uid: " + callingUid + ", pid: " + callingPid + "}");
            }
        }
    }

    private void enforceClusterControlPermission() {
        enforcePermission(Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL);
    }

    private void doStartClusterActivity(Intent intent) {
        enforceClusterControlPermission();

        // Category from given intent should match category from cluster vendor implementation.
        List<ResolveInfo> resolveList = mPackageManager.queryIntentActivities(intent,
                PackageManager.GET_RESOLVED_FILTER);
        if (resolveList == null || resolveList.isEmpty()) {
            Log.w(TAG, "Failed to resolve an intent: " + intent);
            return;
        }

        resolveList = checkPermission(resolveList, Car.PERMISSION_CAR_DISPLAY_IN_CLUSTER);
        if (resolveList.isEmpty()) {
            return;
        }

        // TODO(b/63861009): we may have multiple navigation apps that eligible to be launched in
        // the cluster. We need to resolve intent that may have multiple activity candidates, right
        // now we pickup the first one that matches registered category (resolveList is sorted
        // priority).
        Pair<ResolveInfo, ClusterActivityInfo> attributedResolveInfo =
                findClusterActivityOptions(resolveList);
        if (attributedResolveInfo == null) {
            Log.w(TAG, "Unable to start an activity with intent: " + intent + " in the cluster: "
                    + "category intent didn't match with any categories from vendor "
                    + "implementation");
            return;
        }
        ClusterActivityInfo opts = attributedResolveInfo.second;

        // Intent was already checked for permission and resolved, make it explicit.
        intent.setComponent(attributedResolveInfo.first.getComponentInfo().getComponentName());

        intent.putExtra(CarInstrumentClusterManager.KEY_EXTRA_ACTIVITY_STATE, opts.state);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Virtual display could be private and not available to calling process.
        final long token = Binder.clearCallingIdentity();
        try {
            mContext.startActivity(intent, opts.launchOptions);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private List<ResolveInfo> checkPermission(List<ResolveInfo> resolveList,
            String permission) {
        List<ResolveInfo> permittedResolveList = new ArrayList<>(resolveList.size());
        for (ResolveInfo info : resolveList) {
            String pkgName = info.getComponentInfo().packageName;
            if (mPackageManager.checkPermission(permission, pkgName) == PERMISSION_GRANTED) {
                permittedResolveList.add(info);
            } else {
                Log.w(TAG, "Permission " + permission + " not granted for "
                        + info.getComponentInfo());
            }

        }
        return permittedResolveList;
    }

    private void doRegisterManagerCallback(IInstrumentClusterManagerCallback callback)
            throws RemoteException {
        enforceClusterControlPermission();
        IBinder binder = callback.asBinder();

        List<Pair<String, Bundle>> knownActivityStates = null;
        ManagerCallbackDeathRecipient deathRecipient = new ManagerCallbackDeathRecipient(binder);
        synchronized (mSync) {
            if (mManagerCallbacks.containsKey(binder)) {
                Log.w(TAG, "Manager callback already registered for binder: " + binder);
                return;
            }
            mManagerCallbacks.put(binder, new ManagerCallbackInfo(callback, deathRecipient));
            if (!mActivityInfoByCategory.isEmpty()) {
                knownActivityStates = new ArrayList<>(mActivityInfoByCategory.size());
                for (Map.Entry<String, ClusterActivityInfo> it : mActivityInfoByCategory.entrySet()) {
                    knownActivityStates.add(new Pair<>(it.getKey(), it.getValue().state));
                }
            }
        }
        binder.linkToDeath(deathRecipient, 0);

        // Notify manager immediately with known states.
        if (knownActivityStates != null) {
            for (Pair<String, Bundle> it : knownActivityStates) {
                callback.setClusterActivityState(it.first, it.second);
            }
        }
    }

    private void doUnregisterManagerCallback(IBinder binder) throws RemoteException {
        enforceClusterControlPermission();
        ManagerCallbackInfo info;
        synchronized (mSync) {
            info = mManagerCallbacks.get(binder);
            if (info == null) {
                Log.w(TAG, "Unable to unregister manager callback binder: " + binder + " because "
                        + "it wasn't previously registered.");
                return;
            }
            mManagerCallbacks.remove(binder);
        }
        binder.unlinkToDeath(info.deathRecipient, 0);
    }

    @Nullable
    private Pair<ResolveInfo, ClusterActivityInfo> findClusterActivityOptions(
            List<ResolveInfo> resolveList) {
        synchronized (mSync) {
            Set<String> registeredCategories = mActivityInfoByCategory.keySet();

            for (ResolveInfo resolveInfo : resolveList) {
                for (String category : registeredCategories) {
                    if (resolveInfo.filter != null && resolveInfo.filter.hasCategory(category)) {
                        ClusterActivityInfo categoryInfo = mActivityInfoByCategory.get(category);
                        return new Pair<>(resolveInfo, categoryInfo);
                    }
                }
            }
        }
        return null;
    }

    private class ManagerCallbackDeathRecipient implements DeathRecipient {
        private final IBinder mBinder;

        ManagerCallbackDeathRecipient(IBinder binder) {
            mBinder = binder;
        }

        @Override
        public void binderDied() {
            try {
                doUnregisterManagerCallback(mBinder);
            } catch (RemoteException e) {
                // Ignore, shutdown route.
            }
        }
    }

    private class ClusterManagerService extends IInstrumentClusterManagerService.Stub {

        @Override
        public void startClusterActivity(Intent intent) throws RemoteException {
            doStartClusterActivity(intent);
        }

        @Override
        public void registerCallback(IInstrumentClusterManagerCallback callback)
                throws RemoteException {
            doRegisterManagerCallback(callback);
        }

        @Override
        public void unregisterCallback(IInstrumentClusterManagerCallback callback)
                throws RemoteException {
            doUnregisterManagerCallback(callback.asBinder());
        }
    }

    private ClusterActivityInfo getOrCreateActivityInfoLocked(String category) {
        return mActivityInfoByCategory.computeIfAbsent(category, k -> new ClusterActivityInfo());
    }

    /** This is communication channel from vendor cluster implementation to Car Service. */
    private class ClusterServiceCallback extends IInstrumentClusterCallback.Stub {

        @Override
        public void setClusterActivityLaunchOptions(String category, Bundle activityOptions)
                throws RemoteException {
            doSetActivityLaunchOptions(category, activityOptions);
        }

        @Override
        public void setClusterActivityState(String category, Bundle clusterActivityState)
                throws RemoteException {
            doSetClusterActivityState(category, clusterActivityState);
        }
    }

    /** Called from cluster vendor implementation */
    private void doSetActivityLaunchOptions(String category, Bundle activityOptions) {
        if (DBG) {
            Log.d(TAG, "doSetActivityLaunchOptions, category: " + category
                    + ", options: " + activityOptions);
        }
        synchronized (mSync) {
            ClusterActivityInfo info = getOrCreateActivityInfoLocked(category);
            info.launchOptions = activityOptions;
        }
    }

    /** Called from cluster vendor implementation */
    private void doSetClusterActivityState(String category, Bundle clusterActivityState)
            throws RemoteException {
        if (DBG) {
            Log.d(TAG, "doSetClusterActivityState, category: " + category
                    + ", state: " + clusterActivityState);
        }

        List<ManagerCallbackInfo> managerCallbacks;
        synchronized (mSync) {
            ClusterActivityInfo info = getOrCreateActivityInfoLocked(category);
            info.state = clusterActivityState;
            managerCallbacks = new ArrayList<>(mManagerCallbacks.values());
        }

        for (ManagerCallbackInfo cbInfo : managerCallbacks) {
            cbInfo.callback.setClusterActivityState(category, clusterActivityState);
        }
    }

    private static class ManagerCallbackInfo {
        final IInstrumentClusterManagerCallback callback;
        final ManagerCallbackDeathRecipient deathRecipient;

        ManagerCallbackInfo(IInstrumentClusterManagerCallback callback,
                ManagerCallbackDeathRecipient deathRecipient) {
            this.callback = callback;
            this.deathRecipient = deathRecipient;
        }
    }
}
