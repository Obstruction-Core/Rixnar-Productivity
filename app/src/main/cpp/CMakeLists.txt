cmake_minimum_required(VERSION 3.22.1)
project("rixnar")

# Compiles our native-lib.cpp file into a binary called librixnar.so
add_library(rixnar SHARED native-lib.cpp)

find_library(log-lib log)
target_link_libraries(rixnar ${log-lib})