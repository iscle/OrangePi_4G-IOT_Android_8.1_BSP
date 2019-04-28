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

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.text.format.DateUtils;
import android.util.Log;

import com.android.bluetooth.map.BluetoothMapAccountItem;
import com.android.bluetooth.map.BluetoothMapUtils.TYPE;
import com.android.bluetooth.mapapi.BluetoothMapContract;

public class BluetoothMapAccountLoader {
    private static final String TAG = "BluetoothMapAccountLoader";
    private static final boolean D = BluetoothMapService.DEBUG;
    private static final boolean V = BluetoothMapService.VERBOSE;
    private Context mContext = null;
    private PackageManager mPackageManager = null;
    private ContentResolver mResolver;
    private int mAccountsEnabledCount = 0;
    private ContentProviderClient mProviderClient = null;
    private static final long PROVIDER_ANR_TIMEOUT = 20 * DateUtils.SECOND_IN_MILLIS;

    public BluetoothMapAccountLoader(Context ctx)
    {
        mContext = ctx;
    }

    /**
     * Method to look through all installed packages system-wide and find those that contain one of
     * the BT-MAP intents in their manifest file. For each app the list of accounts are fetched
     * using the method parseAccounts().
     * @return LinkedHashMap with the packages as keys(BluetoothMapAccountItem) and
     *          values as ArrayLists of BluetoothMapAccountItems.
     */
    public LinkedHashMap<BluetoothMapAccountItem,
                         ArrayList<BluetoothMapAccountItem>> parsePackages(boolean includeIcon) {

        LinkedHashMap<BluetoothMapAccountItem, ArrayList<BluetoothMapAccountItem>> groups =
                new LinkedHashMap<BluetoothMapAccountItem,
                                  ArrayList<BluetoothMapAccountItem>>();
        Intent[] searchIntents = new Intent[2];
        //Array <Intent> searchIntents = new Array <Intent>();
        searchIntents[0] = new Intent(BluetoothMapContract.PROVIDER_INTERFACE_EMAIL);
        searchIntents[1] = new Intent(BluetoothMapContract.PROVIDER_INTERFACE_IM);
        // reset the counter every time this method is called.
        mAccountsEnabledCount=0;
        // find all installed packages and filter out those that do not support Bluetooth Map.
        // this is done by looking for a apps with content providers containing the intent-filter
        // in the manifest file.
        mPackageManager = mContext.getPackageManager();

        for (Intent searchIntent : searchIntents) {
            List<ResolveInfo> resInfos =
                mPackageManager.queryIntentContentProviders(searchIntent, 0);
            if (resInfos != null ) {
                if(D) Log.d(TAG,"Found " + resInfos.size() + " application(s) with intent "
                        + searchIntent.getAction().toString());
                BluetoothMapUtils.TYPE msgType = (searchIntent.getAction().toString() ==
                        BluetoothMapContract.PROVIDER_INTERFACE_EMAIL) ?
                        BluetoothMapUtils.TYPE.EMAIL : BluetoothMapUtils.TYPE.IM;
                for (ResolveInfo rInfo : resInfos) {
                    if(D) Log.d(TAG,"ResolveInfo " + rInfo.toString());
                    // We cannot rely on apps that have been force-stopped in the
                    // application settings menu.
                    if ((rInfo.providerInfo.applicationInfo.flags &
                            ApplicationInfo.FLAG_STOPPED) == 0) {
                        BluetoothMapAccountItem app = createAppItem(rInfo, includeIcon, msgType);
                        if (app != null){
                            ArrayList<BluetoothMapAccountItem> accounts = parseAccounts(app);
                            // we do not want to list apps without accounts
                            if(accounts.size() > 0)
                            {// we need to make sure that the "select all" checkbox
                             // is checked if all accounts in the list are checked
                                app.mIsChecked = true;
                                for (BluetoothMapAccountItem acc: accounts)
                                {
                                    if(!acc.mIsChecked)
                                    {
                                        app.mIsChecked = false;
                                        break;
                                    }
                                }
                                groups.put(app, accounts);
                            }
                        }
                    } else {
                        if(D)Log.d(TAG,"Ignoring force-stopped authority "
                                + rInfo.providerInfo.authority +"\n");
                    }
                }
            }
            else {
                if(D) Log.d(TAG,"Found no applications");
            }
        }
        return groups;
    }

    public BluetoothMapAccountItem createAppItem(ResolveInfo rInfo, boolean includeIcon,
            BluetoothMapUtils.TYPE type) {
        String provider = rInfo.providerInfo.authority;
        if(provider != null) {
            String name = rInfo.loadLabel(mPackageManager).toString();
            if(D)Log.d(TAG,rInfo.providerInfo.packageName + " - " + name +
                            " - meta-data(provider = " + provider+")\n");
            BluetoothMapAccountItem app = BluetoothMapAccountItem.create(
                    "0",
                    name,
                    rInfo.providerInfo.packageName,
                    provider,
                    (includeIcon == false)? null : rInfo.loadIcon(mPackageManager),
                    type);
            return app;
        }

        return null;
    }

    /**
     * Method for getting the accounts under a given contentprovider from a package.
     * @param app The parent app object
     * @return An ArrayList of BluetoothMapAccountItems containing all the accounts from the app
     */
    public ArrayList<BluetoothMapAccountItem> parseAccounts(BluetoothMapAccountItem app)  {
        Cursor c = null;
        if(D) Log.d(TAG,"Finding accounts for app "+app.getPackageName());
        ArrayList<BluetoothMapAccountItem> children = new ArrayList<BluetoothMapAccountItem>();
        // Get the list of accounts from the email apps content resolver (if possible)
        mResolver = mContext.getContentResolver();
        try{
            mProviderClient = mResolver.acquireUnstableContentProviderClient(
                    Uri.parse(app.mBase_uri_no_account));
            if (mProviderClient == null) {
                throw new RemoteException("Failed to acquire provider for " + app.getPackageName());
            }
            mProviderClient.setDetectNotResponding(PROVIDER_ANR_TIMEOUT);

            Uri uri = Uri.parse(app.mBase_uri_no_account + "/"
                                + BluetoothMapContract.TABLE_ACCOUNT);

            if(app.getType() == TYPE.IM) {
                c = mProviderClient.query(uri, BluetoothMapContract.BT_IM_ACCOUNT_PROJECTION,
                        null, null, BluetoothMapContract.AccountColumns._ID+" DESC");
            } else {
                c = mProviderClient.query(uri, BluetoothMapContract.BT_ACCOUNT_PROJECTION,
                        null, null, BluetoothMapContract.AccountColumns._ID+" DESC");
            }
        } catch (RemoteException e){
            if(D)Log.d(TAG,"Could not establish ContentProviderClient for "+app.getPackageName()+
                    " - returning empty account list" );
            return children;
        } finally {
            if (mProviderClient != null)
                mProviderClient.release();
        }

        if (c != null) {
            c.moveToPosition(-1);
            int idIndex = c.getColumnIndex(BluetoothMapContract.AccountColumns._ID);
            int dispNameIndex = c.getColumnIndex(
                    BluetoothMapContract.AccountColumns.ACCOUNT_DISPLAY_NAME);
            int exposeIndex = c.getColumnIndex(BluetoothMapContract.AccountColumns.FLAG_EXPOSE);
            int uciIndex = c.getColumnIndex(BluetoothMapContract.AccountColumns.ACCOUNT_UCI);
            int uciPreIndex = c.getColumnIndex(
                    BluetoothMapContract.AccountColumns.ACCOUNT_UCI_PREFIX);
            while (c.moveToNext()) {
                if(D)Log.d(TAG,"Adding account " + c.getString(dispNameIndex) +
                        " with ID " + String.valueOf(c.getInt(idIndex)));
                String uci = null;
                String uciPrefix = null;
                if(app.getType() == TYPE.IM){
                    uci = c.getString(uciIndex);
                    uciPrefix = c.getString(uciPreIndex);
                    if(D)Log.d(TAG,"   Account UCI " + uci);
                }

                BluetoothMapAccountItem child = BluetoothMapAccountItem.create(
                        String.valueOf((c.getInt(idIndex))),
                        c.getString(dispNameIndex),
                        app.getPackageName(),
                        app.getProviderAuthority(),
                        null,
                        app.getType(),
                        uci,
                        uciPrefix);

                child.mIsChecked = (c.getInt(exposeIndex) != 0);
                child.mIsChecked = true; // TODO: Revert when this works
                /* update the account counter
                 * so we can make sure that not to many accounts are checked. */
                if(child.mIsChecked)
                {
                    mAccountsEnabledCount++;
                }
                children.add(child);
            }
            c.close();
        } else {
            if(D)Log.d(TAG, "query failed");
        }
        return children;
    }
    /**
     * Gets the number of enabled accounts in total across all supported apps.
     * NOTE that this method should not be called before the parsePackages method
     * has been successfully called.
     * @return number of enabled accounts
     */
    public int getAccountsEnabledCount() {
        if(D)Log.d(TAG,"Enabled Accounts count:"+ mAccountsEnabledCount);
        return mAccountsEnabledCount;
    }

}
