package com.custom.iphone18keyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

class IPhoneKeyboardService : InputMethodService() {

    private lateinit var rootView: View
    private lateinit var row1: LinearLayout
    private lateinit var row2: LinearLayout
    private lateinit var row3: LinearLayout
    private lateinit var btnShift: Button
    private lateinit var btnSymbols: Button
    private lateinit var btnEmoji: Button
    private lateinit var btnSpace: Button
    private lateinit var btnReturn: Button
    private lateinit var tvPred1: TextView
    private lateinit var tvPred2: TextView
    private lateinit var tvPred3: TextView

    private var isShift = true
    private var isCapsLock = false
    private var mode = Mode.ALPHA // ALPHA, NUMBERS, SYMBOLS, EMOJI
    private var lastSpaceTime = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var isDeleting = false

    enum class Mode { ALPHA, NUMBERS, SYMBOLS, EMOJI }

    private val alphaRows = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m")
    )

    private val numberRows = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("-", "/", ":", ";", "(", ")", "Rp", "&", "@", "\""),
        listOf(".", ",", "?", "!", "'")
    )

    private val symbolRows = listOf(
        listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "="),
        listOf("_", "\\", "|", "~", "<", ">", "€", "£", "¥", "•"),
        listOf(".", ",", "?", "!", "'")
    )

    private val emojiRows = listOf(
        listOf("😂", "❤️", "😭", "🔥", "😍", "🙏", "✨", "🥰", "🤣", "😊"),
        listOf("👍", "🥺", "💀", "🎉", "😎", "🤔", "👏", "💯", "🥳", "😴"),
        listOf("🐱", "🍕", "☕", "🚀", "🎧", "📱", "💸")
    )

    override fun onCreateInputView(): View {
        rootView = layoutInflater.inflate(R.layout.keyboard_view, null)

        row1 = rootView.findViewById(R.id.row1)
        row2 = rootView.findViewById(R.id.row2)
        row3 = rootView.findViewById(R.id.row3)
        btnShift = rootView.findViewById(R.id.btnShift)
        btnSymbols = rootView.findViewById(R.id.btnSymbols)
        btnEmoji = rootView.findViewById(R.id.btnEmoji)
        btnSpace = rootView.findViewById(R.id.btnSpace)
        btnReturn = rootView.findViewById(R.id.btnReturn)
        tvPred1 = rootView.findViewById(R.id.tvPred1)
        tvPred2 = rootView.findViewById(R.id.tvPred2)
        tvPred3 = rootView.findViewById(R.id.tvPred3)

        setupControlButtons()
        renderKeys()
        return rootView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        isShift = true
        mode = Mode.ALPHA
        renderKeys()
    }

    private fun setupControlButtons() {
        btnShift.setOnClickListener {
            playFeedback(isSpecial = true)
            when (mode) {
                Mode.ALPHA -> {
                    isShift = !isShift
                    isCapsLock = false
                }
                Mode.NUMBERS -> mode = Mode.SYMBOLS
                Mode.SYMBOLS -> mode = Mode.NUMBERS
                Mode.EMOJI -> mode = Mode.ALPHA
            }
            renderKeys()
        }

        btnShift.setOnLongClickListener {
            if (mode == Mode.ALPHA) {
                playFeedback(isSpecial = true)
                isCapsLock = true
                isShift = true
                renderKeys()
            }
            true
        }

        val btnBackspace = rootView.findViewById<Button>(R.id.btnBackspace)
        btnBackspace.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    playFeedback(isSpecial = true)
                    deleteOneChar()
                    isDeleting = true
                    handler.postDelayed(deleteRunnable, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDeleting = false
                    handler.removeCallbacks(deleteRunnable)
                }
            }
            true
        }

        btnSymbols.setOnClickListener {
            playFeedback(isSpecial = true)
            mode = if (mode == Mode.ALPHA || mode == Mode.EMOJI) Mode.NUMBERS else Mode.ALPHA
            renderKeys()
        }

        btnEmoji.setOnClickListener {
            playFeedback(isSpecial = true)
            mode = if (mode == Mode.EMOJI) Mode.ALPHA else Mode.EMOJI
            renderKeys()
        }

        btnSpace.setOnClickListener {
            playFeedback(isSpecial = true)
            val now = System.currentTimeMillis()
            val ic = currentInputConnection ?: return@setOnClickListener

            // Double space menjadi titik + spasi khas iPhone
            if (now - lastSpaceTime < 350) {
                val before = ic.getTextBeforeCursor(2, 0)
                if (before != null && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                    isShift = true
                    renderKeys()
                    lastSpaceTime = 0
                    return@setOnClickListener
                }
            }
            ic.commitText(" ", 1)
            lastSpaceTime = now
            updatePredictions()
        }

        btnReturn.setOnClickListener {
            playFeedback(isSpecial = true)
            val ic = currentInputConnection ?: return@setOnClickListener
            val action = currentInputEditorInfo?.imeOptions ?: EditorInfo.IME_ACTION_UNSPECIFIED
            if ((action and EditorInfo.IME_MASK_ACTION) == EditorInfo.IME_ACTION_SEND ||
                (action and EditorInfo.IME_MASK_ACTION) == EditorInfo.IME_ACTION_SEARCH) {
                ic.performEditorAction(action and EditorInfo.IME_MASK_ACTION)
            } else {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                isShift = true
                renderKeys()
            }
        }

        listOf(tvPred1, tvPred2, tvPred3).forEach { tv ->
            tv.setOnClickListener {
                playFeedback(isSpecial = false)
                val word = tv.text.toString()
                if (word.isNotEmpty()) {
                    replaceCurrentWord("$word ")
                    updatePredictions()
                }
            }
        }
    }

    private val deleteRunnable = object : Runnable {
        override fun run() {
            if (isDeleting) {
                deleteOneChar()
                playFeedback(isSpecial = true)
                handler.postDelayed(this, 65)
            }
        }
    }

    private fun deleteOneChar() {
        val ic = currentInputConnection ?: return
        val selectedText = ic.getSelectedText(0)
        if (selectedText.isNullOrEmpty()) {
            ic.deleteSurroundingText(1, 0)
        } else {
            ic.commitText("", 1)
        }
        val before = ic.getTextBeforeCursor(1, 0)
        if (before.isNullOrEmpty()) {
            isShift = true
            renderKeys()
        }
        updatePredictions()
    }

    private fun renderKeys() {
        row1.removeAllViews()
        row2.removeAllViews()
        row3.removeAllViews()

        val currentRows = when (mode) {
            Mode.ALPHA -> alphaRows
            Mode.NUMBERS -> numberRows
            Mode.SYMBOLS -> symbolRows
            Mode.EMOJI -> emojiRows
        }

        btnSymbols.text = if (mode == Mode.ALPHA || mode == Mode.EMOJI) "123" else "ABC"
        btnShift.text = when (mode) {
            Mode.ALPHA -> if (isCapsLock) "⇪" else "⇧"
            Mode.NUMBERS -> "#+="
            Mode.SYMBOLS -> "123"
            Mode.EMOJI -> "ABC"
        }

        populateRow(row1, currentRows[0])
        populateRow(row2, currentRows[1])
        populateRow(row3, currentRows[2])
    }

    private fun populateRow(container: LinearLayout, keys: List<String>) {
        val marginPx = (3 * resources.displayMetrics.density).toInt()
        for (rawKey in keys) {
            val label = if (mode == Mode.ALPHA && isShift) rawKey.uppercase() else rawKey
            val btn = Button(this).apply {
                text = label
                isAllCaps = false
                textSize = if (mode == Mode.EMOJI) 21f else 21f
                setTextColor(ContextCompat.getColor(context, R.color.ios_text_light))
                background = ContextCompat.getDrawable(context, R.drawable.bg_key_normal)
                setPadding(0, 0, 0, 0)
                stateListAnimator = null

                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                params.setMargins(marginPx, 0, marginPx, 0)
                layoutParams = params

                setOnClickListener {
                    playFeedback(isSpecial = false)
                    currentInputConnection?.commitText(label, 1)
                    if (mode == Mode.ALPHA && isShift && !isCapsLock) {
                        isShift = false
                        renderKeys()
                    }
                    updatePredictions()
                }
            }
            container.addView(btn)
        }
    }

    private fun replaceCurrentWord(replacement: String) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(30, 0)?.toString() ?: ""
        val lastSpace = before.lastIndexOf(' ')
        val charsToDelete = if (lastSpace == -1) before.length else before.length - lastSpace - 1
        if (charsToDelete > 0) {
            ic.deleteSurroundingText(charsToDelete, 0)
        }
        ic.commitText(replacement, 1)
    }

    private fun updatePredictions() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(20, 0)?.toString() ?: ""
        val lastWord = before.substringAfterLast(' ').lowercase()

        val suggestions = when {
            lastWord.startsWith("ak") -> listOf("Aku", "Akan", "Akhirnya")
            lastWord.startsWith("la") -> listOf("Lagi", "Langsung", "Lama")
            lastWord.startsWith("ma") -> listOf("Mau", "Makan", "Mantap")
            lastWord.startsWith("bi") -> listOf("Bisa", "Biar", "Biasa")
            lastWord.startsWith("ga") -> listOf("Gak", "Gas", "Gampang")
            lastWord.startsWith("ok") -> listOf("Oke", "Ok", "Oke siap")
            else -> listOf("Aku", "Yang", "Bisa")
        }

        tvPred1.text = suggestions[0]
        tvPred2.text = suggestions[1]
        tvPred3.text = suggestions[2]
    }

    private fun playFeedback(isSpecial: Boolean) {
        // Suara klik keyboard sistem
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val soundType = if (isSpecial) AudioManager.FX_KEYPRESS_STANDARD else AudioManager.FX_KEY_CLICK
        audioManager.playSoundEffect(soundType, 0.8f)

        // Getaran Haptic Engine singkat khas iPhone
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(12)
            }
        }
    }
}
