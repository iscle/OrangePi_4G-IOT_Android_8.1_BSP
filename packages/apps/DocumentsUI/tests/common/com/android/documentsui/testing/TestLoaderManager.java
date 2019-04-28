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

package com.android.documentsui.testing;

import android.app.LoaderManager;
import android.content.AsyncTaskLoader;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.os.Bundle;
import android.util.SparseArray;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * A test double of {@link LoaderManager} that doesn't kick off loading when {@link Loader} is
 * created. If caller needs to kick off loading caller can obtain the loader initialized and
 * explicitly call {@link Loader#startLoading()}.
 */
public class TestLoaderManager extends LoaderManager {

    private final SparseArray<Loader> mLoaders = new SparseArray<>();
    private final SparseArray<OnLoadCompleteListener> mListeners = new SparseArray<>();

    @Override
    public <D> Loader<D> initLoader(int id, Bundle args,
            LoaderCallbacks<D> callback) {
        Loader<D> loader = mLoaders.get(id);
        OnLoadCompleteListener<D> listener = callback::onLoadFinished;
        if (loader == null) {
            loader = callback.onCreateLoader(id, args);
            mLoaders.put(id, loader);
        } else {
            loader.unregisterListener(mListeners.get(id));
        }

        loader.registerListener(id, listener);
        mListeners.put(id, listener);

        return loader;
    }

    @Override
    public <D> Loader<D> restartLoader(int id, Bundle args,
            LoaderCallbacks<D> callback) {
        if (mLoaders.get(id) != null) {
            destroyLoader(id);
        }

        return initLoader(id, args, callback);
    }

    @Override
    public void destroyLoader(int id) {
        Loader loader = getLoader(id);
        if (loader != null) {
            loader.abandon();
            mLoaders.remove(id);
            mListeners.remove(id);
        }
    }

    @Override
    public <D> Loader<D> getLoader(int id) {
        return mLoaders.get(id);
    }

    public <D> OnLoadCompleteListener<D> getListener(int id) {
        return mListeners.get(id);
    }

    public void runAsyncTaskLoader(int id) {
        AsyncTaskLoader loader = (AsyncTaskLoader) getLoader(id);
        loader.startLoading();
        loader.waitForLoader();
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {

    }
}
