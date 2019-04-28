/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.radio;

import android.content.Context;
import android.hardware.radio.RadioManager;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.car.radio.service.RadioStation;

import java.util.ArrayList;
import java.util.List;

/**
 * A controller for the various buttons in the manual tuner screen.
 */
public class ManualTunerController {
    /**
     * The total number of controllable buttons in the manual tuner. This value represents the
     * values 0 - 9.
     */
    private static final int NUM_OF_MANUAL_TUNER_BUTTONS = 10;

    private final StringBuilder mCurrentChannel = new StringBuilder();

    private final TextView mChannelView;

    private int mCurrentRadioBand;

    private final List<Button> mManualTunerButtons = new ArrayList<>(NUM_OF_MANUAL_TUNER_BUTTONS);

    private final String mNumberZero;
    private final String mNumberOne;
    private final String mNumberTwo;
    private final String mNumberThree;
    private final String mNumberFour;
    private final String mNumberFive;
    private final String mNumberSix;
    private final String mNumberSeven;
    private final String mNumberEight;
    private final String mNumberNine;
    private final String mPeriod;

    private ChannelValidator mChannelValidator;
    private final ChannelValidator mAmChannelValidator = new AmChannelValidator();
    private final ChannelValidator mFmChannelValidator = new FMChannelValidator();

    private final int mEnabledButtonColor;
    private final int mDisabledButtonColor;

    private View mDoneButton;
    private ManualTunerClickListener mManualTunerClickListener;

    /**
     * An interface that will perform various validations on {@link #mCurrentChannel}.
     */
    public interface ChannelValidator {
        /**
         * Returns {@code true} if the given character is allowed to be appended to the given
         * number.
         */
        boolean canAppendCharacterToNumber(@NonNull String character, @NonNull String number);

        /**
         * Returns {@code true} if the given number if a valid radio channel frequency.
         */
        boolean isValidChannel(@NonNull String number);

        /**
         * Returns an integer representation of the given number in hertz.
         */
        int convertToHz(@NonNull String number);

        /**
         * Returns {@code true} if a period (decimal point) should be appended to the given
         * number. For example, FM channels should automatically add a period if the given number
         * is over 100 or has two digits.
         */
        boolean shouldAppendPeriod(@NonNull String number);
    }

    /**
     * An interface for a class that will be notified when the done or back buttons of the manual
     * tuner has been clicked.
     */
    public interface ManualTunerClickListener {
        /**
         * Called when the back button on the manual tuner has been clicked.
         */
        void onBack();

        /**
         * Called when the done button has been clicked with the given station that the user has
         * selected.
         */
        void onDone(RadioStation station);
    }

    ManualTunerController(Context context, View container, int currentRadioBand) {
        mChannelView = container.findViewById(R.id.manual_tuner_channel);

        // Default to FM band.
        if (currentRadioBand != RadioManager.BAND_FM && currentRadioBand != RadioManager.BAND_AM) {
            currentRadioBand = RadioManager.BAND_FM;
        }

        mCurrentRadioBand = currentRadioBand;

        mChannelValidator = mCurrentRadioBand == RadioManager.BAND_AM
                ? mAmChannelValidator
                : mFmChannelValidator;

        mEnabledButtonColor = context.getColor(R.color.manual_tuner_button_text);
        mDisabledButtonColor = context.getColor(R.color.car_radio_control_button_disabled);

        mNumberZero = context.getString(R.string.manual_tuner_0);
        mNumberOne = context.getString(R.string.manual_tuner_1);
        mNumberTwo = context.getString(R.string.manual_tuner_2);
        mNumberThree = context.getString(R.string.manual_tuner_3);
        mNumberFour = context.getString(R.string.manual_tuner_4);
        mNumberFive = context.getString(R.string.manual_tuner_5);
        mNumberSix = context.getString(R.string.manual_tuner_6);
        mNumberSeven = context.getString(R.string.manual_tuner_7);
        mNumberEight = context.getString(R.string.manual_tuner_8);
        mNumberNine = context.getString(R.string.manual_tuner_9);
        mPeriod = context.getString(R.string.manual_tuner_period);

        initializeChannelButtons(container);
        initializeManualTunerButtons(container);

        updateButtonState();
    }

    /**
     * Initializes the buttons responsible for adjusting the channel to be entered by the manual
     * tuner.
     */
    private void initializeChannelButtons(View container) {
        RadioBandButton amBandButton = container.findViewById(R.id.manual_tuner_am_band);
        RadioBandButton fmBandButton = container.findViewById(R.id.manual_tuner_fm_band);
        mDoneButton = container.findViewById(R.id.manual_tuner_done_button);

        View backButton = container.findViewById(R.id.exit_manual_tuner_button);
        backButton.setOnClickListener(v -> {
            if (mManualTunerClickListener != null) {
                mManualTunerClickListener.onBack();
            }
        });

        amBandButton.setOnClickListener(v -> {
            mCurrentRadioBand = RadioManager.BAND_AM;
            mChannelValidator = mAmChannelValidator;
            amBandButton.setIsBandSelected(true);
            fmBandButton.setIsBandSelected(false);
            resetChannel();
        });

        fmBandButton.setOnClickListener(v -> {
            mCurrentRadioBand = RadioManager.BAND_FM;
            mChannelValidator = mFmChannelValidator;
            amBandButton.setIsBandSelected(false);
            fmBandButton.setIsBandSelected(true);
            resetChannel();
        });

        mDoneButton.setOnClickListener(v -> {
            if (mManualTunerClickListener == null) {
                return;
            }

            int channelFrequency = mChannelValidator.convertToHz(mCurrentChannel.toString());
            RadioStation station = new RadioStation(channelFrequency, 0 /* subChannelNumber */,
                    mCurrentRadioBand, null /* rds */);

            mManualTunerClickListener.onDone(station);
        });

        if (mCurrentRadioBand == RadioManager.BAND_AM) {
            amBandButton.setIsBandSelected(true);
        } else {
            fmBandButton.setIsBandSelected(true);
        }
    }

    /**
     * Sets up the click listeners and tags for the manual tuner buttons.
     */
    private void initializeManualTunerButtons(View container) {
        Button numberZero = container.findViewById(R.id.manual_tuner_0);
        numberZero.setOnClickListener(new TuneButtonClickListener(mNumberZero));
        numberZero.setTag(R.id.manual_tuner_button_value, mNumberZero);
        mManualTunerButtons.add(numberZero);

        Button numberOne = container.findViewById(R.id.manual_tuner_1);
        numberOne.setOnClickListener(new TuneButtonClickListener(mNumberOne));
        numberOne.setTag(R.id.manual_tuner_button_value, mNumberOne);
        mManualTunerButtons.add(numberOne);

        Button numberTwo = container.findViewById(R.id.manual_tuner_2);
        numberTwo.setOnClickListener(new TuneButtonClickListener(mNumberTwo));
        numberTwo.setTag(R.id.manual_tuner_button_value, mNumberTwo);
        mManualTunerButtons.add(numberTwo);

        Button numberThree = container.findViewById(R.id.manual_tuner_3);
        numberThree.setOnClickListener(new TuneButtonClickListener(mNumberThree));
        numberThree.setTag(R.id.manual_tuner_button_value, mNumberThree);
        mManualTunerButtons.add(numberThree);

        Button numberFour = container.findViewById(R.id.manual_tuner_4);
        numberFour.setOnClickListener(new TuneButtonClickListener(mNumberFour));
        numberFour.setTag(R.id.manual_tuner_button_value, mNumberFour);
        mManualTunerButtons.add(numberFour);

        Button numberFive = container.findViewById(R.id.manual_tuner_5);
        numberFive.setOnClickListener(new TuneButtonClickListener(mNumberFive));
        numberFive.setTag(R.id.manual_tuner_button_value, mNumberFive);
        mManualTunerButtons.add(numberFive);

        Button numberSix = container.findViewById(R.id.manual_tuner_6);
        numberSix.setOnClickListener(new TuneButtonClickListener(mNumberSix));
        numberSix.setTag(R.id.manual_tuner_button_value, mNumberSix);
        mManualTunerButtons.add(numberSix);

        Button numberSeven = container.findViewById(R.id.manual_tuner_7);
        numberSeven.setOnClickListener(new TuneButtonClickListener(mNumberSeven));
        numberSeven.setTag(R.id.manual_tuner_button_value, mNumberSeven);
        mManualTunerButtons.add(numberSeven);

        Button numberEight = container.findViewById(R.id.manual_tuner_8);
        numberEight.setOnClickListener(new TuneButtonClickListener(mNumberEight));
        numberEight.setTag(R.id.manual_tuner_button_value, mNumberEight);
        mManualTunerButtons.add(numberEight);

        Button numberNine = container.findViewById(R.id.manual_tuner_9);
        numberNine.setOnClickListener(new TuneButtonClickListener(mNumberNine));
        numberNine.setTag(R.id.manual_tuner_button_value, mNumberNine);
        mManualTunerButtons.add(numberNine);

        container.findViewById(R.id.manual_tuner_backspace)
                .setOnClickListener(new BackSpaceListener());
    }

    /**
     * Sets the given {@link ManualTunerClickListener} to be notified when the done button of the manual
     * tuner has been clicked.
     */
    void setDoneButtonListener(ManualTunerClickListener listener) {
        mManualTunerClickListener = listener;
    }

    /**
     * Iterates through all the buttons in {@link #mManualTunerButtons} and updates whether or not
     * they are enabled based on the current {@link #mChannelValidator}.
     */
    private void updateButtonState() {
        String currentChannel = mCurrentChannel.toString();

        for (int i = 0, size = mManualTunerButtons.size(); i < size; i++) {
            Button button = mManualTunerButtons.get(i);
            String value = (String) button.getTag(R.id.manual_tuner_button_value);

            boolean enabled = mChannelValidator.canAppendCharacterToNumber(value, currentChannel);

            button.setEnabled(enabled);
            button.setTextColor(enabled ? mEnabledButtonColor : mDisabledButtonColor);
        }

        mDoneButton.setEnabled(mChannelValidator.isValidChannel(currentChannel));
    }

    /**
     * A {@link ChannelValidator} for the AM band. Note this validator is for US regions.
     */
    private final class AmChannelValidator implements ChannelValidator {
        private static final int AM_LOWER_LIMIT = 530;
        private static final int AM_UPPER_LIMIT = 1700;

        @Override
        public boolean canAppendCharacterToNumber(@NonNull String character,
                @NonNull String number) {
            // There are no decimal points for AM numbers.
            if (character.equals(mPeriod)) {
                return false;
            }

            int charValue = Integer.valueOf(character);

            switch (number.length()) {
                case 0:
                    // 5 and 1 are the first digits of AM_LOWER_LIMIT and AM_UPPER_LIMIT.
                    return charValue >= 5 || charValue == 1;
                case 1:
                    // Ensure that the number is above the lower AM limit of 530.
                    return !number.equals(mNumberFive) || charValue >= 3;
                case 2:
                    // Any number is allowed to be appended if the current AM station being entered
                    // is a number in the 1000s.
                    if (String.valueOf(number.charAt(0)).equals(mNumberOne)) {
                        return true;
                    }

                    // Otherwise, only zero is allowed because AM stations go in increments of 10.
                    return character.equals(mNumberZero);
                case 3:
                    // AM station are in increments of 10, so for a 3 digit AM station, only a
                    // zero is allowed at the end. Note, no need to check if the "number" is a
                    // number in the 1000s because this should be handled by "case 2".
                    return character.equals(mNumberZero);
                default:
                    // Otherwise, just disallow the character.
                    return false;
            }
        }

        @Override
        public boolean isValidChannel(@NonNull String number) {
            if (number.length() == 0) {
                return false;
            }

            // No decimal points for AM channels.
            if (number.contains(mPeriod)) {
                return false;
            }

            int value = Integer.valueOf(number);
            return value >= AM_LOWER_LIMIT && value <= AM_UPPER_LIMIT;
        }

        @Override
        public int convertToHz(@NonNull String number) {
            // The number should already been in Hz, so just perform a straight conversion.
            return Integer.valueOf(number);
        }

        @Override
        public boolean shouldAppendPeriod(@NonNull String number) {
            // No decimal points for AM channels.
            return false;
        }
    }

    /**
     * A {@link ChannelValidator} for the FM band. Note that this validator is for US regions.
     */
    private final class FMChannelValidator implements ChannelValidator {
        private static final int FM_LOWER_LIMIT = 87900;
        private static final int FM_UPPER_LIMIT = 107900;

        /**
         * The value including the decimal point of the FM upper limit.
         */
        private static final String FM_UPPER_LIMIT_CHARACTERISTIC = "107.";

        /**
         * The lower limit of FM channels in kilohertz before the decimal point.
         */
        private static final int FM_LOWER_LIMIT_NO_DECIMAL_KHZ = 87;

        private static final String KILOHERTZ_CONVERSION_DIGITS = "000";
        private static final String KILOHERTZ_CONVERSION_DIGITS_WITH_DECIMAL = "00";

        @Override
        public boolean canAppendCharacterToNumber(@NonNull String character,
                @NonNull String number) {
            int indexOfPeriod = number.indexOf(mPeriod);

            if (character.equals(mPeriod)) {
                // Only one decimal point is allowed.
                if (indexOfPeriod != -1) {
                    return false;
                }

                // There needs to be at least two digits before a decimal point is allowed.
                return number.length() >= 2;
            }

            if (number.length() == 0) {
                // No need to check for the decimal point here because it's handled by the first
                // if case.
                int charValue = Integer.valueOf(character);

                // 8 and 1 are the first digits of FM_LOWER_LIMIT and FM_UPPER_LIMIT;
                return charValue >= 8 || charValue == 1;
            }

            if (indexOfPeriod == -1) {
                switch (number.length()) {
                    case 1:
                        // If the number is 1, then only a zero is allowed afterwards since FM
                        // channels can only go up to 108.1.
                        if (number.equals(mNumberOne)) {
                            return character.equals(mNumberZero);
                        }

                        // If the number 8, then we need to only allow 7 and above. This is because
                        // the lower limit of FM channels is 87.9.
                        if (number.equals(mNumberEight)) {
                            int numberValue = Integer.valueOf(character);
                            return numberValue >= 7;
                        }

                        // Otherwise, any number is allowed.
                        return true;

                    case 2:
                        // If there are two digits, only allow another character to be added if the
                        // resulting character will be in the 100s but less than 107.
                        return String.valueOf(number.charAt(0)).equals(mNumberOne)
                                && !character.equals(mNumberEight)
                                && !character.equals(mNumberNine);

                    case 3:
                    default:
                        // If there are already three digits, no more numbers can be added
                        // without a decimal point.
                        return false;
                }
            } else if (number.length() - 1 > indexOfPeriod) {
                // Only one number if allowed after the decimal point.
                return false;
            }

            // If the number being entered it right up on the FM upper limit, then the allowed
            // character can only be a 1 because the upper limit is 108.1.
            if (number.equals(FM_UPPER_LIMIT_CHARACTERISTIC)) {
                return character.equals(mNumberNine);
            }

            // Otherwise, FM frequencies can only end in an odd digit (e.g. 96.5 and not 96.4).
            int charValue = Integer.valueOf(character);
            return charValue % 2 == 1;
        }

        @Override
        public boolean isValidChannel(@NonNull String number) {
            if (number.length() == 0) {
                return false;
            }

            // Strip the period from the number and ensure the number string is represented in
            // kilohertz.
            String updatedNumber = convertNumberToKilohertz(number);
            int value = Integer.valueOf(updatedNumber);
            return value >= FM_LOWER_LIMIT && value <= FM_UPPER_LIMIT;
        }

        @Override
        public int convertToHz(@NonNull String number) {
            return Integer.valueOf(convertNumberToKilohertz(number));
        }

        @Override
        public boolean shouldAppendPeriod(@NonNull String number) {
            // Check if there is already a decimal point.
            if (number.contains(mPeriod)) {
                return false;
            }

            int value = Integer.valueOf(number);
            return value >= FM_LOWER_LIMIT_NO_DECIMAL_KHZ;
        }

        /**
         * Converts the given number to its kilohertz representation. For example, 87.9 will be
         * converted to 87900.
         */
        private String convertNumberToKilohertz(String number) {
            if (number.contains(mPeriod)) {
                return number.replace(mPeriod, "")
                        + KILOHERTZ_CONVERSION_DIGITS_WITH_DECIMAL;
            }

            return number + KILOHERTZ_CONVERSION_DIGITS;
        }
    }

    /**
     * Sets the {@link #mCurrentChannel} on {@link #mChannelView}. Will append a decimal point to
     * the text if necessary. This is based on the current {@link ChannelValidator}.
     */
    private void setChannelText() {
        if (mChannelValidator.shouldAppendPeriod(mCurrentChannel.toString())) {
            mCurrentChannel.append(mPeriod);
        }

        mChannelView.setText(mCurrentChannel.toString());
    }

    /**
     * Resets any radio station that may have been entered and updates the button states
     * accordingly.
     */
    private void resetChannel() {
        mChannelView.setText(null);

        // Clear the string buffer by setting the length to zero rather than allocating a new
        // one.
        mCurrentChannel.setLength(0);

        updateButtonState();
    }

    /**
     * A {@link android.view.View.OnClickListener} that handles back space clicks. It is responsible
     * for removing characters from the {@link #mChannelView} TextView.
     */
    private class BackSpaceListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (mCurrentChannel.length() == 0) {
                return;
            }

            // Since the period cannot be added manually by the user, remove it for them. Both
            // before and after the deletion of a non-period character.
            deleteLastCharacterIfPeriod();
            mCurrentChannel.deleteCharAt(mCurrentChannel.length() - 1);

            mChannelView.setText(mCurrentChannel.toString());

            updateButtonState();
        }

        /**
         * Checks if the last character in {@link ManualTunerController#mCurrentChannel} is a
         * period. If it is, then removes it.
         */
        private void deleteLastCharacterIfPeriod() {
            int lastIndex = mCurrentChannel.length() - 1;
            String lastCharacter = String.valueOf(mCurrentChannel.charAt(lastIndex));

            // If we delete a character and the resulting last character is the decimal point,
            // delete that as well.
            if (lastCharacter.equals(mPeriod)) {
                mCurrentChannel.deleteCharAt(lastIndex);
            }
        }
    }

    /**
     * A {@link android.view.View.OnClickListener} for each of the manual tuner buttons that
     * will update the number being displayed when pressed.
     */
    private class TuneButtonClickListener implements View.OnClickListener {
        private final String mValue;

        TuneButtonClickListener(String value) {
            mValue = value;
        }

        @Override
        public void onClick(View v) {
            mCurrentChannel.append(mValue);
            setChannelText();
            updateButtonState();
        }
    }
}
