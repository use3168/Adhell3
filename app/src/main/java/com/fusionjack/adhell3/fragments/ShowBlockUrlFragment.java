package com.fusionjack.adhell3.fragments;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.fusionjack.adhell3.R;
import com.fusionjack.adhell3.db.AppDatabase;
import com.fusionjack.adhell3.utils.BlockUrlUtils;

import java.lang.ref.WeakReference;
import java.util.List;

public class ShowBlockUrlFragment extends Fragment {
    private AppDatabase appDatabase;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appDatabase = AppDatabase.getAppDatabase(getContext());
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_show_blocked_urls, container, false);
        EditText editText = view.findViewById(R.id.blockedUrlFilter);
        editText.setOnClickListener(v -> editText.setCursorVisible(true));
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                new FilterUrlAsyncTask(getContext(), appDatabase).execute();
            }
        });

        new LoadBlockedUrlAsyncTask(getContext(), getArguments(), appDatabase).execute();

        return view;
    }

    private static class LoadBlockedUrlAsyncTask extends AsyncTask<Void, Void, List<String>> {
        private WeakReference<Context> contextReference;
        private Bundle bundle;
        private AppDatabase appDatabase;

        LoadBlockedUrlAsyncTask(Context context, Bundle bundle, AppDatabase appDatabase) {
            this.contextReference = new WeakReference<>(context);
            this.bundle = bundle;
            this.appDatabase = appDatabase;
        }

        @Override
        protected List<String> doInBackground(Void... o) {
            return bundle == null ?
                    BlockUrlUtils.getAllBlockedUrls(appDatabase) :
                    BlockUrlUtils.getBlockedUrls(bundle.getLong("provider"), appDatabase);
        }

        @Override
        protected void onPostExecute(List<String> blockedUrls) {
            Context context = contextReference.get();
            if (context != null) {
                ArrayAdapter<String> itemsAdapter = new ArrayAdapter<>(context,
                        android.R.layout.simple_list_item_1, blockedUrls);
                ListView listView = ((Activity)context).findViewById(R.id.blocked_url_list);
                listView.setAdapter(itemsAdapter);

                TextView totalBlockedUrls = ((Activity)context).findViewById(R.id.total_blocked_urls);
                totalBlockedUrls.setText(String.format("%s%s",
                        context.getString(R.string.total_blocked_urls), String.valueOf(blockedUrls.size())));
            }
        }
    }

    private static class FilterUrlAsyncTask extends AsyncTask<Void, Void, List<String>> {
        private WeakReference<Context> contextReference;
        private AppDatabase appDatabase;

        FilterUrlAsyncTask(Context context, AppDatabase appDatabase) {
            this.contextReference = new WeakReference<>(context);
            this.appDatabase = appDatabase;
        }

        @Override
        protected List<String> doInBackground(Void... o) {
            Context context = contextReference.get();
            if (context != null) {
                EditText editText = ((Activity) context).findViewById(R.id.blockedUrlFilter);
                final String text = editText.getText().toString();
                final String filterText = '%' + text + '%';
                return text.isEmpty() ?
                        BlockUrlUtils.getAllBlockedUrls(appDatabase) :
                        BlockUrlUtils.getBlockedUrls(filterText, appDatabase);
            }
            return null;
        }

        @Override
        protected void onPostExecute(List<String> list) {
            Context context = contextReference.get();
            if (context != null) {
                ArrayAdapter<String> itemsAdapter = new ArrayAdapter<>(context,
                        android.R.layout.simple_list_item_1, list);
                ListView listView = ((Activity)context).findViewById(R.id.blocked_url_list);
                listView.setAdapter(itemsAdapter);
                listView.invalidateViews();
            }
        }
    }
}
