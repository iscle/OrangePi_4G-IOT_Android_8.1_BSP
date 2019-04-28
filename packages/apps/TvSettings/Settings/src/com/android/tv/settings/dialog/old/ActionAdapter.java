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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.tv.settings.R;
import com.android.tv.settings.widget.ScrollAdapter;
import com.android.tv.settings.widget.ScrollAdapterBase;
import com.android.tv.settings.widget.ScrollAdapterView;
import com.android.tv.settings.widget.ScrollAdapterView.OnScrollListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter class which creates actions.
 */
public class ActionAdapter extends BaseAdapter implements ScrollAdapter,
        OnScrollListener, View.OnKeyListener, View.OnClickListener {
    private static final String TAG = "ActionAdapter";

    private static final boolean DEBUG = false;

    private static final int SELECT_ANIM_DURATION = 100;
    private static final int SELECT_ANIM_DELAY = 0;
    private static final float SELECT_ANIM_SELECTED_ALPHA = 0.2f;
    private static final float SELECT_ANIM_UNSELECTED_ALPHA = 1.0f;
    private static final float CHECKMARK_ANIM_UNSELECTED_ALPHA = 0.0f;
    private static final float CHECKMARK_ANIM_SELECTED_ALPHA = 1.0f;

    private static Integer sDescriptionMaxHeight = null;

    // TODO: this constant is only in KLP: update when KLP has a more standard SDK.
    private static final int FX_KEYPRESS_INVALID = 9; // AudioManager.FX_KEYPRESS_INVALID;

    /**
     * Object listening for adapter events.
     */
    public interface Listener {

        /**
         * Called when the user clicks on an action.
         */
        public void onActionClicked(Action action);
    }

    public interface OnFocusListener {

        /**
         * Called when the user focuses on an action.
         */
        public void onActionFocused(Action action);
    }

    /**
     * Object listening for adapter action select/unselect events.
     */
    public interface OnKeyListener {

        /**
         * Called when user finish selecting an action.
         */
        public void onActionSelect(Action action);

        /**
         * Called when user finish unselecting an action.
         */
        public void onActionUnselect(Action action);
    }


    private final Context mContext;
    private final float mUnselectedAlpha;
    private final float mSelectedTitleAlpha;
    private final float mDisabledTitleAlpha;
    private final float mSelectedDescriptionAlpha;
    private final float mDisabledDescriptionAlpha;
    private final float mUnselectedDescriptionAlpha;
    private final float mSelectedChevronAlpha;
    private final float mDisabledChevronAlpha;
    private final List<Action> mActions;
    private Listener mListener;
    private OnFocusListener mOnFocusListener;
    private OnKeyListener mOnKeyListener;
    private boolean mKeyPressed;
    private ScrollAdapterView mScrollAdapterView;
    private final int mAnimationDuration;
    private View mSelectedView = null;

    public ActionAdapter(Context context) {
        super();
        mContext = context;
        final Resources res = context.getResources();

        mAnimationDuration = res.getInteger(R.integer.dialog_animation_duration);
        mUnselectedAlpha = getFloat(R.dimen.list_item_unselected_text_alpha);

        mSelectedTitleAlpha = getFloat(R.dimen.list_item_selected_title_text_alpha);
        mDisabledTitleAlpha = getFloat(R.dimen.list_item_disabled_title_text_alpha);

        mSelectedDescriptionAlpha = getFloat(R.dimen.list_item_selected_description_text_alpha);
        mUnselectedDescriptionAlpha = getFloat(R.dimen.list_item_unselected_description_text_alpha);
        mDisabledDescriptionAlpha = getFloat(R.dimen.list_item_disabled_description_text_alpha);

        mSelectedChevronAlpha = getFloat(R.dimen.list_item_selected_chevron_background_alpha);
        mDisabledChevronAlpha = getFloat(R.dimen.list_item_disabled_chevron_background_alpha);

        mActions = new ArrayList<>();
        mKeyPressed = false;
    }

    @Override
    public void viewRemoved(View view) {
        // Do nothing.
    }

    @Override
    public View getScrapView(ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(mContext);
        View view = inflater.inflate(R.layout.settings_list_item, parent, false);
        return view;
    }

    @Override
    public int getCount() {
        return mActions.size();
    }

    @Override
    public Object getItem(int position) {
        return mActions.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = getScrapView(parent);
        }
        Action action = mActions.get(position);
        TextView title = (TextView) convertView.findViewById(R.id.action_title);
        TextView description = (TextView) convertView.findViewById(R.id.action_description);
        description.setText(action.getDescription());
        description.setVisibility(
                TextUtils.isEmpty(action.getDescription()) ? View.GONE : View.VISIBLE);
        title.setText(action.getTitle());
        ImageView checkmarkView = (ImageView) convertView.findViewById(R.id.action_checkmark);
        checkmarkView.setVisibility(action.isChecked() ? View.VISIBLE : View.INVISIBLE);

        ImageView indicatorView = (ImageView) convertView.findViewById(R.id.action_icon);
        setIndicator(indicatorView, action);

        ImageView chevronView = (ImageView) convertView.findViewById(R.id.action_next_chevron);
        chevronView.setVisibility(action.hasNext() ? View.VISIBLE : View.GONE);

        View chevronBackgroundView = convertView.findViewById(R.id.action_next_chevron_background);
        chevronBackgroundView.setVisibility(action.hasNext() ? View.VISIBLE : View.INVISIBLE);

        final Resources res = convertView.getContext().getResources();
        if (action.hasMultilineDescription()) {
            title.setMaxLines(res.getInteger(R.integer.action_title_max_lines));
            description.setMaxHeight(
                    getDescriptionMaxHeight(convertView.getContext(), title, description));
        } else {
            title.setMaxLines(res.getInteger(R.integer.action_title_min_lines));
            description.setMaxLines(
                    res.getInteger(R.integer.action_description_min_lines));
        }

        convertView.setTag(R.id.action_title, action);
        convertView.setOnKeyListener(this);
        convertView.setOnClickListener(this);
        changeFocus(convertView, false /* hasFocus */, false /* shouldAnimate */);

        return convertView;
    }

    @Override
    public ScrollAdapterBase getExpandAdapter() {
        return null;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void setOnFocusListener(OnFocusListener onFocusListener) {
        mOnFocusListener = onFocusListener;
    }

    public void setOnKeyListener(OnKeyListener onKeyListener) {
        mOnKeyListener = onKeyListener;
    }

    public void addAction(Action action) {
        mActions.add(action);
        notifyDataSetChanged();
    }

    /**
     * Used for serialization only.
     */
    public ArrayList<Action> getActions() {
        return new ArrayList<>(mActions);
    }

    public void setActions(ArrayList<Action> actions) {
        changeFocus(mSelectedView, false /* hasFocus */, false /* shouldAnimate */);
        mActions.clear();
        mActions.addAll(actions);
        notifyDataSetChanged();
    }

    // We want to highlight a view if we've stopped scrolling on it (mainPosition = 0).
    // If mainPosition is not 0, we don't want to do anything with view, but we do want to ensure
    // we dim the last highlighted view so that while a user is scrolling, nothing is highlighted.
    @Override
    public void onScrolled(View view, int position, float mainPosition, float secondPosition) {
        boolean hasFocus = (mainPosition == 0.0);
        if (hasFocus) {
            if (view != null) {
                changeFocus(view, true /* hasFocus */, true /* shouldAniamte */);
                mSelectedView = view;
            }
        } else if (mSelectedView != null) {
            changeFocus(mSelectedView, false /* hasFocus */, true /* shouldAniamte */);
            mSelectedView = null;
        }
    }

    private void changeFocus(View v, boolean hasFocus, boolean shouldAnimate) {
        if (v == null) {
            return;
        }
        Action action = (Action) v.getTag(R.id.action_title);

        float titleAlpha = action.isEnabled() && !action.infoOnly()
                ? (hasFocus ? mSelectedTitleAlpha : mUnselectedAlpha) : mDisabledTitleAlpha;
        float descriptionAlpha = (!hasFocus || action.infoOnly()) ? mUnselectedDescriptionAlpha
                : (action.isEnabled() ? mSelectedDescriptionAlpha : mDisabledDescriptionAlpha);
        float chevronAlpha = action.hasNext() && !action.infoOnly()
                ? (action.isEnabled() ? mSelectedChevronAlpha : mDisabledChevronAlpha) : 0;

        TextView title = (TextView) v.findViewById(R.id.action_title);
        setAlpha(title, shouldAnimate, titleAlpha);

        TextView description = (TextView) v.findViewById(R.id.action_description);
        setAlpha(description, shouldAnimate, descriptionAlpha);

        ImageView checkmark = (ImageView) v.findViewById(R.id.action_checkmark);
        setAlpha(checkmark, shouldAnimate, titleAlpha);

        ImageView icon = (ImageView) v.findViewById(R.id.action_icon);
        setAlpha(icon, shouldAnimate, titleAlpha);

        View chevronBackground = v.findViewById(R.id.action_next_chevron_background);
        setAlpha(chevronBackground, shouldAnimate, chevronAlpha);

        if (mOnFocusListener != null && hasFocus) {
            // We still call onActionFocused so that listeners can clear state if they want.
            mOnFocusListener.onActionFocused((Action) v.getTag(R.id.action_title));
        }
    }

    private void setIndicator(final ImageView indicatorView, Action action) {

        Drawable indicator = action.getIndicator(mContext);
        if (indicator != null) {
            indicatorView.setImageDrawable(indicator);
            indicatorView.setVisibility(View.VISIBLE);
        } else {
            indicatorView.setVisibility(View.GONE);
        }
    }

    private void setAlpha(View view, boolean shouldAnimate, float alpha) {
        if (shouldAnimate) {
            view.animate().alpha(alpha)
                    .setDuration(mAnimationDuration)
                    .setInterpolator(new DecelerateInterpolator(2F))
                    .start();
        } else {
            view.setAlpha(alpha);
        }
    }

    void setScrollAdapterView(ScrollAdapterView scrollAdapterView) {
        mScrollAdapterView = scrollAdapterView;
    }

    @Override
    public void onClick(View v) {
        if (v != null && v.getWindowToken() != null && mListener != null) {
            final Action action = (Action) v.getTag(R.id.action_title);
            mListener.onActionClicked(action);
        }
    }

    /**
     * Now only handles KEYCODE_ENTER and KEYCODE_NUMPAD_ENTER key event.
     */
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (v == null) {
            return false;
        }
        boolean handled = false;
        Action action = (Action) v.getTag(R.id.action_title);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_ENTER:
                AudioManager manager = (AudioManager) v.getContext().getSystemService(
                        Context.AUDIO_SERVICE);
                if (!action.isEnabled() || action.infoOnly()) {
                    if (v.isSoundEffectsEnabled() && event.getAction() == KeyEvent.ACTION_DOWN) {
                        manager.playSoundEffect(FX_KEYPRESS_INVALID);
                    }
                    return true;
                }

                switch (event.getAction()) {
                    case KeyEvent.ACTION_DOWN:
                        if (!mKeyPressed) {
                            mKeyPressed = true;

                            if (v.isSoundEffectsEnabled()) {
                                manager.playSoundEffect(AudioManager.FX_KEY_CLICK);
                            }

                            if (DEBUG) {
                                Log.d(TAG, "Enter Key down");
                            }

                            prepareAndAnimateView(v, SELECT_ANIM_UNSELECTED_ALPHA,
                                    SELECT_ANIM_SELECTED_ALPHA, SELECT_ANIM_DURATION,
                                    SELECT_ANIM_DELAY, null, mKeyPressed);
                            handled = true;
                        }
                        break;
                    case KeyEvent.ACTION_UP:
                        if (mKeyPressed) {
                            mKeyPressed = false;

                            if (DEBUG) {
                                Log.d(TAG, "Enter Key up");
                            }

                            prepareAndAnimateView(v, SELECT_ANIM_SELECTED_ALPHA,
                                    SELECT_ANIM_UNSELECTED_ALPHA, SELECT_ANIM_DURATION,
                                    SELECT_ANIM_DELAY, null, mKeyPressed);
                            handled = true;
                        }
                        break;
                    default:
                        break;
                }
                break;
            default:
                break;
        }
        return handled;
    }

    private void prepareAndAnimateView(final View v, float initAlpha, float destAlpha, int duration,
            int delay, Interpolator interpolator, final boolean pressed) {
        if (v != null && v.getWindowToken() != null) {
            final Action action = (Action) v.getTag(R.id.action_title);

            if (!pressed) {
                fadeCheckmarks(v, action, duration, delay, interpolator);
            }

            v.setAlpha(initAlpha);
            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            v.buildLayer();
            v.animate().alpha(destAlpha).setDuration(duration).setStartDelay(delay);
            if (interpolator != null) {
                v.animate().setInterpolator(interpolator);
            }
            v.animate().setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {

                    v.setLayerType(View.LAYER_TYPE_NONE, null);
                    if (pressed) {
                        if (mOnKeyListener != null) {
                            mOnKeyListener.onActionSelect(action);
                        }
                    } else {
                        if (mOnKeyListener != null) {
                            mOnKeyListener.onActionUnselect(action);
                        }
                        if (mListener != null) {
                            mListener.onActionClicked(action);
                        }
                    }
                }
            });
            v.animate().start();
        }
    }

    private void fadeCheckmarks(final View v, final Action action, int duration, int delay,
            Interpolator interpolator) {
        int actionCheckSetId = action.getCheckSetId();
        if (actionCheckSetId != Action.NO_CHECK_SET) {
            // Find any actions that are checked and are in the same group
            // as the selected action. Fade their checkmarks out.
            for (int i = 0, size = mActions.size(); i < size; i++) {
                Action a = mActions.get(i);
                if (a != action && a.getCheckSetId() == actionCheckSetId && a.isChecked()) {
                    a.setChecked(false);
                    if (mScrollAdapterView != null) {
                        View viewToAnimateOut = mScrollAdapterView.getItemView(i);
                        if (viewToAnimateOut != null) {
                            final View checkView = viewToAnimateOut.findViewById(
                                    R.id.action_checkmark);
                            checkView.animate().alpha(CHECKMARK_ANIM_UNSELECTED_ALPHA)
                                    .setDuration(duration).setStartDelay(delay);
                            if (interpolator != null) {
                                checkView.animate().setInterpolator(interpolator);
                            }
                            checkView.animate().setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    checkView.setVisibility(View.INVISIBLE);
                                }
                            });
                        }
                    }
                }
            }

            // If we we'ren't already checked, fade our checkmark in.
            if (!action.isChecked()) {
                action.setChecked(true);
                if (mScrollAdapterView != null) {
                    final View checkView = v.findViewById(R.id.action_checkmark);
                    checkView.setVisibility(View.VISIBLE);
                    checkView.setAlpha(CHECKMARK_ANIM_UNSELECTED_ALPHA);
                    checkView.animate().alpha(CHECKMARK_ANIM_SELECTED_ALPHA).setDuration(duration)
                            .setStartDelay(delay);
                    if (interpolator != null) {
                        checkView.animate().setInterpolator(interpolator);
                    }
                    checkView.animate().setListener(null);
                }
            }
        }
    }

    /**
     * @return the max height in pixels the description can be such that the
     * action nicely takes up the entire screen.
     */
    private static Integer getDescriptionMaxHeight(Context context, TextView title,
            TextView description) {
        if (sDescriptionMaxHeight == null) {
            final Resources res = context.getResources();
            final float verticalPadding = res.getDimension(R.dimen.list_item_vertical_padding);
            final int titleMaxLines = res.getInteger(R.integer.action_title_max_lines);
            final int displayHeight = ((WindowManager) context.getSystemService(
                    Context.WINDOW_SERVICE)).getDefaultDisplay().getHeight();

            // The 2 multiplier on the title height calculation is a conservative
            // estimate for font padding which can not be calculated at this stage
            // since the view hasn't been rendered yet.
            sDescriptionMaxHeight = (int) (displayHeight -
                    2 * verticalPadding - 2 * titleMaxLines * title.getLineHeight());
        }
        return sDescriptionMaxHeight;
    }

    private float getFloat(int resourceId) {
        TypedValue buffer = new TypedValue();
        mContext.getResources().getValue(resourceId, buffer, true);
        return buffer.getFloat();
    }
}
