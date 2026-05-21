cmake_minimum_required(VERSION 3.22.1)
project("relentless")

# Compiles our native-lib.cpp file into a binary called librelentless.so
add_library(relentless SHARED native-lib.cpp)

find_library(log-lib log)
target_link_libraries(relentless ${log-lib})