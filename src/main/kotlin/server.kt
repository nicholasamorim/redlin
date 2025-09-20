package org.redlin

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

fun spawnRedisServer(port: Int = 6379, backlog: Int = 1024, host: String = "127.0.0.1") {
    val server = ServerSocket(port, backlog, InetAddress.getByName(host))
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
            val req = readRequest(input) ?: break
            println("Got command ${req.command} args=${req.args}")
            CommandManager.process(req, output)
            output.flush()
        }
    }
}
