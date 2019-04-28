/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mediatek.server.wifi;

import android.os.Handler;
import android.os.Message;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Protocol;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * M: WiFi hotspot manager
 * Listens for events from the hostapd server, and passes them on
 * to the SoftApManager for handling.
 *
 * @hide
 */
public class WifiApMonitor {
    private static final String TAG = "WifiApMonitor";

    /* Supplicant events reported to a state machine */
    private static final int BASE = Protocol.BASE_WIFI_MONITOR;

    /* Connection to supplicant established */
    public static final int SUP_CONNECTION_EVENT                 = BASE + 1;
    /* Connection to supplicant lost */
    public static final int SUP_DISCONNECTION_EVENT              = BASE + 2;
     /* WPS overlap detected */
    public static final int WPS_OVERLAP_EVENT                    = BASE + 10;

    /* hostap events */
    public static final int AP_STA_DISCONNECTED_EVENT            = BASE + 41;
    public static final int AP_STA_CONNECTED_EVENT               = BASE + 42;

    private final WifiApInjector mWifiApInjector;
    private boolean mVerboseLoggingEnabled = true;
    private boolean mConnected = false;

    public WifiApMonitor(WifiApInjector wifiApInjector) {
        mWifiApInjector = wifiApInjector;
    }

    void enableVerboseLogging(int verbose) {
        if (verbose > 0) {
            mVerboseLoggingEnabled = true;
        } else {
            mVerboseLoggingEnabled = false;
        }
    }

    // TODO(b/27569474) remove support for multiple handlers for the same event
    private final Map<String, SparseArray<Set<Handler>>> mHandlerMap = new HashMap<>();

    /**
     * Registers a callback handler for the provided event.
     */
    public synchronized void registerHandler(String iface, int what, Handler handler) {
        SparseArray<Set<Handler>> ifaceHandlers = mHandlerMap.get(iface);
        if (ifaceHandlers == null) {
            ifaceHandlers = new SparseArray<>();
            mHandlerMap.put(iface, ifaceHandlers);
        }
        Set<Handler> ifaceWhatHandlers = ifaceHandlers.get(what);
        if (ifaceWhatHandlers == null) {
            ifaceWhatHandlers = new ArraySet<>();
            ifaceHandlers.put(what, ifaceWhatHandlers);
        }
        ifaceWhatHandlers.add(handler);
    }

    /**
     * Unregisters all callback handlers.
     */
    public synchronized void unregisterAllHandler() {
        mHandlerMap.clear();
    }

    private final Map<String, Boolean> mMonitoringMap = new HashMap<>();
    private boolean isMonitoring(String iface) {
        Boolean val = mMonitoringMap.get(iface);
        if (val == null) {
            return false;
        } else {
            return val.booleanValue();
        }
    }

    /**
     * Enable/Disable monitoring for the provided iface.
     *
     * @param iface Name of the iface.
     * @param enabled true to enable, false to disable.
     */
    @VisibleForTesting
    public void setMonitoring(String iface, boolean enabled) {
        mMonitoringMap.put(iface, enabled);
    }

    private void setMonitoringNone() {
        for (String iface : mMonitoringMap.keySet()) {
            setMonitoring(iface, false);
        }
    }

    /**
     * Wait for hostapd's control interface to be ready.
     *
     * TODO: Add unit tests for these once we remove the legacy code.
     */
    private boolean ensureConnectedLocked() {
        if (mConnected) {
            return true;
        }
        if (mVerboseLoggingEnabled) Log.d(TAG, "connecting to hostapd");
        int connectTries = 0;
        while (true) {
            mConnected = mWifiApInjector.getWifiApNative().connectToHostapd();
            if (mConnected) {
                return true;
            }
            if (connectTries++ < 5) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignore) {
                }
            } else {
                return false;
            }
        }
    }

    /**
     * Start Monitoring for wpa_supplicant events.
     *
     * @param iface Name of iface.
     * TODO: Add unit tests for these once we remove the legacy code.
     */
    public synchronized void startMonitoring(String iface) {
        if (ensureConnectedLocked()) {
            setMonitoring(iface, true);
            broadcastSupplicantConnectionEvent(iface);
        } else {
            boolean originalMonitoring = isMonitoring(iface);
            setMonitoring(iface, true);
            broadcastSupplicantDisconnectionEvent(iface);
            setMonitoring(iface, originalMonitoring);
            Log.e(TAG, "startMonitoring(" + iface + ") failed!");
        }
    }

    /**
     * Stop Monitoring for wpa_supplicant events.
     *
     * @param iface Name of iface.
     * TODO: Add unit tests for these once we remove the legacy code.
     */
    public synchronized void stopMonitoring(String iface) {
        if (mVerboseLoggingEnabled) Log.d(TAG, "stopMonitoring(" + iface + ")");
        setMonitoring(iface, true);
        broadcastSupplicantDisconnectionEvent(iface);
        setMonitoring(iface, false);
    }

    /**
     * Stop Monitoring for wpa_supplicant events.
     *
     * TODO: Add unit tests for these once we remove the legacy code.
     */
    public synchronized void stopAllMonitoring() {
        mConnected = false;
        setMonitoringNone();
    }

    /**
     * Similar functions to Handler#sendMessage that send the message to the registered handler
     * for the given interface and message what.
     * All of these should be called with the WifiMonitor class lock
     */
    private void sendMessage(String iface, int what) {
        sendMessage(iface, Message.obtain(null, what));
    }

    private void sendMessage(String iface, int what, Object obj) {
        sendMessage(iface, Message.obtain(null, what, obj));
    }

    private void sendMessage(String iface, int what, int arg1) {
        sendMessage(iface, Message.obtain(null, what, arg1, 0));
    }

    private void sendMessage(String iface, int what, int arg1, int arg2) {
        sendMessage(iface, Message.obtain(null, what, arg1, arg2));
    }

    private void sendMessage(String iface, int what, int arg1, int arg2, Object obj) {
        sendMessage(iface, Message.obtain(null, what, arg1, arg2, obj));
    }

    private void sendMessage(String iface, Message message) {
        SparseArray<Set<Handler>> ifaceHandlers = mHandlerMap.get(iface);
        if (iface != null && ifaceHandlers != null) {
            if (isMonitoring(iface)) {
                boolean firstHandler = true;
                Set<Handler> ifaceWhatHandlers = ifaceHandlers.get(message.what);
                if (ifaceWhatHandlers != null) {
                    for (Handler handler : ifaceWhatHandlers) {
                        if (firstHandler) {
                            firstHandler = false;
                            sendMessage(handler, message);
                        } else {
                            sendMessage(handler, Message.obtain(message));
                        }
                    }
                }
            } else {
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "Dropping event because (" + iface + ") is stopped");
                }
            }
        } else {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "Sending to all monitors because there's no matching iface");
            }
            boolean firstHandler = true;
            for (Map.Entry<String, SparseArray<Set<Handler>>> entry : mHandlerMap.entrySet()) {
                if (isMonitoring(entry.getKey())) {
                    Set<Handler> ifaceWhatHandlers = entry.getValue().get(message.what);
                    for (Handler handler : ifaceWhatHandlers) {
                        if (firstHandler) {
                            firstHandler = false;
                            sendMessage(handler, message);
                        } else {
                            sendMessage(handler, Message.obtain(message));
                        }
                    }
                }
            }
        }
    }

    private void sendMessage(Handler handler, Message message) {
        if (handler != null) {
            message.setTarget(handler);
            message.sendToTarget();
        }
    }

    /**
     * Broadcast the WPS overlap event to all the handlers registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     */
    public void broadcastWpsOverlapEvent(String iface) {
        sendMessage(iface, WPS_OVERLAP_EVENT);
    }

    /**
     * Broadcast the connection to wpa_supplicant event to all the handlers registered for
     * this event.
     *
     * @param iface Name of iface on which this occurred.
     */
    public void broadcastSupplicantConnectionEvent(String iface) {
        sendMessage(iface, SUP_CONNECTION_EVENT);
    }

    /**
     * Broadcast the loss of connection to wpa_supplicant event to all the handlers registered for
     * this event.
     *
     * @param iface Name of iface on which this occurred.
     */
    public void broadcastSupplicantDisconnectionEvent(String iface) {
        sendMessage(iface, SUP_DISCONNECTION_EVENT);
    }

    /**
     * Broadcast AP STA connection event.
     *
     * @param iface Name of iface on which this occurred.
     * @param macAddress STA MAC address.
     */
    public void broadcastApStaConnected(String iface, String macAddress) {
        sendMessage(iface, AP_STA_CONNECTED_EVENT, macAddress);
    }

    /**
     * Broadcast AP STA disconnection event.
     *
     * @param iface Name of iface on which this occurred.
     * @param macAddress STA MAC address.
     */
    public void broadcastApStaDisconnected(String iface, String macAddress) {
        sendMessage(iface, AP_STA_DISCONNECTED_EVENT, macAddress);
    }
}
