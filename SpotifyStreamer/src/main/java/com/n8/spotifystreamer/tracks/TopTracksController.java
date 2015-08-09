package com.n8.spotifystreamer.tracks;

import android.support.v7.widget.RecyclerView;
import com.n8.n8droid.viewcontroller.ViewController;

public interface TopTracksController extends ViewController {

  void onPlayAllClicked();

  void onShareClicked();

  void onNavIconClicked();

  void onNowPlayingMenuOptionClicked();

  void onSettingsMenuOptionClicked();

  RecyclerView.LayoutManager getLayoutManager();

  String getArtistName();
}
