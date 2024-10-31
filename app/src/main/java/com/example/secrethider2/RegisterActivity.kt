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

class RegisterActivity : AppCompatActivity() {

    private lateinit var editTextUsername: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var editTextSecret: EditText
    private lateinit var buttonRegister: Button
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize views
        editTextUsername = findViewById(R.id.editTextUsername)
        editTextPassword = findViewById(R.id.editTextPassword)
        editTextSecret = findViewById(R.id.editTextSecret)
        buttonRegister = findViewById(R.id.buttonRegister)

        // Set up register button click listener
        buttonRegister.setOnClickListener {
            val username = editTextUsername.text.toString()
            val password = editTextPassword.text.toString()
            val secret = editTextSecret.text.toString()

            if (username.isNotEmpty() && password.isNotEmpty() && secret.isNotEmpty()) {
                Log.d("RegisterActivity", "Attempting to register user with username: $username, secret: $secret")
                registerUser(username, password, secret)
            } else {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                Log.d("RegisterActivity", "Empty fields detected")
            }
        }
    }

    private fun registerUser(username: String, password: String, secret: String) {
        val url = "http://45.33.102.27:5000/register"
        val json = JSONObject()
        json.put("username", username)
        json.put("password", password)
        json.put("secret", secret)

        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        Log.d("RegisterActivity", "Sending request to $url with body: $json")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("RegisterActivity", "Registration request failed", e)
                runOnUiThread {
                    Toast.makeText(this@RegisterActivity, "Registration failed", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    // Log success
                    Log.d("RegisterActivity", "Registration successful. Redirecting to login page.")

                    runOnUiThread {
                        Toast.makeText(this@RegisterActivity, "Registration successful", Toast.LENGTH_SHORT).show()
                        // Redirect to LoginActivity after successful registration
                        val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
                        startActivity(intent)
                        finish() // Close RegisterActivity
                    }
                } else {
                    runOnUiThread {
                        Log.w("RegisterActivity", "Registration failed with message: ${response.message}")
                        Toast.makeText(this@RegisterActivity, "Registration failed: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

        })
    }
}
