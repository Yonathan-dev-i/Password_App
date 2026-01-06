package com.redsytem.passwordapp.OpcionesPassword;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.redsytem.passwordapp.BaseDeDatos.BDHelper;
import com.redsytem.passwordapp.MainActivity;
import com.redsytem.passwordapp.R;

import java.security.SecureRandom;

public class Agregar_Actualizar_Registro extends AppCompatActivity {

    // Vistas existentes
    EditText EtTitulo, EtCuenta, EtNombreUsuario, EtPassword, EtSitioWeb, EtNota;
    ImageView Imagen;
    Button Btn_Adjuntar_Imagen;

    // --- NUEVAS VISTAS PARA GENERADOR Y FORTALEZA ---
    ImageButton btnGenerar;
    TextView txtFortaleza;
    ProgressBar progressFortaleza;
    // ------------------------------------------------

    String id, titulo, cuenta, nombre_usuario, password, sitio_web, nota, tiempo_registro, tiempo_actualizacion;
    private boolean MODO_EDICION = false;

    private BDHelper bdHelper;

    Uri imagenUri = null;

    ImageView Iv_imagen_eliminar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE , WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_agregar_password);
        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setTitle("");

        InicializarVariables();
        ObtenerInformacion();

        // Listener para adjuntar imagen (Cámara)
        Btn_Adjuntar_Imagen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(getApplicationContext(),
                        Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
                    TomarFotografia();
                }else {
                    SolicitudPermisoCamara.launch(Manifest.permission.CAMERA);
                }

            }
        });

        // --- NUEVO: LISTENER PARA GENERAR CONTRASEÑA ---
        btnGenerar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String nuevaPass = generarPasswordSegura(12); // Genera password de 12 caracteres
                EtPassword.setText(nuevaPass);
                EtPassword.setSelection(nuevaPass.length()); // Mueve el cursor al final
            }
        });

        // --- NUEVO: LISTENER PARA ANALIZAR FORTALEZA EN TIEMPO REAL ---
        EtPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                analizarFortaleza(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }



    private void InicializarVariables(){
        EtTitulo = findViewById(R.id.EtTitulo);
        EtCuenta = findViewById(R.id.EtCuenta);
        EtNombreUsuario = findViewById(R.id.EtNombreUsuario);
        EtPassword = findViewById(R.id.EtPassword);
        EtSitioWeb = findViewById(R.id.EtSitioWeb);
        EtNota = findViewById(R.id.EtNota);

        Imagen = findViewById(R.id.Imagen);
        Btn_Adjuntar_Imagen = findViewById(R.id.Btn_Adjuntar_Imagen);
        Iv_imagen_eliminar = findViewById(R.id.Iv_eliminar_imagen);

        // --- INICIALIZAR NUEVAS VISTAS ---
        btnGenerar = findViewById(R.id.btn_generar_pass);
        txtFortaleza = findViewById(R.id.txt_fortaleza);
        progressFortaleza = findViewById(R.id.progress_fortaleza);
        // ---------------------------------

        bdHelper = new BDHelper(this);
    }

    private void ObtenerInformacion(){
        Intent intent = getIntent();
        MODO_EDICION = intent.getBooleanExtra("MODO_EDICION", false);

        if (MODO_EDICION){
            //Verdadero
            id = intent.getStringExtra("ID");
            titulo = intent.getStringExtra("TITULO");
            cuenta = intent.getStringExtra("CUENTA");
            nombre_usuario = intent.getStringExtra("NOMBRE_USUARIO");
            password = intent.getStringExtra("PASSWORD");
            sitio_web = intent.getStringExtra("SITIO_WEB");
            nota = intent.getStringExtra("NOTA");

            // Verificación segura de null para la imagen
            String imgString = intent.getStringExtra("IMAGEN");
            if (imgString != null) {
                imagenUri = Uri.parse(imgString);
            }

            tiempo_registro = intent.getStringExtra("T_REGISTRO");
            tiempo_actualizacion = intent.getStringExtra("T_ACTUALIZACION");

            /*Seteamos la información recuperada en las vistas*/
            EtTitulo.setText(titulo);
            EtCuenta.setText(cuenta);
            EtNombreUsuario.setText(nombre_usuario);
            EtPassword.setText(password);
            EtSitioWeb.setText(sitio_web);
            EtNota.setText(nota);

            /*Si la imagen no existe*/
            if (imagenUri == null || imagenUri.toString().equals("null")){
                Imagen.setImageResource(R.drawable.imagen);
                Iv_imagen_eliminar.setVisibility(View.GONE); // Ocultar si no hay imagen
            }
            /*Si la imagen existe*/
            else {
                Imagen.setImageURI(imagenUri);
                Iv_imagen_eliminar.setVisibility(View.VISIBLE);
            }

            Iv_imagen_eliminar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    imagenUri = null;
                    Imagen.setImageResource(R.drawable.imagen);
                    Iv_imagen_eliminar.setVisibility(View.GONE); // Ocultar al eliminar
                    Toast.makeText(Agregar_Actualizar_Registro.this, "Imagen eliminada", Toast.LENGTH_SHORT).show();
                }
            });

        }
        else {
            //Falso, se agrega un nuevo registro
        }
    }

    private void Agregar_Actualizar_R(){
        /*Obtener datos de entrada*/
        titulo = EtTitulo.getText().toString().trim();
        cuenta = EtCuenta.getText().toString().trim();
        nombre_usuario = EtNombreUsuario.getText().toString().trim();
        password = EtPassword.getText().toString().trim();
        sitio_web = EtSitioWeb.getText().toString().trim();
        nota = EtNota.getText().toString().trim();

        if (MODO_EDICION){
            //Actualizar el registro
            /*Tiempo del dispositivo*/
            String tiempo_actual = ""+ System.currentTimeMillis();
            bdHelper.actualizarRegistro(
                    ""+ id,
                    ""+ titulo,
                    ""+ cuenta,
                    ""+ nombre_usuario,
                    ""+ password,
                    ""+ sitio_web,
                    ""+ nota,
                    ""+ imagenUri,
                    ""+ tiempo_registro,
                    ""+ tiempo_actual
            );

            Toast.makeText(this, "Actualizado con éxito", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(Agregar_Actualizar_Registro.this, MainActivity.class));
            finish();

        }

        else {
            //Agregar un nuevo registro
            if (!titulo.equals("")){
                /*Obtener el tiempo del dispositivo*/
                String tiempo = ""+System.currentTimeMillis();
                long id = bdHelper.insertarRegistro(
                        "" + titulo,
                        "" + cuenta,
                        "" + nombre_usuario,
                        ""+ password,
                        ""+ sitio_web,
                        ""+ nota,
                        "" + imagenUri,
                        "" + tiempo,
                        "" + tiempo
                );

                Toast.makeText(this, "Se ha guardado con éxito: ", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(Agregar_Actualizar_Registro.this, MainActivity.class));
                finish();
            }
            else {
                EtTitulo.setError("Campo obligatorio");
                EtTitulo.setFocusable(true);
            }
        }

    }

    // --- MÉTODOS LÓGICOS NUEVOS ---

    private String generarPasswordSegura(int longitud) {
        String CARACTERES = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+=<>";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(longitud);

        for (int i = 0; i < longitud; i++) {
            int index = random.nextInt(CARACTERES.length());
            sb.append(CARACTERES.charAt(index));
        }
        return sb.toString();
    }

    private void analizarFortaleza(String password) {
        int puntuacion = 0;

        // Reglas básicas
        if (password.length() >= 8) puntuacion += 20;
        if (password.length() >= 12) puntuacion += 20;
        if (password.matches("(?=.*[0-9]).*")) puntuacion += 20; // Tiene números
        if (password.matches("(?=.*[a-z]).*")) puntuacion += 15; // Minúsculas
        if (password.matches("(?=.*[A-Z]).*")) puntuacion += 15; // Mayúsculas
        if (password.matches("(?=.*[@#$%^&+=!]).*")) puntuacion += 10; // Símbolos

        // Actualizar UI
        progressFortaleza.setProgress(puntuacion);

        if (puntuacion < 40) {
            txtFortaleza.setText("Fortaleza: Débil");
            // Usamos ContextCompat para evitar métodos obsoletos
            txtFortaleza.setTextColor(ContextCompat.getColor(this, R.color.rojo_error));
        } else if (puntuacion < 70) {
            txtFortaleza.setText("Fortaleza: Media");
            txtFortaleza.setTextColor(ContextCompat.getColor(this, R.color.amarillo_alerta));
        } else {
            txtFortaleza.setText("Fortaleza: Fuerte");
            txtFortaleza.setTextColor(ContextCompat.getColor(this, R.color.verde_exito));
        }
    }
    // -----------------------------


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_guardar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.Guardar_Password){
            Agregar_Actualizar_R();
        }
        return super.onOptionsItemSelected(item);
    }

    private void TomarFotografia() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "Nueva imagen");
        values.put(MediaStore.Images.Media.DESCRIPTION, "Descripción");
        imagenUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imagenUri);
        camaraAcivityResultLauncher.launch(intent);

    }

    private ActivityResultLauncher<Intent> camaraAcivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK){
                        Imagen.setImageURI(imagenUri);
                        Iv_imagen_eliminar.setVisibility(View.VISIBLE); // Mostrar icono borrar al tomar foto
                    }
                    else {
                        Toast.makeText(Agregar_Actualizar_Registro.this, "Cancelado por el usuario", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    private  ActivityResultLauncher<String> SolicitudPermisoCamara =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), Concede_permiso ->{
                if (Concede_permiso){
                    TomarFotografia();
                }else {
                    Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show();
                }
            });
}