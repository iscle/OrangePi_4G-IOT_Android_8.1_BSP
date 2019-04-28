/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.platform.longevity.scheduler;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import android.os.Bundle;
import android.support.test.filters.SmallTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import org.mockito.Mockito;

/**
 * Unit test the logic for {@link Shuffle}
 */
@RunWith(JUnit4.class)
public class ShuffleTest {
    private static final int NUM_TESTS = 10;
    private static final long SEED_VALUE = new Random().nextLong();

    private Shuffle mShuffle = new Shuffle();

    /**
     * Unit test that shuffling with a specific seed is respected.
     */
    @Test
    @SmallTest
    public void testShuffleSeedRespected()  {
        // Construct argument bundle.
        Bundle args = new Bundle();
        args.putString(Shuffle.SHUFFLE_OPTION_NAME, "true");
        args.putString(Shuffle.SEED_OPTION_NAME, String.valueOf(SEED_VALUE));
        // Construct input runners.
        List<Runner> input = new ArrayList<>();
        IntStream.range(1, NUM_TESTS).forEach(i -> input.add(getMockRunner(i)));
        // Apply shuffler on arguments and runners.
        List<Runner> output = mShuffle.apply(args, new ArrayList(input));
        // Shuffle locally against the same seed and compare results.
        Collections.shuffle(input, new Random(SEED_VALUE));
        assertThat(input).isEqualTo(output);
    }

    private Runner getMockRunner (int id) {
        Runner result = Mockito.mock(Runner.class);
        Description desc = Mockito.mock(Description.class);
        when(result.getDescription()).thenReturn(desc);
        when(desc.getDisplayName()).thenReturn(String.valueOf(id));
        return result;
    }
}
