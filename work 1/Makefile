.RECIPEPREFIX := >

CXX := g++
CXXFLAGS := -std=c++20 -Wall -Wextra -pedantic -Iinclude
SRC := $(wildcard src/*.cpp)
OBJ := $(SRC:src/%.cpp=bin/%.o)
TARGET := bin/app

all: $(TARGET)

$(TARGET): $(OBJ) | bin
>$(CXX) $(CXXFLAGS) -o $@ $^

bin:
>mkdir -p $@

bin/%.o: src/%.cpp | bin
>$(CXX) $(CXXFLAGS) -c $< -o $@

clean:
>rm -f $(OBJ) $(TARGET)

.PHONY: all clean