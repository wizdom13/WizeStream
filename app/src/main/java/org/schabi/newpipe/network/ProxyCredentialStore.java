package org.schabi.newpipe.network;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores the proxy password encrypted with a key held by Android Keystore. */
public final class ProxyCredentialStore {
    private static final String TAG = ProxyCredentialStore.class.getSimpleName();
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "wizestream_proxy_password_v1";
    private static final String FILE_NAME = "proxy_password_v1";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int FILE_VERSION = 1;
    private static final int MIN_IV_BYTES = 12;
    private static final int MAX_IV_BYTES = 16;
    private static final int MAX_FILE_BYTES = 16 * 1024;

    @NonNull
    private final AtomicFile passwordFile;

    public ProxyCredentialStore(@NonNull final Context context) {
        passwordFile = new AtomicFile(new File(context.getNoBackupFilesDir(), FILE_NAME));
    }

    public synchronized boolean savePassword(@NonNull final String password) {
        if (password.isEmpty()) {
            return clearPassword();
        }
        FileOutputStream output = null;
        try {
            final Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateEncryptionKey());
            final byte[] ciphertext = cipher.doFinal(
                    password.getBytes(StandardCharsets.UTF_8));

            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream data = new DataOutputStream(bytes)) {
                data.writeByte(FILE_VERSION);
                data.writeByte(cipher.getIV().length);
                data.write(cipher.getIV());
                data.write(ciphertext);
            }

            output = passwordFile.startWrite();
            output.write(bytes.toByteArray());
            passwordFile.finishWrite(output);
            return true;
        } catch (final Exception e) {
            if (output != null) {
                passwordFile.failWrite(output);
            }
            Log.e(TAG, "Could not securely store the proxy password", e);
            return false;
        }
    }

    @Nullable
    public synchronized String readPassword() {
        if (!passwordFile.getBaseFile().exists()) {
            return null;
        }
        try {
            final byte[] encrypted = passwordFile.readFully();
            if (encrypted.length == 0 || encrypted.length > MAX_FILE_BYTES) {
                return null;
            }
            try (DataInputStream input = new DataInputStream(
                    new ByteArrayInputStream(encrypted))) {
                if (input.readUnsignedByte() != FILE_VERSION) {
                    return null;
                }
                final int ivLength = input.readUnsignedByte();
                if (ivLength < MIN_IV_BYTES || ivLength > MAX_IV_BYTES) {
                    return null;
                }
                final byte[] iv = new byte[ivLength];
                input.readFully(iv);
                final byte[] ciphertext = new byte[input.available()];
                input.readFully(ciphertext);
                if (ciphertext.length == 0) {
                    return null;
                }

                final Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateEncryptionKey(),
                        new GCMParameterSpec(128, iv));
                return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
            }
        } catch (final Exception e) {
            Log.e(TAG, "Could not decrypt the proxy password", e);
            return null;
        }
    }

    public synchronized boolean hasPassword() {
        return readPassword() != null;
    }

    public synchronized boolean clearPassword() {
        passwordFile.delete();
        return !passwordFile.getBaseFile().exists();
    }

    @NonNull
    private SecretKey getOrCreateEncryptionKey() throws Exception {
        final KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        final SecretKey existingKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        if (existingKey != null) {
            return existingKey;
        }

        final KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
        keyGenerator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return keyGenerator.generateKey();
    }
}
