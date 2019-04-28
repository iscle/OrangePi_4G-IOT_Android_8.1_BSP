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

import org.gradle.api.Plugin
import org.gradle.api.Project

public class MavenDependencyCollectorPlugin implements Plugin<Project> {
    private static final String DEFAULT_TASK_NAME = "localizeDependencies"
    static final String EXTENSION_NAME = "localizeMaven"
    @Override
    void apply(Project project) {
        Project parent = project.getRootProject();
        def localizeDependenciesTask = parent.tasks.findByName(DEFAULT_TASK_NAME)
        if (localizeDependenciesTask == null) {
            localizeDependenciesTask = parent.tasks.
                    create(DEFAULT_TASK_NAME, LocalizeDependenciesTask)
            parent.extensions.create(EXTENSION_NAME, LocalizePluginExtension)
        }

        project.allprojects {
            afterEvaluate { p ->
                if (!p.name.equals("dataBinding")) {
                    project.tasks.create("collectDependenciesOf${it.getName().capitalize()}", MavenDependencyCollectorTask, {
                        it.localizeTask = localizeDependenciesTask
                        localizeDependenciesTask.dependsOn it
                    })
                }
            }
        }
        project.tasks.create("buildLicenseNotice", ExportLicensesTask) {

        }
    }
}