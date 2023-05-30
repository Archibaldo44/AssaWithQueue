package vib.assaqueue.communication

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

private const val ACTION_USB_PERMISSION = "assa.industrialdoors.USB_PERMISSION"

class UsbHidDevice(
    private val application: Application,
    name: String,
    address: String,
) : SerialDevice(name, address) {

    var swVersion = ""

    private var messageListenerJob: Job? = null
    private var usbDetachReceiver: BroadcastReceiver? = null

    private var usbDevice: UsbDevice? = null
    private var usbInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null
    private var usbConnection: UsbDeviceConnection? = null

    override suspend fun connectAndListen(
        byteReceivedListener: (bytes: List<Byte>) -> Unit,
        connectionListener: (ConnectionStatus) -> Unit,
    ) {
        super.connectAndListen(byteReceivedListener, connectionListener)

        updateConnectionStatus(ConnectionStatus.CONNECTING)
        val usbManager = application.getSystemService(Context.USB_SERVICE) as UsbManager
        usbManager.deviceList[name]?.let { usbDevice ->
            val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                0
            }
            val permissionIntent = PendingIntent.getBroadcast(
                application,
                0,
                Intent(ACTION_USB_PERMISSION),
                flag,
            )
            val filter = IntentFilter(ACTION_USB_PERMISSION)

            application.registerReceiver(
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        onUsbPermissionRequest(this, intent)
                    }
                },
                filter,
            )
            usbManager.requestPermission(usbDevice, permissionIntent)

            usbDetachReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                        val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        device?.let { usbDevice ->
                            if (usbDevice.deviceName == name) {
                                stopMessageListenerAndReleaseUsb()
                                updateConnectionStatus(ConnectionStatus.UNPLUGGED)
                                application.unregisterReceiver(this)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun write(byteArray: ByteArray) {
        val result = usbConnection?.bulkTransfer(
            outEndpoint,
            byteArray,
            byteArray.size,
            0,
        )
        if (result != null && result >= 0) {
            Log.i("vitDebugCommunication", "out($result) >>> ${byteArray.map { it.toUByte() }}")
        } else {
            Log.e("vitDebugCommunication", "out($result) >>> Transmission FAILED for ${byteArray.toList()}")
        }
    }

    override fun disconnect(
        failure: Boolean,
    ) {
        stopMessageListenerAndReleaseUsb()
        updateConnectionStatus(if (failure) ConnectionStatus.FAILED else ConnectionStatus.DISCONNECTED)
        usbDetachReceiver?.let { application.unregisterReceiver(it) }
        usbDetachReceiver = null
    }

    override fun toString(): String {
        return "[class = ${javaClass.simpleName}, name = $name, address = $address"
    }

    /** Called from BroadcastReceiver in case of Permission request */
    private fun onUsbPermissionRequest(broadcastReceiver: BroadcastReceiver, intent: Intent) {
        synchronized(broadcastReceiver) {
            val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                device?.apply {
                    try {
                        tryToConnectToAssa950d(this)
                        startListeningToUsbHidDevice()
                        updateConnectionStatus(ConnectionStatus.CONNECTED)
                        application.registerReceiver(
                            usbDetachReceiver,
                            IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED),
                        )
                    } catch (e: Exception) {
                        updateConnectionStatus(ConnectionStatus.FAILED)
                    }
                }
            } else {
                updateConnectionStatus(ConnectionStatus.FAILED)
            }
        }
    }

    /**
     * Checks if the USB device we are connecting to is an ASSA 950d door
     * and it has one interface and two endPoints of the proper types.
     *
     * In case of success connects to the door.
     *
     * In case of any problem throws an exception.
     */
    private fun tryToConnectToAssa950d(device: UsbDevice) {
        this.usbDevice = device
        if (device.vendorId != VID || device.productId != PID) {
            stopMessageListenerAndReleaseUsb()
            throw Exception("This is not a Assa 950d door.")
        }

        if (1 != usbDevice?.interfaceCount) {
            stopMessageListenerAndReleaseUsb()
            throw Exception("Number of USB interfaces doesn't equal 1.")
        }

        usbInterface = usbDevice?.getInterface(0)
        if (UsbConstants.USB_CLASS_HID != usbInterface?.interfaceClass) {
            stopMessageListenerAndReleaseUsb()
            throw Exception("USB interface class is not HID.")
        }

        for (i in 0 until (usbInterface?.endpointCount ?: -1)) {
            when (usbInterface?.getEndpoint(i)?.direction) {
                UsbConstants.USB_DIR_IN -> inEndpoint = usbInterface?.getEndpoint(i)
                UsbConstants.USB_DIR_OUT -> outEndpoint = usbInterface?.getEndpoint(i)
            }
        }
        if (inEndpoint == null && outEndpoint == null) {
            stopMessageListenerAndReleaseUsb()
            throw Exception("Number of USB interfaces doesn't equal 2.")
        }

        val usbManager = application.getSystemService(Context.USB_SERVICE) as UsbManager
        usbConnection = usbManager.openDevice(usbDevice)
        if (usbConnection == null) {
            stopMessageListenerAndReleaseUsb()
            throw Exception("Cannot open the device.")
        }

        if (usbConnection?.claimInterface(usbInterface, true) == false) {
            stopMessageListenerAndReleaseUsb()
            throw Exception("Cannot claim the USB interface.")
        }
    }

    private fun startListeningToUsbHidDevice() {
        messageListenerJob?.cancel()
        if (usbConnection == null || inEndpoint == null) {
            throw Exception("Cannot start the listener: either usbConnection or inEndpoint is null.")
        }
        messageListenerJob = GlobalScope.launch(Dispatchers.IO) {
            val usbRequest = UsbRequest()
            while (isActive) {
                val bufferSize = inEndpoint?.maxPacketSize ?: BUFFER_SIZE
                val buffer: ByteBuffer = ByteBuffer.allocate(bufferSize)
                usbRequest.initialize(usbConnection, inEndpoint)
                val queueResult = usbRequest.queue(buffer)
                if (queueResult) {
                    if (usbConnection?.requestWait() === usbRequest) {
                        byteReceivedListener(buffer.array().asList())
                    }
                }
            }
        }
    }

    private fun stopMessageListenerAndReleaseUsb() {
        messageListenerJob?.cancel()
        messageListenerJob = null
        usbConnection?.close()
        usbConnection = null
        inEndpoint = null
        outEndpoint = null
        usbInterface = null
        usbDevice = null
    }

    companion object {
        private const val VID = 1240 // typical ASSA 950d's VendorId
        private const val PID = 63 // typical ASSA 950d's ProductId
        private const val BUFFER_SIZE = 64 // FIXME: to keep it hardcoded or
    }
}
