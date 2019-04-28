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

package android.car.cluster;

import android.annotation.Nullable;
import android.graphics.Rect;
import android.os.Bundle;

/**
 * Helper class that represents activity state in the cluster and can be serialized / deserialized
 * to/from bundle.
 * @hide
 */
public class ClusterActivityState {
    private static final String KEY_VISIBLE = "android.car:activityState.visible";
    private static final String KEY_UNOBSCURED_BOUNDS = "android.car:activityState.unobscured";
    private static final String KEY_EXTRAS = "android.car:activityState.extras";

    private boolean mVisible = true;
    private Rect mUnobscuredBounds;
    private Bundle mExtras;

    public boolean isVisible() {
        return mVisible;
    }

    @Nullable public Rect getUnobscuredBounds() {
        return mUnobscuredBounds;
    }

    public ClusterActivityState setVisible(boolean visible) {
        mVisible = visible;
        return this;
    }

    public ClusterActivityState setUnobscuredBounds(Rect unobscuredBounds) {
        mUnobscuredBounds = unobscuredBounds;
        return this;
    }

    public ClusterActivityState setExtras(Bundle bundle) {
        mExtras = bundle;
        return this;
    }

    /** Use factory methods instead. */
    private ClusterActivityState() {}

    public static ClusterActivityState create(boolean visible, Rect unobscuredBounds) {
        return new ClusterActivityState()
                .setVisible(visible)
                .setUnobscuredBounds(unobscuredBounds);
    }

    public static ClusterActivityState fromBundle(Bundle bundle) {
        return new ClusterActivityState()
                .setVisible(bundle.getBoolean(KEY_VISIBLE, true))
                .setUnobscuredBounds((Rect) bundle.getParcelable(KEY_UNOBSCURED_BOUNDS))
                .setExtras(bundle.getBundle(KEY_EXTRAS));
    }

    public Bundle toBundle() {
        Bundle b = new Bundle();
        b.putBoolean(KEY_VISIBLE, mVisible);
        b.putParcelable(KEY_UNOBSCURED_BOUNDS, mUnobscuredBounds);
        b.putBundle(KEY_EXTRAS, mExtras);
        return b;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " {"
                + "visible: " + mVisible + ", "
                + "unobscuredBounds: " + mUnobscuredBounds
                + " }";
    }
}
