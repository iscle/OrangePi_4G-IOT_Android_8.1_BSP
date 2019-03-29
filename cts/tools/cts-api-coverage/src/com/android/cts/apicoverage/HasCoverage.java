/*
 * Copyright (C) 2010 The Android Open Source Project
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

import java.util.Comparator;

interface HasCoverage {
    float getCoveragePercentage();
    int getMemberSize();
    String getName();
}

class CoverageComparator implements Comparator<HasCoverage> {
    public int compare(HasCoverage entity, HasCoverage otherEntity) {
        int lhsPct = Math.round(entity.getCoveragePercentage());
        int rhsPct = Math.round(otherEntity.getCoveragePercentage());
        int diff = Integer.compare(getCoveragePercentageSegment(lhsPct),
                getCoveragePercentageSegment(rhsPct));
        return diff != 0 ? diff :
            Integer.compare(otherEntity.getMemberSize(), entity.getMemberSize());
    }

    /**
     * Distill coverage percentage down to 3 major segments
     * @param coveragePercentage
     * @return
     */
    private int getCoveragePercentageSegment(int coveragePercentage) {
        // note that this segmentation logic is duplicated in api-coverage.xsl
        if (coveragePercentage <= 50) {
            return 0;
        } else if (coveragePercentage <= 80) {
            return 1;
        } else {
            return 2;
        }
    }
}
