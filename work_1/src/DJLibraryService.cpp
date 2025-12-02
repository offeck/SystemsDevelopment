#include "DJLibraryService.h"
#include "SessionFileParser.h"
#include "MP3Track.h"
#include "WAVTrack.h"
#include <iostream>
#include <memory>
#include <filesystem>


DJLibraryService::DJLibraryService(const Playlist& playlist) 
    : playlist(playlist), library() {}
/**
 * @brief Load a playlist from track indices referencing the library
 * @param library_tracks Vector of track info from config
 */
void DJLibraryService::buildLibrary(const std::vector<SessionConfig::TrackInfo>& library_tracks) {
    //Todo: Implement buildLibrary method
    // std::cout << "[INFO] Building track library from config..." << std::endl;
    int count = 0;
    for (const auto& track_info : library_tracks) {
        if (track_info.type == "MP3") {
            bool has_tags = (track_info.extra_param2 != 0);
            library.push_back(new MP3Track(
                track_info.title,
                track_info.artists,
                track_info.duration_seconds,
                track_info.bpm,
                track_info.extra_param1, // bitrate
                has_tags
            ));
            count++;
        } else if (track_info.type == "WAV") {
            int bit_depth = track_info.extra_param2; // extra_param2 is bit_depth for WAV
            library.push_back(new WAVTrack(
                track_info.title,
                track_info.artists,
                track_info.duration_seconds,
                track_info.bpm,
                track_info.extra_param1, // sample_rate
                bit_depth
            ));
            count++;
        } else {
            std::cerr << "[ERROR] Unknown track type: " << track_info.type << std::endl;
            continue; // Skip unknown types
        }
    }
    std::cout << "[INFO] Track library built: " << count << " tracks loaded" << std::endl;
}
/**
 * @brief Display the current state of the DJ library playlist
 * 
 */
void DJLibraryService::displayLibrary() const {
    std::cout << "=== DJ Library Playlist: " 
              << playlist.get_name() << " ===" << std::endl;

    if (playlist.is_empty()) {
        std::cout << "[INFO] Playlist is empty.\n";
        return;
    }

    // Let Playlist handle printing all track info
    playlist.display();

    std::cout << "Total duration: " << playlist.get_total_duration() << " seconds" << std::endl;
}

/**
 * @brief Get a reference to the current playlist
 * 
 * @return Playlist& 
 */
Playlist& DJLibraryService::getPlaylist() {
    // Your implementation here
    return playlist;
}

/**
 * TODO: Implement findTrack method
 * 
 * HINT: Leverage Playlist's find_track method
 */
AudioTrack* DJLibraryService::findTrack(const std::string& track_title) {
    return playlist.find_track(track_title);
}

void DJLibraryService::loadPlaylistFromIndices(const std::string& playlist_name, 
                                               const std::vector<int>& track_indices) {
    std::cout << "[INFO] Loading playlist: " << playlist_name << std::endl;
    
    Playlist new_playlist(playlist_name);
    
    for (int index : track_indices) {
        if (index < 1 || index > static_cast<int>(library.size())) {
            std::cout << "[WARNING ] Invalid track index: " << index << std::endl;
            continue; // Skip invalid indices
        }
        PointerWrapper<AudioTrack> track = (*library[index - 1]).clone(); // Convert 1-based to 0-based index
        if (track.get() == nullptr) {
            std::cerr << "[Error] Null track at index: " << index << std::endl;
            continue; // Skip null tracks
        }
        track->load();
        track->analyze_beatgrid();
        std::string track_title = track->get_title(); // Get title before releasing ownership
        new_playlist.add_track(track.release()); // Transfer ownership to playlist
    }
    std::cout << "[INFO] Playlist loaded: " << playlist_name 
              << " (" << new_playlist.get_track_count() << " tracks)" << std::endl;
    
    // Move the playlist - transfer ownership without copying
    playlist = std::move(new_playlist);
}
/**
 * TODO: Implement getTrackTitles method
 * @return Vector of track titles in the playlist
 */
std::vector<std::string> DJLibraryService::getTrackTitles() const {
    std::vector<std::string> titles;
    // Iterate through the playlist and collect titles
    std::vector<AudioTrack*> tracks = playlist.getTracks();
    for (const auto& track_ptr : tracks) {
        titles.push_back(track_ptr->get_title());
    }
    return titles;
}
