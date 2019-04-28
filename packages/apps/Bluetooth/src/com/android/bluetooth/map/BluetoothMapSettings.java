/*
* Copyright (C) 2015 Samsung System LSI
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

package com.android.bluetooth.map;

import com.android.bluetooth.R;
import com.android.bluetooth.map.BluetoothMapAccountItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ExpandableListView;


public class BluetoothMapSettings extends Activity {

    private static final String TAG = "BluetoothMapSettings";
    private static final boolean D = BluetoothMapService.DEBUG;
    private static final boolean V = BluetoothMapService.VERBOSE;



    BluetoothMapAccountLoader mLoader = new BluetoothMapAccountLoader(this);
    LinkedHashMap<BluetoothMapAccountItem,ArrayList<BluetoothMapAccountItem>> mGroups;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /* set UI */
        setContentView(R.layout.bluetooth_map_settings);
        /* create structure for list of groups + items*/
        mGroups = mLoader.parsePackages(true);


        /* update expandable listview with correct items */
        ExpandableListView listView =
            (ExpandableListView) findViewById(R.id.bluetooth_map_settings_list_view);

        BluetoothMapSettingsAdapter adapter = new BluetoothMapSettingsAdapter(this,
                listView, mGroups, mLoader.getAccountsEnabledCount());
        listView.setAdapter(adapter);
    }


}
