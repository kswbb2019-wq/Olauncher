package app.olauncher.data

import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import org.json.JSONException
import org.json.JSONObject

/**
 * Appearance overrides pasted as JSON in settings, documented in THEME.md.
 * Every key is optional; a missing key keeps the launcher's normal look.
 */
class LauncherTheme private constructor(private val values: Map<String, Any>) {

    val clockVisible get() = values[CLOCK_VISIBLE] as Boolean?
    val dateVisible get() = values[DATE_VISIBLE] as Boolean?
    val clockSize get() = values[CLOCK_SIZE] as Float?
    val dateSize get() = values[DATE_SIZE] as Float?
    val clockFont get() = values[CLOCK_FONT] as String?
    val clockAlign get() = values[CLOCK_ALIGN] as Int?
    val clockMarginTop get() = values[CLOCK_MARGIN_TOP] as Float?
    val clockMarginSide get() = values[CLOCK_MARGIN_SIDE] as Float?

    val homeTextSize get() = values[HOME_TEXT_SIZE] as Float?
    val homeFont get() = values[HOME_FONT] as String?
    val homeAlign get() = values[HOME_ALIGN] as Int?
    val homePosition get() = values[HOME_POSITION] as Int?
    val homeAppSpacing get() = values[HOME_APP_SPACING] as Float?
    val homeMarginSide get() = values[HOME_MARGIN_SIDE] as Float?

    val drawerTextSize get() = values[DRAWER_TEXT_SIZE] as Float?
    val drawerFont get() = values[DRAWER_FONT] as String?
    val drawerAlign get() = values[DRAWER_ALIGN] as Int?
    val drawerAppSpacing get() = values[DRAWER_APP_SPACING] as Float?

    val textColor get() = values[TEXT_COLOR] as Int?
    val shadowColor get() = values[SHADOW_COLOR] as Int?
    val shadowRadius get() = values[SHADOW_RADIUS] as Float?
    val shadowDy get() = values[SHADOW_DY] as Float?

    /** Applies the shared text color and shadow, plus [size] in sp and [font] when given. */
    fun styleText(view: TextView, size: Float? = null, font: String? = null) {
        size?.let { view.setTextSize(TypedValue.COMPLEX_UNIT_SP, it) }
        font?.let { view.typeface = Typeface.create(it, Typeface.NORMAL) }
        textColor?.let { view.setTextColor(it) }
        if (shadowColor != null || shadowRadius != null || shadowDy != null)
            view.setShadowLayer(
                shadowRadius ?: view.shadowRadius,
                view.shadowDx,
                shadowDy ?: view.shadowDy,
                shadowColor ?: view.shadowColor
            )
    }

    sealed class ParseResult {
        class Ok(val theme: LauncherTheme) : ParseResult()

        /** [line] and [column] are 1-based, null when the parser gave no position. */
        class SyntaxError(val line: Int?, val column: Int?) : ParseResult()
        class UnknownKey(val key: String) : ParseResult()
        class InvalidValue(val key: String, val expected: String) : ParseResult()
    }

    private sealed class Spec(val expected: String) {
        abstract fun read(value: Any): Any?

        object Bool : Spec("true | false") {
            override fun read(value: Any) = value as? Boolean
        }

        class Number(private val min: Float, private val max: Float) :
            Spec("${min.toInt()}..${max.toInt()}") {
            override fun read(value: Any) = (value as? kotlin.Number)?.toFloat()?.takeIf { it in min..max }
        }

        object ColorHex : Spec("#RRGGBB | #AARRGGBB") {
            override fun read(value: Any) = (value as? String)?.takeIf { it.startsWith("#") }?.let {
                try {
                    Color.parseColor(it)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        }

        object Font : Spec(FONTS.joinToString(" | ")) {
            override fun read(value: Any) = (value as? String)?.takeIf { it in FONTS }
        }

        // Same gravity values as Prefs.homeAlignment
        object Align : Spec("left | center | right") {
            override fun read(value: Any) = when (value) {
                "left" -> Gravity.START
                "center" -> Gravity.CENTER
                "right" -> Gravity.END
                else -> null
            }
        }

        object Position : Spec("top | center | bottom") {
            override fun read(value: Any) = when (value) {
                "top" -> Gravity.TOP
                "center" -> Gravity.CENTER_VERTICAL
                "bottom" -> Gravity.BOTTOM
                else -> null
            }
        }
    }

    companion object {
        private const val CLOCK_VISIBLE = "clockVisible"
        private const val DATE_VISIBLE = "dateVisible"
        private const val CLOCK_SIZE = "clockSize"
        private const val DATE_SIZE = "dateSize"
        private const val CLOCK_FONT = "clockFont"
        private const val CLOCK_ALIGN = "clockAlign"
        private const val CLOCK_MARGIN_TOP = "clockMarginTop"
        private const val CLOCK_MARGIN_SIDE = "clockMarginSide"
        private const val HOME_TEXT_SIZE = "homeTextSize"
        private const val HOME_FONT = "homeFont"
        private const val HOME_ALIGN = "homeAlign"
        private const val HOME_POSITION = "homePosition"
        private const val HOME_APP_SPACING = "homeAppSpacing"
        private const val HOME_MARGIN_SIDE = "homeMarginSide"
        private const val DRAWER_TEXT_SIZE = "drawerTextSize"
        private const val DRAWER_FONT = "drawerFont"
        private const val DRAWER_ALIGN = "drawerAlign"
        private const val DRAWER_APP_SPACING = "drawerAppSpacing"
        private const val TEXT_COLOR = "textColor"
        private const val SHADOW_COLOR = "shadowColor"
        private const val SHADOW_RADIUS = "shadowRadius"
        private const val SHADOW_DY = "shadowDy"

        // System font families, so no font files are bundled
        private val FONTS = listOf(
            "sans-serif", "sans-serif-light", "sans-serif-thin", "sans-serif-medium",
            "sans-serif-black", "sans-serif-condensed", "sans-serif-condensed-light",
            "serif", "monospace", "serif-monospace", "casual", "cursive", "sans-serif-smallcaps",
        )

        private val SPECS: Map<String, Spec> = mapOf(
            CLOCK_VISIBLE to Spec.Bool,
            DATE_VISIBLE to Spec.Bool,
            CLOCK_SIZE to Spec.Number(8f, 200f),
            DATE_SIZE to Spec.Number(6f, 80f),
            CLOCK_FONT to Spec.Font,
            CLOCK_ALIGN to Spec.Align,
            CLOCK_MARGIN_TOP to Spec.Number(0f, 400f),
            CLOCK_MARGIN_SIDE to Spec.Number(0f, 400f),
            HOME_TEXT_SIZE to Spec.Number(8f, 120f),
            HOME_FONT to Spec.Font,
            HOME_ALIGN to Spec.Align,
            HOME_POSITION to Spec.Position,
            HOME_APP_SPACING to Spec.Number(0f, 100f),
            HOME_MARGIN_SIDE to Spec.Number(0f, 400f),
            DRAWER_TEXT_SIZE to Spec.Number(8f, 120f),
            DRAWER_FONT to Spec.Font,
            DRAWER_ALIGN to Spec.Align,
            DRAWER_APP_SPACING to Spec.Number(0f, 100f),
            TEXT_COLOR to Spec.ColorHex,
            SHADOW_COLOR to Spec.ColorHex,
            SHADOW_RADIUS to Spec.Number(0f, 25f),
            SHADOW_DY to Spec.Number(-20f, 20f),
        )

        val DEFAULT = LauncherTheme(emptyMap())

        /** The saved theme; anything unreadable falls back to the default look. */
        fun load(json: String): LauncherTheme =
            (parse(json) as? ParseResult.Ok)?.theme ?: DEFAULT

        /** Checks every key and value, stopping at the first problem. */
        fun parse(json: String): ParseResult {
            val obj = try {
                JSONObject(json)
            } catch (e: JSONException) {
                return syntaxError(e, json)
            }
            val values = mutableMapOf<String, Any>()
            for (key in obj.keys()) {
                val spec = SPECS[key] ?: return ParseResult.UnknownKey(key)
                values[key] = spec.read(obj.get(key)) ?: return ParseResult.InvalidValue(key, spec.expected)
            }
            return ParseResult.Ok(LauncherTheme(values))
        }

        // org.json reports "... at character N of <input>", N being the index after the bad character
        private fun syntaxError(e: JSONException, json: String): ParseResult {
            val position = Regex("at character (\\d+)").find(e.message.orEmpty())
                ?.groupValues?.get(1)?.toIntOrNull()
                ?: return ParseResult.SyntaxError(null, null)
            val index = (position - 1).coerceIn(0, json.length)
            val before = json.substring(0, index)
            return ParseResult.SyntaxError(
                line = before.count { it == '\n' } + 1,
                column = index - before.lastIndexOf('\n')
            )
        }
    }
}
