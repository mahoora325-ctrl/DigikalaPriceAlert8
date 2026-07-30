package ir.example.digikalapricealert

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton

class SearchResultAdapter(
    private val onAddClicked: (SearchResultItem) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.ViewHolder>() {

    private val items = mutableListOf<SearchResultItem>()
    // شناسه‌ی محصولاتی که از قبل تحت پایش هستند - برای غیرفعال کردن دکمه‌ی افزودن
    private val alreadyTrackedIds = mutableSetOf<String>()

    fun submitList(newItems: List<SearchResultItem>, trackedIds: Set<String>) {
        items.clear()
        items.addAll(newItems)
        alreadyTrackedIds.clear()
        alreadyTrackedIds.addAll(trackedIds)
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgProduct: ImageView = view.findViewById(R.id.imgSearchResult)
        val txtTitle: TextView = view.findViewById(R.id.txtSearchTitle)
        val txtPrice: TextView = view.findViewById(R.id.txtSearchPrice)
        val btnAdd: MaterialButton = view.findViewById(R.id.btnAddFromSearch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.txtTitle.text = item.title ?: "محصول ${item.productId}"
        holder.txtPrice.text = if (item.priceToman != null)
            "${PersianNumberUtils.formatToman(item.priceToman)} تومان"
        else
            "قیمت نامشخص"

        val alreadyTracked = alreadyTrackedIds.contains(item.productId)
        holder.btnAdd.isEnabled = !alreadyTracked
        holder.btnAdd.text = if (alreadyTracked) "قبلاً اضافه شده" else "افزودن به پایش"
        holder.btnAdd.setOnClickListener { if (!alreadyTracked) onAddClicked(item) }

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

    override fun getItemCount(): Int = items.size
}
