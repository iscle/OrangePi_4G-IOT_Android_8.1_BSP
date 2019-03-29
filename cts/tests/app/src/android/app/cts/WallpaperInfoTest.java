/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.app.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.WallpaperInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.service.wallpaper.WallpaperService;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class WallpaperInfoTest {

    @Test
    public void test() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        Intent intent = new Intent(WallpaperService.SERVICE_INTERFACE);
        intent.setPackage("android.app.stubs");
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> result = pm.queryIntentServices(intent, PackageManager.GET_META_DATA);
        assertEquals(1, result.size());
        ResolveInfo info = result.get(0);
        WallpaperInfo wallpaperInfo = new WallpaperInfo(context, info);
        assertEquals("Title", wallpaperInfo.loadLabel(pm));
        assertEquals("Description", wallpaperInfo.loadDescription(pm));
        assertEquals("Collection", wallpaperInfo.loadAuthor(pm));
        assertEquals("Context", wallpaperInfo.loadContextDescription(pm));
        assertEquals("http://android.com", wallpaperInfo.loadContextUri(pm).toString());
        assertEquals(true, wallpaperInfo.getShowMetadataInPreview());
        assertNotNull(wallpaperInfo.loadIcon(pm));
        assertNotNull(wallpaperInfo.loadThumbnail(pm));
    }
}
