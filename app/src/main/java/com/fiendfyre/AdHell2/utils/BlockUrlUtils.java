package com.fiendfyre.AdHell2.utils;

import android.support.annotation.NonNull;
import android.util.Log;
import android.webkit.URLUtil;

import com.fiendfyre.AdHell2.db.AppDatabase;
import com.fiendfyre.AdHell2.db.entity.BlockUrl;
import com.fiendfyre.AdHell2.db.entity.BlockUrlProvider;
import com.fiendfyre.AdHell2.db.entity.UserBlockUrl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BlockUrlUtils {
    private static final String TAG = BlockUrlUtils.class.getCanonicalName();

    @NonNull
    public static List<BlockUrl> loadBlockUrls(BlockUrlProvider blockUrlProvider) throws IOException, URISyntaxException {
        BufferedReader bufferedReader;
        if (URLUtil.isFileUrl(blockUrlProvider.url)) {
            File file = new File(new URI(blockUrlProvider.url));
            bufferedReader = new BufferedReader(new FileReader(file));
        } else {
            URL urlProviderUrl = new URL(blockUrlProvider.url);
            URLConnection connection = urlProviderUrl.openConnection();
            bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        }

        Set<BlockUrl> blockUrls = new HashSet<>();
        String inputLine;
        while ((inputLine = bufferedReader.readLine()) != null) {
            inputLine = getDomain(inputLine).trim().toLowerCase();
            if (BlockUrlPatternsMatch.isUrlValid(inputLine)) {
                BlockUrl blockUrl = new BlockUrl(inputLine, blockUrlProvider.id);
                blockUrls.add(blockUrl);
            }
        }
        bufferedReader.close();
        return new ArrayList<>(blockUrls);
    }

    private static String getDomain(String inputLine) {
        return inputLine
                // Remove 'deadzone' - We only want the domain
                .replace("127.0.0.1", "")
                .replace("0.0.0.0", "")

                // Remove whitespace
                .replaceAll("\\s","")

                // Remove comments
                .replaceAll("(#.*)|((\\s)+#.*)","")

                // Remove WWW, WWW1 etc. prefix
                .replaceAll("^(www)([0-9]{0,3})?(\\.)","");
    }

    public static Set<String> getUniqueBlockedUrls(AppDatabase appDatabase, boolean logging) {
        Set<String> denyList = new HashSet<>();

        // Process user-defined blocked URLs
        int userBlockUrlCount = 0;
        List<UserBlockUrl> userBlockUrls = appDatabase.userBlockUrlDao().getAll2();
        for (UserBlockUrl userBlockUrl : userBlockUrls) {
            if (userBlockUrl.url.indexOf('|') == -1) {
                final String url = BlockUrlPatternsMatch.getValidatedUrl(userBlockUrl.url);
                denyList.add(url);
                if (logging) LogUtils.getInstance().writeInfo("UserBlockUrl: " + url);
                userBlockUrlCount++;
            }
        }
        if (logging) LogUtils.getInstance().writeInfo("User blocked URL size: " + userBlockUrlCount);

        // Process all blocked URL providers
        List<BlockUrlProvider> blockUrlProviders = appDatabase.blockUrlProviderDao().getBlockUrlProviderBySelectedFlag(1);
        for (BlockUrlProvider blockUrlProvider : blockUrlProviders) {
            List<BlockUrl> blockUrls = appDatabase.blockUrlDao().getUrlsByProviderId(blockUrlProvider.id);
            if (logging) LogUtils.getInstance().writeInfo("Included url provider: " + blockUrlProvider.url + ", size: " + blockUrls.size());

            for (BlockUrl blockUrl : blockUrls) {
                denyList.add(BlockUrlPatternsMatch.getValidatedUrl(blockUrl.url));
            }
        }

        if (logging) LogUtils.getInstance().writeInfo("Total unique domains to block: " + denyList.size());
        return denyList;
    }

    public static Set<String> getMatchBlockedUrls(AppDatabase appDatabase, String filterText) {
        Set<String> result = new HashSet<>();

        List<UserBlockUrl> userBlockUrls = appDatabase.userBlockUrlDao().getByUrl(filterText);
        for (UserBlockUrl userBlockUrl : userBlockUrls) {
            result.add(userBlockUrl.url);
        }

        List<BlockUrlProvider> blockUrlProviders = appDatabase.blockUrlProviderDao().getBlockUrlProviderBySelectedFlag(1);
        for (BlockUrlProvider blockUrlProvider : blockUrlProviders) {
            List<BlockUrl> blockUrls = appDatabase.blockUrlDao().getByUrl(blockUrlProvider.id, filterText);
            for (BlockUrl blockUrl: blockUrls) {
                result.add(blockUrl.url);
            }
        }

        return result;
    }

}
