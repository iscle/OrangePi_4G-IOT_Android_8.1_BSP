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
 * limitations under the License.
 */
package com.android.car.settings.wifi;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.net.wifi.WifiManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.car.settings.R;
import com.android.car.settings.common.BaseFragment;
import com.android.car.view.PagedListView;
import com.android.settingslib.wifi.AccessPoint;

import java.util.List;

/**
 * Renders {@link AccessPoint} to a view to be displayed as a row in a list.
 */
public class AccessPointListAdapter
        extends RecyclerView.Adapter<AccessPointListAdapter.ViewHolder>
        implements PagedListView.ItemCap {
    private static final String TAG = "AccessPointListAdapter";
    private static final int NETWORK_ROW_TYPE = 1;
    private static final int ADD_NETWORK_ROW_TYPE = 2;

    private static final int[] STATE_SECURED = {
            com.android.settingslib.R.attr.state_encrypted
    };
    private static final int[] STATE_NONE = {};
    private static int[] wifi_signal_attributes = {com.android.settingslib.R.attr.wifi_signal};

    private final StateListDrawable mWifiSld;
    private final Context mContext;
    private final BaseFragment.FragmentController mFragmentController;
    private final CarWifiManager mCarWifiManager;
    private final WifiManager.ActionListener mConnectionListener;

    private List<AccessPoint> mAccessPoints;

    public AccessPointListAdapter(
            @NonNull Context context,
            CarWifiManager carWifiManager,
            @NonNull List<AccessPoint> accesssPoints,
            BaseFragment.FragmentController fragmentController) {
        mContext = context;
        mFragmentController = fragmentController;
        mCarWifiManager = carWifiManager;
        mAccessPoints = accesssPoints;
        mWifiSld = (StateListDrawable) context.getTheme()
                .obtainStyledAttributes(wifi_signal_attributes).getDrawable(0);

        mConnectionListener = new WifiManager.ActionListener() {
            @Override
            public void onSuccess() {
            }
            @Override
            public void onFailure(int reason) {
                Toast.makeText(mContext,
                        R.string.wifi_failed_connect_message,
                        Toast.LENGTH_SHORT).show();
            }
        };
    }

    public void updateAccessPoints(@NonNull List<AccessPoint> accesssPoints) {
        mAccessPoints = accesssPoints;
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return mAccessPoints.isEmpty();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mIcon;
        private final ImageView mRightChevron;
        private final TextView mWifiName;
        private final TextView mWifiDesc;

        public ViewHolder(View view) {
            super(view);
            mWifiName = (TextView) view.findViewById(R.id.title);
            mWifiDesc = (TextView) view.findViewById(R.id.desc);
            mIcon = (ImageView) view.findViewById(R.id.icon);
            mRightChevron = (ImageView) view.findViewById(R.id.right_chevron);
        }
    }

    private class AccessPointClickListener implements OnClickListener {
        private final AccessPoint mAccessPoint;

        public AccessPointClickListener(AccessPoint accessPoint) {
            mAccessPoint = accessPoint;
        }

        @Override
        public void onClick(View v) {
            // for new open unsecuried wifi network, connect to it right away
            if (mAccessPoint.getSecurity() == AccessPoint.SECURITY_NONE &&
                    !mAccessPoint.isSaved() && !mAccessPoint.isActive()) {
                mCarWifiManager.connectToPublicWifi(mAccessPoint, mConnectionListener);
            } else if (mAccessPoint.isSaved()) {
                mFragmentController.launchFragment(WifiDetailFragment.getInstance(mAccessPoint));
            } else {
                mFragmentController.launchFragment(AddWifiFragment.getInstance(mAccessPoint));
            }
        }
    };

    @Override
    public int getItemViewType(int position) {
        // the last row is the add device row
        if (position == mAccessPoints.size()) {
            return ADD_NETWORK_ROW_TYPE;
        }
        return NETWORK_ROW_TYPE;
    }

    @Override
    public AccessPointListAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
            int viewType) {
        ViewHolder viewHolder = new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.icon_text_line_item, parent, false));
        if (viewType == ADD_NETWORK_ROW_TYPE) {
            viewHolder.mIcon.setImageResource(R.drawable.ic_add);
            viewHolder.mWifiDesc.setVisibility(View.GONE);
            viewHolder.mWifiName.setText(R.string.wifi_setup_add_network);
            viewHolder.itemView.setOnClickListener(v -> {
                mFragmentController.launchFragment(AddWifiFragment.getInstance(null));
            });
        }
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        // for the last row, it's the "add network button", no more binding needed.
        if (position >= mAccessPoints.size()) {
            return;
        }
        AccessPoint accessPoint = mAccessPoints.get(position);
        holder.itemView.setOnClickListener(new AccessPointClickListener(accessPoint));
        holder.mWifiName.setText(accessPoint.getConfigName());
        holder.mIcon.setImageDrawable(getIcon(accessPoint));
        String summary = accessPoint.getSummary();
        if (summary != null && !summary.isEmpty()) {
            holder.mWifiDesc.setText(summary);
            holder.mWifiDesc.setVisibility(View.VISIBLE);
        } else {
            holder.mWifiDesc.setVisibility(View.GONE);
        }
        if (accessPoint.getSecurity() == accessPoint.SECURITY_NONE &&
                !accessPoint.isSaved() && !accessPoint.isActive()) {
            holder.mRightChevron.setVisibility(View.GONE);
        } else {
            holder.mRightChevron.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        // number of rows include one per device and a row for add network.
        return mAccessPoints.size() + 1;
    }

    @Override
    public void setMaxItems(int maxItems) {
        // no limit in this list.
    }

    private Drawable getIcon(AccessPoint accessPoint) {
        mWifiSld.setState((accessPoint.getSecurity() != AccessPoint.SECURITY_NONE)
                ? STATE_SECURED
                : STATE_NONE);
        Drawable drawable = mWifiSld.getCurrent();
        drawable.setLevel(accessPoint.getLevel());
        drawable.invalidateSelf();
        return drawable;
    }
}
