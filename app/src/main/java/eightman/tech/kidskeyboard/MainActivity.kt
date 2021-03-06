package eightman.tech.kidskeyboard

import android.content.Context
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Color
import android.os.*
import android.speech.tts.TextToSpeech
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import eightman.tech.kidskeyboard.databinding.ActivityMainBinding
import java.io.IOException
import java.util.*


class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var history: MutableList<String>
    private var historyIndex = 0
    private var textInput = ""
    private val textOutput: TextView get() = binding.textOutput
    private val imageView: ImageView get() = binding.imageView
    private lateinit var imageList: List<String>
    private var suggestion = ""
    private var isUpperCase = true
    private lateinit var tts: TextToSpeech
    private var soundOn = false
    private val sharedPref by lazy { getPreferences(Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        imageList = R.drawable::class.java.fields.map { it.name }
            .filter { it.startsWith("a_") }

        tts = TextToSpeech(this, this)

        history = sharedPref.getStringSet("HISTORY", emptySet())!!.toMutableList()
    }

    public override fun onDestroy() {
        tts.stop()
        tts.shutdown()

        super.onDestroy()
    }

    private fun addToHistory() {
        history.add(textInput)
        historyIndex = history.size - 1

        // save the task list to preference
        with(sharedPref.edit()) {
            try {
                putStringSet("HISTORY", history.toSet())
            } catch (e: IOException) {
                e.printStackTrace()
            }
            commit()
        }
    }

    private fun vibrate(millis: Long) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val vibratorManager =
                    this.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        millis,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        millis,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            }
            else -> {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(millis)
            }
        }
    }

    fun onKeyClicked(view: View) {
        if (view == binding.keySound) {
            setSound(!soundOn)
            return
        }
        var nextLetter = '*'
        vibrate(10)
        var updateHistory = true
        when (val keyValue = (view as TextView).text) {
            "SHIFT" -> {
                onShift()
            }
            "CLEAR" -> {
                suggestion = ""
                textInput = ""
            }
            "DELETE" -> {
                if (textInput.isNotEmpty()) {
                    textInput = textInput.substring(0, textInput.length - 1)
                }
            }
            "???" -> {
                if (history.size > 0) {
                    if (historyIndex > 0) {
                        historyIndex--
                    } else {
                        historyIndex = history.size - 1
                    }
                    textInput = history[historyIndex]
                    suggestion = textInput
                }
                updateHistory = false
            }
            "???" -> {
                if (history.size > 0) {
                    if (historyIndex < history.size) {
                        historyIndex++
                    }
                    if (historyIndex == history.size) {
                        historyIndex = 0
                    }
                    textInput = history[historyIndex]
                    suggestion = textInput
                }
                updateHistory = false
            }
            else -> {
                textInput = if (textInput.equals(suggestion, true) && suggestion.isNotEmpty()) {
                    "$keyValue"
                } else {
                    "$textInput$keyValue"
                }
            }
        }

        textInput = textInput.trimStart()
        if (textInput.isNotEmpty()) {
            nextLetter = '-'
        }

        try {
            val textSearch = if (suggestion.startsWith(
                    textInput.trim(),
                    true
                ) && textInput.trim().length != suggestion.length
            ) {
                "__"
            } else {
                textInput
            }
            resources.getIdentifier(
                "drawable/a_${textSearch.lowercase().replace(' ', '_')}",
                null,
                packageName
            ).let {
                imageView.setImageDrawable(ContextCompat.getDrawable(this, it))
            }
            // match
            nextLetter = '+'
            vibrate(100)
            suggestion = ""
            textOutput.text = textInput
            speakOut(textInput)

            if (updateHistory && history.none { it.equals(textInput, true) }) {
                addToHistory()
            }
        } catch (t: Throwable) {
            // no match
            imageView.setImageDrawable(null)

            if (textInput.isNotEmpty()) {
                if (!suggestion.startsWith(textInput, true)) {
                    suggestion = ""
                }

                if (suggestion.isEmpty()) {
                    val sorted =
                        imageList.filter { it.startsWith("a_${textInput.replace(' ', '_')}", true) }
                            .sortedBy { it.length }
                    var reduced = sorted
                    while (reduced.isNotEmpty() && history.any {
                            it.equals(
                                reduced.first().substring(2).replace('_', ' '),
                                true
                            )
                        }) {
                        reduced = reduced.subList(1, reduced.size)
                    }

                    if (reduced.isNotEmpty()) {
                        suggestion = reduced.first().substring(2).replace('_', ' ')
                    } else {
                        sorted.randomOrNull()
                            ?.let {
                                suggestion = it.substring(2).replace('_', ' ')
                            }
                    }
                }
            } else {
                suggestion = ""
            }

            if (suggestion.startsWith(textInput, true) && !suggestion.equals(textInput, true)) {
                suggestion = if (isUpperCase) {
                    suggestion.uppercase()
                } else {
                    suggestion.lowercase()
                }
                val leftover = suggestion.substring(textInput.length)
                nextLetter = leftover.lowercase().first()
                val textToSpan: Spannable =
                    SpannableString("$textInput$leftover")

                textToSpan.setSpan(
                    ForegroundColorSpan(if (isDarkThemeOn()) Color.DKGRAY else Color.LTGRAY),
                    textInput.length,
                    suggestion.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                textOutput.text = textToSpan
            } else {
                textOutput.text = textInput
            }
        }

        setColors(nextLetter)
    }

    private fun isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES
    }

    private fun onShift() {
        val toLowerCase = isUpperCase
        binding.keyA.text = if (toLowerCase) "a" else "A"
        binding.keyB.text = if (toLowerCase) "b" else "B"
        binding.keyC.text = if (toLowerCase) "c" else "C"
        binding.keyD.text = if (toLowerCase) "d" else "D"
        binding.keyE.text = if (toLowerCase) "e" else "E"
        binding.keyF.text = if (toLowerCase) "f" else "F"
        binding.keyG.text = if (toLowerCase) "g" else "G"
        binding.keyH.text = if (toLowerCase) "h" else "H"
        binding.keyI.text = if (toLowerCase) "i" else "I"
        binding.keyJ.text = if (toLowerCase) "j" else "J"
        binding.keyK.text = if (toLowerCase) "k" else "K"
        binding.keyL.text = if (toLowerCase) "l" else "L"
        binding.keyM.text = if (toLowerCase) "m" else "M"
        binding.keyN.text = if (toLowerCase) "n" else "N"
        binding.keyO.text = if (toLowerCase) "o" else "O"
        binding.keyP.text = if (toLowerCase) "p" else "P"
        binding.keyQ.text = if (toLowerCase) "q" else "Q"
        binding.keyR.text = if (toLowerCase) "r" else "R"
        binding.keyS.text = if (toLowerCase) "s" else "S"
        binding.keyT.text = if (toLowerCase) "t" else "T"
        binding.keyU.text = if (toLowerCase) "u" else "U"
        binding.keyV.text = if (toLowerCase) "v" else "V"
        binding.keyW.text = if (toLowerCase) "w" else "W"
        binding.keyX.text = if (toLowerCase) "x" else "X"
        binding.keyY.text = if (toLowerCase) "y" else "Y"
        binding.keyZ.text = if (toLowerCase) "z" else "Z"
        isUpperCase = !isUpperCase
    }

    private fun setColors(letter: Char) {
        val nextColor = ContextCompat.getColor(this, R.color.teal_700)
        val otherColor = ContextCompat.getColor(
            this,
            if (isDarkThemeOn()) R.color.purple_200 else R.color.purple_500
        )
        binding.keyA.setBackgroundColor(if (letter == 'a') nextColor else otherColor)
        binding.keyB.setBackgroundColor(if (letter == 'b') nextColor else otherColor)
        binding.keyC.setBackgroundColor(if (letter == 'c') nextColor else otherColor)
        binding.keyD.setBackgroundColor(if (letter == 'd') nextColor else otherColor)
        binding.keyE.setBackgroundColor(if (letter == 'e') nextColor else otherColor)
        binding.keyF.setBackgroundColor(if (letter == 'f') nextColor else otherColor)
        binding.keyG.setBackgroundColor(if (letter == 'g') nextColor else otherColor)
        binding.keyH.setBackgroundColor(if (letter == 'h') nextColor else otherColor)
        binding.keyI.setBackgroundColor(if (letter == 'i') nextColor else otherColor)
        binding.keyJ.setBackgroundColor(if (letter == 'j') nextColor else otherColor)
        binding.keyK.setBackgroundColor(if (letter == 'k') nextColor else otherColor)
        binding.keyL.setBackgroundColor(if (letter == 'l') nextColor else otherColor)
        binding.keyM.setBackgroundColor(if (letter == 'm') nextColor else otherColor)
        binding.keyN.setBackgroundColor(if (letter == 'n') nextColor else otherColor)
        binding.keyO.setBackgroundColor(if (letter == 'o') nextColor else otherColor)
        binding.keyP.setBackgroundColor(if (letter == 'p') nextColor else otherColor)
        binding.keyQ.setBackgroundColor(if (letter == 'q') nextColor else otherColor)
        binding.keyR.setBackgroundColor(if (letter == 'r') nextColor else otherColor)
        binding.keyS.setBackgroundColor(if (letter == 's') nextColor else otherColor)
        binding.keyT.setBackgroundColor(if (letter == 't') nextColor else otherColor)
        binding.keyU.setBackgroundColor(if (letter == 'u') nextColor else otherColor)
        binding.keyV.setBackgroundColor(if (letter == 'v') nextColor else otherColor)
        binding.keyW.setBackgroundColor(if (letter == 'w') nextColor else otherColor)
        binding.keyX.setBackgroundColor(if (letter == 'x') nextColor else otherColor)
        binding.keyY.setBackgroundColor(if (letter == 'y') nextColor else otherColor)
        binding.keyZ.setBackgroundColor(if (letter == 'z') nextColor else otherColor)
        binding.keySpace.setBackgroundColor(if (letter == ' ') nextColor else otherColor)
        binding.keyClear.setBackgroundColor(if (letter == '+') nextColor else otherColor)
        binding.keyDelete.setBackgroundColor(if (letter == '-') nextColor else otherColor)
    }

    private fun speakOut(text: String) {
        if (soundOn) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
        }
    }

    private fun setSound(on: Boolean) {
        if (on) {
            soundOn = true
            resources.getIdentifier(
                "drawable/soundon",
                null,
                packageName
            ).let {
                binding.keySound.setImageDrawable(ContextCompat.getDrawable(this, it))
            }
        } else {
            soundOn = false
            resources.getIdentifier(
                "drawable/soundoff",
                null,
                packageName
            ).let {
                binding.keySound.setImageDrawable(ContextCompat.getDrawable(this, it))
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            binding.keySound.visibility = View.VISIBLE
            tts.language = Locale.US
            setSound(true)
        }
    }
}