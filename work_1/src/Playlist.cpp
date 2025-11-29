#include "Playlist.h"
#include "AudioTrack.h"
#include "PointerWrapper.h"
#include <iostream>
#include <algorithm>
Playlist::Playlist(const std::string& name)   //consturctor
    : head(nullptr), playlist_name(name), track_count(0) {
    std::cout << "Created playlist: " << name << std::endl;
}

// Copy Constructor - Deep copy
Playlist::Playlist(const Playlist& other)
    : head(nullptr), playlist_name(other.playlist_name), track_count(0) {
    std::cout << "Copy constructor called for playlist: " << playlist_name << std::endl;
    
    // Deep copy all nodes and tracks
    PlaylistNode* other_current = other.head;
    while (other_current) {
        // Clone the track to create a new independent copy
        PointerWrapper<AudioTrack> cloned_track = other_current->track->clone();
        if (cloned_track) {
            add_track(cloned_track.release());
        }
        other_current = other_current->next;
    }
}

// Copy Assignment Operator
Playlist& Playlist::operator=(const Playlist& other) {
    std::cout << "Copy assignment called for playlist: " << playlist_name << std::endl;
    
    // Self-assignment check
    if (this == &other) {
        return *this;
    }
    
    // Clean up existing resources
    while (head != nullptr) {
        PlaylistNode* next = head->next;
        delete head->track;
        delete head;
        head = next;
    }
    track_count = 0;
    
    // Copy data from other
    playlist_name = other.playlist_name;
    
    // Deep copy all nodes and tracks
    PlaylistNode* other_current = other.head;
    while (other_current) {
        PointerWrapper<AudioTrack> cloned_track = other_current->track->clone();
        if (cloned_track) {
            add_track(cloned_track.release());
        }
        other_current = other_current->next;
    }
    
    return *this;
}

// TODO: Fix memory leaks!
// Students must fix this in Phase 1
// Nir: create copy constructors. Do we need to declare them in h?
Playlist::~Playlist() {   //destructor
    std::cout << "Destroying playlist: " << playlist_name << std::endl;
    while (head != nullptr) {
        PlaylistNode* nextNode = head->next;
        delete head->track;  // Delete the AudioTrack
        delete head;         // Delete the node
        head = nextNode;
    }
}

void Playlist::add_track(AudioTrack* track) {
    if (!track) {
        std::cout << "[Error] Cannot add null track to playlist" << std::endl;
        return;
    }

    // Create new node - this allocates memory!
    PlaylistNode* new_node = new PlaylistNode(track);

    // Add to front of list
    new_node->next = head;
    head = new_node;
    track_count++;

    std::cout << "Added '" << track->get_title() << "' to playlist '" 
              << playlist_name << "'" << std::endl;
}

void Playlist::remove_track(const std::string& title) {
    // make them both pointers
    PlaylistNode* current = head;
    PlaylistNode* prev = nullptr;

    // Find the track to remove
    while (current && current->track->get_title() != title) {
        prev = current;
        current = current->next;
    }

    if (current) {
        // Remove from linked list
        // Nir added the deletes
        if (prev) {
            prev->next = current->next;
            // delete current or make it reference
            delete current->track;
            delete current;
        } else {
            // same
            head = current->next;
            delete current->track;
            delete current;
        }

        track_count--;
        std::cout << "Removed '" << title << "' from playlist" << std::endl;

    } else {
        std::cout << "Track '" << title << "' not found in playlist" << std::endl;
    }
}

void Playlist::display() const {
    std::cout << "\n=== Playlist: " << playlist_name << " ===" << std::endl;
    std::cout << "Track count: " << track_count << std::endl;

    PlaylistNode* current = head;
    int index = 1;

    while (current) {
        // not sure how to handle vector
        std::vector<std::string> artists = current->track->get_artists();
        std::string artist_list;

        std::for_each(artists.begin(), artists.end(), [&](const std::string& artist) {
            if (!artist_list.empty()) {
                artist_list += ", ";
            }
            artist_list += artist;
        });

        AudioTrack* track = current->track;
        std::cout << index << ". " << track->get_title() 
                  << " by " << artist_list
                  << " (" << track->get_duration() << "s, " 
                  << track->get_bpm() << " BPM)" << std::endl;
        current = current->next;
        index++;
    }
    // delete current;
    if (track_count == 0) {
        std::cout << "(Empty playlist)" << std::endl;
    }
    std::cout << "========================\n" << std::endl;
}

AudioTrack* Playlist::find_track(const std::string& title) const {
    PlaylistNode* current = head;

    while (current) {
        if (current->track->get_title() == title) {
            return current->track;
        }
        current = current->next;
    }

    return nullptr;
}

int Playlist::get_total_duration() const {
    int total = 0;
    PlaylistNode* current = head;

    while (current) {
        total += current->track->get_duration();
        current = current->next;
    }

    return total;
}

/**
 * Get all tracks as a vector
 * WARNING: Returned pointers are only valid while this Playlist exists
 * Do NOT use after Playlist is destroyed or modified
 */
std::vector<AudioTrack*> Playlist::getTracks() const {
    std::vector<AudioTrack*> tracks;
    PlaylistNode* current = head;
    while (current) {
        if (current->track)
            tracks.push_back(current->track);
        current = current->next;
    }
    return tracks;
}