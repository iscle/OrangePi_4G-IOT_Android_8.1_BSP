package com.android.mail.browse;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.android.mail.R;

/**
 * An image view that respects a custom drawable state (state_starred)
 * that enables a src or background drawable to use to automatically
 * switch between the starred and unstarred state.
 */
public class StarView extends ImageView {

    private static final int[] STATE_STARRED = {R.attr.state_starred};

    private boolean mIsStarred;

    public StarView(Context context) {
        super(context);
    }

    public StarView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StarView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Set the starred state of the view.
     */
    public void setStarred(boolean isStarred) {
        mIsStarred = isStarred;
        setContentDescription(
                getResources().getString(mIsStarred ? R.string.remove_star : R.string.add_star));
        refreshDrawableState();
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (mIsStarred) {
            mergeDrawableStates(drawableState, STATE_STARRED);
        }
        return drawableState;
    }
}
