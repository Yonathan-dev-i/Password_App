package com.redsytem.passwordapp.Utilidades;

public class SesionUsuario {
    // Variable estática para guardar la contraseña en memoria mientras la app corre
    private static String passwordMaestra = null;

    public static String getPasswordMaestra() {
        return passwordMaestra;
    }

    public static void setPasswordMaestra(String password) {
        passwordMaestra = password;
    }
}
