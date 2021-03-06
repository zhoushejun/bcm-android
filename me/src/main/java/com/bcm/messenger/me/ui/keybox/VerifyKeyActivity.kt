package com.bcm.messenger.me.ui.keybox

import android.content.Intent
import android.os.Bundle
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.utils.hideKeyboard
import com.bcm.messenger.me.R
import com.bcm.messenger.me.ui.login.LoginVerifyPinFragment
import com.bcm.messenger.me.ui.login.RegistrationActivity
import com.bcm.messenger.me.ui.login.backup.CheckBackupPasswordFragment
import com.bcm.messenger.me.ui.pinlock.PinInputActivity
import com.bcm.route.annotation.Route

@Route(routePath = ARouterConstants.Activity.VERIFY_PASSWORD)
class VerifyKeyActivity : SwipeBaseActivity() {

    companion object {

        const val ACCOUNT_ID = "account_id"
        const val BACKUP_JUMP_ACTION = "BACKUP_JUMP_ACTION"
        const val NEED_FINISH = "NEED_FINISH"
        const val SHOW_QRCODE_BACKUP = 1
        const val DELETE_PROFILE = 2
        const val LOGIN_PROFILE = 3
        const val CHANGE_PIN = 4
        const val CLEAR_PIN = 5
    }

    private var controlType: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        disableStatusBarLightMode()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_verify)
        initView()
    }

    fun initView() {
        controlType = intent.getIntExtra(BACKUP_JUMP_ACTION, 0)
        val arg = intent.extras
        arg?.putBoolean(NEED_FINISH, true)
        if (controlType == DELETE_PROFILE || controlType == SHOW_QRCODE_BACKUP || controlType == CHANGE_PIN || controlType == CLEAR_PIN) {
            val f = CheckBackupPasswordFragment()
            f.arguments = arg
            supportFragmentManager.beginTransaction()
                    .replace(R.id.register_container, f)
                    .commit()
        } else {
            if (controlType == LOGIN_PROFILE) {
                val f = LoginVerifyPinFragment()
                arg?.putString(RegistrationActivity.RE_LOGIN_ID, intent.getStringExtra(RegistrationActivity.RE_LOGIN_ID))
                f.arguments = arg
                supportFragmentManager.beginTransaction()
                        .replace(R.id.register_container, f)
                        .commit()
            }
        }
    }

    override fun onBackPressed() {
        if (controlType == CHANGE_PIN || controlType == CLEAR_PIN) {
            PinInputActivity.routerVerifyUnlock(this)
            finish()
        } else {
            if (!AMESelfData.isLogin){
                hideKeyboard()
                val intent = Intent(this, RegistrationActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                this.startActivity(intent)
                this.finish()
            } else {
                super.onBackPressed()
            }
        }
    }
}