package com.quickpdf.scanner

import android.app.Activity
import android.content.Intent
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
import com.google.android.material.tabs.TabLayout
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var layoutTabScan: LinearLayout
    private lateinit var layoutTabSaved: LinearLayout
    private lateinit var listSavedPdfs: ListView
    private lateinit var tvSavedHint: TextView
    private var savedFilesList: List<File> = emptyList()

    // Handles the finished PDF returned by Google's Document Scanner
    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pdf?.let { pdf ->
                savePdfToAppFolder(pdf.uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Google AdMob Banner
        MobileAds.initialize(this) {}
        val adView = findViewById<AdView>(R.id.adView)
        adView.loadAd(AdRequest.Builder().build())

        layoutTabScan = findViewById(R.id.layoutTabScan)
        layoutTabSaved = findViewById(R.id.layoutTabSaved)
        listSavedPdfs = findViewById(R.id.listSavedPdfs)
        tvSavedHint = findViewById(R.id.tvSavedHint)

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val btnStartScan = findViewById<Button>(R.id.btnStartScan)

        // Switch between Tab 1 (Scan) and Tab 2 (Saved Docs)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    layoutTabScan.visibility = View.VISIBLE
                    layoutTabSaved.visibility = View.GONE
                } else {
                    layoutTabScan.visibility = View.GONE
                    layoutTabSaved.visibility = View.VISIBLE
                    loadSavedPdfs()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Start Google ML Kit Document Scanner
        btnStartScan.setOnClickListener {
            startDocumentScan()
        }

        // Handle tapping on a saved PDF in Tab 2
        listSavedPdfs.setOnItemClickListener { _, _, position, _ ->
            val selectedFile = savedFilesList[position]
            showPdfOptionsDialog(selectedFile)
        }
    }

    private fun startDocumentScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(30)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        val scanner = GmsDocumentScanning.getClient(options)
        scanner.getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Scanner error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun savePdfToAppFolder(pdfUri: android.net.Uri) {
        try {
            val pdfFolder = File(filesDir, "ScannedPDFs")
            if (!pdfFolder.exists()) pdfFolder.mkdirs()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val destFile = File(pdfFolder, "Scan_$timeStamp.pdf")

            contentResolver.openInputStream(pdfUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            Toast.makeText(this, "Saved cleanly! Check Tab 2.", Toast.LENGTH_LONG).show()
            findViewById<TabLayout>(R.id.tabLayout).getTabAt(1)?.select()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSavedPdfs() {
        val pdfFolder = File(filesDir, "ScannedPDFs")
        savedFilesList = pdfFolder.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

        if (savedFilesList.isEmpty()) {
            tvSavedHint.text = "No saved PDFs yet. Go to Tab 1 and scan your first page!"
            listSavedPdfs.adapter = null
            return
        }

        tvSavedHint.text = "Tap any PDF below to Share on WhatsApp, Open, or Delete:"
        val displayNames = savedFilesList.map { file ->
            val sizeKb = file.length() / 1024
            "📄 ${file.name}  (${sizeKb} KB)"
        }

        listSavedPdfs.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayNames)
    }

    private fun showPdfOptionsDialog(file: File) {
        val options = arrayOf("📤 Share (WhatsApp / Email)", "👁️ Open PDF", "🗑️ Delete")
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> sharePdf(file)
                    1 -> openPdf(file)
                    2 -> {
                        file.delete()
                        loadSavedPdfs()
                        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun sharePdf(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share PDF via"))
    }

    private fun openPdf(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(viewIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "No PDF viewer installed", Toast.LENGTH_SHORT).show()
        }
    }
}
