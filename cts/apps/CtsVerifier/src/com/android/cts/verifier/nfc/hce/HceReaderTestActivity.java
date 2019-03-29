/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.cts.verifier.nfc.hce;

import android.nfc.NfcAdapter;
import android.nfc.cardemulation.CardEmulation;
import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestListAdapter.TestListItem;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

/** Activity that lists all the NFC HCE reader tests. */
public class HceReaderTestActivity extends PassFailButtons.TestListActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_list);
        setInfoResources(R.string.nfc_test, R.string.nfc_hce_reader_test_info, 0);
        setPassFailButtonClickListeners();

        ArrayTestListAdapter adapter = new ArrayTestListAdapter(this);

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) {
            adapter.add(TestListItem.newCategory(this, R.string.nfc_hce_reader_tests));

            adapter.add(TestListItem.newTest(this, R.string.nfc_hce_protocol_params_reader,
                    ProtocolParamsReaderActivity.class.getName(),
                    new Intent(this, ProtocolParamsReaderActivity.class), null));

            adapter.add(TestListItem.newTest(this, R.string.nfc_hce_single_payment_reader,
                    getString(R.string.nfc_hce_single_payment_reader),
                    SinglePaymentEmulatorActivity.buildReaderIntent(this), null));

            adapter.add(TestListItem.newTest(this, R.string.nfc_hce_dual_payment_reader,
                    getString(R.string.nfc_hce_dual_payment_reader),
                    DualPaymentEmulatorActivity.buildReaderIntent(this), null));

            adapter.add(TestListItem.newTest(this, R.string.nfc_hce_change_default_reader,
                    getString(R.string.nfc_hce_change_default_reader),
                    ChangeDefaultEmulatorActivity.buildReaderIntent(this), null));

            adapter.add(TestListItem.newTest(this, R.string.nfc_hce_foreground_payment_reader,
                    getString(R.string.nfc_hce_foreground_payment_reader),
                    ForegroundPaymentEmulatorActivity.buildReaderIntent(this), null));

            adapter.add(TestListItem.newTest(this, R.string.nfc_hce_single_non_payment_reader,
                    getString(R.string.nfc_hce_single_non_payment_reader),
                    SingleNonPaymentEmulatorActivity.buildReaderIntent(this), null));

            adapter.add(TestListItem.newTest(this, R.string.nfc_hce_dual_non_payment_reader,
                    getString(R.string.nfc_hce_dual_non_payment_reader),
                    DualNonPaymentEmulatorActivity.buildReaderIntent(this), null));

            adapter.add(TestListItem.newTest(this, R.string.nfc_hce_conflicting_non_payment_reader,
                    getString(R.string.nfc_hce_conflicting_non_payment_reader),
                    ConflictingNonPaymentEmulatorActivity.buildReaderIntent(this), null));

            adapter.add(TestListItem.newTest(this, R.string.nfc_hce_foreground_non_payment_reader,
                    getString(R.string.nfc_hce_foreground_non_payment_reader),
                    ForegroundNonPaymentEmulatorActivity.buildReaderIntent(this), null));

            adapter.add(TestListItem.newTest(this, R.string.nfc_hce_throughput_reader,
                    getString(R.string.nfc_hce_throughput_reader),
                    ThroughputEmulatorActivity.buildReaderIntent(this), null));

            adapter.add(TestListItem.newTest(this, R.string.nfc_hce_tap_test_reader,
                    getString(R.string.nfc_hce_tap_test_reader),
                    TapTestEmulatorActivity.buildReaderIntent(this), null));

            adapter.add(TestListItem.newTest(this, R.string.nfc_hce_offhost_service_reader,
                    getString(R.string.nfc_hce_offhost_service_reader),
                    OffHostEmulatorActivity.buildReaderIntent(this), null));

            adapter.add(TestListItem.newTest(this, R.string.nfc_hce_on_and_offhost_service_reader,
                    getString(R.string.nfc_hce_on_and_offhost_service_reader),
                    OnAndOffHostEmulatorActivity.buildReaderIntent(this), null));

            adapter.add(TestListItem.newTest(this, R.string.nfc_hce_payment_dynamic_aids_reader,
                    getString(R.string.nfc_hce_payment_dynamic_aids_reader),
                    DynamicAidEmulatorActivity.buildReaderIntent(this), null));

            adapter.add(TestListItem.newTest(this, R.string.nfc_hce_large_num_aids_reader,
                    getString(R.string.nfc_hce_large_num_aids_reader),
                    LargeNumAidsEmulatorActivity.buildReaderIntent(this), null));

            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
            CardEmulation cardEmulation = CardEmulation.getInstance(nfcAdapter);
            if (cardEmulation.supportsAidPrefixRegistration()) {
                adapter.add(TestListItem.newTest(this, R.string.nfc_hce_payment_prefix_aids_reader,
                        getString(R.string.nfc_hce_payment_prefix_aids_reader),
                        PrefixPaymentEmulatorActivity.buildReaderIntent(this), null));

                adapter.add(TestListItem.newTest(this, R.string.nfc_hce_payment_prefix_aids_reader_2,
                        getString(R.string.nfc_hce_payment_prefix_aids_reader_2),
                        PrefixPaymentEmulator2Activity.buildReaderIntent(this), null));

                adapter.add(TestListItem.newTest(this, R.string.nfc_hce_other_prefix_aids_reader,
                        getString(R.string.nfc_hce_other_prefix_aids_reader),
                        DualNonPaymentPrefixEmulatorActivity.buildReaderIntent(this), null));

                adapter.add(TestListItem.newTest(this, R.string.nfc_hce_other_conflicting_prefix_aids_reader,
                        getString(R.string.nfc_hce_other_conflicting_prefix_aids_reader),
                        ConflictingNonPaymentPrefixEmulatorActivity.buildReaderIntent(this), null));
            }
        }

        setTestListAdapter(adapter);
    }
}
