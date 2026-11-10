package dev.fl3xy.wallhue

import android.app.WallpaperManager
import android.content.Context
import android.content.om.FabricatedOverlay
import android.content.om.OverlayIdentifier
import android.content.om.OverlayManagerTransaction
import android.graphics.Bitmap
import android.os.IBinder
import android.util.TypedValue
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.Score
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import androidx.core.graphics.scale

/**
 * Extracts Material You colors from a wallpaper and applies them system-wide as fabricated
 * overlays on `android` (owned by the shell, via Shizuku). Only the Monet palette resources are
 * overridden, so apps reading them (Google / stock apps) follow the wallpaper while One UI keeps
 * its own preset palette.
 */
object ThemeEngine {
    private const val OWNER = "com.android.shell"
    private const val PALETTE = "wallhue_palette"
    private const val TOKENS = "wallhue_tokens"

    init {
        HiddenApiBypass.addHiddenApiExemptions("")
    }

    // Monet shade -> HCT tone
    private val shades = listOf(
        0 to 100, 10 to 99, 50 to 95, 100 to 90, 200 to 80, 300 to 70, 400 to 60,
        500 to 50, 600 to 40, 700 to 30, 800 to 20, 900 to 10, 1000 to 0,
    )

    private val mdc = MaterialDynamicColors()
    private val tokens: List<Pair<String, (MaterialDynamicColors) -> DynamicColor>> = listOf(
        "primary" to { it.primary() },
        "on_primary" to { it.onPrimary() },
        "primary_container" to { it.primaryContainer() },
        "on_primary_container" to { it.onPrimaryContainer() },
        "secondary" to { it.secondary() },
        "on_secondary" to { it.onSecondary() },
        "secondary_container" to { it.secondaryContainer() },
        "on_secondary_container" to { it.onSecondaryContainer() },
        "tertiary" to { it.tertiary() },
        "on_tertiary" to { it.onTertiary() },
        "tertiary_container" to { it.tertiaryContainer() },
        "on_tertiary_container" to { it.onTertiaryContainer() },
        "background" to { it.background() },
        "on_background" to { it.onBackground() },
        "surface" to { it.surface() },
        "on_surface" to { it.onSurface() },
        "surface_variant" to { it.surfaceVariant() },
        "on_surface_variant" to { it.onSurfaceVariant() },
        "outline" to { it.outline() },
        "outline_variant" to { it.outlineVariant() },
        "surface_bright" to { it.surfaceBright() },
        "surface_dim" to { it.surfaceDim() },
        "surface_container" to { it.surfaceContainer() },
        "surface_container_low" to { it.surfaceContainerLow() },
        "surface_container_lowest" to { it.surfaceContainerLowest() },
        "surface_container_high" to { it.surfaceContainerHigh() },
        "surface_container_highest" to { it.surfaceContainerHighest() },
    )

    fun seeds(bitmap: Bitmap): List<Int> {
        val small = bitmap.scale(112, (112f * bitmap.height / bitmap.width).toInt().coerceAtLeast(1))
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        return Score.score(QuantizerCelebi.quantize(px, 128), 4)
    }

    fun scheme(seed: Int, dark: Boolean): DynamicScheme = SchemeTonalSpot(Hct.fromInt(seed), dark, 0.0)

    fun argb(scheme: DynamicScheme, pick: (MaterialDynamicColors) -> DynamicColor) = pick(mdc).getArgb(scheme)

    fun setWallpaper(context: Context, bitmap: Bitmap, flags: Int) {
        WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, flags)
    }

    /** Applies the colors. Throws if Shizuku is unavailable or the system rejects the overlay. */
    fun applyColors(seed: Int) {
        val light = scheme(seed, false)
        val dark = scheme(seed, true)

        val palette = linkedMapOf<String, Int>()
        listOf(
            "accent1" to light.primaryPalette, "accent2" to light.secondaryPalette,
            "accent3" to light.tertiaryPalette, "neutral1" to light.neutralPalette,
            "neutral2" to light.neutralVariantPalette,
        ).forEach { (name, p) ->
            shades.forEach { (shade, tone) -> palette["android:color/system_${name}_$shade"] = p.tone(tone) }
        }
        commit(listOf(overlay(PALETTE, palette)))

        // Android 14+ semantic tokens; best effort, the palette above is what matters most.
        val tokenValues = linkedMapOf<String, Int>()
        tokens.forEach { (name, pick) ->
            tokenValues["android:color/system_${name}_light"] = argb(light, pick)
            tokenValues["android:color/system_${name}_dark"] = argb(dark, pick)
        }
        runCatching { commit(listOf(overlay(TOKENS, tokenValues))) }
    }

    fun resetColors() {
        val builder = txBuilder()
        listOf(PALETTE, TOKENS).forEach { name ->
            runCatching {
                builder.javaClass.getMethod("unregisterFabricatedOverlay", OverlayIdentifier::class.java)
                    .invoke(builder, OverlayIdentifier::class.java
                        .getConstructor(String::class.java, String::class.java).newInstance(OWNER, name))
            }
        }
        send(builder)
    }

    private fun overlay(name: String, values: Map<String, Int>): FabricatedOverlay {
        val cls = Class.forName("android.content.om.FabricatedOverlay\$Builder")
        val b = cls.getConstructor(String::class.java, String::class.java, String::class.java)
            .newInstance(OWNER, name, "android")
        val set = cls.getMethod(
            "setResourceValue", String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
        )
        values.forEach { (res, color) -> set.invoke(b, res, TypedValue.TYPE_INT_COLOR_ARGB8, color) }
        return cls.getMethod("build").invoke(b) as FabricatedOverlay
    }

    private fun txBuilder(): Any =
        Class.forName("android.content.om.OverlayManagerTransaction\$Builder").getConstructor().newInstance()

    private fun commit(overlays: List<FabricatedOverlay>) {
        val builder = txBuilder()
        val bc = builder.javaClass
        overlays.forEach { o ->
            bc.getMethod("registerFabricatedOverlay", FabricatedOverlay::class.java).invoke(builder, o)
            bc.getMethod(
                "setEnabled", OverlayIdentifier::class.java, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            ).invoke(builder, o.identifier, true, 0)
        }
        send(builder)
    }

    private fun send(builder: Any) {
        val tx = builder.javaClass.getMethod("build").invoke(builder) as OverlayManagerTransaction
        val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("overlay"))
        val om = Class.forName("android.content.om.IOverlayManager\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
        om.javaClass.getMethod("commit", OverlayManagerTransaction::class.java).invoke(om, tx)
    }
}
