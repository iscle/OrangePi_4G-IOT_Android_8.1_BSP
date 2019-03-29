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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.android.compatibility.common.util.BusinessLogic.BusinessLogicRule;
import com.android.compatibility.common.util.BusinessLogic.BusinessLogicRuleAction;
import com.android.compatibility.common.util.BusinessLogic.BusinessLogicRuleCondition;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Factory for creating a {@link BusinessLogic}
 */
public class BusinessLogicFactory {

    // Name of list object storing test-rules pairs
    private static final String BUSINESS_LOGIC_RULES_LISTS = "businessLogicRulesLists";
    // Name of test name string
    private static final String TEST_NAME = "testName";
    // Name of rules object (one 'rules' object to a single test)
    private static final String BUSINESS_LOGIC_RULES = "businessLogicRules";
    // Name of rule conditions array
    private static final String RULE_CONDITIONS = "ruleConditions";
    // Name of rule actions array
    private static final String RULE_ACTIONS = "ruleActions";
    // Name of method name string
    private static final String METHOD_NAME = "methodName";
    // Name of method args array of strings
    private static final String METHOD_ARGS = "methodArgs";

    /**
     * Create a BusinessLogic instance from a file of business logic data, formatted in JSON.
     * This format is identical to that which is received from the Android Partner business logic
     * service.
     */
    public static BusinessLogic createFromFile(File f) {
        // Populate the map from testname to business rules for this new BusinessLogic instance
        Map<String, List<BusinessLogicRule>> rulesMap = new HashMap<>();
        BusinessLogic bl = new BusinessLogic();
        try {
            String businessLogicString = readFile(f);
            JSONObject root = new JSONObject(businessLogicString);
            JSONArray rulesLists = null;
            try {
                rulesLists = root.getJSONArray(BUSINESS_LOGIC_RULES_LISTS);
            } catch (JSONException e) {
                bl.mRules = rulesMap;
                return bl; // no rules defined for this suite, leave internal map empty
            }
            for (int i = 0; i < rulesLists.length(); i++) {
                JSONObject rulesList = rulesLists.getJSONObject(i);
                String testName = rulesList.getString(TEST_NAME);
                List<BusinessLogicRule> rules = new ArrayList<>();
                JSONArray rulesJSONArray = null;
                try {
                    rulesJSONArray = rulesList.getJSONArray(BUSINESS_LOGIC_RULES);
                } catch (JSONException e) {
                    // no rules defined for this test case
                    rulesMap.put(testName, rules); // add empty rule list to internal map
                    continue; // advance to next test case
                }
                for (int j = 0; j < rulesJSONArray.length(); j++) {
                    JSONObject ruleJSONObject = rulesJSONArray.getJSONObject(j);
                    // Build conditions list
                    List<BusinessLogicRuleCondition> ruleConditions =
                            extractRuleConditionList(ruleJSONObject);
                    // Build actions list
                    List<BusinessLogicRuleAction> ruleActions =
                            extractRuleActionList(ruleJSONObject);
                    rules.add(new BusinessLogicRule(ruleConditions, ruleActions));
                }
                rulesMap.put(testName, rules);
            }
        } catch (IOException | JSONException e) {
            throw new RuntimeException("Business Logic failed", e);
        }
        // Return business logic
        bl.mRules = rulesMap;
        return bl;
    }

    /* Extract all BusinessLogicRuleConditions from a JSON business logic rule */
    private static List<BusinessLogicRuleCondition> extractRuleConditionList(
            JSONObject ruleJSONObject) throws JSONException {
        List<BusinessLogicRuleCondition> ruleConditions = new ArrayList<>();
        // Rules do not require a condition, return empty list if no condition is found
        JSONArray ruleConditionsJSONArray = null;
        try {
            ruleConditionsJSONArray = ruleJSONObject.getJSONArray(RULE_CONDITIONS);
        } catch (JSONException e) {
            return ruleConditions; // no conditions for this rule, apply in all cases
        }
        for (int i = 0; i < ruleConditionsJSONArray.length(); i++) {
            JSONObject ruleConditionJSONObject = ruleConditionsJSONArray.getJSONObject(i);
            String methodName = ruleConditionJSONObject.getString(METHOD_NAME);
            boolean negated = false;
            if (methodName.startsWith("!")) {
                methodName = methodName.substring(1); // remove negation from method name string
                negated = true; // change "negated" property to true
            }
            List<String> methodArgs = new ArrayList<>();
            JSONArray methodArgsJSONArray = null;
            try {
                methodArgsJSONArray = ruleConditionJSONObject.getJSONArray(METHOD_ARGS);
            } catch (JSONException e) {
                // No method args for this rule condition, add rule condition with empty args list
                ruleConditions.add(new BusinessLogicRuleCondition(methodName, methodArgs, negated));
                continue;
            }
            for (int j = 0; j < methodArgsJSONArray.length(); j++) {
                methodArgs.add(methodArgsJSONArray.getString(j));
            }
            ruleConditions.add(new BusinessLogicRuleCondition(methodName, methodArgs, negated));
        }
        return ruleConditions;
    }

    /* Extract all BusinessLogicRuleActions from a JSON business logic rule */
    private static List<BusinessLogicRuleAction> extractRuleActionList(JSONObject ruleJSONObject)
            throws JSONException {
        List<BusinessLogicRuleAction> ruleActions = new ArrayList<>();
        // All rules require at least one action, line below throws JSONException if not
        JSONArray ruleActionsJSONArray = ruleJSONObject.getJSONArray(RULE_ACTIONS);
        for (int i = 0; i < ruleActionsJSONArray.length(); i++) {
            JSONObject ruleActionJSONObject = ruleActionsJSONArray.getJSONObject(i);
            String methodName = ruleActionJSONObject.getString(METHOD_NAME);
            List<String> methodArgs = new ArrayList<>();
            JSONArray methodArgsJSONArray = null;
            try {
                methodArgsJSONArray = ruleActionJSONObject.getJSONArray(METHOD_ARGS);
            } catch (JSONException e) {
                // No method args for this rule action, add rule action with empty args list
                ruleActions.add(new BusinessLogicRuleAction(methodName, methodArgs));
                continue;
            }
            for (int j = 0; j < methodArgsJSONArray.length(); j++) {
                methodArgs.add(methodArgsJSONArray.getString(j));
            }
            ruleActions.add(new BusinessLogicRuleAction(methodName, methodArgs));
        }
        return ruleActions;
    }

    /* Extract string from file */
    private static String readFile(File f) throws IOException {
        StringBuilder sb = new StringBuilder((int) f.length());
        String lineSeparator = System.getProperty("line.separator");
        try (Scanner scanner = new Scanner(f)) {
            while(scanner.hasNextLine()) {
                sb.append(scanner.nextLine() + lineSeparator);
            }
            return sb.toString();
        }
    }
}
