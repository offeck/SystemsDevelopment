#include "DJLibraryService.h"
#include "SessionFileParser.h"
#include "MP3Track.h"
#include "WAVTrack.h"
#include <iostream>
#include <memory>
#include <filesystem>


DJLibraryService::DJLibraryService(const Playlist& playlist) 
    : playlist(playlist) {}
/**
 * @brief Load a playlist from track indices referencing the library
 * @param library_tracks Vector of track info from config
 */
void DJLibraryService::buildLibrary(const std::vector<SessionConfig::TrackInfo>& library_tracks) {
    //Todo: Implement buildLibrary method
    int count = 0;
    for (const auto& track_info : library_tracks) {
        PointerWrapper<AudioTrack> track_ptr;

        if (track_info.type == "MP3") {
            bool has_tags = (track_info.extra_param2 != 0);
            track_ptr = PointerWrapper<AudioTrack>(new MP3Track(
                track_info.title,
                track_info.artists,
                track_info.duration_seconds,
                track_info.bpm,
                track_info.extra_param1, // bitrate
                has_tags
            ));
            library.push_back(track_ptr.operator->());
            std::cout << "MP3: MP3Track created: " << track_info.extra_param1 << " kbps\n";
            count++;
        } else if (track_info.type == "WAV") {
            bool is_stereo = (track_info.extra_param2 != 0);
            track_ptr = PointerWrapper<AudioTrack>(new WAVTrack(
                track_info.title,
                track_info.artists,
                track_info.duration_seconds,
                track_info.bpm,
                track_info.extra_param1, // sample_rate
                is_stereo
            ));
            library.push_back(track_ptr.operator->());
            std::cout << "WAV: WAVTrack created: " << track_info.extra_param1 << " Hz/" << track_info.extra_param2 << " bit\n";
            count++;
        } else {
            std::cerr << "[ERROR] Unknown track type: " << track_info.type << std::endl;
            continue; // Skip unknown types
        }
    std::cout << "[INFO] Total tracks added to library: " << count << std::endl;
    }
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
    // Your implementation here
    return nullptr; // Placeholder
}

void DJLibraryService::loadPlaylistFromIndices(const std::string& playlist_name, 
                                               const std::vector<int>& track_indices) {
    // Your implementation here
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
        new_playlist.add_track(track.release()); // Transfer ownership to playlist
        std::cout << "Added '" << track->get_title() << "' to playlist '" << playlist_name << "'\n";
    }
    std::cout << "[INFO] Playlist loaded: " << playlist_name 
              << " (" << new_playlist.get_track_count() << " tracks)" << std::endl;
}
/**
 * TODO: Implement getTrackTitles method
 * @return Vector of track titles in the playlist
 */
std::vector<std::string> DJLibraryService::getTrackTitles() const {
    // Your implementation here
    return std::vector<std::string>(); // Placeholder
}
