package cn.zhougy0717.xposed.niwatori;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.google.common.base.Strings;

import cn.zhougy0717.xposed.niwatori.handlers.ActivityHandler;
import cn.zhougy0717.xposed.niwatori.handlers.DialogHandler;
import cn.zhougy0717.xposed.niwatori.handlers.PopupWindowHandler;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Created by tkgktyk on 2015/02/12.
 */
public class ModActivity extends XposedModule {
    private static final String FIELD_FLYING_HELPER = NFW.NAME + "_flyingHelper";
    public static FlyingHelper createFlyingHelper(FrameLayout decorView) {
        try {
            FlyingHelper helper = (FlyingHelper) XposedHelpers.getAdditionalInstanceField(decorView, FIELD_FLYING_HELPER);
            if (helper == null) {
                helper = new FlyingHelper(decorView, 1, false);
                XposedHelpers.setAdditionalInstanceField(decorView, FIELD_FLYING_HELPER, helper);
            }

            final FlyingHelper h = helper;
            decorView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return h.onTouchEvent(event);
                }
            });
            decorView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    boolean changed = (left!=oldLeft) || (right!=oldRight) || (bottom!=oldBottom) || (top!=oldTop);
                    h.onLayout(changed, left, top, right, bottom);
                    while(!h.mLayoutCallbacks.isEmpty()) {
                        Runnable r = h.mLayoutCallbacks.poll();
                        r.run();
                    }
                }
            });
            return helper;
        } catch (Throwable t) {
            logE(t);
            return null;
        }
    }

    public static void initZygote() {
        try {
//            installToDecorView();
//            installToActivity();
            (new ActivityHandler()).install();
            (new DialogHandler()).install();
            (new PopupWindowHandler()).install();
            logD("prepared to attach to Activity and Dialog");
        } catch (Throwable t) {
            logE(t);
        }
    }

    private static void setBackground(View decorView) {
        decorView.setBackground(censorDrawable(decorView, decorView.getBackground()));
    }

    public static Drawable censorDrawable(View decorView, Drawable drawable) {
        if (drawable == null) {
            final TypedValue a = new TypedValue();
            if (decorView.getContext().getTheme().resolveAttribute(android.R.attr.windowBackground, a, true)) {
                if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT && a.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    // color
                    final int color = a.data;
                    logD("background color: " + String.format("#%08X", color));
                    logD("set opaque background color");
                    drawable = new ColorDrawable(color);
                } else {
                    try {
                        final Drawable d = decorView.getResources().getDrawable(a.resourceId);
                        logD("background drawable opacity: " + Integer.toString(d.getOpacity()));
                        logD("set opaque background drawable");
                        drawable = d;
                    }
                    catch (Throwable t) {
                        drawable = new ColorDrawable(Color.BLACK);
                    }
                }
            }
//        } else if (drawable.getOpacity() == PixelFormat.OPAQUE) {
//            logD("decorView has opaque background drawable");
//            decorView.setBackground(drawable);
        }
        return drawable;
    }

    public static FlyingHelper getHelper(@NonNull final FrameLayout decorView) {
        FlyingHelper helper = (FlyingHelper) XposedHelpers.getAdditionalInstanceField(
                decorView, FIELD_FLYING_HELPER);
        return helper;
    }
}
