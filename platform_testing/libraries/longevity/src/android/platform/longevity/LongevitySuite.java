package android.platform.longevity;

import android.os.Bundle;
import android.platform.longevity.listeners.BatteryTerminator;
import android.platform.longevity.listeners.ErrorTerminator;
import android.platform.longevity.listeners.TimeoutTerminator;
import android.platform.longevity.scheduler.Iterate;
import android.platform.longevity.scheduler.Shuffle;
import android.support.annotation.VisibleForTesting;
import android.support.test.InstrumentationRegistry;

import java.util.List;
import java.util.function.BiFunction;

import org.junit.runner.Runner;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;
import org.junit.runner.notification.RunNotifier;

/**
 * Using the {@code LongevitySuite} as a runner allows you to run test sequences repeatedly and with
 * shuffling in order to simulate longevity conditions and repeated stress or exercise. For examples
 * look at the bundled sample package.
 *
 * TODO(b/62445871): Provide external documentation.
 */
public final class LongevitySuite<T> extends Suite {
    private static final String QUITTER_OPTION = "quitter";
    private static final String QUITTER_DEFAULT = "false"; // don't quit

    private Bundle mArguments;

    /**
     * Called reflectively on classes annotated with {@code @RunWith(LongevitySuite.class)}
     */
    public LongevitySuite(Class<T> klass, RunnerBuilder builder) throws InitializationError {
        this(klass, builder, InstrumentationRegistry.getArguments());
    }

    /**
     * Called by tests in order to pass in configurable arguments without affecting the registry.
     */
    @VisibleForTesting
    LongevitySuite(Class<T> klass, RunnerBuilder builder, Bundle args)
            throws InitializationError {
        this(klass, constructClassRunners(klass, builder, args), args);
    }

    /**
     * Called by this class once the suite class and runners have been determined.
     */
    private LongevitySuite(Class<T> klass, List<Runner> runners, Bundle args)
            throws InitializationError {
        super(klass, runners);
        mArguments = args;
    }

    /**
     * Constructs the sequence of {@link Runner}s that produce the full longevity test.
     */
    private static List<Runner> constructClassRunners(
                Class<?> suite, RunnerBuilder builder, Bundle args) throws InitializationError {
        // Retrieve annotated suite classes.
        SuiteClasses annotation = suite.getAnnotation(SuiteClasses.class);
        if (annotation == null) {
            throw new InitializationError(String.format(
                    "Longevity suite, '%s', must have a SuiteClasses annotation", suite.getName()));
        }
        // Construct and store custom runners for the full suite.
        BiFunction<Bundle, List<Runner>, List<Runner>> modifier =
            new Iterate().andThen(new Shuffle());
        return modifier.apply(args, builder.runners(suite, annotation.value()));
    }

    @Override
    public void run(final RunNotifier notifier) {
        // Add action terminators for custom runner logic.
        notifier.addListener(
                new BatteryTerminator(notifier, mArguments, InstrumentationRegistry.getContext()));
        notifier.addListener(
                new TimeoutTerminator(notifier, mArguments));
        if (Boolean.parseBoolean(
                mArguments.getString(QUITTER_OPTION, String.valueOf(QUITTER_DEFAULT)))) {
            notifier.addListener(new ErrorTerminator(notifier));
        }
        // Invoke tests to run through super call.
        super.run(notifier);
    }
}
