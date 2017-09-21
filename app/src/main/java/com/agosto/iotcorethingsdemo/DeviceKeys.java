package com.agosto.iotcorethingsdemo;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;

import javax.security.auth.x500.X500Principal;

/**
 *  Generates public and privates keys for registering with IoT Core
 */
public class DeviceKeys {
    private static final String TAG = "DeviceKeys";
    private static final String ALIAS = "iotcoredemo";
    PrivateKey privateKey;
    PublicKey publicKey;
    private Certificate certificate;

    public DeviceKeys() {
        privateKey = getStoredKey();
        if(privateKey==null) {
            generateKeys();
            getStoredKey();
        }
    }

    private void generateKeys()  {
        KeyPairGenerator kpg = null;
        try {
            kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
            kpg.initialize(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(2048)
                    .setUserAuthenticationRequired(false)
                    .setCertificateSubject(new X500Principal("CN=unused"))
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    .build());
            KeyPair kp = kpg.generateKeyPair();
            publicKey = kp.getPublic();
            privateKey = kp.getPrivate();

        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
    }

    static public void deleteKeys() {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            ks.deleteEntry(ALIAS);
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            e.printStackTrace();
        }
    }

    public PrivateKey getStoredKey() {

        try {
            KeyStore ks = null;
            ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            KeyStore.Entry entry = ks.getEntry(ALIAS, null);
            if (!(entry instanceof KeyStore.PrivateKeyEntry)) {
                Log.w(TAG, "Not an instance of a PrivateKeyEntry");
            } else {
                publicKey = ((KeyStore.PrivateKeyEntry) entry).getCertificate().getPublicKey();
                privateKey = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();
                certificate = ((KeyStore.PrivateKeyEntry) entry).getCertificate();
                return privateKey;
            }
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException | UnrecoverableEntryException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String encodedCertificate() {
        try {
            return Base64.encodeToString(certificate.getEncoded(), Base64.DEFAULT);
        } catch (CertificateEncodingException e) {
            e.printStackTrace();
            return "";
        }
    }

}
