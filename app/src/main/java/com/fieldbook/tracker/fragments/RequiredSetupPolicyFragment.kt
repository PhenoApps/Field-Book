package com.fieldbook.tracker.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.RequiredSetupAdapter
import com.fieldbook.tracker.adapters.RequiredSetupAdapter.RequiredSetupModel
import com.github.appintro.SlidePolicy


class RequiredSetupPolicyFragment : Fragment(), SlidePolicy {

    private var recyclerView: RecyclerView? = null
    private var setupItems: List<RequiredSetupModel>? = null
    private var slideTitle: String? = null
    private var slideSummary: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.app_intro_setup_slide, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val slideTitle = view.findViewById<TextView>(R.id.slide_title)
        val slideSummary = view.findViewById<TextView>(R.id.slide_summary)

        slideTitle?.text = this.slideTitle
        slideSummary?.text = this.slideSummary


        recyclerView = view.findViewById(R.id.setup_rv)
        recyclerView?.adapter = RequiredSetupAdapter()

        setupItems?.let {
            (recyclerView?.adapter as? RequiredSetupAdapter)?.submitList(it)
        }
    }

    private fun validateItems(): Boolean {
        setupItems?.forEach { item ->
            if (!item.isSet()) {
                return false
            }
        }
        return true
    }

    private fun getFirstInvalidItem(): Int {
        setupItems?.forEachIndexed { index, item ->
            if (!item.isSet()) {
                return index
            }
        }
        return -1
    }

    override val isPolicyRespected: Boolean
        get() = validateItems()

    override fun onUserIllegallyRequestedNextPage() {
        try {
            Toast.makeText(
                requireContext().applicationContext,
                setupItems?.get(getFirstInvalidItem())?.invalidateMessage,
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        fun newInstance(
            setupItems: ArrayList<RequiredSetupModel>,
            slideTitle: String,
            slideSummary: String
        ): RequiredSetupPolicyFragment {
            val fragment = RequiredSetupPolicyFragment()
            fragment.setupItems = setupItems
            fragment.slideTitle = slideTitle
            fragment.slideSummary = slideSummary
            return fragment
        }

    }
}
