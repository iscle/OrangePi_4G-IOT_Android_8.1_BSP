package com.android.car.app;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.car.stream.ui.R;
import com.android.car.view.PagedListView;

/**
 * Base Adapter for displaying items in the CarDrawerActivity's Drawer which uses a PagedListView.
 * <p>
 * Subclasses must set the title that will be displayed when displaying the contents of the
 * Drawer via {@link #setTitle(CharSequence)}. The title can be updated at any point later. The
 * title of the root-adapter will also be the main title showed in the toolbar when the drawer is
 * closed.
 * <p>
 * This class also takes care of implementing the PageListView.ItemCamp contract and subclasses
 * should implement {@link #getActualItemCount()}.
 */
public abstract class CarDrawerAdapter extends RecyclerView.Adapter<DrawerItemViewHolder> implements
        PagedListView.ItemCap,
        DrawerItemClickListener {
    interface TitleChangeListener {
        void onTitleChanged(CharSequence newTitle);
    }

    private final boolean mShowDisabledListOnEmpty;
    private final Drawable mEmptyListDrawable;
    private int mMaxItems = -1;
    private CharSequence mTitle;
    private TitleChangeListener mTitleChangeListener;

    protected CarDrawerAdapter(
            Context context, boolean showDisabledListOnEmpty) {
        mShowDisabledListOnEmpty = showDisabledListOnEmpty;
        final int iconColor = context.getColor(R.color.car_tint);
        mEmptyListDrawable = context.getDrawable(R.drawable.ic_list_view_disable);
        mEmptyListDrawable.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
    }

    CharSequence getTitle() {
        return mTitle;
    }

    /**
     * Updates the title to display in the toolbar for this Adapter.
     *
     * @param title Title string.
     */
    public final void setTitle(@NonNull CharSequence title) {
        if (title == null) {
            throw new IllegalArgumentException("title is null!");
        }
        mTitle = title;
        if (mTitleChangeListener != null) {
            mTitleChangeListener.onTitleChanged(mTitle);
        }
    }

    void setTitleChangeListener(@Nullable TitleChangeListener listener) {
        mTitleChangeListener = listener;
    }

    // ItemCap implementation.
    @Override
    public final void setMaxItems(int maxItems) {
        mMaxItems = maxItems;
    }

    private boolean shouldShowDisabledListItem() {
        return mShowDisabledListOnEmpty && getActualItemCount() == 0;
    }

    // Honors ItemCap and mShowDisabledListOnEmpty.
    @Override
    public final int getItemCount() {
        if (shouldShowDisabledListItem()) {
            return 1;
        }
        return mMaxItems >= 0 ? Math.min(mMaxItems, getActualItemCount()) : getActualItemCount();
    }

    /**
     * @return Actual number of items in this adapter.
     */
    protected abstract int getActualItemCount();

    @Override
    public final int getItemViewType(int position) {
        if (shouldShowDisabledListItem()) {
            return R.layout.car_list_item_empty;
        }
        return usesSmallLayout(position)
                ? R.layout.car_menu_list_item_small : R.layout.car_menu_list_item_normal;
    }

    /**
     * Used to indicate the layout used for the Drawer item at given position. Subclasses can
     *  override this to use normal layout which includes text element below title.
     *
     * @param position Adapter position of item.
     * @return Whether the item at this position will use a small layout (default) or normal layout.
     */
    protected boolean usesSmallLayout(int position) {
        return true;
    }

    @Override
    public final DrawerItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
        return new DrawerItemViewHolder(view);
    }

    @Override
    public final void onBindViewHolder(DrawerItemViewHolder holder, int position) {
        if (shouldShowDisabledListItem()) {
            holder.getTitle().setText(null);
            holder.getIcon().setImageDrawable(mEmptyListDrawable);
            holder.setItemClickListener(null);
        } else {
            holder.setItemClickListener(this);
            populateViewHolder(holder, position);
        }
    }

    /**
     * Subclasses should set all elements in {@code holder} to populate the drawer-item.
     * If some element is not used, it should be nulled out since these ViewHolder/View's are
     * recycled.
     */
    protected abstract void populateViewHolder(DrawerItemViewHolder holder, int position);

    /**
     * Called when this adapter has been popped off the stack and is no longer needed. Subclasses
     * can override to do any necessary cleanup.
     */
    public void cleanup() {}
}
