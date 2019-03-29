/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.example.android.tv.channelsprograms.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;

import com.example.android.tv.channelsprograms.R;
import com.example.android.tv.channelsprograms.util.AppLinkHelper;
import com.example.android.tv.channelsprograms.util.SharedPreferencesHelper;

import java.util.Collections;
import java.util.List;

/** Mock database stores data in {@link SharedPreferences}. */
public final class MockDatabase {

    private MockDatabase() {
        // Do nothing.
    }

    /**
     * Returns a subscription to mock content representing tv shows.
     *
     * @param context used for accessing shared preferences.
     * @return a subscription with tv show data.
     */
    public static Subscription getTvShowSubscription(Context context) {

        return findOrCreateSubscription(
                context,
                R.string.title_tv_shows,
                R.string.tv_shows_description,
                R.drawable.ic_video_library_blue_80dp);
    }

    /**
     * Returns a subscription to mock content representing your videos.
     *
     * @param context used for accessing shared preferences.
     * @return a subscription with your video data.
     */
    public static Subscription getVideoSubscription(Context context) {

        return findOrCreateSubscription(
                context,
                R.string.your_videos,
                R.string.your_videos_description,
                R.drawable.ic_video_library_blue_80dp);
    }

    /**
     * Returns a subscription to mock content representing cat videos.
     *
     * @param context used for accessing shared preferences.
     * @return a subscription with cat videos.
     */
    public static Subscription getCatVideosSubscription(Context context) {

        return findOrCreateSubscription(
                context,
                R.string.cat_videos,
                R.string.cat_videos_description,
                R.drawable.ic_movie_blue_80dp);
    }

    private static Subscription findOrCreateSubscription(
            Context context,
            @StringRes int titleResource,
            @StringRes int descriptionResource,
            @DrawableRes int logoResource) {
        // See if we have already created the channel in the TV Provider.
        String title = context.getString(titleResource);

        Subscription subscription = findSubscriptionByTitle(context, title);
        if (subscription != null) {
            return subscription;
        }

        return Subscription.createSubscription(
                title,
                context.getString(descriptionResource),
                AppLinkHelper.buildBrowseUri(title).toString(),
                logoResource);
    }

    @Nullable
    private static Subscription findSubscriptionByTitle(Context context, String title) {
        for (Subscription subscription : getSubscriptions(context)) {
            if (subscription.getName().equals(title)) {
                return subscription;
            }
        }
        return null;
    }

    /**
     * Overrides the subscriptions stored in {@link SharedPreferences}.
     *
     * @param context used for accessing shared preferences.
     * @param subscriptions stored in shared preferences.
     */
    public static void saveSubscriptions(Context context, List<Subscription> subscriptions) {
        SharedPreferencesHelper.storeSubscriptions(context, subscriptions);
    }

    /**
     * Adds the subscription to the list of persisted subscriptions in {@link SharedPreferences}.
     * Will update the persisted subscription if it already exists.
     *
     * @param context used for accessing shared preferences.
     * @param subscription to be saved.
     */
    public static void saveSubscription(Context context, Subscription subscription) {
        List<Subscription> subscriptions = getSubscriptions(context);
        int index = findSubscription(subscriptions, subscription);
        if (index == -1) {
            subscriptions.add(subscription);
        } else {
            subscriptions.set(index, subscription);
        }
        saveSubscriptions(context, subscriptions);
    }

    private static int findSubscription(
            List<Subscription> subscriptions, Subscription subscription) {
        for (int index = 0; index < subscriptions.size(); ++index) {
            Subscription current = subscriptions.get(index);
            if (current.getName().equals(subscription.getName())) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Returns subscriptions stored in {@link SharedPreferences}.
     *
     * @param context used for accessing shared preferences.
     * @return a list of subscriptions or empty list if none exist.
     */
    public static List<Subscription> getSubscriptions(Context context) {
        return SharedPreferencesHelper.readSubscriptions(context);
    }

    /**
     * Finds a subscription given a channel id that the subscription is associated with.
     *
     * @param context used for accessing shared preferences.
     * @param channelId of the channel that the subscription is associated with.
     * @return a subscription or null if none exist.
     */
    @Nullable
    public static Subscription findSubscriptionByChannelId(Context context, long channelId) {
        for (Subscription subscription : getSubscriptions(context)) {
            if (subscription.getChannelId() == channelId) {
                return subscription;
            }
        }
        return null;
    }

    /**
     * Finds a subscription with the given name.
     *
     * @param context used for accessing shared preferences.
     * @param name of the subscription.
     * @return a subscription or null if none exist.
     */
    @Nullable
    public static Subscription findSubscriptionByName(Context context, String name) {
        for (Subscription subscription : getSubscriptions(context)) {
            if (subscription.getName().equals(name)) {
                return subscription;
            }
        }
        return null;
    }

    /**
     * Overrides the movies stored in {@link SharedPreferences} for a given subscription.
     *
     * @param context used for accessing shared preferences.
     * @param channelId of the channel that the movies are associated with.
     * @param movies to be stored.
     */
    public static void saveMovies(Context context, long channelId, List<Movie> movies) {
        SharedPreferencesHelper.storeMovies(context, channelId, movies);
    }

    /**
     * Removes the list of movies associated with a channel. Overrides the current list with an
     * empty list in {@link SharedPreferences}.
     *
     * @param context used for accessing shared preferences.
     * @param channelId of the channel that the movies are associated with.
     */
    public static void removeMovies(Context context, long channelId) {
        saveMovies(context, channelId, Collections.<Movie>emptyList());
    }

    /**
     * Finds movie in subscriptions with channel id and updates it. Otherwise will add the new movie
     * to the subscription.
     *
     * @param context to access shared preferences.
     * @param channelId of the subscription that the movie is associated with.
     * @param movie to be persisted or updated.
     */
    public static void saveMovie(Context context, long channelId, Movie movie) {
        List<Movie> movies = getMovies(context, channelId);
        int index = findMovie(movies, movie);
        if (index == -1) {
            movies.add(movie);
        } else {
            movies.set(index, movie);
        }
        saveMovies(context, channelId, movies);
    }

    private static int findMovie(List<Movie> movies, Movie movie) {
        for (int index = 0; index < movies.size(); ++index) {
            Movie current = movies.get(index);
            if (current.getId() == movie.getId()) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Returns movies stored in {@link SharedPreferences} for a given subscription.
     *
     * @param context used for accessing shared preferences.
     * @param channelId of the subscription that the movie is associated with.
     * @return a list of movies for a subscription
     */
    public static List<Movie> getMovies(Context context, long channelId) {
        return SharedPreferencesHelper.readMovies(context, channelId);
    }

    /**
     * Finds a movie in a subscription by its id.
     *
     * @param context to access shared preferences.
     * @param channelId of the subscription that the movie is associated with.
     * @param movieId of the movie.
     * @return a movie or null if none exist.
     */
    @Nullable
    public static Movie findMovieById(Context context, long channelId, long movieId) {
        for (Movie movie : getMovies(context, channelId)) {
            if (movie.getId() == movieId) {
                return movie;
            }
        }
        return null;
    }
}
