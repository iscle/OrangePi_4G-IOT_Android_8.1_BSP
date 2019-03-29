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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.android.compatibility.common.util.BusinessLogic.BusinessLogicRule;
import com.android.compatibility.common.util.BusinessLogic.BusinessLogicRuleAction;
import com.android.compatibility.common.util.BusinessLogic.BusinessLogicRuleCondition;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Unit tests for {@link BusinessLogic}
 */
@RunWith(JUnit4.class)
public class BusinessLogicTest {

    private static final String CORRECT_LOGIC =
            "{\n" +
            "  \"name\": \"businessLogic/suites/gts\",\n" +
            "  \"businessLogicRulesLists\": [\n" +
            "    {\n" +
            "      \"testName\": \"testCaseName1\",\n" +
            "      \"businessLogicRules\": [\n" +
            "        {\n" +
            "          \"ruleConditions\": [\n" +
            "            {\n" +
            "              \"methodName\": \"conditionMethodName1\",\n" +
            "              \"methodArgs\": [\n" +
            "                \"arg1\"\n" +
            "              ]\n" +
            "            }\n" +
            "          ],\n" +
            "          \"ruleActions\": [\n" +
            "            {\n" +
            "              \"methodName\": \"actionMethodName1\",\n" +
            "              \"methodArgs\": [\n" +
            "                \"arg1\",\n" +
            "                \"arg2\"\n" +
            "              ]\n" +
            "            }\n" +
            "          ]\n" +
            "        }\n" +
            "      ]\n" +
            "    },\n" +
            "    {\n" +
            "      \"testName\": \"testCaseName2\",\n" +
            "      \"businessLogicRules\": [\n" +
            "        {\n" +
            "          \"ruleConditions\": [\n" +
            "            {\n" +
            "              \"methodName\": \"conditionMethodName1\",\n" +
            "              \"methodArgs\": [\n" +
            "                \"arg1\"\n" +
            "              ]\n" +
            "            }\n" +
            "          ],\n" +
            "          \"ruleActions\": [\n" +
            "            {\n" +
            "              \"methodName\": \"actionMethodName1\",\n" +
            "              \"methodArgs\": [\n" +
            "                \"arg1\",\n" +
            "                \"arg2\"\n" +
            "              ]\n" +
            "            }\n" +
            "          ]\n" +
            "        },\n" +
            "        {\n" +
            "          \"ruleConditions\": [\n" +
            "            {\n" +
            "              \"methodName\": \"conditionMethodName1\",\n" +
            "              \"methodArgs\": [\n" +
            "                \"arg1\"\n" +
            "              ]\n" +
            "            },\n" +
            "            {\n" +
            "              \"methodName\": \"!conditionMethodName2\",\n" + // use negation
            "              \"methodArgs\": [\n" +
            "                \"arg2\"\n" +
            "              ]\n" +
            "            }\n" +
            "          ],\n" +
            "          \"ruleActions\": [\n" +
            "            {\n" +
            "              \"methodName\": \"actionMethodName1\",\n" +
            "              \"methodArgs\": [\n" +
            "                \"arg1\",\n" +
            "                \"arg2\"\n" +
            "              ]\n" +
            "            },\n" +
            "            {\n" +
            "              \"methodName\": \"actionMethodName2\"\n" +
            "            }\n" +
            "          ]\n" +
            "        }\n" +
            "      ]\n" +
            "    },\n" +
            "    {\n" +
            "      \"testName\": \"testCaseName3\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";

    @Test
    public void testCorrectLogic() throws Exception {
        File file = createFileFromStr(CORRECT_LOGIC);
        try {
            BusinessLogic bl = BusinessLogicFactory.createFromFile(file);
            assertEquals("Wrong number of business logic rule lists", 3, bl.mRules.size());

            List<BusinessLogicRule> ruleList1 = bl.mRules.get("testCaseName1");
            assertEquals("Wrong number of rules in first rule list", 1, ruleList1.size());
            BusinessLogicRule rule1 = ruleList1.get(0);
            List<BusinessLogicRuleCondition> rule1Conditions = rule1.mConditions;
            assertEquals("Wrong number of conditions", 1, rule1Conditions.size());
            BusinessLogicRuleCondition rule1Condition = rule1Conditions.get(0);
            assertEquals("Wrong method name for business logic rule condition",
                    "conditionMethodName1", rule1Condition.mMethodName);
            assertFalse("Wrong negation value for business logic rule condition",
                    rule1Condition.mNegated);
            assertEquals("Wrong arg string count for business logic rule condition", 1,
                    rule1Condition.mMethodArgs.size());
            assertEquals("Wrong arg for business logic rule condition", "arg1",
                    rule1Condition.mMethodArgs.get(0));
            List<BusinessLogicRuleAction> rule1Actions = rule1.mActions;
            assertEquals("Wrong number of actions", 1, rule1Actions.size());
            BusinessLogicRuleAction rule1Action = rule1Actions.get(0);
            assertEquals("Wrong method name for business logic rule action",
                    "actionMethodName1", rule1Action.mMethodName);
            assertEquals("Wrong arg string count for business logic rule action", 2,
                    rule1Action.mMethodArgs.size());
            assertEquals("Wrong arg for business logic rule action", "arg1",
                    rule1Action.mMethodArgs.get(0));
            assertEquals("Wrong arg for business logic rule action", "arg2",
                    rule1Action.mMethodArgs.get(1));

            List<BusinessLogicRule> ruleList2 = bl.mRules.get("testCaseName2");
            assertEquals("Wrong number of rules in second rule list", 2, ruleList2.size());
            BusinessLogicRule rule2 = ruleList2.get(0);
            List<BusinessLogicRuleCondition> rule2Conditions = rule2.mConditions;
            assertEquals("Wrong number of conditions", 1, rule2Conditions.size());
            BusinessLogicRuleCondition rule2Condition = rule2Conditions.get(0);
            assertEquals("Wrong method name for business logic rule condition",
                    "conditionMethodName1", rule2Condition.mMethodName);
            assertFalse("Wrong negation value for business logic rule condition",
                    rule2Condition.mNegated);
            assertEquals("Wrong arg string count for business logic rule condition", 1,
                    rule2Condition.mMethodArgs.size());
            assertEquals("Wrong arg for business logic rule condition", "arg1",
                    rule2Condition.mMethodArgs.get(0));
            List<BusinessLogicRuleAction> rule2Actions = rule2.mActions;
            assertEquals("Wrong number of actions", 1, rule2Actions.size());
            BusinessLogicRuleAction rule2Action = rule2Actions.get(0);
            assertEquals("Wrong method name for business logic rule action",
                    "actionMethodName1", rule2Action.mMethodName);
            assertEquals("Wrong arg string count for business logic rule action", 2,
                    rule2Action.mMethodArgs.size());
            assertEquals("Wrong arg for business logic rule action", "arg1",
                    rule2Action.mMethodArgs.get(0));
            assertEquals("Wrong arg for business logic rule action", "arg2",
                    rule2Action.mMethodArgs.get(1));
            BusinessLogicRule rule3 = ruleList2.get(1);
            List<BusinessLogicRuleCondition> rule3Conditions = rule3.mConditions;
            assertEquals("Wrong number of conditions", 2, rule3Conditions.size());
            BusinessLogicRuleCondition rule3Condition1 = rule3Conditions.get(0);
            assertEquals("Wrong method name for business logic rule condition",
                    "conditionMethodName1", rule3Condition1.mMethodName);
            assertFalse("Wrong negation value for business logic rule condition",
                    rule3Condition1.mNegated);
            assertEquals("Wrong arg string count for business logic rule condition", 1,
                    rule3Condition1.mMethodArgs.size());
            assertEquals("Wrong arg for business logic rule condition", "arg1",
                    rule3Condition1.mMethodArgs.get(0));
            BusinessLogicRuleCondition rule3Condition2 = rule3Conditions.get(1);
            assertEquals("Wrong method name for business logic rule condition",
                    "conditionMethodName2", rule3Condition2.mMethodName);
            assertTrue("Wrong negation value for business logic rule condition",
                    rule3Condition2.mNegated);
            assertEquals("Wrong arg string count for business logic rule condition", 1,
                    rule3Condition2.mMethodArgs.size());
            assertEquals("Wrong arg for business logic rule condition", "arg2",
                    rule3Condition2.mMethodArgs.get(0));
            List<BusinessLogicRuleAction> rule3Actions = rule3.mActions;
            assertEquals("Wrong number of actions", 2, rule3Actions.size());
            BusinessLogicRuleAction rule3Action1 = rule3Actions.get(0);
            assertEquals("Wrong method name for business logic rule action",
                    "actionMethodName1", rule3Action1.mMethodName);
            assertEquals("Wrong arg string count for business logic rule action", 2,
                    rule3Action1.mMethodArgs.size());
            assertEquals("Wrong arg for business logic rule action", "arg1",
                    rule3Action1.mMethodArgs.get(0));
            assertEquals("Wrong arg for business logic rule action", "arg2",
                    rule3Action1.mMethodArgs.get(1));
            BusinessLogicRuleAction rule3Action2 = rule3Actions.get(1);
            assertEquals("Wrong method name for business logic rule action",
                    "actionMethodName2", rule3Action2.mMethodName);
            assertEquals("Wrong arg string count for business logic rule action", 0,
                    rule3Action2.mMethodArgs.size());

            List<BusinessLogicRule> ruleList3 = bl.mRules.get("testCaseName3");
            assertEquals("Wrong number of rules in third rule list", 0, ruleList3.size());
        } finally {
            FileUtil.deleteFile(file);
        }
    }

    @Test(expected = RuntimeException.class)
    public void testLogicWithWrongNodeName() throws Exception {
        File file = createFileFromStr(CORRECT_LOGIC.replace("testName", "testNam3"));
        try {
            BusinessLogic bl = BusinessLogicFactory.createFromFile(file);
        } finally {
            FileUtil.deleteFile(file);
        }
    }

    private static File createFileFromStr(String blString) throws IOException {
        File file = File.createTempFile("test", "bl");
        FileOutputStream stream = new FileOutputStream(file);
        stream.write(blString.getBytes());
        stream.flush();
        stream.close();
        return file;
    }
}
