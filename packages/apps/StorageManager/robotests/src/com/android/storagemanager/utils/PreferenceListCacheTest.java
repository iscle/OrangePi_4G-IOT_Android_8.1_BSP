/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.storagemanager.utils;

import android.content.Context;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import com.android.storagemanager.testing.TestingConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=TestingConstants.MANIFEST, sdk=TestingConstants.SDK_VERSION)
public class PreferenceListCacheTest {
    @Mock private PreferenceScreen mGroup;
    private PreferenceListCache mCache;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
    }

    @Test
    public void testEmptyPreferenceGroup() {
        setupMockPreferenceGroup(new Preference[0]);

        mCache = new PreferenceListCache(mGroup);
        mCache.removeCachedPrefs();

        verify(mGroup, never()).removePreference(any(Preference.class));
    }

    @Test
    public void testCacheAndRestoreAllPreferences() {
        Preference first = createPreference("first");
        Preference second = createPreference("second");
        Preference third = createPreference("third");
        Preference[] preferences = new Preference[] {first, second, third};
        setupMockPreferenceGroup(preferences);

        mCache = new PreferenceListCache(mGroup);
        assertEquals(first, mCache.getCachedPreference("first"));
        assertEquals(second, mCache.getCachedPreference("second"));
        assertEquals(third, mCache.getCachedPreference("third"));

        mCache.removeCachedPrefs();
        verify(mGroup, never()).removePreference(any(Preference.class));
    }

    @Test
    public void testRestoreSomePreferences() {
        Preference first = createPreference("first");
        Preference second = createPreference("second");
        Preference third = createPreference("third");
        Preference[] preferences = new Preference[] {first, second, third};
        setupMockPreferenceGroup(preferences);

        mCache = new PreferenceListCache(mGroup);
        assertEquals(first, mCache.getCachedPreference("first"));
        assertEquals(second, mCache.getCachedPreference("second"));

        mCache.removeCachedPrefs();

        // Because the third preference was left, it should have been removed by the last call.
        verify(mGroup).removePreference(eq(third));
    }

    @Test
    public void testRestoreZeroPreferences() {
        Preference first = createPreference("first");
        Preference second = createPreference("second");
        Preference third = createPreference("third");
        Preference[] preferences = new Preference[] {first, second, third};
        setupMockPreferenceGroup(preferences);

        mCache = new PreferenceListCache(mGroup);
        mCache.removeCachedPrefs();

        // Because none of the cached preferences were used, the call should remove all three
        // preferences from the group.
        verify(mGroup).removePreference(eq(first));
        verify(mGroup).removePreference(eq(second));
        verify(mGroup).removePreference(eq(third));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testKeyCollisionThrows() {
        Preference first = createPreference("first");
        Preference second = createPreference("first");
        Preference third = createPreference("first");
        Preference[] preferences = new Preference[] {first, second, third};
        setupMockPreferenceGroup(preferences);
        when(mGroup.getKey()).thenReturn("Group");

        mCache = new PreferenceListCache(mGroup);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testEmptyKeyThrows() {
        Preference first = createPreference("");
        Preference[] preferences = new Preference[] {first};
        setupMockPreferenceGroup(preferences);
        when(mGroup.getKey()).thenReturn("Group");

        mCache = new PreferenceListCache(mGroup);
    }

    private Preference createPreference(String key) {
        Preference newPreference = new Preference(mContext);
        newPreference.setKey(key);
        return newPreference;
    }

    private void setupMockPreferenceGroup(Preference[] preferences) {
        when(mGroup.getPreferenceCount()).thenReturn(preferences.length);
        when(mGroup.getPreference(anyInt())).thenAnswer(new Answer<Preference>() {
            @Override
            public Preference answer(InvocationOnMock invocation) throws Throwable {
                int index = (int) invocation.getArguments()[0];
                return preferences[index];
            }
        });
    }
}
