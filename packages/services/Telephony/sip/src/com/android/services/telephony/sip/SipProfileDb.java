/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.services.telephony.sip;

import com.android.internal.os.AtomicFile;

import android.content.Context;
import android.net.sip.SipProfile;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility class that helps perform operations on the SipProfile database.
 */
class SipProfileDb {
    private static final String PREFIX = "[SipProfileDb] ";
    private static final boolean VERBOSE = false; /* STOP SHIP if true */

    private static final String PROFILES_DIR = "/profiles/";
    private static final String PROFILE_OBJ_FILE = ".pobj";

    private static final String SCHEME_PREFIX = "sip:";

    private Context mContext;
    private String mProfilesDirectory;
    private SipPreferences mSipPreferences;
    private int mProfilesCount = -1;

    public SipProfileDb(Context context) {
        // Sip Profile Db should always reference CE storage.
        mContext = context.createCredentialProtectedStorageContext();
        setupDatabase();
    }

    // Only should be used during migration from M->N to move database
    public void accessDEStorageForMigration() {
        mContext = mContext.createDeviceProtectedStorageContext();
        setupDatabase();
    }

    private void setupDatabase() {
        mProfilesDirectory = mContext.getFilesDir().getAbsolutePath() + PROFILES_DIR;
        mSipPreferences = new SipPreferences(mContext);
    }

    public void deleteProfile(SipProfile p) throws IOException {
        synchronized(SipProfileDb.class) {
            File profileFile = new File(mProfilesDirectory, p.getProfileName());
            if (!isChild(new File(mProfilesDirectory), profileFile)) {
                throw new IOException("Invalid Profile Credentials!");
            }
            deleteProfile(profileFile);
            if (mProfilesCount < 0) retrieveSipProfileListInternal();
        }
    }

    private void deleteProfile(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) deleteProfile(child);
        }
        file.delete();
    }

    public void cleanupUponMigration() {
        // Remove empty .../profiles/ directory
        File dbDir = new File(mProfilesDirectory);
        if(dbDir.isDirectory()) {
            dbDir.delete();
        }
        // Remove SharedPreferences file as well
        mSipPreferences.clearSharedPreferences();
    }

    public void saveProfile(SipProfile p) throws IOException {
        synchronized(SipProfileDb.class) {
            if (mProfilesCount < 0) retrieveSipProfileListInternal();
            File f = new File(mProfilesDirectory, p.getProfileName());
            if (!isChild(new File(mProfilesDirectory), f)) {
                throw new IOException("Invalid Profile Credentials!");
            }
            if (!f.exists()) f.mkdirs();
            AtomicFile atomicFile = new AtomicFile(new File(f, PROFILE_OBJ_FILE));
            FileOutputStream fos = null;
            ObjectOutputStream oos = null;
            try {
                fos = atomicFile.startWrite();
                oos = new ObjectOutputStream(fos);
                oos.writeObject(p);
                oos.flush();
                atomicFile.finishWrite(fos);
            } catch (IOException e) {
                atomicFile.failWrite(fos);
                throw e;
            } finally {
                if (oos != null) oos.close();
            }
        }
    }

    public List<SipProfile> retrieveSipProfileList() {
        synchronized(SipProfileDb.class) {
            return retrieveSipProfileListInternal();
        }
    }

    private List<SipProfile> retrieveSipProfileListInternal() {
        List<SipProfile> sipProfileList = Collections.synchronizedList(
                new ArrayList<SipProfile>());

        File root = new File(mProfilesDirectory);
        String[] dirs = root.list();
        if (dirs == null) return sipProfileList;
        for (String dir : dirs) {
            SipProfile p = retrieveSipProfileFromName(dir);
            if (p == null) continue;
            sipProfileList.add(p);
        }
        mProfilesCount = sipProfileList.size();
        return sipProfileList;
    }

    public SipProfile retrieveSipProfileFromName(String name) {
        if (TextUtils.isEmpty(name)) {
            return null;
        }

        File root = new File(mProfilesDirectory);
        File f = new File(new File(root, name), PROFILE_OBJ_FILE);
        if (f.exists()) {
            try {
                SipProfile p = deserialize(f);
                if (p != null && name.equals(p.getProfileName())) {
                    return p;
                }
            } catch (IOException e) {
                log("retrieveSipProfileListInternal, exception: " + e);
            }
        }
        return null;
    }

    private SipProfile deserialize(File profileObjectFile) throws IOException {
        AtomicFile atomicFile = new AtomicFile(profileObjectFile);
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(atomicFile.openRead());
            SipProfile p = (SipProfile) ois.readObject();
            return p;
        } catch (ClassNotFoundException e) {
            log("deserialize, exception: " + e);
        } finally {
            if (ois!= null) ois.close();
        }
        return null;
    }

    private static void log(String msg) {
        Log.d(SipUtil.LOG_TAG, PREFIX + msg);
    }

    /**
     * Verifies that the file is a direct child of the base directory.
     */
    private boolean isChild(File base, File file) {
        if (base == null || file == null) {
            return false;
        }
        if (!base.equals(file.getAbsoluteFile().getParentFile())) {
            Log.w(SipUtil.LOG_TAG, "isChild, file is not a child of the base dir.");
            EventLog.writeEvent(0x534e4554, "31530456", -1, "");
            return false;
        }
        return true;
    }
}
