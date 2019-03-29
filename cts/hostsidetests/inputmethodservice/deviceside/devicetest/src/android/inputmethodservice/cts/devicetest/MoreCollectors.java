package android.inputmethodservice.cts.devicetest;

import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;

/**
 * More stream collectors.
 */
final class MoreCollectors {

    /**
     * Create a collector that collects elements ending at an element that satisfies specified
     * predicate. For example,
     * <pre>
     * Stream.of("a", "b", "c", "d", "c", "d", "e").collect(endingAt(s -> s.equals("d")))
     * </pre>
     * returns {@code Stream.of("a", "b", "c", "d")}.
     *
     * @param predicate a predicator to find a specific element.
     * @param <E> a type of element.
     * @return {@link Collector} object that collects elements ending at an element that is
     *          accepted by {@code predicate}.
     */
    static <E> Collector<E, ?, Stream<E>> endingAt(final Predicate<E> predicate) {
        final BiConsumer<Builder<E>, E> endingAtAccumulator = new BiConsumer<Builder<E>, E>() {
            private boolean mFound = false;

            @Override
            public void accept(final Builder<E> builder, final E element) {
                if (mFound) {
                    return;
                }
                if (predicate.test(element)) {
                    mFound = true;
                }
                builder.accept(element);
            }
        };
        return Collector.of(
                Stream::builder,
                endingAtAccumulator,
                (builder, builder2) -> {
                    throw new UnsupportedOperationException("Do not use on parallel stream.");
                },
                Builder::build);
    }

    /**
     * Create a collector that collects elements starting from an element that satisfies specified
     * predicate. For example,
     * <pre>
     * Stream.of("a", "b", "c", "d", "c", "d", "e").collect(startingFrom(s -> s.equals("d")))
     * </pre>
     * returns {@code Stream.of("d", "c", "d", "e")}.
     *
     * @param predicate a predicator to find a specific element.
     * @param <E> a type of element.
     * @return {@link Collector} object that collects elements starting from an element that is
     *          accepted by {@code predicate}.
     */
    static <E> Collector<E, ?, Stream<E>> startingFrom(final Predicate<E> predicate) {
        final BiConsumer<Builder<E>, E> startingFromAccumulator = new BiConsumer<Builder<E>, E>() {
            private boolean mFound = false;

            @Override
            public void accept(final Builder<E> builder, final E element) {
                if (mFound) {
                    builder.accept(element);
                    return;
                }
                if (predicate.test(element)) {
                    mFound = true;
                    builder.accept(element);
                }
            }
        };
        return Collector.of(
                Stream::builder,
                startingFromAccumulator,
                (builder, builder2) -> {
                    throw new UnsupportedOperationException("Do not use on parallel stream.");
                },
                Builder::build);
    }
}
