package com.itsme.hp

import android.app.admin.DeviceAdminReceiver

/** Penerima Device Admin.
 *
 *  Wajib ada supaya pemilik bisa menjadikan aplikasi ini **Device Owner** sekali saja
 *  lewat ADB (`adb shell dpm set-device-owner com.itsme.hp/.HpAdmin`). Sesudah itu
 *  izin bisa diberikan tanpa dialog, aplikasi bisa dikelola, dan HP bisa dikunci —
 *  semuanya tanpa root dan tanpa Play Store.
 */
class HpAdmin : DeviceAdminReceiver()
