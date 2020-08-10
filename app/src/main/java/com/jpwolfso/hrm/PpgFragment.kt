package com.jpwolfso.hrm

import android.content.*
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.view.*
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries

lateinit var mSeries: LineGraphSeries<DataPoint>

lateinit var measureButton: Button
lateinit var progressBar: ProgressBar
lateinit var textView: TextView
lateinit var graphView: GraphView
lateinit var bpmtextView: TextView

lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
lateinit var alertDialog: AlertDialog

lateinit var broadcastReceiver: BroadcastReceiver

lateinit var disclaimerDialog: AlertDialog
lateinit var helpDialog: AlertDialog
lateinit var unsupportedDialog: AlertDialog

class PpgFragment : Fragment() {

    private lateinit var mService: HRMService
    private var mBound: Boolean = false

    private val connection = object : ServiceConnection {
        override fun onServiceDisconnected(p0: ComponentName?) {
            mBound = false
        }

        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            val binder = p1 as HRMService.MyBinder
            mService = binder.getService()
            mBound = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        disclaimerDialog = AlertDialog.Builder(requireActivity())
            .setTitle("Disclaimer")
            .setMessage("This app is not guaranteed to provide a medically accurate heart rate. Always consult a doctor if you have any heart health related concerns.")
            .setPositiveButton("OK", null)
            .show()

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                when (p1!!.action) {
                    "sensorData" -> {
                        var sensorData = p1.getDoubleArrayExtra("sensorData")
                        var sensorDataPoint = DataPoint(sensorData!![0], sensorData[1])

                        if (p1.getBooleanExtra("firstPoint", true)) {
                            mSeries = LineGraphSeries<DataPoint>(arrayOf(sensorDataPoint))
                            when (sensorId) {
                                65571 -> {
                                    mSeries.color = Color.BLACK
                                }
                                65572 -> {
                                    mSeries.color = Color.RED
                                }
                                65573 -> {
                                    mSeries.color = Color.GREEN
                                }
                                65574 -> {
                                    mSeries.color = Color.BLUE
                                }
                            }
                            graphView.addSeries(mSeries)
                        } else {
                            mSeries.appendData(sensorDataPoint, false, 1000)
                        }
                    }
                    "finishMeasureUI" -> {
                        bpmtextView.apply {
                            visibility = View.VISIBLE
                            text = p1.getDoubleExtra("bpm", 0.0).toInt().toString() + " BPM"
                        }

                        measureButton.visibility = View.VISIBLE
                        progressBar.visibility = View.GONE
                        textView.visibility = View.GONE

                        if (mBound) {
                            requireActivity().unbindService(connection)
                            mBound = false
                        }

                        activity?.unregisterReceiver(broadcastReceiver)
                    }
                    "unsupportedSensor" -> {
                        measureButton.visibility = View.VISIBLE
                        progressBar.visibility = View.GONE
                        textView.visibility = View.GONE

                        if (mBound) {
                            requireActivity().unbindService(connection)
                            mBound = false
                        }

                        activity?.unregisterReceiver(broadcastReceiver)

                        unsupportedDialog = AlertDialog.Builder(requireActivity())
                            .setTitle("Unsupported heart rate sensor")
                            .setMessage("Your device may be incompatible. Please try selecting a different heart rate sensor.")
                            .setPositiveButton("OK") { _, _ ->
                                parentFragmentManager.beginTransaction()
                                    .replace(R.id.fragment_container_view_tag, SettingsFragment())
                                    .addToBackStack("ppg")
                                    .commit()
                            }
                            .setCancelable(false)
                            .show()
                    }
                }
            }
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        requestPermissionLauncher = registerForActivityResult(RequestPermission()) {}
        setHasOptionsMenu(true)

        return inflater.inflate(R.layout.fragment_ppg, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.apply {
            measureButton = findViewById(R.id.button)
            progressBar = findViewById(R.id.progressBar)
            textView = findViewById(R.id.textView)
            graphView = findViewById(R.id.graph)
            graphView.apply {
                viewport.apply {
                    isXAxisBoundsManual = true
                    setMinX(0.0)
                    setMaxX(
                        PreferenceManager.getDefaultSharedPreferences(requireActivity())
                            .getInt("hrm_time", 10).toDouble()
                    )
                    isHorizontalScrollBarEnabled = true
                    isScalable = true
                    isScrollable = true
                    isScrollbarFadingEnabled = true
                }
                gridLabelRenderer.apply {
                    isHorizontalLabelsVisible = false
                    isVerticalLabelsVisible = false
                    title = "PPG data"
                }
            }
            bpmtextView = findViewById(R.id.textView2)
        }

    }

    override fun onResume() {
        super.onResume()

        measureButton.setOnClickListener {
            when {
                activity?.checkSelfPermission(android.Manifest.permission.BODY_SENSORS) == PERMISSION_GRANTED -> {
                    graphView.removeAllSeries()

                    var intentFilter = IntentFilter()
                    intentFilter.addAction("sensorData")
                    activity?.registerReceiver(broadcastReceiver, intentFilter)
                    intentFilter.addAction("finishMeasureUI")
                    activity?.registerReceiver(broadcastReceiver, intentFilter)
                    intentFilter.addAction("unsupportedSensor")
                    activity?.registerReceiver(broadcastReceiver, intentFilter)

                    Intent(activity, HRMService::class.java).also { intent ->
                        requireActivity().bindService(intent, connection, Context.BIND_AUTO_CREATE)
                    }

                    measureButton.visibility = View.GONE
                    progressBar.visibility = View.VISIBLE
                    textView.visibility = View.VISIBLE
                    bpmtextView.visibility = View.INVISIBLE

                }
                shouldShowRequestPermissionRationale(android.Manifest.permission.BODY_SENSORS) -> {
                    alertDialog = AlertDialog.Builder(requireActivity())
                        .setMessage("Access to the Body Sensors permission is required to measure your heart rate.")
                        .setPositiveButton("OK") { _, _ -> requestPermissionLauncher.launch(android.Manifest.permission.BODY_SENSORS) }
                        .setNegativeButton("Exit app") { _, _ -> requireActivity().finish() }
                        .create()
                    alertDialog.show()
                }
                else -> {
                    requestPermissionLauncher.launch(android.Manifest.permission.BODY_SENSORS)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()

        if (mBound) {
            requireActivity().unbindService(connection)
            mBound = false
        }

        measureButton.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        textView.visibility = View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        inflater.inflate(R.menu.overflow, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container_view_tag, SettingsFragment())
                    .addToBackStack("ppg")
                    .commit()
                true
            }
            R.id.help -> {
                helpDialog = AlertDialog.Builder(requireActivity())
                    .setMessage("For best results, ensure that the heart rate sensor is covered properly. It is recommended to repeat measuring your heart rate until the PPG data graph resembles a consistent heart rate pattern.")
                    .setPositiveButton("OK", null)
                    .show()
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

}