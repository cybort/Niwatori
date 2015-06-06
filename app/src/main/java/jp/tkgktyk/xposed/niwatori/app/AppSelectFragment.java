package jp.tkgktyk.xposed.niwatori.app;

import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.tkgktyk.xposed.niwatori.BuildConfig;
import jp.tkgktyk.xposed.niwatori.NFW;
import jp.tkgktyk.xposed.niwatori.R;

/**
 * Created by tkgktyk on 2015/02/13.
 */
public class AppSelectFragment extends ListFragment implements
        LoaderManager.LoaderCallbacks<List<AppSelectFragment.Entry>> {
    private static final String TAG = AppSelectFragment.class.getSimpleName();

    private static final int MSG_ICON_CACHED = 1;

    private boolean mShowOnlySelected;
    private boolean mSave = false;
    private String mPrefKey;

    private IconCache mIconCache;

    private BroadcastReceiver mIconCachedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mHandler.removeMessages(MSG_ICON_CACHED);
            mHandler.sendEmptyMessage(MSG_ICON_CACHED);
        }
    };

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Adapter adapter = (Adapter) getListAdapter();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    };

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public AppSelectFragment() {
    }

    public void setPrefKey(String key) {
        mPrefKey = key;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (mIconCache == null) {
            mIconCache = new IconCache(view.getContext());
        }

        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Adapter adapter = (Adapter) getListAdapter();
        Entry entry = adapter.getItem(position);
        entry.selected = !entry.selected;
        adapter.notifyDataSetChanged();

        mSave = true;

        log(entry.packageName);
    }

    public void setShowOnlySelected(boolean only) {
        if (only != mShowOnlySelected) {
            saveSelectedList();
            mShowOnlySelected = only;
            getLoaderManager().initLoader(0, null, this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        mHandler.removeMessages(MSG_ICON_CACHED);
        mHandler.sendEmptyMessage(MSG_ICON_CACHED);

        IntentFilter filter = new IntentFilter();
        filter.addAction(IconCache.ACTION_ICON_CACHED);
        LocalBroadcastManager.getInstance(getActivity())
                .registerReceiver(mIconCachedReceiver, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mIconCachedReceiver);
        saveSelectedList();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getLoaderManager().destroyLoader(0);
        mIconCache.evict();
    }

    @Override
    public Loader<List<Entry>> onCreateLoader(int id, Bundle bundle) {
        setListShown(false);
        return new SelectedListLoader(getActivity());
    }

    @Override
    public void onLoadFinished(Loader<List<Entry>> loader, List<Entry> entries) {
        List<Entry> deliverer = new ArrayList<>();

        Set<String> selectedSet = NFW.getSharedPreferences(getActivity())
                .getStringSet(mPrefKey, Collections.<String>emptySet());
        for (Entry entry : entries) {
            entry.selected = selectedSet.contains(entry.packageName);
            if (!mShowOnlySelected || entry.selected) {
                deliverer.add(entry);
            }
        }
        setListAdapter(new Adapter(getActivity(), deliverer));
        setListShown(true);
    }

    @Override
    public void onLoaderReset(Loader<List<Entry>> loader) {
        // TODO do nothing?
    }

    public void saveSelectedList() {
        if (!mSave)
            return;
        Set<String> selectedSet = new HashSet<>();
        Adapter adapter = (Adapter) getListAdapter();
        for (int i = 0; i < adapter.getCount(); ++i) {
            Entry entry = adapter.getItem(i);
            if (entry.selected) {
                selectedSet.add(entry.packageName);
            }
        }
        NFW.getSharedPreferences(getActivity())
                .edit()
                .putStringSet(mPrefKey, selectedSet)
                .apply();

        mSave = false;
    }

    private void log(String text) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, text);
        }
    }

    public static class Entry {
        public final String appName;
        public final String packageName;

        public boolean selected = false;

        public Entry(String appName, String packageName) {
            this.appName = appName;
            this.packageName = packageName;
        }
    }

    public static class SelectedListLoader extends MyAsyncTaskLoader<List<Entry>> {
        public SelectedListLoader(Context context) {
            super(context);
        }

        @Override
        public List<Entry> loadInBackground() {
            List<Entry> ret = new ArrayList<>();
            Context context = getContext();

            // get installed application's info
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            Collections.sort(apps, new ApplicationInfo.DisplayNameComparator(pm));
            for (ApplicationInfo info : apps) {
                String appName = (String) pm.getApplicationLabel(info);
                String packageName = info.packageName;
                ret.add(new Entry(appName, packageName));
            }
            return ret;
        }
    }

    private class Adapter extends ArrayAdapter<Entry> {
        public Adapter(Context context, List<Entry> entries) {
            super(context, 0, entries);
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                LayoutInflater inflater = LayoutInflater.from(getContext());
                view = inflater.inflate(R.layout.view_selectable_app, parent, false);
                ViewHolder holder = new ViewHolder();
                holder.icon = (ImageView) view.findViewById(R.id.icon);
                holder.appName = (TextView) view.findViewById(R.id.app_name);
                holder.packageName = (TextView) view.findViewById(R.id.package_name);
                holder.checkbox = (CheckBox) view.findViewById(R.id.checkbox);
                view.setTag(holder);
            }
            ViewHolder holder = (ViewHolder) view.getTag();

            Entry entry = getItem(position);
            //
            Bitmap icon = mIconCache.get(entry.packageName);
            if (icon != null && !icon.isRecycled()) {
                holder.icon.setImageBitmap(icon);
            } else {
                holder.icon.setImageBitmap(null);
                mIconCache.loadAsync(getContext(), entry.packageName);
            }
            holder.appName.setText(entry.appName);
            holder.packageName.setText(entry.packageName);
            holder.checkbox.setChecked(entry.selected);

            return view;
        }

        class ViewHolder {
            ImageView icon;
            TextView appName;
            TextView packageName;
            CheckBox checkbox;
        }
    }
}
