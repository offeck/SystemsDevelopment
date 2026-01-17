#pragma once

#include <string>
#include <map>
#include <vector>
#include <sstream>

class StompFrame {
private:
    std::string command;
    std::map<std::string, std::string> headers;
    std::string body;

public:
    StompFrame(std::string command);

    void addHeader(const std::string& key, const std::string& value);
    std::string getHeader(const std::string& key) const;
    void setBody(const std::string& body);
    std::string getBody() const;
    std::string getCommand() const;
    std::string toString() const;

    static StompFrame parse(const std::string& msg);
};
