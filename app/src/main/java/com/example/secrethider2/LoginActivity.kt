package com.example.secrethider2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class LoginActivity : AppCompatActivity() {

    private lateinit var editTextUsername: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var buttonLogin: Button
    private lateinit var buttonRegister: Button
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize views
        editTextUsername = findViewById(R.id.editTextUsername)
        editTextPassword = findViewById(R.id.editTextPassword)
        buttonLogin = findViewById(R.id.buttonLogin)
        buttonRegister = findViewById(R.id.buttonRegister)

        // Set up login button click listener
        buttonLogin.setOnClickListener {
            val username = editTextUsername.text.toString()
            val password = editTextPassword.text.toString()

            if (username.isNotEmpty() && password.isNotEmpty()) {
                Log.d("LoginActivity", "Attempting to login with username: $username")
                loginUser(username, password)
            } else {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }

        // Set up register button click listener to navigate to RegisterActivity
        buttonRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loginUser(username: String, password: String) {
        val url = "http://45.33.102.27:5000/login"
        val json = JSONObject()
        json.put("username", username)
        json.put("password", password)

        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        Log.d("LoginActivity", "Sending login request to $url with body: $json")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("LoginActivity", "Login request failed: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@LoginActivity, "Login failed", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    // Extract response body string in the background thread
                    val responseBody = response.body
                    val responseString = responseBody?.string() ?: ""
                    responseBody?.close()

                    // Log response for debugging
                    Log.d("LoginActivity", "Login response body: $responseString")

                    // Parse JSON in the background thread
                    val jsonResponse = JSONObject(responseString)
                    val userId = jsonResponse.optInt("user_id", -1)

                    runOnUiThread {
                        if (userId != -1) {
                            Toast.makeText(this@LoginActivity, "Login successful", Toast.LENGTH_SHORT).show()
                            // Navigate to ProfileActivity
                            val intent = Intent(this@LoginActivity, ProfileActivity::class.java)
                            intent.putExtra("user_id", userId)
                            startActivity(intent)
                            finish() // Close LoginActivity
                        } else {
                            Toast.makeText(this@LoginActivity, "Failed to retrieve user ID", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "Login failed: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}
