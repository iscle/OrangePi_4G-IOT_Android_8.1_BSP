/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.view.inputmethod.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils;
import android.util.Printer;
import android.view.inputmethod.InputMethod;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InputMethodInfoTest {
    private Context mContext;

    private InputMethodInfo mInputMethodInfo;
    private String mPackageName;
    private String mClassName;
    private CharSequence mLabel;
    private String mSettingsActivity;

    private int mSubtypeNameResId;
    private int mSubtypeIconResId;
    private String mSubtypeLocale;
    private String mSubtypeMode;
    private String mSubtypeExtraValue_key;
    private String mSubtypeExtraValue_value;
    private String mSubtypeExtraValue;
    private boolean mSubtypeIsAuxiliary;
    private boolean mSubtypeOverridesImplicitlyEnabledSubtype;
    private int mSubtypeId;
    private InputMethodSubtype mInputMethodSubtype;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
        mPackageName = mContext.getPackageName();
        mClassName = InputMethodSettingsActivityStub.class.getName();
        mLabel = "test";
        mSettingsActivity = "android.view.inputmethod.cts.InputMethodSettingsActivityStub";
        mInputMethodInfo = new InputMethodInfo(mPackageName, mClassName, mLabel, mSettingsActivity);

        mSubtypeNameResId = 0;
        mSubtypeIconResId = 0;
        mSubtypeLocale = "en_US";
        mSubtypeMode = "keyboard";
        mSubtypeExtraValue_key = "key1";
        mSubtypeExtraValue_value = "value1";
        mSubtypeExtraValue = "tag," + mSubtypeExtraValue_key + "=" + mSubtypeExtraValue_value;
        mSubtypeIsAuxiliary = false;
        mSubtypeOverridesImplicitlyEnabledSubtype = false;
        mSubtypeId = 99;
        mInputMethodSubtype = new InputMethodSubtype(mSubtypeNameResId, mSubtypeIconResId,
                mSubtypeLocale, mSubtypeMode, mSubtypeExtraValue, mSubtypeIsAuxiliary,
                mSubtypeOverridesImplicitlyEnabledSubtype, mSubtypeId);
    }

    @Test
    public void testInputMethodInfoProperties() throws XmlPullParserException, IOException {
        assertEquals(0, mInputMethodInfo.describeContents());
        assertNotNull(mInputMethodInfo.toString());

        assertInfo(mInputMethodInfo);
        assertEquals(0, mInputMethodInfo.getIsDefaultResourceId());

        Intent intent = new Intent(InputMethod.SERVICE_INTERFACE);
        intent.setClass(mContext, InputMethodSettingsActivityStub.class);
        PackageManager pm = mContext.getPackageManager();
        List<ResolveInfo> ris = pm.queryIntentServices(intent, PackageManager.GET_META_DATA);
        for (int i = 0; i < ris.size(); i++) {
            ResolveInfo resolveInfo = ris.get(i);
            mInputMethodInfo = new InputMethodInfo(mContext, resolveInfo);
            assertService(resolveInfo.serviceInfo, mInputMethodInfo.getServiceInfo());
            assertInfo(mInputMethodInfo);
        }
    }

    @Test
    public void testInputMethodSubtypeProperties() {
        // TODO: Test InputMethodSubtype.getDisplayName()
        assertEquals(mSubtypeNameResId, mInputMethodSubtype.getNameResId());
        assertEquals(mSubtypeIconResId, mInputMethodSubtype.getIconResId());
        assertEquals(mSubtypeLocale, mInputMethodSubtype.getLocale());
        assertEquals(mSubtypeMode, mInputMethodSubtype.getMode());
        assertEquals(mSubtypeExtraValue, mInputMethodSubtype.getExtraValue());
        assertTrue(mInputMethodSubtype.containsExtraValueKey(mSubtypeExtraValue_key));
        assertEquals(mSubtypeExtraValue_value,
                mInputMethodSubtype.getExtraValueOf(mSubtypeExtraValue_key));
        assertEquals(mSubtypeIsAuxiliary, mInputMethodSubtype.isAuxiliary());
        assertEquals(mSubtypeOverridesImplicitlyEnabledSubtype,
                mInputMethodSubtype.overridesImplicitlyEnabledSubtype());
        assertEquals(mSubtypeId, mInputMethodSubtype.hashCode());
    }

    private void assertService(ServiceInfo expected, ServiceInfo actual) {
        assertEquals(expected.getIconResource(), actual.getIconResource());
        assertEquals(expected.labelRes, actual.labelRes);
        assertEquals(expected.nonLocalizedLabel, actual.nonLocalizedLabel);
        assertEquals(expected.icon, actual.icon);
        assertEquals(expected.permission, actual.permission);
    }

    private void assertInfo(InputMethodInfo info) {
        assertEquals(mPackageName, info.getPackageName());
        assertEquals(mSettingsActivity, info.getSettingsActivity());
        ComponentName component = info.getComponent();
        assertEquals(mClassName, component.getClassName());
        String expectedId = component.flattenToShortString();
        assertEquals(expectedId, info.getId());
        assertEquals(mClassName, info.getServiceName());
    }

    @Test
    public void testDump() {
        Printer printer = mock(Printer.class);
        String prefix = "test";
        mInputMethodInfo.dump(printer, prefix);
        verify(printer, atLeastOnce()).println(anyString());
    }

    @Test
    public void testLoadIcon() {
        PackageManager pm = mContext.getPackageManager();
        assertNotNull(mInputMethodInfo.loadIcon(pm));
    }

    @Test
    public void testEquals() {
        InputMethodInfo inputMethodInfo = new InputMethodInfo(mPackageName, mClassName, mLabel,
                mSettingsActivity);
        assertTrue(inputMethodInfo.equals(mInputMethodInfo));
    }

    @Test
    public void testLoadLabel() {
        CharSequence expected = "test";
        PackageManager pm = mContext.getPackageManager();
        assertEquals(expected.toString(), mInputMethodInfo.loadLabel(pm).toString());
    }

    @Test
    public void testInputMethodInfoWriteToParcel() {
        final Parcel p = Parcel.obtain();
        mInputMethodInfo.writeToParcel(p, 0);
        p.setDataPosition(0);
        final InputMethodInfo imi = InputMethodInfo.CREATOR.createFromParcel(p);
        p.recycle();

        assertEquals(mInputMethodInfo.getPackageName(), imi.getPackageName());
        assertEquals(mInputMethodInfo.getServiceName(), imi.getServiceName());
        assertEquals(mInputMethodInfo.getSettingsActivity(), imi.getSettingsActivity());
        assertEquals(mInputMethodInfo.getId(), imi.getId());
        assertEquals(mInputMethodInfo.getIsDefaultResourceId(), imi.getIsDefaultResourceId());
        assertService(mInputMethodInfo.getServiceInfo(), imi.getServiceInfo());
    }

    @Test
    public void testInputMethodSubtypeWriteToParcel() {
        final Parcel p = Parcel.obtain();
        mInputMethodSubtype.writeToParcel(p, 0);
        p.setDataPosition(0);
        final InputMethodSubtype subtype = InputMethodSubtype.CREATOR.createFromParcel(p);
        p.recycle();

        assertEquals(mInputMethodSubtype.containsExtraValueKey(mSubtypeExtraValue_key),
                subtype.containsExtraValueKey(mSubtypeExtraValue_key));
        assertEquals(mInputMethodSubtype.getExtraValue(), subtype.getExtraValue());
        assertEquals(mInputMethodSubtype.getExtraValueOf(mSubtypeExtraValue_key),
                subtype.getExtraValueOf(mSubtypeExtraValue_key));
        assertEquals(mInputMethodSubtype.getIconResId(), subtype.getIconResId());
        assertEquals(mInputMethodSubtype.getLocale(), subtype.getLocale());
        assertEquals(mInputMethodSubtype.getMode(), subtype.getMode());
        assertEquals(mInputMethodSubtype.getNameResId(), subtype.getNameResId());
        assertEquals(mInputMethodSubtype.hashCode(), subtype.hashCode());
        assertEquals(mInputMethodSubtype.isAuxiliary(), subtype.isAuxiliary());
        assertEquals(mInputMethodSubtype.overridesImplicitlyEnabledSubtype(),
                subtype.overridesImplicitlyEnabledSubtype());
    }

    @Test
    public void testInputMethodSubtypesOfSystemImes() {
        if (!mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_INPUT_METHODS)) {
            return;
        }

        final InputMethodManager imm = mContext.getSystemService(InputMethodManager.class);
        final List<InputMethodInfo> imis = imm.getInputMethodList();
        final ArrayList<String> localeList = new ArrayList<>(Arrays.asList(
                Resources.getSystem().getAssets().getLocales()));
        boolean foundEnabledSystemImeSubtypeWithValidLanguage = false;
        for (InputMethodInfo imi : imis) {
            if ((imi.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                continue;
            }
            final int subtypeCount = imi.getSubtypeCount();
            // System IME must have one subtype at least.
            assertTrue(subtypeCount > 0);
            if (foundEnabledSystemImeSubtypeWithValidLanguage) {
                continue;
            }
            final List<InputMethodSubtype> enabledSubtypes =
                    imm.getEnabledInputMethodSubtypeList(imi, true);
            SUBTYPE_LOOP:
            for (InputMethodSubtype subtype : enabledSubtypes) {
                final String subtypeLocale = subtype.getLocale();
                if (subtypeLocale.length() < 2) {
                    continue;
                }
                // TODO: Detect language more strictly.
                final String subtypeLanguage = subtypeLocale.substring(0, 2);
                for (final String locale : localeList) {
                    if (locale.startsWith(subtypeLanguage)) {
                        foundEnabledSystemImeSubtypeWithValidLanguage = true;
                        break SUBTYPE_LOOP;
                    }
                }
            }
        }
        assertTrue(foundEnabledSystemImeSubtypeWithValidLanguage);
    }

    @Test
    public void testAtLeastOneEncryptionAwareInputMethodIsAvailable() {
        if (!mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_INPUT_METHODS)) {
            return;
        }

        if (!TextUtils.equals("native", getFbeMode())) {
            // Skip the test unless the device is in native FBE mode.
            return;
        }

        final InputMethodManager imm = mContext.getSystemService(InputMethodManager.class);
        final List<InputMethodInfo> imis = imm.getInputMethodList();
        boolean hasEncryptionAwareInputMethod = false;
        for (final InputMethodInfo imi : imis) {
            final ServiceInfo serviceInfo = imi.getServiceInfo();
            if (serviceInfo == null) {
                continue;
            }
            if ((serviceInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) !=
                    ApplicationInfo.FLAG_SYSTEM) {
                continue;
            }
            if (serviceInfo.encryptionAware) {
                hasEncryptionAwareInputMethod = true;
                break;
            }
        }
        assertTrue(hasEncryptionAwareInputMethod);
    }

    private String getFbeMode() {
        try (ParcelFileDescriptor.AutoCloseInputStream in =
                     new ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry
                             .getInstrumentation()
                             .getUiAutomation()
                             .executeShellCommand("sm get-fbe-mode"))) {
            try (BufferedReader br =
                         new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                // Assume that the output of "sm get-fbe-mode" is always one-line.
                final String line = br.readLine();
                return line != null ? line.trim() : "";
            }
        } catch (IOException e) {
            return "";
        }
    }
}
