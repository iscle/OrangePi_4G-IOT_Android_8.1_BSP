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

package util.build;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * JarBuildStep takes a single input file and embeds it into a (new) jar file as a single entry.
 */
public class JarBuildStep extends BuildStep {

    String outputJarEntryName;
    private final boolean deleteInputFileAfterBuild;

    public JarBuildStep(BuildFile inputFile, String outputJarEntryName,
            BuildFile outputJarFile, boolean deleteInputFileAfterBuild) {
        super(inputFile, outputJarFile);
        this.outputJarEntryName = outputJarEntryName;
        this.deleteInputFileAfterBuild = deleteInputFileAfterBuild;
    }

    @Override
    boolean build() {
        if (super.build()) {
            File tempFile = new File(inputFile.folder, outputJarEntryName);
            try {
                if (!inputFile.fileName.equals(tempFile)) {
                    copyFile(inputFile.fileName, tempFile);
                } else {
                    tempFile = null;
                }
            } catch (IOException e) {
                System.err.println("io exception:"+e.getMessage());
                e.printStackTrace();
                return false;
            }

            File outDir = outputFile.fileName.getParentFile();
            if (!outDir.exists() && !outDir.mkdirs()) {
                System.err.println("failed to create output dir: "
                        + outDir.getAbsolutePath());
                return false;
            }

            // Find the input. We'll need to look into the input folder, but check with the
            // (relative) destination filename (this is effectively removing the inputFile folder
            // from the entry path in the jar file).
            Path absoluteInputPath = Paths.get(inputFile.folder.getAbsolutePath())
                    .resolve(outputJarEntryName);
            File absoluteInputFile = absoluteInputPath.toFile();
            if (!absoluteInputFile.exists()) {
                // Something went wrong.
                throw new IllegalArgumentException(absoluteInputFile.getAbsolutePath());
            }

            // Use a JarOutputStream to create the output jar file.
            File jarOutFile = outputFile.fileName;
            try (JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(jarOutFile))) {
                // Create the JAR entry for the file. Use destFileName, and copy the timestamp
                // from the input.
                JarEntry entry = new JarEntry(outputJarEntryName);
                entry.setTime(absoluteInputFile.lastModified());

                // Push the entry. The stream will then be ready to accept content.
                jarOut.putNextEntry(entry);

                // Copy absoluteInputFile into the jar file.
                Files.copy(absoluteInputPath, jarOut);

                // Finish the entry.
                jarOut.closeEntry();

                // (Implicitly close the stream, finishing the jar file.)
            } catch (Exception e) {
                System.err.println("exception in JarBuildStep for " +
                        outputFile.fileName.getAbsolutePath() + ", " + outputJarEntryName);
                e.printStackTrace(System.err);
                jarOutFile.delete();
                return false;
            }

            // Clean up.
            if (tempFile != null) {
                tempFile.delete();
            }
            if (deleteInputFileAfterBuild) {
                inputFile.fileName.delete();
            }

            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return inputFile.hashCode() ^ outputFile.hashCode()
                ^ outputJarEntryName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            JarBuildStep other = (JarBuildStep) obj;
            return inputFile.equals(other.inputFile)
                    && outputFile.equals(other.outputFile)
                    && outputJarEntryName.equals(other.outputJarEntryName);

        }
        return false;
    }

}
