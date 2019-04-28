/*
 * Copyright (C) 2012 Google Inc.
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

package com.android.mail.photo;

import android.content.Context;
import android.content.Intent;

import com.android.ex.photo.Intents;
import com.android.ex.photo.PhotoViewActivity;
import com.android.ex.photo.PhotoViewController;
import com.android.mail.R;
import com.android.mail.browse.ConversationMessage;
import com.android.mail.providers.UIProvider;

/**
 * Derives from {@link PhotoViewActivity} to allow customization.
 * Delegates all work to {@link MailPhotoViewController}.
 */
public class MailPhotoViewActivity extends PhotoViewActivity implements
        MailPhotoViewController.ActivityInterface {

    static final String EXTRA_ACCOUNT = MailPhotoViewActivity.class.getName() + "-acct";
    static final String EXTRA_ACCOUNT_TYPE = MailPhotoViewActivity.class.getName() + "-accttype";
    static final String EXTRA_MESSAGE = MailPhotoViewActivity.class.getName() + "-msg";
    static final String EXTRA_HIDE_EXTRA_OPTION_ONE =
            MailPhotoViewActivity.class.getName() + "-hide-extra-option-one";

    /**
     * Start a new MailPhotoViewActivity to view the given images.
     *
     * @param context The context.
     * @param account The email address of the account.
     * @param accountType The type of the account.
     * @param msg The text of the message for this photo.
     * @param photoIndex The index of the photo within the album.
     */
    public static void startMailPhotoViewActivity(final Context context, final String account,
            final String accountType, final ConversationMessage msg, final int photoIndex) {
        final Intents.PhotoViewIntentBuilder builder =
                Intents.newPhotoViewIntentBuilder(context,
                        context.getString(R.string.photo_view_activity));
        builder
                .setPhotosUri(msg.attachmentListUri.toString())
                .setProjection(UIProvider.ATTACHMENT_PROJECTION)
                .setPhotoIndex(photoIndex);

        context.startActivity(wrapIntent(builder.build(), account, accountType, msg));
    }

    /**
     * Start a new MailPhotoViewActivity to view the given images.
     *
     * @param initialPhotoUri The uri of the photo to show first.
     */
    public static void startMailPhotoViewActivity(final Context context, final String account,
            final String accountType, final ConversationMessage msg, final String initialPhotoUri) {
        context.startActivity(
                buildMailPhotoViewActivityIntent(context, account, accountType, msg,
                        initialPhotoUri));
    }

    public static Intent buildMailPhotoViewActivityIntent(
            final Context context, final String account, final String accountType,
            final ConversationMessage msg, final String initialPhotoUri) {
        final Intents.PhotoViewIntentBuilder builder = Intents.newPhotoViewIntentBuilder(
                context, context.getString(R.string.photo_view_activity));

        builder.setPhotosUri(msg.attachmentListUri.toString())
                .setProjection(UIProvider.ATTACHMENT_PROJECTION)
                .setInitialPhotoUri(initialPhotoUri);

        return wrapIntent(builder.build(), account, accountType, msg);
    }

    private static Intent wrapIntent(
            final Intent intent, final String account, final String accountType,
            final ConversationMessage msg) {
        intent.putExtra(EXTRA_MESSAGE, msg);
        intent.putExtra(EXTRA_ACCOUNT, account);
        intent.putExtra(EXTRA_ACCOUNT_TYPE, accountType);
        intent.putExtra(EXTRA_HIDE_EXTRA_OPTION_ONE, msg.getConversation() == null);
        return intent;
    }

    @Override
    public PhotoViewController createController() {
        return new MailPhotoViewController(this);
    }
}
