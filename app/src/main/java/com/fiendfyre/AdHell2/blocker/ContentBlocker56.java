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

        Log.d(TAG, "Adding: DENY MOBILE DATA");
        List<AppInfo> restrictedApps = appDatabase.applicationInfoDao().getMobileRestrictedApps();
        if (restrictedApps.size() > 0) {
            // Define DENY rules for mobile data
            FirewallRule[] mobileRules = new FirewallRule[restrictedApps.size()];
            for (int i = 0; i < restrictedApps.size(); i++) {
                mobileRules[i] = new FirewallRule(FirewallRule.RuleType.DENY, Firewall.AddressType.IPV4);
                mobileRules[i].setNetworkInterface(Firewall.NetworkInterface.MOBILE_DATA_ONLY);
                mobileRules[i].setApplication(new AppIdentity(restrictedApps.get(i).packageName, null));
            }

            // Send rules to the firewall
            FirewallResponse[] response = mFirewall.addRules(mobileRules);
            if (FirewallResponse.Result.SUCCESS == response[0].getResult()) {
                Log.i(TAG, "Mobile data rules have been added: " + response[0].getMessage());
            } else {
                Log.i(TAG, "Failed to add mobile data rules: " + response[0].getMessage());
            }
        }
        Log.i(TAG, "Restricted apps size: " + restrictedApps.size());

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
            Log.i(TAG, "Included url provider: " + blockUrlProvider.url);
            List<BlockUrl> blockUrls = appDatabase.blockUrlDao().getUrlsByProviderId(blockUrlProvider.id);
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

        // Create domain filter rule for white listed apps
        List<String> superAllow = new ArrayList<>();
        superAllow.add("*");
        List<AppInfo> appInfos = appDatabase.applicationInfoDao().getWhitelistedApps();
        Log.d(TAG, "Whitelisted apps size: " + appInfos.size());
        for (AppInfo app : appInfos) {
            Log.d(TAG, app.packageName);
            rules.add(new DomainFilterRule(new AppIdentity(app.packageName, null), new ArrayList<>(), superAllow));
        }

        // Create firewall rule for blocking port 53 and add it to Knox Firewall
        // It is necessary for Chrome
        try {
            Log.d(TAG, "Adding: DENY PORT 53");
            FirewallRule[] portRules = new FirewallRule[2];

            // Add deny rules for DNS port (53)
            portRules[0] = new FirewallRule(FirewallRule.RuleType.DENY, Firewall.AddressType.IPV4);
            portRules[0].setIpAddress("*");
            portRules[0].setPortNumber("53");

            portRules[1] = new FirewallRule(FirewallRule.RuleType.DENY, Firewall.AddressType.IPV6);
            portRules[1].setIpAddress("*");
            portRules[1].setPortNumber("53");

            // Send rules to the firewall
            FirewallResponse[] response = mFirewall.addRules(portRules);
            if (FirewallResponse.Result.SUCCESS == response[0].getResult()) {
                Log.i(TAG, "Port rules have been added: " + response[0].getMessage());
            } else {
                Log.i(TAG, "Failed to add port rules: " + response[0].getMessage());
            }
        }
        catch (SecurityException ex)
        {
            Log.e(TAG, "Failed to add PORT rule.", ex);
            return false;
        }

        // Add domain filter rule to Knox Firewall
        try {
            Log.d(TAG, "Adding: DENY DOMAINS");
            FirewallResponse[] response = mFirewall.addDomainFilterRules(rules);

            if (!mFirewall.isFirewallEnabled()) {
                mFirewall.enableFirewall(true);
            }
            if (!mFirewall.isDomainFilterReportEnabled()) {
                Log.d(TAG, "Enabling filewall report");
                mFirewall.enableDomainFilterReport(true);
            }
            if (FirewallResponse.Result.SUCCESS == response[0].getResult()) {
                Log.i(TAG, "Adhell enabled " + response[0].getMessage());
                return true;
            } else {
                Log.i(TAG, "Adhell enabling failed " + response[0].getMessage());
                return false;
            }
        } catch (SecurityException ex) {
            Log.e(TAG, "Adhell enabling failed", ex);
            return false;
        }
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