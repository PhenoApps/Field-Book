package com.fieldbook.tracker.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.RadioButtonAdapter
import com.fieldbook.tracker.adapters.RadioButtonAdapter.RadioButtonModel
import com.github.appintro.SlidePolicy


class RadioButtonSlidePolicyFragment : Fragment(), SlidePolicy {

    private var recyclerView: RecyclerView? = null
    private var radioButtonItems: List<RadioButtonModel>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.intro_radio_button_slide, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.radioButtonsRecyclerView)
        recyclerView?.adapter = RadioButtonAdapter()

        radioButtonItems?.let {
            (recyclerView?.adapter as? RadioButtonAdapter)?.submitList(it)
        }
    }

    override val isPolicyRespected: Boolean
        get() = (recyclerView?.adapter as? RadioButtonAdapter)?.getSelectedPosition() != -1

    override fun onUserIllegallyRequestedNextPage() {
        Toast.makeText(
            requireContext(),
            R.string.intro_tutorial_warning,
            Toast.LENGTH_SHORT
        ).show()
    }

    companion object {
        fun newInstance(radioButtonItems: ArrayList<RadioButtonModel>): RadioButtonSlidePolicyFragment {
            val fragment = RadioButtonSlidePolicyFragment()
            fragment.radioButtonItems = radioButtonItems
            return fragment
        }
    }
}
