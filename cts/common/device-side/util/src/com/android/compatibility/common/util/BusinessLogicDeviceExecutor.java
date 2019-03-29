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
 * limitations under the License.
 */

package com.android.compatibility.common.util;

import android.content.Context;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * Execute business logic methods for device side test cases
 */
public class BusinessLogicDeviceExecutor extends BusinessLogicExecutor {

    private Context mContext;
    private Object mTestObj;

    public BusinessLogicDeviceExecutor(Context context, Object testObj) {
        mContext = context;
        mTestObj = testObj;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object getTestObject() {
        return mTestObj;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ResolvedMethod getResolvedMethod(Class cls, String methodName, String... args)
            throws ClassNotFoundException {
        List<Method> nameMatches = getMethodsWithName(cls, methodName);
        for (Method m : nameMatches) {
            ResolvedMethod rm = new ResolvedMethod(m);
            int paramTypesMatched = 0;
            int argsUsed = 0;
            Class[] paramTypes = m.getParameterTypes();
            for (Class paramType : paramTypes) {
                if (argsUsed == args.length && paramType.equals(String.class)) {
                    // We've used up all supplied string args, so this method will not match.
                    // If paramType is the Context class, we can match a paramType without needing
                    // more string args. similarly, paramType "String[]" can be matched with zero
                    // string args. If we add support for more paramTypes, this logic may require
                    // adjustment.
                    break;
                }
                if (paramType.equals(String.class)) {
                    // Type "String" -- supply the next available arg
                    rm.addArg(args[argsUsed++]);
                } else if (Context.class.isAssignableFrom(paramType)) {
                    // Type "Context" -- supply the context from the test case
                    rm.addArg(mContext);
                } else if (paramType.equals(Class.forName(STRING_ARRAY_CLASS))) {
                    // Type "String[]" (or "String...") -- supply all remaining args
                    rm.addArg(Arrays.copyOfRange(args, argsUsed, args.length));
                    argsUsed += (args.length - argsUsed);
                } else {
                    break; // Param type is unrecognized, this method will not match.
                }
                paramTypesMatched++; // A param type has been matched when reaching this point.
            }
            if (paramTypesMatched == paramTypes.length && argsUsed == args.length) {
                return rm; // Args match, methods match, so return the first method-args pairing.
            }
            // Not a match, try args for next method that matches by name.
        }
        throw new RuntimeException(String.format(
                "BusinessLogic: Failed to invoke action method %s with args: %s", methodName,
                Arrays.toString(args)));
    }
}
