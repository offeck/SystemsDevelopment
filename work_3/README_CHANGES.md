# Changes Report - Task 3 / STOMP Implementation

## Overview
This document summarizes the changes made to the `work_3` project to implement the STOMP protocol (Task 3.2) and configure the environment.

## 1. Environment & Configuration
*   **Dependencies**: Installed `libboost-all-dev` to enable compilation of the C++ client (`EchoClient`).
*   **Git Configuration**: Updated `/workspace/.gitignore` to exclude build artifacts:
    *   `work_3/server/target/*`
    *   `work_3/client/bin/*`

## 2. Server Implementation (`work_3/server`)

### New Classes
*   **`bgu.spl.net.impl.stomp.StompFrame`**
    *   A utility class designed to parse raw string messages into structured STOMP frames (Command, Headers, Body).
    *   Includes a `toString()` method to reconstruct the wire-format representation of the frame.

*   **`bgu.spl.net.impl.stomp.StompMessagingProtocolImpl`**
    *   Implements the `StompMessagingProtocol<String>` interface.
    *   **Protocol Logic**:
        *   `CONNECT`: Validates `accept-version` (1.2) and `host` (stomp.cs.bgu.ac.il). Returns `CONNECTED`.
        *   `SUBSCRIBE`: Maps a client-provided `id` to a destination topic. Registers the client to the channel in `Connections`.
        *   `UNSUBSCRIBE`: Removes the subscription based on the provided `id`.
        *   `SEND`: Assigns a unique `message-id`, wraps the content in a `MESSAGE` frame, and forwards it to the destination channel.
        *   `DISCONNECT`: Handles graceful shutdown and connection termination.
    *   **Features**:
        *   Automatic `RECEIPT` generation if the `receipt` header is present in any frame.
        *   Error handling with `ERROR` frames and connection closure for malformed/illegal states.

### Modified Classes
*   **`bgu.spl.net.impl.stomp.ConnectionsImpl`**
    *   **Subscription Tracking**: Changed the internal storage structure for channel subscriptions.
        *   From: `ConcurrentHashMap<String, List<Integer>>`
        *   To: `ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>>` (Maps `Channel` -> `ConnectionID` -> `SubscriptionID`).
    *   **Message Fan-out (`send(channel, msg)`)**: 
        *   Updated logic to iterate over all subscribers of a channel.
        *   **Header Injection**: The method now dynamically modifies the outgoing `MESSAGE` frame for each client to include the specific `subscription: <id>` header required by the STOMP spec (matching the ID the client provided during subscription).

## 3. Server Implementation - Update (StompServer & Encoder)

### New & Updated Classes
*   **`bgu.spl.net.impl.stomp.StompEncoderDecoder`**
    *   Implements `MessageEncoderDecoder<String>`.
    *   **Logic**:
        *   **Decoding**: Reads bytes until the null character (`\u0000`) is encountered, indicating the end of a STOMP frame. Returns the parsed UTF-8 string (excluding the null terminator).
        *   **Encoding**: Appends the null character (`\u0000`) to the outgoing string message and converts it to UTF-8 bytes.

*   **`bgu.spl.net.impl.stomp.StompServer`**
    *   **Configuration**: Updated the `main` method to correctly initialize the server based on the mode argument (`tpc` or `reactor`).
    *   **Factories**: Now initializes the server using `StompMessagingProtocolImpl::new` and `StompEncoderDecoder::new` factories.

## 4. Verification
*   **Build**: The server project successfully compiles with `mvn compile`.
*   **Sanity Check**: Verified that the existing Echo Server/Client infrastructure remains functional after environment updates.

## 5. STOMP Compliance & Client Improvements (Post-Review Updates)
*   **Server Authentication & Receipts**:
    *   Added validation for `login`/`passcode` headers and enforced unique logged-in users.
    *   Added wrong-password handling and missing-receipt validation for `DISCONNECT`.
    *   Included `receipt-id` in `ERROR` frames when a receipt was requested.
*   **Server SQL Integration**:
    *   Logs user registrations in `UserRegistrations`, logins in `UserLogins`, and file uploads in `FileTracking`.
    *   Escapes SQL inputs to avoid malformed queries.
    *   Added a server-side `REPORT` STOMP command to print SQL-based summaries.
*   **Frame Serialization**:
    *   Removed explicit null terminators from `StompFrame.toString()` in both Java and C++ to avoid double terminators.
*   **Client Protocol Behavior**:
    *   Login now connects to the host/port specified in the `login` command instead of requiring CLI args.
    *   Message reader parses incoming `MESSAGE` bodies into game events and stores them for summaries.
    *   Join/exit success messages now emit only after corresponding `RECEIPT` frames.
    *   Summaries order events using halftime-aware ordering and event time, with stable tie-breakers.
    *   Reports require an active subscription before sending events.
*   **SQL Schema Adjustment**:
    *   Updated `FileTracking.game_channel` to `TEXT` to match stored channel values.

## 6. Leading Questions & Answers

### 1. In the StompServer implementation, where do I fit the switch case that checks which protocol to use?
You generally **do not** need a switch case inside the protocol logic itself to decide *which* protocol class to use.
*   **Separation of Concerns**: The decision of which protocol to run is made at **construction time** in `StompServer.main()`.
*   **Factory Pattern**: When you start `Server.threadPerClient()` or `Server.reactor()`, you pass a `Supplier<ProcessingProtocol>`.
*   **Implementation**: In `StompServer`, you pass `StompMessagingProtocolImpl::new`. This delegates the creation of the specific protocol instance to the server infrastructure, which instantiates a new protocol object for each connected client.

### 2. Can you explain the different interfaces that appear in the BaseServer?
*   **`MessageEncoderDecoder<T>`**:
    *   **Role**: Translator.
    *   **Input**: Raw bytes from the socket.
    *   **Output**: Typed messages (e.g., `String` or `StompFrame`).
    *   **Why**: TCP sends a stream of bytes. We need to know where one message ends and the next begins (framing).
*   **`MessagingProtocol<T>`**:
    *   **Role**: Brain/Logic.
    *   **Input**: A complete message object (produced by the EncDec).
    *   **Output**: A response message (or null).
    *   **Why**: It processes the *intent* of the data (e.g., "User wants to Subscribe"). It maintains the client's state (e.g., "Is this user logged in?").

### 3. Compare the Thread-Per-Client approach to the Reactor approach.
*   **Thread-Per-Client (TPC)**:
    *   **Model**: One dedicated OS thread for every active connection.
    *   **Blocking**: The thread calls `in.read()` and blocks (sleeps) until data arrives.
    *   **Pros**: Simple to write/debug.
    *   **Cons**: Expensive. Threads consume memory (stack) and context switching overhead. Does not scale to 10k+ clients.
*   **Reactor**:
    *   **Model**: One main "Selector" thread monitoring many sockets. A fixed pool of worker threads handles the processing.
    *   **Non-Blocking**: Uses `java.nio`. The Selector thread is notified *only* when data is ready.
    *   **Pros**: Highly scalable. Can handle thousands of idle connections with few threads.
    *   **Cons**: Complex to implement. Logic must be non-blocking.

### 4. Which part of the code is the Runnable?
*   **Thread-Per-Client**:
    *   The **`BlockingConnectionHandler`** is the `Runnable`.
    *   The `BaseServer` creates a new `Thread(handler).start()` for each client.
*   **Reactor**:
    *   The **`NonBlockingConnectionHandler`** is *not* a Runnable itself in the same way, but the tasks submitted to the thread pool are.
    *   The `Reactor` main loop sees an event (Read/Write) and submits the handler's task to the `ExecutorService` (the Actor Thread Pool).

### 5. Where should I hold the object that represents the database?
*   **Location**: It should be a **Singleton** or a shared service managed by the Application Context.
*   **Initialization**: Initialize it once in `StompServer.main()` before starting the server.
*   **Access**:
    *   Since `StompMessagingProtocolImpl` is created fresh for each client, you cannot store the DB *inside* the Protocol instance (unless it's static).
    *   **Best Practice**: Pass a reference to the shared DB/User object into the generic constructor of the `StompMessagingProtocolImpl`.
    *   **Current Code**: Currently, our `StompMessagingProtocolImpl::new` is a parameterless constructor reference. To support a DB, we would change the supplier to `() -> new StompMessagingProtocolImpl(myDatabaseInstance)`.
