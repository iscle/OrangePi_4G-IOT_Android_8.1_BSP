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

package com.android.cts.apicoverage;

import java.lang.String;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

/** Representation of the entire CDD. */
class CddCoverage {

    private final Map<String, CddRequirement> requirements = new HashMap<>();

    public void addCddRequirement(CddRequirement cddRequirement) {
        requirements.put(cddRequirement.getRequirementId(), cddRequirement);
    }

    public Collection<CddRequirement> getCddRequirements() {
        return Collections.unmodifiableCollection(requirements.values());
    }

    public void addCoverage(String cddRequirementId, TestMethod testMethod) {
        if (!requirements.containsKey(cddRequirementId)) {
            requirements.put(cddRequirementId, new CddRequirement(cddRequirementId));
        }

        requirements.get(cddRequirementId).addTestMethod(testMethod);
    }

    static class CddRequirement {
        private final String mRequirementId;
        private final List<TestMethod> mtestMethods;

        CddRequirement(String requirementId) {
            this.mRequirementId = requirementId;
            this.mtestMethods = new ArrayList<>();
        }

        @Override
        public boolean equals(Object other) {
            if (other == null) {
                return false;
            } else if (!(other instanceof CddRequirement)) {
                return false;
            } else {
                return mRequirementId.equals(((CddRequirement)other).mRequirementId);
            }
        }

        @Override
        public int hashCode() {
            return mRequirementId.hashCode();
        }

        @Override
        public String toString() {
            return String.format("Requirement %s %s", mRequirementId, mtestMethods);
        }

        public String getRequirementId() { return mRequirementId; }

        public void addTestMethod(TestMethod testMethod) {
            mtestMethods.add(testMethod);
        }

        public Collection<TestMethod> getTestMethods() {
            return Collections.unmodifiableCollection(mtestMethods);
        }
    }

    static class TestMethod {
        private final String mTestModule;
        private final String mTestClass;
        private final String mTestMethod;

        TestMethod(String testModule, String testClass, String testMethod) {
            this.mTestModule = testModule;
            this.mTestClass = testClass;
            this.mTestMethod = testMethod;
        }

        public String getTestModule() { return mTestModule; }

        public String getTestClass() { return mTestClass; }

        public String getTestMethod() { return mTestMethod; }

        @Override
        public String toString() {
            return String.format("%s %s#%s", mTestModule, mTestClass, mTestMethod);
        }
    }
}
