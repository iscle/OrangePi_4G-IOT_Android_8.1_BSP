/*
 * Copyright (C) 2014 The Android Open Source Project
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

import java.io.IOException;

public class JackBuildDalvikSuite {

    public static String JACK;

    public static void main(String[] args) throws IOException {

        String[] remainingArgs;
        if (args.length > 0) {
            JACK = args[0];
            remainingArgs = new String[args.length - 1];
            System.arraycopy(args, 1, remainingArgs, 0, remainingArgs.length);
        } else {
            remainingArgs = args;
        }

        if (!BuildDalvikSuite.parseArgs(remainingArgs)) {
            printUsage();
            System.exit(-1);
        }

        long start = System.currentTimeMillis();
        BuildDalvikSuite cat = new BuildDalvikSuite(true);
        cat.compose();
        long end = System.currentTimeMillis();

        System.out.println("elapsed seconds: " + (end - start) / 1000);
    }


    private static void printUsage() {
        System.out.println("usage: java-src-folder output-folder classpath " +
                           "generated-main-files compiled_output generated-main-files " +
                           "[restrict-to-opcode]");
    }
}
