package com.android.car.messenger;

import android.annotation.Nullable;
import android.app.Activity;
import android.os.Bundle;

/**
 * No-op Activity that only exists in-order to have an entry in the manifest with SMS specific
 * intent-filter.
 * <p>
 * We need the manifest entry so that PackageManager will grant this pre-installed app SMS related
 * permissions. See DefaultPermissionGrantPolicy.grantDefaultSystemHandlerPermissions().
 */
public class MessengerActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
    }
}
