package com.android.tv.tuner.exoplayer;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Handler;
import android.util.Log;

import com.google.android.exoplayer.DecoderInfo;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.MediaCodecUtil;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.MediaSoftwareCodecUtil;
import com.google.android.exoplayer.SampleSource;
import com.android.tv.common.feature.CommonFeatures;

import java.lang.reflect.Field;

/**
 * MPEG-2 TS video track renderer
 */
public class MpegTsVideoTrackRenderer extends MediaCodecVideoTrackRenderer {
    private static final String TAG = "MpegTsVideoTrackRender";

    private static final int VIDEO_PLAYBACK_DEADLINE_IN_MS = 5000;
    // If DROPPED_FRAMES_NOTIFICATION_THRESHOLD frames are consecutively dropped, it'll be notified.
    private static final int DROPPED_FRAMES_NOTIFICATION_THRESHOLD = 10;
    private static final int MIN_HD_HEIGHT = 720;
    private static final String MIMETYPE_MPEG2 = "video/mpeg2";
    private static Field sRenderedFirstFrameField;

    private final boolean mIsSwCodecEnabled;
    private boolean mCodecIsSwPreferred;
    private boolean mSetRenderedFirstFrame;

    static {
        // Remove the reflection below once b/31223646 is resolved.
        try {
            sRenderedFirstFrameField = MediaCodecVideoTrackRenderer.class.getDeclaredField(
                    "renderedFirstFrame");
            sRenderedFirstFrameField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            // Null-checking for {@code sRenderedFirstFrameField} will do the error handling.
        }
    }

    public MpegTsVideoTrackRenderer(Context context, SampleSource source, Handler handler,
            MediaCodecVideoTrackRenderer.EventListener listener) {
        super(context, source, MediaCodecSelector.DEFAULT,
                MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, VIDEO_PLAYBACK_DEADLINE_IN_MS, handler,
                listener, DROPPED_FRAMES_NOTIFICATION_THRESHOLD);
        mIsSwCodecEnabled = CommonFeatures.USE_SW_CODEC_FOR_SD.isEnabled(context);
    }

    @Override
    protected DecoderInfo getDecoderInfo(MediaCodecSelector codecSelector, String mimeType,
            boolean requiresSecureDecoder) throws MediaCodecUtil.DecoderQueryException {
        try {
            if (mIsSwCodecEnabled && mCodecIsSwPreferred) {
                DecoderInfo swCodec = MediaSoftwareCodecUtil.getSoftwareDecoderInfo(
                        mimeType, requiresSecureDecoder);
                if (swCodec != null) {
                    return swCodec;
                }
            }
        } catch (MediaSoftwareCodecUtil.DecoderQueryException e) {
        }
        return super.getDecoderInfo(codecSelector, mimeType,requiresSecureDecoder);
    }

    @Override
    protected void onInputFormatChanged(MediaFormatHolder holder) throws ExoPlaybackException {
        mCodecIsSwPreferred = MIMETYPE_MPEG2.equalsIgnoreCase(holder.format.mimeType)
                && holder.format.height < MIN_HD_HEIGHT;
        super.onInputFormatChanged(holder);
    }

    @Override
    protected void onDiscontinuity(long positionUs) throws ExoPlaybackException {
        super.onDiscontinuity(positionUs);
        // Disabling pre-rendering of the first frame in order to avoid a frozen picture when
        // starting the playback. We do this only once, when the renderer is enabled at first, since
        // we need to pre-render the frame in advance when we do trickplay backed by seeking.
        if (!mSetRenderedFirstFrame) {
            setRenderedFirstFrame(true);
            mSetRenderedFirstFrame = true;
        }
    }

    private void setRenderedFirstFrame(boolean renderedFirstFrame) {
        if (sRenderedFirstFrameField != null) {
            try {
                sRenderedFirstFrameField.setBoolean(this, renderedFirstFrame);
            } catch (IllegalAccessException e) {
                Log.w(TAG, "renderedFirstFrame is not accessible. Playback may start with a frozen"
                        +" picture.");
            }
        }
    }
}
