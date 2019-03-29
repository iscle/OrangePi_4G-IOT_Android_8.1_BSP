/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.compatibility.common.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.AssumptionViolatedException;

/**
 * Resolves methods provided by the BusinessLogicService and invokes them
 */
public abstract class BusinessLogicExecutor {

    /** String representations of the String class and String[] class */
    protected static final String STRING_CLASS = "java.lang.String";
    protected static final String STRING_ARRAY_CLASS = "[Ljava.lang.String;";

    /**
     * Execute a business logic condition.
     * @param method the name of the method to invoke. Must include fully qualified name of the
     * enclosing class, followed by '.', followed by the name of the method
     * @param args the string arguments to supply to the method
     * @return the return value of the method invoked
     * @throws RuntimeException when failing to resolve or invoke the condition method
     */
    public boolean executeCondition(String method, String... args) {
        try {
            return (Boolean) invokeMethod(method, args);
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException |
                InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(String.format(
                    "BusinessLogic: Failed to invoke condition method %s with args: %s", method,
                    Arrays.toString(args)), e);
        }
    }

    /**
     * Execute a business logic action.
     * @param method the name of the method to invoke. Must include fully qualified name of the
     * enclosing class, followed by '.', followed by the name of the method
     * @param args the string arguments to supply to the method
     * @throws RuntimeException when failing to resolve or invoke the action method
     */
    public void executeAction(String method, String... args) {
        try {
            invokeMethod(method, args);
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException |
                NoSuchMethodException e) {
            throw new RuntimeException(String.format(
                    "BusinessLogic: Failed to invoke action method %s with args: %s", method,
                    Arrays.toString(args)), e);
        } catch (InvocationTargetException e) {
            // This action throws an exception, so throw the original exception (e.g.
            // AssertionFailedError) for a more readable stacktrace.
            Throwable t = e.getCause();
            if (AssumptionViolatedException.class.isInstance(t)) {
                // This is an assumption failure (registered as a "pass") so don't wrap this
                // throwable in a RuntimeException
                throw (AssumptionViolatedException) t;
            } else {
                RuntimeException re = new RuntimeException(t.getMessage(), t.getCause());
                re.setStackTrace(t.getStackTrace());
                throw re;
            }
        }
    }

    /**
     * Execute a business logic method.
     * @param method the name of the method to invoke. Must include fully qualified name of the
     * enclosing class, followed by '.', followed by the name of the method
     * @param args the string arguments to supply to the method
     * @return the return value of the method invoked (type Boolean if method is a condition)
     * @throws RuntimeException when failing to resolve or invoke the method
     */
    protected Object invokeMethod(String method, String... args) throws ClassNotFoundException,
            IllegalAccessException, InstantiationException, InvocationTargetException,
            NoSuchMethodException {
        // Method names served by the BusinessLogic service should assume format
        // classname.methodName, but also handle format classname#methodName since test names use
        // this format
        int index = (method.indexOf('#') == -1) ? method.lastIndexOf('.') : method.indexOf('#');
        if (index == -1) {
            throw new RuntimeException(String.format("BusinessLogic: invalid method name "
                    + "\"%s\". Method string must include fully qualified class name. "
                    + "For example, \"com.android.packagename.ClassName.methodName\".", method));
        }
        String className = method.substring(0, index);
        Class cls = Class.forName(className);
        Object obj = cls.getDeclaredConstructor().newInstance();
        if (getTestObject() != null && cls.isAssignableFrom(getTestObject().getClass())) {
            // The given method is a member of the test class, use the known test class instance
            obj = getTestObject();
        }
        ResolvedMethod rm = getResolvedMethod(cls, method.substring(index + 1), args);
        return rm.invoke(obj);
    }

    /**
     * Get the test object. This method is left abstract, since non-abstract subclasses will set
     * the test object in the constructor.
     * @return the test case instance
     */
    protected abstract Object getTestObject();

    /**
     * Get the method and list of arguments corresponding to the class, method name, and proposed
     * argument values, in the form of a {@link ResolvedMethod} object. This object stores all
     * information required to successfully invoke the method. getResolvedMethod is left abstract,
     * since argument types differ between device-side (e.g. Context) and host-side
     * (e.g. ITestDevice) implementations of this class.
     * @param cls the Class to which the method belongs
     * @param methodName the name of the method to invoke
     * @param args the string arguments to use when invoking the method
     * @return a {@link ResolvedMethod}
     * @throws ClassNotFoundException
     */
    protected abstract ResolvedMethod getResolvedMethod(Class cls, String methodName,
            String... args) throws ClassNotFoundException;

    /**
     * Retrieve all methods within a class that match a given name
     * @param cls the class
     * @param name the method name
     * @return a list of method objects
     */
    protected List<Method> getMethodsWithName(Class cls, String name) {
        List<Method> methodList = new ArrayList<>();
        for (Method m : cls.getMethods()) {
            if (name.equals(m.getName())) {
                methodList.add(m);
            }
        }
        return methodList;
    }

    /**
     * Helper class for storing a method object, and a list of arguments to use when invoking the
     * method. The class is also equipped with an "invoke" method for convenience.
     */
    protected static class ResolvedMethod {
        private Method mMethod;
        List<Object> mArgs;

        public ResolvedMethod(Method method) {
            mMethod = method;
            mArgs = new ArrayList<>();
        }

        /** Add an argument to the argument list for this instance */
        public void addArg(Object arg) {
            mArgs.add(arg);
        }

        /** Invoke the stored method with the stored args on a given object */
        public Object invoke(Object instance) throws IllegalAccessException,
                InvocationTargetException {
            return mMethod.invoke(instance, mArgs.toArray());
        }
    }
}
