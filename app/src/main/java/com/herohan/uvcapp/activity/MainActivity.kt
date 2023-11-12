package com.herohan.uvcapp.activity

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
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
import com.herohan.uvcapp.utils.SaveHelper
import com.hjq.permissions.XXPermissions
import com.serenegiant.opengl.renderer.MirrorMode
import com.serenegiant.usb.Size
import com.serenegiant.usb.USBMonitor
import com.serenegiant.utils.UriHelper
import java.io.File
import java.text.DecimalFormat
import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity() {
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
    private var mRecordStartTime: Long = 0
    private var mRecordTimer: Timer? = null
    private var mDecimalFormat: DecimalFormat? = null
    private var mIsRecording = false
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

    override fun onStop() {
        super.onStop()
        if (mIsRecording) {
            toggleVideoRecord(false)
        }
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
        if (id == R.id.action_control) {
            showCameraControlsDialog()
        } else if (id == R.id.action_device) {
            showDeviceListDialog()
        } else if (id == R.id.action_safely_eject) {
            safelyEject()
        } else if (id == R.id.action_settings) {
        } else if (id == R.id.action_video_format) {
            showVideoFormatDialog()
        } else if (id == R.id.action_rotate_90_CW) {
            rotateBy(90)
        } else if (id == R.id.action_rotate_90_CCW) {
            rotateBy(-90)
        } else if (id == R.id.action_flip_horizontally) {
            flipHorizontally()
        } else if (id == R.id.action_flip_vertically) {
            flipVertically()
        }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
//        if (mIsCameraConnected) {
//            menu.findItem(R.id.action_control).setVisible(true);
//            menu.findItem(R.id.action_safely_eject).setVisible(true);
//            menu.findItem(R.id.action_video_format).setVisible(true);
//            menu.findItem(R.id.action_rotate_90_CW).setVisible(true);
//            menu.findItem(R.id.action_rotate_90_CCW).setVisible(true);
//            menu.findItem(R.id.action_flip_horizontally).setVisible(true);
//            menu.findItem(R.id.action_flip_vertically).setVisible(true);
//        } else {
//            menu.findItem(R.id.action_control).setVisible(false);
//            menu.findItem(R.id.action_safely_eject).setVisible(false);
//            menu.findItem(R.id.action_video_format).setVisible(false);
//            menu.findItem(R.id.action_rotate_90_CW).setVisible(false);
//            menu.findItem(R.id.action_rotate_90_CCW).setVisible(false);
//            menu.findItem(R.id.action_flip_horizontally).setVisible(false);
//            menu.findItem(R.id.action_flip_vertically).setVisible(false);
//        }
        return super.onPrepareOptionsMenu(menu)
    }

    private fun setListeners() {
        mBinding!!.fabPicture.setOnClickListener { v: View? ->
            XXPermissions.with(this).permission(
                Manifest.permission.MANAGE_EXTERNAL_STORAGE
            ).request { permissions: List<String?>?, all: Boolean -> takePicture() }
        }
        mBinding!!.fabVideo.setOnClickListener { v: View? ->
            XXPermissions.with(this).permission(
                Manifest.permission.MANAGE_EXTERNAL_STORAGE
            ).permission(Manifest.permission.RECORD_AUDIO)
                .request { permissions: List<String?>?, all: Boolean -> toggleVideoRecord(!mIsRecording) }
        }
        mBinding?.fabCktx?.setOnClickListener { v: View? ->
            
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
            mCameraHelper!!.supportedFormatList,
            mCameraHelper!!.previewSize
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
            setCustomVideoCaptureConfig()
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
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                if (mCameraHelper != null) {
                    mCameraHelper!!.addSurface(surface, false)
                }
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
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
        override fun onAttach(device: UsbDevice) {
            if (DEBUG) {
                Log.v(TAG, "onAttach:device=" + device.deviceName)
            }
            attachNewDevice(device)
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
                    this@MainActivity,
                    "onButton(button=$button; state=$state)",
                    Toast.LENGTH_SHORT
                ).show()
            }
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
        }

        override fun onCameraClose(device: UsbDevice) {
            if (DEBUG) {
                Log.v(TAG, "onCameraClose:device=" + device.deviceName)
            }
            if (mIsRecording) {
                toggleVideoRecord(false)
            }
            if (mCameraHelper != null && mBinding!!.viewMainPreview.surfaceTexture != null) {
                mCameraHelper!!.removeSurface(mBinding!!.viewMainPreview.surfaceTexture)
            }
            mIsCameraConnected = false
            updateUIControls()
            closeAllDialogFragment()
        }

        override fun onDeviceClose(device: UsbDevice) {
            if (DEBUG) {
                Log.v(TAG, "onDeviceClose:device=" + device.deviceName)
            }
        }

        override fun onDetach(device: UsbDevice) {
            if (DEBUG) {
                Log.v(TAG, "onDetach:device=" + device.deviceName)
            }
            if (device == mUsbDevice) {
                mUsbDevice = null
            }
        }

        override fun onCancel(device: UsbDevice) {
            if (DEBUG) {
                Log.v(TAG, "onCancel:device=" + device.deviceName)
            }
            if (device == mUsbDevice) {
                mUsbDevice = null
            }
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
                //                mBinding.fabVideo.setVisibility(View.VISIBLE);

                // Update record button
                var colorId = R.color.WHITE
                if (mIsRecording) {
                    colorId = R.color.RED
                }
                val colorStateList = ColorStateList.valueOf(resources.getColor(colorId))
                mBinding!!.fabVideo.supportImageTintList = colorStateList
            } else {
                mBinding!!.viewMainPreview.visibility = View.GONE
                mBinding!!.tvConnectUSBCameraTip.visibility = View.VISIBLE
                mBinding!!.fabPicture.visibility = View.GONE
                mBinding!!.fabVideo.visibility = View.GONE
                mBinding!!.tvVideoRecordTime.visibility = View.GONE
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

    fun takePicture() {
        if (mIsRecording) {
            return
        }
        try {
            val file = File(SaveHelper.getSavePhotoPath())
            val options = ImageCapture.OutputFileOptions.Builder(file).build()
            mCameraHelper!!.takePicture(options, object : OnImageCaptureCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Toast.makeText(
                        this@MainActivity,
                        "save \"" + UriHelper.getPath(
                            this@MainActivity,
                            outputFileResults.savedUri
                        ) + "\"",
                        Toast.LENGTH_SHORT
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

    fun toggleVideoRecord(isRecording: Boolean) {
        try {
            if (isRecording) {
                if (mIsCameraConnected && mCameraHelper != null && !mCameraHelper!!.isRecording) {
                    startRecord()
                }
            } else {
                if (mIsCameraConnected && mCameraHelper != null && mCameraHelper!!.isRecording) {
                    stopRecord()
                }
                stopRecordTimer()
            }
        } catch (e: Exception) {
            Log.e(TAG, e.localizedMessage, e)
            stopRecordTimer()
        }
        mIsRecording = isRecording
        updateUIControls()
    }

    private fun setCustomVideoCaptureConfig() {
        mCameraHelper!!.videoCaptureConfig =
            mCameraHelper!!.videoCaptureConfig //                        .setAudioCaptureEnable(false)
                .setBitRate((1024 * 1024 * 25 * 0.25).toInt()).setVideoFrameRate(25)
                .setIFrameInterval(1)
    }

    private fun startRecord() {
        val file = File(SaveHelper.getSaveVideoPath())
        val options = VideoCapture.OutputFileOptions.Builder(file).build()
        mCameraHelper!!.startRecording(options, object : OnVideoCaptureCallback {
            override fun onStart() {
                startRecordTimer()
            }

            override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
                toggleVideoRecord(false)
                Toast.makeText(
                    this@MainActivity,
                    "save \"" + UriHelper.getPath(
                        this@MainActivity,
                        outputFileResults.savedUri
                    ) + "\"",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                toggleVideoRecord(false)
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun stopRecord() {
        mCameraHelper!!.stopRecording()
    }

    private fun startRecordTimer() {
        runOnUiThread { mBinding!!.tvVideoRecordTime.visibility = View.VISIBLE }

        // Set “00:00:00” to record time TextView
        setVideoRecordTimeText(formatTime(0))

        // Start Record Timer
        mRecordStartTime = SystemClock.elapsedRealtime()
        mRecordTimer = Timer()
        //The timer is refreshed every quarter second
        mRecordTimer!!.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val recordTime = (SystemClock.elapsedRealtime() - mRecordStartTime) / 1000
                if (recordTime > 0) {
                    setVideoRecordTimeText(formatTime(recordTime))
                }
            }
        }, QUARTER_SECOND.toLong(), QUARTER_SECOND.toLong())
    }

    private fun stopRecordTimer() {
        runOnUiThread { mBinding!!.tvVideoRecordTime.visibility = View.GONE }

        // Stop Record Timer
        mRecordStartTime = 0
        if (mRecordTimer != null) {
            mRecordTimer!!.cancel()
            mRecordTimer = null
        }
        // Set “00:00:00” to record time TextView
        setVideoRecordTimeText(formatTime(0))
    }

    private fun setVideoRecordTimeText(timeText: String) {
        runOnUiThread { mBinding!!.tvVideoRecordTime.text = timeText }
    }

    /**
     * 将秒转化为 HH:mm:ss 的格式
     *
     * @param time 秒
     * @return
     */
    private fun formatTime(time: Long): String {
        if (mDecimalFormat == null) {
            mDecimalFormat = DecimalFormat("00")
        }
        val hh = mDecimalFormat!!.format(time / 3600)
        val mm = mDecimalFormat!!.format(time % 3600 / 60)
        val ss = mDecimalFormat!!.format(time % 60)
        return "$hh:$mm:$ss"
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
}