package dev.rankis.openime;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Synthetic cross-UID fixture, independent of the target APK's runtime. */
public final class MediaFixtureProvider extends ContentProvider {
    private final Map<String, File> created = new HashMap<>();
    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        String owner = getContext().getPackageName();
        String caller = owner.substring(0, owner.length() - ".test".length());
        String[] packages = getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (packages == null || !Arrays.asList(packages).contains(caller)) throw new SecurityException();
        Bundle result = new Bundle();
        if ("create".equals(method)) {
            byte[] bytes = extras.getByteArray("audio");
            if (bytes == null || bytes.length > 65536) throw new IllegalArgumentException();
            File directory = new File(getContext().getExternalMediaDirs()[0], "openime-probe-" + System.nanoTime());
            if (!directory.mkdirs()) throw new IllegalStateException();
            try {
                new File(directory, ".nomedia").createNewFile();
                try (FileOutputStream output = new FileOutputStream(new File(directory, "voice.ogg"))) { output.write(bytes); }
            } catch (IOException error) { throw new IllegalStateException(error); }
            created.put(directory.getName(), directory);
            result.putString("relative", "Android/media/" + owner + "/" + directory.getName());
            result.putInt("ownerUid", android.os.Process.myUid());
        } else if ("cleanup".equals(method)) {
            File directory = created.remove(arg);
            if (directory != null) {
                File[] files = directory.listFiles();
                if (files != null) for (File file : files) file.delete();
                directory.delete();
            }
        } else throw new IllegalArgumentException();
        return result;
    }
    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
