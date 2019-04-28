/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.car.settings.datetime;


import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;

import com.android.car.settings.R;
import com.android.car.settings.common.ListSettingsFragment;
import com.android.car.settings.common.TypedPagedListAdapter;
import com.android.car.view.PagedListView;
import com.android.settingslib.datetime.ZoneGetter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lists all time zone and its offset from GMT.
 */
public class TimeZonePickerFragment extends ListSettingsFragment implements
        TimeZoneLineItem.TimeZoneChangeListener {
    private List<Map<String, Object>> mZoneList;

    public static TimeZonePickerFragment getInstance() {
        TimeZonePickerFragment timeZonePickerFragment = new TimeZonePickerFragment();
        Bundle bundle = ListSettingsFragment.getBundle();
        bundle.putInt(EXTRA_TITLE_ID, R.string.date_time_set_timezone_title);
        timeZonePickerFragment.setArguments(bundle);
        return timeZonePickerFragment;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        mZoneList = ZoneGetter.getZonesList(getContext());
        super.onActivityCreated(savedInstanceState);
    }

    public ArrayList<TypedPagedListAdapter.LineItem> getLineItems() {
        ArrayList<TypedPagedListAdapter.LineItem> lineItems = new ArrayList<>();
        for (Map<String, Object> zone : mZoneList) {
            lineItems.add(new TimeZoneLineItem(getContext(), this, zone));
        }
        return lineItems;
    }

    @Override
    public void onTimeZoneChanged() {
        getContext().sendBroadcast(new Intent(Intent.ACTION_TIME_CHANGED));
        mFragmentController.goBack();
    }
}
