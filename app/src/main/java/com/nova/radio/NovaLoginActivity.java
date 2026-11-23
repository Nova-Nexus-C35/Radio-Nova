package com.nova.radio;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Required sign-in gate for Radio Nova's Internet features. Accounts are created in Nova Apps. */
public final class NovaLoginActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private EditText email;
    private EditText password;
    private TextView status;
    private Button loginButton;
    private Button registerButton;
    private boolean english;

    private String t(String es, String en) { return english ? en : es; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        english = getIntent().getBooleanExtra("english", false);
        buildInterface();
        setBusy(true, t("Comprobando tu cuenta NOVA…", "Checking your NOVA account…"));
        worker.execute(() -> {
            try {
                NovaAccountSession.ensureSession(this);
                runOnUiThread(this::finishSuccess);
            } catch (Exception error) {
                runOnUiThread(() -> setBusy(false,
                        t("Para usar la radio por Internet necesitas una cuenta registrada en Nova Apps. Si ya tienes una cuenta, inicia sesión aquí.", "Internet radio requires an account registered in Nova Apps. If you already have an account, sign in here.")));
            }
        });
    }

    private void buildInterface() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(34), dp(24), dp(28));
        root.setBackgroundColor(Color.rgb(9, 17, 35));

        TextView title = new TextView(this);
        title.setText("RADIO NOVA");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title, params());

        TextView subtitle = new TextView(this);
        subtitle.setText(t("INICIA SESIÓN CON TU CUENTA NOVA", "SIGN IN WITH YOUR NOVA ACCOUNT"));
        subtitle.setTextColor(Color.rgb(70, 205, 230));
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(8), 0, dp(20));
        root.addView(subtitle, params());

        TextView explanation = new TextView(this);
        explanation.setText(t("El modo terrestre, tus favoritos y las listas guardadas siguen disponibles sin cuenta. Para buscar y reproducir emisoras por Internet debes iniciar sesión con la misma cuenta registrada en Nova Apps.", "Terrestrial radio, favorites and saved playlists remain available without an account. To search and play Internet stations, sign in with the account registered in Nova Apps."));
        explanation.setTextColor(Color.LTGRAY);
        explanation.setTextSize(14);
        root.addView(explanation, params());

        email = new EditText(this);
        email.setSingleLine(true);
        email.setHint(t("Correo electrónico de Nova", "Nova email address"));
        email.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        root.addView(email, params());

        password = new EditText(this);
        password.setSingleLine(true);
        password.setHint(t("Contraseña", "Password"));
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(password, params());

        loginButton = new Button(this);
        loginButton.setText(t("INICIAR SESIÓN", "SIGN IN"));
        loginButton.setOnClickListener(view -> signIn());
        root.addView(loginButton, params());

        registerButton = new Button(this);
        registerButton.setText(t("REGISTRARME EN NOVA APPS", "CREATE A NOVA APPS ACCOUNT"));
        registerButton.setOnClickListener(view -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://cumba35.github.io/Nova-APPS/")));
            } catch (Exception error) {
                Toast.makeText(this, t("No se pudo abrir Nova Apps.", "Could not open Nova Apps."), Toast.LENGTH_LONG).show();
            }
        });
        root.addView(registerButton, params());

        Button cancel = new Button(this);
        cancel.setText(t("VOLVER AL MODO LOCAL", "RETURN TO LOCAL MODE"));
        cancel.setOnClickListener(view -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        root.addView(cancel, params());

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(13);
        status.setPadding(0, dp(16), 0, 0);
        root.addView(status, params());

        TextView privacy = new TextView(this);
        privacy.setText(t("La contraseña solo se envía a Nova Auth para verificar tu acceso. Los tokens de sesión se guardan cifrados mediante Android Keystore. Radio Nova no crea una cuenta separada.", "Your password is sent only to Nova Auth for sign-in. Session tokens are encrypted with Android Keystore. Radio Nova does not create a separate account."));
        privacy.setTextColor(Color.LTGRAY);
        privacy.setTextSize(12);
        privacy.setPadding(0, dp(20), 0, 0);
        root.addView(privacy, params());

        scroll.addView(root);
        setContentView(scroll);
    }

    private void signIn() {
        String address = email.getText().toString().trim();
        String secret = password.getText().toString();
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches() || secret.isEmpty()) {
            setBusy(false, t("Escribe el correo y la contraseña de tu cuenta Nova.", "Enter your Nova account email and password."));
            return;
        }
        setBusy(true, t("Verificando tu cuenta con Nova…", "Verifying your Nova account…"));
        worker.execute(() -> {
            try {
                NovaAccountSession.signIn(this, address, secret);
                runOnUiThread(this::finishSuccess);
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? t("No se pudo iniciar sesión. Comprueba tus datos y la conexión.", "Could not sign in. Check your details and connection.")
                        : error.getMessage();
                runOnUiThread(() -> setBusy(false, message));
            }
        });
    }

    private void finishSuccess() {
        setResult(RESULT_OK);
        finish();
    }

    private void setBusy(boolean busy, String message) {
        if (status != null) status.setText(message);
        if (loginButton != null) loginButton.setEnabled(!busy);
        if (registerButton != null) registerButton.setEnabled(!busy);
        if (email != null) email.setEnabled(!busy);
        if (password != null) password.setEnabled(!busy);
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
