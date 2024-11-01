package com.example.secrethider2

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class RemoteDeviceManagerActivity : AppCompatActivity() {

    private lateinit var startServiceButton: Button
    private lateinit var serviceStatusTextView: TextView
    private var isServiceRunning = false
    private var serverSocket: ServerSocket? = null

    // Define the port number and hardcoded credentials for the service
    private val port = 12345 // High port for this remote service
    private val username = "admin"
    private val password = "admin"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_device_manager)

        // Initialize UI elements
        startServiceButton = findViewById(R.id.startServiceButton)
        serviceStatusTextView = findViewById(R.id.serviceStatusTextView)

        // Set up the button to start or stop the service
        startServiceButton.setOnClickListener {
            if (isServiceRunning) {
                stopRemoteService()
            } else {
                startRemoteService()
            }
        }
    }

    private fun startRemoteService() {
        Log.d("RemoteDeviceManager", "Attempting to start the remote service on port $port")
        isServiceRunning = true
        updateServiceStatus()

        // Notify the user
        Toast.makeText(this, "Starting service on port $port", Toast.LENGTH_SHORT).show()

        // Start the server in a coroutine
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Create the ServerSocket to accept connections
                serverSocket = ServerSocket(port)
                Log.d("RemoteDeviceManager", "Server started on port $port")

                // Keep accepting client connections while the service is running
                while (isServiceRunning) {
                    val clientSocket = serverSocket?.accept() // Blocking call
                    Log.d("RemoteDeviceManager", "Client connected: ${clientSocket?.inetAddress}")

                    // Handle each client connection in a new coroutine
                    clientSocket?.let {
                        handleClientConnection(it)
                    }
                }
            } catch (e: Exception) {
                Log.e("RemoteDeviceManager", "Error while running the server: ${e.message}")
            }
        }
    }

    private fun stopRemoteService() {
        Log.d("RemoteDeviceManager", "Stopping the remote service")
        isServiceRunning = false
        serverSocket?.close() // Close the server socket
        serverSocket = null
        updateServiceStatus()
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("SetTextI18n")
    private fun updateServiceStatus() {
        runOnUiThread {
            if (isServiceRunning) {
                serviceStatusTextView.text = "Service is running"
                serviceStatusTextView.setTextColor(getColor(android.R.color.holo_green_dark))
                startServiceButton.text = "Stop Remote Service"
                Log.d("RemoteDeviceManager", "Service status updated: Running")
            } else {
                serviceStatusTextView.text = "Service is not running"
                serviceStatusTextView.setTextColor(getColor(android.R.color.holo_red_dark))
                startServiceButton.text = "Start Remote Service"
                Log.d("RemoteDeviceManager", "Service status updated: Not Running")
            }
        }
    }

    private fun handleClientConnection(clientSocket: Socket) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val input = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                val output = PrintWriter(clientSocket.getOutputStream(), true)

                // Request and log username and password inputs
                output.println("Enter username:")
                val userInput = input.readLine()
                Log.d("RemoteDeviceManager", "Received username: $userInput")

                output.println("Enter password:")
                val passInput = input.readLine()
                Log.d("RemoteDeviceManager", "Received password for username $userInput")

                if (userInput == username && passInput == password) {
                    Log.d("RemoteDeviceManager", "Access granted for user: $userInput")
                    output.println("Access granted. Welcome to the Mobile Remote Manager.")

                    // Enter command mode after successful login
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
                    Log.d("RemoteDeviceManager", "Access denied for user: $userInput")
                    output.println("Access denied. Invalid credentials.")
                }
                clientSocket.close()
                Log.d("RemoteDeviceManager", "Client connection closed: ${clientSocket.inetAddress}")
            } catch (e: Exception) {
                Log.e("RemoteDeviceManager", "Error handling client connection: ${e.message}")
            }
        }
    }

    /**
     * Executes a system command received from the client.
     * This is a critical point of vulnerability (RCE), allowing arbitrary command execution.
     */
    private fun executeSystemCommand(command: String): String {
        return try {
            // Execute the system command
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            reader.forEachLine { output.append(it).append("\n") }
            process.waitFor() // Wait for the command to complete

            Log.d("RemoteDeviceManager", "Executed command: $command\nOutput: $output")
            output.toString()
        } catch (e: Exception) {
            Log.e("RemoteDeviceManager", "Error executing command: ${e.message}")
            "Error executing command: ${e.message}"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("RemoteDeviceManager", "Activity destroyed, stopping service if running")
        stopRemoteService()
    }
}
