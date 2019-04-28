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

package android.system.helpers;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.system.helpers.ActivityHelper;
import android.system.helpers.DeviceHelper;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import junit.framework.Assert;

/**
 * Implement common helper methods for account.
 */
public class AccountHelper {
    private static final String TAG = AccountHelper.class.getSimpleName();
    public static final int TIMEOUT = 1000;
    private static AccountHelper sInstance = null;
    private Context mContext = null;
    private UiDevice mDevice = null;
    private ActivityHelper mActivityHelper = null;
    private DeviceHelper mDeviceHelper = null;

    public AccountHelper() {
        mContext = InstrumentationRegistry.getTargetContext();
        mActivityHelper = ActivityHelper.getInstance();
        mDeviceHelper = DeviceHelper.getInstance();
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    public static AccountHelper getInstance() {
        if (sInstance == null) {
            sInstance = new AccountHelper();
        }
        return sInstance;
    }

    /**
     * Checks whether a google account has been enabled in device for backup
     * @return true/false
     * @throws InterruptedException
     */
    public boolean hasDeviceBackupAccount() throws InterruptedException {
        mActivityHelper.launchIntent(android.provider.Settings.ACTION_PRIVACY_SETTINGS);
        dismissInitalDialogs();
        Pattern pattern = Pattern.compile("Backup account", Pattern.CASE_INSENSITIVE);
        if (mDeviceHelper.isNexusExperienceDevice()) {
          pattern = Pattern.compile("Device backup", Pattern.CASE_INSENSITIVE);
        }
        UiObject2 deviceBackup = mDevice.wait(Until.findObject(By.text(pattern)),
                TIMEOUT * 5);
        if (deviceBackup!=null){
            String backupAcct = deviceBackup.getParent().getChildren().get(1).getText();
            if (backupAcct.equals(getRegisteredGoogleAccountOnDevice())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get registered accounts ensures there is at least one account registered returns the google
     * account name
     * @return The registered gogole/gmail account on device
     */
    public String getRegisteredGoogleAccountOnDevice() {
        Account[] accounts = AccountManager.get(mContext).getAccounts();
        Assert.assertTrue("Device doesn't have any account registered", accounts.length >= 1);
        for (int i = 0; i < accounts.length; ++i) {
            if (accounts[i].type.equals("com.google")) {
                return accounts[i].name;
            }
        }
        throw new RuntimeException("The device is not registered with a google account");
    }

    private void dismissInitalDialogs() throws InterruptedException{
        UiObject2 backupDialog = mDevice.wait(
                Until.findObject(By.text("Backup & reset")),
                TIMEOUT);
        if (backupDialog!=null){
            backupDialog.click();
            Thread.sleep(TIMEOUT);
            UiObject2 alwaysBtn = mDevice.wait(
                    Until.findObject(By.res("android","button_always")),
                    TIMEOUT);
            if (alwaysBtn!=null){
                alwaysBtn.click();
            }
        }
        Thread.sleep(TIMEOUT);
    }
}
