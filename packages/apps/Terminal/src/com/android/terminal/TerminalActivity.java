/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.terminal;

import static com.android.terminal.Terminal.TAG;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.PagerTitleStrip;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

/**
 * Activity that displays all {@link Terminal} instances running in a bound
 * {@link TerminalService}.
 */
public class TerminalActivity extends Activity {

    private TerminalService mService;

    private ViewPager mPager;
    private PagerTitleStrip mTitles;

    private final ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = ((TerminalService.ServiceBinder) service).getService();

            final int size = mService.getTerminals().size();
            Log.d(TAG, "Bound to service with " + size + " active terminals");

            // Give ourselves at least one terminal session
            if (size == 0) {
                mService.createTerminal();
            }

            // Bind UI to known terminals
            mTermAdapter.notifyDataSetChanged();
            invalidateOptionsMenu();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            throw new RuntimeException("Service in same process disconnected?");
        }
    };

    private final PagerAdapter mTermAdapter = new PagerAdapter() {
        private SparseArray<SparseArray<Parcelable>>
                mSavedState = new SparseArray<SparseArray<Parcelable>>();

        @Override
        public int getCount() {
            if (mService != null) {
                return mService.getTerminals().size();
            } else {
                return 0;
            }
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            final TerminalView view = new TerminalView(container.getContext());
            view.setId(android.R.id.list);

            final Terminal term = mService.getTerminals().valueAt(position);
            view.setTerminal(term);

            final SparseArray<Parcelable> state = mSavedState.get(term.key);
            if (state != null) {
                view.restoreHierarchyState(state);
            }

            container.addView(view);
            view.requestFocus();
            return view;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            final TerminalView view = (TerminalView) object;

            final int key = view.getTerminal().key;
            SparseArray<Parcelable> state = mSavedState.get(key);
            if (state == null) {
                state = new SparseArray<Parcelable>();
                mSavedState.put(key, state);
            }
            view.saveHierarchyState(state);

            view.setTerminal(null);
            container.removeView(view);
        }

        @Override
        public int getItemPosition(Object object) {
            final TerminalView view = (TerminalView) object;
            final int key = view.getTerminal().key;
            final int index = mService.getTerminals().indexOfKey(key);
            if (index == -1) {
                return POSITION_NONE;
            } else {
                return index;
            }
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mService.getTerminals().valueAt(position).getTitle();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity);

        mPager = (ViewPager) findViewById(R.id.pager);
        mTitles = (PagerTitleStrip) findViewById(R.id.titles);

        mPager.setAdapter(mTermAdapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(
                new Intent(this, TerminalService.class), mServiceConn, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(mServiceConn);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.menu_close_tab).setEnabled(mTermAdapter.getCount() > 0);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_new_tab: {
                mService.createTerminal();
                mTermAdapter.notifyDataSetChanged();
                invalidateOptionsMenu();
                final int index = mService.getTerminals().size() - 1;
                mPager.setCurrentItem(index, true);
                return true;
            }
            case R.id.menu_close_tab: {
                final int index = mPager.getCurrentItem();
                final int key = mService.getTerminals().keyAt(index);
                mService.destroyTerminal(key);
                mTermAdapter.notifyDataSetChanged();
                invalidateOptionsMenu();
                return true;
            }
        }
        return false;
    }
}
