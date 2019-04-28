package com.android.mail.text;

import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;

import com.android.mail.browse.ConversationViewHeader.ConversationViewHeaderCallbacks;

/**
 * A custom span that enables the labels to be clickable in the conversation
 * header while still allowing the subject to be selectable.
 */
public class ChangeLabelsSpan extends ClickableSpan {

    private final ConversationViewHeaderCallbacks mCallbacks;

    public ChangeLabelsSpan(ConversationViewHeaderCallbacks callbacks) {
        mCallbacks = callbacks;
    }

    @Override
    public void onClick(View widget) {
        if (mCallbacks != null) {
            mCallbacks.onFoldersClicked();
        }
    }

    @Override
    public void updateDrawState(TextPaint ds) {
        // DO NOTHING
    }
}
