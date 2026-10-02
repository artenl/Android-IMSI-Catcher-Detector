package org.cellularprivacy.detector.panic

import android.app.admin.DeviceAdminReceiver

/**
 * Device Admin receiver. Granting this (user action) lets the app lock the
 * screen immediately via DevicePolicyManager.lockNow(). We request no other
 * admin power: it is the least privilege needed for the LOCK panic action.
 */
class LockAdminReceiver : DeviceAdminReceiver()
