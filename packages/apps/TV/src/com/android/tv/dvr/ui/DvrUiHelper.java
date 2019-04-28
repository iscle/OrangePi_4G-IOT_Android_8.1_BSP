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

package com.android.tv.dvr.ui;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.tv.TvInputManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityOptionsCompat;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.data.BaseProgram;
import com.android.tv.data.Channel;
import com.android.tv.data.Program;
import com.android.tv.dialog.HalfSizedDialogFragment;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.DvrStorageStatusManager;
import com.android.tv.dvr.data.RecordedProgram;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.data.SeriesRecording;
import com.android.tv.dvr.provider.EpisodicProgramLoadTask;
import com.android.tv.dvr.ui.DvrHalfSizedDialogFragment.DvrAlreadyRecordedDialogFragment;
import com.android.tv.dvr.ui.DvrHalfSizedDialogFragment.DvrAlreadyScheduledDialogFragment;
import com.android.tv.dvr.ui.DvrHalfSizedDialogFragment.DvrChannelRecordDurationOptionDialogFragment;
import com.android.tv.dvr.ui.DvrHalfSizedDialogFragment.DvrChannelWatchConflictDialogFragment;
import com.android.tv.dvr.ui.DvrHalfSizedDialogFragment.DvrInsufficientSpaceErrorDialogFragment;
import com.android.tv.dvr.ui.DvrHalfSizedDialogFragment.DvrMissingStorageErrorDialogFragment;
import com.android.tv.dvr.ui.DvrHalfSizedDialogFragment.DvrNoFreeSpaceErrorDialogFragment;
import com.android.tv.dvr.ui.DvrHalfSizedDialogFragment.DvrProgramConflictDialogFragment;
import com.android.tv.dvr.ui.DvrHalfSizedDialogFragment.DvrScheduleDialogFragment;
import com.android.tv.dvr.ui.DvrHalfSizedDialogFragment.DvrSmallSizedStorageErrorDialogFragment;
import com.android.tv.dvr.ui.DvrHalfSizedDialogFragment.DvrStopRecordingDialogFragment;
import com.android.tv.dvr.ui.browse.DvrBrowseActivity;
import com.android.tv.dvr.ui.browse.DvrDetailsActivity;
import com.android.tv.dvr.ui.list.DvrSchedulesActivity;
import com.android.tv.dvr.ui.list.DvrSchedulesFragment;
import com.android.tv.dvr.ui.list.DvrSeriesSchedulesFragment;
import com.android.tv.dvr.ui.playback.DvrPlaybackActivity;
import com.android.tv.util.ToastUtils;
import com.android.tv.util.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A helper class for DVR UI.
 */
@MainThread
@TargetApi(Build.VERSION_CODES.N)
public class DvrUiHelper {
    private static final String TAG = "DvrUiHelper";

    private static ProgressDialog sProgressDialog = null;

    /**
     * Checks if the storage status is good for recording and shows error messages if needed.
     *
     * @param recordingRequestRunnable if the storage status is OK to record or users choose to
     *                                 perform the operation anyway, this Runnable will run.
     */
    public static void checkStorageStatusAndShowErrorMessage(Activity activity, String inputId,
            Runnable recordingRequestRunnable) {
        if (Utils.isBundledInput(inputId)) {
            switch (TvApplication.getSingletons(activity).getDvrStorageStatusManager()
                    .getDvrStorageStatus()) {
                case DvrStorageStatusManager.STORAGE_STATUS_TOTAL_CAPACITY_TOO_SMALL:
                    showDvrSmallSizedStorageErrorDialog(activity);
                    return;
                case DvrStorageStatusManager.STORAGE_STATUS_MISSING:
                    showDvrMissingStorageErrorDialog(activity);
                    return;
                case DvrStorageStatusManager.STORAGE_STATUS_FREE_SPACE_INSUFFICIENT:
                    showDvrNoFreeSpaceErrorDialog(activity, recordingRequestRunnable);
                    return;
            }
        }
        recordingRequestRunnable.run();
    }

    /**
     * Shows the schedule dialog.
     */
    public static void showScheduleDialog(Activity activity, Program program,
            boolean addCurrentProgramToSeries) {
        if (SoftPreconditions.checkNotNull(program) == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putParcelable(DvrHalfSizedDialogFragment.KEY_PROGRAM, program);
        args.putBoolean(DvrScheduleFragment.KEY_ADD_CURRENT_PROGRAM_TO_SERIES,
                addCurrentProgramToSeries);
        showDialogFragment(activity, new DvrScheduleDialogFragment(), args, true, true);
    }

    /**
     * Shows the recording duration options dialog.
     */
    public static void showChannelRecordDurationOptions(Activity activity, Channel channel) {
        if (SoftPreconditions.checkNotNull(channel) == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putLong(DvrHalfSizedDialogFragment.KEY_CHANNEL_ID, channel.getId());
        showDialogFragment(activity, new DvrChannelRecordDurationOptionDialogFragment(), args);
    }

    /**
     * Shows the dialog which says that the new schedule conflicts with others.
     */
    public static void showScheduleConflictDialog(Activity activity, Program program) {
        if (program == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putParcelable(DvrHalfSizedDialogFragment.KEY_PROGRAM, program);
        showDialogFragment(activity, new DvrProgramConflictDialogFragment(), args, false, true);
    }

    /**
     * Shows the conflict dialog for the channel watching.
     */
    public static void showChannelWatchConflictDialog(MainActivity activity, Channel channel) {
        if (channel == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putLong(DvrHalfSizedDialogFragment.KEY_CHANNEL_ID, channel.getId());
        showDialogFragment(activity, new DvrChannelWatchConflictDialogFragment(), args);
    }

    /**
     * Shows DVR insufficient space error dialog.
     */
    public static void showDvrInsufficientSpaceErrorDialog(MainActivity activity,
            Set<String> failedScheduledRecordingInfoSet) {
        Bundle args = new Bundle();
        ArrayList<String> failedScheduledRecordingInfoArray =
                new ArrayList<>(failedScheduledRecordingInfoSet);
        args.putStringArrayList(DvrInsufficientSpaceErrorFragment.FAILED_SCHEDULED_RECORDING_INFOS,
                failedScheduledRecordingInfoArray);
        showDialogFragment(activity, new DvrInsufficientSpaceErrorDialogFragment(), args);
        Utils.clearRecordingFailedReason(activity,
                TvInputManager.RECORDING_ERROR_INSUFFICIENT_SPACE);
        Utils.clearFailedScheduledRecordingInfoSet(activity);
    }

    /**
     * Shows DVR no free space error dialog.
     *
     * @param recordingRequestRunnable the recording request to be executed when users choose
     *                                 {@link DvrGuidedStepFragment#ACTION_RECORD_ANYWAY}.
     */
    public static void showDvrNoFreeSpaceErrorDialog(Activity activity,
            Runnable recordingRequestRunnable) {
        DvrHalfSizedDialogFragment fragment = new DvrNoFreeSpaceErrorDialogFragment();
        fragment.setOnActionClickListener(new HalfSizedDialogFragment.OnActionClickListener() {
            @Override
            public void onActionClick(long actionId) {
                if (actionId == DvrGuidedStepFragment.ACTION_RECORD_ANYWAY) {
                    recordingRequestRunnable.run();
                } else if (actionId == DvrGuidedStepFragment.ACTION_DELETE_RECORDINGS) {
                    Intent intent = new Intent(activity, DvrBrowseActivity.class);
                    activity.startActivity(intent);
                }
            }
        });
        showDialogFragment(activity, fragment, null);
    }

    /**
     * Shows DVR missing storage error dialog.
     */
    private static void showDvrMissingStorageErrorDialog(Activity activity) {
        showDialogFragment(activity, new DvrMissingStorageErrorDialogFragment(), null);
    }

    /**
     * Shows DVR small sized storage error dialog.
     */
    public static void showDvrSmallSizedStorageErrorDialog(Activity activity) {
        showDialogFragment(activity, new DvrSmallSizedStorageErrorDialogFragment(), null);
    }

    /**
     * Shows stop recording dialog.
     */
    public static void showStopRecordingDialog(Activity activity, long channelId, int reason,
            HalfSizedDialogFragment.OnActionClickListener listener) {
        Bundle args = new Bundle();
        args.putLong(DvrHalfSizedDialogFragment.KEY_CHANNEL_ID, channelId);
        args.putInt(DvrStopRecordingFragment.KEY_REASON, reason);
        DvrHalfSizedDialogFragment fragment = new DvrStopRecordingDialogFragment();
        fragment.setOnActionClickListener(listener);
        showDialogFragment(activity, fragment, args);
    }

    /**
     * Shows "already scheduled" dialog.
     */
    public static void showAlreadyScheduleDialog(Activity activity, Program program) {
        if (program == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putParcelable(DvrHalfSizedDialogFragment.KEY_PROGRAM, program);
        showDialogFragment(activity, new DvrAlreadyScheduledDialogFragment(), args, false, true);
    }

    /**
     * Shows "already recorded" dialog.
     */
    public static void showAlreadyRecordedDialog(Activity activity, Program program) {
        if (program == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putParcelable(DvrHalfSizedDialogFragment.KEY_PROGRAM, program);
        showDialogFragment(activity, new DvrAlreadyRecordedDialogFragment(), args, false, true);
    }

    /**
     * Handle the request of recording a current program. It will handle creating schedules and
     * shows the proper dialog and toast message respectively for timed-recording and program
     * recording cases.
     *
     * @param addProgramToSeries denotes whether the program to be recorded should be added into
     *                           the series recording when users choose to record the entire series.
     */
    public static void requestRecordingCurrentProgram(Activity activity,
            Channel channel, Program program, boolean addProgramToSeries) {
        if (program == null) {
            DvrUiHelper.showChannelRecordDurationOptions(activity, channel);
        } else if (DvrUiHelper.handleCreateSchedule(activity, program, addProgramToSeries)) {
            String msg = activity.getString(R.string.dvr_msg_current_program_scheduled,
                    program.getTitle(), Utils.toTimeString(program.getEndTimeUtcMillis(), false));
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Handle the request of recording a future program. It will handle creating schedules and
     * shows the proper toast message.
     *
     * @param addProgramToSeries denotes whether the program to be recorded should be added into
     *                           the series recording when users choose to record the entire series.
     */
    public static void requestRecordingFutureProgram(Activity activity,
            Program program, boolean addProgramToSeries) {
        if (DvrUiHelper.handleCreateSchedule(activity, program, addProgramToSeries)) {
            String msg = activity.getString(
                    R.string.dvr_msg_program_scheduled, program.getTitle());
            ToastUtils.show(activity, msg, Toast.LENGTH_SHORT);
        }
    }

    /**
     * Handles the action to create the new schedule. It returns {@code true} if the schedule is
     * added and there's no additional UI, otherwise {@code false}.
     */
    private static boolean handleCreateSchedule(Activity activity, Program program,
            boolean addProgramToSeries) {
        if (program == null) {
            return false;
        }
        DvrManager dvrManager = TvApplication.getSingletons(activity).getDvrManager();
        if (!program.isEpisodic()) {
            // One time recording.
            dvrManager.addSchedule(program);
            if (!dvrManager.getConflictingSchedules(program).isEmpty()) {
                DvrUiHelper.showScheduleConflictDialog(activity, program);
                return false;
            }
        } else {
            // Show recorded program rather than the schedule.
            RecordedProgram recordedProgram = dvrManager.getRecordedProgram(program.getTitle(),
                    program.getSeasonNumber(), program.getEpisodeNumber());
            if (recordedProgram != null) {
                DvrUiHelper.showAlreadyRecordedDialog(activity, program);
                return false;
            }
            ScheduledRecording duplicate = dvrManager.getScheduledRecording(program.getTitle(),
                    program.getSeasonNumber(), program.getEpisodeNumber());
            if (duplicate != null
                    && (duplicate.getState() == ScheduledRecording.STATE_RECORDING_NOT_STARTED
                    || duplicate.getState()
                    == ScheduledRecording.STATE_RECORDING_IN_PROGRESS)) {
                DvrUiHelper.showAlreadyScheduleDialog(activity, program);
                return false;
            }
            SeriesRecording seriesRecording = dvrManager.getSeriesRecording(program);
            if (seriesRecording == null || seriesRecording.isStopped()) {
                DvrUiHelper.showScheduleDialog(activity, program, addProgramToSeries);
                return false;
            } else {
                // Just add the schedule.
                dvrManager.addSchedule(program);
            }
        }
        return true;
    }

    private static void showDialogFragment(Activity activity,
            DvrHalfSizedDialogFragment dialogFragment, Bundle args) {
        showDialogFragment(activity, dialogFragment, args, false, false);
    }

    private static void showDialogFragment(Activity activity,
            DvrHalfSizedDialogFragment dialogFragment, Bundle args, boolean keepSidePanelHistory,
            boolean keepProgramGuide) {
        dialogFragment.setArguments(args);
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).getOverlayManager()
                    .showDialogFragment(DvrHalfSizedDialogFragment.DIALOG_TAG, dialogFragment,
                            keepSidePanelHistory, keepProgramGuide);
        } else {
            dialogFragment.show(activity.getFragmentManager(),
                    DvrHalfSizedDialogFragment.DIALOG_TAG);
        }
    }

    /**
     * Checks whether channel watch conflict dialog is open or not.
     */
    public static boolean isChannelWatchConflictDialogShown(MainActivity activity) {
        return activity.getOverlayManager().getCurrentDialog() instanceof
                DvrChannelWatchConflictDialogFragment;
    }

    private static ScheduledRecording getEarliestScheduledRecording(List<ScheduledRecording>
            recordings) {
        ScheduledRecording earlistScheduledRecording = null;
        if (!recordings.isEmpty()) {
            Collections.sort(recordings,
                    ScheduledRecording.START_TIME_THEN_PRIORITY_THEN_ID_COMPARATOR);
            earlistScheduledRecording = recordings.get(0);
        }
        return earlistScheduledRecording;
    }

    /**
     * Launches DVR playback activity for the give recorded program.
     *
     * @param programId the ID of the recorded program going to be played.
     * @param seekTimeMs the seek position to initial playback.
     * @param pinChecked {@code true} if the pin code for parental controls has already been
     *                   verified, otherwise {@code false}.
     */
    public static void startPlaybackActivity(Context context, long programId,
            long seekTimeMs, boolean pinChecked) {
        Intent intent = new Intent(context, DvrPlaybackActivity.class);
        intent.putExtra(Utils.EXTRA_KEY_RECORDED_PROGRAM_ID, programId);
        if (seekTimeMs != TvInputManager.TIME_SHIFT_INVALID_TIME) {
            intent.putExtra(Utils.EXTRA_KEY_RECORDED_PROGRAM_SEEK_TIME, seekTimeMs);
        }
        intent.putExtra(Utils.EXTRA_KEY_RECORDED_PROGRAM_PIN_CHECKED, pinChecked);
        context.startActivity(intent);
    }

    /**
     * Shows the schedules activity to resolve the tune conflict.
     */
    public static void startSchedulesActivityForTuneConflict(Context context, Channel channel) {
        if (channel == null) {
            return;
        }
        List<ScheduledRecording> conflicts = TvApplication.getSingletons(context).getDvrManager()
                .getConflictingSchedulesForTune(channel.getId());
        startSchedulesActivity(context, getEarliestScheduledRecording(conflicts));
    }

    /**
     * Shows the schedules activity to resolve the one time recording conflict.
     */
    public static void startSchedulesActivityForOneTimeRecordingConflict(Context context,
            List<ScheduledRecording> conflicts) {
        startSchedulesActivity(context, getEarliestScheduledRecording(conflicts));
    }

    /**
     * Shows the schedules activity with full schedule.
     */
    public static void startSchedulesActivity(Context context, ScheduledRecording
            focusedScheduledRecording) {
        Intent intent = new Intent(context, DvrSchedulesActivity.class);
        intent.putExtra(DvrSchedulesActivity.KEY_SCHEDULES_TYPE,
                DvrSchedulesActivity.TYPE_FULL_SCHEDULE);
        if (focusedScheduledRecording != null) {
            intent.putExtra(DvrSchedulesFragment.SCHEDULES_KEY_SCHEDULED_RECORDING,
                    focusedScheduledRecording);
        }
        context.startActivity(intent);
    }

    /**
     * Shows the schedules activity for series recording.
     */
    public static void startSchedulesActivityForSeries(Context context,
            SeriesRecording seriesRecording) {
        Intent intent = new Intent(context, DvrSchedulesActivity.class);
        intent.putExtra(DvrSchedulesActivity.KEY_SCHEDULES_TYPE,
                DvrSchedulesActivity.TYPE_SERIES_SCHEDULE);
        intent.putExtra(DvrSeriesSchedulesFragment.SERIES_SCHEDULES_KEY_SERIES_RECORDING,
                seriesRecording);
        context.startActivity(intent);
    }

    /**
     * Shows the series settings activity.
     *
     * @param programs list of programs which belong to the series.
     */
    public static void startSeriesSettingsActivity(Context context, long seriesRecordingId,
            @Nullable List<Program> programs, boolean removeEmptySeriesSchedule,
            boolean isWindowTranslucent, boolean showViewScheduleOptionInDialog,
            Program currentProgram) {
        SeriesRecording series = TvApplication.getSingletons(context).getDvrDataManager()
                .getSeriesRecording(seriesRecordingId);
        if (series == null) {
            return;
        }
        if (programs != null) {
            startSeriesSettingsActivityInternal(context, seriesRecordingId, programs,
                    removeEmptySeriesSchedule, isWindowTranslucent,
                    showViewScheduleOptionInDialog, currentProgram);
        } else {
            EpisodicProgramLoadTask episodicProgramLoadTask =
                    new EpisodicProgramLoadTask(context, series) {
                @Override
                protected void onPostExecute(List<Program> loadedPrograms) {
                    sProgressDialog.dismiss();
                    sProgressDialog = null;
                    startSeriesSettingsActivityInternal(context, seriesRecordingId,
                            loadedPrograms == null ? Collections.EMPTY_LIST : loadedPrograms,
                            removeEmptySeriesSchedule, isWindowTranslucent,
                            showViewScheduleOptionInDialog, currentProgram);
                }
            }.setLoadCurrentProgram(true)
                    .setLoadDisallowedProgram(true)
                    .setLoadScheduledEpisode(true)
                    .setIgnoreChannelOption(true);
            sProgressDialog = ProgressDialog.show(context, null, context.getString(
                    R.string.dvr_series_progress_message_reading_programs), true, true,
                    new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialogInterface) {
                            episodicProgramLoadTask.cancel(true);
                            sProgressDialog = null;
                        }
                    });
            episodicProgramLoadTask.execute();
        }
    }

    private static void startSeriesSettingsActivityInternal(Context context, long seriesRecordingId,
            @NonNull List<Program> programs, boolean removeEmptySeriesSchedule,
            boolean isWindowTranslucent, boolean showViewScheduleOptionInDialog,
            Program currentProgram) {
        SoftPreconditions.checkState(programs != null,
                TAG, "Start series settings activity but programs is null");
        Intent intent = new Intent(context, DvrSeriesSettingsActivity.class);
        intent.putExtra(DvrSeriesSettingsActivity.SERIES_RECORDING_ID, seriesRecordingId);
        BigArguments.reset();
        BigArguments.setArgument(DvrSeriesSettingsActivity.PROGRAM_LIST, programs);
        intent.putExtra(DvrSeriesSettingsActivity.REMOVE_EMPTY_SERIES_RECORDING,
                removeEmptySeriesSchedule);
        intent.putExtra(DvrSeriesSettingsActivity.IS_WINDOW_TRANSLUCENT, isWindowTranslucent);
        intent.putExtra(DvrSeriesSettingsActivity.SHOW_VIEW_SCHEDULE_OPTION_IN_DIALOG,
                showViewScheduleOptionInDialog);
        intent.putExtra(DvrSeriesSettingsActivity.CURRENT_PROGRAM, currentProgram);
        context.startActivity(intent);
    }

    /**
     * Shows "series recording scheduled" dialog activity.
     */
    public static void StartSeriesScheduledDialogActivity(Context context,
            SeriesRecording seriesRecording, boolean showViewScheduleOptionInDialog,
            List<Program> programs) {
        if (seriesRecording == null) {
            return;
        }
        Intent intent = new Intent(context, DvrSeriesScheduledDialogActivity.class);
        intent.putExtra(DvrSeriesScheduledDialogActivity.SERIES_RECORDING_ID,
                seriesRecording.getId());
        intent.putExtra(DvrSeriesScheduledDialogActivity.SHOW_VIEW_SCHEDULE_OPTION,
                showViewScheduleOptionInDialog);
        BigArguments.reset();
        BigArguments.setArgument(DvrSeriesScheduledFragment.SERIES_SCHEDULED_KEY_PROGRAMS,
                programs);
        context.startActivity(intent);
    }

    /**
     * Shows the details activity for the DVR items. The type of DVR items may be
     * {@link ScheduledRecording}, {@link RecordedProgram}, or {@link SeriesRecording}.
     */
    public static void startDetailsActivity(Activity activity, Object dvrItem,
            @Nullable ImageView imageView, boolean hideViewSchedule) {
        if (dvrItem == null) {
            return;
        }
        Intent intent = new Intent(activity, DvrDetailsActivity.class);
        long recordingId;
        int viewType;
        if (dvrItem instanceof ScheduledRecording) {
            ScheduledRecording schedule = (ScheduledRecording) dvrItem;
            recordingId = schedule.getId();
            if (schedule.getState() == ScheduledRecording.STATE_RECORDING_NOT_STARTED) {
                viewType = DvrDetailsActivity.SCHEDULED_RECORDING_VIEW;
            } else if (schedule.getState() == ScheduledRecording.STATE_RECORDING_IN_PROGRESS) {
                viewType = DvrDetailsActivity.CURRENT_RECORDING_VIEW;
            } else {
                return;
            }
        } else if (dvrItem instanceof RecordedProgram) {
            recordingId = ((RecordedProgram) dvrItem).getId();
            viewType = DvrDetailsActivity.RECORDED_PROGRAM_VIEW;
        } else if (dvrItem instanceof SeriesRecording) {
            recordingId = ((SeriesRecording) dvrItem).getId();
            viewType = DvrDetailsActivity.SERIES_RECORDING_VIEW;
        } else {
            return;
        }
        intent.putExtra(DvrDetailsActivity.RECORDING_ID, recordingId);
        intent.putExtra(DvrDetailsActivity.DETAILS_VIEW_TYPE, viewType);
        intent.putExtra(DvrDetailsActivity.HIDE_VIEW_SCHEDULE, hideViewSchedule);
        Bundle bundle = null;
        if (imageView != null) {
            bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(activity, imageView,
                    DvrDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
        }
        activity.startActivity(intent, bundle);
    }

    /**
     * Shows the cancel all dialog for series schedules list.
     */
    public static void showCancelAllSeriesRecordingDialog(DvrSchedulesActivity activity,
            SeriesRecording seriesRecording) {
        DvrStopSeriesRecordingDialogFragment dvrStopSeriesRecordingDialogFragment =
                new DvrStopSeriesRecordingDialogFragment();
        Bundle arguments = new Bundle();
        arguments.putParcelable(DvrStopSeriesRecordingFragment.KEY_SERIES_RECORDING,
                seriesRecording);
        dvrStopSeriesRecordingDialogFragment.setArguments(arguments);
        dvrStopSeriesRecordingDialogFragment.show(activity.getFragmentManager(),
                DvrStopSeriesRecordingDialogFragment.DIALOG_TAG);
    }

    /**
     * Shows the series deletion activity.
     */
    public static void startSeriesDeletionActivity(Context context, long seriesRecordingId) {
        Intent intent = new Intent(context, DvrSeriesDeletionActivity.class);
        intent.putExtra(DvrSeriesDeletionActivity.SERIES_RECORDING_ID, seriesRecordingId);
        context.startActivity(intent);
    }

    public static void showAddScheduleToast(Context context,
            String title, long startTimeMs, long endTimeMs) {
        String msg = (startTimeMs > System.currentTimeMillis()) ?
            context.getString(R.string.dvr_msg_program_scheduled, title)
            : context.getString(R.string.dvr_msg_current_program_scheduled, title,
                    Utils.toTimeString(endTimeMs, false));
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    /**
     * Returns the styled schedule's title with its season and episode number.
     */
    public static CharSequence getStyledTitleWithEpisodeNumber(Context context,
            ScheduledRecording schedule, int episodeNumberStyleResId) {
        return getStyledTitleWithEpisodeNumber(context, schedule.getProgramTitle(),
                schedule.getSeasonNumber(), schedule.getEpisodeNumber(), episodeNumberStyleResId);
    }

    /**
     * Returns the styled program's title with its season and episode number.
     */
    public static CharSequence getStyledTitleWithEpisodeNumber(Context context,
            BaseProgram program, int episodeNumberStyleResId) {
        return getStyledTitleWithEpisodeNumber(context, program.getTitle(),
                program.getSeasonNumber(), program.getEpisodeNumber(), episodeNumberStyleResId);
    }

    @NonNull
    public static CharSequence getStyledTitleWithEpisodeNumber(Context context, String title,
            String seasonNumber, String episodeNumber, int episodeNumberStyleResId) {
        if (TextUtils.isEmpty(title)) {
            return "";
        }
        SpannableStringBuilder builder;
        if (TextUtils.isEmpty(seasonNumber) || seasonNumber.equals("0")) {
            builder = TextUtils.isEmpty(episodeNumber) ? new SpannableStringBuilder(title) :
                    new SpannableStringBuilder(Html.fromHtml(
                            context.getString(R.string.program_title_with_episode_number_no_season,
                                    title, episodeNumber)));
        } else {
            builder = new SpannableStringBuilder(Html.fromHtml(
                    context.getString(R.string.program_title_with_episode_number,
                            title, seasonNumber, episodeNumber)));
        }
        Object[] spans = builder.getSpans(0, builder.length(), Object.class);
        if (spans.length > 0) {
            if (episodeNumberStyleResId != 0) {
                builder.setSpan(new TextAppearanceSpan(context, episodeNumberStyleResId),
                        builder.getSpanStart(spans[0]), builder.getSpanEnd(spans[0]),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            builder.removeSpan(spans[0]);
        }
        return new SpannableString(builder);
    }
}