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

package com.android.cts.verifier.streamquality;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestListAdapter;
import com.android.cts.verifier.TestListAdapter.TestListItem;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.DataSetObserver;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Tests for verifying the quality of streaming videos.  Plays streams of different formats over
 * different protocols for a short amount of time, after which users can mark Pass/Fail depending
 * on the smoothness and subjective quality of the video.
 */
public class StreamingVideoActivity extends PassFailButtons.TestListActivity {
    /**
     * Simple storage class for stream information.
     */
    static class Stream implements Serializable {
        /**
         * Human-readable name for the stream.
         */
        public final String name;

        /**
         * Code name to append to the class name to identify this test.
         */
        public final String code;

        /**
         * URI of the stream
         */
        public final String uri;

        public Stream(String name, String code, String uri) {
            this.name = name;
            this.code = code;
            this.uri = uri;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            } else if (o == null || !(o instanceof Stream)) {
                return false;
            } else {
                Stream stream = (Stream) o;
                return name.equals(stream.name)
                        && code.equals(stream.code)
                        && uri.equals(stream.uri);
            }
        }

        @Override
        public int hashCode() {
            return name.hashCode() ^ uri.hashCode() ^ code.hashCode();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final String TAG = StreamingVideoActivity.class.getName();
    private static final int RTSP_URL_ERROR = 1;
    private static final String ITAG_13_SIGNATURE =
            "53A3A3A46DAB71E3C599DE8FA6A1484593DFACE3" +
            ".9476B91AD5035D88C3895CC2A4703B6DD442E972";
    private static final String ITAG_17_SIGNATURE =
            "10D6D263112C41DA98822D74821DF47340F1A361" +
            ".803649A2258E26BF40E76A95E646FBAE4009CEE8";
    private static final String ITAG_18_SIGNATURE =
            "618FBB112E1B2FBB66DA9F203AE8CC7DF93C7400" +
            ".20498AA006E999F42BE69D66E3596F2C7CA18114";
    private static final String RTSP_LOOKUP_URI_TEMPLATE =
            "http://redirector.gvt1.com/videoplayback?id=271de9756065677e" +
            "&source=youtube&protocol=rtsp&sparams=ip,ipbits,expire,id,itag,source" +
            "&ip=0.0.0.0&ipbits=0&expire=19000000000&key=ik0&alr=yes" +
            "&itag=%d" +
            "&signature=%s";

    private static final Stream[] HTTP_STREAMS = {
        new Stream("H263 Video, AMR Audio", "http_h263_amr",
                "http://redirector.gvt1.com/"
                + "videoplayback?id=271de9756065677e"
                + "&itag=13&ip=0.0.0.0&ipbits=0&expire=19000000000"
                + "&sparams=ip,ipbits,expire,id,itag"
                + "&signature=073A731E2BDF1E05206AC7B9B895C922ABCBA01D"
                + ".1DDA3F999541D2136E6755F16FC44CA972767169"
                + "&source=youtube"
                + "&key=ik0&user=android-device-test"),
        new Stream("MPEG4 SP Video, AAC Audio", "http_mpeg4_aac",
                "http://redirector.gvt1.com/"
                + "videoplayback?id=271de9756065677e"
                + "&itag=17&ip=0.0.0.0&ipbits=0&expire=19000000000"
                + "&sparams=ip,ipbits,expire,id,itag"
                + "&signature=6B0F8B8A6A7FD9E4CDF123349C2E061ED2020D74"
                + ".3460FC81D6C8894BA2D241597D2E1D059845F5F0"
                + "&source=youtube"
                + "&key=ik0&user=android-device-test"),
        new Stream("H264 Base Video, AAC Audio", "http_h264_aac",
                "http://redirector.gvt1.com/"
                + "videoplayback?id=271de9756065677e"
                + "&itag=18&ip=0.0.0.0&ipbits=0&expire=19000000000"
                + "&sparams=ip,ipbits,expire,id,itag"
                + "&signature=75627CD4CEA73D7868CBDE3CE5C4011955164107"
                + ".1DCFB0EF1372B48DDCFBE69645FE137AC02AF561"
                + "&source=youtube"
                + "&key=ik0&user=android-device-test"),
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_list);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.streaming_video, R.string.streaming_video_info, -1);

        TextView empty = (TextView) findViewById(android.R.id.empty);
        empty.setText(R.string.sv_no_data);

        getPassButton().setEnabled(false);
        setTestListAdapter(getStreamAdapter());
    }

    @Override
    public Dialog onCreateDialog(int id, Bundle args) {
        switch (id) {
            case RTSP_URL_ERROR:
                return new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.sv_failed_title))
                        .setMessage(getString(R.string.sv_failed_message))
                        .setNegativeButton("Close", new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                setTestResultAndFinish(false);
                            }
                        }).show();
            default:
                return super.onCreateDialog(id, args);
        }
    }

    private TestListAdapter getStreamAdapter() {
        ArrayTestListAdapter adapter = new ArrayTestListAdapter(this);

        adapter.add(TestListItem.newCategory("RTSP"));
        addRtspStreamToTest(
                adapter, "H263 Video, AMR Audio", "rtsp_h263_amr", 13, ITAG_13_SIGNATURE);
        addRtspStreamToTest(
                adapter, "MPEG4 SP Video, AAC Audio", "rtsp_mpeg4_aac", 17, ITAG_17_SIGNATURE);
        addRtspStreamToTest(
                adapter, "H264 Base Video, AAC Audio", "rtsp_h264_aac", 18, ITAG_18_SIGNATURE);

        adapter.add(TestListItem.newCategory("HTTP Progressive"));
        for (Stream stream : HTTP_STREAMS) {
            addStreamToTests(adapter, stream);
        }

        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                updatePassButton();
            }
        });

        return adapter;
    }

    private void addRtspStreamToTest(
            ArrayTestListAdapter adapter, String name, String code, int itag, String signature) {
        String rtspUrl = lookupRtspUrl(itag, signature);
        if (rtspUrl == null) {
            showDialog(RTSP_URL_ERROR);
        }
        Stream stream = new Stream(name, code, rtspUrl);
        addStreamToTests(adapter, stream);
    }

    private void addStreamToTests(ArrayTestListAdapter streams, Stream stream) {
        Intent i = new Intent(StreamingVideoActivity.this, PlayVideoActivity.class);
        i.putExtra(PlayVideoActivity.EXTRA_STREAM, stream);
        streams.add(TestListItem.newTest(stream.name, PlayVideoActivity.getTestId(stream.code),
                i, null));
    }

    /** @returns the appropriate RTSP url, or null in case of failure */
    private String lookupRtspUrl(int itag, String signature) {
        String rtspLookupUri = String.format(RTSP_LOOKUP_URI_TEMPLATE, itag, signature);
        try {
            return new LookupRtspUrlTask().execute(rtspLookupUri).get();
        } catch (Exception e) {
            Log.e(TAG, "RTSP URL lookup time out.", e);
            showDialog(RTSP_URL_ERROR);
            return null;
        }
    }

    /** Retrieve the URL for an RTSP stream */
    private class LookupRtspUrlTask extends AsyncTask<String, Void, String> {
        protected String doInBackground(String... rtspLookupUri) {
            HttpURLConnection urlConnection = null;
            try {
                URL url = new URL(rtspLookupUri[0]);
                urlConnection = (HttpURLConnection) url.openConnection();
                if (urlConnection.getResponseCode() != 200) {
                    throw new IOException("unable to get rtsp uri. Response Code:"
                            + urlConnection.getResponseCode());
                }
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(urlConnection.getInputStream()));
                return reader.readLine();
            } catch (Exception e) {
                Log.e(TAG, "RTSP URL lookup failed.", e);
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
        }
    }
}
