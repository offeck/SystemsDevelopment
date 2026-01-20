#include "../include/StompProtocol.h"

StompProtocol::StompProtocol() 
    : isLoggedIn(false), currentUserName(""), userMutex(), subscriptionIdCounter(0), receiptIdCounter(0), 
      topicToSubscriptionId(), subscriptionIdToTopic(), subscriptionMutex(), 
      gameReports(), reportMutex(), disconnectReceiptId(-1), receiptActions(), receiptMutex(), debugMode(false) {}

void StompProtocol::setLoggedIn(bool status) {
    isLoggedIn = status;
}

bool StompProtocol::getLoggedIn() const {
    return isLoggedIn;
}

void StompProtocol::setDebug(bool status) {
    debugMode = status;
}

bool StompProtocol::isDebug() const {
    return debugMode;
}

void StompProtocol::setUserName(const std::string& name) {
    std::lock_guard<std::mutex> lock(userMutex);
    currentUserName = name;
}

std::string StompProtocol::getUserName() const {
    std::lock_guard<std::mutex> lock(userMutex);
    return currentUserName;
}

void StompProtocol::clear() {
    isLoggedIn = false;
    {
        std::lock_guard<std::mutex> lock(userMutex);
        currentUserName = "";
    }
    
    {
        std::lock_guard<std::mutex> lockSub(subscriptionMutex);
        topicToSubscriptionId.clear();
        subscriptionIdToTopic.clear();
        subscriptionIdCounter = 0;
    }

    {
        std::lock_guard<std::mutex> lockRep(reportMutex);
        gameReports.clear();
    }

    {
        std::lock_guard<std::mutex> lockRec(receiptMutex);
        receiptActions.clear();
        receiptIdCounter = 0;
    }
    
    disconnectReceiptId = -1;
}

int StompProtocol::generateSubscriptionId() {
    std::lock_guard<std::mutex> lock(subscriptionMutex);
    return subscriptionIdCounter++;
}

int StompProtocol::generateReceiptId() {
    std::lock_guard<std::mutex> lock(receiptMutex);
    return receiptIdCounter++;
}

void StompProtocol::addSubscription(const std::string& topic, int id) {
    std::lock_guard<std::mutex> lock(subscriptionMutex);
    topicToSubscriptionId[topic] = id;
    subscriptionIdToTopic[id] = topic;
}

void StompProtocol::removeSubscription(const std::string& topic) {
    std::lock_guard<std::mutex> lock(subscriptionMutex);
    auto it = topicToSubscriptionId.find(topic);
    if (it != topicToSubscriptionId.end()) {
        int id = it->second;
        topicToSubscriptionId.erase(it);
        subscriptionIdToTopic.erase(id);
    }
}

int StompProtocol::getSubscriptionId(const std::string& topic) {
    std::lock_guard<std::mutex> lock(subscriptionMutex);
    auto it = topicToSubscriptionId.find(topic);
    if (it != topicToSubscriptionId.end()) {
        return it->second;
    }
    return -1; // Not found
}

bool StompProtocol::isSubscribed(const std::string& topic) {
    std::lock_guard<std::mutex> lock(subscriptionMutex);
    return topicToSubscriptionId.find(topic) != topicToSubscriptionId.end();
}

void StompProtocol::setDisconnectReceiptId(int id) {
    disconnectReceiptId = id;
}

int StompProtocol::getDisconnectReceiptId() const {
    return disconnectReceiptId;
}

void StompProtocol::addReceiptAction(int receiptId, const std::string& action, const std::string& gameName) {
    std::lock_guard<std::mutex> lock(receiptMutex);
    receiptActions[receiptId] = std::make_pair(action, gameName);
}

bool StompProtocol::popReceiptAction(int receiptId, std::pair<std::string, std::string>& action) {
    std::lock_guard<std::mutex> lock(receiptMutex);
    auto it = receiptActions.find(receiptId);
    if (it == receiptActions.end()) {
        return false;
    }
    action = it->second;
    receiptActions.erase(it);
    return true;
}

void StompProtocol::addEvent(const Event& event, const std::string& username) {
    std::lock_guard<std::mutex> lock(reportMutex);
    std::string gameName = event.get_team_a_name() + "_" + event.get_team_b_name();
    
    GameState& state = gameReports[gameName][username];
    if (state.teamA.empty()) state.teamA = event.get_team_a_name();
    if (state.teamB.empty()) state.teamB = event.get_team_b_name();

    // Update stats
    for (auto const& pair : event.get_game_updates()) {
        state.generalStats[pair.first] = pair.second;
    }
    for (auto const& pair : event.get_team_a_updates()) {
        state.teamAStats[pair.first] = pair.second;
    }
    for (auto const& pair : event.get_team_b_updates()) {
        state.teamBStats[pair.first] = pair.second;
    }

    auto halftimeFlag = event.get_game_updates().find("before halftime");
    if (halftimeFlag != event.get_game_updates().end()) {
        std::string value = halftimeFlag->second;
        if (value == "false" || value == "False" || value == "0") {
            state.currentHalfIndex = 1;
        } else if (value == "true" || value == "True" || value == "1") {
            state.currentHalfIndex = 0;
        }
    }

    state.events.emplace_back(event, state.currentHalfIndex, state.nextSequence++);
}

bool StompProtocol::getGameState(const std::string& gameName, const std::string& username, GameState& outState) {
    std::lock_guard<std::mutex> lock(reportMutex);
    if (gameReports.count(gameName) && gameReports[gameName].count(username)) {
        outState = gameReports[gameName][username];
        return true;
    }
    return false;
}
