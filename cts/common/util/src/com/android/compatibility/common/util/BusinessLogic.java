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

import java.util.List;
import java.util.Map;

/**
 * Helper and constants accessible to host and device components that enable Business Logic
 * configuration
 */
public class BusinessLogic {

    // Device location to which business logic data is pushed
    public static final String DEVICE_FILE = "/sdcard/bl";

    /* A map from testcase name to the business logic rules for the test case */
    protected Map<String, List<BusinessLogicRule>> mRules;

    /**
     * Determines whether business logic exists for a given test name
     * @param testName the name of the test case, prefixed by fully qualified class name, then '#'.
     * For example, "com.android.foo.FooTest#testFoo"
     * @return whether business logic exists for this test for this suite
     */
    public boolean hasLogicFor(String testName) {
        List<BusinessLogicRule> rules = mRules.get(testName);
        return rules != null && !rules.isEmpty();
    }

    /**
     * Apply business logic for the given test.
     * @param testName the name of the test case, prefixed by fully qualified class name, then '#'.
     * For example, "com.android.foo.FooTest#testFoo"
     * @param executor a {@link BusinessLogicExecutor}
     */
    public void applyLogicFor(String testName, BusinessLogicExecutor executor) {
        List<BusinessLogicRule> rules = mRules.get(testName);
        if (rules == null || rules.isEmpty()) {
            return;
        }
        for (BusinessLogicRule rule : rules) {
            // Check conditions
            if (rule.invokeConditions(executor)) {
                rule.invokeActions(executor);
            }
        }
    }

    /**
     * Nested class representing an Business Logic Rule. Stores a collection of conditions
     * and actions for later invokation.
     */
    protected static class BusinessLogicRule {

        /* Stored conditions and actions */
        protected List<BusinessLogicRuleCondition> mConditions;
        protected List<BusinessLogicRuleAction> mActions;

        public BusinessLogicRule(List<BusinessLogicRuleCondition> conditions,
                List<BusinessLogicRuleAction> actions) {
            mConditions = conditions;
            mActions = actions;
        }

        /**
         * Method that invokes all Business Logic conditions for this rule, and returns true
         * if all conditions evaluate to true.
         */
        public boolean invokeConditions(BusinessLogicExecutor executor) {
            for (BusinessLogicRuleCondition condition : mConditions) {
                if (!condition.invoke(executor)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Method that invokes all Business Logic actions for this rule
         */
        public void invokeActions(BusinessLogicExecutor executor) {
            for (BusinessLogicRuleAction action : mActions) {
                action.invoke(executor);
            }
        }
    }

    /**
     * Nested class representing an Business Logic Rule Condition. Stores the name of a method
     * to invoke, as well as String args to use during invokation.
     */
    protected static class BusinessLogicRuleCondition {

        /* Stored method name and String args */
        protected String mMethodName;
        protected List<String> mMethodArgs;
        /* Whether or not the boolean result of this condition should be reversed */
        protected boolean mNegated;


        public BusinessLogicRuleCondition(String methodName, List<String> methodArgs,
                boolean negated) {
            mMethodName = methodName;
            mMethodArgs = methodArgs;
            mNegated = negated;
        }

        /**
         * Invoke this Business Logic condition with an executor.
         */
        public boolean invoke(BusinessLogicExecutor executor) {
            // XOR the negated boolean with the return value of the method
            return (mNegated != executor.executeCondition(mMethodName,
                    mMethodArgs.toArray(new String[mMethodArgs.size()])));
        }
    }

    /**
     * Nested class representing an Business Logic Rule Action. Stores the name of a method
     * to invoke, as well as String args to use during invokation.
     */
    protected static class BusinessLogicRuleAction {

        /* Stored method name and String args */
        protected String mMethodName;
        protected List<String> mMethodArgs;

        public BusinessLogicRuleAction(String methodName, List<String> methodArgs) {
            mMethodName = methodName;
            mMethodArgs = methodArgs;
        }

        /**
         * Invoke this Business Logic action with an executor.
         */
        public void invoke(BusinessLogicExecutor executor) {
            executor.executeAction(mMethodName,
                    mMethodArgs.toArray(new String[mMethodArgs.size()]));
        }
    }
}
