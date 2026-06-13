package com.example.zyncwave2.presentation

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class DownloadFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = View(requireContext())

    override fun onResume() {
        super.onResume()

        startActivity(Intent(requireContext(), DownloadActivity::class.java))
        requireActivity().let { activity ->
            val viewPager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(
                com.example.zyncwave2.R.id.viewPager
            )
            viewPager?.setCurrentItem(viewPager.currentItem - 1, true)
        }
    }
}