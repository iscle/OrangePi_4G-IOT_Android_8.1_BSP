package com.android.pmc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

public class SettingActivity extends Activity {

    EditText mServerIP;
    EditText mServerPort;
    EditText mInterval;
    EditText mIperfBandwidth;
    EditText mIperfLogfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        mServerIP = (EditText) findViewById(R.id.server_iptext);
        mServerPort = (EditText) findViewById(R.id.server_porttext);
        mInterval = (EditText) findViewById(R.id.intervaltext);
        mIperfBandwidth = (EditText) findViewById(R.id.iperf_bandwidthtext);
        mIperfLogfile = (EditText) findViewById(R.id.iperf_logfiletext);
        // Populate the fields with the current values passed from PMCMainActivity.
        Intent intent = this.getIntent();
        mServerIP.setText(intent.getStringExtra(PMCMainActivity.SETTING_SERVER_IP_KEY));
        mServerPort.setText(intent.getStringExtra(PMCMainActivity.SETTING_SERVER_PORT_KEY));
        mInterval.setText(intent.getStringExtra(PMCMainActivity.SETTING_INTERVAL_KEY));
        mIperfBandwidth.setText(intent.getStringExtra(PMCMainActivity.SETTING_IPERF_BANDWIDTH_KEY));
        mIperfLogfile.setText(intent.getStringExtra(PMCMainActivity.SETTING_IPERF_LOGFILE_KEY));
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.putExtra(PMCMainActivity.SETTING_SERVER_IP_KEY, mServerIP.getText().toString());
        intent.putExtra(PMCMainActivity.SETTING_SERVER_PORT_KEY, mServerPort.getText().toString());
        intent.putExtra(PMCMainActivity.SETTING_INTERVAL_KEY, mInterval.getText().toString());
        intent.putExtra(PMCMainActivity.SETTING_IPERF_BANDWIDTH_KEY,
                mIperfBandwidth.getText().toString());
        intent.putExtra(PMCMainActivity.SETTING_IPERF_LOGFILE_KEY,
                mIperfLogfile.getText().toString());
        setResult(0, intent); //The data you want to send back
        finish();
    }
}
