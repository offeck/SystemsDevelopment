package bgu.spl.net.srv;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class DatabaseHandler {
    
    // Configuration to match the Python server
    private static final String SQL_SERVER_HOST = "127.0.0.1";
    private static final int SQL_SERVER_PORT = 7778; 

    /**
     * Sends an SQL command or query to the Python SQL Server and receives the response.
     * 
     * @param sqlCommand The SQL string to execute (e.g., "INSERT INTO...", "SELECT...").
     * @return The response string from the server (results or confirmation).
     */
    public static String sendSqlRequest(String sqlCommand) {
        String response = "";
        
        try (Socket socket = new Socket(SQL_SERVER_HOST, SQL_SERVER_PORT);
             BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
             BufferedInputStream in = new BufferedInputStream(socket.getInputStream())) {

            // 1. Send the SQL command terminated by a null byte
            // The Python server recv_null_terminated expects this format
            byte[] messageBytes = (sqlCommand + "\0").getBytes(StandardCharsets.UTF_8);
            out.write(messageBytes);
            out.flush();

            // 2. Read the response until the null byte ('\0') is encountered
            StringBuilder sb = new StringBuilder();
            int readByte;
            while ((readByte = in.read()) != -1) {
                if (readByte == '\0') {
                    break; // End of message marker found
                }
                sb.append((char) readByte);
            }
            response = sb.toString();

        } catch (IOException e) {
            // Log error to server console (or use a Logger if available)
            System.err.println("[DatabaseHandler] Connection failed: " + e.getMessage());
            return "error: Database connection failed";
        }

        return response;
    }
}