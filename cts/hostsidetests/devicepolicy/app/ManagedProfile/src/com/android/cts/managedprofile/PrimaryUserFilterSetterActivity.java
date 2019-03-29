package com.android.cts.managedprofile;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import static com.android.cts.managedprofile.BaseManagedProfileTest.ADMIN_RECEIVER_COMPONENT;

/**
 * Class that sets the cross-profile intent filters required to test intent filtering from
 * the primary profile to the managed one.
 */
public class PrimaryUserFilterSetterActivity extends Activity {

    public static final String TAG = PrimaryUserFilterSetterActivity.class.getName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PackageManager packageManager = getPackageManager();
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager)
                getSystemService(Context.DEVICE_POLICY_SERVICE);
        IntentFilter testIntentFilter = new IntentFilter();
        testIntentFilter.addAction(PrimaryUserActivity.ACTION);
        devicePolicyManager.addCrossProfileIntentFilter(ADMIN_RECEIVER_COMPONENT,
                testIntentFilter, DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);

        testIntentFilter = new IntentFilter();
        testIntentFilter.addAction(ManagedProfileActivity.ACTION);
        devicePolicyManager.addCrossProfileIntentFilter(ADMIN_RECEIVER_COMPONENT,
                testIntentFilter, DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);

        testIntentFilter = new IntentFilter();
        testIntentFilter.addAction(AllUsersActivity.ACTION);
        devicePolicyManager.addCrossProfileIntentFilter(ADMIN_RECEIVER_COMPONENT,
                testIntentFilter, DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);
        Log.i(TAG, "Roger that!");
    }
}