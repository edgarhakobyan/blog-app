package com.example.edgar.blog_app.activities;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.MenuInflater;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.edgar.blog_app.R;
import com.example.edgar.blog_app.adapters.PostAdapter;
import com.example.edgar.blog_app.constants.Constants;
import com.example.edgar.blog_app.models.Post;
import com.example.edgar.blog_app.models.User;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private RecyclerView mPostListView;

    private List<Post> postList;
    private List<User> userList;

    private PostAdapter mPostAdapter;

    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirebaseFirestore;

    private DocumentSnapshot lastVisible;

    private Boolean isFirstPageFirstLoad = true;

    private static boolean isSearchedListShown = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        postList = new ArrayList<>();
        userList = new ArrayList<>();

        mAuth = FirebaseAuth.getInstance();
        mFirebaseFirestore = FirebaseFirestore.getInstance();

        FloatingActionButton addPostBtn = findViewById(R.id.fab);
        addPostBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
               Intent intent = new Intent(MainActivity.this, PostActivity.class);
               startActivity(intent);
            }
        });

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close){

            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                changeNavigationHeader();
            }

        };
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        if (mAuth.getCurrentUser() != null) {
            initRecyclerView();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            sendToLogin();
        } else {
            String currentUserId = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
            mFirebaseFirestore.collection(Constants.USERS).document(currentUserId).get()
                    .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                    if (task.isSuccessful()) {
                        if (!task.getResult().exists()) {
                            Intent intent = new Intent(MainActivity.this, SetupActivity.class);
                            startActivity(intent);
                            finish();
                        }
                    } else {
                        String error = Objects.requireNonNull(task.getException()).getMessage();
                        Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (isSearchedListShown) {
            isFirstPageFirstLoad = true;
            initRecyclerView();
            isSearchedListShown = false;
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) searchItem.getActionView();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

            @Override
            public boolean onQueryTextSubmit(String query) {
                Toast.makeText(MainActivity.this, query, Toast.LENGTH_LONG).show();
                searchView.clearFocus();
                showSearchedPosts(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }

        });

        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                isFirstPageFirstLoad = true;
                initRecyclerView();
                return false;
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_voice_search) {

            PopupMenu popup = new PopupMenu(this, findViewById(R.id.action_voice_search));
            MenuInflater inflater = popup.getMenuInflater();
            inflater.inflate(R.menu.search_languages, popup.getMenu());
            popup.setOnMenuItemClickListener(new SearchLanguageClickListener());
            popup.show();

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        switch (id) {
            case R.id.nav_home:

                break;
            case R.id.nav_notification:
                sendToFavorite();
                break;
            case R.id.nav_account:
                sendToAccount();
                break;
            case R.id.nav_account_settings:
                sendToSetup();
                break;
            case R.id.nav_logout:
                logOut();
                break;
            default:
                break;
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 200 && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            String searchedText = results.get(0);
            Toast.makeText(MainActivity.this, searchedText, Toast.LENGTH_LONG).show();
            showSearchedPosts(searchedText);
            isSearchedListShown = true;
        }
    }

    private class SearchLanguageClickListener implements PopupMenu.OnMenuItemClickListener {

        SearchLanguageClickListener() {}

        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            switch (menuItem.getItemId()) {

                case R.id.lang_arm:
                    startVoiceSearch(Constants.LANG_ARM);
                    return true;
                case R.id.lang_eng:
                    startVoiceSearch(Constants.LANG_ENG);
                    return true;
                case R.id.lang_rus:
                    startVoiceSearch(Constants.LANG_RUS);
                    return true;

                default:
                    break;
            }

            return false;
        }

    }

    private void startVoiceSearch(String lang) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
//        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "");

        try {
            startActivityForResult(intent,200);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(getApplicationContext(), "Intent problem", Toast.LENGTH_SHORT).show();
        }
    }

    private void initRecyclerView() {

        mPostAdapter = new PostAdapter(postList, userList);

        mPostListView = findViewById(R.id.post_list_view);
        mPostListView.setLayoutManager(new LinearLayoutManager(this));
        mPostListView.setAdapter(mPostAdapter);

        if (mAuth.getCurrentUser() != null) {

            mPostListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);

                    Boolean reachedBottom = !mPostListView.canScrollVertically(1);
                    if (reachedBottom) {
                        loadMorePosts();
                    }
                }
            });

            // Get posts by order timestamp
            Query firstQuery = mFirebaseFirestore.collection(Constants.POSTS)
                    .orderBy(Constants.TIMESTAMP, Query.Direction.DESCENDING)
                    .limit(8);
            firstQuery.addSnapshotListener(this, new EventListener<QuerySnapshot>() {
                @Override
                public void onEvent(QuerySnapshot queryDocumentSnapshots, FirebaseFirestoreException e) {

                    if (queryDocumentSnapshots != null) {
                        if (isFirstPageFirstLoad && queryDocumentSnapshots.size() > 0) {
                            lastVisible = queryDocumentSnapshots.getDocuments().get(queryDocumentSnapshots.size() - 1);
                            postList.clear();
                            userList.clear();
                        }

                        for (DocumentChange doc : queryDocumentSnapshots.getDocumentChanges()) {
                            if (doc.getType() == DocumentChange.Type.ADDED) {

                                String postId = doc.getDocument().getId();
                                final Post post = doc.getDocument().toObject(Post.class).withId(postId);

                                String blogUserId = doc.getDocument().getString(Constants.USER_ID);
                                assert blogUserId != null;
                                mFirebaseFirestore.collection(Constants.USERS).document(blogUserId).get()
                                        .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                    @Override
                                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {

                                        if (task.isSuccessful()) {
                                            User user = task.getResult().toObject(User.class);

                                            if (isFirstPageFirstLoad) {
                                                postList.add(post);
                                                userList.add(user);
                                            } else {
                                                postList.add(0, post);
                                                userList.add(0,user);
                                            }
                                            mPostAdapter.notifyDataSetChanged();
                                        }
                                    }
                                });

                            }
                        }
                        isFirstPageFirstLoad = false;
                    }
                }
            });
        }
    }

    private void loadMorePosts() {
        Query nextQuery = mFirebaseFirestore.collection(Constants.POSTS)
                .orderBy(Constants.TIMESTAMP, Query.Direction.DESCENDING)
                .startAfter(lastVisible)
                .limit(8);

        nextQuery.addSnapshotListener(this, new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(QuerySnapshot queryDocumentSnapshots, FirebaseFirestoreException e) {

                if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                    lastVisible = queryDocumentSnapshots.getDocuments().get(queryDocumentSnapshots.size() - 1);
                    for (DocumentChange doc : queryDocumentSnapshots.getDocumentChanges()) {
                        if (doc.getType() == DocumentChange.Type.ADDED) {

                            String postId = doc.getDocument().getId();
                            final Post post = doc.getDocument().toObject(Post.class).withId(postId);

                            String blogUserId = doc.getDocument().getString(Constants.USER_ID);
                            assert blogUserId != null;
                            mFirebaseFirestore.collection(Constants.USERS).document(blogUserId).get()
                                    .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                        @Override
                                        public void onComplete(@NonNull Task<DocumentSnapshot> task) {

                                            if (task.isSuccessful()) {
                                                User user = task.getResult().toObject(User.class);

                                                postList.add(post);
                                                userList.add(user);

                                                mPostAdapter.notifyDataSetChanged();
                                            }
                                        }
                                    });
                        }
                    }
                }

            }
        });
    }

    private void logOut() {
        mAuth.signOut();
        sendToLogin();
    }

    private void sendToLogin() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    private void sendToSetup() {
        Intent intent = new Intent(MainActivity.this, SetupActivity.class);
        startActivity(intent);
    }

    private void sendToAccount() {
        Intent intent = new Intent(MainActivity.this, AccountActivity.class);
        startActivity(intent);
    }

    private void sendToFavorite() {
        Intent intent = new Intent(MainActivity.this, FavoritePostsActivity.class);
        startActivity(intent);
    }

    private void showSearchedPosts(final String searchingText) {
        final ArrayList<Post> searchedPosts = new ArrayList<>();
        final List<User> searchedUsers = new ArrayList<>();

        int firstSymbol = searchingText.codePointAt(0);
        String searchingTextWithEnglishSymbols = null;
        if (firstSymbol >= 0x0520 && firstSymbol <= 0x0580) {
            searchingTextWithEnglishSymbols = changeStringFromArmenianToEnglish(searchingText);
        }

        mPostAdapter = new PostAdapter(searchedPosts, searchedUsers);

        mPostListView = findViewById(R.id.post_list_view);
        mPostListView.setLayoutManager(new LinearLayoutManager(this));
        mPostListView.setAdapter(mPostAdapter);

        final String finalSearchingTextWithEnglishSymbols = searchingTextWithEnglishSymbols;

        mFirebaseFirestore.collection(Constants.POSTS).addSnapshotListener(this, new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(QuerySnapshot queryDocumentSnapshots, FirebaseFirestoreException e) {
                for (DocumentChange doc: queryDocumentSnapshots.getDocumentChanges()) {
                    if (doc.getType() == DocumentChange.Type.ADDED) {

                        String postId = doc.getDocument().getId();
                        final Post post = doc.getDocument().toObject(Post.class).withId(postId);

                        String blogUserId = doc.getDocument().getString(Constants.USER_ID);
                        assert blogUserId != null;
                        mFirebaseFirestore.collection(Constants.USERS).document(blogUserId).get()
                                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                    @Override
                                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {

                                        if (task.isSuccessful()) {
                                            User user = task.getResult().toObject(User.class);

                                            String postDesc = post.getDescription().toLowerCase();

                                            if (postDesc.contains(searchingText.toLowerCase())) {
                                                searchedPosts.add(post);
                                                searchedUsers.add(user);
                                            } else if (finalSearchingTextWithEnglishSymbols != null
                                                    && postDesc.contains(finalSearchingTextWithEnglishSymbols.toLowerCase())) {
                                                searchedPosts.add(post);
                                                searchedUsers.add(user);
                                            }

                                            mPostAdapter.notifyDataSetChanged();
                                        }
                                    }
                                });
                    }
                }
            }
        });

    }

    private void changeNavigationHeader() {

        final CircleImageView userHeaderImage = findViewById(R.id.user_header_image);
        final TextView userHeaderName = findViewById(R.id.user_header_name);
        TextView userHeaderEmail = findViewById(R.id.user_header_email);

        String currentUserId = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
        mFirebaseFirestore.collection(Constants.USERS).document(currentUserId).get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @SuppressLint("CheckResult")
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            String userName = task.getResult().getString(Constants.NAME);
                            String userImage = task.getResult().getString(Constants.IMAGE);

                            userHeaderName.setText(userName);

                            RequestOptions placeholderOption = new RequestOptions();
                            placeholderOption.placeholder(R.drawable.profile_placeholder);

                            Glide.with(MainActivity.this).applyDefaultRequestOptions(placeholderOption).load(userImage).into(userHeaderImage);
                        }
                    }
                });
        String email = mAuth.getCurrentUser().getEmail();
        userHeaderEmail.setText(email);
    }

    private String changeStringFromArmenianToEnglish(String word) {

        return word.replace("ա", "a").replace("բ", "b")
                .replace("գ", "g").replace("դ", "d").replace("ե", "e")
                .replace("զ", "z").replace("է", "e").replace("ը", "@")
                .replace("թ", "t").replace("ժ", "zh").replace("ի", "i")
                .replace("լ", "l").replace("խ", "x").replace("ծ", "c")
                .replace("կ", "k").replace("հ", "h").replace("ձ", "c")
                .replace("ղ", "x").replace("ճ", "ch").replace("մ", "m")
                .replace("յ", "y").replace("ն", "n").replace("շ", "sh")
                .replace("ո", "o").replace("չ", "ch").replace("պ", "p")
                .replace("ջ", "j").replace("ռ", "r").replace("ս", "s")
                .replace("վ", "v").replace("տ", "t").replace("ր", "r")
                .replace("ց", "c").replace("ու", "u").replace("փ", "p")
                .replace("ք", "q").replace("և", "ev").replace("օ", "o")
                .replace("ֆ", "f");
    }

}
