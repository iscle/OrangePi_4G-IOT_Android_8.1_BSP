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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import org.mockito.Mockito;

/**
 * Unit test the logic for {@link Iterate}
 */
@RunWith(JUnit4.class)
public class IterateTest {
    private static final int NUM_TESTS = 10;
    private static final int TEST_ITERATIONS = 25;

    private Iterate mIterate = new Iterate();

    /**
     * Unit test the iteration count is respected.
     */
    @Test
    @SmallTest
    public void testIterationsRespected() {
        // Construct argument bundle.
        Bundle args = new Bundle();
        args.putString(Iterate.OPTION_NAME, String.valueOf(TEST_ITERATIONS));
        // Construct input runners.
        List<Runner> input = new ArrayList<>();
        IntStream.range(1, NUM_TESTS).forEach(i -> input.add(getMockRunner(i)));
        // Apply iterator on arguments and runners.
        List<Runner> output = mIterate.apply(args, input);
        // Count occurrences of test descriptions into a map.
        Map<String, Long> countMap = output.stream()
            .map(Runner::getDescription)
                .map(Description::getDisplayName)
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        // Ensure all test descriptions have N entries.
        boolean respected = countMap.entrySet().stream()
            .noneMatch(entry -> (entry.getValue() != TEST_ITERATIONS));
        assertThat(respected).isTrue();
    }

    private Runner getMockRunner (int id) {
        Runner result = Mockito.mock(Runner.class);
        Description desc = Mockito.mock(Description.class);
        when(result.getDescription()).thenReturn(desc);
        when(desc.getDisplayName()).thenReturn(String.valueOf(id));
        return result;
    }
}
