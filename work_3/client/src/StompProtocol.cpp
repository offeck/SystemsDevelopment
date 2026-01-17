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
