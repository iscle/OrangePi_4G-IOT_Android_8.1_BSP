/**
 * Copyright (c) 2012, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.mail.providers;

import android.content.Intent;
import android.os.Parcel;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.mail.utils.Utils;

import org.json.JSONException;
import org.json.JSONObject;

@SmallTest
public class AccountTests extends AndroidTestCase {

    public void testSerializeDeserialize() {
        final Parcel dest = Parcel.obtain();
        dest.writeInt(0);
        dest.writeString("accountUri");
        dest.writeInt(12345);
        dest.writeString("foldersList");
        dest.writeString("searchUri");
        dest.writeString("fromAddresses");
        dest.writeString("expungeMessageUri");
        dest.writeString("undoUri");
        dest.writeString("settingIntentUri");
        dest.writeInt(0);

        final Account before = Account.builder().buildFrom(dest, null);
        final Intent intent = new Intent();
        intent.putExtra(Utils.EXTRA_ACCOUNT, before);

        final Account after = intent.getParcelableExtra(Utils.EXTRA_ACCOUNT);
        assertNotNull(after);

        assertEquals(before.getEmailAddress(), after.getEmailAddress());
        assertEquals(before.getDisplayName(), after.getDisplayName());
        assertEquals(before.accountFromAddresses, after.accountFromAddresses);
        assertEquals(before.capabilities, after.capabilities);
        assertEquals(before.providerVersion, after.providerVersion);
        assertEquals(before.uri, after.uri);
        assertEquals(before.folderListUri, after.folderListUri);
        assertEquals(before.searchUri, after.searchUri);
        assertEquals(before.expungeMessageUri, after.expungeMessageUri);
    }

    public void testDeserializeNullSenderNameUsingJSON() throws JSONException {
        final JSONObject json = new JSONObject();
        // fields required by deserialization
        json.put(UIProvider.AccountColumns.NAME, "name");
        json.put(UIProvider.AccountColumns.TYPE, "type");
        json.put(UIProvider.AccountColumns.PROVIDER_VERSION, 1);
        json.put(UIProvider.AccountColumns.CAPABILITIES, 2);

        // null sender name (same thing as not putting a sender name at all)
        json.put(UIProvider.AccountColumns.SENDER_NAME, null);

        final Account account = Account.newInstance(json.toString());
        assertNotNull(account);
        assertNull(account.getSenderName());
    }
}
