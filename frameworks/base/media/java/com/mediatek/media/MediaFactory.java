package com.mediatek.media;

import android.util.Log;

import com.mediatek.media.mediascanner.MediaFileEx;
import com.mediatek.media.mediascanner.MediaScannerClientEx;
import com.mediatek.media.mediascanner.ThumbnailUtilsEx;
import com.mediatek.media.ringtone.RingtoneManagerEx;

public class MediaFactory {
    private static final String TAG = "MediaFactory";
    private static final String CLASS_NAME_MEDIA_FACTORY_IMPL
            = "com.mediatek.media.MediaFactoryImpl";
    private static final MediaFactory sMediaFactory;

    static {
        MediaFactory mediaFactory;
        try {
            Class clazz = Class.forName(CLASS_NAME_MEDIA_FACTORY_IMPL);
            mediaFactory = (MediaFactory) clazz.newInstance();
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "[static] ClassNotFoundException", e);
            mediaFactory = new MediaFactory();
        } catch (InstantiationException e) {
            Log.e(TAG, "[static] InstantiationException", e);
            mediaFactory = new MediaFactory();
        } catch (IllegalAccessException e) {
            Log.e(TAG, "[static] InstantiationException", e);
            mediaFactory = new MediaFactory();
        }
        sMediaFactory = mediaFactory;
        Log.i(TAG, "[static] sMediaFactory = " + sMediaFactory);
    }

    public static final MediaFactory getInstance() {
        return sMediaFactory;
    }

    public MediaFileEx getMediaFileEx() {
        return new MediaFileEx();
    }

    public MediaScannerClientEx getMediaScannerClientEx() {
        return new MediaScannerClientEx();
    }

    public RingtoneManagerEx getRingtoneManagerEx() {
        return new RingtoneManagerEx();
    }
    public ThumbnailUtilsEx getThumbnailUtilsEx() {
        return new ThumbnailUtilsEx();
    }
}