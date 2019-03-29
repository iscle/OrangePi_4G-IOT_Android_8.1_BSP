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

package vogar;

import com.android.json.stream.JsonReader;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import vogar.commands.Command;
import vogar.util.Log;

/**
 * A database of expected outcomes. Entries in this database come in two forms.
 * <ul>
 *   <li>Outcome expectations name an outcome (or its prefix, such as
 *       "java.util"), its expected result, and an optional pattern to match
 *       the expected output.
 *   <li>Failure expectations include a pattern that may match the output of any
 *       outcome. These expectations are useful for hiding failures caused by
 *       cross-cutting features that aren't supported.
 * </ul>
 *
 * <p>If an outcome matches both an outcome expectation and a failure
 * expectation, the outcome expectation will be returned.
 */
public final class ExpectationStore {

    /** The pattern to use when no expected output is specified */
    private static final Pattern MATCH_ALL_PATTERN
            = Pattern.compile(".*", Pattern.MULTILINE | Pattern.DOTALL);

    /** The expectation of a general successful run. */
    private static final Expectation SUCCESS = new Expectation(Result.SUCCESS, MATCH_ALL_PATTERN,
            Collections.<String>emptySet(), "", -1);

    private static final int PATTERN_FLAGS = Pattern.MULTILINE | Pattern.DOTALL;

    private final Map<String, Expectation> outcomes = new LinkedHashMap<String, Expectation>();
    private final Map<String, Expectation> failures = new LinkedHashMap<String, Expectation>();

    private ExpectationStore() {}

    /**
     * Finds the expected result for the specified action or outcome name. This
     * returns a value for all names, even if no explicit expectation was set.
     */
    public Expectation get(String name) {
        Expectation byName = getByNameOrPackage(name);
        return byName != null ? byName : SUCCESS;
    }

    /**
     * Finds the expected result for the specified outcome after it has
     * completed. Unlike {@code get()}, this also takes into account the
     * outcome's output.
     *
     * <p>For outcomes that have both a name match and an output match,
     * exact name matches are preferred, then output matches, then inexact
     * name matches.
     */
    public Expectation get(Outcome outcome) {
        Expectation exactNameMatch = outcomes.get(outcome.getName());
        if (exactNameMatch != null) {
            return exactNameMatch;
        }

        for (Map.Entry<String, Expectation> entry : failures.entrySet()) {
            if (entry.getValue().matches(outcome)) {
                return entry.getValue();
            }
        }

        Expectation byName = getByNameOrPackage(outcome.getName());
        return byName != null ? byName : SUCCESS;
    }

    private Expectation getByNameOrPackage(String name) {
        while (true) {
            Expectation expectation = outcomes.get(name);
            if (expectation != null) {
                return expectation;
            }

            int dotOrHash = Math.max(name.lastIndexOf('.'), name.lastIndexOf('#'));
            if (dotOrHash == -1) {
                return null;
            }

            name = name.substring(0, dotOrHash);
        }
    }

    public static ExpectationStore parse(Set<File> expectationFiles, ModeId mode) throws IOException {
        ExpectationStore result = new ExpectationStore();
        for (File f : expectationFiles) {
            if (f.exists()) {
                result.parse(f, mode);
            }
        }
        return result;
    }

    /**
     * Create an {@link ExpectationStore} that is populated from expectation resources.
     * @param owningClass the class from which the resources are loaded.
     * @param expectationResources the set of paths to the expectation resources; the paths are
     * either relative to the owning class, or absolute (starting with a /).
     * @param mode the mode within which the tests are to be run.
     * @return the populated {@link ExpectationStore}.
     * @throws IOException if there was a problem loading
     */
    public static ExpectationStore parseResources(
            Class<?> owningClass, Set<String> expectationResources, ModeId mode)
            throws IOException {
        ExpectationStore result = new ExpectationStore();
        for (String expectationsPath : expectationResources) {
            URL url = owningClass.getResource(expectationsPath);
            if (url == null) {
                Log.warn("Could not find resource '" + expectationsPath
                        + "' relative to " + owningClass);
            } else {
                result.parse(url, mode);
            }
        }
        return result;
    }

    private void parse(URL url, ModeId mode) throws IOException {
        Log.verbose("loading expectations from " + url);

        try (InputStream is = url.openStream();
             Reader reader = new InputStreamReader(is)) {
            parse(reader, url.toString(), mode);
        }
    }

    public void parse(File expectationsFile, ModeId mode) throws IOException {
        Log.verbose("loading expectations file " + expectationsFile);

        try (Reader fileReader = new FileReader(expectationsFile)) {
            String source = expectationsFile.toString();
            parse(fileReader, source, mode);
        }
    }

    private void parse(Reader reader, String source, ModeId mode) throws IOException {
        int count = 0;
        try (JsonReader jsonReader = new JsonReader(reader)) {
            jsonReader.setLenient(true);
            jsonReader.beginArray();
            while (jsonReader.hasNext()) {
                readExpectation(jsonReader, mode);
                count++;
            }
            jsonReader.endArray();

            Log.verbose("loaded " + count + " expectations from " + source);
        }
    }

    private void readExpectation(JsonReader reader, ModeId mode) throws IOException {
        boolean isFailure = false;
        Result result = Result.EXEC_FAILED;
        Pattern pattern = MATCH_ALL_PATTERN;
        Set<String> names = new LinkedHashSet<String>();
        Set<String> tags = new LinkedHashSet<String>();
        Set<ModeId> modes = null;
        String description = "";
        long buganizerBug = -1;

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("result")) {
                result = Result.valueOf(reader.nextString());
            } else if (name.equals("name")) {
                names.add(reader.nextString());
            } else if (name.equals("names")) {
                readStrings(reader, names);
            } else if (name.equals("failure")) {
                // isFailure is somewhat arbitrarily keyed on the existence of a "failure"
                // element instead of looking at the "result" field. There are only about 5
                // expectations in our entire expectation store that have this tag.
                //
                // TODO: Get rid of it and the "failures" map and just use the outcomes
                // map for everything. Both uses seem useless.
                isFailure = true;
                names.add(reader.nextString());
            } else if (name.equals("pattern")) {
                pattern = Pattern.compile(reader.nextString(), PATTERN_FLAGS);
            } else if (name.equals("substring")) {
                pattern = Pattern.compile(".*" + Pattern.quote(reader.nextString()) + ".*", PATTERN_FLAGS);
            } else if (name.equals("tags")) {
                readStrings(reader, tags);
            } else if (name.equals("description")) {
                Iterable<String> split = Splitter.on("\n").omitEmptyStrings().trimResults().split(reader.nextString());
                description = Joiner.on("\n").join(split);
            } else if (name.equals("bug")) {
                buganizerBug = reader.nextLong();
            } else if (name.equals("modes")) {
                modes = readModes(reader);
            } else {
                Log.warn("Unhandled name in expectations file: " + name);
                reader.skipValue();
            }
        }
        reader.endObject();

        if (names.isEmpty()) {
            throw new IllegalArgumentException("Missing 'name' or 'failure' key in " + reader);
        }
        if (modes != null && !modes.contains(mode)) {
            return;
        }

        Expectation expectation = new Expectation(result, pattern, tags, description, buganizerBug);
        Map<String, Expectation> map = isFailure ? failures : outcomes;
        for (String name : names) {
            if (map.put(name, expectation) != null) {
                throw new IllegalArgumentException("Duplicate expectations for " + name);
            }
        }
    }

    private void readStrings(JsonReader reader, Set<String> output) throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            output.add(reader.nextString());
        }
        reader.endArray();
    }

    private Set<ModeId> readModes(JsonReader reader) throws IOException {
        Set<ModeId> result = new LinkedHashSet<ModeId>();
        reader.beginArray();
        while (reader.hasNext()) {
            result.add(ModeId.valueOf(reader.nextString().toUpperCase()));
        }
        reader.endArray();
        return result;
    }

    /**
     * Sets the bugIsOpen status on all expectations by querying an external bug
     * tracker.
     */
    public void loadBugStatuses(String openBugsCommand) {
        Iterable<Expectation> allExpectations = Iterables.concat(outcomes.values(), failures.values());

        // figure out what bug IDs we're interested in
        Set<String> bugs = new LinkedHashSet<String>();
        for (Expectation expectation : allExpectations) {
            if (expectation.getBug() != -1) {
                bugs.add(Long.toString(expectation.getBug()));
            }
        }
        if (bugs.isEmpty()) {
            return;
        }

        // query the external app for open bugs
        List<String> openBugs = new Command.Builder()
                .args(openBugsCommand)
                .args(bugs)
                .execute();
        Set<Long> openBugsSet = new LinkedHashSet<Long>();
        for (String bug : openBugs) {
            openBugsSet.add(Long.parseLong(bug));
        }

        Log.verbose("tracking " + openBugsSet.size() + " open bugs: " + openBugs);

        // update our expectations with that set
        for (Expectation expectation : allExpectations) {
            if (openBugsSet.contains(expectation.getBug())) {
                expectation.setBugIsOpen(true);
            }
        }
    }

    public Map<String, Expectation> getAllOutComes() {
        return outcomes;
    }

    public Map<String, Expectation> getAllFailures() {
        return failures;
    }
}
