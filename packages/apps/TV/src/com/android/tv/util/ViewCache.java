package com.android.tv.util;

import android.content.Context;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

/**
 * A cache for the views.
 */
public class ViewCache {
    private final static SparseArray<ArrayList<View>> mViews = new SparseArray();

    private static ViewCache sViewCache;

    private ViewCache() { }

    /**
     * Returns an instance of the view cache.
     */
    public static ViewCache getInstance() {
        if (sViewCache == null) {
            sViewCache = new ViewCache();
        }
        return sViewCache;
    }

    /**
     * Returns if the view cache is empty.
     */
    public boolean isEmpty() {
        return mViews.size() == 0;
    }

    /**
     * Stores a view into this view cache.
     */
    public void putView(int resId, View view) {
        ArrayList<View> views = mViews.get(resId);
        if (views == null) {
            views = new ArrayList();
            mViews.put(resId, views);
        }
        views.add(view);
    }

    /**
     * Stores multi specific views into the view cache.
     */
    public void putView(Context context, int resId, ViewGroup fakeParent, int num) {
        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ArrayList<View> views = mViews.get(resId);
        if (views == null) {
            views = new ArrayList<>();
            mViews.put(resId, views);
        }
        for (int i = 0; i < num; i++) {
            View view = inflater.inflate(resId, fakeParent, false);
            views.add(view);
        }
    }

    /**
     * Returns the view for specific resource id.
     */
    public View getView(int resId) {
        ArrayList<View> views = mViews.get(resId);
        if (views != null && !views.isEmpty()) {
            View view = views.remove(views.size() - 1);
            if (views.isEmpty()) {
                mViews.remove(resId);
            }
            return view;
        } else {
            return null;
        }
    }

    /**
     * Returns the view if exists, or create a new view for the specific resource id.
     */
    public View getOrCreateView(LayoutInflater inflater, int resId, ViewGroup container) {
        View view = getView(resId);
        if (view == null) {
            view = inflater.inflate(resId, container, false);
        }
        return view;
    }

    /**
     * Clears the view cache.
     */
    public void clear() {
        mViews.clear();
    }
}
