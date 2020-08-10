package com.jpwolfso.hrm

import android.content.*
import android.content.pm.PackageManager
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

lateinit var tile: Tile

open class TileService : TileService() {

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

    override fun onStartListening() {
        super.onStartListening()

        tile = this.qsTile
        tile.state = Tile.STATE_INACTIVE
        tile.updateTile()

    }

    override fun onClick() {
        super.onClick()

        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()

        if (checkSelfPermission(android.Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED) {

            broadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(p0: Context?, p1: Intent?) {
                    when (p1!!.action) {
                        "finishMeasureTile" -> {
                            Toast.makeText(
                                applicationContext,
                                "Your heart rate is " + p1.getDoubleExtra("bpm", 0.0)
                                    .toString() + " bpm",
                                Toast.LENGTH_SHORT
                            ).show()
                            if (mBound) {
                                unbindService(connection)
                                mBound = false
                            }
                            unregisterReceiver(broadcastReceiver)
                        }
                    }
                }
            }

            var intentFilter = IntentFilter()
            intentFilter.addAction("finishMeasureTile")
            registerReceiver(broadcastReceiver, intentFilter)

            Intent(applicationContext, HRMService::class.java).also { intent ->
                bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }

        } else {
            Toast.makeText(
                applicationContext,
                "Body Sensors permission not granted.",
                Toast.LENGTH_SHORT
            ).show()
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()
        }


    }

}
