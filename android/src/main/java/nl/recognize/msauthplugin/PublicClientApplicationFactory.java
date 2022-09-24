package nl.recognize.msauthplugin;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.microsoft.identity.client.ISingleAccountPublicClientApplication;
import com.microsoft.identity.client.exception.MsalException;
import java.io.File;

public interface PublicClientApplicationFactory {
    ISingleAccountPublicClientApplication createSingleAccountPublicClientApplication(
        @NonNull final Context context,
        @Nullable final File configFile
    ) throws InterruptedException, MsalException;
}
