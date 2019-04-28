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
package com.android.tv.data;

import static android.media.tv.TvContract.Programs.Genres.COMEDY;
import static android.media.tv.TvContract.Programs.Genres.FAMILY_KIDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.media.tv.TvContentRating;
import android.media.tv.TvContract.Programs.Genres;
import android.os.Parcel;
import android.support.test.filters.SmallTest;

import com.android.tv.data.Program.CriticScore;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tests for {@link Program}.
 */
@SmallTest
public class ProgramTest {
    private static final int NOT_FOUND_GENRE = 987;

    private static final int FAMILY_GENRE_ID = GenreItems.getId(FAMILY_KIDS);

    private static final int COMEDY_GENRE_ID = GenreItems.getId(COMEDY);

    @Test
    public void testBuild() {
        Program program = new Program.Builder().build();
        assertEquals("isValid", false, program.isValid());
    }

    @Test
    public void testNoGenres() {
        Program program = new Program.Builder()
                .setCanonicalGenres("")
                .build();
        assertNullCanonicalGenres(program);
        assertHasGenre(program, NOT_FOUND_GENRE, false);
        assertHasGenre(program, FAMILY_GENRE_ID, false);
        assertHasGenre(program, COMEDY_GENRE_ID, false);
        assertHasGenre(program, GenreItems.ID_ALL_CHANNELS, true);
    }

    @Test
    public void testFamilyGenre() {
        Program program = new Program.Builder()
                .setCanonicalGenres(FAMILY_KIDS)
                .build();
        assertCanonicalGenres(program, FAMILY_KIDS);
        assertHasGenre(program, NOT_FOUND_GENRE, false);
        assertHasGenre(program, FAMILY_GENRE_ID, true);
        assertHasGenre(program, COMEDY_GENRE_ID, false);
        assertHasGenre(program, GenreItems.ID_ALL_CHANNELS, true);
    }

    @Test
    public void testFamilyComedyGenre() {
        Program program = new Program.Builder()
                .setCanonicalGenres(FAMILY_KIDS + ", " + COMEDY)
                .build();
        assertCanonicalGenres(program, FAMILY_KIDS, COMEDY);
        assertHasGenre(program, NOT_FOUND_GENRE, false);
        assertHasGenre(program, FAMILY_GENRE_ID, true);
        assertHasGenre(program, COMEDY_GENRE_ID, true);
        assertHasGenre(program, GenreItems.ID_ALL_CHANNELS, true);
    }

    @Test
    public void testOtherGenre() {
        Program program = new Program.Builder()
                .setCanonicalGenres("other")
                .build();
        assertCanonicalGenres(program);
        assertHasGenre(program, NOT_FOUND_GENRE, false);
        assertHasGenre(program, FAMILY_GENRE_ID, false);
        assertHasGenre(program, COMEDY_GENRE_ID, false);
        assertHasGenre(program, GenreItems.ID_ALL_CHANNELS, true);
    }

    @Test
    public void testParcelable() {
        List<CriticScore> criticScores = new ArrayList<>();
        criticScores.add(new CriticScore("1", "2", "3"));
        criticScores.add(new CriticScore("4", "5", "6"));
        TvContentRating[] ratings = new TvContentRating[2];
        ratings[0] = TvContentRating.unflattenFromString("1/2/3");
        ratings[1] = TvContentRating.unflattenFromString("4/5/6");
        Program p = new Program.Builder()
                .setId(1)
                .setPackageName("2")
                .setChannelId(3)
                .setTitle("4")
                .setSeriesId("5")
                .setEpisodeTitle("6")
                .setSeasonNumber("7")
                .setSeasonTitle("8")
                .setEpisodeNumber("9")
                .setStartTimeUtcMillis(10)
                .setEndTimeUtcMillis(11)
                .setDescription("12")
                .setLongDescription("12-long")
                .setVideoWidth(13)
                .setVideoHeight(14)
                .setCriticScores(criticScores)
                .setPosterArtUri("15")
                .setThumbnailUri("16")
                .setCanonicalGenres(Genres.encode(Genres.SPORTS, Genres.SHOPPING))
                .setContentRatings(ratings)
                .setRecordingProhibited(true)
                .build();
        Parcel p1 = Parcel.obtain();
        Parcel p2 = Parcel.obtain();
        try {
            p.writeToParcel(p1, 0);
            byte[] bytes = p1.marshall();
            p2.unmarshall(bytes, 0, bytes.length);
            p2.setDataPosition(0);
            Program r2 = Program.fromParcel(p2);
            assertEquals(p, r2);
        } finally {
            p1.recycle();
            p2.recycle();
        }
    }

    @Test
    public void testParcelableWithCriticScore() {
        Program program = new Program.Builder()
                .setTitle("MyTitle")
                .addCriticScore(new CriticScore(
                        "default source",
                        "5/10",
                        "https://testurl/testimage.jpg"))
                .build();
        Parcel parcel = Parcel.obtain();
        program.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        Program programFromParcel = Program.CREATOR.createFromParcel(parcel);

        assertNotNull(programFromParcel.getCriticScores());
        assertEquals(programFromParcel.getCriticScores().get(0).source, "default source");
        assertEquals(programFromParcel.getCriticScores().get(0).score, "5/10");
        assertEquals(programFromParcel.getCriticScores().get(0).logoUrl,
                "https://testurl/testimage.jpg");
    }

    private static void assertNullCanonicalGenres(Program program) {
        String[] actual = program.getCanonicalGenres();
        assertNull("Expected null canonical genres but was " + Arrays.toString(actual), actual);
    }

    private static void assertCanonicalGenres(Program program, String... expected) {
        assertEquals("canonical genres", Arrays.asList(expected),
                Arrays.asList(program.getCanonicalGenres()));
    }

    private static void assertHasGenre(Program program, int genreId, boolean expected) {
        assertEquals("hasGenre(" + genreId + ")", expected, program.hasGenre(genreId));
    }
}
