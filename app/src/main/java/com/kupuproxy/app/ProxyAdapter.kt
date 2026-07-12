package com.kupuproxy.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val btnCopy: MaterialButton = itemView.findViewById(R.id.btnCopy)
        private val btnConnect: MaterialButton = itemView.findViewById(R.id.btnConnect)
        private val btnFavorite: ImageView = itemView.findViewById(R.id.btnFavorite)

        fun bind(proxy: ProxyWithPing, position: Int) {
            val info = ProxyManager.parseProxyUrl(proxy.url)
            tvIndex.text = (position + 1).toString()
            tvHost.text = if (info != null) {
                "${info.server}:${info.port}"
            } else {
                proxy.url.take(40)
            }

            // Статус как в Telegram
            when (proxy.status) {
                ProxyStatus.AVAILABLE -> {
                    tvStatus.text = proxy.statusText.ifBlank { "Доступен" }
                    tvStatus.setTextColor(ContextCompat.getColor(context, R.color.ping_excellent))
                    tvStatus.visibility = View.VISIBLE
                }
                ProxyStatus.UNAVAILABLE -> {
                    tvStatus.text = "Недоступен"
                    tvStatus.setTextColor(ContextCompat.getColor(context, R.color.ping_slow))
                    tvStatus.visibility = View.VISIBLE
                }
            }

            if (proxy.pingMs > 0) {
                tvPing.text = context.getString(R.string.ping_format, proxy.pingMs)
                val pingColor = when {
                    proxy.pingMs < 150 -> R.color.ping_excellent
                    proxy.pingMs < 400 -> R.color.ping_good
                    else -> R.color.ping_slow
                }
                tvPing.setTextColor(ContextCompat.getColor(context, pingColor))
                tvPing.visibility = View.VISIBLE
            } else {
                tvPing.visibility = View.GONE
            }

            refreshStar(proxy.url)

            btnFavorite.setOnClickListener {
                val added = ProxyCache.toggleFavorite(context, proxy.url)
                refreshStar(proxy.url)
                Toast.makeText(
                    context,
                    if (added) "В избранном" else "Убрано из избранного",
                    Toast.LENGTH_SHORT
                ).show()
            }

            btnCopy.setOnClickListener {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Proxy", proxy.url))
                Toast.makeText(context, R.string.proxy_copied, Toast.LENGTH_SHORT).show()
            }

            val openTg = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(proxy.url)))
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.telegram_missing, Toast.LENGTH_LONG).show()
                }
            }
            btnConnect.setOnClickListener { openTg() }
            cardView.setOnClickListener { openTg() }
        }

        private fun refreshStar(url: String) {
            val fav = ProxyCache.isFavorite(context, url)
            btnFavorite.setImageResource(
                if (fav) R.drawable.ic_star else R.drawable.ic_star_outline
            )
        }
    }
}
