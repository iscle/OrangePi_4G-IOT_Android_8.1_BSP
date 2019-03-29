package com.android.cts.verifier.sensors;

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
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Debug;
import android.os.Environment;
import android.os.PowerManager;
import android.util.JsonWriter;
import android.util.Log;

import org.opencv.core.Mat;
import org.opencv.core.CvType;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import android.opengl.GLES20;
import javax.microedition.khronos.opengles.GL10;

/**
 *  This class does analysis on the recorded RVCVCXCheck data sets.
 */
public class RVCVXCheckAnalyzer {
    private static final String TAG = "RVCVXAnalysis";
    private static final boolean LOCAL_LOGV = false;
    private static final boolean LOCAL_LOGD = true;
    private final String mPath;

    private static final boolean OUTPUT_DEBUG_IMAGE = false;
    private static final double VALID_FRAME_THRESHOLD = 0.8;
    private static final double REPROJECTION_THREASHOLD_RATIO = 0.008;
    private static final boolean FORCE_CV_ANALYSIS  = false;
    private static final boolean TRACE_VIDEO_ANALYSIS = false;
    private static final double DECIMATION_FPS_TARGET = 15.0;
    private static final double MIN_VIDEO_LENGTH_SEC = 10;

    RVCVXCheckAnalyzer(String path)
    {
        mPath = path;
    }

    /**
     * A class that contains  the analysis results
     *
     */
    class AnalyzeReport {
        public boolean error=true;
        public String reason = "incomplete";

        // roll pitch yaw RMS error ( \sqrt{\frac{1}{n} \sum e_i^2 })
        // unit in rad
        public double roll_rms_error;
        public double pitch_rms_error;
        public double yaw_rms_error;

        // roll pitch yaw max error
        // unit in rad
        public double roll_max_error;
        public double pitch_max_error;
        public double yaw_max_error;

        // optimal t delta between sensor and camera data set to make best match
        public double optimal_delta_t;
        // the associate yaw offset based on initial values
        public double yaw_offset;

        public int n_of_frame;
        public int n_of_valid_frame;

        // both data below are in [sec]
        public double sensor_period_avg;
        public double sensor_period_stdev;

        /**
         * write Json format serialization to a file in case future processing need the data
         */
        public void writeToFile(File file) {
            try {
                writeJSONToStream(new FileOutputStream(file));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Log.e(TAG, "Cannot create analyze report file.");
            }
        }

        /**
         * Get the JSON format serialization
         *@return Json format serialization as String
         */
        @Override
        public String toString() {
            ByteArrayOutputStream s = new ByteArrayOutputStream();
            writeJSONToStream(s);
            return new String(s.toByteArray(),  java.nio.charset.StandardCharsets.UTF_8);
        }

        private void writeJSONToStream(OutputStream s) {
            try{
                JsonWriter writer =
                        new JsonWriter(
                                new OutputStreamWriter( s )
                        );
                writer.beginObject();
                writer.setLenient(true);

                writer.name("roll_rms_error").value(roll_rms_error);
                writer.name("pitch_rms_error").value(pitch_rms_error);
                writer.name("yaw_rms_error").value(yaw_rms_error);
                writer.name("roll_max_error").value(roll_max_error);
                writer.name("pitch_max_error").value(pitch_max_error);
                writer.name("yaw_max_error").value(yaw_max_error);
                writer.name("optimal_delta_t").value(optimal_delta_t);
                writer.name("yaw_offset").value(yaw_offset);
                writer.name("n_of_frame").value(n_of_frame);
                writer.name("n_of_valid_frame").value(n_of_valid_frame);
                writer.name("sensor_period_avg").value(sensor_period_avg);
                writer.name("sensor_period_stdev").value(sensor_period_stdev);

                writer.endObject();

                writer.close();
            } catch (IOException e) {
                // do nothing
                Log.e(TAG, "Error in serialize analyze report to JSON");
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                Log.e(TAG, "Invalid parameter to write into JSON format");
            }
        }
    }

    /**
     *  Process data set stored in the path specified in constructor
     *  and return an analyze report to caller
     *
     *  @return An AnalyzeReport that contains detailed information about analysis
     */
    public AnalyzeReport processDataSet() {
        int nframe;// number of frames in video
        int nslog; // number of sensor log
        int nvlog; // number of video generated log


        AnalyzeReport report = new AnalyzeReport();

        ArrayList<AttitudeRec> srecs = new ArrayList<>();
        ArrayList<AttitudeRec> vrecs = new ArrayList<>();
        ArrayList<AttitudeRec> srecs2 = new ArrayList<>();


        final boolean use_solved = new File(mPath, "vision_rpy.log").exists() && !FORCE_CV_ANALYSIS;

        if (use_solved) {
            nframe = nvlog = loadAttitudeRecs(new File(mPath, "vision_rpy.log"), vrecs);
            nslog = loadAttitudeRecs(new File(mPath, "sensor_rpy.log"),srecs);
        }else {
            nframe = analyzeVideo(vrecs);
            nvlog = vrecs.size();

            if (LOCAL_LOGV) {
                Log.v(TAG, "Post video analysis nvlog = " + nvlog + " nframe=" + nframe);
            }
            if (nvlog <= 0 || nframe <= 0) {
                // invalid results
                report.reason = "Unable to to load recorded video.";
                return report;
            }
            if (nframe < MIN_VIDEO_LENGTH_SEC*VALID_FRAME_THRESHOLD) {
                // video is too short
                Log.w(TAG, "Video record is to short, n frame = " + nframe);
                report.reason = "Video too short.";
                return report;
            }
            if ((double) nvlog / nframe < VALID_FRAME_THRESHOLD) {
                // too many invalid frames
                Log.w(TAG, "Too many invalid frames, n valid frame = " + nvlog +
                        ", n total frame = " + nframe);
                report.reason = "Too many invalid frames.";
                return report;
            }

            fixFlippedAxis(vrecs);

            nslog = loadSensorLog(srecs);
        }

        // Gradient descent will have faster performance than this simple search,
        // but the performance is dominated by the vision part, so it is not very necessary.
        double delta_t;
        double min_rms = Double.MAX_VALUE;
        double min_delta_t =0.;
        double min_yaw_offset =0.;

        // pre-allocation
        for (AttitudeRec i: vrecs) {
            srecs2.add(new AttitudeRec(0,0,0,0));
        }

        // find optimal offset
        for (delta_t = -2.0; delta_t<2.0; delta_t +=0.01) {
            double rms;
            resampleSensorLog(srecs, vrecs, delta_t, 0.0, srecs2);
            rms = Math.sqrt(calcSqrErr(vrecs, srecs2, 0)+ calcSqrErr(vrecs, srecs2, 1));
            if (rms < min_rms) {
                min_rms = rms;
                min_delta_t = delta_t;
                min_yaw_offset = vrecs.get(0).yaw - srecs2.get(0).yaw;
            }
        }
        // sample at optimal offset
        resampleSensorLog(srecs, vrecs, min_delta_t, min_yaw_offset, srecs2);

        if (!use_solved) {
            dumpAttitudeRecs(new File(mPath, "vision_rpy.log"), vrecs);
            dumpAttitudeRecs(new File(mPath, "sensor_rpy.log"), srecs);
        }
        dumpAttitudeRecs(new File(mPath, "sensor_rpy_resampled.log"), srecs2);
        dumpAttitudeError(new File(mPath, "attitude_error.log"), vrecs, srecs2);

        // fill report fields
        report.roll_rms_error = Math.sqrt(calcSqrErr(vrecs, srecs2, 0));
        report.pitch_rms_error = Math.sqrt(calcSqrErr(vrecs, srecs2, 1));
        report.yaw_rms_error = Math.sqrt(calcSqrErr(vrecs, srecs2, 2));

        report.roll_max_error = calcMaxErr(vrecs, srecs2, 0);
        report.pitch_max_error = calcMaxErr(vrecs, srecs2, 1);
        report.yaw_max_error = calcMaxErr(vrecs, srecs2, 2);

        report.optimal_delta_t = min_delta_t;
        report.yaw_offset = (min_yaw_offset);

        report.n_of_frame = nframe;
        report.n_of_valid_frame = nvlog;

        double [] sensor_period_stat = calcSensorPeriodStat(srecs);
        report.sensor_period_avg = sensor_period_stat[0];
        report.sensor_period_stdev = sensor_period_stat[1];

        // output report to file and log in JSON format as well
        report.writeToFile(new File(mPath, "report.json"));
        if (LOCAL_LOGV)    Log.v(TAG, "Report in JSON:" + report.toString());

        report.reason = "Completed";
        report.error = false;
        return report;
    }

    /**
     * Generate pattern geometry like this one
     * http://docs.opencv.org/trunk/_downloads/acircles_pattern.png
     *
     * @return Array of 3D points
     */
    private MatOfPoint3f asymmetricalCircleGrid(Size size) {
        final int cn = 3;

        int n = (int)(size.width * size.height);
        float positions[] = new float[n * cn];
        float unit=0.02f;
        MatOfPoint3f grid = new MatOfPoint3f();

        for (int i = 0; i < size.height; i++) {
            for (int j = 0; j < size.width * cn; j += cn) {
                positions[(int) (i * size.width * cn + j + 0)] =
                        (2 * (j / cn) + i % 2) * (float) unit;
                positions[(int) (i * size.width * cn + j + 1)] =
                        i * unit;
                positions[(int) (i * size.width * cn + j + 2)] = 0;
            }
        }
        grid.create(n, 1, CvType.CV_32FC3);
        grid.put(0, 0, positions);
        return grid;
    }

    /**
     *  Create a camera intrinsic matrix using input parameters
     *
     *  The camera intrinsic matrix will be like:
     *
     *       +-                       -+
     *       |  f   0    center.width  |
     *   A = |  0   f    center.height |
     *       |  0   0         1        |
     *       +-                       -+
     *
     *  @return An approximated (not actually calibrated) camera matrix
     */
    private static Mat cameraMatrix(float f, Size center) {
        final double [] data = {f, 0, center.width, 0, f, center.height, 0, 0, 1f};
        Mat m = new Mat(3,3, CvType.CV_64F);
        m.put(0, 0, data);
        return m;
    }

    /**
     *  Attitude record in time roll pitch yaw format.
     *
     */
    private class AttitudeRec {
        public double time;
        public double roll;
        public double pitch;
        public double yaw;

        // ctor
        AttitudeRec(double atime, double aroll, double apitch, double ayaw) {
            time = atime;
            roll = aroll;
            pitch = apitch;
            yaw = ayaw;
        }

        // ctor
        AttitudeRec(double atime, double [] rpy) {
            time = atime;
            roll = rpy[0];
            pitch = rpy[1];
            yaw = rpy[2];
        }

        // copy value of another to this
        void assign(AttitudeRec rec) {
            time = rec.time;
            roll = rec.time;
            pitch = rec.pitch;
            yaw = rec.yaw;
        }

        // copy roll-pitch-yaw value but leave the time specified by atime
        void assign(AttitudeRec rec, double atime) {
            time = atime;
            roll = rec.time;
            pitch = rec.pitch;
            yaw = rec.yaw;
        }

        // set each field separately
        void set(double atime, double aroll, double apitch, double ayaw) {
            time = atime;
            roll = aroll;
            pitch = apitch;
            yaw = ayaw;
        }
    }


    /**
     *  Load the sensor log in (time Roll-pitch-yaw) format to a ArrayList<AttitudeRec>
     *
     *  @return the number of sensor log items
     */
    private int loadSensorLog(ArrayList<AttitudeRec> recs) {
        //ArrayList<AttitudeRec> recs = new ArrayList<AttitudeRec>();
        File csvFile = new File(mPath, "sensor.log");
        BufferedReader br=null;
        String line;

        // preallocate and reuse
        double [] quat = new double[4];
        double [] rpy = new double[3];

        double t0 = -1;

        try {
            br = new BufferedReader(new FileReader(csvFile));
            while ((line = br.readLine()) != null) {
                //space separator
                String[] items = line.split(" ");

                if (items.length != 5) {
                    recs.clear();
                    return -1;
                }

                quat[0] = Double.parseDouble(items[1]);
                quat[1] = Double.parseDouble(items[2]);
                quat[2] = Double.parseDouble(items[3]);
                quat[3] = Double.parseDouble(items[4]);

                //
                quat2rpy(quat, rpy);

                if (t0 < 0) {
                    t0 = Long.parseLong(items[0])/1e9;
                }
                recs.add(new AttitudeRec(Long.parseLong(items[0])/1e9-t0, rpy));
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e(TAG, "Cannot find sensor logging data");
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Cannot read sensor logging data");
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return recs.size();
    }

    /**
     * Read video meta info
     */
    private class VideoMetaInfo {
        public double fps;
        public int frameWidth;
        public int frameHeight;
        public double fovWidth;
        public double fovHeight;
        public boolean valid = false;

        VideoMetaInfo(File file) {

            BufferedReader br=null;
            String line;
            String content="";
            try {
                br = new BufferedReader(new FileReader(file));
                while ((line = br.readLine()) != null) {
                    content = content +line;
                }

            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Log.e(TAG, "Cannot find video meta info file");
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Cannot read video meta info file");
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (content.isEmpty()) {
                return;
            }

            try {
                JSONObject json = new JSONObject(content);
                frameWidth = json.getInt("width");
                frameHeight = json.getInt("height");
                fps = json.getDouble("frameRate");
                fovWidth = json.getDouble("fovW")*Math.PI/180.0;
                fovHeight = json.getDouble("fovH")*Math.PI/180.0;
            } catch (JSONException e) {
                return;
            }

            valid = true;

        }
    }



    /**
     * Debugging helper function, load ArrayList<AttitudeRec> from a file dumped out by
     * dumpAttitudeRecs
     */
    private int loadAttitudeRecs(File file, ArrayList<AttitudeRec> recs) {
        BufferedReader br=null;
        String line;
        double time;
        double [] rpy = new double[3];

        try {
            br = new BufferedReader(new FileReader(file));
            while ((line = br.readLine()) != null) {
                //space separator
                String[] items = line.split(" ");

                if (items.length != 4) {
                    recs.clear();
                    return -1;
                }

                time = Double.parseDouble(items[0]);
                rpy[0] = Double.parseDouble(items[1]);
                rpy[1] = Double.parseDouble(items[2]);
                rpy[2] = Double.parseDouble(items[3]);

                recs.add(new AttitudeRec(time, rpy));
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e(TAG, "Cannot find AttitudeRecs file specified.");
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Read AttitudeRecs file failure");
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return recs.size();
    }
    /**
     * Debugging helper function, Dump an ArrayList<AttitudeRec> to a file
     */
    private void dumpAttitudeRecs(File file, ArrayList<AttitudeRec> recs) {
        OutputStreamWriter w=null;
        try {
            w = new OutputStreamWriter(new FileOutputStream(file));

            for (AttitudeRec r : recs) {
                w.write(String.format("%f %f %f %f\r\n", r.time, r.roll, r.pitch, r.yaw));
            }
            w.close();
        } catch(FileNotFoundException e) {
            e.printStackTrace();
            Log.e(TAG, "Cannot create AttitudeRecs file.");
        } catch (IOException e) {
            Log.e(TAG, "Write AttitudeRecs file failure");
        } finally {
            if (w!=null) {
                try {
                    w.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     *  Read the sensor log in ArrayList<AttitudeRec> format and find out the sensor sample time
     *  statistics: mean and standard deviation.
     *
     *  @return The returned value will be a double array with exact 2 items, first [0] will be
     *  mean and the second [1]  will be the standard deviation.
     *
     */
    private double [] calcSensorPeriodStat(ArrayList<AttitudeRec> srec)   {
        double tp = srec.get(0).time;
        int i;
        double sum = 0.0;
        double sumsq = 0.0;
        for(i=1; i<srec.size(); ++i) {
            double dt;
            dt = srec.get(i).time - tp;
            sum += dt;
            sumsq += dt*dt;
            tp += dt;
        }
        double [] ret = new double[2];
        ret[0] = sum/srec.size();
        ret[1] = Math.sqrt(sumsq/srec.size() - ret[0]*ret[0]);
        return ret;
    }

    /**
     * Flipping the axis as the image are flipped upside down in OpenGL frames
     */
    private void fixFlippedAxis(ArrayList<AttitudeRec> vrecs)   {
        for (AttitudeRec i: vrecs) {
            i.yaw = -i.yaw;
        }
    }

    /**
     *  Calculate the maximum error on the specified axis between two time aligned (resampled)
     *  ArrayList<AttitudeRec>. Yaw axis needs special treatment as 0 and 2pi error are same thing
     *
     * @param ra  one ArrayList of AttitudeRec
     * @param rb  the other ArrayList of AttitudeRec
     * @param axis axis id for the comparison (0 = roll, 1 = pitch, 2 = yaw)
     * @return Maximum error
     */
    private double calcMaxErr(ArrayList<AttitudeRec> ra, ArrayList<AttitudeRec> rb, int axis)  {
        // check if they are valid and comparable data
        if (ra.size() != rb.size()) {
            throw new ArrayIndexOutOfBoundsException("Two array has to be the same");
        }
        // check input parameter validity
        if (axis<0 || axis > 2) {
            throw new IllegalArgumentException("Invalid data axis.");
        }

        int i;
        double max = 0.0;
        double diff = 0.0;
        for(i=0; i<ra.size(); ++i) {
            // make sure they are aligned data
            if (ra.get(i).time != rb.get(i).time) {
                throw new IllegalArgumentException("Element "+i+
                        " of two inputs has different time.");
            }
            switch(axis) {
                case 0:
                    diff = ra.get(i).roll - rb.get(i).roll; // they always opposite of each other..
                    break;
                case 1:
                    diff = ra.get(i).pitch - rb.get(i).pitch;
                    break;
                case 2:
                    diff = Math.abs(((4*Math.PI + ra.get(i).yaw - rb.get(i).yaw)%(2*Math.PI))
                            -Math.PI)-Math.PI;
                    break;
            }
            diff = Math.abs(diff);
            if (diff>max) {
                max = diff;
            }
        }
        return max;
    }

    /**
     *  Calculate the RMS error on the specified axis between two time aligned (resampled)
     *  ArrayList<AttitudeRec>. Yaw axis needs special treatment as 0 and 2pi error are same thing
     *
     * @param ra  one ArrayList of AttitudeRec
     * @param rb  the other ArrayList of AttitudeRec
     * @param axis axis id for the comparison (0 = roll, 1 = pitch, 2 = yaw)
     * @return Mean square error
     */
    private double calcSqrErr(ArrayList<AttitudeRec> ra, ArrayList<AttitudeRec> rb, int axis) {
        // check if they are valid and comparable data
        if (ra.size() != rb.size()) {
            throw new ArrayIndexOutOfBoundsException("Two array has to be the same");
        }
        // check input parameter validity
        if (axis<0 || axis > 2) {
            throw new IllegalArgumentException("Invalid data axis.");
        }

        int i;
        double sum = 0.0;
        double diff = 0.0;
        for(i=0; i<ra.size(); ++i) {
            // check input data validity
            if (ra.get(i).time != rb.get(i).time) {
                throw new IllegalArgumentException("Element "+i+
                        " of two inputs has different time.");
            }

            switch(axis) {
                case 0:
                    diff = ra.get(i).roll - rb.get(i).roll;
                    break;
                case 1:
                    diff = ra.get(i).pitch - rb.get(i).pitch;
                    break;
                case 2:
                    diff = Math.abs(((4*Math.PI + ra.get(i).yaw - rb.get(i).yaw)%(2*Math.PI))-
                            Math.PI)-Math.PI;
                    break;
            }

            sum += diff*diff;
        }
        return sum/ra.size();
    }

    /**
     * Debugging helper function. Dump the error between two time aligned ArrayList<AttitudeRec>'s
     *
     * @param file File to write to
     * @param ra  one ArrayList of AttitudeRec
     * @param rb  the other ArrayList of AttitudeRec
     */
    private void dumpAttitudeError(File file, ArrayList<AttitudeRec> ra, ArrayList<AttitudeRec> rb){
        if (ra.size() != rb.size()) {
            throw new ArrayIndexOutOfBoundsException("Two array has to be the same");
        }

        int i;

        ArrayList<AttitudeRec> rerr = new ArrayList<>();
        for(i=0; i<ra.size(); ++i) {
            if (ra.get(i).time != rb.get(i).time) {
                throw new IllegalArgumentException("Element "+ i
                        + " of two inputs has different time.");
            }

            rerr.add(new AttitudeRec(ra.get(i).time, ra.get(i).roll - rb.get(i).roll,
                    ra.get(i).pitch - rb.get(i).pitch,
                    (Math.abs(((4*Math.PI + ra.get(i).yaw - rb.get(i).yaw)%(2*Math.PI))
                            -Math.PI)-Math.PI)));

        }
        dumpAttitudeRecs(file, rerr);
    }

    /**
     * Resample one ArrayList<AttitudeRec> with respect to another ArrayList<AttitudeRec>
     *
     * @param rec           the ArrayList of AttitudeRec to be sampled
     * @param timebase      the other ArrayList of AttitudeRec that serves as time base
     * @param delta_t       offset in time before resample
     * @param yaw_offset    offset in yaw axis
     * @param resampled     output ArrayList of AttitudeRec
     */

    private void resampleSensorLog(ArrayList<AttitudeRec> rec, ArrayList<AttitudeRec> timebase,
            double delta_t, double yaw_offset, ArrayList<AttitudeRec> resampled)    {
        int i;
        int j = -1;
        for(i=0; i<timebase.size(); i++) {
            double time = timebase.get(i).time + delta_t;

            while(j<rec.size()-1 && rec.get(j+1).time < time) j++;

            if (j == -1) {
                //use first
                resampled.get(i).assign(rec.get(0), timebase.get(i).time);
            } else if (j == rec.size()-1) {
                // use last
                resampled.get(i).assign(rec.get(j), timebase.get(i).time);
            } else {
                // do linear resample
                double alpha = (time - rec.get(j).time)/((rec.get(j+1).time - rec.get(j).time));
                double roll = (1-alpha) * rec.get(j).roll + alpha * rec.get(j+1).roll;
                double pitch = (1-alpha) * rec.get(j).pitch + alpha * rec.get(j+1).pitch;
                double yaw = (1-alpha) * rec.get(j).yaw + alpha * rec.get(j+1).yaw + yaw_offset;
                resampled.get(i).set(timebase.get(i).time, roll, pitch, yaw);
            }
        }
    }

    /**
     * Analyze video frames using computer vision approach and generate a ArrayList<AttitudeRec>
     *
     * @param recs  output ArrayList of AttitudeRec
     * @return total number of frame of the video
     */
    private int analyzeVideo(ArrayList<AttitudeRec> recs) {
        VideoMetaInfo meta = new VideoMetaInfo(new File(mPath, "videometa.json"));

        int decimation = 1;
        boolean use_timestamp = true;

        // roughly determine if decimation is necessary
        if (meta.fps > DECIMATION_FPS_TARGET) {
            decimation = (int)(meta.fps / DECIMATION_FPS_TARGET);
            meta.fps /=decimation;
        }

        VideoDecoderForOpenCV videoDecoder = new VideoDecoderForOpenCV(
                new File(mPath, "video.mp4"), decimation);


        Mat frame;
        Mat gray = new Mat();
        int i = -1;

        Size frameSize = videoDecoder.getSize();

        if (frameSize.width != meta.frameWidth || frameSize.height != meta.frameHeight) {
            // this is very unlikely
            return -1;
        }

        if (TRACE_VIDEO_ANALYSIS) {
            Debug.startMethodTracing("cvprocess");
        }

        Size patternSize = new Size(4,11);

        float fc = (float)(meta.frameWidth/2.0/Math.tan(meta.fovWidth/2.0));
        Mat camMat = cameraMatrix(fc, new Size(frameSize.width/2, frameSize.height/2));
        MatOfDouble coeff = new MatOfDouble(); // dummy

        MatOfPoint2f centers = new MatOfPoint2f();
        MatOfPoint3f grid = asymmetricalCircleGrid(patternSize);
        Mat rvec = new MatOfFloat();
        Mat tvec = new MatOfFloat();

        MatOfPoint2f reprojCenters = new MatOfPoint2f();

        if (LOCAL_LOGV) {
            Log.v(TAG, "Camera Mat = \n" + camMat.dump());
        }

        long startTime = System.nanoTime();
        long [] ts = new long[1];

        while ((frame = videoDecoder.getFrame(ts)) !=null) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "got a frame " + i);
            }

            if (use_timestamp && ts[0] == -1) {
                use_timestamp = false;
            }

            // has to be in front, as there are cases where execution
            // will skip the later part of this while
            i++;

            // convert to gray manually as by default findCirclesGridDefault uses COLOR_BGR2GRAY
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_RGB2GRAY);

            boolean foundPattern = Calib3d.findCirclesGrid(
                    gray,  patternSize, centers, Calib3d.CALIB_CB_ASYMMETRIC_GRID);

            if (!foundPattern) {
                // skip to next frame
                continue;
            }

            if (OUTPUT_DEBUG_IMAGE) {
                Calib3d.drawChessboardCorners(frame, patternSize, centers, true);
            }

            // figure out the extrinsic parameters using real ground truth 3D points and the pixel
            // position of blobs found in findCircleGrid, an estimated camera matrix and
            // no-distortion are assumed.
            boolean foundSolution =
                    Calib3d.solvePnP(grid, centers, camMat, coeff, rvec, tvec,
                            false, Calib3d.CV_ITERATIVE);

            if (!foundSolution) {
                // skip to next frame
                if (LOCAL_LOGV) {
                    Log.v(TAG, "cannot find pnp solution in frame " + i + ", skipped.");
                }
                continue;
            }

            // reproject points to for evaluation of result accuracy of solvePnP
            Calib3d.projectPoints(grid, rvec, tvec, camMat, coeff, reprojCenters);

            // error is evaluated in norm2, which is real error in pixel distance / sqrt(2)
            double error = Core.norm(centers, reprojCenters, Core.NORM_L2);

            if (LOCAL_LOGV) {
                Log.v(TAG, "Found attitude, re-projection error = " + error);
            }

            // if error is reasonable, add it into the results. use ratio to frame height to avoid
            // discriminating higher definition videos
            if (error < REPROJECTION_THREASHOLD_RATIO * frameSize.height) {
                double [] rv = new double[3];
                double timestamp;

                rvec.get(0,0, rv);
                if (use_timestamp) {
                    timestamp = (double)ts[0] / 1e6;
                } else {
                    timestamp = (double) i / meta.fps;
                }
                if (LOCAL_LOGV) Log.v(TAG, String.format("Added frame %d  ts = %f", i, timestamp));
                recs.add(new AttitudeRec(timestamp, rodr2rpy(rv)));
            }

            if (OUTPUT_DEBUG_IMAGE) {
                Calib3d.drawChessboardCorners(frame, patternSize, reprojCenters, true);
                Imgcodecs.imwrite(Environment.getExternalStorageDirectory().getPath()
                        + "/RVCVRecData/DebugCV/img" + i + ".png", frame);
            }
        }

        if (LOCAL_LOGV) {
            Log.v(TAG, "Finished decoding");
        }

        if (TRACE_VIDEO_ANALYSIS) {
            Debug.stopMethodTracing();
        }

        if (LOCAL_LOGV) {
            // time analysis
            double totalTime = (System.nanoTime()-startTime)/1e9;
            Log.i(TAG, "Total time: "+totalTime +"s, Per frame time: "+totalTime/i );
        }
        return i;
    }

    /**
     * OpenCV for Android have not support the VideoCapture from file
     * This is a make shift solution before it is supported.
     * One issue right now is that the glReadPixels is quite slow .. around 6.5ms for a 720p frame
     */
    private class VideoDecoderForOpenCV implements Runnable {
        static final String TAG = "VideoDecoderForOpenCV";

        private MediaExtractor extractor=null;
        private MediaCodec decoder=null;
        private CtsMediaOutputSurface surface=null;

        private MatBuffer mMatBuffer;

        private final File mVideoFile;

        private boolean valid;
        private Object setupSignal;

        private Thread mThread;
        private int mDecimation;

        /**
         * Constructor
         * @param file video file
         * @param decimation process every "decimation" number of frame
         */
        VideoDecoderForOpenCV(File file, int decimation) {
            mVideoFile = file;
            mDecimation = decimation;
            valid = false;

            start();
        }

        /**
         * Constructor
         * @param file video file
         */
        VideoDecoderForOpenCV(File file)   {
            this(file, 1);
        }

        /**
         * Test if video decoder is in valid states ready to output video.
         * @return true of force.
         */
        public boolean isValid() {
            return valid;
        }

        private void start() {
            setupSignal = new Object();
            mThread = new Thread(this);
            mThread.start();

            synchronized (setupSignal) {
                try {
                    setupSignal.wait();
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted when waiting for video decoder setup ready");
                }
            }
        }
        private void stop() {
            if (mThread != null) {
                mThread.interrupt();
                try {
                    mThread.join();
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted when waiting for video decoder thread to stop");
                }
                try {
                    decoder.stop();
                }catch (IllegalStateException e) {
                    Log.e(TAG, "Video decoder is not in a state that can be stopped");
                }
            }
            mThread = null;
        }

        void teardown() {
            if (decoder!=null) {
                decoder.release();
                decoder = null;
            }
            if (surface!=null) {
                surface.release();
                surface = null;
            }
            if (extractor!=null) {
                extractor.release();
                extractor = null;
            }
        }

        void setup() {
            int width=0, height=0;

            extractor = new MediaExtractor();

            try {
                extractor.setDataSource(mVideoFile.getPath());
            } catch (IOException e) {
                return;
            }

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                width = format.getInteger(MediaFormat.KEY_WIDTH);
                height = format.getInteger(MediaFormat.KEY_HEIGHT);

                if (mime.startsWith("video/")) {
                    extractor.selectTrack(i);
                    try {
                        decoder = MediaCodec.createDecoderByType(mime);
                    }catch (IOException e) {
                        continue;
                    }
                    // Decode to surface
                    //decoder.configure(format, surface, null, 0);

                    // Decode to offscreen surface
                    surface = new CtsMediaOutputSurface(width, height);
                    mMatBuffer = new MatBuffer(width, height);

                    decoder.configure(format, surface.getSurface(), null, 0);
                    break;
                }
            }

            if (decoder == null) {
                Log.e(TAG, "Can't find video info!");
                return;
            }
            valid = true;
        }

        @Override
        public void run() {
            setup();

            synchronized (setupSignal) {
                setupSignal.notify();
            }

            if (!valid) {
                return;
            }

            decoder.start();

            ByteBuffer[] inputBuffers = decoder.getInputBuffers();
            ByteBuffer[] outputBuffers = decoder.getOutputBuffers();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            boolean isEOS = false;
            long startMs = System.currentTimeMillis();
            long timeoutUs = 10000;

            int iframe = 0;
            long frameTimestamp = 0;

            while (!Thread.interrupted()) {
                if (!isEOS) {
                    int inIndex = decoder.dequeueInputBuffer(10000);
                    if (inIndex >= 0) {
                        ByteBuffer buffer = inputBuffers[inIndex];
                        int sampleSize = extractor.readSampleData(buffer, 0);
                        if (sampleSize < 0) {
                            if (LOCAL_LOGD) {
                                Log.d("VideoDecoderForOpenCV",
                                        "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                            }
                            decoder.queueInputBuffer(inIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            isEOS = true;
                        } else {
                            frameTimestamp = extractor.getSampleTime();
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, frameTimestamp, 0);
                            if (LOCAL_LOGD) {
                                Log.d(TAG, String.format("Frame %d sample time %f s",
                                            iframe, (double)frameTimestamp/1e6));
                            }
                            extractor.advance();
                        }
                    }
                }

                int outIndex = decoder.dequeueOutputBuffer(info, 10000);
                MediaFormat outFormat;
                switch (outIndex) {
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        if (LOCAL_LOGD) {
                            Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                        }
                        outputBuffers = decoder.getOutputBuffers();
                        break;
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        outFormat = decoder.getOutputFormat();
                        if (LOCAL_LOGD) {
                            Log.d(TAG, "New format " + outFormat);
                        }
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        if (LOCAL_LOGD) {
                            Log.d(TAG, "dequeueOutputBuffer timed out!");
                        }
                        break;
                    default:

                        ByteBuffer buffer = outputBuffers[outIndex];
                        boolean doRender = (info.size != 0);

                        // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                        // to SurfaceTexture to convert to a texture.  The API doesn't
                        // guarantee that the texture will be available before the call
                        // returns, so we need to wait for the onFrameAvailable callback to
                        // fire.  If we don't wait, we risk rendering from the previous frame.
                        decoder.releaseOutputBuffer(outIndex, doRender);

                        if (doRender) {
                            surface.awaitNewImage();
                            surface.drawImage();
                            if (LOCAL_LOGV) {
                                Log.v(TAG, "Finish drawing a frame!");
                            }
                            if ((iframe++ % mDecimation) == 0) {
                                //Send the frame for processing
                                mMatBuffer.put(frameTimestamp);
                            }
                        }
                        break;
                }

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (LOCAL_LOGD) {
                        Log.d("VideoDecoderForOpenCV", "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                    }
                    break;
                }
            }
            mMatBuffer.invalidate();

            decoder.stop();

            teardown();
            mThread = null;
        }


        /**
         * Get next valid frame
         * @return Frame in OpenCV mat
         */
        public Mat getFrame(long ts[]) {
            return mMatBuffer.get(ts);
        }

        /**
         * Get the size of the frame
         * @return size of the frame
         */
        Size getSize() {
            return mMatBuffer.getSize();
        }

        /**
         * A synchronized buffer
         */
        class MatBuffer {
            private Mat mat;
            private byte[] bytes;
            private ByteBuffer buf;
            private long timestamp;
            private boolean full;

            private int mWidth, mHeight;
            private boolean mValid = false;

            MatBuffer(int width, int height) {
                mWidth = width;
                mHeight = height;

                mat = new Mat(height, width, CvType.CV_8UC4); //RGBA
                buf = ByteBuffer.allocateDirect(width*height*4);
                bytes = new byte[width*height*4];
                timestamp = -1;

                mValid = true;
                full = false;
            }

            public synchronized void invalidate() {
                mValid = false;
                notifyAll();
            }

            public synchronized Mat get(long ts[]) {

                if (!mValid) return null;
                while (full == false) {
                    try {
                        wait();
                        if (!mValid) return null;
                    } catch (InterruptedException e) {
                        return null;
                    }
                }
                mat.put(0,0, bytes);
                full = false;
                notifyAll();
                ts[0] = timestamp;
                return mat;
            }

            public synchronized void put(long ts) {
                while (full) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Interrupted when waiting for space in buffer");
                    }
                }
                GLES20.glReadPixels(0, 0, mWidth, mHeight, GL10.GL_RGBA,
                        GL10.GL_UNSIGNED_BYTE, buf);
                buf.get(bytes);
                buf.rewind();

                timestamp = ts;
                full = true;
                notifyAll();
            }

            public Size getSize() {
                if (valid) {
                    return mat.size();
                }
                return new Size();
            }
        }
    }


    /* a small set of math functions */
    private static double [] quat2rpy( double [] q) {
        double [] rpy = {Math.atan2(2*(q[0]*q[1]+q[2]*q[3]), 1-2*(q[1]*q[1]+q[2]*q[2])),
                Math.asin(2*(q[0]*q[2] - q[3]*q[1])),
                Math.atan2(2*(q[0]*q[3]+q[1]*q[2]), 1-2*(q[2]*q[2]+q[3]*q[3]))};
        return rpy;
    }

    private static void quat2rpy( double [] q, double[] rpy) {
        rpy[0] = Math.atan2(2*(q[0]*q[1]+q[2]*q[3]), 1-2*(q[1]*q[1]+q[2]*q[2]));
        rpy[1] = Math.asin(2*(q[0]*q[2] - q[3]*q[1]));
        rpy[2] = Math.atan2(2*(q[0]*q[3]+q[1]*q[2]), 1-2*(q[2]*q[2]+q[3]*q[3]));
    }

    private static Mat quat2rpy(Mat quat) {
        double [] q = new double[4];
        quat.get(0,0,q);

        double [] rpy = {Math.atan2(2*(q[0]*q[1]+q[2]*q[3]), 1-2*(q[1]*q[1]+q[2]*q[2])),
                Math.asin(2*(q[0]*q[2] - q[3]*q[1])),
                Math.atan2(2*(q[0]*q[3]+q[1]*q[2]), 1-2*(q[2]*q[2]+q[3]*q[3]))};

        Mat rpym = new Mat(3,1, CvType.CV_64F);
        rpym.put(0,0, rpy);
        return rpym;
    }

    private static double [] rodr2quat( double [] r) {
        double t = Math.sqrt(r[0]*r[0]+r[1]*r[1]+r[2]*r[2]);
        double [] quat = {Math.cos(t/2), Math.sin(t/2)*r[0]/t,Math.sin(t/2)*r[1]/t,
                Math.sin(t/2)*r[2]/t};
        return quat;
    }

    private static void rodr2quat( double [] r, double [] quat) {
        double t = Math.sqrt(r[0]*r[0]+r[1]*r[1]+r[2]*r[2]);
        quat[0] = Math.cos(t/2);
        quat[1] = Math.sin(t/2)*r[0]/t;
        quat[2] = Math.sin(t/2)*r[1]/t;
        quat[3] = Math.sin(t/2)*r[2]/t;
    }

    private static Mat rodr2quat(Mat rodr) {
        double t = Core.norm(rodr);
        double [] r = new double[3];
        rodr.get(0,0,r);

        double [] quat = {Math.cos(t/2), Math.sin(t/2)*r[0]/t,Math.sin(t/2)*r[1]/t,
                Math.sin(t/2)*r[2]/t};
        Mat quatm = new Mat(4,1, CvType.CV_64F);
        quatm.put(0, 0, quat);
        return quatm;
    }

    private static double [] rodr2rpy( double [] r) {
        return quat2rpy(rodr2quat(r));
    }
    //////////////////

}
