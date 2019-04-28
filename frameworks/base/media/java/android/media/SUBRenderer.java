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
public class SUBRenderer extends Renderer {
    private final Context mContext;
    private final boolean mRender;
    private final Handler mEventHandler;

    private WebVttRenderingWidget mRenderingWidget;

    public SUBRenderer(Context context) {
        this(context, null);
    }

    SUBRenderer(Context mContext, Handler mEventHandler) {
        this.mContext = mContext;
        this.mRender = (mEventHandler == null);
        this.mEventHandler = mEventHandler;
    }

    @Override
    public boolean supports(MediaFormat format) {
        if (format.containsKey(MediaFormat.KEY_MIME)) {
            if (!MediaPlayer.MEDIA_MIMETYPE_TEXT_SUB
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
            return new SUBTrack(mRenderingWidget, format);
        } else {
            return new SUBTrack(mEventHandler, format);
        }
    }
}

class SUBTrack extends WebVttTrack {
    private static final int MEDIA_TIMED_TEXT = 99;   // MediaPlayer.MEDIA_TIMED_TEXT
    private static final int KEY_STRUCT_TEXT = 16;    // TimedText.KEY_STRUCT_TEXT
    private static final int KEY_START_TIME = 7;      // TimedText.KEY_START_TIME
    private static final int KEY_LOCAL_SETTING = 102; // TimedText.KEY_START_TIME

    private static final String TAG = "SUBTrack";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final int TYPE_UNKNOWN = -1;
    private static final int TYPE_TIME = 0;
    private static final int TYPE_FRAME = 1;

    private final Handler mEventHandler;

    SUBTrack(WebVttRenderingWidget renderingWidget, MediaFormat format) {
        super(renderingWidget, format);
        mEventHandler = null;
    }

    SUBTrack(Handler eventHandler, MediaFormat format) {
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
            int sub_type = TYPE_UNKNOWN;
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

                if (sub_type == TYPE_UNKNOWN) {
                    if (Pattern.matches("^\\d.*,\\d.*", header)) {
                        sub_type = TYPE_TIME;
                    } else if (Pattern.matches("^\\{\\d+\\}\\{\\d+\\}.*", header)) {
                        sub_type = TYPE_FRAME;
                    }
                }

                if (sub_type == TYPE_TIME) {
                    TextTrackCue cue = new TextTrackCue();
                    String[] startEnd = header.split(",");
                    cue.mStartTimeMs = parseMs(startEnd[0]);
                    cue.mEndTimeMs = parseMs(startEnd[1]);
                    cue.mRunID = runID;
                    if (DEBUG) Log.d(TAG, "Starttime = " + cue.mStartTimeMs +
                                          ", Endtime = " + cue.mEndTimeMs);

                    String s;
                    List<String> paragraph = new ArrayList<String>();
                    while (!((s = br.readLine()) == null || s.trim().equals(""))) {
                        if (DEBUG) Log.d(TAG, "Text: " + s);
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
                } else if (sub_type == TYPE_FRAME) {
                    TextTrackCue cue = new TextTrackCue();
                    String[] startEnd = header.split("\\}");
                    int startFrameNo = Integer.parseInt(startEnd[0].split("\\{")[1].trim());
                    int endFrameNo = Integer.parseInt(startEnd[1].split("\\{")[1].trim());

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

    private static long parseMs(String in) {
        long hours = Long.parseLong(in.split(":")[0].trim());
        long minutes = Long.parseLong(in.split(":")[1].trim());
        long seconds = Long.parseLong(in.split(":")[2].split("\\.")[0].trim());
        long millies = Long.parseLong(in.split(":")[2].split("\\.")[1].trim());

        return hours * 60 * 60 * 1000 + minutes * 60 * 1000 + seconds * 1000 + millies;

    }
}
