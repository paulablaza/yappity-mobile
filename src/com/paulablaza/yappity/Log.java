package com.paulablaza.yappity;

final class Log {
    static void i(String msg) { android.util.Log.i("Yappity", msg); }
    static void w(String msg) { android.util.Log.w("Yappity", msg); }
}
