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

package com.android.tv.tuner.setup;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.tv.common.SoftPreconditions;
import com.android.tv.common.ui.setup.SetupFragment;
import com.android.tv.tuner.ChannelScanFileParser;
import com.android.tv.tuner.R;
import com.android.tv.tuner.TunerHal;
import com.android.tv.tuner.TunerPreferences;
import com.android.tv.tuner.data.PsipData;
import com.android.tv.tuner.data.TunerChannel;
import com.android.tv.tuner.data.nano.Channel;
import com.android.tv.tuner.source.FileTsStreamer;
import com.android.tv.tuner.source.TsDataSource;
import com.android.tv.tuner.source.TsStreamer;
import com.android.tv.tuner.source.TunerTsStreamer;
import com.android.tv.tuner.tvinput.ChannelDataManager;
import com.android.tv.tuner.tvinput.EventDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A fragment for scanning channels.
 */
public class ScanFragment extends SetupFragment {
    private static final String TAG = "ScanFragment";
    private static final boolean DEBUG = false;

    // In the fake mode, the connection to antenna or cable is not necessary.
    // Instead dummy channels are added.
    private static final boolean FAKE_MODE = false;

    private static final String VCTLESS_CHANNEL_NAME_FORMAT = "RF%d-%d";

    public static final String ACTION_CATEGORY = "com.android.tv.tuner.setup.ScanFragment";
    public static final int ACTION_CANCEL = 1;
    public static final int ACTION_FINISH = 2;

    public static final String EXTRA_FOR_CHANNEL_SCAN_FILE = "scan_file_choice";

    private static final long CHANNEL_SCAN_SHOW_DELAY_MS = 10000;
    private static final long CHANNEL_SCAN_PERIOD_MS = 4000;
    private static final long SHOW_PROGRESS_DIALOG_DELAY_MS = 300;

    // Build channels out of the locally stored TS streams.
    private static final boolean SCAN_LOCAL_STREAMS = true;

    private ChannelDataManager mChannelDataManager;
    private ChannelScanTask mChannelScanTask;
    private ProgressBar mProgressBar;
    private TextView mScanningMessage;
    private View mChannelHolder;
    private ChannelAdapter mAdapter;
    private volatile boolean mChannelListVisible;
    private Button mCancelButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "onCreateView");
        View view = super.onCreateView(inflater, container, savedInstanceState);
        mChannelDataManager = new ChannelDataManager(getActivity());
        mChannelDataManager.checkDataVersion(getActivity());
        mAdapter = new ChannelAdapter();
        mProgressBar = (ProgressBar) view.findViewById(R.id.tune_progress);
        mScanningMessage = (TextView) view.findViewById(R.id.tune_description);
        ListView channelList = (ListView) view.findViewById(R.id.channel_list);
        channelList.setAdapter(mAdapter);
        channelList.setOnItemClickListener(null);
        ViewGroup progressHolder = (ViewGroup) view.findViewById(R.id.progress_holder);
        LayoutTransition transition = new LayoutTransition();
        transition.enableTransitionType(LayoutTransition.CHANGING);
        progressHolder.setLayoutTransition(transition);
        mChannelHolder = view.findViewById(R.id.channel_holder);
        mCancelButton = (Button) view.findViewById(R.id.tune_cancel);
        mCancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finishScan(false);
            }
        });
        Bundle args = getArguments();
        int tunerType = (args == null ? 0 : args.getInt(TunerSetupActivity.KEY_TUNER_TYPE, 0));
        // TODO: Handle the case when the fragment is restored.
        startScan(args == null ? 0 : args.getInt(EXTRA_FOR_CHANNEL_SCAN_FILE, 0));
        TextView scanTitleView = (TextView) view.findViewById(R.id.tune_title);
        switch (tunerType) {
            case TunerHal.TUNER_TYPE_USB:
                scanTitleView.setText(R.string.ut_channel_scan);
                break;
            case TunerHal.TUNER_TYPE_NETWORK:
                scanTitleView.setText(R.string.nt_channel_scan);
                break;
            default:
                scanTitleView.setText(R.string.bt_channel_scan);
        }
        return view;
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.ut_channel_scan;
    }

    @Override
    protected int[] getParentIdsForDelay() {
        return new int[] {R.id.progress_holder};
    }

    private void startScan(int channelMapId) {
        mChannelScanTask = new ChannelScanTask(channelMapId);
        mChannelScanTask.execute();
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        if (mChannelScanTask != null) {
            // Ensure scan task will stop.
            Log.w(TAG, "The activity went to the background. Stopping channel scan.");
            mChannelScanTask.stopScan();
        }
        super.onPause();
    }

    /**
     * Finishes the current scan thread. This fragment will be popped after the scan thread ends.
     *
     * @param cancel a flag which indicates the scan is canceled or not.
     */
    public void finishScan(boolean cancel) {
        if (mChannelScanTask != null) {
            mChannelScanTask.cancelScan(cancel);

            // Notifies a user of waiting to finish the scanning process.
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mChannelScanTask != null) {
                        mChannelScanTask.showFinishingProgressDialog();
                    }
                }
            }, SHOW_PROGRESS_DIALOG_DELAY_MS);

            // Hides the cancel button.
            mCancelButton.setEnabled(false);
        }
    }

    private class ChannelAdapter extends BaseAdapter {
        private final ArrayList<TunerChannel> mChannels;

        public ChannelAdapter() {
            mChannels = new ArrayList<>();
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int pos) {
            return false;
        }

        @Override
        public int getCount() {
            return mChannels.size();
        }

        @Override
        public Object getItem(int pos) {
            return pos;
        }

        @Override
        public long getItemId(int pos) {
            return pos;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final Context context = parent.getContext();

            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) context
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.ut_channel_list, parent, false);
            }

            TextView channelNum = (TextView) convertView.findViewById(R.id.channel_num);
            channelNum.setText(mChannels.get(position).getDisplayNumber());

            TextView channelName = (TextView) convertView.findViewById(R.id.channel_name);
            channelName.setText(mChannels.get(position).getName());
            return convertView;
        }

        public void add(TunerChannel channel) {
            mChannels.add(channel);
            notifyDataSetChanged();
        }
    }

    private class ChannelScanTask extends AsyncTask<Void, Integer, Void>
            implements EventDetector.EventListener, ChannelDataManager.ChannelScanListener {
        private static final int MAX_PROGRESS = 100;

        private final Activity mActivity;
        private final int mChannelMapId;
        private final TsStreamer mScanTsStreamer;
        private final TsStreamer mFileTsStreamer;
        private final ConditionVariable mConditionStopped;

        private final List<ChannelScanFileParser.ScanChannel> mScanChannelList = new ArrayList<>();
        private boolean mIsCanceled;
        private boolean mIsFinished;
        private ProgressDialog mFinishingProgressDialog;
        private CountDownLatch mLatch;

        public ChannelScanTask(int channelMapId) {
            mActivity = getActivity();
            mChannelMapId = channelMapId;
            if (FAKE_MODE) {
                mScanTsStreamer = new FakeTsStreamer(this);
            } else {
                TunerHal hal = ((TunerSetupActivity) mActivity).getTunerHal();
                if (hal == null) {
                    throw new RuntimeException("Failed to open a DVB device");
                }
                mScanTsStreamer = new TunerTsStreamer(hal, this);
            }
            mFileTsStreamer = SCAN_LOCAL_STREAMS ? new FileTsStreamer(this, mActivity) : null;
            mConditionStopped = new ConditionVariable();
            mChannelDataManager.setChannelScanListener(this, new Handler());
        }

        private void maybeSetChannelListVisible() {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    int channelsFound = mAdapter.getCount();
                    if (!mChannelListVisible && channelsFound > 0) {
                        String format = getResources().getQuantityString(
                                R.plurals.ut_channel_scan_message, channelsFound, channelsFound);
                        mScanningMessage.setText(String.format(format, channelsFound));
                        mChannelHolder.setVisibility(View.VISIBLE);
                        mChannelListVisible = true;
                    }
                }
            });
        }

        private void addChannel(final TunerChannel channel) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mAdapter.add(channel);
                    if (mChannelListVisible) {
                        int channelsFound = mAdapter.getCount();
                        String format = getResources().getQuantityString(
                                R.plurals.ut_channel_scan_message, channelsFound, channelsFound);
                        mScanningMessage.setText(String.format(format, channelsFound));
                    }
                }
            });
        }

        @Override
        protected Void doInBackground(Void... params) {
            mScanChannelList.clear();
            if (SCAN_LOCAL_STREAMS) {
                FileTsStreamer.addLocalStreamFiles(mScanChannelList);
            }
            mScanChannelList.addAll(ChannelScanFileParser.parseScanFile(
                    getResources().openRawResource(mChannelMapId)));
            scanChannels();
            return null;
        }

        @Override
        protected void onCancelled() {
            SoftPreconditions.checkState(false, TAG, "call cancelScan instead of cancel");
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mProgressBar.setProgress(values[0], true);
            } else {
                mProgressBar.setProgress(values[0]);
            }
        }

        private void stopScan() {
            if (mLatch != null) {
                mLatch.countDown();
            }
            mConditionStopped.open();
        }

        private void cancelScan(boolean cancel) {
            mIsCanceled = cancel;
            stopScan();
        }

        private void scanChannels() {
            if (DEBUG) Log.i(TAG, "Channel scan starting");
            mChannelDataManager.notifyScanStarted();

            long startMs = System.currentTimeMillis();
            int i = 1;
            for (ChannelScanFileParser.ScanChannel scanChannel : mScanChannelList) {
                int frequency = scanChannel.frequency;
                String modulation = scanChannel.modulation;
                Log.i(TAG, "Tuning to " + frequency + " " + modulation);

                TsStreamer streamer = getStreamer(scanChannel.type);
                SoftPreconditions.checkNotNull(streamer);
                if (streamer != null && streamer.startStream(scanChannel)) {
                    mLatch = new CountDownLatch(1);
                    try {
                        mLatch.await(CHANNEL_SCAN_PERIOD_MS, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "The current thread is interrupted during scanChannels(). " +
                                "The TS stream is stopped earlier than expected.", e);
                    }
                    streamer.stopStream();

                    addChannelsWithoutVct(scanChannel);
                    if (System.currentTimeMillis() > startMs + CHANNEL_SCAN_SHOW_DELAY_MS
                            && !mChannelListVisible) {
                        maybeSetChannelListVisible();
                    }
                }
                if (mConditionStopped.block(-1)) {
                    break;
                }
                publishProgress(MAX_PROGRESS * i++ / mScanChannelList.size());
            }
            mChannelDataManager.notifyScanCompleted();
            if (!mConditionStopped.block(-1)) {
                publishProgress(MAX_PROGRESS);
            }
            if (DEBUG) Log.i(TAG, "Channel scan ended");
        }


        private void addChannelsWithoutVct(ChannelScanFileParser.ScanChannel scanChannel) {
            if (scanChannel.radioFrequencyNumber == null
                    || !(mScanTsStreamer instanceof TunerTsStreamer)) {
                return;
            }
            for (TunerChannel tunerChannel
                    : ((TunerTsStreamer) mScanTsStreamer).getMalFormedChannels()) {
                if ((tunerChannel.getVideoPid() != TunerChannel.INVALID_PID)
                        && (tunerChannel.getAudioPid() != TunerChannel.INVALID_PID)) {
                    tunerChannel.setFrequency(scanChannel.frequency);
                    tunerChannel.setModulation(scanChannel.modulation);
                    tunerChannel.setShortName(String.format(Locale.US, VCTLESS_CHANNEL_NAME_FORMAT,
                            scanChannel.radioFrequencyNumber,
                            tunerChannel.getProgramNumber()));
                    tunerChannel.setVirtualMajor(scanChannel.radioFrequencyNumber);
                    tunerChannel.setVirtualMinor(tunerChannel.getProgramNumber());
                    onChannelDetected(tunerChannel, true);
                }
            }
        }

        private TsStreamer getStreamer(int type) {
            switch (type) {
                case Channel.TYPE_TUNER:
                    return mScanTsStreamer;
                case Channel.TYPE_FILE:
                    return mFileTsStreamer;
                default:
                    return null;
            }
        }

        @Override
        public void onEventDetected(TunerChannel channel, List<PsipData.EitItem> items) {
            mChannelDataManager.notifyEventDetected(channel, items);
        }

        @Override
        public void onChannelScanDone() {
            if (mLatch != null) {
                mLatch.countDown();
            }
        }

        @Override
        public void onChannelDetected(TunerChannel channel, boolean channelArrivedAtFirstTime) {
            if (channelArrivedAtFirstTime) {
                Log.i(TAG, "Found channel " + channel);
            }
            if (channelArrivedAtFirstTime && channel.hasAudio()) {
                // Playbacks with video-only stream have not been tested yet.
                // No video-only channel has been found.
                addChannel(channel);
                mChannelDataManager.notifyChannelDetected(channel, channelArrivedAtFirstTime);
            }
        }

        public void showFinishingProgressDialog() {
            // Show a progress dialog to wait for the scanning process if it's not done yet.
            if (!mIsFinished && mFinishingProgressDialog == null) {
                mFinishingProgressDialog = ProgressDialog.show(mActivity, "",
                        getString(R.string.ut_setup_cancel), true, false);
            }
        }

        @Override
        public void onChannelHandlingDone() {
            mChannelDataManager.setCurrentVersion(mActivity);
            mChannelDataManager.releaseSafely();
            mIsFinished = true;
            TunerPreferences.setScannedChannelCount(mActivity.getApplicationContext(),
                    mChannelDataManager.getScannedChannelCount());
            // Cancel a previously shown notification.
            TunerSetupActivity.cancelNotification(mActivity.getApplicationContext());
            // Mark scan as done
            TunerPreferences.setScanDone(mActivity.getApplicationContext());
            // finishing will be done manually.
            if (mFinishingProgressDialog != null) {
                mFinishingProgressDialog.dismiss();
            }
            // If the fragment is not resumed, the next fragment (scan result page) can't be
            // displayed. In that case, just close the activity.
            if (isResumed()) {
                onActionClick(ACTION_CATEGORY, mIsCanceled ? ACTION_CANCEL : ACTION_FINISH);
            } else if (getActivity() != null) {
                getActivity().finish();
            }
            mChannelScanTask = null;
        }
    }

    private static class FakeTsStreamer implements TsStreamer {
        private final EventDetector.EventListener mEventListener;
        private int mProgramNumber = 0;

        FakeTsStreamer(EventDetector.EventListener eventListener) {
            mEventListener = eventListener;
        }

        @Override
        public boolean startStream(ChannelScanFileParser.ScanChannel channel) {
            if (++mProgramNumber % 2 == 1) {
                return true;
            }
            final String displayNumber = Integer.toString(mProgramNumber);
            final String name = "Channel-" + mProgramNumber;
            mEventListener.onChannelDetected(new TunerChannel(mProgramNumber, new ArrayList<>()) {
                @Override
                public String getDisplayNumber() {
                    return displayNumber;
                }

                @Override
                public String getName() {
                    return name;
                }
            }, true);
            return true;
        }

        @Override
        public boolean startStream(TunerChannel channel) {
            return false;
        }

        @Override
        public void stopStream() {
        }

        @Override
        public TsDataSource createDataSource() {
            return null;
        }
    }
}
