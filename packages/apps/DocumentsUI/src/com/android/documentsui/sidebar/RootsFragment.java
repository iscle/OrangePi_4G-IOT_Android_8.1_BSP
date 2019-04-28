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

package com.android.documentsui.sidebar;

import static com.android.documentsui.base.Shared.DEBUG;
import static com.android.documentsui.base.Shared.VERBOSE;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnDragListener;
import android.view.View.OnGenericMotionListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.BaseActivity;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.Injector;
import com.android.documentsui.Injector.Injected;
import com.android.documentsui.ItemDragListener;
import com.android.documentsui.R;
import com.android.documentsui.base.BooleanConsumer;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Events;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.roots.ProvidersCache;
import com.android.documentsui.roots.RootsLoader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Display list of known storage backend roots.
 */
public class RootsFragment extends Fragment {

    private static final String TAG = "RootsFragment";
    private static final String EXTRA_INCLUDE_APPS = "includeApps";
    private static final int CONTEXT_MENU_ITEM_TIMEOUT = 500;

    private final OnItemClickListener mItemListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            final Item item = mAdapter.getItem(position);
            item.open();

            getBaseActivity().setRootsDrawerOpen(false);
        }
    };

    private final OnItemLongClickListener mItemLongClickListener = new OnItemLongClickListener() {
        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            final Item item = mAdapter.getItem(position);
            return item.showAppDetails();
        }
    };

    private ListView mList;
    private RootsAdapter mAdapter;
    private LoaderCallbacks<Collection<RootInfo>> mCallbacks;
    private @Nullable OnDragListener mDragListener;

    @Injected
    private Injector<?> mInjector;

    @Injected
    private ActionHandler mActionHandler;

    public static RootsFragment show(FragmentManager fm, Intent includeApps) {
        final Bundle args = new Bundle();
        args.putParcelable(EXTRA_INCLUDE_APPS, includeApps);

        final RootsFragment fragment = new RootsFragment();
        fragment.setArguments(args);

        final FragmentTransaction ft = fm.beginTransaction();
        ft.replace(R.id.container_roots, fragment);
        ft.commitAllowingStateLoss();

        return fragment;
    }

    public static RootsFragment get(FragmentManager fm) {
        return (RootsFragment) fm.findFragmentById(R.id.container_roots);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mInjector = getBaseActivity().getInjector();

        final View view = inflater.inflate(R.layout.fragment_roots, container, false);
        mList = (ListView) view.findViewById(R.id.roots_list);
        mList.setOnItemClickListener(mItemListener);
        // ListView does not have right-click specific listeners, so we will have a
        // GenericMotionListener to listen for it.
        // Currently, right click is viewed the same as long press, so we will have to quickly
        // register for context menu when we receive a right click event, and quickly unregister
        // it afterwards to prevent context menus popping up upon long presses.
        // All other motion events will then get passed to OnItemClickListener.
        mList.setOnGenericMotionListener(
                new OnGenericMotionListener() {
                    @Override
                    public boolean onGenericMotion(View v, MotionEvent event) {
                        if (Events.isMouseEvent(event)
                                && event.getButtonState() == MotionEvent.BUTTON_SECONDARY) {
                            int x = (int) event.getX();
                            int y = (int) event.getY();
                            return onRightClick(v, x, y, () -> {
                                mInjector.menuManager.showContextMenu(
                                        RootsFragment.this, v, x, y);
                            });
                        }
                        return false;
            }
        });
        mList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        return view;
    }

    private boolean onRightClick(View v, int x, int y, Runnable callback) {
        final int pos = mList.pointToPosition(x, y);
        final Item item = mAdapter.getItem(pos);

        // If a read-only root, no need to see if top level is writable (it's not)
        if (!(item instanceof RootItem) || !((RootItem) item).root.supportsCreate()) {
            return false;
        }

        final RootItem rootItem = (RootItem) item;
        getRootDocument(rootItem, (DocumentInfo doc) -> {
            rootItem.docInfo = doc;
            callback.run();
        });
        return true;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final BaseActivity activity = getBaseActivity();
        final ProvidersCache providers = DocumentsApplication.getProvidersCache(activity);
        final State state = activity.getDisplayState();

        mActionHandler = mInjector.actions;

        if (mInjector.config.dragAndDropEnabled()) {
            final DragHost host = new DragHost(
                    activity,
                    DocumentsApplication.getDragAndDropManager(activity),
                    this::getItem,
                    mActionHandler);
            mDragListener = new ItemDragListener<DragHost>(host) {
                @Override
                public boolean handleDropEventChecked(View v, DragEvent event) {
                    final Item item = getItem(v);

                    assert (item.isRoot());

                    return item.dropOn(event);
                }
            };
        }

        mCallbacks = new LoaderCallbacks<Collection<RootInfo>>() {
            @Override
            public Loader<Collection<RootInfo>> onCreateLoader(int id, Bundle args) {
                return new RootsLoader(activity, providers, state);
            }

            @Override
            public void onLoadFinished(
                    Loader<Collection<RootInfo>> loader, Collection<RootInfo> roots) {
                if (!isAdded()) {
                    return;
                }

                Intent handlerAppIntent = getArguments().getParcelable(EXTRA_INCLUDE_APPS);

                final Intent intent = activity.getIntent();
                final boolean excludeSelf =
                        intent.getBooleanExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, false);
                final String excludePackage = excludeSelf ? activity.getCallingPackage() : null;
                List<Item> sortedItems =
                        sortLoadResult(roots, excludePackage, handlerAppIntent);
                mAdapter = new RootsAdapter(activity, sortedItems, mDragListener);
                mList.setAdapter(mAdapter);

                mInjector.shortcutsUpdater.accept(roots);
                onCurrentRootChanged();
            }

            @Override
            public void onLoaderReset(Loader<Collection<RootInfo>> loader) {
                mAdapter = null;
                mList.setAdapter(null);
            }
        };
    }

    /**
     * @param excludePackage Exclude activities from this given package
     * @param handlerAppIntent When not null, apps capable of handling the original intent will
     *            be included in list of roots (in special section at bottom).
     */
    private List<Item> sortLoadResult(
            Collection<RootInfo> roots,
            @Nullable String excludePackage,
            @Nullable Intent handlerAppIntent) {
        final List<Item> result = new ArrayList<>();

        final List<RootItem> libraries = new ArrayList<>();
        final List<RootItem> others = new ArrayList<>();

        for (final RootInfo root : roots) {
            final RootItem item = new RootItem(root, mActionHandler);

            Activity activity = getActivity();
            if (root.isHome() && !Shared.shouldShowDocumentsRoot(activity)) {
                continue;
            } else if (root.isLibrary()) {
                libraries.add(item);
            } else {
                others.add(item);
            }
        }

        final RootComparator comp = new RootComparator();
        Collections.sort(libraries, comp);
        Collections.sort(others, comp);

        if (VERBOSE) Log.v(TAG, "Adding library roots: " + libraries);
        result.addAll(libraries);
        // Only add the spacer if it is actually separating something.
        if (!libraries.isEmpty() && !others.isEmpty()) {
            result.add(new SpacerItem());
        }

        if (VERBOSE) Log.v(TAG, "Adding plain roots: " + libraries);
        result.addAll(others);

        // Include apps that can handle this intent too.
        if (handlerAppIntent != null) {
            includeHandlerApps(handlerAppIntent, excludePackage, result);
        }

        return result;
    }

    /**
     * Adds apps capable of handling the original intent will be included in list of roots (in
     * special section at bottom).
     */
    private void includeHandlerApps(
            Intent handlerAppIntent, @Nullable String excludePackage, List<Item> result) {
        if (VERBOSE) Log.v(TAG, "Adding handler apps for intent: " + handlerAppIntent);
        Context context = getContext();
        final PackageManager pm = context.getPackageManager();
        final List<ResolveInfo> infos = pm.queryIntentActivities(
                handlerAppIntent, PackageManager.MATCH_DEFAULT_ONLY);

        final List<AppItem> apps = new ArrayList<>();

        // Omit ourselves and maybe calling package from the list
        for (ResolveInfo info : infos) {
            if (!context.getPackageName().equals(info.activityInfo.packageName) &&
                    !TextUtils.equals(excludePackage, info.activityInfo.packageName)) {
                final AppItem app = new AppItem(info, mActionHandler);
                if (VERBOSE) Log.v(TAG, "Adding handler app: " + app);
                apps.add(app);
            }
        }

        if (apps.size() > 0) {
            result.add(new SpacerItem());
            result.addAll(apps);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        onDisplayStateChanged();
    }

    public void onDisplayStateChanged() {
        final Context context = getActivity();
        final State state = ((BaseActivity) context).getDisplayState();

        if (state.action == State.ACTION_GET_CONTENT) {
            mList.setOnItemLongClickListener(mItemLongClickListener);
        } else {
            mList.setOnItemLongClickListener(null);
            mList.setLongClickable(false);
        }

        getLoaderManager().restartLoader(2, null, mCallbacks);
    }

    public void onCurrentRootChanged() {
        if (mAdapter == null) {
            return;
        }

        final RootInfo root = ((BaseActivity) getActivity()).getCurrentRoot();
        for (int i = 0; i < mAdapter.getCount(); i++) {
            final Object item = mAdapter.getItem(i);
            if (item instanceof RootItem) {
                final RootInfo testRoot = ((RootItem) item).root;
                if (Objects.equals(testRoot, root)) {
                    mList.setItemChecked(i, true);
                    return;
                }
            }
        }
    }

    /**
     * Attempts to shift focus back to the navigation drawer.
     */
    public boolean requestFocus() {
        return mList.requestFocus();
    }

    private BaseActivity getBaseActivity() {
        return (BaseActivity) getActivity();
    }

    @Override
    public void onCreateContextMenu(
            ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) menuInfo;
        final Item item = mAdapter.getItem(adapterMenuInfo.position);

        BaseActivity activity = getBaseActivity();
        item.createContextMenu(menu, activity.getMenuInflater(), mInjector.menuManager);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
        // There is a possibility that this is called from DirectoryFragment since
        // all fragments' onContextItemSelected gets called when any menu item is selected
        // This is to guard against it since DirectoryFragment's RecylerView does not have a
        // menuInfo
        if (adapterMenuInfo == null) {
            return false;
        }
        final RootItem rootItem = (RootItem) mAdapter.getItem(adapterMenuInfo.position);
        switch (item.getItemId()) {
            case R.id.root_menu_eject_root:
                final View ejectIcon = adapterMenuInfo.targetView.findViewById(R.id.eject_icon);
                ejectClicked(ejectIcon, rootItem.root, mActionHandler);
                return true;
            case R.id.root_menu_open_in_new_window:
                mActionHandler.openInNewWindow(new DocumentStack(rootItem.root));
                return true;
            case R.id.root_menu_paste_into_folder:
                mActionHandler.pasteIntoFolder(rootItem.root);
                return true;
            case R.id.root_menu_settings:
                mActionHandler.openSettings(rootItem.root);
                return true;
            default:
                if (DEBUG) Log.d(TAG, "Unhandled menu item selected: " + item);
                return false;
        }
    }

    private void getRootDocument(RootItem rootItem, RootUpdater updater) {
        // We need to start a GetRootDocumentTask so we can know whether items can be directly
        // pasted into root
        mActionHandler.getRootDocument(
                rootItem.root,
                CONTEXT_MENU_ITEM_TIMEOUT,
                (DocumentInfo doc) -> {
                    updater.updateDocInfoForRoot(doc);
                });
    }

    private Item getItem(View v) {
        final int pos = (Integer) v.getTag(R.id.item_position_tag);
        return mAdapter.getItem(pos);
    }

    static void ejectClicked(View ejectIcon, RootInfo root, ActionHandler actionHandler) {
        assert(ejectIcon != null);
        assert(!root.ejecting);
        ejectIcon.setEnabled(false);
        root.ejecting = true;
        actionHandler.ejectRoot(
                root,
                new BooleanConsumer() {
                    @Override
                    public void accept(boolean ejected) {
                        // Event if ejected is false, we should reset, since the op failed.
                        // Either way, we are no longer attempting to eject the device.
                        root.ejecting = false;

                        // If the view is still visible, we update its state.
                        if (ejectIcon.getVisibility() == View.VISIBLE) {
                            ejectIcon.setEnabled(!ejected);
                        }
                    }
                });
    }

    private static class RootComparator implements Comparator<RootItem> {
        @Override
        public int compare(RootItem lhs, RootItem rhs) {
            return lhs.root.compareTo(rhs.root);
        }
    }

    @FunctionalInterface
    interface RootUpdater {
        void updateDocInfoForRoot(DocumentInfo doc);
    }
}
