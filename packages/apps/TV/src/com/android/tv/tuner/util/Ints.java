package com.android.tv.tuner.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Static utility methods pertaining to int primitives. (Referred Guava's Ints class)
 */
public class Ints {
    private Ints() {}

    public static int[] toArray(List<Integer> integerList) {
        int[] intArray = new int[integerList.size()];
        int i = 0;
        for (Integer data : integerList) {
            intArray[i++] = data;
        }
        return intArray;
    }

    public static List<Integer> asList(int[] intArray) {
        List<Integer> integerList = new ArrayList<>(intArray.length);
        for (int data : intArray) {
            integerList.add(data);
        }
        return integerList;
    }
}
