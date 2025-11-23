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
    if(cache.contains(track.get_title())) {
        cache.get(track.get_title()); // Update usage
        return 1; // Track already in cache
    }
    PointerWrapper<AudioTrack> track_copy = track.clone();
    if(track_copy.get() == nullptr ) {
        std::cerr << "Error: Cloning track failed for " << track.get_title() << std::endl;
        return -101; // Cloning failed
    }
    track_copy->load();
    track_copy->analyze_beatgrid();
    bool success = cache.put(std::move(track_copy));
    return success ? -1 : 0;
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
