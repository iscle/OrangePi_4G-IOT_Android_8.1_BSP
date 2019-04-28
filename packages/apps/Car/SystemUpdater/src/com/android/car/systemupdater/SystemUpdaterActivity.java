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
package com.android.car.systemupdater;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.RecoverySystem;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.List;

import android.os.storage.StorageEventListener;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;

/**
 * A prototype of performing system update using an ota package on internal or external storage.
 * TODO(yaochen): Move the code to a proper location and let it extend CarActivity once available.
 */
public class SystemUpdaterActivity extends Activity {
    private static final String TAG = "SystemUpdaterActivity";
    private static final boolean DEBUG = true;
    private static final String UPDATE_FILE_NAME = "update.zip";

    private final Handler mHandler = new Handler();
    private StorageManager mStorageManager = null;
    private ProgressDialog mVerifyPackageDialog = null;


    private final StorageEventListener mListener = new StorageEventListener() {
        @Override
        public void onVolumeStateChanged(VolumeInfo vol, int oldState, int newState) {
            if (DEBUG) {
                Log.d(TAG, "onVolumeMetadataChanged " + oldState + " " + newState
                        + " " + vol.toString());
            }
            showMountedVolumes();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mStorageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
        if (mStorageManager == null) {
            Log.w(TAG, "Failed to get StorageManager");
            Toast.makeText(this, "Cannot get StorageManager!", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mStorageManager != null) {
            mStorageManager.registerListener(mListener);
            showMountedVolumes();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mStorageManager != null) {
            mStorageManager.unregisterListener(mListener);
        }
    }

    @Override
    public void onBackPressed() {
        if (getFragmentManager().getBackStackEntryCount() > 0) {
            getFragmentManager().popBackStackImmediate();
        } else {
            super.onBackPressed();
        }
    }

    public void showMountedVolumes() {
        if (mStorageManager == null) {
            return;
        }
        final List<VolumeInfo> vols = mStorageManager.getVolumes();
        File[] files = new File[vols.size()];
        int i = 0;
        for (VolumeInfo vol : vols) {
            File path = vol.getPathForUser(getUserId());
            if (vol.getState() != VolumeInfo.STATE_MOUNTED || path == null) {
                continue;
            }
            files[i++] = path;
        }
        getFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        DeviceListFragment frag = new DeviceListFragment();
        frag.updateList(files);
        frag.updateTitle(getString(R.string.title));
        getFragmentManager().beginTransaction()
                .replace(R.id.device_container, frag).commit();
    }

    public void showFolderContent(final File location) {
        if (!location.isDirectory()) {
            return;
        }
         AsyncTask<String, Void, File[]> readFilesTask = new AsyncTask<String, Void, File[]>() {
            @Override
            protected File[] doInBackground(String... strings) {
                File f = new File(strings[0]);
                /* if we want to filter files, use
                File[] files = f.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String filename) {
                        return true;
                    }
                }); */
                return f.listFiles();
            }

            @Override
            protected void onPostExecute(File[] results) {
                super.onPostExecute(results);
                if (results == null) {
                    results = new File[0];
                }
                DeviceListFragment frag = new DeviceListFragment();
                frag.updateTitle(location.getAbsolutePath());
                frag.updateList(results);
                getFragmentManager().beginTransaction()
                        .replace(R.id.device_container, frag).addToBackStack(null).commit();
            }
        };
        readFilesTask.execute(location.getAbsolutePath());
    }

    public void checkPackage(File file) {
        mVerifyPackageDialog = new ProgressDialog(this);
        mVerifyPackageDialog.setTitle("Verifying... " + file.getAbsolutePath());

        final PackageVerifier verifyPackage = new PackageVerifier();
        verifyPackage.execute(file);
        mVerifyPackageDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                verifyPackage.cancel(true);
            }
        });
        mVerifyPackageDialog.setProgressStyle(mVerifyPackageDialog.STYLE_HORIZONTAL);
        mVerifyPackageDialog.setMax(100);
        mVerifyPackageDialog.setProgress(0);
        mVerifyPackageDialog.show();
    }

    private class PackageVerifier extends AsyncTask<File, Void, Exception> {
        File mFile;

        @Override
        protected Exception doInBackground(File... files) {
            File file = files[0];
            mFile = file;
            try {
                RecoverySystem.verifyPackage(file, mProgressListener, null);
            } catch (GeneralSecurityException e) {
                Log.e(TAG, "Security Exception in verifying package " + file, e);
                return e;
            } catch (IOException e) {
                Log.e(TAG, "IO Exception in verifying package " + file, e);
                return e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception result) {
            mVerifyPackageDialog.cancel();
            if (result == null) {
                mVerifyPackageDialog = new ProgressDialog(SystemUpdaterActivity.this);
                mVerifyPackageDialog.setTitle("Copying " + mFile.getName()
                        + " to " + getCacheDir() + "/" + UPDATE_FILE_NAME);
                mVerifyPackageDialog.setProgressStyle(mVerifyPackageDialog.STYLE_HORIZONTAL);
                mVerifyPackageDialog.setMax((int) (mFile.length() / 1024));
                mVerifyPackageDialog.show();
                new CopyFile().execute(mFile);
            } else {
                AlertDialog.Builder doneDialog =
                        new AlertDialog.Builder(SystemUpdaterActivity.this);
                doneDialog.setMessage("Verification failed! " + result.getMessage()).show();
            }
        }
    }


    private class CopyFile extends AsyncTask<File, Void, Exception> {
        @Override
        protected Exception doInBackground(File... files) {
            File file = files[0];
            if (getCacheDir().getFreeSpace() < file.length()) {
                return new IOException("Not enough cache space!");
            }
            File dest = new File(getCacheDir(), UPDATE_FILE_NAME);
            try {
              copy(file, dest);
            } catch (IOException e) {
                Log.e(TAG, "Error when coping file to cache", e);
                dest.delete();
                return new IOException(e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception result) {
            mVerifyPackageDialog.cancel();
            AlertDialog.Builder doneDialog = new AlertDialog.Builder(SystemUpdaterActivity.this);

            doneDialog.setMessage("Copy " + (result == null ? "completed!" : "failed!"
                    + result.getMessage()));

            if (result == null) {
                doneDialog.setPositiveButton("Start system update",
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        try {
                            RecoverySystem.installPackage(SystemUpdaterActivity.this,
                                    new File(getCacheDir(), UPDATE_FILE_NAME));
                        } catch (IOException e) {
                            Log.e(TAG, "IOException in installing ota package");
                            Toast.makeText(SystemUpdaterActivity.this,
                                    "IOException in installing ota package ",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            } else {
                Log.e(TAG, "Copy failed!", result);
            }
            doneDialog.create().show();
        }
    }

    private void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);
        try {
            // Transfer bytes from in to out
            byte[] buf = new byte[0x10000]; // 64k
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mVerifyPackageDialog.incrementProgressBy(1);
                    }
                });
            }
        } finally {
            in.close();
            out.close();
        }
    }

    private final RecoverySystem.ProgressListener mProgressListener =
            new RecoverySystem.ProgressListener() {
        @Override
        public void onProgress(final int i) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mVerifyPackageDialog != null) {
                        mVerifyPackageDialog.setProgress(i);
                    }
                }
            });
        }
    };
}
