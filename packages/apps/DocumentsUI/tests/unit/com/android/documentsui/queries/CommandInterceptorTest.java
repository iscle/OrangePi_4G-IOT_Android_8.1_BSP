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

package com.android.documentsui.queries;

import static com.android.documentsui.queries.CommandInterceptor.COMMAND_PREFIX;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.documentsui.testing.TestEventHandler;
import com.android.documentsui.testing.TestFeatures;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class CommandInterceptorTest {

    private TestEventHandler<String[]> mCommand0;
    private TestEventHandler<String[]> mCommand1;
    private CommandInterceptor mProcessor;

    @Before
    public void setUp() {
        mCommand0 = new TestEventHandler<>();
        mCommand1 = new TestEventHandler<>();
        mProcessor = new CommandInterceptor(new TestFeatures());
        mProcessor.add(mCommand0);
        mProcessor.add(mCommand1);
    }

    @Test
    public void testTriesAllCommands() {
        mProcessor.accept(COMMAND_PREFIX + "poodles");
        mCommand0.assertCalled();
        mCommand1.assertCalled();
    }

    @Test
    public void testStopsAfterCommandHandled() {
        mCommand0.nextReturn(true);
        mProcessor.accept(":poodles");
        mCommand0.assertCalled();
        mCommand1.assertNotCalled();
    }

    @Test
    public void testConveysArguments() {
        mCommand0.nextReturn(true);
        mProcessor.accept(COMMAND_PREFIX + "cheese doodles");

        String[] expected = {"cheese", "doodles"};
        Assert.assertArrayEquals(expected, mCommand0.getLastValue());
    }

    @Test
    public void testMissingCommand() {
        mProcessor.accept(COMMAND_PREFIX);
        mCommand0.assertNotCalled();
        mCommand1.assertNotCalled();
    }
}
