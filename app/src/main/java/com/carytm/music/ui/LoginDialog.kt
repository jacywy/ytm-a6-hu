package com.carytm.music.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.carytm.music.R
import com.carytm.music.auth.GoogleDeviceAuthManager
import com.carytm.music.model.DeviceCodeResponse
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginDialog(
    context: Context,
    private val onLoginSuccess: () -> Unit
) : Dialog(context) {

    private val authManager = GoogleDeviceAuthManager(context)
    private val scope = CoroutineScope(Dispatchers.Main)

    private lateinit var ivQrCode: ImageView
    private lateinit var tvDeviceCode: TextView
    private lateinit var tvAuthStatus: TextView
    private lateinit var btnClose: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_login_device_code)

        ivQrCode = findViewById(R.id.iv_qr_code)
        tvDeviceCode = findViewById(R.id.tv_device_code)
        tvAuthStatus = findViewById(R.id.tv_auth_status)
        btnClose = findViewById(R.id.btn_dialog_close)

        btnClose.setOnClickListener {
            authManager.cancelPolling()
            dismiss()
        }

        loadDeviceCode()
    }

    private fun loadDeviceCode() {
        tvAuthStatus.text = "正在请求授权码..."
        scope.launch {
            val response = authManager.requestDeviceCode()
            if (response != null) {
                displayCodeAndQr(response)
                startPolling(response)
            } else {
                tvAuthStatus.text = "网络错误，无法连接 Google 授权端点"
            }
        }
    }

    private suspend fun displayCodeAndQr(response: DeviceCodeResponse) {
        tvDeviceCode.text = response.userCode
        tvAuthStatus.text = context.getString(R.string.login_waiting)

        // Generate QR code pointing to https://www.google.com/device
        val bitmap = withContext(Dispatchers.Default) {
            generateQrBitmap(response.verificationUrl, 400, 400)
        }
        ivQrCode.setImageBitmap(bitmap)
    }

    private fun startPolling(response: DeviceCodeResponse) {
        authManager.startPollingToken(
            deviceCode = response.deviceCode,
            intervalSec = response.interval,
            onSuccess = {
                tvAuthStatus.text = context.getString(R.string.login_success)
                tvAuthStatus.setTextColor(Color.parseColor("#4CAF50"))
                scope.launch {
                    kotlinx.coroutines.delay(1200L)
                    onLoginSuccess()
                    dismiss()
                }
            },
            onError = { err ->
                tvAuthStatus.text = "授权失败: $err"
            }
        )
    }

    private fun generateQrBitmap(content: String, width: Int, height: Int): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        authManager.cancelPolling()
    }
}
