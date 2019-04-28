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
package com.android.documentsui.inspector.actions;

import static android.provider.DocumentsContract.Document.FLAG_SUPPORTS_SETTINGS;

import android.content.Context;
import android.content.pm.PackageManager;

import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.roots.ProvidersAccess;

/**
 * Model for showing information about a document's provider.
 */
public final class ShowInProviderAction extends Action {

    private ProvidersAccess mProviders;

    public ShowInProviderAction(Context context, PackageManager pm, DocumentInfo doc,
            ProvidersAccess providers) {
        super(context, pm, doc);
        assert providers != null;
        mProviders = providers;
    }

    /**
     * @return the header of this action. In English it would be "This file belongs to"
     */
    @Override
    public String getHeader() {
        return mContext.getString(R.string.handler_app_belongs_to);
    }

    @Override
    public int getButtonIcon() {
        return R.drawable.ic_action_open;
    }

    /**
     * Checks if this documents supports opening in the provider.
     */
    @Override
    public boolean canPerformAction() {
        if ((mDoc.flags & FLAG_SUPPORTS_SETTINGS) != 0) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String getPackageName() {
        return mProviders.getPackageName(mDoc.derivedUri.getAuthority());
    }
}