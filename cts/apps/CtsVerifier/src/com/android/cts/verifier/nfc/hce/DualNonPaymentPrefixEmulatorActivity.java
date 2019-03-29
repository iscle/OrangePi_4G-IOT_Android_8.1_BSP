package com.android.cts.verifier.nfc.hce;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.nfc.cardemulation.CardEmulation;
import android.os.Bundle;

import com.android.cts.verifier.R;
import com.android.cts.verifier.nfc.NfcDialogs;

import java.util.ArrayList;

public class DualNonPaymentPrefixEmulatorActivity extends BaseEmulatorActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_text);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        setupServices(this, PrefixTransportService1.COMPONENT, PrefixAccessService.COMPONENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    void onServicesSetup(boolean result) {
        // Do dynamic AID registration
        ArrayList<String> service1_aids = new ArrayList<String>();
        service1_aids.add(HceUtils.TRANSPORT_PREFIX_AID + "*");
        ArrayList<String> service2_aids = new ArrayList<String>();
        service2_aids.add(HceUtils.ACCESS_PREFIX_AID + "*");
        mCardEmulation.registerAidsForService(PrefixTransportService1.COMPONENT, CardEmulation.CATEGORY_OTHER, service1_aids);
        mCardEmulation.registerAidsForService(PrefixAccessService.COMPONENT, CardEmulation.CATEGORY_OTHER, service2_aids);
        NfcDialogs.createHceTapReaderDialog(this, getString(R.string.nfc_hce_other_prefix_aids_help)).show();
    }

    public static Intent buildReaderIntent(Context context) {
        Intent readerIntent = new Intent(context, SimpleReaderActivity.class);
        // Combine command/response APDU arrays
        CommandApdu[] commandSequences = new CommandApdu[PrefixTransportService1.APDU_COMMAND_SEQUENCE.length +
                PrefixAccessService.APDU_COMMAND_SEQUENCE.length];
        System.arraycopy(PrefixTransportService1.APDU_COMMAND_SEQUENCE, 0, commandSequences, 0,
                PrefixTransportService1.APDU_COMMAND_SEQUENCE.length);
        System.arraycopy(PrefixAccessService.APDU_COMMAND_SEQUENCE, 0, commandSequences,
                PrefixTransportService1.APDU_COMMAND_SEQUENCE.length,
                PrefixAccessService.APDU_COMMAND_SEQUENCE.length);

        String[] responseSequences = new String[PrefixTransportService1.APDU_RESPOND_SEQUENCE.length +
                PrefixAccessService.APDU_RESPOND_SEQUENCE.length];
        System.arraycopy(PrefixTransportService1.APDU_RESPOND_SEQUENCE, 0, responseSequences, 0,
                PrefixTransportService1.APDU_RESPOND_SEQUENCE.length);
        System.arraycopy(PrefixAccessService.APDU_RESPOND_SEQUENCE, 0, responseSequences,
                PrefixTransportService1.APDU_RESPOND_SEQUENCE.length,
                PrefixAccessService.APDU_RESPOND_SEQUENCE.length);

        readerIntent.putExtra(SimpleReaderActivity.EXTRA_APDUS, commandSequences);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_RESPONSES, responseSequences);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_LABEL,
                context.getString(R.string.nfc_hce_other_prefix_aids_reader));
        return readerIntent;
    }

    @Override
    void onApduSequenceComplete(ComponentName component, long duration) {
        if (component.equals(PrefixAccessService.COMPONENT)) {
            getPassButton().setEnabled(true);
        }
    }
}