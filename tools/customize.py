#!/usr/bin/env python3
"""Sesuaikan aplikasi It's Me HP sebelum build, dari input workflow.

Dipanggil GitHub Actions (lihat .github/workflows/build.yml) dengan nilai dari
Pembuat Aplikasi di dashboard It's Me. Hanya mengubah yang diisi; kalau semua
kosong (mis. build dari push biasa), aplikasi dibuat apa adanya. Pustaka standar
saja, jadi tidak perlu dependensi di runner.

Env yang dibaca:
  HP_APP_NAME      nama aplikasi (label di HP)
  HP_ACCENT        warna ikon, hex "#RRGGBB" atau "RRGGBB"
  HP_APP_ID        applicationId, mis. com.itsme.hp
  HP_VERSION_NAME  nama versi, mis. "0.2"
  HP_VERSION_CODE  kode versi (angka); dinaikkan tiap build agar bisa di-upgrade
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STRINGS = "app/src/main/res/values/strings.xml"
COLORS = "app/src/main/res/values/colors.xml"
GRADLE = "app/build.gradle.kts"


def patch(path, pattern, repl, flags=0):
    full = os.path.join(ROOT, path)
    with open(full, encoding="utf-8") as f:
        text = f.read()
    new, n = re.subn(pattern, repl, text, count=1, flags=flags)
    if n == 0:
        print("WARN: pola tidak ditemukan di %s" % path, file=sys.stderr)
        return False
    with open(full, "w", encoding="utf-8") as f:
        f.write(new)
    print("ok: %s" % path)
    return True


def android_string(value):
    """Teks aman untuk nilai <string> Android: escape &, <, > dan apostrof."""
    value = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return value.replace("'", "\\'")


def main():
    name = (os.environ.get("HP_APP_NAME") or "").strip()
    accent = (os.environ.get("HP_ACCENT") or "").strip()
    app_id = (os.environ.get("HP_APP_ID") or "").strip()
    vname = (os.environ.get("HP_VERSION_NAME") or "").strip()
    vcode = (os.environ.get("HP_VERSION_CODE") or "").strip()

    if name:
        patch(STRINGS,
              r'(<string name="app_name">).*?(</string>)',
              lambda m: m.group(1) + android_string(name) + m.group(2))

    if accent:
        acc = accent if accent.startswith("#") else "#" + accent
        if re.fullmatch(r"#[0-9A-Fa-f]{6}", acc):
            patch(COLORS,
                  r'(<color name="ic_bg">)#[0-9A-Fa-f]{6}(</color>)',
                  lambda m: m.group(1) + acc.upper() + m.group(2))
        else:
            print("WARN: warna tidak valid (butuh #RRGGBB): %s" % accent, file=sys.stderr)

    if app_id:
        if re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+", app_id):
            patch(GRADLE, r'applicationId = "[^"]*"', 'applicationId = "%s"' % app_id)
        else:
            print("WARN: applicationId tidak valid: %s" % app_id, file=sys.stderr)

    if vname:
        patch(GRADLE, r'versionName = "[^"]*"', 'versionName = "%s"' % vname)

    if vcode and vcode.isdigit():
        patch(GRADLE, r'versionCode = \d+', 'versionCode = %s' % vcode)


if __name__ == "__main__":
    main()
