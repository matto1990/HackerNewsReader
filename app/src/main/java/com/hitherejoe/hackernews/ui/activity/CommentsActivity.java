package com.hitherejoe.hackernews.ui.activity;

import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hitherejoe.hackernews.HackerNewsApplication;
import com.hitherejoe.hackernews.R;
import com.hitherejoe.hackernews.data.DataManager;
import com.hitherejoe.hackernews.data.model.Comment;
import com.hitherejoe.hackernews.data.model.Post;
import com.hitherejoe.hackernews.ui.adapter.CommentAdapter;
import com.hitherejoe.hackernews.util.DataUtils;
import com.hitherejoe.hackernews.util.ToastFactory;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.android.app.AppObservable;

public class CommentsActivity extends BaseActivity {

    @InjectView(R.id.progress_indicator)
    LinearLayout mProgressBar;

    @InjectView(R.id.layout_offline)
    LinearLayout mOfflineLayout;

    @InjectView(R.id.recycler_comments)
    RecyclerView mCommentsRecycler;

    @InjectView(R.id.text_no_comments)
    TextView mNoCommentsText;

    private static final String TAG = "CommentsActivity";
    public static final String EXTRA_POST =
            "com.hitherejoe.HackerNews.ui.activity.CommentsActivity.EXTRA_POST";

    private Post mPost;
    private DataManager mDataManager;
    private List<Subscription> mSubscriptions;
    private CommentAdapter mCommentsAdapter;
    private ArrayList<Comment> mComments;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comments);
        ButterKnife.inject(this);
        mSubscriptions = new ArrayList<>();
        mComments = new ArrayList<>();
        mPost = getIntent().getParcelableExtra(EXTRA_POST);
        mDataManager = HackerNewsApplication.get().getDataManager();
        setupActionbar();
        setupRecyclerView();
        loadStoriesIfNetworkConnected();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (Subscription subscription : mSubscriptions) subscription.unsubscribe();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.comments, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_bookmark:
                addBookmark();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @OnClick(R.id.button_try_again)
    public void onTryAgainClick() {
        loadStoriesIfNetworkConnected();
    }

    private void setupActionbar() {
        String title = mPost.title;
        if (title != null) getSupportActionBar().setTitle(title);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private void setupRecyclerView() {
        mCommentsRecycler.setLayoutManager(new LinearLayoutManager(this));
        mCommentsAdapter = new CommentAdapter(mPost, mComments);
        mCommentsRecycler.setAdapter(mCommentsAdapter);
    }

    private void loadStoriesIfNetworkConnected() {
        if (DataUtils.isNetworkAvailable(this)) {
            showHideOfflineLayout(false);
            getStoryComments(mPost.kids);
        } else {
            showHideOfflineLayout(true);
        }
    }

    private void getStoryComments(List<Long> commentIds) {
        if (commentIds != null && !commentIds.isEmpty()) {
            mSubscriptions.add(AppObservable.bindActivity(this,
                    mDataManager.getPostComments(commentIds, 0))
                    .subscribeOn(mDataManager.getScheduler())
                    .subscribe(new Subscriber<Comment>() {
                        @Override
                        public void onCompleted() {
                            mProgressBar.setVisibility(View.GONE);
                        }

                        @Override
                        public void onError(Throwable e) {
                            mProgressBar.setVisibility(View.GONE);
                            Log.e(TAG, "There was an error retrieving the comments " + e);
                        }

                        @Override
                        public void onNext(Comment comment) {
                            addCommentViews(comment);
                        }
                    }));
        } else {
            mProgressBar.setVisibility(View.GONE);
            mCommentsRecycler.setVisibility(View.GONE);
            mNoCommentsText.setVisibility(View.VISIBLE);
        }
    }

    private void addCommentViews(Comment comment) {
        mComments.add(comment);
        mComments.addAll(comment.comments);
        mCommentsAdapter.notifyDataSetChanged();
    }

    private void addBookmark() {
        mSubscriptions.add(AppObservable.bindActivity(this,
                mDataManager.addBookmark(mPost))
                .subscribeOn(mDataManager.getScheduler())
                .subscribe(new Observer<Post>() {

                    private Post bookmarkResult;

                    @Override
                    public void onCompleted() {
                        ToastFactory.createToast(
                                CommentsActivity.this,
                                bookmarkResult == null ? getString(R.string.bookmark_exists) : getString(R.string.bookmark_added)
                        ).show();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, "There was an error bookmarking the story " + e);
                        ToastFactory.createToast(
                                CommentsActivity.this,
                                getString(R.string.bookmark_error)
                        ).show();
                    }

                    @Override
                    public void onNext(Post story) {
                        bookmarkResult = story;
                    }
                }));
    }

    private void showHideOfflineLayout(boolean isOffline) {
        mOfflineLayout.setVisibility(isOffline ? View.VISIBLE : View.GONE);
        mCommentsRecycler.setVisibility(isOffline ? View.GONE : View.VISIBLE);
        mProgressBar.setVisibility(isOffline ? View.GONE : View.VISIBLE);
    }
}
