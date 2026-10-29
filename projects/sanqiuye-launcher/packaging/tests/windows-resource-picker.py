"""仅取消指定测试进程的资源选择对话框；不关闭主窗口。"""
import argparse
import ctypes
from ctypes import wintypes
from datetime import datetime
import json
import time

p = argparse.ArgumentParser()
p.add_argument('--pid', type=int, required=True)
p.add_argument('--started-at', required=True)
a = p.parse_args()
k = ctypes.WinDLL('kernel32', use_last_error=True)
u = ctypes.WinDLL('user32', use_last_error=True)
k.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
k.OpenProcess.restype = wintypes.HANDLE
k.GetProcessTimes.argtypes = [wintypes.HANDLE] + [ctypes.POINTER(wintypes.FILETIME)] * 4
k.CloseHandle.argtypes = [wintypes.HANDLE]
handle = k.OpenProcess(0x1000, False, a.pid)
if not handle:
    raise ctypes.WinError(ctypes.get_last_error())
try:
    times = [wintypes.FILETIME() for _ in range(4)]
    if not k.GetProcessTimes(handle, *[ctypes.byref(t) for t in times]):
        raise ctypes.WinError(ctypes.get_last_error())
    started = ((times[0].dwHighDateTime << 32) | times[0].dwLowDateTime) / 10000000 - 11644473600
    if abs(started - datetime.fromisoformat(a.started_at.replace('Z', '+00:00')).timestamp()) > .002:
        raise RuntimeError('PID开始时间不匹配')
    callback = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
    u.EnumWindows.argtypes = [callback, wintypes.LPARAM]
    u.GetWindowThreadProcessId.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.DWORD)]
    u.GetWindowTextW.argtypes = [wintypes.HWND, wintypes.LPWSTR, ctypes.c_int]
    u.GetClassNameW.argtypes = [wintypes.HWND, wintypes.LPWSTR, ctypes.c_int]
    u.PostMessageW.argtypes = [wintypes.HWND, wintypes.UINT, wintypes.WPARAM, wintypes.LPARAM]
    matches = []
    @callback
    def visit(hwnd, _):
        pid = wintypes.DWORD()
        u.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        title, kind = ctypes.create_unicode_buffer(512), ctypes.create_unicode_buffer(256)
        u.GetWindowTextW(hwnd, title, 512)
        u.GetClassNameW(hwnd, kind, 256)
        if pid.value == a.pid and title.value == '选择要导入到三秋叶实例的资源' and kind.value == '#32770':
            matches.append(hwnd)
        return True
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline and not matches:
        u.EnumWindows(visit, 0)
        time.sleep(.1)
    if len(matches) != 1:
        raise RuntimeError('不能唯一确认本次资源文件选择框')
    # WM_COMMAND / IDCANCEL 仅投递给已核对的系统对话框。
    if not u.PostMessageW(matches[0], 0x0111, 2, 0):
        raise ctypes.WinError(ctypes.get_last_error())
    print(json.dumps({'pid': a.pid, 'startedAt': a.started_at, 'ownedPickerCancelled': True}))
finally:
    k.CloseHandle(handle)
