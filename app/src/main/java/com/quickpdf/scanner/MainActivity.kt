package com.quickpdf.scanner

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var layoutHome: LinearLayout
    private lateinit var layoutHistory: LinearLayout
    private lateinit var listSavedDocs: ListView
    private lateinit var tvHistoryHint: TextView
    private lateinit var bottomNav: BottomNavigationView
    private var savedFilesList: List<File> = emptyList()

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pdfUri = scanResult?.pdf?.uri
            val imageUris = scanResult?.pages?.map { it.imageUri } ?: emptyList()

            if (pdfUri != null || imageUris.isNotEmpty()) {
                askOutputFormatDialog(pdfUri, imageUris)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        MobileAds.initialize(this) {}
        findViewById<AdView>(R.id.adView).loadAd(AdRequest.Builder().build())

        layoutHome = findViewById(R.id.layoutHome)
        layoutHistory = findViewById(R.id.layoutHistory)
        listSavedDocs = findViewById(R.id.listSavedDocs)
        tvHistoryHint = findViewById(R.id.tvHistoryHint)
        bottomNav = findViewById(R.id.bottomNav)

        findViewById<Button>(R.id.btnScanMain).setOnClickListener {
            startDocumentScan()
        }

        // Bottom Navigation: Home (1) and History (2)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    layoutHome.visibility = View.VISIBLE
                    layoutHistory.visibility = View.GONE
                    true
                }
                R.id.nav_history -> {
                    layoutHome.visibility = View.GONE
                    layoutHistory.visibility = View.VISIBLE
                    loadHistoryFiles()
                    true
                }
                else -> false
            }
        }

        listSavedDocs.setOnItemClickListener { _, _, position, _ ->
            showFileOptionsDialog(savedFilesList[position])
        }
    }

    private fun startDocumentScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(30)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        GmsDocumentScanning.getClient(options).getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Scanner error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Pop-up right after scanning to choose PDF, JPG, or PNG
    private fun askOutputFormatDialog(pdfUri: Uri?, imageUris: List<Uri>) {
        val formats = arrayOf(
            "📄 Save as PDF (Document)",
            "🖼️ Save as JPG (Photo Image)",
            "🎨 Save as PNG (High Quality Image)"
        )
        AlertDialog.Builder(this)
            .setTitle("Choose File Format")
            .setCancelable(false)
            .setItems(formats) { _, choice ->
                when (choice) {
                    0 -> if (pdfUri != null) saveStreamFile(pdfUri, "pdf")
                    1 -> saveImages(imageUris, "jpg")
                    2 -> saveImages(imageUris, "png")
                }
            }
            .show()
    }

    private fun saveStreamFile(uri: Uri, ext: String) {
        try {
            val folder = File(filesDir, "ScannedPDFs")
            if (!folder.exists()) folder.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val dest = File(folder, "Scan_$stamp.$ext")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            Toast.makeText(this, "Saved ${dest.name}!", Toast.LENGTH_SHORT).show()
            bottomNav.selectedItemId = R.id.nav_history
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImages(uris: List<Uri>, ext: String) {
        try {
            val folder = File(filesDir, "ScannedPDFs")
            if (!folder.exists()) folder.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

            uris.forEachIndexed { index, uri ->
                val suffix = if (uris.size > 1) "_${index + 1}" else ""
                val dest = File(folder, "Scan_$stamp$suffix.$ext")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
            }
            Toast.makeText(this, "Saved ${uris.size} $ext file(s)!", Toast.LENGTH_SHORT).show()
            bottomNav.selectedItemId = R.id.nav_history
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadHistoryFiles() {
        val folder = File(filesDir, "ScannedPDFs")
        savedFilesList = folder.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

        if (savedFilesList.isEmpty()) {
            tvHistoryHint.text = "No files yet. Tap Home and press SCAN!"
            listSavedDocs.adapter = null
            return
        }

        tvHistoryHint.text = "Tap any PDF, JPG, or PNG below to Share or Open:"
        val labels = savedFilesList.map { file ->
            val kb = (file.length() / 1024).coerceAtLeast(1)
            val icon = when (file.extension.lowercase()) {
                "pdf" -> "📄"
                "jpg", "jpeg" -> "🖼️"
                "png" -> "🎨"
                else -> "📁"
            }
            "$icon ${file.name}  (${kb} KB)"
        }
        listSavedDocs.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
    }

    private fun showFileOptionsDialog(file: File) {
        val options = arrayOf("📤 Share (WhatsApp / Email)", "👁️ Open File", "🗑️ Delete")
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> shareFile(file)
                    1 -> openFile(file)
                    2 -> {
                        file.delete()
                        loadHistoryFiles()
                        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun getMimeType(file: File): String = when (file.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        else -> "*/*"
    }

    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = getMimeType(file)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share File via"))
    }

    private fun openFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, getMimeType(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No viewer app found", Toast.LENGTH_SHORT).show()
        }
    }
}
