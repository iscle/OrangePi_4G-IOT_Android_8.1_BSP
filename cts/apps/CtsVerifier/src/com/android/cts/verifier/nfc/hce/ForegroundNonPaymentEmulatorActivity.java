package com.android.cts.verifier.nfc.hce;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.android.cts.verifier.R;
import com.android.cts.verifier.nfc.NfcDialogs;

public class ForegroundNonPaymentEmulatorActivity extends BaseEmulatorActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_text);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupServices(this, TransportService1.COMPONENT, TransportService2.COMPONENT);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCardEmulation.unsetPreferredService(this);
    }

    @Override
    void onServicesSetup(boolean result) {
        // Tell NFC service we prefer TransportService2
        mCardEmulation.setPreferredService(this, TransportService2.COMPONENT);
        NfcDialogs.createHceTapReaderDialog(this,
                getString(R.string.nfc_hce_foreground_non_payment_help)).show();
    }

    public static Intent buildReaderIntent(Context context) {
        Intent readerIntent = new Intent(context, SimpleReaderActivity.class);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_APDUS,
                TransportService2.APDU_COMMAND_SEQUENCE);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_RESPONSES,
                TransportService2.APDU_RESPOND_SEQUENCE);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_LABEL,
                context.getString(R.string.nfc_hce_foreground_non_payment_reader));
        return readerIntent;
    }

    @Override
    void onApduSequenceComplete(ComponentName component, long duration) {
        if (component.equals(TransportService2.COMPONENT)) {
            getPassButton().setEnabled(true);
        }
    }

}
