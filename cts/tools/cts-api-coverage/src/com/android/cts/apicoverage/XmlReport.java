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

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class that outputs an XML report of the {@link ApiCoverage} collected. It can be viewed in
 * a browser when used with the api-coverage.css and api-coverage.xsl files.
 */
class XmlReport {

    public static void printXmlReport(List<File> testApks, ApiCoverage apiCoverage,
            CddCoverage cddCoverage, PackageFilter packageFilter, String reportTitle,
            OutputStream outputStream) {
        PrintStream out = new PrintStream(outputStream);
        out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        out.println("<?xml-stylesheet type=\"text/xsl\"  href=\"api-coverage.xsl\"?>");

        SimpleDateFormat format = new SimpleDateFormat("EEE, MMM d, yyyy h:mm a z");
        String date = format.format(new Date(System.currentTimeMillis()));
        out.println("<api-coverage generatedTime=\"" + date + "\" title=\"" + reportTitle +"\">");

        out.println("<debug>");
        out.println("<sources>");
        for (File testApk : testApks) {
            out.println("<apk path=\"" + testApk.getPath() + "\" />");
        }
        out.println("</sources>");
        out.println("</debug>");

        out.println("<api>");

        CoverageComparator comparator = new CoverageComparator();
        List<ApiPackage> packages = new ArrayList<ApiPackage>(apiCoverage.getPackages());
        Collections.sort(packages, comparator);
        int totalMethods = 0;
        int totalCoveredMethods = 0;
        for (ApiPackage pkg : packages) {
            if (packageFilter.accept(pkg.getName()) && pkg.getTotalMethods() > 0) {
                int pkgTotal = pkg.getTotalMethods();
                totalMethods += pkgTotal;
                int pkgTotalCovered = pkg.getNumCoveredMethods();
                totalCoveredMethods += pkgTotalCovered;
                out.println("<package name=\"" + pkg.getName()
                        + "\" numCovered=\"" + pkgTotalCovered
                        + "\" numTotal=\"" + pkgTotal
                        + "\" coveragePercentage=\""
                        + Math.round(pkg.getCoveragePercentage())
                        + "\">");

                List<ApiClass> classes = new ArrayList<ApiClass>(pkg.getClasses());
                Collections.sort(classes, comparator);

                for (ApiClass apiClass : classes) {
                    if (apiClass.getTotalMethods() > 0) {
                        out.println("<class name=\"" + apiClass.getName()
                                + "\" numCovered=\"" + apiClass.getNumCoveredMethods()
                                + "\" numTotal=\"" + apiClass.getTotalMethods()
                                + "\" deprecated=\"" + apiClass.isDeprecated()
                                + "\" coveragePercentage=\""
                                    + Math.round(apiClass.getCoveragePercentage())
                                + "\">");

                        for (ApiConstructor constructor : apiClass.getConstructors()) {
                            String coveredWithList =
                                    constructor.getCoveredWith().stream().collect(Collectors.joining(","));
                            out.println("<constructor name=\"" + constructor.getName()
                                    + "\" deprecated=\"" + constructor.isDeprecated()
                                    + "\" covered=\"" + constructor.isCovered()
                                    + "\" with=\"" + coveredWithList
                                    + "\">");
                            if (constructor.isDeprecated()) {
                                if (constructor.isCovered()) {
                                    totalCoveredMethods -= 1;
                                }
                                totalMethods -= 1;
                            }
                            for (String parameterType : constructor.getParameterTypes()) {
                                out.println("<parameter type=\"" + parameterType + "\" />");
                            }

                            out.println("</constructor>");
                        }

                        for (ApiMethod method : apiClass.getMethods()) {
                            String coveredWithList =
                                    method.getCoveredWith().stream().collect(Collectors.joining(","));
                            out.println("<method name=\"" + method.getName()
                                    + "\" returnType=\"" + method.getReturnType()
                                    + "\" deprecated=\"" + method.isDeprecated()
                                    + "\" static=\"" + method.isStaticMethod()
                                    + "\" final=\"" + method.isFinalMethod()
                                    + "\" visibility=\"" + method.getVisibility()
                                    + "\" abstract=\"" + method.isAbstractMethod()
                                    + "\" covered=\"" + method.isCovered()
                                    + "\" with=\"" + coveredWithList
                                    + "\">");
                            if (method.isDeprecated()) {
                                if (method.isCovered()) {
                                    totalCoveredMethods -= 1;
                                }
                                totalMethods -= 1;
                            }
                            for (String parameterType : method.getParameterTypes()) {
                                out.println("<parameter type=\"" + parameterType + "\" />");
                            }

                            out.println("</method>");
                        }
                        out.println("</class>");
                    }
                }
                out.println("</package>");
            }
        }

        out.println("</api>");
        out.println("<cdd>");
        for (CddCoverage.CddRequirement requirement : cddCoverage.getCddRequirements()) {
            out.println("<requirement id=\"" + requirement.getRequirementId() + "\">");
            for (CddCoverage.TestMethod method : requirement.getTestMethods()) {
                out.print("<test module=\"" + method.getTestModule()
                        + "\" class=\"" + method.getTestClass() + "\" ");
                if (method.getTestMethod() != null) {
                    out.print("method=\"" + method.getTestMethod() + "\"");
                }
                out.println("/>" );
            }
            out.println("</requirement>");
        }
        out.println("</cdd>");
        out.println("<total numCovered=\"" + totalCoveredMethods + "\" "
                + "numTotal=\"" + totalMethods + "\" "
                + "coveragePercentage=\""
                + Math.round((float)totalCoveredMethods / totalMethods * 100.0f) + "\" />");
        out.println("</api-coverage>");
    }
}
