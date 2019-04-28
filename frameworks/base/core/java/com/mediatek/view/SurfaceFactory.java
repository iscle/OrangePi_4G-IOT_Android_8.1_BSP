package com.mediatek.view;

import android.util.Log;

public class SurfaceFactory {
    private static final String TAG = "SurfaceFactory";
    private static final String CLASS_NAME_SURFACE_FACTORY_IMPL
            = "com.mediatek.view.impl.SurfaceFactoryImpl";
    private static final SurfaceFactory sSurfaceFactory;

    static {
        SurfaceFactory surfaceFactory = null;
        try {
            Class clazz = Class.forName(CLASS_NAME_SURFACE_FACTORY_IMPL);
            surfaceFactory = (SurfaceFactory) clazz.newInstance();
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "[static] ClassNotFoundException", e);
        } catch (InstantiationException e) {
            Log.e(TAG, "[static] InstantiationException", e);
        } catch (IllegalAccessException e) {
            Log.e(TAG, "[static] InstantiationException", e);
        }
        sSurfaceFactory = ((surfaceFactory != null) ? surfaceFactory : new SurfaceFactory());

        Log.i(TAG, "[static] sSurfaceFactory = " + sSurfaceFactory);
    }

    public static final SurfaceFactory getInstance() {
        return sSurfaceFactory;
    }

    public SurfaceExt getSurfaceExt() {
        return new SurfaceExt();
    }
}
