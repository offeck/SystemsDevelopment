#include "../include/event.h"
#include "../include/json.hpp"
#include <iostream>
#include <fstream>
#include <string>
#include <map>
#include <vector>
#include <sstream>
using json = nlohmann::json;

Event::Event(std::string team_a_name, std::string team_b_name, std::string name, int time,
             std::map<std::string, std::string> game_updates, std::map<std::string, std::string> team_a_updates,
             std::map<std::string, std::string> team_b_updates, std::string discription)
    : team_a_name(team_a_name), team_b_name(team_b_name), name(name),
      time(time), game_updates(game_updates), team_a_updates(team_a_updates),
      team_b_updates(team_b_updates), description(discription)
{
}

Event::~Event()
{
}

const std::string &Event::get_team_a_name() const
{
    return this->team_a_name;
}

const std::string &Event::get_team_b_name() const
{
    return this->team_b_name;
}

const std::string &Event::get_name() const
{
    return this->name;
}

int Event::get_time() const
{
    return this->time;
}

const std::map<std::string, std::string> &Event::get_game_updates() const
{
    return this->game_updates;
}

const std::map<std::string, std::string> &Event::get_team_a_updates() const
{
    return this->team_a_updates;
}

const std::map<std::string, std::string> &Event::get_team_b_updates() const
{
    return this->team_b_updates;
}

const std::string &Event::get_discription() const
{
    return this->description;
}

Event::Event(const std::string &frame_body) : team_a_name(""), team_b_name(""), name(""), time(0), game_updates(), team_a_updates(), team_b_updates(), description("")
{
    // Parse the body
    std::stringstream ss(frame_body);
    std::string line;
    std::string current_map = ""; // "general", "team_a", "team_b"

    while (std::getline(ss, line)) {
        if (line.empty()) continue; // Should probably keep newlines in description? 
        // Actually description is the last field and can be multi-line.
        
        size_t colonPos = line.find(':');
        if (colonPos == std::string::npos) {
            // Might be part of description if current_map is description
            if (current_map == "description") {
                description += "\n" + line;
            }
            continue;
        }

        std::string key = line.substr(0, colonPos);
        std::string value = line.substr(colonPos + 1);
        
        // Trim whitespace
        // (Not strictly necessary if format is rigid but good for robustness)
        // Helper lambda for trimming would be nice but C++11... 
        
        // Simplified trim
        while (!key.empty() && (key.back() == ' ' || key.back() == '\t')) key.pop_back();
        while (!key.empty() && (key.front() == ' ' || key.front() == '\t')) key.erase(0, 1);
        while (!value.empty() && (value.back() == ' ' || value.back() == '\t')) value.pop_back();
        while (!value.empty() && (value.front() == ' ' || value.front() == '\t')) value.erase(0, 1);

        if (key == "team a") { team_a_name = value; }
        else if (key == "team b") { team_b_name = value; }
        else if (key == "event name") { name = value; }
        else if (key == "time") { time = std::stoi(value); }
        else if (key == "general game updates") { current_map = "general"; }
        else if (key == "team a updates") { current_map = "team_a"; }
        else if (key == "team b updates") { current_map = "team_b"; }
        else if (key == "description") { 
            current_map = "description"; 
            description = value; 
        }
        else {
            // It's a map entry
            if (current_map == "general") game_updates[key] = value;
            else if (current_map == "team_a") team_a_updates[key] = value;
            else if (current_map == "team_b") team_b_updates[key] = value;
            else if (current_map == "description") {
                // Continuation of description with a colon in it?
                 description += "\n" + line;
            }
        }
    }
}


names_and_events parseEventsFile(std::string json_path)
{
    std::ifstream f(json_path);
    json data = json::parse(f);

    std::string team_a_name = data["team a"];
    std::string team_b_name = data["team b"];

    // run over all the events and convert them to Event objects
    std::vector<Event> events;
    for (auto &event : data["events"])
    {
        std::string name = event["event name"];
        int time = event["time"];
        std::string description = event["description"];
        std::map<std::string, std::string> game_updates;
        std::map<std::string, std::string> team_a_updates;
        std::map<std::string, std::string> team_b_updates;
        for (auto &update : event["general game updates"].items())
        {
            if (update.value().is_string())
                game_updates[update.key()] = update.value();
            else
                game_updates[update.key()] = update.value().dump();
        }

        for (auto &update : event["team a updates"].items())
        {
            if (update.value().is_string())
                team_a_updates[update.key()] = update.value();
            else
                team_a_updates[update.key()] = update.value().dump();
        }

        for (auto &update : event["team b updates"].items())
        {
            if (update.value().is_string())
                team_b_updates[update.key()] = update.value();
            else
                team_b_updates[update.key()] = update.value().dump();
        }
        
        events.push_back(Event(team_a_name, team_b_name, name, time, game_updates, team_a_updates, team_b_updates, description));
    }
    names_and_events events_and_names{team_a_name, team_b_name, events};

    return events_and_names;
}