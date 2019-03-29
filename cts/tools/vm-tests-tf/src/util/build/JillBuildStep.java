/*
 * Copyright (C) 2013 The Android Open Source Project
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

package util.build;

import java.io.File;
import java.io.IOException;

public class JillBuildStep extends BuildStep {

    JillBuildStep(BuildFile inputFile, BuildFile outputFile) {
        super(inputFile, outputFile);
    }

    @Override
    boolean build() {
        if (super.build()) {
            File tmpInputJar = new File(inputFile.fileName.getPath() + ".jar");
            try {

                File outDir = outputFile.fileName.getParentFile();
                if (!outDir.exists() && !outDir.mkdirs()) {
                    System.err.println("failed to create output dir: "
                            + outDir.getAbsolutePath());
                    return false;
                }

                // input file is a class file but jack supports only jar
                JarBuildStep jarStep = new JarBuildStep(
                    inputFile,
                    inputFile.fileName.getName(),
                    new BuildFile(tmpInputJar),
                    /* deleteInputFileAfterBuild = */ false);
                if (!jarStep.build()) {
                  throw new IOException("Failed to make jar: " + outputFile.getPath());
                }


                String[] commandLine = new String[] {
                    "--verbose",
                    "error",
                    "--import",
                    tmpInputJar.getAbsolutePath(),
                    "--output-jack",
                    outputFile.fileName.getAbsolutePath(),
                  };

                ExecuteFile exec = new ExecuteFile(JackBuildDalvikSuite.JACK, commandLine);
                exec.setErr(System.err);
                exec.setOut(System.out);
                if (!exec.run()) {
                    return false;
                }

                return true;
            } catch (Throwable ex) {
                System.err.println("exception while transforming jack file from jar "
                        + inputFile.fileName.getAbsolutePath() + " to "
                        + outputFile.fileName.getAbsolutePath());
                ex.printStackTrace();
            } finally {
                tmpInputJar.delete();
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            JillBuildStep other = (JillBuildStep) obj;

            return inputFile.equals(other.inputFile) && outputFile.equals(other.outputFile);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return inputFile.hashCode() ^ outputFile.hashCode();
    }
}
