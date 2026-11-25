package com.privacyshield.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.privacyshield.app.shield.Shield

/** Invisible trampoline for the Quick Settings tile: starts the shield, then closes. */
class ToggleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Shield.canDraw(this)) Shield.start(this)
        else startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
