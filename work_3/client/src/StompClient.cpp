#include <stdlib.h>
#include <vector>
#include <sstream>
#include <map>
#include <thread>
#include <atomic>
#include "../include/ConnectionHandler.h"
#include "../include/StompFrame.h"
#include "../include/StompProtocol.h"
#include <fstream>
#include "../include/event.h"

/**
* This code assumes that the server replies the exact text the client sent it (as opposed to the practical session example)
*/
int handleInput(std::string& input, ConnectionHandler& connectionHandler, StompProtocol& protocol);

void readerThread(ConnectionHandler& connectionHandler, StompProtocol& protocol) {
    while (true) {
        std::string answer;
        if (!connectionHandler.getFrameAscii(answer, '\0')) {
             std::cout << "Disconnected from server." << std::endl;
             protocol.setLoggedIn(false);
             break;
        }
        StompFrame frame = StompFrame::parse(answer);
        if (frame.getCommand() == "MESSAGE") {
            std::cout << "Received Message: " << frame.getBody() << std::endl;
        } else if (frame.getCommand() == "CONNECTED") {
             std::cout << "Login successful" << std::endl;
             protocol.setLoggedIn(true);
        } else if (frame.getCommand() == "ERROR") {
             std::cout << "Error: " << frame.getHeader("message") << "\n" << frame.getBody() << std::endl;
             // protocol.setLoggedIn(false); // Depends on if ERROR disconnects or not, strictly speaking some errors might not disconnect but usually they do in this assignment
        } else if (frame.getCommand() == "RECEIPT") {
             std::cout << "Receipt: " << frame.getHeader("receipt-id") << std::endl;
             try {
                int receiptId = std::stoi(frame.getHeader("receipt-id"));
                if (receiptId == protocol.getDisconnectReceiptId()) {
                    connectionHandler.close();
                    break;
                }
             } catch (...) {}
        } else {
             std::cout << answer << std::endl;
        }
    }
}

int main (int argc, char *argv[]) {
    if (argc < 3) {
        std::cerr << "Usage: " << argv[0] << " host port" << std::endl << std::endl;
        return -1;
    }
    std::string host = argv[1];
    short port = atoi(argv[2]);
    
    ConnectionHandler connectionHandler(host, port);
    if (!connectionHandler.connect()) {
        std::cerr << "Cannot connect to " << host << ":" << port << std::endl;
        return 1;
    }

    StompProtocol protocol;
    std::thread th1(readerThread, std::ref(connectionHandler), std::ref(protocol));
	
	//From here we will see the rest of the ehco client implementation:
    while (1) {
        const short bufsize = 1024;
        char buf[bufsize];
        std::cin.getline(buf, bufsize);
		std::string line(buf);
		
        int status = handleInput(line, connectionHandler, protocol);
        if (status == -1) {
            break;
        }
    }
    
    connectionHandler.close();
    if(th1.joinable()) th1.join();
    return 0;
}

enum class Command {
    LOGIN,
    JOIN,
    EXIT,
    REPORT,
    SUMMARY,
    LOGOUT,
    UNKNOWN
};

Command getCommand(const std::string& commandStr) {
    if (commandStr == "login") return Command::LOGIN;
    if (commandStr == "join") return Command::JOIN;
    if (commandStr == "exit") return Command::EXIT;
    if (commandStr == "report") return Command::REPORT;
    if (commandStr == "summary") return Command::SUMMARY;
    if (commandStr == "logout") return Command::LOGOUT;
    return Command::UNKNOWN;
}

void handleLogin(ConnectionHandler& connectionHandler, const std::vector<std::string>& words, StompProtocol& protocol) {
    if (protocol.getLoggedIn()) {
        std::cout << "The client is already logged in, log out before trying again" << std::endl;
        return;
    }
    if (words.size() < 4) {
        std::cout << "Error: Invalid command format." << std::endl;
        return;
    }
    
    std::string username = words[2];
    std::string password = words[3];

    protocol.setUserName(username);

    StompFrame frame("CONNECT");
    frame.addHeader("accept-version", "1.2");
    frame.addHeader("host", "stomp.cs.bgu.ac.il");
    frame.addHeader("login", username);
    frame.addHeader("passcode", password);

    if (!connectionHandler.sendFrameAscii(frame.toString(), '\0')) {
         std::cout << "Could not connect to server" << std::endl;
    }
}

void handleJoin(ConnectionHandler& connectionHandler, const std::vector<std::string>& words, StompProtocol& protocol) {
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
    
    StompFrame frame("SUBSCRIBE");
    frame.addHeader("destination", "/" + game_name);
    frame.addHeader("id", std::to_string(subId));
    frame.addHeader("receipt", std::to_string(subId)); 
    
    if (!connectionHandler.sendFrameAscii(frame.toString(), '\0')) {
         std::cout << "Could not join game" << std::endl;
         protocol.removeSubscription(game_name);
         return;
    }
    std::cout << "Joined game " << game_name << std::endl;
}

void handleExit(ConnectionHandler& connectionHandler, const std::vector<std::string>& words, StompProtocol& protocol) {
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
    
    StompFrame frame("UNSUBSCRIBE");
    frame.addHeader("id", std::to_string(subId));
    frame.addHeader("receipt", std::to_string(subId)); 
    
    if (!connectionHandler.sendFrameAscii(frame.toString(), '\0')) {
         std::cout << "Could not exit game" << std::endl;
    }
    std::cout << "Exited game " << game_name << std::endl;
}

void handleReport(ConnectionHandler& connectionHandler, const std::vector<std::string>& words, StompProtocol& protocol) {
     if (!protocol.getLoggedIn()) {
        std::cout << "Not logged in" << std::endl;
        return;
    }
    if (words.size() < 2) {
        std::cout << "Error: Invalid command format." << std::endl;
        return;
    }
    std::string file_path = words[1];
    
    names_and_events data = parseEventsFile(file_path);
    std::string game_name = data.team_a_name + "_" + data.team_b_name;
    
    for (const Event& event : data.events) {
        protocol.addEvent(event, protocol.getUserName());
        
        StompFrame frame("SEND");
        frame.addHeader("destination", "/" + game_name);
        
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
             
        if (!connectionHandler.sendFrameAscii(frame.toString(), '\0')) {
             std::cout << "Could not send report" << std::endl;
        }
    }
    std::cout << "Report sent" << std::endl;
}

void handleSummary(ConnectionHandler& connectionHandler, const std::vector<std::string>& words, StompProtocol& protocol) {
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
    for (const Event& e : state.events) {
        outfile << e.get_time() << " - " << e.get_name() << ":\n\n";
        outfile << e.get_discription() << "\n\n";
    }
    outfile.close();
    std::cout << "Summary created" << std::endl;
}

void handleLogout(ConnectionHandler& connectionHandler, const std::vector<std::string>& words, StompProtocol& protocol) {
    if (!protocol.getLoggedIn()) {
        std::cout << "Not logged in" << std::endl;
        return;
    }
    
    int receiptId = protocol.generateSubscriptionId(); 
    protocol.setDisconnectReceiptId(receiptId);
    
    StompFrame frame("DISCONNECT");
    frame.addHeader("receipt", std::to_string(receiptId));
    
    if (!connectionHandler.sendFrameAscii(frame.toString(), '\0')) {
         std::cout << "Could not send logout" << std::endl;
         return;
    }
    
    std::cout << "Logging out..." << std::endl;
    // Basic busy wait loop
    while(protocol.getLoggedIn()) {
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
    std::cout << "Logged out." << std::endl;
}

int handleInput(std::string& input, ConnectionHandler& connectionHandler, StompProtocol& protocol) {
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
            handleLogin(connectionHandler, words, protocol);
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
        case Command::LOGOUT:
             handleLogout(connectionHandler, words, protocol);
             break;
        default:
            std::cout << "Unknown command: " << words.at(0) << std::endl;
            break;
    }

    return 0;
}