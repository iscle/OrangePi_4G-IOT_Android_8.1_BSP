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

package android.autofillservice.cts;

import static org.testng.Assert.assertThrows;

import android.service.autofill.SaveInfo;
import android.support.test.runner.AndroidJUnit4;
import android.view.autofill.AutofillId;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SaveInfoTest {

    @Test
    public void testRequiredIdsBuilder_null() {
        assertThrows(IllegalArgumentException.class,
                () -> new SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_GENERIC, null));
    }

    @Test
    public void testRequiredIdsBuilder_empty() {
        assertThrows(IllegalArgumentException.class,
                () -> new SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_GENERIC, new AutofillId[] {}));
    }

    @Test
    public void testRequiredIdsBuilder_nullEntry() {
        assertThrows(IllegalArgumentException.class,
                () -> new SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_GENERIC,
                        new AutofillId[] { null }));
    }

    @Test
    public void testBuild_noOptionalIds() {
        final SaveInfo.Builder builder = new SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_GENERIC);
        assertThrows(IllegalStateException.class, ()-> builder.build());
    }

    @Test
    public void testSetOptionalIds_null() {
        final SaveInfo.Builder builder = new SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_GENERIC,
                new AutofillId[] { new AutofillId(42) });
        assertThrows(IllegalArgumentException.class, ()-> builder.setOptionalIds(null));
    }

    @Test
    public void testSetOptional_empty() {
        final SaveInfo.Builder builder = new SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_GENERIC,
                new AutofillId[] { new AutofillId(42) });
        assertThrows(IllegalArgumentException.class,
                () -> builder.setOptionalIds(new AutofillId[] {}));
    }

    @Test
    public void testSetOptional_nullEntry() {
        final SaveInfo.Builder builder = new SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_GENERIC,
                new AutofillId[] { new AutofillId(42) });
        assertThrows(IllegalArgumentException.class,
                () -> builder.setOptionalIds(new AutofillId[] { null }));
    }
}
