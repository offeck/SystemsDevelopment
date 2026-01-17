#!/usr/bin/env python3
"""
Basic Python Server for STOMP Assignment – Stage 3.3

IMPORTANT:
DO NOT CHANGE the server name or the basic protocol.
Students should EXTEND this server by implementing
the methods below.
"""

import socket
import sys
import threading
import sqlite3
import os

SERVER_NAME = "STOMP_PYTHON_SQL_SERVER"  # DO NOT CHANGE!
DB_FILE = "stomp_server.db"              # DO NOT CHANGE!


def recv_null_terminated(sock: socket.socket, buffer: bytes) -> (str, bytes):
    while b"\0" not in buffer:
        try:
            chunk = sock.recv(1024)
        except OSError:
            return "", b""
        if not chunk:
            return "", b""
        buffer += chunk
    
    msg_bytes, buffer = buffer.split(b"\0", 1)
    return msg_bytes.decode("utf-8", errors="replace"), buffer


def init_database():
    """Initializes the database with required tables if they don't exist."""
    print(f"[{SERVER_NAME}] Initializing database {DB_FILE}...")
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()
    
    # Create Users table (username, password)
    c.execute('''CREATE TABLE IF NOT EXISTS Users (
                    username TEXT PRIMARY KEY,
                    password TEXT NOT NULL
                )''')
    # Create User Registration table (username, date-time)
    c.execute('''CREATE TABLE IF NOT EXISTS UserRegistrations (
                    username TEXT PRIMARY KEY,
                    registration_datetime DATETIME NOT NULL,
                    FOREIGN KEY (username) REFERENCES Users(username)
                )''')
    # Create User Logins table (username, login-time, logout-time)
    c.execute('''CREATE TABLE IF NOT EXISTS UserLogins (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL,
                    login_datetime  NOT NULL,
                    logout_datetime DATETIME,
                    FOREIGN KEY (username) REFERENCES Users(username)
                )''')
    # Create Messages table (id, time, sender, receiver, message)
    c.execute('''CREATE TABLE IF NOT EXISTS Messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    message_datetime DATETIME NOT NULL,
                    sender TEXT NOT NULL,
                    receiver TEXT NOT NULL,
                    message TEXT NOT NULL
                )''')
    # Create File Tracking table (id, filename, uploader, upload-time)
    c.execute('''CREATE TABLE IF NOT EXISTS FileTracking (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    filename TEXT NOT NULL,
                    uploader TEXT NOT NULL,
                    upload_datetime DATETIME NOT NULL,
                    game_channel INTEGER NOT NULL
                )''')
    conn.commit()
    conn.close()


def execute_sql_command(sql_command: str) -> str:
    """Executes INSERT, UPDATE, DELETE statements."""
    try:
        conn = sqlite3.connect(DB_FILE)
        c = conn.cursor()
        c.execute(sql_command)
        conn.commit()
        conn.close()
        return "done"
    except sqlite3.Error as e:
        return f"error: {str(e)}"


def execute_sql_query(sql_query: str) -> str:
    """Executes SELECT statements and returns formatted results."""
    try:
        conn = sqlite3.connect(DB_FILE)
        c = conn.cursor()
        c.execute(sql_query)
        rows = c.fetchall()
        
        # Format: each row on a new line, columns separated by spaces
        result_lines = []
        for row in rows:
            line = " ".join(str(item) for item in row)
            result_lines.append(line)
            
        conn.close()
        
        return "\n".join(result_lines) if result_lines else ""
        
    except sqlite3.Error as e:
        return f"error: {str(e)}"


def print_report():
    """Generates and prints the server report using SQL queries."""
    print("-" * 40)
    print(f"[{SERVER_NAME}] SERVER REPORT")
    print("-" * 40)
    
    try:
        conn = sqlite3.connect(DB_FILE)
        c = conn.cursor()

        # 1. Get List of Registered Users
        c.execute("SELECT username FROM Users ORDER BY username ASC")
        users = c.fetchall()

        if not users:
            print("No registered users found.")
        else:
            for (user,) in users:
                print(f"User: {user}")
                
                # 2. Login History
                print("  Login History:")
                c.execute("SELECT login_datetime, logout_datetime FROM UserLogins WHERE username = ? ORDER BY login_datetime ASC", (user,))
                logins = c.fetchall()
                if logins:
                    for login_dt, logout_dt in logins:
                        logout_str = logout_dt if logout_dt else "(Active)"
                        print(f"    - Login: {login_dt} | Logout: {logout_str}")
                else:
                    print("    - No login records.")

                # 3. Uploaded Files
                print("  Uploaded Files:")
                c.execute("SELECT filename, game_channel, upload_datetime FROM FileTracking WHERE uploader = ? ORDER BY upload_datetime ASC", (user,))
                files = c.fetchall()
                if files:
                    for filename, channel, upload_dt in files:
                        print(f"    - {filename} (Channel: {channel}) at {upload_dt}")
                else:
                    print("    - No files uploaded.")
                
                print("-" * 20)

        conn.close()

    except sqlite3.Error as e:
        print(f"Error generating report: {e}")
    
    print("End of Report.\n")


def handle_client(client_socket: socket.socket, addr):
    print(f"[{SERVER_NAME}] Client connected from {addr}")

    buffer = b""
    try:
        while True:
            # 1. Receive message
            message, buffer = recv_null_terminated(client_socket, buffer)
            if message == "":
                break

            print(f"[{SERVER_NAME}] Received: {message}")

            # 2. Determine if it is a Query (SELECT) or Command (INSERT/UPDATE/DELETE)
            # Simple heuristic: Check if it starts with "SELECT" (case-insensitive)
            response = ""
            stripped_msg = message.strip().upper()
            
            if stripped_msg.startswith("SELECT"):
                 response = execute_sql_query(message)
            else:
                 response = execute_sql_command(message)

            # 3. Send Response (Null-terminated)
            print(f"[{SERVER_NAME}] Sending response: {response}")
            client_socket.sendall(response.encode('utf-8') + b"\0")

    except Exception as e:
        print(f"[{SERVER_NAME}] Error handling client {addr}: {e}")
    finally:
        try:
            client_socket.close()
        except Exception:
            pass
        print(f"[{SERVER_NAME}] Client {addr} disconnected")


def start_server(host="127.0.0.1", port=7778):
    # Initialize DB on startup
    init_database()
    
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

    try:
        server_socket.bind((host, port))
        server_socket.listen(5)
        print(f"[{SERVER_NAME}] Server started on {host}:{port}")
        print(f"[{SERVER_NAME}] Waiting for connections...")

        while True:
            client_socket, addr = server_socket.accept()
            t = threading.Thread(
                target=handle_client,
                args=(client_socket, addr),
                daemon=True
            )
            t.start()

    except KeyboardInterrupt:
        print(f"\n[{SERVER_NAME}] Shutting down server...")
    finally:
        try:
            server_socket.close()
        except Exception:
            pass


if __name__ == "__main__":
    port = 7778
    if len(sys.argv) > 1:
        raw_port = sys.argv[1].strip()
        try:
            port = int(raw_port)
        except ValueError:
            print(f"Invalid port '{raw_port}', falling back to default {port}")

    start_server(port=port)
