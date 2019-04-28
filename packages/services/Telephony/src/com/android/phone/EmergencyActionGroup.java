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
 * limitations under the License
 */

package com.android.phone;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.List;

public class EmergencyActionGroup extends FrameLayout implements View.OnClickListener {

    private static final long HIDE_DELAY = 3000;
    private static final int RIPPLE_DURATION = 600;
    private static final long RIPPLE_PAUSE = 1000;

    private final Interpolator mFastOutLinearInInterpolator;

    private ViewGroup mSelectedContainer;
    private TextView mSelectedLabel;
    private View mRippleView;
    private View mLaunchHint;

    private View mLastRevealed;

    private MotionEvent mPendingTouchEvent;

    private boolean mHiding;

    public EmergencyActionGroup(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mFastOutLinearInInterpolator = AnimationUtils.loadInterpolator(context,
                android.R.interpolator.fast_out_linear_in);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mSelectedContainer = (ViewGroup) findViewById(R.id.selected_container);
        mSelectedContainer.setOnClickListener(this);
        mSelectedLabel = (TextView) findViewById(R.id.selected_label);
        mRippleView = findViewById(R.id.ripple_view);
        mLaunchHint = findViewById(R.id.launch_hint);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == View.VISIBLE) {
            setupAssistActions();
        }
    }

    /**
     * Called by the activity before a touch event is dispatched to the view hierarchy.
     */
    public void onPreTouchEvent(MotionEvent event) {
        mPendingTouchEvent = event;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        boolean handled = super.dispatchTouchEvent(event);
        if (mPendingTouchEvent == event && handled) {
            mPendingTouchEvent = null;
        }
        return handled;
    }

    /**
     * Called by the activity after a touch event is dispatched to the view hierarchy.
     */
    public void onPostTouchEvent(MotionEvent event) {
        // Hide the confirmation button if a touch event was delivered to the activity but not to
        // this view.
        if (mPendingTouchEvent != null) {
            hideTheButton();
        }
        mPendingTouchEvent = null;
    }



    private void setupAssistActions() {
        int[] buttonIds = new int[] {R.id.action1, R.id.action2, R.id.action3};

        List<ResolveInfo> infos;

        if (TelephonyManager.EMERGENCY_ASSISTANCE_ENABLED) {
            infos = resolveAssistPackageAndQueryActivites();
        } else {
            infos = null;
        }

        for (int i = 0; i < 3; i++) {
            Button button = (Button) findViewById(buttonIds[i]);
            boolean visible = false;

            button.setOnClickListener(this);

            if (infos != null && infos.size() > i && infos.get(i) != null) {
                ResolveInfo info = infos.get(i);
                ComponentName name = getComponentName(info);

                button.setTag(R.id.tag_intent,
                        new Intent(TelephonyManager.ACTION_EMERGENCY_ASSISTANCE)
                                .setComponent(name));
                button.setText(info.loadLabel(getContext().getPackageManager()));
                visible = true;
            }

            button.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private List<ResolveInfo> resolveAssistPackageAndQueryActivites() {
        List<ResolveInfo> infos = queryAssistActivities();

        if (infos == null || infos.isEmpty()) {
            PackageManager packageManager = getContext().getPackageManager();
            Intent queryIntent = new Intent(TelephonyManager.ACTION_EMERGENCY_ASSISTANCE);
            infos = packageManager.queryIntentActivities(queryIntent, 0);

            PackageInfo bestMatch = null;
            for (int i = 0; i < infos.size(); i++) {
                if (infos.get(i).activityInfo == null) continue;
                String packageName = infos.get(i).activityInfo.packageName;
                PackageInfo packageInfo;
                try {
                    packageInfo = packageManager.getPackageInfo(packageName, 0);
                } catch (PackageManager.NameNotFoundException e) {
                    continue;
                }
                // Get earliest installed system app.
                if (isSystemApp(packageInfo) && (bestMatch == null ||
                        bestMatch.firstInstallTime > packageInfo.firstInstallTime)) {
                    bestMatch = packageInfo;
                }
            }

            if (bestMatch != null) {
                Settings.Secure.putString(getContext().getContentResolver(),
                        Settings.Secure.EMERGENCY_ASSISTANCE_APPLICATION,
                        bestMatch.packageName);
                return queryAssistActivities();
            } else {
                return null;
            }
        } else {
            return infos;
        }
    }

    private List<ResolveInfo> queryAssistActivities() {
        String assistPackage = Settings.Secure.getString(
                getContext().getContentResolver(),
                Settings.Secure.EMERGENCY_ASSISTANCE_APPLICATION);
        List<ResolveInfo> infos = null;

        if (!TextUtils.isEmpty(assistPackage)) {
            Intent queryIntent = new Intent(TelephonyManager.ACTION_EMERGENCY_ASSISTANCE)
                    .setPackage(assistPackage);
            infos = getContext().getPackageManager().queryIntentActivities(queryIntent, 0);
        }
        return infos;
    }

    private boolean isSystemApp(PackageInfo info) {
        return info.applicationInfo != null
                && (info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    private ComponentName getComponentName(ResolveInfo resolveInfo) {
        if (resolveInfo == null || resolveInfo.activityInfo == null) return null;
        return new ComponentName(resolveInfo.activityInfo.packageName,
                resolveInfo.activityInfo.name);
    }

    @Override
    public void onClick(View v) {
        Intent intent = (Intent) v.getTag(R.id.tag_intent);

        switch (v.getId()) {
            case R.id.action1:
            case R.id.action2:
            case R.id.action3:
                if (AccessibilityManager.getInstance(mContext).isTouchExplorationEnabled()) {
                    getContext().startActivity(intent);
                } else {
                    revealTheButton(v);
                }
                break;
            case R.id.selected_container:
                if (!mHiding) {
                    getContext().startActivity(intent);
                }
                break;
        }
    }

    private void revealTheButton(View v) {
        CharSequence buttonText = ((Button) v).getText();
        mSelectedLabel.setText(buttonText);
        mSelectedLabel.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
        mSelectedContainer.setVisibility(VISIBLE);
        int centerX = v.getLeft() + v.getWidth() / 2;
        int centerY = v.getTop() + v.getHeight() / 2;
        Animator reveal = ViewAnimationUtils.createCircularReveal(
                mSelectedContainer,
                centerX,
                centerY,
                0,
                Math.max(centerX, mSelectedContainer.getWidth() - centerX)
                        + Math.max(centerY, mSelectedContainer.getHeight() - centerY));
        reveal.start();

        animateHintText(mSelectedLabel, v, reveal);
        animateHintText(mLaunchHint, v, reveal);

        mSelectedContainer.setTag(R.id.tag_intent, v.getTag(R.id.tag_intent));
        mLastRevealed = v;
        postDelayed(mHideRunnable, HIDE_DELAY);
        postDelayed(mRippleRunnable, RIPPLE_PAUSE / 2);

        // Transfer focus from the originally clicked button to the expanded button.
        mSelectedContainer.requestFocus();
    }

    private void animateHintText(View selectedView, View v, Animator reveal) {
        selectedView.setTranslationX(
                (v.getLeft() + v.getWidth() / 2 - mSelectedContainer.getWidth() / 2) / 5);
        selectedView.animate()
                .setDuration(reveal.getDuration() / 3)
                .setStartDelay(reveal.getDuration() / 5)
                .translationX(0)
                .setInterpolator(mFastOutLinearInInterpolator)
                .start();
    }

    private void hideTheButton() {
        if (mHiding || mSelectedContainer.getVisibility() != VISIBLE) {
            return;
        }

        mHiding = true;

        removeCallbacks(mHideRunnable);

        View v = mLastRevealed;
        int centerX = v.getLeft() + v.getWidth() / 2;
        int centerY = v.getTop() + v.getHeight() / 2;
        Animator reveal = ViewAnimationUtils.createCircularReveal(
                mSelectedContainer,
                centerX,
                centerY,
                Math.max(centerX, mSelectedContainer.getWidth() - centerX)
                        + Math.max(centerY, mSelectedContainer.getHeight() - centerY),
                0);
        reveal.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mSelectedContainer.setVisibility(INVISIBLE);
                removeCallbacks(mRippleRunnable);
                mHiding = false;
            }
        });
        reveal.start();

        // Transfer focus back to the originally clicked button.
        if (mSelectedContainer.isFocused()) {
            v.requestFocus();
        }
    }

    private void startRipple() {
        final View ripple = mRippleView;
        ripple.animate().cancel();
        ripple.setVisibility(VISIBLE);
        Animator reveal = ViewAnimationUtils.createCircularReveal(
                ripple,
                ripple.getLeft() + ripple.getWidth() / 2,
                ripple.getTop() + ripple.getHeight() / 2,
                0,
                ripple.getWidth() / 2);
        reveal.setDuration(RIPPLE_DURATION);
        reveal.start();

        ripple.setAlpha(0);
        ripple.animate().alpha(1).setDuration(RIPPLE_DURATION / 2)
                .withEndAction(new Runnable() {
            @Override
            public void run() {
                ripple.animate().alpha(0).setDuration(RIPPLE_DURATION / 2)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                ripple.setVisibility(INVISIBLE);
                                postDelayed(mRippleRunnable, RIPPLE_PAUSE);
                            }
                        }).start();
            }
        }).start();
    }

    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAttachedToWindow()) return;
            hideTheButton();
        }
    };

    private final Runnable mRippleRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAttachedToWindow()) return;
            startRipple();
        }
    };


}
