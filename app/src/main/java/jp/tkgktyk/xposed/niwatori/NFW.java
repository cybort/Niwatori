package jp.tkgktyk.xposed.niwatori;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.base.Strings;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

import jp.tkgktyk.flyinglayout.FlyingLayout;

/**
 * Created by tkgktyk on 2015/02/12.
 * Niwatori - Fly the Window
 */
public class NFW {
    public static final String PACKAGE_NAME = NFW.class.getPackage().getName();
    public static final String NAME = NFW.class.getSimpleName();

    public static final String ACTION_NONE = "";
    public static final String ACTION_DEFAULT = "";
    public static final String ACTION_TOGGLE = PACKAGE_NAME + ".intent.action.TOGGLE";
    public static final String ACTION_PIN = PACKAGE_NAME + ".intent.action.PIN";
    public static final String ACTION_PIN_OR_RESET = PACKAGE_NAME + ".intent.action.PIN_OR_RESET";
    public static final String ACTION_SMALL_SCREEN_LEFT = PACKAGE_NAME + ".intent.action.SMALL_SCREEN_LEFT";
    public static final String ACTION_SMALL_SCREEN_RIGHT = PACKAGE_NAME + ".intent.action.SMALL_SCREEN_RIGHT";
    public static final String ACTION_RESET = PACKAGE_NAME + ".intent.action.RESET";
    public static final String ACTION_SOFT_RESET = PACKAGE_NAME + ".intent.action.SOFT_RESET";

    public static final String ACTION_SETTINGS_CHANGED = PACKAGE_NAME + ".intent.action.SETTINGS_CHANGED";
    public static final String EXTRA_SETTINGS = PACKAGE_NAME + ".intent.extra.SETTINGS";

    /**
     * Static IntentFilters
     */
    public static final IntentFilter STATUS_BAR_FILTER;
    public static final IntentFilter FOCUSED_DIALOG_FILTER;
    public static final IntentFilter FOCUSED_ACTIVITY_FILTER;
    public static final IntentFilter ACTIVITY_FILTER;
    public static final IntentFilter SETTINGS_CHANGED_FILTER = new IntentFilter(ACTION_SETTINGS_CHANGED);
    /**
     * Receivers are set priority.
     * 1. Status bar
     * 2. Focused Dialog
     * 3. Focused Activity
     * 4. Activity
     * *. Unfoused Dialog <- unregistered
     */
    private static final int PRIORITY_STATUS_BAR = IntentFilter.SYSTEM_HIGH_PRIORITY;
    private static final int PRIORITY_FOCUSED_DIALOG = IntentFilter.SYSTEM_HIGH_PRIORITY / 10;
    private static final int PRIORITY_FOCUSED_ACTIVITY = IntentFilter.SYSTEM_HIGH_PRIORITY / 100;
    private static final int PRIORITY_ACTIVITY = IntentFilter.SYSTEM_HIGH_PRIORITY / 1000;

    /**
     * IntentFilters initialization
     */
    static {
        STATUS_BAR_FILTER = new IntentFilter();
        STATUS_BAR_FILTER.addAction(NFW.ACTION_TOGGLE);
        STATUS_BAR_FILTER.addAction(NFW.ACTION_PIN);
        STATUS_BAR_FILTER.addAction(NFW.ACTION_PIN_OR_RESET);
        STATUS_BAR_FILTER.addAction(NFW.ACTION_SMALL_SCREEN_LEFT);
        STATUS_BAR_FILTER.addAction(NFW.ACTION_SMALL_SCREEN_RIGHT);
        STATUS_BAR_FILTER.addAction(NFW.ACTION_RESET);
        STATUS_BAR_FILTER.addAction(NFW.ACTION_SOFT_RESET);
        FOCUSED_DIALOG_FILTER = new IntentFilter(STATUS_BAR_FILTER);
        FOCUSED_ACTIVITY_FILTER = new IntentFilter(STATUS_BAR_FILTER);
        ACTIVITY_FILTER = new IntentFilter(STATUS_BAR_FILTER);
        // Priority
        STATUS_BAR_FILTER.setPriority(NFW.PRIORITY_STATUS_BAR);
        FOCUSED_DIALOG_FILTER.setPriority(NFW.PRIORITY_FOCUSED_DIALOG);
        FOCUSED_ACTIVITY_FILTER.setPriority(NFW.PRIORITY_FOCUSED_ACTIVITY);
        ACTIVITY_FILTER.setPriority(NFW.PRIORITY_ACTIVITY);
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("WorldReadableFiles")
    public static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(PACKAGE_NAME + "_preferences", Context.MODE_WORLD_READABLE);
    }

    public static void performAction(@NonNull Context context, @Nullable String action) {
        if (!Strings.isNullOrEmpty(action)) {
            context.sendOrderedBroadcast(new Intent(action), null);
        }
    }

    public static boolean isDefaultAction(@Nullable String action) {
        return Strings.isNullOrEmpty(action);
    }

    public static Context getNiwatoriContext(Context context) {
        Context niwatoriContext = null;
        try {
            if (context.getPackageName().equals(NFW.PACKAGE_NAME)) {
                niwatoriContext = context;
            } else {
                niwatoriContext = context.createPackageContext(
                        NFW.PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY);
            }
        } catch (Throwable t) {
            XposedModule.logE(t);
        }
        return niwatoriContext;
    }

    public static GradientDrawable makeBoundaryDrawable(int width, int color) {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.TRANSPARENT);
        drawable.setStroke(width, color);
        return drawable;
    }

    public static class Settings implements Serializable {
        public Set<String> blackSet;
        public boolean animation;
        public boolean resetAutomatically;
        public String actionWhenTapOutside;
        public String actionWhenLongPressOutside;
        public String actionWhenDoubleTapOutside;

        public float speed;
        public boolean drawBoundary;
        public int boundaryColor;
        public int initialXp;
        public int initialYp;

        public float smallScreenSize;

        public boolean testFeature;
        public String actionWhenTapOnRecents;
        public String actionWhenLongPressOnRecents;
        public String actionWhenDoubleTapOnRecents;

        public Settings(SharedPreferences prefs) {
            load(prefs);
        }

        public void load(SharedPreferences prefs) {
            blackSet = prefs.getStringSet("key_black_list", Collections.<String>emptySet());
            animation = prefs.getBoolean("key_animation", true);
            resetAutomatically = prefs.getBoolean("key_reset_automatically", true);
            actionWhenTapOutside = prefs.getString("key_action_when_tap_outside", ACTION_SOFT_RESET);
            actionWhenLongPressOutside = prefs.getString("key_action_when_long_press_outside", ACTION_NONE);
            actionWhenDoubleTapOutside = prefs.getString("key_action_when_double_tap_outside", ACTION_PIN);

            speed = Float.parseFloat(prefs.getString("key_speed", Float.toString(FlyingLayout.DEFAULT_SPEED)));
            drawBoundary = prefs.getBoolean("key_draw_boundary", true);
            boundaryColor = Color.parseColor(prefs.getString("key_boundary_color", "#689F38")); // default is Green
            initialXp = prefs.getInt("key_initial_x_percent", InitialPosition.DEFAULT_X_PERCENT);
            initialYp = prefs.getInt("key_initial_y_percent", InitialPosition.DEFAULT_Y_PERCENT);

            smallScreenSize = Float.parseFloat(prefs.getString("key_small_screen_size", "70")) / 100f;

            testFeature = prefs.getBoolean("key_test_feature", false);
            if (testFeature) {
                actionWhenTapOnRecents = prefs.getString("key_action_when_tap_on_recents", ACTION_DEFAULT);
                actionWhenLongPressOnRecents = prefs.getString("key_action_when_long_press_on_recents", ACTION_DEFAULT);
                actionWhenDoubleTapOnRecents = prefs.getString("key_action_when_double_tap_on_recents", ACTION_DEFAULT);
            } else {
                actionWhenTapOnRecents = ACTION_DEFAULT;
                actionWhenLongPressOnRecents = ACTION_DEFAULT;
                actionWhenDoubleTapOnRecents = ACTION_DEFAULT;
            }
        }
    }
}
