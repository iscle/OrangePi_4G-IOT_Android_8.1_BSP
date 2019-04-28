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
 * limitations under the License.
 */

package com.android.documentsui.dirlist;

import android.annotation.Nullable;
import android.app.AuthenticationRequiredException;
import android.app.PendingIntent;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.Model.Update;
import com.android.documentsui.R;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.dirlist.DocumentsAdapter.Environment;

/**
 * Data object used by {@link InflateMessageDocumentHolder} and {@link HeaderMessageDocumentHolder}.
 */

abstract class Message {
    protected final Environment mEnv;
    // If the message has a button, this will be the default button call back.
    protected final Runnable mDefaultCallback;
    // If a message has a new callback when updated, this field should be updated.
    protected @Nullable Runnable mCallback;

    private @Nullable CharSequence mMessageString;
    private @Nullable CharSequence mButtonString;
    private @Nullable Drawable mIcon;
    private boolean mShouldShow = false;

    Message(Environment env, Runnable defaultCallback) {
        mEnv = env;
        mDefaultCallback = defaultCallback;
    }

    abstract void update(Update Event);

    protected void update(CharSequence messageString, CharSequence buttonString, Drawable icon) {
        if (messageString == null) {
            return;
        }
        mMessageString = messageString;
        mButtonString = buttonString;
        mIcon = icon;
        mShouldShow = true;
    }

    void reset() {
        mMessageString = null;
        mIcon = null;
        mShouldShow = false;
    }

    void runCallback() {
        if (mCallback != null) {
            mCallback.run();
        } else {
            mDefaultCallback.run();
        }
    }

    Drawable getIcon() {
        return mIcon;
    }

    boolean shouldShow() {
        return mShouldShow;
    }

    CharSequence getMessageString() {
        return mMessageString;
    }

    CharSequence getButtonString() {
        return mButtonString;
    }

    final static class HeaderMessage extends Message {

        private static final String TAG = "HeaderMessage";

        HeaderMessage(Environment env, Runnable callback) {
            super(env, callback);
        }

        @Override
        void update(Update event) {
            reset();
            // Error gets first dibs ... for now
            // TODO: These should be different Message objects getting updated instead of
            // overwriting.
            if (event.hasAuthenticationException()) {
                updateToAuthenticationExceptionHeader(event);
            } else if (mEnv.getModel().error != null) {
                update(mEnv.getModel().error, null,
                        mEnv.getContext().getDrawable(R.drawable.ic_dialog_alert));
            } else if (mEnv.getModel().info != null) {
                update(mEnv.getModel().info, null,
                        mEnv.getContext().getDrawable(R.drawable.ic_dialog_info));
            }
        }

        private void updateToAuthenticationExceptionHeader(Update event) {
            assert(mEnv.getFeatures().isRemoteActionsEnabled());

            RootInfo root = mEnv.getDisplayState().stack.getRoot();
            String appName = DocumentsApplication
                    .getProvidersCache(mEnv.getContext()).getApplicationName(root.authority);
            update(mEnv.getContext().getString(R.string.authentication_required, appName),
                    mEnv.getContext().getResources().getText(R.string.sign_in),
                    mEnv.getContext().getDrawable(R.drawable.ic_dialog_info));
            mCallback = () -> {
                AuthenticationRequiredException exception =
                        (AuthenticationRequiredException) event.getException();
                mEnv.getActionHandler().startAuthentication(exception.getUserAction());
            };
        }
    }

    final static class InflateMessage extends Message {

        InflateMessage(Environment env, Runnable callback) {
            super(env, callback);
        }

        @Override
        void update(Update event) {
            reset();
            if (event.hasException() && !event.hasAuthenticationException()) {
                updateToInflatedErrorMesage();
            } else if (event.hasAuthenticationException()) {
                updateToCantDisplayContentMessage();
            } else if (mEnv.getModel().getModelIds().length == 0) {
                updateToInflatedEmptyMessage();
            }
        }

        private void updateToInflatedErrorMesage() {
            update(mEnv.getContext().getResources().getText(R.string.query_error), null,
                    mEnv.getContext().getDrawable(R.drawable.hourglass));
        }

        private void updateToCantDisplayContentMessage() {
            update(mEnv.getContext().getResources().getText(R.string.cant_display_content), null,
                    mEnv.getContext().getDrawable(R.drawable.cabinet));
        }

        private void updateToInflatedEmptyMessage() {
            final CharSequence message;
            if (mEnv.isInSearchMode()) {
                message = String.format(
                        String.valueOf(
                                mEnv.getContext().getResources().getText(R.string.no_results)),
                        mEnv.getDisplayState().stack.getRoot().title);
            } else {
                message = mEnv.getContext().getResources().getText(R.string.empty);
            }
            update(message, null, mEnv.getContext().getDrawable(R.drawable.cabinet));
        }
    }
}
