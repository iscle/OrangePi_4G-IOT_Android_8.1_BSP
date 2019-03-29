package com.android.cts.verifier.nfc.hce;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.nfc.cardemulation.CardEmulation;
import android.os.Bundle;

import com.android.cts.verifier.R;
import com.android.cts.verifier.nfc.NfcDialogs;

import java.util.ArrayList;

public class DynamicAidEmulatorActivity extends BaseEmulatorActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
       super.onCreate(savedInstanceState);
       setContentView(R.layout.pass_fail_text);
       setPassFailButtonClickListeners();
       getPassButton().setEnabled(false);
       setupServices(this, PaymentServiceDynamicAids.COMPONENT);
    }


    @Override
    protected void onResume() {
        super.onResume();
    }


    @Override
    void onServicesSetup(boolean result) {
        ArrayList<String> paymentAids = new ArrayList<String>();
        paymentAids.add(HceUtils.PPSE_AID);
        paymentAids.add(HceUtils.VISA_AID);
        // Register a different set of AIDs for the foreground
        mCardEmulation.registerAidsForService(PaymentServiceDynamicAids.COMPONENT,
                CardEmulation.CATEGORY_PAYMENT, paymentAids);
        // Now make sure it's default
        if (makePaymentDefault(PaymentServiceDynamicAids.COMPONENT,
                R.string.nfc_hce_change_preinstalled_wallet)) {
            // Wait for callback
        } else {
	        NfcDialogs.createHceTapReaderDialog(this, getString(R.string.nfc_hce_payment_dynamic_aids_help)).show();
        }
    }

    @Override
    void onPaymentDefaultResult(ComponentName component, boolean success) {
        if (success) {
	        NfcDialogs.createHceTapReaderDialog(this, getString(R.string.nfc_hce_payment_dynamic_aids_help)).show();
        }
    }

    @Override
    void onApduSequenceComplete(ComponentName component, long duration) {
        if (component.equals(PaymentServiceDynamicAids.COMPONENT)) {
            getPassButton().setEnabled(true);
        }
    }

    public static Intent buildReaderIntent(Context context) {
        Intent readerIntent = new Intent(context, SimpleReaderActivity.class);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_APDUS,
                PaymentServiceDynamicAids.APDU_COMMAND_SEQUENCE);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_RESPONSES,
                PaymentServiceDynamicAids.APDU_RESPOND_SEQUENCE);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_LABEL,
                context.getString(R.string.nfc_hce_payment_dynamic_aids_reader));
        return readerIntent;
    }
}
