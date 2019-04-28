/*
* Copyright (C) 2014 Samsung System LSI
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
package com.android.bluetooth.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.util.Log;

import com.android.bluetooth.mapapi.BluetoothMapContract;

/**
 * Class to construct content observers for for email applications on the system.
 *
 *
 */

public class BluetoothMapAppObserver{

    private static final String TAG = "BluetoothMapAppObserver";

    private static final boolean D = BluetoothMapService.DEBUG;
    private static final boolean V = BluetoothMapService.VERBOSE;
    /*  */
    private LinkedHashMap<BluetoothMapAccountItem, ArrayList<BluetoothMapAccountItem>> mFullList;
    private LinkedHashMap<String,ContentObserver> mObserverMap =
            new LinkedHashMap<String,ContentObserver>();
    private ContentResolver mResolver;
    private Context mContext;
    private BroadcastReceiver mReceiver;
    private PackageManager mPackageManager = null;
    BluetoothMapAccountLoader mLoader;
    BluetoothMapService mMapService = null;
    private boolean mRegisteredReceiver = false;

    public BluetoothMapAppObserver(final Context context, BluetoothMapService mapService) {
        mContext    = context;
        mMapService = mapService;
        mResolver   = context.getContentResolver();
        mLoader     = new BluetoothMapAccountLoader(mContext);
        mFullList   = mLoader.parsePackages(false); /* Get the current list of apps */
        createReceiver();
        initObservers();
    }


    private BluetoothMapAccountItem getApp(String authoritiesName) {
        if(V) Log.d(TAG, "getApp(): Looking for " + authoritiesName);
        for(BluetoothMapAccountItem app:mFullList.keySet()){
            if(V) Log.d(TAG, "  Comparing: " + app.getProviderAuthority());
            if(app.getProviderAuthority().equals(authoritiesName)) {
                if(V) Log.d(TAG, "  found " + app.mBase_uri_no_account);
                return app;
            }
        }
        if(V) Log.d(TAG, "  NOT FOUND!");
        return null;
    }

    private void handleAccountChanges(String packageNameWithProvider) {

        if(D)Log.d(TAG,"handleAccountChanges (packageNameWithProvider: "
                        +packageNameWithProvider+"\n");
        //String packageName = packageNameWithProvider.replaceFirst("\\.[^\\.]+$", "");
        BluetoothMapAccountItem app = getApp(packageNameWithProvider);
        if(app != null) {
            ArrayList<BluetoothMapAccountItem> newAccountList = mLoader.parseAccounts(app);
            ArrayList<BluetoothMapAccountItem> oldAccountList = mFullList.get(app);
            ArrayList<BluetoothMapAccountItem> addedAccountList =
                    (ArrayList<BluetoothMapAccountItem>)newAccountList.clone();
            // Same as oldAccountList.clone
            ArrayList<BluetoothMapAccountItem> removedAccountList = mFullList.get(app);
            if (oldAccountList == null)
                oldAccountList = new ArrayList <BluetoothMapAccountItem>();
            if (removedAccountList == null)
                removedAccountList = new ArrayList <BluetoothMapAccountItem>();

            mFullList.put(app, newAccountList);
            for(BluetoothMapAccountItem newAcc: newAccountList){
                for(BluetoothMapAccountItem oldAcc: oldAccountList){
                    if(newAcc.getId() == oldAcc.getId()){
                        // For each match remove from both removed and added lists
                        removedAccountList.remove(oldAcc);
                        addedAccountList.remove(newAcc);
                        if(!newAcc.getName().equals(oldAcc.getName()) && newAcc.mIsChecked){
                            // Name Changed and the acc is visible - Change Name in SDP record
                            mMapService.updateMasInstances(
                                    BluetoothMapService.UPDATE_MAS_INSTANCES_ACCOUNT_RENAMED);
                            if(V)Log.d(TAG, "    UPDATE_MAS_INSTANCES_ACCOUNT_RENAMED");
                        }
                        if(newAcc.mIsChecked != oldAcc.mIsChecked) {
                            // Visibility changed
                            if(newAcc.mIsChecked){
                                // account added - create SDP record
                                mMapService.updateMasInstances(
                                        BluetoothMapService.UPDATE_MAS_INSTANCES_ACCOUNT_ADDED);
                                if(V)Log.d(TAG, "UPDATE_MAS_INSTANCES_ACCOUNT_ADDED " +
                                        "isChecked changed");
                            } else {
                                // account removed - remove SDP record
                                mMapService.updateMasInstances(
                                        BluetoothMapService.UPDATE_MAS_INSTANCES_ACCOUNT_REMOVED);
                                if(V)Log.d(TAG, "    UPDATE_MAS_INSTANCES_ACCOUNT_REMOVED " +
                                        "isChecked changed");
                            }
                        }
                        break;
                    }
                }
            }
            // Notify on any removed accounts
            for(BluetoothMapAccountItem removedAcc: removedAccountList){
                mMapService.updateMasInstances(
                        BluetoothMapService.UPDATE_MAS_INSTANCES_ACCOUNT_REMOVED);
                if(V)Log.d(TAG, "    UPDATE_MAS_INSTANCES_ACCOUNT_REMOVED " + removedAcc);
            }
            // Notify on any new accounts
            for(BluetoothMapAccountItem addedAcc: addedAccountList){
                mMapService.updateMasInstances(
                        BluetoothMapService.UPDATE_MAS_INSTANCES_ACCOUNT_ADDED);
                if(V)Log.d(TAG, "    UPDATE_MAS_INSTANCES_ACCOUNT_ADDED " + addedAcc);
            }

        } else {
            Log.e(TAG, "Received change notification on package not registered for notifications!");

        }
    }

    /**
     * Adds a new content observer to the list of content observers.
     * The key for the observer is the uri as string
     * @param uri uri for the package that supports MAP email
     */

    public void registerObserver(BluetoothMapAccountItem app) {
        Uri uri = BluetoothMapContract.buildAccountUri(app.getProviderAuthority());
        if (V) Log.d(TAG, "registerObserver for URI "+uri.toString()+"\n");
        ContentObserver observer = new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                onChange(selfChange, null);
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                if (V) Log.d(TAG, "onChange on thread: " + Thread.currentThread().getId()
                        + " Uri: " + uri + " selfchange: " + selfChange);
                if(uri != null) {
                    handleAccountChanges(uri.getHost());
                } else {
                    Log.e(TAG, "Unable to handle change as the URI is NULL!");
                }

            }
        };
        mObserverMap.put(uri.toString(), observer);
        //False "notifyForDescendents" : Get notified whenever a change occurs to the exact URI.
        mResolver.registerContentObserver(uri, false, observer);
    }

    public void unregisterObserver(BluetoothMapAccountItem app) {
        Uri uri = BluetoothMapContract.buildAccountUri(app.getProviderAuthority());
        if (V) Log.d(TAG, "unregisterObserver("+uri.toString()+")\n");
        mResolver.unregisterContentObserver(mObserverMap.get(uri.toString()));
        mObserverMap.remove(uri.toString());
    }

    private void initObservers(){
        if(D)Log.d(TAG,"initObservers()");
        for(BluetoothMapAccountItem app: mFullList.keySet()){
            registerObserver(app);
        }
    }

    private void deinitObservers(){
        if(D)Log.d(TAG,"deinitObservers()");
        for(BluetoothMapAccountItem app: mFullList.keySet()){
            unregisterObserver(app);
        }
    }

    private void createReceiver(){
        if(D)Log.d(TAG,"createReceiver()\n");
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addDataScheme("package");
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(D)Log.d(TAG,"onReceive\n");
                String action = intent.getAction();

                if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                    Uri data = intent.getData();
                    String packageName = data.getEncodedSchemeSpecificPart();
                    if(D)Log.d(TAG,"The installed package is: "+ packageName);

                    BluetoothMapUtils.TYPE msgType = BluetoothMapUtils.TYPE.NONE;
                    ResolveInfo resolveInfo = null;
                    Intent[] searchIntents = new Intent[2];
                    //Array <Intent> searchIntents = new Array <Intent>();
                    searchIntents[0] = new Intent(BluetoothMapContract.PROVIDER_INTERFACE_EMAIL);
                    searchIntents[1] = new Intent(BluetoothMapContract.PROVIDER_INTERFACE_IM);
                    // Find all installed packages and filter out those that support Bluetooth Map.

                    mPackageManager = mContext.getPackageManager();

                    for (Intent searchIntent : searchIntents) {
                        List<ResolveInfo> resInfos =
                                mPackageManager.queryIntentContentProviders(searchIntent, 0);
                        if (resInfos != null ) {
                            if(D) Log.d(TAG,"Found " + resInfos.size()
                                    + " application(s) with intent "
                                    + searchIntent.getAction().toString());
                            for (ResolveInfo rInfo : resInfos) {
                                if(rInfo != null) {
                                    // Find out if package contain Bluetooth MAP support
                                    if (packageName.equals(rInfo.providerInfo.packageName)) {
                                        resolveInfo = rInfo;
                                        if(searchIntent.getAction() ==
                                                BluetoothMapContract.PROVIDER_INTERFACE_EMAIL){
                                            msgType = BluetoothMapUtils.TYPE.EMAIL;
                                        } else if (searchIntent.getAction() ==
                                                BluetoothMapContract.PROVIDER_INTERFACE_IM){
                                            msgType = BluetoothMapUtils.TYPE.IM;
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    // if application found with Bluetooth MAP support add to list
                    if(resolveInfo != null) {
                        if(D) Log.d(TAG,"Found " + resolveInfo.providerInfo.packageName
                                + " application of type " + msgType);
                        BluetoothMapAccountItem app = mLoader.createAppItem(resolveInfo,
                                false, msgType);
                        if(app != null) {
                            registerObserver(app);
                            // Add all accounts to mFullList
                            ArrayList<BluetoothMapAccountItem> newAccountList =
                                    mLoader.parseAccounts(app);
                            mFullList.put(app, newAccountList);
                        }
                    }

                }
                else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                    Uri data = intent.getData();
                    String packageName = data.getEncodedSchemeSpecificPart();
                    if(D)Log.d(TAG,"The removed package is: "+ packageName);
                    BluetoothMapAccountItem app = getApp(packageName);
                    /* Find the object and remove from fullList */
                    if(app != null) {
                        unregisterObserver(app);
                        mFullList.remove(app);
                    }
                }
            }
        };
        if (!mRegisteredReceiver) {
            try {
                mContext.registerReceiver(mReceiver,intentFilter);
                mRegisteredReceiver = true;
            } catch (Exception e) {
                Log.e(TAG,"Unable to register MapAppObserver receiver", e);
            }
        }
    }

    private void removeReceiver(){
        if(D)Log.d(TAG,"removeReceiver()\n");
        if (mRegisteredReceiver) {
            try {
                mRegisteredReceiver = false;
                mContext.unregisterReceiver(mReceiver);
            } catch (Exception e) {
                Log.e(TAG,"Unable to unregister mapAppObserver receiver", e);
            }
        }
    }

    /**
     * Method to get a list of the accounts (across all apps) that are set to be shared
     * through MAP.
     * @return Arraylist<BluetoothMapAccountItem> containing all enabled accounts
     */
    public ArrayList<BluetoothMapAccountItem> getEnabledAccountItems(){
        if(D)Log.d(TAG,"getEnabledAccountItems()\n");
        ArrayList<BluetoothMapAccountItem> list = new ArrayList<BluetoothMapAccountItem>();
        for (BluetoothMapAccountItem app:mFullList.keySet()){
            if (app != null) {
                ArrayList<BluetoothMapAccountItem> accountList = mFullList.get(app);
                if (accountList != null) {
                    for (BluetoothMapAccountItem acc: accountList) {
                        if (acc.mIsChecked) {
                            list.add(acc);
                        }
                    }
                } else {
                    Log.w(TAG,"getEnabledAccountItems() - No AccountList enabled\n");
                }
            } else {
                Log.w(TAG,"getEnabledAccountItems() - No Account in App enabled\n");
            }
        }
        return list;
    }

    /**
     * Method to get a list of the accounts (across all apps).
     * @return Arraylist<BluetoothMapAccountItem> containing all accounts
     */
    public ArrayList<BluetoothMapAccountItem> getAllAccountItems(){
        if(D)Log.d(TAG,"getAllAccountItems()\n");
        ArrayList<BluetoothMapAccountItem> list = new ArrayList<BluetoothMapAccountItem>();
        for(BluetoothMapAccountItem app:mFullList.keySet()){
            ArrayList<BluetoothMapAccountItem> accountList = mFullList.get(app);
            list.addAll(accountList);
        }
        return list;
    }


    /**
     * Cleanup all resources - must be called to avoid leaks.
     */
    public void shutdown() {
        deinitObservers();
        removeReceiver();
    }
}
