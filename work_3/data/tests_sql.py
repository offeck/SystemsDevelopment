
import unittest
import os
import sqlite3
import sql_server
from datetime import datetime

import threading
import socket
import time

class TestStompSQLServer(unittest.TestCase):
    
    SERVER_HOST = "127.0.0.1"
    SERVER_PORT = 7779  # Use a different port for testing to avoid conflicts

    @classmethod
    def setUpClass(cls):
        # Initialize test DB
        cls.test_db = "test_integration_stomp_server.db"
        sql_server.DB_FILE = cls.test_db
        if os.path.exists(cls.test_db):
            os.remove(cls.test_db)
        
        # Start server in a separate thread
        cls.server_thread = threading.Thread(target=sql_server.start_server, args=(cls.SERVER_HOST, cls.SERVER_PORT), daemon=True)
        cls.server_thread.start()
        time.sleep(1) # Give it a second to start

    @classmethod
    def tearDownClass(cls):
        # Clean up database
        if os.path.exists(cls.test_db):
            os.remove(cls.test_db)

    def send_request(self, message):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.connect((self.SERVER_HOST, self.SERVER_PORT))
            # Send message + null terminator
            s.sendall(message.encode('utf-8') + b'\0')
            
            # Receive response until null terminator
            response = b""
            while True:
                chunk = s.recv(1024)
                if not chunk:
                    break
                response += chunk
                if b'\0' in response:
                    break
            
            return response.decode('utf-8').rstrip('\0')

    def test_01_integration_registration(self):
        # This tests the socket communication protocol (used by Java client)
        username = "socketuser"
        reg_time = "2023-10-10 10:10:10"
        
        # 1. Register User via SQL Command
        cmd = f"INSERT INTO Users (username, password) VALUES ('{username}', 'pass')"
        resp = self.send_request(cmd)
        self.assertEqual(resp, "done")
        
        # 2. Query User via SQL Query
        query = f"SELECT username FROM Users WHERE username='{username}'"
        resp = self.send_request(query)
        self.assertEqual(resp.strip(), username)

    def test_user_registration(self):

        # Test creating a user
        username = "testuser"
        password = "password123"
        reg_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

        cmd = f"INSERT INTO Users (username, password) VALUES ('{username}', '{password}')"
        result = sql_server.execute_sql_command(cmd)
        self.assertEqual(result, "done")
        
        cmd = f"INSERT INTO UserRegistrations (username, registration_datetime) VALUES ('{username}', '{reg_time}')"
        result = sql_server.execute_sql_command(cmd)
        self.assertEqual(result, "done")
        
        # Verify
        query = f"SELECT username, password FROM Users WHERE username='{username}'"
        res = sql_server.execute_sql_query(query)
        self.assertIn(f"{username} {password}", res)

    def test_login_logout(self):
        username = "testuser"
        login_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        
        # 1. Login
        cmd = f"INSERT INTO UserLogins (username, login_datetime) VALUES ('{username}', '{login_time}')"
        result = sql_server.execute_sql_command(cmd)
        self.assertEqual(result, "done")
        
        # Verify active login (logout_datetime should be NULL or empty)
        # Note: server returns space separated. NULL usually results in None in python which str(None) is "None"
        # But let's check what executes_sql_query returns for None
        
        query = f"SELECT username, logout_datetime FROM UserLogins WHERE username='{username}'"
        res = sql_server.execute_sql_query(query)
        self.assertTrue(username in res)
        
        # 2. Logout (Update latest login which has no logout time)
        logout_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        # We need to find the specific row, but typically we update the one with NULL logout_datetime
        # Creating a more complex update query as expected from the server side
        update_cmd = f"UPDATE UserLogins SET logout_datetime='{logout_time}' WHERE username='{username}' AND logout_datetime IS NULL"
        result = sql_server.execute_sql_command(update_cmd)
        self.assertEqual(result, "done")
        
        # Verify
        query = f"SELECT logout_datetime FROM UserLogins WHERE username='{username}'"
        res = sql_server.execute_sql_query(query)
        self.assertIn(logout_time, res)

    def test_file_tracking(self):
        username = "uploader"
        filename = "game_stats.txt"
        channel = "active_games"
        upload_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        
        cmd = f"INSERT INTO FileTracking (filename, uploader, upload_datetime, game_channel) VALUES ('{filename}', '{username}', '{upload_time}', '{channel}')"
        result = sql_server.execute_sql_command(cmd)
        self.assertEqual(result, "done")
        
        query = f"SELECT filename, uploader FROM FileTracking WHERE filename='{filename}'"
        res = sql_server.execute_sql_query(query)
        self.assertEqual(f"{filename} {username}", res.strip())

    def test_report_queries(self):
        # Populate data for report
        # User 1
        u1 = "Alice"
        sql_server.execute_sql_command(f"INSERT INTO Users (username, password) VALUES ('{u1}', 'pw')")
        sql_server.execute_sql_command(f"INSERT INTO UserLogins (username, login_datetime) VALUES ('{u1}', '2023-01-01 10:00:00')")
        sql_server.execute_sql_command(f"INSERT INTO FileTracking (filename, uploader, upload_datetime, game_channel) VALUES ('x.txt', '{u1}', '2023-01-01 10:05:00', 'ch1')")
        
        # User 2
        u2 = "Bob"
        sql_server.execute_sql_command(f"INSERT INTO Users (username, password) VALUES ('{u2}', 'pw')")
        
        # We can't capture print output easily from print_report() without redirection, 
        # but we can verify the queries it uses.
        
        # 1. List Users
        q1 = "SELECT username FROM Users ORDER BY username ASC"
        r1 = sql_server.execute_sql_query(q1)
        self.assertIn("Alice", r1)
        self.assertIn("Bob", r1)
        
        # 2. Login History for Alice
        q2 = f"SELECT login_datetime, logout_datetime FROM UserLogins WHERE username = '{u1}' ORDER BY login_datetime ASC"
        r2 = sql_server.execute_sql_query(q2)
        self.assertIn("2023-01-01 10:00:00 None", r2)
        
        # 3. Files for Alice
        q3 = f"SELECT filename, game_channel, upload_datetime FROM FileTracking WHERE uploader = '{u1}' ORDER BY upload_datetime ASC"
        r3 = sql_server.execute_sql_query(q3)
        self.assertIn("x.txt ch1 2023-01-01 10:05:00", r3)

if __name__ == '__main__':
    print("Running SQL Server Tests...")
    # verbosity=2 prints the name of each test and its result (ok/FAIL)
    unittest.main(verbosity=2)
