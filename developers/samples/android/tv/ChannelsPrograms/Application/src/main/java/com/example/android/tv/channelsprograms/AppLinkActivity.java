/*
 * Copyright (c) 2017 Google Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.example.android.tv.channelsprograms;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.example.android.tv.channelsprograms.model.MockDatabase;
import com.example.android.tv.channelsprograms.model.Movie;
import com.example.android.tv.channelsprograms.model.Subscription;
import com.example.android.tv.channelsprograms.playback.PlaybackActivity;
import com.example.android.tv.channelsprograms.util.AppLinkHelper;

/**
 * Delegates to the correct activity based on how the user entered the app.
 *
 * <p>Supports two options: view and play. The view option will open the channel for the user to be
 * able to view more programs. The play option will load the channel/program,
 * subscriptions/mediaContent start playing the movie.
 */
public class AppLinkActivity extends Activity {

    private static final String TAG = "AppLinkActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Uri uri = intent.getData();

        Log.v(TAG, uri.toString());

        if (uri.getPathSegments().isEmpty()) {
            Log.e(TAG, "Invalid uri " + uri);
            finish();
            return;
        }

        AppLinkHelper.AppLinkAction action = AppLinkHelper.extractAction(uri);
        switch (action.getAction()) {
            case AppLinkHelper.PLAYBACK:
                play((AppLinkHelper.PlaybackAction) action);
                break;
            case AppLinkHelper.BROWSE:
                browse((AppLinkHelper.BrowseAction) action);
                break;
            default:
                throw new IllegalArgumentException("Invalid Action " + action);
        }
    }

    private void browse(AppLinkHelper.BrowseAction action) {
        Subscription subscription =
                MockDatabase.findSubscriptionByName(this, action.getSubscriptionName());
        if (subscription == null) {
            Log.e(TAG, "Invalid subscription " + action.getSubscriptionName());
        } else {
            // TODO: Open an activity that has the movies for the subscription.
            Toast.makeText(this, action.getSubscriptionName(), Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private void play(AppLinkHelper.PlaybackAction action) {
        if (action.getPosition() == AppLinkHelper.DEFAULT_POSITION) {
            Log.d(
                    TAG,
                    "Playing program "
                            + action.getMovieId()
                            + " from channel "
                            + action.getChannelId());
        } else {
            Log.d(
                    TAG,
                    "Continuing program "
                            + action.getMovieId()
                            + " from channel "
                            + action.getChannelId()
                            + " at time "
                            + action.getPosition());
        }

        Movie movie = MockDatabase.findMovieById(this, action.getChannelId(), action.getMovieId());
        if (movie == null) {
            Log.e(TAG, "Invalid program " + action.getMovieId());
        } else {
            startPlaying(action.getChannelId(), movie, action.getPosition());
        }
        finish();
    }

    private void startPlaying(long channelId, Movie movie, long position) {
        Intent playMovieIntent = new Intent(this, PlaybackActivity.class);
        playMovieIntent.putExtra(PlaybackActivity.EXTRA_MOVIE, movie);
        playMovieIntent.putExtra(PlaybackActivity.EXTRA_CHANNEL_ID, channelId);
        playMovieIntent.putExtra(PlaybackActivity.EXTRA_POSITION, position);
        startActivity(playMovieIntent);
    }
}
