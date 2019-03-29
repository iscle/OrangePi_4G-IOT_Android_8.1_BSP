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

package android.hardware.multiprocess.camera.cts;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Service for collecting error messages from other processes.
 *
 * <p />
 * Used by CTS for multi-process error logging.
 */
public class ErrorLoggingService extends Service {
    public static final String TAG = "ErrorLoggingService";

    /**
     * Receive all currently logged error strings in replyTo Messenger.
     */
    public static final int MSG_GET_LOG = 0;

    /**
     * Append a new error string to the log maintained in this service.
     */
    public static final int MSG_LOG_EVENT = 1;

    /**
     * Logged errors being reported in a replyTo Messenger by this service.
     */
    public static final int MSG_LOG_REPORT = 2;

    /**
     * A list of strings containing all error messages reported to this service.
     */
    private final ArrayList<LogEvent> mLog = new ArrayList<>();

    /**
     * A list of Messengers waiting for logs for any event.
     */
    private final ArrayList<Pair<Integer, Messenger>> mEventWaiters = new ArrayList<>();

    private static final int DO_EVENT_FILTER = 1;
    private static final String LOG_EVENT = "log_event";
    private static final String LOG_EVENT_ARRAY = "log_event_array";


    /**
     * The messenger binder used by clients of this service to report/retrieve errors.
     */
    private final Messenger mMessenger = new Messenger(new MainHandler(mLog, mEventWaiters));

    @Override
    public void onDestroy() {
        super.onDestroy();
        mLog.clear();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    /**
     * Handler implementing the message interface for this service.
     */
    private static class MainHandler extends Handler {

        ArrayList<LogEvent> mErrorLog;
        ArrayList<Pair<Integer, Messenger>> mEventWaiters;

        MainHandler(ArrayList<LogEvent> log, ArrayList<Pair<Integer, Messenger>> waiters) {
            mErrorLog = log;
            mEventWaiters = waiters;
        }

        private void sendMessages() {
            if (mErrorLog.size() > 0) {
                ListIterator<Pair<Integer, Messenger>> iter = mEventWaiters.listIterator();
                boolean messagesHandled = false;
                while (iter.hasNext()) {
                    Pair<Integer, Messenger> elem = iter.next();
                    for (LogEvent i : mErrorLog) {
                        if (elem.first == null || elem.first == i.getEvent()) {
                            Message m = Message.obtain(null, MSG_LOG_REPORT);
                            Bundle b = m.getData();
                            b.putParcelableArray(LOG_EVENT_ARRAY,
                                    mErrorLog.toArray(new LogEvent[mErrorLog.size()]));
                            m.setData(b);
                            try {
                                elem.second.send(m);
                                messagesHandled = true;
                            } catch (RemoteException e) {
                                Log.e(TAG, "Could not report log message to remote, " +
                                        "received exception from remote: " + e +
                                        "\n  Original errors: " +
                                        Arrays.toString(mErrorLog.toArray()));
                            }
                            iter.remove();
                        }
                    }
                }
                if (messagesHandled) {
                    mErrorLog.clear();
                }
            }
        }

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MSG_GET_LOG:
                    if (msg.replyTo == null) {
                        break;
                    }

                    if (msg.arg1 == DO_EVENT_FILTER) {
                        mEventWaiters.add(new Pair<Integer, Messenger>(msg.arg2, msg.replyTo));
                    } else {
                        mEventWaiters.add(new Pair<Integer, Messenger>(null, msg.replyTo));
                    }

                    sendMessages();

                    break;
                case MSG_LOG_EVENT:
                    Bundle b = msg.getData();
                    b.setClassLoader(LogEvent.class.getClassLoader());
                    LogEvent error = b.getParcelable(LOG_EVENT);
                    mErrorLog.add(error);

                    sendMessages();

                    break;
                default:
                    Log.e(TAG, "Unknown message type: " + msg.what);
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * Parcelable object to use with logged events.
     */
    public static class LogEvent implements Parcelable {

        private final int mEvent;
        private final String mLogText;

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(mEvent);
            out.writeString(mLogText);
        }

        public int getEvent() {
            return mEvent;
        }

        public String getLogText() {
            return mLogText;
        }

        public static final Parcelable.Creator<LogEvent> CREATOR
                = new Parcelable.Creator<LogEvent>() {

            public LogEvent createFromParcel(Parcel in) {
                return new LogEvent(in);
            }

            public LogEvent[] newArray(int size) {
                return new LogEvent[size];
            }
        };

        private LogEvent(Parcel in) {
            mEvent = in.readInt();
            mLogText = in.readString();
        }

        public LogEvent(int id, String msg) {
            mEvent = id;
            mLogText = msg;
        }

        @Override
        public String toString() {
            return "LogEvent{" +
                    "Event=" + mEvent +
                    ", LogText='" + mLogText + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LogEvent logEvent = (LogEvent) o;

            if (mEvent != logEvent.mEvent) return false;
            if (mLogText != null ? !mLogText.equals(logEvent.mLogText) : logEvent.mLogText != null)
                return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = mEvent;
            result = 31 * result + (mLogText != null ? mLogText.hashCode() : 0);
            return result;
        }
    }

    /**
     * Implementation of Future to use when retrieving error messages from service.
     *
     * <p />
     * To use this, either pass a {@link Runnable} or {@link Callable} in the constructor,
     * or use the default constructor and set the result externally with {@link #setResult(Object)}.
     */
    private static class SettableFuture<T> extends FutureTask<T> {

        public SettableFuture() {
            super(new Callable<T>() {
                @Override
                public T call() throws Exception {
                    throw new IllegalStateException(
                            "Empty task, use #setResult instead of calling run.");
                }
            });
        }

        public SettableFuture(Callable<T> callable) {
            super(callable);
        }

        public SettableFuture(Runnable runnable, T result) {
            super(runnable, result);
        }

        public void setResult(T result) {
            set(result);
        }
    }

    /**
     * Helper class for setting up and using a connection to {@link ErrorLoggingService}.
     */
    public static class ErrorServiceConnection implements AutoCloseable {

        private Messenger mService = null;
        private boolean mBind = false;
        private final Object mLock = new Object();
        private final Context mContext;
        private final HandlerThread mReplyThread;
        private ReplyHandler mReplyHandler;
        private Messenger mReplyMessenger;

        /**
         * Construct a connection to the {@link ErrorLoggingService} in the given {@link Context}.
         *
         * @param context the {@link Context} to bind the service in.
         */
        public ErrorServiceConnection(final Context context) {
            mContext = context;
            mReplyThread = new HandlerThread("ErrorServiceConnection");
            mReplyThread.start();
            mReplyHandler = new ReplyHandler(mReplyThread.getLooper());
            mReplyMessenger = new Messenger(mReplyHandler);
        }

        @Override
        public void close() {
            stop();
            mReplyThread.quit();
            synchronized (mLock) {
                mService = null;
                mBind = false;
                mReplyHandler.cancelAll();
            }
        }

        @Override
        protected void finalize() throws Throwable {
            close();
            super.finalize();
        }

        private static final class ReplyHandler extends Handler {

            private final LinkedBlockingQueue<SettableFuture<List<LogEvent>>> mFuturesQueue =
                    new LinkedBlockingQueue<>();

            private ReplyHandler(Looper looper) {
                super(looper);
            }

            /**
             * Cancel all pending futures for this handler.
             */
            public void cancelAll() {
                List<SettableFuture<List<LogEvent>>> logFutures = new ArrayList<>();
                mFuturesQueue.drainTo(logFutures);
                for (SettableFuture<List<LogEvent>> i : logFutures) {
                    i.cancel(true);
                }
            }

            /**
             * Cancel a given future, and remove from the pending futures for this handler.
             *
             * @param report future to remove.
             */
            public void cancel(SettableFuture<List<LogEvent>> report) {
                mFuturesQueue.remove(report);
                report.cancel(true);
            }

            /**
             * Add future for the next received report from this service.
             *
             * @param report a future to get the next received event report from.
             */
            public void addFuture(SettableFuture<List<LogEvent>> report) {
                if (!mFuturesQueue.offer(report)) {
                    Log.e(TAG, "Could not request another error report, too many requests queued.");
                }
            }

            @SuppressWarnings("unchecked")
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_LOG_REPORT:
                        SettableFuture<List<LogEvent>> task = mFuturesQueue.poll();
                        if (task == null) break;
                        Bundle b = msg.getData();
                        b.setClassLoader(LogEvent.class.getClassLoader());
                        Parcelable[] array = b.getParcelableArray(LOG_EVENT_ARRAY);
                        LogEvent[] events = Arrays.copyOf(array, array.length, LogEvent[].class);
                        List<LogEvent> res = Arrays.asList(events);
                        task.setResult(res);
                        break;
                    default:
                        Log.e(TAG, "Unknown message type: " + msg.what);
                        super.handleMessage(msg);
                }
            }
        }

        private ServiceConnection mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                Log.i(TAG, "Service connected.");
                synchronized (mLock) {
                    mService = new Messenger(iBinder);
                    mBind = true;
                    mLock.notifyAll();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                Log.i(TAG, "Service disconnected.");
                synchronized (mLock) {
                    mService = null;
                    mBind = false;
                    mReplyHandler.cancelAll();
                }
            }
        };

        private Messenger blockingGetBoundService() {
            synchronized (mLock) {
                if (!mBind) {
                    mContext.bindService(new Intent(mContext, ErrorLoggingService.class), mConnection,
                            Context.BIND_AUTO_CREATE);
                    mBind = true;
                }
                try {
                    while (mService == null && mBind) {
                        mLock.wait();
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Waiting for error service interrupted: " + e);
                }
                if (!mBind) {
                    Log.w(TAG, "Could not get service, service disconnected.");
                }
                return mService;
            }
        }

        private Messenger getBoundService() {
            synchronized (mLock) {
                if (!mBind) {
                    mContext.bindService(new Intent(mContext, ErrorLoggingService.class), mConnection,
                            Context.BIND_AUTO_CREATE);
                    mBind = true;
                }
                return mService;
            }
        }

        /**
         * If the {@link ErrorLoggingService} is not yet bound, begin service connection attempt.
         *
         * <p />
         * Note: This will not block.
         */
        public void start() {
            synchronized (mLock) {
                if (!mBind) {
                    mContext.bindService(new Intent(mContext, ErrorLoggingService.class), mConnection,
                            Context.BIND_AUTO_CREATE);
                    mBind = true;
                }
            }
        }

        /**
         * Unbind from the {@link ErrorLoggingService} if it has been bound.
         *
         * <p />
         * Note: This will not block.
         */
        public void stop() {
            synchronized (mLock) {
                if (mBind) {
                    mContext.unbindService(mConnection);
                    mBind = false;
                }
            }
        }

        /**
         * Send an logged event to the bound {@link ErrorLoggingService}.
         *
         * <p />
         * If the service is not yet bound, this will bind the service and wait until it has been
         * connected.
         *
         * <p />
         * This is not safe to call from the UI thread, as this will deadlock with the looper used
         * when connecting the service.
         *
         * @param id an int indicating the ID of this event.
         * @param msg a {@link String} message to send.
         */
        public void log(final int id, final String msg) {
            Messenger service = blockingGetBoundService();
            Message m = Message.obtain(null, MSG_LOG_EVENT);
            m.getData().putParcelable(LOG_EVENT, new LogEvent(id, msg));
            try {
                service.send(m);
            } catch (RemoteException e) {
                Log.e(TAG, "Received exception while logging error: " + e);
            }
        }

        /**
         * Send an logged event to the bound {@link ErrorLoggingService} when it becomes available.
         *
         * <p />
         * If the service is not yet bound, this will bind the service.
         *
         * @param id an int indicating the ID of this event.
         * @param msg a {@link String} message to send.
         */
        public void logAsync(final int id, final String msg) {
            AsyncTask.SERIAL_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    log(id, msg);
                }
            });
        }

        /**
         * Retrieve all events logged in the {@link ErrorLoggingService}.
         *
         * <p />
         * If the service is not yet bound, this will bind the service and wait until it has been
         * connected.  Likewise, after the service has been bound, this method will block until
         * the given timeout passes or an event is logged in the service.  Passing a negative
         * timeout is equivalent to using an infinite timeout value.
         *
         * <p />
         * This is not safe to call from the UI thread, as this will deadlock with the looper used
         * when connecting the service.
         *
         * <p />
         * Note: This method clears the events stored in the bound {@link ErrorLoggingService}.
         *
         * @param timeoutMs the number of milliseconds to wait for a logging event.
         * @return a list of {@link String} error messages reported to the bound
         *          {@link ErrorLoggingService} since the last call to getLog.
         *
         * @throws TimeoutException if the given timeout elapsed with no events logged.
         */
        public List<LogEvent> getLog(long timeoutMs) throws TimeoutException {
            return retrieveLog(false, 0, timeoutMs);
        }

        /**
         * Retrieve all events logged in the {@link ErrorLoggingService}.
         *
         * <p />
         * If the service is not yet bound, this will bind the service and wait until it has been
         * connected.  Likewise, after the service has been bound, this method will block until
         * the given timeout passes or an event with the given event ID is logged in the service.
         * Passing a negative timeout is equivalent to using an infinite timeout value.
         *
         * <p />
         * This is not safe to call from the UI thread, as this will deadlock with the looper used
         * when connecting the service.
         *
         * <p />
         * Note: This method clears the events stored in the bound {@link ErrorLoggingService}.
         *
         * @param timeoutMs the number of milliseconds to wait for a logging event.
         * @param event the ID of the event to wait for.
         * @return a list of {@link String} error messages reported to the bound
         *          {@link ErrorLoggingService} since the last call to getLog.
         *
         * @throws TimeoutException if the given timeout elapsed with no events of the given type
         *          logged.
         */
        public List<LogEvent> getLog(long timeoutMs, int event) throws TimeoutException {
            return retrieveLog(true, event, timeoutMs);
        }

        private List<LogEvent> retrieveLog(boolean hasEvent, int event, long timeout)
                throws TimeoutException {
            Messenger service = blockingGetBoundService();

            SettableFuture<List<LogEvent>> task = new SettableFuture<>();

            Message m = (hasEvent) ?
                    Message.obtain(null, MSG_GET_LOG, DO_EVENT_FILTER, event, null) :
                    Message.obtain(null, MSG_GET_LOG);
            m.replyTo = mReplyMessenger;

            synchronized(this) {
                mReplyHandler.addFuture(task);
                try {
                    service.send(m);
                } catch (RemoteException e) {
                    Log.e(TAG, "Received exception while retrieving errors: " + e);
                    return null;
                }
            }

            List<LogEvent> res = null;
            try {
                res = (timeout < 0) ? task.get() : task.get(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException|ExecutionException e) {
                Log.e(TAG, "Received exception while retrieving errors: " + e);
            }
            return res;
        }
    }
}
