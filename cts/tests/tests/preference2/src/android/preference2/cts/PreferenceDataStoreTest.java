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

package android.preference2.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalMatchers.or;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyFloat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Matchers.nullable;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceDataStore;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PreferenceDataStoreTest {

    private PreferenceFragmentActivity mActivity;
    private PreferenceWrapper mPreference;
    private PreferenceDataStore mDataStore;
    private PreferenceScreen mScreen;
    private PreferenceManager mManager;
    private SharedPreferences mSharedPref;

    private static final String KEY = "TestPrefKey";
    private static final String TEST_STR = "Test";
    private static final String TEST_DEFAULT_STR = "TestDefault";
    private static final String TEST_WRONG_STR = "TestFromSharedPref";

    @Rule
    public ActivityTestRule<PreferenceFragmentActivity> mActivityRule =
            new ActivityTestRule<>(PreferenceFragmentActivity.class);


    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mPreference = new PreferenceWrapper(mActivity);
        mPreference.setKey(KEY);

        // Assign the Preference to the PreferenceFragment.
        mScreen = mActivity.prefFragment.getPreferenceManager().createPreferenceScreen(mActivity);
        mManager = mScreen.getPreferenceManager();
        mSharedPref = mManager.getSharedPreferences();

        mDataStore = mock(PreferenceDataStore.class);

        // Make sure that the key is not present in SharedPreferences to ensure test correctness.
        mManager.getSharedPreferences().edit().remove(KEY).commit();
    }

    @Test
    public void testThatDataStoreIsNullByDefault() {
        Preference preference = new Preference(mActivity);
        mScreen.addPreference(preference);

        assertNull(preference.getPreferenceDataStore());
        assertNotNull(preference.getSharedPreferences());

        assertNull(mManager.getPreferenceDataStore());
        assertNotNull(mManager.getSharedPreferences());
    }

    @Test
    public void testSetGetOnPreference() {
        Preference preference = new Preference(mActivity);
        preference.setPreferenceDataStore(mDataStore);

        assertEquals(mDataStore, preference.getPreferenceDataStore());
        assertNull(preference.getSharedPreferences());
    }

    @Test
    public void testSetGetOnPreferenceManager() {
        mManager.setPreferenceDataStore(mDataStore);

        assertEquals(mDataStore, mManager.getPreferenceDataStore());
        assertNull(mManager.getSharedPreferences());
    }

    @Test
    public void testSetOnPreferenceManagerGetOnPreference() {
        Preference preference = new Preference(mActivity);
        mScreen.addPreference(preference);
        mManager.setPreferenceDataStore(mDataStore);

        assertEquals(mDataStore, preference.getPreferenceDataStore());
        assertNull(preference.getSharedPreferences());
    }

    @Test
    public void testDataStoresHierarchy() {
        mPreference.setPreferenceDataStore(mDataStore);
        PreferenceDataStore secondaryDataStore = mock(PreferenceDataStore.class);
        mScreen.addPreference(mPreference);
        mManager.setPreferenceDataStore(secondaryDataStore);
        mPreference.putString(TEST_STR);

        // Check that the Preference returns the correct data store.
        assertEquals(mDataStore, mPreference.getPreferenceDataStore());

        // Check that the secondary data store assigned to the manager was NOT used.
        verifyZeroInteractions(secondaryDataStore);

        // Check that the primary data store assigned directly to the preference was used.
        verify(mDataStore, atLeastOnce()).getString(eq(KEY), any());
    }

    @Test
    public void testPutStringWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        putStringTestCommon();
    }

    @Test
    public void testPutStringWithDataStoreOnMgr() {
        mManager.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        putStringTestCommon();
    }

    private void putStringTestCommon() {
        mPreference.putString(TEST_STR);

        verify(mDataStore, atLeast(0)).getString(eq(KEY), nullable(String.class));
        verify(mDataStore, atLeastOnce()).putString(eq(KEY), anyString());
        verifyNoMoreInteractions(mDataStore);

        // Test that the value was NOT propagated to SharedPreferences.
        assertNull(mSharedPref.getString(KEY, null));
    }

    @Test
    public void testGetStringWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        mPreference.getString(TEST_STR);
        verify(mDataStore, atLeastOnce()).getString(eq(KEY), eq(TEST_STR));
    }

    @Test
    public void testGetStringWithDataStoreOnMgr() {
        mManager.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        mPreference.getString(TEST_STR);
        verify(mDataStore, atLeastOnce()).getString(eq(KEY), eq(TEST_STR));
    }

    /**
     * This test makes sure that when a default value is set to a preference that has a data store
     * assigned that the default value is correctly propagated to
     * {@link Preference#onSetInitialValue(boolean, Object)} instead of passing a value from
     * {@link android.content.SharedPreferences}. We have this test only for String because the
     * implementation is not dependent on value type so this coverage should be fine.
     */
    @Test
    public void testDefaultStringValue() {
        mPreference.setPreferenceDataStore(mDataStore);
        mPreference.setDefaultValue(TEST_DEFAULT_STR);
        mSharedPref.edit().putString(KEY, TEST_WRONG_STR).commit();
        mScreen.addPreference(mPreference);
        mSharedPref.edit().remove(KEY).commit();
        assertEquals(TEST_DEFAULT_STR, mPreference.defaultValue);
    }

    /**
     * Test that the initial value is taken from the data store (before the preference gets assigned
     * to the preference hierarchy).
     */
    @Test
    public void testInitialValueIsFromDataStoreOnPreference() {
        when(mDataStore.getBoolean(anyString(), anyBoolean())).thenReturn(true);

        CheckBoxPreference pref = new CheckBoxPreference(mActivityRule.getActivity());
        pref.setKey("CheckboxTestPref");
        pref.setPreferenceDataStore(mDataStore);

        mScreen.addPreference(pref);

        assertTrue(pref.isChecked());
    }

    /**
     * Test that the initial value is taken from the data store (before the preference gets assigned
     * to the preference hierarchy).
     */
    @Test
    public void testInitialValueIsFromDataStoreOnPreferenceManager() {
        when(mDataStore.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        mManager.setPreferenceDataStore(mDataStore);

        CheckBoxPreference pref = new CheckBoxPreference(mActivityRule.getActivity());
        pref.setKey("CheckboxTestPref");

        mScreen.addPreference(pref);

        assertTrue(pref.isChecked());
    }

    @Test
    public void testPutStringSetWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        putStringSetTestCommon();
    }

    @Test
    public void testPutStringSetWithDataStoreOnMgr() {
        mManager.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        putStringSetTestCommon();
    }

    private void putStringSetTestCommon() {
        Set<String> testSet = new HashSet<>();
        testSet.add(TEST_STR);
        mPreference.putStringSet(testSet);

        verify(mDataStore, atLeast(0)).getStringSet(eq(KEY), or(isNull(Set.class), any()));
        verify(mDataStore, atLeastOnce()).putStringSet(eq(KEY), or(isNull(Set.class), any()));
        verifyNoMoreInteractions(mDataStore);

        // Test that the value was NOT propagated to SharedPreferences.
        assertNull(mSharedPref.getStringSet(KEY, null));
    }

    @Test
    public void testGetStringSetWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        Set<String> testSet = new HashSet<>();
        mPreference.getStringSet(testSet);
        verify(mDataStore, atLeastOnce()).getStringSet(eq(KEY), eq(testSet));
    }

    @Test
    public void testGetStringSetWithDataStoreOnMgr() {
        mManager.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        Set<String> testSet = new HashSet<>();
        mPreference.getStringSet(testSet);
        verify(mDataStore, atLeastOnce()).getStringSet(eq(KEY), eq(testSet));
    }

    @Test
    public void testPutIntWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        putIntTestCommon();
    }

    @Test
    public void testPutIntWithDataStoreOnMgr() {
        mManager.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        putIntTestCommon();
    }

    private void putIntTestCommon() {
        mPreference.putInt(1);

        verify(mDataStore, atLeast(0)).getInt(eq(KEY), anyInt());
        verify(mDataStore, atLeastOnce()).putInt(eq(KEY), anyInt());
        verifyNoMoreInteractions(mDataStore);

        // Test that the value was NOT propagated to SharedPreferences.
        assertEquals(-1, mSharedPref.getInt(KEY, -1));
    }

    @Test
    public void testGetIntWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        mPreference.getInt(1);
        verify(mDataStore, atLeastOnce()).getInt(eq(KEY), eq(1));
    }

    @Test
    public void testGetIntWithDataStoreOnMgr() {
        mManager.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        mPreference.getInt(1);
        verify(mDataStore, atLeastOnce()).getInt(eq(KEY), eq(1));
    }

    @Test
    public void testPutLongWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        putLongTestCommon();
    }

    @Test
    public void testPutLongWithDataStoreOnMgr() {
        mManager.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        putLongTestCommon();
    }

    private void putLongTestCommon() {
        mPreference.putLong(1L);

        verify(mDataStore, atLeast(0)).getLong(eq(KEY), anyLong());
        verify(mDataStore, atLeastOnce()).putLong(eq(KEY), anyLong());
        verifyNoMoreInteractions(mDataStore);

        // Test that the value was NOT propagated to SharedPreferences.
        assertEquals(-1, mSharedPref.getLong(KEY, -1L));
    }

    @Test
    public void testGetLongWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        mPreference.getLong(1L);
        verify(mDataStore, atLeastOnce()).getLong(eq(KEY), eq(1L));
    }

    @Test
    public void testGetLongWithDataStoreOnMgr() {
        mManager.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        mPreference.getLong(1L);
        verify(mDataStore, atLeastOnce()).getLong(eq(KEY), eq(1L));
    }

    @Test
    public void testPutFloatWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        putFloatTestCommon();
    }

    @Test
    public void testPutFloatWithDataStoreOnMgr() {
        mManager.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        putFloatTestCommon();
    }

    private void putFloatTestCommon() {
        mPreference.putFloat(1f);

        verify(mDataStore, atLeast(0)).getFloat(eq(KEY), anyFloat());
        verify(mDataStore, atLeastOnce()).putFloat(eq(KEY), anyFloat());
        verifyNoMoreInteractions(mDataStore);

        // Test that the value was NOT propagated to SharedPreferences.
        assertEquals(-1, mSharedPref.getFloat(KEY, -1f), 0.1f /* epsilon */);
    }

    @Test
    public void testGetFloatWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        mPreference.getFloat(1f);
        verify(mDataStore, atLeastOnce()).getFloat(eq(KEY), eq(1f));
    }

    @Test
    public void testGetFloatWithDataStoreOnMgr() {
        mManager.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        mPreference.getFloat(1f);
        verify(mDataStore, atLeastOnce()).getFloat(eq(KEY), eq(1f));
    }

    @Test
    public void testPutBooleanWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        putBooleanTestCommon();
    }

    @Test
    public void testPutBooleanWithDataStoreOnMgr() {
        mManager.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        putBooleanTestCommon();
    }

    private void putBooleanTestCommon() {
        mPreference.putBoolean(true);

        verify(mDataStore, atLeast(0)).getBoolean(eq(KEY), anyBoolean());
        verify(mDataStore, atLeastOnce()).putBoolean(eq(KEY), anyBoolean());
        verifyNoMoreInteractions(mDataStore);

        // Test that the value was NOT propagated to SharedPreferences.
        assertEquals(false, mSharedPref.getBoolean(KEY, false));
    }

    @Test
    public void testGetBooleanWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        mPreference.getBoolean(true);
        verify(mDataStore, atLeastOnce()).getBoolean(eq(KEY), eq(true));
    }

    @Test
    public void testGetBooleanWithDataStoreOnMgr() {
        mManager.setPreferenceDataStore(mDataStore);
        mScreen.addPreference(mPreference);
        mPreference.getBoolean(true);
        verify(mDataStore, atLeastOnce()).getBoolean(eq(KEY), eq(true));
    }

    /**
     * When {@link PreferenceDataStore} is NOT assigned, the getter for SharedPreferences should not
     * return null.
     */
    @Test
    public void testSharedPrefNotNullIfNoDS() {
        mScreen.addPreference(mPreference);
        assertNotNull(mPreference.getSharedPreferences());
        assertNotNull(mPreference.getEditor());
    }

    /**
     * When {@link PreferenceDataStore} is NOT assigned, the getter for SharedPreferences must not
     * return null for PreferenceManager.
     */
    @Test
    public void testSharedPrefNotNullIfNoDSMgr() {
        assertNotNull(mManager.getSharedPreferences());
    }

    /**
     * When {@link PreferenceDataStore} is assigned, the getter for SharedPreferences has to return
     * null.
     */
    @Test
    public void testSharedPrefNullIfWithDS() {
        mScreen.addPreference(mPreference);
        mPreference.setPreferenceDataStore(mDataStore);
        assertNull(mPreference.getSharedPreferences());
        assertNull(mPreference.getEditor());
    }

    /**
     * When {@link PreferenceDataStore} is assigned, the getter for SharedPreferences has to return
     * null for PreferenceManager.
     */
    @Test
    public void testSharedPrefNullIfWithDSMgr() {
        mManager.setPreferenceDataStore(mDataStore);
        assertNull(mManager.getSharedPreferences());
    }

    /**
     * Wrapper to allow to easily call protected methods.
     */
    private static class PreferenceWrapper extends Preference {

        Object defaultValue;

        PreferenceWrapper(Context context) {
            super(context);
        }

        void putString(String value) {
            persistString(value);
        }

        String getString(String defaultValue) {
            return getPersistedString(defaultValue);
        }

        void putStringSet(Set<String> values) {
            persistStringSet(values);
        }

        Set<String> getStringSet(Set<String> defaultValues) {
            return getPersistedStringSet(defaultValues);
        }

        void putInt(int value) {
            persistInt(value);
        }

        int getInt(int defaultValue) {
            return getPersistedInt(defaultValue);
        }

        void putLong(long value) {
            persistLong(value);
        }

        long getLong(long defaultValue) {
            return getPersistedLong(defaultValue);
        }

        void putFloat(float value) {
            persistFloat(value);
        }

        float getFloat(float defaultValue) {
            return getPersistedFloat(defaultValue);
        }

        void putBoolean(boolean value) {
            persistBoolean(value);
        }

        boolean getBoolean(boolean defaultValue) {
            return getPersistedBoolean(defaultValue);
        }

        @Override
        protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
            this.defaultValue = defaultValue;
            super.onSetInitialValue(restorePersistedValue, defaultValue);
        }
    }

}
