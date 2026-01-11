package com.wlritchi.shulkertrims.fabric.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Simple RCON client for sending commands to Minecraft servers.
 *
 * <p>RCON (Remote Console) is a protocol for remotely executing commands on game servers.
 * This implementation supports the standard Source RCON protocol used by Minecraft.
 */
public final class RconClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("RconClient");

    // RCON packet types
    private static final int SERVERDATA_AUTH = 3;
    private static final int SERVERDATA_AUTH_RESPONSE = 2;
    private static final int SERVERDATA_EXECCOMMAND = 2;
    private static final int SERVERDATA_RESPONSE_VALUE = 0;

    private RconClient() {}

    /**
     * Send a command to an RCON server.
     *
     * @param host Server hostname
     * @param port RCON port
     * @param password RCON password
     * @param command Command to execute
     * @return Server response
     * @throws IOException if communication fails
     */
    public static String sendCommand(String host, int port, String password, String command) throws IOException {
        try (Socket socket = new Socket(host, port);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            socket.setSoTimeout(5000);

            // Authenticate
            int authRequestId = 1;
            sendPacket(out, authRequestId, SERVERDATA_AUTH, password);
            RconPacket authResponse = receivePacket(in);

            if (authResponse.requestId == -1) {
                throw new IOException("RCON authentication failed - bad password");
            }

            if (authResponse.requestId != authRequestId) {
                throw new IOException("RCON authentication failed - unexpected request ID: " + authResponse.requestId);
            }

            LOGGER.debug("RCON authenticated successfully");

            // Send command
            int commandRequestId = 2;
            sendPacket(out, commandRequestId, SERVERDATA_EXECCOMMAND, command);
            RconPacket commandResponse = receivePacket(in);

            if (commandResponse.requestId != commandRequestId) {
                throw new IOException("RCON command failed - unexpected request ID: " + commandResponse.requestId);
            }

            return commandResponse.payload;
        }
    }

    private static void sendPacket(DataOutputStream out, int requestId, int type, String payload) throws IOException {
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        // Packet structure: length (4) + requestId (4) + type (4) + payload + null (1) + null (1)
        int packetLength = 4 + 4 + payloadBytes.length + 1 + 1;

        ByteBuffer buffer = ByteBuffer.allocate(4 + packetLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(packetLength);
        buffer.putInt(requestId);
        buffer.putInt(type);
        buffer.put(payloadBytes);
        buffer.put((byte) 0);
        buffer.put((byte) 0);

        out.write(buffer.array());
        out.flush();
    }

    private static RconPacket receivePacket(DataInputStream in) throws IOException {
        // Read length (little-endian)
        byte[] lengthBytes = new byte[4];
        in.readFully(lengthBytes);
        int length = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();

        // Read rest of packet
        byte[] packetData = new byte[length];
        in.readFully(packetData);

        ByteBuffer buffer = ByteBuffer.wrap(packetData).order(ByteOrder.LITTLE_ENDIAN);
        int requestId = buffer.getInt();
        int type = buffer.getInt();

        // Payload is everything except the two trailing null bytes
        int payloadLength = length - 4 - 4 - 2;
        byte[] payloadBytes = new byte[payloadLength];
        buffer.get(payloadBytes);
        String payload = new String(payloadBytes, StandardCharsets.UTF_8);

        return new RconPacket(requestId, type, payload);
    }

    private record RconPacket(int requestId, int type, String payload) {}
}
