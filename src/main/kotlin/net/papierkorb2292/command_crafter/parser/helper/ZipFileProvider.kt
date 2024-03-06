package net.papierkorb2292.command_crafter.parser.helper

import java.util.zip.ZipFile

interface ZipFileProvider {
    fun `command_crafter$getZipFile`(): ZipFile
}