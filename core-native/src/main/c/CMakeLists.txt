cmake_minimum_required(VERSION 3.22.1)
project(corenative C)

add_library(corenative SHARED shim.c)

find_library(log-lib log)
target_link_libraries(corenative ${log-lib})
