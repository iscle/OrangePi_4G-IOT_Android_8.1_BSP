package com.android.tv.testing.uihelper;

import static com.android.tv.testing.uihelper.UiDeviceAsserts.waitForCondition;
import static junit.framework.TestCase.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;
import android.util.Log;

import com.android.tv.testing.Utils;

import junit.framework.Assert;

/**
 * Helper for testing the Live TV Application.
 */
public class LiveChannelsUiDeviceHelper extends BaseUiDeviceHelper {
    private static final String TAG = "LiveChannelsUiDevice";
    private static final int APPLICATION_START_TIMEOUT_MSEC = 5000;

    private final Context mContext;

    public LiveChannelsUiDeviceHelper(UiDevice uiDevice, Resources targetResources,
            Context context) {
        super(uiDevice, targetResources);
        mContext = context;
    }

    public void assertAppStarted() {
        assertTrue("TvActivity should be enabled.", Utils.isTvActivityEnabled(mContext));
        Intent intent = mContext.getPackageManager()
                .getLaunchIntentForPackage(Constants.TV_APP_PACKAGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);    // Clear out any previous instances
        mContext.startActivity(intent);
        // Wait for idle state before checking the channel banner because waitForCondition() has
        // timeout.
        mUiDevice.waitForIdle();
        // Make sure that the activity is resumed.
        waitForCondition(mUiDevice, Until.hasObject(Constants.TV_VIEW));

        Assert.assertTrue(Constants.TV_APP_PACKAGE + " did not start", mUiDevice
                .wait(Until.hasObject(By.pkg(Constants.TV_APP_PACKAGE).depth(0)),
                        APPLICATION_START_TIMEOUT_MSEC));
        BySelector welcome = ByResource.id(mTargetResources, com.android.tv.R.id.intro);
        if (mUiDevice.hasObject(welcome)) {
            Log.i(TAG, "Welcome screen shown. Clearing dialog by pressing back");
            mUiDevice.pressBack();
        }
    }

    public void assertAppStopped() {
        while(mUiDevice.hasObject(By.pkg(Constants.TV_APP_PACKAGE).depth(0))) {
            mUiDevice.pressBack();
            mUiDevice.waitForIdle();
        }
    }
}