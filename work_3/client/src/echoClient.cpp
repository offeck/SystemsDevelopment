#include <stdlib.h>
#include <vector>
#include <sstream>
#include <map>
#include <thread>
#include <atomic>
#include "../include/ConnectionHandler.h"
#include "../include/StompFrame.h"
#include "../include/StompProtocol.h"

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
             //handle join
             break;
        case Command::EXIT:
             //handle exit
             break;
        case Command::REPORT:
             //handle report
             break;
        case Command::SUMMARY:
             //handle summary
             break;
        case Command::LOGOUT:
             //handle logout
             break;
        default:
            //handle unknown command
            std::cout << "Unknown command: " << words.at(0) << std::endl;
            break;
    }
    return 0;
}