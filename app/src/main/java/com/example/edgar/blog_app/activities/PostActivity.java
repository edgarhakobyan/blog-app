package com.example.edgar.blog_app.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.edgar.blog_app.constants.Constants;
import com.example.edgar.blog_app.R;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import id.zelory.compressor.Compressor;

public class PostActivity extends AppCompatActivity {

    private ImageView postImage;
    private EditText postDescription;
    private Button addPostBtn;
    private ProgressBar postProgress;

    private Uri postImageUri = null;
    private String currentUserId;

    private StorageReference storageReference;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firebaseFirestore;

    private Bitmap compressedImageFile;

    @SuppressLint("CheckResult")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post);

        postImage = findViewById(R.id.new_post_image);
        postDescription = findViewById(R.id.new_post_description);
        addPostBtn = findViewById(R.id.add_new_post_btn);
        postProgress = findViewById(R.id.new_post_progressbar);

        // Get Firebase data
        storageReference = FirebaseStorage.getInstance().getReference();
        firebaseFirestore = FirebaseFirestore.getInstance();
        firebaseAuth = FirebaseAuth.getInstance();

        currentUserId = firebaseAuth.getCurrentUser().getUid();

        final String postId = getIntent().getStringExtra(Constants.POST_ID);

        if (null != postId) {
            String desc = getIntent().getStringExtra(Constants.DESC);
            String currentImage = getIntent().getStringExtra(Constants.IMAGE_URL);

            postDescription.setText(desc);
            addPostBtn.setText(R.string.save_post);

            RequestOptions placeholderRequest = new RequestOptions();
            placeholderRequest.placeholder(R.drawable.post_placeholder);
            Glide.with(this).setDefaultRequestOptions(placeholderRequest).load(currentImage).into(postImage);

        }

        postImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CropImage.activity()
                        .setGuidelines(CropImageView.Guidelines.ON)
                        .setMinCropResultSize(512,512)
                        .setAspectRatio(1,1)
                        .start(PostActivity.this);
            }
        });

        addPostBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String desc = postDescription.getText().toString();

                if (!TextUtils.isEmpty(desc) && postImageUri != null) {

                    postProgress.setVisibility(View.VISIBLE);

                    final String randomName = UUID.randomUUID().toString();

                    // PHOTO UPLOAD
                    File newImageFile = new File(postImageUri.getPath());
                    try {
                        compressedImageFile = new Compressor(PostActivity.this)
                                .setMaxHeight(360)
                                .setMaxWidth(360)
                                .setQuality(25)
                                .compressToBitmap(newImageFile);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    compressedImageFile.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                    byte[] imageData = baos.toByteArray();

                    UploadTask uploadTask = storageReference.child(Constants.POST_IMAGES).child(randomName + ".jpg")
                            .putBytes(imageData);

                    uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            String downloadUri = taskSnapshot.getDownloadUrl().toString();

                            Map<String, Object> postMap = new HashMap<>();
                            postMap.put("imageUrl", downloadUri);
                            postMap.put("description", desc);
                            postMap.put("userId", currentUserId);
                            postMap.put("timestamp", FieldValue.serverTimestamp());

                            if (postId != null) {
                                firebaseFirestore.collection(Constants.POSTS).document(postId).update(postMap).addOnCompleteListener(new OnCompleteListener<Void>() {
                                    @Override
                                    public void onComplete(@NonNull Task<Void> task) {
                                        if (task.isSuccessful()) {
                                            Toast.makeText(PostActivity.this, "Post was updated ", Toast.LENGTH_LONG).show();
                                            Intent intent = new Intent(PostActivity.this, MainActivity.class);
                                            startActivity(intent);
                                            finish();
                                        }
                                        postProgress.setVisibility(View.INVISIBLE);
                                    }
                                });
                            } else {
                                firebaseFirestore.collection(Constants.POSTS).add(postMap).addOnCompleteListener(new OnCompleteListener<DocumentReference>() {
                                    @Override
                                    public void onComplete(@NonNull Task<DocumentReference> task) {
                                        if (task.isSuccessful()) {
                                            Toast.makeText(PostActivity.this, "Post was added ", Toast.LENGTH_LONG).show();
                                            Intent intent = new Intent(PostActivity.this, MainActivity.class);
                                            startActivity(intent);
                                            finish();
                                        }
                                        postProgress.setVisibility(View.INVISIBLE);
                                    }
                                });
                            }
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            //Error handling
                        }
                    });


                } else {
                    postProgress.setVisibility(View.INVISIBLE);
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == RESULT_OK) {
                postImageUri = result.getUri();
                postImage.setImageURI(postImageUri);
            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                Exception error = result.getError();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

}