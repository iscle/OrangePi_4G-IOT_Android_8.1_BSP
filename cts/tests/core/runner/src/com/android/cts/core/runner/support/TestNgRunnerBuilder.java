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

package com.android.cts.core.runner.support;

import java.lang.reflect.Method;

import org.junit.runner.Runner;
import org.junit.runners.model.RunnerBuilder;

import org.testng.annotations.Test;

/**
 * A {@link RunnerBuilder} that can handle TestNG tests.
 */
public class TestNgRunnerBuilder extends RunnerBuilder {
  // Returns a TestNG runner for this class, only if it is a class
  // annotated with testng's @Test or has any methods with @Test in it.
  @Override
  public Runner runnerForClass(Class<?> testClass) {
    if (isTestNgTestClass(testClass)) {
      return new TestNgRunner(testClass);
    }

    return null;
  }

  private static boolean isTestNgTestClass(Class<?> cls) {
    // TestNG test is either marked @Test at the class
    if (cls.getAnnotation(Test.class) != null) {
      return true;
    }

    // Or It's marked @Test at the method level
    for (Method m : cls.getDeclaredMethods()) {
      if (m.getAnnotation(Test.class) != null) {
        return true;
      }
    }

    return false;
  }
}
