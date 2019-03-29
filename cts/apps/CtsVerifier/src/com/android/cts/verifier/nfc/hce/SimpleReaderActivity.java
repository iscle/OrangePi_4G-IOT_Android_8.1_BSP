package com.android.cts.verifier.nfc.hce;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.ReaderCallback;
import android.nfc.tech.IsoDep;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.io.IOException;
import java.util.Arrays;

@TargetApi(19)
public class SimpleReaderActivity extends PassFailButtons.Activity implements ReaderCallback,
        OnItemSelectedListener {
    public static final String PREFS_NAME = "HceTypePrefs";

    public static final String TAG = "SimpleReaderActivity";
    public static final String EXTRA_APDUS = "apdus";
    public static final String EXTRA_RESPONSES = "responses";
    public static final String EXTRA_LABEL = "label";

    NfcAdapter mAdapter;
    CommandApdu[] mApdus;
    String[] mResponses;
    String mLabel;

    TextView mTextView;
    Spinner mSpinner;
    SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.nfc_hce_reader);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        mLabel = getIntent().getStringExtra(EXTRA_LABEL);
        setTitle(mLabel);

        mAdapter = NfcAdapter.getDefaultAdapter(this);
        mTextView = (TextView) findViewById(R.id.text);
        mTextView.setTextSize(12.0f);
        mTextView.setText(R.string.nfc_hce_type_selection);

        Spinner spinner = (Spinner) findViewById(R.id.type_ab_selection);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.nfc_types_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);

        mPrefs = getSharedPreferences(PREFS_NAME, 0);
        boolean isTypeB = mPrefs.getBoolean("typeB", false);
        if (isTypeB) {
            spinner.setSelection(1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAdapter.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_A |
                NfcAdapter.FLAG_READER_NFC_BARCODE | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
        Intent intent = getIntent();
        Parcelable[] apdus = intent.getParcelableArrayExtra(EXTRA_APDUS);
        if (apdus != null) {
	        mApdus = new CommandApdu[apdus.length];
	        for (int i = 0; i < apdus.length; i++) {
	            mApdus[i] = (CommandApdu) apdus[i];
	        }
        } else {
            mApdus = null;
        }
        mResponses = intent.getStringArrayExtra(EXTRA_RESPONSES);
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        final StringBuilder sb = new StringBuilder();
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) {
            // TODO dialog box
            return;
        }

        try {
            isoDep.connect();
            isoDep.setTimeout(5000);
            int count = 0;
            boolean success = true;
            long startTime = System.currentTimeMillis();
            for (CommandApdu apdu: mApdus) {
                sb.append("Request APDU:\n");
                sb.append(apdu.getApdu() + "\n\n");
                long apduStartTime = System.currentTimeMillis();
                byte[] response = isoDep.transceive(HceUtils.hexStringToBytes(apdu.getApdu()));
                long apduEndTime = System.currentTimeMillis();
                sb.append("Response APDU (in " + Long.toString(apduEndTime - apduStartTime) +
                        " ms):\n");
                sb.append(HceUtils.getHexBytes(null, response));

                sb.append("\n\n\n");
                boolean wildCard = "*".equals(mResponses[count]);
                byte[] expectedResponse = HceUtils.hexStringToBytes(mResponses[count]);
                Log.d(TAG, HceUtils.getHexBytes("APDU response: ", response));
                if (!wildCard && !Arrays.equals(response, expectedResponse)) {
                    Log.d(TAG, "Unexpected APDU response: " + HceUtils.getHexBytes("", response));
                    success = false;
                    break;
                }
                count++;
            }
            if (success) {
                sb.insert(0, "Total APDU exchange time: " +
                        Long.toString(System.currentTimeMillis() - startTime) + " ms.\n\n");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTextView.setText(sb.toString());
                        getPassButton().setEnabled(true);
                    }
                });
            } else {
                sb.insert(0, "FAIL. Total APDU exchange time: " +
                        Long.toString(System.currentTimeMillis() - startTime) + " ms.\n\n");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTextView.setText(sb.toString());
                        AlertDialog.Builder builder = new AlertDialog.Builder(SimpleReaderActivity.this);
                        builder.setTitle("Test failed");
                        builder.setMessage("An unexpected response APDU was received, or no APDUs were received at all.");
                        builder.setPositiveButton("OK", null);
                        builder.show();
                    }
                });
            }
        } catch (IOException e) {
            sb.insert(0, "Error while reading: (did you keep the devices in range?)\nPlease try again\n.");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTextView.setText(sb.toString());
                }
            });
        } finally {
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position,
            long id) {
        if (position == 0) {
            // Type-A
            mAdapter.disableReaderMode(this);
            mAdapter.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_A |
                NfcAdapter.FLAG_READER_NFC_BARCODE | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
            SharedPreferences.Editor editor = mPrefs.edit();
            editor.putBoolean("typeB", false);
            editor.commit();
        } else {
            // Type-B
            mAdapter.disableReaderMode(this);
            mAdapter.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_B |
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
            SharedPreferences.Editor editor = mPrefs.edit();
            editor.putBoolean("typeB", true);
            editor.commit();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    @Override
    public String getTestId() {
        return mLabel;
    }
}
