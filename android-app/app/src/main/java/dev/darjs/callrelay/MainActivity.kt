package dev.darjs.callrelay

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

/**
 * Setup UI: shows whether CallRelay is the default call screening app and lets
 * the user request the [RoleManager.ROLE_CALL_SCREENING] role.
 *
 * Plain platform [Activity] (no AppCompat), black-on-white, no animations —
 * e-ink friendly.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.status_text)
        val setButton = findViewById<Button>(R.id.set_button)

        updateStatus(statusText, setButton)

        setButton.setOnClickListener {
            val roleManager = getSystemService(ROLE_SERVICE) as RoleManager
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_ROLE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ROLE) {
            val statusText = findViewById<TextView>(R.id.status_text)
            val setButton = findViewById<Button>(R.id.set_button)
            updateStatus(statusText, setButton)
        }
    }

    private fun updateStatus(statusText: TextView, setButton: Button) {
        val roleManager = getSystemService(ROLE_SERVICE) as RoleManager
        val held = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        statusText.text = if (held) "CallRelay is your call screening app." else "CallRelay is not set as the call screening app."
        setButton.isEnabled = !held
    }

    companion object {
        private const val REQUEST_ROLE = 1
    }
}
