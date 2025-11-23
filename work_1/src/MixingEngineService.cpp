#include "MixingEngineService.h"
#include <iostream>
#include <memory>


/**
 * TODO: Implement MixingEngineService constructor
 */
MixingEngineService::MixingEngineService()
    : active_deck(1), decks{nullptr, nullptr}, auto_sync(false), bpm_tolerance(0)
{
    std::cout << "[MixingEngineService] Initialized with 2 empty decks\n";
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


/**
 * TODO: Implement loadTrackToDeck method
 * @param track: Reference to the track to be loaded
 * @return: Index of the deck where track was loaded, or -1 on failure
 */
int MixingEngineService::loadTrackToDeck(const AudioTrack& track) {
    std::cout << "\n=== Loading Track to Deck ===\n";
    PointerWrapper<AudioTrack> track_ptr = track.clone();
    if(track_ptr.get() == nullptr) {
        std::cerr << "[ERROR] Track: " << track.get_title() << "failed to clone" << std::endl;
        return -1;
    }
    int new_active_deck_index = 1 - active_deck;
    std::cout << "[Deck Switch] Target deck: " << new_active_deck_index << "\n";
    if (decks[new_active_deck_index] != nullptr) {
        delete decks[new_active_deck_index];
        decks[new_active_deck_index] = nullptr;
    }
    track_ptr->load();
    track_ptr->analyze_beatgrid();
    if (decks[active_deck] != nullptr && !can_mix_tracks(track_ptr)) {
        sync_bpm(track_ptr);
    }
    decks[new_active_deck_index] = track_ptr.release(); // Release ownership to avoid double delete
    std::cout << "[Load Complete] ’" << track.get_title() << "’ is now loaded on deck " << new_active_deck_index << "\n";
    if(decks[active_deck] != nullptr) {
        std::cout << "[Unload] Unloading previous deck " << active_deck << " (" << decks[active_deck]->get_title() << ")\n";
        delete decks[active_deck];
        decks[active_deck] = nullptr;
    }
    active_deck = new_active_deck_index;
    std::cout << "[Active Deck] Switched to deck " << active_deck << "\n";
    return active_deck;
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
    if(decks[active_deck] == nullptr) {
        std::cerr << "[ERROR] No active deck to sync with.\n";
        return;
    }
    if(track.get() == nullptr) {
        std::cerr << "[ERROR] Invalid track to sync.\n";
        return;
    }
    int new_track_bpm = track->get_bpm();
    int active_deck_bpm = decks[active_deck]->get_bpm();
    track->set_bpm((new_track_bpm + active_deck_bpm)/2);
    std::cout << "[Sync BPM] Syncing BPM from " << new_track_bpm << " to " << track->get_bpm() << "\n";
}
