#!/bin/bash

# DJ Track Session Manager - Test Script
# This script tests the program output against expected output files

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test results counters
TESTS_PASSED=0
TESTS_FAILED=0

# Paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN_DIR="${SCRIPT_DIR}/bin"
EXECUTABLE="${BIN_DIR}/dj_manager"

# Output files - Main tests (uses bin/dj_config.txt)
EXPECTED_TEST_OUTPUT="${SCRIPT_DIR}/test_output.txt"
EXPECTED_INTERACTIVE_OUTPUT="${SCRIPT_DIR}/interactive_play_all_output.txt"
EXPECTED_INTERACTIVE_FIRST_INPUT="${SCRIPT_DIR}/interactive_play_all_output_first_input.txt"

# Output files - Input 2 tests (uses input_2/dj_config.txt)
INPUT2_DIR="${SCRIPT_DIR}/input_2"
EXPECTED_INPUT2_INTERACTIVE_OUTPUT="${INPUT2_DIR}/interactive_play_all_output.txt"
EXPECTED_INPUT2_FIRST_INPUT="${INPUT2_DIR}/interactive_play_all_output_second_input.txt"

# Temp files for actual output
ACTUAL_TEST_OUTPUT=$(mktemp)
ACTUAL_INTERACTIVE_OUTPUT=$(mktemp)

# Cleanup function
cleanup() {
    rm -f "$ACTUAL_TEST_OUTPUT" "$ACTUAL_INTERACTIVE_OUTPUT"
}
trap cleanup EXIT

# Print header
print_header() {
    echo -e "${BLUE}=================================================="
    echo -e "    DJ TRACK SESSION MANAGER - TEST SUITE"
    echo -e "==================================================${NC}"
    echo ""
}

# Print test result
print_result() {
    local test_name="$1"
    local passed="$2"
    
    if [ "$passed" -eq 1 ]; then
        echo -e "${GREEN}[PASS]${NC} $test_name"
        ((TESTS_PASSED++))
    else
        echo -e "${RED}[FAIL]${NC} $test_name"
        ((TESTS_FAILED++))
    fi
}

# Compare files and show differences if any
compare_outputs() {
    local expected="$1"
    local actual="$2"
    local test_name="$3"
    local show_diff="$4"
    
    if [ ! -f "$expected" ]; then
        echo -e "${YELLOW}[WARN]${NC} Expected output file not found: $expected"
        return 1
    fi
    
    # Normalize both files by replacing pointer addresses (0x...) with a placeholder
    local expected_normalized=$(mktemp)
    local actual_normalized=$(mktemp)
    
    # Replace hex addresses like 0x7ffd088eda98 with 0xADDRESS
    sed 's/0x[0-9a-fA-F]\+/0xADDRESS/g' "$expected" > "$expected_normalized"
    sed 's/0x[0-9a-fA-F]\+/0xADDRESS/g' "$actual" > "$actual_normalized"
    
    if diff -q "$expected_normalized" "$actual_normalized" > /dev/null 2>&1; then
        rm -f "$expected_normalized" "$actual_normalized"
        return 0
    else
        if [ "$show_diff" = "true" ]; then
            echo ""
            echo -e "${YELLOW}--- Differences found in $test_name ---${NC}"
            echo -e "${YELLOW}Expected output: $expected${NC}"
            echo -e "${YELLOW}Actual output: $actual${NC}"
            echo ""
            # Show first 100 lines of diff (using normalized versions)
            diff --color=auto -u "$expected_normalized" "$actual_normalized" | head -100
            echo ""
            echo -e "${YELLOW}(Showing first 100 lines of diff, pointer addresses normalized)${NC}"
            echo ""
        fi
        rm -f "$expected_normalized" "$actual_normalized"
        return 1
    fi
}

# Build the project
build_project() {
    echo -e "${BLUE}Building project with DEBUG flags...${NC}"
    cd "$SCRIPT_DIR"
    
    # Use 'make debug' to enable DEBUG macro which is required for expected output
    if make clean > /dev/null 2>&1 && make debug > /dev/null 2>&1; then
        echo -e "${GREEN}Build successful (DEBUG mode)${NC}"
        echo ""
        return 0
    else
        echo -e "${RED}Build failed${NC}"
        make debug 2>&1 | tail -20
        return 1
    fi
}

# Test 1: Testing mode (without -I flag)
test_testing_mode() {
    echo -e "${BLUE}--- Test 1: Testing Mode (no flags) ---${NC}"
    
    if [ ! -f "$EXPECTED_TEST_OUTPUT" ]; then
        echo -e "${YELLOW}[SKIP]${NC} Expected output file not found: $EXPECTED_TEST_OUTPUT"
        return
    fi
    
    # Run from SCRIPT_DIR (work_1) so that bin/dj_config.txt path works
    cd "$SCRIPT_DIR"
    ./bin/dj_manager > "$ACTUAL_TEST_OUTPUT" 2>&1
    
    if compare_outputs "$EXPECTED_TEST_OUTPUT" "$ACTUAL_TEST_OUTPUT" "Testing Mode" "true"; then
        print_result "Testing Mode output matches expected" 1
    else
        print_result "Testing Mode output matches expected" 0
    fi
}

# Test 2: Interactive mode with -I -A flags (play all playlists)
test_interactive_mode_all() {
    echo ""
    echo -e "${BLUE}--- Test 2: Interactive Mode (-I -A flags) ---${NC}"
    
    if [ ! -f "$EXPECTED_INTERACTIVE_OUTPUT" ]; then
        echo -e "${YELLOW}[SKIP]${NC} Expected output file not found: $EXPECTED_INTERACTIVE_OUTPUT"
        return
    fi
    
    # Run from SCRIPT_DIR (work_1) so that bin/dj_config.txt path works
    cd "$SCRIPT_DIR"
    ./bin/dj_manager -I -A > "$ACTUAL_INTERACTIVE_OUTPUT" 2>&1
    
    if compare_outputs "$EXPECTED_INTERACTIVE_OUTPUT" "$ACTUAL_INTERACTIVE_OUTPUT" "Interactive Mode (-I -A)" "true"; then
        print_result "Interactive Mode (-I -A) output matches expected" 1
    else
        print_result "Interactive Mode (-I -A) output matches expected" 0
    fi
}

# Test 3: Interactive mode with first playlist selection (echo "1" | ./dj_manager -I)
test_interactive_mode_first_input() {
    echo ""
    echo -e "${BLUE}--- Test 3: Interactive Mode with first playlist selection ---${NC}"
    
    if [ ! -f "$EXPECTED_INTERACTIVE_FIRST_INPUT" ]; then
        echo -e "${YELLOW}[SKIP]${NC} Expected output file not found: $EXPECTED_INTERACTIVE_FIRST_INPUT"
        return
    fi
    
    # Run from SCRIPT_DIR (work_1) so that bin/dj_config.txt path works
    cd "$SCRIPT_DIR"
    # Simulate selecting first playlist repeatedly and then exit
    # The -A flag plays all playlists automatically
    echo -e "1\n1\n1\nq" | ./bin/dj_manager -I > "$ACTUAL_INTERACTIVE_OUTPUT" 2>&1
    
    if compare_outputs "$EXPECTED_INTERACTIVE_FIRST_INPUT" "$ACTUAL_INTERACTIVE_OUTPUT" "Interactive Mode (first input)" "true"; then
        print_result "Interactive Mode (first input) output matches expected" 1
    else
        print_result "Interactive Mode (first input) output matches expected" 0
    fi
}

# Test 4: Interactive mode with input_2 config
test_interactive_mode_input2() {
    echo ""
    echo -e "${BLUE}--- Test 4: Interactive Mode with input_2 config ---${NC}"
    
    if [ ! -f "$EXPECTED_INPUT2_INTERACTIVE_OUTPUT" ]; then
        echo -e "${YELLOW}[SKIP]${NC} Expected output file not found: $EXPECTED_INPUT2_INTERACTIVE_OUTPUT"
        return
    fi
    
    # Copy the input_2 config to bin directory temporarily
    local backup_config="${BIN_DIR}/dj_config.txt.bak"
    cp "${BIN_DIR}/dj_config.txt" "$backup_config"
    cp "${INPUT2_DIR}/dj_config.txt" "${BIN_DIR}/dj_config.txt"
    
    # Run from SCRIPT_DIR (work_1) so that bin/dj_config.txt path works
    cd "$SCRIPT_DIR"
    ./bin/dj_manager -I -A > "$ACTUAL_INTERACTIVE_OUTPUT" 2>&1
    
    # Restore original config
    mv "$backup_config" "${BIN_DIR}/dj_config.txt"
    
    if compare_outputs "$EXPECTED_INPUT2_INTERACTIVE_OUTPUT" "$ACTUAL_INTERACTIVE_OUTPUT" "Interactive Mode (input_2)" "true"; then
        print_result "Interactive Mode (input_2) output matches expected" 1
    else
        print_result "Interactive Mode (input_2) output matches expected" 0
    fi
}

# Test 5: Memory leak test with valgrind (if available)
test_memory_leaks() {
    echo ""
    echo -e "${BLUE}--- Test 5: Memory Leak Detection (valgrind) ---${NC}"
    
    if ! command -v valgrind &> /dev/null; then
        echo -e "${YELLOW}[SKIP]${NC} valgrind not installed. Run 'make install-deps' to install."
        return
    fi
    
    # Run from SCRIPT_DIR (work_1) so that bin/dj_config.txt path works
    cd "$SCRIPT_DIR"
    local valgrind_output=$(mktemp)
    
    valgrind --leak-check=full --show-leak-kinds=all --error-exitcode=1 \
        ./bin/dj_manager > /dev/null 2> "$valgrind_output"
    local exit_code=$?
    
    # Check for memory leaks
    if grep -q "definitely lost: 0 bytes" "$valgrind_output" && \
       grep -q "indirectly lost: 0 bytes" "$valgrind_output"; then
        print_result "No memory leaks detected" 1
    else
        print_result "No memory leaks detected" 0
        echo ""
        echo -e "${YELLOW}--- Valgrind Output ---${NC}"
        grep -E "(definitely|indirectly|possibly) lost:" "$valgrind_output"
        echo ""
    fi
    
    rm -f "$valgrind_output"
}

# Test 5: Check executable exists
test_executable_exists() {
    echo -e "${BLUE}--- Test 0: Executable Check ---${NC}"
    
    if [ -f "$EXECUTABLE" ] && [ -x "$EXECUTABLE" ]; then
        print_result "Executable exists and is runnable" 1
    else
        print_result "Executable exists and is runnable" 0
        echo -e "${RED}Error: Cannot find or execute $EXECUTABLE${NC}"
        echo -e "${YELLOW}Run 'make' first to build the project${NC}"
        exit 1
    fi
    echo ""
}

# Print summary
print_summary() {
    echo ""
    echo -e "${BLUE}=================================================="
    echo -e "                 TEST SUMMARY"
    echo -e "==================================================${NC}"
    echo -e "Tests passed: ${GREEN}$TESTS_PASSED${NC}"
    echo -e "Tests failed: ${RED}$TESTS_FAILED${NC}"
    echo ""
    
    if [ "$TESTS_FAILED" -eq 0 ]; then
        echo -e "${GREEN}All tests passed! 🎉${NC}"
        return 0
    else
        echo -e "${RED}Some tests failed. Please review the differences above.${NC}"
        return 1
    fi
}

# Quick test - just run and check exit code
quick_test() {
    echo -e "${BLUE}--- Quick Test: Basic Execution ---${NC}"
    
    # Run from SCRIPT_DIR (work_1) so that bin/dj_config.txt path works
    cd "$SCRIPT_DIR"
    if ./bin/dj_manager > /dev/null 2>&1; then
        print_result "Program executes without crashing" 1
    else
        print_result "Program executes without crashing" 0
    fi
}

# Help message
show_help() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -h, --help     Show this help message"
    echo "  -b, --build    Build before testing"
    echo "  -q, --quick    Quick test (just check execution)"
    echo "  -m, --memtest  Include memory leak test"
    echo "  -a, --all      Run all tests including memory check"
    echo "  -t, --test     Run testing mode test only"
    echo "  -i, --interactive  Run interactive mode test only"
    echo "  -2, --input2   Run input_2 config tests"
    echo "  -v, --verbose  Show full diff output (not truncated)"
    echo ""
    echo "Examples:"
    echo "  $0             # Run standard tests"
    echo "  $0 -b          # Build and run tests"
    echo "  $0 -a          # Run all tests including memory check"
    echo "  $0 -q          # Quick execution test only"
    echo "  $0 -t          # Test mode only"
    echo "  $0 -i          # Interactive mode tests only"
    echo "  $0 -2          # Input 2 config test"
}

# Main
main() {
    local do_build=false
    local do_memtest=false
    local do_quick=false
    local test_mode_only=false
    local interactive_only=false
    local input2_only=false
    
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -b|--build)
                do_build=true
                shift
                ;;
            -m|--memtest)
                do_memtest=true
                shift
                ;;
            -q|--quick)
                do_quick=true
                shift
                ;;
            -a|--all)
                do_memtest=true
                shift
                ;;
            -t|--test)
                test_mode_only=true
                shift
                ;;
            -i|--interactive)
                interactive_only=true
                shift
                ;;
            -2|--input2)
                input2_only=true
                shift
                ;;
            *)
                echo "Unknown option: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    print_header
    
    # Build if requested
    if [ "$do_build" = true ]; then
        if ! build_project; then
            exit 1
        fi
    fi
    
    # Check executable
    test_executable_exists
    
    # Quick test only
    if [ "$do_quick" = true ]; then
        quick_test
        print_summary
        exit $?
    fi
    
    # Run specific tests or all
    if [ "$test_mode_only" = true ]; then
        test_testing_mode
    elif [ "$interactive_only" = true ]; then
        test_interactive_mode_all
        test_interactive_mode_first_input
    elif [ "$input2_only" = true ]; then
        test_interactive_mode_input2
    else
        # Run all standard tests
        test_testing_mode
        test_interactive_mode_all
        test_interactive_mode_first_input
        test_interactive_mode_input2
        
        # Memory test if requested
        if [ "$do_memtest" = true ]; then
            test_memory_leaks
        fi
    fi
    
    print_summary
}

main "$@"
