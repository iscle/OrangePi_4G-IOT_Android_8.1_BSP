/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.provisioning;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_PROVISIONING_ACTIVITY_TIME_MS;

import android.Manifest;
import android.Manifest.permission;
import android.app.Activity;
import android.app.DialogFragment;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.annotation.VisibleForTesting;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.DialogBuilder;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SetupGlifLayoutActivity;
import com.android.managedprovisioning.common.SimpleDialog;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.CustomizationParams;
import com.android.managedprovisioning.model.ProvisioningParams;
import java.util.List;

/**
 * Progress activity shown whilst provisioning is ongoing.
 *
 * <p>This activity registers for updates of the provisioning process from the
 * {@link ProvisioningManager}. It shows progress updates as provisioning progresses and handles
 * showing of cancel and error dialogs.</p>
 */
public class ProvisioningActivity extends SetupGlifLayoutActivity
        implements SimpleDialog.SimpleDialogListener, ProvisioningManagerCallback {

    private static final String KEY_PROVISIONING_STARTED = "ProvisioningStarted";

    private static final String ERROR_DIALOG_OK = "ErrorDialogOk";
    private static final String ERROR_DIALOG_RESET = "ErrorDialogReset";
    private static final String CANCEL_PROVISIONING_DIALOG_OK = "CancelProvisioningDialogOk";
    private static final String CANCEL_PROVISIONING_DIALOG_RESET = "CancelProvisioningDialogReset";

    private ProvisioningParams mParams;
    private ProvisioningManager mProvisioningManager;

    public ProvisioningActivity() {
        this(null, new Utils());
    }

    @VisibleForTesting
    public ProvisioningActivity(ProvisioningManager provisioningManager, Utils utils) {
        super(utils);
        mProvisioningManager = provisioningManager;
    }

    // Lazily initialize ProvisioningManager, since we can't call in ProvisioningManager.getInstance
    // in constructor as base context is not available in constructor
    private ProvisioningManager getProvisioningManager() {
        if (mProvisioningManager == null) {
            mProvisioningManager = ProvisioningManager.getInstance(this);
        }
        return mProvisioningManager;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mParams = getIntent().getParcelableExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS);
        initializeUi(mParams);

        if (savedInstanceState == null
                || !savedInstanceState.getBoolean(KEY_PROVISIONING_STARTED)) {
            getProvisioningManager().maybeStartProvisioning(mParams);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_PROVISIONING_STARTED, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isAnyDialogAdded()) {
            getProvisioningManager().registerListener(this);
        }
    }

    private boolean isAnyDialogAdded() {
        return isDialogAdded(ERROR_DIALOG_OK)
                || isDialogAdded(ERROR_DIALOG_RESET)
                || isDialogAdded(CANCEL_PROVISIONING_DIALOG_OK)
                || isDialogAdded(CANCEL_PROVISIONING_DIALOG_RESET);
    }

    @Override
    public void onPause() {
        getProvisioningManager().unregisterListener(this);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        // if EXTRA_PROVISIONING_SKIP_USER_CONSENT is specified, don't allow user to cancel
        if (mParams.skipUserConsent) {
            return;
        }

        showCancelProvisioningDialog();
    }

    @Override
    public void preFinalizationCompleted() {
        ProvisionLogger.logi("ProvisioningActivity pre-finalization completed");
        setResult(Activity.RESULT_OK);
        maybeLaunchNfcUserSetupCompleteIntent();
        finish();
    }

    private void maybeLaunchNfcUserSetupCompleteIntent() {
        if (mParams != null && mParams.isNfc) {
            // Start SetupWizard to complete the intent.
            final Intent intent = new Intent(DevicePolicyManager.ACTION_STATE_USER_SETUP_COMPLETE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            final PackageManager pm = getPackageManager();
            List<ResolveInfo> ris = pm.queryIntentActivities(intent, 0);

            // Look for the first legitimate component protected by the permission
            ComponentName targetComponent = null;
            for (ResolveInfo ri : ris) {
                if (ri.activityInfo == null) {
                    continue;
                }
                if (!permission.BIND_DEVICE_ADMIN.equals(ri.activityInfo.permission)) {
                    ProvisionLogger.loge("Component " + ri.activityInfo.getComponentName()
                            + " is not protected by " + permission.BIND_DEVICE_ADMIN);
                } else if (pm.checkPermission(permission.DISPATCH_PROVISIONING_MESSAGE,
                        ri.activityInfo.packageName) != PackageManager.PERMISSION_GRANTED) {
                    ProvisionLogger.loge("Package " + ri.activityInfo.packageName
                            + " does not have " + permission.DISPATCH_PROVISIONING_MESSAGE);
                } else {
                    targetComponent = ri.activityInfo.getComponentName();
                    break;
                }
            }

            if (targetComponent == null) {
                ProvisionLogger.logw("No activity accepts intent ACTION_STATE_USER_SETUP_COMPLETE");
                return;
            }

            intent.setComponent(targetComponent);
            startActivity(intent);
            ProvisionLogger.logi("Launched ACTION_STATE_USER_SETUP_COMPLETE with component "
                    + targetComponent);
        }
    }

    @Override
    public void progressUpdate(int progressMessage) {
    }

    @Override
    public void error(int titleId, int messageId, boolean resetRequired) {
        SimpleDialog.Builder dialogBuilder = new SimpleDialog.Builder()
                .setTitle(titleId)
                .setMessage(messageId)
                .setCancelable(false)
                .setPositiveButtonMessage(resetRequired
                        ? R.string.reset : R.string.device_owner_error_ok);

        showDialog(dialogBuilder, resetRequired ? ERROR_DIALOG_RESET : ERROR_DIALOG_OK);
    }

    @Override
    protected void showDialog(DialogBuilder builder, String tag) {
        // Whenever a dialog is shown, stop listening for further updates
        getProvisioningManager().unregisterListener(this);
        super.showDialog(builder, tag);
    }

    @Override
    protected int getMetricsCategory() {
        return PROVISIONING_PROVISIONING_ACTIVITY_TIME_MS;
    }

    private void showCancelProvisioningDialog() {
        final boolean isDoProvisioning = getUtils().isDeviceOwnerAction(mParams.provisioningAction);
        final String dialogTag = isDoProvisioning ? CANCEL_PROVISIONING_DIALOG_RESET
                : CANCEL_PROVISIONING_DIALOG_OK;
        final int positiveResId = isDoProvisioning ? R.string.reset
                : R.string.profile_owner_cancel_ok;
        final int negativeResId = isDoProvisioning ? R.string.device_owner_cancel_cancel
                : R.string.profile_owner_cancel_cancel;
        final int dialogMsgResId = isDoProvisioning
                ? R.string.this_will_reset_take_back_first_screen
                : R.string.profile_owner_cancel_message;

        SimpleDialog.Builder dialogBuilder = new SimpleDialog.Builder()
                .setCancelable(false)
                .setMessage(dialogMsgResId)
                .setNegativeButtonMessage(negativeResId)
                .setPositiveButtonMessage(positiveResId);
        if (isDoProvisioning) {
            dialogBuilder.setTitle(R.string.stop_setup_reset_device_question);
        }

        showDialog(dialogBuilder, dialogTag);
    }

    private void onProvisioningAborted() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    @Override
    public void onNegativeButtonClick(DialogFragment dialog) {
        switch (dialog.getTag()) {
            case CANCEL_PROVISIONING_DIALOG_OK:
            case CANCEL_PROVISIONING_DIALOG_RESET:
                dialog.dismiss();
                break;
            default:
                SimpleDialog.throwButtonClickHandlerNotImplemented(dialog);
        }
        getProvisioningManager().registerListener(this);
    }

    @Override
    public void onPositiveButtonClick(DialogFragment dialog) {
        switch (dialog.getTag()) {
            case CANCEL_PROVISIONING_DIALOG_OK:
                getProvisioningManager().cancelProvisioning();
                onProvisioningAborted();
                break;
            case CANCEL_PROVISIONING_DIALOG_RESET:
                getUtils().sendFactoryResetBroadcast(this, "DO provisioning cancelled by user");
                onProvisioningAborted();
                break;
            case ERROR_DIALOG_OK:
                onProvisioningAborted();
                break;
            case ERROR_DIALOG_RESET:
                getUtils().sendFactoryResetBroadcast(this, "Error during DO provisioning");
                onProvisioningAborted();
                break;
            default:
                SimpleDialog.throwButtonClickHandlerNotImplemented(dialog);
        }
    }

    private void initializeUi(ProvisioningParams params) {
        final boolean isDoProvisioning = getUtils().isDeviceOwnerAction(params.provisioningAction);
        final int headerResId = isDoProvisioning ? R.string.setup_work_device
                : R.string.setting_up_workspace;
        final int titleResId = isDoProvisioning ? R.string.setup_device_progress
                : R.string.setup_profile_progress;

        initializeLayoutParams(R.layout.progress, headerResId, true,
                CustomizationParams.createInstance(mParams, this, mUtils).statusBarColor);
        setTitle(titleResId);
    }
}