package com.example.imageblog;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.button.MaterialButton;

import java.io.PrintWriter;
import java.io.StringWriter;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class EditPostActivity extends AppCompatActivity {
    private static final String TAG = "EditPostActivity";
    private static final int REQ_READ_MEDIA = 1001;
    private ImageView imagePreview;
    private EditText etTitle, etText;
    private ProgressBar progressBar;
    private Uri imageUri;
    private int postId = -1; // 편집 시 사용
    private int userId = -1; // 사용자 ID 저장

    // 기존 이미지 URL만 필요
    private String existingImageUrl = null;

    private ActivityResultLauncher<String> pickImageLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_post);

        imagePreview = findViewById(R.id.imagePreview);
        etTitle = findViewById(R.id.etTitle);
        etText = findViewById(R.id.etText);
        Button btnPick = findViewById(R.id.btnPickImage);
        Button btnSubmit = findViewById(R.id.btnSubmit);
        progressBar = findViewById(R.id.progressBar);

        // 헤더의 사용자명 표시
        TextView tvUsername = findViewById(R.id.headerUserText);
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

        // 인텐트에서 편집용 데이터가 있을 경우 필드 채우기
        Intent intent = getIntent();
        TextView tvPageTitle = findViewById(R.id.tvPageTitle);
        if (intent != null) {
            postId = intent.getIntExtra("id", -1);
            String title = intent.getStringExtra("title");
            String text = intent.getStringExtra("text");
            String image = intent.getStringExtra("image");

            // 이 액티비티는 편집 전용이므로 항상 "수정하기"로 설정
            tvPageTitle.setText("수정하기");

            if (postId < 0) {
                Toast.makeText(this, "유효하지 않은 편집 대상입니다.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            // 기존 이미지 저장
            existingImageUrl = image;

            if (title != null) etTitle.setText(title);
            if (text != null) etText.setText(text);
            if (image != null && !image.isEmpty()) {
                // image가 URL일 경우 Glide로 로드
                try {
                    int radiusDp = 12;
                    int radiusPx = (int) (radiusDp * getResources().getDisplayMetrics().density + 0.5f);
                    Glide.with(this)
                            .load(image)
                            .apply(RequestOptions.bitmapTransform(new RoundedCorners(radiusPx)))
                            .into(imagePreview);
                } catch (Exception e) {
                    Log.w(TAG, "failed to load provided image", e);
                }
            }
        } else {
            // 인텐트가 없으면 편집으로 사용할 수 없음
            tvPageTitle.setText("수정하기");
            Toast.makeText(this, "편집할 게시글 정보가 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                imageUri = uri;
                int radiusDp = 12;
                int radiusPx = (int) (radiusDp * getResources().getDisplayMetrics().density + 0.5f);
                Glide.with(this)
                        .load(uri)
                        .apply(RequestOptions.bitmapTransform(new RoundedCorners(radiusPx)))
                        .into(imagePreview);
            }
        });

        btnPick.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQ_READ_MEDIA);
            } else {
                pickImageLauncher.launch("image/*");
            }
        });

        btnSubmit.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String text = etText.getText().toString().trim();
            if (title.isEmpty() && text.isEmpty() && imageUri == null && postId < 0) {
                Toast.makeText(this, "제목/본문/이미지 중 하나 이상 입력하세요", Toast.LENGTH_SHORT).show();
                return;
            }
            uploadPost(title, text, imageUri, btnSubmit);
        });
    }

    private void uploadPost(String title, String text, Uri imageUri, Button btnSubmit) {
        progressBar.setVisibility(View.VISIBLE);
        btnSubmit.setEnabled(false);

        new Thread(() -> {
            OkHttpClient baseClient = NetworkClient.getClient(EditPostActivity.this);
            OkHttpClient clientForUpload = baseClient.newBuilder()
                    .writeTimeout(120, TimeUnit.SECONDS)
                    .build();

            try {
                SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
                String stored = prefs.getString("token", null);
                String token;
                if (stored != null && !stored.isEmpty()) {
                    token = stored.startsWith("Token ") ? stored : ("Token " + stored);
                } else {
                    String authHelperToken = AuthHelper.getToken(EditPostActivity.this);
                    if (authHelperToken != null && !authHelperToken.isEmpty()) {
                        token = authHelperToken.startsWith("Token ") ? authHelperToken : ("Token " + authHelperToken);
                    } else {
                        token = "Token 4d571c89d156921c3d20cfc59298df353846cae8";
                    }
                }

                String baseUrl = "https://cwijiq.pythonanywhere.com/api_root/Post/";
                boolean isEdit = postId >= 0;

                String url = isEdit ? (baseUrl + postId + "/") : baseUrl;

                String authorId = String.valueOf(userId);

                if (isEdit) {
                    // 편집: 이미지 변경이 있으면 multipart PATCH로 파일 전송
                    if (imageUri != null) {
                        MultipartBody.Builder mBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM);
                        if (title != null) mBuilder.addFormDataPart("title", title);
                        if (text != null) mBuilder.addFormDataPart("text", text);

                        try (InputStream is = getContentResolver().openInputStream(imageUri)) {
                            if (is != null) {
                                byte[] data = toByteArray(is);
                                String mime = getContentResolver().getType(imageUri);
                                if (mime == null) mime = "application/octet-stream";
                                MediaType mediaType = MediaType.parse(mime);

                                String filename = "upload_image";
                                try (android.database.Cursor cursor = getContentResolver().query(imageUri, null, null, null, null)) {
                                    if (cursor != null) {
                                        int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                                        if (nameIndex != -1 && cursor.moveToFirst()) {
                                            filename = cursor.getString(nameIndex);
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "filename lookup failed", e);
                                }

                                RequestBody fileBody = RequestBody.create(data, mediaType);
                                mBuilder.addFormDataPart("image", filename, fileBody);
                            }
                        }

                        RequestBody multipartBody = mBuilder.build();
                        Request patchReq = new Request.Builder()
                                .url(url)
                                .addHeader("Authorization", token)
                                .patch(multipartBody)
                                .build();

                        Response patchResp = clientForUpload.newCall(patchReq).execute();
                        int patchCode = patchResp.code();
                        String patchBodyStr = patchResp.body() != null ? patchResp.body().string() : "";
                        Log.e(TAG, "PATCH (multipart) Response Code: " + patchCode);
                        Log.e(TAG, "PATCH (multipart) Response Body: " + patchBodyStr);
                        final boolean patchSuccess = patchResp.isSuccessful();
                        patchResp.close();

                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            btnSubmit.setEnabled(true);
                            if (patchSuccess) {
                                Toast.makeText(EditPostActivity.this, "수정 성공", Toast.LENGTH_SHORT).show();
                                setResult(RESULT_OK);
                                finish();
                            } else {
                                new AlertDialog.Builder(EditPostActivity.this)
                                        .setTitle("수정 실패")
                                        .setMessage("HTTP " + patchCode + "\n" + patchBodyStr)
                                        .setPositiveButton("확인", null)
                                        .show();
                            }
                        });
                        return;
                    }

                    // 이미지 변경이 없으면 JSON으로 PATCH
                    org.json.JSONObject json = new org.json.JSONObject();
                    try {
                        if (title != null && !title.isEmpty()) json.put("title", title);
                        if (text != null && !text.isEmpty()) json.put("text", text);
                    } catch (org.json.JSONException je) {
                        Log.w(TAG, "failed to build json for patch", je);
                    }

                    if (json.length() == 0) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            btnSubmit.setEnabled(true);
                            Toast.makeText(EditPostActivity.this, "수정할 내용이 없습니다.", Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }

                    RequestBody patchBody = RequestBody.create(json.toString(), okhttp3.MediaType.parse("application/json; charset=utf-8"));
                    Request patchReq = new Request.Builder()
                            .url(url)
                            .addHeader("Authorization", token)
                            .patch(patchBody)
                            .build();

                    Response patchResp = clientForUpload.newCall(patchReq).execute();
                    int patchCode = patchResp.code();
                    String patchBodyStr = patchResp.body() != null ? patchResp.body().string() : "";
                    Log.e(TAG, "PATCH Response Code: " + patchCode);
                    Log.e(TAG, "PATCH Response Body: " + patchBodyStr);
                    final boolean patchSuccess = patchResp.isSuccessful();
                    patchResp.close();

                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        btnSubmit.setEnabled(true);
                        if (patchSuccess) {
                            Toast.makeText(EditPostActivity.this, "수정 성공", Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            new AlertDialog.Builder(EditPostActivity.this)
                                    .setTitle("수정 실패")
                                    .setMessage("HTTP " + patchCode + "\n" + patchBodyStr)
                                    .setPositiveButton("확인", null)
                                    .show();
                        }
                    });
                    return;
                }

                if (userId == -1) {
                    Request userInfoRequest = new Request.Builder()
                            .url("https://cwijiq.pythonanywhere.com/api/auth/user/")
                            .addHeader("Authorization", token)
                            .get()
                            .build();

                    Response userInfoResponse = null;
                    try {
                        userInfoResponse = clientForUpload.newCall(userInfoRequest).execute();
                        int code = userInfoResponse.code();
                        String body = userInfoResponse.body() != null ? userInfoResponse.body().string() : "";
                        Log.d(TAG, "getUserInfo code=" + code + " body=" + body);

                        if (userInfoResponse.isSuccessful() && !body.isEmpty()) {
                            org.json.JSONObject userInfo = new org.json.JSONObject(body);
                            if (userInfo.has("user_id")) {
                                userId = userInfo.optInt("user_id", -1);
                                authorId = String.valueOf(userId);
                                prefs.edit().putInt("userId", userId).apply();
                                Log.d(TAG, "userId obtained=" + userId);
                            } else {
                                Log.w(TAG, "userInfo response missing user_id, will fallback to author=1");
                                authorId = "1";
                            }
                        } else {
                            Log.w(TAG, "getUserInfo failed, code=" + code + ", using default author=1");
                            authorId = "1";
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "getUserInfo exception", e);
                        authorId = "1";
                    } finally {
                        if (userInfoResponse != null) userInfoResponse.close();
                    }
                }

                MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart("author", authorId)
                        .addFormDataPart("title", title)
                        .addFormDataPart("text", text);

                if (imageUri != null) {
                    try (InputStream is = getContentResolver().openInputStream(imageUri)) {
                        if (is != null) {
                            byte[] data = toByteArray(is);

                            String mime = getContentResolver().getType(imageUri);
                            if (mime == null) mime = "application/octet-stream";
                            MediaType mediaType = MediaType.parse(mime);

                            String filename = "upload_image";
                            try (android.database.Cursor cursor = getContentResolver().query(imageUri, null, null, null, null)) {
                                if (cursor != null) {
                                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                                    if (nameIndex != -1 && cursor.moveToFirst()) {
                                        filename = cursor.getString(nameIndex);
                                    }
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "filename lookup failed", e);
                            }

                            RequestBody fileBody = RequestBody.create(data, mediaType);
                            builder.addFormDataPart("image", filename, fileBody);
                        } else {
                            Log.w(TAG, "InputStream is null for imageUri");
                        }
                    }
                }

                RequestBody requestBody = builder.build();
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("Authorization", token)
                        .post(requestBody)
                        .build();

                Response response = clientForUpload.newCall(request).execute();
                int respCode = response.code();
                String respBody = response.body() != null ? response.body().string() : "";
                Log.e(TAG, "POST Response Code: " + respCode);
                Log.e(TAG, "POST Response Body: " + respBody);

                final boolean success = response.isSuccessful();
                response.close();

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnSubmit.setEnabled(true);
                    if (success) {
                        Toast.makeText(EditPostActivity.this, "게시 성공", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    } else {
                        String message = "HTTP " + respCode + "\n" + respBody;
                        new AlertDialog.Builder(EditPostActivity.this)
                                .setTitle("업로드 실패")
                                .setMessage(message)
                                .setPositiveButton("확인", null)
                                .show();
                        Toast.makeText(EditPostActivity.this, "게시 실패: " + respBody, Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "upload failed", e);
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                String stack = sw.toString();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnSubmit.setEnabled(true);
                    new AlertDialog.Builder(EditPostActivity.this)
                            .setTitle("업로드 예외")
                            .setMessage(e.getClass().getSimpleName() + ": " + e.getMessage() + "\n\n" + stack)
                            .setPositiveButton("확인", null)
                            .show();
                    Toast.makeText(EditPostActivity.this, "업로드 중 오류 발생: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static byte[] toByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = input.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
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
                    Log.w(TAG, "logout request failed", e);
                }
                runOnUiThread(() -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.clear();
                    editor.apply();
                    Toast.makeText(EditPostActivity.this, "로그아웃되었습니다", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(EditPostActivity.this, LoginActivity.class);
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
            Intent intent = new Intent(EditPostActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }
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
            android.util.Log.w(TAG, "onResume: token invalid check failed", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_READ_MEDIA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickImageLauncher.launch("image/*");
            } else {
                Toast.makeText(this, "이미지 접근 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
