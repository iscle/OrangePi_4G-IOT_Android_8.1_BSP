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
import android.app.FragmentTransaction;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;

import com.android.tv.settings.R;

/**
* A DialogFragment has 2 fragments, a content fragment and a list fragment.
* <p>
* Subclasses should override to supply the content fragment and list items.
* <p>
* The DialogFragment will handle animating in and out.
* <p>
* This class will use a default layout, but a custom layout can be provided by
* calling {@link #setLayoutProperties}
*/
public class DialogFragment extends Fragment implements ActionAdapter.Listener, LiteFragment {

    private Activity mActivity;
    private final BaseDialogFragment mBase = new BaseDialogFragment(this);

    @Override
    public void onActionClicked(Action action) {
        mBase.onActionClicked(getRealActivity(), action);
    }

    protected void disableEntryAnimation() {
        mBase.disableEntryAnimation();
    }

    public void performEntryTransition() {
        if (mBase.mFirstOnStart) {
            mBase.mFirstOnStart = false;
            // Once the subclass has setup its view hierarchy, we can perform an entry
            // transition if specified by the intent.
            Fragment fragment = getContentFragment();
            if (fragment instanceof ContentFragment) {
                ContentFragment cf = (ContentFragment) fragment;
                mBase.performEntryTransition(getRealActivity(),
                        (ViewGroup) getRealActivity().findViewById(android.R.id.content),
                        cf.getIcon(), cf.getTitle(), cf.getDescription(), cf.getBreadCrumb());
            }
        }
    }

    /**
     * This method sets the layout property of this class. <br/>
     * Activities extending {@link DialogFragment} should call this method
     * before calling {@link #onCreate(Bundle)} if they want to have a
     * custom view.
     *
     * @param contentAreaId id of the content area
     * @param actionAreaId id of the action area
     */
    protected void setLayoutProperties(int contentAreaId, int actionAreaId) {
        mBase.setLayoutProperties(contentAreaId, actionAreaId);
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
        mBase.prepareAndAnimateView(
                v, initAlpha, initTransX, delay, duration, interpolator, isIcon);
    }

    /**
     * Called when intro animation is finished.
     * <p>
     * If a subclass is going to alter the view, should wait until this is called.
     */
    protected void onIntroAnimationFinished() {
        mBase.onIntroAnimationFinished();
    }

    protected boolean isIntroAnimationInProgress() {
        return mBase.isIntroAnimationInProgress();
    }

    protected ColorDrawable getBackgroundDrawable() {
        return mBase.getBackgroundDrawable();
    }

    protected void setBackgroundDrawable(ColorDrawable drawable) {
        mBase.setBackgroundDrawable(drawable);
    }

    /* ********************************************************************* */
    /* Fragment related code below, cannot be placed into BaseDialogFragment */
    /* ********************************************************************* */

    public void setActivity(Activity act) {
        mActivity = act;
    }

    /**
     * Capable of returning {@link Activity} prior to this Fragment being
     * attached to it's parent Activity.  Useful for getting the parent
     * Activity prior to {@link #onAttach(Activity)} being called.
     * @return parent {@link Activity}
     */
    private Activity getRealActivity() {
        return (mActivity != null ? mActivity : getActivity());
    }

    /**
     * Sets the content fragment into the view.
     */
    protected void setContentFragment(Fragment fragment) {
        FragmentTransaction ft = getContentFragmentTransaction(fragment);
        ft.commit();
    }

    /**
     * Sets the action fragment into the view.
     * <p>
     * If an action fragment currently exists, this will be added to the back stack.
     */
    protected void setActionFragment(Fragment fragment) {
        setActionFragment(fragment, true);
    }

    /**
     * Sets the action fragment into the view.
     * <p>
     * If addToBackStack is true, and action fragment currently exists,
     * this will be added to the back stack.
     */
    protected void setActionFragment(Fragment fragment, boolean addToBackStack) {
        FragmentTransaction ft = addActionFragmentToTransaction(fragment, null, addToBackStack,
                getRealActivity().getFragmentManager());
        ft.commit();
    }

    protected Fragment getActionFragment() {
        return getRealActivity().getFragmentManager()
                .findFragmentByTag(BaseDialogFragment.TAG_ACTION);
    }

    protected Fragment getContentFragment() {
        return getRealActivity().getFragmentManager()
                .findFragmentByTag(BaseDialogFragment.TAG_CONTENT);
    }

    /**
     * Set the content and action fragments in the same transaction.
     * <p>
     * If an action fragment currently exists, this will be added to the back stack.
     */
    protected void setContentAndActionFragments(Fragment contentFragment, Fragment actionFragment) {
        setContentAndActionFragments(contentFragment, actionFragment, true);
    }

    /**
     * Set the content and action fragments in the same transaction.
     * <p>
     * If addToBackStack and an action fragment currently exists,
     * this will be added to the back stack.
     */
    protected void setContentAndActionFragments(Fragment contentFragment, Fragment actionFragment,
            boolean addToBackStack) {
        FragmentTransaction ft = getContentFragmentTransaction(contentFragment);
        ft = addActionFragmentToTransaction(actionFragment, ft, addToBackStack,
                getRealActivity().getFragmentManager());
        ft.commit();
    }

    /**
     * Begins a fragment transaction to edit the content fragment.
     */
    private FragmentTransaction getContentFragmentTransaction(Fragment fragment) {
        FragmentManager fm = getRealActivity().getFragmentManager();
        boolean hasContent = fm.findFragmentByTag(BaseDialogFragment.TAG_CONTENT) != null;
        FragmentTransaction ft = fm.beginTransaction();

        if (hasContent) {
            addAnimations(ft);
        }
        ft.replace(mBase.mContentAreaId, fragment, BaseDialogFragment.TAG_CONTENT);
        return ft;
    }

    /**
     * Adds an action fragment replacement to an existing fragment transaction, or creates one if
     * necessary.
     * <p>
     * If an action fragment currently exists, this will be added to the back stack.
     */
    private FragmentTransaction addActionFragmentToTransaction(Fragment fragment,
            FragmentTransaction ft, boolean addToBackStack, FragmentManager fm) {
        if (ft == null) {
            ft = fm.beginTransaction();
        }
        boolean hasActions = fm.findFragmentByTag(BaseDialogFragment.TAG_ACTION) != null;
        if (hasActions) {
            addAnimations(ft);
            if (addToBackStack) {
                ft.addToBackStack(null);
            }
        }
        ft.replace(mBase.mActionAreaId, fragment, BaseDialogFragment.TAG_ACTION);

        if (fragment instanceof ActionFragment) {
            if (!((ActionFragment) fragment).hasListener()) {
                ((ActionFragment) fragment).setListener(this);
            }
        }

        return ft;
    }

    static void addAnimations(FragmentTransaction ft) {
        ft.setCustomAnimations(R.anim.fragment_slide_left_in,
                R.anim.fragment_slide_left_out, R.anim.fragment_slide_right_in,
                R.anim.fragment_slide_right_out);
    }
}

