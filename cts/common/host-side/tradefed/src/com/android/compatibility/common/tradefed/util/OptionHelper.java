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

import com.android.tradefed.config.Option;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class for manipulating fields with @option annotations.
 */
public final class OptionHelper {

    private OptionHelper() {}

    /**
     * Return the {@link List} of {@link Field} entries on the given object
     * that have the {@link Option} annotation.
     *
     * @param object An object with @option-annotated fields.
     */
    private static List<Field> getFields(Object object) {
        Field[] classFields = object.getClass().getDeclaredFields();
        List<Field> optionFields = new ArrayList<Field>();

        for (Field declaredField : classFields) {
            // allow access to protected and private fields
            declaredField.setAccessible(true);

            // store type and values only in annotated fields
            if (declaredField.isAnnotationPresent(Option.class)) {
                optionFields.add(declaredField);
            }
        }
        return optionFields;
    }

    /**
     * Retrieve a {@link Set} of {@link Option} names present on the given
     * object.
     *
     * @param object An object with @option-annotated fields.
     */
    static Set<String> getOptionNames(Object object) {
        Set<String> options = new HashSet<String>();
        List<Field> optionFields = getFields(object);

        for (Field declaredField : optionFields) {
            Option option = declaredField.getAnnotation(Option.class);
            options.add(option.name());
        }
        return options;
    }

    /**
     * Retrieve a {@link Set} of {@link Option} short names present on the given
     * object.
     *
     * @param object An object with @option-annotated fields.
     */
    static Set<String> getOptionShortNames(Object object) {
        Set<String> shortNames = new HashSet<String>();
        List<Field> optionFields = getFields(object);

        for (Field declaredField : optionFields) {
            Option option = declaredField.getAnnotation(Option.class);
            if (option.shortName() != Option.NO_SHORT_NAME) {
                shortNames.add(String.valueOf(option.shortName()));
            }
        }
        return shortNames;
    }

    /**
     * Retrieve a {@link List} of {@link String} entries of the valid
     * command-line options for the given {@link Object} from the given
     * input {@link String}.
     */
    public static List<String> getValidCliArgs(String commandString, Object object) {
        Set<String> optionNames = OptionHelper.getOptionNames(object);
        Set<String> optionShortNames = OptionHelper.getOptionShortNames(object);
        List<String> validCliArgs = new ArrayList<String>();

        // get option/value substrings from the command-line string
        // N.B. tradefed rewrites some expressions from option="value a b" to "option=value a b"
        String quoteMatching = "(\"[^\"]+\")";
        String nonSpacedHypen = "((?<!\\s)-(?!\\s))";
        Pattern cliPattern = Pattern.compile(
            // match -option=value or --option=value
            "((-[-\\w]+([ =]"
            // allow -option "...", -option x y z, and -option x:y:z
            + "(" + quoteMatching + "|([\\w\\/\\s:.]|"+ nonSpacedHypen + ")+))?"
            + "))|"
            // allow anything in direct quotes
            + quoteMatching
        );
        Matcher matcher = cliPattern.matcher(commandString);

        while (matcher.find()) {
            String optionInput = matcher.group();
            // split between the option name and value
            String[] keyNameTokens = optionInput.split("[ =]", 2);
            // remove initial hyphens and any starting double quote from option args
            String keyName = keyNameTokens[0].replaceFirst("^\"?--?", "");

            // add substrings only when the options are recognized
            if (optionShortNames.contains(keyName) || optionNames.contains(keyName)) {
                // add values separated by spaces or in quotes separately to the return array
                Pattern tokenPattern = Pattern.compile("(\".*\")|[^\\s=]+");
                Matcher tokenMatcher = tokenPattern.matcher(optionInput);
                while (tokenMatcher.find()) {
                    String token = tokenMatcher.group().replaceAll("\"", "");
                    validCliArgs.add(token);
                }
            }
        }
        return validCliArgs;
    }
}
