package com.fiendfyre.AdHell2.blocker;

import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Patterns;

import com.fiendfyre.AdHell2.App;
import com.fiendfyre.AdHell2.MainActivity;
import com.fiendfyre.AdHell2.db.AppDatabase;
import com.fiendfyre.AdHell2.db.entity.AppInfo;
import com.fiendfyre.AdHell2.db.entity.BlockUrl;
import com.fiendfyre.AdHell2.db.entity.BlockUrlProvider;
import com.fiendfyre.AdHell2.db.entity.UserBlockUrl;
import com.fiendfyre.AdHell2.db.entity.WhiteUrl;
import com.fiendfyre.AdHell2.utils.BlockUrlPatternsMatch;
import com.sec.enterprise.AppIdentity;
import com.sec.enterprise.firewall.DomainFilterRule;
import com.sec.enterprise.firewall.Firewall;
import com.sec.enterprise.firewall.FirewallResponse;
import com.sec.enterprise.firewall.FirewallRule;

import java.util.ArrayList;
import java.util.Arrays;
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
    private int urlBlockLimit = 2700;

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

        List<WhiteUrl> whiteUrls = appDatabase.whiteUrlDao().getAll2();
        List<String> whiteUrlsString = new ArrayList<>();
        for (WhiteUrl whiteUrl : whiteUrls) {
            whiteUrlsString.add(whiteUrl.url);
        }

        List<String> denyList = new ArrayList<>();
        List<BlockUrlProvider> blockUrlProviders = appDatabase.blockUrlProviderDao().getBlockUrlProviderBySelectedFlag(1);
        for (BlockUrlProvider blockUrlProvider : blockUrlProviders) {
            Log.i(TAG, "Included url provider: " + blockUrlProvider.url);
            List<BlockUrl> blockUrls = appDatabase.blockUrlDao().getUrlsByProviderId(blockUrlProvider.id);

            for (BlockUrl blockUrl : blockUrls) {
                if (whiteUrlsString.contains(blockUrl.url)) {
                    continue;
                }
                if (denyList.size() > urlBlockLimit) {
                    break;
                }

                // If a wildcard entry is passed, bypass current URL filter
                if (blockUrl.url.contains("*")) {
                    Log.d(TAG, "Wildcard detected --> " + blockUrl.url + " requires validation.");
                    boolean validWildcard = BlockUrlPatternsMatch.wildcardValid(blockUrl.url);
                    if (!validWildcard) {
                        Log.d(TAG, blockUrl.url + " is not a valid wildcard.");
                        continue;
                    }
                    denyList.add(blockUrl.url);
                } else {
                    // Let's remove the unnecessary www, www1 etc.
                    blockUrl.url = blockUrl.url.replaceAll("^(www)([0-9]{0,3})?(\\.)", "");
                    boolean validDomain = BlockUrlPatternsMatch.domainValid(blockUrl.url);
                    if (!validDomain) {
                        Log.d(TAG, "Invalid Domain: " + blockUrl.url);
                        continue;
                    }
                    denyList.add("*" + blockUrl.url);
                }
            }
        }

        List<UserBlockUrl> userBlockUrls = appDatabase.userBlockUrlDao().getAll2();
        if (userBlockUrls != null && userBlockUrls.size() > 0) {
            Log.i(TAG, "UserBlockUrls size: " + userBlockUrls.size());
            for (UserBlockUrl userBlockUrl : userBlockUrls) {
                if (Patterns.WEB_URL.matcher(userBlockUrl.url).matches()) {
                    final String urlReady = "*" + userBlockUrl.url + "*";
                    denyList.add(urlReady);
                    Log.i(TAG, "UserBlockUrl: " + urlReady);
                }
            }
        } else {
            Log.i(TAG, "UserBlockUrls is empty.");
        }

        Log.d(TAG, "Number of url block list: " + denyList.size());
        if (denyList.size() > urlBlockLimit) {
            Log.d(TAG, "Number of url block list exceeds limit, reducing to " + urlBlockLimit);
            denyList = denyList.subList(0, urlBlockLimit);
        }

        List<DomainFilterRule> rules = new ArrayList<>();
        AppIdentity appIdentity = new AppIdentity("*", null);
        rules.add(new DomainFilterRule(appIdentity, denyList, new ArrayList<>()));

        List<String> superAllow = new ArrayList<>();
        superAllow.add("*");
        List<AppInfo> appInfos = appDatabase.applicationInfoDao().getWhitelistedApps();
        Log.d(TAG, "Whitelisted apps size: " + appInfos.size());
        for (AppInfo app : appInfos) {
            Log.d(TAG, app.packageName);
            rules.add(new DomainFilterRule(new AppIdentity(app.packageName, null), new ArrayList<>(), superAllow));
        }

        try {
            /* Try to block Port 53
               This is now necessary for Chrome
             */

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

    public void setUrlBlockLimit(int urlBlockLimit) {
        this.urlBlockLimit = urlBlockLimit;
    }

}