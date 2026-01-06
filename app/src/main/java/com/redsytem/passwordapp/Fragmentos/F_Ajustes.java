package com.redsytem.passwordapp.Fragmentos;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.opencsv.CSVReader;
import com.redsytem.passwordapp.BaseDeDatos.BDHelper;
import com.redsytem.passwordapp.BaseDeDatos.Constants;
import com.redsytem.passwordapp.Login_usuario.Logeo_usuario;
import com.redsytem.passwordapp.MainActivity;
import com.redsytem.passwordapp.Modelo.Password;
import com.redsytem.passwordapp.R;
import com.redsytem.passwordapp.Utilidades.CriptoManager;
import com.redsytem.passwordapp.Utilidades.SesionUsuario;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;

public class F_Ajustes extends Fragment {

    // Vistas Principales
    TextView Eliminar_Todos_Registros, Exportar_Archivo, Importar_Archivo, Cambiar_password_maestra;

    // Vistas del Área Oculta de Importación (CardView)
    CardView Card_Input_Importar;
    EditText Et_Password_Importar;
    Button Btn_Confirmar_Import, Btn_Cancelar_Import;

    // Herramientas y Helpers
    Dialog dialog, dialog_p_m;
    BDHelper bdHelper;
    SharedPreferences sharedPreferences;

    // Constantes
    String ordenarTituloAsc = Constants.C_TITULO + " ASC";
    private static final String SHARED_PREF = "mi_pref";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_C_PASSWORD = "c_password";

    // Nombre del archivo unificado para exportar e importar
    private static final String NOMBRE_ARCHIVO_RESPALDO = "Respaldo_PasswordApp.csv";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Asegurar que no se permitan capturas de pantalla
        if (getActivity() != null) {
            getActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
        View view = inflater.inflate(R.layout.fragment_f__ajustes, container, false);

        InicializarVistas(view);

        bdHelper = new BDHelper(getActivity());
        sharedPreferences = getActivity().getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        dialog = new Dialog(getActivity());
        dialog_p_m = new Dialog(getActivity());

        // --- 1. ELIMINAR TODOS ---
        Eliminar_Todos_Registros.setOnClickListener(v -> Dialog_Eliminar_Registros());

        // --- 2. EXPORTAR (Pide permiso -> Pide Clave -> Exporta) ---
        Exportar_Archivo.setOnClickListener(v -> {
            if (checkPermission()) {
                DialogoPasswordParaExportar();
            } else {
                requestPermission();
            }
        });

        // --- 3. IMPORTAR (Muestra el menú oculto debajo) ---
        Importar_Archivo.setOnClickListener(v -> {
            if (Card_Input_Importar.getVisibility() == View.GONE) {
                Card_Input_Importar.setVisibility(View.VISIBLE);
                Et_Password_Importar.requestFocus();
            } else {
                Card_Input_Importar.setVisibility(View.GONE);
            }
        });

        // --- 3.1 CONFIRMAR IMPORTACIÓN (Botón dentro del CardView) ---
        Btn_Confirmar_Import.setOnClickListener(v -> {
            String pass = Et_Password_Importar.getText().toString().trim();

            if (pass.isEmpty()) {
                Et_Password_Importar.setError("Ingresa la contraseña del archivo");
                return;
            }

            if (checkPermission()) {
                // Alerta de seguridad antes de borrar todo
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle("¡Atención!");
                builder.setMessage("Esta acción BORRARÁ todos los datos actuales de la app y cargará los del archivo CSV. ¿Deseas continuar?");
                builder.setPositiveButton("Sí, Importar", (dialogInterface, i) -> {

                    bdHelper.EliminarTodosRegistros(); // Limpiamos la BD actual
                    ImportarRegistrosSeguros(pass); // Importamos lo nuevo

                    // Reseteamos la UI
                    Et_Password_Importar.setText("");
                    Card_Input_Importar.setVisibility(View.GONE);
                });
                builder.setNegativeButton("Cancelar", null);
                builder.show();
            } else {
                requestPermission();
            }
        });

        // --- 3.2 CANCELAR IMPORTACIÓN ---
        Btn_Cancelar_Import.setOnClickListener(v -> {
            Et_Password_Importar.setText("");
            Card_Input_Importar.setVisibility(View.GONE);
        });

        // --- 4. CAMBIAR PASSWORD MAESTRA ---
        Cambiar_password_maestra.setOnClickListener(v -> CuadroDialogoPasswordMaestra());

        return view;
    }

    private void InicializarVistas(View view) {
        Eliminar_Todos_Registros = view.findViewById(R.id.Eliminar_Todos_Registros);
        Exportar_Archivo = view.findViewById(R.id.Exportar_Archivo);
        Importar_Archivo = view.findViewById(R.id.Importar_Archivo);
        Cambiar_password_maestra = view.findViewById(R.id.Cambiar_password_maestra);

        Card_Input_Importar = view.findViewById(R.id.Card_Input_Importar);
        Et_Password_Importar = view.findViewById(R.id.Et_Password_Importar);
        Btn_Confirmar_Import = view.findViewById(R.id.Btn_Confirmar_Import);
        Btn_Cancelar_Import = view.findViewById(R.id.Btn_Cancelar_Import);
    }

    // ====================================================================================
    // LÓGICA DE EXPORTACIÓN (CORREGIDA Y BLINDADA)
    // ====================================================================================

    private void DialogoPasswordParaExportar() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Proteger Respaldo");
        builder.setMessage("Crea una contraseña para encriptar este archivo. \n\nIMPORTANTE: Si olvidas esta contraseña, no podrás recuperar tus datos.");

        final EditText input = new EditText(getActivity());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(input);

        builder.setPositiveButton("Exportar", (dialog, which) -> {
            String password = input.getText().toString().trim();
            if (password.length() >= 4) {
                ExportarRegistrosSeguros(password);
            } else {
                Toast.makeText(getActivity(), "La contraseña es muy corta (mínimo 4)", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void ExportarRegistrosSeguros(String passwordRespaldo) {
        // 1. Validar que haya datos para exportar
        ArrayList<Password> registroList = bdHelper.ObtenerTodosRegistros(ordenarTituloAsc);
        if (registroList.isEmpty()) {
            Toast.makeText(getActivity(), "No hay registros guardados para exportar.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. Validar Sesión Activa
        String llaveMaestraActual = SesionUsuario.getPasswordMaestra();
        if (llaveMaestraActual == null) {
            Toast.makeText(getActivity(), "Error de Sesión: Por favor cierra sesión y vuelve a ingresar.", Toast.LENGTH_LONG).show();
            return;
        }

        File carpeta = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Password App");
        if (!carpeta.exists()) carpeta.mkdirs();
        File archivo = new File(carpeta, NOMBRE_ARCHIVO_RESPALDO);

        try {
            FileWriter fileWriter = new FileWriter(archivo);
            int contadorExportados = 0;

            for (Password p : registroList) {
                // A. Desencriptar de la BD
                String passPlana = CriptoManager.desencriptar(p.getPassword(), llaveMaestraActual);

                // Si la contraseña falla (por ser antigua o corrupta), ponemos un texto placeholder
                // en lugar de dejar el archivo vacío.
                if (passPlana == null || passPlana.startsWith("Error")) {
                    passPlana = "[ERROR_DESCIFRADO]";
                }

                // B. Re-encriptar para el CSV con la clave del usuario
                String passParaCSV = CriptoManager.encriptar(passPlana, passwordRespaldo);

                // C. Escribir datos escapando caracteres peligrosos
                fileWriter.append(escapeCSV(p.getId())).append(",");
                fileWriter.append(escapeCSV(p.getTitulo())).append(",");
                fileWriter.append(escapeCSV(p.getCuenta())).append(",");
                fileWriter.append(escapeCSV(p.getNombre_usuario())).append(",");

                // Ponemos comillas explícitas a la contraseña encriptada
                fileWriter.append("\"").append(passParaCSV).append("\"").append(",");

                fileWriter.append(escapeCSV(p.getSitio_web())).append(",");
                fileWriter.append(escapeCSV(p.getNota())).append(",");
                fileWriter.append(escapeCSV(p.getImagen())).append(",");
                fileWriter.append(escapeCSV(p.getT_registro())).append(",");
                fileWriter.append(escapeCSV(p.getT_actualiacion()));
                fileWriter.append("\n");

                contadorExportados++;
            }

            fileWriter.flush();
            fileWriter.close();

            if (contadorExportados > 0) {
                Toast.makeText(getActivity(), "Éxito: Se exportaron " + contadorExportados + " registros en Documentos/Password App", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getActivity(), "Advertencia: El archivo se creó pero está vacío.", Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            Toast.makeText(getActivity(), "Error crítico al exportar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Método auxiliar para evitar que comas o saltos de línea rompan el CSV
    private String escapeCSV(String data) {
        if (data == null) return "";
        return data.replace("\"", "'").replace("\n", " ").replace(",", ".");
    }

    // ====================================================================================
    // LÓGICA DE IMPORTACIÓN (CORREGIDA Y BLINDADA)
    // ====================================================================================

    private void ImportarRegistrosSeguros(String passwordRespaldo) {
        File carpeta = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Password App");
        File file = new File(carpeta, NOMBRE_ARCHIVO_RESPALDO);

        String llaveMaestraActual = SesionUsuario.getPasswordMaestra();

        if (file.exists()){
            try {
                CSVReader csvReader = new CSVReader(new FileReader(file.getAbsoluteFile()));
                String [] nextLine;
                int contadorImportados = 0;

                while ((nextLine = csvReader.readNext())!=null){
                    // Verificar estructura mínima del CSV
                    if(nextLine.length > 4) {
                        String titulo = nextLine.length > 1 ? nextLine[1] : "";
                        String cuenta = nextLine.length > 2 ? nextLine[2] : "";
                        String nombre_usuario = nextLine.length > 3 ? nextLine[3] : "";
                        String passDelCSV = nextLine.length > 4 ? nextLine[4] : "";

                        // 1. Desencriptar usando clave del CSV
                        String passPlana = CriptoManager.desencriptar(passDelCSV, passwordRespaldo);

                        // VALIDACIÓN: Si sale "Error", la contraseña es incorrecta
                        if (passPlana == null || passPlana.startsWith("Error")) {
                            Toast.makeText(getActivity(), "Error: La contraseña del archivo es incorrecta. Importación cancelada.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        // 2. Re-encriptar con la llave maestra de la App
                        String passParaBD = CriptoManager.encriptar(passPlana, llaveMaestraActual);

                        String sitio_web = nextLine.length > 5 ? nextLine[5] : "";
                        String nota = nextLine.length > 6 ? nextLine[6] : "";
                        String imagen = nextLine.length > 7 ? nextLine[7] : "";
                        String tiempoR = nextLine.length > 8 ? nextLine[8] : "";
                        String tiempoA = nextLine.length > 9 ? nextLine[9] : "";

                        long id = bdHelper.insertarRegistro(titulo, cuenta, nombre_usuario, passParaBD, sitio_web, nota, imagen, tiempoR, tiempoA);

                        if (id > 0) {
                            contadorImportados++;
                        }
                    }
                }

                if (contadorImportados > 0) {
                    Toast.makeText(getActivity(), "Proceso terminado. Se importaron " + contadorImportados + " registros.", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(getActivity(), MainActivity.class)); // Refrescar pantalla
                } else {
                    Toast.makeText(getActivity(), "El archivo estaba vacío o dañado. No se importó nada.", Toast.LENGTH_LONG).show();
                }

            } catch (Exception e){
                Toast.makeText(getActivity(), "Error al importar: "+e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(getActivity(), "No se encontró el archivo: " + NOMBRE_ARCHIVO_RESPALDO, Toast.LENGTH_LONG).show();
        }
    }

    // ====================================================================================
    // PERMISOS (Android 11+ y Legacy)
    // ====================================================================================

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            int write = ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
            int read = ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE);
            return write == PackageManager.PERMISSION_GRANTED && read == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.setData(Uri.parse(String.format("package:%s", getActivity().getPackageName())));
                startActivityForResult(intent, 2296);
            } catch (Exception e) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, 2296);
            }
        } else {
            SolicitudPermisoLegacy.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    private ActivityResultLauncher<String> SolicitudPermisoLegacy =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) Toast.makeText(getActivity(), "Permiso concedido", Toast.LENGTH_SHORT).show();
                else Toast.makeText(getActivity(), "Permiso denegado", Toast.LENGTH_SHORT).show();
            });


    // ====================================================================================
    // HELPERS UI (Eliminar y Cambiar Password)
    // ====================================================================================

    private void Dialog_Eliminar_Registros() {
        dialog.setContentView(R.layout.cuadro_dialogo_eliminar_todos_registros);
        Button Btn_Si = dialog.findViewById(R.id.Btn_Si);
        Button Btn_Cancelar = dialog.findViewById(R.id.Btn_Cancelar);

        Btn_Si.setOnClickListener(v -> {
            bdHelper.EliminarTodosRegistros();
            startActivity(new Intent(getActivity(), MainActivity.class));
            Toast.makeText(getActivity(), "Registros eliminados", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        Btn_Cancelar.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void CuadroDialogoPasswordMaestra() {
        EditText Password_maestra, Et_nuevo_password_maestra, Et_C_nuevo_password_maestra;
        Button Btn_cambiar, Btn_cancelar;

        String password_maestra_recuperada = sharedPreferences.getString(KEY_PASSWORD, null);
        dialog_p_m.setContentView(R.layout.cuadro_dialogo_password_maestra);

        Password_maestra = dialog_p_m.findViewById(R.id.Password_maestra);
        Et_nuevo_password_maestra = dialog_p_m.findViewById(R.id.Et_nuevo_password_maestra);
        Et_C_nuevo_password_maestra = dialog_p_m.findViewById(R.id.Et_C_nuevo_password_maestra);
        Btn_cambiar = dialog_p_m.findViewById(R.id.Btn_cambiar_password_maestra);
        Btn_cancelar = dialog_p_m.findViewById(R.id.Btn_cancelar_password_maestra);

        Btn_cambiar.setOnClickListener(v -> {
            String nueva = Et_nuevo_password_maestra.getText().toString().trim();
            String confirma = Et_C_nuevo_password_maestra.getText().toString().trim();

            if (nueva.isEmpty() || confirma.isEmpty()) {
                Toast.makeText(getActivity(), "Campos vacíos", Toast.LENGTH_SHORT).show();
            } else if (nueva.length() < 6) {
                Toast.makeText(getActivity(), "Mínimo 6 caracteres", Toast.LENGTH_SHORT).show();
            } else if (!nueva.equals(confirma)) {
                Toast.makeText(getActivity(), "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show();
            } else {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(KEY_PASSWORD, nueva);
                editor.putString(KEY_C_PASSWORD, confirma);
                editor.apply();

                // Actualizar sesión para no tener que reloguear para exportar
                SesionUsuario.setPasswordMaestra(nueva);

                startActivity(new Intent(getActivity(), Logeo_usuario.class));
                getActivity().finish();
                Toast.makeText(getActivity(), "Contraseña maestra actualizada", Toast.LENGTH_SHORT).show();
                dialog_p_m.dismiss();
            }
        });

        Btn_cancelar.setOnClickListener(v -> dialog_p_m.dismiss());

        Password_maestra.setText(password_maestra_recuperada);
        Password_maestra.setEnabled(false);
        Password_maestra.setBackgroundColor(Color.TRANSPARENT);
        Password_maestra.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        dialog_p_m.show();
    }
}