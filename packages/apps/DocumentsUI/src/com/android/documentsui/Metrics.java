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

package com.android.documentsui;

import static android.os.Environment.STANDARD_DIRECTORIES;
import static com.android.documentsui.DocumentsApplication.acquireUnstableProviderOrThrow;
import static com.android.documentsui.base.Shared.DEBUG;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.annotation.StringDef;
import android.app.Activity;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Path;
import android.provider.DocumentsProvider;
import android.util.Log;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Providers;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.base.State.ActionType;
import com.android.documentsui.files.LauncherActivity;
import com.android.documentsui.roots.ProvidersAccess;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.services.FileOperationService.OpType;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/** @hide */
public final class Metrics {
    private static final String TAG = "Metrics";

    // These strings have to be whitelisted in tron. Do not change them.
    private static final String COUNT_LAUNCH_ACTION = "docsui_launch_action";
    private static final String COUNT_ROOT_VISITED_IN_MANAGER
            = "docsui_root_visited_in_manager";
    private static final String COUNT_ROOT_VISITED_IN_PICKER
            = "docsui_root_visited_in_picker";
    private static final String COUNT_OPEN_MIME = "docsui_open_mime";
    private static final String COUNT_CREATE_MIME = "docsui_create_mime";
    private static final String COUNT_GET_CONTENT_MIME = "docsui_get_content_mime";
    private static final String COUNT_BROWSE_ROOT = "docsui_browse_root";
    @Deprecated private static final String COUNT_MANAGE_ROOT = "docsui_manage_root";
    @Deprecated private static final String COUNT_MULTI_WINDOW = "docsui_multi_window";
    private static final String COUNT_FILEOP_SYSTEM = "docsui_fileop_system";
    private static final String COUNT_FILEOP_EXTERNAL = "docsui_fileop_external";
    private static final String COUNT_FILEOP_CANCELED = "docsui_fileop_canceled";
    private static final String COUNT_STARTUP_MS = "docsui_startup_ms";
    @Deprecated private static final String COUNT_DRAWER_OPENED = "docsui_drawer_opened";
    private static final String COUNT_USER_ACTION = "docsui_menu_action";
    private static final String COUNT_BROWSE_AT_LOCATION = "docsui_browse_at_location";
    private static final String COUNT_CREATE_AT_LOCATION = "docsui_create_at_location";
    private static final String COUNT_OPEN_AT_LOCATION = "docsui_open_at_location";
    private static final String COUNT_GET_CONTENT_AT_LOCATION = "docsui_get_content_at_location";
    private static final String COUNT_MEDIA_FILEOP_FAILURE = "docsui_media_fileop_failure";
    private static final String COUNT_DOWNLOADS_FILEOP_FAILURE = "docsui_downloads_fileop_failure";
    private static final String COUNT_INTERNAL_STORAGE_FILEOP_FAILURE
            = "docsui_internal_storage_fileop_failure";
    private static final String COUNT_EXTERNAL_STORAGE_FILEOP_FAILURE
            = "docsui_external_storage_fileop_failure";
    private static final String COUNT_MTP_FILEOP_FAILURE = "docsui_mtp_fileop_failure";
    private static final String COUNT_OTHER_FILEOP_FAILURE = "docsui_other_fileop_failure";
    private static final String COUNT_FILE_COPIED = "docsui_file_copied";
    private static final String COUNT_FILE_MOVED = "docsui_file_moved";

    // Indices for bucketing roots in the roots histogram. "Other" is the catch-all index for any
    // root that is not explicitly recognized by the Metrics code (see {@link
    // #getSanitizedRootIndex}). Apps are also bucketed in this histogram.
    // Do not change or rearrange these values, that will break historical data. Only add to the end
    // of the list.
    // Do not use negative numbers or zero; clearcut only handles positive integers.
    private static final int ROOT_NONE = 1;
    private static final int ROOT_OTHER = 2;
    private static final int ROOT_AUDIO = 3;
    private static final int ROOT_DEVICE_STORAGE = 4;
    private static final int ROOT_DOWNLOADS = 5;
    private static final int ROOT_HOME = 6;
    private static final int ROOT_IMAGES = 7;
    private static final int ROOT_RECENTS = 8;
    private static final int ROOT_VIDEOS = 9;
    private static final int ROOT_MTP = 10;
    // Apps aren't really "roots", but they are treated as such in the roots fragment UI and so they
    // are logged analogously to roots.
    private static final int ROOT_THIRD_PARTY_APP = 100;

    @IntDef(flag = true, value = {
            ROOT_NONE,
            ROOT_OTHER,
            ROOT_AUDIO,
            ROOT_DEVICE_STORAGE,
            ROOT_DOWNLOADS,
            ROOT_HOME,
            ROOT_IMAGES,
            ROOT_RECENTS,
            ROOT_VIDEOS,
            ROOT_MTP,
            ROOT_THIRD_PARTY_APP
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Root {}

    // Indices for bucketing mime types.
    // Do not change or rearrange these values, that will break historical data. Only add to the end
    // of the list.
    // Do not use negative numbers or zero; clearcut only handles positive integers.
    private static final int MIME_NONE = 1; // null mime
    private static final int MIME_ANY = 2; // */*
    private static final int MIME_APPLICATION = 3; // application/*
    private static final int MIME_AUDIO = 4; // audio/*
    private static final int MIME_IMAGE = 5; // image/*
    private static final int MIME_MESSAGE = 6; // message/*
    private static final int MIME_MULTIPART = 7; // multipart/*
    private static final int MIME_TEXT = 8; // text/*
    private static final int MIME_VIDEO = 9; // video/*
    private static final int MIME_OTHER = 10; // anything not enumerated below

    @IntDef(flag = true, value = {
            MIME_NONE,
            MIME_ANY,
            MIME_APPLICATION,
            MIME_AUDIO,
            MIME_IMAGE,
            MIME_MESSAGE,
            MIME_MULTIPART,
            MIME_TEXT,
            MIME_VIDEO,
            MIME_OTHER
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mime {}

    public static final int FILES_SCOPE = 1;
    public static final int PICKER_SCOPE = 2;

    @IntDef({ FILES_SCOPE, PICKER_SCOPE })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ContextScope {}

    // Codes representing different kinds of file operations. These are used for bucketing
    // operations in the COUNT_FILEOP_{SYSTEM|EXTERNAL} histograms.
    // Do not change or rearrange these values, that will break historical data. Only add to the
    // list.
    // Do not use negative numbers or zero; clearcut only handles positive integers.
    private static final int FILEOP_OTHER = 1; // any file operation not listed below
    private static final int FILEOP_COPY_INTRA_PROVIDER = 2; // Copy within a provider
    private static final int FILEOP_COPY_SYSTEM_PROVIDER = 3; // Copy to a system provider.
    private static final int FILEOP_COPY_EXTERNAL_PROVIDER = 4; // Copy to a 3rd-party provider.
    private static final int FILEOP_MOVE_INTRA_PROVIDER = 5; // Move within a provider.
    private static final int FILEOP_MOVE_SYSTEM_PROVIDER = 6; // Move to a system provider.
    private static final int FILEOP_MOVE_EXTERNAL_PROVIDER = 7; // Move to a 3rd-party provider.
    private static final int FILEOP_DELETE = 8;
    private static final int FILEOP_RENAME = 9;
    private static final int FILEOP_CREATE_DIR = 10;
    private static final int FILEOP_OTHER_ERROR = 100;
    private static final int FILEOP_DELETE_ERROR = 101;
    private static final int FILEOP_MOVE_ERROR = 102;
    private static final int FILEOP_COPY_ERROR = 103;
    private static final int FILEOP_RENAME_ERROR = 104;
    private static final int FILEOP_CREATE_DIR_ERROR = 105;
    private static final int FILEOP_COMPRESS_INTRA_PROVIDER = 106; // Compres within a provider
    private static final int FILEOP_COMPRESS_SYSTEM_PROVIDER = 107; // Compress to a system provider.
    private static final int FILEOP_COMPRESS_EXTERNAL_PROVIDER = 108; // Compress to a 3rd-party provider.
    private static final int FILEOP_EXTRACT_INTRA_PROVIDER = 109; // Extract within a provider
    private static final int FILEOP_EXTRACT_SYSTEM_PROVIDER = 110; // Extract to a system provider.
    private static final int FILEOP_EXTRACT_EXTERNAL_PROVIDER = 111; // Extract to a 3rd-party provider.
    private static final int FILEOP_COMPRESS_ERROR = 112;
    private static final int FILEOP_EXTRACT_ERROR = 113;

    @IntDef(flag = true, value = {
            FILEOP_OTHER,
            FILEOP_COPY_INTRA_PROVIDER,
            FILEOP_COPY_SYSTEM_PROVIDER,
            FILEOP_COPY_EXTERNAL_PROVIDER,
            FILEOP_MOVE_INTRA_PROVIDER,
            FILEOP_MOVE_SYSTEM_PROVIDER,
            FILEOP_MOVE_EXTERNAL_PROVIDER,
            FILEOP_DELETE,
            FILEOP_RENAME,
            FILEOP_CREATE_DIR,
            FILEOP_OTHER_ERROR,
            FILEOP_DELETE_ERROR,
            FILEOP_MOVE_ERROR,
            FILEOP_COPY_ERROR,
            FILEOP_RENAME_ERROR,
            FILEOP_CREATE_DIR_ERROR,
            FILEOP_COMPRESS_INTRA_PROVIDER,
            FILEOP_COMPRESS_SYSTEM_PROVIDER,
            FILEOP_COMPRESS_EXTERNAL_PROVIDER,
            FILEOP_EXTRACT_INTRA_PROVIDER,
            FILEOP_EXTRACT_SYSTEM_PROVIDER,
            FILEOP_EXTRACT_EXTERNAL_PROVIDER,
            FILEOP_COMPRESS_ERROR,
            FILEOP_EXTRACT_ERROR
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FileOp {}

    // Codes representing different kinds of file operations. These are used for bucketing
    // operations in the COUNT_FILEOP_CANCELED histogram.
    // Do not change or rearrange these values, that will break historical data. Only add to the
    // list.
    // Do not use negative numbers or zero; clearcut only handles positive integers.
    private static final int OPERATION_UNKNOWN = 1;
    private static final int OPERATION_COPY = 2;
    private static final int OPERATION_MOVE = 3;
    private static final int OPERATION_DELETE = 4;
    private static final int OPERATION_COMPRESS = 5;
    private static final int OPERATION_EXTRACT = 6;

    @IntDef(flag = true, value = {
            OPERATION_UNKNOWN,
            OPERATION_COPY,
            OPERATION_MOVE,
            OPERATION_DELETE,
            OPERATION_COMPRESS,
            OPERATION_EXTRACT
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface MetricsOpType {}

    // Codes representing different provider types.  Used for sorting file operations when logging.
    private static final int PROVIDER_INTRA = 0;
    private static final int PROVIDER_SYSTEM = 1;
    private static final int PROVIDER_EXTERNAL = 2;

    @IntDef(flag = false, value = {
            PROVIDER_INTRA,
            PROVIDER_SYSTEM,
            PROVIDER_EXTERNAL
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Provider {}

    // Codes representing different types of sub-fileops. These are used for bucketing fileop
    // failures in COUNT_*_FILEOP_FAILURE.
    public static final int SUBFILEOP_QUERY_DOCUMENT = 1;
    public static final int SUBFILEOP_QUERY_CHILDREN = 2;
    public static final int SUBFILEOP_OPEN_FILE = 3;
    public static final int SUBFILEOP_READ_FILE = 4;
    public static final int SUBFILEOP_CREATE_DOCUMENT = 5;
    public static final int SUBFILEOP_WRITE_FILE = 6;
    public static final int SUBFILEOP_DELETE_DOCUMENT = 7;
    public static final int SUBFILEOP_OBTAIN_STREAM_TYPE = 8;
    public static final int SUBFILEOP_QUICK_MOVE = 9;
    public static final int SUBFILEOP_QUICK_COPY = 10;

    @IntDef(flag = false, value = {
            SUBFILEOP_QUERY_DOCUMENT,
            SUBFILEOP_QUERY_CHILDREN,
            SUBFILEOP_OPEN_FILE,
            SUBFILEOP_READ_FILE,
            SUBFILEOP_CREATE_DOCUMENT,
            SUBFILEOP_WRITE_FILE,
            SUBFILEOP_DELETE_DOCUMENT,
            SUBFILEOP_OBTAIN_STREAM_TYPE,
            SUBFILEOP_QUICK_MOVE,
            SUBFILEOP_QUICK_COPY
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SubFileOp {}

    // Codes representing different user actions. These are used for bucketing stats in the
    // COUNT_USER_ACTION histogram.
    // The historgram includes action triggered from menu or invoked by keyboard shortcut.
    // Do not change or rearrange these values, that will break historical data. Only add to the
    // list.
    // Do not use negative numbers or zero; clearcut only handles positive integers.
    public static final int USER_ACTION_OTHER = 1;
    public static final int USER_ACTION_GRID = 2;
    public static final int USER_ACTION_LIST = 3;
    public static final int USER_ACTION_SORT_NAME = 4;
    public static final int USER_ACTION_SORT_DATE = 5;
    public static final int USER_ACTION_SORT_SIZE = 6;
    public static final int USER_ACTION_SEARCH = 7;
    public static final int USER_ACTION_SHOW_SIZE = 8;
    public static final int USER_ACTION_HIDE_SIZE = 9;
    public static final int USER_ACTION_SETTINGS = 10;
    public static final int USER_ACTION_COPY_TO = 11;
    public static final int USER_ACTION_MOVE_TO = 12;
    public static final int USER_ACTION_DELETE = 13;
    public static final int USER_ACTION_RENAME = 14;
    public static final int USER_ACTION_CREATE_DIR = 15;
    public static final int USER_ACTION_SELECT_ALL = 16;
    public static final int USER_ACTION_SHARE = 17;
    public static final int USER_ACTION_OPEN = 18;
    public static final int USER_ACTION_SHOW_ADVANCED = 19;
    public static final int USER_ACTION_HIDE_ADVANCED = 20;
    public static final int USER_ACTION_NEW_WINDOW = 21;
    public static final int USER_ACTION_PASTE_CLIPBOARD = 22;
    public static final int USER_ACTION_COPY_CLIPBOARD = 23;
    public static final int USER_ACTION_DRAG_N_DROP = 24;
    public static final int USER_ACTION_DRAG_N_DROP_MULTI_WINDOW = 25;
    public static final int USER_ACTION_CUT_CLIPBOARD = 26;
    public static final int USER_ACTION_COMPRESS = 27;
    public static final int USER_ACTION_EXTRACT_TO = 28;
    public static final int USER_ACTION_VIEW_IN_APPLICATION = 29;
    public static final int USER_ACTION_INSPECTOR = 30;

    @IntDef(flag = false, value = {
            USER_ACTION_OTHER,
            USER_ACTION_GRID,
            USER_ACTION_LIST,
            USER_ACTION_SORT_NAME,
            USER_ACTION_SORT_DATE,
            USER_ACTION_SORT_SIZE,
            USER_ACTION_SEARCH,
            USER_ACTION_SHOW_SIZE,
            USER_ACTION_HIDE_SIZE,
            USER_ACTION_SETTINGS,
            USER_ACTION_COPY_TO,
            USER_ACTION_MOVE_TO,
            USER_ACTION_DELETE,
            USER_ACTION_RENAME,
            USER_ACTION_CREATE_DIR,
            USER_ACTION_SELECT_ALL,
            USER_ACTION_SHARE,
            USER_ACTION_OPEN,
            USER_ACTION_SHOW_ADVANCED,
            USER_ACTION_HIDE_ADVANCED,
            USER_ACTION_NEW_WINDOW,
            USER_ACTION_PASTE_CLIPBOARD,
            USER_ACTION_COPY_CLIPBOARD,
            USER_ACTION_DRAG_N_DROP,
            USER_ACTION_DRAG_N_DROP_MULTI_WINDOW,
            USER_ACTION_CUT_CLIPBOARD,
            USER_ACTION_COMPRESS,
            USER_ACTION_EXTRACT_TO,
            USER_ACTION_VIEW_IN_APPLICATION,
            USER_ACTION_INSPECTOR
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface UserAction {}

    // Codes representing different approaches to copy/move a document. OPMODE_PROVIDER indicates
    // it's an optimized operation provided by providers; OPMODE_CONVERTED means it's converted from
    // a virtual file; and OPMODE_CONVENTIONAL means it's byte copied.
    public static final int OPMODE_PROVIDER = 1;
    public static final int OPMODE_CONVERTED = 2;
    public static final int OPMODE_CONVENTIONAL = 3;
    @IntDef({OPMODE_PROVIDER, OPMODE_CONVERTED, OPMODE_CONVENTIONAL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FileOpMode {}

    // Codes representing different menu actions. These are used for bucketing stats in the
    // COUNT_MENU_ACTION histogram.
    // Do not change or rearrange these values, that will break historical data. Only add to the
    // list.
    // Do not use negative numbers or zero; clearcut only handles positive integers.
    private static final int ACTION_OTHER = 1;
    private static final int ACTION_OPEN = 2;
    private static final int ACTION_CREATE = 3;
    private static final int ACTION_GET_CONTENT = 4;
    private static final int ACTION_OPEN_TREE = 5;
    @Deprecated private static final int ACTION_MANAGE = 6;
    private static final int ACTION_BROWSE = 7;
    private static final int ACTION_PICK_COPY_DESTINATION = 8;

    @IntDef(flag = true, value = {
            ACTION_OTHER,
            ACTION_OPEN,
            ACTION_CREATE,
            ACTION_GET_CONTENT,
            ACTION_OPEN_TREE,
            ACTION_MANAGE,
            ACTION_BROWSE,
            ACTION_PICK_COPY_DESTINATION
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface MetricsAction {}

    // Codes representing different actions to open the drawer. They are used for bucketing stats in
    // the COUNT_DRAWER_OPENED histogram.
    // Do not change or rearrange these values, that will break historical data. Only add to the
    // list.
    // Do not use negative numbers or zero; clearcut only handles positive integers.
    private static final int DRAWER_OPENED_HAMBURGER = 1;
    private static final int DRAWER_OPENED_SWIPE = 2;

    @IntDef(flag = true, value = {
            DRAWER_OPENED_HAMBURGER,
            DRAWER_OPENED_SWIPE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface DrawerTrigger {}

    /**
     * Logs when DocumentsUI is started, and how. Call this when DocumentsUI first starts up.
     *
     * @param context
     * @param state
     * @param intent
     */
    public static void logActivityLaunch(Context context, State state, Intent intent) {
        // Log the launch action.
        logHistogram(context, COUNT_LAUNCH_ACTION, toMetricsAction(state.action));
        // Then log auxiliary data (roots/mime types) associated with some actions.
        Uri uri = intent.getData();
        switch (state.action) {
            case State.ACTION_OPEN:
                logHistogram(context, COUNT_OPEN_MIME, sanitizeMime(intent.getType()));
                break;
            case State.ACTION_CREATE:
                logHistogram(context, COUNT_CREATE_MIME, sanitizeMime(intent.getType()));
                break;
            case State.ACTION_GET_CONTENT:
                logHistogram(context, COUNT_GET_CONTENT_MIME, sanitizeMime(intent.getType()));
                break;
            case State.ACTION_BROWSE:
                logHistogram(context, COUNT_BROWSE_ROOT, sanitizeRoot(uri));
                break;
            default:
                break;
        }
    }

    /**
     * Logs when DocumentsUI are launched with {@link DocumentsContract#EXTRA_INITIAL_URI}.
     *
     * @param context
     * @param state used to resolve action
     * @param rootUri the resolved rootUri, or {@code null} if the provider doesn't
     *                support {@link DocumentsProvider#findDocumentPath(String, String)}
     */
    public static void logLaunchAtLocation(Context context, State state, @Nullable Uri rootUri) {
        switch (state.action) {
            case State.ACTION_BROWSE:
                logHistogram(context, COUNT_BROWSE_AT_LOCATION, sanitizeRoot(rootUri));
                break;
            case State.ACTION_CREATE:
                logHistogram(context, COUNT_CREATE_AT_LOCATION, sanitizeRoot(rootUri));
                break;
            case State.ACTION_GET_CONTENT:
                logHistogram(context, COUNT_GET_CONTENT_AT_LOCATION, sanitizeRoot(rootUri));
                break;
            case State.ACTION_OPEN:
                logHistogram(context, COUNT_OPEN_AT_LOCATION, sanitizeRoot(rootUri));
                break;
        }
    }

    /**
     * Logs a root visited event in file managers. Call this when the user
     * taps on a root in {@link com.android.documentsui.sidebar.RootsFragment}.
     *
     * @param context
     * @param scope
     * @param info
     */
    public static void logRootVisited(
            Context context, @ContextScope int scope, RootInfo info) {
        switch (scope) {
            case FILES_SCOPE:
                logHistogram(context, COUNT_ROOT_VISITED_IN_MANAGER,
                        sanitizeRoot(info));
                break;
            case PICKER_SCOPE:
                logHistogram(context, COUNT_ROOT_VISITED_IN_PICKER,
                        sanitizeRoot(info));
                break;
        }
    }

    /**
     * Logs an app visited event in file pickers. Call this when the user visits
     * on an app in the RootsFragment.
     *
     * @param context
     * @param info
     */
    public static void logAppVisited(Context context, ResolveInfo info) {
        logHistogram(context, COUNT_ROOT_VISITED_IN_PICKER, sanitizeRoot(info));
    }

    /**
     * Logs file operation stats. Call this when a file operation has completed. The given
     * DocumentInfo is only used to distinguish broad categories of actions (e.g. copying from one
     * provider to another vs copying within a given provider).  No PII is logged.
     *
     * @param context
     * @param operationType
     * @param srcs
     * @param dst
     */
    public static void logFileOperation(
            Context context,
            @OpType int operationType,
            List<DocumentInfo> srcs,
            @Nullable DocumentInfo dst) {

        ProviderCounts counts = new ProviderCounts();
        countProviders(counts, srcs, dst);

        if (counts.intraProvider > 0) {
            logIntraProviderFileOps(context, dst.authority, operationType);
        }
        if (counts.systemProvider > 0) {
            // Log file operations on system providers.
            logInterProviderFileOps(context, COUNT_FILEOP_SYSTEM, dst, operationType);
        }
        if (counts.externalProvider > 0) {
            // Log file operations on external providers.
            logInterProviderFileOps(context, COUNT_FILEOP_EXTERNAL, dst, operationType);
        }
    }

    public static void logFileOperated(
            Context context, @OpType int operationType, @FileOpMode int approach) {
        switch (operationType) {
            case FileOperationService.OPERATION_COPY:
                logHistogram(context, COUNT_FILE_COPIED, approach);
                break;
            case FileOperationService.OPERATION_MOVE:
                logHistogram(context, COUNT_FILE_MOVED, approach);
                break;
        }
    }

    /**
     * Logs create directory operation. It is a part of file operation stats. We do not
     * differentiate between internal and external locations, all create directory operations are
     * logged under COUNT_FILEOP_SYSTEM. Call this when a create directory operation has completed.
     *
     * @param context
     */
    public static void logCreateDirOperation(Context context) {
        logHistogram(context, COUNT_FILEOP_SYSTEM, FILEOP_CREATE_DIR);
    }

    /**
     * Logs rename file operation. It is a part of file operation stats. We do not differentiate
     * between internal and external locations, all rename operations are logged under
     * COUNT_FILEOP_SYSTEM. Call this when a rename file operation has completed.
     *
     * @param context
     */
    public static void logRenameFileOperation(Context context) {
        logHistogram(context, COUNT_FILEOP_SYSTEM, FILEOP_RENAME);
    }

    /**
     * Logs some kind of file operation error. Call this when a file operation (e.g. copy, delete)
     * fails.
     *
     * @param context
     * @param operationType
     * @param failedFiles
     */
    public static void logFileOperationErrors(Context context, @OpType int operationType,
            List<DocumentInfo> failedFiles, List<Uri> failedUris) {

        ProviderCounts counts = new ProviderCounts();
        countProviders(counts, failedFiles, null);

        // TODO: Report URI errors separate from file operation errors.
        countProviders(counts, failedUris);

        @FileOp int opCode = FILEOP_OTHER_ERROR;
        switch (operationType) {
            case FileOperationService.OPERATION_COPY:
                opCode = FILEOP_COPY_ERROR;
                break;
            case FileOperationService.OPERATION_COMPRESS:
                opCode = FILEOP_COMPRESS_ERROR;
                break;
            case FileOperationService.OPERATION_EXTRACT:
                opCode = FILEOP_EXTRACT_ERROR;
                break;
            case FileOperationService.OPERATION_DELETE:
                opCode = FILEOP_DELETE_ERROR;
                break;
            case FileOperationService.OPERATION_MOVE:
                opCode = FILEOP_MOVE_ERROR;
                break;
        }
        if (counts.systemProvider > 0) {
            logHistogram(context, COUNT_FILEOP_SYSTEM, opCode);
        }
        if (counts.externalProvider > 0) {
            logHistogram(context, COUNT_FILEOP_EXTERNAL, opCode);
        }
    }

    public static void logFileOperationFailure(
            Context context, @SubFileOp int subFileOp, Uri docUri) {
        final String authority = docUri.getAuthority();
        switch (authority) {
            case Providers.AUTHORITY_MEDIA:
                logHistogram(context, COUNT_MEDIA_FILEOP_FAILURE, subFileOp);
                break;
            case Providers.AUTHORITY_STORAGE:
                logStorageFileOperationFailure(context, subFileOp, docUri);
                break;
            case Providers.AUTHORITY_DOWNLOADS:
                logHistogram(context, COUNT_DOWNLOADS_FILEOP_FAILURE, subFileOp);
                break;
            case Providers.AUTHORITY_MTP:
                logHistogram(context, COUNT_MTP_FILEOP_FAILURE, subFileOp);
                break;
            default:
                logHistogram(context, COUNT_OTHER_FILEOP_FAILURE, subFileOp);
                break;
        }
    }

    /**
     * Logs create directory operation error. We do not differentiate between internal and external
     * locations, all create directory errors are logged under COUNT_FILEOP_SYSTEM. Call this when a
     * create directory operation fails.
     *
     * @param context
     */
    public static void logCreateDirError(Context context) {
        logHistogram(context, COUNT_FILEOP_SYSTEM, FILEOP_CREATE_DIR_ERROR);
    }

    /**
     * Logs rename file operation error. We do not differentiate between internal and external
     * locations, all rename errors are logged under COUNT_FILEOP_SYSTEM. Call this
     * when a rename file operation fails.
     *
     * @param context
     */
    public static void logRenameFileError(Context context) {
        logHistogram(context, COUNT_FILEOP_SYSTEM, FILEOP_RENAME_ERROR);
    }

    /**
     * Logs the cancellation of a file operation.  Call this when a Job is canceled.
     * @param context
     * @param operationType
     */
    public static void logFileOperationCancelled(Context context, @OpType int operationType) {
        logHistogram(context, COUNT_FILEOP_CANCELED, toMetricsOpType(operationType));
    }

    /**
     * Logs startup time in milliseconds.
     * @param context
     * @param startupMs Startup time in milliseconds.
     */
    public static void logStartupMs(Context context, int startupMs) {
        logHistogram(context, COUNT_STARTUP_MS, startupMs);
    }

    private static void logInterProviderFileOps(
            Context context,
            String histogram,
            DocumentInfo dst,
            @OpType int operationType) {
        if (operationType == FileOperationService.OPERATION_DELETE) {
            logHistogram(context, histogram, FILEOP_DELETE);
        } else {
            assert(dst != null);
            @Provider int providerType =
                    isSystemProvider(dst.authority) ? PROVIDER_SYSTEM : PROVIDER_EXTERNAL;
            logHistogram(context, histogram, getOpCode(operationType, providerType));
        }
    }

    private static void logIntraProviderFileOps(
            Context context, String authority, @OpType int operationType) {
        // Find the right histogram to log to, then log the operation.
        String histogram = isSystemProvider(authority) ? COUNT_FILEOP_SYSTEM : COUNT_FILEOP_EXTERNAL;
        logHistogram(context, histogram, getOpCode(operationType, PROVIDER_INTRA));
    }

    // Types for logInvalidScopedAccessRequest
    public static final String SCOPED_DIRECTORY_ACCESS_INVALID_ARGUMENTS =
            "docsui_scoped_directory_access_invalid_args";
    public static final String SCOPED_DIRECTORY_ACCESS_INVALID_DIRECTORY =
            "docsui_scoped_directory_access_invalid_dir";
    public static final String SCOPED_DIRECTORY_ACCESS_ERROR =
            "docsui_scoped_directory_access_error";

    @StringDef(value = {
            SCOPED_DIRECTORY_ACCESS_INVALID_ARGUMENTS,
            SCOPED_DIRECTORY_ACCESS_INVALID_DIRECTORY,
            SCOPED_DIRECTORY_ACCESS_ERROR
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface InvalidScopedAccess{}

    public static void logInvalidScopedAccessRequest(Context context,
            @InvalidScopedAccess String type) {
        switch (type) {
            case SCOPED_DIRECTORY_ACCESS_INVALID_ARGUMENTS:
            case SCOPED_DIRECTORY_ACCESS_INVALID_DIRECTORY:
            case SCOPED_DIRECTORY_ACCESS_ERROR:
                logCount(context, type);
                break;
            default:
                Log.wtf(TAG, "invalid InvalidScopedAccess: " + type);
        }
    }

    // Types for logValidScopedAccessRequest
    public static final int SCOPED_DIRECTORY_ACCESS_ALREADY_GRANTED = 0;
    public static final int SCOPED_DIRECTORY_ACCESS_GRANTED = 1;
    public static final int SCOPED_DIRECTORY_ACCESS_DENIED = 2;
    public static final int SCOPED_DIRECTORY_ACCESS_DENIED_AND_PERSIST = 3;
    public static final int SCOPED_DIRECTORY_ACCESS_ALREADY_DENIED = 4;

    @IntDef(flag = true, value = {
            SCOPED_DIRECTORY_ACCESS_ALREADY_GRANTED,
            SCOPED_DIRECTORY_ACCESS_GRANTED,
            SCOPED_DIRECTORY_ACCESS_DENIED,
            SCOPED_DIRECTORY_ACCESS_DENIED_AND_PERSIST,
            SCOPED_DIRECTORY_ACCESS_ALREADY_DENIED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ScopedAccessGrant {}

    public static void logValidScopedAccessRequest(Activity activity, String directory,
            @ScopedAccessGrant int type) {
        int index = -1;
        if (OpenExternalDirectoryActivity.DIRECTORY_ROOT.equals(directory)) {
            index = -2;
        } else {
            for (int i = 0; i < STANDARD_DIRECTORIES.length; i++) {
                if (STANDARD_DIRECTORIES[i].equals(directory)) {
                    index = i;
                    break;
                }
            }
        }
        final String packageName = activity.getCallingPackage();
        switch (type) {
            case SCOPED_DIRECTORY_ACCESS_ALREADY_GRANTED:
                MetricsLogger.action(activity, MetricsEvent
                        .ACTION_SCOPED_DIRECTORY_ACCESS_ALREADY_GRANTED_BY_PACKAGE, packageName);
                MetricsLogger.action(activity, MetricsEvent
                        .ACTION_SCOPED_DIRECTORY_ACCESS_ALREADY_GRANTED_BY_FOLDER, index);
                break;
            case SCOPED_DIRECTORY_ACCESS_GRANTED:
                MetricsLogger.action(activity, MetricsEvent
                        .ACTION_SCOPED_DIRECTORY_ACCESS_GRANTED_BY_PACKAGE, packageName);
                MetricsLogger.action(activity, MetricsEvent
                        .ACTION_SCOPED_DIRECTORY_ACCESS_GRANTED_BY_FOLDER, index);
                break;
            case SCOPED_DIRECTORY_ACCESS_DENIED:
                MetricsLogger.action(activity, MetricsEvent
                        .ACTION_SCOPED_DIRECTORY_ACCESS_DENIED_BY_PACKAGE, packageName);
                MetricsLogger.action(activity, MetricsEvent
                        .ACTION_SCOPED_DIRECTORY_ACCESS_DENIED_BY_FOLDER, index);
                break;
            case SCOPED_DIRECTORY_ACCESS_DENIED_AND_PERSIST:
                MetricsLogger.action(activity, MetricsEvent
                        .ACTION_SCOPED_DIRECTORY_ACCESS_DENIED_AND_PERSIST_BY_PACKAGE, packageName);
                MetricsLogger.action(activity, MetricsEvent
                        .ACTION_SCOPED_DIRECTORY_ACCESS_DENIED_AND_PERSIST_BY_FOLDER, index);
                break;
            case SCOPED_DIRECTORY_ACCESS_ALREADY_DENIED:
                MetricsLogger.action(activity, MetricsEvent
                        .ACTION_SCOPED_DIRECTORY_ACCESS_ALREADY_DENIED_BY_PACKAGE, packageName);
                MetricsLogger.action(activity, MetricsEvent
                        .ACTION_SCOPED_DIRECTORY_ACCESS_ALREADY_DENIED_BY_FOLDER, index);
                break;
            default:
                Log.wtf(TAG, "invalid ScopedAccessGrant: " + type);
        }
    }

    /**
     * Logs the action that was started by user.
     * @param context
     * @param userAction
     */
    public static void logUserAction(Context context, @UserAction int userAction) {
        logHistogram(context, COUNT_USER_ACTION, userAction);
    }

    private static void logStorageFileOperationFailure(
            Context context, @SubFileOp int subFileOp, Uri docUri) {
        assert(Providers.AUTHORITY_STORAGE.equals(docUri.getAuthority()));

        boolean isInternal;
        try (ContentProviderClient client = acquireUnstableProviderOrThrow(
                context.getContentResolver(), Providers.AUTHORITY_STORAGE)) {
            final Path path = DocumentsContract.findDocumentPath(client, docUri);

            final ProvidersAccess providers = DocumentsApplication.getProvidersCache(context);
            final RootInfo root = providers.getRootOneshot(
                    Providers.AUTHORITY_STORAGE, path.getRootId());
            isInternal = !root.supportsEject();
        } catch (RemoteException | RuntimeException e) {
            Log.e(TAG, "Failed to obtain its root info. Log the metrics as internal.", e);
            // It's not very likely to have an external storage so log it as internal.
            isInternal = true;
        }

        final String histogram = isInternal
                ? COUNT_INTERNAL_STORAGE_FILEOP_FAILURE
                : COUNT_EXTERNAL_STORAGE_FILEOP_FAILURE;
        logHistogram(context, histogram, subFileOp);
    }

    /**
     * Internal method for making a MetricsLogger.count call. Increments the given counter by 1.
     *
     * @param context
     * @param name The counter to increment.
     */
    private static void logCount(Context context, String name) {
        if (DEBUG) Log.d(TAG, name + ": " + 1);
        MetricsLogger.count(context, name, 1);
    }

    /**
     * Internal method for making a MetricsLogger.histogram call.
     *
     * @param context
     * @param name The name of the histogram.
     * @param bucket The bucket to increment.
     */
    private static void logHistogram(Context context, String name, @ActionType int bucket) {
        if (DEBUG) Log.d(TAG, name + ": " + bucket);
        MetricsLogger.histogram(context, name, bucket);
    }

    /**
     * Generates an integer identifying the given root. For privacy, this function only recognizes a
     * small set of hard-coded roots (ones provided by the system). Other roots are all grouped into
     * a single ROOT_OTHER bucket.
     */
    private static @Root int sanitizeRoot(Uri uri) {
        if (uri == null || uri.getAuthority() == null || LauncherActivity.isLaunchUri(uri)) {
            return ROOT_NONE;
        }

        switch (uri.getAuthority()) {
            case Providers.AUTHORITY_MEDIA:
                switch (DocumentsContract.getRootId(uri)) {
                    case Providers.ROOT_ID_AUDIO:
                        return ROOT_AUDIO;
                    case Providers.ROOT_ID_IMAGES:
                        return ROOT_IMAGES;
                    case Providers.ROOT_ID_VIDEOS:
                        return ROOT_VIDEOS;
                    default:
                        return ROOT_OTHER;
                }
            case Providers.AUTHORITY_STORAGE:
                if (Providers.ROOT_ID_HOME.equals(DocumentsContract.getRootId(uri))) {
                    return ROOT_HOME;
                } else {
                    return ROOT_DEVICE_STORAGE;
                }
            case Providers.AUTHORITY_DOWNLOADS:
                return ROOT_DOWNLOADS;
            case Providers.AUTHORITY_MTP:
                return ROOT_MTP;
            default:
                return ROOT_OTHER;
        }
    }

    /** @see #sanitizeRoot(Uri) */
    private static @Root int sanitizeRoot(RootInfo root) {
        if (root.isRecents()) {
            // Recents root is special and only identifiable via this method call. Other roots are
            // identified by URI.
            return ROOT_RECENTS;
        } else {
            return sanitizeRoot(root.getUri());
        }
    }

    /** @see #sanitizeRoot(Uri) */
    private static @Root int sanitizeRoot(ResolveInfo info) {
        // Log all apps under a single bucket in the roots histogram.
        return ROOT_THIRD_PARTY_APP;
    }

    /**
     * Generates an int identifying a mime type. For privacy, this function only recognizes a small
     * set of hard-coded types. For any other type, this function returns "other".
     *
     * @param mimeType
     * @return
     */
    private static @Mime int sanitizeMime(String mimeType) {
        if (mimeType == null) {
            return MIME_NONE;
        } else if ("*/*".equals(mimeType)) {
            return MIME_ANY;
        } else {
            String type = mimeType.substring(0, mimeType.indexOf('/'));
            switch (type) {
                case "application":
                    return MIME_APPLICATION;
                case "audio":
                    return MIME_AUDIO;
                case "image":
                    return MIME_IMAGE;
                case "message":
                    return MIME_MESSAGE;
                case "multipart":
                    return MIME_MULTIPART;
                case "text":
                    return MIME_TEXT;
                case "video":
                    return MIME_VIDEO;
            }
        }
        // Bucket all other types into one bucket.
        return MIME_OTHER;
    }

    private static boolean isSystemProvider(String authority) {
        switch (authority) {
            case Providers.AUTHORITY_MEDIA:
            case Providers.AUTHORITY_STORAGE:
            case Providers.AUTHORITY_DOWNLOADS:
                return true;
            default:
                return false;
        }
    }

    /**
     * @param operation
     * @param providerType
     * @return An opcode, suitable for use as histogram bucket, for the given operation/provider
     *         combination.
     */
    private static @FileOp int getOpCode(@OpType int operation, @Provider int providerType) {
        switch (operation) {
            case FileOperationService.OPERATION_COPY:
                switch (providerType) {
                    case PROVIDER_INTRA:
                        return FILEOP_COPY_INTRA_PROVIDER;
                    case PROVIDER_SYSTEM:
                        return FILEOP_COPY_SYSTEM_PROVIDER;
                    case PROVIDER_EXTERNAL:
                        return FILEOP_COPY_EXTERNAL_PROVIDER;
                }
            case FileOperationService.OPERATION_COMPRESS:
                switch (providerType) {
                    case PROVIDER_INTRA:
                        return FILEOP_COMPRESS_INTRA_PROVIDER;
                    case PROVIDER_SYSTEM:
                        return FILEOP_COMPRESS_SYSTEM_PROVIDER;
                    case PROVIDER_EXTERNAL:
                        return FILEOP_COMPRESS_EXTERNAL_PROVIDER;
                }
             case FileOperationService.OPERATION_EXTRACT:
                switch (providerType) {
                    case PROVIDER_INTRA:
                        return FILEOP_EXTRACT_INTRA_PROVIDER;
                    case PROVIDER_SYSTEM:
                        return FILEOP_EXTRACT_SYSTEM_PROVIDER;
                    case PROVIDER_EXTERNAL:
                        return FILEOP_EXTRACT_EXTERNAL_PROVIDER;
                }
            case FileOperationService.OPERATION_MOVE:
                switch (providerType) {
                    case PROVIDER_INTRA:
                        return FILEOP_MOVE_INTRA_PROVIDER;
                    case PROVIDER_SYSTEM:
                        return FILEOP_MOVE_SYSTEM_PROVIDER;
                    case PROVIDER_EXTERNAL:
                        return FILEOP_MOVE_EXTERNAL_PROVIDER;
                }
            case FileOperationService.OPERATION_DELETE:
                return FILEOP_DELETE;
            default:
                Log.w(TAG, "Unrecognized operation type when logging a file operation");
                return FILEOP_OTHER;
        }
    }

    /**
     * Maps FileOperationService OpType values, to MetricsOpType values.
     */
    private static @MetricsOpType int toMetricsOpType(@OpType int operation) {
        switch (operation) {
            case FileOperationService.OPERATION_COPY:
                return OPERATION_COPY;
            case FileOperationService.OPERATION_MOVE:
                return OPERATION_MOVE;
            case FileOperationService.OPERATION_DELETE:
                return OPERATION_DELETE;
            case FileOperationService.OPERATION_UNKNOWN:
            default:
                return OPERATION_UNKNOWN;
        }
    }

    private static @MetricsAction int toMetricsAction(int action) {
        switch(action) {
            case State.ACTION_OPEN:
                return ACTION_OPEN;
            case State.ACTION_CREATE:
                return ACTION_CREATE;
            case State.ACTION_GET_CONTENT:
                return ACTION_GET_CONTENT;
            case State.ACTION_OPEN_TREE:
                return ACTION_OPEN_TREE;
            case State.ACTION_BROWSE:
                return ACTION_BROWSE;
            case State.ACTION_PICK_COPY_DESTINATION:
                return ACTION_PICK_COPY_DESTINATION;
            default:
                return ACTION_OTHER;
        }
    }

    /**
     * Count the given src documents and provide a tally of how many come from the same provider as
     * the dst document (if a dst is provided), how many come from system providers, and how many
     * come from external 3rd-party providers.
     */
    private static void countProviders(
            ProviderCounts counts, List<DocumentInfo> srcs, @Nullable DocumentInfo dst) {
        for (DocumentInfo doc: srcs) {
            countForAuthority(counts, doc.authority, dst);
        }
    }

    /**
     * Count the given uris and provide a tally of how many come from the same provider as
     * the dst document (if a dst is provided), how many come from system providers, and how many
     * come from external 3rd-party providers.
     */
    private static void countProviders(ProviderCounts counts, List<Uri> uris) {
        for (Uri uri: uris) {
            countForAuthority(counts, uri.getAuthority(), null);
        }
    }

    private static void countForAuthority(
            ProviderCounts counts, String authority, @Nullable DocumentInfo dst) {
        if (dst != null && authority.equals(dst.authority)) {
            counts.intraProvider++;
        } else if (isSystemProvider(authority)){
            counts.systemProvider++;
        } else {
            counts.externalProvider++;
        }
    }

    private static class ProviderCounts {
        int intraProvider;
        int systemProvider;
        int externalProvider;
    }
}
