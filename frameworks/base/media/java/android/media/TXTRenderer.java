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
public class TXTRenderer extends Renderer {
    private final Context mContext;
    private final boolean mRender;
    private final Handler mEventHandler;

    private WebVttRenderingWidget mRenderingWidget;

    public TXTRenderer(Context context) {
        this(context, null);
    }

    TXTRenderer(Context mContext, Handler mEventHandler) {
        this.mContext = mContext;
        this.mRender = (mEventHandler == null);
        this.mEventHandler = mEventHandler;
    }

    @Override
    public boolean supports(MediaFormat format) {
        if (format.containsKey(MediaFormat.KEY_MIME)) {
            if (!format.getString(MediaFormat.KEY_MIME)
                    .equals(MediaPlayer.MEDIA_MIMETYPE_TEXT_SUBTXT)) {
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
            return new TXTTrack(mRenderingWidget, format);
        } else {
            return new TXTTrack(mEventHandler, format);
        }
    }
}

class TXTTrack extends WebVttTrack {
    private static final int MEDIA_TIMED_TEXT = 99;   // MediaPlayer.MEDIA_TIMED_TEXT
    private static final int KEY_STRUCT_TEXT = 16;    // TimedText.KEY_STRUCT_TEXT
    private static final int KEY_START_TIME = 7;      // TimedText.KEY_START_TIME
    private static final int KEY_LOCAL_SETTING = 102; // TimedText.KEY_START_TIME

    private static final String TAG = "TXTTrack";
    private final Handler mEventHandler;

    TXTTrack(WebVttRenderingWidget renderingWidget, MediaFormat format) {
        super(renderingWidget, format);
        mEventHandler = null;
    }

    TXTTrack(Handler eventHandler, MediaFormat format) {
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
        try {
            Reader r = new InputStreamReader(new ByteArrayInputStream(data), "UTF-8");
            BufferedReader br = new BufferedReader(r);

            String lineContent;
            String tmpLineContent = br.readLine();
            if (tmpLineContent == null) {
                return;
            }

            boolean neverRead = true;
            long startEndGap = 0;
            while (((lineContent = br.readLine()) != null) && (tmpLineContent != null)) {
                TextTrackCue cue = new TextTrackCue();
                long hours = Long.parseLong(tmpLineContent.split(":")[0].trim());
                long minutes = Long.parseLong(tmpLineContent.split(":")[1].trim());
                long seconds = Long.parseLong(tmpLineContent.split(":")[2].trim());
                cue.mStartTimeMs = hours * 60 * 60 * 1000 + minutes * 60 * 1000 + seconds * 1000;

                long endHours = Long.parseLong(lineContent.split(":")[0].trim());
                long endMinutes = Long.parseLong(lineContent.split(":")[1].trim());
                long endSeconds = Long.parseLong(lineContent.split(":")[2].trim());
                cue.mEndTimeMs = endHours * 60 * 60 * 1000 + endMinutes * 60 * 1000
                                 + endSeconds * 1000;

                startEndGap = cue.mEndTimeMs - cue.mStartTimeMs;

                List<String> paragraph = new ArrayList<String>();
                paragraph.add(tmpLineContent.split(":")[3]);

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
                tmpLineContent = lineContent;
            }

            //last one
            TextTrackCue cue = new TextTrackCue();
            long hours = Long.parseLong(tmpLineContent.split(":")[0].trim());
            long minutes = Long.parseLong(tmpLineContent.split(":")[1].trim());
            long seconds = Long.parseLong(tmpLineContent.split(":")[2].trim());
            cue.mStartTimeMs = hours * 60 * 60 * 1000 + minutes * 60 * 1000 + seconds * 1000;
            cue.mEndTimeMs = cue.mStartTimeMs + startEndGap;

            List<String> paragraph = new ArrayList<String>();
            paragraph.add(tmpLineContent.split(":")[3]);

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
        long seconds = Long.parseLong(in.split(":")[2].split(",")[0].trim());
        long millies = Long.parseLong(in.split(":")[2].split(",")[1].trim());

        return hours * 60 * 60 * 1000 + minutes * 60 * 1000 + seconds * 1000 + millies;

    }
}

