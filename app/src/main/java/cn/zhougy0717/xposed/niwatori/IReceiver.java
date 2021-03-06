package cn.zhougy0717.xposed.niwatori;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.widget.FrameLayout;

/**
 * Created by zhougua on 1/12/2018.
 */

public interface IReceiver {
    public BroadcastReceiver create ();
    public void register();
    public void unregister();
    public void setFilter(IntentFilter filter);
}
