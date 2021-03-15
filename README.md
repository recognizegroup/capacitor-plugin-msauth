# Capacitor Plugin MS auth
This plugin contains an implementation of MSAL for Capacitor. 

## Installation
* `yarn add @recognizebv/capacitor-plugin-msauth`
* `npx cap sync`
* Create an app registration
* In the app registration, go to Authentication, and then Add platform, and then iOS/macOS
* You will be asked for a bundle identifier, which you can find in Xcode (under the General tab of your project)
* Do the same for Android. When asked for the package name, use the name defined in `AndroidManifest.xml`.
* In the Signature section, generate a hash for your key. You will need this key hash later.
* (iOS) Add a new keychain group to your project's Signing & Capabilities. The keychain group should be `com.microsoft.adalcache`
* (iOS) Configure URL-schemes by adding the following to your `Info.plist` file:
```
<key>CFBundleURLTypes</key>
<array>
    <dict>
        <key>CFBundleURLSchemes</key>
        <array>
            <string>msauth.$(PRODUCT_BUNDLE_IDENTIFIER)</string>
        </array>
    </dict>
</array>
<key>LSApplicationQueriesSchemes</key>
<array>
    <string>msauthv2</string>
    <string>msauthv3</string>
</array>
```
* (iOS) if your app's AppDelegate already implements a `application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey : Any] = [:]) -> Bool` function, you should add the following code inside this method:
```swift
if MsAuthPlugin.checkAppOpen(url: url, options: options) == true {
    return true
}
```

* (Android) In the `AndroidManifest.xml` file, append the following code within the `<application>` section:
```xml
<activity
        android:name="com.microsoft.identity.client.BrowserTabActivity">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="msauth"
              android:host="<package name>"
              android:path="/<key hash, with prepending slash>" />
    </intent-filter>
</activity>
```

Note that there are two placeholders, one for you package name and one for the key hash.

## Usage
Usage of the plugin is fairly simple, as it has just two methods: `login` and `logout`.

### Login
```typescript
import {Plugins} from '@capacitor/core';

const {MsAuthPlugin} = Plugins;

const result = await MsAuthPlugin.login({
    clientId: '<client id>',
    tenant: '<tenant, defaults to common>',
    scopes: ['<scopes, defaults to no scopes>'],
    keyHash: '<Android only, the key hash as obtained above>',
});

const accessToken = result.accessToken;
```

### Logout
```typescript
import {Plugins} from '@capacitor/core';

const {MsAuthPlugin} = Plugins;

await MsAuthPlugin.logout({
    clientId: '<client id>',
    tenant: '<tenant, defaults to common>',
    keyHash: '<Android only, the key hash as obtained above>',
});
```
