package com.fusionjack.adhell3.fragments;

import android.app.Activity;
import android.app.enterprise.ApplicationPolicy;
import android.content.Context;
import android.os.AsyncTask;
import android.support.v4.widget.SwipeRefreshLayout;

import com.fusionjack.adhell3.R;
import com.fusionjack.adhell3.db.AppDatabase;
import com.fusionjack.adhell3.db.entity.AppInfo;
import com.fusionjack.adhell3.db.entity.DisabledPackage;
import com.fusionjack.adhell3.db.entity.RestrictedPackage;
import com.fusionjack.adhell3.utils.AdhellAppIntegrity;
import com.fusionjack.adhell3.utils.AppsListDBInitializer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class RefreshAppAsyncTask extends AsyncTask<Void, Void, Void> {
    private WeakReference<Context> contextReference;
    private int sortState;
    private int layout;
    private AppFlag appFlag;

    RefreshAppAsyncTask(int sortState, int layout, AppFlag appFlag, Context context) {
        this.sortState = sortState;
        this.layout = layout;
        this.appFlag = appFlag;
        this.contextReference = new WeakReference<>(context);
    }

    @Override
    protected Void doInBackground(Void... voids) {
        // Get first disabled and restricted apps before they get deleted
        AppDatabase appDatabase = appFlag.getAppDatabase();
        List<AppInfo> disabledApps = appDatabase.applicationInfoDao().getDisabledApps();
        List<AppInfo> restrictedApps = appDatabase.applicationInfoDao().getMobileRestrictedApps();

        // Delete all apps info
        appDatabase.applicationInfoDao().deleteAll();
        AppsListDBInitializer.getInstance().fillPackageDb(appFlag.getPackageManager());

        switch (appFlag.getFlag()) {
            case DISABLER_FLAG:
                ApplicationPolicy appPolicy = appFlag.getAppPolicy();
                if (appPolicy == null) {
                    break;
                }

                appDatabase.disabledPackageDao().deleteAll();
                List<DisabledPackage> disabledPackages = new ArrayList<>();
                for (AppInfo oldAppInfo : disabledApps) {
                    appPolicy.setEnableApplication(oldAppInfo.packageName);
                    AppInfo newAppInfo = appDatabase.applicationInfoDao().getByPackageName(oldAppInfo.packageName);
                    if (newAppInfo != null) {
                        newAppInfo.disabled = true;
                        appDatabase.applicationInfoDao().insert(newAppInfo);
                        appPolicy.setDisableApplication(newAppInfo.packageName);

                        DisabledPackage disabledPackage = new DisabledPackage();
                        disabledPackage.packageName = newAppInfo.packageName;
                        disabledPackage.policyPackageId = AdhellAppIntegrity.DEFAULT_POLICY_ID;
                        disabledPackages.add(disabledPackage);
                    }
                }
                appDatabase.disabledPackageDao().insertAll(disabledPackages);
                break;

            case RESTRICTED_FLAG:
                appDatabase.restrictedPackageDao().deleteAll();
                List<RestrictedPackage> restrictedPackages = new ArrayList<>();
                for (AppInfo oldAppInfo : restrictedApps) {
                    AppInfo newAppInfo = appDatabase.applicationInfoDao().getByPackageName(oldAppInfo.packageName);
                    if (newAppInfo != null) {
                        newAppInfo.mobileRestricted = true;
                        appDatabase.applicationInfoDao().insert(newAppInfo);

                        RestrictedPackage restrictedPackage = new RestrictedPackage();
                        restrictedPackage.packageName = newAppInfo.packageName;
                        restrictedPackage.policyPackageId = AdhellAppIntegrity.DEFAULT_POLICY_ID;
                        restrictedPackages.add(restrictedPackage);
                    }
                }
                appDatabase.restrictedPackageDao().insertAll(restrictedPackages);
                break;
        }

        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        Context context = contextReference.get();
        if (context != null) {
            SwipeRefreshLayout swipeContainer = ((Activity) context).findViewById(R.id.swipeContainer);
            swipeContainer.setRefreshing(false);

            new LoadAppAsyncTask("", sortState, layout, appFlag, context).execute();
        }
    }
}
