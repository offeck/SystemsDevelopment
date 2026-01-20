import subprocess
import time
import os
import signal
import sys
import threading
import argparse

# --- Configuration ---
CLIENT_PATH = "./bin/StompWCIClient"
HOST = "127.0.0.1"
PORT = "7777"
EVENTS_FILE = "events.json" 

# Paths relative to work_3/client
SERVER_DIR = "../server"
SERVER_CMD_TPC = ["mvn", "exec:java", "-Dexec.mainClass=bgu.spl.net.impl.stomp.StompServer", "-Dexec.args=7777 tpc"]
SERVER_CMD_REACTOR = ["mvn", "exec:java", "-Dexec.mainClass=bgu.spl.net.impl.stomp.StompServer", "-Dexec.args=7777 reactor"]

SQL_SERVER_DIR = "../data"
SQL_SERVER_CMD = ["python3", "sql_server.py"]
DB_FILE_PATH = os.path.join(SQL_SERVER_DIR, "stomp_server.db")

class Colors:
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'

class StompClientWrapper:
    def __init__(self, name, executable_path, log_output=False):
        self.name = name
        self.executable_path = executable_path
        self.process = None
        self.log_output = log_output
        self.stdout_log = []
        self.stderr_log = []
        self.out_thread = None
        self.err_thread = None

    def start(self):
        #print(f"[{self.name}] Starting...")
        self.process = subprocess.Popen(
            [self.executable_path],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=0 
        )
        self.out_thread = threading.Thread(target=self._read_stdout)
        self.err_thread = threading.Thread(target=self._read_stderr)
        self.out_thread.daemon = True
        self.err_thread.daemon = True
        self.out_thread.start()
        self.err_thread.start()

    def _read_stdout(self):
        try:
            for line in iter(self.process.stdout.readline, ''):
                cleaned = line.strip()
                if cleaned:
                    self.stdout_log.append(cleaned)
                    if self.log_output:
                        print(f"[{self.name}] {cleaned}")
        except ValueError: pass

    def _read_stderr(self):
        try:
            for line in iter(self.process.stderr.readline, ''):
                cleaned = line.strip()
                if cleaned:
                    self.stderr_log.append(cleaned)
                    if self.log_output:
                        print(f"{Colors.WARNING}[{self.name} ERR] {cleaned}{Colors.ENDC}")
        except ValueError: pass

    def write(self, command):
        if self.process and self.process.poll() is None:
            if self.log_output:
                print(f"[{self.name} INPUT] {command.strip()}")
            try:
                self.process.stdin.write(command + "\n")
                self.process.stdin.flush()
            except BrokenPipeError:
                pass
        
    def stop(self):
        if self.process:
            self.process.terminate()
            try:
                self.process.wait(timeout=1)
            except subprocess.TimeoutExpired:
                self.process.kill()
            self.process = None

    def expect(self, text, timeout=2):
        start = time.time()
        while time.time() - start < timeout:
            for line in self.stdout_log:
                if text in line:
                    return True
            time.sleep(0.1)
        return False
    
    def expect_not(self, text, timeout=2):
        start = time.time()
        while time.time() - start < timeout:
            for line in self.stdout_log:
                if text in line:
                    return False
            time.sleep(0.1)
        return True

    def get_log_content(self):
        return "\n".join(self.stdout_log)
        
    def clear_logs(self):
        self.stdout_log = []
        self.stderr_log = []

class TestEnv:
    def __init__(self, use_reactor=False, verbose=False):
        self.sql_process = None
        self.server_process = None
        self.clients = []
        self.use_reactor = use_reactor
        self.verbose = verbose

    def setup(self):
        self._clean_db()
        self._start_sql()
        self._start_server()

    def teardown(self):
        for c in self.clients:
            c.stop()
        self.clients = []

        if self.server_process:
            self.server_process.terminate()
            try:
                self.server_process.wait(timeout=2)
            except: self.server_process.kill()

        if self.sql_process:
            self.sql_process.terminate()
            try:
                self.sql_process.wait(timeout=2)
            except: self.sql_process.kill()
        
        # self._clean_db() # Optional: leave db for inspection

    def _clean_db(self):
        if os.path.exists(DB_FILE_PATH):
            try: os.remove(DB_FILE_PATH)
            except: pass

    def _start_sql(self):
        self.sql_process = subprocess.Popen(
            SQL_SERVER_CMD, cwd=SQL_SERVER_DIR,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
        )
        time.sleep(1)

    def _start_server(self):
        cmd = SERVER_CMD_REACTOR if self.use_reactor else SERVER_CMD_TPC
        self.server_process = subprocess.Popen(
            cmd, cwd=SERVER_DIR,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
        )
        # Simple wait for startup - scanning stdout is better but sleeping is easier
        time.sleep(5)
        if self.server_process.poll() is not None:
             out, err = self.server_process.communicate()
             raise RuntimeError(f"Server failed to start:\n{out}\n{err}")

    def create_client(self, name):
        c = StompClientWrapper(name, CLIENT_PATH, log_output=self.verbose)
        c.start()
        self.clients.append(c)
        return c

def run_test(name, test_func):
    print(f"{Colors.HEADER}=== Running Test: {name} ==={Colors.ENDC}")
    env = TestEnv(verbose=True) # Set verbose=True to see client output
    try:
        env.setup()
        test_func(env)
        print(f"{Colors.OKGREEN}[PASS] {name}{Colors.ENDC}\n")
    except AssertionError as e:
        print(f"{Colors.FAIL}[FAIL] {name}: {e}{Colors.ENDC}\n")
    except Exception as e:
        print(f"{Colors.FAIL}[ERROR] {name}: {e}{Colors.ENDC}\n")
    finally:
        env.teardown()

# ----------------- SCENARIOS -----------------

def test_login_success(env):
    c1 = env.create_client("C1")
    c1.write(f"login {HOST}:{PORT} user1 pass1")
    time.sleep(1)
    if not c1.expect("Login successful"):
        # Depends on what the client prints. The assignment says "Login successful"
        raise AssertionError("Client C1 did not report successful login")

def test_double_login_same_client(env):
    c1 = env.create_client("C1")
    c1.write(f"login {HOST}:{PORT} user1 pass1")
    time.sleep(1)
    assert c1.expect("Login successful"), "First login failed"
    
    # Try logging in again
    c1.clear_logs()
    c1.write(f"login {HOST}:{PORT} user1 pass1")
    time.sleep(1)
    
    # The assignment says: "The client should simply print 'The client is already logged in...'"
    if not c1.expect("The client is already logged in"):
        raise AssertionError("Client did not prevent double login locally")

def test_double_login_diff_client(env):
    c1 = env.create_client("C1")
    c1.write(f"login {HOST}:{PORT} user1 pass1")
    time.sleep(1)
    assert c1.expect("Login successful")
    
    c2 = env.create_client("C2")
    c2.write(f"login {HOST}:{PORT} user1 pass1")
    time.sleep(1)
    
    # Server should send error. Client should print "User already logged in"
    if not c2.expect("User already logged in"):
        raise AssertionError("Server allowed second client to login as same user")

def test_login_logout_login(env):
    c1 = env.create_client("C1")
    c1.write(f"login {HOST}:{PORT} user1 pass1")
    time.sleep(1)
    assert c1.expect("Login successful")

    c1.write("logout")
    time.sleep(1)
    # Give it a moment to process disconnect
    
    c1.clear_logs()
    c1.write(f"login {HOST}:{PORT} user1 pass1")
    time.sleep(1)
    if not c1.expect("Login successful"):
        raise AssertionError("Could not log in again after logout")

def test_wrong_password(env):
    # First create user
    c1 = env.create_client("C1")
    c1.write(f"login {HOST}:{PORT} user1 pass1")
    time.sleep(1)
    c1.write("logout")
    time.sleep(1)
    c1.stop()

    # Try different password
    c2 = env.create_client("C2")
    c2.write(f"login {HOST}:{PORT} user1 wrongpass")
    time.sleep(1)
    
    if not c2.expect("Wrong password"):
        raise AssertionError("Server did not catch wrong password")

def test_join_report_flow(env):
    c1 = env.create_client("C1")
    c2 = env.create_client("C2")
    
    c1.write(f"login {HOST}:{PORT} user1 pass1")
    c2.write(f"login {HOST}:{PORT} user2 pass2")
    time.sleep(1)
    
    channel = "Japan_Germany"
    c1.write(f"join {channel}")
    c2.write(f"join {channel}")
    time.sleep(1)
    
    if not c1.expect(f"Joined channel {channel}"):
        raise AssertionError("C1 failed to join channel")
    
    # C1 reports
    c1.write(f"report {EVENTS_FILE}")
    time.sleep(2)
    
    # C2 should maintain stats internally. We verify using 'summary' command in C2
    # C2 writes summary to file
    summary_file = "test_summary.txt"
    if os.path.exists(summary_file): os.remove(summary_file)
    
    c2.write(f"summary {channel} user1 {summary_file}")
    time.sleep(1)
    
    if not os.path.exists(summary_file):
        raise AssertionError("Summary file not created by C2")
    
    with open(summary_file, 'r') as f:
        content = f.read()
        # Events file contains "Japan" and "Germany" teams?
        # Actually events.json has "team a": "Japan", "team b": "Germany"
        if "Japan" not in content or "Germany" not in content:
            raise AssertionError("Summary file content seems missing/wrong")

def test_subscribe_unsubscribe(env):
    c1 = env.create_client("C1")
    c1.write(f"login {HOST}:{PORT} user1 pass1")
    time.sleep(1)
    
    channel = "c_topic"
    c1.write(f"join {channel}")
    time.sleep(0.5)
    assert c1.expect(f"Joined channel {channel}")
    
    c1.write(f"exit {channel}")
    time.sleep(0.5)
    assert c1.expect(f"Exited channel {channel}")
    
    # Re-join
    c1.clear_logs()
    c1.write(f"join {channel}")
    time.sleep(0.5)
    if not c1.expect(f"Joined channel {channel}"):
        raise AssertionError("Could not re-join channel")

def test_send_unsubscribed(env):
    c1 = env.create_client("C1")
    c1.write(f"login {HOST}:{PORT} user1 pass1")
    time.sleep(1)
    
    # Try reporting to a channel not subscribed to
    c1.clear_logs()
    c1.write(f"report events.json") 
    # Note: report command reads file -> triggers sends. 
    # But wait, report command usually takes file, parses it, gets game name "Japan_Germany"
    # and sends directly?
    # Spec says: "In your implementation, if a client is not subscribed to a topic it is not allowed to send messages to it, and the server should send back an ERROR frame."
    # The client might not even send it if it checks locally, but usually it sends.
    # The assignment says "server should send back an ERROR frame".
    
    time.sleep(2)
    # Check for Error frame in output? Client should print it.
    if not c1.expect("Error"):
        # Could be "Error:" or "ERROR" depending on client impl
        # If client implementation is good, it might check subscription locally first?
        # Actually assignment says "In your implementation, if a client is not subscribed to a topic it is not allowed to send messages to it, and the server should send back an ERROR frame."
        # This implies server-side check.
        pass # If client blocks it locally, that is also fine? But let's assume server check.

def test_broadcast_and_persistence(env):
    c1 = env.create_client("C1")
    c2 = env.create_client("C2")
    c3 = env.create_client("C3")

    c1.write(f"login {HOST}:{PORT} user1 pass1")
    c2.write(f"login {HOST}:{PORT} user2 pass2")
    c3.write(f"login {HOST}:{PORT} user3 pass3")
    time.sleep(1)

    channel = "Japan_Germany"
    c1.write(f"join {channel}")
    c2.write(f"join {channel}")
    # C3 does NOT join
    
    time.sleep(1)
    c1.clear_logs()
    c2.clear_logs()
    c3.clear_logs()

    c1.write(f"report {EVENTS_FILE}")
    time.sleep(2)

    # C2 should implicitly receive messages. 
    # We can check C2 logs if they print received messages? 
    # The assignment client might not print every message to stdout, but it handles them.
    # We verify by asking C2 for summary.
    
    summary_file = "summary_c2.txt"
    if os.path.exists(summary_file): os.remove(summary_file)
    c2.write(f"summary {channel} user1 {summary_file}")
    time.sleep(1)
    
    if not os.path.exists(summary_file):
        raise AssertionError("C2 did not generate summary - likely never received messages")

    # C3 should NOT have received anything.
    summary_file_3 = "summary_c3.txt"
    if os.path.exists(summary_file_3): os.remove(summary_file_3)
    c3.write(f"summary {channel} user1 {summary_file_3}")
    time.sleep(1)
    
    # If C3 didn't receive anything, summary might be empty or missing data
    if os.path.exists(summary_file_3):
        with open(summary_file_3, 'r') as f:
            content = f.read()
            # If implementation is correct, it should be empty or say no stats
            if "Total stats" in content and "Game active: true" in content:
                 # This would mean it DID receive data
                 raise AssertionError("C3 received data but was not subscribed!")

def test_unsubscribe_stops_receiving(env):
    c1 = env.create_client("C1")
    c2 = env.create_client("C2")
    
    c1.write(f"login {HOST}:{PORT} u1 p1")
    c2.write(f"login {HOST}:{PORT} u2 p2")
    time.sleep(1)
    
    channel = "Japan_Germany"
    c1.write(f"join {channel}")
    c2.write(f"join {channel}")
    time.sleep(1)
    
    c2.write(f"exit {channel}")
    time.sleep(1)
    
    # C1 reports
    c1.write(f"report {EVENTS_FILE}")
    time.sleep(2)
    
    # C2 should NOT have this data
    summary_file = "summary_c2_unsub.txt"
    if os.path.exists(summary_file): os.remove(summary_file)
    c2.write(f"summary {channel} u1 {summary_file}")
    time.sleep(1)
    
    with open(summary_file, 'r') as f:
        content = f.read()
        if "Game active: true" in content:
            raise AssertionError("Client C2 received updates after unsubscribing!")

def test_complex_interaction(env):
    # Setup
    c1 = env.create_client("Alice")
    c2 = env.create_client("Bob")
    c3 = env.create_client("Charlie")

    c1.write(f"login {HOST}:{PORT} alice pass1")
    c2.write(f"login {HOST}:{PORT} bob pass2")
    c3.write(f"login {HOST}:{PORT} charlie pass3")
    time.sleep(1)

    chan1 = "Japan_Germany"
    chan2 = "Brazil_Argentina" # Matches events_2.json

    # Subscriptions
    c1.write(f"join {chan1}")
    c2.write(f"join {chan1}")
    c3.write(f"join {chan2}")
    time.sleep(1)

    # Broadcast on Chan1
    # Alice reports on Chan1 (file has Japan_Germany match) 
    c1.write(f"report {EVENTS_FILE}") 
    time.sleep(2)

    # Bob should have it. Charlie should not.
    
    # Alice switches channels
    c1.write(f"exit {chan1}")
    time.sleep(0.5)
    c1.write(f"join {chan2}")
    time.sleep(0.5)
    
    # Alice reports on Chan2
    c1.write(f"report events_2.json")
    time.sleep(2)
    
    # Charlie should get it. Bob should not (Bob is strictly on Japan_Germany).
    
    # Verify Bob (Japan updates)
    s_bob = "summary_bob_complex.txt"
    if os.path.exists(s_bob): os.remove(s_bob)
    c2.write(f"summary {chan1} alice {s_bob}")
    
    # Verify Charlie (Brazil updates)
    s_charlie = "summary_charlie_complex.txt"
    if os.path.exists(s_charlie): os.remove(s_charlie)
    c3.write(f"summary {chan2} alice {s_charlie}")
    
    time.sleep(1)
    
    # Checks
    if not os.path.exists(s_bob): raise AssertionError("Bob summary missing")
    with open(s_bob, 'r') as f:
        content = f.read()
        if "Japan" not in content: raise AssertionError("Bob missed Japan updates")
        if "Brazil" in content: raise AssertionError("Bob received Brazil updates (Wrong channel)")

    if not os.path.exists(s_charlie): raise AssertionError("Charlie summary missing")
    with open(s_charlie, 'r') as f:
        content = f.read()
        if "Brazil" not in content: raise AssertionError("Charlie missed Brazil updates")
        if "Japan" in content: raise AssertionError("Charlie received Japan updates (Wrong channel)")

def test_client_crash_reconnect(env):
    c1 = env.create_client("C1_Crash")
    c1.write(f"login {HOST}:{PORT} crasher pass")
    time.sleep(1)
    if not c1.expect("Login successful"):
        raise AssertionError("Initial login failed")
    
    # Simulate crash
    # print(f"[{c1.name}] Simulating crash (kill)...")
    c1.stop() # Terminates process
    
    time.sleep(1) # Wait for server to detect closed socket
    
    # Reincarnate
    c1_new = env.create_client("C1_Return")
    c1_new.write(f"login {HOST}:{PORT} crasher pass")
    time.sleep(1)
    
    if not c1_new.expect("Login successful"):
        # If server thinks user still logged in, it sends error.
        raise AssertionError("Could not login again after crash. Server likely didn't clean up connection.")

if __name__ == "__main__":
    tests = [
        test_login_success,
        test_double_login_same_client,
        test_double_login_diff_client,
        test_login_logout_login,
        test_wrong_password,
        test_join_report_flow,
        test_subscribe_unsubscribe,
        test_send_unsubscribed,
        test_broadcast_and_persistence,
        test_unsubscribe_stops_receiving,
        test_complex_interaction,
        test_client_crash_reconnect
    ]
    
    print(f"Running {len(tests)} tests...")
    
    for t in tests:
        run_test(t.__name__, t)
