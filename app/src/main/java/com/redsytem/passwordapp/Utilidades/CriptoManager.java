package com.redsytem.passwordapp.Utilidades;

import android.util.Base64;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class CriptoManager {

    private static final int KEY_SIZE = 256;
    private static final int ITERATIONS = 10000;
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS7Padding";
    private static final String SALT = "S@L_S3gur4_P@r@_Tu_App_2025";

    private static SecretKey generarClave(String passwordMaestra) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec spec = new PBEKeySpec(passwordMaestra.toCharArray(), SALT.getBytes(), ITERATIONS, KEY_SIZE);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), ALGORITHM);
    }

    // --- CORRECCIÓN AQUÍ (Base64.NO_WRAP) ---
    public static String encriptar(String data, String passwordMaestra) {
        try {
            SecretKey key = generarClave(passwordMaestra);
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);

            byte[] encrypted = cipher.doFinal(data.getBytes("UTF-8"));

            // CAMBIO IMPORTANTE: Usar NO_WRAP para evitar saltos de línea en el CSV
            String ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP).trim();
            String encryptedBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP).trim();

            return ivBase64 + "]" + encryptedBase64;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String desencriptar(String dataCifrada, String passwordMaestra) {
        try {
            String[] partes = dataCifrada.split("]");
            if (partes.length < 2) return "Error de datos";

            // CAMBIO IMPORTANTE: Usar NO_WRAP al decodificar también
            byte[] iv = Base64.decode(partes[0], Base64.NO_WRAP);
            byte[] encryptedData = Base64.decode(partes[1], Base64.NO_WRAP);

            SecretKey key = generarClave(passwordMaestra);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);

            return new String(cipher.doFinal(encryptedData), "UTF-8");
        } catch (Exception e) {
            return "Error o Clave Incorrecta";
        }
    }
}