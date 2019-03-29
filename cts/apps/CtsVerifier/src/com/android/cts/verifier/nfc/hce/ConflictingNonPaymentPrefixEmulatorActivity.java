package com.android.cts.verifier.nfc.hce;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.nfc.cardemulation.CardEmulation;
import android.os.Bundle;

import com.android.cts.verifier.R;
import com.android.cts.verifier.nfc.NfcDialogs;

import java.util.ArrayList;

public class ConflictingNonPaymentPrefixEmulatorActivity extends BaseEmulatorActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_text);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        setupServices(this, PrefixTransportService1.COMPONENT, PrefixTransportService2.COMPONENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    void onServicesSetup(boolean result) {
        // Do dynamic AID registration
        ArrayList<String> service_aids = new ArrayList<String>();
        service_aids.add(HceUtils.TRANSPORT_PREFIX_AID + "*");
        mCardEmulation.registerAidsForService(PrefixTransportService1.COMPONENT, CardEmulation.CATEGORY_OTHER, service_aids);
        mCardEmulation.registerAidsForService(PrefixTransportService2.COMPONENT, CardEmulation.CATEGORY_OTHER, service_aids);
        NfcDialogs.createHceTapReaderDialog(this, getString(R.string.nfc_hce_other_conflicting_prefix_aids_help)).show();
    }

    public static Intent buildReaderIntent(Context context) {
        Intent readerIntent = new Intent(context, SimpleReaderActivity.class);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_APDUS, PrefixTransportService2.APDU_COMMAND_SEQUENCE);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_RESPONSES, PrefixTransportService2.APDU_RESPOND_SEQUENCE);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_LABEL,
                context.getString(R.string.nfc_hce_other_conflicting_prefix_aids_reader));
        return readerIntent;
    }

    @Override
    void onApduSequenceComplete(ComponentName component, long duration) {
        if (component.equals(PrefixTransportService2.COMPONENT)) {
            getPassButton().setEnabled(true);
        }
    }
}