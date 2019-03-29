/*
 * Copyright 2014 The Android Open Source Project
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

package android.hardware.camera2.cts.rs;

import static android.hardware.camera2.cts.helpers.Preconditions.*;

import android.hardware.camera2.cts.helpers.UncheckedCloseable;
import android.renderscript.Allocation;
import android.renderscript.RenderScript;
import android.util.Log;

import java.util.HashMap;

/**
 * Base class for all renderscript script abstractions.
 *
 * <p>Each script has exactly one input and one output allocation, and is able to execute
 * one {@link android.renderscript.Script} script file.</p>
 *
 * <p>Each script owns it's input allocation, but not the output allocation.</p>
 *
 * <p>Subclasses of this class must implement exactly one of two constructors:
 * <ul>
 * <li>{@code ScriptSubclass(AllocationInfo inputInfo)}
 *  - if it expects 0 parameters
 * <li>{@code ScriptSubclass(AllocationInfo inputInfo, ParameterMap<T> parameterMap))}
 *  - if it expects 1 or more parameters
 * </ul>
 *
 * @param <T> A concrete subclass of {@link android.renderscript.Script}
 */
public abstract class Script<T extends android.renderscript.Script> implements UncheckedCloseable {

    /**
     * A type-safe heterogenous parameter map for script parameters.
     *
     * @param <ScriptT> A concrete subclass of {@link Script}.
     */
    public static class ParameterMap<ScriptT extends Script<?>> {
        private final HashMap<Script.ScriptParameter<ScriptT, ?>, Object> mParameterMap =
                new HashMap<Script.ScriptParameter<ScriptT, ?>, Object>();

        /**
         * Create a new parameter map with 0 parameters.</p>
         */
        public ParameterMap() {}

        /**
         * Get the value associated with the given parameter key.
         *
         * @param parameter A type-safe key corresponding to a parameter.
         *
         * @return The value, or {@code null} if none was set.
         *
         * @param <T> The type of the value
         *
         * @throws NullPointerException if parameter was {@code null}
         */
        @SuppressWarnings("unchecked")
        public <T> T get(Script.ScriptParameter<ScriptT, T> parameter) {
            checkNotNull("parameter", parameter);

            return (T) mParameterMap.get(parameter);
        }

        /**
         * Sets the value associated with the given parameter key.
         *
         * @param parameter A type-safe key corresponding to a parameter.
         * @param value The value
         *
         * @param <T> The type of the value
         *
         * @throws NullPointerException if parameter was {@code null}
         * @throws NullPointerException if value was {@code null}
         */
        public <T> void set(Script.ScriptParameter<ScriptT, T> parameter, T value) {
            checkNotNull("parameter", parameter);
            checkNotNull("value", value);

            if (!parameter.getValueClass().isInstance(value)) {
                throw new IllegalArgumentException(
                        "Runtime type mismatch between " + parameter + " and value " + value);
            }

            mParameterMap.put(parameter, value);
        }

        /**
         * Whether or not at least one parameter has been {@link #set}.
         *
         * @return true if there is at least one element in the map
         */
        public boolean isEmpty() {
            return mParameterMap.isEmpty();
        }

        /**
         * Check if the parameter has been {@link #set} to a value.
         *
         * @param parameter A type-safe key corresponding to a parameter.
         * @return true if there is a value corresponding to this parameter, false otherwise.
         */
        public boolean contains(Script.ScriptParameter<ScriptT, ?> parameter) {
            checkNotNull("parameter", parameter);

            return mParameterMap.containsKey(parameter);
        }
    }

    /**
     * A type-safe parameter key to be used with {@link ParameterMap}.
     *
     * @param <J> A concrete subclass of {@link Script}.
     * @param <K> The type of the value that the parameter holds.
     */
    public static class ScriptParameter<J extends Script<?>, K> {
        private final Class<J> mScriptClass;
        private final Class<K> mValueClass;

        ScriptParameter(Class<J> jClass, Class<K> kClass) {
            checkNotNull("jClass", jClass);
            checkNotNull("kClass", kClass);

            mScriptClass = jClass;
            mValueClass = kClass;
        }

        /**
         * Get the runtime class associated with the value.
         */
        public Class<K> getValueClass() {
            return mValueClass;
        }

        /**
         * Compare with another object.
         *
         * <p>Two script parameters are considered equal only if their script class and value
         * class are both equal.</p>
         */
        @SuppressWarnings("unchecked")
        @Override
        public boolean equals(Object other) {
            if (other instanceof ScriptParameter) {
                ScriptParameter<J, K> otherParam = (ScriptParameter<J,K>) other;

                return mScriptClass.equals(otherParam.mScriptClass) &&
                        mValueClass.equals(otherParam.mValueClass);
            }

            return false;
        }

        /**
         * Gets the hash code for this object.
         */
        @Override
        public int hashCode() {
            return mScriptClass.hashCode() ^ mValueClass.hashCode();
        }
    }

    private static final String TAG = "Script";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    protected final AllocationCache mCache = RenderScriptSingleton.getCache();
    protected final RenderScript mRS = RenderScriptSingleton.getRS();

    protected final AllocationInfo mInputInfo;
    protected final AllocationInfo mOutputInfo;

    protected Allocation mOutputAllocation;
    protected Allocation mInputAllocation;

    protected final T mScript;
    private boolean mClosed = false;

    /**
     * Gets the {@link AllocationInfo info} associated with this script's input.
     *
     * @return A non-{@code null} {@link AllocationInfo} object.
     *
     * @throws IllegalStateException If the script has already been {@link #close closed}.
     */
    public AllocationInfo getInputInfo() {
        checkNotClosed();

        return mInputInfo;
    }
    /**
     * Gets the {@link AllocationInfo info} associated with this script's output.
     *
     * @return A non-{@code null} {@link AllocationInfo} object.
     *
     * @throws IllegalStateException If the script has already been {@link #close closed}.
     */
    public AllocationInfo getOutputInfo() {
        checkNotClosed();

        return mOutputInfo;
    }

    /**
     * Set the input.
     *
     * <p>Must be called before executing any scripts.</p>
     *
     * @throws IllegalStateException If the script has already been {@link #close closed}.
     */
    void setInput(Allocation allocation) {
        checkNotClosed();
        checkNotNull("allocation", allocation);
        checkEquals("allocation info", AllocationInfo.newInstance(allocation),
                "input info", mInputInfo);

        // Scripts own the input, so return old input to cache if the input changes
        if (mInputAllocation != allocation) {
            mCache.returnToCacheIfNotNull(mInputAllocation);
        }

        mInputAllocation = allocation;
        updateScriptInput();
    }

    protected abstract void updateScriptInput();

    /**
     * Set the output.
     *
     * <p>Must be called before executing any scripts.</p>
     *
     * @throws IllegalStateException If the script has already been {@link #close closed}.
     */
    void setOutput(Allocation allocation) {
        checkNotClosed();
        checkNotNull("allocation", allocation);
        checkEquals("allocation info", AllocationInfo.newInstance(allocation),
                "output info", mOutputInfo);

        // Scripts do not own the output, simply set a reference to the new one.
        mOutputAllocation = allocation;
    }

    protected Script(AllocationInfo inputInfo, AllocationInfo outputInfo, T rsScript) {
        checkNotNull("inputInfo", inputInfo);
        checkNotNull("outputInfo", outputInfo);
        checkNotNull("rsScript", rsScript);

        mInputInfo = inputInfo;
        mOutputInfo = outputInfo;
        mScript = rsScript;

        if (VERBOSE) {
            Log.v(TAG, String.format("%s - inputInfo = %s, outputInfo = %s, rsScript = %s",
                    getName(), inputInfo, outputInfo, rsScript));
        }
    }

    /**
     * Get the {@link Allocation} associated with this script's input.</p>
     *
     * @return The input {@link Allocation}, which is never {@code null}.
     *
     * @throws IllegalStateException If the script has already been {@link #close closed}.
     */
    public Allocation getInput() {
        checkNotClosed();

        return mInputAllocation;
    }
    /**
     * Get the {@link Allocation} associated with this script's output.</p>
     *
     * @return The output {@link Allocation}, which is never {@code null}.
     *
     * @throws IllegalStateException If the script has already been {@link #close closed}.
     */
    public Allocation getOutput() {
        checkNotClosed();

        return mOutputAllocation;
    }

    /**
     * Execute the script's kernel against the input/output {@link Allocation allocations}.
     *
     * <p>Once this is complete, the output will have the new data available (for either
     * the next script, or to read out with a copy).</p>
     *
     * @throws IllegalStateException If the script has already been {@link #close closed}.
     */
    public void execute() {
        checkNotClosed();

        if (mInputAllocation == null || mOutputAllocation == null) {
            throw new IllegalStateException("Both inputs and outputs must have been set");
        }

        executeUnchecked();
    }

    /**
     * Get the name of this script.
     *
     * <p>The name is the short hand name of the concrete class backing this script.</p>
     *
     * <p>This method works even if the script has already been {@link #close closed}.</p>
     *
     * @return A string representing the script name.
     */
    public String getName() {
        return getClass().getSimpleName();
    }

    protected abstract void executeUnchecked();

    protected void checkNotClosed() {
        if (mClosed) {
            throw new IllegalStateException("Script has been closed");
        }
    }

    /**
     * Destroy the underlying script object and return the input allocation back to the
     * {@link AllocationCache cache}.
     *
     * <p>This method has no effect if called more than once.</p>
     */
    @Override
    public void close() {
        if (mClosed) return;

        // Scripts own the input allocation. They do NOT own outputs.
        mCache.returnToCacheIfNotNull(mInputAllocation);

        mScript.destroy();

        mClosed = true;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    protected static RenderScript getRS() {
        return RenderScriptSingleton.getRS();
    }
}
