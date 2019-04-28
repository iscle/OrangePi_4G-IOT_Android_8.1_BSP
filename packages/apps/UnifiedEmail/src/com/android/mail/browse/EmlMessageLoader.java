/*
 * Copyright (C) 2013 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.mail.browse;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.android.emailcommon.TempDirectory;
import com.android.emailcommon.internet.MimeMessage;
import com.android.emailcommon.mail.MessagingException;
import com.android.mail.ui.MailAsyncTaskLoader;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loader that builds a ConversationMessage from an EML file Uri.
 */
public class EmlMessageLoader extends MailAsyncTaskLoader<ConversationMessage> {
    private static final String LOG_TAG = LogTag.getLogTag();

    private Uri mEmlFileUri;

    public EmlMessageLoader(Context context, Uri emlFileUri) {
        super(context);
        mEmlFileUri = emlFileUri;
    }

    @Override
    public ConversationMessage loadInBackground() {
        final Context context = getContext();
        TempDirectory.setTempDirectory(context);
        final ContentResolver resolver = context.getContentResolver();
        final InputStream stream;
        try {
            stream = resolver.openInputStream(mEmlFileUri);
        } catch (FileNotFoundException e) {
            LogUtils.e(LOG_TAG, e, "Could not find eml file at uri: %s", mEmlFileUri);
            return null;
        }

        final MimeMessage mimeMessage;
        ConversationMessage convMessage;
        try {
            mimeMessage = new MimeMessage(stream);
            convMessage = new ConversationMessage(context, mimeMessage, mEmlFileUri);
        } catch (IOException e) {
            LogUtils.e(LOG_TAG, e, "Could not read eml file");
            return null;
        } catch (MessagingException e) {
            LogUtils.e(LOG_TAG, e, "Error in parsing eml file");
            return null;
        } finally {
            try {
                stream.close();
            } catch (IOException e) {
                convMessage = null;
            }

            // delete temp files created during parsing
            final File[] cacheFiles = TempDirectory.getTempDirectory().listFiles();
            for (final File file : cacheFiles) {
                if (file.getName().startsWith("body")) {
                    final boolean deleted = file.delete();
                    if (!deleted) {
                        LogUtils.d(LOG_TAG, "Failed to delete temp file" + file.getName());
                    }
                }
            }
        }

        return convMessage;
    }

    /**
     * Helper function to take care of releasing resources associated
     * with an actively loaded data set.
     */
    @Override
    protected void onDiscardResult(ConversationMessage message) {
        // if this eml message had attachments, start a service to clean up the cache files
        if (message.attachmentListUri != null) {
            final Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setClass(getContext(), EmlTempFileDeletionService.class);
            intent.setData(message.attachmentListUri);

            getContext().startService(intent);
        }
    }
}
