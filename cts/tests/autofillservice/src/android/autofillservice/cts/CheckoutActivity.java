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

import static android.widget.ArrayAdapter.createFromResource;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Activity that has the following fields:
 *
 * <ul>
 *   <li>Credit Card Number EditText (id: cc_numberusername, no input-type)
 *   <li>Credit Card Expiration EditText (id: cc_expiration, no input-type)
 *   <li>Address RadioGroup (id: addess, no autofill-type)
 *   <li>Save Credit Card CheckBox (id: save_cc, no autofill-type)
 *   <li>Clear Button
 *   <li>Buy Button
 * </ul>
 */
public class CheckoutActivity extends AbstractAutoFillActivity {
    private static final long BUY_TIMEOUT_MS = 1000;

    static final String ID_CC_NUMBER = "cc_number";
    static final String ID_CC_EXPIRATION = "cc_expiration";
    static final String ID_ADDRESS = "address";
    static final String ID_HOME_ADDRESS = "home_address";
    static final String ID_WORK_ADDRESS = "work_address";
    static final String ID_SAVE_CC = "save_cc";

    static final int INDEX_ADDRESS_HOME = 0;
    static final int INDEX_ADDRESS_WORK = 1;

    static final int INDEX_CC_EXPIRATION_YESTERDAY = 0;
    static final int INDEX_CC_EXPIRATION_TODAY = 1;
    static final int INDEX_CC_EXPIRATION_TOMORROW = 2;
    static final int INDEX_CC_EXPIRATION_NEVER = 3;

    private EditText mCcNumber;
    private Spinner mCcExpiration;
    private ArrayAdapter<CharSequence> mCcExpirationAdapter;
    private RadioGroup mAddress;
    private RadioButton mHomeAddress;
    private CheckBox mSaveCc;
    private Button mBuyButton;
    private Button mClearButton;

    private FillExpectation mExpectation;
    private CountDownLatch mBuyLatch;

    private static CheckoutActivity sInstance;

    public CheckoutActivity() {
        sInstance = this;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(getContentView());

        mCcNumber = findViewById(R.id.cc_number);
        mCcExpiration = findViewById(R.id.cc_expiration);
        mAddress = findViewById(R.id.address);
        mHomeAddress = findViewById(R.id.home_address);
        mSaveCc = findViewById(R.id.save_cc);
        mBuyButton = findViewById(R.id.buy);
        mClearButton = findViewById(R.id.clear);

        mCcExpirationAdapter = createFromResource(this,
                R.array.cc_expiration_values, android.R.layout.simple_spinner_item);
        mCcExpirationAdapter
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCcExpiration.setAdapter(mCcExpirationAdapter);

        mBuyButton.setOnClickListener((v) -> buy());
        mClearButton.setOnClickListener((v) -> resetFields());
    }

    static void finishIt() {
        if (sInstance != null) {
            sInstance.finish();
        }
    }

    protected int getContentView() {
        return R.layout.checkout_activity;
    }

    /**
     * Resets the values of the input fields.
     */
    private void resetFields() {
        mCcNumber.setText("");
        mCcExpiration.setSelection(0, false);
        mAddress.clearCheck();
        mSaveCc.setChecked(false);
    }

    /**
     * Emulates a buy action.
     */
    private void buy() {
        final Intent intent = new Intent(this, WelcomeActivity.class);
        intent.putExtra(WelcomeActivity.EXTRA_MESSAGE, "Thank you an come again!");
        startActivity(intent);
        if (mBuyLatch != null) {
            // Latch is not set when activity launched outside tests
            mBuyLatch.countDown();
        }
        finish();
    }

    /**
     * Sets the expectation for an auto-fill request, so it can be asserted through
     * {@link #assertAutoFilled()} later.
     */
    void expectAutoFill(String ccNumber, int ccExpirationIndex, int addressId, boolean saveCc) {
        mExpectation = new FillExpectation(ccNumber, ccExpirationIndex, addressId, saveCc);
        mCcNumber.addTextChangedListener(mExpectation.ccNumberWatcher);
        mCcExpiration.setOnItemSelectedListener(mExpectation.ccExpirationListener);
        mAddress.setOnCheckedChangeListener(mExpectation.addressListener);
        mSaveCc.setOnCheckedChangeListener(mExpectation.saveCcListener);
    }

    /**
     * Asserts the activity was auto-filled with the values passed to
     * {@link #expectAutoFill(String, int, int, boolean)}.
     */
    void assertAutoFilled() throws Exception {
        assertWithMessage("expectAutoFill() not called").that(mExpectation).isNotNull();
        mExpectation.ccNumberWatcher.assertAutoFilled();
        mExpectation.ccExpirationListener.assertAutoFilled();
        mExpectation.addressListener.assertAutoFilled();
        mExpectation.saveCcListener.assertAutoFilled();
    }

    /**
     * Visits the {@code ccNumber} in the UiThread.
     */
    void onCcNumber(Visitor<EditText> v) {
        syncRunOnUiThread(() -> v.visit(mCcNumber));
    }

    /**
     * Visits the {@code ccExpirationDate} in the UiThread.
     */
    void onCcExpiration(Visitor<Spinner> v) {
        syncRunOnUiThread(() -> v.visit(mCcExpiration));
    }

    /**
     * Visits the {@code ccExpirationDate} adapter in the UiThread.
     */
    void onCcExpirationAdapter(Visitor<ArrayAdapter<CharSequence>> v) {
        syncRunOnUiThread(() -> v.visit(mCcExpirationAdapter));
    }

    /**
     * Visits the {@code address} in the UiThread.
     */
    void onAddress(Visitor<RadioGroup> v) {
        syncRunOnUiThread(() -> v.visit(mAddress));
    }

    /**
     * Visits the {@code homeAddress} in the UiThread.
     */
    void onHomeAddress(Visitor<RadioButton> v) {
        syncRunOnUiThread(() -> v.visit(mHomeAddress));
    }

    /**
     * Visits the {@code saveCC} in the UiThread.
     */
    void onSaveCc(Visitor<CheckBox> v) {
        syncRunOnUiThread(() -> v.visit(mSaveCc));
    }

    /**
     * Taps the buy button in the UI thread.
     */
    void tapBuy() throws Exception {
        mBuyLatch = new CountDownLatch(1);
        syncRunOnUiThread(() -> mBuyButton.performClick());
        boolean called = mBuyLatch.await(BUY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertWithMessage("Timeout (%s ms) waiting for buy action", BUY_TIMEOUT_MS)
                .that(called).isTrue();
    }

    EditText getCcNumber() {
        return mCcNumber;
    }

    Spinner getCcExpiration() {
        return mCcExpiration;
    }

    ArrayAdapter<CharSequence> getCcExpirationAdapter() {
        return mCcExpirationAdapter;
    }

    /**
     * Holder for the expected auto-fill values.
     */
    private final class FillExpectation {
        private final OneTimeTextWatcher ccNumberWatcher;
        private final OneTimeSpinnerListener ccExpirationListener;
        private final OneTimeRadioGroupListener addressListener;
        private final OneTimeCompoundButtonListener saveCcListener;

        private FillExpectation(String ccNumber, int ccExpirationIndex, int addressId,
                boolean saveCc) {
            this.ccNumberWatcher = new OneTimeTextWatcher("ccNumber", mCcNumber, ccNumber);
            this.ccExpirationListener =
                    new OneTimeSpinnerListener("ccExpiration", mCcExpiration, ccExpirationIndex);
            addressListener = new OneTimeRadioGroupListener("address", mAddress, addressId);
            saveCcListener = new OneTimeCompoundButtonListener("saveCc", mSaveCc, saveCc);
        }
    }
}
