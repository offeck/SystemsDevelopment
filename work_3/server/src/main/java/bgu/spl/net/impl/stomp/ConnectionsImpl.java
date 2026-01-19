package bgu.spl.net.impl.stomp;

import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T> {
    // fields
    private final ConcurrentHashMap<Integer, ConnectionHandler<T>> connectionHandlers;
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>> channelSubscriptions;
    // Reverse mapping for efficient disconnect: connectionId -> Set<Channel>
    private final ConcurrentHashMap<Integer, Set<String>> connectionActiveSubscriptions;
    // Map to track active users: ConnectionId -> Username
    private final ConcurrentHashMap<Integer, String> activeConnectionUsers;
    // Map to track active users: Username -> ConnectionId
    private final ConcurrentHashMap<String, Integer> activeUsernames;

    // constructor
    public ConnectionsImpl() {
        this.connectionHandlers = new ConcurrentHashMap<>();
        this.channelSubscriptions = new ConcurrentHashMap<>();
        this.connectionActiveSubscriptions = new ConcurrentHashMap<>();
        this.activeConnectionUsers = new ConcurrentHashMap<>();
        this.activeUsernames = new ConcurrentHashMap<>();
    }

    public boolean registerUser(int connectionId, String username) {
        Integer existing = activeUsernames.putIfAbsent(username, connectionId);
        if (existing != null) {
            // Username is already registered
            return false;
        }
        // Successfully registered username; record reverse mapping
        activeConnectionUsers.put(connectionId, username);
        return true;
    }

    public boolean isUserConnected(String username) {
        return activeUsernames.containsKey(username);
    }

    public void addConnectionHandler(int connectionId, ConnectionHandler<T> handler) {
        this.connectionHandlers.put(connectionId, handler);
    }

    public void removeConnectionHandler(int connectionId) {
        this.connectionHandlers.remove(connectionId);
    }

    public void addChannelSubscription(String channel, int connectionId, String subscriptionId) {
        channelSubscriptions.computeIfAbsent(channel, k -> new ConcurrentHashMap<>()).put(connectionId, subscriptionId);
        connectionActiveSubscriptions.computeIfAbsent(connectionId, k -> ConcurrentHashMap.newKeySet()).add(channel);
    }

    public void removeChannelSubscription(String channel, int connectionId) {
        // Atomic removal to avoid race condition where we remove a channel map that
        // just got a new subscriber
        channelSubscriptions.computeIfPresent(channel, (k, v) -> {
            v.remove(connectionId);
            return v.isEmpty() ? null : v;
        });

        // Clean up the reverse mapping
        connectionActiveSubscriptions.computeIfPresent(connectionId, (k, v) -> {
            v.remove(channel);
            return v;
        });
    }

    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = this.connectionHandlers.get(connectionId);
        if (handler != null) {
            synchronized (handler) {
                handler.send(msg);
            }
            return true;
        }
        return false;
    }

    @Override
    public void send(String channel, T msg) {
        if (this.channelSubscriptions.containsKey(channel)) {
            ConcurrentHashMap<Integer, String> subscribers = this.channelSubscriptions.get(channel);
            for (Map.Entry<Integer, String> entry : subscribers.entrySet()) {
                int connectionId = entry.getKey();
                String subscriptionId = entry.getValue();
                ConnectionHandler<T> handler = this.connectionHandlers.get(connectionId);
                if (handler != null) {
                    synchronized (handler) {
                        if (msg instanceof String) {
                            String strMsg = (String) msg;
                            // Assuming msg is a MESSAGE frame string.
                            // We need to inject "subscription:subscriptionId" into headers.
                            // Inject after first newline (command line).
                            int firstNewLine = strMsg.indexOf('\n');
                            if (firstNewLine != -1) {
                                String newMsg = strMsg.substring(0, firstNewLine + 1)
                                        + "subscription:" + subscriptionId + "\n"
                                        + strMsg.substring(firstNewLine + 1);
                                handler.send((T) newMsg);
                            } else {
                                handler.send(msg);
                            }
                        } else {
                            handler.send(msg);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void disconnect(int connectionId) {
        this.removeConnectionHandler(connectionId);

        // Remove active user if exists (synchronized with registerUser)
        synchronized (this) {
            String username = activeConnectionUsers.remove(connectionId);
            if (username != null) {
                activeUsernames.remove(username);
            }
        }

        // Efficiently remove only relevant subscriptions
        Set<String> channels = connectionActiveSubscriptions.remove(connectionId);
        if (channels != null) {
            for (String channel : channels) {
                // We use the simpler logic for channelSubscriptions cleanup directly
                // or just call removeChannelSubscription.
                // Calling removeChannelSubscription is safe and handles the map cleanup.
                removeChannelSubscription(channel, connectionId);
            }
        }
    }
}