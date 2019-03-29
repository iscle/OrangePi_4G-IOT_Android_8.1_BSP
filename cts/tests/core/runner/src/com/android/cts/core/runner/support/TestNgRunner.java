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

import android.util.Log;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Map;

/**
 * A {@link Runner} that can TestNG tests.
 *
 * <p>Implementation note: Avoid extending ParentRunner since that also has
 * logic to handle BeforeClass/AfterClass and other junit-specific functionality
 * that would be invalid for TestNG.</p>
 */
class TestNgRunner extends Runner implements Filterable {

  private static final boolean DEBUG = false;

  private Description mDescription;
  /** Class name for debugging. */
  private String mClassName;
  /** Don't include the same method names twice. */
  private HashSet<String> mMethodSet = new HashSet<>();

  /**
   * @param testClass the test class to run
   */
  TestNgRunner(Class<?> testClass) {
    mDescription = generateTestNgDescription(testClass);
    mClassName = testClass.getName();
  }

  // Runner implementation
  @Override
  public Description getDescription() {
    return mDescription;
  }

  // Runner implementation
  @Override
  public int testCount() {
    if (!descriptionHasChildren(getDescription())) {  // Avoid NPE when description is null.
      return 0;
    }

    // We always follow a flat Parent->Leaf hierarchy, so no recursion necessary.
    return getDescription().testCount();
  }

  // Filterable implementation
  @Override
  public void filter(Filter filter) throws NoTestsRemainException {
    mDescription = filterDescription(mDescription, filter);

    if (!descriptionHasChildren(getDescription())) {  // Avoid NPE when description is null.
      if (DEBUG) {
        Log.d("TestNgRunner",
            "Filtering has removed all tests :( for class " + mClassName);
      }
      throw new NoTestsRemainException();
    }

    if (DEBUG) {
      Log.d("TestNgRunner",
          "Filtering has retained " + testCount() + " tests for class " + mClassName);
    }
  }

  // Filterable implementation
  @Override
  public void run(RunNotifier notifier) {
    if (!descriptionHasChildren(getDescription())) {  // Avoid NPE when description is null.
      // Nothing to do.
      return;
    }

    for (Description child : getDescription().getChildren()) {
      String className = child.getClassName();
      String methodName = child.getMethodName();

      Class<?> klass;
      try {
        klass = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
      } catch (ClassNotFoundException e) {
        throw new AssertionError(e);
      }

      notifier.fireTestStarted(child);

      // Avoid looking at all the methods by just using the string method name.
      SingleTestNgTestExecutor.Result result = SingleTestNgTestExecutor.execute(klass, methodName);
      if (result.hasFailure()) {
        // TODO: get the error messages from testng somehow.
        notifier.fireTestFailure(new Failure(child, extractException(result.getFailures())));
      }

      notifier.fireTestFinished(child);
      // TODO: Check @Test(enabled=false) and invoke #fireTestIgnored instead.
    }
  }

  private Throwable extractException(Map<String, Throwable> failures) {
    if (failures.isEmpty()) {
      return new AssertionError();
    }
    if (failures.size() == 1) {
      return failures.values().iterator().next();
    }

    StringBuilder errorMessage = new StringBuilder("========== Multiple Failures ==========");
    for (Map.Entry<String, Throwable> failureEntry : failures.entrySet()) {
      errorMessage.append("\n\n=== "). append(failureEntry.getKey()).append(" ===\n");
      Throwable throwable = failureEntry.getValue();
      errorMessage
              .append(throwable.getClass()).append(": ")
              .append(throwable.getMessage());
      for (StackTraceElement e : throwable.getStackTrace()) {
        if (e.getClassName().equals(getClass().getName())) {
          break;
        }
        errorMessage.append("\n  at ").append(e);
      }
    }
    errorMessage.append("\n=======================================\n\n");
    return new AssertionError(errorMessage.toString());
  }


  /**
   * Recursively (preorder traversal) apply the filter to all the descriptions.
   *
   * @return null if the filter rejects the whole tree.
   */
  private static Description filterDescription(Description desc, Filter filter) {
    if (!filter.shouldRun(desc)) {  // XX: Does the filter itself do the recursion?
      return null;
    }

    Description newDesc = desc.childlessCopy();

    // Return leafs.
    if (!descriptionHasChildren(desc)) {
      return newDesc;
    }

    // Filter all subtrees, only copying them if the filter accepts them.
    for (Description child : desc.getChildren()) {
      Description filteredChild = filterDescription(child, filter);

      if (filteredChild != null) {
        newDesc.addChild(filteredChild);
      }
    }

    return newDesc;
  }

  private Description generateTestNgDescription(Class<?> cls) {
    // Add the overall class description as the parent.
    Description parent = Description.createSuiteDescription(cls);

    if (DEBUG) {
      Log.d("TestNgRunner", "Generating TestNg Description for class " + cls.getName());
    }

    // Add each test method as a child.
    for (Method m : cls.getDeclaredMethods()) {

      // Filter to only 'public void' signatures.
      if ((m.getModifiers() & Modifier.PUBLIC) == 0) {
        continue;
      }

      if (!m.getReturnType().equals(Void.TYPE)) {
        continue;
      }

      // Note that TestNG methods may actually have parameters
      // (e.g. with @DataProvider) which TestNG will populate itself.

      // Add [Class, MethodName] as a Description leaf node.
      String name = m.getName();

      if (!mMethodSet.add(name)) {
        // Overloaded methods have the same name, don't add them twice.
        if (DEBUG) {
          Log.d("TestNgRunner", "Already added child " + cls.getName() + "#" + name);
        }
        continue;
      }

      Description child = Description.createTestDescription(cls, name);

      parent.addChild(child);

      if (DEBUG) {
        Log.d("TestNgRunner", "Add child " + cls.getName() + "#" + name);
      }
    }

    return parent;
  }

  private static boolean descriptionHasChildren(Description desc) {
    // Note: Although "desc.isTest()" is equivalent to "!desc.getChildren().isEmpty()"
    // we add the pre-requisite 2 extra null checks to avoid throwing NPEs.
    return desc != null && desc.getChildren() != null && !desc.getChildren().isEmpty();
  }
}
