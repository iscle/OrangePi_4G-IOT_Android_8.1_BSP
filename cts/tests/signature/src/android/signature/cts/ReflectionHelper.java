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
package android.signature.cts;

import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.List;

/**
 * Uses reflection to obtain runtime representations of elements in the API.
 */
public class ReflectionHelper {

    private static void loge(String message, Exception exception) {
        System.err.println(String.format("%s: %s", message, exception));
    }

    /**
     * Finds the reflected class for the class under test.
     *
     * @param classDescription the description of the class to find.
     * @return the reflected class, or null if not found.
     */
    @SuppressWarnings("unchecked")
    public static Class<?> findMatchingClass(JDiffClassDescription classDescription)
            throws ClassNotFoundException {
        // even if there are no . in the string, split will return an
        // array of length 1
        String shortClassName = classDescription.getShortClassName();
        String[] classNameParts = shortClassName.split("\\.");
        String packageName = classDescription.getPackageName();
        String currentName = packageName + "." + classNameParts[0];

        Class<?> clz = Class.forName(
                currentName, false, ReflectionHelper.class.getClassLoader());
        String absoluteClassName = classDescription.getAbsoluteClassName();
        if (clz.getCanonicalName().equals(absoluteClassName)) {
            return clz;
        }

        // Then it must be an inner class.
        for (int x = 1; x < classNameParts.length; x++) {
            clz = findInnerClassByName(clz, classNameParts[x]);
            if (clz == null) {
                return null;
            }
            if (clz.getCanonicalName().equals(absoluteClassName)) {
                return clz;
            }
        }
        return null;
    }

    /**
     * Searches the class for the specified inner class.
     *
     * @param clz the class to search in.
     * @param simpleName the simpleName of the class to find
     * @return the class being searched for, or null if it can't be found.
     */
    private static Class<?> findInnerClassByName(Class<?> clz, String simpleName) {
        for (Class<?> c : clz.getDeclaredClasses()) {
            if (c.getSimpleName().equals(simpleName)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Searches available constructor.
     *
     * @param runtimeClass the class in which to search.
     * @param jdiffDes constructor description to find.
     * @return reflected constructor, or null if not found.
     */
    @SuppressWarnings("unchecked")
    static Constructor<?> findMatchingConstructor(Class<?> runtimeClass,
            JDiffClassDescription.JDiffConstructor jdiffDes) {
        for (Constructor<?> c : runtimeClass.getDeclaredConstructors()) {
            Type[] params = c.getGenericParameterTypes();
            boolean isStaticClass = ((runtimeClass.getModifiers() & Modifier.STATIC) != 0);

            int startParamOffset = 0;
            int numberOfParams = params.length;

            // non-static inner class -> skip implicit parent pointer
            // as first arg
            if (runtimeClass.isMemberClass() && !isStaticClass && params.length >= 1) {
                startParamOffset = 1;
                --numberOfParams;
            }

            ArrayList<String> jdiffParamList = jdiffDes.mParamList;
            if (jdiffParamList.size() == numberOfParams) {
                boolean isFound = true;
                // i counts jdiff params, j counts reflected params
                int i = 0;
                int j = startParamOffset;
                while (i < jdiffParamList.size()) {
                    if (!compareParam(jdiffParamList.get(i), params[j])) {
                        isFound = false;
                        break;
                    }
                    ++i;
                    ++j;
                }
                if (isFound) {
                    return c;
                }
            }
        }
        return null;
    }

    /**
     * Compares the parameter from the API and the parameter from
     * reflection.
     *
     * @param jdiffParam param parsed from the API xml file.
     * @param reflectionParamType param gotten from the Java reflection.
     * @return True if the two params match, otherwise return false.
     */
    private static boolean compareParam(String jdiffParam, Type reflectionParamType) {
        if (jdiffParam == null) {
            return false;
        }

        String reflectionParam = typeToString(reflectionParamType);
        // Most things aren't varargs, so just do a simple compare
        // first.
        if (jdiffParam.equals(reflectionParam)) {
            return true;
        }

        // Check for varargs.  jdiff reports varargs as ..., while
        // reflection reports them as []
        int jdiffParamEndOffset = jdiffParam.indexOf("...");
        int reflectionParamEndOffset = reflectionParam.indexOf("[]");
        if (jdiffParamEndOffset != -1 && reflectionParamEndOffset != -1) {
            jdiffParam = jdiffParam.substring(0, jdiffParamEndOffset);
            reflectionParam = reflectionParam.substring(0, reflectionParamEndOffset);
            return jdiffParam.equals(reflectionParam);
        }

        return false;
    }

    /**
     * Finds the reflected method specified by the method description.
     *
     * @param runtimeClass the class in which to search.
     * @param method description of the method to find
     * @return the reflected method, or null if not found.
     */
    @SuppressWarnings("unchecked")
    static Method findMatchingMethod(Class<?> runtimeClass,
            JDiffClassDescription.JDiffMethod method) {
        Method[] methods = runtimeClass.getDeclaredMethods();

        for (Method m : methods) {
            if (matches(method, m)) {
                return m;
            }
        }

        return null;
    }

    /**
     * Checks if the two types of methods are the same.
     *
     * @param jDiffMethod the jDiffMethod to compare
     * @param reflectedMethod the reflected method to compare
     * @return true, if both methods are the same
     */
    static boolean matches(JDiffClassDescription.JDiffMethod jDiffMethod,
            Method reflectedMethod) {
        // If the method names aren't equal, the methods can't match.
        if (!jDiffMethod.mName.equals(reflectedMethod.getName())) {
            return false;
        }
        String jdiffReturnType = jDiffMethod.mReturnType;
        String reflectionReturnType = typeToString(reflectedMethod.getGenericReturnType());
        List<String> jdiffParamList = jDiffMethod.mParamList;

        // Next, compare the return types of the two methods.  If
        // they aren't equal, the methods can't match.
        if (!jdiffReturnType.equals(reflectionReturnType)) {
            return false;
        }

        Type[] params = reflectedMethod.getGenericParameterTypes();

        // Next, check the method parameters.  If they have different
        // parameter lengths, the two methods can't match.
        if (jdiffParamList.size() != params.length) {
            return false;
        }

        boolean piecewiseParamsMatch = true;

        // Compare method parameters piecewise and return true if they all match.
        for (int i = 0; i < jdiffParamList.size(); i++) {
            piecewiseParamsMatch &= compareParam(jdiffParamList.get(i), params[i]);
        }
        if (piecewiseParamsMatch) {
            return true;
        }

        /* NOTE: There are cases where piecewise method parameter checking
         * fails even though the strings are equal, so compare entire strings
         * against each other. This is not done by default to avoid a
         * TransactionTooLargeException.
         * Additionally, this can fail anyway due to extra
         * information dug up by reflection.
         *
         * TODO: fix parameter equality checking and reflection matching
         * See https://b.corp.google.com/issues/27726349
         */

        StringBuilder reflectedMethodParams = new StringBuilder("");
        StringBuilder jdiffMethodParams = new StringBuilder("");

        for (int i = 0; i < jdiffParamList.size(); i++) {
            jdiffMethodParams.append(jdiffParamList.get(i));
            reflectedMethodParams.append(params[i]);
        }

        String jDiffFName = jdiffMethodParams.toString();
        String refName = reflectedMethodParams.toString();

        return jDiffFName.equals(refName);
    }

    /**
     * Converts WildcardType array into a jdiff compatible string..
     * This is a helper function for typeToString.
     *
     * @param types array of types to format.
     * @return the jdiff formatted string.
     */
    private static String concatWildcardTypes(Type[] types) {
        StringBuilder sb = new StringBuilder();
        int elementNum = 0;
        for (Type t : types) {
            sb.append(typeToString(t));
            if (++elementNum < types.length) {
                sb.append(" & ");
            }
        }
        return sb.toString();
    }

    /**
     * Converts a Type into a jdiff compatible String.  The returned
     * types from this function should match the same Strings that
     * jdiff is providing to us.
     *
     * @param type the type to convert.
     * @return the jdiff formatted string.
     */
    private static String typeToString(Type type) {
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;

            StringBuilder sb = new StringBuilder();
            sb.append(typeToString(pt.getRawType()));
            sb.append("<");

            int elementNum = 0;
            Type[] types = pt.getActualTypeArguments();
            for (Type t : types) {
                sb.append(typeToString(t));
                if (++elementNum < types.length) {
                    sb.append(", ");
                }
            }

            sb.append(">");
            return sb.toString();
        } else if (type instanceof TypeVariable) {
            return ((TypeVariable<?>) type).getName();
        } else if (type instanceof Class) {
            return ((Class<?>) type).getCanonicalName();
        } else if (type instanceof GenericArrayType) {
            String typeName = typeToString(((GenericArrayType) type).getGenericComponentType());
            return typeName + "[]";
        } else if (type instanceof WildcardType) {
            WildcardType wt = (WildcardType) type;
            Type[] lowerBounds = wt.getLowerBounds();
            if (lowerBounds.length == 0) {
                String name = "? extends " + concatWildcardTypes(wt.getUpperBounds());

                // Special case for ?
                if (name.equals("? extends java.lang.Object")) {
                    return "?";
                } else {
                    return name;
                }
            } else {
                String name = concatWildcardTypes(wt.getUpperBounds()) +
                        " super " +
                        concatWildcardTypes(wt.getLowerBounds());
                // Another special case for ?
                name = name.replace("java.lang.Object", "?");
                return name;
            }
        } else {
            throw new RuntimeException("Got an unknown java.lang.Type");
        }
    }
}
