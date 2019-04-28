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

package foo.bar.fill;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.PendingIntent;
import android.app.assist.AssistStructure;
import android.app.assist.AssistStructure.WindowNode;
import android.app.assist.AssistStructure.ViewNode;
import android.content.Intent;
import android.content.IntentSender;
import android.os.CancellationSignal;
import android.service.autofill.AutofillService;
import android.service.autofill.Dataset;
import android.service.autofill.FillCallback;
import android.service.autofill.FillRequest;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveCallback;
import android.service.autofill.SaveInfo;
import android.service.autofill.SaveRequest;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;

import java.util.function.Predicate;

import foo.bar.fill.R;

public class FillService extends AutofillService {
    static final boolean TEST_RESPONSE_AUTH = false;

    public static final String RESPONSE_ID = "RESPONSE_ID";

    static final String DATASET1_NAME = "Foo";
    static final String DATASET1_USERNAME = "Foo";
    static final String DATASET1_PASSWORD = "1";

    static final String DATASET2_NAME = "Bar";
    static final String DATASET2_USERNAME = "Bar";
    static final String DATASET2_PASSWORD = "12";

    static final String DATASET3_NAME = "Baz";
    static final String DATASET3_USERNAME = "Baz";
    static final String DATASET3_PASSWORD = "123";

    static final String DATASET4_NAME = "Bam";
    static final String DATASET4_USERNAME = "Bam";
    static final String DATASET4_PASSWORD = "1234";

    static final String DATASET5_NAME = "Bak";
    static final String DATASET5_USERNAME = "Bak";
    static final String DATASET5_PASSWORD = "12345";

    static final String EXTRA_RESPONSE_ID = "foo.bar.fill.extra.RESPONSE_ID";
    static final String EXTRA_DATASET_ID = "foo.bar.fill.extra.DATASET_ID";

    @Override
    public void onFillRequest(@NonNull FillRequest request,
            @NonNull CancellationSignal cancellationSignal,
            @NonNull FillCallback callback) {
        AssistStructure structure = request.getFillContexts().get(0).getStructure();

        ViewNode username = findUsername(structure);
        ViewNode password = findPassword(structure);

        if (username != null && password != null) {
            final FillResponse response;

            if (TEST_RESPONSE_AUTH) {
                Intent intent = new Intent(this, AuthActivity.class);
                intent.putExtra(EXTRA_RESPONSE_ID, RESPONSE_ID);
                IntentSender sender = PendingIntent.getActivity(this, 0, intent,
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT)
                        .getIntentSender();

                RemoteViews presentation = new RemoteViews(getPackageName(), R.layout.pathology);

//                presentation.setTextViewText(R.id.text1, "First");
//                Intent firstIntent = new Intent(this, FirstActivity.class);
//                presentation.setOnClickPendingIntent(R.id.text1, PendingIntent.getActivity(
//                        this, 0, firstIntent, PendingIntent.FLAG_CANCEL_CURRENT));

//                presentation.setTextViewText(R.id.text2, "Second");
//                Intent secondIntent = new Intent(this, SecondActivity.class);
//                presentation.setOnClickPendingIntent(R.id.text2, PendingIntent.getActivity(
//                        this, 0, secondIntent, PendingIntent.FLAG_CANCEL_CURRENT));

                response = new FillResponse.Builder()
                        .setAuthentication(new AutofillId[]{username.getAutofillId(),
                                password.getAutofillId()}, sender, presentation)
                        .build();
            } else {
                Intent intent = new Intent(this, AuthActivity.class);
                intent.putExtra(EXTRA_DATASET_ID, DATASET1_NAME);
                IntentSender sender = PendingIntent.getActivity(this, 0, intent,
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT)
                        .getIntentSender();

                RemoteViews presentation1 = new RemoteViews(getPackageName(), R.layout.list_item);
                presentation1.setTextViewText(R.id.text1, DATASET1_NAME);

                RemoteViews presentation2 = new RemoteViews(getPackageName(), R.layout.list_item);
                presentation2.setTextViewText(R.id.text1, DATASET2_NAME);

                RemoteViews presentation3 = new RemoteViews(getPackageName(), R.layout.list_item);
                presentation3.setTextViewText(R.id.text1, DATASET3_NAME);

                RemoteViews presentation4 = new RemoteViews(getPackageName(), R.layout.list_item);
                presentation4.setTextViewText(R.id.text1, DATASET4_NAME);

                RemoteViews presentation5 = new RemoteViews(getPackageName(), R.layout.list_item);
                presentation5.setTextViewText(R.id.text1, /*DATASET5_NAME*/ "Auth needed");

                response = new FillResponse.Builder()
                        .addDataset(new Dataset.Builder(presentation1)
                                .setValue(username.getAutofillId(),
                                        AutofillValue.forText(DATASET1_USERNAME))
                                .setValue(password.getAutofillId(),
                                        AutofillValue.forText(DATASET1_PASSWORD))
                                .build())
                        .addDataset(new Dataset.Builder(presentation2)
                                .setValue(username.getAutofillId(),
                                        AutofillValue.forText(DATASET2_USERNAME))
                                .setValue(password.getAutofillId(),
                                        AutofillValue.forText(DATASET2_PASSWORD))
//                                .setAuthentication(sender)
                                .build())
                        .addDataset(new Dataset.Builder(presentation3)
                                .setValue(username.getAutofillId(),
                                        AutofillValue.forText(DATASET3_USERNAME))
                                .setValue(password.getAutofillId(),
                                        AutofillValue.forText(DATASET3_PASSWORD))
//                                .setAuthentication(sender)
                                .build())
                        .addDataset(new Dataset.Builder(presentation4)
                                .setValue(username.getAutofillId(),
                                        AutofillValue.forText(DATASET4_USERNAME))
                                .setValue(password.getAutofillId(),
                                        AutofillValue.forText(DATASET4_PASSWORD))
//                                .setAuthentication(sender)
                                .build())
                        .addDataset(new Dataset.Builder(presentation5)
                                .setValue(username.getAutofillId(),
                                        AutofillValue.forText(DATASET5_USERNAME))
                                .setValue(password.getAutofillId(),
                                        AutofillValue.forText(DATASET5_PASSWORD))
                                .setAuthentication(sender)
                                .build())
                        .setSaveInfo(new SaveInfo.Builder(
                                SaveInfo.SAVE_DATA_TYPE_PASSWORD
                                        | SaveInfo.SAVE_DATA_TYPE_USERNAME,
                                new AutofillId[] {username.getAutofillId(),
                                        password.getAutofillId()})
                                .build())
                        .build();
            }

            callback.onSuccess(response);
        } else {
            callback.onFailure("Whoops");
        }
    }

    @Override
    public void onSaveRequest(@NonNull SaveRequest request, @NonNull SaveCallback callback) {
        AssistStructure structure = request.getFillContexts().get(0).getStructure();
        ViewNode username = findUsername(structure);
        ViewNode password = findPassword(structure);
    }

    static ViewNode findUsername(AssistStructure structure) {
        return findByPredicate(structure, (node) ->
            node.getAutofillType() == View.AUTOFILL_TYPE_TEXT
                    && "username".equals(node.getIdEntry())
        );
    }

    static ViewNode findPassword(AssistStructure structure) {
        return findByPredicate(structure, (node) ->
                node.getAutofillType() == View.AUTOFILL_TYPE_TEXT
                        && "password".equals(node.getIdEntry())
        );
    }

    private static ViewNode findByPredicate(AssistStructure structure,
            Predicate<ViewNode> predicate) {
        final int windowCount = structure.getWindowNodeCount();
        for (int i = 0; i < windowCount; i++) {
            WindowNode window = structure.getWindowNodeAt(i);
            ViewNode root = window.getRootViewNode();
            if (root == null) {
                return null;
            }
            ViewNode node = findByPredicate(root, predicate);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    private static ViewNode findByPredicate(ViewNode root, Predicate<ViewNode> predicate) {
        if (root == null) {
            return null;
        }
        if (predicate.test(root)) {
            return root;
        }
        final int childCount = root.getChildCount();
        for (int i = 0; i < childCount; i++) {
            ViewNode child = root.getChildAt(i);
            ViewNode node = findByPredicate(child, predicate);
            if (node != null) {
                return node;
            }
        }
        return null;
    }
}
