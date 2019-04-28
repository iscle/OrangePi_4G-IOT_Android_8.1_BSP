package android.databinding.tool.writer;

class DynamicUtilWriter() {
    public fun write(targetSdk : kotlin.Int) : KCode = kcode("package android.databinding;") {
        nl("")
        nl("import android.os.Build.VERSION;")
        nl("import android.os.Build.VERSION_CODES;")
        nl("")
        block("public class DynamicUtil") {
            nl("@SuppressWarnings(\"deprecation\")")
            block("public static int getColorFromResource(final android.view.View view, final int resourceId)") {
                if (targetSdk >= 23) {
                    block("if (VERSION.SDK_INT >= VERSION_CODES.M)") {
                        nl("return view.getContext().getColor(resourceId);")
                    }
                }
                nl("return view.getResources().getColor(resourceId);")
            }

            nl("@SuppressWarnings(\"deprecation\")")
            block("public static android.content.res.ColorStateList getColorStateListFromResource(final android.view.View view, final int resourceId)") {
                if (targetSdk >= 23) {
                    block("if (VERSION.SDK_INT >= VERSION_CODES.M)") {
                        nl("return view.getContext().getColorStateList(resourceId);")
                    }
                }
                nl("return view.getResources().getColorStateList(resourceId);")
            }

            nl("@SuppressWarnings(\"deprecation\")")
            block("public static android.graphics.drawable.Drawable getDrawableFromResource(final android.view.View view, final int resourceId)") {
                if (targetSdk >= 21) {
                    block("if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP)") {
                        nl("return view.getContext().getDrawable(resourceId);")
                    }
                }
                nl("return view.getResources().getDrawable(resourceId);")
            }

            block("public static boolean parse(String str, boolean fallback)") {
                block("if (str == null)") {
                    nl("return fallback;");
                }
                nl("return Boolean.parseBoolean(str);")
            }
            block("public static byte parse(String str, byte fallback)") {
                block("try") {
                    nl("return Byte.parseByte(str);")
                }
                block("catch (NumberFormatException e)") {
                    nl("return fallback;")
                }
            }
            block("public static short parse(String str, short fallback)") {
                block("try") {
                    nl("return Short.parseShort(str);")
                }
                block("catch (NumberFormatException e)") {
                    nl("return fallback;")
                }
            }
            block("public static int parse(String str, int fallback)") {
                block("try") {
                    nl("return Integer.parseInt(str);")
                }
                block("catch (NumberFormatException e)") {
                    nl("return fallback;")
                }
            }
            block("public static long parse(String str, long fallback)") {
                block("try") {
                    nl("return Long.parseLong(str);")
                }
                block("catch (NumberFormatException e)") {
                    nl("return fallback;")
                }
            }
            block("public static float parse(String str, float fallback)") {
                block("try") {
                    nl("return Float.parseFloat(str);")
                }
                block("catch (NumberFormatException e)") {
                    nl("return fallback;")
                }
            }
            block("public static double parse(String str, double fallback)") {
                block("try") {
                    nl("return Double.parseDouble(str);")
                }
                block("catch (NumberFormatException e)") {
                    nl("return fallback;")
                }
            }
            block("public static char parse(String str, char fallback)") {
                block ("if (str == null || str.isEmpty())") {
                    nl("return fallback;")
                }
                nl("return str.charAt(0);")
            }
        }
   }
}