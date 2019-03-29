/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.example.android.tv.channelsprograms.playback;

import android.content.Context;
import android.os.Handler;
import android.support.v17.leanback.media.PlaybackTransportControlGlue;
import android.support.v17.leanback.media.PlayerAdapter;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.PlaybackControlsRow;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

/**
 * Handles common primary and secondary actions such as repeat, thumbs up/down, picture in picture,
 * and closed captions.
 */
class SimplePlaybackTransportControlGlue<T extends PlayerAdapter>
        extends PlaybackTransportControlGlue<T> {

    private PlaybackControlsRow.RepeatAction mRepeatAction;
    private PlaybackControlsRow.ThumbsUpAction mThumbsUpAction;
    private PlaybackControlsRow.ThumbsDownAction mThumbsDownAction;
    private PlaybackControlsRow.PictureInPictureAction mPipAction;
    private PlaybackControlsRow.ClosedCaptioningAction mClosedCaptioningAction;
    private Handler mHandler = new Handler();

    public SimplePlaybackTransportControlGlue(Context context, T impl) {
        super(context, impl);
        mClosedCaptioningAction = new PlaybackControlsRow.ClosedCaptioningAction(context);
        mThumbsUpAction = new PlaybackControlsRow.ThumbsUpAction(context);
        mThumbsUpAction.setIndex(PlaybackControlsRow.ThumbsUpAction.OUTLINE);
        mThumbsDownAction = new PlaybackControlsRow.ThumbsDownAction(context);
        mThumbsDownAction.setIndex(PlaybackControlsRow.ThumbsDownAction.OUTLINE);
        mRepeatAction = new PlaybackControlsRow.RepeatAction(context);
        mPipAction = new PlaybackControlsRow.PictureInPictureAction(context);
    }

    @Override
    protected void onCreatePrimaryActions(ArrayObjectAdapter adapter) {
        super.onCreatePrimaryActions(adapter);
        adapter.add(mRepeatAction);
        adapter.add(mClosedCaptioningAction);
    }

    @Override
    protected void onCreateSecondaryActions(ArrayObjectAdapter adapter) {
        super.onCreateSecondaryActions(adapter);
        adapter.add(mThumbsUpAction);
        adapter.add(mThumbsDownAction);
        adapter.add(mPipAction);
    }

    @Override
    public void onActionClicked(Action action) {
        if (shouldDispatchAction(action)) {
            dispatchAction(action, getPrimaryActionsAdapter());
            dispatchAction(action, getSecondaryActionsAdapter());
            return;
        }
        super.onActionClicked(action);
    }

    @Override
    public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
        if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
            boolean dispatched = dispatchAction(keyEvent, getPrimaryActionsAdapter());
            dispatched |= dispatchAction(keyEvent, getSecondaryActionsAdapter());
            if (dispatched) {
                return true;
            }
        }
        return super.onKey(view, keyCode, keyEvent);
    }

    private boolean dispatchAction(KeyEvent keyEvent, ArrayObjectAdapter adapter) {
        Action action = getControlsRow().getActionForKeyCode(adapter, keyEvent.getKeyCode());
        if (shouldDispatchAction(action)) {
            dispatchAction(action, adapter);
            return true;
        }
        return false;
    }

    private boolean shouldDispatchAction(Action action) {
        return action == mRepeatAction || action == mThumbsUpAction || action == mThumbsDownAction;
    }

    private void dispatchAction(Action action, ArrayObjectAdapter adapter) {
        Toast.makeText(getContext(), action.toString(), Toast.LENGTH_SHORT).show();
        PlaybackControlsRow.MultiAction multiAction = (PlaybackControlsRow.MultiAction) action;
        multiAction.nextIndex();
        notifyActionChanged(multiAction, adapter);
    }

    private void notifyActionChanged(
            PlaybackControlsRow.MultiAction action, ArrayObjectAdapter adapter) {
        if (adapter != null) {
            int index = adapter.indexOf(action);
            if (index >= 0) {
                adapter.notifyArrayItemRangeChanged(index, 1);
            }
        }
    }

    private ArrayObjectAdapter getPrimaryActionsAdapter() {
        if (getControlsRow() == null) {
            return null;
        }
        return (ArrayObjectAdapter) getControlsRow().getPrimaryActionsAdapter();
    }

    private ArrayObjectAdapter getSecondaryActionsAdapter() {
        if (getControlsRow() == null) {
            return null;
        }
        return (ArrayObjectAdapter) getControlsRow().getSecondaryActionsAdapter();
    }

    @Override
    protected void onPlayCompleted() {
        super.onPlayCompleted();
        mHandler.post(
                () -> {
                    if (mRepeatAction.getIndex() != PlaybackControlsRow.RepeatAction.INDEX_NONE) {
                        play();
                    }
                });
    }

    /**
     * Sets the behavior for the repeat action. The possible modes are
     *
     * <ul>
     *   <li>{@link PlaybackControlsRow.RepeatAction#INDEX_NONE}
     *   <li>{@link PlaybackControlsRow.RepeatAction#INDEX_ALL}
     *   <li>{@link PlaybackControlsRow.RepeatAction#INDEX_ONE}
     * </ul>
     *
     * @param mode for repeat behavior.
     */
    public void setRepeatMode(int mode) {
        mRepeatAction.setIndex(mode);
        notifyActionChanged(mRepeatAction, getPrimaryActionsAdapter());
    }
}
