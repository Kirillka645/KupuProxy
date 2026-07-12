package com.kupuproxy.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class ProxyAdapter(
    private val context: Context,
    private val proxies: List<ProxyWithPing>
) : RecyclerView.Adapter<ProxyAdapter.ProxyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProxyViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_proxy, parent, false)
        return ProxyViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProxyViewHolder, position: Int) {
        holder.bind(proxies[position], position)
    }

    override fun getItemCount(): Int = proxies.size

    inner class ProxyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.cardProxy)
        private val tvIndex: TextView = itemView.findViewById(R.id.tvIndex)
        private val tvHost: TextView = itemView.findViewById(R.id.tvHost)
        private val tvPing: TextView = itemView.findViewById(R.id.tvPing)
        private val btnCopy: MaterialButton = itemView.findViewById(R.id.btnCopy)
        private val btnConnect: MaterialButton = itemView.findViewById(R.id.btnConnect)

        fun bind(proxy: ProxyWithPing, position: Int) {
            val info = ProxyManager.parseProxyUrl(proxy.url) ?: return

            tvIndex.text = (position + 1).toString()
            tvHost.text = "${info.server}:${info.port}"
            tvPing.text = context.getString(R.string.ping_format, proxy.pingMs)

            val pingColor = when {
                proxy.pingMs < 100 -> R.color.ping_excellent
                proxy.pingMs < 300 -> R.color.ping_good
                else -> R.color.ping_slow
            }
            tvPing.setTextColor(ContextCompat.getColor(context, pingColor))

            btnCopy.setOnClickListener {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Proxy", proxy.url))
                Toast.makeText(context, R.string.proxy_copied, Toast.LENGTH_SHORT).show()
            }

            btnConnect.setOnClickListener {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(proxy.url)))
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.telegram_missing, Toast.LENGTH_LONG).show()
                }
            }

            cardView.setOnClickListener {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(proxy.url)))
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.telegram_missing, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
