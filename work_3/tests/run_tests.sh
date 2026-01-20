#!/bin/bash

# Configuration
SERVER_HOST="127.0.0.1"
SERVER_PORT=7777 # Standard Stomp Port
PYTHON_DB_PORT=7778
WORK_DIR="/workspace/work_3"
CLIENT_BIN="$WORK_DIR/client/StompWCIClient"
SERVER_BIN_DIR="$WORK_DIR/server"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "=================================================="
echo "      STOMP SERVER & CLIENT TEST SUITE"
echo "=================================================="

# Function to start the python DB server
start_db_server() {
    echo "Starting Python DB Server..."
    python3 "$WORK_DIR/data/sql_server.py" &
    DB_PID=$!
    sleep 2 # Give it time to start
}

# Function to start the Java STOMP server (Reactor or TPC)
start_java_server() {
    TYPE=$1 # "tpc" or "reactor"
    echo "Starting Java STOMP Server ($TYPE)..."
    cd "$SERVER_BIN_DIR"
    mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.stomp.StompServer" -Dexec.args="7777 $TYPE" > server.log 2>&1 &
    SERVER_PID=$!
    cd - > /dev/null
    sleep 3 # Give it time to start
}

# Cleanup function
cleanup() {
    echo "Cleaning up..."
    kill $DB_PID 2>/dev/null
    kill $SERVER_PID 2>/dev/null
    rm -f server.log client_*.log input_*.txt
}

trap cleanup EXIT

# ----------------------------------------------------
#               TEST SCENARIOS
# ----------------------------------------------------

check_output() {
    FILE=$1
    PATTERN=$2
    TEST_NAME=$3

    if grep -q "$PATTERN" "$FILE"; then
        echo -e "${GREEN}[PASS] ${TEST_NAME}${NC}"
    else
        echo -e "${RED}[FAIL] ${TEST_NAME}${NC}"
        echo "Expected pattern: '$PATTERN' in file $FILE"
    fi
}

run_test_client() {
    INPUT_FILE=$1
    OUTPUT_FILE=$2
    
    # Run client with input redirection
    # Assuming client takes host port as args
    # Note: stdin is pipe to allow dynamic interaction simulation if needed, 
    # but here we dump a file.
    $CLIENT_BIN $SERVER_HOST $SERVER_PORT < "$INPUT_FILE" > "$OUTPUT_FILE" 2>&1 &
    CLIENT_PID=$!
    wait $CLIENT_PID
}

# --- TEST 1: Basic Login & Subscribe (Success) ---
test_basic_login() {
    echo ">> Running Test 1: Basic Login & Subscribe"
    cat <<EOF > input_1.txt
login 127.0.0.1:7777 user1 pass1
join germany_spain
exit
EOF
    
    run_test_client input_1.txt output_1.log
    
    check_output output_1.log "Login successful" "Test 1 - Login Msg"
    check_output output_1.log "Joined channel germany_spain" "Test 1 - Join Msg"
}

# --- TEST 2: Wrong Password ---
test_wrong_password() {
    echo ">> Running Test 2: Wrong Password"
    # First ensure user1 exists (from Test 1)
    
    cat <<EOF > input_2.txt
login 127.0.0.1:7777 user1 wrongpass
exit
EOF
    
    run_test_client input_2.txt output_2.log
    
    # Expect error frame or client message "Login failed"
    check_output output_2.log "Wrong password" "Test 2 - Wrong Password Rejection"
}

# --- TEST 3: Double Login (Same User) ---
test_double_login() {
    echo ">> Running Test 3: Double Login"
    
    # Client A logs in and stays
    mkfifo input_pipe
    $CLIENT_BIN $SERVER_HOST $SERVER_PORT < input_pipe > output_3a.log 2>&1 &
    PID_A=$!
    
    echo "login 127.0.0.1:7777 user1 pass1" > input_pipe
    sleep 1
    
    # Client B tries to log in as user1
    cat <<EOF > input_3b.txt
login 127.0.0.1:7777 user1 pass1
exit
EOF
    run_test_client input_3b.txt output_3b.log
    
    check_output output_3b.log "User already logged in" "Test 3 - Double Login Rejection"
    
    # Clean up Client A
    echo "logout" > input_pipe
    sleep 1
    rm input_pipe
    kill $PID_A 2>/dev/null
}

# --- TEST 4: Subscription & Messaging (Two Clients) ---
test_messaging() {
    echo ">> Running Test 4: Messaging between two clients"
    
    # Setup pipes for interactive control
    CLI_A_IN="pipe_a"
    CLI_B_IN="pipe_b"
    mkfifo $CLI_A_IN $CLI_B_IN
    
    # Start Client A (User 3)
    $CLIENT_BIN $SERVER_HOST $SERVER_PORT < $CLI_A_IN > output_4a.log 2>&1 &
    PID_A=$!
    
    # Start Client B (User 4)
    $CLIENT_BIN $SERVER_HOST $SERVER_PORT < $CLI_B_IN > output_4b.log 2>&1 &
    PID_B=$!
    
    sleep 1
    
    # 1. Login both
    echo "login 127.0.0.1:7777 user3 pass3" > $CLI_A_IN
    echo "login 127.0.0.1:7777 user4 pass4" > $CLI_B_IN
    sleep 1
    
    # 2. Both Join 'sports'
    echo "join sports" > $CLI_A_IN
    echo "join sports" > $CLI_B_IN
    sleep 1
    
    # 3. Client A reports a game (sends json event)
    # Since the client usually parses a file, we might simulate a 'report' command if implemented
    # Or just send a frame if the client accepts raw input. 
    # WARNING: Your client reads specific file format for reports. 
    # We will assume 'report' command works with a file provided in the test folder.
    
    # Create dummy json file
    echo '{ "team a": "JAPAN", "team b": "CHINA", "events": [] }' > test_game.json
    
    # Assuming syntax: report <file>
    echo "report test_game.json" > $CLI_A_IN
    sleep 2
    
    # 4. Check if Client B received it
    # Client B output should contain the user3 sent message
    check_output output_4b.log "user3" "Test 4 - Msg Received by Subscriber"
    check_output output_4b.log "JAPAN" "Test 4 - Content Verified"
    
    # Cleanup
    echo "logout" > $CLI_A_IN
    echo "logout" > $CLI_B_IN
    sleep 1
    
    rm $CLI_A_IN $CLI_B_IN test_game.json
    kill $PID_A $PID_B 2>/dev/null
}


# --- EXECUTION ---

# Build everything first
echo "Building project..."
cd "$WORK_DIR/server" && mvn clean compile package -DskipTests > /dev/null 2>&1
cd "$WORK_DIR/client" && make > /dev/null 2>&1

start_db_server
start_java_server "tpc"

# Run Tests
test_basic_login
test_wrong_password
# Note: Double login test requires more complex pipe handling, skipped for basic run
# test_double_login 
test_messaging

echo "=================================================="
echo "               TESTS COMPLETED"
echo "=================================================="
