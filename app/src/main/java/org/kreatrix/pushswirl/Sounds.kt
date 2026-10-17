package org.kreatrix.pushswirl

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log

/**
 * The three configurable cue sounds, each mapped to its built-in default resource.
 * A custom sound is an optional per-cue content:// URI stored on [NotificationSettings].
 */
enum class AppSound(val defaultRes: Int, val label: String) {
    PUSH(R.raw.beep_long, "Push"),
    SWIRL(R.raw.beep_short, "Swirl"),
    PHASE_END(R.raw.finish, "End of phase"),
    PAUSE(R.raw.ding, "Break")
}

fun NotificationSettings.customUriFor(sound: AppSound): String? = when (sound) {
    AppSound.PUSH -> pushSoundUri
    AppSound.SWIRL -> swirlSoundUri
    AppSound.PHASE_END -> phaseEndSoundUri
    AppSound.PAUSE -> pauseSoundUri
}

fun NotificationSettings.withCustomUri(sound: AppSound, uri: String?): NotificationSettings = when (sound) {
    AppSound.PUSH -> copy(pushSoundUri = uri)
    AppSound.SWIRL -> copy(swirlSoundUri = uri)
    AppSound.PHASE_END -> copy(phaseEndSoundUri = uri)
    AppSound.PAUSE -> copy(pauseSoundUri = uri)
}

/**
 * Creates a [MediaPlayer] for the effective sound (custom if set, else the built-in default,
 * falling back to the default if the custom URI cannot be opened). Returns null if even the
 * default fails. Caller owns releasing the returned player.
 */
fun createAppSoundPlayer(context: Context, sound: AppSound, customUri: String?): MediaPlayer? {
    val fromCustom = customUri?.let { createCustomPlayer(context, Uri.parse(it)) }
    return fromCustom ?: runCatching { MediaPlayer.create(context, sound.defaultRes) }.getOrNull()
}

/**
 * Plays a cue at the current system volume (no manual override), auto-releasing on completion.
 * Returns the player so the caller can stop/replace an in-progress preview.
 */
fun playAppSoundPreview(context: Context, sound: AppSound, settings: NotificationSettings): MediaPlayer? {
    val mp = createAppSoundPlayer(context, sound, settings.customUriFor(sound)) ?: return null
    mp.setOnCompletionListener { it.release() }
    mp.start()
    return mp
}

private fun createCustomPlayer(context: Context, uri: Uri): MediaPlayer? {
    val mp = MediaPlayer()
    return try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")!!.use { afd ->
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        }
        mp.prepare()
        mp
    } catch (e: Exception) {
        Log.w("AppSound", "Custom sound $uri unusable, falling back to default", e)
        mp.release()
        null
    }
}
