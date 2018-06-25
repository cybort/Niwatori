package cn.zhougy0717.xposed.niwatori;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Created by zhougua on 1/8/2018.
 */

public class ModPhoneStatusBar_N extends ModPhoneStatusBar {
    protected String getPanelHolderName(){
        return "com.android.systemui.statusbar.phone.NotificationsQuickSettingsContainer";
    }

    @Override
    protected void expandQuickSettings(){
        Object mNotifPanel = XposedHelpers.getObjectField(mPhoneStatusBar, "mNotificationPanel");
        try {
            XposedHelpers.callMethod(mNotifPanel, "instantExpand");
            if (XposedHelpers.getBooleanField(mNotifPanel, "mQsExpansionEnabled")) {
                XposedHelpers.callMethod(mNotifPanel, "setQsExpansion",
                        XposedHelpers.getIntField(mNotifPanel, "mQsMaxExpansionHeight"));
            }
        }
        catch (Throwable t) {
            XposedHelpers.callMethod(mNotifPanel, "expandWithQs");
        }
    }

    @Override
    protected void expandNotificationBar(){
        Object mNotifPanel = XposedHelpers.getObjectField(mPhoneStatusBar, "mNotificationPanel");
        try {
            XposedHelpers.callMethod(mNotifPanel, "instantExpand");
        } catch (Throwable t) {
            XposedHelpers.callMethod(mNotifPanel, "expand", true);
        }
    }
    protected void hookPanelHolderOnTouch(ClassLoader classLoader){
//        XposedHelpers.findAndHookMethod(View.class, "onTouchEvent", MotionEvent.class,
//            new XC_MethodReplacement() {
//                @Override
//                protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
//                    if (!methodHookParam.thisObject
//                            .getClass()
//                            .getName()
//                            .equals(getPanelHolderName())) {
//                        return invokeOriginalMethod(methodHookParam);
//                    }
//                    try {
//                        boolean handled = (boolean)invokeOriginalMethod(methodHookParam);
//                        Log.e("Ben", "NotificationQuickSettingsContainer onTouchEvent: " + handled);
//                        final MotionEvent event = (MotionEvent) methodHookParam.args[0];
//                        if (mHelper.onTouchEvent(event)) {
//                            return true;
//                        }
//                        return handled;
//                    } catch (Throwable t) {
//                        logE(t);
//                        return false;
//                    }
//                }
//            });
    }

    protected String getPanelCollapsedName() {
        return "onPanelCollapsed";
    }

    protected void hookPanelConstructor(ClassLoader classLoader) {
        final Class<?> classPanelHolder = XposedHelpers.findClass(getPanelHolderName(), classLoader);
        XposedBridge.hookAllConstructors(classPanelHolder, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                try {
                    final FrameLayout panelHolder = (FrameLayout) param.thisObject;
                    final GestureDetector gestureDetector = createShadowGesture(panelHolder.getContext());
                    // need to reload on each package?
                    mHelper = new FlyingHelper(panelHolder, 1, false);
                    XposedHelpers.setAdditionalInstanceField(panelHolder,
                            FIELD_FLYING_HELPER, mHelper);

                    /**
                     * TODO:
                     *  1. We can try using onPanelPeeked to register the receivers and unregister them in onPanelCollapsed
                     *  2. We can try merging the mGlobalReceiver with ActionReceiver class.
                     */
                    panelHolder.getContext().registerReceiver(mGlobalReceiver, NFW.STATUS_BAR_FILTER);
                    panelHolder.getContext().registerReceiver(new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            logD("reload settings");
                            // need to reload on each package?
                            Settings settings = (Settings) intent.getSerializableExtra(NFW.EXTRA_SETTINGS);
                            mHelper.onSettingsLoaded(settings);
                        }
                    }, NFW.SETTINGS_CHANGED_FILTER);
                    log("attached to status bar");
                } catch (Throwable t) {
                    logE(t);
                }
            }
        });
    }

    /**
     * REVISIT: It looks like not that useful.
     */
    @Override
    protected void hookPanelHolderDraw(ClassLoader classLoader){
        final Class<?> classPanelView = XposedHelpers.findClass("com.android.systemui.statusbar.phone.NotificationPanelView", classLoader);
        XposedHelpers.findAndHookMethod(classPanelView, "dispatchDraw", Canvas.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
//                            View v = (View) param.thisObject;
//                            if (v.getClass().getName().endsWith("NotificationPanelView")) {
                                final Canvas canvas = (Canvas) param.args[0];
                                mHelper.draw(canvas);
//                            }
                        } catch (Throwable t) {
                            logE(t);
                        }
                    }
                });
    }
}


