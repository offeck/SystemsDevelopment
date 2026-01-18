# Review Findings for work_1

Based on the code analysis, the following critical issues were identified which likely correspond to the 2 memory leaks and 4 failed tests reported.

## 1. Critical Memory Leak in `DJLibraryService`

**Severity:** Critical (Causes massive memory leaks)
**Location:** `src/DJLibraryService.cpp` / `include/DJLibraryService.h`

**Issue:**
The `DJLibraryService` class dynamically allocates `AudioTrack` objects (using `new MP3Track` / `new WAVTrack`) and stores them in the `std::vector<AudioTrack*> library`. However, the class **lacks a destructor**. When the service is destroyed, the vector is cleared properly, but the pointers inside it are not `delete`d, causing every single track loaded into the library to leak.

**Fix Required:**
Implement the destructor in `DJLibraryService` to clean up the library:

```cpp
// In include/DJLibraryService.h
~DJLibraryService();

// In src/DJLibraryService.cpp
DJLibraryService::~DJLibraryService() {
    for (AudioTrack* track : library) {
        delete track;
    }
    library.clear();
}
```

## 2. Logic Error in `PointerWrapper` Interface

**Severity:** High (Causes crashes/test failures)
**Location:** `include/PointerWrapper.h`

**Issue:**
The implementation of `get()` throws an exception if the pointer is null:
```cpp
T* get() const {
    if (!ptr) {
        throw std::runtime_error("Getting null pointer");
    }
    return ptr;
}
```
This contradicts standard smart pointer behavior (like `std::unique_ptr`) and breaks logic in other classes. For example, in `LRUCache::put(PointerWrapper<AudioTrack> track)`:
```cpp
if (track.get() == nullptr) { ... }
```
This check is intended to handle an empty wrapper safely, but your `get()` implementation throws an exception instead, likely crashing the test suite.

**Fix Required:**
Modify `get()` to return `nullptr` safely:
```cpp
T* get() const {
    return ptr;
}
```

## 3. Potential Initialization Issue in `MixingEngineService`

**Severity:** Medium (Potential undefined behavior/crashes)
**Location:** `src/MixingEngineService.cpp`

**Issue:**
The standard array `decks[2]` is initialized in the member initializer list via `decks()`. While this technically performs zero-initialization, explicit initialization is safer and clearer. If `decks` contains garbage values, the `delete decks[target]` call in `loadTrackToDeck` will cause a segmentation fault.

**Fix Required:**
Explicitly initialize pointers in the constructor:
```cpp
MixingEngineService::MixingEngineService()
    : active_deck(1), auto_sync(false), bpm_tolerance(0) // Remove decks() from list
{
    decks[0] = nullptr;
    decks[1] = nullptr;
    std::cout << "[MixingEngineService] Initialized with 2 empty decks.\n";
}
```

## 4. `operator->` Safety in `PointerWrapper`

**Location:** `include/PointerWrapper.h`

**Issue:**
The `operator->` implementation correctly throws if `ptr` is null. However, combined with the `get()` issue above, this might be overly aggressive depending on how the tests expect to check for validity. Ensuring `get()` returns `nullptr` resolves the primary friction, keeping `operator->` strict is generally fine (as dereferencing null is invalid).

## Summary of Fixes for 70/100 -> 100/100

1.  **Add Destructor to `DJLibraryService`**: Fixes the major memory usage/leak issues.
2.  **Fix `PointerWrapper::get()`**: Determine if it should return `nullptr` or throw. Standard practice dictates returning `nullptr` to allow for checks like `if (ptr.get())`. Changing this to return `ptr` (even if null) will likely fix the "4 failed tests" related to logic checks crashing.
