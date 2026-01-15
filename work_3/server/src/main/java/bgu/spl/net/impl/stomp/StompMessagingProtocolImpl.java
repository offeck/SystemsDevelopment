package bgu.spl.net.impl.stomp;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.Connections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {

    private int connectionId;
    private Connections<String> connections;
    private boolean shouldTerminate = false;
    private static AtomicInteger messageIdCounter = new AtomicInteger(0);
    private Map<String, String> subscriptionIdToTopic = new ConcurrentHashMap<>();
    private boolean isConnected = false;

    @Override
    public void start(int connectionId, Connections<String> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public void process(String message) {
        StompFrame frame = StompFrame.fromString(message);
        if (frame == null) {
            sendError("Malformed frame", "Could not parse frame");
            return;
        }

        String command = frame.getCommand();

        if (!isConnected && !command.equals("CONNECT")) {
            sendError("Not connected", "You must first connect");
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
            default:
                sendError("Unknown command", "Command " + command + " not implemented");
        }
    }

    private void handleConnect(StompFrame frame) {
        String version = frame.getHeader("accept-version");
        String host = frame.getHeader("host");

        if (!"1.2".equals(version)) {
            sendError("Version mismatch", "Supported version is 1.2");
            return;
        }
        if (!"stomp.cs.bgu.ac.il".equals(host)) {
            sendError("Host mismatch", "Supported host is stomp.cs.bgu.ac.il");
            return;
        }

        // Login logic can be added here

        isConnected = true;

        StompFrame connectedFrame = new StompFrame("CONNECTED");
        connectedFrame.addHeader("version", "1.2");
        connections.send(connectionId, connectedFrame.toString());
    }

    private void handleSubscribe(StompFrame frame) {
        String dest = frame.getHeader("destination");
        String id = frame.getHeader("id");

        if (dest == null || id == null) {
            sendError("Missing headers", "destination and id are required for SUBSCRIBE");
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
            sendError("Missing headers", "id is required for UNSUBSCRIBE");
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
            sendError("Missing headers", "destination is required for SEND");
            return;
        }

        // Construct MESSAGE
        StompFrame msgFrame = new StompFrame("MESSAGE");
        msgFrame.addHeader("destination", dest);
        msgFrame.addHeader("message-id", String.valueOf(messageIdCounter.incrementAndGet()));
        msgFrame.setBody(frame.getBody());

        connections.send(dest, msgFrame.toString());
        sendReceiptIfNeeded(frame);
    }

    private void handleDisconnect(StompFrame frame) {
        sendReceiptIfNeeded(frame);
        shouldTerminate = true;
        connections.disconnect(connectionId);
    }

    private void sendReceiptIfNeeded(StompFrame frame) {
        String receipt = frame.getHeader("receipt");
        if (receipt != null) {
            StompFrame receiptFrame = new StompFrame("RECEIPT");
            receiptFrame.addHeader("receipt-id", receipt);
            connections.send(connectionId, receiptFrame.toString());
        }
    }

    private void sendError(String message, String description) {
        StompFrame errorFrame = new StompFrame("ERROR");
        errorFrame.addHeader("message", message);
        errorFrame.setBody("The message:\n-----\n" + description + "\n-----");
        connections.send(connectionId, errorFrame.toString());
        shouldTerminate = true;
        connections.disconnect(connectionId);
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }
}
