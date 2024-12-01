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
import com.fieldbook.tracker.adapters.OptionalSetupAdapter
import com.fieldbook.tracker.adapters.OptionalSetupAdapter.OptionalSetupModel
import com.github.appintro.SlidePolicy


class OptionalSetupPolicyFragment : Fragment() {

    private var recyclerView: RecyclerView? = null
    private var radioButtonItems: List<OptionalSetupModel>? = null
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

        slideTitle.text = this.slideTitle
        slideSummary.text = this.slideSummary


        recyclerView = view.findViewById(R.id.setup_rv)
        recyclerView?.adapter = OptionalSetupAdapter()

        radioButtonItems?.let {
            (recyclerView?.adapter as? OptionalSetupAdapter)?.submitList(it)
        }
    }

    companion object {
        fun newInstance(
            radioButtonItems: ArrayList<OptionalSetupModel>,
            slideTitle: String,
            slideSummary: String
        ): OptionalSetupPolicyFragment {
            val fragment = OptionalSetupPolicyFragment()
            fragment.radioButtonItems = radioButtonItems
            fragment.slideTitle = slideTitle
            fragment.slideSummary = slideSummary
            return fragment
        }
    }
}
