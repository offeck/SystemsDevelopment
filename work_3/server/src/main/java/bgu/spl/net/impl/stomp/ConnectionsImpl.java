package bgu.spl.net.impl.stomp;
import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;

public class ConnectionsImpl <T> implements Connections <T>{
    // fields
    ConcurrentHashMap<Integer, ConnectionHandler<T>> connectionHandlers;
    ConcurrentHashMap<String, List<Integer>> channelSubscriptions;

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
    public void addChannelSubscription(String channel, int connectionId) {
        this.channelSubscriptions.putIfAbsent(channel, new ArrayList<>());
        this.channelSubscriptions.get(channel).add(connectionId);
    }
    public void removeChannelSubscription(String channel, int connectionId) {
        if (this.channelSubscriptions.containsKey(channel)) {
            this.channelSubscriptions.get(channel).remove(Integer.valueOf(connectionId));
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
            for (int connectionId : this.channelSubscriptions.get(channel)) {
                ConnectionHandler<T> handler = this.connectionHandlers.get(connectionId);
                if (handler != null) {
                    handler.send(msg);
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