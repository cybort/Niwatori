package jp.tkgktyk.xposed.niwatori;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.FrameLayout;

import de.robv.android.xposed.XposedHelpers;

/**
 * Created by zhougua on 1/12/2018.
 */

public class ActionReceiver extends Receiver implements IReceiver{
    private static final String FIELD_ACTION_RECEIVER = NFW.NAME + "_ActionReceiver";
    private boolean mRegistered = false;

    public static IReceiver getInstance(FrameLayout decorView, IntentFilter filter){
        IReceiver recv = (IReceiver) XposedHelpers.getAdditionalInstanceField(decorView, "ACTION_RECEIVER");
        if (recv == null) {
            recv = new ActionReceiver(decorView, filter);
            XposedHelpers.setAdditionalInstanceField(decorView, "ACTION_RECEIVER", recv);
        }
        return recv;
    }

    public void register(){
        if (!mRegistered) {
            mDecorView.getContext().registerReceiver(mReceiver, mFilter);
            mRegistered = true;
        }
    }
    public void unregister(){
        if (mRegistered) {
            mDecorView.getContext().unregisterReceiver(mReceiver);
//            getHelper(mDecorView).resetState(true);
            ModActivity.getHelper(mDecorView).resetState(true);
            mRegistered = false;
        }

    }

    private ActionReceiver(FrameLayout decorView, IntentFilter filter){
        super(decorView, filter);
        create();
    }
//    public ActionReceiver (FrameLayout decorView) {
//        super(decorView);
//        create();
//    }

    private BroadcastReceiver mReceiver;
    public final BroadcastReceiver create() {
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                try {
                    handler(action);
                    abortBroadcast();
                } catch (Throwable t) {
                    logE(t);
                }
            }
        };
        return mReceiver;
    }

    private void handler (String action){
//        FlyingHelper helper = getHelper(mDecorView);
        FlyingHelper helper = ModActivity.getHelper(mDecorView);
        final String packageName = mDecorView.getContext().getPackageName();
        if (helper.getSettings().blackList.contains(packageName)) {
            if (helper.getSettings().logActions) {
                log(mDecorView.toString() + "is ignored");
            }
            return;
        }
        helper.performAction(action);
        if (helper.getSettings().logActions) {
            log(packageName + " consumed: " + action);
        }
    }
}
