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

package com.android.tv.ui;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.support.annotation.IntDef;
import android.transition.Fade;
import android.transition.Scene;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.data.Channel;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class TvTransitionManager extends TransitionManager {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({SCENE_TYPE_EMPTY, SCENE_TYPE_CHANNEL_BANNER, SCENE_TYPE_INPUT_BANNER,
        SCENE_TYPE_KEYPAD_CHANNEL_SWITCH, SCENE_TYPE_SELECT_INPUT})
    public @interface SceneType {}
    public static final int SCENE_TYPE_EMPTY = 0;
    public static final int SCENE_TYPE_CHANNEL_BANNER = 1;
    public static final int SCENE_TYPE_INPUT_BANNER = 2;
    public static final int SCENE_TYPE_KEYPAD_CHANNEL_SWITCH = 3;
    public static final int SCENE_TYPE_SELECT_INPUT = 4;

    private final MainActivity mMainActivity;
    private final ViewGroup mSceneContainer;
    private final ChannelBannerView mChannelBannerView;
    private final InputBannerView mInputBannerView;
    private final KeypadChannelSwitchView mKeypadChannelSwitchView;
    private final SelectInputView mSelectInputView;
    private final FrameLayout mEmptyView;
    private ViewGroup mCurrentSceneView;
    private Animator mEnterAnimator;
    private Animator mExitAnimator;

    private boolean mInitialized;
    private Scene mEmptyScene;
    private Scene mChannelBannerScene;
    private Scene mInputBannerScene;
    private Scene mKeypadChannelSwitchScene;
    private Scene mSelectInputScene;
    private Scene mCurrentScene;

    private Listener mListener;

    public TvTransitionManager(MainActivity mainActivity, ViewGroup sceneContainer,
            ChannelBannerView channelBannerView, InputBannerView inputBannerView,
            KeypadChannelSwitchView keypadChannelSwitchView, SelectInputView selectInputView) {
        mMainActivity = mainActivity;
        mSceneContainer = sceneContainer;
        mChannelBannerView = channelBannerView;
        mInputBannerView = inputBannerView;
        mKeypadChannelSwitchView = keypadChannelSwitchView;
        mSelectInputView = selectInputView;
        mEmptyView = (FrameLayout) mMainActivity.getLayoutInflater().inflate(
                R.layout.empty_info_banner, sceneContainer, false);
        mCurrentSceneView = mEmptyView;
    }

    public void goToEmptyScene(boolean withAnimation) {
        if (mCurrentScene == mEmptyScene) {
            return;
        }
        initIfNeeded();
        if (withAnimation) {
            mEmptyView.setAlpha(1.0f);
            transitionTo(mEmptyScene);
        } else {
            TransitionManager.go(mEmptyScene, null);
            // When transition is null, transition got stuck without calling endTransitions.
            TransitionManager.endTransitions(mEmptyScene.getSceneRoot());
            // Since Fade.OUT transition doesn't run, we need to set alpha manually.
            mEmptyView.setAlpha(0);
        }
    }

    public void goToChannelBannerScene() {
        initIfNeeded();
        Channel channel = mMainActivity.getCurrentChannel();
        if (channel != null && channel.isPassthrough()) {
            if (mCurrentScene != mInputBannerScene) {
                // Show the input banner instead.
                LayoutParams lp = (LayoutParams) mInputBannerView.getLayoutParams();
                lp.width = mCurrentScene == mSelectInputScene ? mSelectInputView.getWidth()
                        : FrameLayout.LayoutParams.WRAP_CONTENT;
                mInputBannerView.setLayoutParams(lp);
                mInputBannerView.updateLabel();
                transitionTo(mInputBannerScene);
            }
        } else if (mCurrentScene != mChannelBannerScene) {
            transitionTo(mChannelBannerScene);
        }
    }

    public void goToKeypadChannelSwitchScene() {
        initIfNeeded();
        if (mCurrentScene != mKeypadChannelSwitchScene) {
            transitionTo(mKeypadChannelSwitchScene);
        }
    }

    public void goToSelectInputScene() {
        initIfNeeded();
        if (mCurrentScene != mSelectInputScene) {
            mSelectInputView.setCurrentChannel(mMainActivity.getCurrentChannel());
            transitionTo(mSelectInputScene);
        }
    }

    public boolean isSceneActive() {
        return mCurrentScene != mEmptyScene;
    }

    public boolean isKeypadChannelSwitchActive() {
        return mCurrentScene != null && mCurrentScene == mKeypadChannelSwitchScene;
    }

    public boolean isSelectInputActive() {
        return mCurrentScene != null && mCurrentScene == mSelectInputScene;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void initIfNeeded() {
        if (mInitialized) {
            return;
        }
        mEnterAnimator = AnimatorInflater.loadAnimator(mMainActivity,
                R.animator.channel_banner_enter);
        mExitAnimator = AnimatorInflater.loadAnimator(mMainActivity,
                R.animator.channel_banner_exit);

        mEmptyScene = new Scene(mSceneContainer, (View) mEmptyView);
        mEmptyScene.setEnterAction(new Runnable() {
            @Override
            public void run() {
                FrameLayout.LayoutParams emptySceneLayoutParams =
                        (FrameLayout.LayoutParams) mEmptyView.getLayoutParams();
                ViewGroup.MarginLayoutParams lp =
                        (ViewGroup.MarginLayoutParams) mCurrentSceneView.getLayoutParams();
                emptySceneLayoutParams.topMargin = mCurrentSceneView.getTop();
                emptySceneLayoutParams.setMarginStart(lp.getMarginStart());
                emptySceneLayoutParams.height = mCurrentSceneView.getHeight();
                emptySceneLayoutParams.width = mCurrentSceneView.getWidth();
                mEmptyView.setLayoutParams(emptySceneLayoutParams);
                setCurrentScene(mEmptyScene, mEmptyView);
            }
        });
        mEmptyScene.setExitAction(new Runnable() {
            @Override
            public void run() {
                removeAllViewsFromOverlay();
            }
        });

        mChannelBannerScene = buildScene(mSceneContainer, mChannelBannerView);
        mInputBannerScene = buildScene(mSceneContainer, mInputBannerView);
        mKeypadChannelSwitchScene = buildScene(mSceneContainer, mKeypadChannelSwitchView);
        mSelectInputScene = buildScene(mSceneContainer, mSelectInputView);
        mCurrentScene = mEmptyScene;

        // Enter transitions
        TransitionSet enter = new TransitionSet()
                .addTransition(new SceneTransition(SceneTransition.ENTER))
                .addTransition(new Fade(Fade.IN));
        setTransition(mEmptyScene, mChannelBannerScene, enter);
        setTransition(mEmptyScene, mInputBannerScene, enter);
        setTransition(mEmptyScene, mKeypadChannelSwitchScene, enter);
        setTransition(mEmptyScene, mSelectInputScene, enter);

        // Exit transitions
        TransitionSet exit = new TransitionSet()
                .addTransition(new SceneTransition(SceneTransition.EXIT))
                .addTransition(new Fade(Fade.OUT));
        setTransition(mChannelBannerScene, mEmptyScene, exit);
        setTransition(mInputBannerScene, mEmptyScene, exit);
        setTransition(mKeypadChannelSwitchScene, mEmptyScene, exit);
        setTransition(mSelectInputScene, mEmptyScene, exit);

        // All other possible transitions between scenes
        TransitionInflater ti = TransitionInflater.from(mMainActivity);
        Transition transition = ti.inflateTransition(R.transition.transition_between_scenes);
        setTransition(mChannelBannerScene, mKeypadChannelSwitchScene, transition);
        setTransition(mChannelBannerScene, mSelectInputScene, transition);
        setTransition(mInputBannerScene, mSelectInputScene, transition);
        setTransition(mKeypadChannelSwitchScene, mChannelBannerScene, transition);
        setTransition(mKeypadChannelSwitchScene, mSelectInputScene, transition);
        setTransition(mSelectInputScene, mChannelBannerScene, transition);
        setTransition(mSelectInputScene, mInputBannerScene, transition);

        mInitialized = true;
    }

    /**
     * Returns the type of the given scene.
     */
    @SceneType public int getSceneType(Scene scene) {
        if (scene == mChannelBannerScene) {
            return SCENE_TYPE_CHANNEL_BANNER;
        } else if (scene == mInputBannerScene) {
            return SCENE_TYPE_INPUT_BANNER;
        } else if (scene == mKeypadChannelSwitchScene) {
            return SCENE_TYPE_KEYPAD_CHANNEL_SWITCH;
        } else if (scene == mSelectInputScene) {
            return SCENE_TYPE_SELECT_INPUT;
        }
        return SCENE_TYPE_EMPTY;
    }

    private void setCurrentScene(Scene scene, ViewGroup sceneView) {
        if (mListener != null) {
            mListener.onSceneChanged(getSceneType(mCurrentScene), getSceneType(scene));
        }
        mCurrentScene = scene;
        mCurrentSceneView = sceneView;
        // TODO: Is this a still valid call?
        mMainActivity.updateKeyInputFocus();
    }

    public interface TransitionLayout {
        // TODO: remove the parameter fromEmptyScene once a bug regarding transition alpha
        // is fixed. The bug is that the transition alpha is not reset after the transition is
        // canceled.
        void onEnterAction(boolean fromEmptyScene);

        void onExitAction();
    }

    private Scene buildScene(ViewGroup sceneRoot, final TransitionLayout layout) {
        final Scene scene = new Scene(sceneRoot, (View) layout);
        scene.setEnterAction(new Runnable() {
            @Override
            public void run() {
                boolean wasEmptyScene = (mCurrentScene == mEmptyScene);
                setCurrentScene(scene, (ViewGroup) layout);
                layout.onEnterAction(wasEmptyScene);
            }
        });
        scene.setExitAction(new Runnable() {
            @Override
            public void run() {
                removeAllViewsFromOverlay();
                layout.onExitAction();
            }
        });
        return scene;
    }

    private void removeAllViewsFromOverlay() {
        // Clean up all the animations which can be still running.
        mSceneContainer.getOverlay().remove(mChannelBannerView);
        mSceneContainer.getOverlay().remove(mInputBannerView);
        mSceneContainer.getOverlay().remove(mKeypadChannelSwitchView);
        mSceneContainer.getOverlay().remove(mSelectInputView);
    }

    private class SceneTransition extends Transition {
        static final int ENTER = 0;
        static final int EXIT = 1;

        private final Animator mAnimator;

        SceneTransition(int mode) {
            mAnimator = mode == ENTER ? mEnterAnimator : mExitAnimator;
        }

        @Override
        public void captureStartValues(TransitionValues transitionValues) {
        }

        @Override
        public void captureEndValues(TransitionValues transitionValues) {
        }

        @Override
        public Animator createAnimator(
                ViewGroup sceneRoot, TransitionValues startValues, TransitionValues endValues) {
            Animator animator = mAnimator.clone();
            animator.setTarget(sceneRoot);
            animator.addListener(new HardwareLayerAnimatorListenerAdapter(sceneRoot));
            return animator;
        }
    }

    /**
     * An interface for notification of the scene transition.
     */
    public interface Listener {
        /**
         * Called when the scene changes. This method is called just before the scene transition.
         */
        void onSceneChanged(@SceneType int fromSceneType, @SceneType int toSceneType);
    }
}
