package com.mantra.biometricauthmorfin;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FingerprintDatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "fingerprint_auth.db";

    private static final int DB_VERSION = 2;

    public static final String TABLE_FINGERPRINTS = "fingerprints";


    public static final String COL_ID = "id";
    public static final String COL_USER_ID = "user_id";
    public static final String COL_USER_NAME = "user_name";
    public static final String COL_IMAGE_PATH = "image_path";
    public static final String COL_TEMPLATE = "template";
    public static final String COL_QUALITY = "quality";
    public static final String COL_NFIQ = "nfiq";
    public static final String COL_CREATED_AT = "created_at";

    public FingerprintDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d("DB", "Creating fingerprints table...");

        String createTable = "CREATE TABLE " + TABLE_FINGERPRINTS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_USER_ID + " TEXT UNIQUE NOT NULL, " +
                COL_USER_NAME + " TEXT, " +
                COL_IMAGE_PATH + " TEXT, " +
                COL_TEMPLATE + " BLOB NOT NULL, " +
                COL_QUALITY + " INTEGER, " +
                COL_NFIQ + " INTEGER, " +
                COL_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP)";

        db.execSQL(createTable);
        Log.d("DB", "Fingerprints table created");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FINGERPRINTS);
        onCreate(db);
    }


    public String getNextUserId() {
        SQLiteDatabase db = this.getReadableDatabase();
        String nextId = "USER_001";
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_FINGERPRINTS, null);
            if (cursor.moveToFirst()) {
                int count = cursor.getInt(0);
                nextId = String.format("USER_%03d", count + 1);
            }
        } catch (Exception e) {
            Log.e("DB", "Error generating ID", e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return nextId;
    }


    public boolean saveFingerprint(String userId, String name, String imagePath, byte[] template, int quality, int nfiq) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        try {
            values.put(COL_USER_ID, userId);
            values.put(COL_USER_NAME, name);
            values.put(COL_IMAGE_PATH, imagePath);
            values.put(COL_TEMPLATE, template);
            values.put(COL_QUALITY, quality);
            values.put(COL_NFIQ, nfiq);

            long result = db.insert(TABLE_FINGERPRINTS, null, values);
            db.close();

            if (result != -1) {
                Log.d("DB", "Saved: " + userId + " (" + name + ")");
                return true;
            } else {
                Log.e("DB", "Failed to save fingerprint");
                return false;
            }
        } catch (Exception e) {
            Log.e("DB", "Error saving fingerprint", e);
            db.close();
            return false;
        }
    }


    public int getUserCount() {
        int count = 0;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_FINGERPRINTS, null);
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
        } catch (Exception e) {
            Log.e("DB", "Error counting users", e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return count;
    }


    public static class UserRecord {
        public String userId;
        public String userName;
        public String imagePath;
        public byte[] template;


        public UserRecord(String id, String name, String path, byte[] temp) {
            this.userId = id;
            this.userName = name;
            this.imagePath = path;
            this.template = temp;
        }


        public UserRecord(String id, String name, String path) {
            this.userId = id;
            this.userName = name;
            this.imagePath = path;
        }
    }

    public List<UserRecord> getAllUsersList() {
        List<UserRecord> users = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            // Changed DESC to ASC here
            cursor = db.rawQuery("SELECT " + COL_USER_ID + ", " + COL_USER_NAME + ", " + COL_IMAGE_PATH +
                    " FROM " + TABLE_FINGERPRINTS + " ORDER BY " + COL_CREATED_AT + " ASC", null);

            if (cursor.moveToFirst()) {
                do {
                    String id = cursor.getString(0);
                    String name = cursor.getString(1);
                    String path = cursor.getString(2);
                    users.add(new UserRecord(id, name, path));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e("DB", "Error fetching user list", e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return users;
    }

    public List<UserRecord> getAllUsersForMatching() {
        List<UserRecord> users = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT " + COL_USER_ID + ", " + COL_USER_NAME + ", " + COL_IMAGE_PATH + ", " + COL_TEMPLATE +
                    " FROM " + TABLE_FINGERPRINTS, null);

            if (cursor.moveToFirst()) {
                do {
                    String id = cursor.getString(0);
                    String name = cursor.getString(1);
                    String path = cursor.getString(2);
                    byte[] template = cursor.getBlob(3);
                    users.add(new UserRecord(id, name, path, template));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e("DB", "Error fetching users for match", e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return users;
    }


    public byte[] getTemplateByUserId(String userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        byte[] template = null;
        Cursor cursor = null;
        try {
            String query = "SELECT " + COL_TEMPLATE + " FROM " + TABLE_FINGERPRINTS + " WHERE " + COL_USER_ID + " = ?";
            cursor = db.rawQuery(query, new String[]{userId});

            if (cursor != null && cursor.moveToFirst()) {
                template = cursor.getBlob(0);
            }
        } catch (Exception e) {
            Log.e("DB", "Error fetching template for user: " + userId, e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return template;
    }


    public void clearDatabase() {
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.delete(TABLE_FINGERPRINTS, null, null);
            db.close();
            Log.d("DB", "Database Table Cleared");
        } catch (Exception e) {
            Log.e("DB", "Error clearing DB", e);
        }
    }

    public void clearSavedFiles(String folderPath) {
        if (folderPath == null) return;
        try {
            File dir = new File(folderPath);
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        file.delete();
                    }
                }
            }
            Log.d("DB", "Files Cleared from: " + folderPath);
        } catch (Exception e) {
            Log.e("DB", "Error clearing files", e);
        }
    }

    public void clearAllData(String folderPath) {
        clearDatabase();
        clearSavedFiles(folderPath);
    }
}
