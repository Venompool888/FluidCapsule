package io.github.venompool888.fluidcapsule;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

/** Instrumentation APK only. Java keeps this standalone provider independent of the target's Kotlin runtime. */
public final class BackupTestProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/json"; }
    private File file(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null || !name.matches("backup-test-[a-zA-Z0-9-]+\\.json")) throw new IllegalArgumentException();
        return new File(getContext().getCacheDir(), name);
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.parseMode(mode));
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
    @Override public int delete(Uri uri, String selection, String[] args) { return file(uri).delete() ? 1 : 0; }
}
