/*
 * Copyright (C) 2017 The Android Open Source Project
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

package libcore.util;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.icu.util.TimeZone;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A structure that can find matching time zones.
 */
public class TimeZoneFinder {

    private static final String TZLOOKUP_FILE_NAME = "tzlookup.xml";
    private static final String TIMEZONES_ELEMENT = "timezones";
    private static final String COUNTRY_ZONES_ELEMENT = "countryzones";
    private static final String COUNTRY_ELEMENT = "country";
    private static final String COUNTRY_CODE_ATTRIBUTE = "code";
    private static final String ID_ELEMENT = "id";

    private static TimeZoneFinder instance;

    private final ReaderSupplier xmlSource;

    // Cached fields for the last country looked up.
    private String lastCountryIso;
    private List<TimeZone> lastCountryTimeZones;

    private TimeZoneFinder(ReaderSupplier xmlSource) {
        this.xmlSource = xmlSource;
    }

    /**
     * Obtains an instance for use when resolving time zones. This method handles using the correct
     * file when there are several to choose from. This method never returns {@code null}. No
     * in-depth validation is performed on the file content, see {@link #validate()}.
     */
    public static TimeZoneFinder getInstance() {
        synchronized(TimeZoneFinder.class) {
            if (instance == null) {
                String[] tzLookupFilePaths =
                        TimeZoneDataFiles.getTimeZoneFilePaths(TZLOOKUP_FILE_NAME);
                instance = createInstanceWithFallback(tzLookupFilePaths[0], tzLookupFilePaths[1]);
            }
        }
        return instance;
    }

    // VisibleForTesting
    public static TimeZoneFinder createInstanceWithFallback(String... tzLookupFilePaths) {
        IOException lastException = null;
        for (String tzLookupFilePath : tzLookupFilePaths) {
            try {
                // We assume that any file in /data was validated before install, and the system
                // file was validated before the device shipped. Therefore, we do not pay the
                // validation cost here.
                return createInstance(tzLookupFilePath);
            } catch (IOException e) {
                // There's expected to be two files, and it's normal for the first file not to
                // exist so we don't log, but keep the lastException so we can log it if there
                // are no valid files available.
                if (lastException != null) {
                    e.addSuppressed(lastException);
                }
                lastException = e;
            }
        }

        System.logE("No valid file found in set: " + Arrays.toString(tzLookupFilePaths)
                + " Printing exceptions and falling back to empty map.", lastException);
        return createInstanceForTests("<timezones><countryzones /></timezones>");
    }

    /**
     * Obtains an instance using a specific data file, throwing an IOException if the file does not
     * exist or is not readable. This method never returns {@code null}. No in-depth validation is
     * performed on the file content, see {@link #validate()}.
     */
    public static TimeZoneFinder createInstance(String path) throws IOException {
        ReaderSupplier xmlSupplier = ReaderSupplier.forFile(path, StandardCharsets.UTF_8);
        return new TimeZoneFinder(xmlSupplier);
    }

    /** Used to create an instance using an in-memory XML String instead of a file. */
    // VisibleForTesting
    public static TimeZoneFinder createInstanceForTests(String xml) {
        return new TimeZoneFinder(ReaderSupplier.forString(xml));
    }

    /**
     * Parses the data file, throws an exception if it is invalid or cannot be read.
     */
    public void validate() throws IOException {
        try {
            processXml(new CountryZonesValidator());
        } catch (XmlPullParserException e) {
            throw new IOException("Parsing error", e);
        }
    }

    /**
     * Returns a frozen ICU time zone that has / would have had the specified offset and DST value
     * at the specified moment in the specified country.
     *
     * <p>In order to be considered a configured zone must match the supplied offset information.
     *
     * <p>Matches are considered in a well-defined order. If multiple zones match and one of them
     * also matches the (optional) bias parameter then the bias time zone will be returned.
     * Otherwise the first match found is returned.
     */
    public TimeZone lookupTimeZoneByCountryAndOffset(
            String countryIso, int offsetSeconds, boolean isDst, long whenMillis, TimeZone bias) {

        List<TimeZone> candidates = lookupTimeZonesByCountry(countryIso);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        TimeZone firstMatch = null;
        for (int i = 0; i < candidates.size(); i++) {
            TimeZone match = candidates.get(i);
            if (!offsetMatchesAtTime(match, offsetSeconds, isDst, whenMillis)) {
                continue;
            }

            if (firstMatch == null) {
                if (bias == null) {
                    // No bias, so we can stop at the first match.
                    return match;
                }
                // We have to carry on checking in case the bias matches. We want to return the
                // first if it doesn't, though.
                firstMatch = match;
            }

            // Check if match is also the bias. There must be a bias otherwise we'd have terminated
            // already.
            if (match.getID().equals(bias.getID())) {
                return match;
            }
        }
        // Return firstMatch, which can be null if there was no match.
        return firstMatch;
    }

    /**
     * Returns {@code true} if the specified offset, DST state and time would be valid in the
     * timeZone.
     */
    private static boolean offsetMatchesAtTime(TimeZone timeZone, int offsetMillis, boolean isDst,
            long whenMillis) {
        int[] offsets = new int[2];
        timeZone.getOffset(whenMillis, false /* local */, offsets);

        // offsets[1] == 0 when the zone is not in DST.
        boolean zoneIsDst = offsets[1] != 0;
        if (isDst != zoneIsDst) {
            return false;
        }
        return offsetMillis == (offsets[0] + offsets[1]);
    }

    /**
     * Returns an immutable list of frozen ICU time zones known to be used in the specified country.
     * If the country code is not recognized or there is an error during lookup this can return
     * null. The TimeZones returned will never contain {@link TimeZone#UNKNOWN_ZONE}. This method
     * can return an empty list in a case when the underlying configuration references only unknown
     * zone IDs.
     */
    public List<TimeZone> lookupTimeZonesByCountry(String countryIso) {
        synchronized(this) {
            if (countryIso.equals(lastCountryIso)) {
                return lastCountryTimeZones;
            }
        }

        CountryZonesExtractor extractor = new CountryZonesExtractor(countryIso);
        List<TimeZone> countryTimeZones = null;
        try {
            processXml(extractor);
            countryTimeZones = extractor.getMatchedZones();
        } catch (IOException e) {
            System.logW("Error reading country zones ", e);

            // Clear the cached code so we will try again next time.
            countryIso = null;
        } catch (XmlPullParserException e) {
            System.logW("Error reading country zones ", e);
            // We want to cache the null. This won't get better over time.
        }

        synchronized(this) {
            lastCountryIso = countryIso;
            lastCountryTimeZones = countryTimeZones;
        }
        return countryTimeZones;
    }

    /**
     * Processes the XML, applying the {@link CountryZonesProcessor} to the &lt;countryzones&gt;
     * element. Processing can terminate early if the
     * {@link CountryZonesProcessor#process(String, List, String)} returns
     * {@link CountryZonesProcessor#HALT} or it throws an exception.
     */
    private void processXml(CountryZonesProcessor processor)
            throws XmlPullParserException, IOException {
        try (Reader reader = xmlSource.get()) {
            XmlPullParserFactory xmlPullParserFactory = XmlPullParserFactory.newInstance();
            xmlPullParserFactory.setNamespaceAware(false);

            XmlPullParser parser = xmlPullParserFactory.newPullParser();
            parser.setInput(reader);

            /*
             * The expected XML structure is:
             * <timezones>
             *   <countryzones>
             *     <country code="us">
             *       <id>America/New_York"</id>
             *       ...
             *       <id>America/Los_Angeles</id>
             *     </country>
             *     <country code="gb">
             *       <id>Europe/London</id>
             *     </country>
             *   </countryzones>
             * </timezones>
             */

            findRequiredStartTag(parser, TIMEZONES_ELEMENT);

            // There is only one expected sub-element <countryzones> in the format currently, skip
            // over anything before it.
            findRequiredStartTag(parser, COUNTRY_ZONES_ELEMENT);

            if (processCountryZones(parser, processor) == CountryZonesProcessor.HALT) {
                return;
            }

            // Make sure we are on the </countryzones> tag.
            checkOnEndTag(parser, COUNTRY_ZONES_ELEMENT);

            // Advance to the next tag.
            parser.next();

            // Skip anything until </timezones>, and make sure the file is not truncated and we can
            // find the end.
            consumeUntilEndTag(parser, TIMEZONES_ELEMENT);

            // Make sure we are on the </timezones> tag.
            checkOnEndTag(parser, TIMEZONES_ELEMENT);
        }
    }

    private static boolean processCountryZones(XmlPullParser parser,
            CountryZonesProcessor processor) throws IOException, XmlPullParserException {

        // Skip over any unexpected elements and process <country> elements.
        while (findOptionalStartTag(parser, COUNTRY_ELEMENT)) {
            if (processor == null) {
                consumeUntilEndTag(parser, COUNTRY_ELEMENT);
            } else {
                String code = parser.getAttributeValue(
                        null /* namespace */, COUNTRY_CODE_ATTRIBUTE);
                if (code == null || code.isEmpty()) {
                    throw new XmlPullParserException(
                            "Unable to find country code: " + parser.getPositionDescription());
                }

                String debugInfo = parser.getPositionDescription();
                List<String> timeZoneIds = parseZoneIds(parser);
                if (processor.process(code, timeZoneIds, debugInfo)
                        == CountryZonesProcessor.HALT) {
                    return CountryZonesProcessor.HALT;
                }
            }

            // Make sure we are on the </country> element.
            checkOnEndTag(parser, COUNTRY_ELEMENT);
        }

        return CountryZonesExtractor.CONTINUE;
    }

    private static List<String> parseZoneIds(XmlPullParser parser)
            throws IOException, XmlPullParserException {
        List<String> timeZones = new ArrayList<>();

        // Skip over any unexpected elements and process <id> elements.
        while (findOptionalStartTag(parser, ID_ELEMENT)) {
            String zoneIdString = consumeText(parser);

            // Make sure we are on the </id> element.
            checkOnEndTag(parser, ID_ELEMENT);

            // Process the zone ID.
            timeZones.add(zoneIdString);
        }

        // The list is made unmodifiable to avoid callers changing it.
        return Collections.unmodifiableList(timeZones);
    }

    private static void findRequiredStartTag(XmlPullParser parser, String elementName)
            throws IOException, XmlPullParserException {
        findStartTag(parser, elementName, true /* elementRequired */);
    }

    /** Called when on a START_TAG. When returning false, it leaves the parser on the END_TAG. */
    private static boolean findOptionalStartTag(XmlPullParser parser, String elementName)
            throws IOException, XmlPullParserException {
        return findStartTag(parser, elementName, false /* elementRequired */);
    }

    /**
     * Find a START_TAG with the specified name without decreasing the depth, or increasing the
     * depth by more than one. More deeply nested elements and text are skipped, even START_TAGs
     * with matching names. Returns when the START_TAG is found or the next (non-nested) END_TAG is
     * encountered. The return can take the form of an exception or a false if the START_TAG is not
     * found. True is returned when it is.
     */
    private static boolean findStartTag(
            XmlPullParser parser, String elementName, boolean elementRequired)
            throws IOException, XmlPullParserException {

        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
            switch (type) {
                case XmlPullParser.START_TAG:
                    String currentElementName = parser.getName();
                    if (elementName.equals(currentElementName)) {
                        return true;
                    }

                    // It was not the START_TAG we were looking for. Consume until the end.
                    parser.next();
                    consumeUntilEndTag(parser, currentElementName);
                    break;
                case XmlPullParser.END_TAG:
                    if (elementRequired) {
                        throw new XmlPullParserException(
                                "No child element found with name " + elementName);
                    }
                    return false;
                default:
                    // Ignore.
                    break;
            }
        }
        throw new XmlPullParserException("Unexpected end of document while looking for "
                + elementName);
    }

    /**
     * Consume the remaining contents of an element and move to the END_TAG. Used when processing
     * within an element can stop. The parser must be pointing at either the END_TAG we are looking
     * for, a TEXT, or a START_TAG nested within the element to be consumed.
     */
    private static void consumeUntilEndTag(XmlPullParser parser, String elementName)
            throws IOException, XmlPullParserException {

        if (parser.getEventType() == XmlPullParser.END_TAG
                && elementName.equals(parser.getName())) {
            // Early return - we are already there.
            return;
        }

        // Keep track of the required depth in case there are nested elements to be consumed.
        // Both the name and the depth must match our expectation to complete.

        int requiredDepth = parser.getDepth();
        // A TEXT tag would be at the same depth as the END_TAG we are looking for.
        if (parser.getEventType() == XmlPullParser.START_TAG) {
            // A START_TAG would have incremented the depth, so we're looking for an END_TAG one
            // higher than the current tag.
            requiredDepth--;
        }

        while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
            int type = parser.next();

            int currentDepth = parser.getDepth();
            if (currentDepth < requiredDepth) {
                throw new XmlPullParserException(
                        "Unexpected depth while looking for end tag: "
                                + parser.getPositionDescription());
            } else if (currentDepth == requiredDepth) {
                if (type == XmlPullParser.END_TAG) {
                    if (elementName.equals(parser.getName())) {
                        return;
                    }
                    throw new XmlPullParserException(
                            "Unexpected eng tag: " + parser.getPositionDescription());
                }
            }
            // Everything else is either a type we are not interested in or is too deep and so is
            // ignored.
        }
        throw new XmlPullParserException("Unexpected end of document");
    }

    /**
     * Reads the text inside the current element. Should be called when the parser is currently
     * on the START_TAG before the TEXT. The parser will be positioned on the END_TAG after this
     * call when it completes successfully.
     */
    private static String consumeText(XmlPullParser parser)
            throws IOException, XmlPullParserException {

        int type = parser.next();
        String text;
        if (type == XmlPullParser.TEXT) {
            text = parser.getText();
        } else {
            throw new XmlPullParserException("Text not found. Found type=" + type
                    + " at " + parser.getPositionDescription());
        }

        type = parser.next();
        if (type != XmlPullParser.END_TAG) {
            throw new XmlPullParserException(
                    "Unexpected nested tag or end of document when expecting text: type=" + type
                            + " at " + parser.getPositionDescription());
        }
        return text;
    }

    private static void checkOnEndTag(XmlPullParser parser, String elementName)
            throws XmlPullParserException {
        if (!(parser.getEventType() == XmlPullParser.END_TAG
                && parser.getName().equals(elementName))) {
            throw new XmlPullParserException(
                    "Unexpected tag encountered: " + parser.getPositionDescription());
        }
    }

    /**
     * Processes &lt;countryzones&gt; data.
     */
    private interface CountryZonesProcessor {

        boolean CONTINUE = true;
        boolean HALT = false;

        /**
         * Returns {@code #CONTINUE} if processing of the XML should continue, {@code HALT} if it
         * should stop (but without considering this an error). Problems with parser are reported as
         * an exception.
         */
        boolean process(String countryCode, List<String> timeZoneIds, String debugInfo)
                throws XmlPullParserException;
    }

    /**
     * Validates &lt;countryzones&gt; elements. To be valid the country ISO code must be unique
     * and it must not be empty.
     */
    private static class CountryZonesValidator implements CountryZonesProcessor {

        private final Set<String> knownCountryCodes = new HashSet<>();

        @Override
        public boolean process(String countryCode, List<String> timeZoneIds, String debugInfo)
                throws XmlPullParserException {
            if (knownCountryCodes.contains(countryCode)) {
                throw new XmlPullParserException("Second entry for country code: " + countryCode
                        + " at " + debugInfo);
            }
            if (timeZoneIds.isEmpty()) {
                throw new XmlPullParserException("No time zone IDs for country code: " + countryCode
                        + " at " + debugInfo);
            }

            // We don't validate the zone IDs - they may be new and we can't easily check them
            // against other timezone data that may be associated with this file.

            knownCountryCodes.add(countryCode);

            return CONTINUE;
        }
    }

    /**
     * Extracts the zones associated with a country code, halting when the country code is matched
     * and making them available via {@link #getMatchedZones()}.
     */
    private static class CountryZonesExtractor implements CountryZonesProcessor {

        private final String countryCodeToMatch;
        private List<TimeZone> matchedZones;

        private CountryZonesExtractor(String countryCodeToMatch) {
            this.countryCodeToMatch = countryCodeToMatch;
        }

        @Override
        public boolean process(String countryCode, List<String> timeZoneIds, String debugInfo) {
            if (!countryCodeToMatch.equals(countryCode)) {
                return CONTINUE;
            }

            List<TimeZone> timeZones = new ArrayList<>();
            for (String zoneIdString : timeZoneIds) {
                TimeZone tz = TimeZone.getTimeZone(zoneIdString);
                if (tz.getID().equals(TimeZone.UNKNOWN_ZONE_ID)) {
                    System.logW("Skipping invalid zone: " + zoneIdString + " at " + debugInfo);
                } else {
                    // The zone is frozen to prevent mutation by callers.
                    timeZones.add(tz.freeze());
                }
            }
            matchedZones = Collections.unmodifiableList(timeZones);
            return HALT;
        }

        /**
         * Returns the matched zones, or {@code null} if there were no matches. Unknown zone IDs are
         * ignored so the list can be empty if there were no zones or the zone IDs were not
         * recognized.
         */
        List<TimeZone> getMatchedZones() {
            return matchedZones;
        }
    }

    /**
     * A source of Readers that can be used repeatedly.
     */
    private interface ReaderSupplier {
        /** Returns a Reader. Throws an IOException if the Reader cannot be created. */
        Reader get() throws IOException;

        static ReaderSupplier forFile(String fileName, Charset charSet) throws IOException {
            Path file = Paths.get(fileName);
            if (!Files.exists(file)) {
                throw new FileNotFoundException(fileName + " does not exist");
            }
            if (!Files.isRegularFile(file) && Files.isReadable(file)) {
                throw new IOException(fileName + " must be a regular readable file.");
            }
            return () -> Files.newBufferedReader(file, charSet);
        }

        static ReaderSupplier forString(String xml) {
            return () -> new StringReader(xml);
        }
    }
}
