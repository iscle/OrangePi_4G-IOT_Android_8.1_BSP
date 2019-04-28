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

package com.android.documentsui.testing;

import android.content.ClipData;
import android.net.Uri;
import android.util.Pair;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.clipping.DocumentClipper;
import com.android.documentsui.selection.Selection;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.services.FileOperationService.OpType;
import com.android.documentsui.services.FileOperations.Callback;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class TestDocumentClipper implements DocumentClipper {

    public ClipData nextClip;
    public ClipData primaryClip;

    public final TestEventHandler<List<Uri>> clipForCut = new TestEventHandler<>();

    public final TestEventListener<Pair<DocumentStack, ClipData>> copyFromClip =
            new TestEventListener<>();
    public final TestEventListener<Integer> opType = new TestEventListener<>();

    @Override
    public boolean hasItemsToPaste() {
        return false;
    }

    @Override
    public ClipData getClipDataForDocuments(Function<String, Uri> uriBuilder, Selection selection,
            int opType) {
        return nextClip;
    }

    @Override
    public ClipData getClipDataForDocuments(List<Uri> uris,
            @FileOperationService.OpType int opType) {
        return nextClip;
    }

    @Override
    public ClipData getClipDataForDocuments(List<Uri> uris,
            @FileOperationService.OpType int opType, DocumentInfo parent) {
        return nextClip;
    }

    @Override
    public void clipDocumentsForCopy(Function<String, Uri> uriBuilder, Selection selection) {
    }

    @Override
    public void clipDocumentsForCut(Function<String, Uri> uriBuilder, Selection selection,
            DocumentInfo parent) {
        List<Uri> uris = new ArrayList<>(selection.size());
        for (String id : selection) {
            uris.add(uriBuilder.apply(id));
        }

        clipForCut.accept(uris);
    }

    @Override
    public void copyFromClipboard(DocumentInfo destination, DocumentStack docStack,
            Callback callback) {
        copyFromClip.accept(Pair.create(new DocumentStack(docStack, destination), primaryClip));
    }

    @Override
    public void copyFromClipboard(DocumentStack docStack, Callback callback) {
        copyFromClip.accept(Pair.create(docStack, primaryClip));
    }

    @Override
    public void copyFromClipData(DocumentInfo destination, DocumentStack docStack,
            ClipData clipData, Callback callback) {
        copyFromClip.accept(Pair.create(new DocumentStack(docStack, destination), clipData));
    }

    @Override
    public void copyFromClipData(DocumentStack dstStack, ClipData clipData,
            @OpType int opType, Callback callback) {
        copyFromClip.accept(Pair.create(dstStack, clipData));
        this.opType.accept(opType);
    }

    @Override
    public void copyFromClipData(DocumentStack docStack, ClipData clipData, Callback callback) {
        copyFromClip.accept(Pair.create(docStack, clipData));
    }
}
