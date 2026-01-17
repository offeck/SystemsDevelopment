#pragma once

#include <string>
#include <map>
#include <vector>
#include <mutex>
#include <atomic>
#include "event.h"

struct GameState {
    std::string teamA;
    std::string teamB;
    std::map<std::string, std::string> generalStats;
    std::map<std::string, std::string> teamAStats;
    std::map<std::string, std::string> teamBStats;
    std::vector<Event> events;

    GameState() : teamA(""), teamB(""), generalStats(), teamAStats(), teamBStats(), events() {}
};

class StompProtocol {
private:
    std::atomic<bool> isLoggedIn;
    std::string currentUserName;
    std::atomic<int> subscriptionIdCounter;
    std::atomic<int> receiptIdCounter;
    
    std::map<std::string, int> topicToSubscriptionId;
    std::map<int, std::string> subscriptionIdToTopic;
    std::mutex subscriptionMutex; 

    // key1: game name, key2: user name
    std::map<std::string, std::map<std::string, GameState>> gameReports;
    std::mutex reportMutex;

    int disconnectReceiptId;

public:
    StompProtocol();
    
    void setLoggedIn(bool status);
    bool getLoggedIn() const;

    void setUserName(const std::string& name);
    std::string getUserName() const;

    int generateSubscriptionId();
    int generateReceiptId();

    void addSubscription(const std::string& topic, int id);
    void removeSubscription(const std::string& topic);
    int getSubscriptionId(const std::string& topic);
    bool isSubscribed(const std::string& topic);

    void setDisconnectReceiptId(int id);
    int getDisconnectReceiptId() const;

    void addEvent(const Event& event, const std::string& username);
    bool getGameState(const std::string& gameName, const std::string& username, GameState& outState);
};
