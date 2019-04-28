package com.android.bluetooth.btservice;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;

/**
 * This helper class monitors the state of the enabled profiles and will update and restart
 * the adapter when necessary.
 */
public class ProfileObserver extends ContentObserver {
    private Context mContext;
    private AdapterService mService;
    private AdapterStateObserver mStateObserver;

    public ProfileObserver(Context context, AdapterService service, Handler handler) {
        super(handler);
        mContext = context;
        mService = service;
        mStateObserver = new AdapterStateObserver(this);
    }

    public void start() {
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.BLUETOOTH_DISABLED_PROFILES), false,
                this);
    }

    private void onBluetoothOff() {
        mContext.unregisterReceiver(mStateObserver);
        Config.init(mContext);
        mService.enable();
    }

    public void stop() {
        mContext.getContentResolver().unregisterContentObserver(this);
    }

    @Override
    public void onChange(boolean selfChange) {
        if (mService.isEnabled()) {
            mContext.registerReceiver(mStateObserver,
                    new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
            mService.disable();
        }
    }

    private static class AdapterStateObserver extends BroadcastReceiver {
        private ProfileObserver mProfileObserver;

        public AdapterStateObserver(ProfileObserver observer) {
            mProfileObserver = observer;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())
                    && intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                    == BluetoothAdapter.STATE_OFF) {
                mProfileObserver.onBluetoothOff();
            }
        }
    }
}
