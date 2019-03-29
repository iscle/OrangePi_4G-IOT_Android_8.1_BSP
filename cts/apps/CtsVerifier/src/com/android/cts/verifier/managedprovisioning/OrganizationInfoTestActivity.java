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

package com.android.cts.verifier.managedprovisioning;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * Test class to verify Organization Info.
 */
public class OrganizationInfoTestActivity extends PassFailButtons.Activity
        implements View.OnClickListener {

    public static final String EXTRA_ORGANIZATION_NAME = "extra_organization_name";
    public static final String EXTRA_ORGANIZATION_COLOR = "extra_organization_color";

    private int mOrganizationColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.organization_info);
        setPassFailButtonClickListeners();
        setButtonClickListeners();
    }

    private void setButtonClickListeners() {
        findViewById(R.id.organization_info_set_button).setOnClickListener(this);
        findViewById(R.id.go_button).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.organization_info_set_button) {
            EditText organizationNameEditText = (EditText) findViewById(
                    R.id.organization_name_edit_text);
            Intent intent = new Intent(ByodHelperActivity.ACTION_SET_ORGANIZATION_INFO);
            intent.putExtra(EXTRA_ORGANIZATION_NAME, organizationNameEditText.getText().toString());
            if (isOrganizationColorSet()) {
                intent.putExtra(EXTRA_ORGANIZATION_COLOR, mOrganizationColor);
            }
            startActivity(intent);
        } else if (view.getId() == R.id.go_button) {
            Intent intent = new Intent(ByodHelperActivity.ACTION_LAUNCH_CONFIRM_WORK_CREDENTIALS);
            startActivity(intent);
        }
    }

    private boolean isOrganizationColorSet() {
        EditText organizationColorEditText = (EditText) findViewById(
                R.id.organization_color_edit_text);
        try {
            mOrganizationColor = Color.parseColor(organizationColorEditText.getText().toString());
        } catch (Exception e) {
            Toast.makeText(this, "Not a valid Color value", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
}
