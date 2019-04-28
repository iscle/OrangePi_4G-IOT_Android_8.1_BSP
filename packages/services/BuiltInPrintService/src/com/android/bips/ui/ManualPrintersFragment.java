/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
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

package com.android.bips.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.android.bips.BuiltInPrintService;
import com.android.bips.R;
import com.android.bips.discovery.DiscoveredPrinter;
import com.android.bips.discovery.Discovery;
import com.android.bips.discovery.ManualDiscovery;

/**
 * Presents a list of printers and the ability to add a new one
 */
public class ManualPrintersFragment extends PreferenceFragment implements ServiceConnection,
        Discovery.Listener {
    private static final String TAG = ManualPrintersFragment.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final int ORDER_LAST = Integer.MAX_VALUE - 1;

    private BuiltInPrintService mLocalPrint;
    private ManualDiscovery mManualDiscovery;
    private AddManualPrinterDialog mDialog;

    @Override
    public void onCreate(Bundle in) {
        if (DEBUG) Log.d(TAG, "onCreate");
        super.onCreate(in);

        getContext().bindService(new Intent(getContext(), BuiltInPrintService.class), this,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy");
        if (mManualDiscovery != null) {
            mManualDiscovery.stop(this);
        }
        getContext().unbindService(this);
        super.onDestroy();
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        if (DEBUG) Log.d(TAG, "onServiceConnected");
        mLocalPrint = BuiltInPrintService.getInstance();

        // Set up the UI now that we have a bound service
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getActivity());
        setPreferenceScreen(screen);

        AddPrinterPreference addPrinterPreference = new AddPrinterPreference();
        screen.addPreference(addPrinterPreference);
        registerForContextMenu(getView().findViewById(android.R.id.list));

        mManualDiscovery = mLocalPrint.getManualDiscovery();
        mManualDiscovery.start(this);

        // Simulate a click on add printer since that is likely what the user came here to do.
        if (mManualDiscovery.getPrinters().isEmpty()) {
            addPrinterPreference.onPreferenceClick(addPrinterPreference);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        mLocalPrint = null;
    }

    @Override
    public void onPrinterFound(DiscoveredPrinter printer) {
        if (DEBUG) Log.d(TAG, "onPrinterFound: " + printer);
        PreferenceScreen screen = getPreferenceScreen();

        // Do not add duplicates
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            if (screen.getPreference(i) instanceof ManualPrinterPreference) {
                ManualPrinterPreference preference = (ManualPrinterPreference) screen
                        .getPreference(i);
                if (preference.printer.path.equals(printer.path)) {
                    // We have it already, proceed
                    return;
                }
            }
        }
        screen.addPreference(new ManualPrinterPreference(getContext(), printer));
    }

    @Override
    public void onPrinterLost(DiscoveredPrinter printer) {
        if (DEBUG) Log.d(TAG, "onPrinterLost: " + printer);
        PreferenceScreen screen = getPreferenceScreen();
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            Preference preference = screen.getPreference(i);
            if (preference instanceof ManualPrinterPreference) {
                if (((ManualPrinterPreference) preference).printer.getUri()
                        .equals(printer.getUri())) {
                    screen.removePreference(preference);
                    break;
                }
            }
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view,
            ContextMenu.ContextMenuInfo menuInfo) {
        if (view instanceof ListView) {
            int position = ((AdapterView.AdapterContextMenuInfo) menuInfo).position;
            Preference preference = getPreferenceScreen().getPreference(position);
            if (preference instanceof ManualPrinterPreference) {
                final DiscoveredPrinter printer = ((ManualPrinterPreference) preference).printer;
                menu.setHeaderTitle(printer.name);
                MenuItem forgetItem = menu.add(Menu.NONE, R.string.forget_printer,
                        Menu.NONE, R.string.forget_printer);
                forgetItem.setOnMenuItemClickListener(menuItem -> {
                    mManualDiscovery.removeManualPrinter(printer);
                    return true;
                });
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
    }

    private static class ManualPrinterPreference extends Preference {
        final DiscoveredPrinter printer;

        ManualPrinterPreference(Context context, DiscoveredPrinter printer) {
            super(context);
            this.printer = printer;
            setLayoutResource(R.layout.printer_item);
            setTitle(printer.name);
            setSummary(printer.path.getHost());
            setIcon(R.drawable.ic_printer);
        }
    }

    private class AddPrinterPreference extends Preference
            implements Preference.OnPreferenceClickListener {
        AddPrinterPreference() {
            super(ManualPrintersFragment.this.getContext());
            setTitle(R.string.add_manual_printer);
            setIcon(R.drawable.ic_menu_add);
            setOrder(ORDER_LAST);
            setPersistent(false);
            setOnPreferenceClickListener(this);
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (DEBUG) Log.d(TAG, "onPreferenceClick for addPrinterPreference");
            mDialog = new AddManualPrinterDialog(getContext(), mManualDiscovery);
            mDialog.show();
            return true;
        }
    }
}