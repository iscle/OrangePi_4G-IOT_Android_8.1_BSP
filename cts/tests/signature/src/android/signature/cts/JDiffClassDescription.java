/*
 * Copyright (C) 2008 The Android Open Source Project
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

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Represents class descriptions loaded from a jdiff xml file.  Used
 * for CTS SignatureTests.
 */
public class JDiffClassDescription {

    public enum JDiffType {
        INTERFACE, CLASS
    }

    private final String mPackageName;
    private final String mShortClassName;

    /**
     * Package name + short class name
     */
    private final String mAbsoluteClassName;

    private int mModifier;

    private String mExtendedClass;
    private List<String> implInterfaces = new ArrayList<>();
    private List<JDiffField> jDiffFields = new ArrayList<>();
    private List<JDiffMethod> jDiffMethods = new ArrayList<>();
    private List<JDiffConstructor> jDiffConstructors = new ArrayList<>();

    private JDiffType mClassType;

    /**
     * Creates a new JDiffClassDescription.
     *
     * @param pkg the java package this class will end up in.
     * @param className the name of the class.
     */
    public JDiffClassDescription(String pkg, String className) {
        mPackageName = pkg;
        mShortClassName = className;
        mAbsoluteClassName = mPackageName + "." + mShortClassName;
    }


    String getPackageName() {
        return mPackageName;
    }

    String getShortClassName() {
        return mShortClassName;
    }

    int getModifier() {
        return mModifier;
    }

    String getExtendedClass() {
        return mExtendedClass;
    }

    List<String> getImplInterfaces() {
        return implInterfaces;
    }

    List<JDiffField> getFields() {
        return jDiffFields;
    }

    List<JDiffMethod> getMethods() {
        return jDiffMethods;
    }

    List<JDiffConstructor> getConstructors() {
        return jDiffConstructors;
    }

    JDiffType getClassType() {
        return mClassType;
    }

    /**
     * adds implemented interface name.
     *
     * @param iname name of interface
     */
    void addImplInterface(String iname) {
        implInterfaces.add(iname);
    }

    /**
     * Adds a field.
     *
     * @param field the field to be added.
     */
    public void addField(JDiffField field) {
        jDiffFields.add(field);
    }

    /**
     * Adds a method.
     *
     * @param method the method to be added.
     */
    public void addMethod(JDiffMethod method) {
        jDiffMethods.add(method);
    }

    /**
     * Adds a constructor.
     *
     * @param tc the constructor to be added.
     */
    public void addConstructor(JDiffConstructor tc) {
        jDiffConstructors.add(tc);
    }

    static String convertModifiersToAccessLevel(int modifiers) {
        if ((modifiers & Modifier.PUBLIC) != 0) {
            return "public";
        } else if ((modifiers & Modifier.PRIVATE) != 0) {
            return "private";
        } else if ((modifiers & Modifier.PROTECTED) != 0) {
            return "protected";
        } else {
            // package protected
            return "";
        }
    }

    static String convertModifersToModifierString(int modifiers) {
        StringBuilder sb = new StringBuilder();
        String separator = "";

        // order taken from Java Language Spec, sections 8.1.1, 8.3.1, and 8.4.3
        if ((modifiers & Modifier.ABSTRACT) != 0) {
            sb.append(separator).append("abstract");
            separator = " ";
        }
        if ((modifiers & Modifier.STATIC) != 0) {
            sb.append(separator).append("static");
            separator = " ";
        }
        if ((modifiers & Modifier.FINAL) != 0) {
            sb.append(separator).append("final");
            separator = " ";
        }
        if ((modifiers & Modifier.TRANSIENT) != 0) {
            sb.append(separator).append("transient");
            separator = " ";
        }
        if ((modifiers & Modifier.VOLATILE) != 0) {
            sb.append(separator).append("volatile");
            separator = " ";
        }
        if ((modifiers & Modifier.SYNCHRONIZED) != 0) {
            sb.append(separator).append("synchronized");
            separator = " ";
        }
        if ((modifiers & Modifier.NATIVE) != 0) {
            sb.append(separator).append("native");
            separator = " ";
        }
        if ((modifiers & Modifier.STRICT) != 0) {
            sb.append(separator).append("strictfp");
        }

        return sb.toString();
    }

    abstract static class JDiffElement {
        final String mName;
        int mModifier;

        JDiffElement(String name, int modifier) {
            mName = name;
            mModifier = modifier;
        }
    }

    /**
     * Represents a  field.
     */
    public static final class JDiffField extends JDiffElement {
        String mFieldType;
        private String mFieldValue;

        public JDiffField(String name, String fieldType, int modifier, String value) {
            super(name, modifier);

            mFieldType = fieldType;
            mFieldValue = value;
        }

        /**
         * A string representation of the value within the field.
         */
        public String getValueString() {
            return mFieldValue;
        }

        /**
         * Make a readable string according to the class name specified.
         *
         * @param className The specified class name.
         * @return A readable string to represent this field along with the class name.
         */
        String toReadableString(String className) {
            return className + "#" + mName + "(" + mFieldType + ")";
        }

        public String toSignatureString() {
            StringBuilder sb = new StringBuilder();

            // access level
            String accesLevel = convertModifiersToAccessLevel(mModifier);
            if (!"".equals(accesLevel)) {
                sb.append(accesLevel).append(" ");
            }

            String modifierString = convertModifersToModifierString(mModifier);
            if (!"".equals(modifierString)) {
                sb.append(modifierString).append(" ");
            }

            sb.append(mFieldType).append(" ");

            sb.append(mName);

            return sb.toString();
        }
    }

    /**
     * Represents a method.
     */
    public static class JDiffMethod extends JDiffElement {
        String mReturnType;
        ArrayList<String> mParamList;
        ArrayList<String> mExceptionList;

        public JDiffMethod(String name, int modifier, String returnType) {
            super(name, modifier);

            if (returnType == null) {
                mReturnType = "void";
            } else {
                mReturnType = scrubJdiffParamType(returnType);
            }

            mParamList = new ArrayList<>();
            mExceptionList = new ArrayList<>();
        }

        /**
         * Adds a parameter.
         *
         * @param param parameter type
         */
        public void addParam(String param) {
            mParamList.add(scrubJdiffParamType(param));
        }

        /**
         * Adds an exception.
         *
         * @param exceptionName name of exception
         */
        public void addException(String exceptionName) {
            mExceptionList.add(exceptionName);
        }

        /**
         * Makes a readable string according to the class name specified.
         *
         * @param className The specified class name.
         * @return A readable string to represent this method along with the class name.
         */
        String toReadableString(String className) {
            return className + "#" + mName + "(" + convertParamList(mParamList) + ")";
        }

        /**
         * Converts a parameter array to a string
         *
         * @param params the array to convert
         * @return converted parameter string
         */
        private static String convertParamList(final ArrayList<String> params) {

            StringBuilder paramList = new StringBuilder();

            if (params != null) {
                for (String str : params) {
                    paramList.append(str).append(", ");
                }
                if (params.size() > 0) {
                    paramList.delete(paramList.length() - 2, paramList.length());
                }
            }

            return paramList.toString();
        }

        public String toSignatureString() {
            StringBuilder sb = new StringBuilder();

            // access level
            String accesLevel = convertModifiersToAccessLevel(mModifier);
            if (!"".equals(accesLevel)) {
                sb.append(accesLevel).append(" ");
            }

            String modifierString = convertModifersToModifierString(mModifier);
            if (!"".equals(modifierString)) {
                sb.append(modifierString).append(" ");
            }

            String returnType = getReturnType();
            if (!"".equals(returnType)) {
                sb.append(returnType).append(" ");
            }

            sb.append(mName);
            sb.append("(");
            for (int x = 0; x < mParamList.size(); x++) {
                sb.append(mParamList.get(x));
                if (x + 1 != mParamList.size()) {
                    sb.append(", ");
                }
            }
            sb.append(")");

            // does it throw?
            if (mExceptionList.size() > 0) {
                sb.append(" throws ");
                for (int x = 0; x < mExceptionList.size(); x++) {
                    sb.append(mExceptionList.get(x));
                    if (x + 1 != mExceptionList.size()) {
                        sb.append(", ");
                    }
                }
            }

            return sb.toString();
        }

        /**
         * Gets the return type.
         *
         * @return the return type of this method.
         */
        protected String getReturnType() {
            return mReturnType;
        }
    }

    /**
     * Represents a constructor.
     */
    public static final class JDiffConstructor extends JDiffMethod {
        public JDiffConstructor(String name, int modifier) {
            super(name, modifier, null);
        }

        /**
         * Gets the return type.
         *
         * @return the return type of this method.
         */
        @Override
        protected String getReturnType() {
            // Constructors have no return type.
            return "";
        }
    }

    /**
     * Gets the list of fields found within this class.
     *
     * @return the list of fields.
     */
    public Collection<JDiffField> getFieldList() {
        return jDiffFields;
    }

    /**
     * Convert the class into a printable signature string.
     *
     * @return the signature string
     */
    public String toSignatureString() {
        StringBuilder sb = new StringBuilder();

        String accessLevel = convertModifiersToAccessLevel(mModifier);
        if (!"".equals(accessLevel)) {
            sb.append(accessLevel).append(" ");
        }
        if (!JDiffType.INTERFACE.equals(mClassType)) {
            String modifierString = convertModifersToModifierString(mModifier);
            if (!"".equals(modifierString)) {
                sb.append(modifierString).append(" ");
            }
            sb.append("class ");
        } else {
            sb.append("interface ");
        }
        // class name
        sb.append(mShortClassName);

        // does it extends something?
        if (mExtendedClass != null) {
            sb.append(" extends ").append(mExtendedClass).append(" ");
        }

        // implements something?
        if (implInterfaces.size() > 0) {
            sb.append(" implements ");
            for (int x = 0; x < implInterfaces.size(); x++) {
                String interf = implInterfaces.get(x);
                sb.append(interf);
                // if not last elements
                if (x + 1 != implInterfaces.size()) {
                    sb.append(", ");
                }
            }
        }
        return sb.toString();
    }

    /**
     * Sees if the class under test is actually an enum.
     *
     * @return true if this class is enum
     */
    boolean isEnumType() {
        return "java.lang.Enum".equals(mExtendedClass);
    }

    /**
     * Sees if the class under test is actually an annotation.
     *
     * @return true if this class is Annotation.
     */
    boolean isAnnotation() {
        return implInterfaces.contains("java.lang.annotation.Annotation");
    }

    /**
     * Gets the class name for the class under test.
     *
     * @return the class name.
     */
    String getClassName() {
        return mShortClassName;
    }

    /**
     * Gets the package name + short class name
     *
     * @return The package + short class name
     */
    public String getAbsoluteClassName() {
        return mAbsoluteClassName;
    }

    /**
     * Sets the modifier for the class under test.
     *
     * @param modifier the modifier
     */
    public void setModifier(int modifier) {
        mModifier = modifier;
    }

    /**
     * Sets the return type for the class under test.
     *
     * @param type the return type
     */
    public void setType(JDiffType type) {
        mClassType = type;
    }

    /**
     * Sets the class that is beign extended for the class under test.
     *
     * @param extendsClass the class being extended.
     */
    void setExtendsClass(String extendsClass) {
        mExtendedClass = extendsClass;
    }

    /**
     * Cleans up jdiff parameters to canonicalize them.
     *
     * @param paramType the parameter from jdiff.
     * @return the scrubbed version of the parameter.
     */
    private static String scrubJdiffParamType(String paramType) {
        // <? extends java.lang.Object and <?> are the same, so
        // canonicalize them to one form.
        return paramType
            .replace("? extends java.lang.Object", "?")
            .replace("? super java.lang.Object", "? super ?");
    }

    @Override
    public String toString() {
        return mAbsoluteClassName;
    }
}
