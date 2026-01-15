package bgu.spl.net.impl.stomp;

import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;

public class ConnectionsImpl<T> implements Connections<T> {
    // fields
    ConcurrentHashMap<Integer, ConnectionHandler<T>> connectionHandlers;
    ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>> channelSubscriptions;

    // constructor
    public ConnectionsImpl() {
        this.connectionHandlers = new ConcurrentHashMap<>();
        this.channelSubscriptions = new ConcurrentHashMap<>();
    }

    public void addConnectionHandler(int connectionId, ConnectionHandler<T> handler) {
        this.connectionHandlers.put(connectionId, handler);
    }

    public void removeConnectionHandler(int connectionId) {
        this.connectionHandlers.remove(connectionId);
    }

    public void addChannelSubscription(String channel, int connectionId, String subscriptionId) {
        this.channelSubscriptions.putIfAbsent(channel, new ConcurrentHashMap<>());
        this.channelSubscriptions.get(channel).put(connectionId, subscriptionId);
    }

    public void removeChannelSubscription(String channel, int connectionId) {
        if (this.channelSubscriptions.containsKey(channel)) {
            this.channelSubscriptions.get(channel).remove(connectionId);
            if (this.channelSubscriptions.get(channel).isEmpty()) {
                this.channelSubscriptions.remove(channel);
            }
        }
    }

    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = this.connectionHandlers.get(connectionId);
        if (handler != null) {
            handler.send(msg);
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

    @Override
    public void disconnect(int connectionId) {
        this.removeConnectionHandler(connectionId);
        for (String channel : this.channelSubscriptions.keySet()) {
            this.removeChannelSubscription(channel, connectionId);
        }
    }
}