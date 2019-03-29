/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package android.leanbackjank.app.ui;

import android.content.Intent;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.leanbackjank.app.IntentKeys;
import android.leanbackjank.app.R;
import android.leanbackjank.app.data.VideoProvider;
import android.leanbackjank.app.model.Movie;
import android.leanbackjank.app.presenter.CardPresenter;
import android.leanbackjank.app.presenter.IconHeaderItemPresenter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.PresenterSelector;
import android.support.v17.leanback.widget.ShadowOverlayHelper;
import android.util.DisplayMetrics;
import android.view.View;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

class ScrollPatternGenerator {
    private int currentRow;
    private int topRow;
    private int bottomRow;
    private enum ScrollDirection {DOWN, UP};
    private ScrollDirection direction;

    public ScrollPatternGenerator(int topRow, int bottomRow) {
        this.topRow = topRow;
        this.bottomRow = bottomRow;
        assert(topRow < bottomRow);
        direction = ScrollDirection.DOWN;
        currentRow = topRow;
    }

    public int next() {
        if (currentRow == bottomRow) {
            direction = ScrollDirection.UP;
        } else if (currentRow == topRow) {
            direction = ScrollDirection.DOWN;
        }
        if (direction == ScrollDirection.DOWN) {
            currentRow++;
        } else {
            currentRow--;
        }
        return currentRow;
    }
}

/**
 * Main class to show BrowseFragment with header and rows of videos
 */
public class MainFragment extends BrowseFragment {
    private static final int NUM_ROWS = 20;

    // Minimum number of rows that should show above / below the cursor by auto scroll.
    // This is intended to avoid that the work load fluctuates largely when # of cards in the
    // display lowers when the cursor is positioned near the top or bottom of the list.
    private static final int MARGIN_ROWS = 4;

    private final Handler mHandler = new Handler();
    private Timer mAutoScrollTimer;
    private int mAutoScrollCount;

    private ArrayObjectAdapter mRowsAdapter;
    private DisplayMetrics mMetrics;
    private BackgroundManager mBackgroundManager;
    private ScrollPatternGenerator scrollPatternGenerator;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        buildRowAdapterItems(VideoProvider.buildMedia(NUM_ROWS));
        prepareBackgroundManager();
        setupUIElements();
        setupEventListeners();
        Intent intent = getActivity().getIntent();
        if (intent.getExtras() != null) {
            int initialDelay = intent.getExtras().getInt(IntentKeys.SCROLL_DELAY);
            int scrollCount = intent.getExtras().getInt(IntentKeys.SCROLL_COUNT);
            int scrollInterval = intent.getExtras().getInt(IntentKeys.SCROLL_INTERVAL);
            if (scrollInterval != 0 && scrollCount != 0) {
                startAutoScrollTimer(initialDelay, scrollInterval, scrollCount);
            }
            scrollPatternGenerator =
                    new ScrollPatternGenerator(MARGIN_ROWS, NUM_ROWS - 1 - MARGIN_ROWS);
        }
    }

    @Override
    public void onDestroy() {
        if (null != mAutoScrollTimer) {
            mAutoScrollTimer.cancel();
            mAutoScrollTimer = null;
        }
        super.onDestroy();
    }

    @Override
    public void onStop() {
        mBackgroundManager.release();
        super.onStop();
    }

    private void prepareBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(getActivity());
        mBackgroundManager.attach(getActivity().getWindow());
        mBackgroundManager.setDrawable(getActivity().getResources().getDrawable(
                R.drawable.default_background, getContext().getTheme()));
        mMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    private void setupUIElements() {
        setBadgeDrawable(getActivity().getResources().getDrawable(
                R.drawable.videos_by_google_banner, getContext().getTheme()));
        setTitle(getString(R.string.browse_title));
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);

        Theme theme = getContext().getTheme();
        setBrandColor(getResources().getColor(R.color.fastlane_background, theme));

        setSearchAffordanceColor(getResources().getColor(R.color.search_opaque, theme));

        setHeaderPresenterSelector(new PresenterSelector() {
            @Override
            public Presenter getPresenter(Object o) {
                return new IconHeaderItemPresenter();
            }
        });
    }

    private void setupEventListeners() {
        // Add lister to show the search button.
        setOnSearchClickedListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
            }
        });
    }

    public void buildRowAdapterItems(HashMap<String, List<Movie>> data) {
        ListRowPresenter listRowPresenter = new ListRowPresenter() {
            @Override
            protected ShadowOverlayHelper.Options createShadowOverlayOptions() {
                Resources res = getResources();
                ShadowOverlayHelper.Options options = new ShadowOverlayHelper.Options();
                options.dynamicShadowZ(res.getDimension(R.dimen.shadow_unfocused_z),
                        res.getDimension(R.dimen.shadow_focused_z));
                return options;
            }
        };
        mRowsAdapter = new ArrayObjectAdapter(listRowPresenter);
        CardPresenter cardPresenter = new CardPresenter();

        int i = 0;

        for (Map.Entry<String, List<Movie>> entry : data.entrySet()) {
            ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(cardPresenter);
            List<Movie> list = entry.getValue();

            for (int j = 0; j < list.size(); j++) {
                listRowAdapter.add(list.get(j));
            }
            HeaderItem header = new HeaderItem(i, entry.getKey());
            i++;
            mRowsAdapter.add(new ListRow(header, listRowAdapter));
        }

        setAdapter(mRowsAdapter);
    }

    private class UpdateAutoScrollTask extends TimerTask {
        @Override
        public void run() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mAutoScrollCount == 0) {
                      mAutoScrollTimer.cancel();
                      return;
                    }
                    setSelectedPosition(scrollPatternGenerator.next());
                    mAutoScrollCount--;
                }
            });
        }
    }

    private void startAutoScrollTimer(int initialDelay, int interval, int count) {
        if (null != mAutoScrollTimer) {
            mAutoScrollTimer.cancel();
        }
        mAutoScrollCount = count;
        mAutoScrollTimer = new Timer();
        mAutoScrollTimer.schedule(new UpdateAutoScrollTask(), initialDelay, interval);
    }

    public void selectRow(int row) {
        setSelectedPosition(row);
    }
}
