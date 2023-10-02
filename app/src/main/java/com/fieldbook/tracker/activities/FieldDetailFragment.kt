package com.fieldbook.tracker.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FieldDetailFragment : Fragment() {

    @Inject
    lateinit var database: DataHelper
    private var toolbar: Toolbar? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_field_detail, container, false)

        toolbar = view.findViewById(R.id.toolbar)

        toolbar?.setTitle(arguments?.getString("FIELD_NAME"))

        toolbar?.setNavigationIcon(R.drawable.arrow_left)

        toolbar?.setNavigationOnClickListener {

            parentFragmentManager.popBackStack()
        }

        return view

    }

}
