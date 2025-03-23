package com.pikkme.scanner.rest

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
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

    override fun onReadCompleted(
        request: UrlRequest?,
        info: org.chromium.net.UrlResponseInfo?,
        byteBuffer: ByteBuffer?
    ) {
        Log.i("BarcodeScanner", "onReadCompleted")
        byteBuffer?.flip()
        val responseData = StandardCharsets.UTF_8.decode(byteBuffer).toString()
        Log.i("BarcodeScanner", "responseData  "+ responseData)
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
        Log.e("BarcodeScanner", "Error in processing request "+ error!!.message.toString())
//        if (error.message!!.contains("ERR_CONNECTION_TIMED_OUT")) {
//            Log.e("Cronet", "Connection timed out!")
//            (context as Activity).runOnUiThread { showErrorDialog("Request timed out. Please check your internet connection and try again.") }
//        } else {
//            (context as Activity).runOnUiThread { showErrorDialog("An error occurred: " + error.message) }
//        }
    }

    private fun showSuccessDialog() {
        val listView: ListView = (context as Activity).findViewById(R.id.listView)
        val adapter : ArrayAdapter<PikkmeItem> = listView.adapter as ArrayAdapter<PikkmeItem>
        adapter.clear()
        adapter.notifyDataSetChanged()
        AlertDialog.Builder(context)
            .setTitle("Success")
            .setMessage("The request was successful!")
            .setPositiveButton("OK") { dialog, which -> dialog.dismiss() }
            .show()
    }

    private fun showErrorDialog(msg: String) {
        AlertDialog.Builder(context)
            .setTitle("Error")
            .setMessage(msg)
            .setPositiveButton("OK") { dialog, which -> dialog.dismiss() }
            .show()
    }
}