package com.fiendfyre.AdHell2.fragments;

import android.arch.lifecycle.LifecycleFragment;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.fiendfyre.AdHell2.R;
import com.fiendfyre.AdHell2.db.AppDatabase;
import com.fiendfyre.AdHell2.utils.AdhellAppIntegrity;
import com.fiendfyre.AdHell2.utils.BlockUrlUtils;

import java.util.ArrayList;
import java.util.Set;

public class ShowBlockUrlFragment extends LifecycleFragment {
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
        ListView listView = view.findViewById(R.id.blocked_url_list);

        new AsyncTask<Void, Void, Set<String>>() {
            @Override
            protected Set<String> doInBackground(Void... o) {
                return BlockUrlUtils.getUniqueBlockedUrls(appDatabase, false);
            }

            @Override
            protected void onPostExecute(Set<String> blockedUrls) {
                ArrayAdapter<String> itemsAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, new ArrayList<>(blockedUrls));
                listView.setAdapter(itemsAdapter);

                TextView totalBlockedUrls = view.findViewById(R.id.total_blocked_urls);
                totalBlockedUrls.setText(getString(R.string.total_blocked_urls) + String.valueOf(blockedUrls.size()));
            }
        }.execute();

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
                new AsyncTask<Void, Void, Set<String>>() {
                    @Override
                    protected Set<String> doInBackground(Void... o) {
                        final String text = editText.getText().toString();
                        final String filterText = '%' + text + '%';
                        return text.isEmpty() ?
                                BlockUrlUtils.getUniqueBlockedUrls(appDatabase, false) :
                                BlockUrlUtils.getMatchBlockedUrls(appDatabase, filterText);
                    }

                    @Override
                    protected void onPostExecute(Set<String> list) {
                        ArrayAdapter<String> itemsAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, new ArrayList<>(list));
                        listView.setAdapter(itemsAdapter);
                        listView.invalidateViews();
                    }
                }.execute();
            }
        });

        return view;
    }

}
