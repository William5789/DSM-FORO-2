package com.foro_2

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

// FirestoreUtil object for expenses management
object FirestoreUtil {
    private val db = FirebaseFirestore.getInstance()
    private val expensesCollection = db.collection("expenses")
    private val usersCollection = db.collection("users")
    private val historyCollection = db.collection("history")

    // 1. Function to create/update the user document in Firestore
    fun createUserDocument(
        userId: String,
        email: String,
        role: String = "normal", // Default role
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userMap = hashMapOf(
            "email" to email,
            "role" to role
        )

        usersCollection.document(userId)
            .set(userMap, SetOptions.merge()) // Use merge to avoid overwriting existing data
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // 2. Function to get the user's role
    fun getUserRole(userId: String, onSuccess: (String?) -> Unit, onFailure: (Exception) -> Unit) {
        usersCollection.document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val role = document.getString("role")
                    onSuccess(role)
                } else {
                    onSuccess(null) // User document doesn't exist
                }
            }
            .addOnFailureListener { e -> onFailure(e) }
    }


    // Función para escuchar cambios en los gastos del usuario
    fun listenToUserExpenses(userId: String, onExpensesChanged: (List<Expense>) -> Unit): ListenerRegistration {
        return expensesCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("FirestoreUtil", "Listen failed.", error)
                    return@addSnapshotListener
                }

                val expenses = snapshot?.documents?.mapNotNull { doc ->
                    Expense(
                        id = doc.id,
                        userId = doc.getString("userId") ?: "",
                        name = doc.getString("name") ?: "",
                        amount = doc.getDouble("amount") ?: 0.0,
                        category = doc.getString("category") ?: "",
                        date = doc.getString("date") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                } ?: emptyList()

                onExpensesChanged(expenses)
            }
    }

    // Función para agregar un gasto
    fun addExpense(expense: Expense, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val docRef = expensesCollection.document()
        val expenseWithId = expense.copy(id = docRef.id)
        docRef.set(expenseWithId.toMap())
            .addOnSuccessListener {
                // Registrar en el historial
                addToHistory(
                    userId = expense.userId,
                    action = "ADD",
                    expenseName = expense.name,
                    amount = expense.amount,
                    category = expense.category,
                    date = expense.date
                )
                onSuccess()
            }
            .addOnFailureListener { onFailure(it) }
    }

    // Función para eliminar un gasto
    fun deleteExpense(expenseId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        // Primero obtenemos los datos del gasto antes de eliminarlo
        expensesCollection.document(expenseId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val userId = document.getString("userId") ?: ""
                    val name = document.getString("name") ?: ""
                    val amount = document.getDouble("amount") ?: 0.0
                    val category = document.getString("category") ?: ""
                    val date = document.getString("date") ?: ""

                    // Eliminar el gasto
                    expensesCollection.document(expenseId)
                        .delete()
                        .addOnSuccessListener {
                            // Registrar en el historial
                            addToHistory(
                                userId = userId,
                                action = "DELETE",
                                expenseName = name,
                                amount = amount,
                                category = category,
                                date = date
                            )
                            onSuccess()
                        }
                        .addOnFailureListener { onFailure(it) }
                } else {
                    onFailure(Exception("Gasto no encontrado"))
                }
            }
            .addOnFailureListener { onFailure(it) }
    }

    // Función para actualizar un gasto
    fun updateExpense(expense: Expense, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        expensesCollection.document(expense.id)
            .set(expense.toMap())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    // Función para obtener el total mensual de gastos
    fun getMonthlyTotal(userId: String, year: Int, month: Int, onSuccess: (Double) -> Unit, onFailure: (Exception) -> Unit) {
        expensesCollection
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { snapshot ->
                val total = snapshot.documents.mapNotNull { doc ->
                    val date = doc.getString("date") ?: return@mapNotNull null
                    val amount = doc.getDouble("amount") ?: 0.0
                    
                    // Parsear fecha (formato esperado: "dd/MM/yyyy")
                    val parts = date.split("/")
                    if (parts.size == 3) {
                        val expenseMonth = parts[1].toIntOrNull() ?: 0
                        val expenseYear = parts[2].toIntOrNull() ?: 0
                        if (expenseMonth == month && expenseYear == year) amount else null
                    } else null
                }.sum()
                onSuccess(total)
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // Función para obtener gastos filtrados por categoría
    fun getExpensesByCategory(userId: String, category: String, onSuccess: (List<Expense>) -> Unit, onFailure: (Exception) -> Unit) {
        expensesCollection
            .whereEqualTo("userId", userId)
            .whereEqualTo("category", category)
            .get()
            .addOnSuccessListener { snapshot ->
                val expenses = snapshot.documents.mapNotNull { doc ->
                    Expense(
                        id = doc.id,
                        userId = doc.getString("userId") ?: "",
                        name = doc.getString("name") ?: "",
                        amount = doc.getDouble("amount") ?: 0.0,
                        category = doc.getString("category") ?: "",
                        date = doc.getString("date") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                }
                onSuccess(expenses)
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // Función privada para agregar al historial
    private fun addToHistory(
        userId: String,
        action: String,
        expenseName: String,
        amount: Double,
        category: String,
        date: String
    ) {
        Log.d("FirestoreUtil", "Intentando agregar al historial: action=$action, user=$userId, expense=$expenseName")
        
        val historyEntry = HistoryEntry(
            userId = userId,
            action = action,
            expenseName = expenseName,
            amount = amount,
            category = category,
            date = date,
            timestamp = System.currentTimeMillis()
        )
        
        val docRef = historyCollection.document()
        historyEntry.id = docRef.id
        
        Log.d("FirestoreUtil", "Guardando historial con ID: ${docRef.id}")
        
        docRef.set(historyEntry.toMap())
            .addOnSuccessListener {
                Log.d("FirestoreUtil", "✅ Historial guardado exitosamente: ${historyEntry.toMap()}")
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreUtil", "❌ Error al guardar historial: ${e.message}", e)
            }
    }

    // Función para escuchar el historial del usuario
    fun listenToUserHistory(userId: String, onHistoryChanged: (List<HistoryEntry>) -> Unit): ListenerRegistration {
        Log.d("FirestoreUtil", "📡 Configurando listener de historial para userId: $userId")
        
        return historyCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreUtil", "❌ Error en listener de historial: ${error.message}", error)
                    return@addSnapshotListener
                }

                Log.d("FirestoreUtil", "📥 Snapshot recibido, documentos: ${snapshot?.documents?.size ?: 0}")
                
                snapshot?.documents?.forEach { doc ->
                    Log.d("FirestoreUtil", "Documento: ${doc.id} -> ${doc.data}")
                }

                val history = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        HistoryEntry(
                            id = doc.id,
                            userId = doc.getString("userId") ?: "",
                            action = doc.getString("action") ?: "",
                            expenseName = doc.getString("expenseName") ?: "",
                            amount = doc.getDouble("amount") ?: 0.0,
                            category = doc.getString("category") ?: "",
                            date = doc.getString("date") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                    } catch (e: Exception) {
                        Log.e("FirestoreUtil", "Error al parsear documento: ${doc.id}", e)
                        null
                    }
                } ?: emptyList()

                Log.d("FirestoreUtil", "✅ Historial procesado: ${history.size} entradas")
                onHistoryChanged(history)
            }
    }
}