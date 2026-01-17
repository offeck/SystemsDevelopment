#include "../include/StompProtocol.h"

StompProtocol::StompProtocol() 
    : isLoggedIn(false), currentUserName(""), subscriptionIdCounter(0), receiptIdCounter(0), 
      topicToSubscriptionId(), subscriptionIdToTopic(), subscriptionMutex(), 
      gameReports(), reportMutex(), disconnectReceiptId(-1) {}

void StompProtocol::setLoggedIn(bool status) {
    isLoggedIn = status;
}

bool StompProtocol::getLoggedIn() const {
    return isLoggedIn;
}

void StompProtocol::setUserName(const std::string& name) {
    currentUserName = name;
}

std::string StompProtocol::getUserName() const {
    return currentUserName;
}

int StompProtocol::generateSubscriptionId() {
    return subscriptionIdCounter++; // Atomic increment
}

int StompProtocol::generateReceiptId() {
    return receiptIdCounter++; // Atomic increment
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
    
    state.events.push_back(event);
}

bool StompProtocol::getGameState(const std::string& gameName, const std::string& username, GameState& outState) {
    std::lock_guard<std::mutex> lock(reportMutex);
    if (gameReports.count(gameName) && gameReports[gameName].count(username)) {
        outState = gameReports[gameName][username];
        return true;
    }
    return false;
}
