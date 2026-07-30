package ir.example.digikalapricealert

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ProductAdapter(
    private val onRemove: (TrackedProduct) -> Unit,
    private val onItemClick: (TrackedProduct) -> Unit,
    private val onEditThreshold: (TrackedProduct) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ViewHolder>() {

    private val items = mutableListOf<TrackedProduct>()

    fun submitList(newItems: List<TrackedProduct>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardRoot: View = view.findViewById(R.id.cardRoot)
        val imgProduct: ImageView = view.findViewById(R.id.imgProduct)
        val txtTitle: android.widget.TextView = view.findViewById(R.id.txtTitle)
        val txtPrice: android.widget.TextView = view.findViewById(R.id.txtPrice)
        val txtThreshold: android.widget.TextView = view.findViewById(R.id.txtThreshold)
        val txtLastChecked: android.widget.TextView = view.findViewById(R.id.txtLastChecked)
        val btnRemove: com.google.android.material.button.MaterialButton = view.findViewById(R.id.btnRemove)
        val btnEditThreshold: com.google.android.material.button.MaterialButton = view.findViewById(R.id.btnEditThreshold)
        val btnOpenInDigikala: com.google.android.material.button.MaterialButton = view.findViewById(R.id.btnOpenInDigikala)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_product, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.txtTitle.text = item.title ?: "محصول ${item.productId} (در حال دریافت اطلاعات...)"
        holder.txtPrice.text = if (item.lastPriceToman != null)
            "قیمت فعلی: ${PersianNumberUtils.formatToman(item.lastPriceToman!!)} تومان"
        else
            "قیمت فعلی: هنوز بررسی نشده"
        holder.txtThreshold.text = "سقف تعیین‌شده: ${PersianNumberUtils.formatToman(item.thresholdToman)} تومان"
        holder.txtLastChecked.text = item.lastChecked?.let {
            PersianNumberUtils.toPersianDigits(it)
        } ?: ""
        holder.btnRemove.setOnClickListener { onRemove(item) }
        holder.btnEditThreshold.setOnClickListener { onEditThreshold(item) }
        holder.cardRoot.setOnClickListener { onItemClick(item) }
        holder.btnOpenInDigikala.setOnClickListener { openInDigikala(holder, item) }

        if (item.imageUrl != null) {
            Glide.with(holder.imgProduct.context)
                .load(item.imageUrl)
                .centerCrop()
                .into(holder.imgProduct)
        } else {
            Glide.with(holder.imgProduct.context).clear(holder.imgProduct)
            holder.imgProduct.setImageDrawable(null)
        }
    }

    /**
     * صفحه‌ی محصول را با یک لینک عادی دیجی‌کالا باز می‌کند. اگر اپلیکیشن
     * دیجی‌کالا نصب باشد و لینک‌های آن را ثبت کرده باشد (App Links)، مستقیم
     * در همان اپ باز می‌شود؛ وگرنه در مرورگر گوشی باز خواهد شد.
     */
    private fun openInDigikala(holder: ViewHolder, item: TrackedProduct) {
        val url = "https://www.digikala.com/product/dkp-${item.productId}/"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            holder.itemView.context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(holder.itemView.context, "امکان باز کردن این لینک وجود ندارد", Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount(): Int = items.size
}
