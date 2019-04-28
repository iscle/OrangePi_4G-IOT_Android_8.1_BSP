package com.android.bluetooth.hfpclient;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.HandlerThread;
import android.test.AndroidTestCase;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.android.bluetooth.btservice.AdapterService;

import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

public class HeadsetClientServiceTest extends AndroidTestCase {
    // Time to wait for the service to be initialized
    private static int SERVICE_START_TIMEOUT_MS = 5000;  // 5 sec
    private static int STATE_MACHINE_TRANSITION_TIMEOUT_MS = 5000;  // 5 sec
    private HeadsetClientService mService = null;
    private BluetoothAdapter mAdapter = null;

    void startServices() {
        Intent startIntent = new Intent(getContext(), HeadsetClientService.class);
        getContext().startService(startIntent);

        try {
            Thread.sleep(SERVICE_START_TIMEOUT_MS);
        } catch (Exception ex) {}

        // At this point the service should have started so check NOT null
        mService = HeadsetClientService.getHeadsetClientService();
        assertTrue(mService != null);

        // At this point Adapter Service should have started
        AdapterService inst = mock(AdapterService.class);
        assertTrue(inst != null);

        // Try getting the Bluetooth adapter
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        assertTrue(mAdapter != null);
    }

    @Override
    protected void setUp() throws Exception {
        startServices();
    }

    @Override
    protected void tearDown() throws Exception {
        mService = null;
        mAdapter = null;
    }

    // Test that we can initialize the service
    public void testInitialize() {
    }
}
