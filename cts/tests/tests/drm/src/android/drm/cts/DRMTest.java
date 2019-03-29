/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.drm.cts;

import com.android.compatibility.common.util.MediaUtils;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.test.AndroidTestCase;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Vector;

import android.drm.DrmManagerClient;
import android.drm.DrmConvertedStatus;
import android.drm.DrmEvent;
import android.drm.DrmInfo;
import android.drm.DrmInfoRequest;
import android.drm.DrmInfoStatus;
import android.drm.DrmRights;
import android.drm.DrmStore;
import android.drm.DrmUtils;
import android.media.MediaExtractor;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

public class DRMTest extends AndroidTestCase {
    private static String TAG = "CtsDRMTest";
    private static final int WAIT_TIME = 60000; // 1 min max

    private Object mLock = new Object();
    private ArrayList<Config> mConfigs = new ArrayList<Config>();
    private DrmRights mDrmRights;
    private DrmManagerClient mDrmManagerClient;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDrmManagerClient = new DrmManagerClient(getContext());
        String[] plugins = mDrmManagerClient.getAvailableDrmEngines();

        mConfigs.clear();
        for(String plugInName : plugins) {
            Config config = ConfigFactory.getConfig(plugInName);
            if (null != config) {
                mConfigs.add(config);
            }
        }
    }

    private void register(Config config) throws Exception {
        DrmInfo drmInfo = executeAcquireDrmInfo(DrmInfoRequest.TYPE_REGISTRATION_INFO,
                                            config.getInfoOfRegistration(),
                                            config.getMimeType());
        executeProcessDrmInfo(drmInfo, config);
    }

    private void acquireRights(Config config) throws Exception {
        DrmInfo drmInfo = executeAcquireDrmInfo(DrmInfoRequest.TYPE_RIGHTS_ACQUISITION_INFO,
                                            config.getInfoOfRightsAcquisition(),
                                            config.getMimeType());
        executeProcessDrmInfo(drmInfo, config);
    }

    private void deregister(Config config) throws Exception {
        DrmInfo drmInfo = executeAcquireDrmInfo(DrmInfoRequest.TYPE_UNREGISTRATION_INFO,
                                            config.getInfoOfRegistration(),
                                            config.getMimeType());
        executeProcessDrmInfo(drmInfo, config);
    }

    public void testIsDrmDirectoryExist() {
        assertTrue("/data/drm/ does not exist", new File("/data/drm/").exists());
    }

    public void testRegisterAndDeregister() throws Exception {
        for (Config config : mConfigs) {
            register(config);
            deregister(config);
        }
    }

    public void testAcquireRights() throws Exception {
        for (Config config : mConfigs) {
            register(config);
            acquireRights(config);
            deregister(config);
        }
    }

    public void testGetConstraints() throws Exception {
        for (Config config : mConfigs) {
            register(config);
            acquireRights(config);
            ContentValues constraints = mDrmManagerClient.getConstraints(
                                            config.getContentPath(),
                                            DrmStore.Action.DEFAULT);
            assertNotNull("Failed on plugin: " + config.getPluginName(), constraints);
            deregister(config);
        }
    }

    public void testCanHandle() throws Exception {
        for (Config config : mConfigs) {
            assertTrue("Failed on plugin: " + config.getPluginName(),
                    mDrmManagerClient.canHandle(config.getContentPath(), config.getMimeType()));
        }
    }

    public void testGetOriginalMimeType() throws Exception {
        for (Config config : mConfigs) {
            assertNotNull("Failed on plugin: " + config.getPluginName(),
                    mDrmManagerClient.getOriginalMimeType(config.getContentPath()));
        }
    }

    public void testCheckRightsStatus() throws Exception {
        for (Config config : mConfigs) {
            register(config);
            acquireRights(config);
            int rightsStatus = mDrmManagerClient.checkRightsStatus(
                                                config.getContentPath(),
                                                DrmStore.Action.PLAY);
            assertEquals("Failed on plugin: " + config.getPluginName(),
                    DrmStore.RightsStatus.RIGHTS_VALID, rightsStatus);
            deregister(config);
        }
    }

    public void testRemoveRights() throws Exception {
        for (Config config : mConfigs) {
            assertEquals("Failed on plugin: " + config.getPluginName(),
                    DrmManagerClient.ERROR_NONE,
                    mDrmManagerClient.removeRights(config.getContentPath()));
        }
    }

    public void testRemoveAllRights() throws Exception {
        for (Config config : mConfigs) {
            assertEquals("Failed on plugin: " + config.getPluginName(),
                    mDrmManagerClient.removeAllRights(), DrmManagerClient.ERROR_NONE);
        }
    }

    public void testConvertData() throws Exception {
        for (Config config : mConfigs) {
            byte[] inputData = new byte[]{'T','E','S','T'};

            int convertId = mDrmManagerClient.openConvertSession(config.getMimeType());
            DrmConvertedStatus drmConvertStatus
                                = mDrmManagerClient.convertData(convertId, inputData);
            mDrmManagerClient.closeConvertSession(convertId);
        }
    }

    private DrmInfo executeAcquireDrmInfo(
            int type, HashMap<String, String> request, String mimeType) throws Exception {
        DrmInfoRequest infoRequest = new DrmInfoRequest(type, mimeType);

        for (Iterator it = request.keySet().iterator(); it.hasNext(); ) {
            String key = (String) it.next();
            String value = request.get(key);
            infoRequest.put(key, value);
        }

        return mDrmManagerClient.acquireDrmInfo(infoRequest);
    }

    private void executeProcessDrmInfo(DrmInfo drmInfo, Config config) throws Exception {
        if (drmInfo == null) {
            return;
        }

        mDrmManagerClient.setOnEventListener(new OnEventListenerImpl(config));
        drmInfo.put(DrmInfoRequest.ACCOUNT_ID, config.getAccountId());
        assertEquals("Failed on plugin: " + config.getPluginName(),
                DrmManagerClient.ERROR_NONE, mDrmManagerClient.processDrmInfo(drmInfo));

        synchronized(mLock) {
            try {
                mLock.wait(WAIT_TIME);
            } catch(Exception e) {
                Log.v(TAG, "ProcessDrmInfo: wait was interrupted.");
            }
        }
    }

    public void testForwardLockAccess()  throws Exception {
        DrmManagerClient drmManager= new DrmManagerClient(mContext);
        String[] engines = drmManager.getAvailableDrmEngines();
        boolean haveForwardLock = false;
        for (String engine: engines) {
            if (engine.equals("OMA V1 Forward Lock")) {
                haveForwardLock = true;
            }
        }
        drmManager.close();
        if (!haveForwardLock) {
            Log.i(TAG, "Skipping forward lock test because forward lock is not available");
            return;
        }

        Vector<InputStream> sequence = new Vector<InputStream>();

        String dmHeader = "--mime_content_boundary\r\n" +
        "Content-Type: audio/mpeg\r\n" +
        "Content-Transfer-Encoding: binary\r\n\r\n";
        sequence.add(new ByteArrayInputStream(dmHeader.getBytes(StandardCharsets.UTF_8)));

        AssetFileDescriptor afd = mContext.getResources().openRawResourceFd(R.raw.testmp3_2);
        FileInputStream body = afd.createInputStream();
        sequence.add(body);

        String dmFooter = "\r\n--mime_content_boundary--";
        sequence.add(new ByteArrayInputStream(dmFooter.getBytes(StandardCharsets.UTF_8)));

        SequenceInputStream dmStream = new SequenceInputStream(sequence.elements());
        String flPath = mContext.getExternalCacheDir() + "/temp.fl";
        RandomAccessFile flFile = new RandomAccessFile(flPath, "rw");
        assertTrue("couldn't convert to fl file",
                MediaUtils.convertDmToFl(mContext, dmStream,  flFile));
        dmStream.close(); // this closes the underlying streams and AFD as well
        flFile.close();

        ParcelFileDescriptor flFd = null;
        try {
            // check that the .fl file can be played
            MediaPlayer player = new MediaPlayer();
            try {
                flFd = ParcelFileDescriptor.open(
                        new File(flPath), ParcelFileDescriptor.MODE_READ_ONLY);
                player.setDataSource(flFd.getFileDescriptor(), 0, flFd.getStatSize());
                player.prepare();
                player.start();
                SystemClock.sleep(2000);
                assertTrue("player is not playing", player.isPlaying());
                player.release();
            } catch (Exception e) {
                Log.d(TAG, "MediaPlayer playback failed:", e);
            } finally {
                player.release();
            }

            // check that the .fl file can be parsed with MediaMetadataRetriever
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(flFd.getFileDescriptor());
                String numTracks =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS);
                assertEquals("wrong number of tracks found in file", "1", numTracks);
            } finally {
                retriever.release();
            }

            // check that the .fl file cannot be opened with MediaExtractor
            MediaExtractor ex = new MediaExtractor();
            try {
                ex.setDataSource(flFd.getFileDescriptor());
                int n = ex.getTrackCount();
                fail("extractor creation should have failed, but found " + n + " tracks");
            } catch (Exception e) {
                // ignore, expected to fail
            } finally {
                ex.release();
            }
        } finally {
            flFd.close();
            new File(flPath).delete();
        }
    }

    private class OnEventListenerImpl implements DrmManagerClient.OnEventListener {
        private Config mConfig;
        public OnEventListenerImpl(Config config) {
            mConfig = config;
        }

        @Override
        public void onEvent(DrmManagerClient client, DrmEvent event) {
            switch (event.getType()) {
            case DrmEvent.TYPE_DRM_INFO_PROCESSED:
                Log.d(TAG, "processDrmInfo() completed");
                DrmInfoStatus infoStatus
                        = (DrmInfoStatus) event.getAttribute(DrmEvent.DRM_INFO_STATUS_OBJECT);
                switch (infoStatus.infoType) {
                case DrmInfoRequest.TYPE_RIGHTS_ACQUISITION_INFO:
                    mDrmRights = new DrmRights(infoStatus.data, infoStatus.mimeType);
                    assertNotNull(mDrmRights);
                    try {
                        assertEquals(DrmManagerClient.ERROR_NONE, mDrmManagerClient.saveRights(
                                    mDrmRights, mConfig.getRightsPath(), mConfig.getContentPath()));
                        Log.d(TAG, "Rights saved");
                    } catch (IOException e) {
                        Log.e(TAG, "Save Rights failed");
                        e.printStackTrace();
                    }
                    break;
                case DrmInfoRequest.TYPE_REGISTRATION_INFO:
                    Log.d(TAG, "Registration completed");
                    break;
                case DrmInfoRequest.TYPE_UNREGISTRATION_INFO:
                    Log.d(TAG, "Deregistration completed");
                    break;
                default:
                    break;
                }
                break;
            default:
                break;
            }
            synchronized (mLock) {
                mLock.notify();
            }
        }
    }
}
