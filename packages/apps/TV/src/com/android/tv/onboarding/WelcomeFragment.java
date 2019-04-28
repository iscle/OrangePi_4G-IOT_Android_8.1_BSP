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

package com.android.tv.onboarding;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v17.leanback.app.OnboardingFragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.android.tv.R;
import com.android.tv.common.ui.setup.SetupActionHelper;
import com.android.tv.common.ui.setup.animation.SetupAnimationHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * A fragment for the onboarding welcome screen.
 */
public class WelcomeFragment extends OnboardingFragment {
    public static final String ACTION_CATEGORY = "comgoogle.android.tv.onboarding.WelcomeFragment";
    public static final int ACTION_NEXT = 1;

    private static final long START_DELAY_CLOUD_MS = 33;
    private static final long START_DELAY_TV_MS = 567;
    private static final long START_DELAY_TV_CONTENTS_MS = 833;
    private static final long START_DELAY_SHADOW_MS = 567;

    private static final long VIDEO_FADE_OUT_DURATION_MS = 333;

    private static final long BLUE_SCREEN_HOLD_DURATION_MS = 1500;

    // TODO: Use animator list xml.
    private static final int[] TV_FRAMES_1_START = {
            R.drawable.tv_1a_01,
            R.drawable.tv_1a_02,
            R.drawable.tv_1a_03,
            R.drawable.tv_1a_04,
            R.drawable.tv_1a_05,
            R.drawable.tv_1a_06,
            R.drawable.tv_1a_07,
            R.drawable.tv_1a_08,
            R.drawable.tv_1a_09,
            R.drawable.tv_1a_10,
            R.drawable.tv_1a_11,
            R.drawable.tv_1a_12,
            R.drawable.tv_1a_13,
            R.drawable.tv_1a_14,
            R.drawable.tv_1a_15,
            R.drawable.tv_1a_16,
            R.drawable.tv_1a_17,
            R.drawable.tv_1a_18,
            R.drawable.tv_1a_19,
            R.drawable.tv_1a_20
    };

    private static final int[] TV_FRAMES_1_END = {
            R.drawable.tv_1b_01,
            R.drawable.tv_1b_02,
            R.drawable.tv_1b_03,
            R.drawable.tv_1b_04,
            R.drawable.tv_1b_05,
            R.drawable.tv_1b_06,
            R.drawable.tv_1b_07,
            R.drawable.tv_1b_08,
            R.drawable.tv_1b_09,
            R.drawable.tv_1b_10,
            R.drawable.tv_1b_11
    };

    private static final int[] TV_FRAMES_2_START = {
            R.drawable.tv_5a_0,
            R.drawable.tv_5a_1,
            R.drawable.tv_5a_2,
            R.drawable.tv_5a_3,
            R.drawable.tv_5a_4,
            R.drawable.tv_5a_5,
            R.drawable.tv_5a_6,
            R.drawable.tv_5a_7,
            R.drawable.tv_5a_8,
            R.drawable.tv_5a_9,
            R.drawable.tv_5a_10,
            R.drawable.tv_5a_11,
            R.drawable.tv_5a_12,
            R.drawable.tv_5a_13,
            R.drawable.tv_5a_14,
            R.drawable.tv_5a_15,
            R.drawable.tv_5a_16,
            R.drawable.tv_5a_17,
            R.drawable.tv_5a_18,
            R.drawable.tv_5a_19,
            R.drawable.tv_5a_20,
            R.drawable.tv_5a_21,
            R.drawable.tv_5a_22,
            R.drawable.tv_5a_23,
            R.drawable.tv_5a_24,
            R.drawable.tv_5a_25,
            R.drawable.tv_5a_26,
            R.drawable.tv_5a_27,
            R.drawable.tv_5a_28,
            R.drawable.tv_5a_29,
            R.drawable.tv_5a_30,
            R.drawable.tv_5a_31,
            R.drawable.tv_5a_32,
            R.drawable.tv_5a_33,
            R.drawable.tv_5a_34,
            R.drawable.tv_5a_35,
            R.drawable.tv_5a_36,
            R.drawable.tv_5a_37,
            R.drawable.tv_5a_38,
            R.drawable.tv_5a_39,
            R.drawable.tv_5a_40,
            R.drawable.tv_5a_41,
            R.drawable.tv_5a_42,
            R.drawable.tv_5a_43,
            R.drawable.tv_5a_44,
            R.drawable.tv_5a_45,
            R.drawable.tv_5a_46,
            R.drawable.tv_5a_47,
            R.drawable.tv_5a_48,
            R.drawable.tv_5a_49,
            R.drawable.tv_5a_50,
            R.drawable.tv_5a_51,
            R.drawable.tv_5a_52,
            R.drawable.tv_5a_53,
            R.drawable.tv_5a_54,
            R.drawable.tv_5a_55,
            R.drawable.tv_5a_56,
            R.drawable.tv_5a_57,
            R.drawable.tv_5a_58,
            R.drawable.tv_5a_59,
            R.drawable.tv_5a_60,
            R.drawable.tv_5a_61,
            R.drawable.tv_5a_62,
            R.drawable.tv_5a_63,
            R.drawable.tv_5a_64,
            R.drawable.tv_5a_65,
            R.drawable.tv_5a_66,
            R.drawable.tv_5a_67,
            R.drawable.tv_5a_68,
            R.drawable.tv_5a_69,
            R.drawable.tv_5a_70,
            R.drawable.tv_5a_71,
            R.drawable.tv_5a_72,
            R.drawable.tv_5a_73,
            R.drawable.tv_5a_74,
            R.drawable.tv_5a_75,
            R.drawable.tv_5a_76,
            R.drawable.tv_5a_77,
            R.drawable.tv_5a_78,
            R.drawable.tv_5a_79,
            R.drawable.tv_5a_80,
            R.drawable.tv_5a_81,
            R.drawable.tv_5a_82,
            R.drawable.tv_5a_83,
            R.drawable.tv_5a_84,
            R.drawable.tv_5a_85,
            R.drawable.tv_5a_86,
            R.drawable.tv_5a_87,
            R.drawable.tv_5a_88,
            R.drawable.tv_5a_89,
            R.drawable.tv_5a_90,
            R.drawable.tv_5a_91,
            R.drawable.tv_5a_92,
            R.drawable.tv_5a_93,
            R.drawable.tv_5a_94,
            R.drawable.tv_5a_95,
            R.drawable.tv_5a_96,
            R.drawable.tv_5a_97,
            R.drawable.tv_5a_98,
            R.drawable.tv_5a_99,
            R.drawable.tv_5a_100,
            R.drawable.tv_5a_101,
            R.drawable.tv_5a_102,
            R.drawable.tv_5a_103,
            R.drawable.tv_5a_104,
            R.drawable.tv_5a_105,
            R.drawable.tv_5a_106,
            R.drawable.tv_5a_107,
            R.drawable.tv_5a_108,
            R.drawable.tv_5a_109,
            R.drawable.tv_5a_110,
            R.drawable.tv_5a_111,
            R.drawable.tv_5a_112,
            R.drawable.tv_5a_113,
            R.drawable.tv_5a_114,
            R.drawable.tv_5a_115,
            R.drawable.tv_5a_116,
            R.drawable.tv_5a_117,
            R.drawable.tv_5a_118,
            R.drawable.tv_5a_119,
            R.drawable.tv_5a_120,
            R.drawable.tv_5a_121,
            R.drawable.tv_5a_122,
            R.drawable.tv_5a_123,
            R.drawable.tv_5a_124,
            R.drawable.tv_5a_125,
            R.drawable.tv_5a_126,
            R.drawable.tv_5a_127,
            R.drawable.tv_5a_128,
            R.drawable.tv_5a_129,
            R.drawable.tv_5a_130,
            R.drawable.tv_5a_131,
            R.drawable.tv_5a_132,
            R.drawable.tv_5a_133,
            R.drawable.tv_5a_134,
            R.drawable.tv_5a_135,
            R.drawable.tv_5a_136,
            R.drawable.tv_5a_137,
            R.drawable.tv_5a_138,
            R.drawable.tv_5a_139,
            R.drawable.tv_5a_140,
            R.drawable.tv_5a_141,
            R.drawable.tv_5a_142,
            R.drawable.tv_5a_143,
            R.drawable.tv_5a_144,
            R.drawable.tv_5a_145,
            R.drawable.tv_5a_146,
            R.drawable.tv_5a_147,
            R.drawable.tv_5a_148,
            R.drawable.tv_5a_149,
            R.drawable.tv_5a_150,
            R.drawable.tv_5a_151,
            R.drawable.tv_5a_152,
            R.drawable.tv_5a_153,
            R.drawable.tv_5a_154,
            R.drawable.tv_5a_155,
            R.drawable.tv_5a_156,
            R.drawable.tv_5a_157,
            R.drawable.tv_5a_158,
            R.drawable.tv_5a_159,
            R.drawable.tv_5a_160,
            R.drawable.tv_5a_161,
            R.drawable.tv_5a_162,
            R.drawable.tv_5a_163,
            R.drawable.tv_5a_164,
            R.drawable.tv_5a_165,
            R.drawable.tv_5a_166,
            R.drawable.tv_5a_167,
            R.drawable.tv_5a_168,
            R.drawable.tv_5a_169,
            R.drawable.tv_5a_170,
            R.drawable.tv_5a_171,
            R.drawable.tv_5a_172,
            R.drawable.tv_5a_173,
            R.drawable.tv_5a_174,
            R.drawable.tv_5a_175,
            R.drawable.tv_5a_176,
            R.drawable.tv_5a_177,
            R.drawable.tv_5a_178,
            R.drawable.tv_5a_179,
            R.drawable.tv_5a_180,
            R.drawable.tv_5a_181,
            R.drawable.tv_5a_182,
            R.drawable.tv_5a_183,
            R.drawable.tv_5a_184,
            R.drawable.tv_5a_185,
            R.drawable.tv_5a_186,
            R.drawable.tv_5a_187,
            R.drawable.tv_5a_188,
            R.drawable.tv_5a_189,
            R.drawable.tv_5a_190,
            R.drawable.tv_5a_191,
            R.drawable.tv_5a_192,
            R.drawable.tv_5a_193,
            R.drawable.tv_5a_194,
            R.drawable.tv_5a_195,
            R.drawable.tv_5a_196,
            R.drawable.tv_5a_197,
            R.drawable.tv_5a_198,
            R.drawable.tv_5a_199,
            R.drawable.tv_5a_200,
            R.drawable.tv_5a_201,
            R.drawable.tv_5a_202,
            R.drawable.tv_5a_203,
            R.drawable.tv_5a_204,
            R.drawable.tv_5a_205,
            R.drawable.tv_5a_206,
            R.drawable.tv_5a_207,
            R.drawable.tv_5a_208,
            R.drawable.tv_5a_209,
            R.drawable.tv_5a_210,
            R.drawable.tv_5a_211,
            R.drawable.tv_5a_212,
            R.drawable.tv_5a_213,
            R.drawable.tv_5a_214,
            R.drawable.tv_5a_215,
            R.drawable.tv_5a_216,
            R.drawable.tv_5a_217,
            R.drawable.tv_5a_218,
            R.drawable.tv_5a_219,
            R.drawable.tv_5a_220,
            R.drawable.tv_5a_221,
            R.drawable.tv_5a_222,
            R.drawable.tv_5a_223,
            R.drawable.tv_5a_224
    };

    private static final int[] TV_FRAMES_3_BLUE_ARROW = {
            R.drawable.arrow_blue_00,
            R.drawable.arrow_blue_01,
            R.drawable.arrow_blue_02,
            R.drawable.arrow_blue_03,
            R.drawable.arrow_blue_04,
            R.drawable.arrow_blue_05,
            R.drawable.arrow_blue_06,
            R.drawable.arrow_blue_07,
            R.drawable.arrow_blue_08,
            R.drawable.arrow_blue_09,
            R.drawable.arrow_blue_10,
            R.drawable.arrow_blue_11,
            R.drawable.arrow_blue_12,
            R.drawable.arrow_blue_13,
            R.drawable.arrow_blue_14,
            R.drawable.arrow_blue_15,
            R.drawable.arrow_blue_16,
            R.drawable.arrow_blue_17,
            R.drawable.arrow_blue_18,
            R.drawable.arrow_blue_19,
            R.drawable.arrow_blue_20,
            R.drawable.arrow_blue_21,
            R.drawable.arrow_blue_22,
            R.drawable.arrow_blue_23,
            R.drawable.arrow_blue_24,
            R.drawable.arrow_blue_25,
            R.drawable.arrow_blue_26,
            R.drawable.arrow_blue_27,
            R.drawable.arrow_blue_28,
            R.drawable.arrow_blue_29,
            R.drawable.arrow_blue_30,
            R.drawable.arrow_blue_31,
            R.drawable.arrow_blue_32,
            R.drawable.arrow_blue_33,
            R.drawable.arrow_blue_34,
            R.drawable.arrow_blue_35,
            R.drawable.arrow_blue_36,
            R.drawable.arrow_blue_37,
            R.drawable.arrow_blue_38,
            R.drawable.arrow_blue_39,
            R.drawable.arrow_blue_40,
            R.drawable.arrow_blue_41,
            R.drawable.arrow_blue_42,
            R.drawable.arrow_blue_43,
            R.drawable.arrow_blue_44,
            R.drawable.arrow_blue_45,
            R.drawable.arrow_blue_46,
            R.drawable.arrow_blue_47,
            R.drawable.arrow_blue_48,
            R.drawable.arrow_blue_49,
            R.drawable.arrow_blue_50,
            R.drawable.arrow_blue_51,
            R.drawable.arrow_blue_52,
            R.drawable.arrow_blue_53,
            R.drawable.arrow_blue_54,
            R.drawable.arrow_blue_55,
            R.drawable.arrow_blue_56,
            R.drawable.arrow_blue_57,
            R.drawable.arrow_blue_58,
            R.drawable.arrow_blue_59,
            R.drawable.arrow_blue_60
    };

    private static final int[] TV_FRAMES_3_BLUE_START = {
            R.drawable.tv_2a_01,
            R.drawable.tv_2a_02,
            R.drawable.tv_2a_03,
            R.drawable.tv_2a_04,
            R.drawable.tv_2a_05,
            R.drawable.tv_2a_06,
            R.drawable.tv_2a_07,
            R.drawable.tv_2a_08,
            R.drawable.tv_2a_09,
            R.drawable.tv_2a_10,
            R.drawable.tv_2a_11,
            R.drawable.tv_2a_12,
            R.drawable.tv_2a_13,
            R.drawable.tv_2a_14,
            R.drawable.tv_2a_15,
            R.drawable.tv_2a_16,
            R.drawable.tv_2a_17,
            R.drawable.tv_2a_18,
            R.drawable.tv_2a_19
    };

    private static final int[] TV_FRAMES_3_BLUE_END = {
            R.drawable.tv_2b_01,
            R.drawable.tv_2b_02,
            R.drawable.tv_2b_03,
            R.drawable.tv_2b_04,
            R.drawable.tv_2b_05,
            R.drawable.tv_2b_06,
            R.drawable.tv_2b_07,
            R.drawable.tv_2b_08,
            R.drawable.tv_2b_09,
            R.drawable.tv_2b_10,
            R.drawable.tv_2b_11,
            R.drawable.tv_2b_12,
            R.drawable.tv_2b_13,
            R.drawable.tv_2b_14,
            R.drawable.tv_2b_15,
            R.drawable.tv_2b_16,
            R.drawable.tv_2b_17,
            R.drawable.tv_2b_18,
            R.drawable.tv_2b_19
    };

    private static final int[] TV_FRAMES_3_ORANGE_ARROW = {
            R.drawable.arrow_orange_180,
            R.drawable.arrow_orange_181,
            R.drawable.arrow_orange_182,
            R.drawable.arrow_orange_183,
            R.drawable.arrow_orange_184,
            R.drawable.arrow_orange_185,
            R.drawable.arrow_orange_186,
            R.drawable.arrow_orange_187,
            R.drawable.arrow_orange_188,
            R.drawable.arrow_orange_189,
            R.drawable.arrow_orange_190,
            R.drawable.arrow_orange_191,
            R.drawable.arrow_orange_192,
            R.drawable.arrow_orange_193,
            R.drawable.arrow_orange_194,
            R.drawable.arrow_orange_195,
            R.drawable.arrow_orange_196,
            R.drawable.arrow_orange_197,
            R.drawable.arrow_orange_198,
            R.drawable.arrow_orange_199,
            R.drawable.arrow_orange_200,
            R.drawable.arrow_orange_201,
            R.drawable.arrow_orange_202,
            R.drawable.arrow_orange_203,
            R.drawable.arrow_orange_204,
            R.drawable.arrow_orange_205,
            R.drawable.arrow_orange_206,
            R.drawable.arrow_orange_207,
            R.drawable.arrow_orange_208,
            R.drawable.arrow_orange_209,
            R.drawable.arrow_orange_210,
            R.drawable.arrow_orange_211,
            R.drawable.arrow_orange_212,
            R.drawable.arrow_orange_213,
            R.drawable.arrow_orange_214,
            R.drawable.arrow_orange_215,
            R.drawable.arrow_orange_216,
            R.drawable.arrow_orange_217,
            R.drawable.arrow_orange_218,
            R.drawable.arrow_orange_219,
            R.drawable.arrow_orange_220,
            R.drawable.arrow_orange_221,
            R.drawable.arrow_orange_222,
            R.drawable.arrow_orange_223,
            R.drawable.arrow_orange_224,
            R.drawable.arrow_orange_225,
            R.drawable.arrow_orange_226,
            R.drawable.arrow_orange_227,
            R.drawable.arrow_orange_228,
            R.drawable.arrow_orange_229,
            R.drawable.arrow_orange_230,
            R.drawable.arrow_orange_231,
            R.drawable.arrow_orange_232,
            R.drawable.arrow_orange_233,
            R.drawable.arrow_orange_234,
            R.drawable.arrow_orange_235,
            R.drawable.arrow_orange_236,
            R.drawable.arrow_orange_237,
            R.drawable.arrow_orange_238,
            R.drawable.arrow_orange_239,
            R.drawable.arrow_orange_240
    };

    private static final int[] TV_FRAMES_3_ORANGE_START = {
            R.drawable.tv_2c_01,
            R.drawable.tv_2c_02,
            R.drawable.tv_2c_03,
            R.drawable.tv_2c_04,
            R.drawable.tv_2c_05,
            R.drawable.tv_2c_06,
            R.drawable.tv_2c_07,
            R.drawable.tv_2c_08,
            R.drawable.tv_2c_09,
            R.drawable.tv_2c_10,
            R.drawable.tv_2c_11,
            R.drawable.tv_2c_12,
            R.drawable.tv_2c_13,
            R.drawable.tv_2c_14,
            R.drawable.tv_2c_15,
            R.drawable.tv_2c_16
    };

    private static final int[] TV_FRAMES_4_START = {
            R.drawable.tv_3a_01,
            R.drawable.tv_3a_02,
            R.drawable.tv_3a_03,
            R.drawable.tv_3a_04,
            R.drawable.tv_3a_05,
            R.drawable.tv_3a_06,
            R.drawable.tv_3a_07,
            R.drawable.tv_3a_08,
            R.drawable.tv_3a_09,
            R.drawable.tv_3a_10,
            R.drawable.tv_3a_11,
            R.drawable.tv_3a_12,
            R.drawable.tv_3a_13,
            R.drawable.tv_3a_14,
            R.drawable.tv_3a_15,
            R.drawable.tv_3a_16,
            R.drawable.tv_3a_17,
            R.drawable.tv_3b_75,
            R.drawable.tv_3b_76,
            R.drawable.tv_3b_77,
            R.drawable.tv_3b_78,
            R.drawable.tv_3b_79,
            R.drawable.tv_3b_80,
            R.drawable.tv_3b_81,
            R.drawable.tv_3b_82,
            R.drawable.tv_3b_83,
            R.drawable.tv_3b_84,
            R.drawable.tv_3b_85,
            R.drawable.tv_3b_86,
            R.drawable.tv_3b_87,
            R.drawable.tv_3b_88,
            R.drawable.tv_3b_89,
            R.drawable.tv_3b_90,
            R.drawable.tv_3b_91,
            R.drawable.tv_3b_92,
            R.drawable.tv_3b_93,
            R.drawable.tv_3b_94,
            R.drawable.tv_3b_95,
            R.drawable.tv_3b_96,
            R.drawable.tv_3b_97,
            R.drawable.tv_3b_98,
            R.drawable.tv_3b_99,
            R.drawable.tv_3b_100,
            R.drawable.tv_3b_101,
            R.drawable.tv_3b_102,
            R.drawable.tv_3b_103,
            R.drawable.tv_3b_104,
            R.drawable.tv_3b_105,
            R.drawable.tv_3b_106,
            R.drawable.tv_3b_107,
            R.drawable.tv_3b_108,
            R.drawable.tv_3b_109,
            R.drawable.tv_3b_110,
            R.drawable.tv_3b_111,
            R.drawable.tv_3b_112,
            R.drawable.tv_3b_113,
            R.drawable.tv_3b_114,
            R.drawable.tv_3b_115,
            R.drawable.tv_3b_116,
            R.drawable.tv_3b_117,
            R.drawable.tv_3b_118
    };

    private String[] mPageTitles;
    private String[] mPageDescriptions;

    private ImageView mTvContentView;
    private ImageView mArrowView;

    private Animator mAnimator;

    public WelcomeFragment() {
        setExitTransition(new SetupAnimationHelper.TransitionBuilder()
                .setSlideEdge(Gravity.START)
                .setParentIdsForDelay(new int[]{R.id.onboarding_fragment_root})
                .build());
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        initialize();
    }

    private void initialize() {
        if (mPageTitles == null) {
            mPageTitles = getResources().getStringArray(R.array.welcome_page_titles);
            mPageDescriptions = getResources().getStringArray(R.array.welcome_page_descriptions);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        setLogoResourceId(R.drawable.splash_logo);
        if (savedInstanceState != null) {
            switch (getCurrentPageIndex()) {
                case 0:
                    mTvContentView.setImageResource(
                            TV_FRAMES_1_START[TV_FRAMES_1_START.length - 1]);
                    break;
                case 1:
                    mTvContentView.setImageResource(
                            TV_FRAMES_2_START[TV_FRAMES_2_START.length - 1]);
                    break;
                case 2:
                    mTvContentView.setImageResource(
                            TV_FRAMES_3_ORANGE_START[TV_FRAMES_3_ORANGE_START.length - 1]);
                    mArrowView.setImageResource(TV_FRAMES_3_BLUE_ARROW[0]);
                    break;
                case 3:
                default:
                    mTvContentView.setImageResource(
                            TV_FRAMES_4_START[TV_FRAMES_4_START.length - 1]);
                    break;
            }
        }
        return view;
    }

    @Override
    public int onProvideTheme() {
        return R.style.Theme_Leanback_Onboarding;
    }

    @Override
    protected Animator onCreateEnterAnimation() {
        List<Animator> animators = new ArrayList<>();
        // Cloud 1
        View view = getActivity().findViewById(R.id.cloud1);
        view.setAlpha(0);
        Animator animator = AnimatorInflater.loadAnimator(getActivity(),
                R.animator.onboarding_welcome_cloud_enter);
        animator.setStartDelay(START_DELAY_CLOUD_MS);
        animator.setTarget(view);
        animators.add(animator);
        // Cloud 2
        view = getActivity().findViewById(R.id.cloud2);
        view.setAlpha(0);
        animator = AnimatorInflater.loadAnimator(getActivity(),
                R.animator.onboarding_welcome_cloud_enter);
        animator.setStartDelay(START_DELAY_CLOUD_MS);
        animator.setTarget(view);
        animators.add(animator);
        // TV container
        view = getActivity().findViewById(R.id.tv_container);
        view.setAlpha(0);
        animator = AnimatorInflater.loadAnimator(getActivity(),
                R.animator.onboarding_welcome_tv_enter);
        animator.setStartDelay(START_DELAY_TV_MS);
        animator.setTarget(view);
        animators.add(animator);
        // TV content
        view = getActivity().findViewById(R.id.tv_content);
        animator = SetupAnimationHelper.createFrameAnimator((ImageView) view, TV_FRAMES_1_START);
        animator.setStartDelay(START_DELAY_TV_CONTENTS_MS);
        animator.setTarget(view);
        animators.add(animator);
        // Shadow
        view = getActivity().findViewById(R.id.shadow);
        view.setAlpha(0);
        animator = AnimatorInflater.loadAnimator(getActivity(),
                R.animator.onboarding_welcome_shadow_enter);
        animator.setStartDelay(START_DELAY_SHADOW_MS);
        animator.setTarget(view);
        animators.add(animator);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(animators);
        return set;
    }

    @Nullable
    @Override
    protected View onCreateBackgroundView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.onboarding_welcome_background, container, false);
    }

    @Nullable
    @Override
    protected View onCreateContentView(LayoutInflater inflater, ViewGroup container) {
        View view = inflater.inflate(R.layout.onboarding_welcome_content, container, false);
        mTvContentView = (ImageView) view.findViewById(R.id.tv_content);
        return view;
    }

    @Nullable
    @Override
    protected View onCreateForegroundView(LayoutInflater inflater, ViewGroup container) {
        mArrowView = (ImageView) inflater.inflate(R.layout.onboarding_welcome_foreground, container,
                false);
        return mArrowView;
    }

    @Override
    protected int getPageCount() {
        return mPageTitles.length;
    }

    @Override
    protected String getPageTitle(int pageIndex) {
        return mPageTitles[pageIndex];
    }

    @Override
    protected String getPageDescription(int pageIndex) {
        return mPageDescriptions[pageIndex];
    }

    @Override
    protected void onFinishFragment() {
        SetupActionHelper.onActionClick(WelcomeFragment.this, ACTION_CATEGORY, ACTION_NEXT);
    }

    @Override
    protected void onPageChanged(int newPage, int previousPage) {
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        mArrowView.setVisibility(View.GONE);
        // TV screen hiding animator.
        Animator hideAnimator = previousPage == 0
                ? SetupAnimationHelper.createFrameAnimator(mTvContentView, TV_FRAMES_1_END)
                : SetupAnimationHelper.createFadeOutAnimator(mTvContentView,
                VIDEO_FADE_OUT_DURATION_MS, true);
        // TV screen showing animator.
        AnimatorSet animatorSet = new AnimatorSet();
        int firstFrame;
        switch (newPage) {
            case 0:
                animatorSet.playSequentially(hideAnimator,
                        SetupAnimationHelper.createFrameAnimator(mTvContentView,
                                TV_FRAMES_1_START));
                firstFrame = TV_FRAMES_1_START[0];
                break;
            case 1:
                animatorSet.playSequentially(hideAnimator,
                        SetupAnimationHelper.createFrameAnimator(mTvContentView,
                                TV_FRAMES_2_START));
                firstFrame = TV_FRAMES_2_START[0];
                break;
            case 2:
                mArrowView.setVisibility(View.VISIBLE);
                animatorSet.playSequentially(hideAnimator,
                        SetupAnimationHelper.createFrameAnimator(mArrowView,
                                TV_FRAMES_3_BLUE_ARROW),
                        SetupAnimationHelper.createFrameAnimator(mTvContentView,
                                TV_FRAMES_3_BLUE_START),
                        SetupAnimationHelper.createFrameAnimatorWithDelay(mTvContentView,
                                TV_FRAMES_3_BLUE_END, BLUE_SCREEN_HOLD_DURATION_MS),
                        SetupAnimationHelper.createFrameAnimator(mArrowView,
                                TV_FRAMES_3_ORANGE_ARROW),
                        SetupAnimationHelper.createFrameAnimator(mTvContentView,
                                TV_FRAMES_3_ORANGE_START));
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mArrowView.setImageResource(TV_FRAMES_3_BLUE_ARROW[0]);
                    }
                });
                firstFrame = TV_FRAMES_3_BLUE_START[0];
                break;
            case 3:
            default:
                animatorSet.playSequentially(hideAnimator,
                        SetupAnimationHelper.createFrameAnimator(mTvContentView,
                                TV_FRAMES_4_START));
                firstFrame = TV_FRAMES_4_START[0];
                break;
        }
        final int firstImageResource = firstFrame;
        hideAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Shows the first frame of show animation when the hide animator is canceled.
                mTvContentView.setImageResource(firstImageResource);
            }
        });
        mAnimator = SetupAnimationHelper.applyAnimationTimeScale(animatorSet);
        mAnimator.start();
    }
}
