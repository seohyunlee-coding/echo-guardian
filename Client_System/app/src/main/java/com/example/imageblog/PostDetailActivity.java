package com.example.imageblog;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.View;
import android.util.Log;
import android.widget.Toast; // 추가: Toast import
import android.content.SharedPreferences;
import com.google.android.material.button.MaterialButton;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONObject;

public class PostDetailActivity extends AppCompatActivity {
    private static final String TAG = "PostDetailActivity";
    private int postId = -1; // 게시글 id 저장

    // UI references made fields so fetch method can update them
    private TextView headerTitle;
    private TextView bodyView;
    private TextView dateView;
    private ImageView imageView;
    private TextView labelBody;
    private ActivityResultLauncher<Intent> editLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);

        headerTitle = findViewById(R.id.headerTitle);
        bodyView = findViewById(R.id.detailBody);
        dateView = findViewById(R.id.detailDate);
        imageView = findViewById(R.id.detailImage);
        labelBody = findViewById(R.id.labelBody);
        // 헤더의 사용자명 표시
        TextView tvUsername = findViewById(R.id.headerUserText); // activity_post_detail.xml에서 해당 id로 변경 필요
        MaterialButton btnLogout = findViewById(R.id.btn_logout);

        // 로그인 정보 표시
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);
        String username = prefs.getString("username", "");
        if (isLoggedIn && !username.isEmpty()) {
            tvUsername.setText(getString(R.string.user_greeting_format, username));
            btnLogout.setVisibility(View.VISIBLE);
        } else {
            tvUsername.setText("");
            btnLogout.setVisibility(View.GONE);
        }
        btnLogout.setOnClickListener(v -> logout());

        Intent intent = getIntent();
        if (intent != null) {
            String intentTitle = intent.getStringExtra("title");
            String intentText = intent.getStringExtra("text");
            String intentPublished = intent.getStringExtra("published");
            String intentImage = intent.getStringExtra("image");
            postId = intent.getIntExtra("id", -1);

            Log.d(TAG, "open detail for id=" + postId + ", title=" + intentTitle);

            // make effectively-final copies for use inside lambdas
            final String fTitle = intentTitle == null ? "" : intentTitle;
            final String fText = intentText == null ? "" : intentText;
            final String fPublished = intentPublished == null ? "" : intentPublished;
            final String fImage = intentImage == null ? "" : intentImage;

            // 먼저 인텐트로 받은 값을 임시로 표시(빠른 응답 제공)
            headerTitle.setText(fTitle);
            bodyView.setText(fText);
            if (!fPublished.isEmpty()) {
                dateView.setText(formatDateString(fPublished));
            } else {
                dateView.setText("");
            }
            if (!fImage.isEmpty()) {
                int radiusDp = 12;
                int radiusPx = (int) (radiusDp * getResources().getDisplayMetrics().density + 0.5f);
                Glide.with(this)
                        .load(fImage)
                        .apply(RequestOptions.bitmapTransform(new RoundedCorners(radiusPx))
                                .placeholder(android.R.drawable.ic_menu_report_image))
                        .into(imageView);
            } else {
                imageView.setImageDrawable(null);
            }

            if (fText.trim().isEmpty()) {
                labelBody.setVisibility(View.GONE);
                bodyView.setVisibility(View.GONE);
            } else {
                labelBody.setVisibility(View.VISIBLE);
                bodyView.setVisibility(View.VISIBLE);
            }

            // 서버에서 최신 데이터를 가져와 UI 업데이트 (id가 유효할 때)
            if (postId > 0) {
                fetchPostDetails(postId, fTitle, fText, fPublished, fImage);
            }

            // 추가: 수정/삭제 버튼 처리
            android.widget.Button btnEdit = findViewById(R.id.btnEditPost);
            android.widget.Button btnDelete = findViewById(R.id.btnDeletePost);

            // Activity Result API 등록
            editLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    setResult(RESULT_OK);
                    finish();
                }
            });

            btnEdit.setOnClickListener(v -> {
                // 편집 화면으로 이동: 기존 데이터를 전달
                android.content.Intent editIntent = new android.content.Intent(this, EditPostActivity.class);
                editIntent.putExtra("id", postId);
                editIntent.putExtra("title", fTitle);
                editIntent.putExtra("text", fText);
                editIntent.putExtra("image", fImage);
                // Activity Result API로 실행
                editLauncher.launch(editIntent);
            });

            btnDelete.setOnClickListener(v -> {
                // 삭제 확인 다이얼로그
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("게시글 삭제")
                        .setMessage("정말 이 게시글을 삭제하시겠습니까?")
                        .setNegativeButton("취소", (d, which) -> d.dismiss())
                        .setPositiveButton("삭제", (d, which) -> {
                            // DELETE 호출
                            performDeletePost(postId);
                        })
                        .show();
            });
        }
    }

    // fetchPostDetails: 비동기 GET으로 최신 데이터를 받아 UI를 갱신
    private void fetchPostDetails(int id, String fallbackTitle, String fallbackText, String fallbackPublished, String fallbackImage) {
        OkHttpClient client = NetworkClient.getClient(this);
        String url = "https://cwijiq.pythonanywhere.com/api/posts/" + id;
        Request req = new Request.Builder().url(url).get().build();

        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "fetchPostDetails failed, using intent values", e);
                // 폴백: 이미 인텐트로 표시한 값 유지
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        Log.w(TAG, "fetchPostDetails response not successful: " + response.code());
                        response.close();
                        return;
                    }
                    String body = response.body() != null ? response.body().string() : null;
                    response.close();
                    if (body == null || body.isEmpty()) {
                        Log.w(TAG, "fetchPostDetails empty body");
                        return;
                    }
                    JSONObject obj = new JSONObject(body);
                    // API마다 필드명이 다를 수 있으므로 여러 키를 확인
                    final String title = obj.has("title") ? obj.optString("title", fallbackTitle) : fallbackTitle;
                    final String text;
                    if (obj.has("text")) text = obj.optString("text", fallbackText);
                    else if (obj.has("body")) text = obj.optString("body", fallbackText);
                    else text = fallbackText;

                    final String published;
                    if (obj.has("published")) published = obj.optString("published", fallbackPublished);
                    else if (obj.has("created")) published = obj.optString("created", fallbackPublished);
                    else published = fallbackPublished;

                    final String image;
                    if (obj.has("image")) image = obj.optString("image", fallbackImage);
                    else if (obj.has("image_url")) image = obj.optString("image_url", fallbackImage);
                    else if (obj.has("imageUrl")) image = obj.optString("imageUrl", fallbackImage);
                    else image = fallbackImage;

                    runOnUiThread(() -> {
                        headerTitle.setText(title == null ? "" : title);
                        bodyView.setText(text == null ? "" : text);
                        if (published != null && !published.isEmpty()) {
                            dateView.setText(formatDateString(published));
                        } else {
                            dateView.setText("");
                        }
                        if (image != null && !image.isEmpty()) {
                            int radiusDp = 12;
                            int radiusPx = (int) (radiusDp * getResources().getDisplayMetrics().density + 0.5f);
                            Glide.with(PostDetailActivity.this)
                                    .load(image)
                                    .apply(RequestOptions.bitmapTransform(new RoundedCorners(radiusPx))
                                            .placeholder(android.R.drawable.ic_menu_report_image))
                                    .into(imageView);
                        } else {
                            imageView.setImageDrawable(null);
                        }

                        if (text == null || text.trim().isEmpty()) {
                            labelBody.setVisibility(View.GONE);
                            bodyView.setVisibility(View.GONE);
                        } else {
                            labelBody.setVisibility(View.VISIBLE);
                            bodyView.setVisibility(View.VISIBLE);
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "fetchPostDetails parse failed", e);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (AuthHelper.isTokenInvalid(this)) {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("인증 오류")
                        .setMessage("토큰이 만료되었습니다.")
                        .setPositiveButton("확인", (d, w) -> AuthHelper.clearTokenInvalid(this))
                        .setCancelable(false)
                        .show();
            }
        } catch (Exception e) {
            Log.w(TAG, "onResume: token invalid check failed", e);
        }
    }

    private void performDeletePost(int id) {
        if (id < 0) {
            Toast.makeText(this, "유효하지 않은 게시글입니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        final String deleteUrl = "https://cwijiq.pythonanywhere.com/api_root/Post/" + id + "/";
        Toast.makeText(this, "삭제 요청중...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                okhttp3.OkHttpClient client = NetworkClient.getClient(PostDetailActivity.this);
                okhttp3.Request req = new okhttp3.Request.Builder()
                        .url(deleteUrl)
                        .delete()
                        .build();
                okhttp3.Response resp = client.newCall(req).execute();
                int code = resp.code();
                Log.d(TAG, "DELETE response code=" + code);
                String body = resp.body() != null ? resp.body().string() : null;
                resp.close();

                runOnUiThread(() -> {
                    if (code == 204 || code == 200) {
                        Toast.makeText(PostDetailActivity.this, "삭제되었습니다.", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    } else {
                        String msg = "삭제 실패: HTTP " + code + (body != null && !body.isEmpty() ? (" - " + body) : "");
                        Toast.makeText(PostDetailActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "performDeletePost failed", e);
                runOnUiThread(() -> Toast.makeText(PostDetailActivity.this, "삭제 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
         }).start();
    }

    // 날짜 문자열을 "2025년 10월 9일 9:31 오전" 형식으로 변환
    private String formatDateString(String rawDate) {
        if (rawDate == null || rawDate.isEmpty()) return "";
        String trimmed = rawDate;
        try {
            if (trimmed.length() > 19 && trimmed.charAt(19) != ' ') {
                trimmed = trimmed.substring(0, 19);
            }
        } catch (Exception e) {
            // ignore
        }
        String patternIn = trimmed.contains("T") ? "yyyy-MM-dd'T'HH:mm:ss" : "yyyy-MM-dd HH:mm:ss";
        SimpleDateFormat inputFormat = new SimpleDateFormat(patternIn, Locale.getDefault());
        SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy년 M월 d일 h:mm a", Locale.KOREAN);

        try {
            Date date = inputFormat.parse(trimmed);
            if (date != null) {
                return outputFormat.format(date);
            } else {
                return rawDate;
            }
        } catch (ParseException e) {
            Log.w(TAG, "formatDateString: failed to parse date='" + rawDate + "'", e);
            return rawDate;
        }
    }

    private void logout() {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String token = prefs.getString("token", null);
        String username = prefs.getString("username", null);
        String password = prefs.getString("password", null);
        if (token != null && !token.isEmpty() && username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            new Thread(() -> {
                try {
                    URL url = new URL("https://cwijiq.pythonanywhere.com/api/auth/logout/");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Authorization", "Token " + token);
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setDoOutput(true);
                    org.json.JSONObject jsonParam = new org.json.JSONObject();
                    jsonParam.put("username", username);
                    jsonParam.put("password", password);
                    OutputStream os = conn.getOutputStream();
                    os.write(jsonParam.toString().getBytes(StandardCharsets.UTF_8));
                    os.close();
                    conn.getResponseCode();
                    conn.disconnect();
                } catch (Exception e) {
                    Log.w(TAG, "logout failed", e);
                }
                runOnUiThread(() -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.clear();
                    editor.apply();
                    Toast.makeText(PostDetailActivity.this, "로그아웃되었습니다", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(PostDetailActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                });
            }).start();
        } else {
            SharedPreferences.Editor editor = prefs.edit();
            editor.clear();
            editor.apply();
            Toast.makeText(this, "로그아웃되었습니다", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(PostDetailActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }
    }
}
