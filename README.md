# Capacitor Plugin MS auth (iOS only)
This plugin contains an implementation of MSAL for Capacitor. At this moment, only iOS is supported, but this could change in the future.

## Installation
* `yarn add @recognizebv/capacitor-plugin-msauth`
* `npx cap sync`
* Create an app registration
* In the app registration, go to Authentication, and then Add platform, and then iOS/macOS
* You will be asked for a bundle identifier, which you can find in Xcode (under the General tab of your project)
* (iOS) Add a new keychain group to your project Signing & Capabilities. The keychain group should be `com.microsoft.adalcache`
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

## Usage
Usage of the plugin is fairly simple, as it has just two methods: `login` and `logout`.

### Login
```typescript
import {Plugins} from '@capacitor/core';

const {MsAuthPlugin} = Plugins;

const result = await MsAuthPlugin.login({
    clientId: '<client id>',
    tenant: '<tenant, defaults to common>',
    scopes: '<scopes, defaults to no scopes>',
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
});
```
