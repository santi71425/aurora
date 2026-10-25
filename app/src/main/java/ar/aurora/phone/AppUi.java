package ar.aurora.phone;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class AppUi {
    static final int BG = Color.rgb(17,18,28), CARD = Color.rgb(30,32,47);
    static final int TEXT = Color.rgb(239,239,248), MUTED = Color.rgb(175,178,198), ACCENT = Color.rgb(182,154,255);
    static int dp(Context c, int n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    static TextView text(Context c, String value, int size, int color) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t;
    }
    static Button button(Context c, String value, boolean primary) {
        Button b = new Button(c); b.setText(value); b.setAllCaps(false); b.setTextSize(14);
        b.setTextColor(primary ? BG : TEXT); b.setBackgroundTintList(ColorStateList.valueOf(primary ? ACCENT : CARD)); return b;
    }
    static LinearLayout column(Context c) { LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v; }
    static LinearLayout card(Context c) {
        LinearLayout v = column(c); int p = dp(c,16); v.setPadding(p,p,p,p);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(CARD); bg.setCornerRadius(dp(c,18)); v.setBackground(bg); return v;
    }
    static LinearLayout.LayoutParams spaced(Context c) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(c,10); return p;
    }
    static void insets(Activity a, LinearLayout root) {
        root.setBackgroundColor(BG); int p = dp(a,18); root.setPadding(p,p,p,p);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            view.setPadding(p + bars.left,p + bars.top,p + bars.right,p + bars.bottom); return insets;
        });
        root.requestApplyInsets();
    }
}
