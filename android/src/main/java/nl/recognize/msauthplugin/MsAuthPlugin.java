package nl.recognize.msauthplugin;

import android.Manifest;
import androidx.annotation.NonNull;
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.microsoft.identity.client.AcquireTokenParameters;
import com.microsoft.identity.client.AuthenticationCallback;
import com.microsoft.identity.client.IAccount;
import com.microsoft.identity.client.IAuthenticationResult;
import com.microsoft.identity.client.ICurrentAccountResult;
import com.microsoft.identity.client.ISingleAccountPublicClientApplication;
import com.microsoft.identity.client.Prompt;
import com.microsoft.identity.client.exception.MsalException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@CapacitorPlugin(
    name = "MsAuthPlugin",
    permissions = { @Permission(alias = "network", strings = { Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.INTERNET }) }
)
public class MsAuthPlugin extends Plugin {

    private final PublicClientApplicationFactory publicClientApplicationFactory;

    public MsAuthPlugin() {
        this(new DefaultPublicClientApplicationFactory());
    }

    public MsAuthPlugin(PublicClientApplicationFactory publicClientApplicationFactory) {
        this.publicClientApplicationFactory = publicClientApplicationFactory;
    }

    @PluginMethod
    public void login(final PluginCall call) {
        try {
            ISingleAccountPublicClientApplication context = this.createContextFromPluginCall(call);

            if (context == null) {
                call.reject("Context was null");
                return;
            }

            this.acquireToken(
                    context,
                    call.getArray("scopes").toList(),
                    tokenResult -> {
                        if (tokenResult != null) {
                            JSObject result = new JSObject();
                            result.put("accessToken", tokenResult.getAccessToken());
                            result.put("idToken", tokenResult.getIdToken());
                            JSONArray scopes = new JSONArray(Arrays.asList(tokenResult.getScopes()));
                            result.put("scopes", scopes);

                            call.resolve(result);
                        } else {
                            call.reject("Unable to obtain access token");
                        }
                    }
                );
        } catch (Exception ex) {
            Logger.error("Unable to login", ex);
            call.reject("Unable to fetch access token.");
        }
    }

    @PluginMethod
    public void logout(final PluginCall call) {
        try {
            ISingleAccountPublicClientApplication context = this.createContextFromPluginCall(call);

            if (context == null) {
                call.reject("Context was null");
                return;
            }

            if (context.getCurrentAccount() == null) {
                call.reject("Nothing to sign out from.");
            } else {
                context.signOut(
                    new ISingleAccountPublicClientApplication.SignOutCallback() {
                        @Override
                        public void onSignOut() {
                            call.resolve();
                        }

                        @Override
                        public void onError(@NonNull MsalException ex) {
                            Logger.error("Error occurred during logout", ex);
                            call.reject("Unable to sign out.");
                        }
                    }
                );
            }
        } catch (Exception ex) {
            Logger.error("Exception occurred during logout", ex);
            call.reject("Unable to fetch context.");
        }
    }

    @PluginMethod
    public void logoutAll(final PluginCall call) {
        logout(call);
    }

    protected String getAuthorityUrl(ISingleAccountPublicClientApplication context) {
        return context.getConfiguration().getDefaultAuthority().getAuthorityURL().toString();
    }

    private void acquireTokenInteractively(
        ISingleAccountPublicClientApplication context,
        List<String> scopes,
        final TokenResultCallback callback
    ) {
        AcquireTokenParameters.Builder params = new AcquireTokenParameters.Builder()
            .startAuthorizationFromActivity(this.getActivity())
            .withScopes(scopes)
            .withPrompt(Prompt.SELECT_ACCOUNT)
            .withCallback(
                new AuthenticationCallback() {
                    @Override
                    public void onCancel() {
                        Logger.info("Login cancelled");
                        callback.tokenReceived(null);
                    }

                    @Override
                    public void onSuccess(IAuthenticationResult authenticationResult) {
                        TokenResult tokenResult = new TokenResult();

                        IAccount account = authenticationResult.getAccount();
                        tokenResult.setAccessToken(authenticationResult.getAccessToken());
                        tokenResult.setIdToken(account.getIdToken());
                        tokenResult.setScopes(authenticationResult.getScope());

                        callback.tokenReceived(tokenResult);
                    }

                    @Override
                    public void onError(MsalException ex) {
                        Logger.error("Unable to acquire token interactively", ex);
                        callback.tokenReceived(null);
                    }
                }
            );

        context.acquireToken(params.build());
    }

    private void acquireToken(ISingleAccountPublicClientApplication context, List<String> scopes, final TokenResultCallback callback)
        throws MsalException, InterruptedException {
        String authority = getAuthorityUrl(context);

        final ICurrentAccountResult ca;
        if ((ca = context.getCurrentAccount()) != null && ca.getCurrentAccount() == null) {
            this.acquireTokenInteractively(context, scopes, callback);
        } else {
            IAuthenticationResult silentAuthResult = context.acquireTokenSilent(scopes.toArray(new String[0]), authority);
            IAccount account = silentAuthResult.getAccount();

            TokenResult tokenResult = new TokenResult();
            tokenResult.setAccessToken(silentAuthResult.getAccessToken());
            tokenResult.setIdToken(account.getIdToken());
            tokenResult.setScopes(silentAuthResult.getScope());

            callback.tokenReceived(tokenResult);
        }
    }

    private ISingleAccountPublicClientApplication createContextFromPluginCall(PluginCall call)
        throws MsalException, InterruptedException, IOException, JSONException {
        String clientId = call.getString("clientId");
        String domainHint = call.getString("domainHint");
        String tenant = call.getString("tenant");
        String keyHash = call.getString("keyHash");
        String authorityTypeString = call.getString("authorityType", AuthorityType.AAD.name());
        String authorityUrl = call.getString("authorityUrl");

        if (keyHash == null || keyHash.length() == 0) {
            call.reject("Invalid key hash specified.");
            return null;
        }

        AuthorityType authorityType;
        if (AuthorityType.AAD.name().equals(authorityTypeString)) {
            authorityType = AuthorityType.AAD;
        } else if (AuthorityType.B2C.name().equals(authorityTypeString)) {
            authorityType = AuthorityType.B2C;
        } else {
            call.reject("Invalid authorityType specified. Only AAD and B2C are supported.");
            return null;
        }

        return this.createContext(clientId, domainHint, tenant, authorityType, authorityUrl, keyHash);
    }

    private ISingleAccountPublicClientApplication createContext(
        String clientId,
        String domainHint,
        String tenant,
        AuthorityType authorityType,
        String customAuthorityUrl,
        String keyHash
    ) throws MsalException, InterruptedException, IOException, JSONException {
        String tenantId = (tenant != null ? tenant : "common");
        String authorityUrl = customAuthorityUrl != null ? customAuthorityUrl : "https://login.microsoftonline.com/" + tenantId;
        String urlEncodedKeyHash = URLEncoder.encode(keyHash, "UTF-8");
        String redirectUri = "msauth://" + getActivity().getApplicationContext().getPackageName() + "/" + urlEncodedKeyHash;

        JSONObject authorityConfig = new JSONObject();

        switch (authorityType) {
            case AAD:
                authorityConfig.put("type", AuthorityType.AAD.name());
                authorityConfig.put("authority_url", authorityUrl);
                authorityConfig.put("audience", (new JSONObject()).put("type", "AzureADMultipleOrgs").put("tenant_id", tenantId));
                break;
            case B2C:
                authorityConfig.put("type", AuthorityType.B2C.name());
                authorityConfig.put("authority_url", authorityUrl);
                authorityConfig.put("default", "true");
                break;
        }

        JSONObject configFile = new JSONObject();
        configFile.put("client_id", clientId);
        configFile.put("domain_hint", domainHint);
        configFile.put("authorization_user_agent", "DEFAULT");
        configFile.put("redirect_uri", redirectUri);
        configFile.put("broker_redirect_uri_registered", false);
        configFile.put("account_mode", "SINGLE");
        configFile.put("authorities", (new JSONArray()).put(authorityConfig));

        File config = writeJSONObjectConfig(configFile);
        ISingleAccountPublicClientApplication app = publicClientApplicationFactory.createSingleAccountPublicClientApplication(
            getContext().getApplicationContext(),
            config
        );

        if (!config.delete()) {
            Logger.warn("Warning! Unable to delete config file.");
        }

        return app;
    }

    private File writeJSONObjectConfig(JSONObject data) throws IOException {
        File config = new File(getActivity().getFilesDir() + "auth_config.json");

        try (FileWriter writer = new FileWriter(config, false)) {
            writer.write(data.toString());
            writer.flush();
        }

        return config;
    }
}
