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

/** @hide */
public class SRTRenderer extends Renderer {
    private final Context mContext;
    private final boolean mRender;
    private final Handler mEventHandler;

    private WebVttRenderingWidget mRenderingWidget;

    public SRTRenderer(Context context) {
        this(context, null);
    }

    SRTRenderer(Context mContext, Handler mEventHandler) {
        this.mContext = mContext;
        this.mRender = (mEventHandler == null);
        this.mEventHandler = mEventHandler;
    }

    @Override
    public boolean supports(MediaFormat format) {
        if (format.containsKey(MediaFormat.KEY_MIME)) {
            if (!format.getString(MediaFormat.KEY_MIME)
                    .equals(MediaPlayer.MEDIA_MIMETYPE_TEXT_SUBRIP)) {
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
            return new SRTTrack(mRenderingWidget, format);
        } else {
            return new SRTTrack(mEventHandler, format);
        }
    }
}

class SRTTrack extends WebVttTrack {
    private static final int MEDIA_TIMED_TEXT = 99;   // MediaPlayer.MEDIA_TIMED_TEXT
    private static final int KEY_STRUCT_TEXT = 16;    // TimedText.KEY_STRUCT_TEXT
    private static final int KEY_START_TIME = 7;      // TimedText.KEY_START_TIME
    private static final int KEY_LOCAL_SETTING = 102; // TimedText.KEY_START_TIME

    private static final String TAG = "SRTTrack";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private final Handler mEventHandler;

    SRTTrack(WebVttRenderingWidget renderingWidget, MediaFormat format) {
        super(renderingWidget, format);
        mEventHandler = null;
    }

    SRTTrack(Handler eventHandler, MediaFormat format) {
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
            Reader r = new InputStreamReader(new ByteArrayInputStream(data), "UTF-8");
            BufferedReader br = new BufferedReader(r);

            String header;
            while ((header = br.readLine()) != null) {
                // discard subtitle number
                header  = br.readLine();
                if (header == null) {
                    break;
                }

                TextTrackCue cue = new TextTrackCue();
                String[] startEnd = header.split("-->");
                cue.mStartTimeMs = parseMs(startEnd[0]);
                ///M: enhance parse endTime for case
                ///   (00:00:13,769 --> 00:00:16,124  X1:179 X2:536 Y1:496 Y2:522)
                String[] endTime = startEnd[1].trim().split(" ");
                if (DEBUG) Log.d(TAG, "endTime: " + endTime[0]);
                cue.mEndTimeMs = parseMs(endTime[0]);
                ///M @}

                ///M: keep cue in cuelist for seek forward case @{
                cue.mRunID = runID;
                ///M @}

                String s;
                List<String> paragraph = new ArrayList<String>();
                while (!((s = br.readLine()) == null || s.trim().equals(""))) {
                    paragraph.add(s);
                }

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

            Message msg = mEventHandler.obtainMessage(MEDIA_TIMED_TEXT, 0 /* arg1 */, 0 /* arg2 */,
                    parcel);
            mEventHandler.sendMessage(msg);
        }
        activeCues.clear();
    }

    private static long parseMs(String in) {
        long hours = Long.parseLong(in.split(":")[0].trim());
        long minutes = Long.parseLong(in.split(":")[1].trim());
        ///M: handle different case for srt@{
        long seconds = 0;
        long millies = 0;
        if (in.split(":").length == 3) { //for case 00:00:13,769
            seconds = Long.parseLong(in.split(":")[2].split(",")[0].trim());
            millies = Long.parseLong(in.split(":")[2].split(",")[1].trim());
        } else if (in.split(":").length == 4) { //for case 00:00:13:769
            seconds = Long.parseLong(in.split(":")[2].trim());
            millies = Long.parseLong(in.split(":")[3].trim());
        }
        /// @}

        return hours * 60 * 60 * 1000 + minutes * 60 * 1000 + seconds * 1000 + millies;

    }
}
