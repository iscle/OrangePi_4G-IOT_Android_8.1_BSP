package com.android.cts.verifier.managedprovisioning;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

public class ByodFlowTestHelper {
    private Context mContext;
    private PackageManager mPackageManager;

    public ByodFlowTestHelper(Context context) {
        this.mContext = context;
        this.mPackageManager = mContext.getPackageManager();
    }

    public void setup() {
        setComponentsEnabledState(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
    }

    /**
     * Clean up things. This has to be working even it is called multiple times.
     */
    public void tearDown() {
        Utils.requestDeleteManagedProfile(mContext);
        setComponentsEnabledState(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
    }

    /**
     * Disable or enable app components in the current profile. When they are disabled only the
     * counterpart in the other profile can respond (via cross-profile intent filter).
     *
     * @param enabledState {@link PackageManager#COMPONENT_ENABLED_STATE_DISABLED} or
     *                     {@link PackageManager#COMPONENT_ENABLED_STATE_DEFAULT}
     */
    private void setComponentsEnabledState(final int enabledState) {
        final String[] components = {
                ByodHelperActivity.class.getName(),
                WorkStatusTestActivity.class.getName(),
                PermissionLockdownTestActivity.ACTIVITY_ALIAS,
                AuthenticationBoundKeyTestActivity.class.getName(),
                VpnTestActivity.class.getName(),
                AlwaysOnVpnSettingsTestActivity.class.getName(),
                RecentsRedactionActivity.class.getName(),
                CommandReceiverActivity.class.getName(),
                SetSupportMessageActivity.class.getName()
        };
        for (String component : components) {
            mPackageManager.setComponentEnabledSetting(new ComponentName(mContext, component),
                    enabledState, PackageManager.DONT_KILL_APP);
        }
    }
}
