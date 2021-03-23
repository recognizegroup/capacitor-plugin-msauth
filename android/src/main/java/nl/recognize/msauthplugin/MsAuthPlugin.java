package nl.recognize.msauthplugin;

import androidx.annotation.NonNull;

import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.microsoft.identity.client.AcquireTokenParameters;
import com.microsoft.identity.client.AuthenticationCallback;
import com.microsoft.identity.client.IAuthenticationResult;
import com.microsoft.identity.client.ICurrentAccountResult;
import com.microsoft.identity.client.ISingleAccountPublicClientApplication;
import com.microsoft.identity.client.Prompt;
import com.microsoft.identity.client.PublicClientApplication;
import com.microsoft.identity.client.exception.MsalException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;

@NativePlugin
public class MsAuthPlugin extends Plugin {

    @PluginMethod
    public void login(final PluginCall call) {
        try {
            ISingleAccountPublicClientApplication context = this.createContextFromPluginCall(call);

            this.acquireTokenSilently(context, call.getArray("scopes").<String>toList(), new TokenResultCallback() {
                @Override
                public void tokenReceived(String accessToken) {
                    if (accessToken != null) {
                        JSObject result = new JSObject();
                        result.put("accessToken", accessToken);
                        call.resolve(result);
                    } else {
                        call.reject("Unable to obtain access token");
                    }
                }
            });
        } catch (Exception exception) {
            exception.printStackTrace();
            call.reject("Unable to fetch access token.");
        }
    }

    @PluginMethod
    public void logout(final PluginCall call) {
        try {
            ISingleAccountPublicClientApplication context = this.createContextFromPluginCall(call);

            if (context.getCurrentAccount() == null) {
                call.reject("Nothing to sign out from.");
            } else {
                context.signOut(new ISingleAccountPublicClientApplication.SignOutCallback() {
                    @Override
                    public void onSignOut() {
                        call.resolve();
                    }

                    @Override
                    public void onError(@NonNull MsalException exception) {
                        exception.printStackTrace();

                        call.reject("Unable to sign out.");
                    }
                });
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            call.reject("Unable to fetch context.");
        }
    }

    private void acquireTokenInteractively(ISingleAccountPublicClientApplication context, List<String> scopes, final TokenResultCallback callback) {
        AcquireTokenParameters.Builder params = new AcquireTokenParameters.Builder()
                .startAuthorizationFromActivity(this.getActivity())
                .withScopes(scopes)
                .withPrompt(Prompt.SELECT_ACCOUNT)
                .withCallback(new AuthenticationCallback() {
                    @Override
                    public void onCancel() {
                        System.out.println("Login cancelled.");
                        callback.tokenReceived(null);
                    }

                    @Override
                    public void onSuccess(IAuthenticationResult authenticationResult) {
                        callback.tokenReceived(authenticationResult.getAccessToken());
                    }

                    @Override
                    public void onError(MsalException e) {
                        e.printStackTrace();
                        callback.tokenReceived(null);
                    }
                });

        context.acquireToken(params.build());
    }

    private void acquireTokenSilently(ISingleAccountPublicClientApplication context, List<String> scopes, final TokenResultCallback callback) throws MsalException, InterruptedException {
        String authority = context.getConfiguration().getDefaultAuthority().getAuthorityURL().toString();

        if (context.getCurrentAccount().getCurrentAccount() == null) {
            this.acquireTokenInteractively(context, scopes, callback);
        } else {
            IAuthenticationResult silentAuthResult = context.acquireTokenSilent(scopes.toArray(new String[0]), authority);

            callback.tokenReceived(silentAuthResult.getAccessToken());
        }
    }

    private ISingleAccountPublicClientApplication createContextFromPluginCall(PluginCall call) throws MsalException, InterruptedException, IOException, JSONException {
        String clientId = call.getString("clientId");
        String tenant = call.getString("tenant");
        String keyHash = call.getString("keyHash");

        if (keyHash == null || keyHash.length() == 0) {
            call.reject("Invalid key hash specified.");
            return null;
        }

        return this.createContext(clientId, tenant, keyHash);
    }

    private ISingleAccountPublicClientApplication createContext(String clientId, String tenant, String keyHash) throws MsalException, InterruptedException, IOException, JSONException {
        String tenantId = (tenant != null ? tenant : "common");
        String authorityUrl =  "https://login.microsoftonline.com/" + tenantId;

        String urlEncodedKeyHash = URLEncoder.encode(keyHash, "UTF-8");
        String redirectUri = "msauth://" + getActivity().getApplicationContext().getPackageName() + "/" + urlEncodedKeyHash;

        JSONObject authorityConfig = new JSONObject();
        authorityConfig.put("type", "AAD");
        authorityConfig.put("authority_url", authorityUrl);
        authorityConfig.put("audience", (new JSONObject())
            .put("type", "AzureADMultipleOrgs")
            .put("tenant_id", tenantId));

        JSONObject configFile = new JSONObject();
        configFile.put("client_id", clientId);
        configFile.put("authorization_user_agent", "DEFAULT");
        configFile.put("redirect_uri", redirectUri);
        configFile.put("broker_redirect_uri_registered", false);
        configFile.put("account_mode", "SINGLE");
        configFile.put("authorities", (new JSONArray()).put(authorityConfig));

        File config = writeJSONObjectConfig(configFile);
        ISingleAccountPublicClientApplication app = PublicClientApplication.createSingleAccountPublicClientApplication(getContext().getApplicationContext(), config);

        if (!config.delete()) {
            System.out.println("Warning! Unable to delete config file.");
        }

        return app;
    }

    private File writeJSONObjectConfig(JSONObject data) throws IOException {
        File config = new File(getActivity().getFilesDir() + "auth_config.json");

        FileWriter writer = new FileWriter(config, false);
        writer.write(data.toString());
        writer.flush();
        writer.close();

        return config;
    }
}
