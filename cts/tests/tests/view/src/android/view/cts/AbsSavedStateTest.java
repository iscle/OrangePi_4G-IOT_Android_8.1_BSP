/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.AbsSavedState;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AbsSavedStateTest {
    @Test(expected=IllegalArgumentException.class)
    public void testConstructorNullParcelable() {
        new AbsSavedStateImpl((Parcelable) null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullParcel() {
        new AbsSavedStateImpl((Parcel) null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullParcelAndClassLoader() {
        new AbsSavedStateImpl(null, null);
    }

    @Test
    public void testConstructor() {
        AbsSavedState superState = new AbsSavedStateImpl(Parcel.obtain());
        assertSame(AbsSavedState.EMPTY_STATE, superState.getSuperState());

        AbsSavedState s = new AbsSavedStateImpl(superState);
        assertSame(superState, s.getSuperState());

        Parcel source = Parcel.obtain();
        source.writeParcelable(superState, 0);
        source.setDataPosition(0);
        s = new AbsSavedStateImpl(source, AbsSavedStateImpl.class.getClassLoader());
        assertTrue(s.getSuperState() instanceof AbsSavedState);

        source = Parcel.obtain();
        s = new AbsSavedStateImpl(source);
        assertSame(AbsSavedState.EMPTY_STATE, s.getSuperState());

        source = Parcel.obtain();
        source.writeParcelable(superState, 0);
        source.setDataPosition(0);
        s = new AbsSavedStateImpl(source, AbsSavedStateImpl.class.getClassLoader());
        assertTrue(s.getSuperState() instanceof AbsSavedState);

        source = Parcel.obtain();
        s = new AbsSavedStateImpl(source, AbsSavedState.class.getClassLoader());
        assertSame(AbsSavedState.EMPTY_STATE, s.getSuperState());
    }

    @Test
    public void testCreator() {
        int size = 10;
        AbsSavedState[] array = AbsSavedState.CREATOR.newArray(size);
        assertNotNull(array);
        assertEquals(size, array.length);
        for (AbsSavedState state : array) {
            assertNull(state);
        }

        AbsSavedState state = new AbsSavedStateImpl(AbsSavedState.EMPTY_STATE);
        Parcel parcel = Parcel.obtain();
        state.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AbsSavedState unparceled = AbsSavedState.CREATOR.createFromParcel(parcel);
        assertNotNull(unparceled);
        assertNull(unparceled.getSuperState());

        AbsSavedState stateWithSuper = new AbsSavedStateImpl(state);
        parcel = Parcel.obtain();
        stateWithSuper.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        if (AbsSavedState.CREATOR instanceof Parcelable.ClassLoaderCreator) {
            try {
                ((Parcelable.ClassLoaderCreator) AbsSavedState.CREATOR).createFromParcel(parcel,
                        AbsSavedStateImpl.class.getClassLoader());
                fail("Expected IllegalStateException");
            } catch (IllegalStateException e) {
                // Expected.
            }
        }
    }

    @Test
    public void testWriteToParcel() {
        Parcelable superState = mock(Parcelable.class);
        AbsSavedState savedState = new AbsSavedStateImpl(superState);
        Parcel dest = Parcel.obtain();
        int flags = 2;
        savedState.writeToParcel(dest, flags);
        verify(superState).writeToParcel(eq(dest), eq(flags));
    }

    private static class AbsSavedStateImpl extends AbsSavedState {
        AbsSavedStateImpl(Parcelable superState) {
            super(superState);
        }

        AbsSavedStateImpl(Parcel source) {
            super(source);
        }

        AbsSavedStateImpl(Parcel source, ClassLoader loader) {
            super(source, loader);
        }

        public static final Creator<AbsSavedStateImpl> CREATOR =
                new ClassLoaderCreator<AbsSavedStateImpl>() {
            @Override
            public AbsSavedStateImpl createFromParcel(Parcel source) {
                return new AbsSavedStateImpl(source);
            }

            @Override
            public AbsSavedStateImpl createFromParcel(Parcel source, ClassLoader loader) {
                return new AbsSavedStateImpl(source, loader);
            }

            @Override
            public AbsSavedStateImpl[] newArray(int size) {
                return new AbsSavedStateImpl[size];
            }
        };
    }
}
