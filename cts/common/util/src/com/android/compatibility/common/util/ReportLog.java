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

package com.android.compatibility.common.util;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class to add results to the report.
 */
public class ReportLog implements Serializable {

    private static final String ENCODING = "UTF-8";
    private static final String TYPE = "org.kxml2.io.KXmlParser,org.kxml2.io.KXmlSerializer";

    // XML constants
    private static final String METRIC_TAG = "Metric";
    private static final String MESSAGE_ATTR = "message";
    private static final String SCORETYPE_ATTR = "score_type";
    private static final String SCOREUNIT_ATTR = "score_unit";
    private static final String SOURCE_ATTR = "source";
    private static final String SUMMARY_TAG = "Summary";
    private static final String VALUE_TAG = "Value";
    private static final String DEFAULT_NAME = "default";

    protected Metric mSummary;
    protected String mReportLogName;
    protected String mStreamName;

    public static class Metric implements Serializable {
        private static final int MAX_SOURCE_LENGTH = 200;
        private static final int MAX_MESSAGE_LENGTH = 200;
        private static final int MAX_NUM_VALUES = 1000;
        String mSource;
        String mMessage;
        double[] mValues;
        ResultType mType;
        ResultUnit mUnit;

        Metric(String source, String message, double value, ResultType type, ResultUnit unit) {
            this(source, message, new double[] { value }, type, unit);
        }

        /**
         * Creates a metric array to be included in the report. Each object has a message
         * describing its values and enums to interpret them. In addition, each result also includes
         * class, method and line number information about the test which added this result which is
         * collected by looking at the stack trace.
         *
         * @param message A string describing the values
         * @param values An array of the values
         * @param type Represents how to interpret the values (eg. A lower score is better)
         * @param unit Represents the unit in which the values are (eg. Milliseconds)
         */
        Metric(String source, String message, double[] values, ResultType type, ResultUnit unit) {
            int sourceLength = source.length();
            if (sourceLength > MAX_SOURCE_LENGTH) {
                // Substring to the end
                mSource = source.substring(sourceLength - MAX_SOURCE_LENGTH);
            } else {
                mSource = source;
            }
            int messageLength = message.length();
            if (messageLength > MAX_MESSAGE_LENGTH) {
                // Substring from the start
                mMessage = message.substring(0, MAX_MESSAGE_LENGTH);
            } else {
                mMessage = message;
            }
            int valuesLength = values.length;
            if (valuesLength > MAX_NUM_VALUES) {
                // Subarray from the start
                mValues = Arrays.copyOf(values, MAX_NUM_VALUES);
            } else {
                mValues = values;
            }
            mType = type;
            mUnit = unit;
        }

        public String getSource() {
            return mSource;
        }

        public String getMessage() {
            return mMessage;
        }

        public double[] getValues() {
            return mValues;
        }

        public ResultType getType() {
            return mType;
        }

        public ResultUnit getUnit() {
            return mUnit;
        }

        void serialize(XmlSerializer serializer)
                throws IllegalArgumentException, IllegalStateException, IOException {
            serializer.startTag(null, METRIC_TAG);
            serializer.attribute(null, SOURCE_ATTR, getSource());
            serializer.attribute(null, MESSAGE_ATTR, getMessage());
            serializer.attribute(null, SCORETYPE_ATTR, getType().toReportString());
            serializer.attribute(null, SCOREUNIT_ATTR, getUnit().toReportString());
            for (double d : getValues()) {
                serializer.startTag(null, VALUE_TAG);
                serializer.text(Double.toString(d));
                serializer.endTag(null, VALUE_TAG);
            }
            serializer.endTag(null, METRIC_TAG);
        }

        static Metric parse(XmlPullParser parser)
                throws XmlPullParserException, IOException {
            parser.require(XmlPullParser.START_TAG, null, METRIC_TAG);
            String source = parser.getAttributeValue(null, SOURCE_ATTR);
            String message = parser.getAttributeValue(null, MESSAGE_ATTR);
            ResultType type = ResultType.parseReportString(
                    parser.getAttributeValue(null, SCORETYPE_ATTR));
            ResultUnit unit = ResultUnit.parseReportString(
                    parser.getAttributeValue(null, SCOREUNIT_ATTR));
            List<String> valuesList = new ArrayList<>();
            while (parser.nextTag() == XmlPullParser.START_TAG) {
                parser.require(XmlPullParser.START_TAG, null, VALUE_TAG);
                valuesList.add(parser.nextText());
                parser.require(XmlPullParser.END_TAG, null, VALUE_TAG);
            }
            int length = valuesList.size();
            double[] values = new double[length];
            for (int i = 0; i < length; i++) {
                values[i] = Double.parseDouble(valuesList.get(i));
            }
            parser.require(XmlPullParser.END_TAG, null, METRIC_TAG);
            return new Metric(source, message, values, type, unit);
        }
    }

    public ReportLog() {
        mReportLogName = DEFAULT_NAME;
    }

    public ReportLog(String reportLogName, String streamName) {
        mReportLogName = reportLogName;
        mStreamName = streamName;
    }

    /**
     * Adds a double array of metrics to the report.
     */
    public void addValues(String message, double[] values, ResultType type, ResultUnit unit) {
        // Do nothing. Subclasses may implement using InfoStore to write metrics to files.
    }

    /**
     * Adds a double array of metrics to the report.
     */
    public void addValues(String source, String message, double[] values, ResultType type,
            ResultUnit unit) {
        // Do nothing. Subclasses may implement using InfoStore to write metrics to files.
    }

    /**
     * Adds a double metric to the report.
     */
    public void addValue(String message, double value, ResultType type, ResultUnit unit) {
        // Do nothing. Subclasses may implement using InfoStore to write metrics to files.
    }

    /**
     * Adds a double metric to the report.
     */
    public void addValue(String source, String message, double value, ResultType type,
            ResultUnit unit) {
        // Do nothing. Subclasses may implement using InfoStore to write metrics to files.
    }

    /**
     * Adds an int metric to the report.
     */
    public void addValue(String message, int value, ResultType type, ResultUnit unit) {
        // Do nothing. Subclasses may implement using InfoStore to write metrics to files.
    }

    /**
     * Adds a long metric to the report.
     */
    public void addValue(String message, long value, ResultType type, ResultUnit unit) {
        // Do nothing. Subclasses may implement using InfoStore to write metrics to files.
    }

    /**
     * Adds a float metric to the report.
     */
    public void addValue(String message, float value, ResultType type, ResultUnit unit) {
        // Do nothing. Subclasses may implement using InfoStore to write metrics to files.
    }

    /**
     * Adds a boolean metric to the report.
     */
    public void addValue(String message, boolean value, ResultType type, ResultUnit unit) {
        // Do nothing. Subclasses may implement using InfoStore to write metrics to files.
    }

    /**
     * Adds a String metric to the report.
     */
    public void addValue(String message, String value, ResultType type, ResultUnit unit) {
        // Do nothing. Subclasses may implement using InfoStore to write metrics to files.
    }

    /**
     * Adds an int array of metrics to the report.
     */
    public void addValues(String message, int[] values, ResultType type, ResultUnit unit) {
        // Do nothing. Subclasses may implement using InfoStore to write metrics to files.
    }

    /**
     * Adds a long array of metrics to the report.
     */
    public void addValues(String message, long[] values, ResultType type, ResultUnit unit) {
        // Do nothing. Subclasses may implement using InfoStore to write metrics to files.
    }

    /**
     * Adds a float array of metrics to the report.
     */
    public void addValues(String message, float[] values, ResultType type, ResultUnit unit) {
        // Do nothing. Subclasses may implement using InfoStore to write metrics to files.
    }

    /**
     * Adds a boolean array of metrics to the report.
     */
    public void addValues(String message, boolean[] values, ResultType type, ResultUnit unit) {
        // Do nothing. Subclasses may implement using InfoStore to write metrics to files.
    }

    /**
     * Adds a String List of metrics to the report.
     */
    public void addValues(String message, List<String> values, ResultType type, ResultUnit unit) {
        // Do nothing. Subclasses may implement using InfoStore to write metrics to files.
    }

    /**
     * @param elem
     */
    /* package */ void setSummary(Metric elem) {
        mSummary = elem;
    }

    /**
     * Sets the double metric summary of the report.
     *
     * NOTE: messages over {@value Metric#MAX_MESSAGE_LENGTH} chars will be trimmed.
     */
    public void setSummary(String message, double value, ResultType type, ResultUnit unit) {
        setSummary(new Metric(Stacktrace.getTestCallerClassMethodNameLineNumber(), message, value,
                type, unit));
    }

    public Metric getSummary() {
        return mSummary;
    }

    /**
     * Serializes a given {@link ReportLog} to a String.
     * @throws XmlPullParserException
     * @throws IOException
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     */
    public static String serialize(ReportLog reportlog) throws XmlPullParserException,
            IllegalArgumentException, IllegalStateException, IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        XmlSerializer serializer = XmlPullParserFactory.newInstance(TYPE, null).newSerializer();
        serializer.setOutput(byteArrayOutputStream, ENCODING);
        serializer.startDocument(ENCODING, true);
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
        serialize(serializer, reportlog);
        serializer.endDocument();
        return byteArrayOutputStream.toString(ENCODING);
    }

    /**
     * Serializes a given {@link ReportLog} to XML.
     * @param serializer
     * @param reportLog
     * @throws IOException
     */
    public static void serialize(XmlSerializer serializer, ReportLog reportLog)
            throws IOException {
        if (reportLog == null) {
            throw new IllegalArgumentException("Metrics reports was null");
        }
        Metric summary = reportLog.getSummary();
        // Summary is optional. Details are not included in result report.
        if (summary != null) {
            serializer.startTag(null, SUMMARY_TAG);
            summary.serialize(serializer);
            serializer.endTag(null, SUMMARY_TAG);
        }
    }

    /**
     * Parses a {@link ReportLog} from the given string.
     * @throws XmlPullParserException
     * @throws IOException
     */
    public static ReportLog parse(String result) throws XmlPullParserException, IOException {
        if (result == null){
            throw new IllegalArgumentException("Metrics string was null");
        }
        if (result.trim().isEmpty()) {
            // Empty report.
            return new ReportLog();
        }
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new ByteArrayInputStream(result.getBytes(ENCODING)), ENCODING);
        try {
            parser.nextTag();
        } catch (XmlPullParserException e) {
            // Empty Report.
            return new ReportLog();
        }
        return parse(parser);
    }

    /**
     * Parses a {@link ReportLog} from the given XML parser.
     * @param parser
     * @throws IOException
     * @throws XmlPullParserException
     */
    public static ReportLog parse(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, SUMMARY_TAG);
        parser.nextTag();
        ReportLog report = new ReportLog();
        report.setSummary(Metric.parse(parser));
        parser.nextTag();
        parser.require(XmlPullParser.END_TAG, null, SUMMARY_TAG);
        return report;
    }
}
