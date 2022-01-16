package vib.assaqueue

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.usb.UsbManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import vib.assaqueue.communication.CommunicationService
import vib.assaqueue.communication.ConnectionStatus
import vib.assaqueue.communication.IndustrialDoorsProtocolHandler
import vib.assaqueue.communication.UsbHidDevice
import vib.assaqueue.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private lateinit var usbManager: UsbManager
    private lateinit var communicationService: CommunicationService
    private var serviceBound: Boolean = false

    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
            Log.i("vitDebug", "onServiceConnected")
            communicationService =
                (service as CommunicationService.SerialCommunicationBinder).getService()

            communicationService.connectionStatus.observe(this@MainActivity) {
                it?.let {
                    changeUiAccordingToConnectionStatus(it)
                }
            }
        }

        override fun onServiceDisconnected(className: ComponentName?) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        Intent(this, CommunicationService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        setBindings()
    }

    override fun onDestroy() {
        unbindService(serviceConnection)
        serviceBound = false
        super.onDestroy()
    }

    private fun changeUiAccordingToConnectionStatus(connStatus: ConnectionStatus) {
        binding.textConnectionStatus.text = getString(connStatus.getResourceString())
        when (connStatus) {
            ConnectionStatus.CONNECTED -> {
                binding.buttonConnect.visibility = View.GONE
                binding.buttonDisconnect.visibility = View.VISIBLE
            }
            else -> {
                binding.buttonConnect.visibility = View.VISIBLE
                binding.buttonDisconnect.visibility = View.GONE
            }
        }
    }

    private fun setBindings() {
        binding.buttonConnect.setOnClickListener {
            val device = UsbHidDevice(this.application, usbManager.deviceList.keys.first(), usbManager.deviceList.keys.first())
            communicationService.connect(device, IndustrialDoorsProtocolHandler())
        }
        binding.buttonDisconnect.setOnClickListener {
            communicationService.disconnect()
        }
        binding.buttonReadVersion.setOnClickListener {
            binding.textVersion.text = ""
            mainViewModel.getSwVersion()
        }
        binding.buttonReadParameter.setOnClickListener {
            mainViewModel.readParameter(binding.editParameter.text.toString())
        }
        binding.buttomWriteParameter.setOnClickListener {
            mainViewModel.writeParameter(
                binding.editParameterIdToWrite.text.toString(),
                binding.editParameterValueToWrite.text.toString(),
            )
        }

        mainViewModel.swVersion.observe(this) {
            it?.let { binding.textVersion.text = it }
        }
        mainViewModel.parameterValue.observe(this) {
            it?.let { binding.textParameter.text = it }
        }
    }
}