package com.example.grocery

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
// This class manages operations of a shopping cart using Firebase as a backend
class Cart {
    // Get an instance of the Firebase database
    private val database = FirebaseDatabase.getInstance()
    // Get a reference to the "cart" node in the Firebase database
    private val cartRef = database.getReference("cart")
    // Method to add an item to the cart
    fun addItem(item: String) {
        // Create a new unique key in the "cart" node
        val key = cartRef.push().key
        key?.let {
            // Set the value of the new key to the item string
            cartRef.child(it).setValue(item).addOnCompleteListener { task ->
                if (task.isSuccessful) {// Log success message
                    Log.d("Firebase", "Data write is successful!")
                } else {// Log failure message
                    Log.d("Firebase", "Data write failed!", task.exception)
                }
            }
        }
    }
    // Method to retrieve items in the cart
    fun getItems(callback: (List<String>) -> Unit) {
        // Create a mutable list to hold the items
        val items = mutableListOf<String>()
        // Add a value event listener to the "cart" node
        cartRef.addValueEventListener(object : ValueEventListener {
            // Handle data changes
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                items.clear() // clear the list before adding items
                dataSnapshot.children.forEach { snapshot ->
                    // Iterate over all children of the snapshot
                    val item = snapshot.getValue(String::class.java)
                    // Get the value of each snapshot as a string
                    item?.let { items.add(it) }
                }
                callback(items)// Call the callback function with the items list

            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.w("Firebase", "getItems:onCancelled", databaseError.toException())
            }
        })
    }

    fun removeItem(item: String) {
        // remove the item from Firebase
        cartRef.orderByValue().equalTo(item).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (snapshot in dataSnapshot.children) {
                    snapshot.ref.removeValue()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.w("Firebase", "removeItem:onCancelled", databaseError.toException())
            }
        })
    }
}

