#include "../include/StompFrame.h"
#include <sstream>
#include <iostream>

StompFrame::StompFrame(std::string command) : command(command), headers(), body("") {}

void StompFrame::addHeader(const std::string& key, const std::string& value) {
    headers[key] = value;
}

std::string StompFrame::getHeader(const std::string& key) const {
    auto it = headers.find(key);
    if (it != headers.end()) {
        return it->second;
    }
    return "";
}

void StompFrame::setBody(const std::string& body) {
    this->body = body;
}

std::string StompFrame::getBody() const {
    return body;
}

std::string StompFrame::getCommand() const {
    return command;
}

std::string StompFrame::toString() const {
    std::stringstream ss;
    ss << command << "\n";
    for (const auto& pair : headers) {
        ss << pair.first << ":" << pair.second << "\n";
    }
    ss << "\n"; // End of headers
    ss << body;
    ss << '\0'; // Null terminator
    return ss.str();
}

StompFrame StompFrame::parse(const std::string& msg) {
    std::stringstream ss(msg);
    std::string line;
    std::string command;

    // Get command
    if (!std::getline(ss, command) || command.empty()) {
        // Handle empty message or error
        return StompFrame(""); 
    }
    // Remove potential carriage return if present
    if (!command.empty() && command.back() == '\r') {
        command.pop_back();
    }

    StompFrame frame(command);

    // Get headers
    while (std::getline(ss, line) && !line.empty()) {
        if (!line.empty() && line.back() == '\r') {
            line.pop_back();
        }
        if (line.empty()) break; // End of headers

        size_t colonPos = line.find(':');
        if (colonPos != std::string::npos) {
            std::string key = line.substr(0, colonPos);
            std::string value = line.substr(colonPos + 1);
            frame.addHeader(key, value);
        }
    }

    // Get body
    std::string body;
    char ch;
    while (ss.get(ch)) {
        if(ch != '\0')
            body += ch;
    }
    frame.setBody(body);

    return frame;
}
