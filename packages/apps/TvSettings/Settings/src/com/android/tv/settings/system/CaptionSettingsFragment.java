/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tv.settings.system;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.CaptioningManager;

import com.android.internal.widget.SubtitleView;
import com.android.settingslib.accessibility.AccessibilityUtils;
import com.android.tv.settings.BaseSettingsFragment;
import com.android.tv.settings.R;

import java.util.Locale;

public class CaptionSettingsFragment extends BaseSettingsFragment {

    public static final String ACTION_REFRESH_CAPTIONS_PREVIEW = "CaptionSettingsFragment.refresh";

    private int mDefaultFontSize;

    private SubtitleView mPreviewText;
    private View mPreviewWindow;
    private CaptioningManager mCaptioningManager;
    private final CaptioningManager.CaptioningChangeListener mCaptionChangeListener =
            new CaptioningManager.CaptioningChangeListener() {

                @Override
                public void onEnabledChanged(boolean enabled) {
                    refreshPreviewText();
                }

                @Override
                public void onUserStyleChanged(@NonNull CaptioningManager.CaptionStyle userStyle) {
                    loadCaptionSettings();
                    refreshPreviewText();
                }

                @Override
                public void onLocaleChanged(Locale locale) {
                    loadCaptionSettings();
                    refreshPreviewText();
                }

                @Override
                public void onFontScaleChanged(float fontScale) {
                    loadCaptionSettings();
                    refreshPreviewText();
                }
            };
    private final BroadcastReceiver mRefreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshPreviewText();
        }
    };

    private float mFontScale;
    private int mStyleId;
    private Locale mLocale;

    public static CaptionSettingsFragment newInstance() {
        return new CaptionSettingsFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final ViewGroup v = (ViewGroup) super.onCreateView(inflater, container, savedInstanceState);
        if (v == null) {
            throw new IllegalStateException("Unexpectedly null view from super");
        }
        inflater.inflate(R.layout.captioning_preview, v, true);
        return v;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mCaptioningManager =
                (CaptioningManager) getActivity().getSystemService(Context.CAPTIONING_SERVICE);

        mDefaultFontSize =
                getResources().getInteger(R.integer.captioning_preview_default_font_size);

        loadCaptionSettings();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mPreviewText = (SubtitleView) view.findViewById(R.id.preview_text);
        mPreviewWindow = view.findViewById(R.id.preview_window);
    }

    @Override
    public void onPreferenceStartInitialScreen() {
        startPreferenceFragment(CaptionFragment.newInstance());
    }

    @Override
    public void onStart() {
        super.onStart();
        mCaptioningManager.addCaptioningChangeListener (mCaptionChangeListener);
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mRefreshReceiver,
                new IntentFilter(ACTION_REFRESH_CAPTIONS_PREVIEW));
        refreshPreviewText();
    }

    @Override
    public void onStop() {
        super.onStop();
        mCaptioningManager.removeCaptioningChangeListener (mCaptionChangeListener);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mRefreshReceiver);
    }

    private void loadCaptionSettings() {
        mFontScale = mCaptioningManager.getFontScale();
        mStyleId = mCaptioningManager.getRawUserStyle();
        mLocale = mCaptioningManager.getLocale();
    }

    private void refreshPreviewText() {
        if (mPreviewText != null) {
            boolean enabled = mCaptioningManager.isEnabled();
            if (enabled) {
                mPreviewText.setVisibility(View.VISIBLE);
                Activity activity = getActivity();
                mPreviewText.setStyle(mStyleId);
                mPreviewText.setTextSize(mFontScale * mDefaultFontSize);
                if (mLocale != null) {
                    CharSequence localizedText = AccessibilityUtils.getTextForLocale(
                            activity, mLocale, R.string.captioning_preview_text);
                    mPreviewText.setText(localizedText);
                } else {
                    mPreviewText.setText(getResources()
                            .getString(R.string.captioning_preview_text));
                }

                final CaptioningManager.CaptionStyle style = mCaptioningManager.getUserStyle();
                if (style.hasWindowColor()) {
                    mPreviewWindow.setBackgroundColor(style.windowColor);
                } else {
                    final CaptioningManager.CaptionStyle defStyle =
                            CaptioningManager.CaptionStyle.DEFAULT;
                    mPreviewWindow.setBackgroundColor(defStyle.windowColor);
                }

                mPreviewText.invalidate();
            } else {
                mPreviewText.setVisibility(View.INVISIBLE);
            }
        }
    }
}
