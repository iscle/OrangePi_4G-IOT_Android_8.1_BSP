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

import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.util.Size;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Log;

/**
 * Abstract the information necessary to create new {@link Allocation allocations} with
 * their size, element, type, and usage.
 *
 * <p>This also includes convenience functions for printing to a string, something RenderScript
 * lacks at the time of writing.</p>
 *
 * <p>Note that when creating a new {@link AllocationInfo} the usage flags <b>always</b> get ORd
 * to {@link Allocation#USAGE_IO_SCRIPT}.</p>
 */
public class AllocationInfo {

    private final RenderScript mRS = RenderScriptSingleton.getRS();

    private final Size mSize;
    private final Element mElement;
    private final Type mType;
    private final int mUsage;

    private static final String TAG = "AllocationInfo";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    /**
     * Create a new {@link AllocationInfo} holding the element, size, and usage
     * from an existing {@link Allocation}.
     *
     * @param allocation {@link Allocation}
     *
     * @return A new {@link AllocationInfo}
     *
     * @throws NullPointerException if allocation was {@code null}.
     */
    public static AllocationInfo newInstance(Allocation allocation) {
        checkNotNull("allocation", allocation);

        return new AllocationInfo(allocation.getElement(),
                new Size(allocation.getType().getX(), allocation.getType().getY()),
                allocation.getUsage());
    }

    /**
     * Create a new {@link AllocationInfo} holding the specified format, {@link Size},
     * and {@link Allocation#USAGE_SCRIPT usage}.
     *
     * <p>The usage is always ORd with {@link Allocation#USAGE_SCRIPT}.</p>
     *
     * <p>The closest {@link Element} possible is created from the format.</p>
     *
     * @param size {@link Size}
     * @param format An int format
     * @param usage Usage flags
     *
     * @return A new {@link AllocationInfo} holding the given arguments.
     *
     * @throws NullPointerException if size was {@code null}.
     *
     * @see ImageFormat
     * @see PixelFormat
     */
    public static AllocationInfo newInstance(Size size, int format, int usage) {
        RenderScript rs = RenderScriptSingleton.getRS();

        Element element;
        switch (format) {
            case ImageFormat.YUV_420_888:
                element = Element.YUV(rs);
                break;
            case PixelFormat.RGBA_8888:
                element = Element.RGBA_8888(rs);
                break;
            // TODO: map more formats here
            default:
                throw new UnsupportedOperationException("Unsupported format " + format);
        }

        return new AllocationInfo(element, size, usage);
    }


    /**
     * Create a new {@link AllocationInfo} holding the specified format, {@link Size},
     * with the default usage.
     *
     * <p>The default usage is always {@link Allocation#USAGE_SCRIPT}.</p>
     *
     * <p>The closest {@link Element} possible is created from the format.</p>
     *
     * @param size {@link Size}
     * @param format An int format
     *
     * @return A new {@link AllocationInfo} holding the given arguments.
     *
     * @throws NullPointerException if size was {@code null}.
     *
     * @see ImageFormat
     * @see PixelFormat
     */
    public static AllocationInfo newInstance(Size size, int format) {
        return newInstance(size, format, Allocation.USAGE_SCRIPT);
    }

    /**
     * Create a new {@link AllocationInfo} holding the specified {@link Element}, {@link Size},
     * with the default usage.
     *
     * <p>The default usage is always {@link Allocation#USAGE_SCRIPT}.</p>
     *
     * @param element {@link Element}
     * @param size {@link Size}
     *
     * @return A new {@link AllocationInfo} holding the given arguments.
     *
     * @throws NullPointerException if size was {@code null}.
     * @throws NullPointerException if element was {@code null}.
     */
    public static AllocationInfo newInstance(Element element, Size size) {
        return new AllocationInfo(element, size, Allocation.USAGE_SCRIPT);
    }

    /**
     * Create a new {@link AllocationInfo} holding the specified {@link Element}, {@link Size},
     * and {@link Allocation#USAGE_SCRIPT usage}.
     *
     * <p>The usage is always ORd with {@link Allocation#USAGE_SCRIPT}.</p>
     *
     * @param element {@link Element}
     * @param size {@link Size}
     * @param usage usage flags
     *
     * @return A new {@link AllocationInfo} holding the given arguments.
     *
     * @throws NullPointerException if size was {@code null}.
     * @throws NullPointerException if element was {@code null}.
     */
    public static AllocationInfo newInstance(Element element, Size size, int usage) {
        return new AllocationInfo(element, size, usage);
    }

    /**
     * Create a new {@link AllocationInfo} by copying the existing data but appending
     * the new usage flags to the old usage flags.
     *
     * @param usage usage flags
     *
     * @return A new {@link AllocationInfo} with new usage flags ORd to the old ones.
     */
    public AllocationInfo addExtraUsage(int usage) {
        return new AllocationInfo(mElement, mSize, mUsage | usage);
    }

    /**
     * Create a new {@link AllocationInfo} by copying the existing data but changing the format,
     * and appending the new usage flags to the old usage flags.
     *
     * @param format Format
     * @param usage usage flags
     *
     * @return A new {@link AllocationInfo} with new format/usage.
     *
     * @see ImageFormat
     * @see PixelFormat
     */
    public AllocationInfo changeFormatAndUsage(int format, int usage) {
        return newInstance(getSize(), format, usage);
    }

    /**
     * Create a new {@link AllocationInfo} by copying the existing data but replacing the old
     * usage with the new usage flags.
     *
     * @param usage usage flags
     *
     * @return A new {@link AllocationInfo} with new format/usage.
     *
     * @see ImageFormat
     * @see PixelFormat
     */
    public AllocationInfo changeElementWithDefaultUsage(Element element) {
        return newInstance(element, getSize());
    }

    /**
     * Create a new {@link AllocationInfo} by copying the existing data but changing the format,
     * and replacing the old usage flags with default usage flags.
     *
     * @param format Format
     *
     * @return A new {@link AllocationInfo} with new format/usage.
     *
     * @see ImageFormat
     * @see PixelFormat
     */
    public AllocationInfo changeFormatWithDefaultUsage(int format) {
        return newInstance(getSize(), format, Allocation.USAGE_SCRIPT);
    }

    private AllocationInfo(Element element, Size size, int usage) {
        checkNotNull("element", element);
        checkNotNull("size", size);

        mElement = element;
        mSize = size;
        mUsage = usage;

        Type.Builder typeBuilder = typeBuilder(element, size);

        if (element.equals(Element.YUV(mRS))) {
            typeBuilder.setYuvFormat(ImageFormat.YUV_420_888);
        }

        mType = typeBuilder.create();
    }

    /**
     * Get the {@link Type type} for this info.
     *
     * <p>Note that this is the same type that would get used by the {@link Allocation}
     * created with {@link #createAllocation()}.
     *
     * @return The type (never {@code null}).
     */
    public Type getType() {
        return mType;
    }

    /**
     * Get the usage.
     *
     * <p>The bit for {@link Allocation#USAGE_SCRIPT} will always be set to 1.</p>
     *
     * @return usage flags
     */
    public int getUsage() {
        return mUsage;
    }

    /**
     * Get the size.
     *
     * @return The size (never {@code null}).
     */
    public Size getSize() {
        return mSize;
    }

    /**
     * Get the {@link Element}.
     *
     * @return The element (never {@code null}).
     */
    public Element getElement() {
        return mElement;
    }

    /**
     * Convenience enum to represent commonly-used elements without needing a RenderScript object.
     */
    public enum ElementInfo {
        YUV,
        RGBA_8888,
        U8_3,
        U8_4;

        private static final String TAG = "ElementInfo";

        /**
         * Create an {@link ElementInfo} by converting it from a {@link Element}.
         *
         * @param element The element for which you want to get an enum for.
         *
         * @return The element info is a corresponding one exists, or {@code null} otherwise.
         */
        public static ElementInfo fromElement(Element element) {
            checkNotNull("element", element);

            if (element.equals(Element.YUV(RenderScriptSingleton.getRS()))) {
                return YUV;
            } else if (element.equals(Element.RGBA_8888(RenderScriptSingleton.getRS()))) {
                return RGBA_8888;
            } else if (element.equals(Element.U8_3(RenderScriptSingleton.getRS()))) {
                return U8_3;
            } else if (element.equals(Element.U8_4(RenderScriptSingleton.getRS()))) {
                return U8_4;
            }
            // TODO: add more comparisons here as necessary

            Log.w(TAG, "Unknown element of data kind " + element.getDataKind());
            return null;
        }
    }

    /**
     * Compare the current element against the suggested element (info).
     *
     * @param element The other element to compare against.
     *
     * @return true if the elements are equal, false otherwise.
     */
    public boolean isElementEqualTo(ElementInfo element) {
        checkNotNull("element", element);

        Element comparison;
        switch (element) {
            case YUV:
                comparison = Element.YUV(mRS);
                break;
            case RGBA_8888:
                comparison = Element.RGBA_8888(mRS);
                break;
            case U8_3:
                comparison = Element.U8_3(mRS);
                break;
            case U8_4:
                comparison = Element.U8_4(mRS);
                break;
            default:
            // TODO: add more comparisons here as necessary
                comparison = null;
        }

        return mElement.equals(comparison);
    }

    /**
     * Human-readable representation of this info.
     */
    @Override
    public String toString() {
        return String.format("Size: %s, Element: %s, Usage: %x", mSize,
                ElementInfo.fromElement(mElement), mUsage);
    }

    /**
     * Compare against another object.
     *
     * <p>Comparisons against objects that are not instances of {@link AllocationInfo}
     * always return {@code false}.</p>
     *
     * <p>Two {@link AllocationInfo infos} are considered equal only if their elements,
     * sizes, and usage flags are also equal.</p>
     *
     * @param other Another info object
     *
     * @return true if this is equal to other
     */
    @Override
    public boolean equals(Object other) {
        if (other instanceof AllocationInfo) {
            return equals((AllocationInfo)other);
        } else {
            return false;
        }
    }

    /**
     * Compare against another object.
     *
     * <p>Two {@link AllocationInfo infos} are considered equal only if their elements,
     * sizes, and usage flags are also equal.</p>
     *
     * @param other Another info object
     *
     * @return true if this is equal to other
     */
    public boolean equals(AllocationInfo other) {
        if (other == null) {
            return false;
        }

        // Element, Size equality is already incorporated into Type equality
        return mType.equals(other.mType) && mUsage == other.mUsage;
    }

    /**
     * Create a new {@link Allocation} using the {@link #getType type} and {@link #getUsage usage}
     * from this info object.
     *
     * <p>The allocation is always created from a {@link AllocationCache cache}. If possible,
     * return it to the cache once done (although this is not necessary).</p>
     *
     * @return a new {@link Allocation}
     */
    public Allocation createAllocation() {
        if (VERBOSE) Log.v(TAG, "createAllocation - for info =" + toString());
        return RenderScriptSingleton.getCache().getOrCreateTyped(mType, mUsage);
    }

    /**
     * Create a new {@link Allocation} using the {@link #getType type} and {@link #getUsage usage}
     * from this info object; immediately wrap inside a new {@link BlockingInputAllocation}.
     *
     * <p>The allocation is always created from a {@link AllocationCache cache}. If possible,
     * return it to the cache once done (although this is not necessary).</p>
     *
     * @return a new {@link Allocation}
     *
     * @throws IllegalArgumentException
     *            If the usage did not have one of {@code USAGE_IO_INPUT} or {@code USAGE_IO_OUTPUT}
     */
    public BlockingInputAllocation createBlockingInputAllocation() {
        Allocation alloc = createAllocation();
        return BlockingInputAllocation.wrap(alloc);
    }

    private static Type.Builder typeBuilder(Element element, Size size) {
        Type.Builder builder = (new Type.Builder(RenderScriptSingleton.getRS(), element))
                .setX(size.getWidth())
                .setY(size.getHeight());

        return builder;
    }
}
