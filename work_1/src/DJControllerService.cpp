#include "DJControllerService.h"
#include "MP3Track.h"
#include "WAVTrack.h"
#include <iostream>
#include <memory>

DJControllerService::DJControllerService(size_t cache_size)
    : cache(cache_size) {}
/**
 * TODO: Implement loadTrackToCache method
 */
int DJControllerService::loadTrackToCache(AudioTrack& track) {
    std::cout << "[System] Loading track '" << track.get_title() << "' to controller..." << std::endl;
    
    if(cache.contains(track.get_title())) {
        std::cout << "[Cache HIT] " << track.get_title() << " found in cache. Refreshing MRU state." << std::endl;
        cache.get(track.get_title()); // Update usage
        displayCacheStatus();
        return 1; // Track already in cache
    }
    
    std::cout << "[Cache MISS] Cloning track into cache: " << track.get_title() << std::endl;
    PointerWrapper<AudioTrack> track_copy = track.clone();
    if(track_copy.get() == nullptr ) {
        std::cerr << "[ERROR] Track: " << track.get_title() << "failed to clone" << std::endl;
        return -101; // Cloning failed
    }
    track_copy->load();
    track_copy->analyze_beatgrid();
    bool evicted = cache.put(std::move(track_copy));
    std::cout << "[Cache INSERT] Added '" << track.get_title() << "' to cache." << std::endl;
    displayCacheStatus();
    return evicted ? -1 : 0;
}

void DJControllerService::set_cache_size(size_t new_size) {
    cache.set_capacity(new_size);
}
//implemented
void DJControllerService::displayCacheStatus() const {
    std::cout << "\n=== Cache Status ===\n";
    cache.displayStatus();
    std::cout << "====================\n";
}

/**
 * TODO: Implement getTrackFromCache method
 */
AudioTrack* DJControllerService::getTrackFromCache(const std::string& track_title) {
    // Your implementation here
    AudioTrack* track = cache.get(track_title); // Update usage
    return track;
}
