package bgu.spl.net.impl.stomp;

import bgu.spl.net.srv.Server;

public class StompServer {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: StompServer <port> <server_type(tpc/reactor)>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        String serverType = args[1];

        // You need to replace 'String' with the actual class representing your STOMP frames if you create one (e.g., StompFrame)
        // You also need to implement the Protocol and EncoderDecoder classes.
        
        if (serverType.equals("tpc")) {
            Server.threadPerClient(
                    port,
                    () -> new StompMessagingProtocolImpl(), // TODO: Replace with your actual Protocol implementation
                    () -> new StompEncoderDecoder()         // TODO: Replace with your actual EncoderDecoder implementation
            ).serve();
        } else if (serverType.equals("reactor")) {
            Server.reactor(
                    Runtime.getRuntime().availableProcessors(),
                    port,
                    () -> new StompMessagingProtocolImpl(), // TODO: Replace with your actual Protocol implementation
                    () -> new StompEncoderDecoder()         // TODO: Replace with your actual EncoderDecoder implementation
            ).serve();
        } else {
            System.out.println("Invalid server type: " + serverType + ". Use 'tpc' or 'reactor'.");
            System.exit(1);
        }
    }
}
