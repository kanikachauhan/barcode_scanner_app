package com.pikkme.scanner

import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.gson.Gson
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.pikkme.scanner.data.PikkmeItem
import com.pikkme.scanner.rest.PikkmeRequestCallback
import org.chromium.net.CronetEngine
import org.chromium.net.UploadDataProviders
import org.chromium.net.UrlRequest
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class ScanAndUpdateActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var barcodeScanner: BarcodeScanner
    private lateinit var listOfPikkmeItems: MutableList<PikkmeItem>
    private lateinit var pikkmeItemAdapter: PikkmeItemAdapter
    private lateinit var listView: ListView
    private lateinit var successFlashView: View
    private lateinit var errorFlashView: View
    private lateinit var cronetBuilder : CronetEngine.Builder
    private lateinit var requestBuilder : UrlRequest.Builder
    private lateinit var cronetEngine : CronetEngine
//    private lateinit var editText: EditText
    private lateinit var operation : String
    private var isScanning = true
    private var successMediaPlayer: MediaPlayer? = null
    private var errorMediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_scan_and_update)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
//        editText = findViewById(R.id.textView)
        previewView = findViewById(R.id.previewView)
        listView = findViewById(R.id.listView)
        successFlashView = findViewById(R.id.successFlashView)
        errorFlashView = findViewById(R.id.errorFlashView)
        successMediaPlayer = MediaPlayer.create(this, R.raw.success_scan)
        errorMediaPlayer = MediaPlayer.create(this, R.raw.error_scan)
        cameraExecutor = Executors.newSingleThreadExecutor()
        listOfPikkmeItems = mutableListOf()
        pikkmeItemAdapter = PikkmeItemAdapter(this, R.id.listView, listOfPikkmeItems)
        listView.adapter = pikkmeItemAdapter
        operation = intent.getStringExtra("operation")!!
        val options =
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()
        barcodeScanner = BarcodeScanning.getClient(options)
        requestCameraPermission()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmation()
            }
        })
        cronetBuilder = CronetEngine.Builder(this)
        cronetEngine = cronetBuilder.enableHttp2(false).enableQuic(false).build()
    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission required!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        successMediaPlayer?.release()
        errorMediaPlayer?.release()
    }

    fun processData(v:View) {
        if (listOfPikkmeItems.isEmpty()) {
            showErrorDialog("Please scan some values to process !!!")
            return
        }
        val executor: Executor = Executors.newFixedThreadPool(2)
        when (operation) {
            "inbound" -> {
                requestBuilder = cronetEngine.newUrlRequestBuilder(
                    "",
                    PikkmeRequestCallback(this),
                    executor
                )
            }
            "outbound" -> {
                requestBuilder = cronetEngine.newUrlRequestBuilder(
                    "",
                    PikkmeRequestCallback(this),
                    executor
                )
            }
        }
        requestBuilder.setHttpMethod("POST")
        requestBuilder.addHeader("Content-Type", "application/json")
        // to be passed here
        requestBuilder.addHeader("username", "")
        requestBuilder.addHeader("token", "")
        val jsonString = Gson().toJson(listOfPikkmeItems)
        Log.i("BarcodeScanner", jsonString)
        requestBuilder.setUploadDataProvider(UploadDataProviders.create(jsonString.toByteArray()),executor)
        val request: UrlRequest = requestBuilder
            .disableCache()
            .allowDirectExecutor()
            .build()
        request.start()
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.CAMERA), 101
            )
        }
    }

    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                val cameraSelector =
                    CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build()
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                imageAnalysis.setAnalyzer(
                    Executors.newSingleThreadExecutor()
                ) { image: ImageProxy -> scanBarcode(image) }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
                preview.surfaceProvider = previewView.getSurfaceProvider()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun scanBarcode(imageProxy: ImageProxy) {
        if (!isScanning) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image ?: return
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        barcodeScanner.process(image).addOnSuccessListener { barcodes ->
            for (barcode in barcodes) {
                val rawValue = barcode.rawValue
                Log.d("BarcodeScanner", "Scanned: $rawValue")
                runOnUiThread {
                    processScannedResult(rawValue)
                }
                isScanning = false
                Handler(Looper.getMainLooper()).postDelayed({
                    isScanning = true
                }, 2000)
                break
            }

        }.addOnFailureListener { e ->
            playAndFlashError()
            Log.e("BarcodeScanner", "Error: ${e.message}")
        }.addOnCompleteListener {
            imageProxy.close()
        }
    }

    fun processScannedResult(text: String?) {
        try {
            val itemVals = text!!.split(":")
            val sku = itemVals!!.get(0)
            val quantity = itemVals.get(1)
            when {
                listOfPikkmeItems.isEmpty() -> {
                    val pikkmeItem = PikkmeItem(sku, quantity.toInt())
                    listOfPikkmeItems.add(pikkmeItem)
                }

                else -> {
                    val ind = listOfPikkmeItems.indexOfFirst { it.sku == sku }
                    if (ind != -1) {
                        val item = listOfPikkmeItems.get(ind)
                        item.Quantity += quantity.toInt()
                        listOfPikkmeItems.set(ind, PikkmeItem(sku, item.Quantity))
                    } else {
                        val pikkmeItem = PikkmeItem(sku, quantity.toInt())
                        listOfPikkmeItems.add(pikkmeItem)
                    }
                }
            }
            listView.post {
                pikkmeItemAdapter.notifyDataSetChanged()
            }
            playAndFlashSuccess()
        }catch ( e: Exception) {
            e.message?.let { Log.e("BarcodeScanner", it) }
            playAndFlashError()
        }
    }

    private fun playAndFlashSuccess() {
        successMediaPlayer?.start()
        successFlashView.visibility = View.VISIBLE
        previewView.visibility = View.GONE
        successFlashView.animate()
            .alpha(1f)
            .setDuration(300)
            .withEndAction {
                successFlashView.animate()
                    .alpha(0f)
                    .setDuration(400)
                    .withEndAction {
                        successFlashView.visibility = View.GONE
                        previewView.visibility = View.VISIBLE
                    }
            }

    }

    private fun playAndFlashError() {
        errorMediaPlayer?.start()
        errorFlashView.visibility = View.VISIBLE
        previewView.visibility = View.GONE
        errorFlashView.animate()
            .alpha(1f)
            .setDuration(300)
            .withEndAction {
                errorFlashView.animate()
                    .alpha(0f)
                    .setDuration(400)
                    .withEndAction {
                        errorFlashView.visibility = View.GONE
                        previewView.visibility = View.VISIBLE
                    }
            }
    }

    private fun showExitConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Exit Confirmation")
            .setMessage("Are you sure you want to leave this screen?")
            .setPositiveButton("Yes") { _, _ ->
                finish()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showErrorDialog(msg: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(msg)
            .setPositiveButton("OK") { dialog, which -> dialog.dismiss() }
            .show()
    }

    class PikkmeItemAdapter(private val context: Context, resource: Int, private var items: MutableList<PikkmeItem>) :
        ArrayAdapter<PikkmeItem>(context, resource, items) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val pikkmeItem: PikkmeItem? = getItem(position)
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.list_pikkme_item, parent, false)
            val sku: TextView? = view.findViewById(R.id.sku)
            sku?.text = pikkmeItem?.sku
            val quantity: TextView? = view.findViewById(R.id.quantity)
            quantity?.text = pikkmeItem?.Quantity.toString()
            return view
        }
    }
}