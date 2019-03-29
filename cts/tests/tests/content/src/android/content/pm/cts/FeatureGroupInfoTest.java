/**
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package android.content.pm.cts;

import android.content.pm.FeatureGroupInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.test.AndroidTestCase;

import java.util.Arrays;
import java.util.Comparator;

public class FeatureGroupInfoTest extends AndroidTestCase {

    private PackageManager mPackageManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mPackageManager = getContext().getPackageManager();
    }

    public void testFeatureGroupsAreCorrect() throws Exception {
        FeatureInfo[] expectedFeatures = new FeatureInfo[] {
                createFeatureInfo("android.hardware.camera", true),
                createFeatureInfo(0x00020000, true)
        };

        FeatureGroupInfo[] expectedGroups = new FeatureGroupInfo[] {
                createFeatureGroup(new FeatureInfo[] {
                }),
                createFeatureGroup(new FeatureInfo[] {
                        createFeatureInfo("android.hardware.location", true),
                        createFeatureInfo(0x00030000, true)
                }),
                createFeatureGroup(new FeatureInfo[] {
                        createFeatureInfo("android.hardware.camera", true),
                        createFeatureInfo(0x00010001, true)
                })
        };

        PackageInfo pi = mPackageManager.getPackageInfo(getContext().getPackageName(),
                PackageManager.GET_CONFIGURATIONS);
        assertNotNull(pi);
        assertNotNull(pi.reqFeatures);
        assertNotNull(pi.featureGroups);

        assertFeatureInfosMatch(pi.reqFeatures, expectedFeatures);
        assertFeatureGroupsMatch(pi.featureGroups, expectedGroups);
    }

    private static void assertFeatureInfosMatch(FeatureInfo[] actualFeatures,
            FeatureInfo[] expectedFeatures) {
        // We're going to do linear comparisons, so sort everything first.
        Arrays.sort(actualFeatures, sFeatureInfoComparator);
        Arrays.sort(expectedFeatures, sFeatureInfoComparator);

        assertEquals(0, compareFeatureInfoArrays(actualFeatures, expectedFeatures));
    }

    private static void assertFeatureGroupsMatch(FeatureGroupInfo[] actualGroups,
            FeatureGroupInfo[] expectedGroups) {
        // We're going to do linear comparisons, so sort everything first.
        sortFeatureInfos(actualGroups);
        sortFeatureInfos(expectedGroups);

        Arrays.sort(actualGroups, sFeatureGroupComparator);
        Arrays.sort(expectedGroups, sFeatureGroupComparator);

        assertEquals(expectedGroups.length, actualGroups.length);
        final int N = expectedGroups.length;
        for (int i = 0; i < N; i++) {
            assertEquals(0, sFeatureGroupComparator.compare(expectedGroups[i], actualGroups[i]));
        }
    }

    /**
     * Helper method to create FeatureInfo objects.
     */
    private static FeatureInfo createFeatureInfo(String name, boolean required) {
        FeatureInfo fi = new FeatureInfo();
        fi.name = name;
        if (required) {
            fi.flags |= FeatureInfo.FLAG_REQUIRED;
        }
        return fi;
    }

    /**
     * Helper method to create OpenGL FeatureInfo objects.
     */
    private static FeatureInfo createFeatureInfo(int glEsVersion, boolean required) {
        FeatureInfo fi = new FeatureInfo();
        fi.reqGlEsVersion = glEsVersion;
        if (required) {
            fi.flags |= FeatureInfo.FLAG_REQUIRED;
        }
        return fi;
    }

    private static FeatureGroupInfo createFeatureGroup(FeatureInfo[] featureInfos) {
        FeatureGroupInfo group = new FeatureGroupInfo();
        group.features = featureInfos;
        return group;
    }

    private static void sortFeatureInfos(FeatureGroupInfo[] group) {
        for (FeatureGroupInfo g : group) {
            if (g.features != null) {
                Arrays.sort(g.features, sFeatureInfoComparator);
            }
        }
    }

    private static int compareFeatureInfoArrays(FeatureInfo[] a, FeatureInfo[] b) {
        final int aCount = a != null ? a.length : 0;
        final int bCount = b != null ? b.length : 0;
        final int N = Math.min(aCount, bCount);
        for (int i = 0; i < N; i++) {
            int diff = sFeatureInfoComparator.compare(a[i], b[i]);
            if (diff != 0) {
                return diff;
            }
        }
        return Integer.compare(aCount, bCount);
    }

    /**
     * A Comparator for FeatureGroups that assumes that the FeatureInfo array is
     * already sorted.
     */
    private static final Comparator<FeatureGroupInfo> sFeatureGroupComparator =
            new Comparator<FeatureGroupInfo>() {
                @Override
                public int compare(FeatureGroupInfo o1, FeatureGroupInfo o2) {
                    return compareFeatureInfoArrays(o1.features, o2.features);
                }
            };

    private static final Comparator<FeatureInfo> sFeatureInfoComparator =
            new Comparator<FeatureInfo>() {
                @Override
                public int compare(FeatureInfo o1, FeatureInfo o2) {
                    final int diff;
                    if (o1.name == null && o2.name != null) {
                        diff = -1;
                    } else if (o1.name != null && o2.name == null) {
                        diff = 1;
                    } else if (o1.name == null && o2.name == null) {
                        diff = Integer.compare(o1.reqGlEsVersion, o2.reqGlEsVersion);
                    } else {
                        diff = o1.name.compareTo(o2.name);
                    }

                    if (diff == 0) {
                        return Integer.compare(o1.flags, o2.flags);
                    }
                    return diff;
                }
            };
}
