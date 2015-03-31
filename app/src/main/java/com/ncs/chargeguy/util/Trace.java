package com.ncs.chargeguy.util;

import android.util.Log;

// Lightweight re-usable helper code to manage formatted logcat output.

public final class Trace {
    // Various tiny helper methods for collecting trace output in one spot:
    public final static boolean DEBUG_MODE = false;
    public final static String TAG = "chargeguy";

    public static void debug(String what) {
        if (DEBUG_MODE) {
            Log.d(TAG, what);
        }
    }

    public static void debug(String what, Object... args) {
        if (DEBUG_MODE) { // Extra check to bypass String.format wasting.
            debug(String.format(what, args));
        }
    }

    public static void warn(String what) {
        if (DEBUG_MODE) {
            Log.w(TAG, what);
        }
    }

    public static void warn(String what, Object... args) {
        if (DEBUG_MODE) { // Extra check to bypass String.format wasting.
            warn(String.format(what, args));
        }
    }

    public static void error(String what, Object... args) {
        Log.e(TAG, String.format(what, args));
    }

    public static void error(Throwable excep, String what, Object... args) {
        Log.e(TAG, String.format(what, args), excep);
    }

    public static void error(Throwable excep, String what) {
        Log.e(TAG, what, excep);
    }

    public static void error(String what) {
        Log.e(TAG, what);
    }

    static {
        Log.w(TAG, "---- Starting up");
    }
}
