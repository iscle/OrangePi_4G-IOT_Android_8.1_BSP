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

package com.google.android.car.kitchensink.hvac;

import static java.lang.Integer.toHexString;

import com.google.android.car.kitchensink.KitchenSinkActivity;
import com.google.android.car.kitchensink.R;

import android.car.CarNotConnectedException;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.hvac.CarHvacManager;
import android.hardware.automotive.vehicle.V2_0.VehicleAreaWindow;
import android.hardware.automotive.vehicle.V2_0.VehicleAreaZone;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.List;

public class HvacTestFragment extends Fragment {
    private final boolean DBG = true;
    private final String TAG = "HvacTestFragment";
    private RadioButton mRbFanPositionFace;
    private RadioButton mRbFanPositionFloor;
    private RadioButton mRbFanPositionFaceAndFloor;
    private RadioButton mRbFanPositionDefrost;
    private RadioButton mRbFanPositionDefrostAndFloor;
    private ToggleButton mTbAc;
    private ToggleButton mTbAuto;
    private ToggleButton mTbDefrostFront;
    private ToggleButton mTbDefrostRear;
    private ToggleButton mTbDual;
    private ToggleButton mTbMaxAc;
    private ToggleButton mTbMaxDefrost;
    private ToggleButton mTbRecirc;
    private TextView mTvFanSpeed;
    private TextView mTvDTemp;
    private TextView mTvPTemp;
    private TextView mTvOutsideTemp;
    private int mCurFanSpeed;
    private int mMinFanSpeed;
    private int mMaxFanSpeed;
    private float mCurDTemp;
    private float mCurPTemp;
    private float mMinTemp;
    private float mMaxTemp;
    private float mTempStep;
    private CarHvacManager mCarHvacManager;
    private int mZoneForAcOn;
    private int mZoneForSetTempD;
    private int mZoneForSetTempP;
    private int mZoneForFanSpeed;
    private int mZoneForFanPosition;

    private final CarHvacManager.CarHvacEventCallback mHvacCallback =
            new CarHvacManager.CarHvacEventCallback () {
                @Override
                public void onChangeEvent(final CarPropertyValue value) {
                    int zones = value.getAreaId();
                    switch(value.getPropertyId()) {
                        case CarHvacManager.ID_OUTSIDE_AIR_TEMP:
                            mTvOutsideTemp.setText(String.valueOf(value.getValue()));
                            break;
                        case CarHvacManager.ID_ZONED_DUAL_ZONE_ON:
                            mTbDual.setChecked((boolean)value.getValue());
                            break;
                        case CarHvacManager.ID_ZONED_AC_ON:
                            mTbAc.setChecked((boolean)value.getValue());
                            break;
                        case CarHvacManager.ID_ZONED_AUTOMATIC_MODE_ON:
                            mTbAuto.setChecked((boolean)value.getValue());
                            break;
                        case CarHvacManager.ID_ZONED_FAN_POSITION:
                            switch((int)value.getValue()) {
                                case CarHvacManager.FAN_POSITION_FACE:
                                    mRbFanPositionFace.setChecked(true);
                                    break;
                                case CarHvacManager.FAN_POSITION_FLOOR:
                                    mRbFanPositionFloor.setChecked(true);
                                    break;
                                case CarHvacManager.FAN_POSITION_FACE_AND_FLOOR:
                                    mRbFanPositionFaceAndFloor.setChecked(true);
                                    break;
                                case CarHvacManager.FAN_POSITION_DEFROST:
                                    mRbFanPositionDefrost.setChecked(true);
                                    break;
                                case CarHvacManager.FAN_POSITION_DEFROST_AND_FLOOR:
                                    mRbFanPositionDefrostAndFloor.setChecked(true);
                                    break;
                                default:
                                    if (DBG) {
                                        Log.e(TAG, "Unknown fan position: " + value.getValue());
                                    }
                                    break;
                            }
                            break;
                        case CarHvacManager.ID_ZONED_MAX_AC_ON:
                            mTbMaxAc.setChecked((boolean)value.getValue());
                            break;
                        case CarHvacManager.ID_ZONED_AIR_RECIRCULATION_ON:
                            mTbRecirc.setChecked((boolean)value.getValue());
                            break;
                        case CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT:
                            if ((zones & mZoneForFanSpeed) != 0) {
                                mCurFanSpeed = (int)value.getValue();
                                mTvFanSpeed.setText(String.valueOf(mCurFanSpeed));
                            }
                            break;
                        case CarHvacManager.ID_ZONED_TEMP_SETPOINT:
                            if ((zones & mZoneForSetTempD) != 0) {
                                mCurDTemp = (float)value.getValue();
                                mTvDTemp.setText(String.valueOf(mCurDTemp));
                            }
                            if ((zones & mZoneForSetTempP) != 0) {
                                mCurPTemp = (float)value.getValue();
                                mTvPTemp.setText(String.valueOf(mCurPTemp));
                            }
                            break;
                        case CarHvacManager.ID_ZONED_MAX_DEFROST_ON:
                            mTbMaxDefrost.setChecked((boolean)value.getValue());
                            break;
                        case CarHvacManager.ID_WINDOW_DEFROSTER_ON:
                            if((zones & VehicleAreaWindow.FRONT_WINDSHIELD) ==
                                    VehicleAreaWindow.FRONT_WINDSHIELD) {
                                mTbDefrostFront.setChecked((boolean)value.getValue());
                            }
                            if((zones & VehicleAreaWindow.REAR_WINDSHIELD) ==
                                    VehicleAreaWindow.REAR_WINDSHIELD) {
                                mTbDefrostRear.setChecked((boolean)value.getValue());
                            }
                            break;
                        default:
                            Log.d(TAG, "onChangeEvent(): unknown property id = " + value
                                    .getPropertyId());
                    }
                }

                @Override
                public void onErrorEvent(final int propertyId, final int zone) {
                    Log.w(TAG, "Error:  propertyId=0x" + toHexString(propertyId)
                            + ", zone=0x" + toHexString(zone));
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mCarHvacManager = ((KitchenSinkActivity)getActivity()).getHvacManager();
        super.onCreate(savedInstanceState);
        try {
            mCarHvacManager.registerCallback(mHvacCallback);
        } catch (CarNotConnectedException e) {
            Log.e(TAG, "Car is not connected!");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mCarHvacManager.unregisterCallback(mHvacCallback);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstance) {
        View v = inflater.inflate(R.layout.hvac_test, container, false);

        List<CarPropertyConfig> props;
        try {
            props = mCarHvacManager.getPropertyList();
        } catch (CarNotConnectedException e) {
            Log.e(TAG, "Failed to get list of properties", e);
            props = new ArrayList<>();
        }

        for(CarPropertyConfig prop : props) {
            int propId = prop.getPropertyId();

            if(DBG) {
                Log.d(TAG, prop.toString());
            }

            switch(propId) {
                case CarHvacManager.ID_OUTSIDE_AIR_TEMP:
                    configureOutsideTemp(v, prop);
                    break;
                case CarHvacManager.ID_ZONED_DUAL_ZONE_ON:
                    configureDualOn(v, prop);
                    break;
                case CarHvacManager.ID_ZONED_AC_ON:
                    configureAcOn(v, prop);
                    break;
                case CarHvacManager.ID_ZONED_FAN_POSITION:
                    configureFanPosition(v, prop);
                    break;
                case CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT:
                    configureFanSpeed(v, prop);
                    break;
                case CarHvacManager.ID_ZONED_TEMP_SETPOINT:
                    configureTempSetpoint(v, prop);
                    break;
                case CarHvacManager.ID_ZONED_AUTOMATIC_MODE_ON:
                    configureAutoModeOn(v, prop);
                    break;
                case CarHvacManager.ID_ZONED_AIR_RECIRCULATION_ON:
                    configureRecircOn(v, prop);
                    break;
                case CarHvacManager.ID_ZONED_MAX_AC_ON:
                    configureMaxAcOn(v, prop);
                    break;
                case CarHvacManager.ID_ZONED_MAX_DEFROST_ON:
                    configureMaxDefrostOn(v, prop);
                    break;
                case CarHvacManager.ID_WINDOW_DEFROSTER_ON:
                    configureDefrosterOn(v, prop);
                    break;
                default:
                    Log.w(TAG, "propertyId " + propId + " is not handled");
                    break;
            }
        }

        mTvFanSpeed = (TextView) v.findViewById(R.id.tvFanSpeed);
        mTvFanSpeed.setText(String.valueOf(mCurFanSpeed));
        mTvDTemp = (TextView) v.findViewById(R.id.tvDTemp);
        mTvDTemp.setText(String.valueOf(mCurDTemp));
        mTvPTemp = (TextView) v.findViewById(R.id.tvPTemp);
        mTvPTemp.setText(String.valueOf(mCurPTemp));
        mTvOutsideTemp = (TextView) v.findViewById(R.id.tvOutsideTemp);
        mTvOutsideTemp.setText("N/A");

        if(DBG) {
            Log.d(TAG, "Starting HvacTestFragment");
        }

        return v;
    }

    private void configureOutsideTemp(View v, CarPropertyConfig prop) {
        // Do nothing
    }

    private void configureDualOn(View v, CarPropertyConfig prop) {
        int temp = prop.getFirstAndOnlyAreaId();
        mTbDual = (ToggleButton)v.findViewById(R.id.tbDual);
        mTbDual.setEnabled(true);

        mTbDual.setOnClickListener(view -> {
            // TODO handle zone properly
            try {
                mCarHvacManager.setBooleanProperty(CarHvacManager.ID_ZONED_DUAL_ZONE_ON,temp,
                        mTbDual.isChecked());
            } catch (CarNotConnectedException e) {
                Log.e(TAG, "Failed to set HVAC boolean property", e);
            }
        });
    }

    private void configureAcOn(View v, CarPropertyConfig prop) {
        mZoneForAcOn = prop.getFirstAndOnlyAreaId();
        mTbAc = (ToggleButton)v.findViewById(R.id.tbAc);
        mTbAc.setEnabled(true);

        mTbAc.setOnClickListener(view -> {
            // TODO handle zone properly
            try {
                mCarHvacManager.setBooleanProperty(CarHvacManager.ID_ZONED_AC_ON, mZoneForAcOn,
                        mTbAc.isChecked());
            } catch (CarNotConnectedException e) {
                Log.e(TAG, "Failed to set HVAC boolean property", e);
            }
        });
    }

    private void configureAutoModeOn(View v, CarPropertyConfig prop) {
        int temp = prop.getFirstAndOnlyAreaId();
        mTbAuto = (ToggleButton)v.findViewById(R.id.tbAuto);
        mTbAuto.setEnabled(true);

        mTbAuto.setOnClickListener(view -> {
            // TODO handle zone properly
            try {
                mCarHvacManager.setBooleanProperty(CarHvacManager.ID_ZONED_AUTOMATIC_MODE_ON,temp,
                        mTbAuto.isChecked());
            } catch (CarNotConnectedException e) {
                Log.e(TAG, "Failed to set HVAC boolean property", e);
            }
        });
    }

    private void configureFanPosition(View v, CarPropertyConfig prop) {
        mZoneForFanPosition = prop.getFirstAndOnlyAreaId();
        RadioGroup rg = (RadioGroup)v.findViewById(R.id.rgFanPosition);
        rg.setOnCheckedChangeListener((group, checkedId) -> {
            int position;
            switch(checkedId) {
                case R.id.rbPositionFace:
                    position = CarHvacManager.FAN_POSITION_FACE;
                    break;
                case R.id.rbPositionFloor:
                    position = CarHvacManager.FAN_POSITION_FLOOR;
                    break;
                case R.id.rbPositionFaceAndFloor:
                    position = CarHvacManager.FAN_POSITION_FACE_AND_FLOOR;
                    break;
                case R.id.rbPositionDefrost:
                    position = CarHvacManager.FAN_POSITION_DEFROST;
                    break;
                case R.id.rbPositionDefrostAndFloor:
                    position = CarHvacManager.FAN_POSITION_DEFROST_AND_FLOOR;
                    break;
                default:
                    throw new IllegalStateException("Unexpected fan position: " + checkedId);
            }
            try {
                mCarHvacManager.setIntProperty(CarHvacManager.ID_ZONED_FAN_POSITION,
                        mZoneForFanPosition,
                        position);
            } catch (CarNotConnectedException e) {
                Log.e(TAG, "Failed to set HVAC integer property", e);
            }
        });

        mRbFanPositionFace = (RadioButton)v.findViewById(R.id.rbPositionFace);
        mRbFanPositionFace.setClickable(true);
        mRbFanPositionFloor = (RadioButton)v.findViewById(R.id.rbPositionFloor);
        mRbFanPositionFloor.setClickable(true);
        mRbFanPositionFaceAndFloor = (RadioButton)v.findViewById(R.id.rbPositionFaceAndFloor);
        mRbFanPositionFaceAndFloor.setClickable(true);
        mRbFanPositionDefrost = (RadioButton)v.findViewById(R.id.rbPositionDefrost);
        mRbFanPositionDefrost.setClickable(true);
        mRbFanPositionDefrostAndFloor = (RadioButton)v.findViewById(R.id.rbPositionDefrostAndFloor);
        mRbFanPositionDefrostAndFloor.setClickable(true);
    }

    private void configureFanSpeed(View v, CarPropertyConfig prop) {
        mMinFanSpeed = ((Integer)prop.getMinValue()).intValue();
        mMaxFanSpeed = ((Integer)prop.getMaxValue()).intValue();
        mZoneForFanSpeed = prop.getFirstAndOnlyAreaId();
        try {
            mCurFanSpeed = mCarHvacManager.getIntProperty(
                    CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT,
                    mZoneForFanSpeed);
        } catch (CarNotConnectedException e) {
            Log.e(TAG, "Failed to get HVAC int property", e);
        }

        Button btnFanSpeedUp = (Button) v.findViewById(R.id.btnFanSpeedUp);
        btnFanSpeedUp.setEnabled(true);
        btnFanSpeedUp.setOnClickListener(view -> {
            if (mCurFanSpeed < mMaxFanSpeed) {
                mCurFanSpeed++;
                mTvFanSpeed.setText(String.valueOf(mCurFanSpeed));
                try {
                    mCarHvacManager.setIntProperty(CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT,
                            mZoneForFanSpeed, mCurFanSpeed);
                } catch (CarNotConnectedException e) {
                    Log.e(TAG, "Failed to set HVAC int property", e);
                }
            }
        });

        Button btnFanSpeedDn = (Button) v.findViewById(R.id.btnFanSpeedDn);
        btnFanSpeedDn.setEnabled(true);
        btnFanSpeedDn.setOnClickListener(view -> {
            if (mCurFanSpeed > mMinFanSpeed) {
                mCurFanSpeed--;
                mTvFanSpeed.setText(String.valueOf(mCurFanSpeed));
                try {
                    mCarHvacManager.setIntProperty(CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT,
                            mZoneForFanSpeed, mCurFanSpeed);
                } catch (CarNotConnectedException e) {
                    Log.e(TAG, "Failed to set HVAC fan speed property", e);
                }
            }
        });
    }

    private void configureTempSetpoint(View v, CarPropertyConfig prop) {
        mMinTemp = ((Float)prop.getMinValue()).floatValue();
        mMaxTemp = ((Float)prop.getMaxValue()).floatValue();

        if (mMaxTemp > 50) {
            // Assume it's Fahrenheit
            mTempStep = 1.0f;
        } else {
            // Assume it's Celsius
            mTempStep = 0.5f;
        }
        mZoneForSetTempD = 0;
        if (prop.hasArea(VehicleAreaZone.ROW_1_LEFT)) {
            mZoneForSetTempD = VehicleAreaZone.ROW_1_LEFT;
        }
        mZoneForSetTempP = 0;
        if (prop.hasArea(VehicleAreaZone.ROW_1_RIGHT)) {
            mZoneForSetTempP = VehicleAreaZone.ROW_1_RIGHT;
        }
        int[] areas = prop.getAreaIds();
        if (mZoneForSetTempD == 0 && areas.length > 1) {
            mZoneForSetTempD = areas[0];
        }
        if (mZoneForSetTempP == 0 && areas.length > 2) {
            mZoneForSetTempP = areas[1];
        }
        Button btnDTempUp = (Button) v.findViewById(R.id.btnDTempUp);
        if (mZoneForSetTempD != 0) {
            try {
                mCurDTemp = mCarHvacManager.getFloatProperty(
                        CarHvacManager.ID_ZONED_TEMP_SETPOINT,
                        mZoneForSetTempD);
                if (mCurDTemp < mMinTemp) {
                    mCurDTemp = mMinTemp;
                } else if (mCurDTemp > mMaxTemp) {
                    mCurDTemp = mMaxTemp;
                }
            } catch (CarNotConnectedException e) {
                Log.e(TAG, "Failed to get HVAC zoned temp property", e);
            }
            btnDTempUp.setEnabled(true);
            btnDTempUp.setOnClickListener(view -> {
                if(mCurDTemp < mMaxTemp) {
                    mCurDTemp += mTempStep;
                    mTvDTemp.setText(String.valueOf(mCurDTemp));
                    try {
                        mCarHvacManager.setFloatProperty(
                                CarHvacManager.ID_ZONED_TEMP_SETPOINT,
                                mZoneForSetTempD, mCurDTemp);
                    } catch (CarNotConnectedException e) {
                        Log.e(TAG, "Failed to set HVAC zoned temp property", e);
                    }
                }
            });

            Button btnDTempDn = (Button) v.findViewById(R.id.btnDTempDn);
            btnDTempDn.setEnabled(true);
            btnDTempDn.setOnClickListener(view -> {
                if(mCurDTemp > mMinTemp) {
                    mCurDTemp -= mTempStep;
                    mTvDTemp.setText(String.valueOf(mCurDTemp));
                    try {
                        mCarHvacManager.setFloatProperty(
                                CarHvacManager.ID_ZONED_TEMP_SETPOINT,
                                mZoneForSetTempD, mCurDTemp);
                    } catch (CarNotConnectedException e) {
                        Log.e(TAG, "Failed to set HVAC zoned temp property", e);
                    }
                }
            });
        } else {
            btnDTempUp.setEnabled(false);
        }

        Button btnPTempUp = (Button) v.findViewById(R.id.btnPTempUp);
        if (mZoneForSetTempP !=0 ) {
            try {
                mCurPTemp = mCarHvacManager.getFloatProperty(
                        CarHvacManager.ID_ZONED_TEMP_SETPOINT,
                        mZoneForSetTempP);
                if (mCurPTemp < mMinTemp) {
                    mCurPTemp = mMinTemp;
                } else if (mCurPTemp > mMaxTemp) {
                    mCurPTemp = mMaxTemp;
                }
            } catch (CarNotConnectedException e) {
                Log.e(TAG, "Failed to get HVAC zoned temp property", e);
            }
            btnPTempUp.setEnabled(true);
            btnPTempUp.setOnClickListener(view -> {
                if (mCurPTemp < mMaxTemp) {
                    mCurPTemp += mTempStep;
                    mTvPTemp.setText(String.valueOf(mCurPTemp));
                    try {
                        mCarHvacManager.setFloatProperty(
                                CarHvacManager.ID_ZONED_TEMP_SETPOINT,
                                mZoneForSetTempP, mCurPTemp);
                    } catch (CarNotConnectedException e) {
                        Log.e(TAG, "Failed to set HVAC zoned temp property", e);
                    }
                }
            });

            Button btnPTempDn = (Button) v.findViewById(R.id.btnPTempDn);
            btnPTempDn.setEnabled(true);
            btnPTempDn.setOnClickListener(view -> {
                if (mCurPTemp > mMinTemp) {
                    mCurPTemp -= mTempStep;
                    mTvPTemp.setText(String.valueOf(mCurPTemp));
                    try {
                        mCarHvacManager.setFloatProperty(
                                CarHvacManager.ID_ZONED_TEMP_SETPOINT,
                                mZoneForSetTempP, mCurPTemp);
                    } catch (CarNotConnectedException e) {
                        Log.e(TAG, "Failed to set HVAC zoned temp property", e);
                    }
                }
            });
        } else {
            btnPTempUp.setEnabled(false);
        }
    }

    private void configureDefrosterOn(View v, CarPropertyConfig prop1) {
        if (prop1.hasArea(VehicleAreaWindow.FRONT_WINDSHIELD)) {
            mTbDefrostFront = (ToggleButton) v.findViewById(R.id.tbDefrostFront);
            mTbDefrostFront.setEnabled(true);
            mTbDefrostFront.setOnClickListener(view -> {
                try {
                    mCarHvacManager.setBooleanProperty(CarHvacManager.ID_WINDOW_DEFROSTER_ON,
                            VehicleAreaWindow.FRONT_WINDSHIELD,
                            mTbDefrostFront.isChecked());
                } catch (CarNotConnectedException e) {
                    Log.e(TAG, "Failed to set HVAC window defroster property", e);
                }
            });
        }
        if (prop1.hasArea(VehicleAreaWindow.REAR_WINDSHIELD)) {
            mTbDefrostRear = (ToggleButton) v.findViewById(R.id.tbDefrostRear);
            mTbDefrostRear.setEnabled(true);
            mTbDefrostRear.setOnClickListener(view -> {
                try {
                    mCarHvacManager.setBooleanProperty(CarHvacManager.ID_WINDOW_DEFROSTER_ON,
                            VehicleAreaWindow.REAR_WINDSHIELD,
                            mTbDefrostRear.isChecked());
                } catch (CarNotConnectedException e) {
                    Log.e(TAG, "Failed to set HVAC window defroster property", e);
                }
            });
        }
    }

    private void configureRecircOn(View v, CarPropertyConfig prop) {
        int temp = prop.getFirstAndOnlyAreaId();
        mTbRecirc = (ToggleButton)v.findViewById(R.id.tbRecirc);
        mTbRecirc.setEnabled(true);

        mTbRecirc.setOnClickListener(view -> {
            // TODO handle zone properly
            try {
                mCarHvacManager.setBooleanProperty(CarHvacManager.ID_ZONED_AIR_RECIRCULATION_ON,
                        temp, mTbRecirc.isChecked());
            } catch (CarNotConnectedException e) {
                Log.e(TAG, "Failed to set HVAC boolean property", e);
            }
        });
    }

    private void configureMaxAcOn(View v, CarPropertyConfig prop) {
        int temp = prop.getFirstAndOnlyAreaId();
        mTbMaxAc = (ToggleButton)v.findViewById(R.id.tbMaxAc);
        mTbMaxAc.setEnabled(true);

        mTbMaxAc.setOnClickListener(view -> {
            // TODO handle zone properly
            try {
                mCarHvacManager.setBooleanProperty(CarHvacManager.ID_ZONED_MAX_AC_ON,temp,
                        mTbMaxAc.isChecked());
            } catch (CarNotConnectedException e) {
                Log.e(TAG, "Failed to set HVAC boolean property", e);
            }
        });
    }

    private void configureMaxDefrostOn(View v, CarPropertyConfig prop) {
        int temp = prop.getFirstAndOnlyAreaId();
        mTbMaxDefrost = (ToggleButton)v.findViewById(R.id.tbMaxDefrost);
        mTbMaxDefrost.setEnabled(true);

        mTbMaxDefrost.setOnClickListener(view -> {
            // TODO handle zone properly
            try {
                mCarHvacManager.setBooleanProperty(CarHvacManager.ID_ZONED_MAX_DEFROST_ON,temp,
                        mTbMaxDefrost.isChecked());
            } catch (CarNotConnectedException e) {
                Log.e(TAG, "Failed to set HVAC boolean property", e);
            }
        });
    }
}
