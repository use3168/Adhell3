package com.fusionjack.adhell3.fragments;

import android.annotation.SuppressLint;
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

import com.fusionjack.adhell3.R;
import com.fusionjack.adhell3.db.AppDatabase;
import com.fusionjack.adhell3.db.entity.BlockUrl;
import com.fusionjack.adhell3.utils.BlockUrlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ShowBlockUrlFragment extends LifecycleFragment {
    private AppDatabase appDatabase;
    private List<BlockUrl> blockedUrlList;

    public ShowBlockUrlFragment() {
        blockedUrlList = null;
    }

    @SuppressLint("ValidFragment")
    public ShowBlockUrlFragment(List<BlockUrl> list) {
        blockedUrlList = list;
    }

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

        new AsyncTask<Void, Void, List<String>>() {
            @Override
            protected List<String> doInBackground(Void... o) {
                if (blockedUrlList == null) {
                    return new ArrayList<>(BlockUrlUtils.getUniqueBlockedUrls(appDatabase, false));
                } else {
                    List<String> list = new ArrayList<>();
                    for (BlockUrl blockUrl : blockedUrlList) {
                        list.add(blockUrl.url);
                    }
                    return list;
                }
            }

            @Override
            protected void onPostExecute(List<String> blockedUrls) {
                ArrayAdapter<String> itemsAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, blockedUrls);
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
