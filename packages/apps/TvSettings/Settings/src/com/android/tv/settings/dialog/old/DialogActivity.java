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

package com.android.tv.settings.dialog.old;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentManager.OnBackStackChangedListener;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;

import com.android.tv.settings.R;

import java.util.ArrayList;

/**
 * A DialogActivity has 2 fragments, a content fragment and a list fragment.
 * <p>
 * Subclasses should override to supply the content fragment and list items.
 * <p>
 * The DialogActivity will handle animating in and out.
 * <p>
 * This class will use a default layout, but a custom layout can be provided by
 * calling {@link #setLayoutProperties}
 */
public abstract class DialogActivity extends Activity
        implements ActionAdapter.Listener, OnBackStackChangedListener {

    /**
     * Dialog Content Fragment title.
     */
    public static final String EXTRA_DIALOG_TITLE = "dialog_title";

    /**
     * Dialog Content Fragment breadcrumb.
     */
    public static final String EXTRA_DIALOG_BREADCRUMB = "dialog_breadcrumb";

    /**
     * Dialog Content Fragment description.
     */
    public static final String EXTRA_DIALOG_DESCRIPTION = "dialog_description";

    /**
     * Dialog Content Fragment image uri.
     */
    public static final String EXTRA_DIALOG_IMAGE_URI = "dialog_image_uri";

    /**
     * Dialog Content Fragment image background color
     */
    public static final String EXTRA_DIALOG_IMAGE_BACKGROUND_COLOR
            = "dialog_image_background_color";

    /**
     * Dialog Action Fragment actions starting index.
     */
    public static final String EXTRA_DIALOG_ACTIONS_START_INDEX = "dialog_actions_start_index";

    /**
     * Dialog Action Fragment actions.
     */
    public static final String EXTRA_PARCELABLE_ACTIONS = "parcelable_actions";

    /**
     * Whether DialogActivity should create Content Fragment and Action Fragment from extras.
     */
    public static final String EXTRA_CREATE_FRAGMENT_FROM_EXTRA = "create_fragment_from_extra";

    public static final String TAG_DIALOG = "tag_dialog";
    public static final String BACKSTACK_NAME_DIALOG = "backstack_name_dialog";
    public static final String KEY_BACKSTACK_COUNT = "backstack_count";

    protected static final int ANIMATE_IN_DURATION = 250;

    private DialogFragment mDialogFragment;
    private int mLayoutResId = R.layout.lb_dialog_fragment;
    private View mContent;
    private int mLastBackStackCount = 0;

    public DialogActivity() {
        mDialogFragment = new DialogFragment();
        mDialogFragment.setActivity(this);
    }

    public static Intent createIntent(Context context, String title,
            String breadcrumb, String description, String imageUri,
            ArrayList<Action> actions) {
        return createIntent(context, title, breadcrumb, description, imageUri,
                Color.TRANSPARENT, actions);
    }

    public static Intent createIntent(Context context, String title,
            String breadcrumb, String description, String imageUri,
            int imageBackground, ArrayList<Action> actions) {
        Intent intent = new Intent(context, DialogActivity.class);
        intent.putExtra(EXTRA_DIALOG_TITLE, title);
        intent.putExtra(EXTRA_DIALOG_BREADCRUMB, breadcrumb);
        intent.putExtra(EXTRA_DIALOG_DESCRIPTION, description);
        intent.putExtra(EXTRA_DIALOG_IMAGE_URI, imageUri);
        intent.putExtra(EXTRA_DIALOG_IMAGE_BACKGROUND_COLOR, imageBackground);
        intent.putParcelableArrayListExtra(EXTRA_PARCELABLE_ACTIONS, actions);

        return intent;
    }

    public static Intent createIntent(Context context, String title,
            String breadcrumb, String description, String imageUri,
            ArrayList<Action> actions, Class<? extends DialogActivity> activityClass) {
        return createIntent(context, title, breadcrumb, description, imageUri, Color.TRANSPARENT,
                actions, activityClass);
    }

    public static Intent createIntent(Context context, String title,
            String breadcrumb, String description, String imageUri, int imageBackground,
            ArrayList<Action> actions, Class<? extends DialogActivity> activityClass) {
        Intent intent = new Intent(context, activityClass);
        intent.putExtra(EXTRA_DIALOG_TITLE, title);
        intent.putExtra(EXTRA_DIALOG_BREADCRUMB, breadcrumb);
        intent.putExtra(EXTRA_DIALOG_DESCRIPTION, description);
        intent.putExtra(EXTRA_DIALOG_IMAGE_URI, imageUri);
        intent.putExtra(EXTRA_DIALOG_IMAGE_BACKGROUND_COLOR, imageBackground);
        intent.putParcelableArrayListExtra(EXTRA_PARCELABLE_ACTIONS, actions);

        return intent;
    }

    public static Intent createIntent(Context context, String title,
            String breadcrumb, String description, String imageUri, int imageBackground,
            ArrayList<Action> actions, Class<? extends DialogActivity> activityClass,
            int startIndex) {
        Intent intent = new Intent(context, activityClass);
        intent.putExtra(EXTRA_DIALOG_TITLE, title);
        intent.putExtra(EXTRA_DIALOG_BREADCRUMB, breadcrumb);
        intent.putExtra(EXTRA_DIALOG_DESCRIPTION, description);
        intent.putExtra(EXTRA_DIALOG_IMAGE_URI, imageUri);
        intent.putExtra(EXTRA_DIALOG_IMAGE_BACKGROUND_COLOR, imageBackground);
        intent.putParcelableArrayListExtra(EXTRA_PARCELABLE_ACTIONS, actions);
        intent.putExtra(EXTRA_DIALOG_ACTIONS_START_INDEX, startIndex);

        return intent;
    }

    public View getContentView() {
        return mContent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO: replace these hardcoded values with the commented constants whenever Hangouts
        // updates their manifest to build against JB MR2.
        if (Build.VERSION.SDK_INT >= 18 /* Build.VERSION_CODES.JELLY_BEAN_MR2 */) {
            getWindow().addFlags(0x02000000
                    /* WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN */);
        }
        if(savedInstanceState != null) {
            mLastBackStackCount = savedInstanceState.getInt(KEY_BACKSTACK_COUNT);
        }

        super.onCreate(savedInstanceState);
        getFragmentManager().addOnBackStackChangedListener(this);

        LayoutInflater helium = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mContent = helium.inflate(mLayoutResId, null);
        setContentView(mContent);
        if (mLayoutResId == R.layout.lb_dialog_fragment) {
            helium.inflate(R.layout.dialog_container, (ViewGroup) mContent);
            setDialogFragment(mDialogFragment);
        }

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            boolean createFragmentFromExtra = bundle.getBoolean(EXTRA_CREATE_FRAGMENT_FROM_EXTRA);
            if (createFragmentFromExtra) {
                // If intent bundle is not null, and flag indicates that should create fragments,
                // set ContentFragment and ActionFragment using bundle extras.
                String title = bundle.getString(EXTRA_DIALOG_TITLE);
                String breadcrumb = bundle.getString(EXTRA_DIALOG_BREADCRUMB);
                String description = bundle.getString(EXTRA_DIALOG_DESCRIPTION);
                String imageUriStr = bundle.getString(EXTRA_DIALOG_IMAGE_URI);
                Uri imageUri = Uri.parse(imageUriStr);
                int backgroundColor = bundle.getInt(EXTRA_DIALOG_IMAGE_BACKGROUND_COLOR);

                ArrayList<Action> actions =
                        bundle.getParcelableArrayList(EXTRA_PARCELABLE_ACTIONS);

                setContentFragment(ContentFragment.newInstance(title, breadcrumb,
                        description, imageUri, backgroundColor));

                setActionFragment(ActionFragment.newInstance(actions));
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putInt(KEY_BACKSTACK_COUNT, mLastBackStackCount);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mLayoutResId == R.layout.lb_dialog_fragment) {
            getDialogFragment().performEntryTransition();
        }
    }

    @Override
    public void onBackStackChanged() {
        int count = getFragmentManager().getBackStackEntryCount();
        if (count > 0 && count < mLastBackStackCount && DialogActivity.BACKSTACK_NAME_DIALOG.equals(
                getFragmentManager().getBackStackEntryAt(count - 1).getName())) {
            getFragmentManager().popBackStack();
        }
        mLastBackStackCount = count;
    }

    @Override
    public void onActionClicked(Action action) {
        Intent intent = action.getIntent();
        if (intent != null) {
            startActivity(intent);
            finish();
        }
    }

    /**
     * Disables the entry animation that normally happens onStart().
     */
    protected void disableEntryAnimation() {
        getDialogFragment().disableEntryAnimation();
    }

    /**
     * This method sets the layout property of this class. <br/>
     * Activities extending {@link DialogActivity} should call this method
     * before calling {@link #onCreate(Bundle)} if they want to have a
     * custom view.
     *
     * @param layoutResId resource if of the activity layout
     * @param contentAreaId id of the content area
     * @param actionAreaId id of the action area
     */
    protected void setLayoutProperties(int layoutResId, int contentAreaId, int actionAreaId) {
        mLayoutResId = layoutResId;
        getDialogFragment().setLayoutProperties(contentAreaId, actionAreaId);
    }

    /**
     * Animates a view.
     *
     * @param v              view to animate
     * @param initAlpha      initial alpha
     * @param initTransX     initial translation in the X
     * @param delay          delay in ms
     * @param duration       duration in ms
     * @param interpolator   interpolator to be used, can be null
     * @param isIcon         if {@code true}, this is the main icon being moved
     */
    protected void prepareAndAnimateView(final View v, float initAlpha, float initTransX, int delay,
            int duration, Interpolator interpolator, final boolean isIcon) {
        getDialogFragment().prepareAndAnimateView(
                v, initAlpha, initTransX, delay, duration, interpolator, isIcon);
    }

    /**
     * Called when intro animation is finished.
     * <p>
     * If a subclass is going to alter the view, should wait until this is called.
     */
    protected void onIntroAnimationFinished() {
        getDialogFragment().onIntroAnimationFinished();
    }

    protected boolean isIntroAnimationInProgress() {
        return getDialogFragment().isIntroAnimationInProgress();
    }

    protected ColorDrawable getBackgroundDrawable() {
        return getDialogFragment().getBackgroundDrawable();
    }

    protected void setBackgroundDrawable(ColorDrawable drawable) {
        getDialogFragment().setBackgroundDrawable(drawable);
    }

    /**
     * Sets the content fragment into the view.
     */
    protected void setContentFragment(Fragment fragment) {
        getDialogFragment().setContentFragment(fragment);
    }

    /**
     * Sets the action fragment into the view.
     * <p>
     * If an action fragment currently exists, this will be added to the back stack.
     */
    protected void setActionFragment(Fragment fragment) {
        getDialogFragment().setActionFragment(fragment);
    }

    /**
     * Sets the action fragment into the view.
     * <p>
     * If addToBackStack is true, and action fragment currently exists,
     * this will be added to the back stack.
     */
    protected void setActionFragment(Fragment fragment, boolean addToBackStack) {
        getDialogFragment().setActionFragment(fragment, addToBackStack);
    }

    protected Fragment getActionFragment() {
        return getDialogFragment().getActionFragment();
    }

    protected Fragment getContentFragment() {
        return getDialogFragment().getContentFragment();
    }

    /**
     * Set the content and action fragments in the same transaction.
     * <p>
     * If an action fragment currently exists, this will be added to the back stack.
     */
    protected void setContentAndActionFragments(Fragment contentFragment, Fragment actionFragment) {
        getDialogFragment().setContentAndActionFragments(contentFragment, actionFragment);
    }

    /**
     * Set the content and action fragments in the same transaction.
     * <p>
     * If addToBackStack and an action fragment currently exists,
     * this will be added to the back stack.
     */
    protected void setContentAndActionFragments(Fragment contentFragment, Fragment actionFragment,
            boolean addToBackStack) {
        getDialogFragment().setContentAndActionFragments(
                contentFragment, actionFragment, addToBackStack);
    }

    protected void setDialogFragment(DialogFragment fragment) {
        setDialogFragment(fragment, true);
    }

    protected void setDialogFragment(DialogFragment fragment, boolean addToBackStack) {
        mDialogFragment = fragment;
        fragment.setActivity(this);
        FragmentManager fm = getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        boolean hasDialog = fm.findFragmentByTag(DialogActivity.TAG_DIALOG) != null;
        if (hasDialog) {
            if (addToBackStack) {
                ft.addToBackStack(DialogActivity.BACKSTACK_NAME_DIALOG);
            }
        }
        ft.replace(R.id.dialog_fragment, fragment, DialogActivity.TAG_DIALOG);
        ft.commit();
    }

    protected DialogFragment getDialogFragment() {
        final DialogFragment fragment =
                (DialogFragment) getFragmentManager().findFragmentByTag(TAG_DIALOG);
        if (fragment != null) {
            mDialogFragment = fragment;
        }

        return mDialogFragment;
    }
}
