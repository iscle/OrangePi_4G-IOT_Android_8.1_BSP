/*
 * Copyright (C) 2015 The Android Open Source Project
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

package foo.bar.permission;

import android.Manifest;
import android.app.Activity;
import android.app.LoaderManager;
import android.bluetooth.BluetoothDevice;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.BluetoothLEDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.IntentSender;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple sample of how to use the runtime permissions APIs.
 */
public class PermissionActivity extends Activity implements LoaderManager.LoaderCallbacks<Cursor> {

    public static final String LOG_TAG = "PermissionActivity";

    private static final int CHOOSE_DEVICE_REQUEST = 1;

    private static final int CONTACTS_LOADER = 1;

    private static final int EVENTS_LOADER = 2;

    private static final int PERMISSIONS_REQUEST_READ_CONTACTS = 1;

    private static final int PERMISSIONS_REQUEST_READ_CALENDAR = 2;

    private static final int PERMISSIONS_REQUEST_ALL_PERMISSIONS = 3;

    private final static String[] CONTACTS_COLUMNS = {
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
    };

    private final static String[] CALENDAR_COLUMNS = {
            CalendarContract.Events.TITLE
    };

    private static final String[] CONTACTS_PROJECTION = {
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
    };

    private static final String[] EVENTS_PROJECTION = {
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE
    };

    private final static int[] TO_IDS = {
            android.R.id.text1
    };

    private ListView mListView;

    private CursorAdapter mContactsAdapter;
    private CursorAdapter mEventsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bindUi();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.actions, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.show_contacts: {
                showContacts();
                return true;
            }

            case R.id.show_events: {
                showEvents();
                return true;
            }

            case R.id.request_all_perms: {
                requestPermissions();
                return true;
            }
        }

        return false;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int loaderId, Bundle args) {
        switch (loaderId) {
            case CONTACTS_LOADER: {
                return new CursorLoader(this,
                        ContactsContract.Contacts.CONTENT_URI,
                        CONTACTS_PROJECTION, null, null, null);
            }

            case EVENTS_LOADER: {
                return new CursorLoader(this,
                        CalendarContract.Events.CONTENT_URI,
                        EVENTS_PROJECTION, null, null, null);
            }

            default: {
                return null;
            }
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        switch (loader.getId()) {
            case CONTACTS_LOADER: {
                mContactsAdapter.swapCursor(cursor);
            } break;

            case EVENTS_LOADER: {
                mEventsAdapter.swapCursor(cursor);
            } break;
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        switch (loader.getId()) {
            case CONTACTS_LOADER: {
                mContactsAdapter.swapCursor(null);
            } break;

            case EVENTS_LOADER: {
                mEventsAdapter.swapCursor(null);
            } break;
        }
    }

    @Override
    public void onBackPressed() {
        requestPermissions();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CHOOSE_DEVICE_REQUEST) {
            BluetoothDevice device = data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
            int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_READ_CONTACTS: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showContacts();
                }
            } break;

            case PERMISSIONS_REQUEST_READ_CALENDAR: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showEvents();
                }
            } break;

            case PERMISSIONS_REQUEST_ALL_PERMISSIONS: {
                for (int i = 0; i < permissions.length; i++) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        switch (permissions[i]) {
                            case Manifest.permission.READ_CALENDAR: {
                                Toast.makeText(this, "Granted Calendar!",
                                        Toast.LENGTH_SHORT).show();
                            } break;

                            case Manifest.permission.READ_CONTACTS: {
                                showContacts();
                            } break;
                        }
                    }
                }
            } break;
        }
    }

    private void bindUi() {
        setContentView(R.layout.permission_activity);

        mListView = (ListView) findViewById(R.id.list);
        View emptyView = findViewById(R.id.empty_state);
        mListView.setEmptyView(emptyView);

        mContactsAdapter = new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_1,
                null, CONTACTS_COLUMNS, TO_IDS, 0);

        mEventsAdapter = new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_1,
                null, CALENDAR_COLUMNS, TO_IDS, 0);
    }

    private void showContacts() {
        getPackageManager().isInstantApp();


//        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
//                != PackageManager.PERMISSION_GRANTED) {
//            requestPermissions(new String[] {Manifest.permission.READ_CONTACTS},
//                    PERMISSIONS_REQUEST_READ_CONTACTS);
//            return;
//        }
//
//        if (getLoaderManager().getLoader(CONTACTS_LOADER) == null) {
//            getLoaderManager().initLoader(CONTACTS_LOADER, null, this);
//        }
//        mListView.setAdapter(mContactsAdapter);
    }

    private void showEvents() {
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR},
                    PERMISSIONS_REQUEST_READ_CALENDAR);
            return;
        }

        if (getLoaderManager().getLoader(EVENTS_LOADER) == null) {
            getLoaderManager().initLoader(EVENTS_LOADER, null, this);
        }
        mListView.setAdapter(mEventsAdapter);
    }

    private void requestPermissions() {
        List<String> permissions = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CALENDAR);
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CONTACTS);
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]),
                    PERMISSIONS_REQUEST_ALL_PERMISSIONS);
        }
    }
}
