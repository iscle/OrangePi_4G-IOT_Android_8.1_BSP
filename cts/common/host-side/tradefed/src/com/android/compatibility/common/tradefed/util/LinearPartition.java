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
package com.android.compatibility.common.tradefed.util;

import com.android.compatibility.common.tradefed.testtype.IModuleDef;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper for the shard splitting. Split linearly a list into N sublist with
 * approximately the same weight.
 * TODO: Can be generalized for any weighted objects.
 */
public class LinearPartition {

    /**
     * Split a list of {@link IModuleDef} into k sub list based on the runtime hint.
     *
     * @param seq the full list of {@link IModuleDef} to be splitted
     * @param k the number of sub list we need.
     * @return the List of sublist.
     */
    public static List<List<IModuleDef>> split(List<IModuleDef> seq, int k) {
        ArrayList<List<IModuleDef>> result = new ArrayList<>();

        if (k <= 0) {
            ArrayList<IModuleDef> partition = new ArrayList<>();
            partition.addAll(seq);
            result.add(partition);
            return result;
        }

        int n = seq.size() - 1;

        if (k > n) {
            for (IModuleDef value : seq) {
                ArrayList<IModuleDef> partition = new ArrayList<>();
                partition.add(value);
                result.add(partition);
            }
            return result;
        }

        int[][] table = buildPartitionTable(seq, k);
        k = k - 2;

        while (k >= 0) {
            ArrayList<IModuleDef> partition = new ArrayList<>();

            for (int i = table[n - 1][k] + 1; i < n + 1; i++) {
                partition.add(seq.get(i));
            }

            result.add(0, partition);
            n = table[n - 1][k];
            k = k - 1;
        }

        ArrayList<IModuleDef> partition = new ArrayList<>();

        for (int i = 0; i < n + 1; i++) {
            partition.add(seq.get(i));
        }

        result.add(0, partition);

        return result;
    }

    /**
     * Internal helper to build the partition table of the linear distribution used for splitting.
     */
    private static int[][] buildPartitionTable(List<IModuleDef> seq, int k) {
        int n = seq.size();
        float[][] table = new float[n][k];
        int[][] solution = new int[n - 1][k - 1];

        for (int i = 0; i < n; i++) {
            table[i][0] = seq.get(i).getRuntimeHint() + ((i > 0) ? (table[i - 1][0]) : 0);
        }

        for (int j = 0; j < k; j++) {
            table[0][j] = seq.get(0).getRuntimeHint();
        }

        for (int i = 1; i < n; i++) {
            for (int j = 1; j < k; j++) {
                table[i][j] = Integer.MAX_VALUE;
                for (int x = 0; x < i; x++) {
                    float cost = Math.max(table[x][j - 1], table[i][0] - table[x][0]);
                    if (table[i][j] > cost) {
                        table[i][j] = cost;
                        solution[i - 1][j - 1] = x;
                    }
                }
            }
        }

        return solution;
    }
}
