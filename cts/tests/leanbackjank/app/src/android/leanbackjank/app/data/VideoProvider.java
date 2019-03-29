/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.leanbackjank.app.data;

import android.leanbackjank.app.model.Movie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Provides synthesized movie data.
 */
public class VideoProvider {
    private static HashMap<String, List<Movie>> sMovieList;
    private static HashMap<String, Movie> sMovieListById;

    public static Movie getMovieById(String mediaId) {
        return sMovieListById.get(mediaId);
    }

    public static HashMap<String, List<Movie>> getMovieList() {
        return sMovieList;
    }

    public static HashMap<String, List<Movie>> buildMedia(int nCategories) {
        if (null != sMovieList) {
            return sMovieList;
        }
        sMovieList = new HashMap<>();
        sMovieListById = new HashMap<>();

        String title = new String();
        String studio = new String();
        for (int i = 0; i < nCategories; i++) {
            String category_name = String.format("Category %d",  i);
            List<Movie> categoryList = new ArrayList<Movie>();
            for (int j = 0; j < 20; j++) {
                String description = "This is description of a movie.";
                title = String.format("Video %d-%d", i, j);
                studio = String.format("Studio %d", (i + j) % 7);
                Movie movie = buildMovieInfo(category_name, title, description, studio);
                sMovieListById.put(movie.getId(), movie);
                categoryList.add(movie);
            }
            sMovieList.put(category_name, categoryList);
        }
        return sMovieList;
    }

    private static Movie buildMovieInfo(String category,
                                        String title,
                                        String description,
                                        String studio) {
        Movie movie = new Movie();
        movie.setId(Movie.getCount());
        Movie.incrementCount();
        movie.setTitle(title);
        movie.setDescription(description);
        movie.setStudio(studio);
        movie.setCategory(category);

        return movie;
    }
}
