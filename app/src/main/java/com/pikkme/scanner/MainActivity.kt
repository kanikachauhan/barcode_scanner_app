package com.pikkme.scanner

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.pikkme.scanner.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity () {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

    }

    fun scanAndPerformOutBound(view: View) {
        val intent = Intent(applicationContext, ScanAndUpdateActivity::class.java)
        intent.putExtra("operation","outbound")
        startActivity(intent)
    }

    fun scanAndPerformInbound(view: View) {
        val intent = Intent(applicationContext, ScanAndUpdateActivity::class.java)
        intent.putExtra("operation","inbound")
        startActivity(intent)
    }

}