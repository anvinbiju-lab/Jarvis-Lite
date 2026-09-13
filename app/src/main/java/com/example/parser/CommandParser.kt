package com.example.parser

import com.example.actions.JarvisAction

interface CommandParser {
    suspend fun parse(command: String): JarvisAction
}
