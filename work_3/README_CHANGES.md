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
