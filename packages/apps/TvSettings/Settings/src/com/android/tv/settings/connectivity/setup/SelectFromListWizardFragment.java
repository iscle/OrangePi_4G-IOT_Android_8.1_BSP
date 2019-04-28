/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.connectivity.setup;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v17.leanback.widget.FacetProvider;
import android.support.v17.leanback.widget.ItemAlignmentFacet;
import android.support.v17.leanback.widget.ItemAlignmentFacet.ItemAlignmentDef;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v7.util.SortedList;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.util.SortedListAdapterCallback;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.tv.settings.R;
import com.android.tv.settings.connectivity.WifiSecurity;
import com.android.tv.settings.util.AccessibilityHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Displays a UI for selecting a wifi network from a list in the "wizard" style.
 */
public class SelectFromListWizardFragment extends Fragment {

    public static class ListItemComparator implements Comparator<ListItem> {
        @Override
        public int compare(ListItem o1, ListItem o2) {
            int pinnedPos1 = o1.getPinnedPosition();
            int pinnedPos2 = o2.getPinnedPosition();

            if (pinnedPos1 != PinnedListItem.UNPINNED && pinnedPos2 == PinnedListItem.UNPINNED) {
                if (pinnedPos1 == PinnedListItem.FIRST) return -1;
                if (pinnedPos1 == PinnedListItem.LAST) return 1;
            }

            if (pinnedPos1 == PinnedListItem.UNPINNED && pinnedPos2 != PinnedListItem.UNPINNED) {
                if (pinnedPos2 == PinnedListItem.FIRST) return 1;
                if (pinnedPos2 == PinnedListItem.LAST) return -1;
            }

            if (pinnedPos1 != PinnedListItem.UNPINNED && pinnedPos2 != PinnedListItem.UNPINNED) {
                if (pinnedPos1 == pinnedPos2) {
                    PinnedListItem po1 = (PinnedListItem) o1;
                    PinnedListItem po2 = (PinnedListItem) o2;
                    return po1.getPinnedPriority() - po2.getPinnedPriority();
                }
                if (pinnedPos1 == PinnedListItem.LAST) return 1;

                return -1;
            }

            ScanResult o1ScanResult = o1.getScanResult();
            ScanResult o2ScanResult = o2.getScanResult();
            if (o1ScanResult == null) {
                if (o2ScanResult == null) {
                    return 0;
                } else {
                    return 1;
                }
            } else {
                if (o2ScanResult == null) {
                    return -1;
                } else {
                    int levelDiff = o2ScanResult.level - o1ScanResult.level;
                    if (levelDiff != 0) {
                        return levelDiff;
                    }
                    return o1ScanResult.SSID.compareTo(o2ScanResult.SSID);
                }
            }
        }
    }

    public static class ListItem implements Parcelable {

        private final String mName;
        private final int mIconResource;
        private final int mIconLevel;
        private final boolean mHasIconLevel;
        private final ScanResult mScanResult;

        public ListItem(String name, int iconResource) {
            mName = name;
            mIconResource = iconResource;
            mIconLevel = 0;
            mHasIconLevel = false;
            mScanResult = null;
        }

        public ListItem(ScanResult scanResult) {
            mName = scanResult.SSID;
            mIconResource = WifiSecurity.NONE == WifiSecurity.getSecurity(scanResult)
                    ? R.drawable.setup_wifi_signal_open
                    : R.drawable.setup_wifi_signal_lock;
            mIconLevel = WifiManager.calculateSignalLevel(scanResult.level, 4);
            mHasIconLevel = true;
            mScanResult = scanResult;
        }

        public String getName() {
            return mName;
        }

        int getIconResource() {
            return mIconResource;
        }

        int getIconLevel() {
            return mIconLevel;
        }

        boolean hasIconLevel() {
            return mHasIconLevel;
        }

        ScanResult getScanResult() {
            return mScanResult;
        }

        /**
         * Returns whether this item is pinned to the front/back of a sorted list.  Returns
         * PinnedListItem.UNPINNED if the item is not pinned.
         * @return  the pinned/unpinned setting for this item.
         */
        public int getPinnedPosition() {
            return PinnedListItem.UNPINNED;
        }

        @Override
        public String toString() {
            return mName;
        }

        public static Parcelable.Creator<ListItem> CREATOR = new Parcelable.Creator<ListItem>() {

            @Override
            public ListItem createFromParcel(Parcel source) {
                ScanResult scanResult = source.readParcelable(ScanResult.class.getClassLoader());
                if (scanResult == null) {
                    return new ListItem(source.readString(), source.readInt());
                } else {
                    return new ListItem(scanResult);
                }
            }

            @Override
            public ListItem[] newArray(int size) {
                return new ListItem[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeParcelable(mScanResult, flags);
            if (mScanResult == null) {
                dest.writeString(mName);
                dest.writeInt(mIconResource);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof ListItem) {
                ListItem li = (ListItem) o;
                if (mScanResult == null && li.mScanResult == null) {
                    return TextUtils.equals(mName, li.mName);
                }
                return (mScanResult != null && li.mScanResult != null
                        && TextUtils.equals(mName, li.mName)
                        && WifiSecurity.getSecurity(mScanResult)
                                == WifiSecurity.getSecurity(li.mScanResult));
            }
            return false;
        }
    }

    public static class PinnedListItem extends ListItem {
        public static final int UNPINNED = 0;
        public static final int FIRST = 1;
        public static final int LAST = 2;

        private int mPinnedPosition;
        private int mPinnedPriority;

        public PinnedListItem(
                String name, int iconResource, int pinnedPosition, int pinnedPriority) {
            super(name, iconResource);
            mPinnedPosition = pinnedPosition;
            mPinnedPriority = pinnedPriority;
        }

        @Override
        public int getPinnedPosition() {
            return mPinnedPosition;
        }

        /**
         * Returns the priority for this item, which is used for ordering the item between pinned
         * items in a sorted list.  For example, if two items are pinned to the front of the list
         * (FIRST), the priority value is used to determine their ordering.
         * @return  the sorting priority for this item
         */
        public int getPinnedPriority() {
            return mPinnedPriority;
        }
    }

    public interface Listener {
        void onListSelectionComplete(ListItem listItem);
        void onListFocusChanged(ListItem listItem);
    }

    private static interface ActionListener {
        public void onClick(ListItem item);
        public void onFocus(ListItem item);
    }

    private static class ListItemViewHolder extends RecyclerView.ViewHolder implements
            FacetProvider {
        public ListItemViewHolder(View v) {
            super(v);
        }

        public void init(ListItem item, View.OnClickListener onClick,
                View.OnFocusChangeListener onFocusChange) {
            TextView title = (TextView) itemView.findViewById(R.id.list_item_text);
            title.setText(item.getName());
            itemView.setOnClickListener(onClick);
            itemView.setOnFocusChangeListener(onFocusChange);

            int iconResource = item.getIconResource();
            ImageView icon = (ImageView) itemView.findViewById(R.id.list_item_icon);
            // Set the icon if there is one.
            if (iconResource == 0) {
                icon.setVisibility(View.GONE);
                return;
            }
            icon.setVisibility(View.VISIBLE);
            icon.setImageResource(iconResource);
            if (item.hasIconLevel()) {
                icon.setImageLevel(item.getIconLevel());
            }
        }

        // Provide a customized ItemAlignmentFacet so that the mean line of textView is matched.
        // Here We use mean line of the textview to work as the baseline to be matched with
        // guidance title baseline.
        @Override
        public Object getFacet(Class facet) {
            if (facet.equals(ItemAlignmentFacet.class)) {
                ItemAlignmentFacet.ItemAlignmentDef alignedDef =
                        new ItemAlignmentFacet.ItemAlignmentDef();
                alignedDef.setItemAlignmentViewId(R.id.list_item_text);
                alignedDef.setAlignedToTextViewBaseline(false);
                alignedDef.setItemAlignmentOffset(0);
                alignedDef.setItemAlignmentOffsetWithPadding(true);
                // 50 refers to 50 percent, which refers to mid position of textView.
                alignedDef.setItemAlignmentOffsetPercent(50);
                ItemAlignmentFacet f = new ItemAlignmentFacet();
                f.setAlignmentDefs(new ItemAlignmentDef[] {alignedDef});
                return f;
            }
            return null;
        }
    }

    private class VerticalListAdapter extends RecyclerView.Adapter {
        private SortedList mItems;
        private final ActionListener mActionListener;

        public VerticalListAdapter(ActionListener actionListener, List<ListItem> choices) {
            super();
            mActionListener = actionListener;
            ListItemComparator comparator = new ListItemComparator();
            mItems = new SortedList<ListItem>(
                    ListItem.class, new SortedListAdapterCallback<ListItem>(this) {
                        @Override
                        public int compare(ListItem t0, ListItem t1) {
                            return comparator.compare(t0, t1);
                        }

                        @Override
                        public boolean areContentsTheSame(ListItem oldItem, ListItem newItem) {
                            return comparator.compare(oldItem, newItem) == 0;
                        }

                        @Override
                        public boolean areItemsTheSame(ListItem item1, ListItem item2) {
                            return item1.equals(item2);
                        }
                    });
            mItems.addAll(choices.toArray(new ListItem[0]), false);
        }

        private View.OnClickListener createClickListener(final ListItem item) {
            return new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v == null || v.getWindowToken() == null || mActionListener == null) {
                        return;
                    }
                    mActionListener.onClick(item);
                }
            };
        }

        private View.OnFocusChangeListener createFocusListener(final ListItem item) {
            return new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (v == null || v.getWindowToken() == null || mActionListener == null
                            || !hasFocus) {
                        return;
                    }
                    mActionListener.onFocus(item);
                }
            };
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
            View v = inflater.inflate(R.layout.setup_list_item, parent, false);
            return new ListItemViewHolder(v);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder baseHolder, int position) {
            if (position >= mItems.size()) {
                return;
            }

            ListItemViewHolder viewHolder = (ListItemViewHolder) baseHolder;
            ListItem item = (ListItem) mItems.get(position);
            viewHolder.init((ListItem) item, createClickListener(item), createFocusListener(item));
        }

        public SortedList<ListItem> getItems() {
            return mItems;
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        public void updateItems(List<ListItem> inputItems) {
            TreeSet<ListItem> newItemSet = new TreeSet<ListItem>(new ListItemComparator());
            for (ListItem item: inputItems) {
                newItemSet.add(item);
            }
            ArrayList<ListItem> toRemove = new ArrayList<ListItem>();
            for (int j = 0 ; j < mItems.size(); j++) {
                ListItem oldItem = (ListItem) mItems.get(j);
                if (!newItemSet.contains(oldItem)) {
                    toRemove.add(oldItem);
                }
            }
            for (ListItem item: toRemove) {
                mItems.remove(item);
            }
            mItems.addAll(inputItems.toArray(new ListItem[0]), true);
        }
    }

    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_DESCRIPTION = "description";
    private static final String EXTRA_LIST_ELEMENTS = "list_elements";
    private static final String EXTRA_LAST_SELECTION = "last_selection";
    private static final int SELECT_ITEM_DELAY = 100;

    public static SelectFromListWizardFragment newInstance(String title, String description,
            ArrayList<ListItem> listElements, ListItem lastSelection) {
        SelectFromListWizardFragment fragment = new SelectFromListWizardFragment();
        Bundle args = new Bundle();
        args.putString(EXTRA_TITLE, title);
        args.putString(EXTRA_DESCRIPTION, description);
        args.putParcelableArrayList(EXTRA_LIST_ELEMENTS, listElements);
        args.putParcelable(EXTRA_LAST_SELECTION, lastSelection);
        fragment.setArguments(args);
        return fragment;
    }

    private Handler mHandler;
    private View mMainView;
    private VerticalGridView mListView;
    private String mLastSelectedName;
    private OnPreDrawListener mOnListPreDrawListener;
    private Runnable mSelectItemRunnable;

    private void updateSelected(String lastSelectionName) {
        SortedList<ListItem> items = ((VerticalListAdapter) mListView.getAdapter()).getItems();
        for (int i = 0; i < items.size(); i++) {
            ListItem item = (ListItem) items.get(i);
            if (TextUtils.equals(lastSelectionName, item.getName())) {
                mListView.setSelectedPosition(i);
                break;
            }
        }
        mLastSelectedName = lastSelectionName;
    }

    public void update(List<ListItem> listElements) {
        // We want keep the highlight on the same selected item from before the update.  This is
        // currently not possible (b/28120126).  So we post a runnable to run after the update
        // completes.
        if (mSelectItemRunnable != null) {
            mHandler.removeCallbacks(mSelectItemRunnable);
        }

        final String lastSelected = mLastSelectedName;
        mSelectItemRunnable = () -> {
            updateSelected(lastSelected);
            if (mOnListPreDrawListener != null) {
                mListView.getViewTreeObserver().removeOnPreDrawListener(mOnListPreDrawListener);
                mOnListPreDrawListener = null;
            }
            mSelectItemRunnable = null;
        };

        if (mOnListPreDrawListener != null) {
            mListView.getViewTreeObserver().removeOnPreDrawListener(mOnListPreDrawListener);
        }

        mOnListPreDrawListener = () -> {
            mHandler.removeCallbacks(mSelectItemRunnable);
            // Pre-draw can be called multiple times per update.  We delay the runnable to select
            // the item so that it will only run after the last pre-draw of this batch of update.
            mHandler.postDelayed(mSelectItemRunnable, SELECT_ITEM_DELAY);
            return true;
        };

        mListView.getViewTreeObserver().addOnPreDrawListener(mOnListPreDrawListener);
        ((VerticalListAdapter) mListView.getAdapter()).updateItems(listElements);
    }

    private static float getKeyLinePercent(Context context) {
        TypedArray ta = context.getTheme().obtainStyledAttributes(
                R.styleable.LeanbackGuidedStepTheme);
        float percent = ta.getFloat(R.styleable.LeanbackGuidedStepTheme_guidedStepKeyline, 40);
        ta.recycle();
        return percent;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle icicle) {
        Resources resources = getContext().getResources();

        mHandler = new Handler();
        mMainView = inflater.inflate(R.layout.account_content_area, container, false);

        final ViewGroup descriptionArea = (ViewGroup) mMainView.findViewById(R.id.description);
        final View content = inflater.inflate(R.layout.wifi_content, descriptionArea, false);
        descriptionArea.addView(content);

        final ViewGroup actionArea = (ViewGroup) mMainView.findViewById(R.id.action);

        TextView titleText = (TextView) content.findViewById(R.id.guidance_title);
        TextView descriptionText = (TextView) content.findViewById(R.id.guidance_description);
        Bundle args = getArguments();
        String title = args.getString(EXTRA_TITLE);
        String description = args.getString(EXTRA_DESCRIPTION);

        boolean forceFocusable = AccessibilityHelper.forceFocusableViews(getActivity());
        if (title != null) {
            titleText.setText(title);
            titleText.setVisibility(View.VISIBLE);
            if (forceFocusable) {
                titleText.setFocusable(true);
                titleText.setFocusableInTouchMode(true);
            }
        } else {
            titleText.setVisibility(View.GONE);
        }

        if (description != null) {
            descriptionText.setText(description);
            descriptionText.setVisibility(View.VISIBLE);
            if (forceFocusable) {
                descriptionText.setFocusable(true);
                descriptionText.setFocusableInTouchMode(true);
            }
        } else {
            descriptionText.setVisibility(View.GONE);
        }

        ArrayList<ListItem> listItems = args.getParcelableArrayList(EXTRA_LIST_ELEMENTS);

        mListView =
                (VerticalGridView) inflater.inflate(R.layout.setup_list_view, actionArea, false);

        SelectFromListWizardFragment.align(mListView, getActivity());

        actionArea.addView(mListView);
        ActionListener actionListener = new ActionListener() {
            @Override
            public void onClick(ListItem item) {
                Activity a = getActivity();
                if (a instanceof Listener && isResumed()) {
                    ((Listener) a).onListSelectionComplete(item);
                }
            }

            @Override
            public void onFocus(ListItem item) {
                Activity a = getActivity();
                mLastSelectedName = item.getName();
                if (a instanceof Listener) {
                    ((Listener) a).onListFocusChanged(item);
                }
            }
        };
        mListView.setAdapter(new VerticalListAdapter(actionListener, listItems));

        ListItem lastSelection = args.getParcelable(EXTRA_LAST_SELECTION);
        if (lastSelection != null) {
            updateSelected(lastSelection.getName());
        }
        return mMainView;
    }

    private static void align(VerticalGridView listView, Activity activity) {
        Context context = listView.getContext();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        float keyLinePercent = getKeyLinePercent(context);
        activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        listView.setItemSpacing(activity.getResources()
                .getDimensionPixelSize(R.dimen.setup_list_item_margin));
        // Make the keyline of the page match with the mean line(roughly) of the first list item.
        listView.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_HIGH_EDGE);
        listView.setWindowAlignmentOffset(0);
        listView.setWindowAlignmentOffsetPercent(keyLinePercent);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mSelectItemRunnable != null) {
            mHandler.removeCallbacks(mSelectItemRunnable);
            mSelectItemRunnable = null;
        }
        if (mOnListPreDrawListener != null) {
            mListView.getViewTreeObserver().removeOnPreDrawListener(mOnListPreDrawListener);
            mOnListPreDrawListener = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                InputMethodManager inputMethodManager = (InputMethodManager) getActivity()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMethodManager.hideSoftInputFromWindow(
                        mMainView.getApplicationWindowToken(), 0);
            }
        });
    }
}
