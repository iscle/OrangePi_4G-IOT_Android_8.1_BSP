package com.android.cts.verifier.nfc.hce;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.nfc.cardemulation.CardEmulation;
import android.os.Bundle;

import com.android.cts.verifier.R;
import com.android.cts.verifier.nfc.NfcDialogs;

import java.util.ArrayList;

public class LargeNumAidsEmulatorActivity extends BaseEmulatorActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
       super.onCreate(savedInstanceState);
       setContentView(R.layout.pass_fail_text);
       setPassFailButtonClickListeners();
       getPassButton().setEnabled(false);
       setupServices(this, LargeNumAidsService.COMPONENT);
    }


    @Override
    protected void onResume() {
        super.onResume();
    }


    @Override
    void onServicesSetup(boolean result) {
        ArrayList<String> aids = new ArrayList<String>();
        for (int i = 0; i < 256; i++) {
            aids.add(HceUtils.LARGE_NUM_AIDS_PREFIX + String.format("%02X", i) + HceUtils.LARGE_NUM_AIDS_POSTFIX);
        }
        mCardEmulation.registerAidsForService(LargeNumAidsService.COMPONENT,
                CardEmulation.CATEGORY_OTHER, aids);
    }

    @Override
    void onApduSequenceComplete(ComponentName component, long duration) {
        if (component.equals(LargeNumAidsService.COMPONENT)) {
            getPassButton().setEnabled(true);
        }
    }

    public static Intent buildReaderIntent(Context context) {
        Intent readerIntent = new Intent(context, SimpleReaderActivity.class);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_APDUS,
                LargeNumAidsService.getCommandSequence());
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_RESPONSES,
                LargeNumAidsService.getResponseSequence());
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_LABEL,
                context.getString(R.string.nfc_hce_large_num_aids_reader));
        return readerIntent;
    }
}
