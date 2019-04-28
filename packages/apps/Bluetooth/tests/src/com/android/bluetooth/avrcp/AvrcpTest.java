package com.android.bluetooth.avrcp;

import android.bluetooth.BluetoothAvrcp;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.media.session.MediaSession;
import android.media.session.MediaSession.QueueItem;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.AudioManager;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
import android.os.Looper;
import android.test.AndroidTestCase;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AvrcpTest extends AndroidTestCase {

    public void testCanStart() {
        if (Looper.myLooper() == null) Looper.prepare();

        Avrcp a = Avrcp.make(getContext());
    }

    public void testFailedBrowseStart() {
        if (Looper.myLooper() == null) Looper.prepare();

        Context mockContext = mock(Context.class);
        AudioManager mockAudioManager = mock(AudioManager.class);
        PackageManager mockPackageManager = mock(PackageManager.class);

        when(mockAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).thenReturn(100);

        when(mockContext.getSystemService(Context.AUDIO_SERVICE)).thenReturn(mockAudioManager);

        when(mockContext.getApplicationContext()).thenReturn(mockContext);
        when(mockContext.getPackageManager()).thenReturn(mockPackageManager);


        // Call to get the BrowsableMediaPlayers
        // We must return at least one to try to startService
        List<ResolveInfo> resInfos = new ArrayList<ResolveInfo>();

        ServiceInfo fakeService = new ServiceInfo();
        fakeService.name = ".browse.MediaBrowserService";
        fakeService.packageName = "com.test.android.fake";

        ResolveInfo fakePackage = new ResolveInfo();
        fakePackage.serviceInfo = fakeService;
        fakePackage.nonLocalizedLabel = "Fake Package";
        resInfos.add(fakePackage);
        when(mockPackageManager.queryIntentServices(isA(Intent.class), anyInt())).thenReturn(resInfos);

        when(mockContext.startService(isA(Intent.class))).thenThrow(new SecurityException("test"));

        // Make calls start() which calls buildMediaPlayersList() which should
        // try to start the service?
        try {
            Avrcp a = Avrcp.make(mockContext);
        } catch (SecurityException e) {
            fail("Threw SecurityException instead of protecting against it: " + e.toString());
        }
    }
}
