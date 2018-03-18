package com.fiendfyre.AdHell2.viewmodel;


import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;

import com.fiendfyre.AdHell2.db.AppDatabase;
import com.fiendfyre.AdHell2.db.entity.ReportBlockedUrl;

import java.util.Calendar;
import java.util.List;

public class AdhellReportViewModel extends AndroidViewModel {
    private LiveData<List<ReportBlockedUrl>> reportBlockedUrls;
    private AppDatabase mDb;

    public AdhellReportViewModel(Application application) {
        super(application);
        mDb = AppDatabase.getAppDatabase(application);
    }

    public LiveData<List<ReportBlockedUrl>> getReportBlockedUrls() {
        if (reportBlockedUrls == null) {
            reportBlockedUrls = new MutableLiveData<>();
            loadReportBlockedUrls();
        }
        return reportBlockedUrls;
    }

    private void loadReportBlockedUrls() {
        reportBlockedUrls = mDb.reportBlockedUrlDao().getReportBlockUrlBetween(
                yesterday(), System.currentTimeMillis());
    }

    private long yesterday() {
        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        return cal.getTimeInMillis();
    }
}
