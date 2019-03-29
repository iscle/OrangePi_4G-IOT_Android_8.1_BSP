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

package com.android.cts.verifier.camera.its;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.media.Image.Plane;
import android.net.Uri;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;

import com.android.ex.camera2.blocking.BlockingCameraManager;
import com.android.ex.camera2.blocking.BlockingCameraManager.BlockingOpenException;
import com.android.ex.camera2.blocking.BlockingStateCallback;
import com.android.ex.camera2.blocking.BlockingSessionCallback;

import com.android.cts.verifier.camera.its.StatsImage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ItsService extends Service implements SensorEventListener {
    public static final String TAG = ItsService.class.getSimpleName();

    // Timeouts, in seconds.
    private static final int TIMEOUT_CALLBACK = 20;
    private static final int TIMEOUT_3A = 10;

    // Time given for background requests to warm up pipeline
    private static final long PIPELINE_WARMUP_TIME_MS = 2000;

    // State transition timeouts, in ms.
    private static final long TIMEOUT_IDLE_MS = 2000;
    private static final long TIMEOUT_STATE_MS = 500;
    private static final long TIMEOUT_SESSION_CLOSE = 3000;

    // Timeout to wait for a capture result after the capture buffer has arrived, in ms.
    private static final long TIMEOUT_CAP_RES = 2000;

    private static final int MAX_CONCURRENT_READER_BUFFERS = 10;

    // Supports at most RAW+YUV+JPEG, one surface each, plus optional background stream
    private static final int MAX_NUM_OUTPUT_SURFACES = 4;

    public static final int SERVERPORT = 6000;

    public static final String REGION_KEY = "regions";
    public static final String REGION_AE_KEY = "ae";
    public static final String REGION_AWB_KEY = "awb";
    public static final String REGION_AF_KEY = "af";
    public static final String LOCK_AE_KEY = "aeLock";
    public static final String LOCK_AWB_KEY = "awbLock";
    public static final String TRIGGER_KEY = "triggers";
    public static final String TRIGGER_AE_KEY = "ae";
    public static final String TRIGGER_AF_KEY = "af";
    public static final String VIB_PATTERN_KEY = "pattern";
    public static final String EVCOMP_KEY = "evComp";

    private CameraManager mCameraManager = null;
    private HandlerThread mCameraThread = null;
    private Handler mCameraHandler = null;
    private BlockingCameraManager mBlockingCameraManager = null;
    private BlockingStateCallback mCameraListener = null;
    private CameraDevice mCamera = null;
    private CameraCaptureSession mSession = null;
    private ImageReader[] mOutputImageReaders = null;
    private ImageReader mInputImageReader = null;
    private CameraCharacteristics mCameraCharacteristics = null;

    private Vibrator mVibrator = null;

    private HandlerThread mSaveThreads[] = new HandlerThread[MAX_NUM_OUTPUT_SURFACES];
    private Handler mSaveHandlers[] = new Handler[MAX_NUM_OUTPUT_SURFACES];
    private HandlerThread mResultThread = null;
    private Handler mResultHandler = null;

    private volatile boolean mThreadExitFlag = false;

    private volatile ServerSocket mSocket = null;
    private volatile SocketRunnable mSocketRunnableObj = null;
    private Semaphore mSocketQueueQuota = null;
    private LinkedList<Integer> mInflightImageSizes = new LinkedList<>();
    private volatile BlockingQueue<ByteBuffer> mSocketWriteQueue =
            new LinkedBlockingDeque<ByteBuffer>();
    private final Object mSocketWriteEnqueueLock = new Object();
    private final Object mSocketWriteDrainLock = new Object();

    private volatile BlockingQueue<Object[]> mSerializerQueue =
            new LinkedBlockingDeque<Object[]>();

    private AtomicInteger mCountCallbacksRemaining = new AtomicInteger();
    private AtomicInteger mCountRawOrDng = new AtomicInteger();
    private AtomicInteger mCountRaw10 = new AtomicInteger();
    private AtomicInteger mCountRaw12 = new AtomicInteger();
    private AtomicInteger mCountJpg = new AtomicInteger();
    private AtomicInteger mCountYuv = new AtomicInteger();
    private AtomicInteger mCountCapRes = new AtomicInteger();
    private boolean mCaptureRawIsDng;
    private boolean mCaptureRawIsStats;
    private int mCaptureStatsGridWidth;
    private int mCaptureStatsGridHeight;
    private CaptureResult mCaptureResults[] = null;

    private volatile ConditionVariable mInterlock3A = new ConditionVariable(true);
    private volatile boolean mIssuedRequest3A = false;
    private volatile boolean mConvergedAE = false;
    private volatile boolean mConvergedAF = false;
    private volatile boolean mConvergedAWB = false;
    private volatile boolean mLockedAE = false;
    private volatile boolean mLockedAWB = false;
    private volatile boolean mNeedsLockedAE = false;
    private volatile boolean mNeedsLockedAWB = false;

    class MySensorEvent {
        public Sensor sensor;
        public int accuracy;
        public long timestamp;
        public float values[];
    }

    // For capturing motion sensor traces.
    private SensorManager mSensorManager = null;
    private Sensor mAccelSensor = null;
    private Sensor mMagSensor = null;
    private Sensor mGyroSensor = null;
    private volatile LinkedList<MySensorEvent> mEvents = null;
    private volatile Object mEventLock = new Object();
    private volatile boolean mEventsEnabled = false;
    private HandlerThread mSensorThread = null;
    private Handler mSensorHandler = null;

    public interface CaptureCallback {
        void onCaptureAvailable(Image capture);
    }

    public abstract class CaptureResultListener extends CameraCaptureSession.CaptureCallback {}

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        try {
            mThreadExitFlag = false;

            // Get handle to camera manager.
            mCameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
            if (mCameraManager == null) {
                throw new ItsException("Failed to connect to camera manager");
            }
            mBlockingCameraManager = new BlockingCameraManager(mCameraManager);
            mCameraListener = new BlockingStateCallback();

            // Register for motion events.
            mEvents = new LinkedList<MySensorEvent>();
            mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
            mAccelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mMagSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            mSensorThread = new HandlerThread("SensorThread");
            mSensorThread.start();
            mSensorHandler = new Handler(mSensorThread.getLooper());
            mSensorManager.registerListener(this, mAccelSensor,
                    SensorManager.SENSOR_DELAY_NORMAL, mSensorHandler);
            mSensorManager.registerListener(this, mMagSensor,
                    SensorManager.SENSOR_DELAY_NORMAL, mSensorHandler);
            mSensorManager.registerListener(this, mGyroSensor,
                    /*200hz*/5000, mSensorHandler);

            // Get a handle to the system vibrator.
            mVibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);

            // Create threads to receive images and save them.
            for (int i = 0; i < MAX_NUM_OUTPUT_SURFACES; i++) {
                mSaveThreads[i] = new HandlerThread("SaveThread" + i);
                mSaveThreads[i].start();
                mSaveHandlers[i] = new Handler(mSaveThreads[i].getLooper());
            }

            // Create a thread to handle object serialization.
            (new Thread(new SerializerRunnable())).start();;

            // Create a thread to receive capture results and process them.
            mResultThread = new HandlerThread("ResultThread");
            mResultThread.start();
            mResultHandler = new Handler(mResultThread.getLooper());

            // Create a thread for the camera device.
            mCameraThread = new HandlerThread("ItsCameraThread");
            mCameraThread.start();
            mCameraHandler = new Handler(mCameraThread.getLooper());

            // Create a thread to process commands, listening on a TCP socket.
            mSocketRunnableObj = new SocketRunnable();
            (new Thread(mSocketRunnableObj)).start();
        } catch (ItsException e) {
            Logt.e(TAG, "Service failed to start: ", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            // Just log a message indicating that the service is running and is able to accept
            // socket connections.
            while (!mThreadExitFlag && mSocket==null) {
                Thread.sleep(1);
            }
            if (!mThreadExitFlag){
                Logt.i(TAG, "ItsService ready");
            } else {
                Logt.e(TAG, "Starting ItsService in bad state");
            }
        } catch (java.lang.InterruptedException e) {
            Logt.e(TAG, "Error starting ItsService (interrupted)", e);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mThreadExitFlag = true;
        for (int i = 0; i < MAX_NUM_OUTPUT_SURFACES; i++) {
            if (mSaveThreads[i] != null) {
                mSaveThreads[i].quit();
                mSaveThreads[i] = null;
            }
        }
        if (mSensorThread != null) {
            mSensorThread.quitSafely();
            mSensorThread = null;
        }
        if (mResultThread != null) {
            mResultThread.quitSafely();
            mResultThread = null;
        }
        if (mCameraThread != null) {
            mCameraThread.quitSafely();
            mCameraThread = null;
        }
    }

    public void openCameraDevice(int cameraId) throws ItsException {
        Logt.i(TAG, String.format("Opening camera %d", cameraId));

        String[] devices;
        try {
            devices = mCameraManager.getCameraIdList();
            if (devices == null || devices.length == 0) {
                throw new ItsException("No camera devices");
            }
        } catch (CameraAccessException e) {
            throw new ItsException("Failed to get device ID list", e);
        }

        try {
            mCamera = mBlockingCameraManager.openCamera(devices[cameraId],
                    mCameraListener, mCameraHandler);
            mCameraCharacteristics = mCameraManager.getCameraCharacteristics(
                    devices[cameraId]);
            Size maxYuvSize = ItsUtils.getYuvOutputSizes(mCameraCharacteristics)[0];
            // 2 bytes per pixel for RGBA Bitmap and at least 3 Bitmaps per CDD
            int quota = maxYuvSize.getWidth() * maxYuvSize.getHeight() * 2 * 3;
            mSocketQueueQuota = new Semaphore(quota, true);
        } catch (CameraAccessException e) {
            throw new ItsException("Failed to open camera", e);
        } catch (BlockingOpenException e) {
            throw new ItsException("Failed to open camera (after blocking)", e);
        }
        mSocketRunnableObj.sendResponse("cameraOpened", "");
    }

    public void closeCameraDevice() throws ItsException {
        try {
            if (mCamera != null) {
                Logt.i(TAG, "Closing camera");
                mCamera.close();
                mCamera = null;
            }
        } catch (Exception e) {
            throw new ItsException("Failed to close device");
        }
        mSocketRunnableObj.sendResponse("cameraClosed", "");
    }

    class SerializerRunnable implements Runnable {
        // Use a separate thread to perform JSON serialization (since this can be slow due to
        // the reflection).
        @Override
        public void run() {
            Logt.i(TAG, "Serializer thread starting");
            while (! mThreadExitFlag) {
                try {
                    Object objs[] = mSerializerQueue.take();
                    JSONObject jsonObj = new JSONObject();
                    String tag = null;
                    for (int i = 0; i < objs.length; i++) {
                        Object obj = objs[i];
                        if (obj instanceof String) {
                            if (tag != null) {
                                throw new ItsException("Multiple tags for socket response");
                            }
                            tag = (String)obj;
                        } else if (obj instanceof CameraCharacteristics) {
                            jsonObj.put("cameraProperties", ItsSerializer.serialize(
                                    (CameraCharacteristics)obj));
                        } else if (obj instanceof CaptureRequest) {
                            jsonObj.put("captureRequest", ItsSerializer.serialize(
                                    (CaptureRequest)obj));
                        } else if (obj instanceof CaptureResult) {
                            jsonObj.put("captureResult", ItsSerializer.serialize(
                                    (CaptureResult)obj));
                        } else if (obj instanceof JSONArray) {
                            jsonObj.put("outputs", (JSONArray)obj);
                        } else {
                            throw new ItsException("Invalid object received for serialiation");
                        }
                    }
                    if (tag == null) {
                        throw new ItsException("No tag provided for socket response");
                    }
                    mSocketRunnableObj.sendResponse(tag, null, jsonObj, null);
                    Logt.i(TAG, String.format("Serialized %s", tag));
                } catch (org.json.JSONException e) {
                    Logt.e(TAG, "Error serializing object", e);
                    break;
                } catch (ItsException e) {
                    Logt.e(TAG, "Error serializing object", e);
                    break;
                } catch (java.lang.InterruptedException e) {
                    Logt.e(TAG, "Error serializing object (interrupted)", e);
                    break;
                }
            }
            Logt.i(TAG, "Serializer thread terminated");
        }
    }

    class SocketWriteRunnable implements Runnable {

        // Use a separate thread to service a queue of objects to be written to the socket,
        // writing each sequentially in order. This is needed since different handler functions
        // (called on different threads) will need to send data back to the host script.

        public Socket mOpenSocket = null;
        private Thread mThread = null;

        public SocketWriteRunnable(Socket openSocket) {
            mOpenSocket = openSocket;
        }

        public void setOpenSocket(Socket openSocket) {
            mOpenSocket = openSocket;
        }

        @Override
        public void run() {
            Logt.i(TAG, "Socket writer thread starting");
            while (true) {
                try {
                    ByteBuffer b = mSocketWriteQueue.take();
                    synchronized(mSocketWriteDrainLock) {
                        if (mOpenSocket == null) {
                            Logt.e(TAG, "No open socket connection!");
                            continue;
                        }
                        if (b.hasArray()) {
                            mOpenSocket.getOutputStream().write(b.array(), 0, b.capacity());
                        } else {
                            byte[] barray = new byte[b.capacity()];
                            b.get(barray);
                            mOpenSocket.getOutputStream().write(barray);
                        }
                        mOpenSocket.getOutputStream().flush();
                        Logt.i(TAG, String.format("Wrote to socket: %d bytes", b.capacity()));
                        Integer imgBufSize = mInflightImageSizes.peek();
                        if (imgBufSize != null && imgBufSize == b.capacity()) {
                            mInflightImageSizes.removeFirst();
                            if (mSocketQueueQuota != null) {
                                mSocketQueueQuota.release(imgBufSize);
                            }
                        }
                    }
                } catch (IOException e) {
                    Logt.e(TAG, "Error writing to socket", e);
                    mOpenSocket = null;
                    break;
                } catch (java.lang.InterruptedException e) {
                    Logt.e(TAG, "Error writing to socket (interrupted)", e);
                    mOpenSocket = null;
                    break;
                }
            }
            Logt.i(TAG, "Socket writer thread terminated");
        }

        public synchronized void checkAndStartThread() {
            if (mThread == null || mThread.getState() == Thread.State.TERMINATED) {
                mThread = new Thread(this);
            }
            if (mThread.getState() == Thread.State.NEW) {
                mThread.start();
            }
        }

    }

    class SocketRunnable implements Runnable {

        // Format of sent messages (over the socket):
        // * Serialized JSON object on a single line (newline-terminated)
        // * For byte buffers, the binary data then follows
        //
        // Format of received messages (from the socket):
        // * Serialized JSON object on a single line (newline-terminated)

        private Socket mOpenSocket = null;
        private SocketWriteRunnable mSocketWriteRunnable = null;

        @Override
        public void run() {
            Logt.i(TAG, "Socket thread starting");
            try {
                mSocket = new ServerSocket(SERVERPORT);
            } catch (IOException e) {
                Logt.e(TAG, "Failed to create socket", e);
            }

            // Create a new thread to handle writes to this socket.
            mSocketWriteRunnable = new SocketWriteRunnable(null);

            while (!mThreadExitFlag) {
                // Receive the socket-open request from the host.
                try {
                    Logt.i(TAG, "Waiting for client to connect to socket");
                    mOpenSocket = mSocket.accept();
                    if (mOpenSocket == null) {
                        Logt.e(TAG, "Socket connection error");
                        break;
                    }
                    mSocketWriteQueue.clear();
                    mInflightImageSizes.clear();
                    mSocketWriteRunnable.setOpenSocket(mOpenSocket);
                    mSocketWriteRunnable.checkAndStartThread();
                    Logt.i(TAG, "Socket connected");
                } catch (IOException e) {
                    Logt.e(TAG, "Socket open error: ", e);
                    break;
                }

                // Process commands over the open socket.
                while (!mThreadExitFlag) {
                    try {
                        BufferedReader input = new BufferedReader(
                                new InputStreamReader(mOpenSocket.getInputStream()));
                        if (input == null) {
                            Logt.e(TAG, "Failed to get socket input stream");
                            break;
                        }
                        String line = input.readLine();
                        if (line == null) {
                            Logt.i(TAG, "Socket readline retuned null (host disconnected)");
                            break;
                        }
                        processSocketCommand(line);
                    } catch (IOException e) {
                        Logt.e(TAG, "Socket read error: ", e);
                        break;
                    } catch (ItsException e) {
                        Logt.e(TAG, "Script error: ", e);
                        break;
                    }
                }

                // Close socket and go back to waiting for a new connection.
                try {
                    synchronized(mSocketWriteDrainLock) {
                        mSocketWriteQueue.clear();
                        mInflightImageSizes.clear();
                        mOpenSocket.close();
                        mOpenSocket = null;
                        mSocketWriteRunnable.setOpenSocket(null);
                        Logt.i(TAG, "Socket disconnected");
                    }
                } catch (java.io.IOException e) {
                    Logt.e(TAG, "Exception closing socket");
                }
            }

            // It's an overall error state if the code gets here; no recevery.
            // Try to do some cleanup, but the service probably needs to be restarted.
            Logt.i(TAG, "Socket server loop exited");
            mThreadExitFlag = true;
            try {
                synchronized(mSocketWriteDrainLock) {
                    if (mOpenSocket != null) {
                        mOpenSocket.close();
                        mOpenSocket = null;
                        mSocketWriteRunnable.setOpenSocket(null);
                    }
                }
            } catch (java.io.IOException e) {
                Logt.w(TAG, "Exception closing socket");
            }
            try {
                if (mSocket != null) {
                    mSocket.close();
                    mSocket = null;
                }
            } catch (java.io.IOException e) {
                Logt.w(TAG, "Exception closing socket");
            }
        }

        public void processSocketCommand(String cmd)
                throws ItsException {
            // Each command is a serialized JSON object.
            try {
                JSONObject cmdObj = new JSONObject(cmd);
                Logt.i(TAG, "Start processing command" + cmdObj.getString("cmdName"));
                if ("open".equals(cmdObj.getString("cmdName"))) {
                    int cameraId = cmdObj.getInt("cameraId");
                    openCameraDevice(cameraId);
                } else if ("close".equals(cmdObj.getString("cmdName"))) {
                    closeCameraDevice();
                } else if ("getCameraProperties".equals(cmdObj.getString("cmdName"))) {
                    doGetProps();
                } else if ("startSensorEvents".equals(cmdObj.getString("cmdName"))) {
                    doStartSensorEvents();
                } else if ("getSensorEvents".equals(cmdObj.getString("cmdName"))) {
                    doGetSensorEvents();
                } else if ("do3A".equals(cmdObj.getString("cmdName"))) {
                    do3A(cmdObj);
                } else if ("doCapture".equals(cmdObj.getString("cmdName"))) {
                    doCapture(cmdObj);
                } else if ("doVibrate".equals(cmdObj.getString("cmdName"))) {
                    doVibrate(cmdObj);
                } else if ("getCameraIds".equals(cmdObj.getString("cmdName"))) {
                    doGetCameraIds();
                } else if ("doReprocessCapture".equals(cmdObj.getString("cmdName"))) {
                    doReprocessCapture(cmdObj);
                } else {
                    throw new ItsException("Unknown command: " + cmd);
                }
                Logt.i(TAG, "Finish processing command" + cmdObj.getString("cmdName"));
            } catch (org.json.JSONException e) {
                Logt.e(TAG, "Invalid command: ", e);
            }
        }

        public void sendResponse(String tag, String str, JSONObject obj, ByteBuffer bbuf)
                throws ItsException {
            try {
                JSONObject jsonObj = new JSONObject();
                jsonObj.put("tag", tag);
                if (str != null) {
                    jsonObj.put("strValue", str);
                }
                if (obj != null) {
                    jsonObj.put("objValue", obj);
                }
                if (bbuf != null) {
                    jsonObj.put("bufValueSize", bbuf.capacity());
                }
                ByteBuffer bstr = ByteBuffer.wrap(
                        (jsonObj.toString()+"\n").getBytes(Charset.defaultCharset()));
                synchronized(mSocketWriteEnqueueLock) {
                    if (bstr != null) {
                        mSocketWriteQueue.put(bstr);
                    }
                    if (bbuf != null) {
                        mInflightImageSizes.add(bbuf.capacity());
                        mSocketWriteQueue.put(bbuf);
                    }
                }
            } catch (org.json.JSONException e) {
                throw new ItsException("JSON error: ", e);
            } catch (java.lang.InterruptedException e) {
                throw new ItsException("Socket error: ", e);
            }
        }

        public void sendResponse(String tag, String str)
                throws ItsException {
            sendResponse(tag, str, null, null);
        }

        public void sendResponse(String tag, JSONObject obj)
                throws ItsException {
            sendResponse(tag, null, obj, null);
        }

        public void sendResponseCaptureBuffer(String tag, ByteBuffer bbuf)
                throws ItsException {
            sendResponse(tag, null, null, bbuf);
        }

        public void sendResponse(LinkedList<MySensorEvent> events)
                throws ItsException {
            Logt.i(TAG, "Sending " + events.size() + " sensor events");
            try {
                JSONArray accels = new JSONArray();
                JSONArray mags = new JSONArray();
                JSONArray gyros = new JSONArray();
                for (MySensorEvent event : events) {
                    JSONObject obj = new JSONObject();
                    obj.put("time", event.timestamp);
                    obj.put("x", event.values[0]);
                    obj.put("y", event.values[1]);
                    obj.put("z", event.values[2]);
                    if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                        accels.put(obj);
                    } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                        mags.put(obj);
                    } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                        gyros.put(obj);
                    }
                }
                JSONObject obj = new JSONObject();
                obj.put("accel", accels);
                obj.put("mag", mags);
                obj.put("gyro", gyros);
                sendResponse("sensorEvents", null, obj, null);
            } catch (org.json.JSONException e) {
                throw new ItsException("JSON error: ", e);
            }
            Logt.i(TAG, "Sent sensor events");
        }

        public void sendResponse(CameraCharacteristics props)
                throws ItsException {
            try {
                Object objs[] = new Object[2];
                objs[0] = "cameraProperties";
                objs[1] = props;
                mSerializerQueue.put(objs);
            } catch (InterruptedException e) {
                throw new ItsException("Interrupted: ", e);
            }
        }

        public void sendResponseCaptureResult(CameraCharacteristics props,
                                              CaptureRequest request,
                                              CaptureResult result,
                                              ImageReader[] readers)
                throws ItsException {
            try {
                JSONArray jsonSurfaces = new JSONArray();
                for (int i = 0; i < readers.length; i++) {
                    JSONObject jsonSurface = new JSONObject();
                    jsonSurface.put("width", readers[i].getWidth());
                    jsonSurface.put("height", readers[i].getHeight());
                    int format = readers[i].getImageFormat();
                    if (format == ImageFormat.RAW_SENSOR) {
                        if (mCaptureRawIsStats) {
                            int aaw = ItsUtils.getActiveArrayCropRegion(mCameraCharacteristics)
                                              .width();
                            int aah = ItsUtils.getActiveArrayCropRegion(mCameraCharacteristics)
                                              .height();
                            jsonSurface.put("format", "rawStats");
                            jsonSurface.put("width", aaw/mCaptureStatsGridWidth);
                            jsonSurface.put("height", aah/mCaptureStatsGridHeight);
                        } else if (mCaptureRawIsDng) {
                            jsonSurface.put("format", "dng");
                        } else {
                            jsonSurface.put("format", "raw");
                        }
                    } else if (format == ImageFormat.RAW10) {
                        jsonSurface.put("format", "raw10");
                    } else if (format == ImageFormat.RAW12) {
                        jsonSurface.put("format", "raw12");
                    } else if (format == ImageFormat.JPEG) {
                        jsonSurface.put("format", "jpeg");
                    } else if (format == ImageFormat.YUV_420_888) {
                        jsonSurface.put("format", "yuv");
                    } else {
                        throw new ItsException("Invalid format");
                    }
                    jsonSurfaces.put(jsonSurface);
                }

                Object objs[] = new Object[5];
                objs[0] = "captureResults";
                objs[1] = props;
                objs[2] = request;
                objs[3] = result;
                objs[4] = jsonSurfaces;
                mSerializerQueue.put(objs);
            } catch (org.json.JSONException e) {
                throw new ItsException("JSON error: ", e);
            } catch (InterruptedException e) {
                throw new ItsException("Interrupted: ", e);
            }
        }
    }

    public ImageReader.OnImageAvailableListener
            createAvailableListener(final CaptureCallback listener) {
        return new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image i = null;
                try {
                    i = reader.acquireNextImage();
                    listener.onCaptureAvailable(i);
                } finally {
                    if (i != null) {
                        i.close();
                    }
                }
            }
        };
    }

    private ImageReader.OnImageAvailableListener
            createAvailableListenerDropper() {
        return new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image i = reader.acquireNextImage();
                i.close();
            }
        };
    }

    private void doStartSensorEvents() throws ItsException {
        synchronized(mEventLock) {
            mEventsEnabled = true;
        }
        mSocketRunnableObj.sendResponse("sensorEventsStarted", "");
    }

    private void doGetSensorEvents() throws ItsException {
        synchronized(mEventLock) {
            mSocketRunnableObj.sendResponse(mEvents);
            mEvents.clear();
            mEventsEnabled = false;
        }
    }

    private void doGetProps() throws ItsException {
        mSocketRunnableObj.sendResponse(mCameraCharacteristics);
    }

    private void doGetCameraIds() throws ItsException {
        String[] devices;
        try {
            devices = mCameraManager.getCameraIdList();
            if (devices == null || devices.length == 0) {
                throw new ItsException("No camera devices");
            }
        } catch (CameraAccessException e) {
            throw new ItsException("Failed to get device ID list", e);
        }

        try {
            JSONObject obj = new JSONObject();
            JSONArray array = new JSONArray();
            for (String id : devices) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(id);
                // Only supply camera Id for non-legacy cameras since legacy camera does not
                // support ITS
                if (characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) !=
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                    array.put(id);
                }
            }
            obj.put("cameraIdArray", array);
            mSocketRunnableObj.sendResponse("cameraIds", obj);
        } catch (org.json.JSONException e) {
            throw new ItsException("JSON error: ", e);
        } catch (android.hardware.camera2.CameraAccessException e) {
            throw new ItsException("Access error: ", e);
        }
    }

    private void prepareImageReaders(Size[] outputSizes, int[] outputFormats, Size inputSize,
            int inputFormat, int maxInputBuffers) {
        closeImageReaders();
        mOutputImageReaders = new ImageReader[outputSizes.length];
        for (int i = 0; i < outputSizes.length; i++) {
            // Check if the output image reader can be shared with the input image reader.
            if (outputSizes[i].equals(inputSize) && outputFormats[i] == inputFormat) {
                mOutputImageReaders[i] = ImageReader.newInstance(outputSizes[i].getWidth(),
                        outputSizes[i].getHeight(), outputFormats[i],
                        MAX_CONCURRENT_READER_BUFFERS + maxInputBuffers);
                mInputImageReader = mOutputImageReaders[i];
            } else {
                mOutputImageReaders[i] = ImageReader.newInstance(outputSizes[i].getWidth(),
                        outputSizes[i].getHeight(), outputFormats[i],
                        MAX_CONCURRENT_READER_BUFFERS);
            }
        }

        if (inputSize != null && mInputImageReader == null) {
            mInputImageReader = ImageReader.newInstance(inputSize.getWidth(), inputSize.getHeight(),
                    inputFormat, maxInputBuffers);
        }
    }

    private void closeImageReaders() {
        if (mOutputImageReaders != null) {
            for (int i = 0; i < mOutputImageReaders.length; i++) {
                if (mOutputImageReaders[i] != null) {
                    mOutputImageReaders[i].close();
                    mOutputImageReaders[i] = null;
                }
            }
        }
        if (mInputImageReader != null) {
            mInputImageReader.close();
            mInputImageReader = null;
        }
    }

    private void do3A(JSONObject params) throws ItsException {
        try {
            // Start a 3A action, and wait for it to converge.
            // Get the converged values for each "A", and package into JSON result for caller.

            // 3A happens on full-res frames.
            Size sizes[] = ItsUtils.getYuvOutputSizes(mCameraCharacteristics);
            int outputFormats[] = new int[1];
            outputFormats[0] = ImageFormat.YUV_420_888;
            Size[] outputSizes = new Size[1];
            outputSizes[0] = sizes[0];
            int width = outputSizes[0].getWidth();
            int height = outputSizes[0].getHeight();

            prepareImageReaders(outputSizes, outputFormats, /*inputSize*/null, /*inputFormat*/0,
                    /*maxInputBuffers*/0);
            List<Surface> outputSurfaces = new ArrayList<Surface>(1);
            outputSurfaces.add(mOutputImageReaders[0].getSurface());
            BlockingSessionCallback sessionListener = new BlockingSessionCallback();
            mCamera.createCaptureSession(outputSurfaces, sessionListener, mCameraHandler);
            mSession = sessionListener.waitAndGetSession(TIMEOUT_IDLE_MS);

            // Add a listener that just recycles buffers; they aren't saved anywhere.
            ImageReader.OnImageAvailableListener readerListener =
                    createAvailableListenerDropper();
            mOutputImageReaders[0].setOnImageAvailableListener(readerListener, mSaveHandlers[0]);

            // Get the user-specified regions for AE, AWB, AF.
            // Note that the user specifies normalized [x,y,w,h], which is converted below
            // to an [x0,y0,x1,y1] region in sensor coords. The capture request region
            // also has a fifth "weight" element: [x0,y0,x1,y1,w].
            MeteringRectangle[] regionAE = new MeteringRectangle[]{
                    new MeteringRectangle(0,0,width,height,1)};
            MeteringRectangle[] regionAF = new MeteringRectangle[]{
                    new MeteringRectangle(0,0,width,height,1)};
            MeteringRectangle[] regionAWB = new MeteringRectangle[]{
                    new MeteringRectangle(0,0,width,height,1)};
            if (params.has(REGION_KEY)) {
                JSONObject regions = params.getJSONObject(REGION_KEY);
                if (regions.has(REGION_AE_KEY)) {
                    regionAE = ItsUtils.getJsonWeightedRectsFromArray(
                            regions.getJSONArray(REGION_AE_KEY), true, width, height);
                }
                if (regions.has(REGION_AF_KEY)) {
                    regionAF = ItsUtils.getJsonWeightedRectsFromArray(
                            regions.getJSONArray(REGION_AF_KEY), true, width, height);
                }
                if (regions.has(REGION_AWB_KEY)) {
                    regionAWB = ItsUtils.getJsonWeightedRectsFromArray(
                            regions.getJSONArray(REGION_AWB_KEY), true, width, height);
                }
            }

            // If AE or AWB lock is specified, then the 3A will converge first and then lock these
            // values, waiting until the HAL has reported that the lock was successful.
            mNeedsLockedAE = params.optBoolean(LOCK_AE_KEY, false);
            mNeedsLockedAWB = params.optBoolean(LOCK_AWB_KEY, false);

            // An EV compensation can be specified as part of AE convergence.
            int evComp = params.optInt(EVCOMP_KEY, 0);
            if (evComp != 0) {
                Logt.i(TAG, String.format("Running 3A with AE exposure compensation value: %d", evComp));
            }

            // By default, AE and AF both get triggered, but the user can optionally override this.
            // Also, AF won't get triggered if the lens is fixed-focus.
            boolean doAE = true;
            boolean doAF = true;
            if (params.has(TRIGGER_KEY)) {
                JSONObject triggers = params.getJSONObject(TRIGGER_KEY);
                if (triggers.has(TRIGGER_AE_KEY)) {
                    doAE = triggers.getBoolean(TRIGGER_AE_KEY);
                }
                if (triggers.has(TRIGGER_AF_KEY)) {
                    doAF = triggers.getBoolean(TRIGGER_AF_KEY);
                }
            }
            Float minFocusDistance = mCameraCharacteristics.get(
                    CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
            boolean isFixedFocusLens = minFocusDistance != null && minFocusDistance == 0.0;
            if (doAF && isFixedFocusLens) {
                // Send a dummy result back for the code that is waiting for this message to see
                // that AF has converged.
                Logt.i(TAG, "Ignoring request for AF on fixed-focus camera");
                mSocketRunnableObj.sendResponse("afResult", "0.0");
                doAF = false;
            }

            mInterlock3A.open();
            mIssuedRequest3A = false;
            mConvergedAE = false;
            mConvergedAWB = false;
            mConvergedAF = false;
            mLockedAE = false;
            mLockedAWB = false;
            long tstart = System.currentTimeMillis();
            boolean triggeredAE = false;
            boolean triggeredAF = false;

            Logt.i(TAG, String.format("Initiating 3A: AE:%d, AF:%d, AWB:1, AELOCK:%d, AWBLOCK:%d",
                    doAE?1:0, doAF?1:0, mNeedsLockedAE?1:0, mNeedsLockedAWB?1:0));

            // Keep issuing capture requests until 3A has converged.
            while (true) {

                // Block until can take the next 3A frame. Only want one outstanding frame
                // at a time, to simplify the logic here.
                if (!mInterlock3A.block(TIMEOUT_3A * 1000) ||
                        System.currentTimeMillis() - tstart > TIMEOUT_3A * 1000) {
                    throw new ItsException(
                            "3A failed to converge after " + TIMEOUT_3A + " seconds.\n" +
                            "AE converge state: " + mConvergedAE + ", \n" +
                            "AF convergence state: " + mConvergedAF + ", \n" +
                            "AWB convergence state: " + mConvergedAWB + ".");
                }
                mInterlock3A.close();

                // If not converged yet, issue another capture request.
                if (       (doAE && (!triggeredAE || !mConvergedAE))
                        || !mConvergedAWB
                        || (doAF && (!triggeredAF || !mConvergedAF))
                        || (doAE && mNeedsLockedAE && !mLockedAE)
                        || (mNeedsLockedAWB && !mLockedAWB)) {

                    // Baseline capture request for 3A.
                    CaptureRequest.Builder req = mCamera.createCaptureRequest(
                            CameraDevice.TEMPLATE_PREVIEW);
                    req.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                    req.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                    req.set(CaptureRequest.CONTROL_CAPTURE_INTENT,
                            CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW);
                    req.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON);
                    req.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
                    req.set(CaptureRequest.CONTROL_AE_LOCK, false);
                    req.set(CaptureRequest.CONTROL_AE_REGIONS, regionAE);
                    req.set(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_AUTO);
                    req.set(CaptureRequest.CONTROL_AF_REGIONS, regionAF);
                    req.set(CaptureRequest.CONTROL_AWB_MODE,
                            CaptureRequest.CONTROL_AWB_MODE_AUTO);
                    req.set(CaptureRequest.CONTROL_AWB_LOCK, false);
                    req.set(CaptureRequest.CONTROL_AWB_REGIONS, regionAWB);
                    // ITS only turns OIS on when it's explicitly requested
                    req.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                            CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);

                    if (evComp != 0) {
                        req.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, evComp);
                    }

                    if (mConvergedAE && mNeedsLockedAE) {
                        req.set(CaptureRequest.CONTROL_AE_LOCK, true);
                    }
                    if (mConvergedAWB && mNeedsLockedAWB) {
                        req.set(CaptureRequest.CONTROL_AWB_LOCK, true);
                    }

                    // Trigger AE first.
                    if (doAE && !triggeredAE) {
                        Logt.i(TAG, "Triggering AE");
                        req.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                        triggeredAE = true;
                    }

                    // After AE has converged, trigger AF.
                    if (doAF && !triggeredAF && (!doAE || (triggeredAE && mConvergedAE))) {
                        Logt.i(TAG, "Triggering AF");
                        req.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CaptureRequest.CONTROL_AF_TRIGGER_START);
                        triggeredAF = true;
                    }

                    req.addTarget(mOutputImageReaders[0].getSurface());

                    mIssuedRequest3A = true;
                    mSession.capture(req.build(), mCaptureResultListener, mResultHandler);
                } else {
                    mSocketRunnableObj.sendResponse("3aConverged", "");
                    Logt.i(TAG, "3A converged");
                    break;
                }
            }
        } catch (android.hardware.camera2.CameraAccessException e) {
            throw new ItsException("Access error: ", e);
        } catch (org.json.JSONException e) {
            throw new ItsException("JSON error: ", e);
        } finally {
            mSocketRunnableObj.sendResponse("3aDone", "");
        }
    }

    private void doVibrate(JSONObject params) throws ItsException {
        try {
            if (mVibrator == null) {
                throw new ItsException("Unable to start vibrator");
            }
            JSONArray patternArray = params.getJSONArray(VIB_PATTERN_KEY);
            int len = patternArray.length();
            long pattern[] = new long[len];
            for (int i = 0; i < len; i++) {
                pattern[i] = patternArray.getLong(i);
            }
            Logt.i(TAG, String.format("Starting vibrator, pattern length %d",len));
            mVibrator.vibrate(pattern, -1);
            mSocketRunnableObj.sendResponse("vibrationStarted", "");
        } catch (org.json.JSONException e) {
            throw new ItsException("JSON error: ", e);
        }
    }

    /**
     * Parse jsonOutputSpecs to get output surface sizes and formats. Create input and output
     * image readers for the parsed output surface sizes, output formats, and the given input
     * size and format.
     */
    private void prepareImageReadersWithOutputSpecs(JSONArray jsonOutputSpecs, Size inputSize,
            int inputFormat, int maxInputBuffers, boolean backgroundRequest) throws ItsException {
        Size outputSizes[];
        int outputFormats[];
        int numSurfaces = 0;

        if (jsonOutputSpecs != null) {
            try {
                numSurfaces = jsonOutputSpecs.length();
                if (backgroundRequest) {
                    numSurfaces += 1;
                }
                if (numSurfaces > MAX_NUM_OUTPUT_SURFACES) {
                    throw new ItsException("Too many output surfaces");
                }

                outputSizes = new Size[numSurfaces];
                outputFormats = new int[numSurfaces];
                for (int i = 0; i < numSurfaces; i++) {
                    // Append optional background stream at the end
                    if (backgroundRequest && i == numSurfaces - 1) {
                        outputFormats[i] = ImageFormat.YUV_420_888;
                        outputSizes[i] = new Size(640, 480);
                        continue;
                    }
                    // Get the specified surface.
                    JSONObject surfaceObj = jsonOutputSpecs.getJSONObject(i);
                    String sformat = surfaceObj.optString("format");
                    Size sizes[];
                    if ("yuv".equals(sformat) || "".equals(sformat)) {
                        // Default to YUV if no format is specified.
                        outputFormats[i] = ImageFormat.YUV_420_888;
                        sizes = ItsUtils.getYuvOutputSizes(mCameraCharacteristics);
                    } else if ("jpg".equals(sformat) || "jpeg".equals(sformat)) {
                        outputFormats[i] = ImageFormat.JPEG;
                        sizes = ItsUtils.getJpegOutputSizes(mCameraCharacteristics);
                    } else if ("raw".equals(sformat)) {
                        outputFormats[i] = ImageFormat.RAW_SENSOR;
                        sizes = ItsUtils.getRaw16OutputSizes(mCameraCharacteristics);
                    } else if ("raw10".equals(sformat)) {
                        outputFormats[i] = ImageFormat.RAW10;
                        sizes = ItsUtils.getRaw10OutputSizes(mCameraCharacteristics);
                    } else if ("raw12".equals(sformat)) {
                        outputFormats[i] = ImageFormat.RAW12;
                        sizes = ItsUtils.getRaw12OutputSizes(mCameraCharacteristics);
                    } else if ("dng".equals(sformat)) {
                        outputFormats[i] = ImageFormat.RAW_SENSOR;
                        sizes = ItsUtils.getRaw16OutputSizes(mCameraCharacteristics);
                        mCaptureRawIsDng = true;
                    } else if ("rawStats".equals(sformat)) {
                        outputFormats[i] = ImageFormat.RAW_SENSOR;
                        sizes = ItsUtils.getRaw16OutputSizes(mCameraCharacteristics);
                        mCaptureRawIsStats = true;
                        mCaptureStatsGridWidth = surfaceObj.optInt("gridWidth");
                        mCaptureStatsGridHeight = surfaceObj.optInt("gridHeight");
                    } else {
                        throw new ItsException("Unsupported format: " + sformat);
                    }
                    // If the size is omitted, then default to the largest allowed size for the
                    // format.
                    int width = surfaceObj.optInt("width");
                    int height = surfaceObj.optInt("height");
                    if (width <= 0) {
                        if (sizes == null || sizes.length == 0) {
                            throw new ItsException(String.format(
                                    "Zero stream configs available for requested format: %s",
                                    sformat));
                        }
                        width = ItsUtils.getMaxSize(sizes).getWidth();
                    }
                    if (height <= 0) {
                        height = ItsUtils.getMaxSize(sizes).getHeight();
                    }

                    // The stats computation only applies to the active array region.
                    int aaw = ItsUtils.getActiveArrayCropRegion(mCameraCharacteristics).width();
                    int aah = ItsUtils.getActiveArrayCropRegion(mCameraCharacteristics).height();
                    if (mCaptureStatsGridWidth <= 0 || mCaptureStatsGridWidth > aaw) {
                        mCaptureStatsGridWidth = aaw;
                    }
                    if (mCaptureStatsGridHeight <= 0 || mCaptureStatsGridHeight > aah) {
                        mCaptureStatsGridHeight = aah;
                    }

                    outputSizes[i] = new Size(width, height);
                }
            } catch (org.json.JSONException e) {
                throw new ItsException("JSON error", e);
            }
        } else {
            // No surface(s) specified at all.
            // Default: a single output surface which is full-res YUV.
            Size maxYuvSize = ItsUtils.getMaxOutputSize(
                    mCameraCharacteristics, ImageFormat.YUV_420_888);
            numSurfaces = backgroundRequest ? 2 : 1;

            outputSizes = new Size[numSurfaces];
            outputFormats = new int[numSurfaces];
            outputSizes[0] = maxYuvSize;
            outputFormats[0] = ImageFormat.YUV_420_888;
            if (backgroundRequest) {
                outputSizes[1] = new Size(640, 480);
                outputFormats[1] = ImageFormat.YUV_420_888;
            }
        }

        prepareImageReaders(outputSizes, outputFormats, inputSize, inputFormat, maxInputBuffers);
    }

    /**
     * Wait until mCountCallbacksRemaining is 0 or a specified amount of time has elapsed between
     * each callback.
     */
    private void waitForCallbacks(long timeoutMs) throws ItsException {
        synchronized(mCountCallbacksRemaining) {
            int currentCount = mCountCallbacksRemaining.get();
            while (currentCount > 0) {
                try {
                    mCountCallbacksRemaining.wait(timeoutMs);
                } catch (InterruptedException e) {
                    throw new ItsException("Waiting for callbacks was interrupted.", e);
                }

                int newCount = mCountCallbacksRemaining.get();
                if (newCount == currentCount) {
                    throw new ItsException("No callback received within timeout");
                }
                currentCount = newCount;
            }
        }
    }

    private void doCapture(JSONObject params) throws ItsException {
        try {
            // Parse the JSON to get the list of capture requests.
            List<CaptureRequest.Builder> requests = ItsSerializer.deserializeRequestList(
                    mCamera, params, "captureRequests");

            // optional background preview requests
            List<CaptureRequest.Builder> backgroundRequests = ItsSerializer.deserializeRequestList(
                    mCamera, params, "repeatRequests");
            boolean backgroundRequest = backgroundRequests.size() > 0;

            int numSurfaces = 0;
            int numCaptureSurfaces = 0;
            BlockingSessionCallback sessionListener = new BlockingSessionCallback();
            try {
                mCountRawOrDng.set(0);
                mCountJpg.set(0);
                mCountYuv.set(0);
                mCountRaw10.set(0);
                mCountRaw12.set(0);
                mCountCapRes.set(0);
                mCaptureRawIsDng = false;
                mCaptureRawIsStats = false;
                mCaptureResults = new CaptureResult[requests.size()];

                JSONArray jsonOutputSpecs = ItsUtils.getOutputSpecs(params);

                prepareImageReadersWithOutputSpecs(jsonOutputSpecs, /*inputSize*/null,
                        /*inputFormat*/0, /*maxInputBuffers*/0, backgroundRequest);
                numSurfaces = mOutputImageReaders.length;
                numCaptureSurfaces = numSurfaces - (backgroundRequest ? 1 : 0);

                List<Surface> outputSurfaces = new ArrayList<Surface>(numSurfaces);
                for (int i = 0; i < numSurfaces; i++) {
                    outputSurfaces.add(mOutputImageReaders[i].getSurface());
                }
                mCamera.createCaptureSession(outputSurfaces, sessionListener, mCameraHandler);
                mSession = sessionListener.waitAndGetSession(TIMEOUT_IDLE_MS);

                for (int i = 0; i < numSurfaces; i++) {
                    ImageReader.OnImageAvailableListener readerListener;
                    if (backgroundRequest && i == numSurfaces - 1) {
                        readerListener = createAvailableListenerDropper();
                    } else {
                        readerListener = createAvailableListener(mCaptureCallback);
                    }
                    mOutputImageReaders[i].setOnImageAvailableListener(readerListener,
                            mSaveHandlers[i]);
                }

                // Plan for how many callbacks need to be received throughout the duration of this
                // sequence of capture requests. There is one callback per image surface, and one
                // callback for the CaptureResult, for each capture.
                int numCaptures = requests.size();
                mCountCallbacksRemaining.set(numCaptures * (numCaptureSurfaces + 1));

            } catch (CameraAccessException e) {
                throw new ItsException("Error configuring outputs", e);
            }

            // Start background requests and let it warm up pipeline
            if (backgroundRequest) {
                List<CaptureRequest> bgRequestList =
                        new ArrayList<CaptureRequest>(backgroundRequests.size());
                for (int i = 0; i < backgroundRequests.size(); i++) {
                    CaptureRequest.Builder req = backgroundRequests.get(i);
                    req.addTarget(mOutputImageReaders[numCaptureSurfaces].getSurface());
                    bgRequestList.add(req.build());
                }
                mSession.setRepeatingBurst(bgRequestList, null, null);
                // warm up the pipeline
                Thread.sleep(PIPELINE_WARMUP_TIME_MS);
            }

            // Initiate the captures.
            long maxExpTimeNs = -1;
            List<CaptureRequest> requestList =
                    new ArrayList<>(requests.size());
            for (int i = 0; i < requests.size(); i++) {
                CaptureRequest.Builder req = requests.get(i);
                // For DNG captures, need the LSC map to be available.
                if (mCaptureRawIsDng) {
                    req.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, 1);
                }
                Long expTimeNs = req.get(CaptureRequest.SENSOR_EXPOSURE_TIME);
                if (expTimeNs != null && expTimeNs > maxExpTimeNs) {
                    maxExpTimeNs = expTimeNs;
                }

                for (int j = 0; j < numCaptureSurfaces; j++) {
                    req.addTarget(mOutputImageReaders[j].getSurface());
                }
                requestList.add(req.build());
            }
            mSession.captureBurst(requestList, mCaptureResultListener, mResultHandler);

            long timeout = TIMEOUT_CALLBACK * 1000;
            if (maxExpTimeNs > 0) {
                timeout += maxExpTimeNs / 1000000; // ns to ms
            }
            // Make sure all callbacks have been hit (wait until captures are done).
            // If no timeouts are received after a timeout, then fail.
            waitForCallbacks(timeout);

            // Close session and wait until session is fully closed
            mSession.close();
            sessionListener.getStateWaiter().waitForState(
                    BlockingSessionCallback.SESSION_CLOSED, TIMEOUT_SESSION_CLOSE);

        } catch (android.hardware.camera2.CameraAccessException e) {
            throw new ItsException("Access error: ", e);
        } catch (InterruptedException e) {
            throw new ItsException("Unexpected InterruptedException: ", e);
        }
    }

    /**
     * Perform reprocess captures.
     *
     * It takes captureRequests in a JSON object and perform capture requests in two steps:
     * regular capture request to get reprocess input and reprocess capture request to get
     * reprocess outputs.
     *
     * Regular capture requests:
     *   1. For each capture request in the JSON object, create a full-size capture request with
     *      the settings in the JSON object.
     *   2. Remember and clear noise reduction, edge enhancement, and effective exposure factor
     *      from the regular capture requests. (Those settings will be used for reprocess requests.)
     *   3. Submit the regular capture requests.
     *
     * Reprocess capture requests:
     *   4. Wait for the regular capture results and use them to create reprocess capture requests.
     *   5. Wait for the regular capture output images and queue them to the image writer.
     *   6. Set the noise reduction, edge enhancement, and effective exposure factor from #2.
     *   7. Submit the reprocess capture requests.
     *
     * The output images and results for the regular capture requests won't be written to socket.
     * The output images and results for the reprocess capture requests will be written to socket.
     */
    private void doReprocessCapture(JSONObject params) throws ItsException {
        ImageWriter imageWriter = null;
        ArrayList<Integer> noiseReductionModes = new ArrayList<>();
        ArrayList<Integer> edgeModes = new ArrayList<>();
        ArrayList<Float> effectiveExposureFactors = new ArrayList<>();

        mCountRawOrDng.set(0);
        mCountJpg.set(0);
        mCountYuv.set(0);
        mCountRaw10.set(0);
        mCountRaw12.set(0);
        mCountCapRes.set(0);
        mCaptureRawIsDng = false;
        mCaptureRawIsStats = false;

        try {
            // Parse the JSON to get the list of capture requests.
            List<CaptureRequest.Builder> inputRequests =
                    ItsSerializer.deserializeRequestList(mCamera, params, "captureRequests");

            // Prepare the image readers for reprocess input and reprocess outputs.
            int inputFormat = getReprocessInputFormat(params);
            Size inputSize = ItsUtils.getMaxOutputSize(mCameraCharacteristics, inputFormat);
            JSONArray jsonOutputSpecs = ItsUtils.getOutputSpecs(params);
            prepareImageReadersWithOutputSpecs(jsonOutputSpecs, inputSize, inputFormat,
                    inputRequests.size(), /*backgroundRequest*/false);

            // Prepare a reprocessable session.
            int numOutputSurfaces = mOutputImageReaders.length;
            InputConfiguration inputConfig = new InputConfiguration(inputSize.getWidth(),
                    inputSize.getHeight(), inputFormat);
            List<Surface> outputSurfaces = new ArrayList<Surface>();
            boolean addSurfaceForInput = true;
            for (int i = 0; i < numOutputSurfaces; i++) {
                outputSurfaces.add(mOutputImageReaders[i].getSurface());
                if (mOutputImageReaders[i] == mInputImageReader) {
                    // If input and one of the outputs share the same image reader, avoid
                    // adding the same surfaces twice.
                    addSurfaceForInput = false;
                }
            }

            if (addSurfaceForInput) {
                // Besides the output surfaces specified in JSON object, add an additional one
                // for reprocess input.
                outputSurfaces.add(mInputImageReader.getSurface());
            }

            BlockingSessionCallback sessionListener = new BlockingSessionCallback();
            mCamera.createReprocessableCaptureSession(inputConfig, outputSurfaces, sessionListener,
                    mCameraHandler);
            mSession = sessionListener.waitAndGetSession(TIMEOUT_IDLE_MS);

            // Create an image writer for reprocess input.
            Surface inputSurface = mSession.getInputSurface();
            imageWriter = ImageWriter.newInstance(inputSurface, inputRequests.size());

            // Set up input reader listener and capture callback listener to get
            // reprocess input buffers and the results in order to create reprocess capture
            // requests.
            ImageReaderListenerWaiter inputReaderListener = new ImageReaderListenerWaiter();
            mInputImageReader.setOnImageAvailableListener(inputReaderListener, mSaveHandlers[0]);

            CaptureCallbackWaiter captureCallbackWaiter = new CaptureCallbackWaiter();
            // Prepare the reprocess input request
            for (CaptureRequest.Builder inputReqest : inputRequests) {
                // Remember and clear noise reduction, edge enhancement, and effective exposure
                // factors.
                noiseReductionModes.add(inputReqest.get(CaptureRequest.NOISE_REDUCTION_MODE));
                edgeModes.add(inputReqest.get(CaptureRequest.EDGE_MODE));
                effectiveExposureFactors.add(inputReqest.get(
                        CaptureRequest.REPROCESS_EFFECTIVE_EXPOSURE_FACTOR));

                inputReqest.set(CaptureRequest.NOISE_REDUCTION_MODE,
                        CaptureRequest.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG);
                inputReqest.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_ZERO_SHUTTER_LAG);
                inputReqest.set(CaptureRequest.REPROCESS_EFFECTIVE_EXPOSURE_FACTOR, null);
                inputReqest.addTarget(mInputImageReader.getSurface());
                mSession.capture(inputReqest.build(), captureCallbackWaiter, mResultHandler);
            }

            // Wait for reprocess input images
            ArrayList<CaptureRequest.Builder> reprocessOutputRequests = new ArrayList<>();
            for (int i = 0; i < inputRequests.size(); i++) {
                TotalCaptureResult result =
                        captureCallbackWaiter.getResult(TIMEOUT_CALLBACK * 1000);
                reprocessOutputRequests.add(mCamera.createReprocessCaptureRequest(result));
                imageWriter.queueInputImage(inputReaderListener.getImage(TIMEOUT_CALLBACK * 1000));
            }

            // Start performing reprocess captures.

            mCaptureResults = new CaptureResult[inputRequests.size()];

            // Prepare reprocess capture requests.
            for (int i = 0; i < numOutputSurfaces; i++) {
                ImageReader.OnImageAvailableListener outputReaderListener =
                        createAvailableListener(mCaptureCallback);
                mOutputImageReaders[i].setOnImageAvailableListener(outputReaderListener,
                        mSaveHandlers[i]);
            }

            // Plan for how many callbacks need to be received throughout the duration of this
            // sequence of capture requests. There is one callback per image surface, and one
            // callback for the CaptureResult, for each capture.
            int numCaptures = reprocessOutputRequests.size();
            mCountCallbacksRemaining.set(numCaptures * (numOutputSurfaces + 1));

            // Initiate the captures.
            for (int i = 0; i < reprocessOutputRequests.size(); i++) {
                CaptureRequest.Builder req = reprocessOutputRequests.get(i);
                for (ImageReader outputImageReader : mOutputImageReaders) {
                    req.addTarget(outputImageReader.getSurface());
                }

                req.set(CaptureRequest.NOISE_REDUCTION_MODE, noiseReductionModes.get(i));
                req.set(CaptureRequest.EDGE_MODE, edgeModes.get(i));
                req.set(CaptureRequest.REPROCESS_EFFECTIVE_EXPOSURE_FACTOR,
                        effectiveExposureFactors.get(i));

                mSession.capture(req.build(), mCaptureResultListener, mResultHandler);
            }

            // Make sure all callbacks have been hit (wait until captures are done).
            // If no timeouts are received after a timeout, then fail.
            waitForCallbacks(TIMEOUT_CALLBACK * 1000);
        } catch (android.hardware.camera2.CameraAccessException e) {
            throw new ItsException("Access error: ", e);
        } finally {
            closeImageReaders();
            if (mSession != null) {
                mSession.close();
                mSession = null;
            }
            if (imageWriter != null) {
                imageWriter.close();
            }
        }
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        Logt.i(TAG, "Sensor " + sensor.getName() + " accuracy changed to " + accuracy);
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        synchronized(mEventLock) {
            if (mEventsEnabled) {
                MySensorEvent ev2 = new MySensorEvent();
                ev2.sensor = event.sensor;
                ev2.accuracy = event.accuracy;
                ev2.timestamp = event.timestamp;
                ev2.values = new float[event.values.length];
                System.arraycopy(event.values, 0, ev2.values, 0, event.values.length);
                mEvents.add(ev2);
            }
        }
    }

    private final CaptureCallback mCaptureCallback = new CaptureCallback() {
        @Override
        public void onCaptureAvailable(Image capture) {
            try {
                int format = capture.getFormat();
                if (format == ImageFormat.JPEG) {
                    Logt.i(TAG, "Received JPEG capture");
                    byte[] img = ItsUtils.getDataFromImage(capture, mSocketQueueQuota);
                    ByteBuffer buf = ByteBuffer.wrap(img);
                    int count = mCountJpg.getAndIncrement();
                    mSocketRunnableObj.sendResponseCaptureBuffer("jpegImage", buf);
                } else if (format == ImageFormat.YUV_420_888) {
                    Logt.i(TAG, "Received YUV capture");
                    byte[] img = ItsUtils.getDataFromImage(capture, mSocketQueueQuota);
                    ByteBuffer buf = ByteBuffer.wrap(img);
                    int count = mCountYuv.getAndIncrement();
                    mSocketRunnableObj.sendResponseCaptureBuffer("yuvImage", buf);
                } else if (format == ImageFormat.RAW10) {
                    Logt.i(TAG, "Received RAW10 capture");
                    byte[] img = ItsUtils.getDataFromImage(capture, mSocketQueueQuota);
                    ByteBuffer buf = ByteBuffer.wrap(img);
                    int count = mCountRaw10.getAndIncrement();
                    mSocketRunnableObj.sendResponseCaptureBuffer("raw10Image", buf);
                } else if (format == ImageFormat.RAW12) {
                    Logt.i(TAG, "Received RAW12 capture");
                    byte[] img = ItsUtils.getDataFromImage(capture, mSocketQueueQuota);
                    ByteBuffer buf = ByteBuffer.wrap(img);
                    int count = mCountRaw12.getAndIncrement();
                    mSocketRunnableObj.sendResponseCaptureBuffer("raw12Image", buf);
                } else if (format == ImageFormat.RAW_SENSOR) {
                    Logt.i(TAG, "Received RAW16 capture");
                    int count = mCountRawOrDng.getAndIncrement();
                    if (! mCaptureRawIsDng) {
                        byte[] img = ItsUtils.getDataFromImage(capture, mSocketQueueQuota);
                        if (! mCaptureRawIsStats) {
                            ByteBuffer buf = ByteBuffer.wrap(img);
                            mSocketRunnableObj.sendResponseCaptureBuffer("rawImage", buf);
                        } else {
                            // Compute the requested stats on the raw frame, and return the results
                            // in a new "stats image".
                            long startTimeMs = SystemClock.elapsedRealtime();
                            int w = capture.getWidth();
                            int h = capture.getHeight();
                            int aaw = ItsUtils.getActiveArrayCropRegion(mCameraCharacteristics)
                                              .width();
                            int aah = ItsUtils.getActiveArrayCropRegion(mCameraCharacteristics)
                                              .height();
                            int aax = ItsUtils.getActiveArrayCropRegion(mCameraCharacteristics)
                                              .left;
                            int aay = ItsUtils.getActiveArrayCropRegion(mCameraCharacteristics)
                                              .top;
                            int gw = mCaptureStatsGridWidth;
                            int gh = mCaptureStatsGridHeight;
                            float[] stats = StatsImage.computeStatsImage(
                                                             img, w, h, aax, aay, aaw, aah, gw, gh);
                            long endTimeMs = SystemClock.elapsedRealtime();
                            Log.e(TAG, "Raw stats computation takes " + (endTimeMs - startTimeMs) + " ms");
                            int statsImgSize = stats.length * 4;
                            if (mSocketQueueQuota != null) {
                                mSocketQueueQuota.release(img.length);
                                mSocketQueueQuota.acquire(statsImgSize);
                            }
                            ByteBuffer bBuf = ByteBuffer.allocate(statsImgSize);
                            bBuf.order(ByteOrder.nativeOrder());
                            FloatBuffer fBuf = bBuf.asFloatBuffer();
                            fBuf.put(stats);
                            fBuf.position(0);
                            mSocketRunnableObj.sendResponseCaptureBuffer("rawStatsImage", bBuf);
                        }
                    } else {
                        // Wait until the corresponding capture result is ready, up to a timeout.
                        long t0 = android.os.SystemClock.elapsedRealtime();
                        while (! mThreadExitFlag
                                && android.os.SystemClock.elapsedRealtime()-t0 < TIMEOUT_CAP_RES) {
                            if (mCaptureResults[count] != null) {
                                Logt.i(TAG, "Writing capture as DNG");
                                DngCreator dngCreator = new DngCreator(
                                        mCameraCharacteristics, mCaptureResults[count]);
                                ByteArrayOutputStream dngStream = new ByteArrayOutputStream();
                                dngCreator.writeImage(dngStream, capture);
                                byte[] dngArray = dngStream.toByteArray();
                                if (mSocketQueueQuota != null) {
                                    // Ideally we should acquire before allocating memory, but
                                    // here the DNG size is unknown before toByteArray call, so
                                    // we have to register the size afterward. This should still
                                    // works most of the time since all DNG images are handled by
                                    // the same handler thread, so we are at most one buffer over
                                    // the quota.
                                    mSocketQueueQuota.acquire(dngArray.length);
                                }
                                ByteBuffer dngBuf = ByteBuffer.wrap(dngArray);
                                mSocketRunnableObj.sendResponseCaptureBuffer("dngImage", dngBuf);
                                break;
                            } else {
                                Thread.sleep(1);
                            }
                        }
                    }
                } else {
                    throw new ItsException("Unsupported image format: " + format);
                }

                synchronized(mCountCallbacksRemaining) {
                    mCountCallbacksRemaining.decrementAndGet();
                    mCountCallbacksRemaining.notify();
                }
            } catch (IOException e) {
                Logt.e(TAG, "Script error: ", e);
            } catch (InterruptedException e) {
                Logt.e(TAG, "Script error: ", e);
            } catch (ItsException e) {
                Logt.e(TAG, "Script error: ", e);
            }
        }
    };

    private static float r2f(Rational r) {
        return (float)r.getNumerator() / (float)r.getDenominator();
    }

    private final CaptureResultListener mCaptureResultListener = new CaptureResultListener() {
        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request,
                long timestamp, long frameNumber) {
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                TotalCaptureResult result) {
            try {
                // Currently result has all 0 values.
                if (request == null || result == null) {
                    throw new ItsException("Request/result is invalid");
                }

                StringBuilder logMsg = new StringBuilder();
                logMsg.append(String.format(
                        "Capt result: AE=%d, AF=%d, AWB=%d, ",
                        result.get(CaptureResult.CONTROL_AE_STATE),
                        result.get(CaptureResult.CONTROL_AF_STATE),
                        result.get(CaptureResult.CONTROL_AWB_STATE)));
                int[] capabilities = mCameraCharacteristics.get(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                if (capabilities == null) {
                    throw new ItsException("Failed to get capabilities");
                }
                boolean readSensorSettings = false;
                for (int capability : capabilities) {
                    if (capability ==
                            CameraCharacteristics.
                                    REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS) {
                        readSensorSettings = true;
                        break;
                    }
                }
                if (readSensorSettings) {
                    logMsg.append(String.format(
                            "sens=%d, exp=%.1fms, dur=%.1fms, ",
                            result.get(CaptureResult.SENSOR_SENSITIVITY),
                            result.get(CaptureResult.SENSOR_EXPOSURE_TIME).intValue() / 1000000.0f,
                            result.get(CaptureResult.SENSOR_FRAME_DURATION).intValue() /
                                        1000000.0f));
                }
                if (result.get(CaptureResult.COLOR_CORRECTION_GAINS) != null) {
                    logMsg.append(String.format(
                            "gains=[%.1f, %.1f, %.1f, %.1f], ",
                            result.get(CaptureResult.COLOR_CORRECTION_GAINS).getRed(),
                            result.get(CaptureResult.COLOR_CORRECTION_GAINS).getGreenEven(),
                            result.get(CaptureResult.COLOR_CORRECTION_GAINS).getGreenOdd(),
                            result.get(CaptureResult.COLOR_CORRECTION_GAINS).getBlue()));
                } else {
                    logMsg.append("gains=[], ");
                }
                if (result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM) != null) {
                    logMsg.append(String.format(
                            "xform=[%.1f, %.1f, %.1f, %.1f, %.1f, %.1f, %.1f, %.1f, %.1f], ",
                            r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(0,0)),
                            r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(1,0)),
                            r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(2,0)),
                            r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(0,1)),
                            r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(1,1)),
                            r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(2,1)),
                            r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(0,2)),
                            r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(1,2)),
                            r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(2,2))));
                } else {
                    logMsg.append("xform=[], ");
                }
                logMsg.append(String.format(
                        "foc=%.1f",
                        result.get(CaptureResult.LENS_FOCUS_DISTANCE)));
                Logt.i(TAG, logMsg.toString());

                if (result.get(CaptureResult.CONTROL_AE_STATE) != null) {
                    mConvergedAE = result.get(CaptureResult.CONTROL_AE_STATE) ==
                                              CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                                   result.get(CaptureResult.CONTROL_AE_STATE) ==
                                              CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED ||
                                   result.get(CaptureResult.CONTROL_AE_STATE) ==
                                              CaptureResult.CONTROL_AE_STATE_LOCKED;
                    mLockedAE = result.get(CaptureResult.CONTROL_AE_STATE) ==
                                           CaptureResult.CONTROL_AE_STATE_LOCKED;
                }
                if (result.get(CaptureResult.CONTROL_AF_STATE) != null) {
                    mConvergedAF = result.get(CaptureResult.CONTROL_AF_STATE) ==
                                              CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED;
                }
                if (result.get(CaptureResult.CONTROL_AWB_STATE) != null) {
                    mConvergedAWB = result.get(CaptureResult.CONTROL_AWB_STATE) ==
                                               CaptureResult.CONTROL_AWB_STATE_CONVERGED ||
                                    result.get(CaptureResult.CONTROL_AWB_STATE) ==
                                               CaptureResult.CONTROL_AWB_STATE_LOCKED;
                    mLockedAWB = result.get(CaptureResult.CONTROL_AWB_STATE) ==
                                            CaptureResult.CONTROL_AWB_STATE_LOCKED;
                }

                if (mConvergedAE && (!mNeedsLockedAE || mLockedAE)) {
                    if (result.get(CaptureResult.SENSOR_SENSITIVITY) != null
                            && result.get(CaptureResult.SENSOR_EXPOSURE_TIME) != null) {
                        mSocketRunnableObj.sendResponse("aeResult", String.format("%d %d",
                                result.get(CaptureResult.SENSOR_SENSITIVITY).intValue(),
                                result.get(CaptureResult.SENSOR_EXPOSURE_TIME).intValue()
                                ));
                    } else {
                        Logt.i(TAG, String.format(
                                "AE converged but NULL exposure values, sensitivity:%b, expTime:%b",
                                result.get(CaptureResult.SENSOR_SENSITIVITY) == null,
                                result.get(CaptureResult.SENSOR_EXPOSURE_TIME) == null));
                    }
                }

                if (mConvergedAF) {
                    if (result.get(CaptureResult.LENS_FOCUS_DISTANCE) != null) {
                        mSocketRunnableObj.sendResponse("afResult", String.format("%f",
                                result.get(CaptureResult.LENS_FOCUS_DISTANCE)
                                ));
                    } else {
                        Logt.i(TAG, "AF converged but NULL focus distance values");
                    }
                }

                if (mConvergedAWB && (!mNeedsLockedAWB || mLockedAWB)) {
                    if (result.get(CaptureResult.COLOR_CORRECTION_GAINS) != null
                            && result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM) != null) {
                        mSocketRunnableObj.sendResponse("awbResult", String.format(
                                "%f %f %f %f %f %f %f %f %f %f %f %f %f",
                                result.get(CaptureResult.COLOR_CORRECTION_GAINS).getRed(),
                                result.get(CaptureResult.COLOR_CORRECTION_GAINS).getGreenEven(),
                                result.get(CaptureResult.COLOR_CORRECTION_GAINS).getGreenOdd(),
                                result.get(CaptureResult.COLOR_CORRECTION_GAINS).getBlue(),
                                r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(0,0)),
                                r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(1,0)),
                                r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(2,0)),
                                r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(0,1)),
                                r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(1,1)),
                                r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(2,1)),
                                r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(0,2)),
                                r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(1,2)),
                                r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(2,2))
                                ));
                    } else {
                        Logt.i(TAG, String.format(
                                "AWB converged but NULL color correction values, gains:%b, ccm:%b",
                                result.get(CaptureResult.COLOR_CORRECTION_GAINS) == null,
                                result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM) == null));
                    }
                }

                if (mIssuedRequest3A) {
                    mIssuedRequest3A = false;
                    mInterlock3A.open();
                } else {
                    int count = mCountCapRes.getAndIncrement();
                    mCaptureResults[count] = result;
                    mSocketRunnableObj.sendResponseCaptureResult(mCameraCharacteristics,
                            request, result, mOutputImageReaders);
                    synchronized(mCountCallbacksRemaining) {
                        mCountCallbacksRemaining.decrementAndGet();
                        mCountCallbacksRemaining.notify();
                    }
                }
            } catch (ItsException e) {
                Logt.e(TAG, "Script error: ", e);
            } catch (Exception e) {
                Logt.e(TAG, "Script error: ", e);
            }
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                CaptureFailure failure) {
            Logt.e(TAG, "Script error: capture failed");
        }
    };

    private class CaptureCallbackWaiter extends CameraCaptureSession.CaptureCallback {
        private final LinkedBlockingQueue<TotalCaptureResult> mResultQueue =
                new LinkedBlockingQueue<>();

        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request,
                long timestamp, long frameNumber) {
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                TotalCaptureResult result) {
            try {
                mResultQueue.put(result);
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException(
                        "Can't handle InterruptedException in onImageAvailable");
            }
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                CaptureFailure failure) {
            Logt.e(TAG, "Script error: capture failed");
        }

        public TotalCaptureResult getResult(long timeoutMs) throws ItsException {
            TotalCaptureResult result;
            try {
                result = mResultQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new ItsException(e);
            }

            if (result == null) {
                throw new ItsException("Getting an image timed out after " + timeoutMs +
                        "ms");
            }

            return result;
        }
    }

    private static class ImageReaderListenerWaiter implements ImageReader.OnImageAvailableListener {
        private final LinkedBlockingQueue<Image> mImageQueue = new LinkedBlockingQueue<>();

        @Override
        public void onImageAvailable(ImageReader reader) {
            try {
                mImageQueue.put(reader.acquireNextImage());
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException(
                        "Can't handle InterruptedException in onImageAvailable");
            }
        }

        public Image getImage(long timeoutMs) throws ItsException {
            Image image;
            try {
                image = mImageQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new ItsException(e);
            }

            if (image == null) {
                throw new ItsException("Getting an image timed out after " + timeoutMs +
                        "ms");
            }
            return image;
        }
    }

    private int getReprocessInputFormat(JSONObject params) throws ItsException {
        String reprocessFormat;
        try {
            reprocessFormat = params.getString("reprocessFormat");
        } catch (org.json.JSONException e) {
            throw new ItsException("Error parsing reprocess format: " + e);
        }

        if (reprocessFormat.equals("yuv")) {
            return ImageFormat.YUV_420_888;
        } else if (reprocessFormat.equals("private")) {
            return ImageFormat.PRIVATE;
        }

        throw new ItsException("Uknown reprocess format: " + reprocessFormat);
    }
}
