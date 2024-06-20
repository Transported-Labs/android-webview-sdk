package com.cueaudio.cuelightshow

import android.content.Context
import android.webkit.MimeTypeMap
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock


/** I/O utilities.  */
object IoUtils {
    private const val CACHE_DIR = "cache"
    private const val DATA_BUFFER = 1024
    private const val REGEX_ALLOWED_LETTERS = "[^0-9a-zA-Z.\\-]"
    private val sLocks: MutableMap<String, ReadWriteLock> = ConcurrentHashMap(0)

    @JvmStatic
    fun close(closeable: Closeable?) {
        closeable ?: return
        try {
            closeable.close()
        } catch (e: IOException) {
            // ignore
        }
    }

    fun downloadToFile(context: Context, url: String): String {
        var resultMessage: String
        val fileName = makeFileNameFromUrl(context, url)
        val outFile = File(fileName)
        if (outFile.exists()) {
            resultMessage = "Overwritten in cache"
            try {
                outFile.delete()
            } catch (e: Exception) {
                resultMessage = "Not overwritten, failed to delete file"
            }
        } else {
            resultMessage = "Added to cache"
        }
        var outStream: FileOutputStream? = null
        val lock = getLock(outFile)
        try {
            lock!!.writeLock().lock()
            BufferedInputStream(URL(url).openStream()).use { bufferedInputStream ->
                outStream = FileOutputStream(outFile)
                val data = ByteArray(DATA_BUFFER)
                var count: Int
                while (bufferedInputStream.read(data, 0, DATA_BUFFER).also { count = it } != -1) {
                    outStream?.write(data, 0, count)
                }
            }
        } catch (e: Exception) {
            resultMessage = "Failed to save in cache"
        } finally {
            close(outStream)
            // release lock
            lock!!.writeLock().unlock()
            releaseLock(outFile)
        }
        return "$resultMessage: ${shorten(fileName)}"
    }

    fun loadMediaFromFileUrl(context: Context, url: String): Pair<InputStream?, String> {
        var fileInputStream: FileInputStream? = null
        var resultMessage: String
        val fileName = makeFileNameFromUrl(context, url)
        try {
            val file = File(fileName)
            if (!file.exists()) {
                resultMessage = "Not loaded, file does not exist"
            } else {
                fileInputStream = FileInputStream(file.path)
                resultMessage = "Loaded from cache"
            }
        } catch (e: java.lang.Exception) {
            resultMessage = "Failed to load file"
        }
        return  Pair(fileInputStream, "$resultMessage: ${shorten(fileName)}")
    }

    private fun shorten(fileName: String) = fileName.substringAfterLast("_")

    @Synchronized
    private fun getLock(file: File): ReadWriteLock? {
        val fileName = file.path
        if (!sLocks.containsKey(fileName)) {
            sLocks[fileName] = ReentrantReadWriteLock()
        }
        return sLocks[fileName]
    }

    @Synchronized
    private fun releaseLock(file: File) {
        val fileName = file.path
        if (!sLocks.containsKey(fileName)) {
            return
        }
        val lock = sLocks[fileName] as ReentrantReadWriteLock?
        if (lock != null && !lock.hasQueuedThreads()) {
            sLocks.remove(fileName)
        }
    }

    private fun makeFileNameFromUrl(context: Context, url: String): String {
        val filename: String = url.replace(
            REGEX_ALLOWED_LETTERS.toRegex(),
            "_"
        ).lowercase(
            Locale.getDefault()
        )
        val cacheDir = File(context.filesDir, CACHE_DIR)
        if (!cacheDir.exists()) {
            cacheDir.mkdir()
        }
        return "$cacheDir/$filename"
    }

    fun getMimeTypeFromUrl(url: String?): String? {
        var type: String? = null
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
        return type
    }

    fun showCache(context: Context): String {
        var resultMessage = ""
        val cacheDir = File(context.filesDir, CACHE_DIR)
        var index = 0
        if (cacheDir.exists()) {
            for (file in cacheDir.listFiles()!!) {
                index += 1
                resultMessage += "$index. ${shorten(file.name)}\n"
            }
        }
        return resultMessage
    }

    fun clearCache(context: Context): String {
        var resultMessage = ""
        val cacheDir = File(context.filesDir, CACHE_DIR)
        if (cacheDir.exists()) {
            for (file in cacheDir.listFiles()!!) {
                try {
                    val fileName = file.name
                    file.delete()
                    resultMessage += "Deleted: ${shorten(fileName)}\n"
                } catch (e: Exception) {
                    resultMessage += "Error deleting file: ${shorten(file.name)}\n"
                }
            }
        }
        return resultMessage
    }
}