package com.bcm.messenger.chats.user

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.mediabrowser.ui.MediaBrowserActivity
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.database.repositories.RecipientRepo
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.event.HomeTopEvent
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IAmeAppModule
import com.bcm.messenger.common.provider.IContactModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.CommonSettingItem
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.setDrawableRight
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.chats_user_activity.*
import java.util.concurrent.TimeUnit

@Route(routePath = ARouterConstants.Activity.CHAT_USER_PATH)
class ChatUserPageActivity : SwipeBaseActivity(), RecipientModifiedListener {

    companion object {
        const val TAG = "ChatUserPageActivity"
    }

    private var threadId: Long = -1L
    private var historyClearing = false
    private var blockHandling = false

    private var lightMode = false
    private var isBgLight = true

    private var mCurrentPinStatus: Boolean = false

    private var contactModule: IContactModule? = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_CONTACTS_BASE).navigationWithCast()

    private lateinit var mRecipient: Recipient

    private var mGoingFinish = false

    override fun onDestroy() {
        super.onDestroy()
        if (::mRecipient.isInitialized) {
            mRecipient.removeListener(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        disableStatusBarLightMode()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_user_activity)

        initView()
        initData()
    }

    private fun initView() {
        initTitleBar()

        chat_user_name.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            BcmRouter.getInstance().get(ARouterConstants.Activity.PROFILE_EDIT).putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, mRecipient.address).navigation(this@ChatUserPageActivity)
        }
        chat_user_img.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            BcmRouter.getInstance().get(ARouterConstants.Activity.PROFILE_EDIT).putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, mRecipient.address).navigation(this@ChatUserPageActivity)
        }
        chat_user_img.setCallback(object : IndividualAvatarView.RecipientPhotoCallback {
            override fun onLoaded(recipient: Recipient?, bitmap: Bitmap?, success: Boolean) {
                if (success && bitmap != null) {
                    chat_user_top_bg.setGradientBackground(bitmap) {
                        isBgLight = it
                        if (it) {
                            window.setStatusBarLightMode()
                            chat_user_name.setTextColor(getColorCompat(R.color.common_color_black))
                            chat_user_name.setDrawableRight(R.drawable.common_right_arrow_black_icon)
                        } else {
                            chat_user_title_bar.setLeftIcon(R.drawable.common_back_arrow_white_icon)
                            chat_user_title_bar.setRightTextColor(getColorCompat(R.color.common_color_white))
                            chat_user_name.setTextColor(getColorCompat(R.color.common_color_white))
                            chat_user_name.setDrawableRight(R.drawable.common_right_arrow_white_icon)
                        }
                    }
                }
            }
        })

        chat_user_mute.setSwitchEnable(false)
        chat_user_mute.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val isMuted = !mRecipient.isMuted
            if (mRecipient.isMuted != isMuted) {
                Observable.create(ObservableOnSubscribe<Boolean> {
                    try {
                        if (isMuted) {
                            Repository.getRecipientRepo()?.setMuted(mRecipient, System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3650))
                        } else {
                            Repository.getRecipientRepo()?.setMuted(mRecipient, 0)
                        }
                        it.onNext(isMuted)

                    } catch (ex: Exception) {
                        it.onError(ex)
                    } finally {
                        it.onComplete()
                    }
                }).subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            if (it) {
                                chat_user_mute.setSwitchStatus(isMuted)
                            }
                        }, {
                            AmePopup.result.failure(this, getString(R.string.chats_user_mute_canceled_text))
                        })
            }
        }

        chats_media_browser.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            MediaBrowserActivity.router(mRecipient.address)
        }

        chat_user_stick.setSwitchEnable(false)
        chat_user_stick.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val isPinned = !chat_user_stick.getSwitchStatus()
            ConversationUtils.setPin(mRecipient, isPinned) {
                if (it) {
                    chat_user_stick.setSwitchStatus(isPinned)
                }
            }
        }

        chat_user_clear.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            AmePopup.bottom.newBuilder()
                    .withTitle(getString(R.string.chats_user_clear_history_title, mRecipient.name))
                    .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.chats_clear), getColorCompat(R.color.common_color_ff3737)) {
                        clearChatHistory()
                    })
                    .withDoneTitle(getString(R.string.common_cancel))
                    .show(this)
        }

        chat_user_block.setSwitchEnable(false)
        chat_user_block.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            blockChatUser(mRecipient)
        }

        chat_user_delete.setOnClickListener { _ ->
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val bothSide = mRecipient.isFriend
            val title = if (bothSide) {
                getString(R.string.chats_delete_friend_second_confirm_title, mRecipient.name)
            }else {
                getString(R.string.chats_delete_from_contact_confirm_title, mRecipient.name)
            }
            val item = if (bothSide) {
                getString(R.string.chats_user_delete_and_block_text)
            }else {
                getString(R.string.chats_user_delete_from_contact_text)
            }

            AmePopup.bottom.newBuilder()
                    .withTitle(title)
                    .withPopItem(AmeBottomPopup.PopupItem(item, getColorCompat(R.color.common_content_warning_color)) {
                        mGoingFinish = true
                            val provider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_CONTACTS_BASE).navigationWithCast<IContactModule>()
                            provider.deleteFriend(mRecipient.address.serialize()) {
                                if (it) {
                                    ConversationUtils.deleteConversation(mRecipient, threadId) {
                                        AmePopup.result.succeed(this, getString(R.string.chats_user_delete_success)) {
                                            AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)?.gotoHome(HomeTopEvent(true))
                                        }
                                    }

                                } else {
                                    mGoingFinish = false
                                    AmePopup.result.failure(this, getString(R.string.chats_user_delete_fail))
                                }
                            }
                    })
                    .withCancelable(true)
                    .withDoneTitle(getString(R.string.common_cancel))
                    .show(this)

        }
    }

    private fun initTitleBar() {
        chat_user_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                if (mRecipient.isFriend) {

                } else {
                    BcmRouter.getInstance().get(ARouterConstants.Activity.REQUEST_FRIEND).putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, mRecipient.address).navigation(this@ChatUserPageActivity)
                }
            }
        })

        val scrollHeight = 160.dp2Px()
        chat_user_title_bar.setTitleBarAlpha(0f)
        chat_user_main_layout.setOnScrollChangeListener { _: NestedScrollView?, _, scrollY, _, _ ->
            val alpha = if (scrollY >= scrollHeight) 1f else scrollY / scrollHeight.toFloat()
            chat_user_title_bar.setTitleBarAlpha(alpha)

            if (alpha >= 0.5f && !lightMode) {
                lightMode = true
                window.setStatusBarLightMode()
                chat_user_title_bar.setLeftIcon(R.drawable.common_back_arrow_black_icon)
                chat_user_title_bar.setRightTextColor(getColorCompat(R.color.common_color_black))
            } else if (alpha < 0.5f && lightMode) {
                lightMode = false
                if (isBgLight) {
                    window.setStatusBarLightMode()
                } else {
                    window.setStatusBarDarkMode()
                    chat_user_title_bar.setLeftIcon(R.drawable.common_back_arrow_white_icon)
                    chat_user_title_bar.setRightTextColor(getColorCompat(R.color.common_color_white))
                }
            }
        }
    }

    private fun initData() {
        val address = intent.getParcelableExtra<Address>(ARouterConstants.PARAM.PARAM_ADDRESS)
        threadId = intent.getLongExtra(ARouterConstants.PARAM.PARAM_THREAD, -1)
        mRecipient = Recipient.from(this, address, true)
        mRecipient.addListener(this)

        ConversationUtils.checkPin(threadId) {
            mCurrentPinStatus = it
            chat_user_stick.setSwitchStatus(it)
            initUserPage(mRecipient)
        }

    }

    private fun initUserPage(recipient: Recipient) {

        chat_user_main_layout.visibility = View.VISIBLE
        markMute(recipient.mutedUntil)

        chat_user_img.setPhoto(recipient)
        chat_user_name.text = recipient.name
        if (isBgLight) {
            chat_user_name.setDrawableRight(R.drawable.common_right_arrow_black_icon)
        }else {
            chat_user_name.setDrawableRight(R.drawable.common_right_arrow_white_icon)
        }
        chat_user_title_bar.setCenterText(recipient.name)

        if (!mGoingFinish) {
            if (recipient.isBlocked) {
                chat_user_block.setSwitchStatus(true)
            } else {
                chat_user_block.setSwitchStatus(false)
            }
        }

        if (recipient.isFriend) {
            chat_user_delete.setName(getString(R.string.chats_user_delete_text))
            chat_user_delete.visibility = View.VISIBLE
            chat_user_title_bar.setRightText("")

        }
        else if (recipient.relationship == RecipientRepo.Relationship.FOLLOW || recipient.relationship == RecipientRepo.Relationship.FOLLOW_REQUEST || recipient.relationship == RecipientRepo.Relationship.BREAK) {
            chat_user_delete.setName(getString(R.string.chats_user_delete_from_contact_text))
            chat_user_delete.visibility = View.VISIBLE
            chat_user_title_bar.setRightIcon(R.drawable.chats_add_friend_icon)
        }
        else {
            chat_user_delete.visibility = View.GONE
            chat_user_title_bar.setRightIcon(R.drawable.chats_add_friend_icon)
        }

        if (recipient.isSelf) {
            chat_user_mute.visibility = View.GONE
            chat_user_block.visibility = View.GONE
            chat_user_delete.visibility = View.GONE
        }
    }

    private fun markMute(muteUntil: Long) {
        if (muteUntil - System.currentTimeMillis() > 0) {
            chat_user_mute.setSwitchStatus(true)
        } else {
            chat_user_mute.setSwitchStatus(false)
        }
    }

    private fun clearChatHistory() {
        if (historyClearing) {
            Toast.makeText(this@ChatUserPageActivity, getString(R.string.chats_user_clearing_notice), Toast.LENGTH_SHORT).show()
            return
        }
        historyClearing = true
        chat_user_clear.showRightStatus(CommonSettingItem.RIGHT_LOADING)

        Observable.create(ObservableOnSubscribe<Boolean> {
            try {
                // Delete chat history
                Repository.getChatRepo().cleanChatMessages(threadId)
                it.onNext(true)
            } catch (ex: Exception) {
                it.onError(ex)
            } finally {
                it.onComplete()
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnTerminate {
                    chat_user_clear.showRightStatus(CommonSettingItem.RIGHT_NONE)
                }
                .subscribe({
                    historyClearing = false
                    AmePopup.result.succeed(this, getString(R.string.chats_user_clear_success))

                }, {
                    historyClearing = false
                    AmePopup.result.failure(this, getString(R.string.chats_user_clear_fail))
                })
    }

    private fun blockChatUser(recipient: Recipient) {
        fun notify(success: Boolean) {
            blockHandling = false
            if (success) {
                if (recipient.isBlocked) {
                    AmePopup.result.succeed(this, getString(R.string.chats_user_block_success))
                } else {
                    AmePopup.result.succeed(this, getString(R.string.chats_user_unblock_success))
                }
            } else {
                if (!recipient.isBlocked) {
                    AmePopup.result.failure(this, getString(R.string.chats_user_block_fail))
                } else {
                    AmePopup.result.failure(this, getString(R.string.chats_user_unblock_fail))
                }
            }
        }

        if (blockHandling) {
            ToastUtil.show(this@ChatUserPageActivity, getString(R.string.chats_user_blocking_notice))
            return
        }

        if (recipient.isBlocked) {
            blockHandling = true
            contactModule?.blockContact(recipient.address, false) {
                notify(it)
            }
        } else {
            AmePopup.bottom.newBuilder()
                    .withTitle(getString(R.string.chats_block_confirm_title, recipient.name))
                    .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.chats_block_confirm_action), getColorCompat(R.color.common_color_ff3737)) {
                        blockHandling = true
                        contactModule?.blockContact(recipient.address, true) {
                            notify(it)
                        }
                    })
                    .withDoneTitle(getString(R.string.common_cancel))
                    .show(this)
        }
    }

    override fun onModified(recipient: Recipient) {
        if (isDestroyed || isFinishing) {
            return
        }
        chat_user_top_bg.post {
            if(mRecipient == recipient) {
                initUserPage(recipient)
            }
        }
    }

}