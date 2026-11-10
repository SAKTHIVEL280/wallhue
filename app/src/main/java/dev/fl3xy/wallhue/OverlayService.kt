package dev.fl3xy.wallhue

import kotlin.system.exitProcess

/** Shizuku user service, running as the shell user. Only used for the one-time permission grant. */
class OverlayService : IOverlayService.Stub() {
    override fun destroy() = exitProcess(0)

    override fun grant(packageName: String): String? = try {
        val p = ProcessBuilder("pm", "grant", packageName, "android.permission.WRITE_SECURE_SETTINGS")
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        if (p.waitFor() == 0) null else out.ifEmpty { "pm grant failed" }
    } catch (t: Throwable) {
        "${t.javaClass.simpleName}: ${t.message}"
    }
}
