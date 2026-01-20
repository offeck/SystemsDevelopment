import subprocess
import time
import os
import signal

CLIENT_PATH = "./bin/StompWCIClient"
HOST = "127.0.0.1"
PORT = "7777"
EVENTS_FILE = "events.json"

SERVER_DIR = "../server"
SERVER_CMD = ["mvn", "exec:java", "-Dexec.mainClass=bgu.spl.net.impl.stomp.StompServer", "-Dexec.args=7777 tpc"]

SQL_SERVER_DIR = "../data"
SQL_SERVER_CMD = ["python3", "sql_server.py"]
DB_FILE_PATH = os.path.join(SQL_SERVER_DIR, "stomp_server.db")

def run_test():
    # 0. Cleanup DB
    if os.path.exists(DB_FILE_PATH):
        try:
            os.remove(DB_FILE_PATH)
            print(f"Removed existing database: {DB_FILE_PATH}")
        except Exception as e:
            print(f"Warning: Could not remove database: {e}")

    # 1. Start SQL Server
    print(f"Starting Python SQL Server from {SQL_SERVER_DIR}...")
    sql_server = subprocess.Popen(
        SQL_SERVER_CMD,
        cwd=SQL_SERVER_DIR,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )
    
    # Wait for SQL server
    time.sleep(2)
    if sql_server.poll() is not None:
         print("SQL Server failed to start!")
         out, err = sql_server.communicate()
         print(out)
         print(err)
         return

    # 2. Start Java Server
    print(f"Starting Java Server from {SERVER_DIR}...")
    server = subprocess.Popen(
        SERVER_CMD,
        cwd=SERVER_DIR,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )
    
    # Wait for server to initialize
    print("Waiting for server to initialize (10s)...")
    time.sleep(10) 
    
    if server.poll() is not None:
         print("Server failed to start!")
         stdout, stderr = server.communicate()
         print("--- Server STDOUT ---")
         print(stdout)
         print("--- Server STDERR ---")
         print(stderr)
         # Cleanup SQL server before returning
         sql_server.terminate()
         return

    print("Server started (pid={}).".format(server.pid))

    print("Starting Client A...")
    client_a = subprocess.Popen(
        [CLIENT_PATH],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        bufsize=0 
    )

    print("Starting Client B...")
    client_b = subprocess.Popen(
        [CLIENT_PATH],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        bufsize=0
    )

    try:
        # 1. Login
        print("\n[TEST] Logging in Client A...")
        client_a.stdin.write(f"login {HOST}:{PORT} userA passA\n")
        time.sleep(1) 
        
        print("[TEST] Logging in Client B...")
        client_b.stdin.write(f"login {HOST}:{PORT} userB passB\n")
        time.sleep(1)

        # 2. Join Channel
        print("\n[TEST] Client A joining channel 'Japan_Germany'...")
        client_a.stdin.write("join Japan_Germany\n")
        time.sleep(1)

        print("[TEST] Client B joining channel 'Japan_Germany'...")
        client_b.stdin.write("join Japan_Germany\n")
        time.sleep(1)

        # 3. Report
        print("\n[TEST] Client A reporting events...")
        client_a.stdin.write(f"report {EVENTS_FILE}\n")
        time.sleep(5) # Give time for report to be processed and broadcasted

        # 4. Summary (Client B should generate summary of what it heard from A)
        print("\n[TEST] Client B generating summary...")
        client_b.stdin.write("summary Japan_Germany userA summary_b.txt\n")
        time.sleep(1)

        # 5. Exit
        print("\n[TEST] Client A exiting channel...")
        client_a.stdin.write("exit Japan_Germany\n")
        time.sleep(1)

        # 6. Logout
        print("\n[TEST] Logging out Client A...")
        client_a.stdin.write("logout\n")
        print("Logging out Client B...")
        client_b.stdin.write("logout\n")
        time.sleep(1)

    except Exception as e:
        print(f"An error occurred: {e}")

    finally:
        print("\nClosing clients...")
        client_a.terminate()
        client_b.terminate()
        
        print("Closing server...")
        server.terminate()
        try:
            server.wait(timeout=5)
        except subprocess.TimeoutExpired:
            server.kill()

        print("Closing SQL server...")
        sql_server.terminate()
        try:
            sql_server.wait(timeout=5)
        except subprocess.TimeoutExpired:
            sql_server.kill()

        # Print outputs
        out_a, err_a = client_a.communicate()
        out_b, err_b = client_b.communicate()
        
        print("\n--- Client A Output ---")
        if out_a: print(out_a)
        if err_a: print(err_a)
        
        print("\n--- Client B Output ---")
        if out_b: print(out_b)
        if err_b: print(err_b)

        # Check summary
        if os.path.exists("summary_b.txt"):
            print("\n--- Summary File (summary_b.txt) ---")
            with open("summary_b.txt", "r") as f:
                content = f.read()
                print(content)
                # Check for relevant content based on actual output format
                if "Japan" in content and "Germany" in content and "Game stats" in content:
                     print("\n[SUCCESS] Summary file looks correct.")
                else:
                     print("\n[FAILURE] Summary file missing expected content.")
        else:
            print("\n[ERROR] summary_b.txt was not created!")

if __name__ == "__main__":
    run_test()
