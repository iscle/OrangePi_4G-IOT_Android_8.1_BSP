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
package android.autofillservice.cts;

import android.os.Bundle;
import android.util.Log;
import android.view.autofill.AutofillManager;
import android.widget.Button;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Activity that contains a 4x4 grid of cells (named {@code l1c1} to {@code l4c2}) plus
 * {@code save} and {@code clear} buttons.
 */
public class GridActivity extends AbstractAutoFillActivity {

    private static final String TAG = "GridActivity";
    private static final int N_ROWS = 4;
    private static final int N_COLS = 2;

    public static final String ID_L1C1 = getResourceId(1, 1);
    public static final String ID_L1C2 = getResourceId(1, 2);
    public static final String ID_L2C1 = getResourceId(2, 1);
    public static final String ID_L2C2 = getResourceId(2, 2);
    public static final String ID_L3C1 = getResourceId(3, 1);
    public static final String ID_L3C2 = getResourceId(3, 2);
    public static final String ID_L4C1 = getResourceId(4, 1);
    public static final String ID_L4C2 = getResourceId(4, 2);

    private final EditText[][] mCells = new EditText[4][2];
    private Button mSaveButton;
    private Button mClearButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.grid_activity);

        mCells[0][0] = (EditText) findViewById(R.id.l1c1);
        mCells[0][1] = (EditText) findViewById(R.id.l1c2);
        mCells[1][0] = (EditText) findViewById(R.id.l2c1);
        mCells[1][1] = (EditText) findViewById(R.id.l2c2);
        mCells[2][0] = (EditText) findViewById(R.id.l3c1);
        mCells[2][1] = (EditText) findViewById(R.id.l3c2);
        mCells[3][0] = (EditText) findViewById(R.id.l4c1);
        mCells[3][1] = (EditText) findViewById(R.id.l4c2);
        mSaveButton = (Button) findViewById(R.id.save);
        mClearButton = (Button) findViewById(R.id.clear);

        mSaveButton.setOnClickListener((v) -> save());
        mClearButton.setOnClickListener((v) -> resetFields());
    }

    void save() {
        getSystemService(AutofillManager.class).commit();
    }

    void resetFields() {
        for (int i = 0; i < N_ROWS; i++) {
            for (int j = 0; j < N_COLS; j++) {
                mCells[i][j].setText("");
            }
        }
        getSystemService(AutofillManager.class).cancel();
    }

    private EditText getCell(int row, int column) {
        return mCells[row - 1][column - 1];
    }

    public static String getResourceId(int line, int col) {
        return "l" + line + "c" + col;
    }

    public void onCell(int row, int column, Visitor<EditText> v) {
        final EditText cell = getCell(row, column);
        syncRunOnUiThread(() -> v.visit(cell));
    }

    public void focusCell(int row, int column) {
        onCell(row, column, EditText::requestFocus);
    }

    public void clearCell(int row, int column) {
        onCell(row, column, (c) -> c.setText(""));
    }

    public void setText(int row, int column, String text) {
        onCell(row, column, (c) -> c.setText(text));
    }

    public void forceAutofill(int row, int column) {
        onCell(row, column, (c) -> getAutofillManager().requestAutofill(c));
    }

    public void triggerAutofill(boolean manually, int row, int column) {
        if (manually) {
            forceAutofill(row, column);
        } else {
            focusCell(row, column);
        }
    }

    public String getText(int row, int column) throws InterruptedException {
        final BlockingQueue<String> queue = new LinkedBlockingQueue<>(1);
        onCell(row, column, (c) -> queue.offer(c.getText().toString()));
        final String text = queue.poll(100, TimeUnit.MILLISECONDS);
        if (text == null) {
            throw new RetryableException("text not set in 100ms");
        }
        return text;
    }

    public FillExpectation expectAutofill() {
        return new FillExpectation();
    }

    public void dumpCells() {
        final StringBuilder output = new StringBuilder("dumpCells():\n");
        for (int i = 0; i < N_ROWS; i++) {
            for (int j = 0; j < N_COLS; j++) {
                final String id = getResourceId(i + 1, j + 1);
                final String value = mCells[i][j].getText().toString();
                output.append('\t').append(id).append("='").append(value).append("'\n");
            }
        }
        Log.d(TAG, output.toString());
    }

    final class FillExpectation {

        private final ArrayList<OneTimeTextWatcher> mWatchers = new ArrayList<>();

        public FillExpectation onCell(int line, int col, String value) {
            final String resourceId = getResourceId(line, col);
            final EditText cell = getCell(line, col);
            final OneTimeTextWatcher watcher = new OneTimeTextWatcher(resourceId, cell, value);
            mWatchers.add(watcher);
            cell.addTextChangedListener(watcher);
            return this;
        }

        public void assertAutoFilled() throws Exception {
            try {
                for (int i = 0; i < mWatchers.size(); i++) {
                    final OneTimeTextWatcher watcher = mWatchers.get(i);
                    watcher.assertAutoFilled();
                }
            } catch (AssertionError | Exception e) {
                dumpCells();
                throw e;
            }
        }
    }
}
