package com.fusionjack.adhell3.fragments;

import android.app.Activity;
import android.app.enterprise.ApplicationPolicy;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.Toast;

import com.fusionjack.adhell3.App;
import com.fusionjack.adhell3.R;
import com.fusionjack.adhell3.adapter.AppDisablerAdapter;
import com.fusionjack.adhell3.db.AppDatabase;
import com.fusionjack.adhell3.db.entity.AppInfo;
import com.fusionjack.adhell3.db.entity.DisabledPackage;
import com.fusionjack.adhell3.utils.AdhellAppIntegrity;
import com.fusionjack.adhell3.utils.AppsListDBInitializer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

public class PackageDisablerFragment extends Fragment {
    private final static int SORTED_ALPHABETICALLY = 0;
    private final static int SORTED_INSTALL_TIME = 1;
    private final static int SORTED_DISABLED = 2;

    @Nullable
    @Inject
    ApplicationPolicy appPolicy;
    @Inject
    AppDatabase appDatabase;
    @Inject
    PackageManager packageManager;

    private Context context;
    private int sortState = SORTED_ALPHABETICALLY;

    public PackageDisablerFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.get().getAppComponent().inject(this);
        context = getContext();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        getActivity().setTitle(getString(R.string.package_disabler_fragment_title));
        AppCompatActivity parentActivity = (AppCompatActivity) getActivity();
        if (parentActivity.getSupportActionBar() != null) {
            parentActivity.getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            parentActivity.getSupportActionBar().setHomeButtonEnabled(false);
        }
        setHasOptionsMenu(true);

        View view = inflater.inflate(R.layout.fragment_package_disabler, container, false);
        ListView installedAppsView = view.findViewById(R.id.installed_apps_list);
        installedAppsView.setOnItemClickListener((AdapterView<?> adView, View view2, int position, long id) -> {
            AppDisablerAdapter disablerAppAdapter = (AppDisablerAdapter) adView.getAdapter();
            String packageName = disablerAppAdapter.getItem(position).packageName;
            new SetAppAsyncTask(packageName, view2, appDatabase, appPolicy).execute();
        });

        SwipeRefreshLayout swipeContainer = view.findViewById(R.id.swipeContainer);
        swipeContainer.setOnRefreshListener(() ->
                new RefreshAsynckTask(sortState, context, appDatabase, packageManager).execute()
        );

        new LoadAppAsyncTask("", sortState, context, appDatabase, packageManager).execute();
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.package_disabler_menu, menu);

        SearchView searchView = (SearchView) menu.findItem(R.id.search).getActionView();
        searchView.setMaxWidth(Integer.MAX_VALUE);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String text) {
                new LoadAppAsyncTask(text, sortState, context, appDatabase, packageManager).execute();
                return false;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_pack_dis_sort:
                break;
            case R.id.sort_alphabetically_item:
                if (sortState == SORTED_ALPHABETICALLY) break;
                sortState = SORTED_ALPHABETICALLY;
                Toast.makeText(context, getString(R.string.app_list_sorted_by_alphabet), Toast.LENGTH_SHORT).show();
                new LoadAppAsyncTask("", sortState, context, appDatabase, packageManager).execute();
                break;
            case R.id.sort_by_time_item:
                if (sortState == SORTED_INSTALL_TIME) break;
                sortState = SORTED_INSTALL_TIME;
                Toast.makeText(context, getString(R.string.app_list_sorted_by_date), Toast.LENGTH_SHORT).show();
                new LoadAppAsyncTask("", sortState, context, appDatabase, packageManager).execute();
                break;
            case R.id.sort_disabled_item:
                sortState = SORTED_DISABLED;
                Toast.makeText(context, getString(R.string.app_list_sorted_by_disabled), Toast.LENGTH_SHORT).show();
                new LoadAppAsyncTask("", sortState, context, appDatabase, packageManager).execute();
                break;
            case R.id.disabler_import_storage:
                Toast.makeText(context, getString(R.string.imported_from_storage), Toast.LENGTH_SHORT).show();
                importList();
                break;
            case R.id.disabler_export_storage:
                Toast.makeText(context, getString(R.string.exported_to_storage), Toast.LENGTH_SHORT).show();
                exportList();
                break;
            case R.id.disabler_enable_all:
                Toast.makeText(context, getString(R.string.enabled_all_disabled), Toast.LENGTH_SHORT).show();
                enableAllPackages();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void importList() {
        AsyncTask.execute(() -> {
            if (appPolicy == null) {
                return;
            }

            File file = new File(Environment.getExternalStorageDirectory(), "adhell_packages.txt");
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    AppInfo appInfo = appDatabase.applicationInfoDao().getByPackageName(line);
                    appInfo.disabled = true;
                    appPolicy.setDisableApplication(line);
                    appDatabase.applicationInfoDao().insert(appInfo);

                    DisabledPackage disabledPackage = new DisabledPackage();
                    disabledPackage.packageName = line;
                    disabledPackage.policyPackageId = AdhellAppIntegrity.DEFAULT_POLICY_ID;
                    appDatabase.disabledPackageDao().insert(disabledPackage);
                }
                new LoadAppAsyncTask("", sortState, context, appDatabase, packageManager).execute();
            }
            catch (IOException e) {
                Log.e("Exception", "File write failed: " + e.toString());
            }
        });
    }

    private void exportList() {
        AsyncTask.execute(() -> {
            File file = new File(Environment.getExternalStorageDirectory(), "adhell_packages.txt");
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file))) {
                writer.write("");
                List<AppInfo> disabledAppList = appDatabase.applicationInfoDao().getDisabledApps();
                for (AppInfo app : disabledAppList) {
                    writer.append(app.packageName);
                    writer.append("\n");
                }
            } catch (IOException e) {
                Log.e("Exception", "File write failed: " + e.toString());
            }
        });
    }

    private void enableAllPackages() {
        AsyncTask.execute(() -> {
            if (appPolicy == null) {
                return;
            }

            List<AppInfo> disabledAppList = appDatabase.applicationInfoDao().getDisabledApps();
            for (AppInfo app : disabledAppList) {
                app.disabled = false;
                appPolicy.setEnableApplication(app.packageName);
                appDatabase.applicationInfoDao().insert(app);
            }
            appDatabase.disabledPackageDao().deleteAll();
            new LoadAppAsyncTask("", sortState, context, appDatabase, packageManager).execute();
        });
    }

    private static class RefreshAsynckTask extends AsyncTask<Void, Void, Void> {
        private WeakReference<Context> contextReference;
        private AppDatabase appDatabase;
        private PackageManager packageManager;
        private int sortState;

        RefreshAsynckTask(int sortState, Context context, AppDatabase appDatabase, PackageManager packageManager) {
            this.sortState = sortState;
            this.contextReference = new WeakReference<>(context);
            this.appDatabase = appDatabase;
            this.packageManager = packageManager;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            appDatabase.applicationInfoDao().deleteAll();
            AppsListDBInitializer.getInstance().fillPackageDb(packageManager);

            appDatabase.disabledPackageDao().deleteAll();
            List<DisabledPackage> disabledPackages = new ArrayList<>();
            List<AppInfo> disabledApps = appDatabase.applicationInfoDao().getDisabledApps();
            for (AppInfo appInfo : disabledApps) {
                DisabledPackage disabledPackage = new DisabledPackage();
                disabledPackage.packageName = appInfo.packageName;
                disabledPackage.policyPackageId = AdhellAppIntegrity.DEFAULT_POLICY_ID;
                disabledPackages.add(disabledPackage);
            }
            appDatabase.disabledPackageDao().insertAll(disabledPackages);

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            Context context = contextReference.get();
            if (context != null) {
                SwipeRefreshLayout swipeContainer = ((Activity) context).findViewById(R.id.swipeContainer);
                swipeContainer.setRefreshing(false);

                new LoadAppAsyncTask("", sortState, context, appDatabase, packageManager).execute();
            }
        }
    }

    private static class SetAppAsyncTask extends AsyncTask<Void, Void, Boolean> {
        private WeakReference<View> viewWeakReference;
        private AppDatabase appDatabase;
        private ApplicationPolicy appPolicy;
        private String packageName;

        SetAppAsyncTask(String packageName, View view, AppDatabase appDatabase, ApplicationPolicy appPolicy) {
            this.viewWeakReference = new WeakReference<>(view);
            this.packageName = packageName;
            this.appDatabase = appDatabase;
            this.appPolicy = appPolicy;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            if (appPolicy == null) {
                return false;
            }

            AppInfo appInfo = appDatabase.applicationInfoDao().getByPackageName(packageName);
            appInfo.disabled = !appInfo.disabled;
            if (appInfo.disabled) {
                appPolicy.setDisableApplication(packageName);
                DisabledPackage disabledPackage = new DisabledPackage();
                disabledPackage.packageName = packageName;
                disabledPackage.policyPackageId = AdhellAppIntegrity.DEFAULT_POLICY_ID;
                appDatabase.disabledPackageDao().insert(disabledPackage);
            } else {
                appPolicy.setEnableApplication(packageName);
                appDatabase.disabledPackageDao().deleteByPackageName(packageName);
            }
            appDatabase.applicationInfoDao().insert(appInfo);

            return appInfo.disabled;
        }

        @Override
        protected void onPostExecute(Boolean state) {
            ((Switch) viewWeakReference.get().findViewById(R.id.switchDisable)).setChecked(!state);
        }
    }

    private static class LoadAppAsyncTask extends AsyncTask<Void, Void, List<AppInfo>> {
        private WeakReference<Context> contextReference;
        private AppDatabase appDatabase;
        private PackageManager packageManager;
        private String text;
        private int sortState;

        LoadAppAsyncTask(String text, int sortState, Context context, AppDatabase appDatabase, PackageManager packageManager) {
            this.text = text;
            this.sortState = sortState;
            this.contextReference = new WeakReference<>(context);
            this.appDatabase = appDatabase;
            this.packageManager = packageManager;
        }

        @Override
        protected List<AppInfo> doInBackground(Void... voids) {
            return getListFromDb();
        }

        @Override
        protected void onPostExecute(List<AppInfo> packageList) {
            Context context = contextReference.get();
            if (context != null) {
                AppDisablerAdapter adapter = new AppDisablerAdapter(packageList, context, packageManager);
                ListView listView = ((Activity)context).findViewById(R.id.installed_apps_list);
                listView.setAdapter(adapter);
                listView.invalidateViews();
            }
        }

        private List<AppInfo> getListFromDb() {
            String filterText = '%' + text + '%';
            switch (sortState) {
                case SORTED_ALPHABETICALLY:
                    if (text.length() == 0) {
                        return appDatabase.applicationInfoDao().getAll();
                    }
                    return appDatabase.applicationInfoDao().getAllAppsWithStrInName(filterText);
                case SORTED_INSTALL_TIME:
                    if (text.length() == 0) {
                        return appDatabase.applicationInfoDao().getAllRecentSort();
                    }
                    return appDatabase.applicationInfoDao().getAllAppsWithStrInNameTimeOrder(filterText);
                case SORTED_DISABLED:
                    if (text.length() == 0) {
                        return appDatabase.applicationInfoDao().getAllSortedByDisabled();
                    }
                    return appDatabase.applicationInfoDao().getAllAppsWithStrInNameDisabledOrder(filterText);
            }
            return null;
        }
    }
}
