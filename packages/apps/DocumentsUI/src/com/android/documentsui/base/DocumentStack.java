/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.documentsui.base;

import static com.android.documentsui.base.Shared.DEBUG;

import android.content.ContentResolver;
import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.DocumentsProvider;
import android.util.Log;

import com.android.documentsui.picker.LastAccessedProvider;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ProtocolException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

/**
 * Representation of a stack of {@link DocumentInfo}, usually the result of a
 * user-driven traversal.
 */
public class DocumentStack implements Durable, Parcelable {

    private static final String TAG = "DocumentStack";

    private static final int VERSION_INIT = 1;
    private static final int VERSION_ADD_ROOT = 2;

    private LinkedList<DocumentInfo> mList;
    private @Nullable RootInfo mRoot;

    private boolean mStackTouched;

    public DocumentStack() {
        mList = new LinkedList<>();
    }

    /**
     * Creates an instance, and pushes all docs to it in the same order as they're passed as
     * parameters, i.e. the last document will be at the top of the stack.
     */
    public DocumentStack(RootInfo root, DocumentInfo... docs) {
        mList = new LinkedList<>();
        for (int i = 0; i < docs.length; ++i) {
            mList.add(docs[i]);
        }

        mRoot = root;
    }

    /**
     * Same as {@link #DocumentStack(DocumentStack, DocumentInfo...)} except it takes a {@link List}
     * instead of an array.
     */
    public DocumentStack(RootInfo root, List<DocumentInfo> docs) {
        mList = new LinkedList<>(docs);
        mRoot = root;
    }

    /**
     * Makes a new copy, and pushes all docs to the new copy in the same order as they're
     * passed as parameters, i.e. the last document will be at the top of the stack.
     */
    public DocumentStack(DocumentStack src, DocumentInfo... docs) {
        mList = new LinkedList<>(src.mList);
        for (DocumentInfo doc : docs) {
            push(doc);
        }

        mStackTouched = false;
        mRoot = src.mRoot;
    }

    public boolean isInitialized() {
        return mRoot != null;
    }

    public @Nullable RootInfo getRoot() {
        return mRoot;
    }

    public boolean isEmpty() {
        return mList.isEmpty();
    }

    public int size() {
        return mList.size();
    }

    public DocumentInfo peek() {
        return mList.peekLast();
    }

    /**
     * Returns {@link DocumentInfo} at index counted from the bottom of this stack.
     */
    public DocumentInfo get(int index) {
        return mList.get(index);
    }

    public void push(DocumentInfo info) {
        boolean alreadyInStack = mList.contains(info);
        assert (!alreadyInStack);
        if (!alreadyInStack) {
            if (DEBUG) Log.d(TAG, "Adding doc to stack: " + info);
            mList.addLast(info);
            mStackTouched = true;
        }
    }

    public DocumentInfo pop() {
        if (DEBUG) Log.d(TAG, "Popping doc off stack.");
        final DocumentInfo result = mList.removeLast();
        mStackTouched = true;

        return result;
    }

    public void popToRootDocument() {
        if (DEBUG) Log.d(TAG, "Popping docs to root folder.");
        while (mList.size() > 1) {
            mList.removeLast();
        }
        mStackTouched = true;
    }

    public void changeRoot(RootInfo root) {
        if (DEBUG) Log.d(TAG, "Root changed to: " + root);
        reset();
        mRoot = root;
    }

    /** This will return true even when the initial location is set.
     * To get a read on if the user has changed something, use {@link #hasInitialLocationChanged()}.
     */
    public boolean hasLocationChanged() {
        return mStackTouched;
    }

    public String getTitle() {
        if (mList.size() == 1 && mRoot != null) {
            return mRoot.title;
        } else if (mList.size() > 1) {
            return peek().displayName;
        } else {
            return null;
        }
    }

    public boolean isRecents() {
        return mRoot != null && mRoot.isRecents();
    }

    /**
     * Resets this stack to the given stack. It takes the reference of {@link #mList} and
     * {@link #mRoot} instead of making a copy.
     */
    public void reset(DocumentStack stack) {
        if (DEBUG) Log.d(TAG, "Resetting the whole darn stack to: " + stack);

        mList = stack.mList;
        mRoot = stack.mRoot;
        mStackTouched = true;
    }

    @Override
    public String toString() {
        return "DocumentStack{"
                + "root=" + mRoot
                + ", docStack=" + mList
                + ", stackTouched=" + mStackTouched
                + "}";
    }

    @Override
    public void reset() {
        mList.clear();
        mRoot = null;
    }

    private void updateRoot(Collection<RootInfo> matchingRoots) throws FileNotFoundException {
        for (RootInfo root : matchingRoots) {
            // RootInfo's equals() only checks authority and rootId, so this will update RootInfo if
            // its flag has changed.
            if (root.equals(this.mRoot)) {
                this.mRoot = root;
                return;
            }
        }
        throw new FileNotFoundException("Failed to find matching mRoot for " + mRoot);
    }

    /**
     * Update a possibly stale restored stack against a live
     * {@link DocumentsProvider}.
     */
    private void updateDocuments(ContentResolver resolver) throws FileNotFoundException {
        for (DocumentInfo info : mList) {
            info.updateSelf(resolver);
        }
    }

    public static @Nullable DocumentStack fromLastAccessedCursor(
            Cursor cursor, Collection<RootInfo> matchingRoots, ContentResolver resolver)
            throws IOException {

        if (cursor.moveToFirst()) {
            DocumentStack stack = new DocumentStack();
            final byte[] rawStack = cursor.getBlob(
                    cursor.getColumnIndex(LastAccessedProvider.Columns.STACK));
            DurableUtils.readFromArray(rawStack, stack);

            stack.updateRoot(matchingRoots);
            stack.updateDocuments(resolver);

            return stack;
        }

        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof DocumentStack)) {
            return false;
        }

        DocumentStack other = (DocumentStack) o;
        return Objects.equals(mRoot, other.mRoot)
                && mList.equals(other.mList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRoot, mList);
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        final int version = in.readInt();
        switch (version) {
            case VERSION_INIT:
                throw new ProtocolException("Ignored upgrade");
            case VERSION_ADD_ROOT:
                if (in.readBoolean()) {
                    mRoot = new RootInfo();
                    mRoot.read(in);
                }
                final int size = in.readInt();
                for (int i = 0; i < size; i++) {
                    final DocumentInfo doc = new DocumentInfo();
                    doc.read(in);
                    mList.add(doc);
                }
                mStackTouched = in.readInt() != 0;
                break;
            default:
                throw new ProtocolException("Unknown version " + version);
        }
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeInt(VERSION_ADD_ROOT);
        if (mRoot != null) {
            out.writeBoolean(true);
            mRoot.write(out);
        } else {
            out.writeBoolean(false);
        }
        final int size = mList.size();
        out.writeInt(size);
        for (int i = 0; i < size; i++) {
            final DocumentInfo doc = mList.get(i);
            doc.write(out);
        }
        out.writeInt(mStackTouched ? 1 : 0);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        DurableUtils.writeToParcel(dest, this);
    }

    public static final Creator<DocumentStack> CREATOR = new Creator<DocumentStack>() {
        @Override
        public DocumentStack createFromParcel(Parcel in) {
            final DocumentStack stack = new DocumentStack();
            DurableUtils.readFromParcel(in, stack);
            return stack;
        }

        @Override
        public DocumentStack[] newArray(int size) {
            return new DocumentStack[size];
        }
    };
}
