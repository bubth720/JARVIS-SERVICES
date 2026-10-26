package fr.jarvis.services.fixture;

import android.app.Activity;
import android.content.*;

/**
 * Synthetic fault injection only. Manifest requires this lab APK's signature permission. Never
 * packaged in SERVICES AAR or historical diagnostic application.
 */
public final class FixtureControlReceiver extends BroadcastReceiver {
  public void onReceive(Context context, Intent intent) {
    if ("fr.jarvis.services.fixture.REVOKE".equals(intent.getAction())) {
      android.net.Uri uri = android.net.Uri.parse(intent.getStringExtra("uri"));
      if (!"content".equals(uri.getScheme())
          || !FusionDocumentsProvider.AUTHORITY.equals(uri.getAuthority())
          || !("/tree/root".equals(uri.getPath()) || "/document/source.txt".equals(uri.getPath())))
        throw new SecurityException("FIXTURE_URI_ONLY");
      context.revokeUriPermission(
          uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
      setResultCode(Activity.RESULT_OK);
      return;
    }
    if (!"fr.jarvis.services.fixture.MODE".equals(intent.getAction())) return;
    FusionDocumentsProvider.setMode(intent.getStringExtra("mode"));
    setResultCode(Activity.RESULT_OK);
  }
}
