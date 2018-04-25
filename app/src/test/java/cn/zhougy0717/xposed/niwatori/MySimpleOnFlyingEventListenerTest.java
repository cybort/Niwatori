package cn.zhougy0717.xposed.niwatori;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.FrameLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.powermock.reflect.Whitebox;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import de.robv.android.xposed.XposedBridge;
import jp.tkgktyk.flyinglayout.FlyingLayout;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(
        constants = BuildConfig.class,
        sdk = 26
)
@PowerMockIgnore({ "org.mockito.*", "org.robolectric.*", "android.*" })
@PrepareForTest({WorldReadablePreference.class})
public class MySimpleOnFlyingEventListenerTest {
    @Rule
    public PowerMockRule rule = new PowerMockRule();

    private SharedPreferences globalPrefs;
    private SharedPreferences localPrefs;

    @Before
    public void setUp() throws Exception {
        globalPrefs = PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.application);
    }
    @After
    public void tearDown(){
        globalPrefs.edit().clear().apply();
        if (localPrefs != null){
            localPrefs.edit().clear().apply();
        }
    }
    @Test
    public void it_should_call_onSettingsLoad_performAction_in_order() throws Exception {
        FlyingHelper helper = mock(FlyingHelper.class);
        when(helper.getSettings()).thenReturn(mock(Settings.class));
        FlyingLayout.OnFlyingEventListener listener = new MySimpleOnFlyingEventListener(helper);

        FrameLayout v = new FrameLayout(RuntimeEnvironment.application);
        listener.onScrollLeft(v);

        InOrder inOrder = Mockito.inOrder(helper);
        inOrder.verify(helper).onSettingsLoaded();
        inOrder.verify(helper).moveWithoutSpeed(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    public void it_should_load_local_prefs_for_smallScreenSize_while_onScroll() throws Exception {
        FlyingHelper helper = mock(FlyingHelper.class);
        when(helper.getSettings()).thenReturn(mock(Settings.class));
        localPrefs = RuntimeEnvironment.application.getSharedPreferences(FlyingHelper.TEMP_SCREEN_INFO_PREF_FILENAME, Context.MODE_PRIVATE);
        localPrefs.edit().putInt("key_small_screen_size", 45).apply();
        FrameLayout v = new FrameLayout(RuntimeEnvironment.application);
        FlyingLayout.OnFlyingEventListener listener = new MySimpleOnFlyingEventListener(helper);
        listener.onScrollLeft(v);
        assertEquals(45-FlyingHelper.SMALL_SCREEN_SIZE_DELTA, localPrefs.getInt("key_small_screen_size",0));
    }

    @Test
    public void it_should_load_global_prefs_if_no_local_one() throws Exception {
        globalPrefs.edit().putInt("key_small_screen_size", 42).apply();

        FlyingHelper helper = mock(FlyingHelper.class);
        when(helper.getSettings()).thenReturn(new Settings(globalPrefs));

        localPrefs = RuntimeEnvironment.application.getSharedPreferences(FlyingHelper.TEMP_SCREEN_INFO_PREF_FILENAME, Context.MODE_PRIVATE);
        FrameLayout v = new FrameLayout(RuntimeEnvironment.application);

        FlyingLayout.OnFlyingEventListener listener = new MySimpleOnFlyingEventListener(helper);
        listener.onScrollLeft(v);
        assertEquals(42-FlyingHelper.SMALL_SCREEN_SIZE_DELTA, localPrefs.getInt("key_small_screen_size", 0));
    }

    @Test
    public void it_should_save_smallScreenSize_to_local_SharedPreference() throws Exception {
        localPrefs = RuntimeEnvironment.application.getSharedPreferences(FlyingHelper.TEMP_SCREEN_INFO_PREF_FILENAME, Context.MODE_PRIVATE);
        localPrefs.edit().putInt("key_small_screen_size", 45).apply();

        FlyingHelper helper = mock(FlyingHelper.class);
        when(helper.getSettings()).thenReturn(new Settings(globalPrefs));
        FlyingLayout.OnFlyingEventListener listener = new MySimpleOnFlyingEventListener(helper);
        FrameLayout v = new FrameLayout(RuntimeEnvironment.application);
        listener.onScrollLeft(v);
        assertEquals(45 - FlyingHelper.SMALL_SCREEN_SIZE_DELTA, localPrefs.getInt("key_small_screen_size", 0));
    }


    @Test
    public void it_should_not_decrease_size_lower_than_limit() throws Exception {
        localPrefs = RuntimeEnvironment.application.getSharedPreferences(FlyingHelper.TEMP_SCREEN_INFO_PREF_FILENAME, Context.MODE_PRIVATE);
        localPrefs.edit().putInt("key_small_screen_size", FlyingHelper.SMALLEST_SMALL_SCREEN_SIZE).apply();

        PowerMockito.mockStatic(NFW.class);
        PowerMockito.when(NFW.class, "getNiwatoriContext", any(Context.class)).thenReturn(RuntimeEnvironment.application);

        FlyingHelper helper = mock(FlyingHelper.class);
        when(helper.getSettings()).thenReturn(new Settings(globalPrefs));
        FlyingLayout.OnFlyingEventListener listener = new MySimpleOnFlyingEventListener(helper);
        FrameLayout v = new FrameLayout(RuntimeEnvironment.application);
        listener.onScrollLeft(v);
        assertEquals(FlyingHelper.SMALLEST_SMALL_SCREEN_SIZE, localPrefs.getInt("key_small_screen_size", 0));
    }
}