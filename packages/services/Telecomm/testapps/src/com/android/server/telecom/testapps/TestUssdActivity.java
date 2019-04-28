package com.android.server.telecom.testapps;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.Toast;

public class TestUssdActivity extends Activity {

    private EditText mUssdNumberView;
    private Context mContext;
    public static final String LOG_TAG = "TestUssdActivity";

    private TelephonyManager.UssdResponseCallback mReceiveUssdResponseCallback =
            new TelephonyManager.UssdResponseCallback () {
                @Override
                public void onReceiveUssdResponse(final TelephonyManager telephonyManager,
                                                  String request, CharSequence response) {
                    Log.i(LOG_TAG, "USSD Success: " + request + "," + response);
                    showToast("USSD Response Successly received for code:" + request + "," +
                            response);
                }

                public void onReceiveUssdResponseFailed(final TelephonyManager telephonyManager,
                                                        String request, int failureCode) {
                    Log.i(LOG_TAG, "USSD Fail: " + request + "," + failureCode);
                    showToast("USSD Response failed for code:" + request + "," + failureCode);
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getApplicationContext();

        setContentView(R.layout.testussd_main);
        findViewById(R.id.place_ussd_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                placeUssdRequest();
            }
        });
        findViewById(R.id.place_many_ussd_button).setOnClickListener((v) -> {
                    placeUssdRequestMultiple();
                }
        );

        mUssdNumberView = (EditText) findViewById(R.id.number);
    }

    private void placeUssdRequest() {
        String mUssdNumber = mUssdNumberView.getText().toString();
        if (mUssdNumber.equals("") || mUssdNumber == null) {
            mUssdNumber = "#932#";
        }
        final TelephonyManager telephonyManager =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        try {
            Handler h = new Handler(Looper.getMainLooper());
            Log.i(LOG_TAG, "placeUssdRequest: " + mUssdNumber);
            telephonyManager.sendUssdRequest(mUssdNumber, mReceiveUssdResponseCallback, h);
        } catch (SecurityException e) {
            showToast("Permission check failed");
            return;
        }
    }

    private void placeUssdRequestMultiple() {
        for (int ix = 0; ix < 4 ; ix++) {
            placeUssdRequest();
        }
    }

    private void showToast(String message) {
        Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
    }
}