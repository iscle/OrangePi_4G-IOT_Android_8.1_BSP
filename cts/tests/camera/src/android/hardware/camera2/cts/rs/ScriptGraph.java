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
import static junit.framework.Assert.*;

import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.util.Size;
import android.hardware.camera2.cts.helpers.MaybeNull;
import android.hardware.camera2.cts.helpers.UncheckedCloseable;
import android.hardware.camera2.cts.rs.Script.ParameterMap;
import android.renderscript.Allocation;
import android.util.Log;
import android.view.Surface;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import com.android.ex.camera2.exceptions.TimeoutRuntimeException;

/**
 * An abstraction to simplify chaining together the execution of multiple RenderScript
 * {@link android.renderscript.Script scripts} and managing their {@link Allocation allocations}.
 *
 * <p>Create a new script graph by using {@link #create}, configure the input with
 * {@link Builder#configureInput}, then configure one or more scripts with
 * {@link Builder#configureScript} or {@link Builder#chainScript}. Finally, freeze the graph
 * with {@link Builder#buildGraph}.</p>
 *
 * <p>Once a script graph has been built, all underlying scripts and allocations are instantiated.
 * Each script may be executed with {@link #execute}. Scripts are executed in the order that they
 * were configured, with each previous script's output used as the input for the next script.
 * </p>
 *
 * <p>In case the input {@link Allocation} is actually backed by a {@link Surface}, convenience
 * methods ({@link #advanceInputWaiting} and {@link #advanceInputAndDrop} are provided to
 * automatically update the {@link Allocation allocation} with the latest buffer available.</p>
 *
 * <p>All resources are managed by the {@link ScriptGraph} and {@link #close closing} the graph
 * will release all underlying resources. See {@link #close} for more details.</p>
 */
public class ScriptGraph implements UncheckedCloseable {

    private static final String TAG = "ScriptGraph";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    private static final int INPUT_SCRIPT_LOCATION = 0;
    private final int OUTPUT_SCRIPT_LOCATION; // calculated in constructor

    private final AllocationCache mCache = RenderScriptSingleton.getCache();

    private final Size mSize;
    private final int mFormat;
    private final int mUsage;
    private final List<Script<?>> mScripts;

    private final BlockingInputAllocation mInputBlocker;
    private final Allocation mOutputAllocation;
    private boolean mClosed = false;

    /**
     * Create a new {@link Builder} that will be used to configure the graph's inputs
     * and scripts (and parameters).
     *
     * <p>Once a graph has been fully built, the configuration is immutable.</p>
     *
     * @return a {@link Builder} that will be used to configure the graph settings
     */
    public static Builder create() {
        return new Builder();
    }

    /**
     * Wait until another buffer is produced into the input {@link Surface}, then
     * update the backing input {@link Allocation} with the latest buffer with
     * {@link Allocation#ioReceive ioReceive}.
     *
     * @throws IllegalArgumentException
     *            if the graph wasn't configured with
     *            {@link Builder#configureInputWithSurface configureInputWithSurface}
     * @throws TimeoutRuntimeException
     *            if waiting for the buffer times out
     */
    public void advanceInputWaiting() {
        checkNotClosed();
        if (!isInputFromSurface()) {
            throw new IllegalArgumentException("Graph was not configured with USAGE_IO_INPUT");
        }

        mInputBlocker.waitForBufferAndReceive();
    }

    /**
     * Update the backing input {@link Allocation} with the latest buffer with
     * {@link Allocation#ioReceive ioReceive} repeatedly until no more buffers are pending.
     *
     * <p>Does not wait for new buffers to become available if none are currently available
     * (i.e. {@code false} is returned immediately).</p>
     *
     * @return true if any buffers were pending
     *
     * @throws IllegalArgumentException
     *            if the graph wasn't configured with
     *            {@link Builder#configureInputWithSurface configureInputWithSurface}
     * @throws TimeoutRuntimeException
     *            if waiting for the buffer times out
     */
    public boolean advanceInputAndDrop() {
        checkNotClosed();
        if (!isInputFromSurface()) {
            throw new IllegalArgumentException("Graph was not configured with USAGE_IO_INPUT");
        }

        return mInputBlocker.receiveLatestAvailableBuffers();
    }

    /**
     * Execute each script in the graph, with each next script's input using the
     * previous script's output.
     *
     * <p>Scripts are executed in the same order that they were configured by the {@link Builder}.
     * </p>
     *
     * @throws IllegalStateException if the graph was already {@link #close closed}
     */
    public void execute() {
        checkNotClosed();

        // TODO: Can we use android.renderscript.ScriptGroup here to make it faster?

        int i = 0;
        for (Script<?> script : mScripts) {
            script.execute();
            i++;
        }

        if (VERBOSE) Log.v(TAG, "execute - invoked " + i + " scripts");
    }

    /**
     * Copies the data from the last script's output {@link Allocation} into a byte array.
     *
     * <p>The output allocation must be of an 8 bit integer
     * {@link android.renderscript.Element Element} type.</p>
     *
     * @return A byte[] copy.
     *
     * @throws IllegalStateException if the graph was already {@link #close closed}
     */
    public byte[] getOutputData() {
        checkNotClosed();

        Allocation outputAllocation = getOutputAllocation();

        byte[] destination = new byte[outputAllocation.getBytesSize()];
        outputAllocation.copyTo(destination);

        return destination;
    }

    /**
     * Copies the data from the first script's input {@link Allocation} into a byte array.
     *
     * <p>The input allocation must be of an 8 bit integer
     * {@link android.renderscript.Element Element} type.</p>
     *
     * @return A byte[] copy.
     *
     * @throws IllegalStateException if the graph was already {@link #close closed}
     */
    public byte[] getInputData() {
        checkNotClosed();

        Allocation inputAllocation = getInputAllocation();

        byte[] destination = new byte[inputAllocation.getBytesSize()];
        inputAllocation.copyTo(destination);

        return destination;
    }

    /**
     * Builds a {@link ScriptGraph} by configuring input size/format/usage,
     * the script classes in the graph, and the parameters passed to the scripts.
     *
     * @see ScriptGraph#create
     */
    public static class Builder {

        private Size mSize;
        private int mFormat;
        private int mUsage;

        private final List<ScriptBuilder<? extends Script<?>>> mChainedScriptBuilders =
                new ArrayList<ScriptBuilder<? extends Script<?>>>();

        /**
         * Configure the {@link Allocation} that will be used as the input to the first
         * script, using the default usage.
         *
         * <p>Short hand for calling {@link #configureInput(int, int, int, int)} with a
         * {@code 0} usage.</p>
         *
         * @param width Width in pixels
         * @param height Height in pixels
         * @param format Format from {@link ImageFormat} or {@link PixelFormat}
         *
         * @return The current builder ({@code this}). Use for chaining method calls.
         */
        public Builder configureInput(int width, int height, int format) {
            return configureInput(new Size(width, height), format, /*usage*/0);
        }

        /**
         * Configure the {@link Allocation} that will be used as the input to the first
         * script.
         *
         * <p>The {@code usage} is always ORd together with {@link Allocation#USAGE_SCRIPT}.</p>
         *
         * @param width Width in pixels
         * @param height Height in pixels
         * @param format Format from {@link ImageFormat} or {@link PixelFormat}
         * @param usage Usage flags such as {@link Allocation#USAGE_IO_INPUT}
         *
         * @return The current builder ({@code this}). Use for chaining method calls.
         */
        public Builder configureInput(int width, int height, int format, int usage) {
            return configureInput(new Size(width, height), format, usage);
        }

        /**
         * Configure the {@link Allocation} that will be used as the input to the first
         * script, using the default usage.
         *
         * <p>Short hand for calling {@link #configureInput(Size, int, int)} with a
         * {@code 0} usage.</p>
         *
         * @param size Size (width, height)
         * @param format Format from {@link ImageFormat} or {@link PixelFormat}
         *
         * @return The current builder ({@code this}). Use for chaining method calls.
         *
         * @throws NullPointerException if size was {@code null}
         */
        public Builder configureInput(Size size, int format) {
            return configureInput(size, format, /*usage*/0);
        }

        /**
         * Configure the {@link Allocation} that will use a {@link Surface} to produce input into
         * the first script.
         *
         * <p>Short hand for calling {@link #configureInput(Size, int, int)} with the
         * {@link Allocation#USAGE_IO_INPUT} usage.</p>
         *
         * <p>The {@code usage} is always ORd together with {@link Allocation#USAGE_SCRIPT}.</p>
         *
         * @param size Size (width, height)
         * @param format Format from {@link ImageFormat} or {@link PixelFormat}
         *
         * @return The current builder ({@code this}). Use for chaining method calls.
         *
         * @throws NullPointerException if size was {@code null}
         */
        public Builder configureInputWithSurface(Size size, int format) {
            return configureInput(size, format, Allocation.USAGE_IO_INPUT);
        }

        /**
         * Configure the {@link Allocation} that will be used as the input to the first
         * script.
         *
         * <p>The {@code usage} is always ORd together with {@link Allocation#USAGE_SCRIPT}.</p>
         *
         * @param size Size (width, height)
         * @param format Format from {@link ImageFormat} or {@link PixelFormat}
         * @param usage Usage flags such as {@link Allocation#USAGE_IO_INPUT}
         *
         * @return The current builder ({@code this}). Use for chaining method calls.
         *
         * @throws NullPointerException if size was {@code null}
         */
        public Builder configureInput(Size size, int format, int usage) {
            checkNotNull("size", size);

            mSize = size;
            mFormat = format;
            mUsage = usage | Allocation.USAGE_SCRIPT;

            return this;
        }

        /**
         * Build a {@link Script} by setting parameters it might require for execution.
         *
         * <p>Refer to the documentation for {@code T} to see if there are any
         * {@link Script.ScriptParameter parameters} in it.
         * </p>
         *
         * @param <T> Concrete type subclassing the {@link Script} class.
         */
        public class ScriptBuilder<T extends Script<?>> {

            private final Class<T> mScriptClass;

            private ScriptBuilder(Class<T> scriptClass) {
                mScriptClass = scriptClass;
            }

            private final ParameterMap<T> mParameterMap = new ParameterMap<T>();

            /**
             * Set a script parameter to the specified value.
             *
             * @param parameter The {@link Script.ScriptParameter parameter key} in {@code T}
             * @param value A value of type {@code K} that the script expects.
             * @param <K> The type of the parameter {@code value}.
             *
             * @return The current builder ({@code this}). Use to chain method calls.
             *
             * @throws NullPointerException if parameter was {@code null}
             * @throws NullPointerException if value was {@code null}
             * @throws IllegalStateException if the parameter was already {@link #set}
             */
            public <K> ScriptBuilder<T> set(Script.ScriptParameter<T, K> parameter, K value) {
                checkNotNull("parameter", parameter);
                checkNotNull("value", value);
                checkState("Parameter has already been set", !mParameterMap.contains(parameter));

                mParameterMap.set(parameter, value);

                return this;
            }

            ParameterMap<T> getParameterMap() {
                return mParameterMap;
            }

            Class<T> getScriptClass() {
                return mScriptClass;
            }

            /**
             * Build the script and freeze the parameter list to what was {@link #set}.
             *
             * @return
             *            The {@link ScriptGraph#Builder} that was used to configure
             *            {@link this} script.</p>
             */
            public Builder buildScript() {
                mChainedScriptBuilders.add(this);

                return Builder.this;
            }
        }

        /**
         * Configure the script with no parameters.
         *
         * <p>Short hand for invoking {@link #configureScript} immediately followed by
         * {@link ScriptBuilder#buildScript()}.
         *
         * @param scriptClass A concrete class that subclasses {@link Script}
         * @return The current builder ({@code this}). Use to chain method calls.
         *
         * @throws NullPointerException if {@code scriptClass} was {@code null}
         */
        public <T extends Script<?>> Builder chainScript(Class<T> scriptClass) {
            checkNotNull("scriptClass", scriptClass);

            return (new ScriptBuilder<T>(scriptClass)).buildScript();
        }

        /**
         * Configure the script with parameters.
         *
         * <p>Only useful when the {@code scriptClass} has one or more
         * {@link Script.ScriptParameter script parameters} defined.</p>
         *
         * @param scriptClass A concrete class that subclasses {@link Script}
         * @return A script configuration {@link ScriptBuilder builder}. Use to chain method calls.
         *
         * @throws NullPointerException if {@code scriptClass} was {@code null}
         */
        public <T extends Script<?>> ScriptBuilder<T> configureScript(Class<T> scriptClass) {
            checkNotNull("scriptClass", scriptClass);

            return new ScriptBuilder<T>(scriptClass);
        }

        /**
         * Finish configuring the graph and freeze the settings, instantiating all
         * the {@link Script scripts} and {@link Allocation allocations}.
         *
         * @return A constructed {@link ScriptGraph}.
         */
        public ScriptGraph buildGraph() {
            return new ScriptGraph(this);
        }

        private Builder() {}
    }

    private ScriptGraph(Builder builder) {
        mSize = builder.mSize;
        mFormat = builder.mFormat;
        mUsage = builder.mUsage;
        List<Builder.ScriptBuilder<? extends Script<?>>> chainedScriptBuilders =
                builder.mChainedScriptBuilders;
        mScripts = new ArrayList<Script<?>>(/*capacity*/chainedScriptBuilders.size());
        OUTPUT_SCRIPT_LOCATION = chainedScriptBuilders.size() - 1;

        if (mSize == null) {
            throw new IllegalArgumentException("Inputs were not configured");
        }

        if (chainedScriptBuilders.isEmpty()) {
            throw new IllegalArgumentException("At least one script should be chained");
        }

        /*
         * The first input is special since it could be USAGE_IO_INPUT.
         */
        AllocationInfo inputInfo = AllocationInfo.newInstance(mSize, mFormat, mUsage);
        Allocation inputAllocation;

        // Create an Allocation with a Surface if the input to the graph requires it
        if (isInputFromSurface()) {
            mInputBlocker = inputInfo.createBlockingInputAllocation();
            inputAllocation = mInputBlocker.getAllocation();
        } else {
            mInputBlocker = null;
            inputAllocation = inputInfo.createAllocation();
        }

        if (VERBOSE) Log.v(TAG, "ScriptGraph() - Instantiating all script classes");

        // Create all scripts.
        for (Builder.ScriptBuilder<? extends Script<?>> scriptBuilder: chainedScriptBuilders) {

            @SuppressWarnings("unchecked")
            Class<Script<?>> scriptClass = (Class<Script<?>>) scriptBuilder.getScriptClass();

            @SuppressWarnings("unchecked")
            ParameterMap<Script<?>> parameters = (ParameterMap<Script<?>>)
                    scriptBuilder.getParameterMap();

            Script<?> script = instantiateScript(scriptClass, inputInfo, parameters);
            mScripts.add(script);

            // The next script's input info is the current script's output info
            inputInfo = script.getOutputInfo();
        }

        if (VERBOSE) Log.v(TAG, "ScriptGraph() - Creating all inputs");

        // Create and wire up all inputs.
        int i = 0;
        Script<?> inputScript = mScripts.get(INPUT_SCRIPT_LOCATION);
        do {
            if (VERBOSE) {
                Log.v(TAG, "ScriptGraph() - Setting input for script " + inputScript.getName());
            }

            inputScript.setInput(inputAllocation);

            i++;

            if (i >= mScripts.size()) {
                break;
            }

            // Use the graph input for the first loop iteration
            inputScript = mScripts.get(i);
            inputInfo = inputScript.getInputInfo();
            inputAllocation = inputInfo.createAllocation();
        } while (true);

        if (VERBOSE) Log.v(TAG, "ScriptGraph() - Creating all outputs");

        // Create and wire up all outputs.
        Allocation lastOutput = null;
        for (i = 0; i < mScripts.size(); ++i) {
            Script<?> script = mScripts.get(i);
            Script<?> nextScript = (i + 1 < mScripts.size()) ? mScripts.get(i + 1) : null;

            // Each script's output uses the next script's input.
            // -- Since the last script has no next script, we own its output allocation.
            lastOutput = (nextScript != null) ? nextScript.getInput()
                                              : script.getOutputInfo().createAllocation();

            if (VERBOSE) {
                Log.v(TAG, "ScriptGraph() - Setting output for script " + script.getName());
            }

            script.setOutput(lastOutput);
        }
        mOutputAllocation = checkNotNull("lastOutput", lastOutput);

        // Done. Safe to execute the graph now.

        if (VERBOSE) Log.v(TAG, "ScriptGraph() - Graph has been built");
    }

    /**
     * Construct the script by instantiating it via reflection.
     *
     * <p>The {@link Script scriptClass} should have a {@code Script(AllocationInfo inputInfo)}
     * constructor if it expects an empty parameter map.</p>
     *
     * <p>If it expects a non-empty parameter map, it should have a
     * {@code Script(AllocationInfo inputInfo, ParameterMap<T> parameterMap)} constructor.</p>
     */
    private static <T extends Script<?>> T instantiateScript(
            Class<T> scriptClass, AllocationInfo inputInfo, ParameterMap<T> parameterMap) {

        Constructor<T> ctor;
        try {
            // TODO: Would be better if we looked at the script class to see if it expects params
            if (parameterMap.isEmpty()) {
                // Script(AllocationInfo inputInfo)
                ctor = scriptClass.getConstructor(AllocationInfo.class);
            } else {
                // Script(AllocationInfo inputInfo, ParameterMap<T> parameterMap)
                ctor = scriptClass.getConstructor(AllocationInfo.class, ParameterMap.class);
            }
        } catch (NoSuchMethodException e) {
            throw new UnsupportedOperationException(
                    "Script class " + scriptClass + " must have a matching constructor", e);
        }

        try {
            if (parameterMap.isEmpty()) {
                return ctor.newInstance(inputInfo);
            } else {
                return ctor.newInstance(inputInfo, parameterMap);
            }
        } catch (InstantiationException e) {
            throw new UnsupportedOperationException(e);
        } catch (IllegalAccessException e) {
            throw new UnsupportedOperationException(e);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedOperationException(e);
        } catch (InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    private boolean isInputFromSurface() {
        return (mUsage & Allocation.USAGE_IO_INPUT) != 0;
    }

    /**
     * Get the input {@link Allocation} that is used by the first script as the input.
     *
     * @return An {@link Allocation} (never {@code null}).
     *
     * @throws IllegalStateException if the graph was already {@link #close closed}
     */
    public Allocation getInputAllocation() {
        checkNotClosed();

        return mScripts.get(INPUT_SCRIPT_LOCATION).getInput();
    }

    /**
     * Get the output {@link Allocation} that is used by the last script as the output.
     *
     * @return An {@link Allocation} (never {@code null}).
     *
     * @throws IllegalStateException if the graph was already {@link #close closed}
     */
    public Allocation getOutputAllocation() {
        checkNotClosed();
        Allocation output = mScripts.get(OUTPUT_SCRIPT_LOCATION).getOutput();

        assertEquals("Graph's output should match last script's output", mOutputAllocation, output);

        return output;
    }

    /**
     * Get the {@link Surface} that can be used produce buffers into the
     * {@link #getInputAllocation input allocation}.
     *
     * @throws IllegalStateException
     *            if input wasn't configured with {@link Allocation#USAGE_IO_INPUT} {@code usage}.
     * @throws IllegalStateException
     *            if the graph was already {@link #close closed}
     *
     * @return A {@link Surface} (never {@code null}).
     */
    public Surface getInputSurface() {
        checkNotClosed();
        checkState("This graph was not configured with IO_USAGE_INPUT", isInputFromSurface());

        return getInputAllocation().getSurface();
    }

    private void checkNotClosed() {
        checkState("ScriptGraph has been closed", !mClosed);
    }

    /**
     * Releases all underlying resources associated with this {@link ScriptGraph}.
     *
     * <p>In particular, all underlying {@link Script scripts} and all
     * {@link Allocation allocations} are also closed.</p>
     *
     * <p>All further calls to any other public methods (other than {@link #close}) will throw
     * an {@link IllegalStateException}.</p>
     *
     * <p>This method is idempotent; calling it more than once will
     * have no further effect.</p>
     */
    @Override
    public synchronized void close() {
        if (mClosed) return;

        for (Script<?> script : mScripts) {
            script.close();
        }
        mScripts.clear();

        MaybeNull.close(mInputBlocker);
        mCache.returnToCache(mOutputAllocation);

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
}
