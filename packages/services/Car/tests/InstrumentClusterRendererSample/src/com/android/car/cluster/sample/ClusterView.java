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

package com.android.car.cluster.sample;

import static com.android.car.cluster.sample.BitmapUtils.generateMediaIcon;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.Transformation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.android.car.cluster.sample.cards.CallCard;
import com.android.car.cluster.sample.cards.CallCard.CallStatus;
import com.android.car.cluster.sample.cards.CardView;
import com.android.car.cluster.sample.cards.CardView.CardType;
import com.android.car.cluster.sample.cards.MessageCard;
import com.android.car.cluster.sample.cards.MediaCard;
import com.android.car.cluster.sample.cards.NavCard;
import com.android.car.cluster.sample.cards.WeatherCard;

import java.util.PriorityQueue;

/**
 * Class that represents cluster view. It is responsible of ranking cards and play animations
 * during card transitions.
 */
public class ClusterView extends FrameLayout implements CardView.PriorityChangedListener{
    private static final String TAG = DebugUtil.getTag(ClusterView.class);

    private CardPanel mCardPanel;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final PriorityQueue<CardView> mQueue = new PriorityQueue<>(8);

    public ClusterView(Context context) {
        this(context, null);
    }

    public ClusterView(Context context, AttributeSet attrs) {
        super(context, attrs);

        inflate(getContext(), R.layout.cluster_view, this);
        mCardPanel = (CardPanel) findViewById(R.id.card_panel);
    }

    private <E extends CardView> E createCard(@CardType int cardType) {
        CardView card;
        switch (cardType)
        {
            case CardType.WEATHER:
                card = new WeatherCard(getContext(), this /* priority listener */);
                break;
            case CardType.MEDIA:
                card = new MediaCard(getContext(), this /* priority listener */);
                break;
            case CardType.PHONE_CALL:
                card = new CallCard(getContext(), this /* priority listener */);
                break;
            case CardType.NAV:
                card = new NavCard(getContext(), this /* priority listener */);
                break;
            case CardType.HANGOUT:
                card = new MessageCard(getContext(), this /* priority listener */);
                break;
            default:
                card = new CardView(getContext(), cardType, this /* priority listener */);
        }
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        card.setLayoutParams(params);
        return (E) card;
    }

    public void handleIncomingCall(Bitmap contactImage, String contactName) {
        if (contactImage == null) {
            contactImage = BitmapFactory.decodeResource(getResources(),
                    R.drawable.unknown_contact_480);
        }
        CardView card = createIncomingCallCard(contactImage, contactName);
        enqueueCard(card);
    }

    public void handleDialingCall(Bitmap contactImage, String contactName) {
        if (contactImage == null) {
            contactImage = BitmapFactory.decodeResource(getResources(),
                    R.drawable.unknown_contact_480);
        }
        CardView card = createDialingCallCard(contactImage, contactName);
        enqueueCard(card);
    }

    public void handleUpdateContactName(String contactName) {
        CallCard card = getCallCard();
        if (card != null) {
            card.setContactName(contactName);
        }
    }

    public void handleUpdateContactImage(Bitmap image) {
        CallCard card = getCallCard();
        if (card != null) {
            updateContactImage(card, image);
        }
    }

    public void handleHangoutMessage(Bitmap contactImage, String contactName) {
        if (getCardOrNull(MessageCard.class) != null) {
            return; // Deduplicate.
        }

        if (contactImage == null) {
            contactImage = BitmapFactory.decodeResource(getResources(),
                    R.drawable.unknown_contact_480);
        }
        MessageCard card = createCard(CardType.HANGOUT);
        card.setContactName(contactName);

        int c = getResources().getColor(R.color.hangout_background, null);
        card.setBackgroundColor(c);
        card.setLeftIcon(BitmapUtils.circleCropBitmap(contactImage));

        enqueueCard(card);
    }

    public void handleCallConnected(long connectedTimestamp) {
        if (DebugUtil.DEBUG) {
            Log.d(TAG, "handleCallConnected, connectedTimestamp: " + connectedTimestamp);
        }
        CallCard card = getCallCard();
        if (card != null) {
            if (DebugUtil.DEBUG) {
                Log.d(TAG, "handleCallConnected, call status: " + card.getCallStatus());
            }

            if (card.getCallStatus() == CallStatus.INCOMING_OR_DIALING) {
                card.animateCallConnected(connectedTimestamp);
            }
        }
    }

    public void handleCallDisconnected() {
        final CallCard callCard = getCallCard();
        if (callCard != null) {
            if (DebugUtil.DEBUG) {
                Log.d(TAG, "handleCallDisconnected, callCard status: " + callCard.getCallStatus());
            }

            if (callCard == getCurrentCard()
                    && callCard.getCallStatus() == CallStatus.ACTIVE) {
                callCard.animateCallDisconnected();
            } else {
                removeCard(callCard);
            }
        }
    }

    public void runDelayed(long delay, final Runnable task) {
        mHandler.postDelayed(task, delay);
    }

    public MediaCard createMediaCard(Bitmap albumCover, String title, String subtitle,
            int appColor) {
        MediaCard card = createCard(CardType.MEDIA);
        int iconSize = card.getIconSize();

        if (albumCover != null) {
            Bitmap albumIcon = BitmapUtils.scaleBitmap(albumCover, iconSize, iconSize);
            albumIcon = BitmapUtils.circleCropBitmap(albumIcon);
            card.setLeftIcon(albumIcon);

            Bitmap backgroundImage = BitmapUtils.scaleBitmapColors(albumCover,
                    getResources().getColor(R.color.media_background_dark, null),
                    getResources().getColor(R.color.media_background, null));

            backgroundImage = BitmapUtils.scaleBitmap(backgroundImage,
                    (int) (getResources().getDimension(R.dimen.card_width)),
                    (int) getResources().getDimension(R.dimen.card_height));

            int c = getResources().getColor(R.color.phone_background, null);
            card.setBackground(backgroundImage, c);
        }

        Bitmap mediaApp = generateMediaIcon(iconSize,
                appColor,
                getResources().getColor(R.color.media_icon_foreground, null));
        card.setRightIcon(mediaApp);
        card.setProgressColor(appColor);
        card.setTitle(title);
        card.setSubtitle(subtitle);
        return card;
    }

    public CardView getCurrentCard() {
        return (CardView) mCardPanel.getTopVisibleChild();
    }

    public CardView createWeatherCard() {
        CardView card;
        card = createCard(CardType.WEATHER);
        card.setBackgroundColor(getResources().getColor(R.color.weather_blue_sky, null));
        return card;
    }

    public CallCard createIncomingCallCard(Bitmap contactImage, String contactName) {
        CallCard card = createCard(CardType.PHONE_CALL);
        updateContactImage(card, contactImage);
        card.setContactName(contactName);
        card.setStatusLabel(getContext().getString(R.string.incoming_call));
        return card;
    }

    public CallCard createDialingCallCard(Bitmap contactImage, String contactName) {
        CallCard card = createCard(CardType.PHONE_CALL);

        updateContactImage(card, contactImage);
        card.setContactName(contactName);
        card.setStatusLabel(getContext().getString(R.string.dialing));
        return card;
    }

    public NavCard createNavCard() {
        return createCard(CardType.NAV);
    }

    private void updateContactImage(CardView card, Bitmap contactImage) {
        int iconSize = (int)getResources().getDimension(R.dimen.card_icon_size);
        Bitmap contactImageCircle = BitmapUtils.circleCropBitmap(
                BitmapUtils.scaleBitmap(contactImage, iconSize, iconSize));
        card.setLeftIcon(contactImageCircle);

        contactImage = BitmapUtils.scaleBitmapColors(contactImage,
                getResources().getColor(R.color.phone_background_dark, null),
                getResources().getColor(R.color.phone_background, null));

        contactImage = BitmapUtils.scaleBitmap(contactImage,
                (int) getResources().getDimension(R.dimen.card_width),
                (int) getResources().getDimension(R.dimen.card_height));

        int c = getResources().getColor(R.color.phone_background, null);
        card.setBackground(contactImage, c);
    }

    public boolean cardExists(CardView card) {
        return mCardPanel.childViewExists(card);
    }

    public void removeCard(final CardView card) {
        if (DebugUtil.DEBUG) {
            Log.d(TAG, "removeCard, card: " + card);
        }
        final CardView currentlyShownCard = getCurrentCard();
        if (currentlyShownCard == card) {
            // Card is on the screen, play nice animation and then remove it.
            mQueue.remove(card);
            mCardPanel.markViewToBeRemoved(card);
            CardView cardToShow = mQueue.peek();
            if (cardToShow != null) {
                Animation animation = cardToShow.getAnimation();
                if (DebugUtil.DEBUG) {
                    Log.d(TAG, "card to show: " + cardToShow + ", animation: " + animation);
                }
                if (animation != null) {
                    cardToShow.getAnimation().cancel();
                }

                cardToShow.setVisibility(VISIBLE);
                mCardPanel.moveChildBehindTheTop(cardToShow);
            }
            playUnrevealAnimation(card, new Runnable() {
                @Override
                public void run() {
                    removeCardInternal(card);
                }
            });
        } else {
            // Card is not on the screen, just remove it.
            if (DebugUtil.DEBUG) {
                Log.d(TAG, "removeCard, card is not on the screen, remove it immediately");
                removeCardInternal(card);
            }
        }
    }

    public void enqueueCard(final CardView card) {
        if (DebugUtil.DEBUG) {
            Log.d(TAG, "enqueueCard, card: " + card);
        }
        final CardView currentCard = getCurrentCard();
        boolean cardIsOnTheScreen = card == currentCard;

        boolean cardExisted = mQueue.remove(card);
        mQueue.offer(card);

        CardView activeCard = mQueue.peek();
        boolean shouldDisplayCard = activeCard == card;

        if (DebugUtil.DEBUG) {
            Log.d(TAG, "enqueueCard, card: " + card + ", onScreen: "
                    + cardIsOnTheScreen + ", cardExisted: " + cardExisted + ", shouldDisplayCard: "
                    + shouldDisplayCard + ", activeCard: " + activeCard
                    + ", currentCard: " + currentCard);
        }

        if (cardIsOnTheScreen) {
            if (!shouldDisplayCard) {
                // Card priority was decreased, but it still active. Need to reverse reveal
                // animation and show underlying card.
                showCardWithFadeoutAnimation(activeCard);
            }
        } else {
            // Card is not on the screen right now.
            if (cardExisted) {
                if (shouldDisplayCard) {
                    // Card was created in the past and is in the queue, need to show
                    // this card using unreveal animation.
                    showCardWithUnrevealAnimation(card);
                }
            } else {
                if (shouldDisplayCard) {
                    // Card doesn't exist, but we want to show it.
                    showCardWithRevealAnimation(card);
                } else {
                    // We want to add the card to the panel, but do not want to show it.
                    if (DebugUtil.DEBUG) {
                        Log.d(TAG, "Adding hidden card");
                    }
                    card.setVisibility(GONE);
                    mCardPanel.addView(card, 0);
                }
            }
        }

        removeInvisibleDuplicatedCard(card);  // Remove invisible cards with the same card type.

        dumpCardsToLog();
    }

    private void showCardWithRevealAnimation(final CardView cardToShow) {
        if (DebugUtil.DEBUG) {
            Log.d(TAG, "showCardWithRevealAnimation, card: " + cardToShow);
        }

        removeInvisibleDuplicatedCard(cardToShow);

        CardView currentCard = getCurrentCard();
        mCardPanel.addView(cardToShow);
        playRevealAnimation(cardToShow, currentCard, new RemoveOrHideCard(this, currentCard));
    }

    private void removeInvisibleDuplicatedCard(CardView card) {
        Log.d(TAG, "removeInvisibleDuplicatedCard, card: " + card);
        // Remove cards that has the same card type and not visible on the screen.
        for (int i = mCardPanel.getChildCount() - 1; i >= 0; i--) {
            CardView child = (CardView) mCardPanel.getChildAt(i);
            if (child.getCardType() == card.getCardType()
                    && child != card
                    && child.getVisibility() != VISIBLE) {
                Log.d(TAG, "removeInvisibleDuplicatedCard, found dup: " + child);
                removeCardInternal(child);
            }
        }
    }

    private void showCardWithFadeoutAnimation(final CardView cardToShow) {
        if (DebugUtil.DEBUG) {
            Log.d(TAG, "showCardWithFadeoutAnimation, card: " + cardToShow);
        }
        // Place card behind the top card, it will become visible once fade out animation for the
        // top card starts to play.
        mCardPanel.moveChildBehindTheTop(cardToShow);
        cardToShow.setVisibility(VISIBLE);

        // Hide top card with animation.
        playFadeOutAndSlideOutAnimation((CardView) mCardPanel.getTopVisibleChild());
    }

    private void showCardWithUnrevealAnimation(CardView cardToShow) {
        if (DebugUtil.DEBUG) {
            Log.d(TAG, "showCardWithUnrevealAnimation, card: " + cardToShow);
        }
        final CardView currentCard = (CardView) mCardPanel.getTopVisibleChild();
        // Card was created in the past and is in the queue, need to show reverse reveal
        // animation to unreveal this card.
        mCardPanel.moveChildBehindTheTop(cardToShow);
        cardToShow.setVisibility(VISIBLE);
        playUnrevealAnimation(currentCard, new RemoveOrHideCard(this, currentCard));
    }

    private static class RemoveOrHideCard implements Runnable {
        private final CardView mCard;

        private final ClusterView mClusterView;

        RemoveOrHideCard(ClusterView clusterView, CardView card) {
            mCard = card;
            mClusterView = clusterView;
        }

        @Override
        public void run() {
            if (DebugUtil.DEBUG) {
                Log.d(TAG, "RemoveOrHideCard: " + mCard);
            }

            mClusterView.removeInvisibleDuplicatedCard(mCard);

            if (mCard.isGarbage()) {
                if (DebugUtil.DEBUG) {
                    Log.d(TAG, "RemoveOrHideCard, card has garbage priority");
                }
                mClusterView.removeCardInternal(mCard);
            } else {
                if (DebugUtil.DEBUG) {
                    Log.d(TAG, "RemoveOrHideCard, hiding card: " + mCard);
                }
                mCard.setVisibility(GONE);
                mCard.setAlpha(1);  // Restore alpha after fade-out animation, it's gone anyway.
                mClusterView.dumpCardsToLog();
            }
        }
    }

    private void removeCardInternal(CardView card) {
        if (DebugUtil.DEBUG) {
            Log.d(TAG, "removeCardInternal, card: " + card);
        }
        mCardPanel.removeView(card);
        mQueue.remove(card);

        dumpCardsToLog();
    }

    @Override
    public void onPriorityChanged(CardView card, int priority) {
        if (DebugUtil.DEBUG) {
            Log.d(TAG, "onPriorityChanged, card: " + card + ", priority: " + priority);
        }
        if (cardExists(card)) {
            enqueueCard(card);
        }
    }

    private void playRevealAnimation(final CardView cardToShow, final CardView currentCard,
            final Runnable oldCardDissapearedAction) {

        if (currentCard == cardToShow) {
            return;
        }

        if (currentCard != null) {
            playAlphaAnimation(currentCard,
                    0,    // Target alpha
                    400,  // Duration
                    new DecelerateInterpolator(0.5f),
                    oldCardDissapearedAction);
        }

        cardToShow.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                cardToShow.removeOnLayoutChangeListener(this);  // Just need it once.

                createRevealAnimator(cardToShow, new DecelerateInterpolator(1f), true)
                        .start();
            }
        });
        cardToShow.onPlayRevealAnimation();
    }

    private void playAlphaAnimation(final CardView card, float targetAlpha, long duration,
            Interpolator interpolator, final Runnable endAction) {
        Animation animation = new AlphaAnimation(card.getAlpha(), targetAlpha);
        animation.setDuration(duration * DebugUtil.ANIMATION_FACTOR);
        animation.setInterpolator(interpolator);

        animation.setAnimationListener(new AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) { }

            @Override
            public void onAnimationEnd(Animation animation) {
                // For some reason, cancelled animation hasEnded() == true here.
                // Check for start time instead.
                if (endAction != null && animation.getStartTime() != Long.MIN_VALUE) {
                    endAction.run();
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) { }
        });
        card.setAnimation(animation);
        animation.start();
    }

    private static boolean isAnimationCancelled(Animation animation) {
        return (animation.getStartTime() == Long.MIN_VALUE) || (!animation.hasEnded());
    }

    private void playFadeOutAndSlideOutAnimation(final CardView card) {
        Animation animation = new TranslateAnimation(0, card.getWidth(), 0, 0) {
            private final float mFromAlpha = card.getAlpha();
            private final float mToAlpha = 0;

            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                super.applyTransformation(interpolatedTime, t);

                final float alpha = mFromAlpha;
                t.setAlpha(alpha + ((mToAlpha - alpha) * interpolatedTime));
            }
        };
        animation.setDuration(600 * DebugUtil.ANIMATION_FACTOR);
        animation.setAnimationListener(new AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) { }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (DebugUtil.DEBUG) {
                    Log.d(TAG, "playFadeOutAndSlideOutAnimation, onAnimationEnd " + animation
                            + ", startTime: " + animation.getStartTime());
                }
                if (!isAnimationCancelled(animation)) {
                    new RemoveOrHideCard(ClusterView.this, card)
                            .run();
                }
                // Reset X position.
                card.setTranslationX(0);
            }

            @Override
            public void onAnimationRepeat(Animation animation) { }
        });
        card.setAnimation(animation);
        animation.start();
    }

    /** Hides given card and reveals underlying card */
    private void playUnrevealAnimation(final CardView card, final Runnable unrevealCompleteAction) {
        if (DebugUtil.DEBUG) {
            Log.d(TAG, "playUnrevealAnimation, card: " + card);
        }

        final Animator anim = createRevealAnimator(card,
                new AccelerateInterpolator(2f), false /* hide */);

        anim.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                if (DebugUtil.DEBUG) {
                    Log.d(TAG, "onAnimationCancel, animation: " + animation);
                }
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled) {
                    return;
                }

                if (DebugUtil.DEBUG) {
                    Log.d(TAG, "onAnimationEnd, animation: " + animation);
                }
                unrevealCompleteAction.run();
            }
        });

        anim.start();
        card.onPlayUnrevealAnimation();
    }

    private Animator createRevealAnimator(CardView card, Interpolator interpolator, boolean show) {
        int cardWidth = (int) getResources().getDimension(R.dimen.card_width);
        int radius = (int) (cardWidth * 1.2f);
        int centerY = (int) (getResources().getDimension(R.dimen.card_height) / 2);

        Animator anim = ViewAnimationUtils.createCircularReveal(card, radius, centerY,
                show ? 0 : radius /* start radius */,
                show ? radius : 0 /* end radius */);

        anim.setInterpolator(interpolator);
        anim.setDuration(600 * DebugUtil.ANIMATION_FACTOR);
        return anim;
    }

    public <E> E getCardOrNull(Class<E> clazz) {
        return mCardPanel.getChildOrNull(clazz);
    }

    public <E> E getVisibleCardOrNull(Class<E> clazz) {
        return mCardPanel.getVisibleChildOrNull(clazz);
    }

    private CallCard getCallCard() {
        return getCardOrNull(CallCard.class);
    }

    private void dumpCardsToLog() {
        if (DebugUtil.DEBUG) {
            Log.d(TAG, "Cards in layout: " + mCardPanel.getChildCount() + ", cards in queue: "
                    + mQueue.size());
            for (int i = 0; i < mCardPanel.getChildCount(); i++) {
                Log.d(TAG, "child: " + mCardPanel.getChildAt(i));
            }
        }
    }
}
