package eu.kanade.tachiyomi.widget.preference

import android.app.Dialog
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.View
import com.afollestad.materialdialogs.MaterialDialog
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.dd.processbutton.iml.ActionProcessButton
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.manga.info.MangaWebViewController
import eu.kanade.tachiyomi.widget.SimpleTextWatcher
import kotlinx.android.synthetic.main.pref_account_login.view.*
import rx.Subscription
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

abstract class LoginDialogPreference(bundle: Bundle? = null) : DialogController(bundle) {

    var v: View? = null
        private set

    val preferences: PreferencesHelper by injectLazy()

    var requestSubscription: Subscription? = null

    override fun onCreateDialog(savedState: Bundle?): Dialog {
        val dialog = MaterialDialog.Builder(activity!!)
                .customView(R.layout.pref_account_login, false)
                .negativeText(android.R.string.cancel)
                .build()

        onViewCreated(dialog.view)

        return dialog
    }

    fun onViewCreated(view: View) {
        val source = Injekt.get<SourceManager>().get(args.getLong("key"))

        v = view.apply {
            show_password.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked)
                    password.transformationMethod = null
                else
                    password.transformationMethod = PasswordTransformationMethod()
            }

            login.setMode(ActionProcessButton.Mode.ENDLESS)
            login.setOnClickListener { checkLogin() }
            if(source != null && source is HttpSource){
                login_captcha.setOnClickListener {
                    router.pushController(MangaWebViewController(source.id, source.baseUrl)
                            .withFadeTransaction())
                }
            } else{
                login_captcha.visibility = View.GONE
                two_factor_static.visibility = View.GONE
                two_factor_edit.visibility = View.GONE
            }


            setCredentialsOnView(this)

            show_password.isEnabled = password.text.isNullOrEmpty()

            password.addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    if (s.isEmpty()) {
                        show_password.isEnabled = true
                    }
                }
            })
        }

    }

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeStarted(handler, type)
        if (!type.isEnter) {
            onDialogClosed()
        }
    }

    open fun onDialogClosed() {
        requestSubscription?.unsubscribe()
    }

    protected abstract fun checkLogin()

    protected abstract fun setCredentialsOnView(view: View)

}
