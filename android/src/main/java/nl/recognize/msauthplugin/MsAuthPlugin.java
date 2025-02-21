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
import com.microsoft.identity.client.*;
import com.microsoft.identity.client.exception.MsalException;
import com.microsoft.identity.client.exception.MsalUiRequiredException;
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

            Prompt prompt = Prompt.SELECT_ACCOUNT;
            if (call.hasOption("prompt")) {
                switch (call.getString("prompt").toLowerCase()) {
                    case "select_account":
                        prompt = Prompt.SELECT_ACCOUNT;
                        break;
                    case "login":
                        prompt = Prompt.LOGIN;
                        break;
                    case "consent":
                        prompt = Prompt.CONSENT;
                        break;
                    case "none":
                        prompt = Prompt.WHEN_REQUIRED;
                        break;
                    case "create":
                        prompt = Prompt.CREATE;
                        break;
                    default:
                        Logger.warn("Unrecognized prompt option: " + call.getString("prompt"));
                        break;
                }
            }

            this.acquireToken(context, call.getArray("scopes").toList(), prompt, tokenResult -> {
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
                });
        } catch (Exception ex) {
            Logger.error("Unable to login: " + ex.getMessage(), ex);
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

    private void acquireToken(
        ISingleAccountPublicClientApplication context,
        List<String> scopes,
        Prompt prompt,
        final TokenResultCallback callback
    ) throws MsalException, InterruptedException {
        String authority = getAuthorityUrl(context);

        ICurrentAccountResult result = context.getCurrentAccount();
        if (result.getCurrentAccount() != null) {
            try {
                Logger.info("Starting silent login flow");
                AcquireTokenSilentParameters.Builder builder = new AcquireTokenSilentParameters.Builder()
                    .withScopes(scopes)
                    .fromAuthority(authority)
                    .forAccount(result.getCurrentAccount());

                AcquireTokenSilentParameters parameters = builder.build();
                IAuthenticationResult silentAuthResult = context.acquireTokenSilent(parameters);
                IAccount account = silentAuthResult.getAccount();

                TokenResult tokenResult = new TokenResult();
                tokenResult.setAccessToken(silentAuthResult.getAccessToken());
                tokenResult.setIdToken(account.getIdToken());
                tokenResult.setScopes(silentAuthResult.getScope());

                callback.tokenReceived(tokenResult);

                return;
            } catch (MsalUiRequiredException ex) {
                Logger.error("Silent login failed", ex);
            }
        }

        Logger.info("Starting interactive login flow");
        AcquireTokenParameters.Builder params = new AcquireTokenParameters.Builder()
            .startAuthorizationFromActivity(this.getActivity())
            .withScopes(scopes)
            .withPrompt(prompt)
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

        if (result.getCurrentAccount() != null) {
            // Set loginHint otherwise MSAL throws an exception because of mismatched account
            params.withLoginHint(result.getCurrentAccount().getUsername());
        }

        context.acquireToken(params.build());
    }

    private ISingleAccountPublicClientApplication createContextFromPluginCall(PluginCall call)
        throws MsalException, InterruptedException, IOException, JSONException {
        String clientId = call.getString("clientId");
        String domainHint = call.getString("domainHint");
        String tenant = call.getString("tenant");
        String keyHash = call.getString("keyHash");
        String authorityTypeString = call.getString("authorityType", AuthorityType.AAD.name());
        String authorityUrl = call.getString("authorityUrl");
        Boolean brokerRedirectUriRegistered = call.getBoolean("brokerRedirectUriRegistered", false);

        if (keyHash == null || keyHash.length() == 0) {
            call.reject("Invalid key hash specified.");
            return null;
        }

        AuthorityType authorityType;
        if (AuthorityType.AAD.name().equals(authorityTypeString)) {
            authorityType = AuthorityType.AAD;
        } else if (AuthorityType.B2C.name().equals(authorityTypeString)) {
            authorityType = AuthorityType.B2C;
        } else if (AuthorityType.CIAM.name().equals(authorityTypeString)) {
            authorityType = AuthorityType.CIAM;
        } else {
            call.reject("Invalid authorityType specified. Only AAD, B2C and CIAM are supported.");
            return null;
        }

        return this.createContext(clientId, domainHint, tenant, authorityType, authorityUrl, keyHash, brokerRedirectUriRegistered);
    }

    private ISingleAccountPublicClientApplication createContext(
        String clientId,
        String domainHint,
        String tenant,
        AuthorityType authorityType,
        String customAuthorityUrl,
        String keyHash,
        Boolean brokerRedirectUriRegistered
    ) throws MsalException, InterruptedException, IOException, JSONException {
        String tenantId = (tenant != null ? tenant : "common");
        String authorityUrl = customAuthorityUrl != null ? customAuthorityUrl : "https://login.microsoftonline.com/" + tenantId;
        String urlEncodedKeyHash = URLEncoder.encode(keyHash, "UTF-8");
        String redirectUri = "msauth://" + getActivity().getApplicationContext().getPackageName() + "/" + urlEncodedKeyHash;

        JSONObject configFile = new JSONObject();
        JSONObject authorityConfig = new JSONObject();

        switch (authorityType) {
            case AAD:
                authorityConfig.put("type", AuthorityType.AAD.name());
                authorityConfig.put("authority_url", authorityUrl);
                authorityConfig.put("audience", (new JSONObject()).put("type", "AzureADMultipleOrgs").put("tenant_id", tenantId));
                configFile.put("broker_redirect_uri_registered", brokerRedirectUriRegistered);
                break;
            case B2C:
                authorityConfig.put("type", AuthorityType.B2C.name());
                authorityConfig.put("authority_url", authorityUrl);
                authorityConfig.put("default", "true");
                break;
            case CIAM:
                authorityConfig.put("type", AuthorityType.CIAM.name()).put("authority_url", authorityUrl);
                break;
        }

        configFile.put("client_id", clientId);
        configFile.put("domain_hint", domainHint);
        configFile.put("authorization_user_agent", "DEFAULT");
        configFile.put("redirect_uri", redirectUri);
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
