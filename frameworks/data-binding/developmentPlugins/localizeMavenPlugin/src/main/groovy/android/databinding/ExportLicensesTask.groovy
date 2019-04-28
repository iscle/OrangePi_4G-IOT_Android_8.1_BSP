/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.databinding

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.tasks.TaskAction;

class ExportLicensesTask extends DefaultTask {
    List<ResolvedArtifact> artifacts = new ArrayList();

    static def knownLicenses = [
            [
                    libraries: ["kotlin-stdlib", "kotlin-runtime", "kotlin-annotation-processing", "kotlin-gradle-plugin", "kotlin-gradle-plugin-api",
                    "kdoc", "kotlin-gradle-plugin-core", "kotlin-jdk-annotations", "kotlin-compiler", "kotlin-compiler-embeddable", "kotlin-android-extensions"],
                    licenses : ["https://raw.githubusercontent.com/JetBrains/kotlin/master/license/LICENSE.txt",
                                "http://www.apache.org/licenses/LICENSE-2.0.txt"],
                    notices  : ["https://raw.githubusercontent.com/JetBrains/kotlin/master/license/NOTICE.txt"]
            ],
            [
                    libraries: ["antlr4", "antlr4-runtime", "antlr-runtime", "antlr4-annotations"],
                    licenses : ["https://raw.githubusercontent.com/antlr/antlr4/master/LICENSE.txt"]
            ],
            [
                    libraries: ["antlr"],
                    licenses: ["http://www.antlr3.org/license.html", "http://www.antlr2.org/license.html"]
            ],
            [
                    libraries: ["java.g4"],
                    licenses : ["https://raw.githubusercontent.com/antlr/antlr4/master/LICENSE.txt"]
            ],
            [
                    libraries: ["ST4", "stringtemplate"],
                    licenses : ["https://raw.githubusercontent.com/antlr/stringtemplate4/master/LICENSE.txt"]
            ],
            [
                    libraries: ["org.abego.treelayout.core"],
                    licenses : ["http://treelayout.googlecode.com/files/LICENSE.TXT"]
            ],
            [
                    libraries: ["junit"],
                    licenses : ["https://raw.githubusercontent.com/junit-team/junit/master/LICENSE-junit.txt"],
                    notices  : ["https://raw.githubusercontent.com/junit-team/junit/master/NOTICE.txt"]
            ],
            [
                    libraries: ["commons-io"],
                    licenses : ["http://svn.apache.org/viewvc/commons/proper/io/trunk/LICENSE.txt?view=co"],
                    notices  : ["http://svn.apache.org/viewvc/commons/proper/io/trunk/NOTICE.txt?view=co"]
            ],
            [
                    libraries: ["commons-codec"],
                    licenses: ["http://svn.apache.org/viewvc/commons/proper/codec/trunk/LICENSE.txt?view=co"],
                    notices: ["http://svn.apache.org/viewvc/commons/proper/codec/trunk/NOTICE.txt?view=co"]
            ],
            [
                    libraries: ["commons-lang3"],
                    licenses : ["https://git-wip-us.apache.org/repos/asf?p=commons-lang.git;a=blob_plain;f=LICENSE.txt;hb=refs/heads/master"],
                    notices  : ["https://git-wip-us.apache.org/repos/asf?p=commons-lang.git;a=blob_plain;f=NOTICE.txt;hb=refs/heads/master"]
            ],
            [
                    libraries: ["guava"],
                    licenses : ["http://www.apache.org/licenses/LICENSE-2.0.txt"]
            ],
            [
                    libraries: ["hamcrest-core"],
                    licenses : ["https://raw.githubusercontent.com/hamcrest/JavaHamcrest/master/LICENSE.txt"]
            ],
            [
                    libraries: ["avalon-framework"],
                    licenses : ["http://archive.apache.org/dist/avalon/LICENSE.txt"]
            ],
            [
                    libraries: ["log4j"],
                    licenses: ["https://git-wip-us.apache.org/repos/asf?p=logging-log4j2.git;a=blob_plain;f=LICENSE.txt;hb=HEAD"],
                    notices: ["https://git-wip-us.apache.org/repos/asf?p=logging-log4j2.git;a=blob_plain;f=NOTICE.txt;hb=HEAD"]
            ],
            [
                    libraries: ["ant", "ant-launcher"],
                    licenses: ["http://www.apache.org/licenses/LICENSE-2.0.html"]
            ],
            [
                    libraries: ["xz"],
                    licenses: ["http://git.tukaani.org/?p=xz-java.git;a=blob_plain;f=COPYING;hb=HEAD"]
            ],
            [
                    libraries: ["logkit"],
                    licenseText: ["unknown. see: http://commons.apache.org/proper/commons-logging/dependencies.html"]
            ],
            [
                    libraries: ["juniversalchardet"],
                    licenses: ["https://mozorg.cdn.mozilla.net/media/MPL/1.1/index.0c5913925d40.txt"]
            ],
    ]

    Map<String, Object> usedLicenses = new HashMap<>();
    static Map<String, Object> licenseLookup = new HashMap<>();
    static {
        knownLicenses.each {license ->
            license.libraries.each {
                licenseLookup.put(it, license)
            }
        }
    }

    ExportLicensesTask() {
    }

    public void add(ResolvedArtifact artifact) {
        artifacts.add(artifact)
        println("adding artifact $artifact")
    }

    @TaskAction
    public void exportNotice() {
        project.configurations.compile.getResolvedConfiguration()
                .getFirstLevelModuleDependencies().each {
            if (!it.getModuleGroup().equals("com.android.tools.build")) {
                it.getAllModuleArtifacts().each { add(it) }
            }
        }
        resolveLicenses()
        def notice = buildNotice(usedLicenses)
        def noticeFile = new File("${project.projectDir}/src/main/resources",'NOTICE.txt')
        noticeFile.delete()
        println ("writing notice file to: ${noticeFile.getAbsolutePath()}")
        noticeFile << notice
    }

    public void resolveLicenses() {
        artifacts.each { artifact ->
            if (!shouldSkip(artifact)) {
                def license = licenseLookup.get(artifact.name)
                if (license  == null) {
                    throw new RuntimeException("Cannot find license for ${artifact.getModuleVersion().id} in ${artifact.getFile()}")
                }
                usedLicenses.put(artifact, license)
            }
        }
    }

    public static Object findLicenseFor(String artifactId) {
        return licenseLookup.get(artifactId)
    }

    public static String urlToText(String url) {
        return new URL(url).getText()
    }

    public boolean shouldSkip(ResolvedArtifact artifact) {
        return artifact.getModuleVersion().id.group.startsWith("com.android");
    }

    public static String buildNotice(Map<String, Object> licenses) {
        // now build the output
        StringBuilder notice = new StringBuilder();
        notice.append("List of 3rd party licenses:")
        licenses.each {
            notice.append("\n-----------------------------------------------------------------------------")
            notice.append("\n* ${it.key}")
            notice.append("\n")
            def license = it.value
            if (license.notices != null) {
                license.notices.each {
                    notice.append("\n ****** NOTICE:\n${urlToText(it)}")
                }
            }
            license.licenses.each {
                notice.append("\n ****** LICENSE:\n${urlToText(it)}")
            }
            license.licenseText.each {
                notice.append("\n ****** LICENSE:\n${it}")
            }
            notice.append("\n\n\n")
        }
        return notice.toString()
    }
}