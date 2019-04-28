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

/**
 * Holds an {@link InlineAttachmentViewIntentBuilderCreator} that is used to create
 * {@link InlineAttachmentViewIntentBuilder}s for the conversation views. <p/>
 *
 * Unfortunately, this pattern requires three layers. The holder (the top layer) is created at
 * application start and should have its creator set in the {@link android.app.Application}
 * so that each app has a creator that provides app-specific functionality.
 * Typically, that functionality is creating a different type of
 * {@link InlineAttachmentViewIntentBuilder} to do app-specific work. <p/>
 *
 * The middle layer is the {@link InlineAttachmentViewIntentBuilderCreator}. Only one of
 * these exist and is created at {@link android.app.Application} start time (usually
 * in a static block). During conversation view setup, this is used to create
 * an {@link InlineAttachmentViewIntentBuilder}. The creation needs to be done at this
 * time so that each conversation view can have its own builder that is passed
 * conversation-specific data at builder creation time. <p/>
 *
 * The bottom layer is the {@link InlineAttachmentViewIntentBuilder}. This builder
 * is passed into a {@link com.android.mail.browse.WebViewContextMenu} and used
 * when an image is long-pressed to determine whether "View image" should be a menu
 * option and what intent should fire when "View image" is selected.
 */
public class InlineAttachmentViewIntentBuilderCreatorHolder {
    private static InlineAttachmentViewIntentBuilderCreator sCreator;

    public static void setInlineAttachmentViewIntentCreator(
            InlineAttachmentViewIntentBuilderCreator creator) {
        sCreator = creator;
    }

    public static InlineAttachmentViewIntentBuilderCreator getInlineAttachmentViewIntentCreator() {
        return sCreator;
    }
}
