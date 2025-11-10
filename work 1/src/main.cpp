#include <chrono>
#include <iostream>
#include <string>
#include <thread>

namespace demo {
void greet(const std::string& name) {
    std::cout << "Hello, " << name << "! Welcome to your dev container." << std::endl;
}

void spinner(std::chrono::milliseconds duration) {
    const char frames[] = "|/-\\";
    const auto frame_count = sizeof(frames) - 1;
    const auto end = std::chrono::steady_clock::now() + duration;
    std::size_t i = 0;
    while (std::chrono::steady_clock::now() < end) {
        std::cout << '\r' << "Working " << frames[i % frame_count] << std::flush;
        std::this_thread::sleep_for(std::chrono::milliseconds(80));
        ++i;
    }
    std::cout << '\r' << "All set!    " << std::endl;
}
}  // namespace demo

int main() {
    std::string name;
    std::cout << "What's your name? ";
    std::getline(std::cin, name);

    if (name.empty()) {
        name = "C++ Builder";
    }

    demo::spinner(std::chrono::milliseconds(800));
    demo::greet(name);
    return 0;
}