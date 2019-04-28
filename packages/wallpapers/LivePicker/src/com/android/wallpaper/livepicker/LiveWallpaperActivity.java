/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.wallpaper.livepicker;

import android.app.ListActivity;
import android.app.WallpaperInfo;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;

public class LiveWallpaperActivity extends ListActivity {
    private static final int REQUEST_PREVIEW = 100;

    private LiveWallpaperListAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.live_wallpaper_base);

        mAdapter = new LiveWallpaperListAdapter(this);
        setListAdapter(mAdapter);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_PREVIEW) {
            if (resultCode == RESULT_OK) finish();
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        LiveWallpaperListAdapter.LiveWallpaperInfo wallpaperInfo =
                (LiveWallpaperListAdapter.LiveWallpaperInfo) mAdapter.getItem(position);
        final WallpaperInfo info = wallpaperInfo.info;
        if (info != null) {
            Intent preview = new Intent(this, LiveWallpaperPreview.class);
            preview.putExtra(LiveWallpaperPreview.EXTRA_LIVE_WALLPAPER_INFO, info);
            startActivityForResult(preview, REQUEST_PREVIEW);
        }
    }

}
