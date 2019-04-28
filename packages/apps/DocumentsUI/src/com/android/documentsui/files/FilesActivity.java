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

package com.android.documentsui.files;

import static com.android.documentsui.OperationDialogFragment.DIALOG_TYPE_UNKNOWN;

import android.app.ActivityManager.TaskDescription;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.Context;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.Menu;
import android.view.MenuItem;

import com.android.documentsui.ActionModeController;
import com.android.documentsui.BaseActivity;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.FocusManager;
import com.android.documentsui.Injector;
import com.android.documentsui.MenuManager.DirectoryDetails;
import com.android.documentsui.OperationDialogFragment;
import com.android.documentsui.OperationDialogFragment.DialogType;
import com.android.documentsui.ProviderExecutor;
import com.android.documentsui.R;
import com.android.documentsui.SharedInputHandler;
import com.android.documentsui.ShortcutsUpdater;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.clipping.DocumentClipper;
import com.android.documentsui.dirlist.AnimationView.AnimationType;
import com.android.documentsui.dirlist.DirectoryFragment;
import com.android.documentsui.prefs.ScopedPreferences;
import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.sidebar.RootsFragment;
import com.android.documentsui.ui.DialogController;
import com.android.documentsui.ui.MessageBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Standalone file management activity.
 */
public class FilesActivity extends BaseActivity implements ActionHandler.Addons {

    private static final String TAG = "FilesActivity";
    static final String PREFERENCES_SCOPE = "files";

    private Injector<ActionHandler<FilesActivity>> mInjector;
    private ActivityInputHandler mActivityInputHandler;
    private SharedInputHandler mSharedInputHandler;

    public FilesActivity() {
        super(R.layout.files_activity, TAG);
    }

    // make these methods visible in this package to work around compiler bug http://b/62218600
    @Override protected boolean focusSidebar() { return super.focusSidebar(); }
    @Override protected boolean popDir() { return super.popDir(); }

    @Override
    public void onCreate(Bundle icicle) {

        MessageBuilder messages = new MessageBuilder(this);
        Features features = Features.create(this);
        ScopedPreferences prefs = ScopedPreferences.create(this, PREFERENCES_SCOPE);

        mInjector = new Injector<>(
                features,
                new Config(),
                ScopedPreferences.create(this, PREFERENCES_SCOPE),
                messages,
                DialogController.create(features, this, messages),
                DocumentsApplication.getFileTypeLookup(this),
                new ShortcutsUpdater(this, prefs)::update);

        super.onCreate(icicle);

        DocumentClipper clipper = DocumentsApplication.getDocumentClipper(this);
        mInjector.selectionMgr = new SelectionManager(SelectionManager.MODE_MULTIPLE);

        mInjector.focusManager = new FocusManager(
                mInjector.features,
                mInjector.selectionMgr,
                mDrawer,
                this::focusSidebar,
                getColor(R.color.accent_dark));

        mInjector.menuManager = new MenuManager(
                mInjector.features,
                mSearchManager,
                mState,
                new DirectoryDetails(this) {
                    @Override
                    public boolean hasItemsToPaste() {
                        return clipper.hasItemsToPaste();
                    }
                },
                getApplicationContext(),
                mInjector.selectionMgr,
                mProviders::getApplicationName,
                mInjector.getModel()::getItemUri);

        mInjector.actionModeController = new ActionModeController(
                this,
                mInjector.selectionMgr,
                mInjector.menuManager,
                mInjector.messages);

        mInjector.actions = new ActionHandler<>(
                this,
                mState,
                mProviders,
                mDocs,
                mSearchManager,
                ProviderExecutor::forAuthority,
                mInjector.actionModeController,
                clipper,
                DocumentsApplication.getClipStore(this),
                DocumentsApplication.getDragAndDropManager(this),
                mInjector);

        mInjector.searchManager = mSearchManager;

        mActivityInputHandler =
                new ActivityInputHandler(mInjector.actions::deleteSelectedDocuments);
        mSharedInputHandler =
                new SharedInputHandler(
                        mInjector.focusManager,
                        mInjector.selectionMgr,
                        mInjector.searchManager::cancelSearch,
                        this::popDir,
                        mInjector.features);

        RootsFragment.show(getFragmentManager(), null);

        final Intent intent = getIntent();

        mInjector.actions.initLocation(intent);

        // Allow the activity to masquerade as another, so we can look both like
        // Downloads and Files, but with only a single underlying activity.
        if (intent.hasExtra(LauncherActivity.TASK_LABEL_RES)
                && intent.hasExtra(LauncherActivity.TASK_ICON_RES)) {
            updateTaskDescription(intent);
        }

        presentFileErrors(icicle, intent);
    }

    // This is called in the intent contains label and icon resources.
    // When that is true, the launcher activity has supplied them so we
    // can adapt our presentation to how we were launched.
    // Without this code, overlaying launcher_icon and launcher_label
    // resources won't create a complete illusion of the activity being renamed.
    // E.g. if we re-brand Files to Downloads by overlaying label and icon
    // when the user tapped recents they'd see not "Downloads", but the
    // underlying Activity description...Files.
    // Alternate if we rename this activity, when launching other ways
    // like when browsing files on a removable disk, the app would be
    // called Downloads, which is also not the desired behavior.
    private void updateTaskDescription(final Intent intent) {
        int labelRes = intent.getIntExtra(LauncherActivity.TASK_LABEL_RES, -1);
        assert(labelRes > -1);
        String label = getResources().getString(labelRes);

        int iconRes = intent.getIntExtra(LauncherActivity.TASK_ICON_RES, -1);
        assert(iconRes > -1);

        Drawable drawable = getResources().getDrawable(
                iconRes,
                null  // we don't care about theme, since the supplier should have handled that.
                );

        setTaskDescription(new TaskDescription(label, flattenDrawableToBitmap(drawable)));
    }

    // AdaptiveIconDrawable assumes that the consumer of the icon applies the shadow and
    // recents assume that the provider of the task description handles these. Hence,
    // we apply the shadow treatment same as Launcher3 implementation.
    private Bitmap flattenDrawableToBitmap(Drawable d) {
        // Percent of actual icon size
        float ICON_SIZE_BLUR_FACTOR = 0.5f/48;
        // Percent of actual icon size
        float ICON_SIZE_KEY_SHADOW_DELTA_FACTOR = 1f/48;
        int KEY_SHADOW_ALPHA = 61;
        int AMBIENT_SHADOW_ALPHA = 30;
        if (d instanceof BitmapDrawable) {
            return ((BitmapDrawable) d).getBitmap();
        } else if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable aid = (AdaptiveIconDrawable) d;
            int iconSize = getResources().getDimensionPixelSize(android.R.dimen.app_icon_size);
            int shadowSize = Math.max(iconSize, aid.getIntrinsicHeight());
            aid.setBounds(0, 0, shadowSize, shadowSize);

            float blur = ICON_SIZE_BLUR_FACTOR * shadowSize;
            float keyShadowDistance = ICON_SIZE_KEY_SHADOW_DELTA_FACTOR * shadowSize;

            int bitmapSize = (int) (shadowSize + 2 * blur + keyShadowDistance);
            Bitmap shadow = Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888);

            Canvas canvas = new Canvas(shadow);
            canvas.translate(blur + keyShadowDistance / 2, blur);

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.TRANSPARENT);

            // Draw ambient shadow
            paint.setShadowLayer(blur, 0, 0, AMBIENT_SHADOW_ALPHA << 24);
            canvas.drawPath(aid.getIconMask(), paint);

            // Draw key shadow
            canvas.translate(0, keyShadowDistance);
            paint.setShadowLayer(blur, 0, 0, KEY_SHADOW_ALPHA << 24);
            canvas.drawPath(aid.getIconMask(), paint);

            // Draw original drawable
            aid.draw(canvas);

            canvas.setBitmap(null);
            return shadow;
        }
        return null;
    }

    private void presentFileErrors(Bundle icicle, final Intent intent) {
        final @DialogType int dialogType = intent.getIntExtra(
                FileOperationService.EXTRA_DIALOG_TYPE, DIALOG_TYPE_UNKNOWN);
        // DialogFragment takes care of restoring the dialog on configuration change.
        // Only show it manually for the first time (icicle is null).
        if (icicle == null && dialogType != DIALOG_TYPE_UNKNOWN) {
            final int opType = intent.getIntExtra(
                    FileOperationService.EXTRA_OPERATION_TYPE,
                    FileOperationService.OPERATION_COPY);
            final ArrayList<DocumentInfo> docList =
                    intent.getParcelableArrayListExtra(FileOperationService.EXTRA_FAILED_DOCS);
            final ArrayList<Uri> uriList =
                    intent.getParcelableArrayListExtra(FileOperationService.EXTRA_FAILED_URIS);
            OperationDialogFragment.show(
                    getFragmentManager(),
                    dialogType,
                    docList,
                    uriList,
                    mState.stack,
                    opType);
        }
    }

    @Override
    public void includeState(State state) {
        final Intent intent = getIntent();

        // This is a remnant of old logic where we used to initialize accept MIME types in
        // BaseActivity. ProvidersAccess still rely on this being correctly initialized so we still have
        // to initialize it in FilesActivity.
        state.initAcceptMimes(intent, "*/*");
        state.action = State.ACTION_BROWSE;
        state.allowMultiple = true;

        // Options specific to the DocumentsActivity.
        assert(!intent.hasExtra(Intent.EXTRA_LOCAL_ONLY));
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // This check avoids a flicker from "Recents" to "Home".
        // Only update action bar at this point if there is an active
        // search. Why? Because this avoid an early (undesired) load of
        // the recents root...which is the default root in other activities.
        // In Files app "Home" is the default, but it is loaded async.
        // update will be called once Home root is loaded.
        // Except while searching we need this call to ensure the
        // search bits get laid out correctly.
        if (mSearchManager.isSearching()) {
            mNavigator.update();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        final RootInfo root = getCurrentRoot();

        // If we're browsing a specific root, and that root went away, then we
        // have no reason to hang around.
        // TODO: Rather than just disappearing, maybe we should inform
        // the user what has happened, let them close us. Less surprising.
        if (mProviders.getRootBlocking(root.authority, root.rootId) == null) {
            finish();
        }
    }

    @Override
    public String getDrawerTitle() {
        Intent intent = getIntent();
        return (intent != null && intent.hasExtra(Intent.EXTRA_TITLE))
                ? intent.getStringExtra(Intent.EXTRA_TITLE)
                : getString(R.string.app_label);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        mInjector.menuManager.updateOptionMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        DirectoryFragment dir;
        switch (item.getItemId()) {
            case R.id.option_menu_create_dir:
                assert(canCreateDirectory());
                mInjector.actions.showCreateDirectoryDialog();
                break;
            case R.id.option_menu_new_window:
                mInjector.actions.openInNewWindow(mState.stack);
                break;
            case R.id.option_menu_settings:
                mInjector.actions.openSettings(getCurrentRoot());
                break;
            case R.id.option_menu_select_all:
                mInjector.actions.selectAllFiles();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onProvideKeyboardShortcuts(
            List<KeyboardShortcutGroup> data, Menu menu, int deviceId) {
        mInjector.menuManager.updateKeyboardShortcutsMenu(data, this::getString);
    }

    @Override
    public void refreshDirectory(@AnimationType int anim) {
        final FragmentManager fm = getFragmentManager();
        final RootInfo root = getCurrentRoot();
        final DocumentInfo cwd = getCurrentDirectory();

        assert(!mSearchManager.isSearching());

        if (mState.stack.isRecents()) {
            DirectoryFragment.showRecentsOpen(fm, anim);
        } else {
            // Normal boring directory
            DirectoryFragment.showDirectory(fm, root, cwd, anim);
        }
    }

    @Override
    public void onDocumentsPicked(List<DocumentInfo> docs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onDocumentPicked(DocumentInfo doc) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onDirectoryCreated(DocumentInfo doc) {
        assert(doc.isDirectory());
        mInjector.focusManager.focusDocument(doc.documentId);
    }

    @CallSuper
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return mActivityInputHandler.onKeyDown(keyCode, event)
                || mSharedInputHandler.onKeyDown(
                        keyCode,
                        event)
                || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        DirectoryFragment dir;
        // TODO: All key events should be statically bound using alphabeticShortcut.
        // But not working.
        switch (keyCode) {
            case KeyEvent.KEYCODE_A:
                mInjector.actions.selectAllFiles();
                return true;
            case KeyEvent.KEYCODE_X:
                mInjector.actions.cutToClipboard();
                return true;
            case KeyEvent.KEYCODE_C:
                mInjector.actions.copyToClipboard();
                return true;
            case KeyEvent.KEYCODE_V:
                dir = getDirectoryFragment();
                if (dir != null) {
                    dir.pasteFromClipboard();
                }
                return true;
            default:
                return super.onKeyShortcut(keyCode, event);
        }
    }

    @Override
    public Injector<ActionHandler<FilesActivity>> getInjector() {
        return mInjector;
    }
}
