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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import android.icu.util.TimeZone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class TimeZoneFinderTest {

    private static final int HOUR_MILLIS = 60 * 60 * 1000;

    // Zones used in the tests. NEW_YORK_TZ and LONDON_TZ chosen because they never overlap but both
    // have DST.
    private static final TimeZone NEW_YORK_TZ = TimeZone.getTimeZone("America/New_York");
    private static final TimeZone LONDON_TZ = TimeZone.getTimeZone("Europe/London");
    // A zone that matches LONDON_TZ for WHEN_NO_DST. It does not have DST so differs for WHEN_DST.
    private static final TimeZone REYKJAVIK_TZ = TimeZone.getTimeZone("Atlantic/Reykjavik");
    // Another zone that matches LONDON_TZ for WHEN_NO_DST. It does not have DST so differs for
    // WHEN_DST.
    private static final TimeZone UTC_TZ = TimeZone.getTimeZone("Etc/UTC");

    // 22nd July 2017, 13:14:15 UTC (DST time in all the timezones used in these tests that observe
    // DST).
    private static final long WHEN_DST = 1500729255000L;
    // 22nd January 2018, 13:14:15 UTC (non-DST time in all timezones used in these tests).
    private static final long WHEN_NO_DST = 1516626855000L;

    private static final int LONDON_DST_OFFSET_MILLIS = HOUR_MILLIS;
    private static final int LONDON_NO_DST_OFFSET_MILLIS = 0;

    private static final int NEW_YORK_DST_OFFSET_MILLIS = -4 * HOUR_MILLIS;
    private static final int NEW_YORK_NO_DST_OFFSET_MILLIS = -5 * HOUR_MILLIS;

    private Path testDir;

    @Before
    public void setUp() throws Exception {
        testDir = Files.createTempDirectory("TimeZoneFinderTest");
    }

    @After
    public void tearDown() throws Exception {
        // Delete the testDir and all contents.
        Files.walkFileTree(testDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Test
    public void createInstanceWithFallback() throws Exception {
        String validXml1 = "<timezones>\n"
                + "  <countryzones>\n"
                + "    <country code=\"gb\">\n"
                + "      <id>Europe/London</id>\n"
                + "    </country>\n"
                + "  </countryzones>\n"
                + "</timezones>\n";
        String validXml2 = "<timezones>\n"
                + "  <countryzones>\n"
                + "    <country code=\"gb\">\n"
                + "      <id>Europe/Paris</id>\n"
                + "    </country>\n"
                + "  </countryzones>\n"
                + "</timezones>\n";

        String invalidXml = "<foo></foo>\n";
        checkValidateThrowsParserException(invalidXml);

        String validFile1 = createFile(validXml1);
        String validFile2 = createFile(validXml2);
        String invalidFile = createFile(invalidXml);
        String missingFile = createMissingFile();

        TimeZoneFinder file1ThenFile2 =
                TimeZoneFinder.createInstanceWithFallback(validFile1, validFile2);
        assertZonesEqual(zones("Europe/London"), file1ThenFile2.lookupTimeZonesByCountry("gb"));

        TimeZoneFinder missingFileThenFile1 =
                TimeZoneFinder.createInstanceWithFallback(missingFile, validFile1);
        assertZonesEqual(zones("Europe/London"),
                missingFileThenFile1.lookupTimeZonesByCountry("gb"));

        TimeZoneFinder file2ThenFile1 =
                TimeZoneFinder.createInstanceWithFallback(validFile2, validFile1);
        assertZonesEqual(zones("Europe/Paris"), file2ThenFile1.lookupTimeZonesByCountry("gb"));

        // We assume the file has been validated so an invalid file is not checked ahead of time.
        // We will find out when we look something up.
        TimeZoneFinder invalidThenValid =
                TimeZoneFinder.createInstanceWithFallback(invalidFile, validFile1);
        assertNull(invalidThenValid.lookupTimeZonesByCountry("gb"));

        // This is not a normal case: It would imply a define shipped without a file in /system!
        TimeZoneFinder missingFiles =
                TimeZoneFinder.createInstanceWithFallback(missingFile, missingFile);
        assertNull(missingFiles.lookupTimeZonesByCountry("gb"));
    }

    @Test
    public void xmlParsing_emptyFile() throws Exception {
        checkValidateThrowsParserException("");
    }

    @Test
    public void xmlParsing_unexpectedRootElement() throws Exception {
        checkValidateThrowsParserException("<foo></foo>\n");
    }

    @Test
    public void xmlParsing_missingCountryZones() throws Exception {
        checkValidateThrowsParserException("<timezones></timezones>\n");
    }

    @Test
    public void xmlParsing_noCountriesOk() throws Exception {
        validate("<timezones>\n"
                + "  <countryzones>\n"
                + "  </countryzones>\n"
                + "</timezones>\n");
    }

    @Test
    public void xmlParsing_unexpectedComments() throws Exception {
        TimeZoneFinder finder = validate("<timezones>\n"
                + "  <countryzones>\n"
                + "    <country code=\"gb\">\n"
                + "      <!-- This is a comment -->"
                + "      <id>Europe/London</id>\n"
                + "    </country>\n"
                + "  </countryzones>\n"
                + "</timezones>\n");
        assertZonesEqual(zones("Europe/London"), finder.lookupTimeZonesByCountry("gb"));

        // This is a crazy comment, but also helps prove that TEXT nodes are coalesced by the
        // parser.
        finder = validate("<timezones>\n"
                + "  <countryzones>\n"
                + "    <country code=\"gb\">\n"
                + "      <id>Europe/<!-- Don't freak out! -->London</id>\n"
                + "    </country>\n"
                + "  </countryzones>\n"
                + "</timezones>\n");
        assertZonesEqual(zones("Europe/London"), finder.lookupTimeZonesByCountry("gb"));
    }

    @Test
    public void xmlParsing_unexpectedElementsIgnored() throws Exception {
        String unexpectedElement = "<unexpected-element>\n<a /></unexpected-element>\n";
        TimeZoneFinder finder = validate("<timezones>\n"
                + "  " + unexpectedElement
                + "  <countryzones>\n"
                + "    <country code=\"gb\">\n"
                + "      <id>Europe/London</id>\n"
                + "    </country>\n"
                + "  </countryzones>\n"
                + "</timezones>\n");
        assertZonesEqual(zones("Europe/London"), finder.lookupTimeZonesByCountry("gb"));

        finder = validate("<timezones>\n"
                + "  <countryzones>\n"
                + "    " + unexpectedElement
                + "    <country code=\"gb\">\n"
                + "      <id>Europe/London</id>\n"
                + "    </country>\n"
                + "  </countryzones>\n"
                + "</timezones>\n");
        assertZonesEqual(zones("Europe/London"), finder.lookupTimeZonesByCountry("gb"));

        finder = validate("<timezones>\n"
                + "  <countryzones>\n"
                + "    <country code=\"gb\">\n"
                + "      " + unexpectedElement
                + "      <id>Europe/London</id>\n"
                + "    </country>\n"
                + "  </countryzones>\n"
                + "</timezones>\n");
        assertZonesEqual(zones("Europe/London"), finder.lookupTimeZonesByCountry("gb"));

        finder = validate("<timezones>\n"
                + "  <countryzones>\n"
                + "    <country code=\"gb\">\n"
                + "      <id>Europe/London</id>\n"
                + "      " + unexpectedElement
                + "      <id>Europe/Paris</id>\n"
                + "    </country>\n"
                + "  </countryzones>\n"
                + "</timezones>\n");
        assertZonesEqual(zones("Europe/London", "Europe/Paris"),
                finder.lookupTimeZonesByCountry("gb"));

        finder = validate("<timezones>\n"
                + "  <countryzones>\n"
                + "    <country code=\"gb\">\n"
                + "      <id>Europe/London</id>\n"
                + "    </country>\n"
                + "    " + unexpectedElement
                + "  </countryzones>\n"
                + "</timezones>\n");
        assertZonesEqual(zones("Europe/London"), finder.lookupTimeZonesByCountry("gb"));

        // This test is important because it ensures we can extend the format in future with
        // more information.
        finder = validate("<timezones>\n"
                + "  <countryzones>\n"
                + "    <country code=\"gb\">\n"
                + "      <id>Europe/London</id>\n"
                + "    </country>\n"
                + "  </countryzones>\n"
                + "  " + unexpectedElement
                + "</timezones>\n");
        assertZonesEqual(zones("Europe/London"), finder.lookupTimeZonesByCountry("gb"));
    }

    @Test
    public void xmlParsing_unexpectedTextIgnored() throws Exception {
        String unexpectedText = "unexpected-text";
        TimeZoneFinder finder = validate("<timezones>\n"
                + "  " + unexpectedText
                + "  <countryzones>\n"
                + "    <country code=\"gb\">\n"
                + "      <id>Europe/London</id>\n"
                + "    </country>\n"
                + "  </countryzones>\n"
                + "</timezones>\n");
        assertZonesEqual(zones("Europe/London"), finder.lookupTimeZonesByCountry("gb"));

        finder = validate("<timezones>\n"
                + "  <countryzones>\n"
                + "    " + unexpectedText
                + "    <country code=\"gb\">\n"
                + "      <id>Europe/London</id>\n"
                + "    </country>\n"
                + "  </countryzones>\n"
                + "</timezones>\n");
        assertZonesEqual(zones("Europe/London"), finder.lookupTimeZonesByCountry("gb"));

        finder = validate("<timezones>\n"
                + "  <countryzones>\n"
                + "    <country code=\"gb\">\n"
                + "      " + unexpectedText
                + "      <id>Europe/London</id>\n"
                + "    </country>\n"
                + "  </countryzones>\n"
                + "</timezones>\n");
        assertZonesEqual(zones("Europe/London"), finder.lookupTimeZonesByCountry("gb"));

        finder = validate("<timezones>\n"
                + "  <countryzones>\n"
                + "    <country code=\"gb\">\n"
                + "      <id>Europe/London</id>\n"
                + "      " + unexpectedText
                + "      <id>Europe/Paris</id>\n"
                + "    </country>\n"
                + "  </countryzones>\n"
                + "</timezones>\n");
        assertZonesEqual(zones("Europe/London", "Europe/Paris"),
                finder.lookupTimeZonesByCountry("gb"));
    }

    @Test
    public void xmlParsing_truncatedInput() throws Exception {
        checkValidateThrowsParserException("<timezones>\n");

        checkValidateThrowsParserException("<timezones>\n"
                + "  <countryzones>\n");

        checkValidateThrowsParserException("<timezones>\n"
                + "  <countryzones>\n"
                + "    <country code=\"gb\">\n");

        checkValidateThrowsParserException("<timezones>\n"
                + "  <countryzones>\n"
                + "    <country code=\"gb\">\n"
                + "      <id>Europe/London</id>\n");

        checkValidateThrowsParserException("<timezones>\n"
                + "  <countryzones>\n"
                + "    <country code=\"gb\">\n"
                + "      <id>Europe/London</id>\n"
                + "    </country>\n");

        checkValidateThrowsParserException("<timezones>\n"
                + "  <countryzones>\n"
                + "    <country code=\"gb\">\n"
                + "      <id>Europe/London</id>\n"
                + "    </country>\n"
                + "  </countryzones>\n");
    }

    @Test
    public void xmlParsing_unexpectedChildInTimeZoneIdThrows() throws Exception {
        checkValidateThrowsParserException("<timezones>\n"
                + "  <countryzones>\n"
                + "    <country code=\"gb\">\n"
                + "      <id><unexpected-element /></id>\n"
                + "    </country>\n"
                + "  </countryzones>\n"
                + "</timezones>\n");
    }

    @Test
    public void xmlParsing_unknownTimeZoneIdIgnored() throws Exception {
        TimeZoneFinder finder = validate("<timezones>\n"
                + "  <countryzones>\n"
                + "    <country code=\"gb\">\n"
                + "      <id>Unknown_Id</id>\n"
                + "      <id>Europe/London</id>\n"
                + "    </country>\n"
                + "  </countryzones>\n"
                + "</timezones>\n");
        assertZonesEqual(zones("Europe/London"), finder.lookupTimeZonesByCountry("gb"));
    }

    @Test
    public void xmlParsing_missingCountryCode() throws Exception {
        checkValidateThrowsParserException("<timezones>\n"
                + "  <countryzones>\n"
                + "    <country>\n"
                + "      <id>Europe/London</id>\n"
                + "    </country>\n"
                + "  </countryzones>\n"
                + "</timezones>\n");
    }

    @Test
    public void xmlParsing_unknownCountryReturnsNull() throws Exception {
        TimeZoneFinder finder = validate("<timezones>\n"
                + "  <countryzones>\n"
                + "  </countryzones>\n"
                + "</timezones>\n");
        assertNull(finder.lookupTimeZonesByCountry("gb"));
    }

    @Test
    public void lookupTimeZonesByCountry_structuresAreImmutable() throws Exception {
        TimeZoneFinder finder = validate("<timezones>\n"
                + "  <countryzones>\n"
                + "    <country code=\"gb\">\n"
                + "      <id>Europe/London</id>\n"
                + "    </country>\n"
                + "  </countryzones>\n"
                + "</timezones>\n");

        List<TimeZone> gbList = finder.lookupTimeZonesByCountry("gb");
        assertEquals(1, gbList.size());
        assertImmutableList(gbList);
        assertImmutableTimeZone(gbList.get(0));

        assertNull(finder.lookupTimeZonesByCountry("unknown"));
    }

    @Test
    public void lookupTimeZoneByCountryAndOffset_unknownCountry() throws Exception {
        TimeZoneFinder finder = validate("<timezones>\n"
                + "  <countryzones>\n"
                + "    <country code=\"xx\">\n"
                + "      <id>Europe/London</id>\n"
                + "    </country>\n"
                + "  </countryzones>\n"
                + "</timezones>\n");

        // Demonstrate the arguments work for a known country.
        assertZoneEquals(LONDON_TZ,
                finder.lookupTimeZoneByCountryAndOffset("xx", LONDON_DST_OFFSET_MILLIS,
                        true /* isDst */, WHEN_DST, null /* bias */));

        // Test with an unknown country.
        String unknownCountryCode = "yy";
        assertNull(finder.lookupTimeZoneByCountryAndOffset(unknownCountryCode,
                LONDON_DST_OFFSET_MILLIS, true /* isDst */, WHEN_DST, null /* bias */));

        assertNull(finder.lookupTimeZoneByCountryAndOffset(unknownCountryCode,
                LONDON_DST_OFFSET_MILLIS, true /* isDst */, WHEN_DST, LONDON_TZ /* bias */));
    }

    @Test
    public void lookupTimeZoneByCountryAndOffset_oneCandidate() throws Exception {
        TimeZoneFinder finder = validate("<timezones>\n"
                + "  <countryzones>\n"
                + "    <country code=\"xx\">\n"
                + "      <id>Europe/London</id>\n"
                + "    </country>\n"
                + "  </countryzones>\n"
                + "</timezones>\n");

        // The three parameters match the configured zone: offset, isDst and when.
        assertZoneEquals(LONDON_TZ,
                finder.lookupTimeZoneByCountryAndOffset("xx", LONDON_DST_OFFSET_MILLIS,
                        true /* isDst */, WHEN_DST, null /* bias */));
        assertZoneEquals(LONDON_TZ,
                finder.lookupTimeZoneByCountryAndOffset("xx", LONDON_NO_DST_OFFSET_MILLIS,
                        false /* isDst */, WHEN_NO_DST, null /* bias */));

        // Some lookup failure cases where the offset, isDst and when do not match the configured
        // zone.
        TimeZone noDstMatch1 = finder.lookupTimeZoneByCountryAndOffset("xx",
                LONDON_DST_OFFSET_MILLIS, true /* isDst */, WHEN_NO_DST, null /* bias */);
        assertNull(noDstMatch1);

        TimeZone noDstMatch2 = finder.lookupTimeZoneByCountryAndOffset("xx",
                LONDON_DST_OFFSET_MILLIS, false /* isDst */, WHEN_NO_DST, null /* bias */);
        assertNull(noDstMatch2);

        TimeZone noDstMatch3 = finder.lookupTimeZoneByCountryAndOffset("xx",
                LONDON_NO_DST_OFFSET_MILLIS, true /* isDst */, WHEN_DST, null /* bias */);
        assertNull(noDstMatch3);

        TimeZone noDstMatch4 = finder.lookupTimeZoneByCountryAndOffset("xx",
                LONDON_NO_DST_OFFSET_MILLIS, true /* isDst */, WHEN_NO_DST, null /* bias */);
        assertNull(noDstMatch4);

        TimeZone noDstMatch5 = finder.lookupTimeZoneByCountryAndOffset("xx",
                LONDON_DST_OFFSET_MILLIS, false /* isDst */, WHEN_DST, null /* bias */);
        assertNull(noDstMatch5);

        TimeZone noDstMatch6 = finder.lookupTimeZoneByCountryAndOffset("xx",
                LONDON_NO_DST_OFFSET_MILLIS, false /* isDst */, WHEN_DST, null /* bias */);
        assertNull(noDstMatch6);

        // Some bias cases below.

        // The bias is irrelevant here: it matches what would be returned anyway.
        assertZoneEquals(LONDON_TZ,
                finder.lookupTimeZoneByCountryAndOffset("xx", LONDON_DST_OFFSET_MILLIS,
                        true /* isDst */, WHEN_DST, LONDON_TZ /* bias */));
        assertZoneEquals(LONDON_TZ,
                finder.lookupTimeZoneByCountryAndOffset("xx", LONDON_NO_DST_OFFSET_MILLIS,
                        false /* isDst */, WHEN_NO_DST, LONDON_TZ /* bias */));
        // A sample of a non-matching case with bias.
        assertNull(finder.lookupTimeZoneByCountryAndOffset("xx", LONDON_DST_OFFSET_MILLIS,
                true /* isDst */, WHEN_NO_DST, LONDON_TZ /* bias */));

        // The bias should be ignored: it doesn't match any of the country's zones.
        assertZoneEquals(LONDON_TZ,
                finder.lookupTimeZoneByCountryAndOffset("xx", LONDON_DST_OFFSET_MILLIS,
                        true /* isDst */, WHEN_DST, NEW_YORK_TZ /* bias */));

        // The bias should still be ignored even though it matches the offset information given:
        // it doesn't match any of the country's configured zones.
        assertNull(finder.lookupTimeZoneByCountryAndOffset("xx", NEW_YORK_DST_OFFSET_MILLIS,
                true /* isDst */, WHEN_DST, NEW_YORK_TZ /* bias */));
    }

    @Test
    public void lookupTimeZoneByCountryAndOffset_multipleNonOverlappingCandidates()
            throws Exception {
        TimeZoneFinder finder = validate("<timezones>\n"
                + "  <countryzones>\n"
                + "    <country code=\"xx\">\n"
                + "      <id>America/New_York</id>\n"
                + "      <id>Europe/London</id>\n"
                + "    </country>\n"
                + "  </countryzones>\n"
                + "</timezones>\n");

        // The three parameters match the configured zone: offset, isDst and when.
        assertZoneEquals(LONDON_TZ, finder.lookupTimeZoneByCountryAndOffset("xx",
                LONDON_DST_OFFSET_MILLIS, true /* isDst */, WHEN_DST, null /* bias */));
        assertZoneEquals(LONDON_TZ, finder.lookupTimeZoneByCountryAndOffset("xx",
                LONDON_NO_DST_OFFSET_MILLIS, false /* isDst */, WHEN_NO_DST, null /* bias */));
        assertZoneEquals(NEW_YORK_TZ, finder.lookupTimeZoneByCountryAndOffset("xx",
                NEW_YORK_DST_OFFSET_MILLIS, true /* isDst */, WHEN_DST, null /* bias */));
        assertZoneEquals(NEW_YORK_TZ, finder.lookupTimeZoneByCountryAndOffset("xx",
                NEW_YORK_NO_DST_OFFSET_MILLIS, false /* isDst */, WHEN_NO_DST, null /* bias */));

        // Some lookup failure cases where the offset, isDst and when do not match the configured
        // zone. This is a sample, not complete.
        TimeZone noDstMatch1 = finder.lookupTimeZoneByCountryAndOffset("xx",
                LONDON_DST_OFFSET_MILLIS, true /* isDst */, WHEN_NO_DST, null /* bias */);
        assertNull(noDstMatch1);

        TimeZone noDstMatch2 = finder.lookupTimeZoneByCountryAndOffset("xx",
                LONDON_DST_OFFSET_MILLIS, false /* isDst */, WHEN_NO_DST, null /* bias */);
        assertNull(noDstMatch2);

        TimeZone noDstMatch3 = finder.lookupTimeZoneByCountryAndOffset("xx",
                NEW_YORK_NO_DST_OFFSET_MILLIS, true /* isDst */, WHEN_DST, null /* bias */);
        assertNull(noDstMatch3);

        TimeZone noDstMatch4 = finder.lookupTimeZoneByCountryAndOffset("xx",
                NEW_YORK_NO_DST_OFFSET_MILLIS, true /* isDst */, WHEN_NO_DST, null /* bias */);
        assertNull(noDstMatch4);

        TimeZone noDstMatch5 = finder.lookupTimeZoneByCountryAndOffset("xx",
                LONDON_DST_OFFSET_MILLIS, false /* isDst */, WHEN_DST, null /* bias */);
        assertNull(noDstMatch5);

        TimeZone noDstMatch6 = finder.lookupTimeZoneByCountryAndOffset("xx",
                LONDON_NO_DST_OFFSET_MILLIS, false /* isDst */, WHEN_DST, null /* bias */);
        assertNull(noDstMatch6);

        // Some bias cases below.

        // The bias is irrelevant here: it matches what would be returned anyway.
        assertZoneEquals(LONDON_TZ,
                finder.lookupTimeZoneByCountryAndOffset("xx", LONDON_DST_OFFSET_MILLIS,
                        true /* isDst */, WHEN_DST, LONDON_TZ /* bias */));
        assertZoneEquals(LONDON_TZ,
                finder.lookupTimeZoneByCountryAndOffset("xx", LONDON_NO_DST_OFFSET_MILLIS,
                        false /* isDst */, WHEN_NO_DST, LONDON_TZ /* bias */));
        // A sample of a non-matching case with bias.
        assertNull(finder.lookupTimeZoneByCountryAndOffset("xx", LONDON_DST_OFFSET_MILLIS,
                true /* isDst */, WHEN_NO_DST, LONDON_TZ /* bias */));

        // The bias should be ignored: it matches a configured zone, but the offset is wrong so
        // should not be considered a match.
        assertZoneEquals(LONDON_TZ,
                finder.lookupTimeZoneByCountryAndOffset("xx", LONDON_DST_OFFSET_MILLIS,
                        true /* isDst */, WHEN_DST, NEW_YORK_TZ /* bias */));
    }

    // This is an artificial case very similar to America/Denver and America/Phoenix in the US: both
    // have the same offset for 6 months of the year but diverge. Australia/Lord_Howe too.
    @Test
    public void lookupTimeZoneByCountryAndOffset_multipleOverlappingCandidates() throws Exception {
        // Three zones that have the same offset for some of the year. Europe/London changes
        // offset WHEN_DST, the others do not.
        TimeZoneFinder finder = validate("<timezones>\n"
                + "  <countryzones>\n"
                + "    <country code=\"xx\">\n"
                + "      <id>Atlantic/Reykjavik</id>\n"
                + "      <id>Europe/London</id>\n"
                + "      <id>Etc/UTC</id>\n"
                + "    </country>\n"
                + "  </countryzones>\n"
                + "</timezones>\n");

        // This is the no-DST offset for LONDON_TZ, REYKJAVIK_TZ. UTC_TZ.
        final int noDstOffset = LONDON_NO_DST_OFFSET_MILLIS;
        // This is the DST offset for LONDON_TZ.
        final int dstOffset = LONDON_DST_OFFSET_MILLIS;

        // The three parameters match the configured zone: offset, isDst and when.
        assertZoneEquals(LONDON_TZ, finder.lookupTimeZoneByCountryAndOffset("xx", dstOffset,
                true /* isDst */, WHEN_DST, null /* bias */));
        assertZoneEquals(REYKJAVIK_TZ, finder.lookupTimeZoneByCountryAndOffset("xx", noDstOffset,
                false /* isDst */, WHEN_NO_DST, null /* bias */));
        assertZoneEquals(LONDON_TZ, finder.lookupTimeZoneByCountryAndOffset("xx", dstOffset,
                true /* isDst */, WHEN_DST, null /* bias */));
        assertZoneEquals(REYKJAVIK_TZ, finder.lookupTimeZoneByCountryAndOffset("xx", noDstOffset,
                false /* isDst */, WHEN_NO_DST, null /* bias */));
        assertZoneEquals(REYKJAVIK_TZ, finder.lookupTimeZoneByCountryAndOffset("xx", noDstOffset,
                false /* isDst */, WHEN_DST, null /* bias */));

        // Some lookup failure cases where the offset, isDst and when do not match the configured
        // zones.
        TimeZone noDstMatch1 = finder.lookupTimeZoneByCountryAndOffset("xx", dstOffset,
                true /* isDst */, WHEN_NO_DST, null /* bias */);
        assertNull(noDstMatch1);

        TimeZone noDstMatch2 = finder.lookupTimeZoneByCountryAndOffset("xx", noDstOffset,
                true /* isDst */, WHEN_DST, null /* bias */);
        assertNull(noDstMatch2);

        TimeZone noDstMatch3 = finder.lookupTimeZoneByCountryAndOffset("xx", noDstOffset,
                true /* isDst */, WHEN_NO_DST, null /* bias */);
        assertNull(noDstMatch3);

        TimeZone noDstMatch4 = finder.lookupTimeZoneByCountryAndOffset("xx", dstOffset,
                false /* isDst */, WHEN_DST, null /* bias */);
        assertNull(noDstMatch4);


        // Some bias cases below.

        // The bias is relevant here: it overrides what would be returned naturally.
        assertZoneEquals(REYKJAVIK_TZ, finder.lookupTimeZoneByCountryAndOffset("xx", noDstOffset,
                false /* isDst */, WHEN_NO_DST, null /* bias */));
        assertZoneEquals(LONDON_TZ, finder.lookupTimeZoneByCountryAndOffset("xx", noDstOffset,
                false /* isDst */, WHEN_NO_DST, LONDON_TZ /* bias */));
        assertZoneEquals(UTC_TZ, finder.lookupTimeZoneByCountryAndOffset("xx", noDstOffset,
                false /* isDst */, WHEN_NO_DST, UTC_TZ /* bias */));

        // The bias should be ignored: it matches a configured zone, but the offset is wrong so
        // should not be considered a match.
        assertZoneEquals(LONDON_TZ, finder.lookupTimeZoneByCountryAndOffset("xx",
                LONDON_DST_OFFSET_MILLIS, true /* isDst */, WHEN_DST, REYKJAVIK_TZ /* bias */));
    }

    @Test
    public void consistencyTest() throws Exception {
        // Confirm that no new zones have been added to zones.tab without also adding them to the
        // configuration used to drive TimeZoneFinder.

        // zone.tab is a tab separated ASCII file provided by IANA and included in Android's tzdata
        // file. Each line contains a mapping from country code -> zone ID. The ordering used by
        // TimeZoneFinder is Android-specific, but we can use zone.tab to make sure we know about
        // all country zones. Any update to tzdata that adds, renames, or removes zones should be
        // reflected in the file used by TimeZoneFinder.
        Map<String, Set<String>> zoneTabMappings = new HashMap<>();
        for (String line : ZoneInfoDB.getInstance().getZoneTab().split("\n")) {
            int countryCodeEnd = line.indexOf('\t', 1);
            int olsonIdStart = line.indexOf('\t', 4) + 1;
            int olsonIdEnd = line.indexOf('\t', olsonIdStart);
            if (olsonIdEnd == -1) {
                olsonIdEnd = line.length(); // Not all zone.tab lines have a comment.
            }
            String countryCode = line.substring(0, countryCodeEnd);
            String olsonId = line.substring(olsonIdStart, olsonIdEnd);
            Set<String> zoneIds = zoneTabMappings.get(countryCode);
            if (zoneIds == null) {
                zoneIds = new HashSet<>();
                zoneTabMappings.put(countryCode, zoneIds);
            }
            zoneIds.add(olsonId);
        }

        TimeZoneFinder timeZoneFinder = TimeZoneFinder.getInstance();
        for (Map.Entry<String, Set<String>> countryEntry : zoneTabMappings.entrySet()) {
            String countryCode = countryEntry.getKey();
            // Android uses lower case, IANA uses upper.
            countryCode = countryCode.toLowerCase();

            List<String> ianaZoneIds = countryEntry.getValue().stream().sorted()
                    .collect(Collectors.toList());
            List<TimeZone> androidZones = timeZoneFinder.lookupTimeZonesByCountry(countryCode);
            List<String> androidZoneIds =
                    androidZones.stream().map(TimeZone::getID).sorted()
                            .collect(Collectors.toList());

            assertEquals("Android zones for " + countryCode + " do not match IANA data",
                    ianaZoneIds, androidZoneIds);
        }
    }

    private void assertImmutableTimeZone(TimeZone timeZone) {
        try {
            timeZone.setRawOffset(1000);
            fail();
        } catch (UnsupportedOperationException expected) {
        }
    }

    private static void assertImmutableList(List<TimeZone> timeZones) {
        try {
            timeZones.add(null);
            fail();
        } catch (UnsupportedOperationException expected) {
        }
    }

    private static void assertZoneEquals(TimeZone expected, TimeZone actual) {
        // TimeZone.equals() only checks the ID, but that's ok for these tests.
        assertEquals(expected, actual);
    }

    private static void assertZonesEqual(List<TimeZone> expected, List<TimeZone> actual) {
        // TimeZone.equals() only checks the ID, but that's ok for these tests.
        assertEquals(expected, actual);
    }

    private static void checkValidateThrowsParserException(String xml) throws Exception {
        try {
            validate(xml);
            fail();
        } catch (IOException expected) {
        }
    }

    private static TimeZoneFinder validate(String xml) throws IOException {
        TimeZoneFinder timeZoneFinder = TimeZoneFinder.createInstanceForTests(xml);
        timeZoneFinder.validate();
        return timeZoneFinder;
    }

    private static List<TimeZone> zones(String... ids) {
        return Arrays.stream(ids).map(TimeZone::getTimeZone).collect(Collectors.toList());
    }

    private String createFile(String fileContent) throws IOException {
        Path filePath = Files.createTempFile(testDir, null, null);
        Files.write(filePath, fileContent.getBytes(StandardCharsets.UTF_8));
        return filePath.toString();
    }

    private String createMissingFile() throws IOException {
        Path filePath = Files.createTempFile(testDir, null, null);
        Files.delete(filePath);
        return filePath.toString();
    }
}
