package com.example.grocery

import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class CartActivity : AppCompatActivity() {

    private lateinit var cartListView: ListView
    private val cart = Cart()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cart)

        cartListView = findViewById(R.id.cartListView)

        cart.getItems { items ->
            val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items)
            cartListView.adapter = adapter

            // Set the on item click listener for the list view
            cartListView.setOnItemClickListener { parent, view, position, id ->
                val selectedItem = parent.getItemAtPosition(position).toString()
                showRemoveFromCartDialog(selectedItem)
            }
        }
    }

    private fun showRemoveFromCartDialog(item: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Remove from cart")
            .setMessage("Do you want to remove $item from your cart?")
            .setPositiveButton("Yes") { _, _ ->
                cart.removeItem(item)
                // update the list view
                (cartListView.adapter as ArrayAdapter<*>).notifyDataSetChanged()
            }
            .setNegativeButton("No", null)
            .create()
            .show()
    }
}

