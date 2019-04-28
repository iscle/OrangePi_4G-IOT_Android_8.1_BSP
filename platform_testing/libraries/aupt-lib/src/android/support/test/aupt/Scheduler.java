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

package android.support.test.aupt;

import junit.framework.TestCase;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * A Scheduler produces an execution ordering for our test cases.
 *
 * For example, the Sequential scheduler will just repeat our test cases a fixed number of times.
 */
public abstract class Scheduler {

    /** Shuffle and/or iterate through a list of test cases. */
    public final Iterable<TestCase> apply(final List<TestCase> cases) {
        return new Iterable<TestCase>() {
            public Iterator<TestCase> iterator() {
                return applyInternal(cases);
            }
        };
    }

    /**
     * A Scheduler that just loops through test cases a fixed number of times.
     *
     * @param iterations the number of times to loop through every test case.
     */
    public static Scheduler sequential(Long iterations) {
        return new Sequential(iterations);
    }

    /**
     * A Scheduler that permutes the test case list, then just iterates.
     *
     * @param iterations the number of times to loop through every test case.
     * @param random the Random to use: with the same random, we'll return the same ordering.
     */
    public static Scheduler shuffled(Random random, Long iterations) {
        return new Shuffled(random, iterations);
    }

    /** Private interface to Scheduler::apply */
    protected abstract Iterator<TestCase> applyInternal(List<TestCase> cases);

    private static class Sequential extends Scheduler {
        private final Long mIterations;

        Sequential(Long iterations) {
            mIterations = iterations;
        }

        protected Iterator<TestCase> applyInternal (final List<TestCase> cases) {
            return new Iterator<TestCase>() {
                private int count = 0;

                public boolean hasNext() {
                    return count < (cases.size() * mIterations);
                }

                public TestCase next() {
                    return cases.get(count++ % cases.size());
                }

                public void remove() { }
            };
        }
    }

    private static class Shuffled extends Scheduler {
        private final Random mRandom;
        private final Long mIterations;

        Shuffled(Random random, Long iterations) {
            mRandom = random;
            mIterations = iterations;
        }

        /**
         * Find a GCD by the nieve Euclidean Algorithm
         * TODO: get this from guava or some other library
         */
        private int gcd(final int _a, final int _b) {
            int a = _a;
            int b = _b;

            while (b > 0) {
                int tmp = b;
                b = a % b;
                a = tmp;
            }

            return a;
        }

        /**
         * Find a random number relatively prime to our modulus
         * TODO: get this from guava or some other library
         */
        private int randomRelPrime(Integer modulus) {
            if (modulus <= 1) {
                return 1;
            } else {
                int x = 0;

                // Sample random numbers until we get something coprime to our modulus
                while (gcd(x, modulus) != 1) {
                    x = mRandom.nextInt() % modulus;
                }

                return x % modulus;
            }
        }

        /**
         * Return the tests in a shuffled order using a simple linear congruential generator: i.e.
         * the elements are permuted by (a x + b % n), will produce a permutation of the elements of n
         * iff a is coprime to n.
         * <p>
         * The reason to do this is that it also produces a permutation each cases.size() rounds, which
         * is *not* the case for Collections.shuffle(); and implementing a comparable iterator with
         * those primitives is somewhat less elegant.
         */
        protected Iterator<TestCase> applyInternal(final List<TestCase> cases) {
            final int a = randomRelPrime(cases.size());
            final int b = Math.abs(mRandom.nextInt());

            return new Iterator<TestCase>() {
                private int count = 0;

                public boolean hasNext() {
                    return count < (cases.size() * mIterations);
                }

                public TestCase next() {
                    return cases.get((a * (count++) + b) % cases.size());
                }

                public void remove() {
                }
            };
        }
    }
}
