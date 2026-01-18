package bgu.spl.net.impl.stomp;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.DatabaseHandler;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {

    private static final Map<String, String> registeredUsers = new ConcurrentHashMap<>();
    private static final Set<String> loggedInUsers = ConcurrentHashMap.newKeySet();

    private int connectionId;
    private Connections<String> connections;
    private boolean shouldTerminate = false;
    private static AtomicInteger messageIdCounter = new AtomicInteger(0);
    private Map<String, String> subscriptionIdToTopic = new ConcurrentHashMap<>();
    private boolean isConnected = false;

    // You likely need to store the username to log logout events later
    private String loggedInUser; 

    @Override
    public void start(int connectionId, Connections<String> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public void process(String message) {
        StompFrame frame = StompFrame.fromString(message);
        if (frame == null) {
            sendError(frame, "Malformed frame", "Could not parse frame");
            return;
        }

        String command = frame.getCommand();

        if (!isConnected && !command.equals("CONNECT")) {
            sendError(frame, "Not connected", "You must first connect");
            return;
        }

        switch (command) {
            case "CONNECT":
                handleConnect(frame);
                break;
            case "SUBSCRIBE":
                handleSubscribe(frame);
                break;
            case "UNSUBSCRIBE":
                handleUnsubscribe(frame);
                break;
            case "SEND":
                handleSend(frame);
                break;
            case "DISCONNECT":
                handleDisconnect(frame);
                break;
            case "REPORT":
                handleReport(frame);
                break;
            default:
                sendError(frame, "Unknown command", "Command " + command + " not implemented");
        }
    }

    private void handleConnect(StompFrame frame) {
        String login = frame.getHeader("login");
        String passcode = frame.getHeader("passcode");
        String host = frame.getHeader("host");

        if (isConnected) {
            sendError(frame, "Already connected", "Client is already connected");
            return;
        }
        if (!"1.2".equals(frame.getHeader("accept-version"))) {
            sendError(frame, "Version mismatch", "Supported version is 1.2");
            return;
        }
        if (!"stomp.cs.bgu.ac.il".equals(host)) {
            sendError(frame, "Host mismatch", "Supported host is stomp.cs.bgu.ac.il");
            return;
        }
        if (login == null || passcode == null) {
            sendError(frame, "Missing headers", "login and passcode are required");
            return;
        }
        if (loggedInUsers.contains(login)) {
            sendError(frame, "User already logged in", "User already logged in");
            return;
        }

        String existingPassword = registeredUsers.get(login);
        if (existingPassword != null && !existingPassword.equals(passcode)) {
            sendError(frame, "Wrong password", "Wrong password");
            return;
        }

        if (existingPassword == null) {
            registeredUsers.put(login, passcode);
            DatabaseHandler.sendSqlRequest("INSERT OR IGNORE INTO Users (username, password) VALUES ('"
                    + escapeSql(login) + "', '" + escapeSql(passcode) + "')");
            DatabaseHandler.sendSqlRequest("INSERT OR IGNORE INTO UserRegistrations (username, registration_datetime) VALUES ('"
                    + escapeSql(login) + "', datetime('now'))");
        }

        DatabaseHandler.sendSqlRequest("INSERT INTO UserLogins (username, login_datetime) VALUES ('"
                + escapeSql(login) + "', datetime('now'))");
        this.loggedInUser = login;
        loggedInUsers.add(login);

        isConnected = true;

        StompFrame connectedFrame = new StompFrame("CONNECTED");
        connectedFrame.addHeader("version", "1.2");
        connections.send(connectionId, connectedFrame.toString());
        sendReceiptIfNeeded(frame);
    }

    private void handleSubscribe(StompFrame frame) {
        String dest = frame.getHeader("destination");
        String id = frame.getHeader("id");

        if (dest == null || id == null) {
            sendError(frame, "Missing headers", "destination and id are required for SUBSCRIBE");
            return;
        }

        subscriptionIdToTopic.put(id, dest);
        if (connections instanceof ConnectionsImpl) {
            ((ConnectionsImpl<String>) connections).addChannelSubscription(dest, connectionId, id);
        }

        sendReceiptIfNeeded(frame);
    }

    private void handleUnsubscribe(StompFrame frame) {
        String id = frame.getHeader("id");
        if (id == null) {
            sendError(frame, "Missing headers", "id is required for UNSUBSCRIBE");
            return;
        }
        String dest = subscriptionIdToTopic.remove(id);
        if (dest != null) {
            if (connections instanceof ConnectionsImpl) {
                ((ConnectionsImpl<String>) connections).removeChannelSubscription(dest, connectionId);
            }
        }
        sendReceiptIfNeeded(frame);
    }

    private void handleSend(StompFrame frame) {
        String dest = frame.getHeader("destination");
        if (dest == null) {
            sendError(frame, "Missing headers", "destination is required for SEND");
            return;
        }
        if (!subscriptionIdToTopic.containsValue(dest)) {
            sendError(frame, "Not subscribed", "You must subscribe before sending to this destination");
            return;
        }

        // Construct MESSAGE
        StompFrame msgFrame = new StompFrame("MESSAGE");
        msgFrame.addHeader("destination", dest);
        msgFrame.addHeader("message-id", String.valueOf(messageIdCounter.incrementAndGet()));
        msgFrame.setBody(frame.getBody());

        connections.send(dest, msgFrame.toString());
        sendReceiptIfNeeded(frame);

        String filename = frame.getHeader("file");
        if (filename != null && loggedInUser != null) {
            DatabaseHandler.sendSqlRequest("INSERT INTO FileTracking (filename, uploader, upload_datetime, game_channel) VALUES ('"
                    + escapeSql(filename) + "', '" + escapeSql(loggedInUser) + "', datetime('now'), '"
                    + escapeSql(dest) + "')");
        }
    }

    private void handleDisconnect(StompFrame frame) {
        if (frame.getHeader("receipt") == null) {
            sendError(frame, "Missing headers", "receipt is required for DISCONNECT");
            return;
        }
        if (loggedInUser != null) {
             DatabaseHandler.sendSqlRequest("UPDATE UserLogins SET logout_datetime = datetime('now') WHERE username = '"
                     + escapeSql(loggedInUser) + "' AND logout_datetime IS NULL");
             loggedInUsers.remove(loggedInUser);
        }

        sendReceiptIfNeeded(frame);
        shouldTerminate = true;
        connections.disconnect(connectionId);
    }

    private void handleReport(StompFrame frame) {
        DatabaseHandler.printReport();
        sendReceiptIfNeeded(frame);
    }

    private void sendReceiptIfNeeded(StompFrame frame) {
        String receipt = frame.getHeader("receipt");
        if (receipt != null) {
            StompFrame receiptFrame = new StompFrame("RECEIPT");
            receiptFrame.addHeader("receipt-id", receipt);
            connections.send(connectionId, receiptFrame.toString());
        }
    }

    private void sendError(StompFrame frame, String message, String description) {
        StompFrame errorFrame = new StompFrame("ERROR");
        errorFrame.addHeader("message", message);
        if (frame != null) {
            String receipt = frame.getHeader("receipt");
            if (receipt != null) {
                errorFrame.addHeader("receipt-id", receipt);
            }
        }
        errorFrame.setBody("The message:\n-----\n" + description + "\n-----");
        connections.send(connectionId, errorFrame.toString());
        if (loggedInUser != null) {
            loggedInUsers.remove(loggedInUser);
            loggedInUser = null;
        }
        shouldTerminate = true;
        connections.disconnect(connectionId);
    }

    private String escapeSql(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("'", "''");
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }
}
