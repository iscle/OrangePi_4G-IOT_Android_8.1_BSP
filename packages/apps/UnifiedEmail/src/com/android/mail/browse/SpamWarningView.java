package com.android.mail.browse;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.emailcommon.mail.Address;
import com.android.mail.R;
import com.android.mail.providers.Message;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.Utils;

public class SpamWarningView extends TextView {
    // Prefix added to the Spam warning text so that ImageSpan overrides it to
    // display the alert icon.
    private static final String SPANNABLE_SPAM_WARNING_PREFIX = ".";

    private final int mHighWarningColor;
    private final int mLowWarningColor;
    private final int mHighWarningBackgroundColor;
    private final int mLowWarningBackgroundColor;

    public SpamWarningView(Context context) {
        this(context, null);
    }

    public SpamWarningView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mHighWarningColor = getResources().getColor(R.color.high_spam_color);
        mHighWarningBackgroundColor = getResources().getColor(
            R.color.high_spam_warning_background_color);
        mLowWarningColor = getResources().getColor(R.color.low_spam_color);
        mLowWarningBackgroundColor = getResources().getColor(
            R.color.low_spam_warning_background_color);
    }

    public void showSpamWarning(Message message, Address sender) {
        setVisibility(VISIBLE);

        // Sets the text and adds any necessary formatting
        // to enable the proper display.
        final String senderAddress = sender.getAddress();
        final String senderDomain = senderAddress.substring(senderAddress.indexOf('@')+1);
        final String spamWarningText = Utils.convertHtmlToPlainText(String.format(
                message.spamWarningString, senderAddress, senderDomain));
        final int alertIconResourceId;
        if (message.spamWarningLevel == UIProvider.SpamWarningLevel.HIGH_WARNING) {
            setBackgroundColor(mHighWarningBackgroundColor);
            setTextColor(mHighWarningColor);
            alertIconResourceId = R.drawable.ic_warning_white;
        } else {
            setBackgroundColor(mLowWarningBackgroundColor);
            setTextColor(mLowWarningColor);
            alertIconResourceId = R.drawable.ic_warning_gray;
        }
        final Drawable alertIcon = getResources().getDrawable(alertIconResourceId);
        alertIcon.setBounds(0, 0, alertIcon.getIntrinsicWidth(), alertIcon.getIntrinsicHeight());
        ImageSpan imageSpan = new ImageSpan(alertIcon, ImageSpan.ALIGN_BASELINE);

        // Spam warning format: <Alert Icon><space><warning message>
        SpannableString ss = new SpannableString(
            SPANNABLE_SPAM_WARNING_PREFIX + " " + spamWarningText);
        ss.setSpan(imageSpan, 0, SPANNABLE_SPAM_WARNING_PREFIX.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        setText(ss);
    }
}
