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

package com.android.tv.menu;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.support.v7.graphics.Palette;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.data.Channel;
import com.android.tv.util.BitmapUtils;
import com.android.tv.util.ImageLoader;
import com.android.tv.util.TvInputManagerHelper;

import java.util.Objects;

/**
 * A view to render an app link card.
 */
public class AppLinkCardView extends BaseCardView<ChannelsRowItem> {
    private static final String TAG = MenuView.TAG;
    private static final boolean DEBUG = MenuView.DEBUG;

    private final int mCardImageWidth;
    private final int mCardImageHeight;
    private final int mIconWidth;
    private final int mIconHeight;
    private final int mIconPadding;
    private final int mIconColorFilter;
    private final Drawable mDefaultDrawable;

    private ImageView mImageView;
    private TextView mAppInfoView;
    private View mMetaViewHolder;
    private Channel mChannel;
    private Intent mIntent;
    private final PackageManager mPackageManager;
    private final TvInputManagerHelper mTvInputManagerHelper;

    public AppLinkCardView(Context context) {
        this(context, null);
    }

    public AppLinkCardView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppLinkCardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mCardImageWidth = getResources().getDimensionPixelSize(R.dimen.card_image_layout_width);
        mCardImageHeight = getResources().getDimensionPixelSize(R.dimen.card_image_layout_height);
        mIconWidth = getResources().getDimensionPixelSize(R.dimen.app_link_card_icon_width);
        mIconHeight = getResources().getDimensionPixelSize(R.dimen.app_link_card_icon_height);
        mIconPadding = getResources().getDimensionPixelOffset(R.dimen.app_link_card_icon_padding);
        mPackageManager = context.getPackageManager();
        mTvInputManagerHelper = ((MainActivity) context).getTvInputManagerHelper();
        mIconColorFilter = getResources().getColor(R.color.app_link_card_icon_color_filter, null);
        mDefaultDrawable = getResources().getDrawable(R.drawable.ic_recent_thumbnail_default, null);
    }

    /**
     * Returns the intent which will be started once this card is clicked.
     */
    public Intent getIntent() {
        return mIntent;
    }

    @Override
    public void onBind(ChannelsRowItem item, boolean selected) {
        Channel newChannel = item.getChannel();
        boolean channelChanged = !Objects.equals(mChannel, newChannel);
        String previousPosterArtUri = mChannel == null ? null : mChannel.getAppLinkPosterArtUri();
        boolean posterArtChanged = previousPosterArtUri == null
                || newChannel.getAppLinkPosterArtUri() == null
                || !TextUtils.equals(previousPosterArtUri, newChannel.getAppLinkPosterArtUri());
        mChannel = newChannel;
        if (DEBUG) {
            Log.d(TAG, "onBind(channelName=" + mChannel.getDisplayName() + ", selected=" + selected
                    + ")");
        }
        ApplicationInfo appInfo = mTvInputManagerHelper.getTvInputAppInfo(mChannel.getInputId());
        if (channelChanged) {
            int linkType = mChannel.getAppLinkType(getContext());
            mIntent = mChannel.getAppLinkIntent(getContext());

            CharSequence appLabel;
            switch (linkType) {
                case Channel.APP_LINK_TYPE_CHANNEL:
                    setText(mChannel.getAppLinkText());
                    mAppInfoView.setVisibility(VISIBLE);
                    mAppInfoView.setCompoundDrawablePadding(mIconPadding);
                    mAppInfoView.setCompoundDrawablesRelative(null, null, null, null);
                    appLabel = mTvInputManagerHelper
                            .getTvInputApplicationLabel(mChannel.getInputId());
                    if (appLabel != null) {
                        mAppInfoView.setText(appLabel);
                    } else {
                        new AsyncTask<Void, Void, CharSequence>() {
                            private final String mLoadTvInputId = mChannel.getInputId();

                            @Override
                            protected CharSequence doInBackground(Void... params) {
                                if (appInfo != null) {
                                    return mPackageManager.getApplicationLabel(appInfo);
                                }
                                return null;
                            }

                            @Override
                            protected void onPostExecute(CharSequence appLabel) {
                                mTvInputManagerHelper.setTvInputApplicationLabel(
                                        mLoadTvInputId, appLabel);
                                if (mLoadTvInputId != mChannel.getInputId()
                                        || !isAttachedToWindow()) {
                                    return;
                                }
                                mAppInfoView.setText(appLabel);
                            }
                        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    }
                    if (!TextUtils.isEmpty(mChannel.getAppLinkIconUri())) {
                        mChannel.loadBitmap(getContext(), Channel.LOAD_IMAGE_TYPE_APP_LINK_ICON,
                                mIconWidth, mIconHeight, createChannelLogoCallback(
                                        this, mChannel, Channel.LOAD_IMAGE_TYPE_APP_LINK_ICON));
                    } else if (appInfo.icon != 0) {
                        Drawable appIcon = mTvInputManagerHelper
                                .getTvInputApplicationIcon(mChannel.getInputId());
                        if (appIcon != null) {
                            BitmapUtils.setColorFilterToDrawable(mIconColorFilter, appIcon);
                            appIcon.setBounds(0, 0, mIconWidth, mIconHeight);
                            mAppInfoView.setCompoundDrawablesRelative(appIcon, null, null, null);
                        } else {
                            new AsyncTask<Void, Void, Drawable>() {
                                private final String mLoadTvInputId = mChannel.getInputId();

                                @Override
                                protected Drawable doInBackground(Void... params) {
                                    return mPackageManager.getApplicationIcon(appInfo);
                                }

                                @Override
                                protected void onPostExecute(Drawable appIcon) {
                                    mTvInputManagerHelper.setTvInputApplicationIcon(
                                            mLoadTvInputId, appIcon);
                                    if (!mLoadTvInputId.equals(mChannel.getInputId())
                                            || !isAttachedToWindow()) {
                                        return;
                                    }
                                    BitmapUtils.setColorFilterToDrawable(mIconColorFilter, appIcon);
                                    appIcon.setBounds(0, 0, mIconWidth, mIconHeight);
                                    mAppInfoView.setCompoundDrawablesRelative(
                                            appIcon, null, null, null);
                                }
                            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        }
                    }
                    break;
                case Channel.APP_LINK_TYPE_APP:
                    appLabel = mTvInputManagerHelper
                        .getTvInputApplicationLabel(mChannel.getInputId());
                    if (appLabel != null) {
                        setText(getContext()
                            .getString(R.string.channels_item_app_link_app_launcher, appLabel));
                    } else {
                        new AsyncTask<Void, Void, CharSequence>() {
                            private final String mLoadTvInputId = mChannel.getInputId();

                            @Override
                            protected CharSequence doInBackground(Void... params) {
                                if (appInfo != null) {
                                    return mPackageManager.getApplicationLabel(appInfo);
                                }
                                return null;
                            }

                            @Override
                            protected void onPostExecute(CharSequence appLabel) {
                                mTvInputManagerHelper.setTvInputApplicationLabel(
                                    mLoadTvInputId, appLabel);
                                if (!mLoadTvInputId.equals(mChannel.getInputId())
                                    || !isAttachedToWindow()) {
                                    return;
                                }
                                setText(getContext()
                                    .getString(
                                        R.string.channels_item_app_link_app_launcher,
                                        appLabel));
                            }
                        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    }
                    mAppInfoView.setVisibility(GONE);
                    break;
                default:
                    mAppInfoView.setVisibility(GONE);
                    Log.d(TAG, "Should not be here.");
            }

            if (mChannel.getAppLinkColor() == 0) {
                mMetaViewHolder.setBackgroundResource(R.color.channel_card_meta_background);
            } else {
                mMetaViewHolder.setBackgroundColor(mChannel.getAppLinkColor());
            }
        }
        if (posterArtChanged) {
            mImageView.setImageDrawable(mDefaultDrawable);
            mImageView.setForeground(null);
            if (!TextUtils.isEmpty(mChannel.getAppLinkPosterArtUri())) {
                mChannel.loadBitmap(getContext(), Channel.LOAD_IMAGE_TYPE_APP_LINK_POSTER_ART,
                        mCardImageWidth, mCardImageHeight, createChannelLogoCallback(this, mChannel,
                                Channel.LOAD_IMAGE_TYPE_APP_LINK_POSTER_ART));
            } else {
                setCardImageWithBanner(appInfo);
            }
        }
        super.onBind(item, selected);
    }

    private static ImageLoader.ImageLoaderCallback<AppLinkCardView> createChannelLogoCallback(
            AppLinkCardView cardView, final Channel channel, final int type) {
        return new ImageLoader.ImageLoaderCallback<AppLinkCardView>(cardView) {
            @Override
            public void onBitmapLoaded(AppLinkCardView cardView, @Nullable Bitmap bitmap) {
                // mChannel can be changed before the image load finished.
                if (!cardView.mChannel.hasSameReadOnlyInfo(channel)) {
                    return;
                }
                cardView.updateChannelLogo(bitmap, type);
            }
        };
    }

    private void updateChannelLogo(@Nullable Bitmap bitmap, int type) {
        if (type == Channel.LOAD_IMAGE_TYPE_APP_LINK_ICON) {
            BitmapDrawable drawable = null;
            if (bitmap != null) {
                drawable = new BitmapDrawable(getResources(), bitmap);
                if (bitmap.getWidth() > bitmap.getHeight()) {
                    drawable.setBounds(0, 0, mIconWidth,
                            mIconWidth * bitmap.getHeight() / bitmap.getWidth());
                } else {
                    drawable.setBounds(0, 0,
                            mIconHeight * bitmap.getWidth() / bitmap.getHeight(),
                            mIconHeight);
                }
            }
            BitmapUtils.setColorFilterToDrawable(mIconColorFilter, drawable);
            mAppInfoView.setCompoundDrawablesRelative(drawable, null, null, null);
        } else if (type == Channel.LOAD_IMAGE_TYPE_APP_LINK_POSTER_ART) {
            if (bitmap == null) {
                setCardImageWithBanner(
                        mTvInputManagerHelper.getTvInputAppInfo(mChannel.getInputId()));
            } else {
                mImageView.setImageBitmap(bitmap);
                mImageView.setForeground(getContext().getDrawable(R.drawable.card_image_gradient));
                if (mChannel.getAppLinkColor() == 0) {
                    extractAndSetMetaViewBackgroundColor(bitmap);
                }
            }
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mImageView = (ImageView) findViewById(R.id.image);
        mAppInfoView = (TextView) findViewById(R.id.app_info);
        mMetaViewHolder = findViewById(R.id.app_link_text_holder);
    }

    // Try to set the card image with following order:
    // 1) Provided poster art image, 2) Activity banner, 3) Activity icon, 4) Application banner,
    // 5) Application icon, and 6) default image.
    private void setCardImageWithBanner(ApplicationInfo appInfo) {
        new AsyncTask<Void, Void, Drawable>() {
            private String mLoadTvInputId = mChannel.getInputId();
            @Override
            protected Drawable doInBackground(Void... params) {
                Drawable banner = null;
                if (mIntent != null) {
                    try {
                        banner = mPackageManager.getActivityBanner(mIntent);
                        if (banner == null) {
                            banner = mPackageManager.getActivityIcon(mIntent);
                        }
                    } catch (PackageManager.NameNotFoundException e) {
                        // do nothing.
                    }
                }
                return banner;
            }

            @Override
            protected void onPostExecute(Drawable banner) {
                if (mLoadTvInputId != mChannel.getInputId() || !isAttachedToWindow()) {
                    return;
                }
                if (banner != null) {
                    setCardImageWithBannerInternal(banner);
                } else {
                    setCardImageWithApplicationInfoBanner(appInfo);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void setCardImageWithApplicationInfoBanner(ApplicationInfo appInfo) {
        Drawable appBanner =
                mTvInputManagerHelper.getTvInputApplicationBanner(mChannel.getInputId());
        if (appBanner != null) {
            setCardImageWithBannerInternal(appBanner);
        } else {
            new AsyncTask<Void, Void, Drawable>() {
                private final String mLoadTvInputId = mChannel.getInputId();
                @Override
                protected Drawable doInBackground(Void... params) {
                    Drawable banner = null;
                    if (appInfo != null) {
                        if (appInfo.banner != 0) {
                            banner = mPackageManager.getApplicationBanner(appInfo);
                        }
                        if (banner == null && appInfo.icon != 0) {
                            banner = mPackageManager.getApplicationIcon(appInfo);
                        }
                    }
                    return banner;
                }

                @Override
                protected void onPostExecute(Drawable banner) {
                    mTvInputManagerHelper.setTvInputApplicationBanner(mLoadTvInputId, banner);
                    if (!TextUtils.equals(mLoadTvInputId, mChannel.getInputId())
                            || !isAttachedToWindow()) {
                        return;
                    }
                    setCardImageWithBannerInternal(banner);
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    private void setCardImageWithBannerInternal(Drawable banner) {
        if (banner == null) {
            mImageView.setImageDrawable(mDefaultDrawable);
            mImageView.setBackgroundResource(R.color.channel_card);
        } else {
            Bitmap bitmap = Bitmap.createBitmap(
                    mCardImageWidth, mCardImageHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            banner.setBounds(0, 0, mCardImageWidth, mCardImageHeight);
            banner.draw(canvas);
            mImageView.setImageDrawable(banner);
            mImageView.setForeground(getContext().getDrawable(R.drawable.card_image_gradient));
            if (mChannel.getAppLinkColor() == 0) {
                extractAndSetMetaViewBackgroundColor(bitmap);
            }
        }
    }

    private void extractAndSetMetaViewBackgroundColor(Bitmap bitmap) {
        new Palette.Builder(bitmap).generate(new Palette.PaletteAsyncListener() {
            @Override
            public void onGenerated(Palette palette) {
                mMetaViewHolder.setBackgroundColor(palette.getDarkVibrantColor(
                        getResources().getColor(R.color.channel_card_meta_background, null)));
            }
        });
    }
}
