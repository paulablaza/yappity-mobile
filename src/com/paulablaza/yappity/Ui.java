package com.paulablaza.yappity;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;

// Colors and small drawing helpers. The app follows the phone's light or dark mode,
// the floating box is always dark so it reads on top of any app.
final class Ui {
    static final int ACCENT = 0xFF8B5CF6;
    static final int ACCENT_SOFT = 0xFFC4B5FD;
    static final int GOOD = 0xFF4ADE80;
    static final int GOOD_DEEP = 0xFF16A34A;
    static final int BAD = 0xFFF87171;
    static final int PANEL = 0xF2141418;
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    static boolean dark;
    static int bg, card, border, text, muted, chip;

    static void init(Context c) {
        dark = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (dark) {
            bg = 0xFF0C0C10; card = 0xFF17171C; border = 0xFF26262E; text = 0xFFF4F4F5; muted = 0xFF9F9FA9; chip = 0xFF22222A;
        } else {
            bg = 0xFFF6F6F9; card = 0xFFFFFFFF; border = 0xFFE4E4EA; text = 0xFF18181B; muted = 0xFF6B6B76; chip = 0xFFF0EEFB;
        }
    }

    static GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }
}
