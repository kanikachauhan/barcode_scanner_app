package com.pikkme.scanner.rest

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import com.pikkme.scanner.R
import com.pikkme.scanner.ScanAndUpdateActivity
import com.pikkme.scanner.data.PikkmeItem
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets




class PikkmeRequestCallback(var context : Context) : UrlRequest.Callback() {


    override fun onRedirectReceived(
        request: UrlRequest?,
        info: org.chromium.net.UrlResponseInfo?,
        newLocationUrl: String?
    ) {
        TODO("Not yet implemented")
    }

    override fun onResponseStarted(request: UrlRequest?, info: org.chromium.net.UrlResponseInfo?) {
        Log.d("BarcodeScanner", "Response started")
        request?.read(ByteBuffer.allocateDirect(1024))
    }
    val responseBuffer = StringBuilder()

    override fun onReadCompleted(
        request: UrlRequest?,
        info: org.chromium.net.UrlResponseInfo?,
        byteBuffer: ByteBuffer?
    ) {
        Log.i("BarcodeScanner", "onReadCompleted")
        byteBuffer?.flip()
        val bytes = ByteArray(byteBuffer!!.remaining())
        byteBuffer.get(bytes)
        responseBuffer.append(String(bytes)) // Append received data
        byteBuffer.clear()
        request!!.read(byteBuffer)

        if (responseBuffer.contains("One or more sku's in the file does not exists in system.")) {
            (context as ScanAndUpdateActivity).runOnUiThread {
                showErrorDialog("One or more sku's does not exists in system.")
            }
            return
        }
        Log.i("BarcodeScanner", "responseData  "+ responseBuffer)
        (context as ScanAndUpdateActivity).runOnUiThread { showSuccessDialog() }
    }

    override fun onSucceeded(request: UrlRequest?, info: org.chromium.net.UrlResponseInfo?) {
        Log.i("BarcodeScanner", "status "+ info!!.httpStatusCode.toString())
    }

    override fun onFailed(
        request: UrlRequest?,
        info: org.chromium.net.UrlResponseInfo?,
        error: CronetException?
    ) {
        Log.e("BarcodeScanner", "Request failed: ${error!!.message}", error)
        showErrorDialog("Request failed: ${error.message}")
    }

    private fun showSuccessDialog() {
        clearView()
        AlertDialog.Builder(context)
            .setTitle("Success")
            .setMessage("The request was successful!")
            .setPositiveButton("OK") { dialog, which -> dialog.dismiss() }
            .show()
    }

    private fun clearView() {
        val listView: ListView = (context as Activity).findViewById(R.id.listView)
        val adapter : ArrayAdapter<PikkmeItem> = listView.adapter as ArrayAdapter<PikkmeItem>
        adapter.clear()
        adapter.notifyDataSetChanged()
    }


    private fun showErrorDialog(msg: String) {
        clearView()
        AlertDialog.Builder(context)
            .setTitle("Error")
            .setMessage(msg)
            .setPositiveButton("OK") { dialog, which -> dialog.dismiss() }
            .show()
    }

}