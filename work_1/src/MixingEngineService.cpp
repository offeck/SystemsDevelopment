#include "MixingEngineService.h"
#include <iostream>
#include <memory>


/**
 * TODO: Implement MixingEngineService constructor
 */
MixingEngineService::MixingEngineService()
    : decks(), active_deck(0), auto_sync(false), bpm_tolerance(0)
{
    std::cout << "[MixingEngineService] Initialized with 2 empty decks.\n";
}

/**
 * TODO: Implement MixingEngineService destructor
 */
MixingEngineService::~MixingEngineService() {
    std::cout << "[MixingEngineService] Cleaning up decks...\n";
    for (size_t i = 0; i < 2; ++i) {
        if (decks[i] != nullptr) {
            delete decks[i];
            decks[i] = nullptr;
        }
    }
}
// Copy assignment operator
MixingEngineService& MixingEngineService::operator=(const MixingEngineService& other){
    if (this != &other) {
        // Clean up existing decks
        for (size_t i = 0; i < 2; ++i) {
            if (decks[i] != nullptr) {
                delete decks[i];
                decks[i] = nullptr;
            }
        }
        // Copy decks from other
        for (size_t i = 0; i < 2; ++i) {
            if (other.decks[i] != nullptr) {
                decks[i] = other.decks[i]->clone().release(); // Deep copy using clone
            } else {
                decks[i] = nullptr;
            }
        }
        active_deck = other.active_deck;
        auto_sync = other.auto_sync;
        bpm_tolerance = other.bpm_tolerance;
    }
    return *this;
}
// copy constructor
MixingEngineService::MixingEngineService(const MixingEngineService& other)
    : decks(), active_deck(other.active_deck), auto_sync(other.auto_sync), bpm_tolerance(other.bpm_tolerance)
{
    for (size_t i = 0; i < 2; ++i) {
        if (other.decks[i] != nullptr) {
            decks[i] = other.decks[i]->clone().release(); // Deep copy using clone
        } else {
            decks[i] = nullptr;
        }
    }
}



/**
 * TODO: Implement loadTrackToDeck method
 * @param track: Reference to the track to be loaded
 * @return: Index of the deck where track was loaded, or -1 on failure
 */
int MixingEngineService::loadTrackToDeck(const AudioTrack& track) {
    std::cout << "\n=== Loading Track to Deck ===\n";
    PointerWrapper<AudioTrack> track_ptr = track.clone();
    if(track_ptr.get() == nullptr) {
        std::cerr << "[ERROR] Track: \"" << track.get_title() << "\" failed to clone" << std::endl;
        return -1;
    }
    
    // Target deck is 1 - active_deck (the one that is NOT currently active)
    int target = 1 - active_deck;
    bool is_first_track = (decks[0] == nullptr && decks[1] == nullptr);
    
    std::cout << "[Deck Switch] Target deck: " << target << "\n";
    
    // Unload target deck if occupied
    if (decks[target] != nullptr) {
        delete decks[target];
        decks[target] = nullptr;
    }
    
    // Perform track preparation
    track_ptr->load();
    track_ptr->analyze_beatgrid();
    
    // BPM Management: If active deck has a track and auto_sync is enabled
    if (!is_first_track && decks[active_deck] != nullptr && auto_sync) {
        if (!can_mix_tracks(track_ptr)) {
            sync_bpm(track_ptr);
        }
    }
    
    // Release pointer and assign to target deck
    decks[target] = track_ptr.release();
    std::cout << "[Load Complete] '" << track.get_title() << "' is now loaded on deck " << target << "\n";
    
    // Instant Transition: Unload the previously active deck (if this is not first track)
    if (!is_first_track && decks[active_deck] != nullptr) {
        std::cout << "[Unload] Unloading previous deck " << active_deck << " (" << decks[active_deck]->get_title() << ")\n";
        delete decks[active_deck];
        decks[active_deck] = nullptr;
    }
    
    // Switch active deck
    active_deck = target;
    std::cout << "[Active Deck] Switched to deck " << target << "\n";
    displayDeckStatus();
    
    return target;
}

/**
 * @brief Display current deck status
 */
void MixingEngineService::displayDeckStatus() const {
    std::cout << "\n=== Deck Status ===\n";
    for (size_t i = 0; i < 2; ++i) {
        if (decks[i])
            std::cout << "Deck " << i << ": " << decks[i]->get_title() << "\n";
        else
            std::cout << "Deck " << i << ": [EMPTY]\n";
    }
    std::cout << "Active Deck: " << active_deck << "\n";
    std::cout << "===================\n";
}

/**
 * TODO: Implement can_mix_tracks method
 * 
 * Check if two tracks can be mixed based on BPM difference.
 * 
 * @param track: Track to check for mixing compatibility
 * @return: true if BPM difference <= tolerance, false otherwise
 */
bool MixingEngineService::can_mix_tracks(const PointerWrapper<AudioTrack>& track) const {
    // Check against the active deck (the one currently playing)
    if(decks[active_deck] == nullptr) {
        return false;
    }
    if(track.get() == nullptr) {
        return false;
    }
    int bpm_diff = std::abs(decks[active_deck]->get_bpm() - track->get_bpm());
    return bpm_diff <= bpm_tolerance;
    
}

/**
 * TODO: Implement sync_bpm method
 * @param track: Track to synchronize with active deck
 */
void MixingEngineService::sync_bpm(const PointerWrapper<AudioTrack>& track) const {
    // Sync with the active deck (the one currently playing)
    if(decks[active_deck] == nullptr) {
        std::cerr << "[ERROR] No active deck to sync with.\n";
        return;
    }
    if(track.get() == nullptr) {
        std::cerr << "[ERROR] Invalid track to sync.\n";
        return;
    }
    int original_bpm = track->get_bpm();
    int active_deck_bpm = decks[active_deck]->get_bpm();
    int average_bpm = (original_bpm + active_deck_bpm) / 2;
    track->set_bpm(average_bpm);
    std::cout << "[Sync BPM] Syncing BPM from " << original_bpm << " to " << average_bpm << "\n";
}
