// kotlin
package com.foro_2

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.foro_2.databinding.ActivityLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import android.widget.Button
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import android.util.Log

class LoginActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001

    private lateinit var binding: ActivityLoginBinding
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializa Firebase explícitamente
        FirebaseApp.initializeApp(this)
        firebaseAuth = FirebaseAuth.getInstance()

        binding.notUserYet.setOnClickListener{
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        // Configuración para el inicio de sesión con Google
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Referencias a los elementos del layout usando binding
        val emailEditText = binding.emailEditText
        val passwordEditText = binding.passwordEditText
        val googleSignInButton = binding.googleSignInButton as Button

        // Acción del botón de login tradicional
        binding.loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (!isValidEmail(email)) {
                Toast.makeText(this, "Correo inválido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                Toast.makeText(this, "La contraseña no puede estar vacía", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validación aprobada, acceso permitido
            firebaseAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("LoginActivity", "Inicio de sesión exitoso")
                    val intent = Intent(this, HomeActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Log.e("LoginActivity", "Error en login", task.exception)
                    Toast.makeText(this, task.exception?.localizedMessage ?: "Error desconocido", Toast.LENGTH_SHORT).show()
                }
            }

        }

        // Acción del botón de Google Sign-In
        googleSignInButton.setOnClickListener {
            // Forzar cierre de sesión para mostrar selector de cuenta
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                startActivityForResult(signInIntent, RC_SIGN_IN)
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken

                if (idToken != null) {
                    firebaseAuthWithGoogle(idToken)
                } else {
                    Toast.makeText(this, "Error: token de Google es nulo", Toast.LENGTH_SHORT).show()
                    Log.e("LoginActivity", "idToken nulo en Google Sign-In")
                }
            } catch (e: ApiException) {
                Toast.makeText(this, "Google Sign-In falló: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("LoginActivity", "Google Sign-In error", e)
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    goToHome()
                } else {
                    Toast.makeText(this, "Error con Firebase: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    Log.e("LoginActivity", "Firebase signInWithCredential failed", task.exception)
                }
            }
    }

    private fun goToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }
}
