package com.codingblocks.cbonlineapp.auth.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.codingblocks.cbonlineapp.R
import com.codingblocks.cbonlineapp.util.PreferenceHelper
import com.codingblocks.cbonlineapp.util.extensions.replaceFragmentSafely
import com.codingblocks.cbonlineapp.util.extensions.showSnackbar
import com.codingblocks.onlineapi.Clients
import com.codingblocks.onlineapi.ResultWrapper
import com.codingblocks.onlineapi.safeApiCall
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_sign_up.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.anko.support.v4.runOnUiThread
import org.json.JSONObject
import org.koin.android.ext.android.inject

class SignUpFragment : Fragment() {

    var map = HashMap<String, String>()
    private val sharedPrefs by inject<PreferenceHelper>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?):
        View? = inflater.inflate(R.layout.fragment_sign_up, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        backBtn.setOnClickListener {
            requireActivity().onBackPressed()
        }
        proceedBtn.setOnClickListener {
            val name = nameLayout.editText?.text.toString().split(" ")
            val number = if (mobileLayout.editText?.text?.length!! > 10) "+91-${mobileLayout.editText?.text?.substring(3)}" else "+91-${mobileLayout.editText?.text}"

            if (name.size < 2) {
                signUpRoot.showSnackbar("Last Name Cannot Be Empty", Snackbar.LENGTH_SHORT)
            } else {
                map["username"] = userNameLayout.editText?.text.toString()
                map["mobile"] = number
                map["firstname"] = name[0]
                map["lastname"] = name[1]
                map["email"] = emailLayout.editText?.text.toString()
            }

            proceedBtn.isEnabled = false

            GlobalScope.launch {
                when (val response = safeApiCall { Clients.api.createUser(map) }) {
                    is ResultWrapper.GenericError -> {
                        runOnUiThread {
                            proceedBtn.isEnabled = true
                            signUpRoot.showSnackbar(response.error, Snackbar.LENGTH_SHORT)
                        }
                    }
                    is ResultWrapper.Success -> {
                        if (response.value.isSuccessful) {
                            response.value.body()?.let {
                                sendOtp(it["oneauth_id"].asString)
                            }
                        } else
                            runOnUiThread {
                                val errRes = response.value.errorBody()?.string()
                                val error = if (errRes.isNullOrEmpty()) "Please Try Again" else JSONObject(errRes).getString("description")
                                signUpRoot.showSnackbar(error.capitalize(), Snackbar.LENGTH_SHORT)
                                proceedBtn.isEnabled = true
                            }
                    }
                }
            }
        }
    }

    private fun sendOtp(id: String) {
        val otpMap = HashMap<String, String>()
        otpMap["oneauth_id"] = id
        GlobalScope.launch {
            when (val response = safeApiCall { Clients.api.getOtp(otpMap) }) {
                is ResultWrapper.GenericError -> {
                    runOnUiThread {
                        proceedBtn.isEnabled = true
                        signUpRoot.showSnackbar(response.error, Snackbar.LENGTH_SHORT)
                    }
                }
                is ResultWrapper.Success -> {
                    if (response.value.isSuccessful)
                        replaceFragmentSafely(LoginOtpFragment.newInstance(map["mobile"]
                            ?: "", id), containerViewId = R.id.loginContainer)
                    else
                        runOnUiThread {
                            signUpRoot.showSnackbar("Invalid Number.Please Try Again", Snackbar.LENGTH_SHORT)
                            proceedBtn.isEnabled = true
                        }
                }
            }
        }
    }
}
