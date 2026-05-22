package com.Rixnar.NeverGiveUp;

import android.content.Context;
import android.util.Base64;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class BackupManager {

    private static final String MAGIC = "RIXNAR_SEC";
    private static final int VERSION = 3; 
    private static final String SALT = "rixnar_focus_integrity_2024_v1";

    private final Context context;

    public BackupManager(Context context) {
        this.context = context;
    }

    /**
     * Export all app data to a signed, compressed backup string.
     * Credits are stripped to prevent "rewinding" cheats.
     */
    public String exportToString() throws Exception {
        PersistenceHelper.saveToInternalStorage(context);
        
        JSONObject json = PersistenceHelper.getPrefsAsJson(context);
        
        // SECURITY: Remove credits and active session state
        json.remove("native_credits");
        json.remove("social_unlocked");
        json.remove("social_unlock_expiry");
        json.remove("last_credit_reset_date_string");
        json.remove("last_credit_reset");
        json.remove("onboarding_key_temp");
        json.remove("onboarding_duration_temp");
        
        String serialized = json.toString();
        byte[] compressed = compress(serialized.getBytes("UTF-8"));
        String checksum = calculateChecksum(serialized);
        
        // Format: MAGIC|VERSION|CHECKSUM|COMPRESSED_DATA
        String header = MAGIC + "|" + VERSION + "|" + checksum + "|";
        byte[] headerBytes = header.getBytes("UTF-8");
        byte[] result = new byte[headerBytes.length + compressed.length];
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
        System.arraycopy(compressed, 0, result, headerBytes.length, compressed.length);
        
        return Base64.encodeToString(result, Base64.NO_WRAP);
    }

    /**
     * Import data from a backup string. Returns true if successful.
     */
    public boolean importFromString(String backupData) throws Exception {
        byte[] raw = Base64.decode(backupData, Base64.NO_WRAP);
        
        int p1 = findPipe(raw, 0);
        int p2 = findPipe(raw, p1 + 1);
        int p3 = findPipe(raw, p2 + 1);
        
        if (p1 == -1 || p2 == -1 || p3 == -1) throw new IllegalArgumentException("Invalid Rixnar backup format");
        
        String magic = new String(raw, 0, p1, "UTF-8");
        int version = Integer.parseInt(new String(raw, p1 + 1, p2 - p1 - 1, "UTF-8"));
        String storedChecksum = new String(raw, p2 + 1, p3 - p2 - 1, "UTF-8");
        
        if (!MAGIC.equals(magic)) throw new IllegalArgumentException("Unsupported backup file type");
        
        byte[] compressed = new byte[raw.length - p3 - 1];
        System.arraycopy(raw, p3 + 1, compressed, 0, compressed.length);
        
        byte[] decompressed = decompress(compressed);
        String serialized = new String(decompressed, "UTF-8");
        
        // Verify Integrity
        if (!storedChecksum.equals(calculateChecksum(serialized))) {
            throw new IllegalArgumentException("Backup integrity check failed (Tampered file)");
        }
        
        JSONObject json = new JSONObject(serialized);
        PersistenceHelper.applyJsonToPrefs(context, json);
        PersistenceHelper.saveToInternalStorage(context);
        
        return true;
    }
    
    private int findPipe(byte[] data, int start) {
        for (int i = start; i < data.length; i++) {
            if (data[i] == '|') return i;
        }
        return -1;
    }

    private String calculateChecksum(String data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(SALT.getBytes("UTF-8"));
        md.update(data.getBytes("UTF-8"));
        byte[] hash = md.digest();
        
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            hex.append(String.format("%02x", hash[i]));
        }
        return hex.toString();
    }

    private byte[] compress(byte[] data) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gzos = new GZIPOutputStream(baos);
        gzos.write(data);
        gzos.close();
        return baos.toByteArray();
    }

    private byte[] decompress(byte[] data) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        GZIPInputStream gzis = new GZIPInputStream(bais);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = gzis.read(buffer)) > 0) {
            baos.write(buffer, 0, len);
        }
        gzis.close();
        return baos.toByteArray();
    }

    public boolean isValidBackup(String backupData) {
        try {
            byte[] raw = Base64.decode(backupData, Base64.NO_WRAP);
            String magic = new String(raw, 0, MAGIC.length(), "UTF-8");
            return MAGIC.equals(magic);
        } catch (Exception e) {
            return false;
        }
    }
}
