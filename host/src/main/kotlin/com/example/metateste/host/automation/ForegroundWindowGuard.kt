package com.example.metateste.host.automation

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Kernel32Util
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import java.io.File

/**
 * Heuristic safety net: identifies the process behind the currently focused window
 * so [ClipboardPasteInjector] can refuse to type into something that clearly isn't a terminal
 * (e.g. a browser), rather than blindly injecting into whatever happens to have OS focus.
 */
class ForegroundWindowGuard {

    fun foregroundProcessName(): String? {
        val hwnd = User32.INSTANCE.GetForegroundWindow() ?: return null
        val pidRef = IntByReference()
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef)
        if (pidRef.value == 0) return null

        val handle = Kernel32.INSTANCE.OpenProcess(
            WinNT.PROCESS_QUERY_LIMITED_INFORMATION,
            false,
            pidRef.value,
        ) ?: return null

        return try {
            File(Kernel32Util.QueryFullProcessImageName(handle, 0)).name
        } catch (e: Exception) {
            null
        } finally {
            Kernel32.INSTANCE.CloseHandle(handle)
        }
    }

    fun looksLikeTerminal(processName: String?): Boolean =
        processName?.lowercase() in KNOWN_TERMINAL_PROCESS_NAMES

    companion object {
        private val KNOWN_TERMINAL_PROCESS_NAMES = setOf(
            "windowsterminal.exe",
            "wt.exe",
            "cmd.exe",
            "powershell.exe",
            "pwsh.exe",
        )
    }
}
