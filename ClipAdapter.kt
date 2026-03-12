package com.clipvault.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.clipvault.app.databinding.ItemClipBinding
import com.clipvault.app.databinding.ItemClipFloatBinding
import java.io.File

// ─── Main list adapter ────────────────────────────────────────────────────────

class ClipAdapter(
    private val onCopy:   (ClipEntry) -> Unit,
    private val onShare:  (ClipEntry) -> Unit,
    private val onDelete: (ClipEntry) -> Unit
) : ListAdapter<ClipEntry, ClipAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ClipEntry>() {
            override fun areItemsTheSame(a: ClipEntry, b: ClipEntry) = a.id == b.id
            override fun areContentsTheSame(a: ClipEntry, b: ClipEntry) = a == b
        }
    }

    inner class VH(val b: ItemClipBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, vt: Int) =
        VH(ItemClipBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val entry = getItem(pos)
        val b = holder.b

        // Badge
        b.tvTypeBadge.text = when (entry.type) {
            ClipType.TEXT       -> "TEXT"
            ClipType.URL        -> "LINK"
            ClipType.NUMBER     -> "NUM"
            ClipType.IMAGE      -> "IMAGE"
            ClipType.SCREENSHOT -> "SHOT"
        }
        b.tvTypeBadge.setBackgroundColor(badgeColor(entry.type))

        b.tvTime.text     = entry.shortDate()
        b.tvFullTime.text = entry.formattedTime()

        if (entry.type == ClipType.IMAGE || entry.type == ClipType.SCREENSHOT) {
            b.tvContent.visibility   = View.GONE
            b.ivImage.visibility     = View.VISIBLE
            b.tvCharCount.visibility = View.GONE
            val file = File(entry.content)
            if (file.exists()) b.ivImage.setImageBitmap(BitmapFactory.decodeFile(entry.content))
            else b.ivImage.setImageResource(android.R.drawable.ic_menu_gallery)
        } else {
            b.tvContent.visibility = View.VISIBLE
            b.ivImage.visibility   = View.GONE
            b.tvContent.text = if (entry.content.length > 200)
                entry.content.take(200) + "…" else entry.content
            if (entry.content.length > 60) {
                b.tvCharCount.visibility = View.VISIBLE
                b.tvCharCount.text = "${entry.content.length} chars"
            } else {
                b.tvCharCount.visibility = View.GONE
            }
        }

        b.btnCopy.setOnClickListener   { onCopy(entry) }
        b.btnShare.setOnClickListener  { onShare(entry) }
        b.btnDelete.setOnClickListener { onDelete(entry) }
    }

    private fun badgeColor(type: ClipType): Int = when (type) {
        ClipType.TEXT       -> 0xFF1C7ED6.toInt()
        ClipType.URL        -> 0xFF9C36B5.toInt()
        ClipType.NUMBER     -> 0xFFE67700.toInt()
        ClipType.IMAGE      -> 0xFF2F9E44.toInt()
        ClipType.SCREENSHOT -> 0xFF1098AD.toInt()
    }
}

// ─── Floating panel adapter (compact) ────────────────────────────────────────

class FloatingClipAdapter(
    private var items: MutableList<ClipEntry>,
    private val context: Context
) : RecyclerView.Adapter<FloatingClipAdapter.VH>() {

    inner class VH(val b: ItemClipFloatBinding) : RecyclerView.ViewHolder(b.root)

    fun updateData(newItems: MutableList<ClipEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, vt: Int) =
        VH(ItemClipFloatBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val entry = items[pos]
        val b = holder.b

        b.tvContent.text = when (entry.type) {
            ClipType.IMAGE, ClipType.SCREENSHOT -> "📸 ${File(entry.content).name}"
            ClipType.URL    -> "🔗 ${entry.content.take(60)}"
            ClipType.NUMBER -> "🔢 ${entry.content}"
            else            -> entry.content.take(80)
        }
        b.tvTime.text = entry.shortDate()

        b.btnCopy.setOnClickListener {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("ClipVault", entry.content))
            Toast.makeText(context, "✅ Copied!", Toast.LENGTH_SHORT).show()
        }

        b.btnShare.setOnClickListener {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, entry.content)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }, "Share via"
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
