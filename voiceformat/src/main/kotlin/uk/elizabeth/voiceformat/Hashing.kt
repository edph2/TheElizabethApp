// SPDX-License-Identifier: Apache-2.0
package uk.elizabeth.voiceformat

import java.security.MessageDigest

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
