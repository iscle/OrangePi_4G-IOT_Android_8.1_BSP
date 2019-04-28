package com.mediatek.media.ringtone;

import android.annotation.NonNull;
import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;

/**
 * Extension for RingtoneManager.
 */
public class RingtoneManagerEx {
    public void preFilterDrmFilesForFlType(final Context context,
        @NonNull final Uri fileUri) {
    }

    public String appendDrmToWhereClause(final Activity activity) {
        return "";
    }

    public String[] getMtkMediaColumns() {
        return new String[] {
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            "\"" + MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "\"",
            MediaStore.Audio.Media.TITLE_KEY
        };
    }

}

