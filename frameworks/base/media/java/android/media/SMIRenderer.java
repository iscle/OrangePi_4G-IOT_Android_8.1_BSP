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
import java.util.Vector;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.text.Html;

/** @hide */
public class SMIRenderer extends Renderer {
    private final Context mContext;
    private final boolean mRender;
    private final Handler mEventHandler;

    private WebVttRenderingWidget mRenderingWidget;

    public SMIRenderer(Context context) {
        this(context, null);
    }

    SMIRenderer(Context mContext, Handler mEventHandler) {
        this.mContext = mContext;
        this.mRender = (mEventHandler == null);
        this.mEventHandler = mEventHandler;
    }

    @Override
    public boolean supports(MediaFormat format) {
        if (format.containsKey(MediaFormat.KEY_MIME)) {
            if (!format.getString(MediaFormat.KEY_MIME)
                    .equals(MediaPlayer.MEDIA_MIMETYPE_TEXT_SUBSMI)) {
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
            return new SMITrack(mRenderingWidget, format);
        } else {
            return new SMITrack(mEventHandler, format);
        }
    }
}

class SMITrack extends WebVttTrack {
    private static final int MEDIA_TIMED_TEXT = 99;   // MediaPlayer.MEDIA_TIMED_TEXT
    private static final int KEY_STRUCT_TEXT = 16;    // TimedText.KEY_STRUCT_TEXT
    private static final int KEY_START_TIME = 7;      // TimedText.KEY_START_TIME
    private static final int KEY_LOCAL_SETTING = 102; // TimedText.KEY_START_TIME

    private static final String TAG = "SMITrack";
    private final Handler mEventHandler;

    SMITrack(WebVttRenderingWidget renderingWidget, MediaFormat format) {
        super(renderingWidget, format);
        mEventHandler = null;
    }

    SMITrack(Handler eventHandler, MediaFormat format) {
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
        ArrayList<String> paragraph = new ArrayList<String>();
        String stringData = "";
        boolean startFlag = false;
        boolean started = false;
        boolean tmpStartFlag = false;
        boolean tmpStarted = false;
        String smiTemp = "";
        Pattern patternstart = Pattern.compile("<SYNC Start=(.*?)><P Class=(.*?)>",
                2);
        Matcher matcher = null;
        Pattern time = Pattern.compile("><P Class=(.*?)>", 2);
        Pattern spaceP = Pattern
                .compile("<SYNC Start=//d+><P Class=//w+>?", 2);
        Matcher spacematcher = null;

        try {
            Reader r = new InputStreamReader(new ByteArrayInputStream(data), "UTF-8");
            BufferedReader br = new BufferedReader(r);
            Integer tmpTS = 0;
            Integer nowTS = 0;

            //pilot
            while ((stringData = br.readLine()) != null) {
                boolean findFirstContent = false;
                boolean findFirstTS = false;
                TextTrackCue cue = new TextTrackCue();
                if (!tmpStarted) {
                    // remove header, ignore up/low
                    matcher = patternstart.matcher(stringData);
                    if (matcher.find()) {
                        tmpStarted = true;
                        tmpStartFlag = true;
                    }
                }
                if (tmpStartFlag) {
                    matcher = patternstart.matcher(stringData);
                    if (matcher.find()) {
                        if (smiTemp != "") {
                            //paragraph.clear();
                            paragraph.add(smiTemp);
                            Log.d(TAG, "first subtitle:" + smiTemp);
                            smiTemp = "";
                            findFirstContent = true;
                        }
                        tmpTS = (Integer.parseInt(stringData.substring(
                                stringData.indexOf("=") + 1, stringData.indexOf(">"))));
                        findFirstTS = true;
                        Log.d(TAG, "first timestamp:" + tmpTS);
                        spacematcher = spaceP.matcher(stringData);
                        if (spacematcher.find()) {
                            smiTemp = smiTemp + "  ";
                        }
                    } else {
                        smiTemp = smiTemp + Html.fromHtml(stringData).toString();
                    }
                    if (findFirstContent && findFirstContent) {
                        break;
                    }
                }
            }

            while ((stringData = br.readLine()) != null) {
                TextTrackCue cue = new TextTrackCue();
                if (!started) {
                    // remove header, ignore up/low
                    matcher = patternstart.matcher(stringData);
                    if (matcher.find()) {
                        started = true;
                        startFlag = true;
                    }
                }

                boolean needInsertSmi = false;
                if (startFlag) {
                    //needInsertSmi = false;
                    matcher = patternstart.matcher(stringData);
                    if (matcher.find()) {
                        if (smiTemp != "") {
                            needInsertSmi = true;
                        }
                        nowTS = (Integer.parseInt(stringData.substring(
                                stringData.indexOf("=") + 1, stringData.indexOf(">"))));
                        spacematcher = spaceP.matcher(stringData);
                        if (spacematcher.find()) {
                            smiTemp = smiTemp + "  ";
                        }
                    } else {
                        smiTemp = smiTemp + Html.fromHtml(stringData).toString();
                    }
                }

                cue.mStartTimeMs = tmpTS;
                cue.mEndTimeMs = nowTS;
                tmpTS = nowTS;
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

                if (needInsertSmi) {
                    paragraph.clear();
                    paragraph.add(smiTemp);
                    smiTemp = "";
                }

                addCue(cue);
            }

            //last but not least
            TextTrackCue cue = new TextTrackCue();
            cue.mStartTimeMs = tmpTS;
            cue.mEndTimeMs = tmpTS;
            tmpTS = 0;
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
            paragraph.clear();
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

}

