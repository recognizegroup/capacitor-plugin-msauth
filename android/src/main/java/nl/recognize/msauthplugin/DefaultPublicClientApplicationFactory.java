package nl.recognize.msauthplugin;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.microsoft.identity.client.ISingleAccountPublicClientApplication;
import com.microsoft.identity.client.PublicClientApplication;
import com.microsoft.identity.client.exception.MsalException;
import java.io.File;

public class DefaultPublicClientApplicationFactory implements PublicClientApplicationFactory {

    @Override
    public ISingleAccountPublicClientApplication createSingleAccountPublicClientApplication(
        @NonNull Context context,
        @Nullable File configFile
    ) throws InterruptedException, MsalException {
        return PublicClientApplication.createSingleAccountPublicClientApplication(context, configFile);
    }
}
