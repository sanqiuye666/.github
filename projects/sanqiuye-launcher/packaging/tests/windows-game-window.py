"""仅对专用测试记录中的 PID 与开始时间操作窗口；不终止任何进程。"""
import argparse
import ctypes
from ctypes import wintypes
from datetime import datetime, timezone
import json
from pathlib import Path
import sys
import time


def main():
    if sys.platform != "win32":
        raise RuntimeError("本验收辅助只适用于 Windows")
    parser = argparse.ArgumentParser()
    parser.add_argument("--pid", type=int, required=True)
    parser.add_argument("--started-at")
    parser.add_argument("--action", choices=["inspect", "window-info", "capture", "close", "wake", "resize"], required=True)
    parser.add_argument("--width", type=int)
    parser.add_argument("--height", type=int)
    parser.add_argument("--output")
    parser.add_argument("--title", help="指定原生程序标题；默认只接受Minecraft窗口")
    parser.add_argument("--keep-visible", action="store_true")
    parser.add_argument("--capture-screen", action="store_true", help="仅在已验证窗口位于前台时截取其屏幕矩形")
    args = parser.parse_args()
    if args.action != "inspect" and not args.started_at:
        parser.error("操作窗口必须提供本次进程的精确开始时间")
    user = ctypes.WinDLL("user32", use_last_error=True)
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    # 线程使用物理像素坐标，避免高 DPI 下截入邻近桌面或裁掉游戏窗口。
    user.SetThreadDpiAwarenessContext.argtypes = [wintypes.HANDLE]
    user.SetThreadDpiAwarenessContext.restype = wintypes.HANDLE
    if not user.SetThreadDpiAwarenessContext(ctypes.c_void_p(-4)):
        raise ctypes.WinError(ctypes.get_last_error())
    kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
    kernel.OpenProcess.restype = wintypes.HANDLE
    kernel.GetProcessTimes.argtypes = [wintypes.HANDLE] + [ctypes.POINTER(wintypes.FILETIME)] * 4
    kernel.GetExitCodeProcess.argtypes = [wintypes.HANDLE, ctypes.POINTER(wintypes.DWORD)]
    kernel.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]
    kernel.CloseHandle.argtypes = [wintypes.HANDLE]
    handle = kernel.OpenProcess(0x1000 | 0x100000, False, args.pid)
    if not handle:
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        times = [wintypes.FILETIME() for _ in range(4)]
        if not kernel.GetProcessTimes(handle, *[ctypes.byref(value) for value in times]):
            raise ctypes.WinError(ctypes.get_last_error())
        creation = ((times[0].dwHighDateTime << 32) | times[0].dwLowDateTime) / 10000000 - 11644473600
        if args.action == "inspect":
            kernel.QueryFullProcessImageNameW.argtypes = [wintypes.HANDLE, wintypes.DWORD, wintypes.LPWSTR, ctypes.POINTER(wintypes.DWORD)]
            executable = ctypes.create_unicode_buffer(32768)
            length = wintypes.DWORD(len(executable))
            if not kernel.QueryFullProcessImageNameW(handle, 0, executable, ctypes.byref(length)):
                raise ctypes.WinError(ctypes.get_last_error())
            print(json.dumps({"pid": args.pid, "processStartedAt": datetime.fromtimestamp(creation, timezone.utc).isoformat(), "executable": executable.value}))
            return
        expected = datetime.fromisoformat(args.started_at.replace("Z", "+00:00")).timestamp()
        if abs(creation - expected) > 0.002:
            raise RuntimeError("PID 开始时间不匹配，拒绝操作窗口")
        callback_type = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
        user.EnumWindows.argtypes = [callback_type, wintypes.LPARAM]
        user.GetWindowThreadProcessId.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.DWORD)]
        user.IsWindowVisible.argtypes = [wintypes.HWND]
        user.IsIconic.argtypes = [wintypes.HWND]
        user.IsZoomed.argtypes = [wintypes.HWND]
        user.ShowWindow.argtypes = [wintypes.HWND, ctypes.c_int]
        user.GetWindowTextW.argtypes = [wintypes.HWND, wintypes.LPWSTR, ctypes.c_int]
        user.GetClassNameW.argtypes = [wintypes.HWND, wintypes.LPWSTR, ctypes.c_int]
        user.PostMessageW.argtypes = [wintypes.HWND, wintypes.UINT, wintypes.WPARAM, wintypes.LPARAM]
        windows = []
        @callback_type
        def visit(window, _):
            pid = wintypes.DWORD()
            user.GetWindowThreadProcessId(window, ctypes.byref(pid))
            if pid.value == args.pid and user.IsWindowVisible(window):
                title, kind = ctypes.create_unicode_buffer(512), ctypes.create_unicode_buffer(256)
                user.GetWindowTextW(window, title, 512)
                user.GetClassNameW(window, kind, 256)
                accepted = args.title in title.value if args.title else ("Minecraft" in title.value or "GLFW" in kind.value or "LWJGL" in kind.value)
                if accepted:
                    windows.append((window, title.value, kind.value))
            return True
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            windows.clear()
            user.EnumWindows(visit, 0)
            if windows:
                break
            if kernel.WaitForSingleObject(handle, 500) == 0:
                raise RuntimeError("游戏进程退出且没有 Minecraft 窗口")
        if not windows:
            raise RuntimeError("没有找到属于本次进程的可见 Minecraft 窗口")
        window, title, kind = windows[0]
        result = {"pid": args.pid, "processStartedAt": datetime.fromtimestamp(creation, timezone.utc).isoformat(), "title": title, "windowClass": kind, "windowDetected": True, "minimized": bool(user.IsIconic(window)), "maximized": bool(user.IsZoomed(window))}
        if args.action == "resize":
            if not args.width or not args.height or not 960 <= args.width <= 1920 or not 560 <= args.height <= 1200:
                raise RuntimeError("测试窗口尺寸必须在已允许范围内")
            user.SetWindowPos.argtypes = [wintypes.HWND, wintypes.HWND, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_int, wintypes.UINT]
            user.ShowWindow(window, 9)
            if not user.SetWindowPos(window, None, 30, 30, args.width, args.height, 0x0004 | 0x0010):
                raise ctypes.WinError(ctypes.get_last_error())
            result["requestedSize"] = [args.width, args.height]
            user.GetWindowRect.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.RECT)]
            rectangle = wintypes.RECT()
            if not user.GetWindowRect(window, ctypes.byref(rectangle)):
                raise ctypes.WinError(ctypes.get_last_error())
            result["actualSize"] = [rectangle.right - rectangle.left, rectangle.bottom - rectangle.top]
            print(json.dumps(result, ensure_ascii=False))
            return
        if args.action == "window-info":
            print(json.dumps(result, ensure_ascii=False))
            return
        if args.action == "wake":
            # 仅给已核对PID/开始时间/标题的测试窗口投递空消息，不操作其他程序。
            if not user.PostMessageW(window, 0, 0, 0):
                raise ctypes.WinError(ctypes.get_last_error())
            result["wakeRequested"] = True
            print(json.dumps(result, ensure_ascii=False))
            return
        if args.action == "capture":
            from PIL import ImageGrab
            output = Path(args.output)
            if not output.is_absolute() or output.exists():
                raise RuntimeError("截图需要新的绝对输出路径")
            # Pillow 的 HWND 捕获只读取已核对的游戏窗口，不依赖抢占前台。
            user.ShowWindow(window, 4)
            time.sleep(3)
            if args.capture_screen:
                user.SetForegroundWindow.argtypes = [wintypes.HWND]
                user.GetForegroundWindow.restype = wintypes.HWND
                user.GetWindowRect.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.RECT)]
                user.ShowWindow(window, 9)
                user.SetForegroundWindow(window)
                time.sleep(.4)
                if user.GetForegroundWindow() != window:
                    raise RuntimeError("本次窗口不在前台，拒绝截入其他应用")
                rectangle = wintypes.RECT()
                if not user.GetWindowRect(window, ctypes.byref(rectangle)):
                    raise ctypes.WinError(ctypes.get_last_error())
                captured = ImageGrab.grab(bbox=(rectangle.left, rectangle.top, rectangle.right, rectangle.bottom), all_screens=True)
            else:
                captured = ImageGrab.grab(window=window)
            if captured.width < 300 or captured.height < 200:
                raise RuntimeError("游戏窗口尺寸无效")
            output.parent.mkdir(parents=True, exist_ok=True)
            captured.save(output)
            # 截图后仅最小化本次测试游戏，避免长时间遮住用户正在使用的界面。
            if not args.keep_visible:
                user.ShowWindow(window, 6)
            result["screenshot"] = str(output)
            result["captureMethod"] = "owned-foreground-rectangle" if args.capture_screen else "HWND"
        else:
            if not user.PostMessageW(window, 0x0010, 0, 0):
                raise ctypes.WinError(ctypes.get_last_error())
            if kernel.WaitForSingleObject(handle, 30000) != 0:
                raise RuntimeError("游戏窗口关闭请求后仍未退出；没有强制结束")
            code = wintypes.DWORD()
            if not kernel.GetExitCodeProcess(handle, ctypes.byref(code)):
                raise ctypes.WinError(ctypes.get_last_error())
            result.update({"windowCloseRequested": True, "exitCode": code.value, "normalExitConfirmed": code.value == 0})
        print(json.dumps(result, ensure_ascii=False))
    finally:
        kernel.CloseHandle(handle)


if __name__ == "__main__":
    main()
