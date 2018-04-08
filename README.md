# How to install ADhell 3?

Step 1: Download Android Studio from https://developer.android.com/studio/install.html

Step 2: Enroll as a developer on https://seap.samsung.com/enrollment. Go to Generate License Keys and generate a key for Enterprise License Key.

Step 3: Plug in your phone and Enable USB Debugging. (Settings > About phone > Software information > Press about 10 times on Build Number until a message “You are now a developer!” appears. > Go back to Settings > Developer Options > Turn on USB Debugging.

Step 4: Download Adhell 3 from https://github.com/neberej/Adhell3/archive/master.zip and extract to Adhell folder.

Step 5: Go to https://seap.samsung.com/sdk/knox-standard-android and download the SDK. Extract and copy the contents of /addon_mdm_5_9_samsung_electronics_24/libs/ into your Adhell/apps/libs folder. (Create libs folder if it doesn't exist).


Step 6: Open Android Studio, click on File > Open and point to Adhell folder. Find or Ctrl + Shift + F to search for `applicationID`. Replace the value of applicationID from `com.` to `com.somthingelse`. **<<--
Make this unique**. Expand libs folder and select edm.jar, license.jar and rc.jar (whole Ctrl to multi-select) and Right Click > Add as library.  Nowclick on the green play button to deploy on your phone.

Step 7: Open app on your phone, give admin priviledges, enter your Knox key (from samsung website) and turn on SABS.

Done!!
