package com.example.secrethider2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class ProfileActivity : AppCompatActivity() {

    private lateinit var textViewSecret: TextView
    private lateinit var buttonLogout: Button
    private val client = OkHttpClient()
    private var userId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Initialize views
        textViewSecret = findViewById(R.id.textViewSecret)
        buttonLogout = findViewById(R.id.buttonLogout)

        // Retrieve user_id passed from LoginActivity
        userId = intent.getIntExtra("user_id", -1)

        // Fetch the user's secret if the user_id is valid
        if (userId != -1) {
            fetchUserSecret(userId)
        } else {
            Toast.makeText(this, "Invalid User ID", Toast.LENGTH_SHORT).show()
        }

        // Logout button listener to go back to login screen
        buttonLogout.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun fetchUserSecret(userId: Int) {
        val url = "http://45.33.102.27:5000/profile?user_id=$userId"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@ProfileActivity, "Failed to load secret", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        // Assign response.body() to a variable to comply with the new usage
                        val responseBody = response.body
                        val responseString = responseBody?.string() ?: ""
                        responseBody?.close() // Close the body after use

                        val jsonResponse = JSONObject(responseString)
                        val secret = jsonResponse.optString("secret", "No secret found")

                        // Display the secret in textViewSecret
                        textViewSecret.text = secret
                    } else {
                        Toast.makeText(this@ProfileActivity, "Failed to load secret: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

        })
    }
}