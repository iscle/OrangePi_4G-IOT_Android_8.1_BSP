package android.inputmethodservice.cts.devicetest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Stream;

/**
 * Sequence matcher on {@link Stream}.
 */
final class SequenceMatcher {

    /**
     * Return type of this matcher.
     * @param <E> type of stream element.
     */
    static final class MatchResult<E> {

        private final boolean mMatched;
        private final List<E> mMatchedSequence;

        MatchResult(final boolean matched, final List<E> matchedSequence) {
            mMatched = matched;
            mMatchedSequence = matchedSequence;
        }

        boolean matched() {
            return mMatched;
        }

        List<E> getMatchedSequence() {
            return mMatchedSequence;
        }
    }

    /**
     * Accumulate continuous sequence of elements that satisfy specified {@link Predicate}s.
     * @param <E> type of stream element.
     */
    private static final class SequenceAccumulator<E> {

        private final Predicate<E>[] mPredicates;
        private final List<E> mSequence = new ArrayList<>();

        SequenceAccumulator(final Predicate<E>... predicates) {
            mPredicates = predicates;
        }

        void accumulate(final E element) {
            if (mSequence.isEmpty()) {
                // Search for the first element of sequence.
                if (mPredicates[0].test(element)) {
                    mSequence.add(element);
                }
                return;
            }
            final int currentIndex = mSequence.size();
            if (currentIndex >= mPredicates.length) {
                // Already found sequence.
                return;
            }
            if (mPredicates[currentIndex].test(element)) {
                mSequence.add(element);
            } else {
                // Not continuous, restart searching from the first.
                mSequence.clear();
            }
        }

        MatchResult<E> getResult() {
            return new MatchResult<>(mSequence.size() == mPredicates.length, mSequence);
        }
    }


    /**
     * Create a {@link Collector} that collects continuous sequence of elements that equal to
     * specified {@code elements}. It returns {@link MatchResult<E>} of found such sequence.
     * <pre>
     * Stream.of(1, 2, 3, 4, 5).collect(
     *      SequenceMatcher.of(1)).matched();  // true
     * Stream.of(1, 2, 3, 4, 5).collect(
     *      SequenceMatcher.of(2)).matched();  // true
     * Stream.of(1, 2, 3, 4, 5).collect(
     *      SequenceMatcher.of(0)).matched();  // false
     * Stream.of(1, 2, 3, 4, 5).collect(
     *      SequenceMatcher.of(2, 3, 4)).matched();  // true
     * Stream.of(1, 2, 3, 4, 5).collect(
     *      SequenceMatcher.of(2, 3, 5)).matched();  // false, not continuous.
     * Stream.of(1, 2, 3, 4, 5).collect(
     *      SequenceMatcher.of(2, 1)).matched();  // false
     * Stream.of(1, 2, 3, 4, 5).collect(
     *      SequenceMatcher.of(1, 2, 3, 4, 5, 6)).matched();  // false
     * Stream.of(1, 1, 1, 1, 1).collect(
     *      SequenceMatcher.of(1, 1)).matched();  // true
     * Stream.of(1, 1, 1, 1, 1).collect(
     *      SequenceMatcher.of(1, 1, 1)).matched();  // true
     * Stream.of(1, 1, 1, 1, 1).collect(
     *      SequenceMatcher.of(1, 1, 1, 1, 1, 1)).matched();  // false
     * Stream.of(1, 1, 0, 1, 1).collect(
     *      SequenceMatcher.of(1, 1, 1)).matched();  // false, not continuous.
     * </pre>
     *
     * @param elements elements of matching sequence.
     * @param <E> type of stream element
     * @return {@link MatchResult<E>} of matcher sequence.
     */
    static <E> Collector<E, ?, MatchResult<E>> of(final E... elements) {
        if (elements == null || elements.length == 0) {
            throw new IllegalArgumentException("At least one element.");
        }
        final IntFunction<Predicate<E>[]> arraySupplier = Predicate[]::new;
        return of(Arrays.stream(elements).map(Predicate::isEqual).toArray(arraySupplier));
    }

    /**
     * Create a {@link Collector} that collects continuous sequence of elements that satisfied
     * specified {@code predicates}. It returns {@link MatchResult<E>} of found such sequence.
     * <p>Please see examples in {@link #of(Object...)}.
     *
     * @param predicates array of {@link Predicate<E>} that each of sequence element should satisfy.
     * @param <E> type of stream element.
     * @return {@link MatchResult<E>} of matched sequence.
     */
    static <E> Collector<E, ?, MatchResult<E>> of(final Predicate<E>... predicates) {
        if (predicates == null || predicates.length == 0) {
            throw new IllegalArgumentException("At least one Predicate.");
        }
        return Collector.of(
                () -> new SequenceAccumulator<>(predicates),
                SequenceAccumulator::accumulate,
                (accumulate, accumulate2) -> {
                    throw new UnsupportedOperationException("Do not use on parallel stream.");
                },
                SequenceAccumulator::getResult);
    }
}
