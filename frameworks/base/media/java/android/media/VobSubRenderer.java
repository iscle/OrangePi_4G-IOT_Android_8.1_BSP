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
import java.util.Vector;
import java.io.FileDescriptor;


/** @hide */
public class VobSubRenderer extends Renderer {
    private final Context mContext;
    private final boolean mRender;
    private final Handler mEventHandler;
    private static final String TAG = "VobSubRenderer";

    private WebVttRenderingWidget mRenderingWidget;

    public VobSubRenderer(Context context) {
        this(context, null);
    }

    VobSubRenderer(Context mContext, Handler mEventHandler) {
        this.mContext = mContext;
        this.mRender = (mEventHandler == null);
        this.mEventHandler = mEventHandler;
    }

    @Override
    public boolean supports(MediaFormat format) {
        if (format.containsKey(MediaFormat.KEY_MIME)) {
            if (!MediaPlayer.MEDIA_MIMETYPE_TEXT_IDX
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
            return new VobSubTrack(mRenderingWidget, format);
        } else {
            return new VobSubTrack(mEventHandler, format);
        }
    }
}

class VobSubTrack extends WebVttTrack {
    private static final int MEDIA_TIMED_TEXT = 99;   // MediaPlayer.MEDIA_TIMED_TEXT
    private static final int KEY_STRUCT_TEXT = 16;    // TimedText.KEY_STRUCT_TEXT
    private static final int KEY_START_TIME = 7;      // TimedText.KEY_START_TIME
    private static final int KEY_LOCAL_SETTING = 102; // TimedText.KEY_START_TIME
    private static final int KEY_STRUCT_BITMAP = 17;  // TimedText.KEY_STRUCT_BITMAP

    private static final String TAG = "VobSubTrack";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private final Handler mEventHandler;
    private String mSubPath;
    private long mNativeContext;
    private int[] mPalette;

    static {
        System.loadLibrary("vobsub_jni");
        native_init();
    }

    VobSubTrack(WebVttRenderingWidget renderingWidget, MediaFormat format) {
        super(renderingWidget, format);
        mEventHandler = null;
        mSubPath = "";
    }

    VobSubTrack(Handler eventHandler, MediaFormat format) {
        super(null, format);
        mEventHandler = eventHandler;
        mSubPath = "";
    }

    void getSubPathFromFileDescriptor(FileDescriptor fd) {
         mSubPath = getPathFromFileDescriptor(fd);
    }

    private static native final void native_init();

    private native String getPathFromFileDescriptor(FileDescriptor fd);

    private native void native_setup_SubParser(String path);

    private native void setVobPalette(int[] palette);

    private native int[] executeParser(int offset) throws IllegalStateException;

    private native void native_finalized_SubParser();


    public void setUpSubParser() {
         native_setup_SubParser(mSubPath);
         setVobPalette(mPalette);
    }

    public void finalizedSubParser() {
         native_finalized_SubParser();
    }

    public String getSubPath() {
         return  mSubPath;
    }

    @Override
    protected void finalize() {
        native_finalized_SubParser();
    }


    @Override
    public void onData(byte[] data, boolean eos, long runID) {
        // TODO make reentrant
        try {
            Reader r = new InputStreamReader(new ByteArrayInputStream(data), "UTF-8");
            BufferedReader br = new BufferedReader(r);

            Log.d(TAG, "Parse idx file>>>");
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (DEBUG) Log.d(TAG, "line: " + line);
                if (line.startsWith("#")) {
                    // ignore #
                    continue;
                } else if (line.startsWith("size:")) {
                    // todo get size
                } else if (line.startsWith("palette:")) {
                    //get palette
                    String[] paletteArray = line.split(":")[1].trim().split(",");
                    mPalette = new int[paletteArray.length];

                    for (int i = 0; i < paletteArray.length; i++) {
                        mPalette[i] = Integer.parseInt(paletteArray[i].trim(), 16);
                        if (DEBUG) Log.d(TAG, "mPalette[" + i + "]: " +
                                              Integer.toHexString(mPalette[i]));
                    }
                } else if (line.startsWith("langidx:")) {
                    //todo get language
                } else if (line.startsWith("timestamp:")) {
                    //get timestamp  & filepos
                    String[] time_pos = line.split(",");
                    String firstTimestamp, firstFilePos;
                    long lastTimestamp = -1;
                    String lastFilePos = "";

                    //first timestamp line
                    firstTimestamp = time_pos[0].substring("timestamp:".length()).trim();
                    firstFilePos = (time_pos[1].split(":"))[1].trim();
                    if (DEBUG) Log.d(TAG, "firstTimestamp: " + firstTimestamp +
                                          ", firstFilePos: " + firstFilePos);

                    String timestampLine;
                    while ((timestampLine = br.readLine()) != null &&
                            !timestampLine.trim().equals("")) {
                        if (timestampLine.startsWith("timestamp:")) {
                            //timestamp line
                            TextTrackCue cue = new TextTrackCue();
                            String[] timestampPos = timestampLine.split(",");
                            String timestamp, filePos;

                            timestamp = timestampPos[0].substring("timestamp:".length()).trim();
                            filePos = (timestampPos[1].split(":"))[1].trim();
                            if (DEBUG) Log.d(TAG, "timestamp: " + timestamp +
                                                  " filePos: " + filePos);

                            if (lastTimestamp == -1 && lastFilePos.equals("")) {
                                cue.mStartTimeMs = parseMs(firstTimestamp);
                                cue.mEndTimeMs = parseMs(timestamp);
                                cue.mRunID = runID;
                                cue.mStrings = new String[] {firstFilePos};
                                cue.mLines = new TextTrackCueSpan[][]{ new TextTrackCueSpan[]{
                                                 new TextTrackCueSpan(firstFilePos, -1)} };
                            } else {
                                cue.mStartTimeMs = lastTimestamp;
                                cue.mEndTimeMs = parseMs(timestamp);
                                cue.mRunID = runID;
                                cue.mStrings = new String[] {lastFilePos};
                                cue.mLines = new TextTrackCueSpan[][]{ new TextTrackCueSpan[]{
                                                 new TextTrackCueSpan(lastFilePos, -1)} };
                            }
                            //store end time for next line as start time
                            lastTimestamp = cue.mEndTimeMs;
                            lastFilePos = filePos;
                            if (DEBUG) Log.d(TAG, "lastTimestamp: " + lastTimestamp +
                                                  ", lastFilePos: " + lastFilePos);

                            addCue(cue);
                        }
                    }
                     //last timestamp line
                     TextTrackCue cue = new TextTrackCue();
                     cue.mStartTimeMs = lastTimestamp;
                     cue.mRunID = runID;
                     cue.mStrings = new String[] {lastFilePos};
                     cue.mEndTimeMs = Integer.MAX_VALUE;
                     cue.mLines = new TextTrackCueSpan[][]{ new TextTrackCueSpan[]{
                                                new TextTrackCueSpan(lastFilePos, -1)} };
                     addCue(cue);

                     break;
                }
            }
            Log.d(TAG, "Parse idx file<<<<");
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
            parcel.writeInt(0);
            parcel.writeByteArray(null);

            //vobsub should only contain one filepos in each cue
            int offset = Integer.parseInt(ttc.mStrings[0], 16);
            int[] result = executeParser(offset);
            if (DEBUG) Log.d(TAG, "index: " + result[0] + ", width: " + result[1] +
                                  ", height: " + result[2]);
            parcel.writeInt(KEY_STRUCT_BITMAP);
            parcel.writeInt(result[1]);  //width
            parcel.writeInt(result[2]);  //height
            parcel.writeInt(result[0]);  //idx

            Message msg = mEventHandler.obtainMessage(MEDIA_TIMED_TEXT, 0, 0, parcel);
            mEventHandler.sendMessage(msg);
        }
        activeCues.clear();
    }

    private static long parseMs(String in) {
        long hours = Long.parseLong(in.split(":")[0].trim());
        long minutes = Long.parseLong(in.split(":")[1].trim());
        long seconds = Long.parseLong(in.split(":")[2].trim());
        long millies = Long.parseLong(in.split(":")[3].trim());

        return hours * 60 * 60 * 1000 + minutes * 60 * 1000 + seconds * 1000 + millies;
    }
}
