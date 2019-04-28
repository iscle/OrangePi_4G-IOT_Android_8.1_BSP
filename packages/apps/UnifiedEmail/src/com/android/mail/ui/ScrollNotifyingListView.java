/*
 * Copyright (C) 2014 Google Inc.
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

package com.android.mail.ui;

import android.content.Context;
import android.database.Observable;
import android.util.AttributeSet;
import android.widget.ListView;

import com.android.mail.browse.ScrollNotifier;

/**
 * Ordinary list view with extra boilerplate to notify on scrollbar-related events (unrelated to
 * {@link android.widget.AbsListView.OnScrollListener}!)
 */
public class ScrollNotifyingListView extends ListView implements ScrollNotifier {

    private final ScrollObservable mObservable = new ScrollObservable();

    public ScrollNotifyingListView(Context c) {
        this(c, null);
    }

    public ScrollNotifyingListView(Context c, AttributeSet attrs) {
        super(c, attrs);
    }

    @Override
    public void addScrollListener(ScrollListener l) {
        mObservable.registerObserver(l);
    }

    @Override
    public void removeScrollListener(ScrollListener l) {
        mObservable.unregisterObserver(l);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        mObservable.onScrollChanged(l, t, oldl, oldt);
    }

    @Override
    public int computeVerticalScrollRange() {
        return super.computeVerticalScrollRange();
    }

    @Override
    public int computeVerticalScrollOffset() {
        return super.computeVerticalScrollOffset();
    }

    @Override
    public int computeVerticalScrollExtent() {
        return super.computeVerticalScrollExtent();
    }

    @Override
    public int computeHorizontalScrollRange() {
        return super.computeHorizontalScrollRange();
    }

    @Override
    public int computeHorizontalScrollOffset() {
        return super.computeHorizontalScrollOffset();
    }

    @Override
    public int computeHorizontalScrollExtent() {
        return super.computeHorizontalScrollExtent();
    }

    private static class ScrollObservable extends Observable<ScrollListener> {

        @SuppressWarnings("unused")
        public void onScrollChanged(int l, int t, int oldl, int oldt) {
            for (ScrollListener sl : mObservers) {
                sl.onNotifierScroll(t);
            }
        }

    }

}
