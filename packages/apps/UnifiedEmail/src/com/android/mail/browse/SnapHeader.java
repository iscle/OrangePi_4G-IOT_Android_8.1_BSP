/**
 * Copyright (c) 2013, Google Inc.
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

package com.android.mail.browse;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.android.emailcommon.mail.Address;
import com.android.mail.ContactInfoSource;
import com.android.mail.browse.MessageHeaderView.MessageHeaderViewCallbacks;
import com.android.mail.utils.VeiledAddressMatcher;

import java.util.Map;

/**
 * Abstract view class that must be overridden for any view that wishes to be a snap
 * header in the {@link com.android.mail.browse.ConversationContainer}.
 */
public abstract class SnapHeader extends LinearLayout {

    public SnapHeader(Context context) {
        this(context, null);
    }

    public SnapHeader(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public SnapHeader(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public abstract void initialize(ConversationAccountController accountController,
            Map<String, Address> addressCache, MessageHeaderViewCallbacks callbacks,
            ContactInfoSource contactInfoSource, VeiledAddressMatcher veiledAddressMatcher);


    public abstract void setSnappy();
    public abstract boolean isBoundTo(ConversationOverlayItem item);
    public abstract void unbind();
    public abstract void refresh();
}
