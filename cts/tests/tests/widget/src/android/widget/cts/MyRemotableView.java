/*
 * Copyright (C) 2016 The Android Open Source Project.
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

package android.widget.cts;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.AttributeSet;
import android.widget.RemoteViews;
import android.widget.TextView;

@RemoteViews.RemoteView
public class MyRemotableView extends TextView {
    private byte mByteField;
    private char mCharField;
    private double mDoubleField;
    private short mShortField;
    private Bundle mBundleField;
    private Intent mIntentField;

    public MyRemotableView(Context context) {
        super(context);
    }

    public MyRemotableView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyRemotableView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @android.view.RemotableViewMethod
    public void setByteField(byte value) {
        mByteField = value;
    }

    public byte getByteField() {
        return mByteField;
    }

    @android.view.RemotableViewMethod
    public void setCharField(char value) {
        mCharField = value;
    }

    public char getCharField() {
        return mCharField;
    }

    @android.view.RemotableViewMethod
    public void setDoubleField(double value) {
        mDoubleField = value;
    }

    public double getDoubleField() {
        return mDoubleField;
    }

    @android.view.RemotableViewMethod
    public void setShortField(short value) {
        mShortField = value;
    }

    public short getShortField() {
        return mShortField;
    }

    @android.view.RemotableViewMethod
    public void setBundleField(Bundle value) {
        mBundleField = value;
    }

    public Bundle getBundleField() {
        return mBundleField;
    }

    @android.view.RemotableViewMethod
    public void setIntentField(Intent value) {
        mIntentField = value;
    }

    public Intent getIntentField() {
        return mIntentField;
    }
}
