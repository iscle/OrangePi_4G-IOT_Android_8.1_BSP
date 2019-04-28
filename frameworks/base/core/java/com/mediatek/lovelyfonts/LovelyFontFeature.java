package com.mediatek.lovelyfonts;

import android.widget.TextView;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemProperties;
import java.lang.reflect.Method;
import dalvik.system.PathClassLoader;

public class LovelyFontFeature {

    /**
     * {@hide}
     */
   public boolean isDefaultSystemFont = false;

   /**
    * flag to get whether current using system font font is LovelyFont
    * @hide
    */
   public static boolean sUseLovelyFont = false;
    /**
     * flag to get whether user has used LovelyFont before
     * @hide
    */
   public static boolean sCheckUsedLoveyFont = false;
   private static Method updateLovelyFontMethod = null;
   private static Method getLovelyFontTypefaceMethod = null;
   private static Method isDefaultTypefaceMethod = null;
   private static Method updateLovelyFontTypefaceMethod = null;
   private static Method getPaintTypefaceInstanceMethd = null;
   private static final String JAR_PATH = "/system/framework/LibFonts.jar";
   private static Class<?> mLovelyFontClass;
   /**
    * @hide
    */
   public static void updateLovelyFont() {
       if (SystemProperties.getBoolean("persist.sys.lovelyfonts.used", false)) {
           try {
               if(updateLovelyFontMethod == null){
                   mLovelyFontClass = getLovelyFontClass();
                   updateLovelyFontMethod =
                       mLovelyFontClass.getMethod("updateLovelyFont", (Class[]) null);
               }
               Typeface.sTypefaceCache.clear();
               updateLovelyFontMethod.invoke(null, (Object[]) null);
           } catch (Exception e) {
               e.printStackTrace();
           }
       }
   }

   /**
    * @hide
    */
   public static long getLovelyFontTypeface(long nativePaint,long nativeTypeface, Typeface typeface
                                            ,boolean setTypeface) {
       long paintInstance = nativeTypeface;
       if(LovelyFontFeature.sCheckUsedLoveyFont){
           try {
               if(getLovelyFontTypefaceMethod == null){
                   mLovelyFontClass = getLovelyFontClass();
                   getLovelyFontTypefaceMethod = mLovelyFontClass.
                       getMethod("getLovelyFontTypefaceInstance", new Class[] {long.class,
                           long.class, Typeface.class,boolean.class });
               }
               paintInstance = (long) getLovelyFontTypefaceMethod.invoke(null,
                   new Object[] {nativePaint, nativeTypeface, typeface,setTypeface });
           } catch (Exception e) {
               e.printStackTrace();
           }
       }
       return paintInstance;
   }
   /**
    * @hide
    */
   public static void initLovelyFontdata() {
       if ("1".equals(SystemProperties.get("ro.lovelyfonts_support", "0"))) {
           try {
               mLovelyFontClass = getLovelyFontClass();
               mLovelyFontClass.getMethod("initLovelyFontdata",
                   (Class[]) null).invoke(null, (Object[]) null);
               updateLovelyFontMethod =
                   mLovelyFontClass.getMethod("updateLovelyFont", (Class[]) null);
               getLovelyFontTypefaceMethod =
                   mLovelyFontClass.getMethod("getLovelyFontTypefaceInstance",
                       new Class[] { long.class, long.class,Typeface.class,boolean.class });
               isDefaultTypefaceMethod =
                   mLovelyFontClass.getMethod("isDefaultSystemUseFont", String.class);
               updateLovelyFontTypefaceMethod =
                   mLovelyFontClass.getMethod("updateLovelyFontTypeface", TextView.class);
               getPaintTypefaceInstanceMethd =
                   mLovelyFontClass.getDeclaredMethod("getPaintTypefceInstance", Paint.class);
           } catch (Exception e) {
               e.printStackTrace();
           }
       }
   }

   /**
    * @hide
    */
   public static long getPaintTypefaceInstance(Paint paint){
       if(LovelyFontFeature.sCheckUsedLoveyFont && LovelyFontFeature.sUseLovelyFont){
           try {
               if(getPaintTypefaceInstanceMethd == null){
                   mLovelyFontClass = getLovelyFontClass();
                   getPaintTypefaceInstanceMethd =
                       mLovelyFontClass.getMethod("getPaintTypefceInstance", Paint.class);
               }
               return (long) getPaintTypefaceInstanceMethd.invoke(null, paint);
           } catch (Exception e) {
               e.printStackTrace();
           }
       }
       return paint.mNativeTypeface;
   }
   /**
    * @hide
    */
   public static long getDefaultTypefaceInstance(Typeface family,int style){
       if("1".equals(SystemProperties.get("ro.lovelyfonts_support", "0"))){
           if(LovelyFontFeature.sUseLovelyFont && family.isDefaultSystemFont){
               return Typeface.sDefaults[style].native_instance;
               //return 0;
           }
       }
       return family.native_instance;
   }

   /**
    * @hide
    */
   public static boolean getDefaultSystemUseFontFlag(Typeface family){
       if (family != null) {
               return family.isDefaultSystemFont;
       }
       return true;
   }

   private static Class<?> getLovelyFontClass(){
       if(mLovelyFontClass == null){
           PathClassLoader classLoder =
               new PathClassLoader(JAR_PATH,LovelyFontFeature.class.getClassLoader());
           try {
                mLovelyFontClass =
                    classLoder.loadClass("com.android.lovelyfonts.LovelyFontTypeface");
           } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
           }
       }
       return mLovelyFontClass;
   }

}
