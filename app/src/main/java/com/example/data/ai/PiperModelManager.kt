package com.example.data.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class PiperModelManager(private val context: Context) {

    private val voicesDir: File
        get() = File(context.filesDir, "piper_voices").apply { mkdirs() }

    fun getVoiceModelDir(voice: PiperVoice): File {
        return File(voicesDir, voice.folderName)
    }

    fun isVoiceDownloaded(voice: PiperVoice): Boolean {
        val voiceFolder = getVoiceModelDir(voice)
        val modelFile = File(voiceFolder, voice.modelFilename)
        val tokensFile = File(voiceFolder, voice.tokensFilename)
        val espeakDir = File(voiceFolder, "espeak-ng-data")
        val baseChecks = modelFile.exists() && modelFile.length() > 1024 * 1024 && // At least 1MB
               tokensFile.exists() && tokensFile.length() > 0 &&
               espeakDir.exists() && espeakDir.isDirectory && (espeakDir.list()?.isNotEmpty() ?: false)
               
        return if (voice.isKokoro) {
            val voicesFile = File(voiceFolder, voice.voicesFilename)
            baseChecks && voicesFile.exists() && voicesFile.length() > 0
        } else {
            baseChecks
        }
    }

    suspend fun downloadAndExtractVoice(
        voice: PiperVoice,
        onProgress: (Int) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val voiceFolder = getVoiceModelDir(voice)
        val tempTarBz2File = File(context.cacheDir, "${voice.folderName}.tar.bz2")
        
        try {
            // Step 1: Download .tar.bz2 to cache with progress (0% - 50%)
            var urlString = voice.tarBz2Url
            var connection: HttpURLConnection
            var responseCode: Int
            var redirectCount = 0
            val maxRedirects = 5

            while (true) {
                val url = URL(urlString)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                connection.connect()

                responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM || 
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307 || responseCode == 308) {
                    
                    redirectCount++
                    if (redirectCount > maxRedirects) {
                        return@withContext Result.failure(Exception("Too many redirects"))
                    }
                    val newUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (newUrl != null) {
                        urlString = newUrl
                        continue
                    }
                }
                break
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(
                    Exception("Failed to download voice: Server returned HTTP $responseCode")
                )
            }

            val totalSize = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(tempTarBz2File).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Long = 0
                    var lastPercent = 0
                    
                    while (true) {
                        val len = input.read(buffer)
                        if (len == -1) break
                        output.write(buffer, 0, len)
                        bytesRead += len
                        
                        if (totalSize > 0) {
                            val percent = ((bytesRead * 50) / totalSize).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }

            // Step 2: Extract .tar.bz2 directly into context.filesDir/piper_voices (50% - 100%)
            onProgress(55)
            voiceFolder.mkdirs()

            FileInputStream(tempTarBz2File).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    BZip2CompressorInputStream(bis).use { bzIn ->
                        TarArchiveInputStream(bzIn).use { tarIn ->
                            var entry: TarArchiveEntry? = tarIn.nextEntry
                            val buffer = ByteArray(8192)
                            
                            while (entry != null) {
                                // entries inside tar.bz2 typically start with "vits-piper-..." folder
                                // we want to extract them into the voicesDir
                                val targetFile = File(voicesDir, entry.name)
                                
                                if (entry.isDirectory) {
                                    targetFile.mkdirs()
                                } else {
                                    targetFile.parentFile?.mkdirs()
                                    FileOutputStream(targetFile).use { fos ->
                                        while (true) {
                                            val len = tarIn.read(buffer)
                                            if (len == -1) break
                                            fos.write(buffer, 0, len)
                                        }
                                    }
                                }
                                entry = tarIn.nextEntry
                            }
                        }
                    }
                }
            }

            // Validate extracted files
            if (!isVoiceDownloaded(voice)) {
                throw Exception("Extracted files are incomplete or corrupted.")
            }

            onProgress(100)
            Result.success(Unit)
        } catch (e: Exception) {
            voiceFolder.deleteRecursively()
            Result.failure(e)
        } finally {
            if (tempTarBz2File.exists()) {
                tempTarBz2File.delete()
            }
        }
    }

    fun deleteVoice(voice: PiperVoice): Boolean {
        val voiceFolder = getVoiceModelDir(voice)
        return voiceFolder.deleteRecursively()
    }
}
