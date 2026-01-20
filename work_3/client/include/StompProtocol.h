#pragma once

#include <string>
#include <map>
#include <vector>
#include <mutex>
#include <atomic>
#include "event.h"

struct GameEvent {
    Event event;
    int halfIndex;
    int sequence;

    GameEvent(const Event& event, int halfIndex, int sequence)
        : event(event), halfIndex(halfIndex), sequence(sequence) {}
};

struct GameState {
    std::string teamA;
    std::string teamB;
    std::map<std::string, std::string> generalStats;
    std::map<std::string, std::string> teamAStats;
    std::map<std::string, std::string> teamBStats;
    std::vector<GameEvent> events;
    int currentHalfIndex;
    int nextSequence;

    GameState() : teamA(""), teamB(""), generalStats(), teamAStats(), teamBStats(), events(),
                  currentHalfIndex(0), nextSequence(0) {}
};

class StompProtocol {
private:
    std::atomic<bool> isLoggedIn;
    std::string currentUserName;
    mutable std::mutex userMutex; // Mutex for currentUserName
    std::atomic<int> subscriptionIdCounter;
    std::atomic<int> receiptIdCounter;
    
    std::map<std::string, int> topicToSubscriptionId;
    std::map<int, std::string> subscriptionIdToTopic;
    std::mutex subscriptionMutex; 

    // key1: game name, key2: user name
    std::map<std::string, std::map<std::string, GameState>> gameReports;
    std::mutex reportMutex;

    std::atomic<int> disconnectReceiptId;
    std::map<int, std::pair<std::string, std::string>> receiptActions;
    std::mutex receiptMutex;
    
    std::atomic<bool> debugMode;

public:
    StompProtocol();
    
    void setLoggedIn(bool status);
    bool getLoggedIn() const;

    void setDebug(bool status);
    bool isDebug() const;

    void setUserName(const std::string& name);
    std::string getUserName() const;

    // Resets all protocol/session-related state in this instance to initial values.
    // Intended to be called before starting a new logical session or login flow.
    // Thread-safety: must not be invoked concurrently with other operations on
    // the same StompProtocol instance.
    void clear();

    int generateSubscriptionId();
    int generateReceiptId();

    void addSubscription(const std::string& topic, int id);
    void removeSubscription(const std::string& topic);
    int getSubscriptionId(const std::string& topic);
    bool isSubscribed(const std::string& topic);

    void setDisconnectReceiptId(int id);
    int getDisconnectReceiptId() const;

    void addReceiptAction(int receiptId, const std::string& action, const std::string& gameName);
    bool popReceiptAction(int receiptId, std::pair<std::string, std::string>& action);

    void addEvent(const Event& event, const std::string& username);
    bool getGameState(const std::string& gameName, const std::string& username, GameState& outState);
};
