package nl.recognize.msauthplugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import com.microsoft.identity.client.IAccount;
import com.microsoft.identity.client.IAuthenticationResult;
import com.microsoft.identity.client.ISingleAccountPublicClientApplication;
import com.microsoft.identity.client.exception.MsalException;
import java.io.File;
import java.util.List;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class MsAuthPluginTest {

    public static final String AUTHORITY_URL = "https://www.recognize.nl";
    public static final String KEY_HASH = "cmFuZG9tLWtleS1oYXNoLW9idGFpbmVkLWZyb20tYXp1cmU=";
    public static final String TENANT = "f6785b1e-7ae8-4c41-8b72-d418f03cc1d7";
    public static final String CLIENT_ID = "3892f330-5945-4db6-9167-4d5e644ab840";
    public static final String DOMAIN_HINT = "recognize.nl";
    public static final String ID_TOKEN =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

    @Mock
    Context mockedContext;

    @Mock
    AppCompatActivity mockedActivity;

    @Mock
    PublicClientApplicationFactory publicClientApplicationFactoryMock;

    @Mock
    ISingleAccountPublicClientApplication singleAccountPublicClientApplication;

    MsAuthPlugin plugin;

    @BeforeEach
    void setUp() throws MsalException, InterruptedException {
        reset(mockedContext, mockedActivity, publicClientApplicationFactoryMock, singleAccountPublicClientApplication);

        when(publicClientApplicationFactoryMock.createSingleAccountPublicClientApplication(any(Context.class), any(File.class)))
            .thenReturn(singleAccountPublicClientApplication);

        plugin =
            new MsAuthPlugin(publicClientApplicationFactoryMock) {
                final Context applicationContext = mock(Context.class);

                @Override
                public Context getContext() {
                    when(mockedContext.getApplicationContext()).thenReturn(applicationContext);

                    return mockedContext;
                }

                @Override
                public AppCompatActivity getActivity() {
                    lenient().when(applicationContext.getPackageName()).thenReturn("nl.recognize.project-x");
                    lenient().when(mockedActivity.getApplicationContext()).thenReturn(applicationContext);

                    return mockedActivity;
                }

                @Override
                protected String getLogTag() {
                    return "LogTag";
                }

                @Override
                protected String getAuthorityUrl(ISingleAccountPublicClientApplication context) {
                    return AUTHORITY_URL;
                }
            };
    }

    @Test
    void loginExpectAcquireTokenSilent() throws JSONException, MsalException, InterruptedException {
        // Setup plugin call
        PluginCall pluginCallMock = mock(PluginCall.class);
        initializePluginCallMockWithDefaults(pluginCallMock);
        IAuthenticationResult result = createAuthenticationResult(
            "access-token",
            ID_TOKEN,
            new String[] { "mocked-scope", "openid", "profile" }
        );
        when(
            singleAccountPublicClientApplication.acquireTokenSilent(
                argThat(
                    parameters -> parameters.getScopes().equals(List.of("mocked-scope")) && parameters.getAuthority().equals(AUTHORITY_URL)
                )
            )
        )
            .thenReturn(result);

        ArgumentCaptor<JSObject> jsObjectCaptor = ArgumentCaptor.forClass(JSObject.class);
        doNothing().when(pluginCallMock).resolve(jsObjectCaptor.capture());

        // Run
        plugin.login(pluginCallMock);

        // Verify
        JSObject resolve = jsObjectCaptor.getValue();
        assertEquals("access-token", resolve.getString("accessToken"));
        assertEquals(ID_TOKEN, resolve.getString("idToken"));

        verify(singleAccountPublicClientApplication)
            .acquireTokenSilent(argThat(parameters -> parameters.getAuthority().equals(AUTHORITY_URL)));
    }

    private void initializePluginCallMockWithDefaults(PluginCall pluginCallMock) throws JSONException {
        when(pluginCallMock.getArray("scopes")).thenReturn(new JSArray(new String[] { "mocked-scope" }));
        when(pluginCallMock.getString(any()))
            .thenAnswer(
                (Answer<String>) invocation -> {
                    switch (invocation.getArgument(0).toString()) {
                        case "clientId":
                            return CLIENT_ID;
                        case "domainHint":
                            return DOMAIN_HINT;
                        case "tenant":
                            return TENANT;
                        case "keyHash":
                            return KEY_HASH;
                        case "authorityUrl":
                            return AUTHORITY_URL;
                    }

                    return null;
                }
            );
        when(pluginCallMock.getString("authorityType", AuthorityType.AAD.name())).thenReturn("AAD");
    }

    private IAuthenticationResult createAuthenticationResult(String accessToken, String idToken, String[] scopes) {
        IAccount account = mock(IAccount.class);
        IAuthenticationResult authResult = mock(IAuthenticationResult.class);

        when(account.getIdToken()).thenReturn(idToken);
        when(authResult.getAccount()).thenReturn(account);
        when(authResult.getAccessToken()).thenReturn(accessToken);
        when(authResult.getScope()).thenReturn(scopes);

        return authResult;
    }
}
