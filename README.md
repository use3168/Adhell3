# Adhell3
The original Adhell app was marketized as a system wide adblocking app without VPN, but it can do more than just blocking ads. It can also disable system apps and disable app's permissions.<br/>
Adhell is working by utilizing Samsung's Knox SDK. Therefore, it only works on Samsung devices.<br/>
Within this SDK, there is a firewall function which can be used to block domains or define firewall rule to block a particular port for a specific app.<br/>
Adhell is merely a front-end for Samsung Knox SDK which initilizes the Knox with what you define (blocked domains, firewall rules, whitelists, disabled apps, etc). Therefore it doesn't run in the background.<br/>

Adhell was developed by Samsung's developer. After he was forced to remove the code from internet by Samsung, FiendFyre was stepped up by providing the Adhell2. But after a while, it is also discontinued.<br/>
Adhell3 is an extension of previous discontinued Adhell2 app with more additional features.

## Features
- Mobile internet blocker<br/>
Disable internet completely when you are on mobile for specific apps.

- Custom deny firewall rule<br/>
This can be used for example to define a custom firewall rule to block ads for Chrome app on port 53 for all ip addresses:<br/>
    `com.android.chrome|*|53`

- Whitelist URL for a specific app<br/>
When you have a domain that you want to block system wide, but you need this domain on a particular app. Otherwise, the app won't work.<br/>
Instead whitelist-ing this app, you can just whitelist that domain for this app.<br/>
Example: Block the domain `graph.facebook.com` system wide, but allows it for Facebook Messenger so that it can be used for login:<br/>
    `com.facebook.orca|graph.facebook.com`

- Support local host source<br/>
The host file can be located on internal or external storage.<br/>
An example to use host.txt file which is located at internal storage:<br/>
`file:///mnt/sdcard/hosts.txt`

- Show blocked domains<br/>
Show all domains that are blocked which can be used for checking whether particular URL is in the list.


## FAQ
### How Adhell works?
With Knox Standard SDK: https://seap.samsung.com/sdk/knox-standard-android

### Only Samsung?
Yes

### Do I have to install the MyKnox app too?
No

### Does it block on everything or just Samsung's browser?
Blocks ads system wide.

### Which is better, Adhell of Disconnect Pro?
Adhell is free and open source.

### If I have disconnect pro and advantages to running this as well?
 Disconnect Pro and Adhell are using the same underlying Knox Standard SDK. If Disconnect Pro is running Samsung Firewall Adhell doesn't have rights to change Firewall settings and vice versa. So they can't work together at the same time.

### Any noticeable battery drain using this?
No

### Need to be rooted?
No

### What about YouTube native app?
You may see some ads.

### Is it okay to use Adhell with Adguard?
Adguard (without root) will set up a local vpn to route adds to nowhere basically. I think with root it uses a proxy. Either way, it's different than how Adhell does it, so they should work side by side just fine.
