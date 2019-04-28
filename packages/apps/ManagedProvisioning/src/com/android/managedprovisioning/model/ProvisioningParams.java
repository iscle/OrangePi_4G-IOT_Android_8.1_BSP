/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.model;

import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_ICON_URI;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_LABEL;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DISCLAIMERS;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCALE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCAL_TIME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_MAIN_COLOR;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ORGANIZATION_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_USER_CONSENT;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_USER_SETUP;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SUPPORT_URL;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TIME_ZONE;
import static com.android.internal.util.Preconditions.checkArgument;
import static com.android.internal.util.Preconditions.checkNotNull;
import static com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences.DEFAULT_PROVISIONING_ID;
import static com.android.managedprovisioning.common.StoreUtils.accountToPersistableBundle;
import static com.android.managedprovisioning.common.StoreUtils.getIntegerAttrFromPersistableBundle;
import static com.android.managedprovisioning.common.StoreUtils.getObjectAttrFromPersistableBundle;
import static com.android.managedprovisioning.common.StoreUtils.getStringAttrFromPersistableBundle;
import static com.android.managedprovisioning.common.StoreUtils.putIntegerIfNotNull;
import static com.android.managedprovisioning.common.StoreUtils.putPersistableBundlableIfNotNull;

import android.accounts.Account;
import android.content.ComponentName;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.support.annotation.Nullable;
import android.util.Xml;
import com.android.internal.util.FastXmlSerializer;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.PersistableBundlable;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.StoreUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

/**
 * Provisioning parameters for Device Owner and Profile Owner provisioning.
 */
public final class ProvisioningParams extends PersistableBundlable {
    public static final long DEFAULT_LOCAL_TIME = -1;
    public static final Integer DEFAULT_MAIN_COLOR = null;
    public static final boolean DEFAULT_STARTED_BY_TRUSTED_SOURCE = false;
    public static final boolean DEFAULT_IS_NFC = false;
    public static final boolean DEFAULT_LEAVE_ALL_SYSTEM_APPS_ENABLED = false;
    public static final boolean DEFAULT_EXTRA_PROVISIONING_SKIP_ENCRYPTION = false;
    public static final boolean DEFAULT_EXTRA_PROVISIONING_SKIP_USER_CONSENT = false;
    public static final boolean DEFAULT_EXTRA_PROVISIONING_KEEP_ACCOUNT_MIGRATED = false;
    public static final boolean DEFAULT_SKIP_USER_SETUP = true;
    // Intent extra used internally for passing data between activities and service.
    public static final String EXTRA_PROVISIONING_PARAMS = "provisioningParams";

    private static final String TAG_PROVISIONING_ID = "provisioning-id";
    private static final String TAG_PROVISIONING_PARAMS = "provisioning-params";
    private static final String TAG_WIFI_INFO = "wifi-info";
    private static final String TAG_PACKAGE_DOWNLOAD_INFO = "download-info";
    private static final String TAG_STARTED_BY_TRUSTED_SOURCE = "started-by-trusted-source";
    private static final String TAG_IS_NFC = "started-is-nfc";
    private static final String TAG_PROVISIONING_ACTION = "provisioning-action";

    public static final Parcelable.Creator<ProvisioningParams> CREATOR
            = new Parcelable.Creator<ProvisioningParams>() {
        @Override
        public ProvisioningParams createFromParcel(Parcel in) {
            return new ProvisioningParams(in);
        }

        @Override
        public ProvisioningParams[] newArray(int size) {
            return new ProvisioningParams[size];
        }
    };

    public final long provisioningId;

    @Nullable
    public final String timeZone;

    public final long localTime;

    @Nullable
    public final Locale locale;

    /** WiFi configuration. */
    @Nullable
    public final WifiInfo wifiInfo;

    /**
     * Package name of the device admin package.
     *
     * <p>At least one one of deviceAdminPackageName and deviceAdminComponentName should be
     * non-null.
     */
    @Deprecated
    public final String deviceAdminPackageName;

    /**
     * {@link ComponentName} of the device admin package.
     *
     * <p>At least one one of deviceAdminPackageName and deviceAdminComponentName should be
     * non-null.
     */
    public final ComponentName deviceAdminComponentName;

    public final String deviceAdminLabel;
    public final String organizationName;
    public final String supportUrl;
    public final String deviceAdminIconFilePath;

    /** {@link Account} that should be migrated to the managed profile. */
    @Nullable
    public final Account accountToMigrate;

    /** True if the account will not be removed from the calling user after it is migrated. */
    public final boolean keepAccountMigrated;

    /** Provisioning action comes along with the provisioning data. */
    public final String provisioningAction;

    /**
     * The main color theme used in managed profile only.
     *
     * <p>{@code null} means the default value.
     */
    @Nullable
    public final Integer mainColor;

    /** The download information of device admin package. */
    @Nullable
    public final PackageDownloadInfo deviceAdminDownloadInfo;

    /** List of disclaimers */
    @Nullable
    public final DisclaimersParam disclaimersParam;

    /**
     * Custom key-value pairs from enterprise mobility management which are passed to device admin
     * package after provisioning.
     *
     * <p>Note that {@link ProvisioningParams} is not immutable because this field is mutable.
     */
    @Nullable
    public final PersistableBundle adminExtrasBundle;

    /**
     * True iff provisioning flow was started by a trusted app. This includes Nfc bump and QR code.
     */
    public final boolean startedByTrustedSource;

    public final boolean isNfc;

    /** True if all system apps should be enabled after provisioning. */
    public final boolean leaveAllSystemAppsEnabled;

    /** True if device encryption should be skipped. */
    public final boolean skipEncryption;

    /** True if user setup can be skipped. */
    public final boolean skipUserSetup;

    /** True if user consent page in pre-provisioning can be skipped. */
    public final boolean skipUserConsent;

    public static String inferStaticDeviceAdminPackageName(ComponentName deviceAdminComponentName,
            String deviceAdminPackageName) {
        if (deviceAdminComponentName != null) {
            return deviceAdminComponentName.getPackageName();
        }
        return deviceAdminPackageName;
    }

    public String inferDeviceAdminPackageName() {
        return inferStaticDeviceAdminPackageName(deviceAdminComponentName, deviceAdminPackageName);
    }

    private ProvisioningParams(Builder builder) {
        provisioningId = builder.mProvisioningId;
        timeZone = builder.mTimeZone;
        localTime = builder.mLocalTime;
        locale = builder.mLocale;

        wifiInfo = builder.mWifiInfo;

        deviceAdminComponentName = builder.mDeviceAdminComponentName;
        deviceAdminPackageName = builder.mDeviceAdminPackageName;
        deviceAdminLabel = builder.mDeviceAdminLabel;
        organizationName = builder.mOrganizationName;
        supportUrl = builder.mSupportUrl;
        deviceAdminIconFilePath = builder.mDeviceAdminIconFilePath;

        deviceAdminDownloadInfo = builder.mDeviceAdminDownloadInfo;
        disclaimersParam = builder.mDisclaimersParam;

        adminExtrasBundle = builder.mAdminExtrasBundle;

        startedByTrustedSource = builder.mStartedByTrustedSource;
        isNfc = builder.mIsNfc;
        leaveAllSystemAppsEnabled = builder.mLeaveAllSystemAppsEnabled;
        skipEncryption = builder.mSkipEncryption;
        accountToMigrate = builder.mAccountToMigrate;
        provisioningAction = checkNotNull(builder.mProvisioningAction);
        mainColor = builder.mMainColor;
        skipUserConsent = builder.mSkipUserConsent;
        skipUserSetup = builder.mSkipUserSetup;
        keepAccountMigrated = builder.mKeepAccountMigrated;

        validateFields();
    }

    private ProvisioningParams(Parcel in) {
        this(createBuilderFromPersistableBundle(
                PersistableBundlable.getPersistableBundleFromParcel(in)));
    }

    private void validateFields() {
        checkArgument(deviceAdminPackageName != null || deviceAdminComponentName != null);
    }

    @Override
    public PersistableBundle toPersistableBundle() {
        final PersistableBundle bundle = new PersistableBundle();

        bundle.putLong(TAG_PROVISIONING_ID, provisioningId);
        bundle.putString(EXTRA_PROVISIONING_TIME_ZONE, timeZone);
        bundle.putLong(EXTRA_PROVISIONING_LOCAL_TIME, localTime);
        bundle.putString(EXTRA_PROVISIONING_LOCALE, StoreUtils.localeToString(locale));
        putPersistableBundlableIfNotNull(bundle, TAG_WIFI_INFO, wifiInfo);
        bundle.putString(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, deviceAdminPackageName);
        bundle.putString(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                StoreUtils.componentNameToString(deviceAdminComponentName));
        bundle.putString(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_LABEL, deviceAdminLabel);
        bundle.putString(EXTRA_PROVISIONING_ORGANIZATION_NAME, organizationName);
        bundle.putString(EXTRA_PROVISIONING_SUPPORT_URL, supportUrl);
        bundle.putString(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_ICON_URI, deviceAdminIconFilePath);
        bundle.putPersistableBundle(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, accountToMigrate == null
                ? null : accountToPersistableBundle(accountToMigrate));
        bundle.putString(TAG_PROVISIONING_ACTION, provisioningAction);
        putIntegerIfNotNull(bundle, EXTRA_PROVISIONING_MAIN_COLOR, mainColor);
        putPersistableBundlableIfNotNull(bundle, TAG_PACKAGE_DOWNLOAD_INFO,
                deviceAdminDownloadInfo);
        putPersistableBundlableIfNotNull(bundle, EXTRA_PROVISIONING_DISCLAIMERS,
                disclaimersParam);
        bundle.putPersistableBundle(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, adminExtrasBundle);
        bundle.putBoolean(TAG_STARTED_BY_TRUSTED_SOURCE, startedByTrustedSource);
        bundle.putBoolean(TAG_IS_NFC, isNfc);
        bundle.putBoolean(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                leaveAllSystemAppsEnabled);
        bundle.putBoolean(EXTRA_PROVISIONING_SKIP_ENCRYPTION, skipEncryption);
        bundle.putBoolean(EXTRA_PROVISIONING_SKIP_USER_SETUP, skipUserSetup);
        bundle.putBoolean(EXTRA_PROVISIONING_SKIP_USER_CONSENT, skipUserConsent);
        bundle.putBoolean(EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION, keepAccountMigrated);
        return bundle;
    }

    /* package */ static ProvisioningParams fromPersistableBundle(PersistableBundle bundle) {
        return createBuilderFromPersistableBundle(bundle).build();
    }

    private static Builder createBuilderFromPersistableBundle(PersistableBundle bundle) {
        Builder builder = new Builder();
        builder.setProvisioningId(bundle.getLong(TAG_PROVISIONING_ID, DEFAULT_PROVISIONING_ID));
        builder.setTimeZone(bundle.getString(EXTRA_PROVISIONING_TIME_ZONE));
        builder.setLocalTime(bundle.getLong(EXTRA_PROVISIONING_LOCAL_TIME));
        builder.setLocale(getStringAttrFromPersistableBundle(bundle,
                EXTRA_PROVISIONING_LOCALE, StoreUtils::stringToLocale));
        builder.setWifiInfo(getObjectAttrFromPersistableBundle(bundle,
                TAG_WIFI_INFO, WifiInfo::fromPersistableBundle));
        builder.setDeviceAdminPackageName(bundle.getString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME));
        builder.setDeviceAdminComponentName(getStringAttrFromPersistableBundle(bundle,
                EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, StoreUtils::stringToComponentName));
        builder.setDeviceAdminLabel(bundle.getString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_LABEL));
        builder.setOrganizationName(bundle.getString(EXTRA_PROVISIONING_ORGANIZATION_NAME));
        builder.setSupportUrl(bundle.getString(EXTRA_PROVISIONING_SUPPORT_URL));
        builder.setDeviceAdminIconFilePath(bundle.getString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_ICON_URI));
        builder.setAccountToMigrate(getObjectAttrFromPersistableBundle(bundle,
                EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, StoreUtils::persistableBundleToAccount));
        builder.setProvisioningAction(bundle.getString(TAG_PROVISIONING_ACTION));
        builder.setMainColor(getIntegerAttrFromPersistableBundle(bundle,
                EXTRA_PROVISIONING_MAIN_COLOR));
        builder.setDeviceAdminDownloadInfo(getObjectAttrFromPersistableBundle(bundle,
                TAG_PACKAGE_DOWNLOAD_INFO, PackageDownloadInfo::fromPersistableBundle));
        builder.setDisclaimersParam(getObjectAttrFromPersistableBundle(bundle,
                EXTRA_PROVISIONING_DISCLAIMERS, DisclaimersParam::fromPersistableBundle));
        builder.setAdminExtrasBundle(bundle.getPersistableBundle(
                EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE));
        builder.setStartedByTrustedSource(bundle.getBoolean(TAG_STARTED_BY_TRUSTED_SOURCE));
        builder.setIsNfc(bundle.getBoolean(TAG_IS_NFC));
        builder.setSkipEncryption(bundle.getBoolean(EXTRA_PROVISIONING_SKIP_ENCRYPTION));
        builder.setLeaveAllSystemAppsEnabled(bundle.getBoolean(
                EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED));
        builder.setSkipUserSetup(bundle.getBoolean(EXTRA_PROVISIONING_SKIP_USER_SETUP));
        builder.setSkipUserConsent(bundle.getBoolean(EXTRA_PROVISIONING_SKIP_USER_CONSENT));
        builder.setKeepAccountMigrated(bundle.getBoolean(
                EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION));
        return builder;
    }

    @Override
    public String toString() {
        return "ProvisioningParams values: " + toPersistableBundle().toString();
    }

    /**
     * Saves the ProvisioningParams to the specified file.
     */
    public void save(File file) {
        ProvisionLogger.logd("Saving ProvisioningParams to " + file);
        try (FileOutputStream stream = new FileOutputStream(file)) {
            XmlSerializer serializer = new FastXmlSerializer();
            serializer.setOutput(stream, StandardCharsets.UTF_8.name());
            serializer.startDocument(null, true);
            serializer.startTag(null, TAG_PROVISIONING_PARAMS);
            toPersistableBundle().saveToXml(serializer);
            serializer.endTag(null, TAG_PROVISIONING_PARAMS);
            serializer.endDocument();
        } catch (IOException | XmlPullParserException e) {
            ProvisionLogger.loge("Caught exception while trying to save Provisioning Params to "
                    + " file " + file, e);
            file.delete();
        }
    }

    public void cleanUp() {
        if (disclaimersParam != null) {
            disclaimersParam.cleanUp();
        }
        if (deviceAdminIconFilePath != null) {
            new File(deviceAdminIconFilePath).delete();
        }
    }

    /**
     * Loads the ProvisioningParams From the specified file.
     */
    public static ProvisioningParams load(File file) {
        if (!file.exists()) {
            return null;
        }
        ProvisionLogger.logd("Loading ProvisioningParams from " + file);
        try (FileInputStream stream = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(stream, null);
            return load(parser);
        } catch (IOException | XmlPullParserException e) {
            ProvisionLogger.loge("Caught exception while trying to load the provisioning params"
                    + " from file " + file, e);
            return null;
        }
    }

    private static ProvisioningParams load(XmlPullParser parser) throws XmlPullParserException,
            IOException {
        int type;
        int outerDepth = parser.getDepth();
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
             if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                 continue;
             }
             String tag = parser.getName();
             switch (tag) {
                 case TAG_PROVISIONING_PARAMS:
                     return createBuilderFromPersistableBundle(
                             PersistableBundle.restoreFromXml(parser)).build();
             }
        }
        return new Builder().build();
    }

    public final static class Builder {
        private long mProvisioningId;
        private String mTimeZone;
        private long mLocalTime = DEFAULT_LOCAL_TIME;
        private Locale mLocale;
        private WifiInfo mWifiInfo;
        private String mDeviceAdminPackageName;
        private ComponentName mDeviceAdminComponentName;
        private String mDeviceAdminLabel;
        private String mOrganizationName;
        private String mSupportUrl;
        private String mDeviceAdminIconFilePath;
        private Account mAccountToMigrate;
        private String mProvisioningAction;
        private Integer mMainColor = DEFAULT_MAIN_COLOR;
        private PackageDownloadInfo mDeviceAdminDownloadInfo;
        private DisclaimersParam mDisclaimersParam;
        private PersistableBundle mAdminExtrasBundle;
        private boolean mStartedByTrustedSource = DEFAULT_STARTED_BY_TRUSTED_SOURCE;
        private boolean mIsNfc = DEFAULT_IS_NFC;
        private boolean mLeaveAllSystemAppsEnabled = DEFAULT_LEAVE_ALL_SYSTEM_APPS_ENABLED;
        private boolean mSkipEncryption = DEFAULT_EXTRA_PROVISIONING_SKIP_ENCRYPTION;
        private boolean mSkipUserConsent = DEFAULT_EXTRA_PROVISIONING_SKIP_USER_CONSENT;
        private boolean mSkipUserSetup = DEFAULT_SKIP_USER_SETUP;
        private boolean mKeepAccountMigrated = DEFAULT_EXTRA_PROVISIONING_KEEP_ACCOUNT_MIGRATED;

        public Builder setProvisioningId(long provisioningId) {
            mProvisioningId = provisioningId;
            return this;
        }

        public Builder setTimeZone(String timeZone) {
            mTimeZone = timeZone;
            return this;
        }

        public Builder setLocalTime(long localTime) {
            mLocalTime = localTime;
            return this;
        }

        public Builder setLocale(Locale locale) {
            mLocale = locale;
            return this;
        }

        public Builder setWifiInfo(WifiInfo wifiInfo) {
            mWifiInfo = wifiInfo;
            return this;
        }

        @Deprecated
        public Builder setDeviceAdminPackageName(String deviceAdminPackageName) {
            mDeviceAdminPackageName = deviceAdminPackageName;
            return this;
        }

        public Builder setDeviceAdminComponentName(ComponentName deviceAdminComponentName) {
            mDeviceAdminComponentName = deviceAdminComponentName;
            return this;
        }

        public Builder setDeviceAdminLabel(String deviceAdminLabel) {
            mDeviceAdminLabel = deviceAdminLabel;
            return this;
        }

        public Builder setOrganizationName(String organizationName) {
            mOrganizationName = organizationName;
            return this;
        }

        public Builder setSupportUrl(String supportUrl) {
            mSupportUrl = supportUrl;
            return this;
        }

        public Builder setDeviceAdminIconFilePath(String deviceAdminIconFilePath) {
            mDeviceAdminIconFilePath = deviceAdminIconFilePath;
            return this;
        }

        public Builder setAccountToMigrate(Account accountToMigrate) {
            mAccountToMigrate = accountToMigrate;
            return this;
        }

        public Builder setProvisioningAction(String provisioningAction) {
            mProvisioningAction = provisioningAction;
            return this;
        }

        public Builder setMainColor(Integer mainColor) {
            mMainColor = mainColor;
            return this;
        }

        public Builder setDeviceAdminDownloadInfo(PackageDownloadInfo deviceAdminDownloadInfo) {
            mDeviceAdminDownloadInfo = deviceAdminDownloadInfo;
            return this;
        }

        public Builder setDisclaimersParam(DisclaimersParam disclaimersParam) {
            mDisclaimersParam = disclaimersParam;
            return this;
        }

        public Builder setAdminExtrasBundle(PersistableBundle adminExtrasBundle) {
            mAdminExtrasBundle = adminExtrasBundle;
            return this;
        }

        public Builder setStartedByTrustedSource(boolean startedByTrustedSource) {
            mStartedByTrustedSource = startedByTrustedSource;
            return this;
        }

        public Builder setIsNfc(boolean isNfc) {
            mIsNfc = isNfc;
            return this;
        }


        public Builder setLeaveAllSystemAppsEnabled(boolean leaveAllSystemAppsEnabled) {
            mLeaveAllSystemAppsEnabled = leaveAllSystemAppsEnabled;
            return this;
        }

        public Builder setSkipEncryption(boolean skipEncryption) {
            mSkipEncryption = skipEncryption;
            return this;
        }

        public Builder setSkipUserConsent(boolean skipUserConsent) {
            mSkipUserConsent = skipUserConsent;
            return this;
        }

        public Builder setSkipUserSetup(boolean skipUserSetup) {
            mSkipUserSetup = skipUserSetup;
            return this;
        }

        public Builder setKeepAccountMigrated(boolean keepAccountMigrated) {
            mKeepAccountMigrated = keepAccountMigrated;
            return this;
        }

        public ProvisioningParams build() {
            return new ProvisioningParams(this);
        }

        public static Builder builder() {
            return new Builder();
        }
    }
}
