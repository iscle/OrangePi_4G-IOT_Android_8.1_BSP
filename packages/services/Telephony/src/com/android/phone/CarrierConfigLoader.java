/**
 * Copyright (c) 2015, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.phone;

import static android.Manifest.permission.READ_PHONE_STATE;
import static android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.service.carrier.CarrierIdentifier;
import android.service.carrier.CarrierService;
import android.service.carrier.ICarrierService;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.ICarrierConfigLoader;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.IndentingPrintWriter;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * CarrierConfigLoader binds to privileged carrier apps to fetch carrier config overlays.
 */

public class CarrierConfigLoader extends ICarrierConfigLoader.Stub {
    private static final String LOG_TAG = "CarrierConfigLoader";

    // Package name for platform carrier config app, bundled with system image.
    private final String mPlatformCarrierConfigPackage;

    /** The singleton instance. */
    private static CarrierConfigLoader sInstance;
    // The context for phone app, passed from PhoneGlobals.
    private Context mContext;
    // Carrier configs from default app, indexed by phoneID.
    private PersistableBundle[] mConfigFromDefaultApp;
    // Carrier configs from privileged carrier config app, indexed by phoneID.
    private PersistableBundle[] mConfigFromCarrierApp;
    // Service connection for binding to config app.
    private CarrierServiceConnection[] mServiceConnection;
    // Whether we have sent config change bcast for each phone id.
    private boolean[] mHasSentConfigChange;

    // Broadcast receiver for Boot intents, register intent filter in construtor.
    private final BroadcastReceiver mBootReceiver = new ConfigLoaderBroadcastReceiver();
    // Broadcast receiver for SIM and pkg intents, register intent filter in constructor.
    private final BroadcastReceiver mPackageReceiver = new ConfigLoaderBroadcastReceiver();

    // Message codes; see mHandler below.
    // Request from SubscriptionInfoUpdater when SIM becomes absent or error.
    private static final int EVENT_CLEAR_CONFIG = 0;
    // Has connected to default app.
    private static final int EVENT_CONNECTED_TO_DEFAULT = 3;
    // Has connected to carrier app.
    private static final int EVENT_CONNECTED_TO_CARRIER = 4;
    // Config has been loaded from default app.
    private static final int EVENT_LOADED_FROM_DEFAULT = 5;
    // Config has been loaded from carrier app.
    private static final int EVENT_LOADED_FROM_CARRIER = 6;
    // Attempt to fetch from default app or read from XML.
    private static final int EVENT_FETCH_DEFAULT = 7;
    // Attempt to fetch from carrier app or read from XML.
    private static final int EVENT_FETCH_CARRIER = 8;
    // A package has been installed, uninstalled, or updated.
    private static final int EVENT_PACKAGE_CHANGED = 9;
    // Bind timed out for the default app.
    private static final int EVENT_BIND_DEFAULT_TIMEOUT = 10;
    // Bind timed out for a carrier app.
    private static final int EVENT_BIND_CARRIER_TIMEOUT = 11;
    // Check if the system fingerprint has changed.
    private static final int EVENT_CHECK_SYSTEM_UPDATE = 12;
    // Rerun carrier config binding after system is unlocked.
    private static final int EVENT_SYSTEM_UNLOCKED = 13;

    private static final int BIND_TIMEOUT_MILLIS = 30000;

    // Tags used for saving and restoring XML documents.
    private static final String TAG_DOCUMENT = "carrier_config";
    private static final String TAG_VERSION = "package_version";
    private static final String TAG_BUNDLE = "bundle_data";

    // SharedPreferences key for last known build fingerprint.
    private static final String KEY_FINGERPRINT = "build_fingerprint";

    // Handler to process various events.
    //
    // For each phoneId, the event sequence should be:
    //     fetch default, connected to default, loaded from default,
    //     fetch carrier, connected to carrier, loaded from carrier.
    //
    // If there is a saved config file for either the default app or the carrier app, we skip
    // binding to the app and go straight from fetch to loaded.
    //
    // At any time, at most one connection is active. If events are not in this order, previous
    // connection will be unbound, so only latest event takes effect.
    //
    // We broadcast ACTION_CARRIER_CONFIG_CHANGED after:
    // 1. loading from carrier app (even if read from a file)
    // 2. loading from default app if there is no carrier app (even if read from a file)
    // 3. clearing config (e.g. due to sim removal)
    // 4. encountering bind or IPC error
    private Handler mHandler = new Handler() {
            @Override
        public void handleMessage(Message msg) {
            int phoneId = msg.arg1;
            log("mHandler: " + msg.what + " phoneId: " + phoneId);
            String iccid;
            CarrierIdentifier carrierId;
            String carrierPackageName;
            CarrierServiceConnection conn;
            PersistableBundle config;
            switch (msg.what) {
                case EVENT_CLEAR_CONFIG:

                    /* Ignore clear configuration request if device is being shutdown. */
                    Phone phone = PhoneFactory.getPhone(phoneId);
                    if (phone != null) {
                        if (phone.isShuttingDown()) {
                            break;
                        }
                    }

                    if (mConfigFromDefaultApp[phoneId] == null &&
                        mConfigFromCarrierApp[phoneId] == null)
                        break;

                    mConfigFromDefaultApp[phoneId] = null;
                    mConfigFromCarrierApp[phoneId] = null;
                    mServiceConnection[phoneId] = null;
                    broadcastConfigChangedIntent(phoneId);
                    break;

                case EVENT_SYSTEM_UNLOCKED:
                    for (int i = 0; i < TelephonyManager.from(mContext).getPhoneCount(); ++i) {
                        // When user unlock device, we should only try to send broadcast again if
                        // we have sent it before unlock. This will avoid we try to load carrier
                        // config when SIM is still loading when unlock happens.
                        if (mHasSentConfigChange[i]) {
                            updateConfigForPhoneId(i);
                        }
                    }
                    break;

                case EVENT_PACKAGE_CHANGED:
                    carrierPackageName = (String) msg.obj;
                    // Only update if there are cached config removed to avoid updating config
                    // for unrelated packages.
                    if (clearCachedConfigForPackage(carrierPackageName)) {
                        int numPhones = TelephonyManager.from(mContext).getPhoneCount();
                        for (int i = 0; i < numPhones; ++i) {
                            updateConfigForPhoneId(i);
                        }
                    }
                    break;

                case EVENT_FETCH_DEFAULT:
                    iccid = getIccIdForPhoneId(phoneId);
                    config = restoreConfigFromXml(mPlatformCarrierConfigPackage, iccid);
                    if (config != null) {
                        log("Loaded config from XML. package=" + mPlatformCarrierConfigPackage
                                + " phoneId=" + phoneId);
                        mConfigFromDefaultApp[phoneId] = config;
                        Message newMsg = obtainMessage(EVENT_LOADED_FROM_DEFAULT, phoneId, -1);
                        newMsg.getData().putBoolean("loaded_from_xml", true);
                        mHandler.sendMessage(newMsg);
                    } else {
                        if (bindToConfigPackage(mPlatformCarrierConfigPackage,
                                phoneId, EVENT_CONNECTED_TO_DEFAULT)) {
                            sendMessageDelayed(obtainMessage(EVENT_BIND_DEFAULT_TIMEOUT, phoneId, -1),
                                    BIND_TIMEOUT_MILLIS);
                        } else {
                            // Send bcast if bind fails
                            broadcastConfigChangedIntent(phoneId);
                        }
                    }
                    break;

                case EVENT_CONNECTED_TO_DEFAULT:
                    removeMessages(EVENT_BIND_DEFAULT_TIMEOUT);
                    carrierId = getCarrierIdForPhoneId(phoneId);
                    conn = (CarrierServiceConnection) msg.obj;
                    // If new service connection has been created, unbind.
                    if (mServiceConnection[phoneId] != conn || conn.service == null) {
                        mContext.unbindService(conn);
                        break;
                    }
                    try {
                        ICarrierService carrierService = ICarrierService.Stub
                                .asInterface(conn.service);
                        config = carrierService.getCarrierConfig(carrierId);
                        iccid = getIccIdForPhoneId(phoneId);
                        saveConfigToXml(mPlatformCarrierConfigPackage, iccid, config);
                        mConfigFromDefaultApp[phoneId] = config;
                        sendMessage(obtainMessage(EVENT_LOADED_FROM_DEFAULT, phoneId, -1));
                    } catch (Exception ex) {
                        // The bound app could throw exceptions that binder will pass to us.
                        loge("Failed to get carrier config: " + ex.toString());
                    } finally {
                        mContext.unbindService(mServiceConnection[phoneId]);
                    }
                    break;

                case EVENT_BIND_DEFAULT_TIMEOUT:
                    mContext.unbindService(mServiceConnection[phoneId]);
                    broadcastConfigChangedIntent(phoneId);
                    break;

                case EVENT_LOADED_FROM_DEFAULT:
                    // If we attempted to bind to the app, but the service connection is null, then
                    // config was cleared while we were waiting and we should not continue.
                    if (!msg.getData().getBoolean("loaded_from_xml", false)
                            && mServiceConnection[phoneId] == null) {
                        break;
                    }
                    carrierPackageName = getCarrierPackageForPhoneId(phoneId);
                    if (carrierPackageName != null) {
                        log("Found carrier config app: " + carrierPackageName);
                        sendMessage(obtainMessage(EVENT_FETCH_CARRIER, phoneId));
                    } else {
                        broadcastConfigChangedIntent(phoneId);
                    }
                    break;

                case EVENT_FETCH_CARRIER:
                    carrierPackageName = getCarrierPackageForPhoneId(phoneId);
                    iccid = getIccIdForPhoneId(phoneId);
                    config = restoreConfigFromXml(carrierPackageName, iccid);
                    if (config != null) {
                        log("Loaded config from XML. package=" + carrierPackageName + " phoneId="
                                + phoneId);
                        mConfigFromCarrierApp[phoneId] = config;
                        Message newMsg = obtainMessage(EVENT_LOADED_FROM_CARRIER, phoneId, -1);
                        newMsg.getData().putBoolean("loaded_from_xml", true);
                        sendMessage(newMsg);
                    } else {
                        if (carrierPackageName != null
                            && bindToConfigPackage(carrierPackageName, phoneId,
                                    EVENT_CONNECTED_TO_CARRIER)) {
                            sendMessageDelayed(obtainMessage(EVENT_BIND_CARRIER_TIMEOUT, phoneId, -1),
                                    BIND_TIMEOUT_MILLIS);
                        } else {
                            // Send bcast if bind fails
                            broadcastConfigChangedIntent(phoneId);
                        }
                    }
                    break;

                case EVENT_CONNECTED_TO_CARRIER:
                    removeMessages(EVENT_BIND_CARRIER_TIMEOUT);
                    carrierId = getCarrierIdForPhoneId(phoneId);
                    conn = (CarrierServiceConnection) msg.obj;
                    // If new service connection has been created, unbind.
                    if (mServiceConnection[phoneId] != conn ||
                            conn.service == null) {
                        mContext.unbindService(conn);
                        break;
                    }
                    try {
                        ICarrierService carrierService = ICarrierService.Stub
                                .asInterface(conn.service);
                        config = carrierService.getCarrierConfig(carrierId);
                        carrierPackageName = getCarrierPackageForPhoneId(phoneId);
                        iccid = getIccIdForPhoneId(phoneId);
                        saveConfigToXml(carrierPackageName, iccid, config);
                        mConfigFromCarrierApp[phoneId] = config;
                        sendMessage(obtainMessage(EVENT_LOADED_FROM_CARRIER, phoneId, -1));
                    } catch (Exception ex) {
                        // The bound app could throw exceptions that binder will pass to us.
                        loge("Failed to get carrier config: " + ex.toString());
                    } finally {
                        mContext.unbindService(mServiceConnection[phoneId]);
                    }
                    break;

                case EVENT_BIND_CARRIER_TIMEOUT:
                    mContext.unbindService(mServiceConnection[phoneId]);
                    broadcastConfigChangedIntent(phoneId);
                    break;

                case EVENT_LOADED_FROM_CARRIER:
                    // If we attempted to bind to the app, but the service connection is null, then
                    // config was cleared while we were waiting and we should not continue.
                    if (!msg.getData().getBoolean("loaded_from_xml", false)
                            && mServiceConnection[phoneId] == null) {
                        break;
                    }
                    broadcastConfigChangedIntent(phoneId);
                    break;

                case EVENT_CHECK_SYSTEM_UPDATE:
                    SharedPreferences sharedPrefs =
                            PreferenceManager.getDefaultSharedPreferences(mContext);
                    final String lastFingerprint = sharedPrefs.getString(KEY_FINGERPRINT, null);
                    if (!Build.FINGERPRINT.equals(lastFingerprint)) {
                        log("Build fingerprint changed. old: "
                                + lastFingerprint + " new: " + Build.FINGERPRINT);
                        clearCachedConfigForPackage(null);
                        sharedPrefs.edit().putString(KEY_FINGERPRINT, Build.FINGERPRINT).apply();
                    }
                    break;
            }
        }
    };

    /**
     * Constructs a CarrierConfigLoader, registers it as a service, and registers a broadcast
     * receiver for relevant events.
     */
    private CarrierConfigLoader(Context context) {
        mContext = context;
        mPlatformCarrierConfigPackage =
                mContext.getString(R.string.platform_carrier_config_package);

        IntentFilter bootFilter = new IntentFilter();
        bootFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
        context.registerReceiver(mBootReceiver, bootFilter);

        // Register for package updates. Update app or uninstall app update will have all 3 intents,
        // in the order or removed, added, replaced, all with extra_replace set to true.
        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pkgFilter.addDataScheme("package");
        context.registerReceiverAsUser(mPackageReceiver, UserHandle.ALL, pkgFilter, null, null);

        int numPhones = TelephonyManager.from(context).getPhoneCount();
        mConfigFromDefaultApp = new PersistableBundle[numPhones];
        mConfigFromCarrierApp = new PersistableBundle[numPhones];
        mServiceConnection = new CarrierServiceConnection[numPhones];
        mHasSentConfigChange = new boolean[numPhones];
        // Make this service available through ServiceManager.
        ServiceManager.addService(Context.CARRIER_CONFIG_SERVICE, this);
        log("CarrierConfigLoader has started");
        mHandler.sendEmptyMessage(EVENT_CHECK_SYSTEM_UPDATE);
    }

    /**
     * Initialize the singleton CarrierConfigLoader instance.
     *
     * This is only done once, at startup, from {@link com.android.phone.PhoneApp#onCreate}.
     */
    /* package */
    static CarrierConfigLoader init(Context context) {
        synchronized (CarrierConfigLoader.class) {
            if (sInstance == null) {
                sInstance = new CarrierConfigLoader(context);
            } else {
                Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
            }
            return sInstance;
        }
    }

    private void broadcastConfigChangedIntent(int phoneId) {
        Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT |
                Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, phoneId);
        ActivityManager.broadcastStickyIntent(intent, UserHandle.USER_ALL);
        mHasSentConfigChange[phoneId] = true;
    }

    /** Binds to the default or carrier config app. */
    private boolean bindToConfigPackage(String pkgName, int phoneId, int eventId) {
        log("Binding to " + pkgName + " for phone " + phoneId);
        Intent carrierService = new Intent(CarrierService.CARRIER_SERVICE_INTERFACE);
        carrierService.setPackage(pkgName);
        mServiceConnection[phoneId] = new CarrierServiceConnection(phoneId, eventId);
        try {
            return mContext.bindService(carrierService, mServiceConnection[phoneId],
                    Context.BIND_AUTO_CREATE);
        } catch (SecurityException ex) {
            return false;
        }
    }

    private CarrierIdentifier getCarrierIdForPhoneId(int phoneId) {
        String mcc = "";
        String mnc = "";
        String imsi = "";
        String gid1 = "";
        String gid2 = "";
        String spn = TelephonyManager.from(mContext).getSimOperatorNameForPhone(phoneId);
        String simOperator = TelephonyManager.from(mContext).getSimOperatorNumericForPhone(phoneId);
        // A valid simOperator should be 5 or 6 digits, depending on the length of the MNC.
        if (simOperator != null && simOperator.length() >= 3) {
            mcc = simOperator.substring(0, 3);
            mnc = simOperator.substring(3);
        }
        Phone phone = PhoneFactory.getPhone(phoneId);
        if (phone != null) {
            imsi = phone.getSubscriberId();
            gid1 = phone.getGroupIdLevel1();
            gid2 = phone.getGroupIdLevel2();
        }

        return new CarrierIdentifier(mcc, mnc, spn, imsi, gid1, gid2);
    }

    /** Returns the package name of a priveleged carrier app, or null if there is none. */
    private String getCarrierPackageForPhoneId(int phoneId) {
        List<String> carrierPackageNames = TelephonyManager.from(mContext)
                .getCarrierPackageNamesForIntentAndPhone(
                        new Intent(CarrierService.CARRIER_SERVICE_INTERFACE), phoneId);
        if (carrierPackageNames != null && carrierPackageNames.size() > 0) {
            return carrierPackageNames.get(0);
        } else {
            return null;
        }
    }

    private String getIccIdForPhoneId(int phoneId) {
        if (!SubscriptionManager.isValidPhoneId(phoneId)) {
            return null;
        }
        Phone phone = PhoneFactory.getPhone(phoneId);
        if (phone == null) {
            return null;
        }
        return phone.getIccSerialNumber();
    }

    /**
     * Writes a bundle to an XML file.
     *
     * The bundle will be written to a file named after the package name and ICCID, so that it can
     * be restored later with {@link @restoreConfigFromXml}. The XML output will include the bundle
     * and the current version of the specified package.
     *
     * In case of errors or invalid input, no file will be written.
     *
     * @param packageName the name of the package from which we fetched this bundle.
     * @param iccid the ICCID of the subscription for which this bundle was fetched.
     * @param config the bundle to be written. Null will be treated as an empty bundle.
     */
    private void saveConfigToXml(String packageName, String iccid, PersistableBundle config) {
        if (packageName == null || iccid == null) {
            loge("Cannot save config with null packageName or iccid.");
            return;
        }
        // b/32668103 Only save to file if config isn't empty.
        // In case of failure, not caching an empty bundle will
        // try loading config again on next power on or sim loaded.
        // Downside is for genuinely empty bundle, will bind and load
        // on every power on.
        if (config == null || config.isEmpty()) {
            return;
        }

        final String version = getPackageVersion(packageName);
        if (version == null) {
            loge("Failed to get package version for: " + packageName);
            return;
        }

        FileOutputStream outFile = null;
        try {
            outFile = new FileOutputStream(
                    new File(mContext.getFilesDir(), getFilenameForConfig(packageName, iccid)));
            FastXmlSerializer out = new FastXmlSerializer();
            out.setOutput(outFile, "utf-8");
            out.startDocument("utf-8", true);
            out.startTag(null, TAG_DOCUMENT);
            out.startTag(null, TAG_VERSION);
            out.text(version);
            out.endTag(null, TAG_VERSION);
            out.startTag(null, TAG_BUNDLE);
            config.saveToXml(out);
            out.endTag(null, TAG_BUNDLE);
            out.endTag(null, TAG_DOCUMENT);
            out.endDocument();
            out.flush();
            outFile.close();
        }
        catch (IOException e) {
            loge(e.toString());
        }
        catch (XmlPullParserException e) {
            loge(e.toString());
        }
    }

    /**
     * Reads a bundle from an XML file.
     *
     * This restores a bundle that was written with {@link #saveConfigToXml}. This returns the saved
     * config bundle for the given package and ICCID.
     *
     * In case of errors, or if the saved config is from a different package version than the
     * current version, then null will be returned.
     *
     * @param packageName the name of the package from which we fetched this bundle.
     * @param iccid the ICCID of the subscription for which this bundle was fetched.
     * @return the bundle from the XML file. Returns null if there is no saved config, the saved
     *         version does not match, or reading config fails.
     */
    private PersistableBundle restoreConfigFromXml(String packageName, String iccid) {
        final String version = getPackageVersion(packageName);
        if (version == null) {
            loge("Failed to get package version for: " + packageName);
            return null;
        }
        if (packageName == null || iccid == null) {
            loge("Cannot restore config with null packageName or iccid.");
            return null;
        }

        PersistableBundle restoredBundle = null;
        FileInputStream inFile = null;
        try {
            inFile = new FileInputStream(
                    new File(mContext.getFilesDir(), getFilenameForConfig(packageName, iccid)));
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(inFile, "utf-8");

            int event;
            while (((event = parser.next()) != XmlPullParser.END_DOCUMENT)) {

                if (event == XmlPullParser.START_TAG && TAG_VERSION.equals(parser.getName())) {
                    String savedVersion = parser.nextText();
                    if (!version.equals(savedVersion)) {
                        log("Saved version mismatch: " + version + " vs " + savedVersion);
                        break;
                    }
                }

                if (event == XmlPullParser.START_TAG && TAG_BUNDLE.equals(parser.getName())) {
                    restoredBundle = PersistableBundle.restoreFromXml(parser);
                }
            }
            inFile.close();
        }
        catch (FileNotFoundException e) {
            loge(e.toString());
        }
        catch (XmlPullParserException e) {
            loge(e.toString());
        }
        catch (IOException e) {
            loge(e.toString());
        }

        return restoredBundle;
    }

    /**
     * Clears cached carrier config.
     * This deletes all saved XML files associated with the given package name. If packageName is
     * null, then it deletes all saved XML files.
     *
     * @param packageName the name of a carrier package, or null if all cached config should be
     *                    cleared.
     * @return true iff one or more files were deleted.
     */
    private boolean clearCachedConfigForPackage(final String packageName) {
        File dir = mContext.getFilesDir();
        File[] packageFiles = dir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String filename) {
                if (packageName != null) {
                    return filename.startsWith("carrierconfig-" + packageName + "-");
                } else {
                    return filename.startsWith("carrierconfig-");
                }
            }
        });
        if (packageFiles == null || packageFiles.length < 1) return false;
        for (File f : packageFiles) {
            log("deleting " + f.getName());
            f.delete();
        }
        return true;
    }

    /** Builds a canonical file name for a config file. */
    private String getFilenameForConfig(@NonNull String packageName, @NonNull String iccid) {
        return "carrierconfig-" + packageName + "-" + iccid + ".xml";
    }

    /** Return the current version code of a package, or null if the name is not found. */
    private String getPackageVersion(String packageName) {
        try {
            PackageInfo info = mContext.getPackageManager().getPackageInfo(packageName, 0);
            return Integer.toString(info.versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** Read up to date config.
     *
     * This reads config bundles for the given phoneId. That means getting the latest bundle from
     * the default app and a privileged carrier app, if present. This will not bind to an app if we
     * have a saved config file to use instead.
     */
    private void updateConfigForPhoneId(int phoneId) {
        // Clear in-memory cache for carrier app config, so when carrier app gets uninstalled, no
        // stale config is left.
        if (mConfigFromCarrierApp[phoneId] != null &&
                getCarrierPackageForPhoneId(phoneId) == null) {
            mConfigFromCarrierApp[phoneId] = null;
        }
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_FETCH_DEFAULT, phoneId, -1));
    }

    @Override public
    @NonNull
    PersistableBundle getConfigForSubId(int subId) {
        try {
            mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, null);
            // SKIP checking run-time READ_PHONE_STATE since using PRIVILEGED
        } catch (SecurityException e) {
            mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, null);
        }
        int phoneId = SubscriptionManager.getPhoneId(subId);
        PersistableBundle retConfig = CarrierConfigManager.getDefaultConfig();
        if (SubscriptionManager.isValidPhoneId(phoneId)) {
            PersistableBundle config = mConfigFromDefaultApp[phoneId];
            if (config != null)
                retConfig.putAll(config);
            config = mConfigFromCarrierApp[phoneId];
            if (config != null)
                retConfig.putAll(config);
        }
        return retConfig;
    }

    @Override
    public void notifyConfigChangedForSubId(int subId) {
        int phoneId = SubscriptionManager.getPhoneId(subId);
        if (!SubscriptionManager.isValidPhoneId(phoneId)) {
            log("Ignore invalid phoneId: " + phoneId + " for subId: " + subId);
            return;
        }
        String callingPackageName = mContext.getPackageManager().getNameForUid(
                Binder.getCallingUid());
        // TODO: Check that the calling packages is privileged for subId specifically.
        int privilegeStatus = TelephonyManager.from(mContext).checkCarrierPrivilegesForPackage(
                callingPackageName);
        // Requires the calling app to be either a carrier privileged app or
        // system privileged app with MODIFY_PHONE_STATE permission.
        if (privilegeStatus != TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS) {
            mContext.enforceCallingOrSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE,
                    "Require carrier privileges or MODIFY_PHONE_STATE permission.");
        }

        // This method should block until deleting has completed, so that an error which prevents us
        // from clearing the cache is passed back to the carrier app. With the files successfully
        // deleted, this can return and we will eventually bind to the carrier app.
        clearCachedConfigForPackage(callingPackageName);
        updateConfigForPhoneId(phoneId);
    }

    @Override
    public void updateConfigForPhoneId(int phoneId, String simState) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.MODIFY_PHONE_STATE, null);
        log("update config for phoneId: " + phoneId + " simState: " + simState);
        if (!SubscriptionManager.isValidPhoneId(phoneId)) {
            return;
        }
        // requires Java 7 for switch on string.
        switch (simState) {
            case IccCardConstants.INTENT_VALUE_ICC_ABSENT:
            case IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR:
            case IccCardConstants.INTENT_VALUE_ICC_CARD_RESTRICTED:
            case IccCardConstants.INTENT_VALUE_ICC_UNKNOWN:
                mHandler.sendMessage(mHandler.obtainMessage(EVENT_CLEAR_CONFIG, phoneId, -1));
                break;
            case IccCardConstants.INTENT_VALUE_ICC_LOADED:
            case IccCardConstants.INTENT_VALUE_ICC_LOCKED:
                updateConfigForPhoneId(phoneId);
                break;
        }
    }

    @Override
    public String getDefaultCarrierServicePackageName() {
        return mPlatformCarrierConfigPackage;
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump carrierconfig from from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            return;
        }
        pw.println("CarrierConfigLoader: " + this);
        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            pw.println("Phone Id = " + i);
            // display default values in CarrierConfigManager
            printConfig(CarrierConfigManager.getDefaultConfig(), pw,
                    "Default Values from CarrierConfigManager");
            pw.println("");
            // display ConfigFromDefaultApp
            printConfig(mConfigFromDefaultApp[i], pw, "mConfigFromDefaultApp");
            pw.println("");
            // display ConfigFromCarrierApp
            printConfig(mConfigFromCarrierApp[i], pw, "mConfigFromCarrierApp");
        }
    }

    private void printConfig(PersistableBundle configApp, PrintWriter pw, String name) {
        IndentingPrintWriter indentPW = new IndentingPrintWriter(pw, "    ");
        if (configApp == null) {
            indentPW.increaseIndent();
            indentPW.println(name + " : null ");
            return;
        }
        indentPW.increaseIndent();
        indentPW.println(name + " : ");
        List<String> sortedKeys = new ArrayList<String>(configApp.keySet());
        Collections.sort(sortedKeys);
        indentPW.increaseIndent();
        indentPW.increaseIndent();
        for (String key : sortedKeys) {
            if (configApp.get(key) != null && configApp.get(key) instanceof Object[]) {
                indentPW.println(key + " = " +
                        Arrays.toString((Object[]) configApp.get(key)));
            } else if (configApp.get(key) != null && configApp.get(key) instanceof int[]) {
                indentPW.println(key + " = " + Arrays.toString((int[]) configApp.get(key)));
            } else {
                indentPW.println(key + " = " + configApp.get(key));
            }
        }
    }

    private class CarrierServiceConnection implements ServiceConnection {
        int phoneId;
        int eventId;
        IBinder service;

        public CarrierServiceConnection(int phoneId, int eventId) {
            this.phoneId = phoneId;
            this.eventId = eventId;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            log("Connected to config app: " + name.flattenToString());
            this.service = service;
            mHandler.sendMessage(mHandler.obtainMessage(eventId, phoneId, -1, this));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            this.service = null;
        }
    }

    private class ConfigLoaderBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            boolean replace = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
            // If replace is true, only care ACTION_PACKAGE_REPLACED.
            if (replace && !Intent.ACTION_PACKAGE_REPLACED.equals(action))
                return;

            switch (action) {
                case Intent.ACTION_BOOT_COMPLETED:
                    mHandler.sendMessage(mHandler.obtainMessage(EVENT_SYSTEM_UNLOCKED, null));
                    break;

                case Intent.ACTION_PACKAGE_ADDED:
                case Intent.ACTION_PACKAGE_REMOVED:
                case Intent.ACTION_PACKAGE_REPLACED:
                    int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
                    String packageName = mContext.getPackageManager().getNameForUid(uid);
                    if (packageName != null) {
                        // We don't have a phoneId for arg1.
                        mHandler.sendMessage(
                                mHandler.obtainMessage(EVENT_PACKAGE_CHANGED, packageName));
                    }
                    break;
            }
        }
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    private static void loge(String msg) {
        Log.e(LOG_TAG, msg);
    }
}
