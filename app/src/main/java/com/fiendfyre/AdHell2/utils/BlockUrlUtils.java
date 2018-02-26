package com.fiendfyre.AdHell2.utils;

import android.support.annotation.NonNull;
import android.util.Log;
import android.webkit.URLUtil;

import com.fiendfyre.AdHell2.db.entity.BlockUrl;
import com.fiendfyre.AdHell2.db.entity.BlockUrlProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BlockUrlUtils {
    private static final String TAG = BlockUrlUtils.class.getCanonicalName();

    @NonNull
    public static List<BlockUrl> loadBlockUrls(BlockUrlProvider blockUrlProvider) throws IOException {
        URL urlProviderUrl = new URL(blockUrlProvider.url);
        URLConnection connection = urlProviderUrl.openConnection();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        Set<BlockUrl> blockUrls = new HashSet<>();
        String inputLine;
        while ((inputLine = bufferedReader.readLine()) != null) {
            inputLine = inputLine
                    .replaceAll("\\s","") // Remove whitespace
                    .replaceAll("(#.*)|((\\s)+#.*)","") // Remove comments
                    .toLowerCase();

            if (blockUrls.size() > AdhellAppIntegrity.BLOCK_URL_LIMIT) {
                throw new IllegalArgumentException("The URL provider contains more than " +
                        AdhellAppIntegrity.BLOCK_URL_LIMIT + " domains.");
            }

            if (URLUtil.isValidUrl("http://" + inputLine)) {
                if (BlockUrlPatternsMatch.isUrlValid(inputLine)) {
                    BlockUrl blockUrl = new BlockUrl(inputLine, blockUrlProvider.id);
                    blockUrls.add(blockUrl);
                } else {
                    Log.d(TAG, "Invalid URL: " + inputLine);
                }
            }
        }
        bufferedReader.close();
        return new ArrayList<>(blockUrls);
    }
}
