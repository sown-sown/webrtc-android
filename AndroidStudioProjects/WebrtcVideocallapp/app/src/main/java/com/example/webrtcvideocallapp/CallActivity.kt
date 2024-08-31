package com.example.webrtcvideocallapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.util.*

class CallActivity : AppCompatActivity() {

    private var username = ""
    private var friendsUsername = ""
    private var isPeerConnected = false
    private val firebaseRef = Firebase.database.getReference("users")

    private var isAudio = true
    private var isVideo = true
    private lateinit var webView: WebView
    private lateinit var callBtn: Button
    private lateinit var toggleAudioBtn: ImageView
    private lateinit var toggleVideoBtn: ImageView
    private lateinit var friendNameEdit: EditText
    private lateinit var callLayout: ViewGroup
    private lateinit var incomingCallTxt: TextView
    private lateinit var acceptBtn: ImageView
    private lateinit var rejectBtn: ImageView
    private lateinit var inputLayout: ViewGroup
    private lateinit var callControlLayout: ViewGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        // Initialize UI components
        webView = findViewById(R.id.webView)
        callBtn = findViewById(R.id.callBtn)
        toggleAudioBtn = findViewById(R.id.toggleAudioBtn)
        toggleVideoBtn = findViewById(R.id.toggleVideoBtn)
        friendNameEdit = findViewById(R.id.friendNameEdit)
        callLayout = findViewById(R.id.callLayout)
        incomingCallTxt = findViewById(R.id.incomingCallTxt)
        acceptBtn = findViewById(R.id.acceptBtn)
        rejectBtn = findViewById(R.id.rejectBtn)
        inputLayout = findViewById(R.id.inputLayout)
        callControlLayout = findViewById(R.id.callControlLayout)

        // Retrieve username from intent
        username = intent.getStringExtra("username") ?: ""
        Log.d("CallActivity", "Received username: $username")

        // Set up button listeners
        callBtn.setOnClickListener {
            friendsUsername = friendNameEdit.text.toString()
            sendCallRequest()
        }

        toggleAudioBtn.setOnClickListener {
            isAudio = !isAudio
            callJavascriptFunction("javascript:toggleAudio(\"$isAudio\")")
            toggleAudioBtn.setImageResource(if (isAudio) R.drawable.ic_baseline_mic_24 else R.drawable.ic_launcher_background)
        }

        toggleVideoBtn.setOnClickListener {
            isVideo = !isVideo
            callJavascriptFunction("javascript:toggleVideo(\"$isVideo\")")
            toggleVideoBtn.setImageResource(if (isVideo) R.drawable.ic_baseline_videocam_24 else R.drawable.ic_baseline_videocam_off_24)
        }

        setupWebView()
    }

    private fun sendCallRequest() {
        if (!isPeerConnected) {
            Toast.makeText(this, "You're not connected. Check your internet", Toast.LENGTH_LONG).show()
            return
        }

        Log.d("CallActivity", "Sending call request to $friendsUsername with username $username")

        firebaseRef.child(friendsUsername).child("incoming").setValue(username)
            .addOnSuccessListener {
                Log.d("FirebaseWrite", "Call request sent successfully.")
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseWriteError", "Failed to send call request: ${e.message}")
            }

        firebaseRef.child(friendsUsername).child("isAvailable").addValueEventListener(object: ValueEventListener {
            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseError", "Error in isAvailable listener: ${error.message}")
            }

            override fun onDataChange(snapshot: DataSnapshot) {
                val isAvailable = snapshot.getValue(Boolean::class.java)
                Log.d("CallActivity", "isAvailable value: $isAvailable")
                if (isAvailable == true) {
                    listenForConnId()
                }
            }
        })
    }

    private fun listenForConnId() {
        Log.d("CallActivity", "Listening for connId for $friendsUsername")
        firebaseRef.child(friendsUsername).child("connId").addValueEventListener(object: ValueEventListener {
            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseError", "Error in connId listener: ${error.message}")
            }

            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("CallActivity", "connId value: ${snapshot.value}")
                if (snapshot.value != null) {
                    switchToControls()
                    callJavascriptFunction("javascript:startCall(\"${snapshot.value}\")")
                }
            }
        })
    }

    private fun setupWebView() {
        webView.webChromeClient = object: WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }

        webView.settings.javaScriptEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.addJavascriptInterface(JavascriptInterface(this), "Android")

        loadVideoCall()
    }

    private fun loadVideoCall() {
        val filePath = "file:android_asset/call.html"
        webView.loadUrl(filePath)

        webView.webViewClient = object: WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                initializePeer()
            }
        }
    }

    private var uniqueId = ""

    private fun initializePeer() {
        uniqueId = getUniqueID()
        Log.d("CallActivity", "Initializing peer with uniqueId: $uniqueId")

        callJavascriptFunction("javascript:init(\"$uniqueId\")")
        firebaseRef.child(username).child("incoming").addValueEventListener(object: ValueEventListener {
            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseError", "Error in incoming call listener: ${error.message}")
            }

            override fun onDataChange(snapshot: DataSnapshot) {
                val caller = snapshot.getValue(String::class.java)
                Log.d("CallActivity", "Incoming call value: $caller")
                onCallRequest(caller)
            }
        })
    }

    private fun onCallRequest(caller: String?) {
        if (caller == null) return

        callLayout.visibility = View.VISIBLE
        incomingCallTxt.text = "$caller is calling..."

        acceptBtn.setOnClickListener {
            firebaseRef.child(username).child("connId").setValue(uniqueId)
                .addOnSuccessListener {
                    Log.d("FirebaseWrite", "Connection ID set successfully.")
                }
                .addOnFailureListener { e ->
                    Log.e("FirebaseWriteError", "Failed to set connection ID: ${e.message}")
                }
            firebaseRef.child(username).child("isAvailable").setValue(true)
                .addOnSuccessListener {
                    Log.d("FirebaseWrite", "Availability status set to true.")
                }
                .addOnFailureListener { e ->
                    Log.e("FirebaseWriteError", "Failed to set availability status: ${e.message}")
                }

            callLayout.visibility = View.GONE
            switchToControls()
        }

        rejectBtn.setOnClickListener {
            firebaseRef.child(username).child("incoming").setValue(null)
                .addOnSuccessListener {
                    Log.d("FirebaseWrite", "Incoming call request rejected.")
                }
                .addOnFailureListener { e ->
                    Log.e("FirebaseWriteError", "Failed to reject incoming call request: ${e.message}")
                }
            callLayout.visibility = View.GONE
        }
    }

    private fun switchToControls() {
        inputLayout.visibility = View.GONE
        callControlLayout.visibility = View.VISIBLE
    }

    private fun getUniqueID(): String {
        return UUID.randomUUID().toString()
    }

    private fun callJavascriptFunction(functionString: String) {
        webView.post { webView.evaluateJavascript(functionString, null) }
    }

    fun onPeerConnected() {
        isPeerConnected = true
    }

    override fun onDestroy() {
        super.onDestroy()
        firebaseRef.child(username).removeValue()
            .addOnSuccessListener {
                Log.d("FirebaseWrite", "User data removed on destroy.")
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseWriteError", "Failed to remove user data: ${e.message}")
            }
        webView.loadUrl("about:blank")
    }
}
