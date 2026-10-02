package com.revivo

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class RevivoNative(private val activity: Activity) {

    @JavascriptInterface
    fun saveImage(base64: String, fileName: String): Boolean {
        return try {
            val decodedBytes = Base64.decode(base64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size) ?: return false
            val resolver = activity.contentResolver

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Revivo")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val imageUri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    resolver.openOutputStream(imageUri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                    true
                } else {
                    false
                }
            } else {
                val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Revivo")
                if (!picturesDir.exists()) {
                    picturesDir.mkdirs()
                }
                val imageFile = File(picturesDir, fileName)
                FileOutputStream(imageFile).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                }
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JavascriptInterface
    fun shareImage(base64: String, fileName: String, target: String): Boolean {
        return try {
            val decodedBytes = Base64.decode(base64, Base64.DEFAULT)
            val cachePath = File(activity.cacheDir, "shared")
            cachePath.mkdirs()
            val file = File(cachePath, fileName)

            FileOutputStream(file).use { outputStream ->
                outputStream.write(decodedBytes)
            }

            val fileUri: Uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)

            val shareIntent: Intent
            val pkg = when (target) {
                "instagram" -> "com.instagram.android"
                "whatsapp" -> "com.whatsapp"
                "facebook" -> "com.facebook.katana"
                "snapchat" -> "com.snapchat.android"
                else -> null
            }

            if (target == "instagram") {
                val storyIntent = Intent("com.instagram.share.ADD_TO_STORY").apply {
                    setDataAndType(fileUri, "image/png")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setPackage("com.instagram.android")
                }
                activity.grantUriPermission("com.instagram.android", fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

                shareIntent = if (activity.packageManager.resolveActivity(storyIntent, 0) != null) {
                    storyIntent
                } else {
                    Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, fileUri)
                        clipData = ClipData.newRawUri("", fileUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setPackage("com.instagram.android")
                    }
                }
            } else {
                shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    clipData = ClipData.newRawUri("", fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (pkg != null) {
                        setPackage(pkg)
                    }
                }
            }

            activity.runOnUiThread {
                try {
                    if (target == "generic" || shareIntent.`package` == null) {
                        activity.startActivity(Intent.createChooser(shareIntent, "Condividi immagine"))
                    } else {
                        if (activity.packageManager.resolveActivity(shareIntent, 0) != null) {
                            activity.startActivity(shareIntent)
                        } else {
                            val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, fileUri)
                                clipData = ClipData.newRawUri("", fileUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            activity.startActivity(Intent.createChooser(fallbackIntent, "Condividi immagine"))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}