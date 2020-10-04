package com.github.iguanastin.app


fun <T> Pair<T, T>.contains(item: T): Boolean {
    return first == item || second == item
}