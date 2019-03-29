package com.android.cts.verifier.nfc.hce;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.CardEmulation;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

@TargetApi(19)
public abstract class BaseEmulatorActivity extends PassFailButtons.Activity {
    static final String TAG = "BaseEmulatorActivity";
    NfcAdapter mAdapter;
    CardEmulation mCardEmulation;
    ProgressDialog mSetupDialog;
    ComponentName mMakingDefault;

    final ArrayList<ComponentName> SERVICES = new ArrayList<ComponentName>(
            Arrays.asList(
            PaymentService1.COMPONENT,
            PaymentService2.COMPONENT,
            TransportService1.COMPONENT,
            TransportService2.COMPONENT,
            AccessService.COMPONENT,
            ThroughputService.COMPONENT,
            OffHostService.COMPONENT,
            PaymentServiceDynamicAids.COMPONENT,
            PrefixPaymentService1.COMPONENT,
            PrefixPaymentService2.COMPONENT,
            PrefixTransportService1.COMPONENT,
            PrefixTransportService2.COMPONENT,
            PrefixAccessService.COMPONENT,
            LargeNumAidsService.COMPONENT)
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAdapter = NfcAdapter.getDefaultAdapter(this);
        mCardEmulation = CardEmulation.getInstance(mAdapter);
        IntentFilter filter = new IntentFilter(HceUtils.ACTION_APDU_SEQUENCE_COMPLETE);
        registerReceiver(mReceiver, filter);
    }

    abstract void onServicesSetup(boolean result);

    abstract void onApduSequenceComplete(ComponentName component, long duration);

    void onApduSequenceError() {

    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    final void setupServices(Context context, ComponentName... components) {
        mSetupDialog = new ProgressDialog(context);
        new SetupServicesTask().execute(components);
    }

    final boolean makePaymentDefault(final ComponentName defaultComponent, int stringId) {
        if (!mCardEmulation.isDefaultServiceForCategory(defaultComponent,
                CardEmulation.CATEGORY_PAYMENT)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Note");
            builder.setMessage(stringId);
            mMakingDefault = defaultComponent;
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                Intent changeDefault = new Intent(CardEmulation.ACTION_CHANGE_DEFAULT);
                changeDefault.putExtra(CardEmulation.EXTRA_CATEGORY, CardEmulation.CATEGORY_PAYMENT);
                changeDefault.putExtra(CardEmulation.EXTRA_SERVICE_COMPONENT, defaultComponent);
                startActivityForResult(changeDefault, 0);
                }
            });
            builder.show();
            return true;
        } else {
            return false;
        }
    }

    final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (HceUtils.ACTION_APDU_SEQUENCE_COMPLETE.equals(action)) {
                // Get component whose sequence was completed
                ComponentName component = intent.getParcelableExtra(HceUtils.EXTRA_COMPONENT);
                long duration = intent.getLongExtra(HceUtils.EXTRA_DURATION, 0);
                if (component != null) {
                    onApduSequenceComplete(component, duration);
                }
            } else if (HceUtils.ACTION_APDU_SEQUENCE_ERROR.equals(action)) {
                onApduSequenceError();
            }
        }
    };

    private class SetupServicesTask extends AsyncTask<ComponentName, Void, Boolean> {
        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            mSetupDialog.dismiss();
            onServicesSetup(result);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mSetupDialog.setTitle(R.string.nfc_hce_please_wait);
            mSetupDialog.setMessage(getString(R.string.nfc_hce_setting_up));
            mSetupDialog.setCancelable(false);
            mSetupDialog.show();
        }

        @Override
        protected Boolean doInBackground(ComponentName... components) {
            List<ComponentName> enableComponents = Arrays.asList(components);
            for (ComponentName component : SERVICES) {
                if (enableComponents.contains(component)) {
                    Log.d(TAG, "Enabling component " + component);
                    HceUtils.enableComponent(getPackageManager(), component);
                } else {
                    Log.d(TAG, "Disabling component " + component);
                    HceUtils.disableComponent(getPackageManager(), component);
                }
            }
            // This is a trick to invalidate the HCE cache and avoid
            // having to wait for PackageManager broadcasts to NFCService.
            ComponentName bogusComponent = new ComponentName("com.android.cts.verifier",
                    "com.android.cts.verifier.nfc.hce.BogusService");
            mCardEmulation.isDefaultServiceForCategory(bogusComponent,
                    CardEmulation.CATEGORY_PAYMENT);
            return true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            // Verify it's default
            if (!mCardEmulation.isDefaultServiceForCategory(mMakingDefault,
                    CardEmulation.CATEGORY_PAYMENT)) {
                // Popup dialog-box
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Test failed.");
                builder.setMessage("The service was not made the default service according " +
                        "to CardEmulation.getDefaultServiceForCategory(), verify the make " +
                        "default implementation is correct.");
                builder.setPositiveButton("OK", null);
                builder.show();
                onPaymentDefaultResult(mMakingDefault, false);
            } else {
                onPaymentDefaultResult(mMakingDefault, true);
            }
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Test failed.");
            builder.setMessage("You clicked no.");
            builder.setPositiveButton("OK", null);
            builder.show();
            onPaymentDefaultResult(mMakingDefault, false);
        }
    }

    void onPaymentDefaultResult(ComponentName component, boolean success) {

    };
}
