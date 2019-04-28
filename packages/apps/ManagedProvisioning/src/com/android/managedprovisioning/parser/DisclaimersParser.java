/*
 * Copyright 2016, The Android Open Source Project
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
package com.android.managedprovisioning.parser;

import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DISCLAIMERS;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DISCLAIMER_CONTENT;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DISCLAIMER_HEADER;
import static com.android.managedprovisioning.common.StoreUtils.DIR_PROVISIONING_PARAMS_FILE_CACHE;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.StoreUtils;
import com.android.managedprovisioning.model.DisclaimersParam;
import com.android.managedprovisioning.model.DisclaimersParam.Disclaimer;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for {@link EXTRA_PROVISIONING_DISCLAIMERS} into {@link DisclaimersParam}
 * It also saves the disclaimer content into files
 */
public class DisclaimersParser {
    private static final int MAX_LENGTH = 3;

    private final Context mContext;
    private final long mProvisioningId;
    private final File mDisclaimerDir;

    public DisclaimersParser(Context context, long provisioningId) {
        mContext = context;
        mProvisioningId = provisioningId;
        mDisclaimerDir =  new File(mContext.getFilesDir(), DIR_PROVISIONING_PARAMS_FILE_CACHE);
    }

    @Nullable
    public DisclaimersParam parse(Parcelable[] parcelables) throws ClassCastException {
        if (parcelables == null) {
            return null;
        }

        List<Disclaimer> disclaimers = new ArrayList<>(MAX_LENGTH);
        for (int i = 0; i < parcelables.length; i++) {
            // maximum 3 disclaimers are accepted in the EXTRA_PROVISIONING_DISCLAIMERS API
            if (disclaimers.size() >= MAX_LENGTH) {
                break;
            }
            final Bundle disclaimerBundle = (Bundle) parcelables[i];
            final String header = disclaimerBundle.getString(EXTRA_PROVISIONING_DISCLAIMER_HEADER);
            final Uri uri = disclaimerBundle.getParcelable(EXTRA_PROVISIONING_DISCLAIMER_CONTENT);
            if (TextUtils.isEmpty(header)) {
                ProvisionLogger.logw("Empty disclaimer header in " + i + " element");
                continue;
            }

            if (uri == null) {
                ProvisionLogger.logw("Null disclaimer content uri in " + i + " element");
                continue;
            }

            File disclaimerFile = saveDisclaimerContentIntoFile(uri, i);

            if (disclaimerFile == null) {
                ProvisionLogger.logw("Failed to copy disclaimer uri in " + i + " element");
                continue;
            }

            disclaimers.add(new Disclaimer(header, disclaimerFile.getPath()));
        }
        return disclaimers.isEmpty() ? null : new DisclaimersParam.Builder()
                .setDisclaimers(disclaimers.toArray(new Disclaimer[disclaimers.size()])).build();
    }

    /**
     * @return {@link File} if the uri content is saved into the file successfully. Otherwise,
     * return null.
     */
    private File saveDisclaimerContentIntoFile(Uri uri, int index) {
        if (!mDisclaimerDir.exists()) {
            mDisclaimerDir.mkdirs();
        }

        String filename = "disclaimer_content_" + mProvisioningId + "_" + index + ".txt";
        File outputFile = new File(mDisclaimerDir, filename);

        boolean success = StoreUtils.copyUriIntoFile(mContext.getContentResolver(), uri,
                outputFile);
        return success ? outputFile : null;
    }
}
