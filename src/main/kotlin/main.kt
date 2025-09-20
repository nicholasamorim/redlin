package org.redlin

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import org.redlin.protocol.types.SimpleStringType

fun main() {
    val server = ServerSocket(6379, 1024, InetAddress.getByName("127.0.0.1"))
    val vts = Executors.newVirtualThreadPerTaskExecutor()
    Runtime.getRuntime().addShutdownHook(Thread { vts.shutdown() })

    println("listening on ${server.inetAddress.hostAddress}:${server.localPort}")

    while (true) {
        val socket = server.accept()
        vts.submit { handleClient(socket) }
    }
}

private fun handleClient(s: Socket) {
    s.soTimeout = 0
    s.tcpNoDelay = true

    s.use {
        val input = BufferedInputStream(it.getInputStream())
        val output = BufferedOutputStream(it.getOutputStream())

        while (true) {
            val cmd = readCommand(input) ?: break
            println("Got command $cmd")
            when (cmd.uppercase()) {
                "PING" -> output.write(SimpleStringType.serialize("PONG"))
                else -> writeSimpleError(output, "unknown command '$cmd'")
            }
            output.flush()
        }
    }
}
