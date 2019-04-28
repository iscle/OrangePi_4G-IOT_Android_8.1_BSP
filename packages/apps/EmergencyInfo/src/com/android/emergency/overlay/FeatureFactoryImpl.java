package com.android.emergency.overlay;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.Keep;

import com.android.emergency.edit.EmergencyContactsFeatureProvider;
import com.android.emergency.edit.EmergencyContactsFeatureProviderImpl;

/**
 * {@link FeatureFactory} implementation for AOSP Emergency Info.
 */
@Keep
public class FeatureFactoryImpl extends FeatureFactory {
    protected EmergencyContactsFeatureProvider mEmergencyContactsFeatureProvider;

    @Override
    public EmergencyContactsFeatureProvider getEmergencyContactsFeatureProvider() {
        if (mEmergencyContactsFeatureProvider == null) {
            mEmergencyContactsFeatureProvider = new EmergencyContactsFeatureProviderImpl();
        }
        return mEmergencyContactsFeatureProvider;
    }
}
