package cn.zhougy0717.xposed.niwatori;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.os.Build;
import android.support.annotation.NonNull;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static android.content.Context.KEYGUARD_SERVICE;

/**
 * Created by tkgktyk on 2015/02/13.
 */
public abstract class ModPhoneStatusBar extends XposedModule {
    private static final String CLASS_PHONE_STATUS_BAR =
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)? "com.android.systemui.statusbar.phone.StatusBar":
                    "com.android.systemui.statusbar.phone.PhoneStatusBar";
    private static final String CLASS_PHONE_STATUS_BAR_VIEW = "com.android.systemui.statusbar.phone.PhoneStatusBarView";

    protected static final String FIELD_FLYING_HELPER = NFW.NAME + "_flyingHelper";

    // for status bar
    protected static FlyingHelper mHelper;

    protected static Object mPhoneStatusBar;
    private static View mPhoneStatusBarView;

    abstract protected String getPanelHolderName();
    abstract protected String getPanelCollapsedName();

    abstract protected void hookPanelHolderOnTouch(ClassLoader classLoader);
    abstract protected void hookPanelConstructor(ClassLoader classLoader);
//    abstract protected void hookPanelHolderDraw(ClassLoader classLoader);

    protected void expandNotificationBar(){
        XposedHelpers.callMethod(mPhoneStatusBar, "animateExpandNotificationsPanel");
    }
    protected void expandQuickSettings(){
        XposedHelpers.callMethod(mPhoneStatusBar, "animateExpandSettingsPanel");
    }

    protected GestureDetector createShadowGesture(Context context) {
        return new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                mHelper.performAction(NFW.ACTION_SMALL_SCREEN);
//                mHelper.performAction(NFW.ACTION_MOVABLE_SCREEN);
                return true;
            }
        });
    }
    protected final BroadcastReceiver mGlobalReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                final String action = intent.getAction();
                logD("global broadcast receiver: " + action);
                final int mState = XposedHelpers.getIntField(mPhoneStatusBarView, "mState");
                if (action.startsWith(NFW.PREFIX_ACTION_SB)) {
                    consumeMyAction(action);
                    return;
                }
                if (mState == 0) { // STATE_CLOSED = 0
                    return;
                }
                // target is status bar
                mHelper.performAction(action);
                abortBroadcast();
                if (mHelper.getSettings().logActions) {
                    log("statusbar consumed: " + action);
                }
            } catch (Throwable t) {
                logE(t);
            }
        }

        @SuppressWarnings("ResourceType")
        private void consumeMyAction(String action) {
            if (action.equals(NFW.ACTION_SB_EXPAND_NOTIFICATIONS)) {
                expandNotificationBar();
                mHelper.performExtraAction();
            } else if (action.equals(NFW.ACTION_SB_EXPAND_QUICK_SETTINGS)) {
                expandQuickSettings();
                mHelper.performExtraAction();
            }
            if (mHelper.getSettings().logActions) {
                log("statusbar consumed: " + action);
            }
        }
    };

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (!loadPackageParam.packageName.equals("com.android.systemui")) {
            return;
        }
        try {
            installToStatusBar(loadPackageParam.classLoader);
            //
            // for Software Keys
            //
            Settings settings = WorldReadablePreference.getSettings();
            if (settings.extraActionOnRecents != NFW.NONE_ON_RECENTS) {
                final ClassLoader classLoader = loadPackageParam.classLoader;
                modifySoftwareKey(classLoader);
                log("prepared to modify software recents key");
            }
        } catch (Throwable t) {
            logE(t);
        }
    }

    private void handleNotificationGesture(ClassLoader classLoader){
        final Class<?> classPanelView = XposedHelpers.findClass("com.android.systemui.statusbar.phone.NotificationPanelView", classLoader);
        final Class<?> classStatusBar = XposedHelpers.findClass(CLASS_PHONE_STATUS_BAR, classLoader);
        XposedBridge.hookAllMethods(classStatusBar, "onScreenTurnedOff", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mHelper.resetState(true);
                mHelper.onExit();
            }
        });
        XposedBridge.hookAllConstructors(classPanelView, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View v = (View) param.thisObject;
                final GestureDetector gestureDetector = createShadowGesture(v.getContext());
                XposedHelpers.setAdditionalInstanceField(v, "CUSTOM_GESTURE_DETECTOR", gestureDetector);
                XposedHelpers.setAdditionalInstanceField(v, "IGNORE_NEXT_UP", false);
            }
        });
        XposedBridge.hookAllMethods(classPanelView, "onTouchEvent", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                View v = (View) param.thisObject;
                MotionEvent e = (MotionEvent) param.args[0];
                GestureDetector gestureDetector = (GestureDetector) XposedHelpers.getAdditionalInstanceField(v, "CUSTOM_GESTURE_DETECTOR");
                boolean result = false;
                KeyguardManager mKeyguardManager = (KeyguardManager) v.getContext().getSystemService(KEYGUARD_SERVICE);
                if (mKeyguardManager.inKeyguardRestrictedInputMode()) {
                    return invokeOriginalMethod(param);
                }

                if (!mHelper.isResized() && mHelper.staysHome()) {
                    if (gestureDetector.onTouchEvent(e)) {
                        /**
                         *  Why do we need to ignore next ACTION_UP?
                         *  Double tap takes effect in the next ACTION_DOWN event.
                         *  If we don't ignore the coming ACTION_UP, systemui will respond to it and collapse the notification panel.
                         *  That's not what I want.
                         */
                        XposedHelpers.setAdditionalInstanceField(v, "IGNORE_NEXT_UP", true);
                        return true;
                    }
                }

                boolean ignoreUp = (boolean)XposedHelpers.getAdditionalInstanceField(v,"IGNORE_NEXT_UP");
                if(ignoreUp && e.getAction()==MotionEvent.ACTION_UP){
                    XposedHelpers.setAdditionalInstanceField(v, "IGNORE_NEXT_UP", false);
                    return true;
                }
                return invokeOriginalMethod(param);
            }
        });
    }

    protected void hookPanelHolderDraw(ClassLoader classLoader){
        // This is for version before Marshmallow(6.0)
        XposedHelpers.findAndHookMethod(FrameLayout.class, "draw", Canvas.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            final Canvas canvas = (Canvas) param.args[0];
                            final FlyingHelper helper = getHelper(param.thisObject);
                            if (helper != null) {
                                mHelper.draw(canvas);
                            }
                        } catch (Throwable t) {
                            logE(t);
                        }
                    }
                });
    }
    private void installToStatusBar(ClassLoader classLoader) {
        hookPanelConstructor(classLoader);
        hookPanelHolderOnTouch(classLoader);

        handleNotificationGesture(classLoader);
        hookPanelHolderDraw(classLoader);


        XposedHelpers.findAndHookMethod(FrameLayout.class, "onLayout", boolean.class,
                int.class, int.class, int.class, int.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        try {
                            final FlyingHelper helper = getHelper(methodHookParam.thisObject);
                            if (helper != null) {
                                final boolean changed = (Boolean) methodHookParam.args[0];
                                final int left = (Integer) methodHookParam.args[1];
                                final int top = (Integer) methodHookParam.args[2];
                                final int right = (Integer) methodHookParam.args[3];
                                final int bottom = (Integer) methodHookParam.args[4];
                                helper.onLayout(changed, left, top, right, bottom);
                                return null;
                            }
                        } catch (Throwable t) {
                            logE(t);
                        }
                        return invokeOriginalMethod(methodHookParam);
                    }
                });
        XposedHelpers.findAndHookMethod(ViewGroup.class, "onInterceptTouchEvent", MotionEvent.class,
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        try {
                            final MotionEvent event = (MotionEvent) methodHookParam.args[0];
                            final FlyingHelper helper = getHelper(methodHookParam.thisObject);
                            if (helper != null && helper.onInterceptTouchEvent(event)) {
                                return true;
                            }
                        } catch (Throwable t) {
                            logE(t);
                        }
                        return invokeOriginalMethod(methodHookParam);
                    }
                });
        final Class<?> classPhoneStatusBar = XposedHelpers.findClass(
                CLASS_PHONE_STATUS_BAR, classLoader);
        XposedBridge.hookAllConstructors(classPhoneStatusBar, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mPhoneStatusBar = param.thisObject;
            }
        });
        final Class<?> classPhoneStatusBarView = XposedHelpers.findClass(
                CLASS_PHONE_STATUS_BAR_VIEW, classLoader);
        XposedBridge.hookAllConstructors(classPhoneStatusBarView, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mPhoneStatusBarView = (View) param.thisObject;
            }
        });
        //
        // Reset state when status bar collapsed
        //
        try {
            XposedHelpers.findAndHookMethod(classPhoneStatusBarView, getPanelCollapsedName(),
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
//                                if (mHelper.getSettings().autoReset) {
                                mHelper.resetState(true);
                                mHelper.onExit();
//                                }
                            } catch (Throwable t) {
                                logE(t);
                            }
                        }
                    });
        } catch (NoSuchMethodError e) {
            log("PhoneStatusBarView#onAllPanelsCollapsed is not found.");
        }
    }

    private static FlyingHelper getHelper(@NonNull Object obj) {
        FlyingHelper helper = (FlyingHelper) XposedHelpers.getAdditionalInstanceField(obj, FIELD_FLYING_HELPER);
        return helper;
    }

    private static void modifySoftwareKey(ClassLoader classLoader) {
        final Class<?> classPhoneStatusBar = XposedHelpers.findClass(CLASS_PHONE_STATUS_BAR, classLoader);
        XposedBridge.hookAllMethods(classPhoneStatusBar, "prepareNavigationBarView",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        logD("prepareNavigationBarView");
                        try {
                            Object phoneStatusBar = param.thisObject;
                            final View navigationBarView = (View) XposedHelpers.getObjectField(
                                    phoneStatusBar, "mNavigationBarView");
                            modifyRecentsKey(phoneStatusBar, navigationBarView);
                        } catch (Throwable t) {
                            logE(t);
                        }
                    }

                    private void modifyRecentsKey(final Object phoneStatusBar, View navigationBarView) {
                        final View recentsButton = (View) XposedHelpers.callMethod(
                                navigationBarView, "getRecentsButton");
                        final View.OnClickListener clickListener
                                = (View.OnClickListener) XposedHelpers.getObjectField(
                                phoneStatusBar, "mRecentsClickListener");
                        final View.OnTouchListener touchListener
                                = (View.OnTouchListener) XposedHelpers.getObjectField(
                                phoneStatusBar, "mRecentsPreloadOnTouchListener");
                        View.OnLongClickListener localLCL = null;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            localLCL = (View.OnLongClickListener) XposedHelpers.getObjectField(
                                    phoneStatusBar, "mLongPressBackRecentsListener");
                        }
                        final View.OnLongClickListener longClickListener = localLCL;
                        recentsButton.setLongClickable(false);
                        recentsButton.setOnLongClickListener(null);
                        recentsButton.setOnClickListener(null);
                        final GestureDetector gestureDetector = new GestureDetector(
                                navigationBarView.getContext(), new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onSingleTapConfirmed(MotionEvent e) {
                                try {
                                    Settings settings = mHelper.getSettings();
                                    if (settings.extraActionOnRecents != NFW.TAP_ON_RECENTS) {
                                        clickListener.onClick(recentsButton);
                                    } else {
                                        NFW.performAction(recentsButton.getContext(),
                                                settings.extraAction);
                                    }
                                } catch (Throwable t) {
                                    logE(t);
                                }
                                return true;
                            }

                            @Override
                            public void onLongPress(MotionEvent e) {
                                try {
                                    Settings settings = mHelper.getSettings();
                                    if (settings.extraActionOnRecents != NFW.LONG_PRESS_ON_RECENTS) {
                                        if (longClickListener != null) {
                                            longClickListener.onLongClick(recentsButton);
                                        } else {
                                            clickListener.onClick(recentsButton);
                                        }
                                    } else {
                                        NFW.performAction(recentsButton.getContext(),
                                                settings.extraAction);
                                    }
                                    recentsButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                                } catch (Throwable t) {
                                    logE(t);
                                }
                            }

                            @Override
                            public boolean onDoubleTap(MotionEvent e) {
                                try {
                                    Settings settings = mHelper.getSettings();
                                    if (settings.extraActionOnRecents != NFW.DOUBLE_TAP_ON_RECENTS) {
                                        if (settings.extraActionOnRecents == NFW.TAP_ON_RECENTS) {
                                            clickListener.onClick(recentsButton);
                                        } else {
                                            return false;
                                        }
                                    } else {
                                        NFW.performAction(recentsButton.getContext(),
                                                settings.extraAction);
                                    }
                                    recentsButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                                } catch (Throwable t) {
                                    logE(t);
                                }
                                return true;
                            }
                        });
                        recentsButton.setOnTouchListener(new View.OnTouchListener() {
                            @Override
                            public boolean onTouch(View v, MotionEvent event) {
                                try {
                                    // original touchListener always return false.
                                    touchListener.onTouch(v, event);
                                } catch (Throwable t) {
                                    logE(t);
                                }
                                return gestureDetector.onTouchEvent(event);
                            }
                        });
                    }
                });
    }
}
