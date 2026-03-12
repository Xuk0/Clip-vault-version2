package com.clipvault.app

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.clipvault.app.databinding.ActivityMainBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var adapter: ClipAdapter
    private var currentFilter: ClipType? = null

    // ── Launchers ──────────────────────────────────────────────────────────

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        ClipVaultService.start(this)
        if (Settings.canDrawOverlays(this)) showSnack("Floating bubble enabled! 🎉")
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Handled silently */ }

    // ── Broadcast ──────────────────────────────────────────────────────────

    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            runOnUiThread { loadData() }
        }
    }

    companion object {
        const val ACTION_DATA_CHANGED = "com.clipvault.DATA_CHANGED"
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.title = ""

        setupRecyclerView()
        setupFilterChips()
        setupSearch()
        setupFab()
        requestRequiredPermissions()
        loadData()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(dataReceiver, IntentFilter(ACTION_DATA_CHANGED), RECEIVER_NOT_EXPORTED)
        loadData()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(dataReceiver) } catch (_: Exception) {}
    }

    // ── Setup ──────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = ClipAdapter(
            onCopy   = { copyToClipboard(it) },
            onShare  = { shareEntry(it) },
            onDelete = {
                ClipStorage.delete(this, it.id)
                loadData()
                showSnack("🗑️ Removed")
            }
        )
        b.recyclerView.layoutManager = LinearLayoutManager(this)
        b.recyclerView.adapter = adapter
        b.recyclerView.setHasFixedSize(false)
    }

    private fun setupFilterChips() {
        b.chipAll.isChecked = true
        val listener = View.OnClickListener { v ->
            currentFilter = when ((v as Chip).id) {
                b.chipText.id       -> ClipType.TEXT
                b.chipUrl.id        -> ClipType.URL
                b.chipNumber.id     -> ClipType.NUMBER
                b.chipImage.id      -> ClipType.IMAGE
                b.chipScreenshot.id -> ClipType.SCREENSHOT
                else                -> null
            }
            loadData()
        }
        listOf(b.chipAll, b.chipText, b.chipUrl, b.chipNumber,
               b.chipImage, b.chipScreenshot).forEach { it.setOnClickListener(listener) }
    }

    private fun setupSearch() {
        b.searchView.setOnQueryTextListener(object :
            androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = true.also { filterData(q) }
            override fun onQueryTextChange(q: String?) = true.also { filterData(q) }
        })
    }

    private fun setupFab() {
        b.fabService.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) showOverlayDialog()
            else {
                ClipVaultService.start(this)
                showSnack("Bubble refreshed ✅")
            }
        }
    }

    // ── Data ───────────────────────────────────────────────────────────────

    private fun loadData() {
        val all      = ClipStorage.load(this)
        val filtered = if (currentFilter == null) all else all.filter { it.type == currentFilter }
        adapter.submitList(filtered.toMutableList())
        updateEmptyState(filtered.isEmpty())
        b.tvCount.text = "${all.size} items"
    }

    private fun filterData(query: String?) {
        val q   = query?.trim()?.lowercase() ?: ""
        val all = ClipStorage.load(this)
        val res = all.filter { e ->
            (currentFilter == null || e.type == currentFilter) &&
            (q.isEmpty() || (e.type != ClipType.IMAGE && e.type != ClipType.SCREENSHOT &&
                e.content.lowercase().contains(q)))
        }
        adapter.submitList(res.toMutableList())
        updateEmptyState(res.isEmpty())
    }

    private fun updateEmptyState(empty: Boolean) {
        b.emptyState.visibility  = if (empty) View.VISIBLE else View.GONE
        b.recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
    }

    // ── Copy & Share ───────────────────────────────────────────────────────

    private fun copyToClipboard(entry: ClipEntry) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("ClipVault", entry.content))
        showSnack("✅ Copied to clipboard!")
    }

    private fun shareEntry(entry: ClipEntry) {
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, entry.content)
            }, "Share via"
        ))
    }

    // ── Permissions ────────────────────────────────────────────────────────

    private fun requestRequiredPermissions() {
        val needed = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
            != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.READ_MEDIA_IMAGES
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (needed.isNotEmpty()) permLauncher.launch(needed.toTypedArray())

        if (!Settings.canDrawOverlays(this)) showOverlayDialog()
        else ClipVaultService.start(this)
    }

    private fun showOverlayDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Allow Display Over Other Apps")
            .setMessage("ClipVault needs this to show its floating clipboard bubble on top of other apps.\n\nTap OK to open Settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                overlayLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                           Uri.parse("package:$packageName"))
                )
            }
            .setNegativeButton("Skip") { _, _ ->
                ClipVaultService.start(this)
                Toast.makeText(this, "Floating bubble disabled", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // ── Menu ───────────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_clear -> {
            val count = ClipStorage.load(this).size
            MaterialAlertDialogBuilder(this)
                .setTitle("Clear All")
                .setMessage("Delete all $count saved items?")
                .setPositiveButton("Delete All") { _, _ ->
                    ClipStorage.clear(this)
                    loadData()
                    showSnack("All $count items cleared")
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }
        R.id.action_toggle_service -> {
            if (Settings.canDrawOverlays(this)) {
                ClipVaultService.start(this)
                showSnack("Service started ✅")
            } else showOverlayDialog()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showSnack(msg: String) =
        com.google.android.material.snackbar.Snackbar.make(b.root, msg, 2200).show()
}
