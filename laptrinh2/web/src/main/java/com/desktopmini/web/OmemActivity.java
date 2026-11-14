package com.desktopmini.web;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebResponse;

/**
 * "Lập trình" của Desktop Mini = claude.ai với 10 trang (số 1 đến 10), chạy bằng Gecko trong app phụ riêng.
 * Mỗi trang có kho cookie riêng (contextId) nên đăng nhập 10 tài khoản khác nhau mà không lẫn nhau.
 * (Tên lớp OmemActivity và khóa "omem" giữ nguyên để launcher vẫn gọi đúng.)
 */
public class OmemActivity extends Activity {
    private static final String START_URL = "https://claude.ai/";
    private static final int REQ_FILE = 32;
    private static final int N = 10;
    private static final String[] COLORS = {"#4fc3f7", "#ff8a80", "#81c784", "#ffb74d", "#ba68c8",
        "#4db6ac", "#f06292", "#aed581", "#7986cb", "#a1887f"};

    private CloseRx closeRx;
    private boolean visible;
    private String restoreP = "", restoreUrl = "";
    private GeckoView view;
    private GeckoSession session;
    private LinearLayout home, bar, chips;
    private View homeView;
    private Ic icBack, icFwd;
    private String profile = "", url = "";
    private boolean backOk, fwdOk;
    private GeckoSession.PromptDelegate.FilePrompt filePrompt;
    private GeckoResult<GeckoSession.PromptDelegate.PromptResponse> fileRes;
    private final TextView[] chipViews = new TextView[N];

    private static String startUrl(String p) { return START_URL; }
    private static boolean valid(String p) {
        if (p == null) return false;
        try { int n = Integer.parseInt(p); return n >= 1 && n <= N && String.valueOf(n).equals(p); } catch (NumberFormatException e) { return false; }
    }

    private int dp(int v) { return GeckoCore.dp(this, v); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        GeckoCore.born();
        GeckoCore.rt(this);
        closeRx = CloseRx.reg(this);
        buildUi();
        hideBars();
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override public void onSystemUiVisibilityChange(int vis) { if ((vis & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) hideBars(); }
        });
        handle(getIntent());
    }

    @Override protected void onNewIntent(Intent it) { super.onNewIntent(it); setIntent(it); handle(it); }

    /** Ẩn thanh trạng thái/điều hướng như Trình duyệt: nội dung bắt đầu từ mép trên màn hình, nên phần chừa cho thanh menu nổi khớp đúng, không còn dải trống thừa phía trên hàng số trang. */
    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    private void hideBars() {
        immersive();
        getWindow().getDecorView().postDelayed(new Runnable() { public void run() { immersive(); } }, 400);
    }
    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) hideBars(); }
    @Override protected void onResume() { super.onResume(); hideBars(); }

    private void handle(Intent it) {
        String p = it == null ? null : it.getStringExtra("profile");
        if (p != null && valid(p)) open(p);   /* không chỉ định trang thì ở màn hình danh sách 1-10 để chọn */
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFE9E9EE);
        root.setPadding(0, GeckoCore.menuBarInset(this), 0, 0);   /* chừa chỗ cho thanh menu nổi của Desktop Mini */

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL); bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(2), 0, dp(2), 0);
        bar.addView(Ic.make(this, Ic.CLOSE, new View.OnClickListener() { public void onClick(View v) { dropSession(); showHome(); } }));   /* X = về danh sách 1-10 để chọn trang khác */
        chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL); chips.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < N; i++) {
            final String id = String.valueOf(i + 1);
            TextView c = new TextView(this);
            c.setText(id); c.setTextSize(12); c.setGravity(Gravity.CENTER);
            c.setPadding(dp(11), 0, dp(11), 0);
            c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { open(id); } });
            chipViews[i] = c;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(22));
            lp.rightMargin = dp(4);
            chips.addView(c, lp);
        }
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        chipScroll.addView(chips, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        chipScroll.setVisibility(View.INVISIBLE);   /* đã bấm vào trang thì không hiện hàng số nữa */
        bar.addView(chipScroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        icBack = Ic.make(this, Ic.BACK, new View.OnClickListener() { public void onClick(View v) { if (session != null && backOk) session.goBack(); } });
        icFwd = Ic.make(this, Ic.FWD, new View.OnClickListener() { public void onClick(View v) { if (session != null && fwdOk) session.goForward(); } });
        bar.addView(icBack); bar.addView(icFwd);
        bar.addView(Ic.make(this, Ic.RELOAD, new View.OnClickListener() { public void onClick(View v) { if (session != null) session.reload(); } }));
        for (int i = 0; i < bar.getChildCount(); i++) { View v = bar.getChildAt(i); if (v instanceof Ic) v.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(30))); }
        bar.setVisibility(View.GONE);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));

        FrameLayout content = new FrameLayout(this);
        content.setBackgroundColor(Color.WHITE);
        view = new GeckoView(this);
        view.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW);
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setVisibility(View.GONE);
        GeckoCore.attachZoomKeys(this, view);
        content.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL); home.setGravity(Gravity.CENTER_HORIZONTAL);
        home.setPadding(dp(24), dp(24), dp(24), dp(24));
        TextView title = new TextView(this);
        title.setText("Lập trình"); title.setTextSize(24); title.setTypeface(Typeface.DEFAULT_BOLD); title.setTextColor(0xFF1C1C1E);
        title.setGravity(Gravity.CENTER); title.setPadding(0, 0, 0, dp(16));
        home.addView(title);
        for (int i = 0; i < N; i++) {
            final String id = String.valueOf(i + 1);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(10), dp(16), dp(10));
            GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(14)); bg.setStroke(dp(3), Color.parseColor(COLORS[i]));
            row.setBackground(bg);
            TextView nm = new TextView(this); nm.setText("Trang " + id); nm.setTextSize(18); nm.setTypeface(Typeface.DEFAULT_BOLD); nm.setTextColor(0xFF1C1C1E);
            row.addView(nm, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(pill("Mở", new View.OnClickListener() { public void onClick(View v) { open(id); } }));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(420), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(12);
            home.addView(row, lp);
        }
        ScrollView homeScroll = new ScrollView(this);
        homeScroll.setBackgroundColor(0xFFF2F2F7);
        homeScroll.setFillViewport(true);
        homeScroll.addView(home, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        homeView = homeScroll;
        content.addView(homeScroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        markChips();
    }

    private TextView pill(String s, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(14); t.setTypeface(Typeface.DEFAULT_BOLD); t.setTextColor(0xFF0A60D8); t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(8), dp(16), dp(8));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(0xFFE6EFFC); bg.setCornerRadius(dp(16));
        t.setBackground(bg);
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private void markChips() {
        for (int i = 0; i < N; i++) {
            boolean on = false;   /* không tô sáng trang đang mở: tất cả số hiển thị giống nhau */
            GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(dp(16)); bg.setColor(on ? 0xFF0A60D8 : 0xFFFFFFFF);
            chipViews[i].setBackground(bg);
            chipViews[i].setTextColor(on ? Color.WHITE : 0xFF3A3A3C);
        }
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    /* ---------------- Gecko ---------------- */
    private GeckoSession newSession(String p) {
        GeckoSessionSettings st = new GeckoSessionSettings.Builder()
            .contextId("omem-" + p)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP)
            .build();
        final GeckoSession se = new GeckoSession(st);
        se.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override public void onExternalResponse(GeckoSession s, WebResponse response) { Downloads.start(OmemActivity.this, response); toast("Đang tải xuống…"); }
            @Override public void onCrash(GeckoSession s) { dropSession(); toast("Lập trình bị văng, hãy mở lại."); showHome(); }
            @Override public void onKill(GeckoSession s) { dropSession(); toast("Lập trình bị tắt do thiếu RAM, hãy mở lại."); showHome(); }
        });
        se.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onPageStart(GeckoSession s, String u) { if (u != null) url = u; }
        });
        se.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override public void onCanGoBack(GeckoSession s, boolean can) { backOk = can; icBack.setEnabled(can); }
            @Override public void onCanGoForward(GeckoSession s, boolean can) { fwdOk = can; icFwd.setEnabled(can); }
            @Override public GeckoResult<GeckoSession> onNewSession(GeckoSession s, String uri) { se.loadUri(uri); return null; }
        });
        se.setPromptDelegate(new GeckoSession.PromptDelegate() {
            @Override public GeckoResult<PromptResponse> onFilePrompt(GeckoSession s, FilePrompt prompt) {
                if (fileRes != null) fileRes.complete(prompt.dismiss());
                filePrompt = prompt;
                fileRes = new GeckoResult<PromptResponse>();
                Intent it = new Intent(Intent.ACTION_GET_CONTENT);
                it.addCategory(Intent.CATEGORY_OPENABLE); it.setType("*/*");
                it.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try { startActivityForResult(Intent.createChooser(it, "Chọn tệp"), REQ_FILE); }
                catch (Exception e) { fileRes.complete(prompt.dismiss()); }
                return fileRes;
            }
        });
        se.open(GeckoCore.rt(this));
        return se;
    }

    private GeckoSession.PromptDelegate.PromptResponse confirm(GeckoSession.PromptDelegate.FilePrompt prompt, Uri[] uris) {
        try {
            if (uris.length == 1) return prompt.confirm(this, uris[0]);
            return prompt.confirm(this, uris);
        } catch (Throwable t) {
            try { return prompt.confirm(this, uris[0]); } catch (Throwable t2) { return prompt.dismiss(); }
        }
    }

    private void dropSession() {
        GeckoSession old = session;
        session = null; backOk = false; fwdOk = false;
        try { view.releaseSession(); } catch (Throwable t) { }
        if (old != null) { try { old.close(); } catch (Throwable t) { } }
    }

    private void showHome() {
        view.setVisibility(View.GONE); bar.setVisibility(View.GONE); homeView.setVisibility(View.VISIBLE);
        profile = ""; markChips();
    }

    /** Mở / chuyển trang (mỗi trang một kho cookie riêng, nên mỗi trang đăng nhập một tài khoản riêng). */
    private void open(String p) {
        if (!valid(p)) return;
        if (session != null && p.equals(profile)) return;
        dropSession();
        profile = p;
        session = newSession(p);
        view.setSession(session);
        url = startUrl(p);
        session.loadUri(url);
        homeView.setVisibility(View.GONE); bar.setVisibility(View.VISIBLE); view.setVisibility(View.VISIBLE);
        icBack.setEnabled(false); icFwd.setEnabled(false);
        markChips();
    }

    private static Uri[] uris(Intent d) {
        if (d == null) return null;
        if (d.getClipData() != null && d.getClipData().getItemCount() > 0) {
            Uri[] u = new Uri[d.getClipData().getItemCount()];
            for (int i = 0; i < u.length; i++) u[i] = d.getClipData().getItemAt(i).getUri();
            return u;
        }
        return d.getData() != null ? new Uri[]{d.getData()} : null;
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        Uri[] u = res == RESULT_OK ? uris(data) : null;
        if (req == REQ_FILE) {
            GeckoResult<GeckoSession.PromptDelegate.PromptResponse> fr = fileRes;
            GeckoSession.PromptDelegate.FilePrompt fp = filePrompt;
            fileRes = null; filePrompt = null;
            if (fr == null || fp == null) return;
            if (u != null && u.length > 0) fr.complete(confirm(fp, u)); else fr.complete(fp.dismiss());
        }
    }

    @Override public void onBackPressed() {
        if (session != null && backOk) { session.goBack(); return; }
        if (session != null) { dropSession(); showHome(); return; }   /* hết lịch sử: về danh sách để chọn trang khác */
        finish();
    }

    @Override protected void onStart() { super.onStart(); visible = true; if (session == null && restoreP.length() > 0) { String rp = restoreP, ru = restoreUrl; restoreP = ""; restoreUrl = ""; open(rp); if (session != null && ru.length() > 0) { url = ru; session.loadUri(ru); } } if (session != null) { try { session.setActive(true); } catch (Throwable e) { } } }
    @Override protected void onStop() { super.onStop(); visible = false; if (session != null && !Downloads.anyRunning()) { try { session.setActive(false); } catch (Throwable e) { } } }
    /** Thiếu RAM trong lúc Lập trình nằm nền (và không có tệp đang tải): đóng phiên Gecko, mở lại thì nạp lại đúng trang (đăng nhập vẫn còn vì mỗi trang có kho cookie riêng). */
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (!visible && session != null && valid(profile) && level >= 40 && !Downloads.anyRunning()) {
            restoreP = profile; restoreUrl = url == null ? "" : url;
            dropSession(); profile = "";
        }
    }
    @Override protected void onDestroy() { CloseRx.unreg(this, closeRx); dropSession(); super.onDestroy(); GeckoCore.gone(this); }
}
