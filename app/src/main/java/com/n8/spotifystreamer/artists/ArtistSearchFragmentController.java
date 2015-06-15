/*
COPYRIGHT 1995-2015 ESRI

TRADE SECRETS: ESRI PROPRIETARY AND CONFIDENTIAL 
Unpublished material - all rights reserved under the Copyright Laws of the United States.

For additional information, contact: Environmental Systems Research Institute, Inc. 
Attn: Contracts Dept 380 New York Street Redlands, California, USA 92373

email: contracts@esri.com
*/
package com.n8.spotifystreamer.artists;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.provider.SearchRecentSuggestions;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.transition.TransitionInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.n8.spotifystreamer.AndroidUtils;
import com.n8.spotifystreamer.BaseFragmentController;
import com.n8.spotifystreamer.BusProvider;
import com.n8.spotifystreamer.R;
import com.n8.spotifystreamer.SpotifyStreamerApplication;
import com.n8.spotifystreamer.events.ArtistClickedEvent;
import com.n8.spotifystreamer.events.SearchIntentReceivedEvent;
import com.n8.spotifystreamer.tracks.TopTracksFragment;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kaaes.spotify.webapi.android.models.Artist;
import kaaes.spotify.webapi.android.models.ArtistsPager;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * Handles the business logic for {@link ArtistSearchFragment}.  Listens and responds to events from
 * {@link ArtistSearchFragmentView}.
 */
public class ArtistSearchFragmentController extends BaseFragmentController<ArtistSearchFragmentView> implements
    ArtistSearchController, ArtistsRecyclerAdapter.ArtistClickListener {

  private static final int REQUEST_LIMIT = 20;

  public static final String SUGGESTIONS_PREFERENCES_KEY = "suggestions_preferences_key";

  private List<Artist> mArtists;

  private ArtistsRecyclerAdapter mAdapter;

  private String mCurrentQuery;

  private int mCurrentQueryOffset = 0;

  private int mTotalCurrentSearchResults;

  private boolean mPagingNewResults;

  public ArtistSearchFragmentController() {

  }

  @Override
  public void onCreateView(@NonNull ArtistSearchFragmentView view) {
    super.onCreateView(view);
    BusProvider.getInstance().register(this);
    // If view is being recreated after a rotation, there may be existing artist data to view
    if (mArtists != null && mArtists.size() > 0) {
      bindArtists(false);
    }
  }

  @Override
  public void onDetachView() {
    super.onDetachView();
    BusProvider.getInstance().unregister(this);
  }

  @Override
  public LinearLayoutManager getLinearLayoutManager() {
    return new LinearLayoutManager(mActivity);
  }

  @Override
  public void onClearSuggestions() {
    clearSearchSuggestions();
  }

  @Override
  public void onSubmitQuery(String query) {
    submitQuery(query);
  }

  @Override
  public void onRecyclerViewScrolled(RecyclerView recyclerView, int dx, int dy) {
    if (mAdapter.getItemCount() != mTotalCurrentSearchResults) {
      LinearLayoutManager manager = (LinearLayoutManager) mView.getArtistRecyclerView().getLayoutManager();
      if (manager.findLastVisibleItemPosition() == mAdapter.getItemCount() - 1 && !mPagingNewResults) {

        mView.getProgressBar().setVisibility(View.VISIBLE);
        final Handler handler = new Handler();

        mPagingNewResults = true;
        searchForArtists(null, handler);
      }
    }
  }

  @Subscribe
  public void onSearchIntentReceived(SearchIntentReceivedEvent event) {
    submitQuery(event.getQuery());
  }

  private void submitQuery(String query) {
    mCurrentQuery = query;
    mTotalCurrentSearchResults = 0;
    mCurrentQueryOffset = 0;
    mArtists = null;
    mAdapter = null;

    // Leverage provider to populate suggestions adapter
    //
    SearchRecentSuggestions suggestions = new SearchRecentSuggestions(mActivity,
        ArtistSuggestionProvider.AUTHORITY, ArtistSuggestionProvider.MODE);
    suggestions.saveRecentQuery(query, null);

    final ProgressDialog progressDialog = new ProgressDialog(mActivity);
    progressDialog.setTitle(mActivity.getString(R.string.searching_for) + query);
    progressDialog.setCancelable(false);
    progressDialog.setCanceledOnTouchOutside(false);
    progressDialog.show();

    final Handler handler = new Handler();
    searchForArtists(progressDialog, handler);
  }

  private void searchForArtists(final ProgressDialog progressDialog,
      final Handler handler) {

    Map<String, Object> queryMap = new HashMap<>();
    queryMap.put("offset", mCurrentQueryOffset);
    queryMap.put("limit", REQUEST_LIMIT);

    SpotifyStreamerApplication.getSpotifyService().searchArtists(mCurrentQuery, queryMap,
        new Callback<ArtistsPager>() {
          @Override
          public void success(final ArtistsPager artistsPager, Response response) {
            if (progressDialog != null) {
              progressDialog.cancel();
            }

            handler.post(new Runnable() {
              @Override
              public void run() {
                mCurrentQueryOffset += REQUEST_LIMIT;
                mTotalCurrentSearchResults = artistsPager.artists.total;
                if (mPagingNewResults) {
                  mPagingNewResults = false;
                }
                mView.getProgressBar().setVisibility(View.GONE);

                handleQueryResponse(artistsPager);
              }
            });
          }

          @Override
          public void failure(RetrofitError error) {
            handler.post(new Runnable() {
              @Override
              public void run() {
                if (progressDialog != null) {
                  progressDialog.cancel();
                }
                mView.getProgressBar().setVisibility(View.GONE);

                AndroidUtils.showToast(mActivity,
                    mActivity.getString(R.string.error_searching_for_artist));
              }
            });
          }
        });
  }

  private SharedPreferences getSharedPreferences() {
    return mActivity
        .getSharedPreferences(ArtistSearchFragment.class.getSimpleName(), Context.MODE_PRIVATE);
  }

  public void onArtistViewClicked(Artist artist, ImageView sharedImageView) {
    BusProvider.getInstance().post(new ArtistClickedEvent(artist, sharedImageView));
  }

  private void clearSearchSuggestions(){
    SearchRecentSuggestions suggestions = new SearchRecentSuggestions(mActivity, ArtistSuggestionProvider.AUTHORITY,
        ArtistSuggestionProvider.MODE);
    suggestions.clearHistory();

    SharedPreferences sharedPreferences = getSharedPreferences();
    sharedPreferences.edit().remove(SUGGESTIONS_PREFERENCES_KEY).apply();
  }

  private void handleQueryResponse(ArtistsPager artistsPager) {
    if (mArtists == null) {
      mArtists = artistsPager.artists.items;
    } else {
      int startIndex = mArtists.size();
      mArtists.addAll(artistsPager.artists.items);
      mAdapter.notifyItemRangeInserted(startIndex, startIndex + REQUEST_LIMIT);
      return;
    }
    bindArtists(true);
    mView.getSearchView().clearFocus();
  }

  private void bindArtists(boolean bindFromScratch) {
    if (mArtists == null || mArtists.size() == 0) {
      mView.showNoContentView();
      return;
    }

    if (bindFromScratch) {
      mAdapter = new ArtistsRecyclerAdapter(mArtists, this);
    }
    mView.getArtistRecyclerView().setAdapter(mAdapter);
    setupSwipeToDeleteHelper(mView.getArtistRecyclerView());
    mView.showContentView();
  }

  private void setupSwipeToDeleteHelper(ViewGroup view) {
    ArtistRecyclerViewTouchHelperCallback callback = new ArtistRecyclerViewTouchHelperCallback(
        view, mAdapter, mArtists);
    ItemTouchHelper itemTouchHelper = new ItemTouchHelper(callback);
    itemTouchHelper.attachToRecyclerView(mView.getArtistRecyclerView());
  }
}

