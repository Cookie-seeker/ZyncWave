package com.example.zyncwave2.presentation

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount() = 6

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> PlayerFragment()
        1 -> SongsFragment()
        2 -> ListsFragment()
        3 -> ArtistFragment()
        4 -> AlbumFragment()
        5 -> FolderFragment()
        else -> SongsFragment()
    }
}