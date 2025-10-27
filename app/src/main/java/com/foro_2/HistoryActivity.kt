package com.foro_2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var auth: FirebaseAuth
    private lateinit var tvDebugInfo: TextView
    private var historyListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        container = findViewById(R.id.historyListContainer)
        tvDebugInfo = findViewById(R.id.tvDebugInfo)
        auth = FirebaseAuth.getInstance()

        val user = auth.currentUser
        tvDebugInfo.text = "Usuario ID: ${user?.uid ?: "NO AUTENTICADO"}\nEstado: Iniciando..."

        // Botón para volver al Home
        findViewById<Button>(R.id.btnBackToHome).setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }

        // Botón de prueba para agregar entrada al historial
        findViewById<Button>(R.id.btnAddTestEntry).setOnClickListener {
            addTestHistoryEntry()
        }
    }

    private fun addTestHistoryEntry() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("HistoryActivity", "Agregando entrada de prueba para usuario: ${user.uid}")

        val testEntry = hashMapOf(
            "userId" to user.uid,
            "action" to "ADD",
            "expenseName" to "PRUEBA - Gasto de prueba ${System.currentTimeMillis()}",
            "amount" to 99.99,
            "category" to "Prueba",
            "date" to "26/10/2025",
            "timestamp" to System.currentTimeMillis()
        )

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("history")
            .add(testEntry)
            .addOnSuccessListener { documentReference ->
                Log.d("HistoryActivity", "✅ Entrada de prueba agregada con ID: ${documentReference.id}")
                Toast.makeText(this, "✅ Entrada agregada! ID: ${documentReference.id}", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Log.e("HistoryActivity", "❌ Error al agregar entrada de prueba", e)
                Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onResume() {
        super.onResume()
        setupHistoryListener()
    }

    override fun onPause() {
        super.onPause()
        historyListener?.remove()
    }

    private fun setupHistoryListener() {
        val user = auth.currentUser ?: run {
            tvDebugInfo.text = "ERROR: Usuario no autenticado"
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        tvDebugInfo.text = "Usuario: ${user.uid}\nConfigurando listener..."
        Log.d("HistoryActivity", "Configurando listener para usuario: ${user.uid}")
        
        historyListener = FirestoreUtil.listenToUserHistory(user.uid) { history ->
            Log.d("HistoryActivity", "Historial recibido: ${history.size} entradas")
            tvDebugInfo.text = "Usuario: ${user.uid}\nEntradas recibidas: ${history.size}"
            displayHistory(history.sortedByDescending { it.timestamp })
        }
    }

    private fun displayHistory(history: List<HistoryEntry>) {
        container.removeAllViews()

        if (history.isEmpty()) {
            // Mostrar un mensaje visual en pantalla
            val emptyView = TextView(this).apply {
                text = "📭\n\nNo hay historial disponible\n\nAgregar o eliminar gastos creará entradas en el historial"
                textSize = 18f
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                setPadding(32, 100, 32, 32)
                setTextColor(getColor(android.R.color.white))
            }
            container.addView(emptyView)
            Log.d("HistoryActivity", "Historial vacío")
            return
        }
        
        Log.d("HistoryActivity", "Mostrando ${history.size} entradas")

        for (entry in history) {
            try {
                Log.d("HistoryActivity", "Procesando entrada: ${entry.action} - ${entry.expenseName}")
                
                val view = layoutInflater.inflate(R.layout.history_item, container, false)

                // Configurar el icono y texto según la acción
                val tvAction = view.findViewById<TextView>(R.id.tvAction)
                val tvExpenseName = view.findViewById<TextView>(R.id.tvExpenseName)
                val tvAmount = view.findViewById<TextView>(R.id.tvAmount)
                val tvCategory = view.findViewById<TextView>(R.id.tvCategory)
                val tvDate = view.findViewById<TextView>(R.id.tvDate)
                val tvTimestamp = view.findViewById<TextView>(R.id.tvTimestamp)

                when (entry.action) {
                    "ADD" -> {
                        tvAction.text = "➕ AGREGADO"
                        tvAction.setTextColor(getColor(android.R.color.holo_green_dark))
                    }
                    "DELETE" -> {
                        tvAction.text = "🗑️ ELIMINADO"
                        tvAction.setTextColor(getColor(android.R.color.holo_red_dark))
                    }
                }

                tvExpenseName.text = entry.expenseName
                tvAmount.text = String.format("$%.2f", entry.amount)
                tvCategory.text = "Categoría: ${entry.category}"
                tvDate.text = "Fecha del gasto: ${entry.date}"
                
                // Formatear timestamp a fecha legible
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                val timestampDate = Date(entry.timestamp)
                tvTimestamp.text = "Acción realizada: ${dateFormat.format(timestampDate)}"

                container.addView(view)
                Log.d("HistoryActivity", "Vista agregada exitosamente")
            } catch (e: Exception) {
                Log.e("HistoryActivity", "Error al procesar entrada del historial", e)
            }
        }
    }
}

