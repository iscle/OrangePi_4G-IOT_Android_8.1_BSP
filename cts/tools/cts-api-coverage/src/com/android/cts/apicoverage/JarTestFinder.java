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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Representation of the entire CDD. */
class JarTestFinder {

    public static Collection<Class<?>> getClasses(File jarTestFile)
            throws IllegalArgumentException  {
        List<Class<?>> classes = new ArrayList<>();

        try (JarFile jarFile = new JarFile(jarTestFile)) {
            Enumeration<JarEntry> e = jarFile.entries();

            URL[] urls = {
                new URL(String.format("jar:file:%s!/", jarTestFile.getAbsolutePath()))
            };
            URLClassLoader cl = URLClassLoader.newInstance(urls, JarTestFinder.class.getClassLoader());

            while (e.hasMoreElements()) {
                JarEntry je = e.nextElement();
                if (je.isDirectory() || !je.getName().endsWith(".class")
                        || je.getName().contains("$")) {
                    continue;
                }
                String className = getClassName(je.getName());
                if (!className.endsWith("Test")) {
                    continue;
                }
                try {
                    Class<?> cls = cl.loadClass(className);
                    int modifiers = cls.getModifiers();
                    if (!Modifier.isStatic(modifiers)
                            && !Modifier.isPrivate(modifiers)
                            && !Modifier.isProtected(modifiers)
                            && !Modifier.isInterface(modifiers)
                            && !Modifier.isAbstract(modifiers)) {

                        classes.add(cls);
                    }
                } catch (ClassNotFoundException | Error x) {
                    System.err.println(
                            String.format("Cannot find test class %s from %s",
                                    className, jarTestFile.getName()));
                    x.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return classes;
    }

    private static String getClassName(String name) {
        // -6 because of .class
        return name.substring(0, name.length() - 6).replace('/', '.');
    }
}
