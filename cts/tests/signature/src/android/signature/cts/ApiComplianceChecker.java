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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Checks that the runtime representation of a class matches the API representation of a class.
 */
public class ApiComplianceChecker {

    /** Indicates that the class is an annotation. */
    private static final int CLASS_MODIFIER_ANNOTATION = 0x00002000;

    /** Indicates that the class is an enum. */
    private static final int CLASS_MODIFIER_ENUM       = 0x00004000;

    /** Indicates that the method is a bridge method. */
    private static final int METHOD_MODIFIER_BRIDGE    = 0x00000040;

    /** Indicates that the method is takes a variable number of arguments. */
    private static final int METHOD_MODIFIER_VAR_ARGS  = 0x00000080;

    /** Indicates that the method is a synthetic method. */
    private static final int METHOD_MODIFIER_SYNTHETIC = 0x00001000;

    private static final Set<String> HIDDEN_INTERFACE_WHITELIST = new HashSet<>();

    static {
        // Interfaces that define @hide or @SystemApi or @TestApi methods will by definition contain
        // methods that do not appear in current.txt. Interfaces added to this
        // list are probably not meant to be implemented in an application.
        HIDDEN_INTERFACE_WHITELIST.add("public abstract boolean android.companion.DeviceFilter.matches(D)");
        HIDDEN_INTERFACE_WHITELIST.add("public static <D> boolean android.companion.DeviceFilter.matches(android.companion.DeviceFilter<D>,D)");
        HIDDEN_INTERFACE_WHITELIST.add("public abstract java.lang.String android.companion.DeviceFilter.getDeviceDisplayName(D)");
        HIDDEN_INTERFACE_WHITELIST.add("public abstract int android.companion.DeviceFilter.getMediumType()");
        HIDDEN_INTERFACE_WHITELIST.add("public abstract void android.nfc.tech.TagTechnology.reconnect() throws java.io.IOException");
        HIDDEN_INTERFACE_WHITELIST.add("public abstract void android.os.IBinder.shellCommand(java.io.FileDescriptor,java.io.FileDescriptor,java.io.FileDescriptor,java.lang.String[],android.os.ShellCallback,android.os.ResultReceiver) throws android.os.RemoteException");
        HIDDEN_INTERFACE_WHITELIST.add("public abstract int android.text.ParcelableSpan.getSpanTypeIdInternal()");
        HIDDEN_INTERFACE_WHITELIST.add("public abstract void android.text.ParcelableSpan.writeToParcelInternal(android.os.Parcel,int)");
        HIDDEN_INTERFACE_WHITELIST.add("public abstract void android.view.WindowManager.requestAppKeyboardShortcuts(android.view.WindowManager$KeyboardShortcutsReceiver,int)");
        HIDDEN_INTERFACE_WHITELIST.add("public abstract boolean javax.microedition.khronos.egl.EGL10.eglReleaseThread()");
        HIDDEN_INTERFACE_WHITELIST.add("public abstract void org.w3c.dom.ls.LSSerializer.setFilter(org.w3c.dom.ls.LSSerializerFilter)");
        HIDDEN_INTERFACE_WHITELIST.add("public abstract org.w3c.dom.ls.LSSerializerFilter org.w3c.dom.ls.LSSerializer.getFilter()");
        HIDDEN_INTERFACE_WHITELIST.add("public abstract android.graphics.Region android.view.WindowManager.getCurrentImeTouchRegion()");
    }


    private final ResultObserver resultObserver;

    public ApiComplianceChecker(ResultObserver resultObserver) {
        this.resultObserver = resultObserver;
    }

    private static void loge(String message, Exception exception) {
        System.err.println(String.format("%s: %s", message, exception));
    }

    private void logMismatchInterfaceSignature(JDiffClassDescription.JDiffType mClassType,
            String classFullName, String errorMessage) {
        if (JDiffClassDescription.JDiffType.INTERFACE.equals(mClassType)) {
            resultObserver.notifyFailure(FailureType.MISMATCH_INTERFACE,
                    classFullName,
                    errorMessage);
        } else {
            resultObserver.notifyFailure(FailureType.MISMATCH_CLASS,
                    classFullName,
                    errorMessage);
        }
    }

    /**
     * Checks test class's name, modifier, fields, constructors, and
     * methods.
     */
    public void checkSignatureCompliance(JDiffClassDescription classDescription) {
        Class<?> runtimeClass = checkClassCompliance(classDescription);
        if (runtimeClass != null) {
            checkFieldsCompliance(classDescription, runtimeClass);
            checkConstructorCompliance(classDescription, runtimeClass);
            checkMethodCompliance(classDescription, runtimeClass);
        }
    }

    /**
     * Checks that the class found through reflection matches the
     * specification from the API xml file.
     *
     * @param classDescription a description of a class in an API.
     */
    @SuppressWarnings("unchecked")
    private Class<?> checkClassCompliance(JDiffClassDescription classDescription) {
        try {
            Class<?> runtimeClass = findRequiredClass(classDescription);

            if (runtimeClass == null) {
                // No class found, notify the observer according to the class type
                if (JDiffClassDescription.JDiffType.INTERFACE.equals(
                        classDescription.getClassType())) {
                    resultObserver.notifyFailure(FailureType.MISSING_INTERFACE,
                            classDescription.getAbsoluteClassName(),
                            "Classloader is unable to find " + classDescription
                                    .getAbsoluteClassName());
                } else {
                    resultObserver.notifyFailure(FailureType.MISSING_CLASS,
                            classDescription.getAbsoluteClassName(),
                            "Classloader is unable to find " + classDescription
                                    .getAbsoluteClassName());
                }

                return null;
            }

            List<String> methods = checkInterfaceMethodCompliance(classDescription, runtimeClass);
            if (JDiffClassDescription.JDiffType.INTERFACE.equals(classDescription.getClassType()) && methods.size() > 0) {
                resultObserver.notifyFailure(FailureType.MISMATCH_INTERFACE_METHOD,
                        classDescription.getAbsoluteClassName(), "Interfaces cannot be modified: "
                                + classDescription.getAbsoluteClassName() + ": " + methods);
                return null;
            }

            if (!checkClassModifiersCompliance(classDescription, runtimeClass)) {
                logMismatchInterfaceSignature(classDescription.getClassType(),
                        classDescription.getAbsoluteClassName(),
                                "Non-compatible class found when looking for " +
                                        classDescription.toSignatureString());
                return null;
            }

            if (!checkClassAnnotationCompliance(classDescription, runtimeClass)) {
                logMismatchInterfaceSignature(classDescription.getClassType(),
                        classDescription.getAbsoluteClassName(),
                                "Annotation mismatch");
                return null;
            }

            if (!runtimeClass.isAnnotation()) {
                // check father class
                if (!checkClassExtendsCompliance(classDescription, runtimeClass)) {
                    logMismatchInterfaceSignature(classDescription.getClassType(),
                            classDescription.getAbsoluteClassName(),
                                    "Extends mismatch");
                    return null;
                }

                // check implements interface
                if (!checkClassImplementsCompliance(classDescription, runtimeClass)) {
                    logMismatchInterfaceSignature(classDescription.getClassType(),
                            classDescription.getAbsoluteClassName(),
                                    "Implements mismatch");
                    return null;
                }
            }
            return runtimeClass;
        } catch (Exception e) {
            loge("Got exception when checking field compliance", e);
            resultObserver.notifyFailure(
                    FailureType.CAUGHT_EXCEPTION,
                    classDescription.getAbsoluteClassName(),
                    "Exception!");
            return null;
        }
    }

    private Class<?> findRequiredClass(JDiffClassDescription classDescription) {
        try {
            return ReflectionHelper.findMatchingClass(classDescription);
        } catch (ClassNotFoundException e) {
            loge("ClassNotFoundException for " + classDescription.getAbsoluteClassName(), e);
            return null;
        }
    }

    /**
     * Validate that an interfaces method count is as expected.
     *
     * @param classDescription the class's API description.
     * @param runtimeClass the runtime class corresponding to {@code classDescription}.
     */
    private static List<String> checkInterfaceMethodCompliance(
            JDiffClassDescription classDescription, Class<?> runtimeClass) {
        List<String> unexpectedMethods = new ArrayList<>();
        for (Method method : runtimeClass.getDeclaredMethods()) {
            if (method.isDefault()) {
                continue;
            }
            if (method.isSynthetic()) {
                continue;
            }
            if (method.isBridge()) {
                continue;
            }
            if (HIDDEN_INTERFACE_WHITELIST.contains(method.toGenericString())) {
                continue;
            }

            boolean foundMatch = false;
            for (JDiffClassDescription.JDiffMethod jdiffMethod : classDescription.getMethods()) {
                if (ReflectionHelper.matches(jdiffMethod, method)) {
                    foundMatch = true;
                }
            }
            if (!foundMatch) {
                unexpectedMethods.add(method.toGenericString());
            }
        }

        return unexpectedMethods;

    }

    /**
     * Checks if the class under test has compliant modifiers compared to the API.
     *
     * @param classDescription a description of a class in an API.
     * @param runtimeClass the runtime class corresponding to {@code classDescription}.
     * @return true if modifiers are compliant.
     */
    private static boolean checkClassModifiersCompliance(JDiffClassDescription classDescription,
            Class<?> runtimeClass) {
        int reflectionModifier = runtimeClass.getModifiers();
        int apiModifier = classDescription.getModifier();

        // If the api class isn't abstract
        if (((apiModifier & Modifier.ABSTRACT) == 0) &&
                // but the reflected class is
                ((reflectionModifier & Modifier.ABSTRACT) != 0) &&
                // and it isn't an enum
                !classDescription.isEnumType()) {
            // that is a problem
            return false;
        }
        // ABSTRACT check passed, so mask off ABSTRACT
        reflectionModifier &= ~Modifier.ABSTRACT;
        apiModifier &= ~Modifier.ABSTRACT;

        if (classDescription.isAnnotation()) {
            reflectionModifier &= ~CLASS_MODIFIER_ANNOTATION;
        }
        if (runtimeClass.isInterface()) {
            reflectionModifier &= ~(Modifier.INTERFACE);
        }
        if (classDescription.isEnumType() && runtimeClass.isEnum()) {
            reflectionModifier &= ~CLASS_MODIFIER_ENUM;
        }

        return ((reflectionModifier == apiModifier) &&
                (classDescription.isEnumType() == runtimeClass.isEnum()));
    }

    /**
     * Checks if the class under test is compliant with regards to
     * annnotations when compared to the API.
     *
     * @param classDescription a description of a class in an API.
     * @param runtimeClass the runtime class corresponding to {@code classDescription}.
     * @return true if the class is compliant
     */
    private static boolean checkClassAnnotationCompliance(JDiffClassDescription classDescription,
            Class<?> runtimeClass) {
        if (runtimeClass.isAnnotation()) {
            // check annotation
            for (String inter : classDescription.getImplInterfaces()) {
                if ("java.lang.annotation.Annotation".equals(inter)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    /**
     * Checks if the class under test extends the proper classes
     * according to the API.
     *
     * @param classDescription a description of a class in an API.
     * @param runtimeClass the runtime class corresponding to {@code classDescription}.
     * @return true if the class is compliant.
     */
    private static boolean checkClassExtendsCompliance(JDiffClassDescription classDescription,
            Class<?> runtimeClass) {
        // Nothing to check if it doesn't extend anything.
        if (classDescription.getExtendedClass() != null) {
            Class<?> superClass = runtimeClass.getSuperclass();

            while (superClass != null) {
                if (superClass.getCanonicalName().equals(classDescription.getExtendedClass())) {
                    return true;
                }
                superClass = superClass.getSuperclass();
            }
            // Couldn't find a matching superclass.
            return false;
        }
        return true;
    }

    /**
     * Checks if the class under test implements the proper interfaces
     * according to the API.
     *
     * @param classDescription a description of a class in an API.
     * @param runtimeClass the runtime class corresponding to {@code classDescription}.
     * @return true if the class is compliant
     */
    private static boolean checkClassImplementsCompliance(JDiffClassDescription classDescription,
            Class<?> runtimeClass) {
        Class<?>[] interfaces = runtimeClass.getInterfaces();
        Set<String> interFaceSet = new HashSet<>();

        for (Class<?> c : interfaces) {
            interFaceSet.add(c.getCanonicalName());
        }

        for (String inter : classDescription.getImplInterfaces()) {
            if (!interFaceSet.contains(inter)) {
                return false;
            }
        }
        return true;
    }


    /**
     * Checks all fields in test class for compliance with the API xml.
     *
     * @param classDescription a description of a class in an API.
     * @param runtimeClass the runtime class corresponding to {@code classDescription}.
     */
    @SuppressWarnings("unchecked")
    private void checkFieldsCompliance(JDiffClassDescription classDescription,
            Class<?> runtimeClass) {
        // A map of field name to field of the fields contained in runtimeClass.
        Map<String, Field> classFieldMap = buildFieldMap(runtimeClass);
        for (JDiffClassDescription.JDiffField field : classDescription.getFields()) {
            try {
                Field f = classFieldMap.get(field.mName);
                if (f == null) {
                    resultObserver.notifyFailure(FailureType.MISSING_FIELD,
                            field.toReadableString(classDescription.getAbsoluteClassName()),
                            "No field with correct signature found:" +
                                    field.toSignatureString());
                } else if (f.getModifiers() != field.mModifier) {
                    resultObserver.notifyFailure(FailureType.MISMATCH_FIELD,
                            field.toReadableString(classDescription.getAbsoluteClassName()),
                            "Non-compatible field modifiers found when looking for " +
                                    field.toSignatureString());
                } else if (!checkFieldValueCompliance(field, f)) {
                    resultObserver.notifyFailure(FailureType.MISMATCH_FIELD,
                            field.toReadableString(classDescription.getAbsoluteClassName()),
                            "Incorrect field value found when looking for " +
                                    field.toSignatureString());
                } else if (!f.getType().getCanonicalName().equals(field.mFieldType)) {
                    // type name does not match, but this might be a generic
                    String genericTypeName = null;
                    Type type = f.getGenericType();
                    if (type != null) {
                        genericTypeName = type instanceof Class ? ((Class) type).getName() :
                                type.toString().replace('$', '.');
                    }
                    if (genericTypeName == null || !genericTypeName.equals(field.mFieldType)) {
                        resultObserver.notifyFailure(
                                FailureType.MISMATCH_FIELD,
                                field.toReadableString(classDescription.getAbsoluteClassName()),
                                "Non-compatible field type found when looking for " +
                                        field.toSignatureString());
                    }
                }

            } catch (Exception e) {
                loge("Got exception when checking field compliance", e);
                resultObserver.notifyFailure(
                        FailureType.CAUGHT_EXCEPTION,
                        field.toReadableString(classDescription.getAbsoluteClassName()),
                        "Exception!");
            }
        }
    }

    /**
     * Checks whether the field values are compatible.
     *
     * @param apiField The field as defined by the platform API.
     * @param deviceField The field as defined by the device under test.
     */
    private static boolean checkFieldValueCompliance(JDiffClassDescription.JDiffField apiField, Field deviceField)
            throws IllegalAccessException {
        if ((apiField.mModifier & Modifier.FINAL) == 0 ||
                (apiField.mModifier & Modifier.STATIC) == 0) {
            // Only final static fields can have fixed values.
            return true;
        }
        if (apiField.getValueString() == null) {
            // If we don't define a constant value for it, then it can be anything.
            return true;
        }
        // Some fields may be protected or package-private
        deviceField.setAccessible(true);
        switch (apiField.mFieldType) {
            case "byte":
                return Objects.equals(apiField.getValueString(),
                        Byte.toString(deviceField.getByte(null)));
            case "char":
                return Objects.equals(apiField.getValueString(),
                        Integer.toString(deviceField.getChar(null)));
            case "short":
                return Objects.equals(apiField.getValueString(),
                        Short.toString(deviceField.getShort(null)));
            case "int":
                return Objects.equals(apiField.getValueString(),
                        Integer.toString(deviceField.getInt(null)));
            case "long":
                return Objects.equals(apiField.getValueString(),
                        Long.toString(deviceField.getLong(null)) + "L");
            case "float":
                return Objects.equals(apiField.getValueString(),
                        canonicalizeFloatingPoint(
                                Float.toString(deviceField.getFloat(null)), "f"));
            case "double":
                return Objects.equals(apiField.getValueString(),
                        canonicalizeFloatingPoint(
                                Double.toString(deviceField.getDouble(null)), ""));
            case "boolean":
                return Objects.equals(apiField.getValueString(),
                        Boolean.toString(deviceField.getBoolean(null)));
            case "java.lang.String":
                String value = apiField.getValueString();
                // Remove the quotes the value string is wrapped in
                value = unescapeFieldStringValue(value.substring(1, value.length() - 1));
                return Objects.equals(value, deviceField.get(null));
            default:
                return true;
        }
    }

    /**
     * Canonicalize the string representation of floating point numbers.
     *
     * This needs to be kept in sync with the doclava canonicalization.
     */
    private static String canonicalizeFloatingPoint(String val, String suffix) {
        switch (val) {
            case "Infinity":
                return "(1.0" + suffix + "/0.0" + suffix + ")";
            case "-Infinity":
                return "(-1.0" + suffix + "/0.0" + suffix + ")";
            case "NaN":
                return "(0.0" + suffix + "/0.0" + suffix + ")";
        }

        if (val.indexOf('E') != -1) {
            return val + suffix;
        }

        // 1.0 is the only case where a trailing "0" is allowed.
        // 1.00 is canonicalized as 1.0.
        int i = val.length() - 1;
        int d = val.indexOf('.');
        while (i >= d + 2 && val.charAt(i) == '0') {
            val = val.substring(0, i--);
        }
        return val + suffix;
    }

    // This unescapes the string format used by doclava and so needs to be kept in sync with any
    // changes made to that format.
    private static String unescapeFieldStringValue(String str) {
        final int N = str.length();

        // If there's no special encoding strings in the string then just return it.
        if (str.indexOf('\\') == -1) {
            return str;
        }

        final StringBuilder buf = new StringBuilder(str.length());
        char escaped = 0;
        final int START = 0;
        final int CHAR1 = 1;
        final int CHAR2 = 2;
        final int CHAR3 = 3;
        final int CHAR4 = 4;
        final int ESCAPE = 5;
        int state = START;

        for (int i = 0; i < N; i++) {
            final char c = str.charAt(i);
            switch (state) {
                case START:
                    if (c == '\\') {
                        state = ESCAPE;
                    } else {
                        buf.append(c);
                    }
                    break;
                case ESCAPE:
                    switch (c) {
                        case '\\':
                            buf.append('\\');
                            state = START;
                            break;
                        case 't':
                            buf.append('\t');
                            state = START;
                            break;
                        case 'b':
                            buf.append('\b');
                            state = START;
                            break;
                        case 'r':
                            buf.append('\r');
                            state = START;
                            break;
                        case 'n':
                            buf.append('\n');
                            state = START;
                            break;
                        case 'f':
                            buf.append('\f');
                            state = START;
                            break;
                        case '\'':
                            buf.append('\'');
                            state = START;
                            break;
                        case '\"':
                            buf.append('\"');
                            state = START;
                            break;
                        case 'u':
                            state = CHAR1;
                            escaped = 0;
                            break;
                    }
                    break;
                case CHAR1:
                case CHAR2:
                case CHAR3:
                case CHAR4:
                    escaped <<= 4;
                    if (c >= '0' && c <= '9') {
                        escaped |= c - '0';
                    } else if (c >= 'a' && c <= 'f') {
                        escaped |= 10 + (c - 'a');
                    } else if (c >= 'A' && c <= 'F') {
                        escaped |= 10 + (c - 'A');
                    } else {
                        throw new RuntimeException(
                                "bad escape sequence: '" + c + "' at pos " + i + " in: \""
                                        + str + "\"");
                    }
                    if (state == CHAR4) {
                        buf.append(escaped);
                        state = START;
                    } else {
                        state++;
                    }
                    break;
            }
        }
        if (state != START) {
            throw new RuntimeException("unfinished escape sequence: " + str);
        }
        return buf.toString();
    }

    /**
     * Scan a class (an its entire inheritance chain) for fields.
     *
     * @return a {@link Map} of fieldName to {@link Field}
     */
    private static Map<String, Field> buildFieldMap(Class testClass) {
        Map<String, Field> fieldMap = new HashMap<>();
        // Scan the superclass
        if (testClass.getSuperclass() != null) {
            fieldMap.putAll(buildFieldMap(testClass.getSuperclass()));
        }

        // Scan the interfaces
        for (Class interfaceClass : testClass.getInterfaces()) {
            fieldMap.putAll(buildFieldMap(interfaceClass));
        }

        // Check the fields in the test class
        for (Field field : testClass.getDeclaredFields()) {
            fieldMap.put(field.getName(), field);
        }

        return fieldMap;
    }

    /**
     * Checks whether the constructor parsed from API xml file and
     * Java reflection are compliant.
     *
     * @param classDescription a description of a class in an API.
     * @param runtimeClass the runtime class corresponding to {@code classDescription}.
     */
    @SuppressWarnings("unchecked")
    private void checkConstructorCompliance(JDiffClassDescription classDescription,
            Class<?> runtimeClass) {
        for (JDiffClassDescription.JDiffConstructor con : classDescription.getConstructors()) {
            try {
                Constructor<?> c = ReflectionHelper.findMatchingConstructor(runtimeClass, con);
                if (c == null) {
                    resultObserver.notifyFailure(FailureType.MISSING_METHOD,
                            con.toReadableString(classDescription.getAbsoluteClassName()),
                            "No method with correct signature found:" +
                                    con.toSignatureString());
                } else {
                    if (c.isVarArgs()) {// some method's parameter are variable args
                        con.mModifier |= METHOD_MODIFIER_VAR_ARGS;
                    }
                    if (c.getModifiers() != con.mModifier) {
                        resultObserver.notifyFailure(
                                FailureType.MISMATCH_METHOD,
                                con.toReadableString(classDescription.getAbsoluteClassName()),
                                "Non-compatible method found when looking for " +
                                        con.toSignatureString());
                    }
                }
            } catch (Exception e) {
                loge("Got exception when checking constructor compliance", e);
                resultObserver.notifyFailure(FailureType.CAUGHT_EXCEPTION,
                        con.toReadableString(classDescription.getAbsoluteClassName()),
                        "Exception!");
            }
        }
    }

    /**
     * Checks that the method found through reflection matches the
     * specification from the API xml file.
     *
     * @param classDescription a description of a class in an API.
     * @param runtimeClass the runtime class corresponding to {@code classDescription}.
     */
    private void checkMethodCompliance(JDiffClassDescription classDescription,
            Class<?> runtimeClass) {
        for (JDiffClassDescription.JDiffMethod method : classDescription.getMethods()) {
            try {

                Method m = ReflectionHelper.findMatchingMethod(runtimeClass, method);
                if (m == null) {
                    resultObserver.notifyFailure(FailureType.MISSING_METHOD,
                            method.toReadableString(classDescription.getAbsoluteClassName()),
                            "No method with correct signature found:" +
                                    method.toSignatureString());
                } else {
                    if (m.isVarArgs()) {
                        method.mModifier |= METHOD_MODIFIER_VAR_ARGS;
                    }
                    if (m.isBridge()) {
                        method.mModifier |= METHOD_MODIFIER_BRIDGE;
                    }
                    if (m.isSynthetic()) {
                        method.mModifier |= METHOD_MODIFIER_SYNTHETIC;
                    }

                    // FIXME: A workaround to fix the final mismatch on enumeration
                    if (runtimeClass.isEnum() && method.mName.equals("values")) {
                        return;
                    }

                    if (!areMethodsModifiedCompatible(classDescription, method, m)) {
                        resultObserver.notifyFailure(FailureType.MISMATCH_METHOD,
                                method.toReadableString(classDescription.getAbsoluteClassName()),
                                "Non-compatible method found when looking for " +
                                        method.toSignatureString());
                    }
                }
            } catch (Exception e) {
                loge("Got exception when checking method compliance", e);
                resultObserver.notifyFailure(FailureType.CAUGHT_EXCEPTION,
                        method.toReadableString(classDescription.getAbsoluteClassName()),
                        "Exception!");
            }
        }
    }

    /**
     * Checks to ensure that the modifiers value for two methods are compatible.
     *
     * Allowable differences are:
     *   - synchronized is allowed to be removed from an apiMethod
     *     that has it
     *   - the native modified is ignored
     *
     * @param classDescription a description of a class in an API.
     * @param apiMethod the method read from the api file.
     * @param reflectedMethod the method found via reflection.
     */
    private static boolean areMethodsModifiedCompatible(
            JDiffClassDescription classDescription,
            JDiffClassDescription.JDiffMethod apiMethod,
            Method reflectedMethod) {

        // If the apiMethod isn't synchronized
        if (((apiMethod.mModifier & Modifier.SYNCHRONIZED) == 0) &&
                // but the reflected method is
                ((reflectedMethod.getModifiers() & Modifier.SYNCHRONIZED) != 0)) {
            // that is a problem
            return false;
        }

        // Mask off NATIVE since it is a don't care.  Also mask off
        // SYNCHRONIZED since we've already handled that check.
        int ignoredMods = (Modifier.NATIVE | Modifier.SYNCHRONIZED | Modifier.STRICT);
        int mod1 = reflectedMethod.getModifiers() & ~ignoredMods;
        int mod2 = apiMethod.mModifier & ~ignoredMods;

        // We can ignore FINAL for classes
        if ((classDescription.getModifier() & Modifier.FINAL) != 0) {
            mod1 &= ~Modifier.FINAL;
            mod2 &= ~Modifier.FINAL;
        }

        return mod1 == mod2;
    }
}
