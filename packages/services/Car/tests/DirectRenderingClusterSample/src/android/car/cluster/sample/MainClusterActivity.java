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
package android.car.cluster.sample;

import static android.car.cluster.sample.SampleClusterServiceImpl.LOCAL_BINDING_ACTION;

import android.car.cluster.sample.SampleClusterServiceImpl.Listener;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

public class MainClusterActivity extends FragmentActivity
        implements Listener {
    private static final String TAG = MainClusterActivity.class.getSimpleName();

    private Button mNavButton;
    private Button mPhoneButton;
    private Button mCarInfoButton;
    private Button mMusicButton;
    private TextView mTextOverlay;
    private ViewPager mPager;

    private SampleClusterServiceImpl mService;

    private final Handler mHandler = new Handler();

    private HashMap<Button, Facet<?>> mButtonToFacet = new HashMap<>();
    private SparseArray<Facet<?>> mOrderToFacet = new SparseArray<>();

    private final View.OnFocusChangeListener mFacetButtonFocusListener =
            new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus) {
                mPager.setCurrentItem(mButtonToFacet.get(v).order);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = new Intent(this, SampleClusterServiceImpl.class);
        intent.setAction(LOCAL_BINDING_ACTION);
        bindService(intent,
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        Log.i(TAG, "onServiceConnected, name: " + name + ", service: " + service);
                        mService = ((SampleClusterServiceImpl.LocalBinder) service)
                                .getService();
                        mService.registerListener(MainClusterActivity.this);
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        Log.i(TAG, "onServiceDisconnected, name: " + name);
                        mService = null;
                    }
                }, BIND_AUTO_CREATE);

        mNavButton = findViewById(R.id.btn_nav);
        mPhoneButton = findViewById(R.id.btn_phone);
        mCarInfoButton = findViewById(R.id.btn_car_info);
        mMusicButton = findViewById(R.id.btn_music);
        mTextOverlay = findViewById(R.id.text_overlay);

        registerFacets(
                new Facet<>(mNavButton, 0, NavigationFragment.class),
                new Facet<>(mPhoneButton, 1, PhoneFragment.class),
                new Facet<>(mMusicButton, 2, MusicFragment.class),
                new Facet<>(mCarInfoButton, 3, CarInfoFragment.class));

        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(new ClusterPageAdapter(getSupportFragmentManager()));

        mNavButton.requestFocus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mService != null) {
            mService.unregisterListener();
        }
    }

    @Override
    public void onShowToast(String text) {
        if (mTextOverlay.getVisibility() == View.VISIBLE) {
            if (!TextUtils.isEmpty(mTextOverlay.getText())) {
                mTextOverlay.setText(mTextOverlay.getText() + "\n" + text);
            } else {
                mTextOverlay.setText(text);
            }
        }

        mTextOverlay.setVisibility(View.VISIBLE);

        mHandler.removeCallbacksAndMessages(null);
        mHandler.postDelayed(() -> {
            mTextOverlay.setVisibility(View.GONE);
            mTextOverlay.setText("");
        }, 3000);
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        Log.i(TAG, "onKeyEvent, event: " + event);
        dispatchKeyEvent(event);  // TODO: dispatch event doesn't work for some reason.

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int nextItem = (mPager.getCurrentItem() + 1) % mButtonToFacet.size();
                mOrderToFacet.get(nextItem).button.requestFocus();
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
                int nextItem = (mPager.getCurrentItem() - 1);
                if (nextItem < 0) nextItem =  mButtonToFacet.size() - 1;
                mOrderToFacet.get(nextItem).button.requestFocus();
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean consumed = super.dispatchKeyEvent(event);
        Log.i(TAG, "dispatchKeyEvent, event: " + event + ", consumed: " + consumed);
        return consumed;
    }

    public class ClusterPageAdapter extends FragmentPagerAdapter {
        public ClusterPageAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return mButtonToFacet.size();
        }

        @Override
        public Fragment getItem(int position) {
            return mOrderToFacet.get(position).getOrCreateFragment();
        }
    }

    private void registerFacets(Facet<?>... facets) {
        for (Facet<?> f : facets) {
            registerFacet(f);
        }
    }

    private <T> void registerFacet(Facet<T> facet) {
        mOrderToFacet.append(facet.order, facet);
        mButtonToFacet.put(facet.button, facet);

        facet.button.setOnFocusChangeListener(mFacetButtonFocusListener);
    }

    private static class Facet<T> {
        Button button;
        Class<T> clazz;
        int order;

        Facet(Button button, int order, Class<T> clazz) {
            this.button = button;
            this.order = order;
            this.clazz = clazz;
        }

        private Fragment mFragment;

        Fragment getOrCreateFragment() {
            if (mFragment == null) {
                try {
                    mFragment = (Fragment) clazz.getConstructors()[0].newInstance();
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
            return mFragment;
        }
    }

    SampleClusterServiceImpl getService() {
        return mService;
    }
}
