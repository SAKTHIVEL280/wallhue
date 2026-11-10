package dev.fl3xy.wallhue

import android.Manifest
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.graphics.scale
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.Score
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Extracts Material You colors from a wallpaper and hands the seed to the system's own Monet
 * engine (the `theme_customization_overlay_packages` secure setting). Writing it needs
 * WRITE_SECURE_SETTINGS, granted once via Shizuku or adb; the grant survives reboots.
 */
object ThemeEngine {
    private const val KEY = "theme_customization_overlay_packages"
    private const val PREFS = "wallhue"
    private const val ORIGINAL = "original_theme"

    private val mdc = MaterialDynamicColors()

    fun canWrite(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

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

    fun applyColors(context: Context, seed: Int) {
        val cr = context.contentResolver
        val current = Settings.Secure.getString(cr, KEY)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(ORIGINAL)) prefs.edit { putString(ORIGINAL, current ?: "") }

        val hex = "%06X".format(seed and 0xFFFFFF)
        val json = runCatching { JSONObject(current ?: "{}") }.getOrDefault(JSONObject())
        json.put("android.theme.customization.color_source", "preset")
        json.put("android.theme.customization.theme_style", "TONAL_SPOT")
        json.put("android.theme.customization.system_palette", hex)
        json.put("android.theme.customization.accent_color", hex)
        json.put("_applied_timestamp", System.currentTimeMillis())
        check(Settings.Secure.putString(cr, KEY, json.toString())) { "System refused the color change" }
    }

    /** Restores the color setting that was active before Wallhue's first apply. */
    fun resetColors(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val original = prefs.getString(ORIGINAL, null) ?: error("Nothing to reset. Wallhue hasn't changed your colors.")
        val json = runCatching { JSONObject(original.ifEmpty { "{}" }) }.getOrDefault(JSONObject())
        json.put("_applied_timestamp", System.currentTimeMillis())
        check(Settings.Secure.putString(context.contentResolver, KEY, json.toString())) { "System refused the reset" }
        prefs.edit { remove(ORIGINAL) }
    }

    /** One-time setup: asks the Shizuku helper (shell user) to grant WRITE_SECURE_SETTINGS. */
    fun grantWithShizuku() {
        check(Shizuku.pingBinder()) { "Shizuku is not running" }
        val latch = CountDownLatch(1)
        var service: IOverlayService? = null
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = IOverlayService.Stub.asInterface(binder); latch.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        val args = Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, OverlayService::class.java.name))
            .daemon(false).processNameSuffix("helper").debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE)
        Handler(Looper.getMainLooper()).post { Shizuku.bindUserService(args, conn) }
        check(latch.await(15, TimeUnit.SECONDS)) { "Couldn't start the Shizuku helper" }
        try {
            service!!.grant(BuildConfig.APPLICATION_ID)?.let { error(it) }
        } finally {
            Handler(Looper.getMainLooper()).post { Shizuku.unbindUserService(args, conn, true) }
        }
    }
}
