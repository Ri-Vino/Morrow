@file:OptIn(ExperimentalForeignApi::class)

package app.morrow.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeSpokenAudio
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesisVoiceQualityEnhanced
import platform.AVFAudio.AVSpeechSynthesisVoiceQualityPremium
import platform.AVFAudio.AVSpeechSynthesisVoiceTraitIsNoveltyVoice
import platform.AVFAudio.AVSpeechSynthesisVoiceTraitIsPersonalVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.setActive
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSLocaleLanguageCode
import platform.Foundation.currentLocale
import platform.Foundation.preferredLanguages
import platform.darwin.NSObject
import kotlin.time.Duration.Companion.seconds

/**
 * Pip's voice through AVSpeechSynthesizer, which speaks on the phone with nothing sent anywhere. It speaks over the
 * playback audio session, so the silent switch doesn't mute it, ducking any music while it talks. With no voice
 * chosen, Pip uses the best one installed for the phone's language: Premium, then Enhanced.
 * Use it from the main thread.
 */
class IosSpeaker : Speaker {

    private class Utterance(val spoken: AVSpeechUtterance, val done: CompletableDeferred<Unit> = CompletableDeferred())

    private val synthesizer = AVSpeechSynthesizer()
    // The synthesizer holds its delegate weakly, so this keeps it.
    private val listener = SpeechListener { finished -> current?.takeIf { it.spoken === finished }?.done?.complete(Unit) }
    private var current: Utterance? = null
    private var chosenVoice: String? = null

    init {
        synthesizer.delegate = listener
    }

    override suspend fun speak(text: String, purpose: SpeechPurpose) {
        if (text.isBlank()) return
        withContext(Dispatchers.Main) {
            cutOff()
            val utterance = Utterance(AVSpeechUtterance(string = text).apply { voice = pick() })
            current = utterance
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, mode = AVAudioSessionModeSpokenAudio, options = AVAudioSessionCategoryOptionDuckOthers, error = null)
            session.setActive(true, error = null)
            try {
                synthesizer.speakUtterance(utterance.spoken)
                withTimeoutOrNull(SPEECH_LIMIT) { utterance.done.await() }
            } finally {
                if (current === utterance) {
                    // Cancelled or timed out rather than finished.
                    if (!utterance.done.isCompleted) synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
                    current = null
                    // Lets music that was ducked come back up.
                    session.setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
                }
            }
        }
    }

    override fun stop() {
        cutOff()
    }

    override suspend fun voices(): List<VoiceInfo> = withContext(Dispatchers.Main) {
        installed().map { VoiceInfo(it.identifier, it.name, languageLabel(it.language)) }
    }

    override fun setVoice(id: String?) {
        chosenVoice = id
    }

    private fun cutOff() {
        current?.done?.complete(Unit)
        if (synthesizer.speaking) synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }

    /** The chosen voice if it's still installed, else the best one; null leaves it to the phone. */
    private fun pick(): AVSpeechSynthesisVoice? {
        val voices = installed()
        return voices.firstOrNull { it.identifier == chosenVoice } ?: voices.firstOrNull()
    }

    /** The phone's language's voices, best first: higher quality, then the phone's own region, then name. */
    private fun installed(): List<AVSpeechSynthesisVoice> {
        val phone = phoneLanguage()
        val language = phone.substringBefore('-')
        return AVSpeechSynthesisVoice.speechVoices()
            .filterIsInstance<AVSpeechSynthesisVoice>()
            .filter { it.language.substringBefore('-').equals(language, ignoreCase = true) }
            // Novelty voices are jokes, and a personal voice needs its own permission.
            .filter { it.voiceTraits and (AVSpeechSynthesisVoiceTraitIsNoveltyVoice or AVSpeechSynthesisVoiceTraitIsPersonalVoice) == 0uL }
            .sortedWith(
                compareByDescending<AVSpeechSynthesisVoice> { rank(it) }
                    .thenByDescending { it.language.equals(phone, ignoreCase = true) }
                    .thenBy { it.name },
            )
    }

    private fun rank(voice: AVSpeechSynthesisVoice): Int = when (voice.quality) {
        AVSpeechSynthesisVoiceQualityPremium -> 2
        AVSpeechSynthesisVoiceQualityEnhanced -> 1
        else -> 0
    }

    /** Like "en-GB": the phone's first language. */
    private fun phoneLanguage(): String =
        (NSLocale.preferredLanguages.firstOrNull() as? String) ?: AVSpeechSynthesisVoice.currentLanguageCode()

    /** "English (UK)": a voice's language and region, in the phone's language. */
    private fun languageLabel(code: String): String {
        val locale = NSLocale.currentLocale
        val language = locale.displayNameForKey(NSLocaleLanguageCode, value = code.substringBefore('-'))?.replaceFirstChar { it.titlecase() } ?: code
        val region = code.substringAfter('-', missingDelimiterValue = "")
        val regionName = when (region) {
            "" -> return language
            "GB" -> "UK"
            "US" -> "US"
            else -> locale.displayNameForKey(NSLocaleCountryCode, value = region) ?: region
        }
        return "$language ($regionName)"
    }

    companion object {
        /** A ceiling on one utterance, in case the synthesizer never says it has finished. */
        val SPEECH_LIMIT = 60.seconds
    }
}

private class SpeechListener(private val onFinish: (AVSpeechUtterance) -> Unit) : NSObject(), AVSpeechSynthesizerDelegateProtocol {
    // Only the finish is needed: Morrow ends its own wait when it cuts an utterance off.
    override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didFinishSpeechUtterance: AVSpeechUtterance) {
        onFinish(didFinishSpeechUtterance)
    }
}
