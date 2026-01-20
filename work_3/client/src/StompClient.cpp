#include <stdlib.h>
#include <vector>
#include <sstream>
#include <map>
#include <thread>
#include <atomic>
#include <memory>
#include <algorithm>
#include <chrono>
#include "../include/ConnectionHandler.h"
#include "../include/StompFrame.h"
#include "../include/StompProtocol.h"
#include <fstream>
#include "../include/event.h"

/**
* This code assumes that the server replies the exact text the client sent it (as opposed to the practical session example)
*/
int handleInput(std::string& input, std::shared_ptr<ConnectionHandler>& connectionHandler, std::thread& reader,
                std::atomic<bool>& readerRunning, StompProtocol& protocol);

std::string parseUserFromBody(const std::string& body) {
    std::istringstream iss(body);
    std::string line;
    while (std::getline(iss, line)) {
        if (line.rfind("user:", 0) == 0) {
            std::string user = line.substr(5);
            while (!user.empty() && (user.front() == ' ' || user.front() == '\t')) {
                user.erase(0, 1);
            }
            while (!user.empty() && (user.back() == ' ' || user.back() == '\t' || user.back() == '\r')) {
                user.pop_back();
            }
            return user;
        }
    }
    return "";
}

void readerThread(std::shared_ptr<ConnectionHandler> connectionHandler, StompProtocol& protocol, std::atomic<bool>& readerRunning) {
    while (true) {
        std::string answer;
        if (!connectionHandler->getFrameAscii(answer, '\0')) {
             std::cout << "Disconnected from server." << std::endl;
             protocol.setLoggedIn(false);
             break;
        }

        if (protocol.isDebug()) {
            std::cout << "[DEBUG] Received frame:\n" << answer << std::endl;
        }

        StompFrame frame = StompFrame::parse(answer);
        if (frame.getCommand() == "MESSAGE") {

            std::string reporter = parseUserFromBody(frame.getBody());
            if (reporter.empty()) {
                reporter = "unknown";
            }
            if (protocol.getUserName() == reporter) continue;
            Event event(frame.getBody());
            protocol.addEvent(event, reporter);
        } else if (frame.getCommand() == "CONNECTED") {
             std::cout << "Login successful" << std::endl;
             protocol.setLoggedIn(true);
        } else if (frame.getCommand() == "ERROR") {
             std::cout << "Error: " << frame.getHeader("message") << "\n" << frame.getBody() << std::endl;
             protocol.setLoggedIn(false);
             connectionHandler->close();
             break;
        } else if (frame.getCommand() == "RECEIPT") {
             try {
                int receiptId = std::stoi(frame.getHeader("receipt-id"));
                std::pair<std::string, std::string> action;
                if (protocol.popReceiptAction(receiptId, action)) {
                    if (action.first == "join") {
                        std::cout << "Joined channel " << action.second << std::endl;
                    } else if (action.first == "exit") {
                        std::cout << "Exited channel " << action.second << std::endl;
                    }
                }
                if (receiptId == protocol.getDisconnectReceiptId()) {
                    protocol.setLoggedIn(false);
                    connectionHandler->close();
                    break;
                }
             } catch (...) {}
        } else {
             std::cout << answer << std::endl;
        }
    }
    readerRunning.store(false);
}

int main (int argc, char *argv[]) {
    StompProtocol protocol;
    std::shared_ptr<ConnectionHandler> connectionHandler;
    std::thread reader;
    std::atomic<bool> readerRunning(false);
	
	//From here we will see the rest of the ehco client implementation:
    while (1) {
        const short bufsize = 1024;
        char buf[bufsize];
        std::cin.getline(buf, bufsize);
		std::string line(buf);
		
        int status = handleInput(line, connectionHandler, reader, readerRunning, protocol);
        if (status == -1) {
            break;
        }
    }
    
    if (connectionHandler) {
        connectionHandler->close();
    }
    if(reader.joinable()) reader.join();
    return 0;
}

enum class Command {
    LOGIN,
    JOIN,
    EXIT,
    REPORT,
    SUMMARY,
    LOGOUT,
    STATS,
    DEBUG,
    UNKNOWN
};

Command getCommand(const std::string& commandStr) {
    if (commandStr == "login") return Command::LOGIN;
    if (commandStr == "join") return Command::JOIN;
    if (commandStr == "exit") return Command::EXIT;
    if (commandStr == "report") return Command::REPORT;
    if (commandStr == "summary") return Command::SUMMARY;
    if (commandStr == "logout") return Command::LOGOUT;
    if (commandStr == "stats") return Command::STATS;
    if (commandStr == "debug") return Command::DEBUG;
    return Command::UNKNOWN;
}

// Helper function to send frames and optionally print debug info
bool sendFrame(const StompFrame& frame, std::shared_ptr<ConnectionHandler>& connectionHandler, StompProtocol& protocol) {
    if (protocol.isDebug()) {
        std::cout << "[DEBUG] Sending frame:\n" << frame.toString() << std::endl;
    }
    if (!connectionHandler) return false;
    return connectionHandler->sendFrameAscii(frame.toString(), '\0');
}

void handleLogin(std::shared_ptr<ConnectionHandler>& connectionHandler, std::thread& reader,
                 std::atomic<bool>& readerRunning, const std::vector<std::string>& words, StompProtocol& protocol) {
    if (protocol.getLoggedIn()) {
        std::cout << "The client is already logged in, log out before trying again" << std::endl;
        return;
    }
    if (words.size() < 4) {
        std::cout << "Error: Invalid command format." << std::endl;
        return;
    }
    
    std::string hostPort = words[1];
    size_t colonPos = hostPort.find(':');
    if (colonPos == std::string::npos) {
        std::cout << "Error: Invalid host:port format." << std::endl;
        return;
    }
    std::string host = hostPort.substr(0, colonPos);
    short port;
    try {
        port = static_cast<short>(std::stoi(hostPort.substr(colonPos + 1)));
    } catch (...) {
        std::cout << "Error: Invalid port." << std::endl;
        return;
    }

    std::string username = words[2];
    std::string password = words[3];

    // Clear previous session data/state before new login
    protocol.clear();
    protocol.setUserName(username);

    if (!connectionHandler || !readerRunning.load()) {
        if (reader.joinable()) {
            reader.join();
        }
        connectionHandler = std::make_shared<ConnectionHandler>(host, port);
        if (!connectionHandler->connect()) {
            std::cout << "Could not connect to server" << std::endl;
            connectionHandler.reset();
            return;
        }
        if (!readerRunning.load()) {
            readerRunning.store(true);
            reader = std::thread(readerThread, connectionHandler, std::ref(protocol), std::ref(readerRunning));
        }
    }

    StompFrame frame("CONNECT");
    frame.addHeader("accept-version", "1.2");
    frame.addHeader("host", "stomp.cs.bgu.ac.il");
    frame.addHeader("login", username);
    frame.addHeader("passcode", password);

    if (!sendFrame(frame, connectionHandler, protocol)) {
         std::cout << "Could not connect to server" << std::endl;
    }
}

void handleJoin(std::shared_ptr<ConnectionHandler>& connectionHandler, const std::vector<std::string>& words, StompProtocol& protocol) {
    if (!protocol.getLoggedIn()) {
        std::cout << "Not logged in" << std::endl;
        return;
    }
    if (words.size() < 2) {
        std::cout << "Error: Invalid command format." << std::endl;
        return;
    }
    std::string game_name = words[1];
    
    int subId = protocol.generateSubscriptionId();
    protocol.addSubscription(game_name, subId);
    
    int receiptId = protocol.generateReceiptId();
    protocol.addReceiptAction(receiptId, "join", game_name);

    StompFrame frame("SUBSCRIBE");
    frame.addHeader("destination", "/" + game_name);
    frame.addHeader("id", std::to_string(subId));
    frame.addHeader("receipt", std::to_string(receiptId));
    
    if (!sendFrame(frame, connectionHandler, protocol)) {
         std::cout << "Could not join game" << std::endl;
         protocol.removeSubscription(game_name);
         return;
    }
}

void handleExit(std::shared_ptr<ConnectionHandler>& connectionHandler, const std::vector<std::string>& words, StompProtocol& protocol) {
     if (!protocol.getLoggedIn()) {
        std::cout << "Not logged in" << std::endl;
        return;
    }
    if (words.size() < 2) {
        std::cout << "Error: Invalid command format." << std::endl;
        return;
    }
    std::string game_name = words[1];
    
    int subId = protocol.getSubscriptionId(game_name);
    if (subId == -1) {
         std::cout << "Not subscribed to " << game_name << std::endl;
         return;
    }
    protocol.removeSubscription(game_name);
    
    int receiptId = protocol.generateReceiptId();
    protocol.addReceiptAction(receiptId, "exit", game_name);

    StompFrame frame("UNSUBSCRIBE");
    frame.addHeader("id", std::to_string(subId));
    frame.addHeader("receipt", std::to_string(receiptId));
    
    if (!sendFrame(frame, connectionHandler, protocol)) {
         std::cout << "Could not exit game" << std::endl;
    }
}

void handleReport(std::shared_ptr<ConnectionHandler>& connectionHandler, const std::vector<std::string>& words, StompProtocol& protocol) {
     if (!protocol.getLoggedIn()) {
        std::cout << "Not logged in" << std::endl;
        return;
    }
    if (words.size() < 2) {
        std::cout << "Error: Invalid command format." << std::endl;
        return;
    }
    std::string file_path = words[1];
    
    names_and_events data;
    try {
        data = parseEventsFile(file_path);
    } catch (const std::exception& e) {
        std::cout << "Error: Failed to parse events file '" << file_path << "': " << e.what() << std::endl;
        return;
    }

    std::string game_name = data.team_a_name + "_" + data.team_b_name;
    if (!protocol.isSubscribed(game_name)) {
        std::cout << "Not subscribed to " << game_name << std::endl;
        return;
    }
    
    for (const Event& event : data.events) {
        protocol.addEvent(event, protocol.getUserName());
        
        StompFrame frame("SEND");
        frame.addHeader("destination", "/" + game_name);
        frame.addHeader("file", file_path);
        
        std::string body = "user: " + protocol.getUserName() + "\n";
        body += "team a: " + data.team_a_name + "\n";
        body += "team b: " + data.team_b_name + "\n";
        body += "event name: " + event.get_name() + "\n";
        body += "time: " + std::to_string(event.get_time()) + "\n";
        body += "general game updates:\n";
        for (auto const& pair : event.get_game_updates()) {
            body += pair.first + ":" + pair.second + "\n";
        }
        body += "team a updates:\n";
        for (auto const& pair : event.get_team_a_updates()) {
            body += pair.first + ":" + pair.second + "\n";
        }
        body += "team b updates:\n";
        for (auto const& pair : event.get_team_b_updates()) {
            body += pair.first + ":" + pair.second + "\n";
        }
        body += "description:\n" + event.get_discription();
        
        frame.setBody(body);
             
        if (!sendFrame(frame, connectionHandler, protocol)) {
             std::cout << "Could not send report" << std::endl;
        }
    }
    std::cout << "Report sent" << std::endl;
}

void handleSummary(std::shared_ptr<ConnectionHandler>& connectionHandler, const std::vector<std::string>& words, StompProtocol& protocol) {
     if (!protocol.getLoggedIn()) {
        std::cout << "Not logged in" << std::endl;
        return;
    }
    if (words.size() < 4) {
        std::cout << "Error: Invalid command format." << std::endl;
        return;
    }
    std::string game_name = words[1];
    std::string user_name = words[2];
    std::string file_path = words[3];
    
    GameState state;
    protocol.getGameState(game_name, user_name, state); // Returns bool, but we default to empty state if not found
    std::ofstream outfile(file_path);
    if (!outfile.is_open()) {
         std::cout << "Could not open file " << file_path << std::endl;
         return;
    }
    
    std::string team_a = state.teamA;
    std::string team_b = state.teamB;
    if (team_a.empty() || team_b.empty()) {
         size_t underscore = game_name.find('_');
         if (underscore != std::string::npos) {
             team_a = game_name.substr(0, underscore);
             team_b = game_name.substr(underscore + 1);
         }
    }

    outfile << team_a << " vs " << team_b << "\n";
    outfile << "Game stats:\n";
    outfile << "General stats:\n";
    for (auto const& pair : state.generalStats) {
        outfile << pair.first << ": " << pair.second << "\n";
    }
    outfile << team_a << " stats:\n";
    for (auto const& pair : state.teamAStats) {
        outfile << pair.first << ": " << pair.second << "\n";
    }
    outfile << team_b << " stats:\n";
    for (auto const& pair : state.teamBStats) {
        outfile << pair.first << ": " << pair.second << "\n";
    }
    outfile << "Game event reports:\n";
    std::vector<GameEvent> sortedEvents = state.events;
    std::sort(sortedEvents.begin(), sortedEvents.end(), [](const GameEvent& a, const GameEvent& b) {
        if (a.halfIndex != b.halfIndex) {
            return a.halfIndex < b.halfIndex;
        }
        if (a.event.get_time() != b.event.get_time()) {
            return a.event.get_time() < b.event.get_time();
        }
        return a.sequence < b.sequence;
    });
    for (const GameEvent& entry : sortedEvents) {
        const Event& e = entry.event;
        outfile << e.get_time() << " - " << e.get_name() << ":\n";
        outfile << e.get_discription() << "\n\n";
    }
    outfile.close();
    std::cout << "Summary created" << std::endl;
}

void handleStats(std::shared_ptr<ConnectionHandler>& connectionHandler, const std::vector<std::string>& words, StompProtocol& protocol) {
    if (!protocol.getLoggedIn()) {
        std::cout << "Not logged in" << std::endl;
        return;
    }

    StompFrame frame("REPORT");
    // No destination header needed for the custom REPORT command
    
    // Add receipt header to get confirmation
    int receiptId = protocol.generateReceiptId();
    frame.addHeader("receipt", std::to_string(receiptId));

    if (!sendFrame(frame, connectionHandler, protocol)) {
         std::cout << "Could not send stats request" << std::endl;
    }
}

void handleLogout(std::shared_ptr<ConnectionHandler>& connectionHandler, const std::vector<std::string>& words, StompProtocol& protocol) {
    if (!protocol.getLoggedIn()) {
        std::cout << "Not logged in" << std::endl;
        return;
    }
    
    int receiptId = protocol.generateReceiptId(); 
    protocol.setDisconnectReceiptId(receiptId);
    
    StompFrame frame("DISCONNECT");
    frame.addHeader("receipt", std::to_string(receiptId));
    
    if (!sendFrame(frame, connectionHandler, protocol)) {
         std::cout << "Could not send logout" << std::endl;
         return;
    }
    
    std::cout << "Logging out..." << std::endl;
    // Timeout loop (approx 10 seconds)
    int timeout = 100; 
    while(protocol.getLoggedIn() && timeout > 0) {
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
        timeout--;
    }
    if (timeout == 0) {
        std::cout << "Logout timed out. Forcing disconnect." << std::endl;
        protocol.setLoggedIn(false);
        // Ensure the underlying connection is cleaned up when forcing disconnect
        if (connectionHandler) {
            connectionHandler.reset();
        }
    }
    std::cout << "Logged out." << std::endl;
}

void handleDebug(const std::vector<std::string>& words, StompProtocol& protocol) {
    if (words.size() < 2) {
        std::cout << "Error: Invalid command format. Usage: debug <on/off>" << std::endl;
        return;
    }
    if (words[1] == "on") {
        protocol.setDebug(true);
        std::cout << "Debug mode activated" << std::endl;
    } else if (words[1] == "off") {
        protocol.setDebug(false);
        std::cout << "Debug mode deactivated" << std::endl;
    } else {
        std::cout << "Error: Invalid argument. Usage: debug <on/off>" << std::endl;
    }
}

int handleInput(std::string& input, std::shared_ptr<ConnectionHandler>& connectionHandler, std::thread& reader,
                std::atomic<bool>& readerRunning, StompProtocol& protocol) {
    // split input into words by spaces
    std::istringstream iss(input);
    std::vector<std::string> words;
    std::string word;
    while (iss >> word) {
        words.push_back(word);
    }
    
    if (words.empty()) {
        return 0; 
    }

    Command cmd = getCommand(words.at(0));
    switch(cmd){
        case Command::LOGIN:
            handleLogin(connectionHandler, reader, readerRunning, words, protocol);
            break;
        case Command::JOIN:
             handleJoin(connectionHandler, words, protocol);
             break;
        case Command::EXIT:
             handleExit(connectionHandler, words, protocol);
             break;
        case Command::REPORT:
             handleReport(connectionHandler, words, protocol);
             break;
        case Command::SUMMARY:
             handleSummary(connectionHandler, words, protocol);
             break;
        case Command::STATS:
             handleStats(connectionHandler, words, protocol);
             break;
        case Command::DEBUG:
             handleDebug(words, protocol);
             break;
        case Command::LOGOUT:
             handleLogout(connectionHandler, words, protocol);
             break;
        default:
            std::cout << "Unknown command: " << words.at(0) << std::endl;
            break;
    }

    return 0;
}
