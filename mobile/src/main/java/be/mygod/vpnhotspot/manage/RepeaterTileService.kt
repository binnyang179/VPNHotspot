package be.mygod.vpnhotspot.manage

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.wifi.p2p.WifiP2pGroup
import android.os.IBinder
import android.service.quicksettings.Tile
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import be.mygod.vpnhotspot.R
import be.mygod.vpnhotspot.RepeaterService
import be.mygod.vpnhotspot.util.KillableTileService
import be.mygod.vpnhotspot.util.stopAndUnbind

@RequiresApi(24)
class RepeaterTileService : KillableTileService() {
    private val tile by lazy { Icon.createWithResource(application, R.drawable.ic_action_settings_input_antenna) }

    private var binder: RepeaterService.Binder? = null

    override fun onStartListening() {
        super.onStartListening()
        if (!RepeaterService.supported) updateTile()
        else bindService(Intent(this, RepeaterService::class.java), this, Context.BIND_AUTO_CREATE)
    }

    override fun onStopListening() {
        if (RepeaterService.supported) stopAndUnbind(this)
        super.onStopListening()
    }

    override fun onClick() {
        val binder = binder
        if (binder == null) tapPending = true else when (binder.service.status) {
            RepeaterService.Status.ACTIVE -> binder.shutdown()
            RepeaterService.Status.IDLE ->
                ContextCompat.startForegroundService(this, Intent(this, RepeaterService::class.java))
            else -> { }
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        binder = service as RepeaterService.Binder
        service.statusChanged[this] = { updateTile() }
        service.groupChanged[this] = this::updateTile
        super.onServiceConnected(name, service)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        val binder = binder ?: return
        this.binder = null
        binder.statusChanged -= this
        binder.groupChanged -= this
    }

    private fun updateTile(group: WifiP2pGroup? = binder?.group) {
        qsTile?.run {
            when ((binder ?: return).service.status) {
                RepeaterService.Status.IDLE -> {
                    state = Tile.STATE_INACTIVE
                    label = getText(R.string.title_repeater)
                }
                RepeaterService.Status.ACTIVE -> {
                    state = Tile.STATE_ACTIVE
                    label = group?.networkName
                }
                else -> {   // STARTING or DESTROYED, which should never occur
                    state = Tile.STATE_UNAVAILABLE
                    label = getText(R.string.title_repeater)
                }
            }
            icon = tile
            updateTile()
        }
    }
}
