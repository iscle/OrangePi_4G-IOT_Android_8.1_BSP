package android.media;

import android.content.Context;
import android.media.SubtitleController.Renderer;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.regex.*;

/** @hide */
public class MPLRenderer extends Renderer {
    private final Context mContext;
    private final boolean mRender;
    private final Handler mEventHandler;

    private WebVttRenderingWidget mRenderingWidget;

    public MPLRenderer(Context context) {
        this(context, null);
    }

    MPLRenderer(Context mContext, Handler mEventHandler) {
        this.mContext = mContext;
        this.mRender = (mEventHandler == null);
        this.mEventHandler = mEventHandler;
    }

    @Override
    public boolean supports(MediaFormat format) {
        if (format.containsKey(MediaFormat.KEY_MIME)) {
            if (!MediaPlayer.MEDIA_MIMETYPE_TEXT_SUBMPL
                    .equals(format.getString(MediaFormat.KEY_MIME))) {
                return false;
            };
            return mRender == (format.getInteger(MediaFormat.KEY_IS_TIMED_TEXT, 0) == 0);
        }
        return false;
    }

    @Override
    public SubtitleTrack createTrack(MediaFormat format) {
        if (mRender && mRenderingWidget == null) {
            mRenderingWidget = new WebVttRenderingWidget(mContext);
        }

        if (mRender) {
            return new MPLTrack(mRenderingWidget, format);
        } else {
            return new MPLTrack(mEventHandler, format);
        }
    }
}

class MPLTrack extends WebVttTrack {
    private static final int MEDIA_TIMED_TEXT = 99;   // MediaPlayer.MEDIA_TIMED_TEXT
    private static final int KEY_STRUCT_TEXT = 16;    // TimedText.KEY_STRUCT_TEXT
    private static final int KEY_START_TIME = 7;      // TimedText.KEY_START_TIME
    private static final int KEY_LOCAL_SETTING = 102; // TimedText.KEY_START_TIME

    private static final String TAG = "MPLTrack";

    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final Handler mEventHandler;

    MPLTrack(WebVttRenderingWidget renderingWidget, MediaFormat format) {
        super(renderingWidget, format);
        mEventHandler = null;
    }

    MPLTrack(Handler eventHandler, MediaFormat format) {
        super(null, format);
        mEventHandler = eventHandler;
    }

    @Override
    protected void onData(SubtitleData data) {
        try {
            TextTrackCue cue = new TextTrackCue();
            cue.mStartTimeMs = data.getStartTimeUs() / 1000;
            cue.mEndTimeMs = (data.getStartTimeUs() + data.getDurationUs()) / 1000;

            String paragraph;
            paragraph = new String(data.getData(), "UTF-8");
            String[] lines = paragraph.split("\\r?\\n");
            cue.mLines = new TextTrackCueSpan[lines.length][];

            int i = 0;
            for (String line : lines) {
                TextTrackCueSpan[] span = new TextTrackCueSpan[] {
                    new TextTrackCueSpan(line, -1)
                };
                cue.mLines[i++] = span;
            }

            addCue(cue);
        } catch (UnsupportedEncodingException e) {
            Log.w(TAG, "subtitle data is not UTF-8 encoded: " + e);
        }
    }

    @Override
    public void onData(byte[] data, boolean eos, long runID) {
        // TODO make reentrant
        try {
            int framerate = 30;
            Reader r = new InputStreamReader(new ByteArrayInputStream(data), "UTF-8");
            BufferedReader br = new BufferedReader(r);

            String header;
            while ((header = br.readLine()) != null) {

                if (DEBUG) Log.d(TAG, "" + header);

                if (Pattern.matches("\\[.*\\]", header)) {
                    Log.i(TAG, "ignore file header infomation: " + header);
                    continue;
                }

                if (Pattern.matches("^\\[\\d+\\]\\[\\d+\\].*", header)) {
                    TextTrackCue cue = new TextTrackCue();
                    String[] startEnd = header.split("\\]");
                    int startFrameNo = Integer.parseInt(startEnd[0].split("\\[")[1].trim());
                    int endFrameNo = Integer.parseInt(startEnd[1].split("\\[")[1].trim());

                    cue.mStartTimeMs = (startFrameNo * 1000) / framerate;
                    cue.mEndTimeMs = (endFrameNo * 1000) / framerate;
                    cue.mRunID = runID;
                    if (DEBUG) Log.d(TAG, "Starttime = " + cue.mStartTimeMs +
                                          ", Endtime = " + cue.mEndTimeMs);

                    String s = startEnd[startEnd.length - 1];
                    if (DEBUG) Log.d(TAG, "Text: " + s);

                    List<String> paragraph = new ArrayList<String>();
                    paragraph.add(s);

                    int i = 0;
                    cue.mLines = new TextTrackCueSpan[paragraph.size()][];
                    cue.mStrings = paragraph.toArray(new String[0]);
                    for (String line : paragraph) {
                        TextTrackCueSpan[] span = new TextTrackCueSpan[] {
                                new TextTrackCueSpan(line, -1)
                        };
                        cue.mStrings[i] = line;
                        cue.mLines[i++] = span;
                    }

                    addCue(cue);
                }
            }

        } catch (UnsupportedEncodingException e) {
            Log.w(TAG, "subtitle data is not UTF-8 encoded: " + e);
        } catch (IOException ioe) {
            // shouldn't happen
            Log.e(TAG, ioe.getMessage(), ioe);
        }
    }

    @Override
    public void updateView(Vector<Cue> activeCues) {
        if (getRenderingWidget() != null) {
            super.updateView(activeCues);
            return;
        }

        if (mEventHandler == null) {
            return;
        }

        ///M: notify null content to AP when it is at endTime@{
        if (activeCues.isEmpty()) {
            if (DEBUG) Log.d(TAG, "activeCues is Empty");
            Message msg = mEventHandler.obtainMessage(MEDIA_TIMED_TEXT, 0, 0, null);
            mEventHandler.sendMessage(msg);
        }
        /// @}
        for (Cue cue : activeCues) {
            TextTrackCue ttc = (TextTrackCue) cue;

            Parcel parcel = Parcel.obtain();
            parcel.writeInt(KEY_LOCAL_SETTING);
            parcel.writeInt(KEY_START_TIME);
            parcel.writeInt((int) cue.mStartTimeMs);

            parcel.writeInt(KEY_STRUCT_TEXT);
            StringBuilder sb = new StringBuilder();
            for (String line : ttc.mStrings) {
                sb.append(line).append('\n');
            }

            byte[] buf = sb.toString().getBytes();
            parcel.writeInt(buf.length);
            parcel.writeByteArray(buf);

            Message msg = mEventHandler.obtainMessage(MEDIA_TIMED_TEXT, 0, 0, parcel);
            mEventHandler.sendMessage(msg);
        }
        activeCues.clear();
    }
}
