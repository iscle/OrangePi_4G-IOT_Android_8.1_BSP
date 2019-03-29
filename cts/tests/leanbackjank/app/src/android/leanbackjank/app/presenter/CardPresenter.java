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

package android.leanbackjank.app.presenter;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.Presenter;
import android.view.ViewGroup;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import android.leanbackjank.app.R;
import android.leanbackjank.app.model.Movie;

/**
 * A CardPresenter is used to generate Views and bind Objects to them on demand.
 * It contains an Image CardView
 */
public class CardPresenter extends Presenter {
    private static int CARD_WIDTH = 560;
    private static int CARD_HEIGHT = 320;
    private static int sSelectedBackgroundColor;
    private static int sDefaultBackgroundColor;
    private Drawable mDefaultCardImage;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        sDefaultBackgroundColor = parent.getResources().getColor(R.color.default_background, null);
        sSelectedBackgroundColor =
                parent.getResources().getColor(R.color.selected_background, null);
        mDefaultCardImage = parent.getResources().getDrawable(R.drawable.movie, null);

        ImageCardView cardView = new ImageCardView(parent.getContext()) {
            @Override
            public void setSelected(boolean selected) {
                findViewById(R.id.info_field).setBackgroundColor(
                        selected ? sSelectedBackgroundColor : sDefaultBackgroundColor);
                super.setSelected(selected);
            }
        };

        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        return new ViewHolder(cardView);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        Movie movie = (Movie) item;
        ImageCardView cardView = (ImageCardView) viewHolder.view;

        cardView.setTitleText(movie.getTitle());
        cardView.setContentText(movie.getStudio());
        cardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT);
        cardView.setBackgroundColor(sDefaultBackgroundColor);

        Glide.with(viewHolder.view.getContext())
                .load(R.drawable.gradation)
                .centerCrop()
                .error(mDefaultCardImage)
                .listener(new RequestListener<Integer, GlideDrawable>() {
                    @Override
                    public boolean onException(Exception e, Integer i, Target<GlideDrawable> target,
                            boolean b) {
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(GlideDrawable glideDrawable, Integer i,
                            Target<GlideDrawable> target, boolean b, boolean b1) {
                        // Remove the background color to reduce overdraw.
                        cardView.setBackground(null);
                        return false;
                    }
                })
                .into(cardView.getMainImageView());
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        ImageCardView cardView = (ImageCardView) viewHolder.view;
        // Remove references to images so that the garbage collector can free up memory
        cardView.setBadgeImage(null);
        cardView.setMainImage(null);
    }
}
