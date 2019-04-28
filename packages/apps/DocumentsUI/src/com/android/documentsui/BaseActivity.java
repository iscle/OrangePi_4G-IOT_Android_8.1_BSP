/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui;

import static com.android.documentsui.base.Shared.DEBUG;
import static com.android.documentsui.base.Shared.EXTRA_BENCHMARK;
import static com.android.documentsui.base.State.MODE_GRID;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.MessageQueue.IdleHandler;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.support.annotation.CallSuper;
import android.support.annotation.LayoutRes;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toolbar;

import com.android.documentsui.AbstractActionHandler.CommonAddons;
import com.android.documentsui.Injector.Injected;
import com.android.documentsui.NavigationViewManager.Breadcrumb;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.EventHandler;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.base.State.ViewMode;
import com.android.documentsui.dirlist.AnimationView;
import com.android.documentsui.dirlist.DirectoryFragment;
import com.android.documentsui.prefs.LocalPreferences;
import com.android.documentsui.prefs.Preferences;
import com.android.documentsui.prefs.PreferencesMonitor;
import com.android.documentsui.prefs.ScopedPreferences;
import com.android.documentsui.queries.CommandInterceptor;
import com.android.documentsui.queries.SearchViewManager;
import com.android.documentsui.queries.SearchViewManager.SearchManagerListener;
import com.android.documentsui.roots.ProvidersCache;
import com.android.documentsui.selection.Selection;
import com.android.documentsui.sidebar.RootsFragment;
import com.android.documentsui.sorting.SortController;
import com.android.documentsui.sorting.SortModel;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.annotation.Nullable;

public abstract class BaseActivity
        extends Activity implements CommonAddons, NavigationViewManager.Environment {

    private static final String BENCHMARK_TESTING_PACKAGE = "com.android.documentsui.appperftests";

    protected SearchViewManager mSearchManager;
    protected State mState;

    @Injected
    protected Injector<?> mInjector;

    protected @Nullable RetainedState mRetainedState;
    protected ProvidersCache mProviders;
    protected DocumentsAccess mDocs;
    protected DrawerController mDrawer;

    protected NavigationViewManager mNavigator;
    protected SortController mSortController;

    private final List<EventListener> mEventListeners = new ArrayList<>();
    private final String mTag;

    @LayoutRes
    private int mLayoutId;

    private RootsMonitor<BaseActivity> mRootsMonitor;

    private long mStartTime;

    private PreferencesMonitor mPreferencesMonitor;

    public BaseActivity(@LayoutRes int layoutId, String tag) {
        mLayoutId = layoutId;
        mTag = tag;
    }

    protected abstract void refreshDirectory(int anim);
    /** Allows sub-classes to include information in a newly created State instance. */
    protected abstract void includeState(State initialState);
    protected abstract void onDirectoryCreated(DocumentInfo doc);

    public abstract Injector<?> getInjector();

    @CallSuper
    @Override
    public void onCreate(Bundle icicle) {
        // Record the time when onCreate is invoked for metric.
        mStartTime = new Date().getTime();

        super.onCreate(icicle);

        final Intent intent = getIntent();

        addListenerForLaunchCompletion();

        setContentView(mLayoutId);

        mInjector = getInjector();
        mState = getState(icicle);
        mDrawer = DrawerController.create(this, mInjector.config);
        Metrics.logActivityLaunch(this, mState, intent);

        // we're really interested in retainining state in our very complex
        // DirectoryFragment. So we do a little code yoga to extend
        // support to that fragment.
        mRetainedState = (RetainedState) getLastNonConfigurationInstance();
        mProviders = DocumentsApplication.getProvidersCache(this);
        mDocs = DocumentsAccess.create(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);

        Breadcrumb breadcrumb =
                Shared.findView(this, R.id.dropdown_breadcrumb, R.id.horizontal_breadcrumb);
        assert(breadcrumb != null);

        mNavigator = new NavigationViewManager(mDrawer, toolbar, mState, this, breadcrumb);
        SearchManagerListener searchListener = new SearchManagerListener() {
            /**
             * Called when search results changed. Refreshes the content of the directory. It
             * doesn't refresh elements on the action bar. e.g. The current directory name displayed
             * on the action bar won't get updated.
             */
            @Override
            public void onSearchChanged(@Nullable String query) {
                if (query != null) {
                    Metrics.logUserAction(BaseActivity.this, Metrics.USER_ACTION_SEARCH);
                }

                mInjector.actions.loadDocumentsForCurrentStack();
            }

            @Override
            public void onSearchFinished() {
                // Restores menu icons state
                invalidateOptionsMenu();
            }

            @Override
            public void onSearchViewChanged(boolean opened) {
                mNavigator.update();
            }
        };

        // "Commands" are meta input for controlling system behavior.
        // We piggy back on search input as it is the only text input
        // area in the app. But the functionality is independent
        // of "regular" search query processing.
        final CommandInterceptor cmdInterceptor = new CommandInterceptor(mInjector.features);
        cmdInterceptor.add(new CommandInterceptor.DumpRootsCacheHandler(this));

        // A tiny decorator that adds support for enabling CommandInterceptor
        // based on query input. It's sorta like CommandInterceptor, but its metaaahhh.
        EventHandler<String> queryInterceptor =
                CommandInterceptor.createDebugModeFlipper(
                        mInjector.features,
                        mInjector.debugHelper::toggleDebugMode,
                        cmdInterceptor);
        mSearchManager = new SearchViewManager(searchListener, queryInterceptor, icicle);
        mSortController = SortController.create(this, mState.derivedMode, mState.sortModel);

        mPreferencesMonitor = new PreferencesMonitor(
                getApplicationContext().getPackageName(),
                PreferenceManager.getDefaultSharedPreferences(this),
                this::onPreferenceChanged);
        mPreferencesMonitor.start();

        // Base classes must update result in their onCreate.
        setResult(Activity.RESULT_CANCELED);
    }

    public void onPreferenceChanged(String pref) {
        // For now, we only work with prefs that we backup. This
        // just limits the scope of what we expect to come flowing
        // through here until we know we want more and fancier options.
        assert(Preferences.shouldBackup(pref));

        switch (pref) {
            case ScopedPreferences.INCLUDE_DEVICE_ROOT:
                updateDisplayAdvancedDevices(mInjector.prefs.getShowDeviceRoot());
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        mRootsMonitor = new RootsMonitor<>(
                this,
                mInjector.actions,
                mProviders,
                mDocs,
                mState,
                mSearchManager,
                mInjector.actionModeController::finishActionMode);
        mRootsMonitor.start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean showMenu = super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.activity, menu);
        mNavigator.update();
        boolean fullBarSearch = getResources().getBoolean(R.bool.full_bar_search_view);
        mSearchManager.install(menu, fullBarSearch);

        return showMenu;
    }

    @Override
    @CallSuper
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        mSearchManager.showMenu(mState.stack);
        return true;
    }

    @Override
    protected void onDestroy() {
        mRootsMonitor.stop();
        mPreferencesMonitor.stop();
        mSortController.destroy();
        super.onDestroy();
    }

    private State getState(@Nullable Bundle icicle) {
        if (icicle != null) {
            State state = icicle.<State>getParcelable(Shared.EXTRA_STATE);
            if (DEBUG) Log.d(mTag, "Recovered existing state object: " + state);
            return state;
        }

        State state = new State();

        final Intent intent = getIntent();

        state.sortModel = SortModel.createModel();
        state.localOnly = intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false);
        state.excludedAuthorities = getExcludedAuthorities();

        includeState(state);

        state.showAdvanced = Shared.mustShowDeviceRoot(intent)
                || mInjector.prefs.getShowDeviceRoot();

        // Only show the toggle if advanced isn't forced enabled.
        state.showDeviceStorageOption = !Shared.mustShowDeviceRoot(intent);

        if (DEBUG) Log.d(mTag, "Created new state object: " + state);

        return state;
    }

    @Override
    public void setRootsDrawerOpen(boolean open) {
        mNavigator.revealRootsDrawer(open);
    }

    @Override
    public void onRootPicked(RootInfo root) {
        // Clicking on the current root removes search
        mSearchManager.cancelSearch();

        // Skip refreshing if root nor directory didn't change
        if (root.equals(getCurrentRoot()) && mState.stack.size() == 1) {
            return;
        }

        mInjector.actionModeController.finishActionMode();
        mSortController.onViewModeChanged(mState.derivedMode);

        // Set summary header's visibility. Only recents and downloads root may have summary in
        // their docs.
        mState.sortModel.setDimensionVisibility(
                SortModel.SORT_DIMENSION_ID_SUMMARY,
                root.isRecents() || root.isDownloads() ? View.VISIBLE : View.INVISIBLE);

        // Clear entire backstack and start in new root
        mState.stack.changeRoot(root);

        // Recents is always in memory, so we just load it directly.
        // Otherwise we delegate loading data from disk to a task
        // to ensure a responsive ui.
        if (mProviders.isRecentsRoot(root)) {
            refreshCurrentRootAndDirectory(AnimationView.ANIM_NONE);
        } else {
            mInjector.actions.getRootDocument(
                    root,
                    TimeoutTask.DEFAULT_TIMEOUT,
                    doc -> mInjector.actions.openRootDocument(doc));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;

            case R.id.option_menu_create_dir:
                getInjector().actions.showCreateDirectoryDialog();
                return true;

            case R.id.option_menu_search:
                // SearchViewManager listens for this directly.
                return false;

            case R.id.option_menu_grid:
                setViewMode(State.MODE_GRID);
                return true;

            case R.id.option_menu_list:
                setViewMode(State.MODE_LIST);
                return true;

            case R.id.option_menu_advanced:
                onDisplayAdvancedDevices();
                return true;

            case R.id.option_menu_select_all:
                getInjector().actions.selectAllFiles();
                return true;

            case R.id.option_menu_debug:
                getInjector().actions.showDebugMessage();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    protected final @Nullable DirectoryFragment getDirectoryFragment() {
        return DirectoryFragment.get(getFragmentManager());
    }

    /**
     * Returns true if a directory can be created in the current location.
     * @return
     */
    protected boolean canCreateDirectory() {
        final RootInfo root = getCurrentRoot();
        final DocumentInfo cwd = getCurrentDirectory();
        return cwd != null
                && cwd.isCreateSupported()
                && !mSearchManager.isSearching()
                && !root.isRecents();
    }

    // TODO: make navigator listen to state
    @Override
    public final void updateNavigator() {
        mNavigator.update();
    }

    @Override
    public void restoreRootAndDirectory() {
        // We're trying to restore stuff in document stack from saved instance. If we didn't have a
        // chance to spawn a fragment before we need to do it now. However if we spawned a fragment
        // already, system will automatically restore the fragment for us so we don't need to do
        // that manually this time.
        if (DirectoryFragment.get(getFragmentManager()) == null) {
            refreshCurrentRootAndDirectory(AnimationView.ANIM_NONE);
        }
    }

    /**
     * Refreshes the content of the director and the menu/action bar.
     * The current directory name and selection will get updated.
     * @param anim
     */
    @Override
    public final void refreshCurrentRootAndDirectory(int anim) {
        // The following call will crash if it's called before onCreateOptionMenu() is called in
        // which we install menu item to search view manager, and there is a search query we need to
        // restore. This happens when we're still initializing our UI so we shouldn't cancel the
        // search which will be restored later in onCreateOptionMenu(). Try finding a way to guard
        // refreshCurrentRootAndDirectory() from being called while we're restoring the state of UI
        // from the saved state passed in onCreate().
        mSearchManager.cancelSearch();

        mState.derivedMode = LocalPreferences.getViewMode(this, mState.stack.getRoot(), MODE_GRID);

        refreshDirectory(anim);

        final RootsFragment roots = RootsFragment.get(getFragmentManager());
        if (roots != null) {
            roots.onCurrentRootChanged();
        }

        mNavigator.update();
        // Causes talkback to announce the activity's new title
        if (mState.stack.isRecents()) {
            setTitle(mProviders.getRecentsRoot().title);
        } else {
            setTitle(mState.stack.getTitle());
        }
        invalidateOptionsMenu();
    }

    private final List<String> getExcludedAuthorities() {
        List<String> authorities = new ArrayList<>();
        if (getIntent().getBooleanExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, false)) {
            // Exclude roots provided by the calling package.
            String packageName = Shared.getCallingPackageName(this);
            try {
                PackageInfo pkgInfo = getPackageManager().getPackageInfo(packageName,
                        PackageManager.GET_PROVIDERS);
                for (ProviderInfo provider: pkgInfo.providers) {
                    authorities.add(provider.authority);
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(mTag, "Calling package name does not resolve: " + packageName);
            }
        }
        return authorities;
    }

    public static BaseActivity get(Fragment fragment) {
        return (BaseActivity) fragment.getActivity();
    }

    public State getDisplayState() {
        return mState;
    }

    /**
     * Set internal storage visible based on explicit user action.
     */
    private void onDisplayAdvancedDevices() {
        boolean display = !mState.showAdvanced;
        Metrics.logUserAction(this,
                display ? Metrics.USER_ACTION_SHOW_ADVANCED : Metrics.USER_ACTION_HIDE_ADVANCED);

        mInjector.prefs.setShowDeviceRoot(display);
        updateDisplayAdvancedDevices(display);
    }

    private void updateDisplayAdvancedDevices(boolean display) {
        mState.showAdvanced = display;
        @Nullable RootsFragment fragment = RootsFragment.get(getFragmentManager());
        if (fragment != null) {
            // This also takes care of updating launcher shortcuts (which are roots :)
            fragment.onDisplayStateChanged();
        }
        invalidateOptionsMenu();
    }

    /**
     * Set mode based on explicit user action.
     */
    void setViewMode(@ViewMode int mode) {
        if (mode == State.MODE_GRID) {
            Metrics.logUserAction(this, Metrics.USER_ACTION_GRID);
        } else if (mode == State.MODE_LIST) {
            Metrics.logUserAction(this, Metrics.USER_ACTION_LIST);
        }

        LocalPreferences.setViewMode(this, getCurrentRoot(), mode);
        mState.derivedMode = mode;

        // view icon needs to be updated, but we *could* do it
        // in onOptionsItemSelected, and not do the full invalidation
        // But! That's a larger refactoring we'll save for another day.
        invalidateOptionsMenu();
        DirectoryFragment dir = getDirectoryFragment();
        if (dir != null) {
            dir.onViewModeChanged();
        }

        mSortController.onViewModeChanged(mode);
    }

    public void setPending(boolean pending) {
        // TODO: Isolate this behavior to PickActivity.
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putParcelable(Shared.EXTRA_STATE, mState);
        mSearchManager.onSaveInstanceState(state);
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
    }

    /**
     * Delegate ths call to the current fragment so it can save selection.
     * Feel free to expand on this with other useful state.
     */
    @Override
    public RetainedState onRetainNonConfigurationInstance() {
        RetainedState retained = new RetainedState();
        DirectoryFragment fragment = DirectoryFragment.get(getFragmentManager());
        if (fragment != null) {
            fragment.retainState(retained);
        }
        return retained;
    }

    public @Nullable RetainedState getRetainedState() {
        return mRetainedState;
    }

    @Override
    public boolean isSearchExpanded() {
        return mSearchManager.isExpanded();
    }

    @Override
    public RootInfo getCurrentRoot() {
        RootInfo root = mState.stack.getRoot();
        if (root != null) {
            return root;
        } else {
            return mProviders.getRecentsRoot();
        }
    }

    @Override
    public DocumentInfo getCurrentDirectory() {
        return mState.stack.peek();
    }

    @VisibleForTesting
    public void addEventListener(EventListener listener) {
        mEventListeners.add(listener);
    }

    @VisibleForTesting
    public void removeEventListener(EventListener listener) {
        mEventListeners.remove(listener);
    }

    @VisibleForTesting
    public void notifyDirectoryLoaded(Uri uri) {
        for (EventListener listener : mEventListeners) {
            listener.onDirectoryLoaded(uri);
        }
    }

    @VisibleForTesting
    @Override
    public void notifyDirectoryNavigated(Uri uri) {
        for (EventListener listener : mEventListeners) {
            listener.onDirectoryNavigated(uri);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mInjector.debugHelper.debugCheck(event.getDownTime(), event.getKeyCode());
        }

        DocumentsApplication.getDragAndDropManager(this).onKeyEvent(event);

        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mInjector.actions.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Pops the top entry off the directory stack, and returns the user to the previous directory.
     * If the directory stack only contains one item, this method does nothing.
     *
     * @return Whether the stack was popped.
     */
    protected boolean popDir() {
        if (mState.stack.size() > 1) {
            mState.stack.pop();
            refreshCurrentRootAndDirectory(AnimationView.ANIM_LEAVE);
            return true;
        }
        return false;
    }

    protected boolean focusSidebar() {
        RootsFragment rf = RootsFragment.get(getFragmentManager());
        assert (rf != null);
        return rf.requestFocus();
    }

    /**
     * Closes the activity when it's idle.
     */
    private void addListenerForLaunchCompletion() {
        addEventListener(new EventListener() {
            @Override
            public void onDirectoryNavigated(Uri uri) {
            }

            @Override
            public void onDirectoryLoaded(Uri uri) {
                removeEventListener(this);
                getMainLooper().getQueue().addIdleHandler(new IdleHandler() {
                    @Override
                    public boolean queueIdle() {
                        // If startup benchmark is requested by a whitelisted testing package, then
                        // close the activity once idle, and notify the testing activity.
                        if (getIntent().getBooleanExtra(EXTRA_BENCHMARK, false) &&
                                BENCHMARK_TESTING_PACKAGE.equals(getCallingPackage())) {
                            setResult(RESULT_OK);
                            finish();
                        }

                        Metrics.logStartupMs(
                                BaseActivity.this, (int) (new Date().getTime() - mStartTime));

                        // Remove the idle handler.
                        return false;
                    }
                });
            }
        });
    }

    public static final class RetainedState {
        public @Nullable Selection selection;

        public boolean hasSelection() {
            return selection != null;
        }
    }

    @VisibleForTesting
    protected interface EventListener {
        /**
         * @param uri Uri navigated to. If recents, then null.
         */
        void onDirectoryNavigated(@Nullable Uri uri);

        /**
         * @param uri Uri of the loaded directory. If recents, then null.
         */
        void onDirectoryLoaded(@Nullable Uri uri);
    }
}
