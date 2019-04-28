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

package com.android.managedprovisioning.preprovisioning;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;

import android.annotation.NonNull;
import android.app.Activity;
import android.app.DialogFragment;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.ClickableSpanFactory;
import com.android.managedprovisioning.common.LogoUtils;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SetupGlifLayoutActivity;
import com.android.managedprovisioning.common.SimpleDialog;
import com.android.managedprovisioning.common.StringConcatenator;
import com.android.managedprovisioning.common.TouchTargetEnforcer;
import com.android.managedprovisioning.model.CustomizationParams;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.preprovisioning.anim.BenefitsAnimation;
import com.android.managedprovisioning.preprovisioning.anim.ColorMatcher;
import com.android.managedprovisioning.preprovisioning.anim.SwiperThemeMatcher;
import com.android.managedprovisioning.preprovisioning.terms.TermsActivity;
import com.android.managedprovisioning.provisioning.ProvisioningActivity;
import java.util.ArrayList;
import java.util.List;

public class PreProvisioningActivity extends SetupGlifLayoutActivity implements
        SimpleDialog.SimpleDialogListener, PreProvisioningController.Ui {
    private static final List<Integer> SLIDE_CAPTIONS = createImmutableList(
            R.string.info_anim_title_0,
            R.string.info_anim_title_1,
            R.string.info_anim_title_2);
    private static final List<Integer> SLIDE_CAPTIONS_COMP = createImmutableList(
            R.string.info_anim_title_0,
            R.string.one_place_for_work_apps,
            R.string.info_anim_title_2);

    private static final int ENCRYPT_DEVICE_REQUEST_CODE = 1;
    @VisibleForTesting
    protected static final int PROVISIONING_REQUEST_CODE = 2;
    private static final int WIFI_REQUEST_CODE = 3;
    private static final int CHANGE_LAUNCHER_REQUEST_CODE = 4;

    // Note: must match the constant defined in HomeSettings
    private static final String EXTRA_SUPPORT_MANAGED_PROFILES = "support_managed_profiles";
    private static final String SAVED_PROVISIONING_PARAMS = "saved_provisioning_params";

    private static final String ERROR_AND_CLOSE_DIALOG = "PreProvErrorAndCloseDialog";
    private static final String BACK_PRESSED_DIALOG = "PreProvBackPressedDialog";
    private static final String CANCELLED_CONSENT_DIALOG = "PreProvCancelledConsentDialog";
    private static final String LAUNCHER_INVALID_DIALOG = "PreProvCurrentLauncherInvalidDialog";
    private static final String DELETE_MANAGED_PROFILE_DIALOG = "PreProvDeleteManagedProfileDialog";

    private PreProvisioningController mController;
    private ControllerProvider mControllerProvider;
    private final AccessibilityContextMenuMaker mContextMenuMaker;
    private BenefitsAnimation mBenefitsAnimation;
    private ClickableSpanFactory mClickableSpanFactory;
    private TouchTargetEnforcer mTouchTargetEnforcer;

    public PreProvisioningActivity() {
        this(activity -> new PreProvisioningController(activity, activity), null);
    }

    @VisibleForTesting
    public PreProvisioningActivity(ControllerProvider controllerProvider,
            AccessibilityContextMenuMaker contextMenuMaker) {
        mControllerProvider = controllerProvider;
        mContextMenuMaker =
                contextMenuMaker != null ? contextMenuMaker : new AccessibilityContextMenuMaker(
                        this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mClickableSpanFactory = new ClickableSpanFactory(getColor(R.color.blue));
        mTouchTargetEnforcer = new TouchTargetEnforcer(getResources().getDisplayMetrics().density);
        mController = mControllerProvider.getInstance(this);
        ProvisioningParams params = savedInstanceState == null ? null
                : savedInstanceState.getParcelable(SAVED_PROVISIONING_PARAMS);
        mController.initiateProvisioning(getIntent(), params, getCallingPackage());
    }

    @Override
    public void finish() {
        // The user has backed out of provisioning, so we perform the necessary clean up steps.
        LogoUtils.cleanUp(this);
        ProvisioningParams params = mController.getParams();
        if (params != null) {
            params.cleanUp();
        }
        EncryptionController.getInstance(this).cancelEncryptionReminder();
        super.finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(SAVED_PROVISIONING_PARAMS, mController.getParams());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case ENCRYPT_DEVICE_REQUEST_CODE:
                if (resultCode == RESULT_CANCELED) {
                    ProvisionLogger.loge("User canceled device encryption.");
                }
                break;
            case PROVISIONING_REQUEST_CODE:
                setResult(resultCode);
                finish();
                break;
            case CHANGE_LAUNCHER_REQUEST_CODE:
                mController.continueProvisioningAfterUserConsent();
                break;
            case WIFI_REQUEST_CODE:
                if (resultCode == RESULT_CANCELED) {
                    ProvisionLogger.loge("User canceled wifi picking.");
                } else if (resultCode == RESULT_OK) {
                    ProvisionLogger.logd("Wifi request result is OK");
                }
                mController.initiateProvisioning(getIntent(), null /* cached params */,
                        getCallingPackage());
                break;
            default:
                ProvisionLogger.logw("Unknown result code :" + resultCode);
                break;
        }
    }

    @Override
    public void showErrorAndClose(Integer titleId, int messageId, String logText) {
        ProvisionLogger.loge(logText);

        SimpleDialog.Builder dialogBuilder = new SimpleDialog.Builder()
                .setTitle(titleId)
                .setMessage(messageId)
                .setCancelable(false)
                .setPositiveButtonMessage(R.string.device_owner_error_ok);
        showDialog(dialogBuilder, ERROR_AND_CLOSE_DIALOG);
    }

    @Override
    public void onNegativeButtonClick(DialogFragment dialog) {
        switch (dialog.getTag()) {
            case CANCELLED_CONSENT_DIALOG:
            case BACK_PRESSED_DIALOG:
                // user chose to continue. Do nothing
                break;
            case LAUNCHER_INVALID_DIALOG:
                dialog.dismiss();
                break;
            case DELETE_MANAGED_PROFILE_DIALOG:
                setResult(Activity.RESULT_CANCELED);
                finish();
                break;
            default:
                SimpleDialog.throwButtonClickHandlerNotImplemented(dialog);
        }
    }

    @Override
    public void onPositiveButtonClick(DialogFragment dialog) {
        switch (dialog.getTag()) {
            case ERROR_AND_CLOSE_DIALOG:
            case BACK_PRESSED_DIALOG:
                // Close activity
                setResult(Activity.RESULT_CANCELED);
                // TODO: Move logging to close button, if we finish provisioning there.
                mController.logPreProvisioningCancelled();
                finish();
                break;
            case CANCELLED_CONSENT_DIALOG:
                mUtils.sendFactoryResetBroadcast(this, "Device owner setup cancelled");
                break;
            case LAUNCHER_INVALID_DIALOG:
                requestLauncherPick();
                break;
            case DELETE_MANAGED_PROFILE_DIALOG:
                DeleteManagedProfileDialog d = (DeleteManagedProfileDialog) dialog;
                mController.removeUser(d.getUserId());
                // TODO: refactor as evil - logic should be less spread out
                // Check if we are in the middle of silent provisioning and were got blocked by an
                // existing user profile. If so, we can now resume.
                mController.checkResumeSilentProvisioning();
                break;
            default:
                SimpleDialog.throwButtonClickHandlerNotImplemented(dialog);
        }
    }

    @Override
    public void requestEncryption(ProvisioningParams params) {
        Intent encryptIntent = new Intent(this, EncryptDeviceActivity.class);
        encryptIntent.putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, params);
        startActivityForResult(encryptIntent, ENCRYPT_DEVICE_REQUEST_CODE);
    }

    @Override
    public void requestWifiPick() {
        startActivityForResult(mUtils.getWifiPickIntent(), WIFI_REQUEST_CODE);
    }

    @Override
    public void showCurrentLauncherInvalid() {
        SimpleDialog.Builder dialogBuilder = new SimpleDialog.Builder()
                .setCancelable(false)
                .setTitle(R.string.change_device_launcher)
                .setMessage(R.string.launcher_app_cant_be_used_by_work_profile)
                .setNegativeButtonMessage(R.string.cancel_provisioning)
                .setPositiveButtonMessage(R.string.pick_launcher);
        showDialog(dialogBuilder, LAUNCHER_INVALID_DIALOG);
    }

    private void requestLauncherPick() {
        Intent changeLauncherIntent = new Intent(Settings.ACTION_HOME_SETTINGS);
        changeLauncherIntent.putExtra(EXTRA_SUPPORT_MANAGED_PROFILES, true);
        startActivityForResult(changeLauncherIntent, CHANGE_LAUNCHER_REQUEST_CODE);
    }

    public void startProvisioning(int userId, ProvisioningParams params) {
        Intent intent = new Intent(this, ProvisioningActivity.class);
        intent.putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, params);
        startActivityForResultAsUser(intent, PROVISIONING_REQUEST_CODE, new UserHandle(userId));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    public void initiateUi(int layoutId, int titleId, String packageLabel, Drawable packageIcon,
            boolean isProfileOwnerProvisioning, boolean isComp, List<String> termsHeaders,
            CustomizationParams customization) {
        if (isProfileOwnerProvisioning) {
            // setting a theme so that the animation swiper matches the mainColor
            // needs to happen before {@link Activity#setContentView}
            setTheme(new SwiperThemeMatcher(this,
                    new ColorMatcher()) // TODO: introduce DI framework
                    .findTheme(customization.swiperColor));
        }

        initializeLayoutParams(
                layoutId,
                isProfileOwnerProvisioning ? null : R.string.set_up_your_device,
                false /* progress bar */,
                customization.statusBarColor);

        // set up the 'accept and continue' button
        Button nextButton = (Button) findViewById(R.id.next_button);
        nextButton.setOnClickListener(v -> {
            ProvisionLogger.logi("Next button (next_button) is clicked.");
            mController.continueProvisioningAfterUserConsent();
        });
        nextButton.setBackgroundTintList(ColorStateList.valueOf(customization.buttonColor));
        if (mUtils.isBrightColor(customization.buttonColor)) {
            nextButton.setTextColor(getColor(R.color.gray_button_text));
        }

        // set the activity title
        setTitle(titleId);

        // set up terms headers
        String headers = new StringConcatenator(getResources()).join(termsHeaders);

        // initiate UI for MP / DO
        if (isProfileOwnerProvisioning) {
            initiateUIProfileOwner(headers, isComp);
        } else {
            initiateUIDeviceOwner(packageLabel, packageIcon, headers, customization);
        }
    }

    private void initiateUIProfileOwner(@NonNull String termsHeaders, boolean isComp) {
        // set up the cancel button
        Button cancelButton = (Button) findViewById(R.id.close_button);
        cancelButton.setOnClickListener(v -> {
            ProvisionLogger.logi("Close button (close_button) is clicked.");
            PreProvisioningActivity.this.onBackPressed();
        });

        int messageId = isComp ? R.string.profile_owner_info_comp : R.string.profile_owner_info;
        int messageWithTermsId = isComp ? R.string.profile_owner_info_with_terms_headers_comp
                : R.string.profile_owner_info_with_terms_headers;

        // set the short info text
        TextView shortInfo = (TextView) findViewById(R.id.profile_owner_short_info);
        shortInfo.setText(termsHeaders.isEmpty()
                ? getString(messageId)
                : getResources().getString(messageWithTermsId, termsHeaders));

        // set up show terms button
        View viewTermsButton = findViewById(R.id.show_terms_button);
        viewTermsButton.setOnClickListener(this::startViewTermsActivity);
        mTouchTargetEnforcer.enforce(viewTermsButton, (View) viewTermsButton.getParent());

        // show the intro animation
        mBenefitsAnimation = new BenefitsAnimation(
                this,
                isComp
                        ? SLIDE_CAPTIONS_COMP
                        : SLIDE_CAPTIONS,
                isComp
                        ? R.string.comp_profile_benefits_description
                        : R.string.profile_benefits_description);
    }

    private void initiateUIDeviceOwner(String packageName, Drawable packageIcon,
            @NonNull String termsHeaders, CustomizationParams customization) {
        // short terms info text with clickable 'view terms' link
        TextView shortInfoText = (TextView) findViewById(R.id.device_owner_terms_info);
        shortInfoText.setText(assembleDOTermsMessage(termsHeaders, customization.orgName));
        shortInfoText.setMovementMethod(LinkMovementMethod.getInstance()); // make clicks work
        mContextMenuMaker.registerWithActivity(shortInfoText);

        // if you have any questions, contact your device's provider
        //
        // TODO: refactor complex localized string assembly to an abstraction http://b/34288292
        // there is a bit of copy-paste, and some details easy to forget (e.g. setMovementMethod)
        if (customization.supportUrl != null) {
            TextView info = (TextView) findViewById(R.id.device_owner_provider_info);
            info.setVisibility(View.VISIBLE);
            String deviceProvider = getString(R.string.organization_admin);
            String contactDeviceProvider = getString(R.string.contact_device_provider,
                    deviceProvider);
            SpannableString spannableString = new SpannableString(contactDeviceProvider);

            Intent intent = WebActivity.createIntent(this, customization.supportUrl,
                    customization.statusBarColor);
            if (intent != null) {
                ClickableSpan span = mClickableSpanFactory.create(intent);
                int startIx = contactDeviceProvider.indexOf(deviceProvider);
                int endIx = startIx + deviceProvider.length();
                spannableString.setSpan(span, startIx, endIx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                info.setMovementMethod(LinkMovementMethod.getInstance()); // make clicks work
            }

            info.setText(spannableString);
            mContextMenuMaker.registerWithActivity(info);
        }

        // set up DPC icon and label
        setDpcIconAndLabel(packageName, packageIcon, customization.orgName);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v instanceof TextView) {
            mContextMenuMaker.populateMenuContent(menu, (TextView) v);
        }
    }

    private void startViewTermsActivity(@SuppressWarnings("unused") View view) {
        startActivity(createViewTermsIntent());
    }

    private Intent createViewTermsIntent() {
        return new Intent(this, TermsActivity.class).putExtra(
                ProvisioningParams.EXTRA_PROVISIONING_PARAMS, mController.getParams());
    }

    // TODO: refactor complex localized string assembly to an abstraction http://b/34288292
    // there is a bit of copy-paste, and some details easy to forget (e.g. setMovementMethod)
    private Spannable assembleDOTermsMessage(@NonNull String termsHeaders, String orgName) {
        String linkText = getString(R.string.view_terms);

        if (TextUtils.isEmpty(orgName)) {
            orgName = getString(R.string.your_organization_middle);
        }
        String messageText = termsHeaders.isEmpty()
                ? getString(R.string.device_owner_info, orgName, linkText)
                : getString(R.string.device_owner_info_with_terms_headers, orgName, termsHeaders,
                        linkText);

        Spannable result = new SpannableString(messageText);
        int start = messageText.indexOf(linkText);

        ClickableSpan span = mClickableSpanFactory.create(createViewTermsIntent());
        result.setSpan(span, start, start + linkText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return result;
    }

    private void setDpcIconAndLabel(@NonNull String appName, Drawable packageIcon, String orgName) {
        if (packageIcon == null || TextUtils.isEmpty(appName)) {
            return;
        }

        // make a container with all parts of DPC app description visible
        findViewById(R.id.intro_device_owner_app_info_container).setVisibility(View.VISIBLE);

        if (TextUtils.isEmpty(orgName)) {
            orgName = getString(R.string.your_organization_beginning);
        }
        String message = getString(R.string.your_org_app_used, orgName);
        TextView appInfoText = (TextView) findViewById(R.id.device_owner_app_info_text);
        appInfoText.setText(message);

        ImageView imageView = (ImageView) findViewById(R.id.device_manager_icon_view);
        imageView.setImageDrawable(packageIcon);
        imageView.setContentDescription(getResources().getString(R.string.mdm_icon_label, appName));

        TextView deviceManagerName = (TextView) findViewById(R.id.device_manager_name);
        deviceManagerName.setText(appName);
    }

    @Override
    public void showDeleteManagedProfileDialog(ComponentName mdmPackageName, String domainName,
            int userId) {
        showDialog(() -> DeleteManagedProfileDialog.newInstance(userId,
                mdmPackageName, domainName), DELETE_MANAGED_PROFILE_DIALOG);
    }

    @Override
    public void onBackPressed() {
        mController.logPreProvisioningCancelled();
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBenefitsAnimation != null) {
            mBenefitsAnimation.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mBenefitsAnimation != null) {
            mBenefitsAnimation.stop();
        }
    }

    private static List<Integer> createImmutableList(int... values) {
        if (values == null || values.length == 0) {
            return emptyList();
        }
        List<Integer> result = new ArrayList<>(values.length);
        for (int value : values) {
            result.add(value);
        }
        return unmodifiableList(result);
    }

    /**
     * Constructs {@link PreProvisioningController} for a given {@link PreProvisioningActivity}
     */
    interface ControllerProvider {
        /**
         * Constructs {@link PreProvisioningController} for a given {@link PreProvisioningActivity}
         */
        PreProvisioningController getInstance(PreProvisioningActivity activity);
    }
}