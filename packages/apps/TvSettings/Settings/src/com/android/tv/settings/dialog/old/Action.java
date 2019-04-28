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

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.ArrayList;

/**
 * An action within an {@link ActionAdapter}.
 */
public class Action implements Parcelable {

    private static final String TAG = "Action";

    public static final int NO_DRAWABLE = 0;
    public static final int NO_CHECK_SET = 0;
    public static final int DEFAULT_CHECK_SET_ID = 1;

    private final String mKey;
    private final String mTitle;
    private final String mDescription;
    private final Intent mIntent;

    /**
     * If not {@code null}, the package name to use to retrieve {{@link #mDrawableResource}.
     */
    private final String mResourcePackageName;

    private final int mDrawableResource;
    private boolean mChecked;
    private final boolean mMultilineDescription;
    private final boolean mHasNext;
    private final boolean mInfoOnly;
    private final int mCheckSetId;
    private boolean mEnabled;

    /**
     * Builds a Action object.
     */
    public static class Builder {
        private String mKey;
        private String mTitle;
        private String mDescription;
        private Intent mIntent;
        private String mResourcePackageName;
        private int mDrawableResource = NO_DRAWABLE;
        private boolean mChecked;
        private boolean mMultilineDescription;
        private boolean mHasNext;
        private boolean mInfoOnly;
        private int mCheckSetId = NO_CHECK_SET;
        private boolean mEnabled = true;

        public Action build() {
            return new Action(
                    mKey,
                    mTitle,
                    mDescription,
                    mResourcePackageName,
                    mDrawableResource,
                    mChecked,
                    mMultilineDescription,
                    mHasNext,
                    mInfoOnly,
                    mIntent,
                    mCheckSetId,
                    mEnabled);
        }

        public Builder key(String key) {
            mKey = key;
            return this;
        }

        public Builder title(String title) {
            mTitle = title;
            return this;
        }

        public Builder description(String description) {
            mDescription = description;
            return this;
        }

        public Builder intent(Intent intent) {
            mIntent = intent;
            return this;
        }

        public Builder resourcePackageName(String resourcePackageName) {
            mResourcePackageName = resourcePackageName;
            return this;
        }

        public Builder drawableResource(int drawableResource) {
            mDrawableResource = drawableResource;
            return this;
        }

        public Builder checked(boolean checked) {
            mChecked = checked;
            return this;
        }

        public Builder multilineDescription(boolean multilineDescription) {
            mMultilineDescription = multilineDescription;
            return this;
        }

        public Builder hasNext(boolean hasNext) {
            mHasNext = hasNext;
            return this;
        }

        public Builder infoOnly(boolean infoOnly) {
            mInfoOnly = infoOnly;
            return this;
        }

        public Builder checkSetId(int checkSetId) {
            mCheckSetId = checkSetId;
            return this;
        }

        public Builder enabled(boolean enabled) {
            mEnabled = enabled;
            return this;
        }
    }

    protected Action(String key, String title, String description, String resourcePackageName,
            int drawableResource, boolean checked, boolean multilineDescription,
            boolean hasNext, boolean infoOnly, Intent intent, int checkSetId, boolean enabled) {
        mKey = key;
        mTitle = title;
        mDescription = description;
        mResourcePackageName = resourcePackageName;
        mDrawableResource = drawableResource;
        mChecked = checked;
        mMultilineDescription = multilineDescription;
        mHasNext = hasNext;
        mInfoOnly = infoOnly;
        mIntent = intent;
        mCheckSetId = checkSetId;
        mEnabled = enabled;
    }

    /**
     * Returns a list of {@link Action} with the specified keys and titles
     * matched up.
     * <p>
     * The key and title arrays must be of equal length.
     */
    public static ArrayList<Action> createActionsFromArrays(String[] keys, String[] titles) {
        return createActionsFromArrays(keys, titles, NO_CHECK_SET, null);
    }

    /**
     * Returns a list of {@link Action} with the specified keys and titles
     * matched up and a given check set ID so that they are related.
     * <p>
     * The key and title arrays must be of equal length.
     */
    public static ArrayList<Action> createActionsFromArrays(String[] keys, String[] titles,
            int checkSetId, String checkedItemKey) {
        int keysLength = keys.length;
        int titlesLength = titles.length;

        if (keysLength != titlesLength) {
            throw new IllegalArgumentException("Keys and titles dimensions must match");
        }

        ArrayList<Action> actions = new ArrayList<Action>();
        for (int i = 0; i < keysLength; i++) {
            Action.Builder builder = new Action.Builder();
            builder.key(keys[i]).title(titles[i]).checkSetId(checkSetId);
            if (checkedItemKey != null) {
                if (checkedItemKey.equals(keys[i])) {
                    builder.checked(true);
                } else {
                    builder.checked(false);
                }
            }
            Action action = builder.build();
            actions.add(action);
        }
        return actions;
    }

    public String getKey() {
        return mKey;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getDescription() {
        return mDescription;
    }

    public Intent getIntent() {
        return mIntent;
    }

    public boolean isChecked() {
        return mChecked;
    }

    public Uri getIconUri() {
        return null;
    }

    /**
     * Returns the check set id this action is a part of.  All actions in the same list with the
     * same check set id are considered linked.  When one of the actions within that set is selected
     * that action becomes checked while all the other actions become unchecked.
     * @return an integer representing the check set this action is a part of or {@NO_CHECK_SET} if
     * this action isn't a part of a check set.
     */
    public int getCheckSetId() {
        return mCheckSetId;
    }

    public boolean hasMultilineDescription() {
        return mMultilineDescription;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public void setChecked(boolean checked) {
        mChecked = checked;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    /**
     * @return true if the action will request further user input when selected
     *         (such as showing another dialog or launching a new activity).
     *         False, otherwise.
     */
    public boolean hasNext() {
        return mHasNext;
    }

    /**
     * @return true if the action will only display information and is thus unactionable.
     * If both this and {@link #hasNext()} are true, infoOnly takes precedence. (default is false)
     * e.g. Play balance, or cost of an app.
     */
    public boolean infoOnly() {
        return mInfoOnly;
    }

    /**
     * Returns an indicator to be drawn. If null is returned, no space for the
     * indicator will be made.
     *
     * @param context the context of the Activity this Action belongs to
     * @return an indicator to draw or null if no indicator space should exist.
     */
    public Drawable getIndicator(Context context) {
        if (mDrawableResource == NO_DRAWABLE) {
            return null;
        }
        if (mResourcePackageName == null) {
            return context.getDrawable(mDrawableResource);
        }
        // If we get to here, need to load the resources.
        Drawable icon = null;
        try {
            Context packageContext = context.createPackageContext(mResourcePackageName, 0);
            icon = packageContext.getDrawable(mDrawableResource);
        } catch (PackageManager.NameNotFoundException e) {
            if (Log.isLoggable(TAG, Log.WARN)) {
                Log.w(TAG, "No icon for this action.");
            }
        } catch (Resources.NotFoundException e) {
            if (Log.isLoggable(TAG, Log.WARN)) {
                Log.w(TAG, "No icon for this action.");
            }
        }
        return icon;
    }

    public static Parcelable.Creator<Action> CREATOR = new Parcelable.Creator<Action>() {

        @Override
        public Action createFromParcel(Parcel source) {

            return new Action.Builder()
                    .key(source.readString())
                    .title(source.readString())
                    .description(source.readString())
                    .intent((Intent) source.readParcelable(Intent.class.getClassLoader()))
                    .resourcePackageName(source.readString())
                    .drawableResource(source.readInt())
                    .checked(source.readInt() != 0)
                    .multilineDescription(source.readInt() != 0)
                    .checkSetId(source.readInt())
                    .build();
        }

        @Override
        public Action[] newArray(int size) {
            return new Action[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mKey);
        dest.writeString(mTitle);
        dest.writeString(mDescription);
        dest.writeParcelable(mIntent, flags);
        dest.writeString(mResourcePackageName);
        dest.writeInt(mDrawableResource);
        dest.writeInt(mChecked ? 1 : 0);
        dest.writeInt(mMultilineDescription ? 1 : 0);
        dest.writeInt(mCheckSetId);
    }
}
