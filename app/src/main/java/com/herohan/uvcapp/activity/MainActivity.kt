package com.herohan.uvcapp.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.google.gson.Gson
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.herohan.uvcapp.ImageCapture
import com.herohan.uvcapp.ImageCapture.OnImageCaptureCallback
import com.herohan.uvcapp.R
import com.herohan.uvcapp.VideoCapture
import com.herohan.uvcapp.VideoCapture.OnVideoCaptureCallback
import com.herohan.uvcapp.databinding.ActivityMainBinding
import com.herohan.uvcapp.fragment.CameraControlsDialogFragment
import com.herohan.uvcapp.fragment.DeviceListDialogFragment
import com.herohan.uvcapp.fragment.VideoFormatDialogFragment
import com.herohan.uvcapp.utils.HexUtils
import com.herohan.uvcapp.utils.SaveHelper
import com.hjq.permissions.XXPermissions
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.serenegiant.opengl.renderer.MirrorMode
import com.serenegiant.usb.Size
import com.serenegiant.usb.USBMonitor
import com.serenegiant.utils.UriHelper
import java.io.File
import java.text.DecimalFormat
import java.util.Timer
import java.util.TimerTask


class MainActivity : AppCompatActivity(), SerialInputOutputManager.Listener {
    private var mBinding: ActivityMainBinding? = null

    /**
     * Camera preview width
     */
    private var mPreviewWidth = DEFAULT_WIDTH

    /**
     * Camera preview height
     */
    private var mPreviewHeight = DEFAULT_HEIGHT
    private var mPreviewRotation = 0
    private var mCameraHelper: ICameraHelper? = null
    private var mUsbDevice: UsbDevice? = null
    private val mStateCallback: ICameraHelper.StateCallback = MyCameraHelperCallback()
    private var mIsCameraConnected = false
    private var mControlsDialog: CameraControlsDialogFragment? = null
    private var mDeviceListDialog: DeviceListDialogFragment? = null
    private var mFormatDialog: VideoFormatDialogFragment? = null
    private val ckTag = "==UsbHelper=="
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding!!.root)
        setSupportActionBar(mBinding!!.toolbar)
        checkCameraHelper()
        setListeners()
        tryOpen()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            if (!mIsCameraConnected) {
                mUsbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                selectDevice(mUsbDevice)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        initPreviewView()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearCameraHelper()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId
        if (id == R.id.action_device) {
            showDeviceListDialog()
        }
        return true
    }

    private fun setListeners() {
        mBinding!!.fabPicture.setOnClickListener { v: View? ->
            XXPermissions.with(this).permission(
                Manifest.permission.MANAGE_EXTERNAL_STORAGE
            ).request { permissions: List<String?>?, all: Boolean -> takePicture() }
        }
        mBinding?.fabFd?.setOnClickListener { v: View? ->
            zoomIn()
        }
        mBinding?.fabSx?.setOnClickListener { v: View? ->
            zoomOut()
        }
    }

    //串口方法
    private var port: UsbSerialPort? = null

    @SuppressLint("SetTextI18n")
    private fun tryOpen() {
        try {
            // Find all available drivers from attached devices.
            val manager = getSystemService(USB_SERVICE) as UsbManager
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
            LogUtils.i(ckTag, "availableDrivers", availableDrivers.size)
            if (availableDrivers.isEmpty()) {
                //更新状态
                mBinding?.tvCk?.text = "串口:未发现"
                return
            }
            // Open a connection to the first available driver.
            val driver = availableDrivers[0]
            LogUtils.i(ckTag, "driver", driver.device.deviceName)
            if (manager.hasPermission(driver.device)) {
                val connection = manager.openDevice(driver.device)
                if (connection == null) {
                    //更新状态
                    mBinding?.tvCk?.text = "串口:连接失败"
                    return
                }
                port = driver.ports[0] // Most devices have just one port (port 0)
                port?.open(connection)
                port?.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                LogUtils.i(ckTag, "open", port?.isOpen)
                //监听输出数据
                val usbIoManager = SerialInputOutputManager(port, this)
                usbIoManager.start()
                //更新状态
                mBinding?.tvCk?.text = "串口:" + port?.isOpen
            } else {
                mBinding?.tvCk?.text = "串口:暂无权限"
            }
        } catch (e: Exception) {
            ToastUtils.showLong(e.message)
            //更新状态
            mBinding?.tvCk?.text = "串口:" + e.message
        }
    }

    private fun showCameraControlsDialog() {
        if (mControlsDialog == null) {
            mControlsDialog = CameraControlsDialogFragment(mCameraHelper)
        }
        // When DialogFragment is not showing
        if (!mControlsDialog!!.isAdded) {
            mControlsDialog!!.show(supportFragmentManager, "camera_controls")
        }
    }

    private fun showDeviceListDialog() {
        if (mDeviceListDialog != null && mDeviceListDialog!!.isAdded) {
            return
        }
        mDeviceListDialog =
            DeviceListDialogFragment(mCameraHelper, if (mIsCameraConnected) mUsbDevice else null)
        mDeviceListDialog!!.setOnDeviceItemSelectListener { usbDevice: UsbDevice? ->
            if (mIsCameraConnected) {
                mCameraHelper!!.closeCamera()
            }
            mUsbDevice = usbDevice
            selectDevice(mUsbDevice)
        }
        mDeviceListDialog!!.show(supportFragmentManager, "device_list")
    }

    private fun showVideoFormatDialog() {
        if (mFormatDialog != null && mFormatDialog!!.isAdded) {
            return
        }
        mFormatDialog = VideoFormatDialogFragment(
            mCameraHelper!!.supportedFormatList, mCameraHelper!!.previewSize
        )
        mFormatDialog!!.setOnVideoFormatSelectListener { size: Size ->
            if (mIsCameraConnected && !mCameraHelper!!.isRecording) {
                mCameraHelper!!.stopPreview()
                mCameraHelper!!.previewSize = size
                mCameraHelper!!.startPreview()
                resizePreviewView(size)
                // save selected preview size
                setSavedPreviewSize(size)
            }
        }
        mFormatDialog!!.show(supportFragmentManager, "video_format")
    }

    private fun closeAllDialogFragment() {
        if (mControlsDialog != null && mControlsDialog!!.isAdded) {
            mControlsDialog!!.dismiss()
        }
        if (mDeviceListDialog != null && mDeviceListDialog!!.isAdded) {
            mDeviceListDialog!!.dismiss()
        }
        if (mFormatDialog != null && mFormatDialog!!.isAdded) {
            mFormatDialog?.dismiss()
        }
    }

    private fun safelyEject() {
        if (mCameraHelper != null) {
            mCameraHelper!!.closeCamera()
        }
    }

    private fun rotateBy(angle: Int) {
        mPreviewRotation += angle
        mPreviewRotation %= 360
        if (mPreviewRotation < 0) {
            mPreviewRotation += 360
        }
        if (mCameraHelper != null) {
            mCameraHelper!!.previewConfig =
                mCameraHelper!!.previewConfig.setRotation(mPreviewRotation)
        }
    }

    private fun flipHorizontally() {
        if (mCameraHelper != null) {
            mCameraHelper!!.previewConfig =
                mCameraHelper!!.previewConfig.setMirror(MirrorMode.MIRROR_HORIZONTAL)
        }
    }

    private fun flipVertically() {
        if (mCameraHelper != null) {
            mCameraHelper!!.previewConfig =
                mCameraHelper!!.previewConfig.setMirror(MirrorMode.MIRROR_VERTICAL)
        }
    }

    private fun checkCameraHelper() {
        if (!mIsCameraConnected) {
            clearCameraHelper()
        }
        initCameraHelper()
    }

    private fun initCameraHelper() {
        if (mCameraHelper == null) {
            mCameraHelper = CameraHelper()
            mCameraHelper?.setStateCallback(mStateCallback)
            setCustomImageCaptureConfig()
        }
    }

    private fun clearCameraHelper() {
        if (DEBUG) {
            Log.v(TAG, "clearCameraHelper:")
        }
        if (mCameraHelper != null) {
            mCameraHelper!!.release()
            mCameraHelper = null
        }
    }

    private fun initPreviewView() {
        mBinding!!.viewMainPreview.setAspectRatio(mPreviewWidth, mPreviewHeight)
        mBinding!!.viewMainPreview.surfaceTextureListener = object : SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture, width: Int, height: Int
            ) {
                if (mCameraHelper != null) {
                    mCameraHelper!!.addSurface(surface, false)
                }
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture, width: Int, height: Int
            ) {
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                if (mCameraHelper != null) {
                    mCameraHelper!!.removeSurface(surface)
                }
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    fun attachNewDevice(device: UsbDevice?) {
        if (mUsbDevice == null) {
            mUsbDevice = device
            selectDevice(device)
        }
    }

    /**
     * In Android9+, connected to the UVC CAMERA, CAMERA permission is required
     *
     * @param device
     */
    protected fun selectDevice(device: UsbDevice?) {
        if (DEBUG) {
            Log.v(TAG, "selectDevice:device=" + device!!.deviceName)
        }
        XXPermissions.with(this).permission(Manifest.permission.CAMERA)
            .request { permissions: List<String?>?, all: Boolean ->
                mIsCameraConnected = false
                updateUIControls()
                if (mCameraHelper != null) {
                    // 通过UsbDevice对象，尝试获取设备权限
                    mCameraHelper!!.selectDevice(device)
                }
            }
    }

    private inner class MyCameraHelperCallback : ICameraHelper.StateCallback {
        @SuppressLint("SetTextI18n")
        override fun onAttach(device: UsbDevice) {
            if (DEBUG) {
                Log.v(TAG, "onAttach:device=" + device.deviceName)
            }
            attachNewDevice(device)
            mBinding?.tvXj?.text = "相机:" + device.deviceName
        }

        /**
         * After obtaining USB device permissions, connect the USB camera
         */
        override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {
            if (DEBUG) {
                Log.v(TAG, "onDeviceOpen:device=" + device.deviceName)
            }
            mCameraHelper?.openCamera(savedPreviewSize)
            mCameraHelper!!.setButtonCallback { button, state ->
                Toast.makeText(
                    this@MainActivity, "onButton(button=$button; state=$state)", Toast.LENGTH_SHORT
                ).show()
            }
            mBinding?.tvXj?.text = "相机:设备打开"
        }

        override fun onCameraOpen(device: UsbDevice) {
            if (DEBUG) {
                Log.v(TAG, "onCameraOpen:device=" + device.deviceName)
            }
            mCameraHelper!!.startPreview()
            // After connecting to the camera, you can get preview size of the camera
            val size = mCameraHelper!!.previewSize
            size?.let { resizePreviewView(it) }
            if (mBinding!!.viewMainPreview.surfaceTexture != null) {
                mCameraHelper!!.addSurface(mBinding!!.viewMainPreview.surfaceTexture, false)
            }
            mIsCameraConnected = true
            updateUIControls()
            mBinding?.tvXj?.text = "相机:相机打开"
        }

        override fun onCameraClose(device: UsbDevice) {
            if (DEBUG) {
                Log.v(TAG, "onCameraClose:device=" + device.deviceName)
            }
            if (mCameraHelper != null && mBinding!!.viewMainPreview.surfaceTexture != null) {
                mCameraHelper!!.removeSurface(mBinding!!.viewMainPreview.surfaceTexture)
            }
            mIsCameraConnected = false
            updateUIControls()
            closeAllDialogFragment()
            mBinding?.tvXj?.text = "相机:相机关闭"
        }

        override fun onDeviceClose(device: UsbDevice) {
            if (DEBUG) {
                Log.v(TAG, "onDeviceClose:device=" + device.deviceName)
            }
            mBinding?.tvXj?.text = "相机:设备关闭"
        }

        override fun onDetach(device: UsbDevice) {
            if (DEBUG) {
                Log.v(TAG, "onDetach:device=" + device.deviceName)
            }
            if (device == mUsbDevice) {
                mUsbDevice = null
            }
            mBinding?.tvXj?.text = "相机:设备断开"
        }

        override fun onCancel(device: UsbDevice) {
            if (DEBUG) {
                Log.v(TAG, "onCancel:device=" + device.deviceName)
            }
            if (device == mUsbDevice) {
                mUsbDevice = null
            }
            mBinding?.tvXj?.text = "相机:设备取消"
        }
    }

    private fun resizePreviewView(size: Size) {
        // Update the preview size
        mPreviewWidth = size.width
        mPreviewHeight = size.height
        // Set the aspect ratio of TextureView to match the aspect ratio of the camera
        mBinding!!.viewMainPreview.setAspectRatio(mPreviewWidth, mPreviewHeight)
    }

    private fun updateUIControls() {
        runOnUiThread {
            if (mIsCameraConnected) {
                mBinding!!.viewMainPreview.visibility = View.VISIBLE
                mBinding!!.tvConnectUSBCameraTip.visibility = View.GONE
                mBinding!!.fabPicture.visibility = View.VISIBLE
            } else {
                mBinding!!.viewMainPreview.visibility = View.GONE
                mBinding!!.tvConnectUSBCameraTip.visibility = View.VISIBLE
                mBinding!!.fabPicture.visibility = View.GONE
            }
            invalidateOptionsMenu()
        }
    }

    private val savedPreviewSize: Size?
        private get() {
            val key = getString(R.string.saved_preview_size) + USBMonitor.getProductKey(mUsbDevice)
            val sizeStr = getPreferences(MODE_PRIVATE).getString(key, null)
            if (TextUtils.isEmpty(sizeStr)) {
                return null
            }
            val gson = Gson()
            return gson.fromJson(sizeStr, Size::class.java)
        }

    private fun setSavedPreviewSize(size: Size) {
        val key = getString(R.string.saved_preview_size) + USBMonitor.getProductKey(mUsbDevice)
        val gson = Gson()
        val json = gson.toJson(size)
        getPreferences(MODE_PRIVATE).edit().putString(key, json).apply()
    }

    private fun setCustomImageCaptureConfig() {
        mCameraHelper!!.imageCaptureConfig =
            mCameraHelper!!.imageCaptureConfig.setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        mCameraHelper!!.imageCaptureConfig =
            mCameraHelper!!.imageCaptureConfig.setJpegCompressionQuality(90)
    }

    private fun takePicture() {
        try {
            val file = File(SaveHelper.getSavePhotoPath())
            val options = ImageCapture.OutputFileOptions.Builder(file).build()
            mCameraHelper!!.takePicture(options, object : OnImageCaptureCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Toast.makeText(
                        this@MainActivity, "save \"" + UriHelper.getPath(
                            this@MainActivity, outputFileResults.savedUri
                        ) + "\"", Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onError(imageCaptureError: Int, message: String, cause: Throwable?) {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, e.localizedMessage, e)
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val DEBUG = true
        private const val QUARTER_SECOND = 250
        private const val HALF_SECOND = 500
        private const val ONE_SECOND = 1000
        private const val DEFAULT_WIDTH = 640
        private const val DEFAULT_HEIGHT = 480
    }

    /**
     * 串口监听
     */
    override fun onNewData(data: ByteArray?) {
        val hexStr = HexUtils.byteArrayToHexStr(data)
        LogUtils.i(ckTag, "data", hexStr)
        //放大
        //4B310D0A
        val fd = "4B310D0A"
        //缩小
        //4B330D0A
        val sx = "4B330D0A"
        //拍照
        //4B320D0A
        val pz = "4B320D0A"
        when (hexStr) {
            fd -> {
                LogUtils.i(ckTag, "放大")
                zoomIn()
            }

            sx -> {
                LogUtils.i(ckTag, "缩小")
                zoomOut()
            }

            pz -> {
                LogUtils.i(ckTag, "拍照")
                takePicture()
            }

            else -> {
                LogUtils.i(ckTag, "未识别")
            }
        }
    }

    // 放大的点击事件
    private fun zoomIn() {
        // 播放视频的view
        val textureView = mBinding?.viewMainPreview
        val matrix = Matrix()
        // 为了重复点击能在之前基础上放大
        textureView?.getTransform(matrix)

        // sx/sy  -1~1 代表缩小  >1或<-1 代表放大
        // 其中，负值还代表根据中心轴翻转
        // 第一、第二个参数，代表放大还是缩小，sx/sy
        // 第三、第四个参数，代表缩放的原点，下面是以中心点缩放
        // postScale（）和preScale（） 效果相同，不知道有什么区别
        matrix.postScale(
            2f,
            2f,
            (mBinding?.viewMainPreview!!.width / 2).toFloat(),
            (mBinding?.viewMainPreview!!.height / 2).toFloat()
        )
        // 设置矩阵
        textureView?.setTransform(matrix)
        // 刷新view
        textureView?.postInvalidate()
    }

    // 缩小的点击事件
    private fun zoomOut() {
        // 播放视频的view
        val textureView = mBinding?.viewMainPreview
        val matrix = Matrix()
        // 为了重复点击能在之前基础上放大
        textureView?.getTransform(matrix)
        // 0.5 代表画面缩小回之前的一半，对应上面放大2倍的操作
        matrix.postScale(
            0.5f,
            0.5f,
            (mBinding?.viewMainPreview!!.width / 2).toFloat(),
            (mBinding?.viewMainPreview!!.height / 2).toFloat()
        )
        // 设置矩阵
        textureView?.setTransform(matrix)
        // 刷新view
        textureView?.postInvalidate()
    }

    override fun onRunError(e: java.lang.Exception?) {
        LogUtils.e(ckTag, e?.message)
    }
}