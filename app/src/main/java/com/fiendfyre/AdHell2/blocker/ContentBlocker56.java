package com.fiendfyre.AdHell2.blocker;

import android.support.annotation.Nullable;
import android.util.Log;

import com.fiendfyre.AdHell2.App;
import com.fiendfyre.AdHell2.db.AppDatabase;
import com.fiendfyre.AdHell2.db.entity.AppInfo;
import com.fiendfyre.AdHell2.db.entity.BlockUrl;
import com.fiendfyre.AdHell2.db.entity.BlockUrlProvider;
import com.fiendfyre.AdHell2.db.entity.UserBlockUrl;
import com.fiendfyre.AdHell2.db.entity.WhiteUrl;
import com.fiendfyre.AdHell2.utils.AdhellAppIntegrity;
import com.fiendfyre.AdHell2.utils.BlockUrlPatternsMatch;
import com.sec.enterprise.AppIdentity;
import com.sec.enterprise.firewall.DomainFilterRule;
import com.sec.enterprise.firewall.Firewall;
import com.sec.enterprise.firewall.FirewallResponse;
import com.sec.enterprise.firewall.FirewallRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

public class ContentBlocker56 implements ContentBlocker {
    private static ContentBlocker56 mInstance = null;
    private final String TAG = ContentBlocker56.class.getCanonicalName();

    @Nullable
    @Inject
    Firewall mFirewall;
    @Inject
    AppDatabase appDatabase;

    private ContentBlocker56() {
        App.get().getAppComponent().inject(this);
    }

    public static ContentBlocker56 getInstance() {
        if (mInstance == null) {
            mInstance = getSync();
        }
        return mInstance;
    }

    private static synchronized ContentBlocker56 getSync() {
        if (mInstance == null) {
            mInstance = new ContentBlocker56();
        }
        return mInstance;
    }

    @Override
    public boolean enableBlocker() {
        if (isEnabled()) {
            disableBlocker();
        }

        processChromeApps();
        boolean result = processMobileRestrictedApps();
        result |= processWhitelistedApps();
        result |= processBlockedDomains();

        if (result) {
            try {
                if (!mFirewall.isFirewallEnabled()) {
                    Log.i(TAG, "Enabling firewall...");
                    mFirewall.enableFirewall(true);
                }
                if (!mFirewall.isDomainFilterReportEnabled()) {
                    Log.i(TAG, "Enabling firewall report...");
                    mFirewall.enableDomainFilterReport(true);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to enable firewall: " + e.getMessage(), e);
            }
        }

        return result;
    }

    private void processChromeApps() {
        processChromeApp("com.android.chrome");
        processChromeApp("com.chrome.beta");
        processChromeApp("com.chrome.dev");
        processChromeApp("com.chrome.canary");
    }

    private void processChromeApp(String packageName) {
        Log.i(TAG, "Processing chrome app '" + packageName + "'...");

        // Block port 53 traffic for Chromium-based browsers,
        // since Chromium has its own DNS-resolution implementation.
        // Blocking this implementation's resolution makes it fallback to the system's one.
        FirewallRule[] firewallRules = new FirewallRule[1];
        firewallRules[0] = new FirewallRule(FirewallRule.RuleType.DENY, Firewall.AddressType.IPV4);
        firewallRules[0].setIpAddress("*");
        firewallRules[0].setPortNumber("53");
        firewallRules[0].setApplication(new AppIdentity(packageName, null));

        // Send rules to the firewall
        FirewallResponse[] response = null;
        try {
            Log.i(TAG, "Adding firewall rule to Knox Firewall...");
            response = mFirewall.addRules(firewallRules);
            Log.i(TAG, "Result: " + response[0].getMessage());
        } catch (SecurityException ex) {
            // Missing required MDM permission
            Log.e(TAG, "Failed to add rules to Knox Firewall", ex);
        }
    }

    private boolean processMobileRestrictedApps() {
        Log.i(TAG, "Processing mobile restricted apps...");

        List<AppInfo> restrictedApps = appDatabase.applicationInfoDao().getMobileRestrictedApps();
        Log.i(TAG, "Restricted apps size: " + restrictedApps.size());
        if (restrictedApps.size() == 0) {
            return true;
        }

        // Define DENY rules for mobile data
        FirewallRule[] mobileRules = new FirewallRule[restrictedApps.size()];
        for (int i = 0; i < restrictedApps.size(); i++) {
            mobileRules[i] = new FirewallRule(FirewallRule.RuleType.DENY, Firewall.AddressType.IPV4);
            mobileRules[i].setNetworkInterface(Firewall.NetworkInterface.MOBILE_DATA_ONLY);
            mobileRules[i].setApplication(new AppIdentity(restrictedApps.get(i).packageName, null));
        }

        // Send rules to the firewall
        FirewallResponse[] response = null;
        try {
            Log.i(TAG, "Adding firewall rule to Knox Firewall...");
            response = mFirewall.addRules(mobileRules);
            Log.i(TAG, "Result: " + response[0].getMessage());
        } catch (SecurityException ex) {
            // Missing required MDM permission
            Log.e(TAG, "Failed to add firewall rules to Knox Firewall", ex);
        }
        return response != null && (FirewallResponse.Result.SUCCESS == response[0].getResult());
    }

    private boolean processWhitelistedApps() {
        Log.i(TAG, "Processing white-listed apps...");

        // Create domain filter rule for white listed apps
        List<AppInfo> whitelistedApps = appDatabase.applicationInfoDao().getWhitelistedApps();
        Log.i(TAG, "Whitelisted apps size: " + whitelistedApps.size());
        if (whitelistedApps.size() == 0) {
            return true;
        }

        List<DomainFilterRule> rules = new ArrayList<>();
        List<String> superAllow = new ArrayList<>();
        superAllow.add("*");
        for (AppInfo app : whitelistedApps) {
            Log.d(TAG, app.packageName);
            rules.add(new DomainFilterRule(new AppIdentity(app.packageName, null), new ArrayList<>(), superAllow));
        }

        // Add domain filter rule to Knox Firewall
        Log.i(TAG, "Adding domain filter rule to Knox Firewall...");
        FirewallResponse[] response = null;
        try {
            response = mFirewall.addDomainFilterRules(rules);
            Log.i(TAG, "Result: " + response[0].getMessage());
        } catch (SecurityException ex) {
            // Missing required MDM permission
            Log.e(TAG, "Failed to add domain filter rule to Knox Firewall", ex);
        }
        return response != null && (FirewallResponse.Result.SUCCESS == response[0].getResult());
    }

    private boolean processBlockedDomains() {
        Log.i(TAG, "Processing blocked domains...");

        // Process user-defined white list
        List<WhiteUrl> whiteUrls = appDatabase.whiteUrlDao().getAll2();
        Set<String> whiteList = new HashSet<>();
        for (WhiteUrl whiteUrl : whiteUrls) {
            final String url = BlockUrlPatternsMatch.getValidatedUrl(whiteUrl.url);
            whiteList.add(url);
            Log.i(TAG, "WhiteUrl: " + url);
        }
        Log.i(TAG, "White list size: " + whiteList.size());

        // Process user-defined blocked URLs
        Set<String> denyList = new HashSet<>();
        List<UserBlockUrl> userBlockUrls = appDatabase.userBlockUrlDao().getAll2();
        for (UserBlockUrl userBlockUrl : userBlockUrls) {
            final String url = BlockUrlPatternsMatch.getValidatedUrl(userBlockUrl.url);
            denyList.add(url);
            Log.i(TAG, "UserBlockUrl: " + url);
        }
        Log.i(TAG, "User blocked URL size: " + userBlockUrls.size());

        // Process all block URL providers into deny list
        List<BlockUrlProvider> blockUrlProviders = appDatabase.blockUrlProviderDao().getBlockUrlProviderBySelectedFlag(1);
        int urlBlockLimit = AdhellAppIntegrity.BLOCK_URL_LIMIT;
        for (BlockUrlProvider blockUrlProvider : blockUrlProviders) {
            List<BlockUrl> blockUrls = appDatabase.blockUrlDao().getUrlsByProviderId(blockUrlProvider.id);
            Log.i(TAG, "Included url provider: " + blockUrlProvider.url + ", size: " + blockUrls.size());

            if (denyList.size() + blockUrls.size() > urlBlockLimit) {
                Log.i(TAG, "Total number of blocked URLs has reached limit! " +
                        "Deny list size: " + denyList.size() + ", " +
                        "Current URL provider size: " + blockUrls.size());
                break;
            }

            for (BlockUrl blockUrl : blockUrls) {
                denyList.add(BlockUrlPatternsMatch.getValidatedUrl(blockUrl.url));
            }
        }
        Log.i(TAG, "Deny list size: " + denyList.size());

        // Create domain filter rule with deny and white list
        List<DomainFilterRule> rules = new ArrayList<>();
        AppIdentity appIdentity = new AppIdentity("*", null);
        rules.add(new DomainFilterRule(appIdentity, new ArrayList<>(denyList), new ArrayList<>(whiteList)));

        // Add domain filter rule to Knox Firewall
        Log.i(TAG, "Adding domain filter rule to Knox Firewall...");
        FirewallResponse[] response = null;
        try {
            response = mFirewall.addDomainFilterRules(rules);
            Log.i(TAG, "Result: " + response[0].getMessage());
        } catch (SecurityException ex) {
            // Missing required MDM permission
            Log.e(TAG, "Failed to add domain filter rule to Knox Firewall", ex);
        }
        return response != null && (FirewallResponse.Result.SUCCESS == response[0].getResult());
    }

    @Override
    public boolean disableBlocker() {
        FirewallResponse[] response;
        try {
            // Clear IP rules
            response = mFirewall.clearRules(Firewall.FIREWALL_ALL_RULES);

            // Clear domain filter rules
            response = mFirewall.removeDomainFilterRules(DomainFilterRule.CLEAR_ALL);

            Log.i(TAG, "disableBlocker " + response[0].getMessage());
            if (mFirewall.isFirewallEnabled()) {
                mFirewall.enableFirewall(false);
            }
            if (mFirewall.isDomainFilterReportEnabled()) {
                mFirewall.enableDomainFilterReport(false);
            }
        } catch (SecurityException ex) {
            Log.e(TAG, "Failed to remove firewall rules", ex);
            return false;
        }
        return true;
    }

    @Override
    public boolean isEnabled() {
        return mFirewall.isFirewallEnabled();
    }

}