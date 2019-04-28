/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui.services;

import static com.android.documentsui.services.FileOperationService.OPERATION_COPY;
import static com.android.documentsui.services.FileOperationService.OPERATION_COMPRESS;
import static com.android.documentsui.services.FileOperationService.OPERATION_EXTRACT;
import static com.android.documentsui.services.FileOperationService.OPERATION_DELETE;
import static com.android.documentsui.services.FileOperationService.OPERATION_MOVE;
import static com.android.documentsui.services.FileOperationService.OPERATION_UNKNOWN;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.VisibleForTesting;

import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Features;
import com.android.documentsui.clipping.UrisSupplier;
import com.android.documentsui.services.FileOperationService.OpType;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * FileOperation describes a file operation, such as move/copy/delete etc.
 */
public abstract class FileOperation implements Parcelable {
    private final @OpType int mOpType;

    private final UrisSupplier mSrcs;
    private final List<Handler.Callback> mMessageListeners = new ArrayList<>();
    private DocumentStack mDestination;
    private Messenger mMessenger = new Messenger(
            new Handler(Looper.getMainLooper(), this::onMessage));

    @VisibleForTesting
    FileOperation(@OpType int opType, UrisSupplier srcs, DocumentStack destination) {
        assert(opType != OPERATION_UNKNOWN);
        assert(srcs.getItemCount() > 0);

        mOpType = opType;
        mSrcs = srcs;
        mDestination = destination;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public @OpType int getOpType() {
        return mOpType;
    }

    public UrisSupplier getSrc() {
        return mSrcs;
    }

    public DocumentStack getDestination() {
        return mDestination;
    }

    public Messenger getMessenger() {
        return mMessenger;
    }

    public void setDestination(DocumentStack destination) {
        mDestination = destination;
    }

    public void dispose() {
        mSrcs.dispose();
    }

    abstract Job createJob(Context service, Job.Listener listener, String id, Features features);

    private void appendInfoTo(StringBuilder builder) {
        builder.append("opType=").append(mOpType);
        builder.append(", srcs=").append(mSrcs.toString());
        builder.append(", destination=").append(mDestination.toString());
    }

    @Override
    public void writeToParcel(Parcel out, int flag) {
        out.writeInt(mOpType);
        out.writeParcelable(mSrcs, flag);
        out.writeParcelable(mDestination, flag);
        out.writeParcelable(mMessenger, flag);
    }

    private FileOperation(Parcel in) {
        mOpType = in.readInt();
        mSrcs = in.readParcelable(FileOperation.class.getClassLoader());
        mDestination = in.readParcelable(FileOperation.class.getClassLoader());
        mMessenger = in.readParcelable(FileOperation.class.getClassLoader());
    }

    public static class CopyOperation extends FileOperation {
        private CopyOperation(UrisSupplier srcs, DocumentStack destination) {
            super(OPERATION_COPY, srcs, destination);
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();

            builder.append("CopyOperation{");
            super.appendInfoTo(builder);
            builder.append("}");

            return builder.toString();
        }

        CopyJob createJob(Context service, Job.Listener listener, String id, Features features) {
            return new CopyJob(
                    service, listener, id, getDestination(), getSrc(), getMessenger(), features);
        }

        private CopyOperation(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<CopyOperation> CREATOR =
                new Parcelable.Creator<CopyOperation>() {

                    @Override
                    public CopyOperation createFromParcel(Parcel source) {
                        return new CopyOperation(source);
                    }

                    @Override
                    public CopyOperation[] newArray(int size) {
                        return new CopyOperation[size];
                    }
                };
    }

    public static class CompressOperation extends FileOperation {
        private CompressOperation(UrisSupplier srcs, DocumentStack destination) {
            super(OPERATION_COMPRESS, srcs, destination);
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();

            builder.append("CompressOperation{");
            super.appendInfoTo(builder);
            builder.append("}");

            return builder.toString();
        }

        CopyJob createJob(Context service, Job.Listener listener, String id, Features features) {
            return new CompressJob(service, listener, id, getDestination(), getSrc(),
                    getMessenger(), features);
        }

        private CompressOperation(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<CompressOperation> CREATOR =
                new Parcelable.Creator<CompressOperation>() {

                    @Override
                    public CompressOperation createFromParcel(Parcel source) {
                        return new CompressOperation(source);
                    }

                    @Override
                    public CompressOperation[] newArray(int size) {
                        return new CompressOperation[size];
                    }
                };
    }

    public static class ExtractOperation extends FileOperation {
        private ExtractOperation(UrisSupplier srcs, DocumentStack destination) {
            super(OPERATION_EXTRACT, srcs, destination);
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();

            builder.append("ExtractOperation{");
            super.appendInfoTo(builder);
            builder.append("}");

            return builder.toString();
        }

        // TODO: Replace CopyJob with ExtractJob.
        CopyJob createJob(Context service, Job.Listener listener, String id, Features features) {
            return new CopyJob(
                    service, listener, id, getDestination(), getSrc(), getMessenger(), features);
        }

        private ExtractOperation(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<ExtractOperation> CREATOR =
                new Parcelable.Creator<ExtractOperation>() {

                    @Override
                    public ExtractOperation createFromParcel(Parcel source) {
                        return new ExtractOperation(source);
                    }

                    @Override
                    public ExtractOperation[] newArray(int size) {
                        return new ExtractOperation[size];
                    }
                };
    }

    public static class MoveDeleteOperation extends FileOperation {
        private final @Nullable Uri mSrcParent;

        private MoveDeleteOperation(@OpType int opType, UrisSupplier srcs,
                DocumentStack destination, @Nullable Uri srcParent) {
            super(opType, srcs, destination);

            mSrcParent = srcParent;
        }

        @Override
        Job createJob(Context service, Job.Listener listener, String id, Features features) {
            switch(getOpType()) {
                case OPERATION_MOVE:
                    return new MoveJob(
                            service, listener, id, getDestination(), getSrc(), mSrcParent,
                            getMessenger(), features);
                case OPERATION_DELETE:
                    return new DeleteJob(service, listener, id, getDestination(), getSrc(),
                            mSrcParent, features);
                default:
                    throw new UnsupportedOperationException("Unsupported op type: " + getOpType());
            }
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();

            builder.append("MoveDeleteOperation{");
            super.appendInfoTo(builder);
            builder.append(", srcParent=").append(mSrcParent.toString());
            builder.append("}");

            return builder.toString();
        }

        @Override
        public void writeToParcel(Parcel out, int flag) {
            super.writeToParcel(out, flag);
            out.writeParcelable(mSrcParent, flag);
        }

        private MoveDeleteOperation(Parcel in) {
            super(in);
            mSrcParent = in.readParcelable(null);
        }

        public static final Parcelable.Creator<MoveDeleteOperation> CREATOR =
                new Parcelable.Creator<MoveDeleteOperation>() {


            @Override
            public MoveDeleteOperation createFromParcel(Parcel source) {
                return new MoveDeleteOperation(source);
            }

            @Override
            public MoveDeleteOperation[] newArray(int size) {
                return new MoveDeleteOperation[size];
            }
        };
    }

    public static class Builder {
        private @OpType int mOpType;
        private Uri mSrcParent;
        private UrisSupplier mSrcs;
        private DocumentStack mDestination;

        public Builder withOpType(@OpType int opType) {
            mOpType = opType;
            return this;
        }

        public Builder withSrcParent(@Nullable Uri srcParent) {
            mSrcParent = srcParent;
            return this;
        }

        public Builder withSrcs(UrisSupplier srcs) {
            mSrcs = srcs;
            return this;
        }

        public Builder withDestination(DocumentStack destination) {
            mDestination = destination;
            return this;
        }

        public FileOperation build() {
            switch (mOpType) {
                case OPERATION_COPY:
                    return new CopyOperation(mSrcs, mDestination);
                case OPERATION_COMPRESS:
                    return new CompressOperation(mSrcs, mDestination);
                case OPERATION_EXTRACT:
                    return new ExtractOperation(mSrcs, mDestination);
                case OPERATION_MOVE:
                case OPERATION_DELETE:
                    return new MoveDeleteOperation(mOpType, mSrcs, mDestination, mSrcParent);
                default:
                    throw new UnsupportedOperationException("Unsupported op type: " + mOpType);
            }
        }
    }

    boolean onMessage(Message message) {
        for (Handler.Callback listener : mMessageListeners) {
            if (listener.handleMessage(message)) {
              return true;
            }
        }
        return false;
    }

    /**
     * Registers a listener for messages from the service job.
     *
     * Callbacks must return true if the message is handled, and false if not.
     * Once handled, consecutive callbacks will not be called.
     */
    public void addMessageListener(Handler.Callback handler) {
        mMessageListeners.add(handler);
    }

    public void removeMessageListener(Handler.Callback handler) {
        mMessageListeners.remove(handler);
    }
}
